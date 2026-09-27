package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

/**
 * Lo que la politica de moderacion manda hacer con un texto
 * (moderacion-lista-negra.yaml 2.0.x).
 */
public enum AccionDeModeracion {
    /** No coincide ningun termino activo. */
    PERMITIR,
    /** El nombre no se acepta (apodo, equipo, torneo, sala). */
    RECHAZAR,
    /** Se publica en revision: un moderador decide (comentarios). */
    REVISION,
    /** No se entrega (chat y mensajes privados). */
    BLOQUEAR
}
