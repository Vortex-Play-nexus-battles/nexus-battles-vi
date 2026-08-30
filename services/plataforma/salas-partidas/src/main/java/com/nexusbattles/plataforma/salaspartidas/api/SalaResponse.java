package com.nexusbattles.plataforma.salaspartidas.api;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;

import java.time.Instant;
import java.util.UUID;

/**
 * Respuesta de una sala, calcada del esquema {@code Sala} del contrato OpenAPI.
 *
 * <p>El apodo del anfitrion sale del token, no de una llamada al modulo de
 * identidad: Keycloak ya lo trae en {@code preferred_username}. Una llamada mas
 * por cada sala creada, para un dato que viaja gratis en el token, seria trabajo
 * regalado.
 */
public record SalaResponse(
        UUID id,
        String nombre,
        EstadoSala estado,
        Modalidad modalidad,
        int maximoParticipantes,
        int recompensaCreditos,
        boolean incluirHeroeIA,
        boolean privada,
        Integer tamanoEquipo,
        ResumenJugador anfitrion,
        UUID idPartida,
        Instant creadaEn) {

    /** Subconjunto del jugador que la sala necesita mostrar. */
    public record ResumenJugador(UUID id, String apodo) {
    }

    static SalaResponse desde(Sala sala, String apodoDelAnfitrion) {
        return new SalaResponse(
                sala.id(),
                sala.nombre(),
                sala.estado(),
                sala.modalidad(),
                sala.maximoParticipantes(),
                sala.recompensaCreditos(),
                sala.incluirHeroeIA(),
                sala.privada(),
                sala.tamanoEquipo(),
                new ResumenJugador(sala.idAnfitrion(), apodoDelAnfitrion),
                null, // la partida no existe hasta que la sala se llena
                sala.creadaEn());
    }
}
