package nexus.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import nexus.api.PlantillaDisenoProducto;
import nexus.api.SolicitudModificarProducto;
import nexus.api.VersionDisenoVista;
import nexus.dominio.Producto;
import nexus.dominio.ProductoNoEncontradoException;
import nexus.dominio.RespaldoProducto;
import nexus.dominio.TipoCambioProducto;
import nexus.persistencia.ProductoRepository;
import nexus.persistencia.RespaldoProductoRepository;
import org.springframework.stereotype.Service;

@Service
public class GestionarVersionesDisenoServicio {

        private final ProductoRepository productos;
        private final RespaldoProductoRepository respaldos;
        private final ModificarProductoServicio modificar;
        private final Clock reloj;

        public GestionarVersionesDisenoServicio(
                        ProductoRepository productos,
                        RespaldoProductoRepository respaldos,
                        ModificarProductoServicio modificar,
                        Clock reloj) {
                this.productos = productos;
                this.respaldos = respaldos;
                this.modificar = modificar;
                this.reloj = reloj;
        }

        public List<VersionDisenoVista> consultar(String productoId) {
                Producto actual = buscar(productoId);
                Map<Integer, VersionDisenoVista> versiones = new LinkedHashMap<>();
                versiones.put(actual.version(), VersionDisenoVista.de(actual, true));

                for (RespaldoProducto respaldo : respaldos.findByProductoIdOrderByModificadoEnDesc(productoId)) {
                        Producto anterior = respaldo.estadoAnterior();
                        Producto aplicado = respaldo.estadoAplicado();
                        if (anterior != null && cambioElDiseno(anterior, aplicado)) {
                                versiones.putIfAbsent(anterior.version(), VersionDisenoVista.de(anterior, false));
                        }
                }

                List<VersionDisenoVista> resultado = new ArrayList<>(versiones.values());
                resultado.sort(Comparator.comparingInt(VersionDisenoVista::version).reversed());
                return List.copyOf(resultado);
        }

        public PlantillaDisenoProducto exportar(String productoId) {
                Producto producto = buscar(productoId);
                return PlantillaDisenoProducto.de(producto, Instant.now(reloj));
        }

        public Producto importar(String productoId, PlantillaDisenoProducto plantilla, String autor) {
                SolicitudModificarProducto cambios = SolicitudModificarProducto.soloDiseno(
                                plantilla.imagen(),
                                plantilla.descripcion());
                return modificar.modificar(
                                productoId,
                                cambios,
                                autor,
                                TipoCambioProducto.IMPORTACION_DISENO);
        }

        private Producto buscar(String productoId) {
                return productos.findById(productoId)
                                .orElseThrow(ProductoNoEncontradoException::new);
        }

        private static boolean cambioElDiseno(Producto anterior, Producto aplicado) {
                return aplicado == null
                                || !Objects.equals(anterior.imagen(), aplicado.imagen())
                                || !Objects.equals(anterior.descripcion(), aplicado.descripcion());
        }
}
