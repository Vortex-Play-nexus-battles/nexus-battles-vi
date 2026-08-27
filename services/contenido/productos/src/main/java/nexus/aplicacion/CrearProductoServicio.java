package nexus.aplicacion;

import java.time.Instant;
import java.util.UUID;

import nexus.api.SolicitudCrearProducto;
import nexus.dominio.EstadoProducto;
import nexus.dominio.Producto;
import nexus.persistencia.ProductoRepository;
import org.springframework.stereotype.Service;

@Service
public class CrearProductoServicio {

        private final ProductoRepository repositorio;

        public CrearProductoServicio(ProductoRepository repositorio) {
                this.repositorio = repositorio;
        }

        public Producto crear(SolicitudCrearProducto solicitud) {
                Instant ahora = Instant.now();

                Producto producto = new Producto(
                        UUID.randomUUID().toString(),
                        solicitud.nombre(),
                        solicitud.imagen(),
                        solicitud.descripcion(),
                        solicitud.tipo(),
                        solicitud.tiraje(),
                        solicitud.precioCreditos(),
                        solicitud.precioMonedaReal(),
                        solicitud.premium(),
                        solicitud.prototipo(),
                        solicitud.heroe(),
                        solicitud.costoPoder(),
                        solicitud.multiplicadorNivel(),
                        solicitud.turnosCarga(),
                        solicitud.turnosRecarga(),
                        solicitud.efectoGeneral(),
                        solicitud.efectoPotenciado(),
                        solicitud.defensa(),
                        solicitud.parte(),
                        solicitud.efecto(),
                        solicitud.poderDeAtaque(),
                        solicitud.tasaDeCaida(),
                        EstadoProducto.ACTIVO,
                        1,
                        ahora,
                        ahora);

                return repositorio.save(producto);
        }
}