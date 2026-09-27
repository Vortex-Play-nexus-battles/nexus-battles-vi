package nexus.aplicacion;

import java.time.Instant;

import nexus.api.SolicitudCrearProducto;
import nexus.dominio.Producto;
import nexus.persistencia.ProductoRepository;
import org.springframework.stereotype.Service;

@Service
public class CrearProductoServicio {

        private final ProductoRepository repositorio;
        private final ProductoMapper mapper;

        public CrearProductoServicio(
                        ProductoRepository repositorio,
                        ProductoMapper mapper) {
                this.repositorio = repositorio;
                this.mapper = mapper;
        }

        /**
         * Da de alta el producto con {@code insert}, no con {@code save}: desde
         * B4 {@code Producto.version} es {@code @Version}, y un {@code save} de
         * un documento con version 1 se leeria como la modificacion de uno que
         * no existe. {@code insert} ademas nunca pisa un identificador ocupado.
         */
        public Producto crear(SolicitudCrearProducto solicitud) {
                Instant ahora = Instant.now();

                Producto producto = mapper.aProducto(solicitud, ahora);

                return repositorio.insert(producto);
        }
}
