package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

/**
 * Donde se va a usar el texto que se verifica (moderacion-lista-negra.yaml
 * 2.0.x). Decide la accion que la politica aplica si coincide un termino:
 * ver {@link PoliticaDeModeracion}.
 */
public enum ContextoDeTexto {
    APODO,
    COMENTARIO,
    CHAT_GENERAL,
    CHAT_SALA,
    MENSAJE_PRIVADO,
    NOMBRE_EQUIPO,
    NOMBRE_TORNEO,
    NOMBRE_SALA,
    /** Cuando el llamador no dice el contexto. */
    GENERICO
}
