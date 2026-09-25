package com.nexusbattles.ms_identidad.auth.codigos;

import java.util.UUID;

/**
 * Se publica dentro de la transaccion del autorregistro; al confirmarse,
 * {@link CorreosDeCuenta} deja el alta de la cuenta (aun sin verificar) en la
 * auditoria. El alta del JUGADOR —creditos, heroe, equipo— no empieza aqui:
 * espera a {@link CorreoVerificado}.
 */
public record CuentaRegistrada(UUID uid, String apodo, String ip) {
}
