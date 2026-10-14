package com.nexusbattles.ms_chatbot.chat.brechas;

import com.nexusbattles.ms_chatbot.chat.model.BrechaConocimiento;
import com.nexusbattles.ms_chatbot.chat.model.EstadoBrecha;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

// ms-chatbot.yaml 1.7.0 (7.4.5): el estado de una pregunta sin respuesta.
class BrechaConocimientoEstadoTest {

    private static final Instant AHORA = Instant.parse("2026-10-14T15:00:00Z");

    private static BrechaConocimiento brecha() {
        return new BrechaConocimiento("como gano gemas", "¿Cómo gano gemas?");
    }

    @Test
    void naceComoPendienteSinQuienNiCuando() {
        BrechaConocimiento brecha = brecha();

        assertThat(brecha.getEstado()).isEqualTo(EstadoBrecha.PENDIENTE);
        assertThat(brecha.getEstadoCambiadoPor()).isNull();
        assertThat(brecha.getEstadoCambiadoEn()).isNull();
        assertThat(brecha.getReaperturas()).isZero();
        assertThat(brecha.getTotal()).isZero();
    }

    @Test
    void elAdministradorLaMarcaConQuienYCuando() {
        BrechaConocimiento brecha = brecha();

        assertThat(brecha.cambiarEstado(EstadoBrecha.ATENDIDA, " uid-admin ", AHORA)).isTrue();

        assertThat(brecha.getEstado()).isEqualTo(EstadoBrecha.ATENDIDA);
        assertThat(brecha.getEstadoCambiadoPor()).isEqualTo("uid-admin");
        assertThat(brecha.getEstadoCambiadoEn()).isEqualTo(AHORA);
    }

    // Marcarla con el estado que ya tiene no cambia ni quien ni cuando.
    @Test
    void elMismoEstadoNoCambiaNada() {
        BrechaConocimiento brecha = brecha();
        brecha.cambiarEstado(EstadoBrecha.DESCARTADA, "uno", AHORA);

        assertThat(brecha.cambiarEstado(EstadoBrecha.DESCARTADA, "otro", AHORA.plusSeconds(60))).isFalse();
        assertThat(brecha.cambiarEstado(null, "otro", AHORA.plusSeconds(60))).isFalse();

        assertThat(brecha.getEstadoCambiadoPor()).isEqualTo("uno");
        assertThat(brecha.getEstadoCambiadoEn()).isEqualTo(AHORA);
    }

    // El patron: una ATENDIDA que se vuelve a preguntar vuelve sola a
    // PENDIENTE, sin quien, y suma una reapertura.
    @Test
    void unaAtendidaQueSeVuelveAPreguntar_vuelveAPendiente() {
        BrechaConocimiento brecha = brecha();
        brecha.registrarEscalamiento();
        brecha.cambiarEstado(EstadoBrecha.ATENDIDA, "uid-admin", AHORA);

        brecha.registrarEscalamiento();

        assertThat(brecha.getEstado()).isEqualTo(EstadoBrecha.PENDIENTE);
        assertThat(brecha.getEstadoCambiadoPor()).isNull();
        assertThat(brecha.getEstadoCambiadoEn()).isEqualTo(brecha.getFechaUltimaOcurrencia());
        assertThat(brecha.getReaperturas()).isEqualTo(1);

        brecha.cambiarEstado(EstadoBrecha.ATENDIDA, "uid-admin", AHORA);
        brecha.registrarNoUtil();

        assertThat(brecha.getEstado()).isEqualTo(EstadoBrecha.PENDIENTE);
        assertThat(brecha.getReaperturas()).isEqualTo(2);
        assertThat(brecha.getTotal()).isEqualTo(3);
    }

    // Una DESCARTADA es ruido: sigue contando, pero no vuelve a la lista de trabajo.
    @Test
    void unaDescartadaSigueDescartadaAunqueSeRepita() {
        BrechaConocimiento brecha = brecha();
        brecha.cambiarEstado(EstadoBrecha.DESCARTADA, "uid-admin", AHORA);

        brecha.registrarEscalamiento();
        brecha.registrarNoUtil();

        assertThat(brecha.getEstado()).isEqualTo(EstadoBrecha.DESCARTADA);
        assertThat(brecha.getReaperturas()).isZero();
        assertThat(brecha.getTotal()).isEqualTo(2);
    }

    @Test
    void quienSeRecortaASuColumnaYUnoEnBlancoQuedaSinQuien() {
        BrechaConocimiento brecha = brecha();
        brecha.cambiarEstado(EstadoBrecha.ATENDIDA, "x".repeat(100), AHORA);
        assertThat(brecha.getEstadoCambiadoPor()).hasSize(BrechaConocimiento.LONGITUD_MAXIMA_DE_QUIEN);

        brecha.cambiarEstado(EstadoBrecha.PENDIENTE, "  ", AHORA);
        assertThat(brecha.getEstadoCambiadoPor()).isNull();
    }
}
