package nexus.dominio;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Un mensaje del banner rotativo de la página de inicio — HU-PRD-013, §7.2.4.
 *
 * <p>{@code creadoPor} y {@code modificadoPor} (SA-18, productos.yaml 1.12.0):
 * el {@code uid} de quien lo publicó y de quien lo tocó por última vez. Los
 * banners anteriores a SA-18 no los tienen. Lo que se hizo con cada uno, con
 * sus valores antes y después y su motivo, está en su historial (las
 * operaciones administrativas del catálogo).
 */
@Document(collection = "banners")
public record Banner(
        @Id String id,
        String contenido,
        Instant publicarDesde,
        Instant vigenteHasta,
        boolean retirado,
        Instant creadoEn,
        Instant modificadoEn,
        String creadoPor,
        String modificadoPor) {

        @PersistenceCreator
        public Banner {
        }

        /** Sin autores, como antes de SA-18. */
        public Banner(
                        String id,
                        String contenido,
                        Instant publicarDesde,
                        Instant vigenteHasta,
                        boolean retirado,
                        Instant creadoEn,
                        Instant modificadoEn) {
                this(id, contenido, publicarDesde, vigenteHasta, retirado, creadoEn, modificadoEn, null, null);
        }
}
