package com.nexusbattles.ms_subastas.subastas.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbattles.ms_subastas.pujas.service.MutableClock;
import com.nexusbattles.ms_subastas.subastas.dto.PublicarSubastaResponse;
import com.nexusbattles.ms_subastas.subastas.model.PublicacionIdempotente;
import com.nexusbattles.ms_subastas.subastas.port.IdempotenciaPublicacion;
import com.nexusbattles.ms_subastas.subastas.repository.PublicacionIdempotenteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * La idempotencia de {@code POST /subastas} en la base de datos (B8): sobrevive
 * a un reinicio y se comparte entre replicas, que es lo que el mapa en memoria
 * no hacia. Contra PostgreSQL real, porque lo que se prueba es el
 * {@code ON CONFLICT} y las transacciones propias.
 */
@SpringBootTest(properties = {
        "app.pujas.emision-automatica-intervalo-ms=3600000",
        "app.subastas.cierre-intervalo-ms=3600000",
        "app.notificaciones.drenaje-intervalo-ms=3600000",
        // B8: el drenador de correos tambien es un trabajo programado:
        // fuera del camino de la prueba.
        "app.correo.drenaje-intervalo-ms=3600000",
        "app.subastas.recordatorio-intervalo-ms=3600000",
        "app.subastas.pendientes-intervalo-ms=3600000"
})
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Idempotencia de la publicacion en la base de datos (B8)")
class IdempotenciaPublicacionEnBaseIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private IdempotenciaPublicacion idempotencia;

    @Autowired
    private PublicacionIdempotenteRepository repositorio;

    @Autowired
    private PlatformTransactionManager transacciones;

    @Autowired
    private ObjectMapper mapper;

    private static String clave() {
        return UUID.randomUUID() + ":k-" + UUID.randomUUID();
    }

    private static PublicarSubastaResponse respuesta() {
        Instant ahora = Instant.parse("2026-09-20T12:00:00Z");
        return new PublicarSubastaResponse(UUID.randomUUID(), UUID.randomUUID(), "elemento-1", UUID.randomUUID(),
                BigDecimal.TEN, BigDecimal.TEN, null, "ACTIVA", ahora, ahora.plus(Duration.ofHours(24)),
                BigDecimal.ONE, "Espada", "ARMA", "Rara", null, null, null, new BigDecimal("2.50"));
    }

    @Test
    @DisplayName("es la implementacion activa: la de memoria ya no se usa en produccion")
    void implementacionActiva() {
        assertInstanceOf(IdempotenciaPublicacionEnBase.class, idempotencia);
    }

    @Test
    @DisplayName("adquirir, confirmar y reproducir: el reintento recibe la misma respuesta")
    void confirmarYReproducir() {
        String clave = clave();
        var adquisicion = idempotencia.adquirir(clave, "huella-1");
        assertTrue(adquisicion.resultado().isEmpty());
        PublicarSubastaResponse respuesta = respuesta();

        idempotencia.confirmar(clave, adquisicion.titular(), respuesta);

        var reintento = idempotencia.adquirir(clave, "huella-1");
        assertEquals(respuesta, reintento.resultado().orElseThrow().respuesta());
        assertEquals(respuesta.id(), idempotencia.buscar(clave).orElseThrow().subastaId());
        assertEquals(PublicacionIdempotente.Estado.CONFIRMADA, repositorio.findById(clave).orElseThrow().getEstado());
    }

    @Test
    @DisplayName("la misma clave con otra solicitud es un conflicto")
    void otraSolicitud() {
        String clave = clave();
        idempotencia.adquirir(clave, "huella-1");

        PublicacionSubastaException error = assertThrows(PublicacionSubastaException.class,
                () -> idempotencia.adquirir(clave, "huella-2"));

        assertEquals(PublicacionSubastaException.Motivo.CONFLICTO, error.getMotivo());
        assertEquals(IdempotenciaPublicacionEnBase.OTRA_SOLICITUD, error.getMessage());
    }

    @Test
    @DisplayName("mientras una esta en curso, el duplicado recibe 409 y no publica otra vez")
    void enCurso() {
        String clave = clave();
        idempotencia.adquirir(clave, "huella-1");

        PublicacionSubastaException error = assertThrows(PublicacionSubastaException.class,
                () -> idempotencia.adquirir(clave, "huella-1"));

        assertEquals(PublicacionSubastaException.Motivo.CONFLICTO, error.getMotivo());
        assertEquals(IdempotenciaPublicacionEnBase.EN_CURSO, error.getMessage());
        assertTrue(idempotencia.buscar(clave).isEmpty(), "en curso no es un resultado");
    }

    @Test
    @DisplayName("liberar deja reintentar; solo el titular libera o confirma")
    void liberarYTitular() {
        String clave = clave();
        var mia = idempotencia.adquirir(clave, "huella-1");

        idempotencia.liberar(clave, UUID.randomUUID());
        idempotencia.confirmar(clave, UUID.randomUUID(), respuesta());
        assertEquals(PublicacionIdempotente.Estado.EN_CURSO, repositorio.findById(clave).orElseThrow().getEstado(),
                "otro titular no toca la clave");

        idempotencia.liberar(clave, mia.titular());
        assertTrue(repositorio.findById(clave).isEmpty());
        assertTrue(idempotencia.adquirir(clave, "huella-1").resultado().isEmpty(), "se puede volver a intentar");
    }

    @Test
    @DisplayName("un resultado incierto no se adivina: pide conciliacion")
    void incierta() {
        String clave = clave();
        var mia = idempotencia.adquirir(clave, "huella-1");

        idempotencia.marcarIncierta(clave, mia.titular());

        PublicacionSubastaException error = assertThrows(PublicacionSubastaException.class,
                () -> idempotencia.adquirir(clave, "huella-1"));
        assertEquals(PublicacionSubastaException.Motivo.DEPENDENCIA_NO_DISPONIBLE, error.getMotivo());
        assertEquals(IdempotenciaPublicacionEnBase.INCIERTA, error.getMessage());
    }

    @Test
    @DisplayName("una clave que quedo EN_CURSO (el proceso murio) pasa a INCIERTA tras el plazo")
    void enCursoCaducada() {
        MutableClock reloj = new MutableClock(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        IdempotenciaPublicacionEnBase conReloj = new IdempotenciaPublicacionEnBase(repositorio, transacciones, mapper,
                reloj, 120);
        String clave = clave();
        conReloj.adquirir(clave, "huella-1");

        reloj.avanzar(Duration.ofSeconds(119));
        assertEquals(PublicacionSubastaException.Motivo.CONFLICTO,
                assertThrows(PublicacionSubastaException.class, () -> conReloj.adquirir(clave, "huella-1")).getMotivo());

        reloj.avanzar(Duration.ofSeconds(2));
        assertEquals(PublicacionSubastaException.Motivo.DEPENDENCIA_NO_DISPONIBLE,
                assertThrows(PublicacionSubastaException.class, () -> conReloj.adquirir(clave, "huella-1")).getMotivo());
        assertEquals(PublicacionIdempotente.Estado.INCIERTA, repositorio.findById(clave).orElseThrow().getEstado());
    }

    @Test
    @DisplayName("una respuesta guardada que no se puede leer tampoco se reproduce ni se republica")
    void respuestaIlegible() {
        String clave = clave();
        var mia = idempotencia.adquirir(clave, "huella-1");
        idempotencia.confirmar(clave, mia.titular(), respuesta());
        PublicacionIdempotente fila = repositorio.findById(clave).orElseThrow();
        fila.setRespuesta("{ilegible");
        repositorio.saveAndFlush(fila);

        assertEquals(PublicacionSubastaException.Motivo.DEPENDENCIA_NO_DISPONIBLE,
                assertThrows(PublicacionSubastaException.class, () -> idempotencia.adquirir(clave, "huella-1"))
                        .getMotivo());
    }

    @Test
    @DisplayName("diez intentos simultaneos con la misma clave: uno adquiere, nueve reciben conflicto")
    void concurrencia() throws Exception {
        String clave = clave();
        int intentos = 10;
        ExecutorService hilos = Executors.newFixedThreadPool(intentos);
        try {
            CountDownLatch salida = new CountDownLatch(1);
            List<Future<Object>> resultados = new ArrayList<>();
            for (int i = 0; i < intentos; i++) {
                Callable<Object> intento = () -> {
                    salida.await();
                    try {
                        return idempotencia.adquirir(clave, "huella-1");
                    } catch (PublicacionSubastaException conflicto) {
                        return conflicto;
                    }
                };
                resultados.add(hilos.submit(intento));
            }
            salida.countDown();
            int adquiridas = 0;
            int conflictos = 0;
            for (Future<Object> resultado : resultados) {
                Object valor = resultado.get(20, TimeUnit.SECONDS);
                if (valor instanceof IdempotenciaPublicacion.Adquisicion) {
                    adquiridas++;
                } else {
                    assertEquals(PublicacionSubastaException.Motivo.CONFLICTO,
                            ((PublicacionSubastaException) valor).getMotivo());
                    conflictos++;
                }
            }
            assertEquals(1, adquiridas);
            assertEquals(intentos - 1, conflictos);
        } finally {
            hilos.shutdownNow();
        }
    }
}
