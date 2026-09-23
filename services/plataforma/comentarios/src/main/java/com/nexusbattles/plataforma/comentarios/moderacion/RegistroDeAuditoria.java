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
 * <h2>Fail-open, y dicho</h2>
 *
 * ms-cumplimiento no esta desplegado en el host de dev por capacidad (#459):
 * hoy solo existe en el banco E2E. Un registro que no sale no puede tumbar una
 * decision de moderacion ya confirmada —seria degradar la moderacion por una
 * dependencia que ni siquiera esta arriba—, asi que esto no lanza. Lo que si
 * hace es dejarlo en la bitacora local en JSON, que es lo que se puede
 * garantizar mientras el servicio no exista en AWS.
 */
public interface RegistroDeAuditoria {

    void registrar(AsientoDeModeracion asiento);
}
