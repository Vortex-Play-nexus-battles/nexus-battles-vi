package com.nexusbattles.plataforma.salaspartidas.tiemporeal;

import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;

import java.util.UUID;

/**
 * Mensaje {@code sala.participante.salio} del AsyncAPI, tal cual viaja.
 *
 * <p>Calcado de {@code ParticipanteSalio} en
 * {@code contracts/websocket/salas-partidas.yaml}, y simetrico de
 * {@link AvisoDeIngreso}: los mismos cuatro campos, con el discriminador
 * cambiado. Son simetricos a proposito — la vista que pinta el aforo aplica la
 * misma logica en los dos sentidos y no necesita dos formas de leer lo mismo.
 */
public record AvisoDeSalida(String tipo, UUID idSala, UUID idJugador,
                            AvisoDeIngreso.Ocupacion ocupacion) {

    /** Valor constante del discriminador, fijado por el contrato. */
    public static final String TIPO = "sala.participante.salio";

    static AvisoDeSalida de(Sala sala, UUID idJugador) {
        return new AvisoDeSalida(TIPO, sala.id(), idJugador,
                new AvisoDeIngreso.Ocupacion(sala.ocupacion(), sala.maximoParticipantes()));
    }
}
