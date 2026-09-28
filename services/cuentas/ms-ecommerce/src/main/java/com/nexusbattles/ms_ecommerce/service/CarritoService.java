package com.nexusbattles.ms_ecommerce.service;

import com.nexusbattles.ms_ecommerce.catalogo.CatalogoMaestro;
import com.nexusbattles.ms_ecommerce.catalogo.CatalogoNoDisponibleException;
import com.nexusbattles.ms_ecommerce.catalogo.CopiaDelCatalogo;
import com.nexusbattles.ms_ecommerce.catalogo.ProductoDelCatalogo;
import com.nexusbattles.ms_ecommerce.dto.AgregarItemRequest;
import com.nexusbattles.ms_ecommerce.dto.CarritoDto;
import com.nexusbattles.ms_ecommerce.model.Carrito;
import com.nexusbattles.ms_ecommerce.model.ItemCarrito;
import com.nexusbattles.ms_ecommerce.precios.CalculadoraDePrecios;
import com.nexusbattles.ms_ecommerce.precios.Moneda;
import com.nexusbattles.ms_ecommerce.precios.MonedaNoDisponibleException;
import com.nexusbattles.ms_ecommerce.precios.Tarifa;
import com.nexusbattles.ms_ecommerce.precios.TasasDeCambio;
import com.nexusbattles.ms_ecommerce.repository.CarritoRepository;
import com.nexusbattles.ms_ecommerce.service.ProductoNoAgregableException.Motivo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * El carrito del jugador, sobre el catalogo maestro (R16) y con precio del
 * servidor (B5).
 *
 * <p>Cada producto que entra se resuelve contra el catalogo del servicio
 * productos —el que vende la tienda— y la linea guarda una instantanea: la
 * referencia del catalogo, el nombre y el ultimo precio en COP (el que se
 * ensena si el catalogo no responde). Cada respuesta sale de
 * {@link CotizadorDelCarrito}, que recalcula precios y total con el catalogo
 * de ese momento y en la moneda pedida.
 *
 * <p><b>Nada de HTTP con la base tomada.</b> El catalogo se consulta antes de
 * abrir la transaccion y el precio se calcula despues de cerrarla (sobre
 * {@link CarritoLeido}): con {@code @Transactional} en el metodo, un catalogo
 * lento dejaba la conexion tomada mientras se esperaba al otro servicio, y
 * podia agotar el pool y tumbar tambien las lecturas del carrito.
 *
 * <p><b>Una escritura a la vez por carrito.</b> Toda escritura bloquea la
 * fila del carrito ({@link CarritoRepository#bloquear}): dos pestanas que
 * cambian el mismo carrito se ponen en fila en vez de pisarse, y el tope de 20
 * por linea no se salta sumando desde dos sitios a la vez.
 */
@Service
public class CarritoService {

    /**
     * La tienda cobra en moneda real (RF-CAR-002, RN-PAG-001) y el catalogo
     * publica ese precio en COP: las instantaneas de las lineas se guardan en
     * COP y se convierten al responder.
     */
    static final String MONEDA_DE_LA_TIENDA = "COP";

    private final CarritoRepository carritoRepository;
    private final CatalogoMaestro catalogo;
    private final CopiaDelCatalogo copia;
    private final TasasDeCambio tasas;
    private final CotizadorDelCarrito cotizador;
    private final Clock reloj;
    private final TransactionTemplate transaccion;

    public CarritoService(CarritoRepository carritoRepository, CatalogoMaestro catalogo, CopiaDelCatalogo copia,
                          TasasDeCambio tasas, CotizadorDelCarrito cotizador, Clock reloj,
                          PlatformTransactionManager gestorDeTransacciones) {
        this.carritoRepository = carritoRepository;
        this.catalogo = catalogo;
        this.copia = copia;
        this.tasas = tasas;
        this.cotizador = cotizador;
        this.reloj = reloj;
        this.transaccion = new TransactionTemplate(gestorDeTransacciones);
    }

    /**
     * El carrito del jugador (se crea vacio si no tenia), con precio.
     *
     * @throws MonedaNoDisponibleException si se pide USD o EUR sin tasa
     */
    public CarritoDto obtener(String usuarioId, Moneda moneda) {
        Tarifa tarifa = tasas.tarifa(moneda);
        CarritoLeido leido = leer(usuarioId);
        return cotizador.cotizar(leido, tarifa, copia.siDisponible(), reloj.instant());
    }

    /** El carrito en COP: la lectura de antes de 1.4.0. */
    public CarritoDto obtenerOCrearCarrito(String usuarioId) {
        return obtener(usuarioId, Moneda.COP);
    }

    /** Las lineas tal como estan, sin precio: lo que la compra vuelve a cotizar. */
    public CarritoLeido leer(String usuarioId) {
        return Objects.requireNonNull(transaccion.execute(estado -> CarritoLeido.de(carritoDe(usuarioId))));
    }

    /**
     * Agrega el producto del catalogo maestro, o suma la cantidad si ya habia
     * una linea suya.
     *
     * @throws CantidadNoPermitidaException si pasa de 20 por linea o de las
     *         unidades que le quedan al producto
     * @throws ProductoNoAgregableException si el catalogo no lo tiene o no lo vende
     * @throws CatalogoNoDisponibleException si el catalogo no se pudo consultar
     * @throws MonedaNoDisponibleException si se pide USD o EUR sin tasa
     */
    public CarritoDto agregarProducto(String usuarioId, AgregarItemRequest request, Moneda moneda) {
        int cantidad = request.getCantidad();
        if (cantidad < 1 || cantidad > CotizadorDelCarrito.MAXIMO_POR_LINEA) {
            throw CantidadNoPermitidaException.fueraDeRango(cantidad);
        }
        Tarifa tarifa = tasas.tarifa(moneda);
        ProductoDelCatalogo producto = productoQueSePuedeAgregar(request.getProductoId());
        String referencia = Objects.requireNonNullElse(producto.id(), request.getProductoId());
        Instant ahora = reloj.instant();
        BigDecimal precioEnPesos = precioEnPesos(producto, ahora);
        CarritoLeido leido = transaccion.execute(estado -> {
            Carrito carrito = carritoBloqueado(usuarioId);
            ItemCarrito linea = lineaDe(carrito, referencia).orElseGet(() -> lineaNueva(carrito, referencia));
            int yaHabia = Objects.requireNonNullElse(linea.getCantidad(), 0);
            int nueva = yaHabia + cantidad;
            if (nueva > CotizadorDelCarrito.MAXIMO_POR_LINEA) {
                throw CantidadNoPermitidaException.maximaPorLinea(yaHabia);
            }
            exigirTiraje(producto, nueva);
            linea.setCantidad(nueva);
            // La instantanea se refresca tambien al volver a agregar (RN-PRD-003).
            instantanea(linea, producto, precioEnPesos);
            carrito.recalcularTotal();
            return CarritoLeido.de(carritoRepository.save(carrito));
        });
        return cotizador.cotizar(leido, tarifa, catalogoConElRecienLeido(producto), ahora);
    }

    /** Agregar en COP: la escritura de antes de 1.4.0. */
    public CarritoDto agregarProducto(String usuarioId, AgregarItemRequest request) {
        return agregarProducto(usuarioId, request, Moneda.COP);
    }

    /**
     * Fija la cantidad de una linea (1..20), contra el catalogo de ese momento.
     *
     * @param itemId el id de la linea, tal como llega en la ruta
     * @throws CantidadNoPermitidaException fuera de rango o sin unidades suficientes
     * @throws LineaInexistenteException si la linea no esta en el carrito del jugador
     * @throws ProductoNoAgregableException si el producto se suspendio, se agoto
     *         o dejo de tener precio en dinero real
     * @throws CatalogoNoDisponibleException si el catalogo no se pudo consultar
     */
    public CarritoDto cambiarCantidad(String usuarioId, String itemId, int cantidad, Moneda moneda) {
        if (cantidad < 1 || cantidad > CotizadorDelCarrito.MAXIMO_POR_LINEA) {
            throw CantidadNoPermitidaException.fueraDeRango(cantidad);
        }
        Long idDeLaLinea = idDeLinea(itemId);
        Tarifa tarifa = tasas.tarifa(moneda);
        CarritoLeido.Linea actual = leer(usuarioId).lineas().stream()
                .filter(linea -> idDeLaLinea.equals(linea.id()))
                .findFirst()
                .orElseThrow(LineaInexistenteException::new);
        if (actual.productoRef() == null) {
            throw new ProductoNoAgregableException(Motivo.NO_DISPONIBLE,
                    "Esa linea es de un producto que ya no esta en el catalogo: quitala del carrito.");
        }
        ProductoDelCatalogo producto = catalogo.producto(actual.productoRef())
                .orElseThrow(() -> new ProductoNoAgregableException(Motivo.NO_DISPONIBLE,
                        "El producto ya no esta en el catalogo."));
        exigirQueSeVenda(producto);
        exigirTiraje(producto, cantidad);
        Instant ahora = reloj.instant();
        BigDecimal precioEnPesos = precioEnPesos(producto, ahora);
        CarritoLeido leido = transaccion.execute(estado -> {
            Carrito carrito = carritoBloqueado(usuarioId);
            ItemCarrito linea = carrito.getItems().stream()
                    .filter(item -> idDeLaLinea.equals(item.getId()))
                    .findFirst()
                    .orElseThrow(LineaInexistenteException::new);
            linea.setCantidad(cantidad);
            instantanea(linea, producto, precioEnPesos);
            carrito.recalcularTotal();
            return CarritoLeido.de(carritoRepository.save(carrito));
        });
        return cotizador.cotizar(leido, tarifa, catalogoConElRecienLeido(producto), ahora);
    }

    /** Quita una linea; si no estaba, el carrito no cambia (DELETE es idempotente). */
    public CarritoDto eliminarItem(String usuarioId, Long itemId, Moneda moneda) {
        Tarifa tarifa = tasas.tarifa(moneda);
        CarritoLeido leido = transaccion.execute(estado -> {
            Carrito carrito = carritoBloqueado(usuarioId);
            carrito.getItems().removeIf(item -> Objects.equals(item.getId(), itemId));
            carrito.recalcularTotal();
            return CarritoLeido.de(carritoRepository.save(carrito));
        });
        return cotizador.cotizar(leido, tarifa, copia.siDisponible(), reloj.instant());
    }

    /** Quitar en COP: la escritura de antes de 1.4.0. */
    public CarritoDto eliminarItem(String usuarioId, Long itemId) {
        return eliminarItem(usuarioId, itemId, Moneda.COP);
    }

    /**
     * Saca del carrito lo que se acaba de pagar: por cada producto, las
     * unidades compradas (la linea desaparece si no le queda ninguna). Lo que
     * el jugador anadio despues de empezar a pagar, se queda.
     *
     * <p>Corre dentro de la transaccion de la compra que marca la orden como
     * COBRADA: o las dos cosas quedan escritas, o ninguna.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void retirarComprado(String usuarioId, Map<String, Integer> unidadesPorProducto) {
        Optional<Carrito> encontrado = carritoRepository.bloquear(usuarioId);
        if (encontrado.isEmpty()) {
            return;
        }
        Carrito carrito = encontrado.get();
        Map<String, Integer> pendientes = new HashMap<>(unidadesPorProducto);
        carrito.getItems().removeIf(item -> {
            Integer compradas = item.getProductoRef() == null ? null : pendientes.remove(item.getProductoRef());
            if (compradas == null) {
                return false;
            }
            int quedan = Objects.requireNonNullElse(item.getCantidad(), 0) - compradas;
            if (quedan <= 0) {
                return true;
            }
            item.setCantidad(quedan);
            item.calcularSubtotal();
            return false;
        });
        carrito.recalcularTotal();
        carritoRepository.save(carrito);
    }

    /**
     * Las reglas de venta, en este orden: que exista, que el catalogo lo
     * ofrezca (RN-PRD-004), que queden unidades (RF-CAR-007, "valida
     * existencias") y que tenga precio en moneda real (RF-CAR-002).
     */
    private ProductoDelCatalogo productoQueSePuedeAgregar(String productoId) {
        ProductoDelCatalogo producto = catalogo.producto(productoId)
                .orElseThrow(() -> new ProductoNoAgregableException(Motivo.INEXISTENTE,
                        "El producto no existe en el catalogo."));
        exigirQueSeVenda(producto);
        return producto;
    }

    private static void exigirQueSeVenda(ProductoDelCatalogo producto) {
        if (!producto.estaEnVenta()) {
            throw new ProductoNoAgregableException(Motivo.NO_DISPONIBLE,
                    "El producto no esta disponible para la venta (estado " + producto.estado() + ").");
        }
        if (!producto.tieneExistencias()) {
            throw new ProductoNoAgregableException(Motivo.AGOTADO,
                    "El producto esta agotado.");
        }
        if (!producto.tienePrecioEnMonedaReal()) {
            throw new ProductoNoAgregableException(Motivo.SIN_PRECIO_EN_MONEDA_REAL,
                    "El producto no tiene precio en moneda real, y la tienda solo vende en moneda real.");
        }
    }

    /** RF-PRD-003: no mas unidades de las que quedan (el tiraje -1 es ilimitado). */
    private static void exigirTiraje(ProductoDelCatalogo producto, int cantidad) {
        if (producto.tieneTirajeLimitado() && cantidad > producto.tiraje()) {
            throw CantidadNoPermitidaException.tirajeInsuficiente(producto.tiraje());
        }
    }

    private static BigDecimal precioEnPesos(ProductoDelCatalogo producto, Instant ahora) {
        return CalculadoraDePrecios.deProducto(producto, Tarifa.enPesos(), ahora).precioFinal();
    }

    private static void instantanea(ItemCarrito linea, ProductoDelCatalogo producto, BigDecimal precioEnPesos) {
        linea.setProductoNombre(producto.nombre());
        linea.setPrecioUnitario(precioEnPesos);
        linea.setMoneda(MONEDA_DE_LA_TIENDA);
        linea.calcularSubtotal();
    }

    /**
     * La copia del catalogo con el producto que se acaba de leer por su id: la
     * respuesta de una escritura ensena el precio que se acaba de comprobar,
     * aunque la copia de 30 s tenga uno anterior.
     */
    private Optional<Map<String, ProductoDelCatalogo>> catalogoConElRecienLeido(ProductoDelCatalogo producto) {
        Map<String, ProductoDelCatalogo> actual = new HashMap<>(copia.siDisponible().orElse(Map.of()));
        if (producto.id() != null) {
            actual.put(producto.id(), producto);
        }
        return Optional.of(actual);
    }

    private static Long idDeLinea(String itemId) {
        try {
            return Long.valueOf(itemId);
        } catch (NumberFormatException noEsUnaLinea) {
            throw new LineaInexistenteException();
        }
    }

    /** El carrito del jugador, creado si no tenia; sin bloquear (lecturas). */
    private Carrito carritoDe(String usuarioId) {
        return carritoRepository.findByUsuarioId(usuarioId).orElseGet(() -> {
            carritoRepository.crearSiNoExiste(usuarioId);
            return carritoRepository.findByUsuarioId(usuarioId)
                    .orElseThrow(() -> new IllegalStateException("No se pudo crear el carrito de " + usuarioId));
        });
    }

    /** El carrito del jugador, creado si no tenia, y bloqueado hasta el final de la transaccion. */
    private Carrito carritoBloqueado(String usuarioId) {
        return carritoRepository.bloquear(usuarioId).orElseGet(() -> {
            carritoRepository.crearSiNoExiste(usuarioId);
            return carritoRepository.bloquear(usuarioId)
                    .orElseThrow(() -> new IllegalStateException("No se pudo crear el carrito de " + usuarioId));
        });
    }

    /** La linea de ese producto del catalogo. Las lineas legadas (sin referencia) nunca coinciden. */
    private static Optional<ItemCarrito> lineaDe(Carrito carrito, String referencia) {
        return carrito.getItems().stream()
                .filter(item -> referencia.equals(item.getProductoRef()))
                .findFirst();
    }

    private static ItemCarrito lineaNueva(Carrito carrito, String referencia) {
        ItemCarrito linea = new ItemCarrito();
        linea.setCarrito(carrito);
        linea.setProductoRef(referencia);
        linea.setCantidad(0);
        carrito.getItems().add(linea);
        return linea;
    }
}
