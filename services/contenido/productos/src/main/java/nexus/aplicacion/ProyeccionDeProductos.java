package nexus.aplicacion;

import java.time.Clock;
import java.time.Instant;

import nexus.api.ProductoCreado;
import nexus.api.PromocionVista;
import nexus.dominio.Producto;
import org.springframework.stereotype.Component;

/**
 * Lo que la API devuelve de un producto segun quien pregunta — B4.
 *
 * <p>Decide que campos ve cada quien (los internos, solo un servicio o un
 * administrador; si el producto existe para el lo decide
 * {@link Visibilidad#ve}) y evalua, con el reloj del servidor, si la promocion
 * esta vigente.
 */
@Component
public class ProyeccionDeProductos {

        private final ProductoMapper mapper;
        private final Clock reloj;

        public ProyeccionDeProductos(ProductoMapper mapper, Clock reloj) {
                this.mapper = mapper;
                this.reloj = reloj;
        }

        /**
         * El producto en la forma que le corresponde. La promocion sale siempre
         * en la vista completa (con {@code vigente} calculado) y en la publica
         * solo mientras esta vigente: una promocion futura o vencida es un dato
         * de administracion, no un anuncio.
         */
        public ProductoCreado proyectar(Producto producto, Visibilidad visibilidad) {
                Instant ahora = reloj.instant();
                PromocionVista promocion = PromocionVista.de(producto.promocion(), ahora);
                if (visibilidad.veLoInterno()) {
                        return mapper.aRespuestaCompleta(producto, promocion);
                }
                return mapper.aRespuestaPublica(
                        producto,
                        promocion != null && promocion.vigente() ? promocion : null);
        }
}
