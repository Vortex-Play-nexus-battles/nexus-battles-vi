package nexus.misiones.dominio.simulacion;

import java.util.List;
import java.util.UUID;

/**
 * Un turno de combate de una mision simulada, tal como lo resolvio el motor
 * (HU-SIM-003, «registra los eventos de combate»). Es el dato que consume el
 * modelo de IA de HU-SIM-008: por eso es legible y estable —nombres de campo
 * en espanol, valores de los enums del motor tal cual— y cada evento se basta
 * solo, sin tener que reconstruir lo anterior.
 *
 * <p>Hay un evento por TURNO DE UN COMBATIENTE (no por ronda): el del heroe y
 * el del enemigo de una misma ronda son dos eventos, con el mismo
 * {@code turno}. {@code secuencia} los ordena dentro de la ejecucion, desde 1.
 *
 * @param ejecucionId  la ejecucion a la que pertenece; es la clave de busqueda
 * @param misionId     la mision simulada
 * @param secuencia    orden dentro de la ejecucion, desde 1 y sin saltos
 * @param encuentro    numero del encuentro dentro de la mision, desde 1 (el jefe va al final)
 * @param enemigo      nombre del enemigo de ese encuentro
 * @param turno        ronda dentro del encuentro, desde 1
 * @param actor        quien juega
 * @param oponente     contra quien: el otro combatiente del encuentro (HU-SIM-008; sin este dato un turno no dice
 *                     contra que prototipo se jugo si el rival cae antes de actuar)
 * @param antes       el estado de los dos al DECIDIR, es decir despues de aplicar el inicio del turno
 * @param alIniciar    lo que paso al empezar el turno: efectos por turno, poder recuperado...
 * @param jugada       lo que hizo; nula si cayo al empezar su turno (un sangrado)
 * @param despues      el estado de los dos al terminar el turno
 */
public record EventoDeCombate(
        UUID ejecucionId,
        String misionId,
        int secuencia,
        int encuentro,
        String enemigo,
        int turno,
        Actor actor,
        Actor oponente,
        Estados antes,
        List<Suceso> alIniciar,
        Jugada jugada,
        Estados despues) {

    public EventoDeCombate {
        alIniciar = alIniciar == null ? List.of() : List.copyOf(alIniciar);
    }

    /** Quien juega: el heroe, un enemigo regular, un Master o el jefe final (seccion 7.8.3). */
    public enum Lado {
        HEROE,
        ENEMIGO,
        JEFE,
        MASTER
    }

    public record Actor(Lado lado, String nombre, String prototipo, int nivel) {
    }

    /** El estado de los dos combatientes del encuentro en un instante. */
    public record Estados(EstadoDeCombatiente actor, EstadoDeCombatiente oponente) {
    }

    /**
     * @param recargas por accion en carga, los turnos propios que le faltan
     * @param efectos  los efectos activos
     */
    public record EstadoDeCombatiente(int vida, int vidaMaxima, int poder, int poderMaximo,
                                      List<Recarga> recargas, List<EfectoVigente> efectos) {

        public EstadoDeCombatiente {
            recargas = recargas == null ? List.of() : List.copyOf(recargas);
            efectos = efectos == null ? List.of() : List.copyOf(efectos);
        }
    }

    public record Recarga(String accion, int turnosRestantes) {
    }

    public record EfectoVigente(String nombre, String tipo, int valor, int turnos) {
    }

    /**
     * Lo que hizo el actor.
     *
     * @param decidida     la accion que eligio la IA (nombre de la Tabla 7 o «Ataque básico»)
     * @param ejecutada    la que de verdad se jugo; distinta de la decidida si faltaba poder
     * @param enValorBase  faltaba poder y el turno se jugo como ataque basico en valor base (6.1.1)
     * @param costoDecidido lo que costaba la accion decidida, segun heroes
     * @param costoDePoder el poder que de verdad gasto, segun el estado que devolvio el motor
     * @param rechazadas   las opciones que el motor rechazo antes de aceptar esta, en orden
     * @param resultado    lo que resolvio el motor
     * @param decididaPor  quien tomo la decision: la regla de heroes o el modelo propio (HU-SIM-008)
     * @param versionDelModelo la version del modelo que se consulto en este turno; nula si no se consulto
     * @param candidatas   las opciones legales que puntuo el modelo (sirven para reentrenarlo); vacia si no
     *                     se consulto
     */
    public record Jugada(String decidida, String ejecutada, boolean enValorBase, int costoDecidido, int costoDePoder,
                         List<Rechazo> rechazadas, Resultado resultado, DecididaPor decididaPor,
                         String versionDelModelo, List<DecisionDeTurno.Candidata> candidatas) {

        public Jugada {
            rechazadas = rechazadas == null ? List.of() : List.copyOf(rechazadas);
            decididaPor = decididaPor == null ? DecididaPor.REGLA : decididaPor;
            candidatas = candidatas == null ? List.of() : List.copyOf(candidatas);
        }

        /** Una jugada que decidio la regla sola. */
        public Jugada(String decidida, String ejecutada, boolean enValorBase, int costoDecidido, int costoDePoder,
                      List<Rechazo> rechazadas, Resultado resultado) {
            this(decidida, ejecutada, enValorBase, costoDecidido, costoDePoder, rechazadas, resultado,
                    DecididaPor.REGLA, null, List.of());
        }
    }

    /** Una opcion de la rotacion que el motor no admitio (409), con su motivo (EN_CARGA...). */
    public record Rechazo(String accion, String motivo) {
    }

    /**
     * @param categoria nula si la accion no golpea; si no, la del motor (CAUSAR_DANO, SIN_EFECTO...)
     * @param acierta   nulo si la accion no golpea
     */
    public record Resultado(String categoria, Boolean acierta, Integer ataqueResuelto, Integer defensaObjetivo,
                            Integer porcentajeDano, Integer danoBase, Integer danoAplicado, boolean critico,
                            List<Suceso> sucesos) {

        public Resultado {
            sucesos = sucesos == null ? List.of() : List.copyOf(sucesos);
        }
    }
}
