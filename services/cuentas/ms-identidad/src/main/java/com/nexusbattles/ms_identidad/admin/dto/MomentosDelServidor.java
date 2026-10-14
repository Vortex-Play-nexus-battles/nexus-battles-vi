package com.nexusbattles.ms_identidad.admin.dto;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Las marcas de tiempo de la base, con su zona — SA-21.
 *
 * <p>Las columnas de cuentas ({@code creado_en}, {@code ultimo_acceso},
 * {@code suspendido_hasta}...) guardan la hora de pared del servidor, sin
 * zona. Publicadas asi («2026-10-14T17:30:00»), un navegador las lee como hora
 * SUYA: con el servidor en UTC y la consola en Colombia, una cuenta registrada
 * a las 12:30 salia «registrada a las 5:30 p. m.». La API las publica como
 * instante con la zona del servidor («2026-10-14T17:30:00Z»), que es lo que
 * pide el {@code format: date-time} del contrato (RFC 3339), y cada consola
 * las pinta en su hora.
 */
public final class MomentosDelServidor {

    private MomentosDelServidor() {
    }

    /**
     * @param momento hora de pared del servidor, o null
     * @param zona    la zona en la que se escribio (la del reloj del servicio)
     * @return el mismo instante con su desfase, o null
     */
    public static OffsetDateTime conZona(LocalDateTime momento, ZoneId zona) {
        return momento == null ? null : momento.atZone(zona).toOffsetDateTime();
    }
}
