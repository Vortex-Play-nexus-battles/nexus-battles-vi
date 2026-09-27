package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/** La tabla de salidas por evento de {@link SalidasDeSancion} (7.3.2). */
@ExtendWith(MockitoExtension.class)
@DisplayName("SalidasDeSancion · que sale por cada evento")
class SalidasDeSancionTest {

    private static final OffsetDateTime AHORA = OffsetDateTime.of(2026, 9, 25, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final UUID JUGADOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MODERADORA = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private SalidaPendienteRepository repositorio;

    private SalidasDeSancion salidas;

    @BeforeEach
    void preparar() {
        salidas = new SalidasDeSancion(repositorio);
    }

    private static Sancion sancion(Sancion.Tipo tipo) {
        return new Sancion(UUID.randomUUID(), JUGADOR, tipo, "Lenguaje ofensivo", null, null, MODERADORA,
                "ADMINISTRADOR", AHORA, tipo == Sancion.Tipo.SUSPENSION ? AHORA.plusHours(24) : null);
    }

    private List<SalidaPendiente> guardadas() {
        ArgumentCaptor<SalidaPendiente> captor = ArgumentCaptor.forClass(SalidaPendiente.class);
        verify(repositorio, atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("advertencia: aviso y correo; ninguna proyeccion (no restringe el acceso)")
    void advertencia() {
        Sancion s = sancion(Sancion.Tipo.ADVERTENCIA);

        salidas.emision(s, "titulo", "cuerpo", AHORA);

        assertThat(guardadas()).extracting(SalidaPendiente::canal, SalidaPendiente::tipo, SalidaPendiente::clave)
                .containsExactly(
                        tuple(CanalDeSalida.AVISO, "SANCION_ADVERTENCIA", "sancion-" + s.id() + "-emision-aviso"),
                        tuple(CanalDeSalida.CORREO, "EMISION", "sancion-" + s.id() + "-emision"));
    }

    @Test
    @DisplayName("suspension y baneo: aviso, proyeccion y correo, todos con la sancion y el jugador")
    void suspensionYBaneo() {
        Sancion s = sancion(Sancion.Tipo.SUSPENSION);

        salidas.emision(s, "titulo", "cuerpo", AHORA);

        List<SalidaPendiente> guardadas = guardadas();
        assertThat(guardadas).extracting(SalidaPendiente::canal)
                .containsExactly(CanalDeSalida.AVISO, CanalDeSalida.PROYECCION, CanalDeSalida.CORREO);
        assertThat(guardadas).allSatisfy(salida -> {
            assertThat(salida.usuarioId()).isEqualTo(JUGADOR);
            assertThat(salida.sancionId()).isEqualTo(s.id());
            assertThat(salida.creadoEn()).isEqualTo(AHORA);
            assertThat(salida.entregadoEn()).isNull();
        });
        assertThat(guardadas.get(0).titulo()).isEqualTo("titulo");
        assertThat(guardadas.get(0).cuerpo()).isEqualTo("cuerpo");
    }

    @Test
    @DisplayName("apelacion revertida de una suspension: aviso, proyeccion y correo con la apelacion")
    void revertida() {
        Sancion s = sancion(Sancion.Tipo.SUSPENSION);
        Apelacion a = new Apelacion(UUID.randomUUID(), s.id(), JUGADOR, "no fui yo", AHORA);
        a.resolver(Apelacion.Estado.REVERTIDA, "Tiene razon", MODERADORA, AHORA, null);

        salidas.resolucion(a, s, "titulo", "cuerpo", AHORA);

        assertThat(guardadas()).extracting(SalidaPendiente::canal, SalidaPendiente::tipo, SalidaPendiente::apelacionId,
                        SalidaPendiente::clave)
                .containsExactly(
                        tuple(CanalDeSalida.AVISO, "APELACION_REVERTIDA", null,
                                "apelacion-" + a.id() + "-resolucion-aviso"),
                        tuple(CanalDeSalida.PROYECCION, "APELACION_RESUELTA", a.id(),
                                "apelacion-" + a.id() + "-resolucion-proyeccion"),
                        tuple(CanalDeSalida.CORREO, "APELACION_RESUELTA", a.id(),
                                "apelacion-" + a.id() + "-resolucion"));
    }

    @Test
    @DisplayName("apelacion mantenida: aviso y correo con la decision; el acceso no cambia")
    void mantenida() {
        Sancion s = sancion(Sancion.Tipo.BANEO);
        Apelacion a = new Apelacion(UUID.randomUUID(), s.id(), JUGADOR, "no fui yo", AHORA);
        a.resolver(Apelacion.Estado.MANTENIDA, "Se confirma", MODERADORA, AHORA, null);

        salidas.resolucion(a, s, "titulo", "cuerpo", AHORA);

        assertThat(guardadas()).extracting(SalidaPendiente::canal)
                .containsExactly(CanalDeSalida.AVISO, CanalDeSalida.CORREO);
    }

    @Test
    @DisplayName("apelacion revertida de una advertencia: sin proyeccion")
    void advertenciaRevertida() {
        Sancion s = sancion(Sancion.Tipo.ADVERTENCIA);
        Apelacion a = new Apelacion(UUID.randomUUID(), s.id(), JUGADOR, "no fui yo", AHORA);
        a.resolver(Apelacion.Estado.REVERTIDA, "Tiene razon", MODERADORA, AHORA, null);

        salidas.resolucion(a, s, "titulo", "cuerpo", AHORA);

        assertThat(guardadas()).extracting(SalidaPendiente::canal)
                .containsExactly(CanalDeSalida.AVISO, CanalDeSalida.CORREO);
    }

    @Test
    @DisplayName("levantamiento: aviso y proyeccion; sin correo (correo.yaml no tiene tipo para el)")
    void levantamiento() {
        Sancion baneo = sancion(Sancion.Tipo.BANEO);

        salidas.levantamiento(baneo, "titulo", "cuerpo", AHORA);

        assertThat(guardadas()).extracting(SalidaPendiente::canal, SalidaPendiente::tipo)
                .containsExactly(tuple(CanalDeSalida.AVISO, "SANCION_LEVANTADA"),
                        tuple(CanalDeSalida.PROYECCION, "LEVANTAMIENTO"));
    }

    @Test
    @DisplayName("levantar una advertencia solo avisa")
    void levantarAdvertencia() {
        salidas.levantamiento(sancion(Sancion.Tipo.ADVERTENCIA), "titulo", "cuerpo", AHORA);

        assertThat(guardadas()).extracting(SalidaPendiente::canal).containsExactly(CanalDeSalida.AVISO);
    }
}
