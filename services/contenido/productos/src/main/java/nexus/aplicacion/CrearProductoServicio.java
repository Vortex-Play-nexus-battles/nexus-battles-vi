package nexus.aplicacion;

import java.time.Instant;
import java.util.UUID;

import nexus.api.SolicitudCrearProducto;
import nexus.dominio.Producto;
import nexus.dominio.RespaldoProducto;
import nexus.dominio.TipoCambioProducto;
import nexus.persistencia.ProductoRepository;
import nexus.persistencia.RespaldoProductoRepository;
import org.springframework.stereotype.Service;

@Service
public class CrearProductoServicio {

        private final ProductoRepository repositorio;
        private final ProductoMapper mapper;
        private final RespaldoProductoRepository respaldos;

        public CrearProductoServicio(
                        ProductoRepository repositorio,
                        ProductoMapper mapper,
                        RespaldoProductoRepository respaldos) {
                this.repositorio = repositorio;
                this.mapper = mapper;
                this.respaldos = respaldos;
        }

        /**
         * Da de alta el producto con {@code insert}, no con {@code save}: desde
         * B4 {@code Producto.version} es {@code @Version}, y un {@code save} de
         * un documento con version 1 se leeria como la modificacion de uno que
         * no existe. {@code insert} ademas nunca pisa un identificador ocupado.
         */
        public Producto crear(SolicitudCrearProducto solicitud) {
                return crear(solicitud, "sistema");
        }

        public Producto crear(SolicitudCrearProducto solicitud, String autor) {
                Instant ahora = Instant.now();

                Producto producto = mapper.aProducto(solicitud, ahora);
                RespaldoProducto auditoria = new RespaldoProducto(
                                UUID.randomUUID().toString(),
                                producto.id(),
                                null,
                                producto,
                                ahora,
                                autor,
                                TipoCambioProducto.CREACION,
                                null);
                respaldos.save(auditoria);
                try {
                        return repositorio.insert(producto);
                } catch (RuntimeException fallo) {
                        respaldos.deleteById(auditoria.id());
                        throw fallo;
                }
        }
}
