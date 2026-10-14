package com.nexusbattles.plataforma.moderacionsanciones.auditoria;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * SA-5 (§7.3.5): la bitacora central de moderacion-sanciones — quien y desde
 * donde salen de la peticion, la cola y su entrega a ms-cumplimiento.
 */
class BitacoraDeAuditoriaTest {

    private static final Instant AHORA = Instant.parse("2026-10-14T15:00:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);
    private static final UUID MODERADORA = UUID.fromString("5b2e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55");
    private static final String URL = "http://srv-ms-cumplimiento:8091/api/v1/admin/auditoria/eventos";

    private final AsientoDeAuditoriaRepository asientos = mock(AsientoDeAuditoriaRepository.class);

    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    private static void peticionDesde(String ipDelBorde) {
        MockHttpServletRequest peticion = new MockHttpServletRequest();
        if (ipDelBorde != null) {
            peticion.addHeader("X-Real-IP", ipDelBorde);
        }
        peticion.addHeader("X-Forwarded-For", "1.2.3.4, 10.0.0.2");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(peticion));
    }

    private static void conSesionDe(UUID uid) {
        Jwt token = Jwt.withTokenValue("t").header("alg", "RS256").subject("moderadora_qa")
                .claim("uid", uid.toString()).claim("rol", "MODERADOR").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token, List.of()));
    }

    @Nested
    @DisplayName("quien y desde donde")
    class Registro {

        private final BitacoraEnCola bitacora = new BitacoraEnCola(asientos, RELOJ);

        @Test
        @DisplayName("una accion que entra por el borde deja su asiento con el uid del token y la IP del borde")
        void desdeElBorde() {
            peticionDesde("203.0.113.9");
            conSesionDe(MODERADORA);

            bitacora.registrar(TipoDeAccion.SANCION, "7a1e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55", null,
                    "Advertencia · Causal: Spam o publicidad no autorizada", "Mensajes repetidos");

            ArgumentCaptor<AsientoDeAuditoria> captor = ArgumentCaptor.forClass(AsientoDeAuditoria.class);
            verify(asientos).save(captor.capture());
            AsientoDeAuditoria asiento = captor.getValue();
            assertThat(asiento.tipoAccion()).isEqualTo("SANCION");
            assertThat(asiento.administradorId()).isEqualTo(MODERADORA.toString());
            assertThat(asiento.ipOrigen()).as("la de X-Real-IP, nunca la que declara el cliente")
                    .isEqualTo("203.0.113.9");
            assertThat(asiento.ocurridoEn()).isEqualTo(OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC));
            assertThat(asiento.referencia()).isEqualTo("moderacion-sanciones:" + asiento.id());
            assertThat(asiento.entregadoEn()).isNull();
        }

        @Test
        @DisplayName("sin X-Real-IP la accion llego de otro servicio (ms-identidad), que la registra: no se repite")
        void desdeOtroServicio() {
            peticionDesde(null);
            conSesionDe(MODERADORA);

            bitacora.registrar(TipoDeAccion.SUSPENSION, "cuenta", null, "Suspensión", "motivo");

            verify(asientos, never()).save(any());
        }

        @Test
        @DisplayName("una X-Real-IP que no parece una IP no se guarda como si lo fuera")
        void ipFalsa() {
            peticionDesde("<script>");
            conSesionDe(MODERADORA);

            bitacora.registrar(TipoDeAccion.SANCION, "cuenta", null, "Advertencia", "motivo");

            verify(asientos, never()).save(any());
        }

        @Test
        @DisplayName("fuera de una peticion (una tarea programada) no hay accion de persona que registrar")
        void sinPeticion() {
            bitacora.registrar(TipoDeAccion.SANCION, "cuenta", null, "Advertencia", "motivo");

            verify(asientos, never()).save(any());
        }

        @Test
        @DisplayName("textos largos se recortan al largo de ms-cumplimiento; sin motivo, se dice")
        void recortes() {
            peticionDesde("2001:db8::1");
            conSesionDe(MODERADORA);

            bitacora.registrar(TipoDeAccion.ACTUALIZACION, "lista-negra:" + "x".repeat(300), "a".repeat(2500),
                    null, " ");

            ArgumentCaptor<AsientoDeAuditoria> captor = ArgumentCaptor.forClass(AsientoDeAuditoria.class);
            verify(asientos).save(captor.capture());
            assertThat(captor.getValue().afectado()).hasSize(255).endsWith("…");
            assertThat(captor.getValue().valorAnterior()).hasSize(2000);
            assertThat(captor.getValue().motivo()).isEqualTo("Sin motivo indicado");
            assertThat(captor.getValue().ipOrigen()).isEqualTo("2001:db8::1");
        }
    }

    @Nested
    @DisplayName("el cliente de ms-cumplimiento")
    class Cliente {

        private final RestClient.Builder constructor = RestClient.builder();
        private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(constructor).build();
        private final ClienteDeCumplimiento cliente = new ClienteDeCumplimiento(constructor.build(), URL);

        private AsientoDeAuditoria asiento() {
            return AsientoDeAuditoria.nuevo(TipoDeAccion.APROBACION, MODERADORA, "7a1e1c4e", "Apelación pendiente",
                    "Apelación estimada: sanción revertida", "Error de identificación",
                    "203.0.113.9", OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC));
        }

        @Test
        @DisplayName("manda RegistrarAuditoriaRequest 1.3.0 con el momento y la referencia; 201 es registrado")
        void cuerpo() {
            AsientoDeAuditoria asiento = asiento();
            servidor.expect(requestTo(URL))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                    .andExpect(jsonPath("$.tipoAccion").value("APROBACION"))
                    .andExpect(jsonPath("$.administradorId").value(MODERADORA.toString()))
                    .andExpect(jsonPath("$.afectado").value("7a1e1c4e"))
                    .andExpect(jsonPath("$.valorAnterior").value("Apelación pendiente"))
                    .andExpect(jsonPath("$.valorNuevo").value("Apelación estimada: sanción revertida"))
                    .andExpect(jsonPath("$.motivo").value("Error de identificación"))
                    .andExpect(jsonPath("$.ipOrigen").value("203.0.113.9"))
                    .andExpect(jsonPath("$.ocurridoEn").value("2026-10-14T15:00:00Z"))
                    .andExpect(jsonPath("$.referencia").value("moderacion-sanciones:" + asiento.id()))
                    .andRespond(withStatus(HttpStatus.CREATED));

            assertThat(cliente.entregar(asiento)).isEqualTo(ClienteDeCumplimiento.Resultado.REGISTRADO);
            servidor.verify();
        }

        @Test
        @DisplayName("200 (la referencia ya estaba) tambien es registrado; 400 es rechazado")
        void respuestas() {
            servidor.expect(requestTo(URL)).andRespond(withSuccess());
            servidor.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

            assertThat(cliente.entregar(asiento())).isEqualTo(ClienteDeCumplimiento.Resultado.REGISTRADO);
            assertThat(cliente.entregar(asiento())).isEqualTo(ClienteDeCumplimiento.Resultado.RECHAZADO);
        }
    }

    @Nested
    @DisplayName("la entrega")
    class Entrega {

        private final ClienteDeCumplimiento cliente = mock(ClienteDeCumplimiento.class);
        private final EntregaDeAuditoria entrega = new EntregaDeAuditoria(asientos, cliente, RELOJ,
                Duration.ofSeconds(10), Duration.ofMinutes(15));

        private AsientoDeAuditoria pendiente() {
            return AsientoDeAuditoria.nuevo(TipoDeAccion.CREACION, MODERADORA, "lista-negra:troll", null,
                    "«troll» · Ofensivo · palabra completa · activo", "Alta en la lista negra", "203.0.113.9",
                    OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC));
        }

        @Test
        @DisplayName("lo registrado queda entregado; lo rechazado se queda con su motivo y una espera")
        void entregaYRechazo() {
            AsientoDeAuditoria bueno = pendiente();
            AsientoDeAuditoria malo = pendiente();
            when(asientos.porEntregar(any(), any(Pageable.class))).thenReturn(List.of(bueno, malo));
            when(cliente.entregar(bueno)).thenReturn(ClienteDeCumplimiento.Resultado.REGISTRADO);
            when(cliente.entregar(malo)).thenReturn(ClienteDeCumplimiento.Resultado.RECHAZADO);

            assertThat(entrega.ejecutar()).isEqualTo(1);

            assertThat(bueno.entregadoEn()).isNotNull();
            assertThat(malo.entregadoEn()).isNull();
            assertThat(malo.ultimoError()).contains("rechazado");
            assertThat(malo.proximoIntentoEn()).isEqualTo(OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC)
                    .plusSeconds(10));
            verify(asientos, times(2)).save(any());
        }

        @Test
        @DisplayName("si ms-cumplimiento no responde se para la vuelta: los demas esperan sin gastar intentos")
        void caido() {
            AsientoDeAuditoria primero = pendiente();
            AsientoDeAuditoria segundo = pendiente();
            when(asientos.porEntregar(any(), any(Pageable.class))).thenReturn(List.of(primero, segundo));
            when(cliente.entregar(primero)).thenThrow(new ResourceAccessException("timeout", new IOException()));

            assertThat(entrega.ejecutar()).isZero();

            assertThat(primero.intentos()).isEqualTo(1);
            assertThat(segundo.intentos()).isZero();
            verify(cliente, never()).entregar(segundo);
        }

        @Test
        @DisplayName("la espera crece con cada fallo y no pasa del tope")
        void esperaCreciente() {
            AsientoDeAuditoria asiento = pendiente();
            OffsetDateTime ahora = OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC);
            for (int i = 0; i < 12; i++) {
                asiento.fallo("caido", ahora, Duration.ofSeconds(10), Duration.ofMinutes(15));
            }
            assertThat(asiento.proximoIntentoEn()).isEqualTo(ahora.plusMinutes(15));
        }

        @Test
        @DisplayName("sin AUDITORIA_URL no hay cliente: los asientos esperan en la cola")
        void sinUrl() {
            EntregaDeAuditoria sinCliente = new EntregaDeAuditoria(asientos, null, RELOJ, Duration.ofSeconds(10),
                    Duration.ofMinutes(15));

            assertThat(sinCliente.ejecutar()).isZero();
            verify(asientos, never()).porEntregar(any(), any(Pageable.class));
        }
    }
}
