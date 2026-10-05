package com.nexusbattles.ms_identidad.privacidad;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Solicitud de cierre de cuenta (HU-PRV-005, RN-USR-011)")
class SolicitudDeCierreTest {

    private static final UUID UID = UUID.fromString("aaaaaaaa-1111-4222-8333-444444444444");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 5, 14, 30, 15, 987_654_321);

    @Test
    @DisplayName("se programa a exactamente 30 dias, al segundo")
    void programaATreintaDias() {
        SolicitudDeCierre solicitud = SolicitudDeCierre.programar(UID, AHORA);

        assertThat(solicitud.getId()).isNotNull();
        assertThat(solicitud.getUsuarioUid()).isEqualTo(UID);
        assertThat(solicitud.getEstado()).isEqualTo(SolicitudDeCierre.PROGRAMADA);
        assertThat(solicitud.getSolicitadaEn()).isEqualTo(LocalDateTime.of(2026, 10, 5, 14, 30, 15));
        assertThat(solicitud.getProgramadaPara()).isEqualTo(LocalDateTime.of(2026, 11, 4, 14, 30, 15));
        assertThat(SolicitudDeCierre.PLAZO_DIAS).isEqualTo(30);
        assertThat(SolicitudDeCierre.PLAZO.toDays()).isEqualTo(30);
    }

    @Test
    @DisplayName("vence justo al cumplirse el plazo, ni un segundo antes")
    void vencimiento() {
        SolicitudDeCierre solicitud = SolicitudDeCierre.programar(UID, AHORA);
        LocalDateTime vence = solicitud.getProgramadaPara();

        assertThat(solicitud.vencida(vence.minusSeconds(1))).isFalse();
        assertThat(solicitud.vencida(vence)).isTrue();
        assertThat(solicitud.vencida(vence.plusDays(3))).isTrue();
    }

    @Test
    @DisplayName("cancelar solo desde PROGRAMADA; despues ya no vence ni se ejecuta")
    void cancelar() {
        SolicitudDeCierre solicitud = SolicitudDeCierre.programar(UID, AHORA);
        LocalDateTime luego = AHORA.plusDays(2);

        solicitud.cancelar(luego);
        solicitud.cancelar(luego.plusDays(1));
        solicitud.marcarEjecutada(luego.plusDays(40));

        assertThat(solicitud.getEstado()).isEqualTo(SolicitudDeCierre.CANCELADA);
        assertThat(solicitud.getCanceladaEn()).isEqualTo(luego);
        assertThat(solicitud.getEjecutadaEn()).isNull();
        assertThat(solicitud.estaProgramada()).isFalse();
        assertThat(solicitud.vencida(luego.plusDays(40))).isFalse();
    }

    @Test
    @DisplayName("ejecutar es idempotente y una ejecutada no se puede cancelar")
    void ejecutar() {
        SolicitudDeCierre solicitud = SolicitudDeCierre.programar(UID, AHORA);
        LocalDateTime vence = solicitud.getProgramadaPara();

        solicitud.marcarEjecutada(vence);
        solicitud.marcarEjecutada(vence.plusHours(1));
        solicitud.cancelar(vence.plusHours(2));

        assertThat(solicitud.getEstado()).isEqualTo(SolicitudDeCierre.EJECUTADA);
        assertThat(solicitud.getEjecutadaEn()).isEqualTo(vence);
        assertThat(solicitud.getCanceladaEn()).isNull();
    }

    @Test
    @DisplayName("sin cuenta o sin hora no hay solicitud")
    void datosObligatorios() {
        assertThatThrownBy(() -> SolicitudDeCierre.programar(null, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SolicitudDeCierre.programar(UID, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
