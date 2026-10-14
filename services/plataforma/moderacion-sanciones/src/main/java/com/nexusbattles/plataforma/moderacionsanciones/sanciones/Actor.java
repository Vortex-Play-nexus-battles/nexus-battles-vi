package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Quien actua, tal como sale del token (ADR-002): su {@code uid} y su rol.
 * La regla de que puede hacer cada rol (Tabla 24 del documento oficial) vive
 * en {@link SancionesService}, no en el controlador.
 *
 * <p><b>Jerarquia (7.3.1)</b>: Jugador &lt; Moderador &lt; Administrador &lt;
 * Super Administrador. {@link #superaA} dice si este actor esta por encima
 * del rol de la cuenta afectada; un rol desconocido no supera a nadie.
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

    /**
     * Si el rol de este actor es estrictamente superior a {@code rolDeLaCuenta}.
     * Un rol desconocido, de cualquiera de los dos lados, responde que no.
     */
    public boolean superaA(String rolDeLaCuenta) {
        int propio = rango(rol);
        int objetivo = rango(rolDeLaCuenta);
        return propio > 0 && objetivo >= 0 && propio > objetivo;
    }

    /** 0 jugador, 1 moderador, 2 administrador, 3 super administrador; -1 si no es ninguno. */
    static int rango(String rol) {
        if (rol == null) {
            return -1;
        }
        return switch (rol.strip().toUpperCase(Locale.ROOT)) {
            case "JUGADOR" -> 0;
            case "MODERADOR" -> 1;
            case "ADMINISTRADOR" -> 2;
            case "SUPER_ADMINISTRADOR" -> 3;
            default -> -1;
        };
    }
}
