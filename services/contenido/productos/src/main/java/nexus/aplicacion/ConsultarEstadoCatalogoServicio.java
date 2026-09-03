package nexus.aplicacion;

import java.util.EnumMap;
import java.util.Map;

import nexus.api.ResumenCatalogo;
import nexus.dominio.EstadoProducto;
import nexus.dominio.TipoProducto;
import nexus.persistencia.ProductoRepository;
import org.springframework.stereotype.Service;

@Service
public class ConsultarEstadoCatalogoServicio {

        private final ProductoRepository repositorio;

        public ConsultarEstadoCatalogoServicio(ProductoRepository repositorio) {
                this.repositorio = repositorio;
        }

        public ResumenCatalogo consultar() {
                Map<TipoProducto, Long> porTipo =
                        new EnumMap<>(TipoProducto.class);
                for (TipoProducto tipo : TipoProducto.values()) {
                        porTipo.put(tipo, repositorio.countByTipo(tipo));
                }

                Map<EstadoProducto, Long> porEstado =
                        new EnumMap<>(EstadoProducto.class);
                for (EstadoProducto estado : EstadoProducto.values()) {
                        porEstado.put(estado, repositorio.countByEstado(estado));
                }

                return new ResumenCatalogo(
                        repositorio.count(),
                        porTipo,
                        porEstado);
        }
}
