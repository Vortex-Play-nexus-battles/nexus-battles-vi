/**
 * Panel de administración del asistente — HU-CHA-012.
 *
 * Tres pestañas, una por requisito:
 *
 * | Pestaña             | Requisito  | Módulo                               |
 * |---------------------|------------|--------------------------------------|
 * | Analíticas          | RF-CHA-012 | `panel-chatbot-analiticas.js`        |
 * | Base de conocimiento| RF-CHA-013 | `panel-chatbot-base.js`              |
 * | Reentrenamiento     | RF-CHA-014 | `panel-chatbot-reentrenamiento.js`   |
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
import { montarReentrenamiento } from './panel-chatbot-reentrenamiento.js';

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
        'Cómo está respondiendo el asistente, qué sabe y qué versión de su base de conocimiento atiende a los jugadores.',
    }),
  );

  const dependencias = { cliente, descargarArchivo };
  const secciones = [
    { id: 'analiticas', etiqueta: 'Analíticas', montar: montarAnaliticas },
    { id: 'base', etiqueta: 'Base de conocimiento', montar: montarBaseConocimiento },
    { id: 'reentrenamiento', etiqueta: 'Reentrenamiento', montar: montarReentrenamiento },
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
  return montarPestanas(raiz, pestanas, { alCambiar, hash });
}
