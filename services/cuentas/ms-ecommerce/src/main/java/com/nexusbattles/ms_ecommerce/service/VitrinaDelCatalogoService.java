package com.nexusbattles.ms_ecommerce.service;

import com.nexusbattles.ms_ecommerce.catalogo.CatalogoMaestro;
import com.nexusbattles.ms_ecommerce.catalogo.CatalogoNoDisponibleException;
import com.nexusbattles.ms_ecommerce.catalogo.ProductoDelCatalogo;
import com.nexusbattles.ms_ecommerce.dto.PaginaDeVitrina;
import com.nexusbattles.ms_ecommerce.dto.ProductoEnVentaDto;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * La vitrina de la tienda: una proyeccion del catalogo maestro (RF-CAR-001).
 *
 * <p>Se vende lo que el catalogo ofrece (ACTIVO o UNICO), tiene precio en
 * moneda real y le quedan unidades. Lo demas no se ensena: un producto sin
 * precio en moneda real no aparece "a 0" (RF-CAR-002, RN-PAG-001), uno
 * agotado no se ofrece (RF-PRD-003) y uno suspendido tampoco (RN-PRD-004).
 *
 * <p><b>Copia de 30 segundos.</b> Cada pagina de la vitrina no puede costar
 * una ronda de peticiones al otro host, asi que la lista del catalogo se
 * guarda en memoria {@value #SEGUNDOS_DE_VIGENCIA} s. Es lo que tarda, como
 * mucho, en verse aqui un cambio o una suspension hecha en el catalogo.
 * Pasado ese tiempo se vuelve a leer, y si el catalogo no responde la vitrina
 * responde 503: no se sirve una copia caducada, porque podria ofrecer un
 * producto que ya se suspendio.
 *
 * <p>La copia no lleva cerrojo, a proposito: si el catalogo tarda, un cerrojo
 * pondria en fila a todas las peticiones que llegan y la ultima esperaria la
 * suma de todos los tiempos de espera — el borde la cortaria antes. Dos
 * lecturas simultaneas del catalogo son inocuas: leen lo mismo y gana la
 * ultima.
 */
@Service
public class VitrinaDelCatalogoService {

    static final long SEGUNDOS_DE_VIGENCIA = 30;
    static final Duration VIGENCIA_DE_LA_COPIA = Duration.ofSeconds(SEGUNDOS_DE_VIGENCIA);

    /** Sin conversion de moneda implementada (RF-CAR-002): el precio es el del catalogo, en COP. */
    static final String MONEDA = "COP";

    private final CatalogoMaestro catalogo;
    private final Clock reloj;
    private final AtomicReference<CopiaDelCatalogo> copia = new AtomicReference<>();

    public VitrinaDelCatalogoService(CatalogoMaestro catalogo, Clock reloj) {
        this.catalogo = catalogo;
        this.reloj = reloj;
    }

    /**
     * Una pagina de los productos en venta, en el orden del catalogo.
     *
     * @param numero pagina, desde 0
     * @param tamano productos por pagina, al menos 1
     * @param tipo   si viene, solo ese tipo de producto (sin distinguir mayusculas)
     * @throws CatalogoNoDisponibleException si no hay copia vigente y el
     *         catalogo no se pudo leer
     */
    public PaginaDeVitrina pagina(int numero, int tamano, String tipo) {
        List<ProductoEnVentaDto> enVenta = productosDelCatalogo().stream()
                .filter(VitrinaDelCatalogoService::seVende)
                .filter(producto -> esDelTipo(producto, tipo))
                .map(VitrinaDelCatalogoService::aProductoEnVenta)
                .toList();
        return PaginaDeVitrina.de(enVenta, numero, tamano);
    }

    private List<ProductoDelCatalogo> productosDelCatalogo() {
        CopiaDelCatalogo vigente = copia.get();
        if (vigente != null && reloj.instant().isBefore(vigente.caducaEn())) {
            return vigente.productos();
        }
        List<ProductoDelCatalogo> leidos = catalogo.productosEnVenta();
        copia.set(new CopiaDelCatalogo(leidos, reloj.instant().plus(VIGENCIA_DE_LA_COPIA)));
        return leidos;
    }

    /** Estado de venta, precio en moneda real y existencias: las tres a la vez. */
    static boolean seVende(ProductoDelCatalogo producto) {
        return producto.estaEnVenta() && producto.tienePrecioEnMonedaReal() && producto.tieneExistencias();
    }

    private static boolean esDelTipo(ProductoDelCatalogo producto, String tipo) {
        return tipo == null || tipo.isBlank() || tipo.strip().equalsIgnoreCase(producto.tipo());
    }

    static ProductoEnVentaDto aProductoEnVenta(ProductoDelCatalogo producto) {
        return new ProductoEnVentaDto(
                producto.id(),
                producto.nombre(),
                producto.imagen(),
                producto.descripcion(),
                textoDeHabilidades(producto.habilidades()),
                producto.tipo(),
                producto.precioMonedaReal(),
                // Sin promociones implementadas: el precio original es el mismo.
                producto.precioMonedaReal(),
                MONEDA,
                false,
                null,
                false,
                false);
    }

    /**
     * Las habilidades para mostrar. El catalogo las publica como texto o como
     * lista segun el tipo de producto, o no las publica: un texto se muestra
     * tal cual, una lista se une con ", " y lo demas (ausente, vacio, un
     * objeto) no se muestra.
     */
    static String textoDeHabilidades(Object habilidades) {
        String texto;
        if (habilidades instanceof String cadena) {
            texto = cadena;
        } else if (habilidades instanceof Collection<?> lista) {
            texto = lista.stream()
                    .filter(Objects::nonNull)
                    .filter(elemento -> !(elemento instanceof Map<?, ?>) && !(elemento instanceof Collection<?>))
                    .map(elemento -> String.valueOf(elemento).strip())
                    .filter(elemento -> !elemento.isEmpty())
                    .collect(Collectors.joining(", "));
        } else {
            texto = null;
        }
        return texto == null || texto.isBlank() ? null : texto;
    }

    private record CopiaDelCatalogo(List<ProductoDelCatalogo> productos, Instant caducaEn) {
    }
}
