package com.nexusbattles.ms_ecommerce.service;

import com.nexusbattles.ms_ecommerce.catalogo.CatalogoMaestro;
import com.nexusbattles.ms_ecommerce.catalogo.CatalogoNoDisponibleException;
import com.nexusbattles.ms_ecommerce.catalogo.ProductoDelCatalogo;
import com.nexusbattles.ms_ecommerce.dto.AgregarItemRequest;
import com.nexusbattles.ms_ecommerce.dto.CarritoDto;
import com.nexusbattles.ms_ecommerce.model.Carrito;
import com.nexusbattles.ms_ecommerce.model.ItemCarrito;
import com.nexusbattles.ms_ecommerce.repository.CarritoRepository;
import com.nexusbattles.ms_ecommerce.service.ProductoNoAgregableException.Motivo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.Optional;

/**
 * El carrito del jugador, sobre el catalogo maestro (R16).
 *
 * <p>Cada producto que entra se resuelve contra el catalogo del servicio
 * productos —el que vende la tienda— y la linea guarda una instantanea: la
 * referencia del catalogo, el nombre, el precio en moneda real y la moneda.
 * Los endpoints devuelven {@link CarritoDto}, nunca la entidad.
 */
@Service
public class CarritoService {

    /**
     * La tienda cobra en moneda real (RF-CAR-002, RN-PAG-001) y el catalogo
     * publica ese precio en COP. La conversion a otras monedas no esta
     * implementada: se declara COP en lugar de fingir otra.
     */
    static final String MONEDA_DE_LA_TIENDA = "COP";

    private final CarritoRepository carritoRepository;
    private final CatalogoMaestro catalogo;
    private final CarritoMapper mapper;
    private final TransactionTemplate transaccion;

    public CarritoService(CarritoRepository carritoRepository, CatalogoMaestro catalogo,
                          CarritoMapper mapper, PlatformTransactionManager gestorDeTransacciones) {
        this.carritoRepository = carritoRepository;
        this.catalogo = catalogo;
        this.mapper = mapper;
        this.transaccion = new TransactionTemplate(gestorDeTransacciones);
    }

    @Transactional
    public CarritoDto obtenerOCrearCarrito(String usuarioId) {
        return mapper.aDto(carritoDe(usuarioId));
    }

    /**
     * Agrega el producto del catalogo maestro, o suma la cantidad si ya habia
     * una linea suya.
     *
     * <p>El catalogo se consulta <b>antes</b> de abrir la transaccion, y a
     * proposito: con {@code @Transactional} en el metodo, la conexion a la
     * base quedaba tomada mientras se esperaba al otro servicio (hasta los
     * tiempos de espera del cliente), y un catalogo lento podia agotar el pool
     * y tumbar tambien las lecturas del carrito, que no lo necesitan.
     *
     * @throws ProductoNoAgregableException si el catalogo no lo tiene o no lo vende
     * @throws CatalogoNoDisponibleException si el catalogo no se pudo consultar
     */
    public CarritoDto agregarProducto(String usuarioId, AgregarItemRequest request) {
        ProductoDelCatalogo producto = productoQueSePuedeAgregar(request.getProductoId());
        String referencia = Objects.requireNonNullElse(producto.id(), request.getProductoId());
        return transaccion.execute(estado -> {
            Carrito carrito = carritoDe(usuarioId);
            ItemCarrito linea = lineaDe(carrito, referencia).orElseGet(() -> lineaNueva(carrito, referencia));
            linea.setCantidad(linea.getCantidad() + request.getCantidad());
            // La instantanea se refresca tambien al volver a agregar: toda la
            // linea pasa a costar lo que el catalogo dice hoy (RN-PRD-003).
            linea.setProductoNombre(producto.nombre());
            linea.setPrecioUnitario(producto.precioMonedaReal());
            linea.setMoneda(MONEDA_DE_LA_TIENDA);
            linea.calcularSubtotal();
            carrito.recalcularTotal();
            return mapper.aDto(carritoRepository.save(carrito));
        });
    }

    @Transactional
    public CarritoDto eliminarItem(String usuarioId, Long itemId) {
        Carrito carrito = carritoDe(usuarioId);
        carrito.getItems().removeIf(item -> Objects.equals(item.getId(), itemId));
        carrito.recalcularTotal();
        return mapper.aDto(carritoRepository.save(carrito));
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
        return producto;
    }

    private Carrito carritoDe(String usuarioId) {
        return carritoRepository.findByUsuarioId(usuarioId)
                .orElseGet(() -> {
                    Carrito nuevoCarrito = new Carrito();
                    nuevoCarrito.setUsuarioId(usuarioId);
                    return carritoRepository.save(nuevoCarrito);
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
