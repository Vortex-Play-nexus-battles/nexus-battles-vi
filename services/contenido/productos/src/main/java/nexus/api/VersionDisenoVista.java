package nexus.api;

import java.time.Instant;

import nexus.dominio.Producto;

/** Una versión del diseño visible en el historial administrativo. */
public record VersionDisenoVista(
                int version,
                Instant fecha,
                String autor,
                String imagen,
                String descripcion,
                boolean actual) {

        public static VersionDisenoVista de(Producto producto, boolean actual) {
                String autor = producto.modificadoPor();
                return new VersionDisenoVista(
                                producto.version(),
                                producto.modificadoEn(),
                                autor == null || autor.isBlank() ? "Sin registrar" : autor,
                                producto.imagen(),
                                producto.descripcion(),
                                actual);
        }
}
