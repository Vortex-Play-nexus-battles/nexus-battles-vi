package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Los destinos de PROYECCION y CORREO: que contenido componen al entregar y
 * como clasifican las respuestas (rechazo o reintento).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Destinos de las salidas · proyeccion en identidad y correo")
class DestinosDeSalidasTest {

    private static final OffsetDateTime EMITIDA = OffsetDateTime.of(2026, 9, 25, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final UUID JUGADOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ADMIN = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Mock ClienteIdentidad identidad;
    @Mock ClienteCorreo correo;
    @Mock SancionesService sanciones;
    @Mock SancionRepository repositorio;
    @Mock ApelacionRepository apelaciones;

    private static Sancion sancion(Sancion.Tipo tipo) {
        return new Sancion(UUID.randomUUID(), JUGADOR, tipo, "Reincidencia", null, null, ADMIN, "ADMINISTRADOR",
                EMITIDA, tipo == Sancion.Tipo.SUSPENSION ? EMITIDA.plusHours(48) : null);
    }

    private static SalidaPendiente salida(CanalDeSalida canal, EventoDeSancion evento, Sancion s, UUID apelacion) {
        return SalidaPendiente.de(canal, evento, JUGADOR, s.id(), apelacion, "clave-" + s.id(), EMITIDA);
    }

    @Nested
    @DisplayName("PROYECCION: el estado de acceso vigente, no el del evento")
    class Proyeccion {

        private ProyeccionEnIdentidad destino() {
            return new ProyeccionEnIdentidad(identidad, sanciones, repositorio);
        }

        @Test
        @DisplayName("con una suspension vigente: SUSPENDIDO hasta su fin, con su id y su motivo")
        void suspendido() {
            Sancion suspension = sancion(Sancion.Tipo.SUSPENSION);
            when(sanciones.activaDe(JUGADOR)).thenReturn(Optional.of(suspension));
            ArgumentCaptor<ClienteIdentidad.ProyeccionDeSancion> proyeccion =
                    ArgumentCaptor.forClass(ClienteIdentidad.ProyeccionDeSancion.class);

            assertThat(destino().entregar(salida(CanalDeSalida.PROYECCION, EventoDeSancion.EMISION, suspension, null)))
                    .isEqualTo(DestinoDeSalidas.Resultado.ENTREGADO);

            verify(identidad).proyectar(eq(JUGADOR), proyeccion.capture());
            assertThat(proyeccion.getValue()).isEqualTo(new ClienteIdentidad.ProyeccionDeSancion(
                    "SUSPENDIDO", suspension.vigenteHasta(), suspension.id(), "Reincidencia"));
            assertThat(destino().canal()).isEqualTo(CanalDeSalida.PROYECCION);
        }

        @Test
        @DisplayName("con un baneo vigente: BANEADO sin fecha, aunque la salida sea de otra sancion")
        void baneado() {
            Sancion baneo = sancion(Sancion.Tipo.BANEO);
            Sancion otra = sancion(Sancion.Tipo.SUSPENSION);
            when(sanciones.activaDe(JUGADOR)).thenReturn(Optional.of(baneo));

            destino().entregar(salida(CanalDeSalida.PROYECCION, EventoDeSancion.LEVANTAMIENTO, otra, null));

            verify(identidad).proyectar(JUGADOR,
                    new ClienteIdentidad.ProyeccionDeSancion("BANEADO", null, baneo.id(), "Reincidencia"));
        }

        @Test
        @DisplayName("sin restriccion vigente: ACTIVO con la sancion de la salida y el motivo del levantamiento")
        void activo() {
            Sancion levantada = sancion(Sancion.Tipo.BANEO);
            levantada.revertir(ADMIN, "Error de moderacion", EMITIDA.plusHours(1));
            when(sanciones.activaDe(JUGADOR)).thenReturn(Optional.empty());
            when(repositorio.findById(levantada.id())).thenReturn(Optional.of(levantada));

            destino().entregar(salida(CanalDeSalida.PROYECCION, EventoDeSancion.LEVANTAMIENTO, levantada, null));

            verify(identidad).proyectar(JUGADOR,
                    new ClienteIdentidad.ProyeccionDeSancion("ACTIVO", null, levantada.id(), "Error de moderacion"));
        }

        @Test
        @DisplayName("404 (identidad no conoce la cuenta) es rechazo; 401/403 y 5xx se reintentan")
        void respuestas() {
            Sancion baneo = sancion(Sancion.Tipo.BANEO);
            when(sanciones.activaDe(JUGADOR)).thenReturn(Optional.of(baneo));
            SalidaPendiente s = salida(CanalDeSalida.PROYECCION, EventoDeSancion.EMISION, baneo, null);

            doThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "no", null, null, null))
                    .when(identidad).proyectar(eq(JUGADOR), any());
            assertThat(destino().entregar(s)).isEqualTo(DestinoDeSalidas.Resultado.RECHAZADO);

            doThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "no", null, null, null))
                    .when(identidad).proyectar(eq(JUGADOR), any());
            assertThatThrownBy(() -> destino().entregar(s)).isInstanceOf(HttpClientErrorException.class);

            doThrow(HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "no", null, null, null))
                    .when(identidad).proyectar(eq(JUGADOR), any());
            assertThatThrownBy(() -> destino().entregar(s)).isInstanceOf(HttpServerErrorException.class);
        }
    }

    @Nested
    @DisplayName("CORREO: contenido de la sancion, contacto de identidad, clave de idempotencia")
    class Correo {

        private CorreoDeSancion destino() {
            return new CorreoDeSancion(identidad, correo, repositorio, apelaciones,
                    LimitesDeSancion.Fijos.de(1, 30, 30));
        }

        private void conContacto(String email) {
            when(identidad.contacto(JUGADOR)).thenReturn(new ClienteIdentidad.Contacto(JUGADOR, email, "Lyra", "ACTIVO"));
        }

        @Test
        @DisplayName("suspension: tipo, motivo, fin y ultimo dia para apelar (emision + plazo vigente)")
        void suspension() {
            Sancion suspension = sancion(Sancion.Tipo.SUSPENSION);
            when(repositorio.findById(suspension.id())).thenReturn(Optional.of(suspension));
            conContacto("lyra@nexus.test");
            SalidaPendiente s = salida(CanalDeSalida.CORREO, EventoDeSancion.EMISION, suspension, null);

            assertThat(destino().entregar(s)).isEqualTo(DestinoDeSalidas.Resultado.ENTREGADO);

            verify(correo).enviar("clave-" + suspension.id(), new ClienteCorreo.CorreoSancion("lyra@nexus.test", "Lyra",
                    "SUSPENSION", "Reincidencia", suspension.vigenteHasta(), EMITIDA.plusDays(30), null));
            assertThat(destino().canal()).isEqualTo(CanalDeSalida.CORREO);
        }

        @Test
        @DisplayName("advertencia y baneo: sin fecha fin")
        void sinFecha() {
            Sancion advertencia = sancion(Sancion.Tipo.ADVERTENCIA);
            when(repositorio.findById(advertencia.id())).thenReturn(Optional.of(advertencia));

            assertThat(destino().contenido(salida(CanalDeSalida.CORREO, EventoDeSancion.EMISION, advertencia, null)))
                    .contains(new CorreoDeSancion.Contenido("ADVERTENCIA", "Reincidencia", null, EMITIDA.plusDays(30), null));
        }

        @Test
        @DisplayName("apelacion reducida: APELACION_RESUELTA con la decision, la nueva fecha y el resultado")
        void apelacionReducida() {
            Sancion suspension = sancion(Sancion.Tipo.SUSPENSION);
            Apelacion apelacion = new Apelacion(UUID.randomUUID(), suspension.id(), JUGADOR, "argumento", EMITIDA);
            apelacion.resolver(Apelacion.Estado.REDUCIDA, "Primera falta", ADMIN, EMITIDA.plusHours(1),
                    EMITIDA.plusHours(24));
            when(repositorio.findById(suspension.id())).thenReturn(Optional.of(suspension));
            when(apelaciones.findById(apelacion.id())).thenReturn(Optional.of(apelacion));

            assertThat(destino().contenido(salida(CanalDeSalida.CORREO, EventoDeSancion.APELACION_RESUELTA, suspension,
                    apelacion.id()))).contains(new CorreoDeSancion.Contenido("APELACION_RESUELTA", "Primera falta",
                    EMITIDA.plusHours(24), null, "REDUCIDA"));
        }

        @Test
        @DisplayName("contacto 404 o sin correo: rechazo; correo 400: rechazo; correo 503: reintento")
        void respuestas() {
            Sancion baneo = sancion(Sancion.Tipo.BANEO);
            when(repositorio.findById(baneo.id())).thenReturn(Optional.of(baneo));
            SalidaPendiente s = salida(CanalDeSalida.CORREO, EventoDeSancion.EMISION, baneo, null);

            when(identidad.contacto(JUGADOR))
                    .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "no", null, null, null));
            assertThat(destino().entregar(s)).isEqualTo(DestinoDeSalidas.Resultado.RECHAZADO);
            verifyNoInteractions(correo);
        }

        @Test
        @DisplayName("un contacto sin correo no se envia")
        void sinCorreo() {
            Sancion baneo = sancion(Sancion.Tipo.BANEO);
            when(repositorio.findById(baneo.id())).thenReturn(Optional.of(baneo));
            conContacto(" ");

            assertThat(destino().entregar(salida(CanalDeSalida.CORREO, EventoDeSancion.EMISION, baneo, null)))
                    .isEqualTo(DestinoDeSalidas.Resultado.RECHAZADO);
            verify(correo, never()).enviar(any(), any());
        }

        @Test
        @DisplayName("correo rechaza (400) o no puede (503)")
        void respuestasDeCorreo() {
            Sancion baneo = sancion(Sancion.Tipo.BANEO);
            when(repositorio.findById(baneo.id())).thenReturn(Optional.of(baneo));
            conContacto("lyra@nexus.test");
            SalidaPendiente s = salida(CanalDeSalida.CORREO, EventoDeSancion.EMISION, baneo, null);

            doThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "no", null, null, null))
                    .when(correo).enviar(any(), any());
            assertThat(destino().entregar(s)).isEqualTo(DestinoDeSalidas.Resultado.RECHAZADO);

            doThrow(HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "no", null, null, null))
                    .when(correo).enviar(any(), any());
            assertThatThrownBy(() -> destino().entregar(s)).isInstanceOf(HttpServerErrorException.class);
        }

        @Test
        @DisplayName("sin sancion, un levantamiento o una apelacion aun pendiente: no hay correo que componer")
        void sinContenido() {
            Sancion baneo = sancion(Sancion.Tipo.BANEO);
            when(repositorio.findById(baneo.id())).thenReturn(Optional.empty());
            assertThat(destino().entregar(salida(CanalDeSalida.CORREO, EventoDeSancion.EMISION, baneo, null)))
                    .isEqualTo(DestinoDeSalidas.Resultado.RECHAZADO);

            Sancion otra = sancion(Sancion.Tipo.BANEO);
            when(repositorio.findById(otra.id())).thenReturn(Optional.of(otra));
            assertThat(destino().contenido(salida(CanalDeSalida.CORREO, EventoDeSancion.LEVANTAMIENTO, otra, null)))
                    .isEmpty();
            Apelacion pendiente = new Apelacion(UUID.randomUUID(), otra.id(), JUGADOR, "x", EMITIDA);
            when(apelaciones.findById(pendiente.id())).thenReturn(Optional.of(pendiente));
            assertThat(destino().contenido(salida(CanalDeSalida.CORREO, EventoDeSancion.APELACION_RESUELTA, otra,
                    pendiente.id()))).isEmpty();
            verifyNoInteractions(identidad, correo);
        }
    }
}
