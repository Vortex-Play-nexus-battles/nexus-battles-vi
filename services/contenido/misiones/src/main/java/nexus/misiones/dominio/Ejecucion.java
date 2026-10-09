package nexus.misiones.dominio;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import nexus.misiones.dominio.simulacion.ResultadoDeMision;

/**
 * Una ejecucion: un heroe enviado una vez a una mision (seccion 7.8.6). Es el
 * «almacenamiento del estado de misiones activas» y el «registro completo de
 * misiones completadas» de 7.8.12.
 *
 * <h2>Ciclo de vida</h2>
 *
 * Nace En progreso al matricular, con el heroe ya bloqueado en el inventario.
 * Sale de ahi una sola vez: al vencer su plazo, con el resultado de la
 * simulacion (Completada o Fallida), o al cancelar (Abandonada). Despues solo
 * cambian los pasos de su liquidacion ({@link PasoDeLiquidacion}), que el
 * trabajo en segundo plano va cumpliendo con reintentos.
 *
 * <p>La version optimista la lleva la persistencia: dos escrituras a la vez
 * (cancelar mientras el trabajo la termina) no se pisan, la segunda falla.
 */
public final class Ejecucion {

    /** Tope de la espera exponencial entre reintentos de la liquidacion. */
    public static final Duration ESPERA_MAXIMA_ENTRE_INTENTOS = Duration.ofHours(1);

    /**
     * Tope de la espera entre intentos de SIMULAR una ejecucion vencida. Corto
     * a proposito: el jugador esta esperando su reporte y, cuando vuelva el
     * servicio que faltaba, no debe esperar una hora mas. Solo existe para que
     * una ejecucion que no se puede simular deje paso a las demas.
     */
    public static final Duration ESPERA_MAXIMA_ANTES_DE_SIMULAR = Duration.ofMinutes(5);

    /**
     * Cuanto tiempo es de una vuelta la simulacion que reservo (HU-SIM-007). Una mision entera son cientos de
     * llamadas a heroes y al motor (menos de un minuto en la practica); cinco minutos dejan margen y, si el proceso
     * muere, la ejecucion no espera mas que eso. Es el mismo valor que el tope del aplazamiento.
     */
    public static final Duration ARRIENDO_DE_SIMULACION = Duration.ofMinutes(5);

    private final UUID id;
    private final String misionId;
    private final String jugadorUid;
    private final HeroeEnMision heroe;
    private final List<List<String>> estrategia;
    private final Escalon escalon;
    private final Instant iniciadaEn;
    private final Instant terminaEn;
    private final long semilla;
    private final String claveIdempotencia;

    private EstadoEjecucion estado;
    private Instant terminadaEn;
    private ResultadoDeMision resultado;
    private RecompensasDeEjecucion recompensas;
    private final EnumMap<PasoDeLiquidacion, EstadoDePaso> pasos;
    private final EnumMap<PasoDeLiquidacion, String> motivos;
    private int intentosDeLiquidacion;
    private Instant proximoIntento;
    private String ultimoError;
    private Integer nivelAlcanzado;
    private Double experienciaAcumulada;
    private int intentosDeSimulacion;
    private boolean canceladaSinPenalizacion;
    private Long version;

    private Ejecucion(Estado e) {
        this.id = Objects.requireNonNull(e.id, "id");
        this.misionId = Objects.requireNonNull(e.misionId, "misionId");
        this.jugadorUid = Objects.requireNonNull(e.jugadorUid, "jugadorUid");
        this.heroe = Objects.requireNonNull(e.heroe, "heroe");
        this.estrategia = e.estrategia == null ? List.of() : e.estrategia.stream().map(List::copyOf).toList();
        this.escalon = e.escalon == null ? Escalon.NORMAL : e.escalon;
        this.iniciadaEn = Objects.requireNonNull(e.iniciadaEn, "iniciadaEn");
        this.terminaEn = Objects.requireNonNull(e.terminaEn, "terminaEn");
        this.semilla = e.semilla;
        this.claveIdempotencia = e.claveIdempotencia;
        this.estado = Objects.requireNonNull(e.estado, "estado");
        this.terminadaEn = e.terminadaEn;
        this.resultado = e.resultado;
        this.recompensas = e.recompensas;
        this.pasos = new EnumMap<>(PasoDeLiquidacion.class);
        if (e.pasos != null) {
            this.pasos.putAll(e.pasos);
        }
        this.motivos = new EnumMap<>(PasoDeLiquidacion.class);
        if (e.motivos != null) {
            this.motivos.putAll(e.motivos);
        }
        this.intentosDeLiquidacion = e.intentosDeLiquidacion;
        this.proximoIntento = e.proximoIntento;
        this.ultimoError = e.ultimoError;
        this.nivelAlcanzado = e.nivelAlcanzado;
        this.experienciaAcumulada = e.experienciaAcumulada;
        this.intentosDeSimulacion = e.intentosDeSimulacion == null ? 0 : e.intentosDeSimulacion;
        this.canceladaSinPenalizacion = Boolean.TRUE.equals(e.sinPenalizacion);
        this.version = e.version;
    }

    /** Una ejecucion recien matriculada, En progreso. */
    public static Ejecucion nueva(UUID id, String misionId, String jugadorUid, HeroeEnMision heroe,
                                  List<List<String>> estrategia, Escalon escalon, Instant ahora,
                                  Duration duracion, long semilla, String claveIdempotencia) {
        Estado e = new Estado();
        e.id = id;
        e.misionId = misionId;
        e.jugadorUid = jugadorUid;
        e.heroe = heroe;
        e.estrategia = estrategia;
        e.escalon = escalon;
        e.iniciadaEn = ahora;
        e.terminaEn = ahora.plus(duracion);
        e.semilla = semilla;
        e.claveIdempotencia = claveIdempotencia;
        e.estado = EstadoEjecucion.EN_PROGRESO;
        return new Ejecucion(e);
    }

    /** Para la persistencia: vuelve a montar una ejecucion guardada. */
    public static Ejecucion reconstruir(Estado estado) {
        return new Ejecucion(estado);
    }

    // ------------------------------------------------------------ reglas

    /** Tiempo transcurrido sobre la duracion, de 0 a 1 (7.8.9, «progreso estimado»). */
    public double progreso(Instant ahora) {
        long total = Duration.between(iniciadaEn, terminaEn).toMillis();
        if (total <= 0) {
            return 1.0;
        }
        long transcurrido = Duration.between(iniciadaEn, ahora).toMillis();
        return Math.max(0.0, Math.min(1.0, (double) transcurrido / total));
    }

    /** «Al completarse el tiempo de duracion» (7.8.6). */
    public boolean vencida(Instant ahora) {
        return !ahora.isBefore(terminaEn);
    }

    /**
     * Vencida, en progreso y sin un intento de simulacion aplazado para mas
     * tarde: es lo que el trabajo en segundo plano debe simular ahora.
     */
    public boolean listaParaSimular(Instant ahora) {
        return estado == EstadoEjecucion.EN_PROGRESO && vencida(ahora)
                && (proximoIntento == null || !proximoIntento.isAfter(ahora));
    }

    /**
     * La simulacion de esta ejecucion VIENE FALLANDO: sigue en progreso y hay al menos un intento de simularla
     * fallido y registrado ({@link #simulacionAplazada}), es decir, un error del sistema y no del jugador la tiene
     * detenida. Mientras esta en progreso, {@link #intentosDeLiquidacion()} solo lo suma ese aplazamiento (los
     * reintentos de la liquidacion empiezan cuando termina, y {@code terminar} y {@code cancelar} lo ponen a cero),
     * asi que es la cuenta de simulaciones fallidas. Reservar para simular no es fallar.
     */
    public boolean simulacionFallando() {
        return estado == EstadoEjecucion.EN_PROGRESO && intentosDeLiquidacion > 0;
    }

    /**
     * Cancelar (7.8.7, «Abandonada: cancelada por el jugador, con
     * penalizacion»). La penalizacion provisional es perder todo lo de esta
     * ejecucion: no hay resultado ni recompensas, y solo queda liberar al heroe
     * sin experiencia.
     *
     * <p>Excepcion (decision del PO, 2026-10-06): si la simulacion venia fallando por un error del sistema
     * ({@link #simulacionFallando()}), la cancelacion es SIN penalizacion: el jugador no tuvo la culpa de que la
     * mision no se pudiera resolver, asi que no gasta uno de sus intentos ({@link #canceladaSinPenalizacion()}).
     * Se decide antes de empezar la liquidacion, que borra la cuenta de fallos.
     */
    public void cancelar(Instant ahora) {
        exigirEnProgreso("cancelar");
        canceladaSinPenalizacion = simulacionFallando();
        estado = EstadoEjecucion.ABANDONADA;
        terminadaEn = ahora;
        pasos.clear();
        pasos.put(PasoDeLiquidacion.LIBERACION, EstadoDePaso.PENDIENTE);
        empezarLiquidacion(ahora);
    }

    /**
     * Termina con el resultado de la simulacion y deja pendientes los pasos
     * de entrega que hagan falta, sin avisos en la bandeja.
     *
     * @param conCorreo si el correo de misiones esta activo en este entorno
     */
    public void terminar(ResultadoDeMision resultado, RecompensasDeEjecucion recompensas,
                         boolean conCorreo, Instant ahora) {
        terminar(resultado, recompensas, conCorreo, false, ahora);
    }

    /**
     * Termina con el resultado de la simulacion y deja pendientes los pasos
     * de entrega que hagan falta y, si estan activos, los avisos (RF-NOT-004):
     * el de la finalizacion siempre, el de la epica si se obtuvo alguna, y el
     * de las misiones desbloqueadas solo la PRIMERA vez que el jugador la
     * completa, que es la unica vez que desbloquea algo (7.8.2).
     *
     * @param conCorreo si el correo de misiones esta activo en este entorno
     * @param conAvisos si los avisos en la bandeja estan activos en este entorno
     */
    public void terminar(ResultadoDeMision resultado, RecompensasDeEjecucion recompensas,
                         boolean conCorreo, boolean conAvisos, Instant ahora) {
        exigirEnProgreso("terminar");
        this.resultado = Objects.requireNonNull(resultado);
        this.recompensas = Objects.requireNonNull(recompensas);
        this.estado = resultado.exito() ? EstadoEjecucion.COMPLETADA : EstadoEjecucion.FALLIDA;
        this.terminadaEn = ahora;
        pasos.clear();
        pasos.put(PasoDeLiquidacion.LIBERACION, EstadoDePaso.PENDIENTE);
        if (recompensas.creditos() > 0) {
            pasos.put(PasoDeLiquidacion.CREDITOS, EstadoDePaso.PENDIENTE);
        }
        if (!recompensas.productos().isEmpty()) {
            pasos.put(PasoDeLiquidacion.BOTIN, EstadoDePaso.PENDIENTE);
        }
        if (recompensas.tieneEpicaEntregable()) {
            pasos.put(PasoDeLiquidacion.EPICA, EstadoDePaso.PENDIENTE);
        }
        if (conCorreo) {
            pasos.put(PasoDeLiquidacion.CORREO, EstadoDePaso.PENDIENTE);
            if (!recompensas.epicas().isEmpty()) {
                pasos.put(PasoDeLiquidacion.CORREO_EPICA, EstadoDePaso.PENDIENTE);
            }
        }
        if (conAvisos) {
            pasos.put(PasoDeLiquidacion.AVISO, EstadoDePaso.PENDIENTE);
            if (!recompensas.epicas().isEmpty()) {
                pasos.put(PasoDeLiquidacion.AVISO_EPICA, EstadoDePaso.PENDIENTE);
            }
            if (estado == EstadoEjecucion.COMPLETADA && recompensas.primeraVez()) {
                pasos.put(PasoDeLiquidacion.AVISO_DESBLOQUEO, EstadoDePaso.PENDIENTE);
            }
        }
        empezarLiquidacion(ahora);
    }

    /**
     * Quien va a simular una ejecucion vencida la RESERVA primero y guarda la reserva con la version que leyo: si
     * otro barrido u otra instancia se adelanto, esa escritura falla y este no simula (HU-SIM-007). La reserva es un
     * arriendo de {@link #ARRIENDO_DE_SIMULACION} que usa el mismo campo que el aplazamiento de un fallo
     * ({@link #proximoIntento()}), asi que {@link #listaParaSimular} y la consulta de las vencidas ya la excluyen
     * sin mas: si el proceso muere simulando, el arriendo vence y otra vuelta la retoma. Cada reserva suma un
     * intento, que es lo que permite saber, despues, si la reserva que se tiene en la mano sigue siendo la vigente.
     */
    public void reservarParaSimular(Instant ahora) {
        exigirEnProgreso("simular");
        intentosDeSimulacion++;
        proximoIntento = ahora.plus(ARRIENDO_DE_SIMULACION);
    }

    /** El arriendo o la espera de un fallo todavia no vencio: alguna vuelta la tiene o la esta esperando. */
    public boolean reservaVigente(Instant ahora) {
        return estado == EstadoEjecucion.EN_PROGRESO && proximoIntento != null && ahora.isBefore(proximoIntento);
    }

    /**
     * La simulacion de esta ejecucion vencida no se pudo hacer (un servicio no
     * respondio, o la rechazo): se vuelve a intentar mas tarde, cada vez mas
     * tarde y con tope de {@link #ESPERA_MAXIMA_ANTES_DE_SIMULAR}. Sin esta
     * espera, las que no se pueden simular eran siempre las primeras de cada
     * lote (las mas antiguas) y, con tantas como el lote, ninguna otra
     * ejecucion se simulaba nunca. El heroe sigue en mision; el jugador puede
     * cancelarla.
     */
    public void simulacionAplazada(Instant ahora, Duration esperaBase, String error) {
        exigirEnProgreso("aplazar la simulación de");
        intentosDeLiquidacion++;
        proximoIntento = ahora.plus(espera(esperaBase, intentosDeLiquidacion, ESPERA_MAXIMA_ANTES_DE_SIMULAR));
        ultimoError = error;
    }

    /**
     * La liquidacion empieza de cero: los intentos de simular que hubiera no
     * cuentan para la espera de sus reintentos, y su error ya no aplica.
     */
    private void empezarLiquidacion(Instant ahora) {
        intentosDeLiquidacion = 0;
        ultimoError = null;
        proximoIntento = ahora;
    }

    public void pasoHecho(PasoDeLiquidacion paso) {
        pasos.put(paso, EstadoDePaso.HECHO);
        motivos.remove(paso);
    }

    /**
     * El paso se hizo y queda una nota de como: por ejemplo, que el jugador ya
     * tenia la epica y el inventario no la entrego otra vez. Se guarda con los
     * motivos de los pasos, que ya se persisten, y los avisos posteriores la leen.
     */
    public void pasoHecho(PasoDeLiquidacion paso, String nota) {
        pasos.put(paso, EstadoDePaso.HECHO);
        motivos.put(paso, nota);
    }

    /** La nota con la que se hizo un paso, o nula si se hizo sin nada que contar (o no se ha hecho). */
    public String notaDe(PasoDeLiquidacion paso) {
        return pasos.get(paso) == EstadoDePaso.HECHO ? motivos.get(paso) : null;
    }

    /** Rechazo definitivo del otro servicio: no se reintenta, queda el motivo. */
    public void pasoFallido(PasoDeLiquidacion paso, String motivo) {
        pasos.put(paso, EstadoDePaso.FALLIDO);
        motivos.put(paso, motivo);
    }

    /** El otro servicio no respondio: se vuelve a intentar despues, cada vez mas tarde. */
    public void reintentarMasTarde(Instant ahora, Duration esperaBase, String error) {
        intentosDeLiquidacion++;
        proximoIntento = ahora.plus(espera(esperaBase, intentosDeLiquidacion, ESPERA_MAXIMA_ENTRE_INTENTOS));
        ultimoError = error;
    }

    /** Espera exponencial: la base en el primer intento, el doble en cada uno, con tope. */
    private static Duration espera(Duration base, int intento, Duration tope) {
        long factor = 1L << Math.min(20, Math.max(0, intento - 1));
        Duration espera = base.multipliedBy(factor);
        return espera.compareTo(tope) > 0 || espera.isNegative() ? tope : espera;
    }

    /** Nivel y experiencia que devolvio el inventario al liberar al heroe. */
    public void registrarProgresion(int nivel, double experiencia) {
        this.nivelAlcanzado = nivel;
        this.experienciaAcumulada = experiencia;
    }

    public boolean liquidacionPendiente() {
        return pasos.containsValue(EstadoDePaso.PENDIENTE);
    }

    /** Los pasos pendientes, en el orden en que se hacen. */
    public List<PasoDeLiquidacion> pasosPendientes() {
        List<PasoDeLiquidacion> pendientes = new ArrayList<>();
        for (PasoDeLiquidacion paso : PasoDeLiquidacion.values()) {
            if (pasos.get(paso) == EstadoDePaso.PENDIENTE) {
                pendientes.add(paso);
            }
        }
        return pendientes;
    }

    public EstadoDePaso estadoDe(PasoDeLiquidacion paso) {
        return pasos.get(paso);
    }

    public String motivoDe(PasoDeLiquidacion paso) {
        return motivos.get(paso);
    }

    private void exigirEnProgreso(String que) {
        if (estado != EstadoEjecucion.EN_PROGRESO) {
            throw new TransicionNoPermitida(
                    "No se puede " + que + " una misión que ya terminó (" + estado.name().toLowerCase() + ").");
        }
    }

    // ---------------------------------------------------------- lectura

    public UUID id() {
        return id;
    }

    public String misionId() {
        return misionId;
    }

    public String jugadorUid() {
        return jugadorUid;
    }

    public HeroeEnMision heroe() {
        return heroe;
    }

    public List<List<String>> estrategia() {
        return estrategia;
    }

    public Escalon escalon() {
        return escalon;
    }

    public Instant iniciadaEn() {
        return iniciadaEn;
    }

    public Instant terminaEn() {
        return terminaEn;
    }

    public long semilla() {
        return semilla;
    }

    public String claveIdempotencia() {
        return claveIdempotencia;
    }

    public EstadoEjecucion estado() {
        return estado;
    }

    public Instant terminadaEn() {
        return terminadaEn;
    }

    public ResultadoDeMision resultado() {
        return resultado;
    }

    public RecompensasDeEjecucion recompensas() {
        return recompensas;
    }

    public Map<PasoDeLiquidacion, EstadoDePaso> pasos() {
        return Map.copyOf(pasos);
    }

    public Map<PasoDeLiquidacion, String> motivos() {
        return Map.copyOf(motivos);
    }

    public int intentosDeLiquidacion() {
        return intentosDeLiquidacion;
    }

    public Instant proximoIntento() {
        return proximoIntento;
    }

    public String ultimoError() {
        return ultimoError;
    }

    public Integer nivelAlcanzado() {
        return nivelAlcanzado;
    }

    public Double experienciaAcumulada() {
        return experienciaAcumulada;
    }

    /** Cuantas veces se reservo para simular, con exito o sin el: el primer intento es el 1. */
    public int intentosDeSimulacion() {
        return intentosDeSimulacion;
    }

    /**
     * Se cancelo con la simulacion fallando por un error del sistema: no cuenta como intento consumido de un
     * desafio ni lleva la penalizacion de abandonar.
     */
    public boolean canceladaSinPenalizacion() {
        return canceladaSinPenalizacion;
    }

    public Long version() {
        return version;
    }

    /** Lo que guarda la persistencia; mutable solo para reconstruir. */
    public static final class Estado {
        public UUID id;
        public String misionId;
        public String jugadorUid;
        public HeroeEnMision heroe;
        public List<List<String>> estrategia;
        public Escalon escalon;
        public Instant iniciadaEn;
        public Instant terminaEn;
        public long semilla;
        public String claveIdempotencia;
        public EstadoEjecucion estado;
        public Instant terminadaEn;
        public ResultadoDeMision resultado;
        public RecompensasDeEjecucion recompensas;
        public Map<PasoDeLiquidacion, EstadoDePaso> pasos;
        public Map<PasoDeLiquidacion, String> motivos;
        public int intentosDeLiquidacion;
        public Instant proximoIntento;
        public String ultimoError;
        public Integer nivelAlcanzado;
        public Double experienciaAcumulada;
        /** Nulo en lo guardado antes de HU-SIM-007: se lee como cero. */
        public Integer intentosDeSimulacion;
        /** Nulo en lo guardado antes de la cancelacion sin penalizacion: se lee como falso. */
        public Boolean sinPenalizacion;
        public Long version;
    }
}
