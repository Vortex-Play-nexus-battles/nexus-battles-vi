package com.nexusbattles.plataforma.salaspartidas.dominio;

import com.nexusbattles.comun.error.ErrorDeCampo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Sala de batalla. Raiz del dominio de HU-SAL-001.
 *
 * <p>Concentra las reglas de RF-JUE-001 (parametros de creacion), RF-JUE-004
 * (modalidades y limites de participantes) y RF-JUE-014 (creditos comprometidos).
 * Se construye siempre por {@link #crear}: no hay constructor publico, porque una
 * sala que no ha pasado las validaciones no deberia poder existir.
 *
 * <p>La validacion vive aqui y no en el controlador a proposito. Que un duelo sean
 * dos jugadores es una regla del juego: si estuviera en una anotacion del objeto
 * de entrada, entrar por un camino distinto —una cola, una prueba, otro servicio—
 * se la saltaria.
 */
public final class Sala {

    private final UUID id;

    /**
     * Deja de ser final en HU-SAL-002: al ocuparse el ultimo cupo la sala pasa
     * a {@link EstadoSala#LLENA}. Ver la nota sobre mutabilidad en la cabecera
     * de {@link #unirse(UUID)}.
     */
    private EstadoSala estado;

    private final Modalidad modalidad;
    private final int maximoParticipantes;
    private final int recompensaCreditos;
    private final boolean incluirHeroeIA;
    private final boolean privada;
    private final Integer tamanoEquipo;
    private final UUID idAnfitrion;

    /**
     * Quienes estan dentro. <b>Es la unica fuente de verdad del aforo:</b>
     * {@link #ocupacion()} se deriva de su tamano, no de un contador aparte.
     * Asi el numero y las identidades no pueden desmentirse entre si.
     */
    private final Set<UUID> participantes = new LinkedHashSet<>();

    private final Instant creadaEn;

    private Sala(UUID id, EstadoSala estado, Modalidad modalidad,
                 int maximoParticipantes, int recompensaCreditos, boolean incluirHeroeIA,
                 boolean privada, Integer tamanoEquipo, UUID idAnfitrion,
                 Set<UUID> participantes, Instant creadaEn) {
        this.id = id;
        this.estado = estado;
        this.modalidad = modalidad;
        this.maximoParticipantes = maximoParticipantes;
        this.recompensaCreditos = recompensaCreditos;
        this.incluirHeroeIA = incluirHeroeIA;
        this.privada = privada;
        this.tamanoEquipo = tamanoEquipo;
        this.idAnfitrion = idAnfitrion;
        this.creadaEn = creadaEn;

        // El anfitrion entra primero, siempre: es participante desde que la sala
        // existe. Despues, el resto de quienes ya estuvieran dentro.
        if (idAnfitrion != null) {
            this.participantes.add(idAnfitrion);
        }
        if (participantes != null) {
            this.participantes.addAll(participantes);
        }
    }

    /**
     * Reconstruye una sala que ya existia, tal y como estaba almacenada.
     *
     * <p>NO valida: los datos que salen de la base de datos ya pasaron por
     * {@link #crear} el dia que se guardaron. Volver a validarlos haria que un
     * cambio futuro de las reglas dejara ilegibles las salas antiguas.
     *
     * <p>Uso exclusivo de la capa de persistencia.
     */
    public static Sala rehidratar(UUID id, EstadoSala estado, Modalidad modalidad,
                                  int maximoParticipantes, int recompensaCreditos,
                                  boolean incluirHeroeIA, boolean privada, Integer tamanoEquipo,
                                  UUID idAnfitrion, Set<UUID> participantes, Instant creadaEn) {
        return new Sala(id, estado, modalidad, maximoParticipantes, recompensaCreditos,
                incluirHeroeIA, privada, tamanoEquipo, idAnfitrion, participantes, creadaEn);
    }

    /**
     * Crea una sala validada, con el anfitrion dentro.
     *
     * <p>Se acumulan TODOS los errores de parametros antes de rechazar, en vez de
     * parar en el primero: el requisito obliga a decir el motivo, y decir de uno en
     * uno obliga a la persona a enviar el formulario varias veces para enterarse.
     *
     * <p>NO comprueba el saldo. Con reserva atomica, «me alcanza» no es una
     * pregunta que se pueda responder aqui: solo el modulo de creditos puede
     * comprobar y descontar en la misma operacion. Esa parte vive en el caso de
     * uso, contra su puerto.
     *
     * @param parametros  parametros elegidos por el jugador
     * @param idAnfitrion jugador que crea la sala
     * @throws ParametrosInvalidos si algun parametro esta fuera de rango
     */
    public static Sala crear(ParametrosDeSala parametros, UUID idAnfitrion) {
        Objects.requireNonNull(parametros, "Una sala necesita parametros de creacion.");
        Objects.requireNonNull(idAnfitrion, "Una sala necesita un anfitrion.");
        Objects.requireNonNull(parametros.modalidad(), "Una sala necesita una modalidad.");

        List<ErrorDeCampo> errores = new ArrayList<>();
        validarParticipantes(parametros, errores);
        validarEquipo(parametros, errores);
        validarRecompensa(parametros.recompensaCreditos(), errores);

        if (!errores.isEmpty()) {
            throw new ParametrosInvalidos(errores);
        }

        return new Sala(
                UUID.randomUUID(),
                parametros.privada() ? EstadoSala.PRIVADA : EstadoSala.ABIERTA,
                parametros.modalidad(),
                parametros.maximoParticipantes(),
                parametros.recompensaCreditos(),
                parametros.incluirHeroeIA(),
                parametros.privada(),
                parametros.tamanoEquipo(),
                idAnfitrion,
                Set.of(), // al crearla solo esta el anfitrion, que el constructor anade
                Instant.now());
    }

    /** RF-JUE-004: cada modalidad admite un rango distinto de participantes. */
    private static void validarParticipantes(ParametrosDeSala parametros, List<ErrorDeCampo> errores) {
        Modalidad modalidad = parametros.modalidad();
        if (modalidad.admite(parametros.maximoParticipantes())) {
            return;
        }
        String rango = modalidad.minimoParticipantes() == modalidad.maximoParticipantes()
                ? "exactamente " + modalidad.minimoParticipantes()
                : "entre " + modalidad.minimoParticipantes() + " y " + modalidad.maximoParticipantes();
        errores.add(new ErrorDeCampo("maximoParticipantes",
                "Esta modalidad admite " + rango + " jugadores."));
    }

    /** RF-JUE-004: equipos de un maximo de tres integrantes, y solo en cooperativo. */
    private static void validarEquipo(ParametrosDeSala parametros, List<ErrorDeCampo> errores) {
        Integer tamano = parametros.tamanoEquipo();
        if (tamano == null) {
            return;
        }
        if (!parametros.modalidad().admiteEquipos()) {
            errores.add(new ErrorDeCampo("tamanoEquipo",
                    "Solo la modalidad de hasta seis jugadores admite equipos."));
            return;
        }
        if (tamano < 1 || tamano > Modalidad.MAXIMO_POR_EQUIPO) {
            errores.add(new ErrorDeCampo("tamanoEquipo",
                    "Un equipo va de 1 a " + Modalidad.MAXIMO_POR_EQUIPO + " integrantes."));
        }
    }

    /** RF-JUE-014: apostar es libre, incluso cero. Lo que no cabe es deber creditos. */
    private static void validarRecompensa(int recompensa, List<ErrorDeCampo> errores) {
        if (recompensa < 0) {
            errores.add(new ErrorDeCampo("recompensaCreditos",
                    "La recompensa no puede ser negativa."));
        }
    }

    /**
     * Admite a un jugador en la sala — RF-JUE-002, HU-SAL-002.
     *
     * <p><b>Por que esta clase paso a ser mutable.</b> Hasta HU-SAL-001 una sala
     * nacia y no cambiaba, y por eso todos sus campos eran finales. Entrar a una
     * sala SI la cambia: sube el aforo y, al ocuparse el ultimo cupo, cambia el
     * estado. Se modela como agregado mutable en vez de devolver una copia
     * porque el criterio de aceptacion habla de «el estado de la sala», en
     * singular: una sala es una cosa que evoluciona, no una sucesion de valores.
     *
     * <p>Rechaza, todo con 409 (lo fija el contrato):
     * <ul>
     *   <li>sala LLENA, EN_JUEGO, CANCELADA o FINALIZADA;</li>
     *   <li>jugador que ya esta dentro, incluido el anfitrion;</li>
     *   <li>aforo completo aunque el estado no se haya actualizado.</li>
     * </ul>
     *
     * <p><b>Una sala PRIVADA rechaza siempre, de momento.</b> El contrato exige
     * un 403 cuando el codigo de invitacion falta o no vale, pero ese codigo no
     * esta modelado en ninguna parte: ni en {@link ParametrosDeSala}, ni aqui,
     * ni en la migracion de Flyway. Sin forma de demostrar que alguien esta
     * invitado, dejar entrar a cualquiera seria peor que rechazar: convertiria
     * «privada» en una etiqueta decorativa. Se rechaza hasta que exista el flujo
     * real de invitaciones, y no se inventa un codigo para salir del paso.
     *
     * @param idJugador jugador que quiere entrar
     * @throws IngresoNoPermitido si la sala no lo admite
     */
    public void unirse(UUID idJugador) {
        Objects.requireNonNull(idJugador, "Para entrar a una sala hace falta un jugador.");

        // La sala privada tiene su propio rechazo, con 403: el contrato lo separa
        // del 409 porque la interfaz reacciona distinto a cada uno.
        if (estado == EstadoSala.PRIVADA) {
            throw new SalaPrivadaSinInvitacion();
        }
        if (!estadoAdmiteIngreso()) {
            throw new IngresoNoPermitido(motivoDelEstado());
        }
        if (participantes.contains(idJugador)) {
            throw new IngresoNoPermitido("Ya estas en esta sala.");
        }
        if (participantes.size() >= maximoParticipantes) {
            throw new IngresoNoPermitido("La sala ya alcanzo su maximo de participantes.");
        }

        participantes.add(idJugador);

        if (participantes.size() == maximoParticipantes) {
            estado = EstadoSala.LLENA;
        }
    }

    /** Solo ABIERTA admite. PRIVADA queda fuera hasta que exista la invitacion. */
    private boolean estadoAdmiteIngreso() {
        return estado == EstadoSala.ABIERTA;
    }

    /** El motivo se dice en claro: rechazar sin explicar obliga a adivinar. */
    private String motivoDelEstado() {
        return switch (estado) {
            case LLENA -> "La sala ya alcanzo su maximo de participantes.";
            case EN_JUEGO -> "La partida ya comenzo.";
            case CANCELADA -> "El anfitrion cancelo esta sala.";
            case FINALIZADA -> "Esta partida ya termino.";
            case PRIVADA -> "Esta sala es privada: hace falta una invitacion.";
            default -> "Esta sala no admite ingresos.";
        };
    }

    /**
     * Quienes estan dentro, con el anfitrion primero.
     *
     * <p>El orden del resto no se promete: la tabla no lo guarda, porque el
     * modelo no lo necesita. Al anfitrion se le reconoce por
     * {@link #idAnfitrion()}, no por su posicion.
     */
    public Set<UUID> participantes() {
        return Collections.unmodifiableSet(participantes);
    }

    public UUID id() {
        return id;
    }

    public EstadoSala estado() {
        return estado;
    }

    public Modalidad modalidad() {
        return modalidad;
    }

    public int maximoParticipantes() {
        return maximoParticipantes;
    }

    public int recompensaCreditos() {
        return recompensaCreditos;
    }

    public boolean incluirHeroeIA() {
        return incluirHeroeIA;
    }

    public boolean privada() {
        return privada;
    }

    public Integer tamanoEquipo() {
        return tamanoEquipo;
    }

    public UUID idAnfitrion() {
        return idAnfitrion;
    }

    /** Cuantos hay dentro ahora mismo. Al crear la sala, solo el anfitrion. */
    /**
     * Aforo ocupado. <b>Derivado</b> del conjunto de participantes: no existe un
     * contador que pueda quedarse desfasado respecto a quienes estan dentro.
     */
    public int ocupacion() {
        return participantes.size();
    }

    /** Momento de creacion. Viaja en el contrato como {@code creadaEn}. */
    public Instant creadaEn() {
        return creadaEn;
    }
}
