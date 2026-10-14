package nexus.inventario.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IndiceDeBusquedaDelCatalogoTest {

    private Instant ahora = Instant.parse("2026-10-13T20:00:00Z");
    private final List<Runnable> pendientes = new ArrayList<>();
    private final AtomicInteger lecturas = new AtomicInteger();
    private volatile Map<String, String> catalogo = Map.of("p-1", "Amuleto de Bruma");
    private volatile boolean caido;

    private final Clock reloj = new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    };

    private final FuenteDelCatalogo fuente = () -> {
        lecturas.incrementAndGet();
        if (caido) {
            throw new ResolutorDeProductoException("productos no responde");
        }
        return catalogo;
    };

    private final IndiceDeBusquedaDelCatalogo indice = new IndiceDeBusquedaDelCatalogo(
            fuente, reloj, Duration.ofMinutes(5), Duration.ofSeconds(30), pendientes::add);

    @Test
    @DisplayName("la primera búsqueda construye el índice y las siguientes lo reutilizan")
    void construyeUnaVez() {
        assertEquals(Set.of("p-1"), indice.productosQueCoinciden("bruma"));
        assertEquals(Set.of("p-1"), indice.productosQueCoinciden("amul"));

        assertEquals(1, lecturas.get());
    }

    @Test
    @DisplayName("pasada la vigencia se sigue sirviendo el índice y se renueva en segundo plano")
    void renuevaEnSegundoPlano() {
        indice.productosQueCoinciden("bruma");
        catalogo = Map.of("p-2", "Escudo de dragón");
        ahora = ahora.plus(Duration.ofMinutes(6));

        // Nadie espera: todavía responde el índice anterior...
        assertEquals(Set.of("p-1"), indice.productosQueCoinciden("bruma"));
        assertEquals(1, pendientes.size());
        // ...y una sola renovación en curso, por muchas búsquedas que lleguen.
        indice.productosQueCoinciden("bruma");
        assertEquals(1, pendientes.size());

        pendientes.removeFirst().run();
        assertEquals(Set.of("p-2"), indice.productosQueCoinciden("drag"));
        assertTrue(indice.productosQueCoinciden("bruma").isEmpty());
    }

    @Test
    @DisplayName("con el catálogo caído la búsqueda no falla y no se martillea al servicio")
    void catalogoCaido() {
        caido = true;

        assertTrue(indice.productosQueCoinciden("bruma").isEmpty());
        assertTrue(indice.productosQueCoinciden("bruma").isEmpty());
        assertEquals(1, lecturas.get());

        caido = false;
        ahora = ahora.plus(Duration.ofSeconds(31));
        assertEquals(Set.of("p-1"), indice.productosQueCoinciden("bruma"));
        assertEquals(2, lecturas.get());
    }

    @Test
    @DisplayName("si la renovación falla se conserva el índice anterior")
    void renovacionFallidaConservaElAnterior() {
        indice.productosQueCoinciden("bruma");
        caido = true;
        ahora = ahora.plus(Duration.ofMinutes(6));

        indice.productosQueCoinciden("bruma");
        pendientes.removeFirst().run();

        assertEquals(Set.of("p-1"), indice.productosQueCoinciden("bruma"));
    }

    @Test
    @DisplayName("una renovación fallida no se reintenta en cada búsqueda: espera el respiro")
    void renovacionFallidaRespetaElRespiro() {
        indice.productosQueCoinciden("bruma");
        caido = true;
        ahora = ahora.plus(Duration.ofMinutes(6));

        indice.productosQueCoinciden("bruma");
        pendientes.removeFirst().run();
        int lecturasTrasElFallo = lecturas.get();

        // Otra búsqueda en seguida: ni una renovación nueva ni otra lectura.
        indice.productosQueCoinciden("bruma");
        assertTrue(pendientes.isEmpty());
        assertEquals(lecturasTrasElFallo, lecturas.get());

        // Pasado el respiro, se vuelve a intentar y, con el catálogo de vuelta, se renueva.
        caido = false;
        catalogo = Map.of("p-2", "Escudo de dragón");
        ahora = ahora.plus(Duration.ofSeconds(31));
        indice.productosQueCoinciden("bruma");
        assertEquals(1, pendientes.size());
        pendientes.removeFirst().run();
        assertEquals(Set.of("p-2"), indice.productosQueCoinciden("drag"));
    }

    @Test
    @DisplayName("muchas primeras búsquedas a la vez, en hilos virtuales, construyen el índice una sola vez")
    void primerasBusquedasConcurrentes() throws Exception {
        java.util.concurrent.CountDownLatch dentro = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch soltar = new java.util.concurrent.CountDownLatch(1);
        FuenteDelCatalogo lenta = () -> {
            lecturas.incrementAndGet();
            dentro.countDown();
            try {
                soltar.await();
            } catch (InterruptedException interrumpido) {
                Thread.currentThread().interrupt();
            }
            return catalogo;
        };
        IndiceDeBusquedaDelCatalogo concurrido = new IndiceDeBusquedaDelCatalogo(
                lenta, reloj, Duration.ofMinutes(5), Duration.ofSeconds(30), pendientes::add);

        List<Thread> hilos = new ArrayList<>();
        List<Set<String>> resultados = java.util.Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < 8; i++) {
            hilos.add(Thread.ofVirtual().start(() -> resultados.add(concurrido.productosQueCoinciden("bruma"))));
        }
        dentro.await();
        soltar.countDown();
        for (Thread hilo : hilos) {
            hilo.join(5_000);
        }

        assertEquals(1, lecturas.get());
        assertEquals(8, resultados.size());
        resultados.forEach(resultado -> assertEquals(Set.of("p-1"), resultado));
    }
}
