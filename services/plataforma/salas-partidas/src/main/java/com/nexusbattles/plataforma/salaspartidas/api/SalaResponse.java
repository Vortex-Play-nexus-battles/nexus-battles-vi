package com.nexusbattles.plataforma.salaspartidas.api;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Respuesta de una sala, calcada del esquema {@code Sala} del contrato OpenAPI.
 *
 * <p>Lleva exactamente lo que la interfaz pinta. La {@code Tarjeta de sala} del
 * sistema de diseno tiene dos propiedades — la variante {@code Estado} y una
 * unica linea de texto — y sus ocho ejemplos de la Pantalla 2 la resuelven como
 * «4 de 6 jugadores · 320 creditos · Con heroe de la IA». De ahi salen
 * {@code estado}, {@code ocupacion}, {@code maximoParticipantes},
 * {@code recompensaCreditos} e {@code incluirHeroeIA}, y nada mas.
 *
 * <p>No viaja el apodo de nadie. El apodo pertenece al modulo de cuentas y este
 * servicio no puede leer su base de datos (regla 7 de plataforma) ni tiene
 * motivo para copiarlo: ninguna pantalla de HU-SAL-002 lo muestra. Un
 * participante es un identificador.
 */
public record SalaResponse(
        UUID id,
        EstadoSala estado,
        Modalidad modalidad,
        int maximoParticipantes,
        int ocupacion,
        int recompensaCreditos,
        boolean incluirHeroeIA,
        boolean privada,
        Integer tamanoEquipo,
        UUID idAnfitrion,
        List<UUID> participantes,
        UUID idPartida,
        Instant creadaEn) {

    static SalaResponse desde(Sala sala) {
        return new SalaResponse(
                sala.id(),
                sala.estado(),
                sala.modalidad(),
                sala.maximoParticipantes(),
                sala.ocupacion(),
                sala.recompensaCreditos(),
                sala.incluirHeroeIA(),
                sala.privada(),
                sala.tamanoEquipo(),
                sala.idAnfitrion(),
                List.copyOf(sala.participantes()),
                null, // la partida no existe hasta que la sala arranca (HU-SAL-004)
                sala.creadaEn());
    }
}
