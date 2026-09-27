package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos;

import java.util.Objects;
import java.util.UUID;

/**
 * La conversacion privada entre dos jugadores — B6.
 *
 * <p>FEEDBACK DEL PROFESOR, no requisito del documento: la seccion 7.6 pide
 * chat en las salas y en la vista general; los mensajes privados se piden en
 * la demostracion y se montan sobre el mismo chat.
 *
 * <p><b>Una sola por pareja.</b> Su clave es {@code dm:{uidMenor}:{uidMayor}}
 * ({@code contracts/websocket/mensajes-directos.yaml}): escriba quien escriba,
 * los dos caen en la misma. El orden es el lexicografico de la forma canonica
 * del UUID (minusculas con guiones), no {@link UUID#compareTo}, que compara
 * con signo y daria otro orden que ningun cliente sabria reproducir.
 *
 * @param menor el uid que va primero en la clave
 * @param mayor el que va segundo
 */
public record Conversacion(UUID menor, UUID mayor) {

    private static final String PREFIJO = "dm:";

    public Conversacion {
        Objects.requireNonNull(menor, "Una conversacion es entre dos jugadores.");
        Objects.requireNonNull(mayor, "Una conversacion es entre dos jugadores.");
        if (menor.equals(mayor)) {
            throw new IllegalArgumentException("Nadie tiene una conversacion privada consigo mismo.");
        }
        if (menor.toString().compareTo(mayor.toString()) > 0) {
            throw new IllegalArgumentException("La conversacion se construye con Conversacion.entre(...).");
        }
    }

    /** La conversacion de esos dos, en el orden que sea. */
    public static Conversacion entre(UUID uno, UUID otro) {
        Objects.requireNonNull(uno, "Una conversacion es entre dos jugadores.");
        Objects.requireNonNull(otro, "Una conversacion es entre dos jugadores.");
        return uno.toString().compareTo(otro.toString()) <= 0
                ? new Conversacion(uno, otro)
                : new Conversacion(otro, uno);
    }

    /** {@code dm:{uidMenor}:{uidMayor}}, la forma en que viaja y se guarda. */
    public String clave() {
        return PREFIJO + menor + ":" + mayor;
    }

    /** Si ese jugador es uno de los dos. */
    public boolean incluye(UUID jugador) {
        return menor.equals(jugador) || mayor.equals(jugador);
    }

    /**
     * El otro participante, visto desde uno de los dos.
     *
     * @throws IllegalArgumentException si quien pregunta no es de la conversacion
     */
    public UUID otroDe(UUID jugador) {
        if (menor.equals(jugador)) {
            return mayor;
        }
        if (mayor.equals(jugador)) {
            return menor;
        }
        throw new IllegalArgumentException("Ese jugador no es de esta conversacion.");
    }

    /**
     * Reconstruye la conversacion desde su clave guardada.
     *
     * @throws IllegalArgumentException si la clave no tiene la forma del contrato
     */
    public static Conversacion desdeClave(String clave) {
        if (clave == null || !clave.startsWith(PREFIJO)) {
            throw new IllegalArgumentException("Clave de conversacion sin el prefijo dm:.");
        }
        String[] partes = clave.substring(PREFIJO.length()).split(":");
        if (partes.length != 2) {
            throw new IllegalArgumentException("Clave de conversacion sin sus dos participantes.");
        }
        return new Conversacion(UUID.fromString(partes[0]), UUID.fromString(partes[1]));
    }
}
