package com.nexusbattles.plataforma.comentarios.moderacion;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.nexusbattles.plataforma.comentarios.Comentario;

/**
 * Lo que un moderador puede hacerle a un comentario, y desde donde — RF-COM-008.
 *
 * <h2>Por que las transiciones viven en el enum</h2>
 *
 * Porque la pregunta "¿se puede ocultar un comentario ya eliminado?" tiene una
 * sola respuesta y no debe depender de en cual de los tres sitios que tocan
 * estados se este mirando. Aqui esta escrita una vez, la comprueba el dominio
 * antes de cambiar nada, y {@code V4} la respalda en la base con un CHECK
 * sobre los cuatro estados validos.
 *
 * <h2>OCULTO no es ELIMINADO</h2>
 *
 * La ficha lo dice con todas las letras: "La accion «Ocultar» debe preservar
 * el registro en base de datos, a diferencia de «Eliminar»". En el modelo eso
 * se traduce en que OCULTO es reversible —{@link #RESTAURAR} lo devuelve a
 * PUBLICADO— y ELIMINADO es terminal. Sin esa distincion, "ocultar" seria un
 * sinonimo caro de "eliminar" y el moderador no tendria ninguna accion de la
 * que se pueda volver.
 *
 * <p>El caso que CA-03 nombra —"comentario ya resuelto por otro moderador"—
 * cae solo de aqui: el segundo moderador pide una transicion que ya no es
 * valida desde el estado actual, y se le dice que no con un 409, sin tocar
 * nada.
 */
public enum AccionDeModeracion {

    /** Lo deja visible: el reporte no prospero. */
    APROBAR(Comentario.Estado.PUBLICADO, EnumSet.of(Comentario.Estado.EN_REVISION)),

    /** Lo retira de la vista conservando el registro. Reversible. */
    OCULTAR(Comentario.Estado.OCULTO,
            EnumSet.of(Comentario.Estado.EN_REVISION, Comentario.Estado.PUBLICADO)),

    /** Lo retira definitivamente. Terminal. */
    ELIMINAR(Comentario.Estado.ELIMINADO,
            EnumSet.of(Comentario.Estado.EN_REVISION, Comentario.Estado.PUBLICADO,
                    Comentario.Estado.OCULTO)),

    /** Deshace un OCULTAR. No deshace un ELIMINAR: eso es el punto de ELIMINAR. */
    RESTAURAR(Comentario.Estado.PUBLICADO, EnumSet.of(Comentario.Estado.OCULTO));

    private static final Map<Comentario.Estado, Set<AccionDeModeracion>> DESDE =
            new EnumMap<>(Comentario.Estado.class);

    static {
        for (Comentario.Estado estado : Comentario.Estado.values()) {
            Set<AccionDeModeracion> posibles = EnumSet.noneOf(AccionDeModeracion.class);
            for (AccionDeModeracion accion : values()) {
                if (accion.origenes.contains(estado)) {
                    posibles.add(accion);
                }
            }
            DESDE.put(estado, posibles);
        }
    }

    private final Comentario.Estado destino;
    private final Set<Comentario.Estado> origenes;

    AccionDeModeracion(Comentario.Estado destino, Set<Comentario.Estado> origenes) {
        this.destino = destino;
        this.origenes = origenes;
    }

    /** El estado en el que queda el comentario tras aplicarla. */
    public Comentario.Estado destino() {
        return destino;
    }

    /** Si la accion tiene sentido desde ese estado. */
    public boolean aplicableDesde(Comentario.Estado actual) {
        return origenes.contains(actual);
    }

    /** Que se puede hacer con un comentario que esta en ese estado. */
    public static Set<AccionDeModeracion> desde(Comentario.Estado actual) {
        return Set.copyOf(DESDE.getOrDefault(actual, EnumSet.noneOf(AccionDeModeracion.class)));
    }
}
