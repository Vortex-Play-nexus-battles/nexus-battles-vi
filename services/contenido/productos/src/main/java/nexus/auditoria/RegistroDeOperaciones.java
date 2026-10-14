package nexus.auditoria;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import nexus.dominio.Banner;
import nexus.dominio.Producto;
import nexus.dominio.RespaldoProducto;
import nexus.dominio.TipoCambioProducto;
import nexus.recursos.RecursoGrafico;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * El registro de lo que hacen los administradores en el catálogo — SA-18,
 * §7.2.5 («registro de auditoría de todas las operaciones realizadas») y
 * §7.3.5 (la bitácora de todas las acciones administrativas).
 *
 * <p>Cada operación queda con su momento, quién la hizo, el tipo, lo afectado
 * y su nombre, los valores anteriores y nuevos, el motivo y la IP de la
 * petición. Se escribe <b>después</b> de que la operación se aplicó: así nunca
 * queda registrada una operación que no ocurrió (MongoDB sin réplica no da
 * transacciones entre documentos). Si la escritura falla, la operación ya
 * hecha no se deshace: queda el error en la bitácora del servicio con todo lo
 * necesario para reconstruirla, y el historial del producto la conserva.
 *
 * <p>Solo se registra lo que hace una persona: el alta sin autor (la que usan
 * las pruebas y el {@code "sistema"}) y la semilla del catálogo no son
 * acciones administrativas.
 */
@Component
public class RegistroDeOperaciones {

        private static final Logger BITACORA = LoggerFactory.getLogger(RegistroDeOperaciones.class);

        /** Límites del lote (productos.yaml 1.12.0), los mismos que los de los cambios del catálogo. */
        static final int LIMITE_MINIMO = 1;
        static final int LIMITE_MAXIMO = 200;

        /** El nombre de lo afectado cabe en la columna de la bitácora central. */
        static final int LARGO_DEL_NOMBRE = 120;

        /** El autor de lo que no hace una persona. */
        static final String SISTEMA = "sistema";

        private final OperacionAdministrativaRepository operaciones;
        private final Clock reloj;

        public RegistroDeOperaciones(OperacionAdministrativaRepository operaciones, Clock reloj) {
                this.operaciones = Objects.requireNonNull(operaciones, "las operaciones son obligatorias");
                this.reloj = Objects.requireNonNull(reloj, "el reloj es obligatorio");
        }

        /**
         * Un cambio de producto ya aplicado, a partir de su entrada del
         * historial: alta, modificación, cambio de balance, importación de
         * diseño, suspensión, reactivación o reversión.
         */
        public void deProducto(RespaldoProducto respaldo) {
                if (respaldo == null || !esPersona(respaldo.autor())) {
                        return;
                }
                Producto antes = respaldo.estadoAnterior();
                Producto despues = respaldo.estadoAplicado();
                ResumenDeCambios.Cambio cambio = ResumenDeCambios.entre(antes, despues);
                Producto conNombre = despues != null ? despues : antes;
                registrar(new OperacionAdministrativa(
                                null,
                                momento(respaldo.modificadoEn()),
                                respaldo.autor(),
                                tipoDe(respaldo.tipoCambioNormalizado(), antes, despues),
                                ObjetoAuditado.PRODUCTO,
                                respaldo.productoId(),
                                conNombre == null ? null : nombre(conNombre.nombre()),
                                cambio.antes(),
                                cambio.despues(),
                                respaldo.motivo(),
                                null,
                                null));
        }

        /** El alta o el retiro de un recurso gráfico de la biblioteca. */
        public void deRecurso(TipoOperacion tipo, RecursoGrafico antes, RecursoGrafico despues, String autor,
                        String motivo) {
                RecursoGrafico recurso = despues != null ? despues : antes;
                if (recurso == null || !esPersona(autor)) {
                        return;
                }
                Instant cuando = tipo == TipoOperacion.RETIRO_RECURSO ? recurso.retiradoEn() : recurso.subidoEn();
                registrar(new OperacionAdministrativa(
                                null,
                                momento(cuando),
                                autor,
                                tipo,
                                ObjetoAuditado.RECURSO_GRAFICO,
                                recurso.id(),
                                nombre(recurso.nombre()),
                                ResumenDeCambios.deRecurso(antes),
                                ResumenDeCambios.deRecurso(despues),
                                motivo,
                                null,
                                null));
        }

        /** La publicación, la edición o el retiro de un banner. */
        public void deBanner(TipoOperacion tipo, Banner antes, Banner despues, String autor, String motivo) {
                Banner banner = despues != null ? despues : antes;
                if (banner == null || !esPersona(autor)) {
                        return;
                }
                registrar(new OperacionAdministrativa(
                                null,
                                momento(banner.modificadoEn()),
                                autor,
                                tipo,
                                ObjetoAuditado.BANNER,
                                banner.id(),
                                nombre(banner.contenido()),
                                ResumenDeCambios.deBanner(antes),
                                ResumenDeCambios.deBanner(despues),
                                motivo,
                                null,
                                null));
        }

        /**
         * Las operaciones de {@code (desde, ahora]}, de la más antigua a la más
         * reciente — {@code GET /api/v1/productos/auditoria/operaciones}.
         *
         * <p>Sin {@code desde}, desde la primera: la bitácora central necesita
         * todas, no una línea base. Con más de {@code limite}, el lote se corta
         * en la marca de tiempo de la última que cabe y entra el grupo entero de
         * esa marca, para que {@code desde=hasta} no se salte ninguna.
         */
        public LoteDeOperaciones consultarLote(Instant desde, int limite) {
                if (limite < LIMITE_MINIMO || limite > LIMITE_MAXIMO) {
                        throw new IllegalArgumentException(
                                        "limite debe estar entre " + LIMITE_MINIMO + " y " + LIMITE_MAXIMO);
                }
                Instant inicio = desde == null ? Instant.EPOCH : desde;
                Instant ahora = reloj.instant().truncatedTo(ChronoUnit.MILLIS);
                List<OperacionAdministrativa> primeras = enOrden(
                                operaciones.buscarPrimerasEntre(inicio, ahora, PageRequest.of(0, limite + 1)));
                if (primeras.size() <= limite) {
                        Instant hasta = primeras.isEmpty() ? inicio : primeras.getLast().ocurridaEn();
                        return new LoteDeOperaciones(hasta, true, primeras);
                }
                Instant corte = primeras.get(limite - 1).ocurridaEn();
                List<OperacionAdministrativa> lote = enOrden(operaciones.buscarEntre(inicio, corte));
                boolean completo = operaciones.buscarPrimerasEntre(corte, ahora, PageRequest.of(0, 1)).isEmpty();
                return new LoteDeOperaciones(corte, completo, lote);
        }

        /** Lo que se hizo sobre un objeto, de lo más reciente a lo más antiguo. */
        public List<OperacionAdministrativa> historialDe(ObjetoAuditado objeto, String objetoId) {
                return operaciones.findByObjetoAndObjetoIdOrderByOcurridaEnDesc(objeto, objetoId);
        }

        private void registrar(OperacionAdministrativa borrador) {
                OperacionAdministrativa operacion = new OperacionAdministrativa(
                                UUID.randomUUID().toString(),
                                borrador.ocurridaEn(),
                                borrador.autor().strip(),
                                borrador.tipo(),
                                borrador.objeto(),
                                borrador.objetoId(),
                                borrador.nombre(),
                                borrador.valorAnterior(),
                                borrador.valorNuevo(),
                                motivoLimpio(borrador.motivo()),
                                descripcion(borrador.tipo()),
                                OrigenDeLaPeticion.ipActual());
                try {
                        operaciones.insert(operacion);
                } catch (RuntimeException fallo) {
                        BITACORA.error(
                                        "No se pudo registrar la operacion administrativa {} sobre {} {} de {} ({}): {}",
                                        operacion.tipo(),
                                        operacion.objeto(),
                                        operacion.objetoId(),
                                        operacion.autor(),
                                        operacion.ocurridaEn(),
                                        fallo.getMessage(),
                                        fallo);
                }
        }

        static TipoOperacion tipoDe(TipoCambioProducto tipo, Producto antes, Producto despues) {
                return switch (tipo) {
                        case CREACION -> TipoOperacion.ALTA_PRODUCTO;
                        case SUSPENSION -> TipoOperacion.SUSPENSION_PRODUCTO;
                        case REACTIVACION -> TipoOperacion.REACTIVACION_PRODUCTO;
                        case REVERSION -> TipoOperacion.REVERSION_PRODUCTO;
                        case IMPORTACION_DISENO -> TipoOperacion.IMPORTACION_DE_DISENO;
                        case MODIFICACION -> ResumenDeCambios.tocaElBalance(antes, despues)
                                        ? TipoOperacion.CAMBIO_DE_BALANCE
                                        : TipoOperacion.MODIFICACION_PRODUCTO;
                };
        }

        /** Qué se hizo, en palabras: lo que la bitácora muestra si no hay motivo escrito. */
        static String descripcion(TipoOperacion tipo) {
                return switch (tipo) {
                        case ALTA_PRODUCTO -> "Alta del producto en el catálogo";
                        case MODIFICACION_PRODUCTO -> "Modificación del producto";
                        case CAMBIO_DE_BALANCE -> "Cambio de balance del producto";
                        case IMPORTACION_DE_DISENO -> "Importación de una versión de diseño del producto";
                        case SUSPENSION_PRODUCTO -> "Suspensión del producto";
                        case REACTIVACION_PRODUCTO -> "Reactivación del producto";
                        case REVERSION_PRODUCTO -> "Reversión del producto a un estado anterior";
                        case ALTA_RECURSO -> "Alta de un recurso gráfico en la biblioteca";
                        case RETIRO_RECURSO -> "Retiro de un recurso gráfico de la biblioteca";
                        case ALTA_BANNER -> "Publicación de un banner";
                        case MODIFICACION_BANNER -> "Edición de un banner";
                        case RETIRO_BANNER -> "Retiro de un banner";
                };
        }

        private Instant momento(Instant propio) {
                return (propio != null ? propio : reloj.instant()).truncatedTo(ChronoUnit.MILLIS);
        }

        private static boolean esPersona(String autor) {
                return autor != null && !autor.isBlank() && !SISTEMA.equals(autor.strip());
        }

        private static String nombre(String texto) {
                String limpio = ResumenDeCambios.recortado(texto);
                if (limpio == null || limpio.length() <= LARGO_DEL_NOMBRE) {
                        return limpio;
                }
                return limpio.substring(0, LARGO_DEL_NOMBRE - 1).stripTrailing() + "…";
        }

        private static String motivoLimpio(String motivo) {
                return motivo == null || motivo.isBlank() ? null : motivo.strip();
        }

        /** De la más antigua a la más reciente y, con la misma marca, por id: un orden estable. */
        private static List<OperacionAdministrativa> enOrden(List<OperacionAdministrativa> lista) {
                return lista.stream()
                                .sorted(Comparator.comparing(OperacionAdministrativa::ocurridaEn)
                                                .thenComparing(OperacionAdministrativa::id))
                                .toList();
        }
}
