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
import com.nexusbattles.ms_ecommerce.precios.PrecioEnCreditos;
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
 * <p>Se vende lo que el catalogo ofrece (ACTIVO o UNICO), le quedan unidades y
 * tiene algun precio: en moneda real o, desde G3 (4-oct), en creditos del
 * juego. Lo demas no se ensena: un producto sin precio no aparece "a 0"
 * (RF-CAR-002, RN-PAG-001), uno agotado no se ofrece (RF-PRD-003) y uno
 * suspendido tampoco (RN-PRD-004). Un premium sin precio en moneda real no se
 * convierte a creditos: no se vende.
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
                .filter(cotizado -> !consulta.enPromocion() || cotizado.enPromocion())
                .filter(cotizado -> dentroDelRango(cotizado.precioFinal(), consulta))
                .filter(cotizado -> busqueda.vacia()
                        || busqueda.coincide(textoIndexado(cotizado.producto()), cotizado.precioFinal()))
                .map(cotizado -> aProductoEnVenta(cotizado.producto(), cotizado.precio(), cotizado.creditos(),
                        suyos.contains(cotizado.producto().id()), enLaLista.contains(cotizado.producto().id())))
                .toList();
        List<String> disponibles = tasas.disponibles().stream().sorted().map(Moneda::name).toList();
        return PaginaDeVitrina.de(enVenta, consulta.numero(), consulta.tamano(), tarifa.moneda().name(), disponibles);
    }

    /**
     * Estado de venta, existencias y ALGUN precio: en dinero real o en creditos
     * (G3, 4-oct). Un producto que solo se vende en creditos sale con su precio
     * en creditos y sin precio en dinero real (nunca «a 0»); un premium sin
     * precio en dinero real no sale, porque no se convierte a creditos.
     */
    static boolean seVende(ProductoDelCatalogo producto) {
        return producto.estaEnVenta() && producto.tieneExistencias() && producto.tieneAlgunPrecio();
    }

    private static boolean esDelTipo(ProductoDelCatalogo producto, String tipo) {
        return tipo == null || tipo.isBlank() || tipo.strip().equalsIgnoreCase(producto.tipo());
    }

    /**
     * El rango es de precio en dinero real, en la moneda pedida. Un producto
     * que solo se vende en creditos no tiene ese precio: con un rango pedido no
     * entra, sin rango si.
     */
    private static boolean dentroDelRango(BigDecimal precio, ConsultaDeVitrina consulta) {
        if (precio == null) {
            return consulta.precioMinimo() == null && consulta.precioMaximo() == null;
        }
        if (consulta.precioMinimo() != null && precio.compareTo(consulta.precioMinimo()) < 0) {
            return false;
        }
        return consulta.precioMaximo() == null || precio.compareTo(consulta.precioMaximo()) <= 0;
    }

    /**
     * Sus precios: en dinero real si lo tiene, en creditos si se puede pagar
     * asi. Nada si no se puede calcular ninguno (un porcentaje fuera de 1..99
     * ya lo descarta la promocion; esto es el seguro de que un dato raro del
     * catalogo no tumbe la vitrina entera).
     */
    private static Stream<Cotizado> cotizado(ProductoDelCatalogo producto, Tarifa tarifa, Instant ahora) {
        PrecioEnCreditos creditos = CalculadoraDePrecios.enCreditos(producto, ahora).orElse(null);
        if (!producto.tienePrecioEnMonedaReal()) {
            return creditos == null ? Stream.empty() : Stream.of(new Cotizado(producto, null, creditos));
        }
        try {
            return Stream.of(new Cotizado(producto, CalculadoraDePrecios.deProducto(producto, tarifa, ahora),
                    creditos));
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

    /**
     * La tarjeta de la vitrina. {@code precioCreditos} (D-44, contrato 1.6.0)
     * sale del {@code precioCreditos} del catalogo con la promocion vigente en
     * el mismo instante que el precio en dinero real; null si el producto no se
     * puede pagar con creditos.
     *
     * <p>G3 (contrato 1.7.0): un producto que solo se vende en creditos sale con
     * {@code precioFinal}, {@code precioOriginal} y {@code moneda} a null, y la
     * promocion se lee de su precio en creditos (es el mismo porcentaje).
     *
     * @param precio   en dinero real; null si solo se vende en creditos
     * @param creditos en creditos; null si no se puede pagar asi
     */
    static ProductoEnVentaDto aProductoEnVenta(ProductoDelCatalogo producto, PrecioCalculado precio,
                                               PrecioEnCreditos creditos, boolean propio, boolean deseado) {
        Integer porcentaje = precio != null ? precio.porcentajeDescuento()
                : creditos == null ? null : creditos.porcentajeDescuento();
        return new ProductoEnVentaDto(
                producto.id(),
                producto.nombre(),
                producto.imagen(),
                producto.descripcion(),
                textoDeHabilidades(producto.habilidades()),
                producto.tipo(),
                precio == null ? null : precio.precioFinal(),
                precio == null ? null : precio.precioOriginal(),
                precio == null ? null : precio.moneda().name(),
                precio != null ? precio.enPromocion() : porcentaje != null,
                porcentaje,
                propio,
                deseado,
                creditos == null ? null : creditos.precioFinal());
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

    /** Un producto con sus precios: {@code precio} null si solo se vende en creditos. */
    private record Cotizado(ProductoDelCatalogo producto, PrecioCalculado precio, PrecioEnCreditos creditos) {

        BigDecimal precioFinal() {
            return precio == null ? null : precio.precioFinal();
        }

        boolean enPromocion() {
            return precio != null ? precio.enPromocion()
                    : creditos != null && creditos.porcentajeDescuento() != null;
        }
    }
}
