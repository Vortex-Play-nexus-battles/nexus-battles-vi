package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SalidaPendiente · reintento con espera creciente, sin descartar nunca")
class SalidaPendienteTest {

    private static final OffsetDateTime AHORA = OffsetDateTime.of(2026, 9, 25, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final Duration BASE = Duration.ofSeconds(15);
    private static final Duration MAXIMA = Duration.ofMinutes(15);

    @Test
    @DisplayName("la espera se duplica con cada fallo y se queda en el tope")
    void esperaCreciente() {
        assertThat(SalidaPendiente.espera(1, BASE, MAXIMA)).isEqualTo(Duration.ofSeconds(15));
        assertThat(SalidaPendiente.espera(2, BASE, MAXIMA)).isEqualTo(Duration.ofSeconds(30));
        assertThat(SalidaPendiente.espera(3, BASE, MAXIMA)).isEqualTo(Duration.ofSeconds(60));
        assertThat(SalidaPendiente.espera(6, BASE, MAXIMA)).isEqualTo(Duration.ofMinutes(8));
        assertThat(SalidaPendiente.espera(7, BASE, MAXIMA)).as("16 min pasaria el tope").isEqualTo(MAXIMA);
        assertThat(SalidaPendiente.espera(40, BASE, MAXIMA)).isEqualTo(MAXIMA);
    }

    @Test
    @DisplayName("un fallo suma el intento, guarda el motivo recortado y programa el siguiente; entregar lo limpia")
    void falloYEntrega() {
        SalidaPendiente salida = SalidaPendiente.de(CanalDeSalida.CORREO, EventoDeSancion.EMISION, UUID.randomUUID(),
                UUID.randomUUID(), null, "sancion-x-emision", AHORA);

        salida.fallo("x".repeat(600), AHORA, BASE, MAXIMA);
        assertThat(salida.intentos()).isEqualTo(1);
        assertThat(salida.ultimoError()).hasSize(500);
        assertThat(salida.proximoIntentoEn()).isEqualTo(AHORA.plusSeconds(15));

        salida.fallo(null, AHORA, BASE, MAXIMA);
        assertThat(salida.ultimoError()).isEmpty();
        assertThat(salida.proximoIntentoEn()).isEqualTo(AHORA.plusSeconds(30));

        salida.entregado(AHORA.plusMinutes(1));
        assertThat(salida.intentos()).isEqualTo(3);
        assertThat(salida.entregadoEn()).isEqualTo(AHORA.plusMinutes(1));
        assertThat(salida.ultimoError()).isNull();
        assertThat(salida.proximoIntentoEn()).isNull();
    }

    @Test
    @DisplayName("proyeccion y correo llevan sancion, clave y evento; un aviso no se crea por esa via")
    void formas() {
        UUID sancion = UUID.randomUUID();
        SalidaPendiente proyeccion = SalidaPendiente.de(CanalDeSalida.PROYECCION, EventoDeSancion.LEVANTAMIENTO,
                UUID.randomUUID(), sancion, null, "sancion-x-levantamiento-proyeccion", AHORA);

        assertThat(proyeccion.canal()).isEqualTo(CanalDeSalida.PROYECCION);
        assertThat(proyeccion.evento()).isEqualTo(EventoDeSancion.LEVANTAMIENTO);
        assertThat(proyeccion.sancionId()).isEqualTo(sancion);
        assertThat(proyeccion.titulo()).isNull();
        assertThat(proyeccion.creadoEn()).isEqualTo(AHORA);
        assertThatThrownBy(() -> SalidaPendiente.de(CanalDeSalida.AVISO, EventoDeSancion.EMISION, UUID.randomUUID(),
                sancion, null, "c", AHORA)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SalidaPendiente.de(CanalDeSalida.CORREO, EventoDeSancion.EMISION, UUID.randomUUID(),
                null, null, "c", AHORA)).isInstanceOf(NullPointerException.class);

        SalidaPendiente aviso = SalidaPendiente.aviso(UUID.randomUUID(), UUID.randomUUID(), "SANCION_BANEO", "t", "c",
                AHORA);
        assertThat(aviso.canal()).isEqualTo(CanalDeSalida.AVISO);
        assertThat(aviso.clave()).isNull();
        assertThat(aviso.apelacionId()).isNull();
    }
}
