package nexus.inventario.dominio;

/**
 * Otra escritura llego antes al mismo inventario — B4.
 *
 * <p>El guardado es condicional a la version leida ({@code @Version}): si
 * alguien escribio el documento entre la lectura y el guardado, no se aplica
 * nada. Hacia fuera es la misma falla que cualquier escritura que no se
 * completo (503 "Inventario no disponible", sin cambios parciales), por eso
 * hereda de {@link FalloPersistenciaInventarioException}; quien sabe
 * reintentar —la entrega— la distingue por su tipo, vuelve a leer y reintenta.
 */
public class ConflictoDeEscrituraException extends FalloPersistenciaInventarioException {

    public ConflictoDeEscrituraException(Throwable causa) {
        super(causa);
    }
}
