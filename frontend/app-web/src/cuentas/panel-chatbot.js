/**
 * Panel de administración del asistente — HU-CHA-012.
 *
 * Cinco pestañas:
 *
 * | Pestaña                 | Requisito  | Módulo                               |
 * |-------------------------|------------|--------------------------------------|
 * | Analíticas              | RF-CHA-012 | `panel-chatbot-analiticas.js`        |
 * | Preguntas sin respuesta | 7.4.5      | `panel-chatbot-brechas.js`           |
 * | Base de conocimiento    | RF-CHA-013 | `panel-chatbot-base.js`              |
 * | Reentrenamiento         | RF-CHA-014 | `panel-chatbot-reentrenamiento.js`   |
 * | Soporte                 | RF-ADM-004 | `panel-chatbot-soporte.js`           |
 *
 * Cada pestaña carga sus datos al abrirse, no al entrar a la vista: quien
 * solo mira analíticas no dispara las consultas de las otras.
 *
 * @module cuentas/panel-chatbot
 */

import { montarPestanas } from '../comun/ui/pestanas.js';
import { h } from '../comun/ui/dom.js';
import { encabezadoDePagina } from '../comun/ui/pagina.js';
import { crearClientePanelChatbot, descargar } from './cliente-panel-chatbot.js';
import { montarAnaliticas } from './panel-chatbot-analiticas.js';
import { montarBaseConocimiento } from './panel-chatbot-base.js';
import { montarBrechas } from './panel-chatbot-brechas.js';
import { montarReentrenamiento } from './panel-chatbot-reentrenamiento.js';
import { montarSoporte } from './panel-chatbot-soporte.js';

/**
 * @param {HTMLElement} raiz
 * @param {{cliente?: object, descargarArchivo?: Function, hash?: boolean}} [opciones]
 * @returns {{mostrar: (id: string) => void}}
 */
export function montarPanelChatbot(
  raiz,
  { cliente = crearClientePanelChatbot(), descargarArchivo = descargar, hash = true } = {},
) {
  raiz.append(
    encabezadoDePagina({
      titulo: 'Asistente',
      descripcion:
        'Cómo está respondiendo el asistente, qué sabe, qué versión de su base de conocimiento atiende a los jugadores y qué le piden a soporte.',
    }),
  );

  // Las pestañas se montan antes de existir el control que las cambia: `irA`
  // lo busca cuando se usa («Preguntas sin respuesta» lleva a la base).
  let control = null;
  const dependencias = { cliente, descargarArchivo, irA: (id) => control?.mostrar(id) };
  const secciones = [
    { id: 'analiticas', etiqueta: 'Analíticas', montar: montarAnaliticas },
    { id: 'brechas', etiqueta: 'Preguntas sin respuesta', montar: montarBrechas },
    { id: 'base', etiqueta: 'Base de conocimiento', montar: montarBaseConocimiento },
    { id: 'reentrenamiento', etiqueta: 'Reentrenamiento', montar: montarReentrenamiento },
    { id: 'soporte', etiqueta: 'Soporte', montar: montarSoporte },
  ];

  const montadas = new Map();
  const pestanas = secciones.map((seccion) => {
    const panel = h('section', { clase: 'pila pila--amplia panel-chatbot__panel' });
    montadas.set(seccion.id, seccion.montar(panel, dependencias));
    return { id: seccion.id, etiqueta: seccion.etiqueta, panel };
  });

  // Se recarga cada vez que se abre una pestaña, no solo la primera: lo que
  // se hace en una cambia lo que muestra la otra (crear la candidata en «Base
  // de conocimiento» habilita el despliegue en «Reentrenamiento», y
  // desplegar cambia las versiones).
  function alCambiar(id) {
    montadas.get(id)?.cargar();
  }

  // montarPestanas ya muestra (y por tanto carga) la pestaña inicial.
  control = montarPestanas(raiz, pestanas, { alCambiar, hash });
  return control;
}
