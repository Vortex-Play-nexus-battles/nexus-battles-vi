package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Contexto -> accion cuando un texto coincide con la lista negra, y el motivo
 * generico que se le da a quien lo escribio (moderacion-lista-negra.yaml
 * 2.0.x, «Contexto y accion»).
 *
 * <p>La tabla por omision es la del contrato; cada entrada se puede cambiar
 * por variable de entorno ({@code LISTA_NEGRA_ACCION_<CONTEXTO>}, ver
 * {@code application.yml}) sin tocar codigo, para que cada consumidor no
 * invente su propia politica. {@code PERMITIR} no se admite como accion de una
 * coincidencia: un texto que coincide y se permite es un texto que no se
 * verifico, y eso se decide apagando el termino, no el contexto.
 *
 * <p>El motivo nunca nombra el termino ni su categoria: lo lee la persona que
 * escribio el texto, y decirle que termino fue es ensenarle a esquivarlo.
 */
public final class PoliticaDeModeracion {

    /** La tabla del contrato. */
    public static final Map<ContextoDeTexto, AccionDeModeracion> POR_OMISION;

    static {
        Map<ContextoDeTexto, AccionDeModeracion> tabla = new EnumMap<>(ContextoDeTexto.class);
        tabla.put(ContextoDeTexto.APODO, AccionDeModeracion.RECHAZAR);
        tabla.put(ContextoDeTexto.NOMBRE_EQUIPO, AccionDeModeracion.RECHAZAR);
        tabla.put(ContextoDeTexto.NOMBRE_TORNEO, AccionDeModeracion.RECHAZAR);
        tabla.put(ContextoDeTexto.NOMBRE_SALA, AccionDeModeracion.RECHAZAR);
        tabla.put(ContextoDeTexto.COMENTARIO, AccionDeModeracion.REVISION);
        tabla.put(ContextoDeTexto.CHAT_GENERAL, AccionDeModeracion.BLOQUEAR);
        tabla.put(ContextoDeTexto.CHAT_SALA, AccionDeModeracion.BLOQUEAR);
        tabla.put(ContextoDeTexto.MENSAJE_PRIVADO, AccionDeModeracion.BLOQUEAR);
        tabla.put(ContextoDeTexto.GENERICO, AccionDeModeracion.RECHAZAR);
        POR_OMISION = Collections.unmodifiableMap(tabla);
    }

    private final Map<ContextoDeTexto, AccionDeModeracion> acciones;

    /**
     * @param cambios las entradas que se quieren distintas de la tabla del
     *                contrato; las que faltan conservan su valor por omision
     * @throws IllegalArgumentException si alguna es {@code PERMITIR}
     */
    public PoliticaDeModeracion(Map<ContextoDeTexto, AccionDeModeracion> cambios) {
        Map<ContextoDeTexto, AccionDeModeracion> tabla = new EnumMap<>(POR_OMISION);
        (cambios == null ? Map.<ContextoDeTexto, AccionDeModeracion>of() : cambios).forEach((contexto, accion) -> {
            Objects.requireNonNull(contexto);
            if (accion == null || accion == AccionDeModeracion.PERMITIR) {
                throw new IllegalArgumentException("La accion del contexto " + contexto
                        + " tiene que ser RECHAZAR, REVISION o BLOQUEAR (lista-negra.accion.*)");
            }
            tabla.put(contexto, accion);
        });
        this.acciones = Collections.unmodifiableMap(tabla);
    }

    /** La politica del contrato, sin cambios. */
    public static PoliticaDeModeracion porOmision() {
        return new PoliticaDeModeracion(Map.of());
    }

    /** La accion si el texto coincide; {@code null} se trata como {@code GENERICO}. */
    public AccionDeModeracion siCoincide(ContextoDeTexto contexto) {
        return acciones.get(contexto == null ? ContextoDeTexto.GENERICO : contexto);
    }

    /** Motivo para quien escribio el texto, segun donde lo iba a usar. Sin el termino. */
    public String motivo(ContextoDeTexto contexto) {
        return switch (contexto == null ? ContextoDeTexto.GENERICO : contexto) {
            case APODO -> "El apodo no está permitido: no puede contener palabras ofensivas ni nombres de "
                    + "políticos, celebridades, dirigentes o marcas registradas.";
            case NOMBRE_EQUIPO, NOMBRE_TORNEO, NOMBRE_SALA ->
                    "El nombre no está permitido: contiene un término de la lista de términos prohibidos.";
            case COMENTARIO -> "El comentario contiene un término no permitido y queda pendiente de revisión.";
            case CHAT_GENERAL, CHAT_SALA, MENSAJE_PRIVADO ->
                    "El mensaje contiene un término no permitido y no se envió.";
            case GENERICO -> "El texto contiene un término no permitido.";
        };
    }

    public Map<ContextoDeTexto, AccionDeModeracion> acciones() {
        return acciones;
    }
}
