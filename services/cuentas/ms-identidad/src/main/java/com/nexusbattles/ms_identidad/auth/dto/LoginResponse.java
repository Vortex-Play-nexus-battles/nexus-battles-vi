package com.nexusbattles.ms_identidad.auth.dto;

import lombok.Getter;

/**
 * Respuesta del login.
 *
 * <p>R17 anade dos campos, sin tocar los que ya habia (contrato 1.1.0,
 * aditivo):
 * <ul>
 *   <li>{@code uid} — el identificador estable (ADR-002). El navegador lo
 *       leia decodificando el JWT; ahora lo tiene explicito.</li>
 *   <li>{@code onboardingListo} — si el alta del jugador ya termino (creditos
 *       y heroe iniciales). Si es {@code false}, la interfaz lleva a
 *       «Preparando tu cuenta» en vez de al inicio.</li>
 * </ul>
 */
@Getter
public class LoginResponse {
    private final Long usuarioId;
    private final String apodo;
    private final String email;
    private final String rol;
    private final boolean dispositivoNuevo;
    private final String token;
    private final String uid;
    private final boolean onboardingListo;

    public LoginResponse(Long usuarioId, String apodo, String email, String rol,
                         boolean dispositivoNuevo, String token) {
        this(usuarioId, apodo, email, rol, dispositivoNuevo, token, null, true);
    }

    public LoginResponse(Long usuarioId, String apodo, String email, String rol,
                         boolean dispositivoNuevo, String token, String uid, boolean onboardingListo) {
        this.usuarioId = usuarioId;
        this.apodo = apodo;
        this.email = email;
        this.rol = rol;
        this.dispositivoNuevo = dispositivoNuevo;
        this.token = token;
        this.uid = uid;
        this.onboardingListo = onboardingListo;
    }
}
