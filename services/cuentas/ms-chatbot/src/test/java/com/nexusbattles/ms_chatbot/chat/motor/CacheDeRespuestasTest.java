package com.nexusbattles.ms_chatbot.chat.motor;

import com.nexusbattles.ms_chatbot.chat.enriquecido.VistaDelChat;
import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.motor.model.TipoRespuesta;
import com.nexusbattles.ms_chatbot.chat.preferencias.IdiomaPreferido;
import com.nexusbattles.ms_chatbot.chat.preferencias.NivelDeDetalle;
import com.nexusbattles.ms_chatbot.chat.preferencias.PreferenciasDeRespuesta;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

// 7.4.9 «Sistema de cache para respuestas frecuentes» (ms-chatbot.yaml 1.9.0).
class CacheDeRespuestasTest {

    private static final UUID V1 = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID V2 = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final PreferenciasDeRespuesta POR_DEFECTO = PreferenciasDeRespuesta.POR_DEFECTO;

    private final AtomicReference<Instant> ahora = new AtomicReference<>(Instant.parse("2026-10-14T10:00:00Z"));
    private final Clock reloj = new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora.get();
        }
    };
    private final SimpleMeterRegistry metricas = new SimpleMeterRegistry();
    private final AtomicInteger calculadas = new AtomicInteger();

    private CacheDeRespuestas cache(int maximo) {
        return new CacheDeRespuestas(maximo, 30, reloj, metricas);
    }

    private ResultadoMotor respuesta(String texto) {
        calculadas.incrementAndGet();
        return ResultadoMotor.deTema(texto, Categoria.SUBASTA_Y_COMERCIO, TipoRespuesta.DIRECTA, "publicar-subasta");
    }

    private static CacheDeRespuestas.Clave clave(UUID version, String pregunta) {
        return CacheDeRespuestas.Clave.de(version, pregunta, null, POR_DEFECTO);
    }

    private double contador(String nombre) {
        return metricas.counter(nombre).count();
    }

    private double desalojos(String causa) {
        return metricas.counter("chatbot.cache.respuestas.desalojos", "causa", causa).count();
    }

    @Test
    void laMismaPreguntaSeSirveDeLaCacheSinCalcularOtraVez() {
        CacheDeRespuestas cache = cache(10);

        ResultadoMotor primera = cache.obtener(clave(V1, "como publico una subasta"), () -> respuesta("Desde Subasta."));
        ResultadoMotor segunda = cache.obtener(clave(V1, "como publico una subasta"), () -> respuesta("otra"));

        assertThat(segunda).isSameAs(primera);
        assertThat(calculadas).hasValue(1);
        assertThat(contador("chatbot.cache.respuestas.fallos")).isEqualTo(1.0);
        assertThat(contador("chatbot.cache.respuestas.aciertos")).isEqualTo(1.0);
        assertThat(metricas.get("chatbot.cache.respuestas.tamano").gauge().value()).isEqualTo(1.0);
    }

    // La clave es la pregunta como la lee el motor: sin tildes, mayusculas ni signos.
    @Test
    void tildesMayusculasYSignosNoCambianLaPregunta() {
        CacheDeRespuestas cache = cache(10);

        cache.obtener(clave(V1, "¿Cómo publico una SUBASTA?"), () -> respuesta("Desde Subasta."));
        cache.obtener(clave(V1, "como publico una subasta"), () -> respuesta("otra"));

        assertThat(calculadas).hasValue(1);
    }

    @Test
    void cadaIdiomaNivelDeDetalleYVistaEsOtraRespuesta() {
        CacheDeRespuestas cache = cache(10);
        String pregunta = "como publico una subasta";

        cache.obtener(CacheDeRespuestas.Clave.de(V1, pregunta, null, POR_DEFECTO), () -> respuesta("a"));
        cache.obtener(CacheDeRespuestas.Clave.de(V1, pregunta, null,
            new PreferenciasDeRespuesta(IdiomaPreferido.EN, NivelDeDetalle.NORMAL)), () -> respuesta("b"));
        cache.obtener(CacheDeRespuestas.Clave.de(V1, pregunta, null,
            new PreferenciasDeRespuesta(IdiomaPreferido.AUTOMATICO, NivelDeDetalle.BREVE)), () -> respuesta("c"));
        cache.obtener(CacheDeRespuestas.Clave.de(V1, pregunta, VistaDelChat.SUBASTAS, POR_DEFECTO),
            () -> respuesta("d"));

        assertThat(calculadas).hasValue(4);
        assertThat(cache.tamano()).isEqualTo(4);
    }

    @Test
    void alLlenarseSaleLaUsadaHaceMasTiempo() {
        CacheDeRespuestas cache = cache(2);
        cache.obtener(clave(V1, "a"), () -> respuesta("A"));
        cache.obtener(clave(V1, "b"), () -> respuesta("B"));
        cache.obtener(clave(V1, "a"), () -> respuesta("A otra vez"));   // "a" pasa a ser la mas reciente

        cache.obtener(clave(V1, "c"), () -> respuesta("C"));             // sale "b"

        assertThat(cache.tamano()).isEqualTo(2);
        assertThat(desalojos("capacidad")).isEqualTo(1.0);
        calculadas.set(0);
        cache.obtener(clave(V1, "a"), () -> respuesta("A"));
        assertThat(calculadas).as("«a» sigue guardada").hasValue(0);
        cache.obtener(clave(V1, "b"), () -> respuesta("B"));
        assertThat(calculadas).as("«b» salio al llenarse").hasValue(1);
    }

    @Test
    void unaRespuestaValeSusMinutosYDespuesSeCalculaOtraVez() {
        CacheDeRespuestas cache = cache(10);
        cache.obtener(clave(V1, "a"), () -> respuesta("A"));

        ahora.set(ahora.get().plus(Duration.ofMinutes(29)));
        cache.obtener(clave(V1, "a"), () -> respuesta("A"));
        assertThat(calculadas).hasValue(1);

        ahora.set(ahora.get().plus(Duration.ofMinutes(1)));
        cache.obtener(clave(V1, "a"), () -> respuesta("A"));
        assertThat(calculadas).hasValue(2);
        assertThat(desalojos("caducidad")).isEqualTo(1.0);
    }

    // Desplegar o revertir la base cambia la version en produccion: lo de la
    // anterior no se encuentra (esta en la clave) y se descarta de una vez.
    @Test
    void alCambiarLaVersionEnProduccionSeDescartaTodoLoDeLaAnterior() {
        CacheDeRespuestas cache = cache(10);
        cache.obtener(clave(V1, "a"), () -> respuesta("A con la v1"));
        cache.obtener(clave(V1, "b"), () -> respuesta("B con la v1"));

        ResultadoMotor conLaNueva = cache.obtener(clave(V2, "a"), () -> respuesta("A con la v2"));

        assertThat(conLaNueva.texto()).isEqualTo("A con la v2");
        assertThat(cache.tamano()).isEqualTo(1);
        assertThat(desalojos("version")).isEqualTo(2.0);

        // Una reversion a la v1 vuelve a empezar: lo que habia ya se descarto.
        cache.obtener(clave(V1, "a"), () -> respuesta("A con la v1 otra vez"));
        assertThat(calculadas).hasValue(4);
    }

    @Test
    void unaRespuestaCalculadaConLaVersionAnteriorNoSeGuarda() {
        CacheDeRespuestas cache = cache(10);

        // Mientras se calcula con la v1, otro mensaje ya ve la v2.
        cache.obtener(clave(V1, "a"), () -> {
            cache.obtener(clave(V2, "b"), () -> respuesta("B con la v2"));
            return respuesta("A con la v1");
        });

        assertThat(cache.tamano()).isEqualTo(1);
        calculadas.set(0);
        cache.obtener(clave(V2, "b"), () -> respuesta("B"));
        assertThat(calculadas).hasValue(0);
    }

    @Test
    void sinVersionEnProduccionNoSeGuardaNada() {
        CacheDeRespuestas cache = cache(10);

        assertThat(CacheDeRespuestas.Clave.de(null, "a", null, POR_DEFECTO)).isNull();
        cache.obtener(null, () -> respuesta("A"));
        cache.obtener(null, () -> respuesta("A"));

        assertThat(calculadas).hasValue(2);
        assertThat(cache.tamano()).isZero();
        assertThat(contador("chatbot.cache.respuestas.aciertos")).isZero();
    }

    @Test
    void conCeroEntradasLaCacheNoGuarda() {
        CacheDeRespuestas apagada = cache(0);
        apagada.obtener(clave(V1, "a"), () -> respuesta("A"));
        apagada.obtener(clave(V1, "a"), () -> respuesta("A"));

        assertThat(calculadas).hasValue(2);
        assertThat(apagada.tamano()).isZero();
    }

    // En memoria no queda lo que escribio nadie: solo la huella de la forma normalizada.
    @Test
    void laClaveNoGuardaElTextoDeLaPregunta() {
        CacheDeRespuestas.Clave clave = clave(V1, "Mi apodo es Lyra y quiero publicar una subasta");

        assertThat(clave.huellaDePregunta()).matches("[0-9a-f]{64}");
        assertThat(clave.toString()).doesNotContainIgnoringCase("lyra").doesNotContain("subasta");
        assertThat(clave(V1, "x".repeat(4000)).huellaDePregunta()).hasSize(64);
    }

    @Test
    void variosHilosALaVezNoRompenLaCacheNiSePasanDelMaximo() throws Exception {
        CacheDeRespuestas cache = cache(50);
        ExecutorService hilos = Executors.newFixedThreadPool(8);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            List<Future<?>> tareas = new ArrayList<>();
            for (int h = 0; h < 8; h++) {
                int hilo = h;
                tareas.add(hilos.submit(() -> {
                    salida.await();
                    for (int i = 0; i < 500; i++) {
                        UUID version = (i + hilo) % 97 == 0 ? V2 : V1;
                        cache.obtener(clave(version, "pregunta " + (i % 120)), () -> respuesta("R"));
                    }
                    return null;
                }));
            }
            salida.countDown();
            for (Future<?> tarea : tareas) {
                tarea.get(30, TimeUnit.SECONDS);
            }
        } finally {
            hilos.shutdownNow();
        }
        assertThat(cache.tamano()).isBetween(0, 50);
        assertThat(contador("chatbot.cache.respuestas.aciertos") + contador("chatbot.cache.respuestas.fallos"))
            .isEqualTo(8 * 500.0);
    }
}
