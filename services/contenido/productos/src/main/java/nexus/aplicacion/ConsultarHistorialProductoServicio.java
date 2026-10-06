package nexus.aplicacion;

import java.util.List;

import nexus.api.CambioProductoVista;
import nexus.dominio.Producto;
import nexus.dominio.ProductoNoEncontradoException;
import nexus.persistencia.ProductoRepository;
import nexus.persistencia.RespaldoProductoRepository;
import org.springframework.stereotype.Service;

@Service
public class ConsultarHistorialProductoServicio {

        private final ProductoRepository productos;
        private final RespaldoProductoRepository respaldos;

        public ConsultarHistorialProductoServicio(
                        ProductoRepository productos,
                        RespaldoProductoRepository respaldos) {
                this.productos = productos;
                this.respaldos = respaldos;
        }

        public List<CambioProductoVista> consultar(String productoId) {
                Producto actual = productos.findById(productoId)
                                .orElseThrow(ProductoNoEncontradoException::new);
                return respaldos.findByProductoIdOrderByModificadoEnDesc(productoId).stream()
                                .map(respaldo -> CambioProductoVista.de(respaldo, actual.version()))
                                .toList();
        }
}
