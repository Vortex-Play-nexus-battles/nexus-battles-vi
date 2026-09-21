package com.nexusbattles.plataforma.salaspartidas.dominio;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Estado de la liquidacion de la apuesta de una partida — HU-JUE-014, CA-06.
 *
 * <p><b>Por que se persiste.</b> Cuando la partida termina hay que cobrar las
 * reservas de los perdedores y pagarle al ganador, y eso lo hace otro servicio
 * que puede no contestar en ese instante. Si no contesta, la partida ya
 * termino igual —el ultimo golpe se dio— y no se puede deshacer; lo que no
 * puede pasar es que la deuda se olvide. Esta fila es la memoria de esa deuda:
 * nace {@link Estado#PENDIENTE} cuando el libro falla, y pasa a
 * {@link Estado#LIQUIDADA} cuando un reintento la cierra. Nunca se borra en
 * silencio.
 *
 * <p>No guarda el reparto: se recalcula desde la sala y la partida, que son la
 * verdad, cada vez que se intenta.
 */
public final class LiquidacionDeApuesta {

    /** En que punto esta. */
    public enum Estado {
        /** El libro de creditos no respondio; hay que reintentar. */
        PENDIENTE,
        /** Cobrada y pagada (o devuelta, si hubo empate). */
        LIQUIDADA
    }

    private final UUID idPartida;
    private final UUID idSala;
    private Estado estado;
    private int intentos;
    private String ultimoError;
    private final Instant creadaEn;
    private Instant actualizadaEn;

    private LiquidacionDeApuesta(UUID idPartida, UUID idSala, Estado estado, int intentos,
                                 String ultimoError, Instant creadaEn, Instant actualizadaEn) {
        this.idPartida = Objects.requireNonNull(idPartida, "Una liquidacion es de una partida.");
        this.idSala = Objects.requireNonNull(idSala, "Una liquidacion es de la sala de esa partida.");
        this.estado = Objects.requireNonNull(estado);
        this.intentos = intentos;
        this.ultimoError = ultimoError;
        this.creadaEn = Objects.requireNonNull(creadaEn);
        this.actualizadaEn = Objects.requireNonNull(actualizadaEn);
    }

    /** Primera anotacion, en el momento en que la partida termina. */
    public static LiquidacionDeApuesta nueva(UUID idPartida, UUID idSala, Instant ahora) {
        return new LiquidacionDeApuesta(idPartida, idSala, Estado.PENDIENTE, 0, null, ahora, ahora);
    }

    /** Reconstruye una fila guardada. Solo para la capa de persistencia. */
    public static LiquidacionDeApuesta rehidratar(UUID idPartida, UUID idSala, Estado estado,
                                                  int intentos, String ultimoError,
                                                  Instant creadaEn, Instant actualizadaEn) {
        return new LiquidacionDeApuesta(idPartida, idSala, estado, intentos, ultimoError,
                creadaEn, actualizadaEn);
    }

    /** El libro respondio y todo quedo cobrado o devuelto. */
    public void liquidada(Instant ahora) {
        this.estado = Estado.LIQUIDADA;
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
