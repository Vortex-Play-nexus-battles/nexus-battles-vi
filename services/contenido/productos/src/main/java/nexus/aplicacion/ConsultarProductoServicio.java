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
}
