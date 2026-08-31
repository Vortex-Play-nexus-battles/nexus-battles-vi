package com.nexusbattles.plataforma.comentarios.publicacion;

import com.nexusbattles.plataforma.comentarios.HiloDeComentarios;

/**
 * Puerta al filtro automatico de contenido que exige RN-CMT-001.
 *
 * <p>El dominio no sabe como se verifica el texto, solo recibe el veredicto.
 * La implementacion real llama por REST al servicio de moderacion y sanciones
 * usando el contrato publicado en contracts/openapi/moderacion-lista-negra.yaml,
 * respetando la regla de plataforma de integrarse siempre por interfaz y nunca
 * por consulta directa a un esquema ajeno.
 */
public interface FiltroDeContenido {

    HiloDeComentarios.ResultadoDelFiltro verificar(String texto);
}
