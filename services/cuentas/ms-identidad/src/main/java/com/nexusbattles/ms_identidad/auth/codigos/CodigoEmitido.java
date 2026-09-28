package com.nexusbattles.ms_identidad.auth.codigos;

/**
 * Lo que queda de un codigo emitido para quien lo pidio: su fila y su
 * vigencia. El valor en claro NO va aqui (solo en {@link CodigoParaEnviar},
 * camino del correo).
 */
public record CodigoEmitido(Long id, TipoCodigo tipo, int minutosVigencia) {
}
