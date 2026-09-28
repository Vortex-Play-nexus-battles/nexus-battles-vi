package com.nexusbattles.plataforma.torneos.integracion;

import com.nexusbattles.comun.seguridad.servicio.InterceptorDePortadorDeServicio;
import com.nexusbattles.plataforma.observabilidad.InterceptorDeTraza;
import com.nexusbattles.plataforma.torneos.torneo.EntregaDeInventario;
import com.nexusbattles.plataforma.torneos.torneo.FalloDeIntegracion;
import com.nexusbattles.plataforma.torneos.torneo.LibroDeCreditos;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Los clientes tal como los arma {@link ConfiguracionDeIntegraciones}, contra
 * un servidor HTTP de verdad (el del JDK) que hace de libro de creditos o de
 * inventario: se comprueba lo que un doble en memoria no puede, que el tiempo
 * de espera corta a un proveedor colgado y que la clave de idempotencia viaja
 * en la cabecera.
 */
@DisplayName("Torneos · clientes contra un servidor HTTP falso")
class ServidorFalsoTest {

    private HttpServer servidor;
    private final List<String> claves = new CopyOnWriteArrayList<>();
    private volatile long esperaMs;

    @BeforeEach
    void levantar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", intercambio -> {
            claves.add(String.valueOf(intercambio.getRequestHeaders().getFirst("Idempotency-Key")));
            try {
                Thread.sleep(esperaMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] cuerpo = "{\"reservaId\":\"%s\",\"estado\":\"ACTIVA\"}".formatted(UUID.randomUUID())
                    .getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(intercambio.getRequestURI().getPath().endsWith("/entregas") ? 201 : 200,
                    cuerpo.length);
            intercambio.getResponseBody().write(cuerpo);
            intercambio.close();
        });
        servidor.start();
    }

    @AfterEach
    void apagar() {
        servidor.stop(0);
    }

    private String base() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    private static <T> ObjectProvider<T> ninguno(Class<T> tipo) {
        return new StaticListableBeanFactory().getBeanProvider(tipo);
    }

    @Test
    @DisplayName("un proveedor que tarda mas que el tiempo de lectura se corta y el fallo es reintentable")
    void tiempoDeEspera() {
        ConfiguracionDeIntegraciones configuracion = new ConfiguracionDeIntegraciones();
        SimpleClientHttpRequestFactory fabrica = configuracion.fabricaDePeticiones(500, 300);
        LibroDeCreditos libro = configuracion.libroDeCreditos(base() + "/api/v1", fabrica,
                ninguno(InterceptorDeTraza.class), ninguno(InterceptorDePortadorDeServicio.class));
        esperaMs = 2_000;

        Instant inicio = Instant.now();
        assertThatThrownBy(() -> libro.consumir(UUID.randomUUID()))
                .isInstanceOfSatisfying(FalloDeIntegracion.class, f -> assertThat(f.reintentable()).isTrue());
        assertThat(Duration.between(inicio, Instant.now())).isLessThan(Duration.ofMillis(1_800));
    }

    @Test
    @DisplayName("la reserva y la entrega viajan con su Idempotency-Key; un proveedor rapido responde normal")
    void clavesEnLaCabecera() {
        ConfiguracionDeIntegraciones configuracion = new ConfiguracionDeIntegraciones();
        SimpleClientHttpRequestFactory fabrica = configuracion.fabricaDePeticiones(500, 1_000);
        LibroDeCreditos libro = configuracion.libroDeCreditos(base() + "/api/v1", fabrica,
                ninguno(InterceptorDeTraza.class), ninguno(InterceptorDePortadorDeServicio.class));
        EntregaDeInventario inventario = configuracion.entregaDeInventario(base(), fabrica,
                ninguno(InterceptorDeTraza.class), ninguno(InterceptorDePortadorDeServicio.class));
        esperaMs = 0;

        assertThat(libro.reservar(UUID.randomUUID(), 10, "torneo-t-jugador-j-inscripcion", "torneo-t").activa()).isTrue();
        inventario.entregarEpica(UUID.randomUUID(), "epica-1", "torneo-t", "torneo-t-jugador-j-epica");

        assertThat(claves).containsExactly("torneo-t-jugador-j-inscripcion", "torneo-t-jugador-j-epica");
    }
}
