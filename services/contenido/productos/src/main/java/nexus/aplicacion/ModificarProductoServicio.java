package nexus.aplicacion;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import nexus.api.SolicitudCrearProducto;
import nexus.api.SolicitudModificarProducto;
import nexus.dominio.ModificacionProductoInvalidaException;
import nexus.dominio.Producto;
import nexus.dominio.ProductoNoEncontradoException;
import nexus.dominio.RespaldoProducto;
import nexus.persistencia.ProductoRepository;
import nexus.persistencia.RespaldoProductoRepository;
import org.springframework.stereotype.Service;

@Service
public class ModificarProductoServicio {

        private final ProductoRepository repositorio;
        private final RespaldoProductoRepository respaldoRepositorio;
        private final ProductoMapper mapper;
        private final Validator validator;

        public ModificarProductoServicio(
                        ProductoRepository repositorio,
                        RespaldoProductoRepository respaldoRepositorio,
                        ProductoMapper mapper,
                        Validator validator) {
                this.repositorio = repositorio;
                this.respaldoRepositorio = respaldoRepositorio;
                this.mapper = mapper;
                this.validator = validator;
        }

        public Producto modificar(String id, SolicitudModificarProducto cambios) {
                Producto existente = repositorio.findById(id)
                        .orElseThrow(ProductoNoEncontradoException::new);

                SolicitudCrearProducto fusionada = mapper.fusionar(existente, cambios);

                Set<ConstraintViolation<SolicitudCrearProducto>> violaciones =
                        validator.validate(fusionada);

                if (!violaciones.isEmpty()) {
                        throw new ModificacionProductoInvalidaException(violaciones);
                }

                Instant ahora = Instant.now();

                RespaldoProducto respaldo = new RespaldoProducto(
                        UUID.randomUUID().toString(),
                        existente.id(),
                        existente,
                        ahora);

                // El respaldo se guarda ANTES de tocar el producto: si esto falla,
                // el producto original queda intacto y no hay nada que revertir.
                respaldoRepositorio.save(respaldo);

                Producto actualizado = mapper.actualizar(fusionada, existente, ahora);

                // NOTA DE CONCURRENCIA (pedido explicito de dejarlo documentado):
                // Producto no declara @Version (control de concurrencia optimista
                // de Spring Data). Dos administradores modificando el MISMO campo
                // del MISMO producto al mismo tiempo pueden pisarse silenciosamente:
                // gana la ultima escritura, sin error ni aviso. La fusion parcial de
                // este servicio evita el problema cuando editan campos DISTINTOS,
                // pero no lo resuelve por completo. Fuera de alcance de HU-PRD-003;
                // valdria la pena una historia aparte de control de concurrencia
                // optimista sobre Producto.
                //
                // MongoDB standalone (sin replica set) no soporta transacciones
                // multi-documento de forma confiable, por eso no se usa
                // @Transactional aqui: en su lugar, si el guardado final falla
                // despues de haber guardado el respaldo, se borra ese respaldo de
                // forma compensatoria para no dejar un historico huerfano que
                // referencia un cambio que nunca se aplico.
                try {
                        return repositorio.save(actualizado);
                } catch (RuntimeException fallo) {
                        respaldoRepositorio.deleteById(respaldo.id());
                        throw fallo;
                }
        }
}
