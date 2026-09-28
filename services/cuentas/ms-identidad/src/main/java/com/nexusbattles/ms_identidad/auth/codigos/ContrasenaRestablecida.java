package com.nexusbattles.ms_identidad.auth.codigos;

import java.util.UUID;

/**
 * Se canjeo un codigo de restablecimiento (o de activacion) y la cuenta tiene
 * contrasena nueva. Al confirmarse: aviso {@code cambio-clave} al correo —para
 * que quien NO lo hizo reaccione— y auditoria ({@link CorreosDeCuenta}).
 *
 * @param codigoId  el codigo canjeado: clave de idempotencia del aviso
 * @param uid       puede ser nulo en cuentas anteriores al identificador publico
 * @param usuarioId clave interna, para auditar las cuentas sin uid
 */
public record ContrasenaRestablecida(Long codigoId, TipoCodigo tipo, UUID uid, Long usuarioId, String email,
                                     String apodo, String ip) {

    /** Como aparece la cuenta en la auditoria: su uid, o su clave interna si no tiene. */
    public String afectado() {
        return uid != null ? uid.toString() : "usuario-" + usuarioId;
    }
}
