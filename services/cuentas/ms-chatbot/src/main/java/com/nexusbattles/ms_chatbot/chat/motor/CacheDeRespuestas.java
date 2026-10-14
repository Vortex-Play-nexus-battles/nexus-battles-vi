package com.nexusbattles.ms_chatbot.chat.motor;

import com.nexusbattles.ms_chatbot.chat.enriquecido.VistaDelChat;
import com.nexusbattles.ms_chatbot.chat.preferencias.IdiomaPreferido;
import com.nexusbattles.ms_chatbot.chat.preferencias.NivelDeDetalle;
import com.nexusbattles.ms_chatbot.chat.preferencias.PreferenciasDeRespuesta;
import com.nexusbattles.ms_chatbot.chat.texto.NormalizadorTexto;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 7.4.9 «Sistema de caché para respuestas frecuentes» (ms-chatbot.yaml 1.9.0).
 *
 * <h2>Qué se guarda</h2>
 *
 * Solo el {@link ResultadoMotor} de la base de conocimiento, es decir, lo que
 * devuelve {@link MotorRespuestas#generarRespuesta(String, VistaDelChat,
 * PreferenciasDeRespuesta)} con la versión en producción. Esa respuesta es
 * una función pura de cinco cosas, y esas cinco son la clave ({@link Clave}):
 * la versión en producción, la pregunta normalizada (el motor no mira otra
 * cosa del texto), el idioma pedido, el nivel de detalle y la {@code vista}.
 * No depende de quién pregunta: no lleva nada de su cuenta.
 *
 * <p>Nunca pasan por aquí, porque no salen de ese método: las consultas en
 * vivo ({@code MotorConsultasAsistidas}: inventario, subastas,
 * notificaciones, créditos, misiones, torneos, todas privadas del jugador),
 * los seguimientos de la respuesta anterior ({@code Seguimiento}) ni el aviso
 * de un dato tapado, que {@code ChatService} añade después. La brecha de
 * conocimiento de una pregunta escalada la registra {@code ChatService} a
 * partir del resultado, venga de aquí o no.
 *
 * <p>De la pregunta se guarda solo la huella SHA-256 de su forma normalizada:
 * la clave ocupa lo mismo con 4000 caracteres que con diez, y en memoria no
 * queda lo que escribió nadie.
 *
 * <h2>Cuánto y cuánto tiempo</h2>
 *
 * Como mucho {@code chatbot.cache.respuestas.maximo} respuestas (500), cada
 * una vale {@code chatbot.cache.respuestas.minutos} (30) desde que se guardó;
 * al llenarse sale la usada hace más tiempo (LRU). Cifras provisionales,
 * D-CHA-08. Con 0 entradas la caché no guarda nada. Unos 3 KB por respuesta:
 * 500 son ~1,5 MB del montón de 128 MB del contenedor de DEV.
 *
 * <h2>Versiones</h2>
 *
 * Un despliegue, una reversión o la publicación programada cambian el id de
 * la versión en producción: lo guardado con la anterior ya no se encuentra
 * (está en la clave) y, en cuanto se ve una clave de la versión nueva, se
 * descarta todo lo de la anterior. Una respuesta calculada con la versión
 * vieja que termina después del cambio no se guarda.
 *
 * <p>Por instancia, en memoria: cada instancia nota el cambio de versión en
 * su siguiente mensaje (ver {@link TemasEnProduccion}). Métricas en Actuator:
 * {@code chatbot.cache.respuestas.aciertos}, {@code .fallos},
 * {@code .desalojos{causa}} y {@code .tamano}.
 */
@Component
public class CacheDeRespuestas {

    /** Lo que identifica una respuesta de la base. */
    public record Clave(UUID versionId, String huellaDePregunta, IdiomaPreferido idioma, NivelDeDetalle detalle,
                        VistaDelChat vista) {

        public Clave {
            Objects.requireNonNull(versionId, "versionId");
            Objects.requireNonNull(huellaDePregunta, "huellaDePregunta");
            Objects.requireNonNull(idioma, "idioma");
            Objects.requireNonNull(detalle, "detalle");
        }

        /**
         * La clave de esa pregunta en esa versión, o {@code null} si no hay
         * versión en producción (sin versión no se guarda nada).
         */
        public static Clave de(UUID versionId, String pregunta, VistaDelChat vista,
                               PreferenciasDeRespuesta preferencias) {
            if (versionId == null) {
                return null;
            }
            PreferenciasDeRespuesta pedidas = preferencias == null ? PreferenciasDeRespuesta.POR_DEFECTO : preferencias;
            return new Clave(versionId, huella(NormalizadorTexto.normalizar(pregunta)), pedidas.idioma(),
                pedidas.nivelDetalle(), vista);
        }
    }

    /** Por qué salió una respuesta de la caché. */
    enum Desalojo { CAPACIDAD, CADUCIDAD, VERSION }

    private record Entrada(ResultadoMotor resultado, long venceEnMs) {
    }

    private final int maximo;
    private final long vigenciaMs;
    private final Clock reloj;
    private final Counter aciertos;
    private final Counter fallos;
    private final Map<Desalojo, Counter> desalojos = new EnumMap<>(Desalojo.class);

    // Orden de acceso: la primera es la usada hace mas tiempo. Todo acceso va
    // con el monitor de `entradas`; dentro solo hay operaciones en memoria
    // (nunca se calcula una respuesta con el monitor tomado).
    private final LinkedHashMap<Clave, Entrada> entradas = new LinkedHashMap<>(64, 0.75f, true);
    private UUID versionVigente;

    public CacheDeRespuestas(@Value("${chatbot.cache.respuestas.maximo:500}") int maximo,
                             @Value("${chatbot.cache.respuestas.minutos:30}") long minutos,
                             Clock reloj, MeterRegistry metricas) {
        this.maximo = Math.max(0, maximo);
        this.vigenciaMs = Duration.ofMinutes(Math.max(0, minutos)).toMillis();
        this.reloj = reloj;
        this.aciertos = Counter.builder("chatbot.cache.respuestas.aciertos")
            .description("Respuestas de la base de conocimiento servidas desde la cache (7.4.9)")
            .register(metricas);
        this.fallos = Counter.builder("chatbot.cache.respuestas.fallos")
            .description("Respuestas de la base de conocimiento que hubo que calcular (7.4.9)")
            .register(metricas);
        for (Desalojo causa : Desalojo.values()) {
            desalojos.put(causa, Counter.builder("chatbot.cache.respuestas.desalojos")
                .description("Respuestas que salieron de la cache, por causa (7.4.9)")
                .tag("causa", causa.name().toLowerCase(Locale.ROOT))
                .register(metricas));
        }
        Gauge.builder("chatbot.cache.respuestas.tamano", this, CacheDeRespuestas::tamano)
            .description("Respuestas de la base de conocimiento guardadas en la cache (7.4.9)")
            .register(metricas);
    }

    /**
     * La respuesta guardada con esa clave o, si no la hay (o venció), la que
     * calcula {@code calcular}, que se guarda. Con clave {@code null} o la
     * caché apagada, siempre calcula y no guarda nada.
     */
    public ResultadoMotor obtener(Clave clave, Supplier<ResultadoMotor> calcular) {
        if (clave == null || maximo == 0 || vigenciaMs == 0) {
            return calcular.get();
        }
        ResultadoMotor guardada = buscar(clave);
        if (guardada != null) {
            aciertos.increment();
            return guardada;
        }
        fallos.increment();
        ResultadoMotor calculada = calcular.get();
        guardar(clave, calculada);
        return calculada;
    }

    /** Cuántas respuestas hay guardadas (vigentes o no, hasta que se miren). */
    public int tamano() {
        synchronized (entradas) {
            return entradas.size();
        }
    }

    private ResultadoMotor buscar(Clave clave) {
        synchronized (entradas) {
            adoptarVersion(clave.versionId());
            Entrada entrada = entradas.get(clave);
            if (entrada == null) {
                return null;
            }
            if (entrada.venceEnMs() <= reloj.millis()) {
                entradas.remove(clave);
                desalojos.get(Desalojo.CADUCIDAD).increment();
                return null;
            }
            return entrada.resultado();
        }
    }

    private void guardar(Clave clave, ResultadoMotor resultado) {
        if (resultado == null) {
            return;
        }
        synchronized (entradas) {
            // Se calculo con una version que ya no es la de produccion: no vale.
            if (!clave.versionId().equals(versionVigente)) {
                return;
            }
            entradas.put(clave, new Entrada(resultado, reloj.millis() + vigenciaMs));
            Iterator<Map.Entry<Clave, Entrada>> masAntiguas = entradas.entrySet().iterator();
            while (entradas.size() > maximo && masAntiguas.hasNext()) {
                masAntiguas.next();
                masAntiguas.remove();
                desalojos.get(Desalojo.CAPACIDAD).increment();
            }
        }
    }

    // Una clave de otra version: la de produccion cambio y todo lo guardado
    // con la anterior se suelta de una vez.
    private void adoptarVersion(UUID versionId) {
        if (versionId.equals(versionVigente)) {
            return;
        }
        if (!entradas.isEmpty()) {
            desalojos.get(Desalojo.VERSION).increment(entradas.size());
            entradas.clear();
        }
        versionVigente = versionId;
    }

    static String huella(String preguntaNormalizada) {
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256")
                .digest(preguntaNormalizada.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumen);
        } catch (NoSuchAlgorithmException imposible) {
            throw new IllegalStateException("La JVM no trae SHA-256", imposible);
        }
    }
}
