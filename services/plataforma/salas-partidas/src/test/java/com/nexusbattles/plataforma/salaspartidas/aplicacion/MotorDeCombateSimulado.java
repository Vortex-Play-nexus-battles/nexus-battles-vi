package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.AccionNoPermitida;
import com.nexusbattles.plataforma.salaspartidas.dominio.CombatienteResuelto;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.EventoDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.InicioDeTurno;
import com.nexusbattles.plataforma.salaspartidas.dominio.MotorDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParticipanteDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.ResolucionDeAccion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Doble del motor de combate con reglas minimas y previsibles, para probar la
 * COORDINACION de salas —que se pregunta, que se guarda, que se anuncia y en
 * que orden— sin las reglas de la seccion 6, que se prueban en el motor.
 *
 * <p>Cada golpe quita {@link #dano}; cuesta {@link #costo} de poder de un
 * maximo de {@link #PODER_MAXIMO}; el objetivo se elige como el motor real (un
 * rival en pie; nunca un companero; si hay varios, hay que decirlo; la maquina
 * elige al de menos vida). Al empezar un turno aplica los sangrados
 * programados en {@link #sangrado} y recupera 2 de poder. Todo lo que se le
 * pide queda anotado.
 */
public final class MotorDeCombateSimulado implements MotorDeCombate {

    public static final int PODER_MAXIMO = 10;

    /** Vida que quita cada golpe. */
    public int dano = 10;
    /**
     * Vida que el objetivo le devuelve a quien lo golpea (Pinchos de escudo,
     * Toma y lleva): el motor lo anota como {@code REFLEJO} sobre el atacante.
     */
    public int reflejo = 0;
    /** Poder que cuesta la accion (0 = basica). */
    public int costo = 0;
    /** Si no es nulo, cualquier accion lo lanza. */
    public RuntimeException falloAlResolver;
    /** Si no es nulo, cualquier comienzo de turno lo lanza. */
    public RuntimeException falloAlEmpezarTurno;
    /** Dano por turno programado: al empezar el turno de la clave, pierde tanta vida. */
    public final Map<UUID, Integer> sangrado = new HashMap<>();
    /** Algo que hacer mientras se resuelve una accion (p. ej. otra escritura a la vez). */
    public Consumer<Partida> mientrasResuelve = partida -> { };

    /** «accion ejecutor->objetivo» de cada accion pedida. */
    public final List<String> acciones = new ArrayList<>();
    /** Quien empezo cada turno pedido, y si era a vida completa. */
    public final List<String> turnos = new ArrayList<>();

    @Override
    public ResolucionDeAccion resolverAccion(String accion, UUID ejecutor, UUID objetivo, Partida partida) {
        acciones.add(accion + " " + ejecutor + "->" + objetivo);
        mientrasResuelve.accept(partida);
        if (falloAlResolver != null) {
            throw falloAlResolver;
        }
        boolean decideLaMaquina = DECISION_DE_LA_MAQUINA.equals(accion);
        ParticipanteDePartida blanco = blanco(partida, ejecutor, objetivo, decideLaMaquina);
        int antes = blanco.heroe().vidaActual();
        int despues = Math.max(0, antes - dano);
        int vidaDelEjecutor = partida.participante(ejecutor).map(p -> p.heroe().vidaActual()).orElse(0);
        int vidaDelEjecutorDespues = Math.max(0, vidaDelEjecutor - reflejo);
        String ejecutada = decideLaMaquina ? ATAQUE_BASICO : accion;

        List<CombatienteResuelto> combatientes = new ArrayList<>();
        for (ParticipanteDePartida p : conReglas(partida)) {
            int vida = p.idJugador().equals(blanco.idJugador()) ? despues
                    : p.idJugador().equals(ejecutor) ? vidaDelEjecutorDespues : p.heroe().vidaActual();
            EstadoDeCombate estado = estadoDe(p);
            if (p.idJugador().equals(ejecutor)) {
                estado = new EstadoDeCombate(Math.max(0, estado.poderActual() - costo), PODER_MAXIMO,
                        estado.turnosJugados() + 1, estado.cargas(), estado.efectos(), estado.ultimoDanoRecibido(),
                        estado.recargas(), estado.acciones(), null);
            }
            combatientes.add(new CombatienteResuelto(p.idJugador(), vida, p.heroe().vidaMaxima(), estado));
        }
        List<ResolucionDeAccion.Afectado> afectados = new ArrayList<>();
        List<EventoDeCombate> eventos = new ArrayList<>();
        if (antes != despues) {
            afectados.add(new ResolucionDeAccion.Afectado(blanco.idJugador(), antes, despues, despues - antes));
            eventos.add(new EventoDeCombate("DANO", blanco.idJugador(), ejecutor, ejecutada, antes - despues));
        }
        if (vidaDelEjecutor != vidaDelEjecutorDespues) {
            afectados.add(new ResolucionDeAccion.Afectado(ejecutor, vidaDelEjecutor, vidaDelEjecutorDespues,
                    vidaDelEjecutorDespues - vidaDelEjecutor));
            eventos.add(new EventoDeCombate("REFLEJO", ejecutor, blanco.idJugador(), "Pinchos de escudo",
                    vidaDelEjecutor - vidaDelEjecutorDespues));
        }
        return new ResolucionDeAccion(ejecutada, ejecutada, false, ejecutor, blanco.idJugador(), "ATAQUE", false,
                false, new ResolucionDeAccion.Golpe(14, 11, true, "CAUSAR_DANO", 3120, 100, dano, antes - despues),
                afectados, eventos, combatientes);
    }

    @Override
    public InicioDeTurno iniciarTurno(UUID combatiente, Partida partida, boolean aVidaCompleta) {
        turnos.add(combatiente + (aVidaCompleta ? " a vida completa" : ""));
        if (falloAlEmpezarTurno != null) {
            throw falloAlEmpezarTurno;
        }
        List<CombatienteResuelto> combatientes = new ArrayList<>();
        List<ResolucionDeAccion.Afectado> afectados = new ArrayList<>();
        List<EventoDeCombate> eventos = new ArrayList<>();
        for (ParticipanteDePartida p : conReglas(partida)) {
            int vida = aVidaCompleta ? p.heroe().vidaMaxima() : p.heroe().vidaActual();
            EstadoDeCombate estado = estadoDe(p);
            if (p.idJugador().equals(combatiente)) {
                Integer perdida = sangrado.get(combatiente);
                if (perdida != null && vida > 0) {
                    int despues = Math.max(0, vida - perdida);
                    afectados.add(new ResolucionDeAccion.Afectado(combatiente, vida, despues, despues - vida));
                    eventos.add(new EventoDeCombate("DANO_POR_TURNO", combatiente, null, "Cierra sangrienta",
                            vida - despues));
                    vida = despues;
                }
                estado = new EstadoDeCombate(Math.min(PODER_MAXIMO, estado.poderActual() + 2), PODER_MAXIMO,
                        estado.turnosJugados(), estado.cargas(), estado.efectos(), estado.ultimoDanoRecibido(),
                        estado.recargas(), estado.acciones(), null);
            }
            combatientes.add(new CombatienteResuelto(p.idJugador(), vida, p.heroe().vidaMaxima(), estado));
        }
        return new InicioDeTurno(combatiente, afectados, eventos, combatientes);
    }

    /** El estado que tenia, o el de un heroe recien sentado: todo su poder y el ataque basico. */
    private static EstadoDeCombate estadoDe(ParticipanteDePartida p) {
        if (p.combate() != null) {
            return p.combate();
        }
        return new EstadoDeCombate(PODER_MAXIMO, PODER_MAXIMO, 0, Map.of(), List.of(), null, Map.of(),
                List.of(new EstadoDeCombate.AccionDisponible(ATAQUE_BASICO, "Ataque basico", "ATAQUE", false, 0,
                        false, 0, 1, true, null)),
                null);
    }

    private static List<ParticipanteDePartida> conReglas(Partida partida) {
        return partida.participantes().stream()
                .filter(p -> p.heroe() != null && p.heroe().prototipo() != null)
                .toList();
    }

    private static ParticipanteDePartida blanco(Partida partida, UUID ejecutor, UUID objetivo,
                                                boolean decideLaMaquina) {
        List<ParticipanteDePartida> rivales = conReglas(partida).stream()
                .filter(ParticipanteDePartida::enPie)
                .filter(p -> !p.idJugador().equals(ejecutor))
                .filter(p -> !partida.sonDelMismoEquipo(ejecutor, p.idJugador()))
                .toList();
        if (objetivo != null) {
            if (partida.sonDelMismoEquipo(ejecutor, objetivo)) {
                throw new AccionNoPermitida("OBJETIVO_INVALIDO",
                        "Es de tu equipo: en el modo cooperativo no se ataca a un compañero.");
            }
            return rivales.stream().filter(p -> p.idJugador().equals(objetivo)).findFirst()
                    .orElseThrow(() -> new AccionNoPermitida("OBJETIVO_INVALIDO",
                            "Ese objetivo no vale para esta acción: tiene que ser un rival en pie."));
        }
        if (rivales.isEmpty()) {
            throw new AccionNoPermitida("OBJETIVO_INVALIDO", "No hay a quién dirigir esta acción.");
        }
        if (decideLaMaquina) {
            return rivales.stream().min(Comparator.comparingInt(p -> p.heroe().vidaActual())).orElseThrow();
        }
        if (rivales.size() > 1) {
            throw new AccionNoPermitida("OBJETIVO_REQUERIDO", "Hay más de un objetivo posible: indica a quién.");
        }
        return rivales.get(0);
    }
}
