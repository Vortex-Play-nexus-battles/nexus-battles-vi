package com.nexusbattles.ms_identidad.privacidad;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * {@code CierreDeCuenta} de ms-identidad-perfiles.yaml 1.4.0: si el cierre de
 * la propia cuenta esta programado y para cuando.
 *
 * <p>{@code plazoDias} viaja siempre para que la interfaz no repita el plazo
 * por su cuenta (RN-USR-011). Las fechas llevan su desplazamiento horario: se
 * guardan con la hora del servidor, como el resto del esquema de identidad.
 */
public record EstadoDelCierre(String estado, int plazoDias, OffsetDateTime solicitadoEn,
                              OffsetDateTime programadoPara) {

    public static final String SIN_SOLICITUD = "SIN_SOLICITUD";
    public static final String PROGRAMADO = "PROGRAMADO";

    static EstadoDelCierre sinSolicitud() {
        return new EstadoDelCierre(SIN_SOLICITUD, SolicitudDeCierre.PLAZO_DIAS, null, null);
    }

    static EstadoDelCierre de(SolicitudDeCierre solicitud, ZoneId zona) {
        if (solicitud == null || !solicitud.estaProgramada()) {
            return sinSolicitud();
        }
        return new EstadoDelCierre(PROGRAMADO, SolicitudDeCierre.PLAZO_DIAS,
                solicitud.getSolicitadaEn().atZone(zona).toOffsetDateTime(),
                solicitud.getProgramadaPara().atZone(zona).toOffsetDateTime());
    }
}
