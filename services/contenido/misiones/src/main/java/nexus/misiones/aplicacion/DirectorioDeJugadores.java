package nexus.misiones.aplicacion;

import java.util.Optional;

/**
 * El correo y el apodo de un jugador por su {@code uid}
 * ({@code GET /api/v1/internal/usuarios/{uid}/contacto}, ms-identidad-admin).
 * Misiones no guarda copias del correo de nadie (regla 7 y minimizacion de
 * datos personales): lo pide justo antes de escribirle.
 */
public interface DirectorioDeJugadores {

    /** Vacio si identidad no conoce a ese jugador. */
    Optional<Contacto> contacto(String jugadorUid);

    record Contacto(String email, String apodo) {
    }
}
