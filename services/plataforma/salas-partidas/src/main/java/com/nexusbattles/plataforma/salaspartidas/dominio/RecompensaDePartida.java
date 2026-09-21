package com.nexusbattles.plataforma.salaspartidas.dominio;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Estado de la recompensa por jugar de una partida — HU-JUE-012, CA-05.
 *
 * <p>Misma razon de ser que {@link LiquidacionDeApuesta}: al terminar la
 * partida hay que informar el resultado al libro de creditos para que
 * acredite 2/4/1, y el libro puede no contestar en ese instante. La partida
 * ya termino y no se deshace; lo que no puede pasar es que la recompensa se
 * olvide. Esta fila es la memoria de esa deuda: nace {@link Estado#PENDIENTE}
 * cuando el libro falla y pasa a {@link Estado#ACREDITADA} cuando entra —a
 * la primera o en un reintento—. Nunca se borra en silencio.
 *
 * <p>Se lleva aparte de la liquidacion de la apuesta a proposito (CA-03): son
 * dos movimientos distintos en el libro y en el historial del jugador, y uno
 * puede quedar pendiente sin el otro.
 *
 * <p>No guarda lo acreditado: el libro es la verdad de eso y responde
 * {@code 409 partida-ya-procesada} si se le vuelve a preguntar; ese 409 se
 * toma como «ya esta hecho», no como fallo.
 */
public final class RecompensaDePartida {

    /** En que punto esta. */
    public enum Estado {
        /** El libro de creditos no respondio; hay que reintentar. */
        PENDIENTE,
        /** El libro acredito la recompensa (o ya la tenia acreditada). */
        ACREDITADA
    }

    private final UUID idPartida;
    private final UUID idSala;
    private Estado estado;
    private int intentos;
    private String ultimoError;
    private final Instant creadaEn;
    private Instant actualizadaEn;

    private RecompensaDePartida(UUID idPartida, UUID idSala, Estado estado, int intentos,
                                String ultimoError, Instant creadaEn, Instant actualizadaEn) {
        this.idPartida = Objects.requireNonNull(idPartida, "Una recompensa es de una partida.");
        this.idSala = Objects.requireNonNull(idSala, "Una recompensa es de la sala de esa partida.");
        this.estado = Objects.requireNonNull(estado);
        this.intentos = intentos;
        this.ultimoError = ultimoError;
        this.creadaEn = Objects.requireNonNull(creadaEn);
        this.actualizadaEn = Objects.requireNonNull(actualizadaEn);
    }

    /** Primera anotacion, en el momento en que la partida termina. */
    public static RecompensaDePartida nueva(UUID idPartida, UUID idSala, Instant ahora) {
        return new RecompensaDePartida(idPartida, idSala, Estado.PENDIENTE, 0, null, ahora, ahora);
    }

    /** Reconstruye una fila guardada. Solo para la capa de persistencia. */
    public static RecompensaDePartida rehidratar(UUID idPartida, UUID idSala, Estado estado,
                                                 int intentos, String ultimoError,
                                                 Instant creadaEn, Instant actualizadaEn) {
        return new RecompensaDePartida(idPartida, idSala, estado, intentos, ultimoError,
                creadaEn, actualizadaEn);
    }

    /** El libro acredito (o confirmo que ya lo habia hecho). */
    public void acreditada(Instant ahora) {
        this.estado = Estado.ACREDITADA;
        this.intentos++;
        this.ultimoError = null;
        this.actualizadaEn = ahora;
    }

    /** El libro no respondio: se anota el motivo y se deja para reintentar. */
    public void fallo(String motivo, Instant ahora) {
        this.estado = Estado.PENDIENTE;
        this.intentos++;
        this.ultimoError = motivo == null ? "" : motivo.substring(0, Math.min(motivo.length(), 500));
        this.actualizadaEn = ahora;
    }

    public UUID idPartida() {
        return idPartida;
    }

    public UUID idSala() {
        return idSala;
    }

    public Estado estado() {
        return estado;
    }

    public int intentos() {
        return intentos;
    }

    public String ultimoError() {
        return ultimoError;
    }

    public Instant creadaEn() {
        return creadaEn;
    }

    public Instant actualizadaEn() {
        return actualizadaEn;
    }
}
