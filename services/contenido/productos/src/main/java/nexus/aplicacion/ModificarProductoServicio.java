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
import nexus.dominio.TipoCambioProducto;
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

        /**
         * @param autor identificador estable de quien modifica (claim
         *              {@code uid} del token del administrador). Queda en el
         *              respaldo y en el producto ({@code modificadoPor}).
         * @throws org.springframework.dao.OptimisticLockingFailureException si
         *         otro escribio el producto entre la lectura y el guardado; la
         *         API lo responde con 409 y el respaldo ya se revirtio
         */
        public Producto modificar(String id, SolicitudModificarProducto cambios, String autor) {
                Producto existente = repositorio.findById(id)
                        .orElseThrow(ProductoNoEncontradoException::new);
                if (existente.version() == 0) {
                        // Anterior a @Version (sin version, o insertado a mano con 0):
                        // para Spring Data seria un alta. Se lleva a la version 1 y se
                        // relee, y la edicion sigue como cualquier otra.
                        repositorio.normalizarVersion(id);
                        existente = repositorio.findById(id)
                                .orElseThrow(ProductoNoEncontradoException::new);
                }

                SolicitudCrearProducto fusionada = mapper.fusionar(existente, cambios);

                Set<ConstraintViolation<SolicitudCrearProducto>> violaciones =
                        validator.validate(fusionada);

                if (!violaciones.isEmpty()) {
                        throw new ModificacionProductoInvalidaException(violaciones);
                }

                Instant ahora = Instant.now();

                Producto actualizado = mapper.actualizar(fusionada, existente, ahora, autor);
                Producto resultadoEsperado = conVersion(actualizado, existente.version() + 1);

                RespaldoProducto respaldo = new RespaldoProducto(
                        UUID.randomUUID().toString(),
                        existente.id(),
                        existente,
                        resultadoEsperado,
                        ahora,
                        autor,
                        TipoCambioProducto.MODIFICACION,
                        null);

                // El respaldo se guarda ANTES de tocar el producto: si esto falla,
                // el producto original queda intacto y no hay nada que revertir.
                respaldoRepositorio.save(respaldo);

                // CONCURRENCIA (B4): Producto declara @Version. El guardado solo
                // reemplaza el documento si su version sigue siendo la que se leyo
                // arriba; si otro administrador —o una suspension, o una reserva
                // de tiraje, que tambien la suben— escribio entre medias, Spring
                // Data lanza OptimisticLockingFailureException y la API responde
                // 409. Hasta B4 ganaba la ultima escritura, sin aviso.
                //
                // MongoDB standalone (sin replica set) no soporta transacciones
                // multi-documento de forma confiable, por eso no se usa
                // @Transactional aqui: en su lugar, si el guardado final falla
                // despues de haber guardado el respaldo —tambien por conflicto—,
                // se borra ese respaldo de forma compensatoria para no dejar un
                // historico huerfano que referencia un cambio que nunca se aplico.
                try {
                        return repositorio.save(actualizado);
                } catch (RuntimeException fallo) {
                        respaldoRepositorio.deleteById(respaldo.id());
                        throw fallo;
                }
        }

        private static Producto conVersion(Producto producto, int version) {
                return new Producto(
                                producto.id(), producto.nombre(), producto.imagen(), producto.descripcion(),
                                producto.tipo(), producto.tiraje(), producto.precioCreditos(),
                                producto.precioMonedaReal(), producto.premium(), producto.prototipo(),
                                producto.heroe(), producto.costoPoder(), producto.multiplicadorNivel(),
                                producto.turnosCarga(), producto.turnosRecarga(), producto.efectoGeneral(),
                                producto.efectoPotenciado(), producto.defensa(), producto.parte(), producto.efecto(),
                                producto.poderDeAtaque(), producto.tasaDeCaida(), producto.estado(), version,
                                producto.creadoEn(), producto.modificadoEn(), producto.promocion(), producto.origen(),
                                producto.semillaVersion(), producto.modificadoPor(),
                                producto.estadoAnteriorSuspension(), producto.reservasRecientes());
        }
}
