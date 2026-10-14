package nexus.auditoria;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Una operación de un administrador sobre el catálogo, tal como ocurrió —
 * SA-18, §7.2.5 y §7.3.5.
 *
 * <p>Lleva lo que pide §7.3.5 de cada acción administrativa: el momento exacto
 * ({@code ocurridaEn}), quién la hizo ({@code autor}, el {@code uid} de su
 * cuenta), el tipo, lo afectado ({@code objeto} + {@code objetoId}, con su
 * nombre en ese momento), los valores anteriores y nuevos, el motivo y la
 * dirección IP. {@code descripcion} dice en palabras qué se hizo, para cuando
 * quien actuó no escribió motivo (un alta o una edición no lo piden).
 *
 * <p>Solo se añade: ningún código la modifica ni la borra. La copia inmutable
 * —con disparadores que impiden cambiarla— es la de la bitácora central de
 * ms-cumplimiento, que la importa por {@code GET
 * /api/v1/productos/auditoria/operaciones}.
 */
@Document(collection = "operaciones_administrativas")
public record OperacionAdministrativa(
        @Id String id,
        Instant ocurridaEn,
        String autor,
        TipoOperacion tipo,
        ObjetoAuditado objeto,
        String objetoId,
        String nombre,
        String valorAnterior,
        String valorNuevo,
        String motivo,
        String descripcion,
        String ipOrigen) {
}
