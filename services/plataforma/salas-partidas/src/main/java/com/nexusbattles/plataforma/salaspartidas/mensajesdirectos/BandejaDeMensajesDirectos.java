package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Lo que un jugador lee de sus mensajes privados — B6 (feedback del profesor).
 *
 * <p><b>Solo lo suyo, siempre.</b> Cada metodo recibe el {@code uid} de quien
 * pregunta, y lo pone el adaptador sacandolo del token. El historial se pide
 * como «mi conversacion con {@code uidOtro}»: no existe una forma de nombrar la
 * conversacion de otros dos, asi que no hay ninguna comprobacion de permisos
 * que olvidar.
 */
public class BandejaDeMensajesDirectos {

    /** {@code limite} del contrato: minimo 1, maximo 100, 50 por omision. */
    public static final int LIMITE_POR_OMISION = 50;
    public static final int LIMITE_MAXIMO = 100;

    private final RepositorioDeMensajesDirectos repositorio;
    private final Clock reloj;
    private final BloqueosDeMensajes bloqueos;

    public BandejaDeMensajesDirectos(RepositorioDeMensajesDirectos repositorio, Clock reloj) {
        this(repositorio, reloj, BloqueosDeMensajes.sinBloqueos());
    }

    /** @param bloqueos para decir en cada conversacion si se puede escribir (D-40) */
    public BandejaDeMensajesDirectos(RepositorioDeMensajesDirectos repositorio, Clock reloj,
                                     BloqueosDeMensajes bloqueos) {
        this.repositorio = Objects.requireNonNull(repositorio);
        this.reloj = Objects.requireNonNull(reloj);
        this.bloqueos = Objects.requireNonNull(bloqueos);
    }

    /**
     * Las conversaciones de ese jugador, la de actividad mas reciente primero.
     *
     * <p>El apodo del otro sale del propio ultimo mensaje (se guardo al
     * escribirlo): ninguna llamada a ms-identidad por fila.
     */
    public List<ResumenDeConversacion> conversacionesDe(UUID jugador) {
        Objects.requireNonNull(jugador, "Hace falta saber de quien son las conversaciones.");
        Map<UUID, Long> noLeidos = repositorio.noLeidosPorRemitente(jugador);
        BloqueosDeMensajes.Vista vista = bloqueos.vistaDe(jugador);
        return repositorio.ultimosPorConversacion(jugador).stream()
                .sorted(Comparator.comparing(MensajeDirecto::enviadoEn)
                        .thenComparing(m -> m.id().toString())
                        .reversed())
                .map(ultimo -> {
                    UUID otro = ultimo.conversacion().otroDe(jugador);
                    return new ResumenDeConversacion(otro, ultimo.apodoDelOtro(jugador), ultimo,
                            noLeidos.getOrDefault(otro, 0L), vista.con(otro));
                })
                .toList();
    }

    /**
     * Una pagina de la conversacion de ese jugador con {@code otro}, del mas
     * antiguo al mas reciente.
     *
     * @param antesDe para cargar hacia atras; {@code null} = los ultimos
     * @param limite  {@code null} = {@value #LIMITE_POR_OMISION}
     * @throws MensajeDirectoRechazado {@code DESTINATARIO_PROPIO} si {@code otro} es el propio jugador
     * @throws ConsultaInvalida        si el limite se sale de 1..100
     */
    public List<MensajeDirecto> historial(UUID jugador, UUID otro, Instant antesDe, Integer limite) {
        Objects.requireNonNull(jugador, "Hace falta saber de quien es la conversacion.");
        Objects.requireNonNull(otro, "Hace falta saber con quien es la conversacion.");
        if (jugador.equals(otro)) {
            throw new MensajeDirectoRechazado(MotivoDeRechazo.DESTINATARIO_PROPIO, null);
        }
        int cuantos = limite == null ? LIMITE_POR_OMISION : limite;
        if (cuantos < 1 || cuantos > LIMITE_MAXIMO) {
            throw new ConsultaInvalida("limite", "El límite va de 1 a " + LIMITE_MAXIMO + " mensajes.");
        }
        return repositorio.historial(Conversacion.entre(jugador, otro), antesDe, cuantos);
    }

    /**
     * Marca como leido lo que {@code otro} le escribio a ese jugador.
     * Idempotente; con uno mismo no hay nada que marcar.
     */
    public void marcarLeida(UUID jugador, UUID otro) {
        Objects.requireNonNull(jugador, "Hace falta saber quien lee.");
        Objects.requireNonNull(otro, "Hace falta saber de quien son los mensajes.");
        if (jugador.equals(otro)) {
            return;
        }
        repositorio.marcarLeidos(jugador, otro, reloj.instant());
    }
}
