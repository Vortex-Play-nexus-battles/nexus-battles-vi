package com.nexusbattles.plataforma.comentarios.moderacion;

/**
 * Deja constancia de una accion de moderacion fuera del servicio — RF-AUD-001.
 *
 * <h2>Dos registros, no uno duplicado</h2>
 *
 * El {@link AsientoDeModeracion} vive en la base de comentarios y es el
 * historico del dominio: lo que el moderador ve al abrir un comentario. Esto
 * otro es la bitacora transversal de la empresa, en ms-cumplimiento, donde
 * caben las acciones de los veinte modulos y donde mira un auditor que no
 * sabe —ni tiene por que— que existe un servicio de comentarios.
 *
 * <p>No es duplicacion: son dos preguntas distintas. "¿Que le paso a este
 * comentario?" la contesta el asiento; "¿que ha hecho este moderador este
 * mes?" solo la contesta la bitacora transversal.
 *
 * <h2>Desde SA-5, por la cola del propio asiento</h2>
 *
 * Este punto ya no llama a nadie: deja la linea de la decision en la bitacora
 * JSON (regla 6). La entrega a ms-cumplimiento la hace {@link EnvioDeAuditoria}
 * desde el asiento, que se guarda en la transaccion de la decision, con
 * reintentos y una referencia por asiento: una bitacora central caida no
 * tumba una decision ni la pierde.
 */
public interface RegistroDeAuditoria {

    void registrar(AsientoDeModeracion asiento);
}
