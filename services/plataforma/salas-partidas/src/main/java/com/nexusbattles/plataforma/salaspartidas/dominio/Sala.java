package com.nexusbattles.plataforma.salaspartidas.dominio;

import com.nexusbattles.comun.error.ErrorDeCampo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
    private final String nombre;
    private final EstadoSala estado;
    private final Modalidad modalidad;
    private final int maximoParticipantes;
    private final int recompensaCreditos;
    private final boolean incluirHeroeIA;
    private final boolean privada;
    private final Integer tamanoEquipo;
    private final UUID idAnfitrion;
    private final int ocupacion;
    private final Instant creadaEn;

    private Sala(UUID id, String nombre, EstadoSala estado, Modalidad modalidad,
                 int maximoParticipantes, int recompensaCreditos, boolean incluirHeroeIA,
                 boolean privada, Integer tamanoEquipo, UUID idAnfitrion, int ocupacion,
                 Instant creadaEn) {
        this.id = id;
        this.nombre = nombre;
        this.estado = estado;
        this.modalidad = modalidad;
        this.maximoParticipantes = maximoParticipantes;
        this.recompensaCreditos = recompensaCreditos;
        this.incluirHeroeIA = incluirHeroeIA;
        this.privada = privada;
        this.tamanoEquipo = tamanoEquipo;
        this.idAnfitrion = idAnfitrion;
        this.ocupacion = ocupacion;
        this.creadaEn = creadaEn;
    }

    /**
     * Reconstruye una sala que ya existia, tal y como estaba almacenada.
     *
     * <p>NO valida: los datos que salen de la base de datos ya pasaron por
     * {@link #crear} el dia que se guardaron. Volver a validarlos haria que un
     * cambio futuro de las reglas dejara ilegibles las salas antiguas.
     *
     * <p>Uso exclusivo de la capa de persistencia. Ningun caso de uso deberia
     * llamarla: para crear una sala esta {@link #crear}.
     */
    public static Sala rehidratar(UUID id, String nombre, EstadoSala estado, Modalidad modalidad,
                                  int maximoParticipantes, int recompensaCreditos,
                                  boolean incluirHeroeIA, boolean privada, Integer tamanoEquipo,
                                  UUID idAnfitrion, int ocupacion, Instant creadaEn) {
        return new Sala(id, nombre, estado, modalidad, maximoParticipantes, recompensaCreditos,
                incluirHeroeIA, privada, tamanoEquipo, idAnfitrion, ocupacion, creadaEn);
    }

    /**
     * Crea una sala validada, con el anfitrion dentro y sus creditos comprometidos.
     *
     * <p>Se acumulan TODOS los errores de parametros antes de rechazar, en vez de
     * parar en el primero: el requisito obliga a decir el motivo, y decir de uno en
     * uno obliga a la persona a enviar el formulario cinco veces para enterarse.
     *
     * <p>NO comprueba el saldo. Con reserva atomica, «me alcanza» no es una
     * pregunta que se pueda responder aqui: solo el modulo de creditos puede
     * comprobar y descontar en la misma operacion, y cualquier comprobacion
     * previa quedaria obsoleta en el instante siguiente. Esa parte vive en el
     * caso de uso, contra su puerto.
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
        validarNombre(parametros.nombre(), errores);
        validarParticipantes(parametros, errores);
        validarEquipo(parametros, errores);
        validarRecompensa(parametros.recompensaCreditos(), errores);

        if (!errores.isEmpty()) {
            throw new ParametrosInvalidos(errores);
        }

        return new Sala(
                UUID.randomUUID(),
                parametros.nombre().strip(),
                parametros.privada() ? EstadoSala.PRIVADA : EstadoSala.ABIERTA,
                parametros.modalidad(),
                parametros.maximoParticipantes(),
                parametros.recompensaCreditos(),
                parametros.incluirHeroeIA(),
                parametros.privada(),
                parametros.tamanoEquipo(),
                idAnfitrion,
                1, // al crearla solo esta el anfitrion
                Instant.now());
    }

    private static void validarNombre(String nombre, List<ErrorDeCampo> errores) {
        if (nombre == null || nombre.isBlank()) {
            errores.add(new ErrorDeCampo("nombre", "Ponle un nombre a la sala."));
            return;
        }
        int longitud = nombre.strip().length();
        if (longitud < ParametrosDeSala.NOMBRE_MINIMO || longitud > ParametrosDeSala.NOMBRE_MAXIMO) {
            errores.add(new ErrorDeCampo("nombre",
                    "El nombre va de " + ParametrosDeSala.NOMBRE_MINIMO + " a "
                            + ParametrosDeSala.NOMBRE_MAXIMO + " caracteres."));
        }
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

    public UUID id() {
        return id;
    }

    public String nombre() {
        return nombre;
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
    public int ocupacion() {
        return ocupacion;
    }

    /** Momento de creacion. Viaja en el contrato como {@code creadaEn}. */
    public Instant creadaEn() {
        return creadaEn;
    }
}
