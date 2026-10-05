package nexus.dominio;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "banners")
public record Banner(
        @Id String id,
        String contenido,
        Instant publicarDesde,
        Instant vigenteHasta,
        boolean retirado,
        Instant creadoEn,
        Instant modificadoEn) {
}
