package nexus.aplicacion;

import nexus.dominio.Producto;
import nexus.dominio.ProductoNoEncontradoException;
import nexus.persistencia.ProductoRepository;
import org.springframework.stereotype.Service;

@Service
public class ConsultarProductoServicio {

        private final ProductoRepository repositorio;

        public ConsultarProductoServicio(ProductoRepository repositorio) {
                this.repositorio = repositorio;
        }

        public Producto consultar(String id) {
                return repositorio.findById(id)
                        .orElseThrow(ProductoNoEncontradoException::new);
        }

        /**
         * B4: un producto SUSPENDIDO no existe para el publico — el mismo 404
         * que si no existiera, porque responder "existe pero no lo ves" tambien
         * lo filtraria. Para un jugador si existe (puede tenerlo en su
         * inventario) y para un servicio o un administrador, siempre.
         */
        public Producto consultar(String id, Visibilidad visibilidad) {
                Producto producto = consultar(id);
                if (!visibilidad.ve(producto)) {
                        throw new ProductoNoEncontradoException();
                }
                return producto;
        }
}
