package com.nexusbattles.plataforma.comentarios.moderacion;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.nexusbattles.plataforma.comentarios.Comentario;

/**
 * Lo que un moderador puede hacerle a un comentario, y desde donde — RF-COM-008,
 * 7.3.3 del documento del curso.
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
 * <h2>EDITAR, MARCAR y DESMARCAR (B3, 7.3.3)</h2>
 *
 * Las tres dejan el comentario en el estado en que estaba: EDITAR cambia su
 * texto («modificar contenido inapropiado manteniendo el contexto del
 * comentario (con registro de la edicion)») y MARCAR/DESMARCAR su marca de
 * «seguimiento especial». Ninguna vale sobre un ELIMINADO, que sigue siendo
 * terminal. MARCAR un comentario ya marcado (o DESMARCAR uno que no lo esta)
 * es una transicion invalida, igual que aprobar dos veces: casi siempre es
 * otro moderador que se adelanto, y se le dice con un 409 sin tocar nada.
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
    RESTAURAR(Comentario.Estado.PUBLICADO, EnumSet.of(Comentario.Estado.OCULTO)),

    /** Cambia el texto y deja registro del anterior. No cambia el estado. */
    EDITAR(null, EnumSet.of(Comentario.Estado.PUBLICADO, Comentario.Estado.EN_REVISION,
            Comentario.Estado.OCULTO)),

    /** Lo senala para seguimiento especial. No cambia el estado. */
    MARCAR(null, EnumSet.of(Comentario.Estado.PUBLICADO, Comentario.Estado.EN_REVISION,
            Comentario.Estado.OCULTO)),

    /** Le quita la marca de seguimiento. No cambia el estado. */
    DESMARCAR(null, EnumSet.of(Comentario.Estado.PUBLICADO, Comentario.Estado.EN_REVISION,
            Comentario.Estado.OCULTO));

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

    /** Nulo en las acciones que no cambian el estado. */
    private final Comentario.Estado destino;
    private final Set<Comentario.Estado> origenes;

    AccionDeModeracion(Comentario.Estado destino, Set<Comentario.Estado> origenes) {
        this.destino = destino;
        this.origenes = origenes;
    }

    /** El estado en el que queda un comentario que estaba en {@code actual}. */
    public Comentario.Estado destinoDesde(Comentario.Estado actual) {
        return destino == null ? actual : destino;
    }

    /** Si la accion tiene sentido desde ese estado (sin mirar la marca). */
    public boolean aplicableDesde(Comentario.Estado actual) {
        return origenes.contains(actual);
    }

    /**
     * Si la accion tiene sentido sobre ese comentario: su estado y, para
     * MARCAR y DESMARCAR, su marca.
     */
    public boolean aplicableA(Comentario comentario) {
        if (!aplicableDesde(comentario.estado())) {
            return false;
        }
        return switch (this) {
            case MARCAR -> !comentario.marcado();
            case DESMARCAR -> comentario.marcado();
            default -> true;
        };
    }

    /**
     * El comentario despues de la accion. Quien llama ya comprobo que es
     * {@link #aplicableA} y, para EDITAR, que trae texto nuevo.
     */
    public Comentario aplicarA(Comentario comentario, String textoNuevo) {
        return switch (this) {
            case EDITAR -> comentario.editadoCon(textoNuevo);
            case MARCAR -> comentario.conMarca(true);
            case DESMARCAR -> comentario.conMarca(false);
            default -> comentario.con(destino);
        };
    }

    /**
     * Si la accion se le avisa al autor. MARCAR y DESMARCAR no: son una nota
     * interna de moderacion («seguimiento especial»), no una decision sobre su
     * comentario, y avisarle seria senalarle que se le esta vigilando.
     */
    public boolean seAvisaAlAutor() {
        return this != MARCAR && this != DESMARCAR;
    }

    /** Que se puede hacer con un comentario que esta en ese estado (sin mirar la marca). */
    public static Set<AccionDeModeracion> desde(Comentario.Estado actual) {
        return Set.copyOf(DESDE.getOrDefault(actual, EnumSet.noneOf(AccionDeModeracion.class)));
    }
}
