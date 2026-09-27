package nexus.aplicacion;

import java.time.Clock;
import java.util.Optional;

import nexus.dominio.AdquisicionRegistrada;
import nexus.dominio.ClaveDeIdempotenciaReutilizadaException;
import nexus.dominio.ProductoNoEncontradoException;
import nexus.persistencia.AdquisicionRegistradaRepository;
import nexus.productos.dominio.CatalogoProductos;
import nexus.productos.dominio.EstadoAdquisicion;
import nexus.productos.dominio.ResultadoAdquisicion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Reserva de tiraje para quien vende — B4
 * ({@code POST /api/v1/productos/{id}/adquisiciones}, contrato 1.4.0, HU-PRD-002).
 *
 * <p>Solo la usan servicios (ms-ecommerce al cobrar una compra, y los que
 * vengan): reserva UNA unidad y nada mas. No cobra ni entrega; la entrega al
 * inventario es {@code POST /api/v1/inventario/entregas}.
 *
 * <h2>Idempotente por Idempotency-Key, sin transacciones</h2>
 *
 * Mongo standalone no tiene transacciones multi-documento, asi que la reserva
 * (en el producto) y el registro de la clave (en {@code adquisiciones}) son dos
 * escrituras. El orden las hace seguras:
 *
 * <ol>
 *   <li>Se registra la clave, en curso ({@code insert}: el {@code _id} es la
 *       clave, unico). Si ya existia con otro producto, 409 sin tocar nada; si
 *       ya tenia resultado, se devuelve ese mismo resultado.</li>
 *   <li>Se reserva. La reserva recuerda la clave en el propio producto, en la
 *       misma escritura que descuenta la unidad: si el paso 3 no llega a
 *       escribirse y el cliente reintenta, la reserva ve su clave y responde
 *       ACEPTADA sin descontar otra vez.</li>
 *   <li>Se guarda el resultado junto a la clave. Tambien los negativos
 *       (AGOTADO, SUSPENDIDO): la misma clave devuelve siempre la misma
 *       respuesta; un intento nuevo lleva una clave nueva.</li>
 * </ol>
 * Un producto inexistente no consume la clave: se borra y responde 404.
 */
@Service
public class AdquirirProductoServicio {

        private static final Logger BITACORA = LoggerFactory.getLogger(AdquirirProductoServicio.class);

        private final CatalogoProductos catalogo;
        private final AdquisicionRegistradaRepository registro;
        private final Clock reloj;

        public AdquirirProductoServicio(
                        CatalogoProductos catalogo,
                        AdquisicionRegistradaRepository registro,
                        Clock reloj) {
                this.catalogo = catalogo;
                this.registro = registro;
                this.reloj = reloj;
        }

        /**
         * @param clave       {@code Idempotency-Key} (la valida la frontera HTTP)
         * @param solicitante {@code azp} del servicio que pide, para la bitacora
         * @throws ProductoNoEncontradoException si el producto no existe (404)
         * @throws ClaveDeIdempotenciaReutilizadaException si la clave ya se uso
         *         con otro producto (409)
         */
        public ResultadoAdquisicion adquirir(String productoId, String clave, String solicitante) {
                Optional<ResultadoAdquisicion> yaResuelta = registrarClave(productoId, clave, solicitante);
                if (yaResuelta.isPresent()) {
                        return yaResuelta.get();
                }

                ResultadoAdquisicion resultado = catalogo.adquirir(productoId, clave);
                if (resultado.estado() == EstadoAdquisicion.NO_ENCONTRADO) {
                        registro.deleteById(clave);
                        throw new ProductoNoEncontradoException();
                }

                registro.save(new AdquisicionRegistrada(
                        clave, productoId, resultado.estado(), resultado.mensaje(),
                        solicitante, reloj.instant()));
                BITACORA.info("Adquisicion {} de {} para {}: {}",
                        clave, productoId, solicitante, resultado.estado());
                return resultado;
        }

        /**
         * Registra la clave en curso, o devuelve el resultado que ya tenia.
         *
         * @return vacio si hay que reservar (clave nueva, o en curso de un
         *         intento anterior que no termino)
         */
        private Optional<ResultadoAdquisicion> registrarClave(String productoId, String clave, String solicitante) {
                AdquisicionRegistrada previa = registro.findById(clave).orElse(null);
                if (previa == null) {
                        try {
                                registro.insert(new AdquisicionRegistrada(
                                        clave, productoId, null, null, solicitante, reloj.instant()));
                                return Optional.empty();
                        } catch (DuplicateKeyException otraPeticionConLaMismaClave) {
                                previa = registro.findById(clave).orElseThrow(() -> otraPeticionConLaMismaClave);
                        }
                }
                if (!previa.productoId().equals(productoId)) {
                        throw new ClaveDeIdempotenciaReutilizadaException();
                }
                if (previa.enCurso()) {
                        return Optional.empty();
                }
                return Optional.of(new ResultadoAdquisicion(previa.estado(), previa.mensaje()));
        }
}
