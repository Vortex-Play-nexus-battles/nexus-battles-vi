package com.nexusbattles.plataforma.moderacionsanciones.auditoria;

/**
 * Deja constancia de una accion administrativa en la bitacora central de
 * ms-cumplimiento — §7.3.5 (SA-5).
 *
 * <p>Quien actua y desde que direccion no se pasan: los pone la
 * implementacion a partir de la peticion en curso (el token y la cabecera que
 * fija el borde). Asi un servicio no puede firmar una accion en nombre de otro
 * ni inventarse la direccion.
 */
@FunctionalInterface
public interface BitacoraDeAuditoria {

    /** Para las pruebas y para el cableado sin bitacora: no registra nada. */
    BitacoraDeAuditoria NINGUNA = (tipo, afectado, valorAnterior, valorNuevo, motivo) -> { };

    /**
     * @param tipo          el tipo de accion
     * @param afectado      sobre quien o sobre que: el {@code uid} de la cuenta, o
     *                      {@code lista-negra:<termino>}
     * @param valorAnterior como estaba (nulo si no aplica)
     * @param valorNuevo    como quedo (nulo si no aplica)
     * @param motivo        la justificacion de quien actua
     */
    void registrar(TipoDeAccion tipo, String afectado, String valorAnterior, String valorNuevo, String motivo);
}
