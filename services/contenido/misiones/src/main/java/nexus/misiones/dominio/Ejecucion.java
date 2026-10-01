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
 *
 * <h2>La simulacion en segundo plano (HU-SIM-007)</h2>
 *
 * Quien va a simular una ejecucion vencida primero la <b>reserva</b>
 * ({@link #reservarParaSimular}) y guarda la reserva con la version leida: si
 * otro barrido (u otra instancia) la reservo antes, esa escritura falla y el
 * segundo no simula. La reserva es un arriendo ({@link #ARRIENDO_DE_SIMULACION}):
 * si el proceso muere simulando, el arriendo vence y otra vuelta la retoma.
 * Si la simulacion falla, el mismo campo hace de espera ({@link #simulacionFallida}):
 * una ejecucion que falla siempre no se reintenta en cada vuelta ni le quita su
 * sitio en el lote a las demas.
 */
public final class Ejecucion {

    /** Tope de la espera exponencial entre reintentos de la liquidacion. */
    public static final Duration ESPERA_MAXIMA_ENTRE_INTENTOS = Duration.ofHours(1);

    /**
     * Cuanto tiempo es de una vuelta la simulacion que reservo. Una mision
     * entera son cientos de llamadas a heroes y al motor (menos de un minuto
     * en la practica); cinco minutos dejan margen y, si el proceso muere, la
     * ejecucion no espera mas que eso.
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
    private Instant simulacionReservadaHasta;
    private String ultimoErrorDeSimulacion;
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
        this.simulacionReservadaHasta = e.simulacionReservadaHasta;
        this.ultimoErrorDeSimulacion = e.ultimoErrorDeSimulacion;
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
     * Cancelar (7.8.7, «Abandonada: cancelada por el jugador, con
     * penalizacion»). La penalizacion provisional es perder todo lo de esta
     * ejecucion: no hay resultado ni recompensas, y solo queda liberar al heroe
     * sin experiencia.
     */
    public void cancelar(Instant ahora) {
        exigirEnProgreso("cancelar");
        estado = EstadoEjecucion.ABANDONADA;
        terminadaEn = ahora;
        simulacionReservadaHasta = null;
        pasos.clear();
        pasos.put(PasoDeLiquidacion.LIBERACION, EstadoDePaso.PENDIENTE);
        proximoIntento = ahora;
    }

    /**
     * Termina con el resultado de la simulacion y deja pendientes los pasos
     * de entrega que hagan falta.
     *
     * @param conCorreo si el correo de misiones esta activo en este entorno
     */
    public void terminar(ResultadoDeMision resultado, RecompensasDeEjecucion recompensas,
                         boolean conCorreo, Instant ahora) {
        exigirEnProgreso("terminar");
        this.resultado = Objects.requireNonNull(resultado);
        this.recompensas = Objects.requireNonNull(recompensas);
        this.estado = resultado.exito() ? EstadoEjecucion.COMPLETADA : EstadoEjecucion.FALLIDA;
        this.terminadaEn = ahora;
        this.simulacionReservadaHasta = null;
        this.ultimoErrorDeSimulacion = null;
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
        proximoIntento = ahora;
    }

    /**
     * Se puede reservar para simular: sigue en progreso, su plazo vencio y
     * ninguna otra vuelta la tiene reservada ahora.
     */
    public boolean reclamable(Instant ahora) {
        return estado == EstadoEjecucion.EN_PROGRESO && vencida(ahora) && !reservaVigente(ahora);
    }

    /** Alguna vuelta la tiene reservada: simulando, o esperando para reintentar tras un fallo. */
    public boolean reservaVigente(Instant ahora) {
        return simulacionReservadaHasta != null && ahora.isBefore(simulacionReservadaHasta);
    }

    /** Cuenta el intento y la deja en manos de quien la tomo por {@link #ARRIENDO_DE_SIMULACION}. */
    public void reservarParaSimular(Instant ahora) {
        exigirEnProgreso("simular");
        intentosDeSimulacion++;
        simulacionReservadaHasta = ahora.plus(ARRIENDO_DE_SIMULACION);
    }

    /**
     * La simulacion fallo: no se toca el estado de la mision (sigue en
     * progreso y el heroe en mision) y no se reintenta hasta que pase la
     * espera, que se duplica con cada intento hasta
     * {@link #ESPERA_MAXIMA_ENTRE_INTENTOS}. Nunca se da por perdida: un
     * servicio que vuelve la deja terminar.
     */
    public void simulacionFallida(Instant ahora, Duration esperaBase, String error) {
        exigirEnProgreso("simular");
        simulacionReservadaHasta = ahora.plus(espera(esperaBase, intentosDeSimulacion));
        ultimoErrorDeSimulacion = error;
    }

    public void pasoHecho(PasoDeLiquidacion paso) {
        pasos.put(paso, EstadoDePaso.HECHO);
        motivos.remove(paso);
    }

    /** Rechazo definitivo del otro servicio: no se reintenta, queda el motivo. */
    public void pasoFallido(PasoDeLiquidacion paso, String motivo) {
        pasos.put(paso, EstadoDePaso.FALLIDO);
        motivos.put(paso, motivo);
    }

    /** El otro servicio no respondio: se vuelve a intentar despues, cada vez mas tarde. */
    public void reintentarMasTarde(Instant ahora, Duration esperaBase, String error) {
        intentosDeLiquidacion++;
        proximoIntento = ahora.plus(espera(esperaBase, intentosDeLiquidacion));
        ultimoError = error;
    }

    /** La espera antes del intento siguiente: la base, duplicada con cada intento, con tope. */
    private static Duration espera(Duration base, int intentos) {
        long factor = 1L << Math.min(20, Math.max(0, intentos - 1));
        Duration espera = base.multipliedBy(factor);
        return espera.compareTo(ESPERA_MAXIMA_ENTRE_INTENTOS) > 0 || espera.isNegative()
                ? ESPERA_MAXIMA_ENTRE_INTENTOS
                : espera;
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

    /** Hasta cuando es de una vuelta la simulacion; nulo si nadie la tiene. */
    public Instant simulacionReservadaHasta() {
        return simulacionReservadaHasta;
    }

    public String ultimoErrorDeSimulacion() {
        return ultimoErrorDeSimulacion;
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
        public Instant simulacionReservadaHasta;
        public String ultimoErrorDeSimulacion;
        public Long version;
    }
}
