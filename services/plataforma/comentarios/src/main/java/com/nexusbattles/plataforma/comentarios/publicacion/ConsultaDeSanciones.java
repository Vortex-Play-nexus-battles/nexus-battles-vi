package com.nexusbattles.plataforma.comentarios.publicacion;

import com.nexusbattles.plataforma.comentarios.HiloDeComentarios;

/**
 * Puerta al estado disciplinario del autor, que resuelve otro modulo.
 *
 * <p>El dominio ya distingue entre un autor habilitado y uno silenciado por
 * sancion, pero quien conoce las sanciones no es este servicio. Esta interfaz
 * deja el punto de integracion listo para cuando el modulo de sanciones
 * publique su contrato.
 */
public interface ConsultaDeSanciones {

    HiloDeComentarios.EstadoDeAutor estadoDe(String autorId);
}
