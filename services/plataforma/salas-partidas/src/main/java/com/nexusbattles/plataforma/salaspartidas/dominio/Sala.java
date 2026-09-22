package com.nexusbattles.plataforma.salaspartidas.dominio;

import com.nexusbattles.comun.error.ErrorDeCampo;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
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

    /**
     * Alfabeto del codigo de invitacion: 32 caracteres sin los cuatro que se
     * confunden entre si al leerlos o dictarlos ({@code I}, {@code O},
     * {@code 0}, {@code 1}). Un codigo que hay que deletrear dos veces no
     * cumple su funcion.
     */
    private static final String ALFABETO_DEL_CODIGO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    /** Caracteres significativos del codigo, sin contar el guion separador. */
    private static final int LONGITUD_DEL_CODIGO = 8;

    private static final SecureRandom AZAR = new SecureRandom();

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

    /**
     * Cupos de la maquina — HU-SAL-004. <b>Cuentan en el aforo:</b> una sala
     * contra la IA con dos cupos esta llena con el anfitrion solo, y en una de
     * seis con dos maquinas caben cuatro personas. Sin esto, la partida tendria
     * mas combatientes que el maximo elegido.
     */
    private final int heroesIA;
    private final boolean privada;
    private final Integer tamanoEquipo;
    private final UUID idAnfitrion;

    /**
     * Quienes estan dentro. <b>Es la unica fuente de verdad del aforo:</b>
     * {@link #ocupacion()} se deriva de su tamano, no de un contador aparte.
     * Asi el numero y las identidades no pueden desmentirse entre si.
     */
    private final Map<UUID, FichaDeParticipante> participantes = new LinkedHashMap<>();

    private final Instant creadaEn;

    /**
     * Codigo de invitacion de una sala privada. {@code null} en las publicas.
     *
     * <p>Lo genera el servidor al crear la sala, no lo elige el anfitrion: un
     * codigo escogido a mano seria corto, memorable y adivinable, que es justo
     * lo que no queremos de una llave de acceso. Ver
     * {@link #generarCodigoDeInvitacion()}.
     *
     * <p>Solo se le devuelve al anfitrion (lo filtra la capa de API). Al resto
     * de participantes les llega la sala sin el: quien ya entro no necesita la
     * llave, y darsela convertiria a cualquier invitado en repartidor de
     * invitaciones.
     */
    private final String codigoInvitacion;

    /**
     * Reserva de creditos ligada a esta sala — RF-JUE-014. {@code null} cuando
     * la sala no compromete creditos.
     *
     * <p>Se guarda porque cancelar la sala tiene que devolverlos, y para
     * devolverlos hay que saber que reserva liberar. Sin este dato, cancelar
     * dejaria los creditos del anfitrion retenidos para siempre — que es
     * exactamente lo contrario de lo que promete {@link EstadoSala#CANCELADA}.
     */
    private final UUID idReservaCreditos;

    /**
     * Marca de concurrencia — HU-SAL-002.
     *
     * <p>El dominio no razona con ella: la lleva tal cual de la lectura a la
     * escritura para que la persistencia detecte si otro ingreso se guardo en
     * medio (bloqueo optimista). Una sala recien creada nace en 0; la base la
     * incrementa en cada escritura. Sin esto, dos jugadores que compiten por el
     * ultimo cupo se pisan la escritura y uno de los dos «entra» solo en su
     * pantalla.
     */
    private final long version;

    private Sala(UUID id, EstadoSala estado, Modalidad modalidad,
                 int maximoParticipantes, int recompensaCreditos, int heroesIA,
                 boolean privada, Integer tamanoEquipo, UUID idAnfitrion,
                 Map<UUID, FichaDeParticipante> participantes, Instant creadaEn, long version,
                 String codigoInvitacion, UUID idReservaCreditos) {
        this.id = id;
        this.estado = estado;
        this.modalidad = modalidad;
        this.maximoParticipantes = maximoParticipantes;
        this.recompensaCreditos = recompensaCreditos;
        this.heroesIA = heroesIA;
        this.privada = privada;
        this.tamanoEquipo = tamanoEquipo;
        this.idAnfitrion = idAnfitrion;
        this.creadaEn = creadaEn;
        this.version = version;
        this.codigoInvitacion = codigoInvitacion;
        this.idReservaCreditos = idReservaCreditos;

        // El anfitrion entra primero, siempre: es participante desde que la sala
        // existe. Despues, el resto de quienes ya estuvieran dentro.
        if (idAnfitrion != null) {
            this.participantes.put(idAnfitrion, null);
        }
        if (participantes != null) {
            // putAll y no un bucle con putIfAbsent: si la fila del anfitrion
            // trae ficha, esa es la buena y tiene que pisar al null de arriba.
            this.participantes.putAll(participantes);
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
                                  int heroesIA, boolean privada, Integer tamanoEquipo,
                                  UUID idAnfitrion,
                                  Map<UUID, FichaDeParticipante> participantes, Instant creadaEn,
                                  long version, String codigoInvitacion, UUID idReservaCreditos) {
        return new Sala(id, estado, modalidad, maximoParticipantes, recompensaCreditos,
                heroesIA, privada, tamanoEquipo, idAnfitrion, participantes, creadaEn,
                version, codigoInvitacion, idReservaCreditos);
    }

    /** Con el booleano de antes de HU-SAL-004: una maquina o ninguna. */
    public static Sala rehidratar(UUID id, EstadoSala estado, Modalidad modalidad,
                                  int maximoParticipantes, int recompensaCreditos,
                                  boolean incluirHeroeIA, boolean privada, Integer tamanoEquipo,
                                  UUID idAnfitrion,
                                  Map<UUID, FichaDeParticipante> participantes, Instant creadaEn,
                                  long version, String codigoInvitacion, UUID idReservaCreditos) {
        return rehidratar(id, estado, modalidad, maximoParticipantes, recompensaCreditos,
                incluirHeroeIA ? 1 : 0, privada, tamanoEquipo, idAnfitrion, participantes,
                creadaEn, version, codigoInvitacion, idReservaCreditos);
    }

    /**
     * Variante con solo los identificadores, sin ficha.
     *
     * <p>La usan las filas anteriores a la migracion V7 y los dobles de prueba
     * que solo ejercitan reglas de aforo. Las fichas quedan nulas, que es la
     * verdad: de esos participantes no se sabe con que heroe entraron.
     */
    public static Sala rehidratar(UUID id, EstadoSala estado, Modalidad modalidad,
                                  int maximoParticipantes, int recompensaCreditos,
                                  boolean incluirHeroeIA, boolean privada, Integer tamanoEquipo,
                                  UUID idAnfitrion, Set<UUID> participantes, Instant creadaEn,
                                  long version, String codigoInvitacion, UUID idReservaCreditos) {
        Map<UUID, FichaDeParticipante> sinFicha = new LinkedHashMap<>();
        if (participantes != null) {
            for (UUID jugador : participantes) {
                sinFicha.put(jugador, null);
            }
        }
        return rehidratar(id, estado, modalidad, maximoParticipantes, recompensaCreditos,
                incluirHeroeIA, privada, tamanoEquipo, idAnfitrion, sinFicha, creadaEn,
                version, codigoInvitacion, idReservaCreditos);
    }

    /**
     * Variante sin codigo de invitacion ni reserva, para salas publicas y para
     * los dobles de prueba anteriores a ambos campos.
     */
    public static Sala rehidratar(UUID id, EstadoSala estado, Modalidad modalidad,
                                  int maximoParticipantes, int recompensaCreditos,
                                  boolean incluirHeroeIA, boolean privada, Integer tamanoEquipo,
                                  UUID idAnfitrion, Set<UUID> participantes, Instant creadaEn,
                                  long version) {
        return rehidratar(id, estado, modalidad, maximoParticipantes, recompensaCreditos,
                incluirHeroeIA, privada, tamanoEquipo, idAnfitrion, participantes, creadaEn,
                version, null, null);
    }

    /** Variante sin marca de concurrencia, para dobles y fixtures que no persisten. */
    public static Sala rehidratar(UUID id, EstadoSala estado, Modalidad modalidad,
                                  int maximoParticipantes, int recompensaCreditos,
                                  boolean incluirHeroeIA, boolean privada, Integer tamanoEquipo,
                                  UUID idAnfitrion, Set<UUID> participantes, Instant creadaEn) {
        return rehidratar(id, estado, modalidad, maximoParticipantes, recompensaCreditos,
                incluirHeroeIA, privada, tamanoEquipo, idAnfitrion, participantes, creadaEn, 0L);
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
        return crear(parametros, idAnfitrion, null);
    }

    /**
     * Crea la sala con la ficha del anfitrion, la que devolvio la puerta de
     * heroe al crearla (SCRUM-1074).
     *
     * <p>El anfitrion es participante desde que la sala existe, asi que su
     * heroe se guarda igual que el de cualquiera que entre despues. Sin esto,
     * seria el unico de la partida sin barra de vida.
     */
    public static Sala crear(ParametrosDeSala parametros, UUID idAnfitrion,
                             FichaDeParticipante fichaDelAnfitrion) {
        Objects.requireNonNull(parametros, "Una sala necesita parametros de creacion.");
        Objects.requireNonNull(idAnfitrion, "Una sala necesita un anfitrion.");
        Objects.requireNonNull(parametros.modalidad(), "Una sala necesita una modalidad.");

        List<ErrorDeCampo> errores = new ArrayList<>();
        boolean aforoValido = validarParticipantes(parametros, errores);
        validarEquipo(parametros, errores);
        validarHeroesIA(parametros, aforoValido, errores);
        validarRecompensa(parametros.recompensaCreditos(), errores);

        if (!errores.isEmpty()) {
            throw new ParametrosInvalidos(errores);
        }

        // Contra la IA la maquina va siempre, la pida el formulario o no: es la
        // modalidad, no una opcion.
        int heroesIA = Math.max(parametros.heroesIA(), parametros.modalidad().minimoHeroesIA());

        // Nace LLENA si la maquina ya ocupa lo que quedaba: contra la IA, o
        // hasta seis con todos los cupos menos uno para la maquina.
        EstadoSala alNacer;
        if (1 + heroesIA >= parametros.maximoParticipantes()) {
            alNacer = EstadoSala.LLENA;
        } else {
            alNacer = parametros.privada() ? EstadoSala.PRIVADA : EstadoSala.ABIERTA;
        }

        return new Sala(
                UUID.randomUUID(),
                alNacer,
                parametros.modalidad(),
                parametros.maximoParticipantes(),
                parametros.recompensaCreditos(),
                heroesIA,
                parametros.privada(),
                parametros.tamanoEquipo(),
                idAnfitrion,
                // Al crearla solo esta el anfitrion. El constructor ya lo mete
                // por su cuenta; aqui viaja su ficha, que el constructor no
                // puede adivinar.
                fichaDelAnfitrion == null ? Map.of() : Map.of(idAnfitrion, fichaDelAnfitrion),
                Instant.now(),
                0L, // nace sin escrituras; la base la incrementa a partir de aqui
                parametros.privada() ? generarCodigoDeInvitacion() : null,
                null); // la reserva la anota el caso de uso, cuando el modulo de creditos responde
    }

    /**
     * Genera el codigo de invitacion de una sala privada.
     *
     * <p>Ocho caracteres de un alfabeto de 32 —sin {@code I}, {@code O},
     * {@code 0} ni {@code 1}, que se confunden al dictarlos por voz o al
     * copiarlos de una captura— son 40 bits: mil millones de veces mas
     * combinaciones que salas puede haber abiertas a la vez. El grupo de cuatro
     * con guion es para que se pueda leer en voz alta sin perder la cuenta.
     *
     * <p>Se usa {@link SecureRandom} y no {@code Math.random()} a proposito: un
     * generador predecible convierte el codigo en un tramite, no en una llave.
     */
    private static String generarCodigoDeInvitacion() {
        StringBuilder codigo = new StringBuilder(LONGITUD_DEL_CODIGO + 1);
        for (int i = 0; i < LONGITUD_DEL_CODIGO; i++) {
            if (i == LONGITUD_DEL_CODIGO / 2) {
                codigo.append('-');
            }
            codigo.append(ALFABETO_DEL_CODIGO.charAt(AZAR.nextInt(ALFABETO_DEL_CODIGO.length())));
        }
        return codigo.toString();
    }

    /**
     * RF-JUE-004: cada modalidad admite un rango distinto de participantes.
     *
     * @return si el aforo es valido; con uno invalido no tiene sentido medir
     *         contra el cuantas maquinas caben
     */
    private static boolean validarParticipantes(ParametrosDeSala parametros, List<ErrorDeCampo> errores) {
        Modalidad modalidad = parametros.modalidad();
        if (modalidad.admite(parametros.maximoParticipantes())) {
            return true;
        }
        String rango = modalidad.minimoParticipantes() == modalidad.maximoParticipantes()
                ? "exactamente " + modalidad.minimoParticipantes()
                : "entre " + modalidad.minimoParticipantes() + " y " + modalidad.maximoParticipantes();
        errores.add(new ErrorDeCampo("maximoParticipantes",
                "Esta modalidad admite " + rango + " jugadores."));
        return false;
    }

    /**
     * RF-JUE-004 — HU-SAL-004: cuantos cupos puede ocupar la maquina en cada
     * modalidad. Fuera de limite se rechaza diciendo el limite (CA-04).
     *
     * <p>Contra la IA pedir cero no es un error —la modalidad ya la trae— pero
     * pedir dos si: un duelo contra la maquina es contra una. Uno contra uno no
     * lleva maquina: si se quiere una, la modalidad es otra. Hasta seis admite
     * cualquier cupo para la IA menos el del anfitrion, que siempre juega.
     */
    private static void validarHeroesIA(ParametrosDeSala parametros, boolean aforoValido,
                                        List<ErrorDeCampo> errores) {
        int pedidos = parametros.heroesIA();
        Modalidad modalidad = parametros.modalidad();
        if (pedidos < 0) {
            errores.add(new ErrorDeCampo("heroesIA", "Los heroes de la IA no pueden ser negativos."));
            return;
        }
        if (modalidad == Modalidad.UNO_CONTRA_UNO && pedidos > 0) {
            errores.add(new ErrorDeCampo("heroesIA",
                    "Uno contra uno es entre dos jugadores, sin heroes de la IA: "
                            + "para jugar contra la maquina elige la modalidad contra la IA."));
            return;
        }
        if (!aforoValido) {
            return;
        }
        int caben = modalidad.maximoHeroesIA(parametros.maximoParticipantes());
        if (pedidos > caben) {
            String limite = modalidad == Modalidad.CONTRA_IA
                    ? "Contra la IA se enfrenta a un solo heroe de la maquina: como maximo 1."
                    : "Con " + parametros.maximoParticipantes() + " participantes caben como maximo "
                            + caben + " heroes de la IA: el anfitrion siempre juega.";
            errores.add(new ErrorDeCampo("heroesIA", limite));
        }
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
     * <p>Una sala <b>PRIVADA</b> solo admite a quien traiga su codigo de
     * invitacion; sin el, o con uno que no case, el rechazo es 403. La
     * comparacion es carácter a carácter salvo por mayusculas y guiones, que se
     * normalizan: quien recibe el codigo por chat suele pegarlo con el formato
     * cambiado, y rechazarlo por eso seria rechazar a alguien que si esta
     * invitado.
     *
     * @param idJugador jugador que quiere entrar
     * @param codigo    codigo de invitacion; se ignora si la sala es publica
     * @throws IngresoNoPermitido        si la sala no lo admite
     * @throws SalaPrivadaSinInvitacion  si es privada y el codigo falta o no vale
     */
    public void unirse(UUID idJugador, String codigo) {
        unirse(idJugador, null, codigo);
    }

    /**
     * Ingreso con la ficha que devolvio la puerta de heroe (SCRUM-1074).
     *
     * <p>Es la forma que usa el caso de uso real. Las variantes sin ficha
     * quedan para los dobles que solo ejercitan reglas de aforo y para el
     * anfitrion de las salas creadas antes de que existiera la puerta.
     *
     * @param ficha heroe y apodo con los que entra; puede ser {@code null}
     */
    public void unirse(UUID idJugador, FichaDeParticipante ficha, String codigo) {
        Objects.requireNonNull(idJugador, "Para entrar a una sala hace falta un jugador.");

        // La sala privada tiene su propio rechazo, con 403: el contrato lo separa
        // del 409 porque la interfaz reacciona distinto a cada uno.
        if (estado == EstadoSala.PRIVADA && !codigoCoincide(codigo)) {
            throw new SalaPrivadaSinInvitacion();
        }
        if (!estadoAdmiteIngreso()) {
            throw new IngresoNoPermitido(motivoDelEstado());
        }
        if (participantes.containsKey(idJugador)) {
            throw new IngresoNoPermitido("Ya estas en esta sala.");
        }
        // Los cupos de la maquina cuentan (HU-SAL-004): no se admite a alguien
        // en un puesto que ya es de la IA.
        if (ocupacion() >= maximoParticipantes) {
            throw new IngresoNoPermitido("La sala ya alcanzo su maximo de participantes.");
        }

        participantes.put(idJugador, ficha);

        if (ocupacion() == maximoParticipantes) {
            estado = EstadoSala.LLENA;
        }
    }

    /**
     * Ingreso sin codigo. Equivale a {@code unirse(idJugador, null)}: sirve para
     * las salas publicas, y en una privada rechaza, que es lo correcto.
     */
    public void unirse(UUID idJugador) {
        unirse(idJugador, null);
    }

    /**
     * Saca a un jugador de la sala — operacion {@code abandonarSala} del contrato.
     *
     * <p>Simetrica de {@link #unirse(UUID, String)}: si al entrar la sala pudo
     * llenarse, al salir puede volver a admitir gente, asi que una sala LLENA
     * regresa a ABIERTA o a PRIVADA segun como se creo. No se queda en LLENA con
     * un hueco libre.
     *
     * <p><b>El anfitrion no abandona: cancela.</b> Lo rechaza aqui, y el caso de
     * uso lo encamina a {@link #cancelar(UUID)}. Dejar una sala sin anfitrion
     * abriria preguntas que ningun requisito responde —quien hereda la sala,
     * quien recupera los creditos comprometidos, quien puede cancelarla
     * despues— y responderlas por nuestra cuenta seria inventarlas.
     *
     * @param idJugador jugador que se va
     * @throws SalidaNoPermitida si no esta dentro, si es el anfitrion, o si la
     *                           partida ya empezo o termino
     */
    public void abandonar(UUID idJugador) {
        Objects.requireNonNull(idJugador, "Para salir de una sala hace falta un jugador.");

        if (idJugador.equals(idAnfitrion)) {
            throw new SalidaNoPermitida(
                    "El anfitrion no abandona su sala: la cancela.");
        }
        if (!participantes.containsKey(idJugador)) {
            throw new SalidaNoPermitida("No estas en esta sala.");
        }
        if (estado == EstadoSala.EN_JUEGO) {
            throw new SalidaNoPermitida("La partida ya comenzo: no puedes abandonar la sala.");
        }
        if (estado == EstadoSala.FINALIZADA || estado == EstadoSala.CANCELADA) {
            throw new SalidaNoPermitida("Esta sala ya no esta activa.");
        }

        participantes.remove(idJugador);

        // Al liberarse un cupo la sala vuelve a admitir, con la misma etiqueta
        // con la que nacio: una sala privada no se vuelve publica por que
        // alguien se haya ido.
        if (estado == EstadoSala.LLENA) {
            estado = privada ? EstadoSala.PRIVADA : EstadoSala.ABIERTA;
        }
    }

    /**
     * Cancela la sala — operacion {@code cancelarSala} del contrato.
     *
     * <p>Solo el anfitrion, y solo antes de empezar. Los dos rechazos van con el
     * codigo que fija el contrato: 403 si lo pide otro, 409 si la partida ya
     * arranco o la sala ya no esta activa.
     *
     * <p>No borra la sala: la marca CANCELADA. Un borrado dejaria sin explicacion
     * a quienes estaban dentro, y las tarjetas del listado se filtran por estado
     * ({@link EstadoSala#delListado()}), asi que una sala cancelada desaparece
     * del listado igual, pero conservando su rastro.
     *
     * <p>Los creditos los devuelve el caso de uso, con
     * {@link #idReservaCreditos()}: el dominio no habla con el modulo de creditos.
     *
     * @param idSolicitante quien pide cancelarla
     * @throws NoEsElAnfitrion   si no es quien creo la sala
     * @throws SalidaNoPermitida si la partida ya empezo o la sala ya no esta activa
     */
    public void cancelar(UUID idSolicitante) {
        Objects.requireNonNull(idSolicitante, "Cancelar una sala requiere saber quien lo pide.");

        if (!idSolicitante.equals(idAnfitrion)) {
            throw new NoEsElAnfitrion();
        }
        if (estado == EstadoSala.EN_JUEGO) {
            throw new SalidaNoPermitida("La partida ya comenzo: la sala no se puede cancelar.");
        }
        if (estado == EstadoSala.CANCELADA) {
            throw new SalidaNoPermitida("Esta sala ya estaba cancelada.");
        }
        if (estado == EstadoSala.FINALIZADA) {
            throw new SalidaNoPermitida("Esta partida ya termino.");
        }

        estado = EstadoSala.CANCELADA;
    }

    /**
     * Arranca el combate — HU-SAL-004, RF-JUE-017.
     *
     * <p>Solo el anfitrion, y solo una vez: al pasar a {@link EstadoSala#EN_JUEGO}
     * la sala deja de admitir gente (ver {@code estadoAdmiteIngreso}) y un
     * segundo intento choca con este mismo guardia. Es lo que impide que una
     * sala tenga dos partidas.
     *
     * <p>Una sala de un solo participante no arranca: RF-JUE-004 define las
     * modalidades como enfrentamientos, y un combate de uno no lo es. La unica
     * excepcion es la sala con heroe de la IA, que ya trae rival.
     *
     * @param idSolicitante quien pide iniciarla
     * @throws NoEsElAnfitrion   si no es quien creo la sala
     * @throws IngresoNoPermitido si la sala no esta en un estado que admita empezar
     */
    public void iniciarPartida(UUID idSolicitante) {
        Objects.requireNonNull(idSolicitante, "Iniciar la partida requiere saber quien lo pide.");

        if (!idSolicitante.equals(idAnfitrion)) {
            throw new NoEsElAnfitrion();
        }
        if (estado == EstadoSala.EN_JUEGO) {
            throw new IngresoNoPermitido("Esta partida ya empezo.");
        }
        if (estado == EstadoSala.CANCELADA || estado == EstadoSala.FINALIZADA) {
            throw new IngresoNoPermitido("Esta sala ya no esta activa.");
        }
        if (ocupacion() < 2) {
            throw new IngresoNoPermitido(
                    "Hace falta al menos un rival para empezar: invita a alguien o crea la sala con heroe de la IA.");
        }

        estado = EstadoSala.EN_JUEGO;
    }

    /**
     * Anota la reserva de creditos que quedo ligada a esta sala.
     *
     * <p>Lo llama el caso de uso de creacion en cuanto el modulo de creditos
     * responde, y solo entonces: la reserva no existe hasta que la confirma su
     * dueno, y el dominio no puede inventarse un identificador que no emitio.
     *
     * @throws IllegalStateException si ya habia una reserva anotada
     */
    public Sala conReserva(UUID idReserva) {
        Objects.requireNonNull(idReserva, "Una reserva sin identificador no se puede liberar.");
        if (idReservaCreditos != null) {
            throw new IllegalStateException("Esta sala ya tiene una reserva de creditos anotada.");
        }
        return new Sala(id, estado, modalidad, maximoParticipantes, recompensaCreditos,
                heroesIA, privada, tamanoEquipo, idAnfitrion, participantes, creadaEn,
                version, codigoInvitacion, idReserva);
    }

    /**
     * Compara el codigo recibido con el de la sala, tolerando mayusculas,
     * espacios sobrantes y guiones puestos de otra forma.
     */
    private boolean codigoCoincide(String recibido) {
        if (codigoInvitacion == null || recibido == null) {
            return false;
        }
        return normalizar(codigoInvitacion).equals(normalizar(recibido));
    }

    private static String normalizar(String codigo) {
        return codigo.replace("-", "").replace(" ", "").toUpperCase(java.util.Locale.ROOT);
    }

    /** Si este jugador es quien creo la sala. */
    public boolean esAnfitrion(UUID idJugador) {
        return idAnfitrion.equals(idJugador);
    }

    /** Solo ABIERTA admite. PRIVADA entra por el camino del codigo de invitacion. */
    private boolean estadoAdmiteIngreso() {
        return estado == EstadoSala.ABIERTA || estado == EstadoSala.PRIVADA;
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
        return Collections.unmodifiableSet(participantes.keySet());
    }

    /**
     * Ficha con la que entro un participante, o {@code null} si no se conoce.
     *
     * <p>Es nula en dos casos legitimos: el anfitrion de una sala creada antes
     * de SCRUM-1074, y cualquier fila guardada antes de la migracion V7. No se
     * inventa una: quien la reciba tiene que saber distinguir «no lo se» de un
     * heroe con vida cero.
     */
    public FichaDeParticipante fichaDe(UUID idJugador) {
        return participantes.get(idJugador);
    }

    /** Fichas conocidas, en el orden en que entraron. Copia inmutable. */
    public Map<UUID, FichaDeParticipante> fichas() {
        return Collections.unmodifiableMap(participantes);
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

    /** Si la maquina ocupa al menos un cupo (la forma de RF-JUE-001). */
    public boolean incluirHeroeIA() {
        return heroesIA > 0;
    }

    /** Cuantos cupos son de la maquina — HU-SAL-004. Cuentan en {@link #ocupacion()}. */
    public int heroesIA() {
        return heroesIA;
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

    /**
     * Aforo ocupado: las personas dentro mas los cupos de la maquina.
     * <b>Derivado</b> del conjunto de participantes: no existe un contador que
     * pueda quedarse desfasado respecto a quienes estan dentro. Al crear la
     * sala, el anfitrion y las maquinas que pidio.
     */
    public int ocupacion() {
        return participantes.size() + heroesIA;
    }

    /** Marca de concurrencia leida de la base. Ver el campo. */
    public long version() {
        return version;
    }

    /**
     * Codigo de invitacion, o {@code null} si la sala es publica.
     *
     * <p><b>Es un secreto:</b> quien lo tenga entra. La capa de API solo se lo
     * devuelve al anfitrion; ningun otro camino debe exponerlo.
     */
    public String codigoInvitacion() {
        return codigoInvitacion;
    }

    /**
     * Reserva de creditos <b>del anfitrion</b>, o {@code null} si la sala no
     * compromete creditos.
     *
     * <p>Desde HU-JUE-014 cada participante tiene la suya: la del anfitrion se
     * anota aqui al crear la sala (columna de V5) y la de los demas viaja en su
     * {@link FichaDeParticipante} al entrar (V9). Para verlas todas juntas,
     * {@link #reservasDeCreditos()}.
     */
    public UUID idReservaCreditos() {
        return idReservaCreditos;
    }

    /**
     * Reserva de creditos de un participante concreto — HU-JUE-014.
     *
     * <p>Vacio si no esta dentro, si la sala no tiene recompensa, o si entro
     * antes de que existiera la apuesta (fila anterior a V9). Se consulta
     * <b>antes</b> de sacarlo de la sala: una vez fuera, la sala ya no sabe
     * nada de el.
     */
    public java.util.Optional<UUID> reservaDe(UUID idJugador) {
        if (idJugador == null || !participantes.containsKey(idJugador)) {
            return java.util.Optional.empty();
        }
        if (idJugador.equals(idAnfitrion)) {
            return java.util.Optional.ofNullable(idReservaCreditos);
        }
        FichaDeParticipante ficha = participantes.get(idJugador);
        return ficha == null ? java.util.Optional.empty()
                : java.util.Optional.ofNullable(ficha.idReservaCreditos());
    }

    /**
     * Todas las reservas comprometidas en la sala, por participante, con el
     * anfitrion primero — HU-JUE-014.
     *
     * <p>Es lo que hay que devolver al cancelar y lo que hay que liquidar al
     * terminar. Solo aparecen quienes tienen reserva: en una sala sin
     * recompensa el mapa esta vacio y nadie molesta al libro de creditos.
     */
    public Map<UUID, UUID> reservasDeCreditos() {
        Map<UUID, UUID> reservas = new LinkedHashMap<>();
        for (UUID jugador : participantes.keySet()) {
            reservaDe(jugador).ifPresent(reserva -> reservas.put(jugador, reserva));
        }
        return Collections.unmodifiableMap(reservas);
    }

    /** Momento de creacion. Viaja en el contrato como {@code creadaEn}. */
    public Instant creadaEn() {
        return creadaEn;
    }
}
