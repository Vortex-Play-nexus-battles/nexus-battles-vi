package com.nexusbattles.ms_ecommerce.service;

import com.nexusbattles.ms_ecommerce.catalogo.CatalogoNoDisponibleException;
import com.nexusbattles.ms_ecommerce.catalogo.CopiaDelCatalogo;
import com.nexusbattles.ms_ecommerce.catalogo.ProductoDelCatalogo;
import com.nexusbattles.ms_ecommerce.dto.ConsultaDeVitrina;
import com.nexusbattles.ms_ecommerce.dto.PaginaDeVitrina;
import com.nexusbattles.ms_ecommerce.dto.ProductoEnVentaDto;
import com.nexusbattles.ms_ecommerce.integracion.inventario.ProductosPropios;
import com.nexusbattles.ms_ecommerce.precios.CalculadoraDePrecios;
import com.nexusbattles.ms_ecommerce.precios.Moneda;
import com.nexusbattles.ms_ecommerce.precios.MonedaNoDisponibleException;
import com.nexusbattles.ms_ecommerce.precios.PrecioCalculado;
import com.nexusbattles.ms_ecommerce.precios.Tarifa;
import com.nexusbattles.ms_ecommerce.precios.TasasDeCambio;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * La vitrina de la tienda: una proyeccion del catalogo maestro (RF-CAR-001).
 *
 * <p>Se vende lo que el catalogo ofrece (ACTIVO o UNICO), tiene precio en
 * moneda real y le quedan unidades. Lo demas no se ensena: un producto sin
 * precio en moneda real no aparece "a 0" (RF-CAR-002, RN-PAG-001), uno
 * agotado no se ofrece (RF-PRD-003) y uno suspendido tampoco (RN-PRD-004).
 *
 * <p><b>B5 — la vitrina del 7.5.</b> Cada precio lo calcula
 * {@link CalculadoraDePrecios} en la moneda pedida, con la promocion vigente
 * aplicada; se filtra por tipo, rango de precio (en esa moneda) y promocion, y
 * se busca por texto. Con sesion de usuario se marcan los productos de su
 * lista de deseos y los que ya tiene en el inventario; si el inventario no
 * responde, la vitrina sale igual, sin esa marca ({@link ProductosPropios}).
 *
 * <p>El catalogo se lee de {@link CopiaDelCatalogo} (30 segundos): es lo que
 * tarda, como mucho, en verse aqui un cambio o una suspension hecha en el
 * catalogo, y si no responde al caducar la vitrina responde 503.
 */
@Service
public class VitrinaDelCatalogoService {

    private final CopiaDelCatalogo copia;
    private final TasasDeCambio tasas;
    private final ListaDeDeseosService deseos;
    private final ProductosPropios propios;
    private final Clock reloj;

    public VitrinaDelCatalogoService(CopiaDelCatalogo copia, TasasDeCambio tasas, ListaDeDeseosService deseos,
                                     ProductosPropios propios, Clock reloj) {
        this.copia = copia;
        this.tasas = tasas;
        this.deseos = deseos;
        this.propios = propios;
        this.reloj = reloj;
    }

    /** Una pagina en COP, sin filtros ni sesion: la vitrina de antes de 1.4.0. */
    public PaginaDeVitrina pagina(int numero, int tamano, String tipo) {
        return pagina(ConsultaDeVitrina.de(numero, tamano, tipo), null);
    }

    /**
     * Una pagina de los productos en venta, en el orden del catalogo.
     *
     * @param usuarioId el {@code uid} del jugador si la peticion trae su sesion;
     *                  null sin sesion (vitrina publica, sin marcas)
     * @throws MonedaNoDisponibleException si se pide USD o EUR sin tasa
     * @throws CatalogoNoDisponibleException si no hay copia vigente y el
     *         catalogo no se pudo leer
     */
    public PaginaDeVitrina pagina(ConsultaDeVitrina consulta, String usuarioId) {
        Tarifa tarifa = tasas.tarifa(consulta.moneda());
        List<ProductoDelCatalogo> productos = copia.productos();
        Instant ahora = reloj.instant();
        Set<String> enLaLista = usuarioId == null ? Set.of() : deseos.productosDe(usuarioId);
        Set<String> suyos = usuarioId == null ? Set.of() : propios.de(usuarioId);
        BusquedaEnLaVitrina busqueda = BusquedaEnLaVitrina.de(consulta.busqueda());

        List<ProductoEnVentaDto> enVenta = productos.stream()
                .filter(VitrinaDelCatalogoService::seVende)
                .filter(producto -> esDelTipo(producto, consulta.tipo()))
                .flatMap(producto -> cotizado(producto, tarifa, ahora))
                .filter(cotizado -> !consulta.enPromocion() || cotizado.precio().enPromocion())
                .filter(cotizado -> dentroDelRango(cotizado.precio().precioFinal(), consulta))
                .filter(cotizado -> busqueda.vacia()
                        || busqueda.coincide(textoIndexado(cotizado.producto()), cotizado.precio().precioFinal()))
                .map(cotizado -> aProductoEnVenta(cotizado.producto(), cotizado.precio(),
                        suyos.contains(cotizado.producto().id()), enLaLista.contains(cotizado.producto().id())))
                .toList();
        List<String> disponibles = tasas.disponibles().stream().sorted().map(Moneda::name).toList();
        return PaginaDeVitrina.de(enVenta, consulta.numero(), consulta.tamano(), tarifa.moneda().name(), disponibles);
    }

    /** Estado de venta, precio en moneda real y existencias: las tres a la vez. */
    static boolean seVende(ProductoDelCatalogo producto) {
        return producto.estaEnVenta() && producto.tienePrecioEnMonedaReal() && producto.tieneExistencias();
    }

    private static boolean esDelTipo(ProductoDelCatalogo producto, String tipo) {
        return tipo == null || tipo.isBlank() || tipo.strip().equalsIgnoreCase(producto.tipo());
    }

    private static boolean dentroDelRango(BigDecimal precio, ConsultaDeVitrina consulta) {
        if (consulta.precioMinimo() != null && precio.compareTo(consulta.precioMinimo()) < 0) {
            return false;
        }
        return consulta.precioMaximo() == null || precio.compareTo(consulta.precioMaximo()) <= 0;
    }

    /**
     * El precio del producto, o nada si no se puede calcular (un porcentaje
     * fuera de 1..99 ya lo descarta la promocion; esto es el seguro de que un
     * dato raro del catalogo no tumbe la vitrina entera).
     */
    private static Stream<Cotizado> cotizado(ProductoDelCatalogo producto, Tarifa tarifa, Instant ahora) {
        try {
            return Stream.of(new Cotizado(producto, CalculadoraDePrecios.deProducto(producto, tarifa, ahora)));
        } catch (IllegalArgumentException datoRaro) {
            return Stream.empty();
        }
    }

    /** El texto en que se busca: nombre, descripcion, habilidades y tipo, normalizados. */
    static String textoIndexado(ProductoDelCatalogo producto) {
        return Stream.of(producto.nombre(), producto.descripcion(), textoDeHabilidades(producto.habilidades()),
                        producto.tipo())
                .filter(Objects::nonNull)
                .map(BusquedaEnLaVitrina::normalizar)
                .collect(Collectors.joining(" "));
    }

    static ProductoEnVentaDto aProductoEnVenta(ProductoDelCatalogo producto, PrecioCalculado precio,
                                               boolean propio, boolean deseado) {
        return new ProductoEnVentaDto(
                producto.id(),
                producto.nombre(),
                producto.imagen(),
                producto.descripcion(),
                textoDeHabilidades(producto.habilidades()),
                producto.tipo(),
                precio.precioFinal(),
                precio.precioOriginal(),
                precio.moneda().name(),
                precio.enPromocion(),
                precio.porcentajeDescuento(),
                propio,
                deseado);
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

    private record Cotizado(ProductoDelCatalogo producto, PrecioCalculado precio) {
    }
}
