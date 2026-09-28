package com.nexusbattles.ms_ecommerce.compra;

import com.nexusbattles.ms_ecommerce.catalogo.CatalogoMaestro;
import com.nexusbattles.ms_ecommerce.catalogo.CatalogoNoDisponibleException;
import com.nexusbattles.ms_ecommerce.catalogo.ProductoDelCatalogo;
import com.nexusbattles.ms_ecommerce.catalogo.PropiedadesDelCatalogo;
import com.nexusbattles.ms_ecommerce.compra.CompraRechazadaException.Motivo;
import com.nexusbattles.ms_ecommerce.compra.pago.PasarelaSimulada;
import com.nexusbattles.ms_ecommerce.compra.pago.ResultadoDeCobro;
import com.nexusbattles.ms_ecommerce.compra.pago.SolicitudDePago;
import com.nexusbattles.ms_ecommerce.compra.pago.TarjetaValidada;
import com.nexusbattles.ms_ecommerce.compra.pago.ValidadorDeTarjeta;
import com.nexusbattles.ms_ecommerce.integracion.PropiedadesDeLaTienda;
import com.nexusbattles.ms_ecommerce.precios.CalculadoraDePrecios;
import com.nexusbattles.ms_ecommerce.precios.Moneda;
import com.nexusbattles.ms_ecommerce.precios.PrecioCalculado;
import com.nexusbattles.ms_ecommerce.precios.Tarifa;
import com.nexusbattles.ms_ecommerce.precios.TasasDeCambio;
import com.nexusbattles.ms_ecommerce.repository.CarritoRepository;
import com.nexusbattles.ms_ecommerce.seguridad.CredencialDeServicio;
import com.nexusbattles.ms_ecommerce.service.CantidadNoPermitidaException;
import com.nexusbattles.ms_ecommerce.service.CarritoLeido;
import com.nexusbattles.ms_ecommerce.service.CarritoService;
import com.nexusbattles.ms_ecommerce.service.CotizadorDelCarrito;
import com.nexusbattles.ms_ecommerce.service.ProductoNoAgregableException;
import com.nexusbattles.ms_ecommerce.traza.Traza;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * La compra (POST /checkout, contrato 1.4.0): valida, cotiza en el servidor,
 * crea la orden, cobra en la pasarela simulada y deja que
 * {@link ProcesadorDeOrdenes} haga el resto.
 *
 * <h2>Idempotencia</h2>
 *
 * <p>La {@code Idempotency-Key} es del jugador: la restriccion unica
 * {@code (usuario_id, clave_idempotencia)} de V4 impide dos ordenes con la
 * misma clave, tambien si llegan dos peticiones a la vez. La misma clave
 * devuelve la orden original sin repetir nada; la unica excepcion es una
 * orden PENDIENTE —la pasarela no respondio, nada se cobro—, que se vuelve a
 * intentar con la tarjeta que llegue: eso es lo que hace que reintentar tras
 * un 503 sea seguro.
 *
 * <h2>Una compra a la vez por jugador</h2>
 *
 * <p>Una orden se procesa con su concesion tomada ({@code bloqueada_hasta}).
 * Mientras un jugador tenga una compra en proceso, otra (con otra clave)
 * responde 409 {@code compra-en-curso}: dos pagos simultaneos del mismo
 * carrito cobrarian dos veces lo mismo. La comprobacion y la creacion van en
 * la misma transaccion que bloquea el carrito del jugador, asi que no hay
 * hueco entre las dos.
 *
 * <h2>Lo comprado sale del carrito al cobrar</h2>
 *
 * <p>En la misma transaccion que marca la orden COBRADA, y no al final: si la
 * entrega o el correo se retrasan (un servicio caido, un reintento a los
 * minutos), el carrito no puede seguir ensenando —y dejando pagar otra vez—
 * lo que ya se cobro. Un rechazo de la pasarela no toca el carrito.
 */
@Service
public class CompraService {

    private static final Logger log = LoggerFactory.getLogger(CompraService.class);

    static final int CLAVE_MINIMA = 8;
    static final int CLAVE_MAXIMA = 100;

    private static final Set<EstadoOrden> EN_CURSO =
            Set.of(EstadoOrden.COBRADA, EstadoOrden.ENTREGADA, EstadoOrden.COMPENSACION_PENDIENTE);

    private final OrdenRepository ordenes;
    private final CarritoRepository carritos;
    private final CarritoService carrito;
    private final CatalogoMaestro catalogo;
    private final TasasDeCambio tasas;
    private final ValidadorDeTarjeta validador;
    private final PasarelaSimulada pasarela;
    private final ProcesadorDeOrdenes procesador;
    private final CredencialDeServicio credencial;
    private final PropiedadesDeLaTienda propiedades;
    private final PropiedadesDelCatalogo propiedadesDelCatalogo;
    private final Clock reloj;
    private final TransactionTemplate transaccion;

    public CompraService(OrdenRepository ordenes, CarritoRepository carritos, CarritoService carrito,
                         CatalogoMaestro catalogo, TasasDeCambio tasas, ValidadorDeTarjeta validador,
                         PasarelaSimulada pasarela, ProcesadorDeOrdenes procesador, CredencialDeServicio credencial,
                         PropiedadesDeLaTienda propiedades, PropiedadesDelCatalogo propiedadesDelCatalogo,
                         Clock reloj, PlatformTransactionManager gestorDeTransacciones) {
        this.ordenes = ordenes;
        this.carritos = carritos;
        this.carrito = carrito;
        this.catalogo = catalogo;
        this.tasas = tasas;
        this.validador = validador;
        this.pasarela = pasarela;
        this.procesador = procesador;
        this.credencial = credencial;
        this.propiedades = propiedades;
        this.propiedadesDelCatalogo = propiedadesDelCatalogo;
        this.reloj = reloj;
        this.transaccion = new TransactionTemplate(gestorDeTransacciones);
    }

    /**
     * Paga el carrito del jugador.
     *
     * @param usuarioId el {@code uid} del token
     * @param clave     la {@code Idempotency-Key} de la peticion
     * @return la orden, nueva (201) o la de esa clave (200)
     * @throws CompraRechazadaException con el motivo (400, 402, 409, 503)
     * @throws com.nexusbattles.ms_ecommerce.compra.pago.DatosDePagoInvalidosException 400
     * @throws com.nexusbattles.ms_ecommerce.precios.MonedaNoDisponibleException 422
     * @throws ProductoNoAgregableException 409 si un producto ya no se puede vender (no se cobra)
     * @throws CantidadNoPermitidaException 409 si no quedan tantas unidades (no se cobra)
     * @throws CatalogoNoDisponibleException 503 (no se crea orden)
     */
    public ResultadoDeCompra pagar(String usuarioId, String clave, SolicitudDePago solicitud) {
        String claveValida = validarClave(clave);
        Moneda moneda = solicitud.monedaPedida();
        Optional<Orden> previa = ordenes.findByUsuarioIdAndClaveIdempotencia(usuarioId, claveValida);
        if (previa.isPresent()) {
            return repetir(previa.get(), moneda, solicitud);
        }
        exigirConfiguracion();
        TarjetaValidada tarjeta = validador.validar(solicitud);
        Tarifa tarifa = tasas.tarifa(moneda);
        List<LineaCotizada> lineas = cotizarElCarrito(usuarioId, tarifa);
        Orden orden;
        try {
            orden = crear(usuarioId, claveValida, tarifa, lineas, tarjeta);
        } catch (DataIntegrityViolationException mismaClave) {
            // Otra peticion con la misma clave creo la orden entre la consulta
            // de arriba y esta insercion: es la misma compra.
            Orden otra = ordenes.findByUsuarioIdAndClaveIdempotencia(usuarioId, claveValida)
                    .orElseThrow(() -> mismaClave);
            return repetir(otra, moneda, solicitud);
        }
        return cobrarYProcesar(orden.getId(), tarjeta);
    }

    /** Las ordenes del jugador, de la mas reciente a la mas antigua. */
    public List<OrdenDto> ordenesDe(String usuarioId) {
        return ordenes.findByUsuarioIdOrderByCreadaEnDesc(usuarioId).stream().map(OrdenDto::de).toList();
    }

    /** Una orden del jugador; la de otro no existe para el. */
    public OrdenDto ordenDe(String usuarioId, String ordenId) {
        UUID id;
        try {
            id = UUID.fromString(ordenId);
        } catch (IllegalArgumentException noEsUnId) {
            throw new OrdenInexistenteException();
        }
        return ordenes.findByIdAndUsuarioId(id, usuarioId).map(OrdenDto::de)
                .orElseThrow(OrdenInexistenteException::new);
    }

    /**
     * Retoma las ordenes a medias que ya toca reintentar y caduca las
     * PENDIENTE que nadie reintento. La llama la tarea programada.
     *
     * @return cuantas ordenes se retomaron
     */
    public int reanudarPendientes() {
        Instant ahora = reloj.instant();
        caducarPendientes(ahora);
        List<UUID> porRetomar = ordenes.porRetomar(EN_CURSO, EstadoOrden.RECHAZADA, EstadoDelAsiento.PENDIENTE,
                ahora, PageRequest.of(0, propiedades.ordenes().lote()));
        int retomadas = 0;
        for (UUID id : porRetomar) {
            if (ordenes.tomar(id, reloj.instant(), reloj.instant().plus(propiedades.ordenes().concesion())) == 0) {
                continue;
            }
            try {
                Traza.abrir(ordenes.findById(id).map(Orden::getTraza).orElse(null));
                procesador.avanzar(id);
                retomadas++;
            } catch (OptimisticLockingFailureException otroLaTomo) {
                log.warn("Orden {}: otro proceso la escribio mientras se retomaba; queda para la siguiente vuelta", id);
            } catch (RuntimeException inesperado) {
                log.error("Orden {}: fallo inesperado al retomarla", id, inesperado);
            } finally {
                ordenes.soltar(id);
                Traza.cerrar();
            }
        }
        return retomadas;
    }

    // ------------------------------------------------------------ compra

    private ResultadoDeCompra repetir(Orden orden, Moneda moneda, SolicitudDePago solicitud) {
        if (orden.getMoneda() != moneda) {
            throw new CompraRechazadaException(Motivo.CLAVE_REUTILIZADA,
                    "Esa clave ya se uso para una compra en " + orden.getMoneda() + ".", OrdenDto.de(orden));
        }
        if (orden.getEstado() != EstadoOrden.PENDIENTE) {
            return new ResultadoDeCompra(OrdenDto.de(orden), false);
        }
        Instant ahora = reloj.instant();
        if (ordenes.tomar(orden.getId(), ahora, ahora.plus(propiedades.ordenes().concesion())) == 0) {
            throw new CompraRechazadaException(Motivo.COMPRA_EN_CURSO,
                    "Ese pago ya se esta procesando. Consulta tus compras en unos segundos.", OrdenDto.de(orden));
        }
        TarjetaValidada tarjeta;
        try {
            exigirConfiguracion();
            tarjeta = validador.validar(solicitud);
        } catch (RuntimeException rechazo) {
            ordenes.soltar(orden.getId());
            throw rechazo;
        }
        return cobrarYProcesar(orden.getId(), tarjeta);
    }

    /** Cobra una orden PENDIENTE cuya concesion ya se tiene, y la lleva tan lejos como se pueda. */
    private ResultadoDeCompra cobrarYProcesar(UUID id, TarjetaValidada tarjeta) {
        try {
            Orden orden = cargar(id);
            ResultadoDeCobro cobro = pasarela.cobrar(tarjeta, orden.getTotal(), orden.getMoneda(), id);
            if (cobro instanceof ResultadoDeCobro.NoDisponible) {
                Orden pendiente = procesador.actualizar(id, o -> {
                    o.setIntentos(o.getIntentos() + 1);
                    o.setUltimoError("La pasarela de pagos no respondió.");
                    o.setMedioMarca(tarjeta.marca());
                    o.setMedioUltimos4(tarjeta.ultimos4());
                });
                throw new CompraRechazadaException(Motivo.PASARELA_NO_DISPONIBLE,
                        "La pasarela de pagos no respondió y no se cobró nada. Vuelve a intentarlo.",
                        OrdenDto.de(pendiente));
            }
            if (cobro instanceof ResultadoDeCobro.Rechazado rechazado) {
                procesador.actualizar(id, o -> {
                    o.setEstado(EstadoOrden.RECHAZADA);
                    o.setMotivo(rechazado.motivo());
                    o.setCorreo(EstadoDelCorreo.OMITIDO);
                    o.setMedioMarca(tarjeta.marca());
                    o.setMedioUltimos4(tarjeta.ultimos4());
                });
                // El asiento RECHAZADO: si ms-finanzas no responde, lo retoma la tarea programada.
                Orden rechazadaFinal = avanzarSinPerderLaRespuesta(id);
                throw new CompraRechazadaException(Motivo.PAGO_RECHAZADO, rechazado.motivo(),
                        OrdenDto.de(rechazadaFinal));
            }
            ResultadoDeCobro.Aprobado aprobado = (ResultadoDeCobro.Aprobado) cobro;
            marcarCobrada(id, aprobado.referencia(), tarjeta);
            Orden procesada = avanzarSinPerderLaRespuesta(id);
            if (procesada.getEstado() == EstadoOrden.REEMBOLSADA
                    || procesada.getEstado() == EstadoOrden.COMPENSACION_PENDIENTE) {
                throw new CompraRechazadaException(Motivo.COMPRA_REEMBOLSADA,
                        Objects.requireNonNullElse(procesada.getMotivo(), "No se pudo entregar la compra."),
                        OrdenDto.de(procesada));
            }
            return new ResultadoDeCompra(OrdenDto.de(procesada), true);
        } finally {
            ordenes.soltar(id);
        }
    }

    /**
     * Avanza la orden y, pase lo que pase en los pasos posteriores al cobro,
     * devuelve como quedo: el jugador ya pago, y la respuesta tiene que decir
     * en que estado esta su orden aunque un paso haya fallado de una forma
     * inesperada (la tarea programada la terminara).
     */
    private Orden avanzarSinPerderLaRespuesta(UUID id) {
        try {
            return procesador.avanzar(id);
        } catch (RuntimeException inesperado) {
            log.error("Orden {}: fallo inesperado despues del cobro; la retomara la tarea programada", id, inesperado);
            return cargar(id);
        }
    }

    private void marcarCobrada(UUID id, String referencia, TarjetaValidada tarjeta) {
        transaccion.executeWithoutResult(estado -> {
            Orden orden = cargar(id);
            Instant ahora = reloj.instant();
            orden.setEstado(EstadoOrden.COBRADA);
            orden.setReferenciaPasarela(referencia);
            orden.setMedioMarca(tarjeta.marca());
            orden.setMedioUltimos4(tarjeta.ultimos4());
            orden.setCobradaEn(ahora);
            orden.setActualizadaEn(ahora);
            orden.setUltimoError(null);
            orden.setBloqueadaHasta(ahora.plus(propiedades.ordenes().concesion()));
            ordenes.save(orden);
            carrito.retirarComprado(orden.getUsuarioId(), orden.unidadesPorProducto());
        });
        log.info("Orden {}: cobrada", id);
    }

    private Orden crear(String usuarioId, String clave, Tarifa tarifa, List<LineaCotizada> lineas,
                        TarjetaValidada tarjeta) {
        return Objects.requireNonNull(transaccion.execute(estado -> {
            // El carrito del jugador bloqueado: la comprobacion de abajo y la
            // insercion no dejan hueco para otra compra suya.
            carritos.bloquear(usuarioId);
            Instant ahora = reloj.instant();
            if (ordenes.hayCompraEnCurso(usuarioId, ahora)) {
                throw new CompraRechazadaException(Motivo.COMPRA_EN_CURSO,
                        "Ya tienes un pago en proceso. Espera a que termine para pagar otra vez.");
            }
            Orden orden = new Orden();
            orden.setId(UUID.randomUUID());
            orden.setUsuarioId(usuarioId);
            orden.setClaveIdempotencia(clave);
            orden.setEstado(EstadoOrden.PENDIENTE);
            orden.setMoneda(tarifa.moneda());
            orden.setTasaDeCambio(tarifa.tasaParaMostrar());
            orden.setMedioMarca(tarjeta.marca());
            orden.setMedioUltimos4(tarjeta.ultimos4());
            orden.setTraza(Traza.actual().orElse(null));
            orden.setCreadaEn(ahora);
            orden.setActualizadaEn(ahora);
            orden.setBloqueadaHasta(ahora.plus(propiedades.ordenes().concesion()));
            BigDecimal total = BigDecimal.ZERO.setScale(tarifa.moneda().escala());
            for (LineaCotizada cotizada : lineas) {
                LineaDeOrden linea = new LineaDeOrden();
                linea.setPosicion(cotizada.posicion());
                linea.setProductoRef(cotizada.productoRef());
                linea.setNombre(cotizada.nombre());
                linea.setCantidad(cotizada.cantidad());
                linea.setPrecioUnitario(cotizada.precio().precioFinal());
                linea.setPrecioOriginal(cotizada.precio().precioOriginal());
                linea.setDescuentoPorcentaje(cotizada.precio().porcentajeDescuento());
                linea.setSubtotal(cotizada.precio().subtotal(cotizada.cantidad()));
                orden.agregarLinea(linea);
                total = total.add(linea.getSubtotal());
            }
            orden.setTotal(total);
            Orden guardada = ordenes.saveAndFlush(orden);
            log.info("Orden {}: creada con {} linea(s) por {} {}", guardada.getId(), lineas.size(), total,
                    tarifa.moneda());
            return guardada;
        }));
    }

    /**
     * El carrito, producto a producto contra el catalogo de este momento (no la
     * copia de 30 s: lo que se cobra se comprueba al cobrar), con el precio del
     * servidor.
     */
    private List<LineaCotizada> cotizarElCarrito(String usuarioId, Tarifa tarifa) {
        CarritoLeido leido = carrito.leer(usuarioId);
        List<CarritoLeido.Linea> delCatalogo = leido.delCatalogo();
        if (delCatalogo.isEmpty()) {
            throw new CompraRechazadaException(Motivo.CARRITO_VACIO, "Tu carrito está vacío: no hay nada que pagar.");
        }
        Instant ahora = reloj.instant();
        List<LineaCotizada> lineas = new ArrayList<>();
        int posicion = 0;
        for (CarritoLeido.Linea linea : delCatalogo) {
            String nombre = Objects.requireNonNullElse(linea.nombre(), "El producto");
            ProductoDelCatalogo producto = catalogo.producto(linea.productoRef())
                    .orElseThrow(() -> new ProductoNoAgregableException(ProductoNoAgregableException.Motivo.NO_DISPONIBLE,
                            "«" + nombre + "» ya no está en el catálogo. Quítalo del carrito para pagar."));
            if (!producto.estaEnVenta() || !producto.tienePrecioEnMonedaReal()) {
                throw new ProductoNoAgregableException(ProductoNoAgregableException.Motivo.NO_DISPONIBLE,
                        "«" + producto.nombre() + "» ya no está a la venta. Quítalo del carrito para pagar.");
            }
            if (!producto.tieneExistencias()) {
                throw new ProductoNoAgregableException(ProductoNoAgregableException.Motivo.AGOTADO,
                        "«" + producto.nombre() + "» se agotó. Quítalo del carrito para pagar.");
            }
            if (linea.cantidad() > CotizadorDelCarrito.MAXIMO_POR_LINEA) {
                throw CantidadNoPermitidaException.fueraDeRango(linea.cantidad());
            }
            if (producto.tieneTirajeLimitado() && linea.cantidad() > producto.tiraje()) {
                throw CantidadNoPermitidaException.tirajeInsuficiente(producto.tiraje());
            }
            PrecioCalculado precio = CalculadoraDePrecios.deProducto(producto, tarifa, ahora);
            lineas.add(new LineaCotizada(++posicion, linea.productoRef(), producto.nombre(), linea.cantidad(), precio));
        }
        return lineas;
    }

    /**
     * Antes de cobrar, que la tienda pueda terminar lo que empieza: sin
     * credencial de servicio o sin la direccion de alguno de los servicios de
     * la compra, se cobraria y la orden se quedaria sin entregar hasta que
     * alguien arreglara el despliegue.
     */
    private void exigirConfiguracion() {
        PropiedadesDeLaTienda.Servicios servicios = propiedades.servicios();
        boolean correoNecesario = propiedades.correo().habilitado();
        boolean completa = credencial.configurada()
                && tiene(propiedadesDelCatalogo.url())
                && tiene(servicios.inventario())
                && tiene(servicios.finanzas())
                && (!correoNecesario || (tiene(servicios.correo()) && tiene(servicios.identidad())));
        if (!completa) {
            log.error("La compra no esta disponible: falta la credencial de servicio de la tienda o la direccion "
                    + "de un servicio de la compra (DIRECTORIO_ACTIVO_*, PRODUCTOS_URL, INVENTARIO_BASE_URL, "
                    + "FINANZAS_BASE_URL, CORREO_URL, IDENTIDAD_URL)");
            throw new CompraRechazadaException(Motivo.COMPRA_NO_DISPONIBLE,
                    "La compra no está disponible en este momento. Tu carrito sigue guardado.");
        }
    }

    private static boolean tiene(String url) {
        return url != null && !url.isBlank();
    }

    private static String validarClave(String clave) {
        String limpia = clave == null ? "" : clave.strip();
        if (limpia.length() < CLAVE_MINIMA || limpia.length() > CLAVE_MAXIMA) {
            throw new CompraRechazadaException(Motivo.CLAVE_INVALIDA,
                    "Falta la cabecera Idempotency-Key o no tiene entre " + CLAVE_MINIMA + " y " + CLAVE_MAXIMA
                            + " caracteres.");
        }
        return limpia;
    }

    private void caducarPendientes(Instant ahora) {
        Instant limite = ahora.minus(propiedades.ordenes().pendienteCaducaTras());
        for (UUID id : ordenes.pendientesCaducadas(EstadoOrden.PENDIENTE, limite, ahora,
                PageRequest.of(0, propiedades.ordenes().lote()))) {
            if (ordenes.tomar(id, ahora, ahora.plus(propiedades.ordenes().concesion())) == 0) {
                continue;
            }
            try {
                procesador.actualizar(id, o -> {
                    if (o.getEstado() == EstadoOrden.PENDIENTE) {
                        o.setEstado(EstadoOrden.RECHAZADA);
                        o.setMotivo("La pasarela no respondió y el pago no se completó.");
                        o.setAsiento(EstadoDelAsiento.NO_APLICA);
                        o.setCorreo(EstadoDelCorreo.OMITIDO);
                    }
                });
                log.info("Orden {}: caduco PENDIENTE sin reintento; queda RECHAZADA", id);
            } finally {
                ordenes.soltar(id);
            }
        }
    }

    private Orden cargar(UUID id) {
        return ordenes.findById(id).orElseThrow(() -> new IllegalStateException("La orden " + id + " no existe"));
    }

    /** Una linea del carrito con el precio que se va a cobrar. */
    private record LineaCotizada(int posicion, String productoRef, String nombre, int cantidad, PrecioCalculado precio) {
    }
}
