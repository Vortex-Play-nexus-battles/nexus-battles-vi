package nexus.alertas;

import java.time.Instant;
import java.util.Objects;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "alertas_catalogo")
public record AlertaCatalogo(
        @Id String id,
        String productoId,
        String productoNombre,
        TipoCambioCatalogo tipo,
        String descripcion,
        Instant implementadaEn) {

    public AlertaCatalogo {
        id = exigirTexto(id, "id");
        productoId = exigirTexto(productoId, "productoId");
        productoNombre = exigirTexto(productoNombre, "productoNombre");
        tipo = Objects.requireNonNull(tipo, "El tipo es obligatorio");
        descripcion = exigirTexto(descripcion, "descripcion");
        implementadaEn = Objects.requireNonNull(
                implementadaEn,
                "La fecha de implementacion es obligatoria");
    }

    private static String exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " es obligatorio");
        }
        return valor.trim();
    }
}
