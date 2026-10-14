package com.nexusbattles.ms_identidad.admin.dto;

import com.nexusbattles.ms_identidad.auth.model.Usuario;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * {@code GET /api/v1/admin/jugadores/bloqueos} — SA-20, §7.3.4 «Alertas de
 * comportamiento sospechoso», señal 1 de D-45: las cuentas bloqueadas por
 * intentos fallidos de inicio de sesion desde un dia (esquema
 * {@code CuentasBloqueadasPorIntentos} de ms-identidad-admin.yaml 1.11.0).
 *
 * <p>Identifica a cada cuenta a proposito: es una alerta para actuar sobre
 * ella, no una estadistica (la cifra agregada esta en los indicadores). Por eso
 * solo la lee quien gestiona cuentas, y no lleva correo ni nada que sirva para
 * entrar.
 *
 * @param desde          primer dia del periodo (inclusive)
 * @param total          cuentas bloqueadas en el periodo (una cuenta bloqueada
 *                       dos veces cuenta una, con su ultimo bloqueo)
 * @param cuentas        las de bloqueo mas reciente primero, hasta el limite pedido
 * @param ocultarPruebas si se dejaron fuera las cuentas de pruebas automaticas
 * @param calculadoEn    cuando se consulto
 */
public record CuentasBloqueadasResponse(
        LocalDate desde,
        long total,
        List<CuentaBloqueada> cuentas,
        boolean ocultarPruebas,
        OffsetDateTime calculadoEn) {

    /**
     * Una cuenta bloqueada por intentos fallidos.
     *
     * @param uid            identificador publico de la cuenta
     * @param apodo          nombre visible
     * @param rol            su rol (un bloqueo de una cuenta administrativa pesa mas)
     * @param estado         estado de la cuenta (el bloqueo no lo cambia)
     * @param bloqueadaEn    cuando empezo su ultimo bloqueo, con la zona del servidor
     * @param vigente        si sigue bloqueada ahora mismo
     * @param bloqueadaHasta hasta cuando, si sigue bloqueada; nulo si ya puede entrar
     */
    public record CuentaBloqueada(
            UUID uid,
            String apodo,
            String rol,
            String estado,
            OffsetDateTime bloqueadaEn,
            boolean vigente,
            OffsetDateTime bloqueadaHasta) {

        /**
         * @param ahora la hora del servidor, en su zona
         * @param zona  la zona del servidor: las marcas de la base no la llevan,
         *              y la respuesta si, para que nadie las lea en otra hora
         */
        public static CuentaBloqueada de(Usuario usuario, LocalDateTime ahora, ZoneId zona) {
            LocalDateTime hasta = usuario.getBloqueadoHasta();
            boolean vigente = hasta != null && ahora.isBefore(hasta);
            return new CuentaBloqueada(
                    usuario.getPublicId(),
                    usuario.getApodo(),
                    usuario.getRol() != null ? usuario.getRol().getNombre() : null,
                    usuario.getEstado(),
                    conZona(usuario.getUltimoBloqueoEn(), zona),
                    vigente,
                    vigente ? conZona(hasta, zona) : null);
        }

        private static OffsetDateTime conZona(LocalDateTime momento, ZoneId zona) {
            return momento == null ? null : momento.atZone(zona).toOffsetDateTime();
        }
    }
}
