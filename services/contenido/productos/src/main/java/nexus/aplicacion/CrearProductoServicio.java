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

        public Producto crear(SolicitudCrearProducto solicitud) {
                Instant ahora = Instant.now();

                Producto producto = mapper.aProducto(solicitud, ahora);

                return repositorio.save(producto);
        }
}
