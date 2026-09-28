package com.nexusbattles.ms_identidad.auth.codigos;

import java.util.UUID;

/**
 * La cuenta confirmo su correo y paso a ACTIVO (B1). Al confirmarse la
 * transaccion: auditoria de la verificacion y correo de bienvenida
 * ({@link CorreosDeCuenta}). El alta del jugador la arranca
 * {@code OnboardingService.iniciar} dentro de la misma transaccion.
 */
public record CorreoVerificado(UUID uid, String email, String apodo, String nombres, String apellidos,
                               String ip) {
}
