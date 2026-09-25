package nexus.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/**
 * Producto del catalogo maestro (coleccion {@code productos}).
 *
 * <h2>{@code version}: bloqueo optimista de verdad (B4)</h2>
 *
 * Hasta B4 {@code version} era un contador que el mapeador subia a mano en cada
 * modificacion y nadie comprobaba: dos administradores editando el mismo
 * producto se pisaban en silencio. Ahora es {@link Version} de Spring Data:
 * {@code save} solo reemplaza el documento si su version sigue siendo la que se
 * leyo, y la sube; si otro escribio entre medias lanza
 * {@code OptimisticLockingFailureException}, que la API traduce a 409. Las
 * escrituras parciales ({@code updateFirst}, {@code findAndModify}: suspender,
 * reactivar, reservar tiraje) la suben tambien, asi que una edicion completa
 * nunca deshace una suspension ni devuelve unidades ya vendidas.
 *
 * <p>Por ser un primitivo, 0 significa "documento nuevo": {@code insert} lo
 * inicializa en 1, que es el minimo que publica el contrato. Por eso las altas
 * usan {@code insert} y no {@code save}.
 *
 * <h2>Campos de B4</h2>
 * <ul>
 *   <li>{@code promocion}: descuento con vigencia (ver {@link Promocion}).</li>
 *   <li>{@code origen} y {@code semillaVersion}: marcas de la semilla
 *       versionada. {@code modificadoPor}: uid del ultimo administrador que
 *       edito el contenido; si no es nulo, la semilla respeta el producto.</li>
 *   <li>{@code estadoAnteriorSuspension}: el estado al que vuelve al
 *       reactivarse. Ya se guardaba, fuera del registro; mapearlo evita que una
 *       edicion completa de un producto suspendido lo borre.</li>
 *   <li>{@code reservasRecientes}: las ultimas claves de idempotencia que
 *       reservaron tiraje. Viven en el MISMO documento que el tiraje para que
 *       descontar una unidad y recordar la clave sean una sola escritura
 *       atomica (ver {@code RepositorioDisponibilidadMongo}).</li>
 * </ul>
 * Ninguno de estos campos internos sale en la proyeccion publica.
 */
@Document(collection = "productos")
public record Producto(

        @Id
        String id,

        String nombre,

        String imagen,

        String descripcion,

        TipoProducto tipo,

        int tiraje,

        Integer precioCreditos,

        @Field(targetType = FieldType.DECIMAL128)
        BigDecimal precioMonedaReal,

        boolean premium,

        String prototipo,

        String heroe,

        Integer costoPoder,

        @Field(targetType = FieldType.DECIMAL128)
        BigDecimal multiplicadorNivel,

        Integer turnosCarga,

        Integer turnosRecarga,

        String efectoGeneral,

        String efectoPotenciado,

        Integer defensa,

        ParteArmadura parte,

        String efecto,

        Integer poderDeAtaque,

        @Field(targetType = FieldType.DECIMAL128)
        BigDecimal tasaDeCaida,

        EstadoProducto estado,

        @Version
        int version,

        Instant creadoEn,

        Instant modificadoEn,

        Promocion promocion,

        OrigenProducto origen,

        Integer semillaVersion,

        String modificadoPor,

        EstadoProducto estadoAnteriorSuspension,

        List<String> reservasRecientes) {

        @PersistenceCreator
        public Producto {
                reservasRecientes = reservasRecientes == null ? List.of() : List.copyOf(reservasRecientes);
        }

        /**
         * La forma anterior a B4, sin promocion ni marcas. La conservan las
         * altas que no las necesitan y las pruebas escritas antes de B4.
         */
        public Producto(
                        String id,
                        String nombre,
                        String imagen,
                        String descripcion,
                        TipoProducto tipo,
                        int tiraje,
                        Integer precioCreditos,
                        BigDecimal precioMonedaReal,
                        boolean premium,
                        String prototipo,
                        String heroe,
                        Integer costoPoder,
                        BigDecimal multiplicadorNivel,
                        Integer turnosCarga,
                        Integer turnosRecarga,
                        String efectoGeneral,
                        String efectoPotenciado,
                        Integer defensa,
                        ParteArmadura parte,
                        String efecto,
                        Integer poderDeAtaque,
                        BigDecimal tasaDeCaida,
                        EstadoProducto estado,
                        int version,
                        Instant creadoEn,
                        Instant modificadoEn) {
                this(id, nombre, imagen, descripcion, tipo, tiraje, precioCreditos, precioMonedaReal,
                        premium, prototipo, heroe, costoPoder, multiplicadorNivel, turnosCarga,
                        turnosRecarga, efectoGeneral, efectoPotenciado, defensa, parte, efecto,
                        poderDeAtaque, tasaDeCaida, estado, version, creadoEn, modificadoEn,
                        null, null, null, null, null, List.of());
        }

        /** Si lo edito un administrador (la semilla ya no lo pone al dia). */
        public boolean editadoPorAdministrador() {
                return modificadoPor != null && !modificadoPor.isBlank();
        }
}
