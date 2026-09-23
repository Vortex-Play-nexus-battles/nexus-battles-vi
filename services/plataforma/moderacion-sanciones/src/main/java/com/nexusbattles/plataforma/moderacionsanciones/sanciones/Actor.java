package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Quien actua, tal como sale del token (ADR-002): su {@code uid} y su rol.
 * La regla de que puede hacer cada rol (Tabla 24 del documento oficial) vive
 * en {@link SancionesService}, no en el controlador.
 */
public record Actor(UUID id, String rol) {

    static final Set<String> MODERACION = Set.of("MODERADOR", "ADMINISTRADOR", "SUPER_ADMINISTRADOR");
    static final Set<String> ADMINISTRACION = Set.of("ADMINISTRADOR", "SUPER_ADMINISTRADOR");

    public Actor {
        Objects.requireNonNull(id);
        Objects.requireNonNull(rol);
    }

    public boolean puedeModerar() {
        return MODERACION.contains(rol);
    }

    public boolean puedeAdministrar() {
        return ADMINISTRACION.contains(rol);
    }
}
