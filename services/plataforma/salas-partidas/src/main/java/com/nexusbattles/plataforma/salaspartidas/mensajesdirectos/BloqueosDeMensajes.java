package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Bloquear a un jugador en los mensajes privados — auditoria de DEV del
 * 30-sep («Bloquear jugador» respondia «No pudimos bloquear»: la interfaz lo
 * ofrecia y el servicio no lo tenia, por una decision del PO pendiente).
 *
 * <p><b>Reglas provisionales (D-40).</b> Son las que la interfaz ya prometia
 * al confirmar el bloqueo (UXC-6): «Dejaras de recibir sus mensajes y no podra
 * escribirte. Puedes desbloquearlo cuando quieras».
 * <ul>
 *   <li>Mientras dure, ninguno de los dos se escribe por privado: quien
 *       bloqueo ve la conversacion {@link EstadoDeConversacion#BLOQUEADA} y
 *       quien fue bloqueado {@link EstadoDeConversacion#NO_ADMITE}, sin que se
 *       le diga por que.</li>
 *   <li>El historial se queda para los dos: bloquear no borra nada.</li>
 *   <li>Solo afecta a los mensajes privados: ni al chat general ni al de sala
 *       ni a las partidas.</li>
 *   <li>Desbloquear lo deshace del todo. Las dos operaciones son
 *       idempotentes.</li>
 * </ul>
 */
public class BloqueosDeMensajes {

    private final RepositorioDeBloqueos repositorio;
    private final Clock reloj;

    public BloqueosDeMensajes(RepositorioDeBloqueos repositorio, Clock reloj) {
        this.repositorio = Objects.requireNonNull(repositorio);
        this.reloj = Objects.requireNonNull(reloj);
    }

    /** Sin almacen: nadie bloquea a nadie (pruebas y cableados anteriores a D-40). */
    public static BloqueosDeMensajes sinBloqueos() {
        return new BloqueosDeMensajes(NINGUNO, Clock.systemUTC());
    }

    /** Si {@code yo} puede escribirle a {@code otro}, y si no, por que. */
    public EstadoDeConversacion estado(UUID yo, UUID otro) {
        Objects.requireNonNull(yo, "Hace falta saber quien pregunta.");
        Objects.requireNonNull(otro, "Hace falta saber con quien es la conversacion.");
        if (repositorio.bloqueo(yo, otro)) {
            return EstadoDeConversacion.BLOQUEADA;
        }
        if (repositorio.bloqueo(otro, yo)) {
            return EstadoDeConversacion.NO_ADMITE;
        }
        return EstadoDeConversacion.ACTIVA;
    }

    /**
     * @return el estado de la conversacion para {@code quien} despues de bloquear
     * @throws MensajeDirectoRechazado {@code DESTINATARIO_PROPIO} si se bloquea a si mismo
     */
    public EstadoDeConversacion bloquear(UUID quien, UUID aQuien) {
        validar(quien, aQuien);
        repositorio.bloquear(quien, aQuien, reloj.instant());
        return estado(quien, aQuien);
    }

    /**
     * @return el estado de la conversacion para {@code quien} despues de
     *     desbloquear: {@code ACTIVA}, o {@code NO_ADMITE} si el otro tambien
     *     lo tiene bloqueado a el
     */
    public EstadoDeConversacion desbloquear(UUID quien, UUID aQuien) {
        validar(quien, aQuien);
        repositorio.desbloquear(quien, aQuien);
        return estado(quien, aQuien);
    }

    /** El estado de todas las conversaciones de un jugador, en dos consultas. */
    public Vista vistaDe(UUID jugador) {
        Objects.requireNonNull(jugador, "Hace falta saber de quien son las conversaciones.");
        return new Vista(repositorio.bloqueadosPor(jugador), repositorio.quienesBloquearonA(jugador));
    }

    /** Lo que un jugador bloqueo y quien lo bloqueo, para pintar su lista. */
    public record Vista(Set<UUID> bloqueados, Set<UUID> leBloquearon) {

        public Vista {
            bloqueados = Set.copyOf(bloqueados);
            leBloquearon = Set.copyOf(leBloquearon);
        }

        public EstadoDeConversacion con(UUID otro) {
            if (bloqueados.contains(otro)) {
                return EstadoDeConversacion.BLOQUEADA;
            }
            return leBloquearon.contains(otro) ? EstadoDeConversacion.NO_ADMITE : EstadoDeConversacion.ACTIVA;
        }
    }

    private static void validar(UUID quien, UUID aQuien) {
        Objects.requireNonNull(quien, "Hace falta saber quien bloquea.");
        Objects.requireNonNull(aQuien, "Hace falta saber a quien.");
        if (quien.equals(aQuien)) {
            throw new MensajeDirectoRechazado(MotivoDeRechazo.DESTINATARIO_PROPIO, null);
        }
    }

    private static final RepositorioDeBloqueos NINGUNO = new RepositorioDeBloqueos() {
        @Override
        public boolean bloquear(UUID quien, UUID aQuien, Instant cuando) {
            throw new UnsupportedOperationException("Este cableado no guarda bloqueos.");
        }

        @Override
        public boolean desbloquear(UUID quien, UUID aQuien) {
            return false;
        }

        @Override
        public boolean bloqueo(UUID quien, UUID aQuien) {
            return false;
        }

        @Override
        public Set<UUID> bloqueadosPor(UUID quien) {
            return Set.of();
        }

        @Override
        public Set<UUID> quienesBloquearonA(UUID jugador) {
            return Set.of();
        }
    };
}
