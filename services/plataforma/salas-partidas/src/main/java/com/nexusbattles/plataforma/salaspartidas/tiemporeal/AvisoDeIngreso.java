package com.nexusbattles.plataforma.salaspartidas.tiemporeal;

import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;

import java.util.UUID;

/**
 * Mensaje {@code sala.participante.ingreso} del AsyncAPI, tal cual viaja.
 *
 * <p>Calcado de {@code ParticipanteIngreso} en
 * {@code contracts/websocket/salas-partidas.yaml}: cuatro campos, todos
 * obligatorios. Es simetrico con {@code participanteSalio} — lleva el hecho,
 * no la ficha del participante.
 *
 * <p>Aqui NO viaja el heroe ni el apodo. El apodo pertenece al modulo de
 * cuentas y el heroe al de contenido; ninguno de los dos vive en este servicio
 * (regla 7 de plataforma). El roster con esos datos lo pinta la Pantalla 5 con
 * lo que devuelva HU-SAL-003.
 *
 * <p>Vive en {@code tiemporeal} y no en el dominio a proposito: es formato de
 * cable, y el dominio no debe saber como se serializa lo que le pasa.
 */
public record AvisoDeIngreso(String tipo, UUID idSala, UUID idJugador, Ocupacion ocupacion) {

    /** Valor constante del discriminador, fijado por el contrato. */
    public static final String TIPO = "sala.participante.ingreso";

    /**
     * Cuantos hay dentro sobre el maximo, como lo define el esquema Ocupacion.
     *
     * <p>Publico igual que su contenedor: Jackson serializa esto al publicar, y
     * un record de paquete le obliga a forzar el acceso por reflexion.
     */
    public record Ocupacion(int actual, int maximo) {
    }

    static AvisoDeIngreso de(Sala sala, UUID idJugador) {
        return new AvisoDeIngreso(TIPO, sala.id(), idJugador,
                new Ocupacion(sala.ocupacion(), sala.maximoParticipantes()));
    }
}
