package com.nexusbattles.plataforma.comentarios.publicacion;

/**
 * El modulo de sanciones no respondio y no se puede saber si el autor esta
 * habilitado. Se responde 503 y el autor reintenta: publicar «por si acaso»
 * daria voz a quien un moderador silencio, y retenerlo en revision seria
 * decidir por el moderador algo que no es del filtro de contenido.
 */
public class SancionesNoDisponibles extends RuntimeException {

    public SancionesNoDisponibles(String motivo) {
        super("el servicio de sanciones no respondio y el comentario no se publico; intenta de nuevo"
                + " en un momento (" + motivo + ")");
    }
}
