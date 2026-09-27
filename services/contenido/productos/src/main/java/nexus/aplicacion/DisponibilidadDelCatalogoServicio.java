package nexus.aplicacion;

import nexus.productos.dominio.CatalogoProductos;
import nexus.productos.dominio.DisponibilidadProducto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Suspension logica y reactivacion de productos — HU-PRD-004, B4 (seccion
 * 7.2.1: "eliminacion logica").
 *
 * <p>Suspender no borra nada ni toca el tiraje: el producto sigue en el
 * catalogo y en los inventarios de quienes ya lo tienen, y deja de estar
 * disponible para nuevas adquisiciones (la reserva de tiraje lo rechaza) y para
 * el publico (la proyeccion publica no lo muestra). Reactivar lo devuelve al
 * estado que tenia, ACTIVO o UNICO. Las dos son idempotentes: repetirlas no
 * escribe nada.
 */
@Service
public class DisponibilidadDelCatalogoServicio {

        private static final Logger BITACORA = LoggerFactory.getLogger(DisponibilidadDelCatalogoServicio.class);

        private final CatalogoProductos catalogo;

        public DisponibilidadDelCatalogoServicio(CatalogoProductos catalogo) {
                this.catalogo = catalogo;
        }

        /** @param autor identificador estable del administrador, para la bitacora */
        public DisponibilidadProducto suspender(String productoId, String autor) {
                DisponibilidadProducto suspendido = catalogo.suspender(productoId);
                BITACORA.info("Producto {} suspendido por {}", productoId, autor);
                return suspendido;
        }

        /** @param autor identificador estable del administrador, para la bitacora */
        public DisponibilidadProducto reactivar(String productoId, String autor) {
                DisponibilidadProducto reactivado = catalogo.reactivar(productoId);
                BITACORA.info("Producto {} reactivado por {} ({})", productoId, autor, reactivado.estado());
                return reactivado;
        }
}
