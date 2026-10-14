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
}
