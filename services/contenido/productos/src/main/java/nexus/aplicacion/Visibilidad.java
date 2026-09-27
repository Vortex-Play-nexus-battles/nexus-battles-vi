package nexus.aplicacion;

import nexus.dominio.EstadoProducto;
import nexus.dominio.Producto;

/**
 * Cuanto del catalogo ve quien consulta — B4 (contrato productos 1.4.0).
 *
 * <p>Las dos lecturas del catalogo ({@code GET /api/v1/productos} y
 * {@code /{id}}) siguen siendo publicas, pero ya no devuelven lo mismo a todos:
 *
 * <ul>
 *   <li>{@link #PUBLICA}, sin token (la vitrina de la landing, la tienda sin
 *       sesion): ni productos SUSPENDIDO ni campos internos.</li>
 *   <li>{@link #AUTENTICADA}, un jugador o un moderador: tampoco campos
 *       internos, pero SI un producto suspendido pedido por su id. El documento
 *       dice que un producto suspendido "permanece en el inventario de los
 *       jugadores que ya lo poseen" (7.2.1), y la ficha de ese inventario lo
 *       muestra con el aviso de no disponible (RN-27): negarselo a su dueno con
 *       un 404 romperia esa ficha.</li>
 *   <li>{@link #PRIVILEGIADA}, un servicio o un ADMINISTRADOR /
 *       SUPER_ADMINISTRADOR: todo, incluido el listado de suspendidos.</li>
 * </ul>
 */
public enum Visibilidad {

        PUBLICA(false, false),
        AUTENTICADA(true, false),
        PRIVILEGIADA(true, true);

        private final boolean veSuspendidosPorId;
        private final boolean veLoInterno;

        Visibilidad(boolean veSuspendidosPorId, boolean veLoInterno) {
                this.veSuspendidosPorId = veSuspendidosPorId;
                this.veLoInterno = veLoInterno;
        }

        /** Si un producto SUSPENDIDO pedido por su identificador existe para quien pregunta. */
        public boolean veSuspendidosPorId() {
                return veSuspendidosPorId;
        }

        /** Si ese producto existe para quien pregunta: un SUSPENDIDO, no para el publico. */
        public boolean ve(Producto producto) {
                return producto.estado() != EstadoProducto.SUSPENDIDO || veSuspendidosPorId;
        }

        /**
         * Si ve los campos internos y puede listar los suspendidos: solo quien
         * administra el catalogo o un servicio del sistema.
         */
        public boolean veLoInterno() {
                return veLoInterno;
        }
}
