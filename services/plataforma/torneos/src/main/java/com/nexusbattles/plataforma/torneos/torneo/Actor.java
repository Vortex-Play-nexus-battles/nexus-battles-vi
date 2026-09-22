package com.nexusbattles.plataforma.torneos.torneo;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Quien actua, sacado del token: un usuario (uid + rol) o un servicio
 * (credencial de ADR-005, {@code rol = SERVICIO}, sin uid).
 *
 * <p>D-21 (INC-13): el «administrador de torneo» de la ficha no esta en la
 * Tabla 24; mientras el PO no lo defina, lo asume ADMINISTRADOR o
 * SUPER_ADMINISTRADOR.
 */
public record Actor(UUID id, String rol, String nombre) {

    static final Set<String> ADMINISTRACION = Set.of("ADMINISTRADOR", "SUPER_ADMINISTRADOR");
    static final String SERVICIO = "SERVICIO";

    public Actor {
        Objects.requireNonNull(rol);
        Objects.requireNonNull(nombre);
    }

    public static Actor usuario(UUID id, String rol) {
        return new Actor(Objects.requireNonNull(id), rol, id.toString());
    }

    public static Actor servicio(String clientId) {
        return new Actor(null, SERVICIO, clientId);
    }

    public boolean puedeAdministrar() {
        return ADMINISTRACION.contains(rol);
    }

    public boolean esServicio() {
        return SERVICIO.equals(rol);
    }

    public boolean esUsuario() {
        return id != null && !esServicio();
    }
}
