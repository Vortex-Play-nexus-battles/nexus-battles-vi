package nexus.inventario.aplicacion;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import nexus.inventario.dominio.ClaveDeEntregaOcupadaException;
import nexus.inventario.dominio.ConflictoDeEscrituraException;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.Entrega;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.LineaDeEntrega;
import nexus.inventario.dominio.ParteArmadura;
import nexus.inventario.dominio.RepositorioDeEntregas;
import nexus.inventario.dominio.RepositorioDeInventarios;
import nexus.inventario.dominio.TipoElementoInventario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * La unica via por la que un servicio da a un jugador la propiedad de un
 * producto — B4 ({@code POST /api/v1/inventario/entregas}; seccion 7.1: el
 * inventario tiene "los personajes e items" que el jugador consiguio, no los
 * que se crea).
 *
 * <h2>Todo o nada, sin transacciones multi-documento</h2>
 *
 * MongoDB standalone no tiene transacciones entre documentos, y una entrega
 * toca dos: su registro (coleccion {@code entregas}) y el inventario del
 * jugador. El orden las hace seguras:
 *
 * <ol>
 *   <li>Se valida cada producto contra el catalogo (existe, no esta
 *       suspendido) y se planean los elementos, con sus identificadores. Si
 *       algo falla aqui no se guarda nada: 422, 409 o 503.</li>
 *   <li>Se registra la entrega PENDIENTE con esos elementos. El indice unico
 *       de la clave hace que dos peticiones simultaneas con la misma clave
 *       registren una sola.</li>
 *   <li>Se aplica al inventario en UNA escritura del documento del jugador:
 *       los elementos y la anotacion de la entrega juntos (MongoDB garantiza
 *       la atomicidad de un documento). El guardado es condicional a la
 *       version leida; si otra escritura llego antes, se relee y se reintenta.</li>
 *   <li>Se marca COMPLETADA.</li>
 * </ol>
 *
 * Si el proceso cae entre 2 y 3, o entre 3 y 4, la entrega queda PENDIENTE; el
 * reintento con la misma clave la encuentra, aplica los MISMOS elementos —el
 * inventario sabe que ya la recibio si 3 llego a escribirse, aunque el jugador
 * haya movido despues lo recibido— y la completa. Nunca duplica.
 *
 * <p>No consume tiraje: lo reserva quien vende, con
 * {@code POST /api/v1/productos/{id}/adquisiciones}.
 */
@Service
public class EntregarProductos {

    private static final Logger BITACORA = LoggerFactory.getLogger(EntregarProductos.class);

    /** Reintentos de la escritura del inventario cuando otra llego antes. */
    static final int INTENTOS_ANTE_CONFLICTO = 5;

    private static final String ESTADO_SUSPENDIDO = "SUSPENDIDO";

    private final RepositorioDeEntregas entregas;
    private final RepositorioDeInventarios inventarios;
    private final ResolutorDeProducto productos;
    private final Clock reloj;

    public EntregarProductos(
            RepositorioDeEntregas entregas,
            RepositorioDeInventarios inventarios,
            ResolutorDeProducto productos,
            Clock reloj) {
        this.entregas = entregas;
        this.inventarios = inventarios;
        this.productos = productos;
        this.reloj = reloj;
    }

    /**
     * @param realizadaAhora falso si la clave ya estaba completada (200 con la
     *                       entrega original); verdadero si esta llamada la
     *                       aplico, nueva o retomada (201)
     */
    public record ResultadoEntrega(Entrega entrega, boolean realizadaAhora) {
    }

    /**
     * @param clave       {@code Idempotency-Key} (la valida la frontera HTTP)
     * @param solicitante quien la pide, para la auditoria
     * @throws ClaveDeEntregaReutilizadaException la clave ya se uso con otro cuerpo (409)
     * @throws ProductoInexistenteException       un producto no existe (422)
     * @throws ProductoSuspendidoException        un producto esta suspendido (409)
     * @throws ProductoIncompletoException        una armadura sin parte en el catalogo (422)
     * @throws CatalogoNoDisponibleException      productos no respondio (503)
     */
    public ResultadoEntrega entregar(SolicitudDeEntrega solicitud, String clave, String solicitante) {
        String huella = solicitud.huella();
        Optional<Entrega> previa = entregas.buscarPorClave(clave);
        if (previa.isPresent()) {
            return retomar(previa.get(), huella);
        }

        List<ElementoInventario> planeados = planear(solicitud);
        Entrega pendiente = Entrega.pendiente(
                UUID.randomUUID().toString(), clave, huella, solicitud.uid().toString(), solicitud.origen(),
                solicitud.referencia(), solicitud.productos(), planeados, solicitante, reloj.instant());
        try {
            entregas.registrar(pendiente);
        } catch (ClaveDeEntregaOcupadaException otraPeticionConLaMismaClave) {
            return retomar(entregas.buscarPorClave(clave).orElseThrow(() -> otraPeticionConLaMismaClave), huella);
        }
        return aplicarYCompletar(pendiente);
    }

    private ResultadoEntrega retomar(Entrega registrada, String huella) {
        if (!registrada.huella().equals(huella)) {
            throw new ClaveDeEntregaReutilizadaException();
        }
        if (registrada.completada()) {
            return new ResultadoEntrega(registrada, false);
        }
        BITACORA.info("Entrega {} pendiente: se retoma con los mismos elementos", registrada.id());
        return aplicarYCompletar(registrada);
    }

    private ResultadoEntrega aplicarYCompletar(Entrega entrega) {
        aplicar(entrega);
        Entrega completada = entrega.completadaEn(reloj.instant());
        entregas.completar(entrega.id(), completada.entregadaEn());
        BITACORA.info("Entrega {} ({} {}) completada para {}: {} elementos, pedida por {}",
                entrega.id(), entrega.origen(), entrega.referencia(), entrega.uid(),
                entrega.elementos().size(), entrega.solicitante());
        // Si otra peticion con la misma clave la completo antes, manda su
        // momento: completar solo escribe sobre una PENDIENTE, y la respuesta
        // tiene que coincidir con lo guardado.
        Entrega guardada = entregas.buscarPorClave(entrega.clave())
                .filter(Entrega::completada)
                .orElse(completada);
        return new ResultadoEntrega(guardada, true);
    }

    /** Una escritura del inventario con los elementos y la anotacion; se relee si otra llego antes. */
    private void aplicar(Entrega entrega) {
        for (int intento = 1; ; intento++) {
            Inventario inventario = inventarios.buscarPorPropietario(entrega.uid())
                    .orElseGet(() -> Inventario.vacio(entrega.uid()));
            if (inventario.recibio(entrega.id())) {
                return;
            }
            try {
                inventarios.guardar(inventario.recibirEntrega(entrega.id(), entrega.elementos()));
                return;
            } catch (ConflictoDeEscrituraException conflicto) {
                if (intento >= INTENTOS_ANTE_CONFLICTO) {
                    throw conflicto;
                }
            }
        }
    }

    /** Valida cada producto una vez y planea sus elementos: tipo, nombre y parte los decide el catalogo. */
    private List<ElementoInventario> planear(SolicitudDeEntrega solicitud) {
        Map<String, ResolutorDeProducto.DetalleProducto> vistos = new LinkedHashMap<>();
        List<ElementoInventario> planeados = new ArrayList<>();
        for (LineaDeEntrega linea : solicitud.productos()) {
            ResolutorDeProducto.DetalleProducto producto =
                    vistos.computeIfAbsent(linea.productoId(), this::productoEntregable);
            TipoElementoInventario tipo = TipoElementoInventario.valueOf(producto.tipo());
            ParteArmadura parte = tipo == TipoElementoInventario.ARMADURA ? producto.parteArmadura() : null;
            if (tipo == TipoElementoInventario.ARMADURA && parte == null) {
                throw new ProductoIncompletoException();
            }
            String nombre = producto.nombre() == null || producto.nombre().isBlank()
                    ? linea.productoId()
                    : producto.nombre();
            for (int unidad = 0; unidad < linea.cantidad(); unidad++) {
                planeados.add(ElementoInventario.entregado(
                        UUID.randomUUID().toString(), linea.productoId(), tipo, nombre, parte,
                        solicitud.origen(), solicitud.referencia()));
            }
        }
        return planeados;
    }

    private ResolutorDeProducto.DetalleProducto productoEntregable(String productoId) {
        ResolutorDeProducto.DetalleProducto producto;
        try {
            producto = productos.resolver(productoId);
        } catch (ProductoNoEncontradoException noExiste) {
            throw new ProductoInexistenteException();
        } catch (ResolutorDeProductoException caido) {
            throw new CatalogoNoDisponibleException(caido);
        }
        if (producto == null || producto.tipo() == null || !esTipoConocido(producto.tipo())) {
            throw new ProductoInexistenteException();
        }
        if (ESTADO_SUSPENDIDO.equals(producto.estado())) {
            throw new ProductoSuspendidoException();
        }
        return producto;
    }

    private static boolean esTipoConocido(String tipo) {
        for (TipoElementoInventario conocido : TipoElementoInventario.values()) {
            if (conocido.name().equals(tipo)) {
                return true;
            }
        }
        return false;
    }
}
