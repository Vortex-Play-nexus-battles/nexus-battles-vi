package com.nexusbattles.plataforma.salaspartidas.chat;

/**
 * Filtro de contenido inapropiado respaldado por la lista negra de HU-ADM-002.
 *
 * <p>Tiene tres respuestas y no dos a proposito: cuando el filtro no contesta,
 * el caso de uso no puede decidir por su cuenta que el texto estaba limpio.
 *
 * <p>Recibe el canal porque moderacion aplica su politica segun donde va el
 * texto: el {@code contexto} CHAT_SALA o CHAT_GENERAL de
 * {@code moderacion-lista-negra.yaml}.
 */
public interface FiltroDeContenido {

    enum Veredicto { LIMPIO, SENALADO, SIN_VERIFICAR }

    Veredicto verificar(String texto, Canal canal);
}
