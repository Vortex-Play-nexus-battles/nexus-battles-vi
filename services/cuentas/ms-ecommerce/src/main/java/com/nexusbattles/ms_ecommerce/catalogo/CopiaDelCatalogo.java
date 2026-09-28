package com.nexusbattles.ms_ecommerce.catalogo;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Copia en memoria del listado del catalogo maestro, de
 * {@value #SEGUNDOS_DE_VIGENCIA} segundos.
 *
 * <p>Nacio dentro de la vitrina (R16) y sale de ella en B5 porque ahora la
 * leen tres: la vitrina (que productos se venden y a que precio), el carrito
 * (el precio y la disponibilidad de cada linea en cada respuesta) y la lista
 * de deseos (nombre e imagen de lo guardado). Cada una de esas respuestas no
 * puede costar una ronda de peticiones al otro host.
 *
 * <p>Las reglas no cambian: pasado ese tiempo se vuelve a leer, y si el
 * catalogo no responde no se sirve una copia caducada, porque podria ofrecer
 * un producto que ya se suspendio. Quien necesita seguir funcionando sin
 * catalogo (el carrito) usa {@link #siDisponible()} y lo dice.
 *
 * <p>Lo que la copia NO decide es lo que se cobra: la compra vuelve a leer cada
 * producto del catalogo en el momento de pagar ({@link CatalogoMaestro#producto}).
 *
 * <p>Sin cerrojo, a proposito: si el catalogo tarda, un cerrojo pondria en
 * fila a todas las peticiones que llegan y la ultima esperaria la suma de
 * todos los tiempos de espera. Dos lecturas simultaneas son inocuas: leen lo
 * mismo y gana la ultima.
 */
@Component
public class CopiaDelCatalogo {

    public static final long SEGUNDOS_DE_VIGENCIA = 30;
    static final Duration VIGENCIA = Duration.ofSeconds(SEGUNDOS_DE_VIGENCIA);

    private final CatalogoMaestro catalogo;
    private final Clock reloj;
    private final AtomicReference<Copia> copia = new AtomicReference<>();

    public CopiaDelCatalogo(CatalogoMaestro catalogo, Clock reloj) {
        this.catalogo = catalogo;
        this.reloj = reloj;
    }

    /**
     * Los productos del listado del catalogo, en su orden.
     *
     * @throws CatalogoNoDisponibleException si no hay copia vigente y el
     *         catalogo no se pudo leer
     */
    public List<ProductoDelCatalogo> productos() {
        return vigente().productos();
    }

    /** Los productos por id; vacio si el catalogo no responde, sin lanzar. */
    public Optional<Map<String, ProductoDelCatalogo>> siDisponible() {
        try {
            return Optional.of(vigente().porId());
        } catch (CatalogoNoDisponibleException caido) {
            return Optional.empty();
        }
    }

    private Copia vigente() {
        Copia actual = copia.get();
        if (actual != null && reloj.instant().isBefore(actual.caducaEn())) {
            return actual;
        }
        List<ProductoDelCatalogo> leidos = catalogo.productosEnVenta();
        Copia nueva = new Copia(leidos, porId(leidos), reloj.instant().plus(VIGENCIA));
        copia.set(nueva);
        return nueva;
    }

    private static Map<String, ProductoDelCatalogo> porId(List<ProductoDelCatalogo> productos) {
        Map<String, ProductoDelCatalogo> indice = new LinkedHashMap<>();
        for (ProductoDelCatalogo producto : productos) {
            if (producto.id() != null) {
                indice.putIfAbsent(producto.id(), producto);
            }
        }
        return Map.copyOf(indice);
    }

    private record Copia(List<ProductoDelCatalogo> productos, Map<String, ProductoDelCatalogo> porId,
                         Instant caducaEn) {
    }
}
