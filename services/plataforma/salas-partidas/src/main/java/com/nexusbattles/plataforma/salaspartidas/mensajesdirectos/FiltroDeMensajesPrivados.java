package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos;

/**
 * La lista negra de HU-ADM-002 aplicada a un mensaje privado — contexto
 * {@code MENSAJE_PRIVADO} de {@code contracts/openapi/moderacion-lista-negra.yaml}
 * 2.0.x, cuya politica por omision es BLOQUEAR (el mensaje no se entrega).
 *
 * <p>Tres respuestas y no dos, como el filtro del chat: cuando moderacion no
 * contesta, el caso de uso no puede decidir por su cuenta que el texto estaba
 * limpio.
 */
public interface FiltroDeMensajesPrivados {

    enum Veredicto { ENTREGABLE, BLOQUEADO, SIN_VERIFICAR }

    Veredicto verificar(String texto);
}
