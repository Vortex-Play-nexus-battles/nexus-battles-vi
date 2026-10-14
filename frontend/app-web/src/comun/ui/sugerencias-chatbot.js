/**
 * Preguntas rápidas, temas frecuentes y autocompletado de la ventana del
 * asistente.
 *
 * Todo sale de `GET /chat/sugerencias`, que lee la versión en producción de la
 * base de conocimiento: cada sugerencia es una pregunta que el asistente sabe
 * responder. Al elegir una, la ventana la envía como si el jugador la hubiera
 * escrito; el servidor reconoce el título del tema tal cual.
 *
 * Si el servicio no responde, las preguntas rápidas simplemente no aparecen y
 * el autocompletado no sugiere nada: el chat sigue funcionando igual.
 *
 * 1.15.0 (§7.4.3 «sugerencias proactivas: recomendaciones basadas en el
 * comportamiento del usuario») — «Quizá te interese»: lo que el asistente le
 * propone a quien abre la ventana (`GET /chat/sugerencias/para-ti`), sacado
 * de lo que esa persona ya le preguntó, de la sección donde está o, si aún no
 * preguntó nada, de lo más consultado. Cada grupo dice por qué sale, para que
 * no parezca adivinación (D-CHA-14).
 *
 * @module comun/ui/sugerencias-chatbot
 */

import { h, vaciar } from './dom.js';
import { CATEGORIAS } from './soporte-chatbot.js';

export const TEXTOS_SUGERENCIAS = Object.freeze({
  titulo: 'Preguntas frecuentes',
  tema: 'Explorar por tema',
  todos: 'Las más consultadas',
  sugerencias: 'Sugerencias',
});

/** Cuántas preguntas rápidas, sugerencias de autocompletado y sugerencias para ti. */
export const CUANTAS = Object.freeze({ rapidas: 4, porTema: 6, autocompletar: 5, paraTi: 3 });

/** 1.15.0 — «Quizá te interese» y por qué sale cada grupo (el `motivo` del contrato). */
export const TEXTOS_PARA_TI = Object.freeze({
  titulo: 'Quizá te interese:',
  porLoQuePreguntaste: (tema) => `Porque preguntaste por «${tema}»`,
  porLoQueYaPreguntaste: 'Por lo que ya me preguntaste',
  porDondeEstas: 'Por la sección donde estás',
  paraEmpezar: 'Lo más consultado',
});

/** Letras mínimas para empezar a sugerir mientras se escribe (contrato). */
export const MINIMO_PARA_AUTOCOMPLETAR = 2;

let contador = 0;

/**
 * Botones de preguntas rápidas con un selector de temas, dentro de un
 * desplegable («Preguntas frecuentes») que empieza cerrado: así la
 * conversación conserva el espacio y las preguntas siguen a un clic. Es un
 * `<details>` nativo, operable por teclado sin código propio (RNF-ACC-002).
 *
 * @param {{cliente: {sugerencias: Function}, alElegir: (pregunta: string) => void}} opciones
 * @returns {{elemento: HTMLElement, cargar: () => Promise<void>, habilitar: (activo: boolean) => void}}
 */
export function crearPreguntasRapidas({ cliente, alElegir }) {
  contador += 1;
  const idTitulo = `chatbot-rapidas-titulo-${contador}`;
  let categoria = null;
  let activo = true;
  let consulta = 0;

  const selector = h('select', {
    clase: 'chatbot-rapidas__tema',
    atributos: { 'aria-label': TEXTOS_SUGERENCIAS.tema, name: 'tema-rapidas' },
    hijos: [
      h('option', { texto: TEXTOS_SUGERENCIAS.todos, atributos: { value: '' } }),
      ...CATEGORIAS.map((c) => h('option', { texto: c.texto, atributos: { value: c.valor } })),
    ],
  });
  selector.addEventListener('change', () => {
    categoria = selector.value || null;
    cargar();
  });

  const lista = h('div', {
    clase: 'chatbot-rapidas__lista',
    atributos: { role: 'group', 'aria-labelledby': idTitulo },
  });
  const titulo = h('summary', {
    clase: 'chatbot-rapidas__titulo',
    texto: TEXTOS_SUGERENCIAS.titulo,
  });
  titulo.id = idTitulo;

  // Cerrado por omisión: `<details>` sin el atributo `open`.
  const elemento = h('details', {
    clase: 'chatbot-rapidas',
    atributos: { hidden: true },
    datos: { chatbotRapidas: '' },
    hijos: [
      titulo,
      h('div', {
        clase: 'chatbot-rapidas__contenido',
        hijos: [h('div', { clase: 'chatbot-rapidas__cabecera', hijos: [selector] }), lista],
      }),
    ],
  });

  async function cargar() {
    consulta += 1;
    const esta = consulta;
    let sugerencias = [];
    try {
      sugerencias =
        (await cliente.sugerencias({
          categoria,
          limite: categoria ? CUANTAS.porTema : CUANTAS.rapidas,
        })) ?? [];
    } catch {
      sugerencias = [];
    }
    pintar(esta, sugerencias);
  }

  // Fuera de `cargar`: lo que se toca después de un `await` vive aparte
  // (regla require-atomic-updates de ESLint). Una respuesta vieja se ignora.
  function pintar(esta, sugerencias) {
    if (esta !== consulta) {
      return;
    }
    vaciar(lista);
    for (const sugerencia of sugerencias) {
      const boton = h('button', {
        clase: 'chatbot-rapidas__opcion',
        texto: sugerencia.titulo,
        atributos: { type: 'button', disabled: !activo },
        datos: { accion: 'pregunta-rapida', clave: sugerencia.clave ?? '' },
      });
      boton.addEventListener('click', () => {
        // Se pliega al elegir: la respuesta llega a una conversación con espacio.
        elemento.open = false;
        alElegir(sugerencia.pregunta ?? sugerencia.titulo);
      });
      lista.append(boton);
    }
    // Con un tema elegido el bloque se queda aunque no haya nada, para poder
    // volver a «Las más consultadas».
    elemento.hidden = sugerencias.length === 0 && categoria === null;
  }

  function habilitar(si) {
    activo = si;
    for (const boton of lista.querySelectorAll('button')) {
      boton.disabled = !si;
    }
  }

  return { elemento, cargar, habilitar };
}

/**
 * Autocompletado sobre la caja de texto (patrón combobox de ARIA).
 *
 * Debe conectarse ANTES que el manejador de Enter de la ventana: cuando hay
 * una sugerencia marcada, Enter la elige y no envía lo escrito.
 *
 * @param {{entrada: HTMLTextAreaElement, cliente: {sugerencias: Function},
 *          alElegir: (pregunta: string) => void, esperaMs?: number}} opciones
 * @returns {{elemento: HTMLElement, cerrar: () => void}}
 */
export function conectarAutocompletado({ entrada, cliente, alElegir, esperaMs = 250 }) {
  contador += 1;
  const idLista = `chatbot-autocompletar-${contador}`;
  let temporizador = null;
  let consulta = 0;
  let opciones = [];
  let marcada = -1;

  const lista = h('ul', {
    clase: 'chatbot-autocompletar',
    atributos: { role: 'listbox', hidden: true, 'aria-label': TEXTOS_SUGERENCIAS.sugerencias },
  });
  lista.id = idLista;

  entrada.setAttribute('role', 'combobox');
  entrada.setAttribute('aria-autocomplete', 'list');
  entrada.setAttribute('aria-controls', idLista);
  entrada.setAttribute('aria-expanded', 'false');

  function cerrar() {
    consulta += 1;
    clearTimeout(temporizador);
    opciones = [];
    marcada = -1;
    vaciar(lista);
    lista.hidden = true;
    entrada.setAttribute('aria-expanded', 'false');
    entrada.removeAttribute('aria-activedescendant');
  }

  function elegir(indice) {
    const sugerencia = opciones[indice];
    cerrar();
    if (sugerencia) {
      alElegir(sugerencia.pregunta ?? sugerencia.titulo);
    }
  }

  async function buscar(texto) {
    consulta += 1;
    const esta = consulta;
    let encontradas = [];
    try {
      encontradas = (await cliente.sugerencias({ q: texto, limite: CUANTAS.autocompletar })) ?? [];
    } catch {
      encontradas = [];
    }
    mostrar(esta, encontradas);
  }

  function mostrar(esta, encontradas) {
    if (esta !== consulta) {
      return;
    }
    opciones = encontradas;
    marcada = -1;
    vaciar(lista);
    entrada.removeAttribute('aria-activedescendant');
    encontradas.forEach((sugerencia, indice) => {
      const opcion = h('li', {
        clase: 'chatbot-autocompletar__opcion',
        texto: sugerencia.titulo,
        atributos: { role: 'option', 'aria-selected': 'false' },
        datos: { indice },
      });
      opcion.id = `${idLista}-opcion-${indice}`;
      // `mousedown` y no `click`: el clic haría perder el foco a la caja antes.
      opcion.addEventListener('mousedown', (evento) => {
        evento.preventDefault();
        elegir(indice);
      });
      lista.append(opcion);
    });
    const hay = encontradas.length > 0;
    lista.hidden = !hay;
    entrada.setAttribute('aria-expanded', String(hay));
  }

  function marcar(indice) {
    const items = [...lista.children];
    if (items.length === 0) {
      return;
    }
    marcada = (indice + items.length) % items.length;
    items.forEach((item, i) => item.setAttribute('aria-selected', String(i === marcada)));
    entrada.setAttribute('aria-activedescendant', items[marcada].id);
  }

  entrada.addEventListener('input', () => {
    clearTimeout(temporizador);
    const texto = entrada.value.trim();
    if (texto.length < MINIMO_PARA_AUTOCOMPLETAR) {
      cerrar();
      return;
    }
    temporizador = setTimeout(() => buscar(texto), esperaMs);
  });

  entrada.addEventListener('keydown', (evento) => {
    if (lista.hidden) {
      return;
    }
    if (evento.key === 'ArrowDown' || evento.key === 'ArrowUp') {
      evento.preventDefault();
      marcar(marcada + (evento.key === 'ArrowDown' ? 1 : -1));
    } else if (evento.key === 'Enter' && marcada >= 0 && !evento.shiftKey) {
      // Elige la sugerencia y no deja que la ventana envíe lo escrito.
      evento.preventDefault();
      evento.stopImmediatePropagation();
      elegir(marcada);
    } else if (evento.key === 'Escape') {
      // Cierra la lista, no la ventana.
      evento.stopPropagation();
      cerrar();
    }
  });

  entrada.addEventListener('blur', () => cerrar());

  return { elemento: lista, cerrar };
}

/**
 * 1.15.0 — por qué sale una sugerencia para ti, en palabras del jugador.
 *
 * @param {{motivo?: string, relacionadoCon?: string|null}} sugerencia
 * @returns {string}
 */
export function motivoParaTi({ motivo, relacionadoCon } = {}) {
  if (motivo === 'POR_LO_QUE_PREGUNTASTE') {
    return relacionadoCon
      ? TEXTOS_PARA_TI.porLoQuePreguntaste(relacionadoCon)
      : TEXTOS_PARA_TI.porLoQueYaPreguntaste;
  }
  if (motivo === 'POR_DONDE_ESTAS') {
    return TEXTOS_PARA_TI.porDondeEstas;
  }
  return TEXTOS_PARA_TI.paraEmpezar;
}

/**
 * 1.15.0 — las sugerencias agrupadas por su motivo (las de lo que preguntó,
 * por el tema que las trajo), en el orden en que llegan.
 *
 * @param {Array<{motivo?: string, relacionadoCon?: string|null}>|null|undefined} sugerencias
 * @returns {Array<{titulo: string, sugerencias: Array<object>}>}
 */
export function gruposParaTi(sugerencias) {
  const grupos = new Map();
  for (const sugerencia of sugerencias ?? []) {
    const titulo = motivoParaTi(sugerencia);
    if (!grupos.has(titulo)) {
      grupos.set(titulo, []);
    }
    grupos.get(titulo).push(sugerencia);
  }
  return [...grupos].map(([titulo, deEste]) => ({ titulo, sugerencias: deEste }));
}

/**
 * 1.15.0 — lo que dice la burbuja «Quizá te interese»: el título y, por cada
 * motivo, su explicación y sus preguntas, botones que la envían como si el
 * jugador la hubiera escrito. `null` si no hay ninguna que mostrar.
 *
 * @param {Array<{clave?: string, titulo?: string, pregunta?: string, motivo?: string,
 *          relacionadoCon?: string|null}>|null|undefined} sugerencias
 * @param {{alElegir: (pregunta: string) => void}} opciones
 * @returns {HTMLElement|null}
 */
export function contenidoParaTi(sugerencias, { alElegir }) {
  const validas = (sugerencias ?? []).filter(
    (sugerencia) => sugerencia?.pregunta || sugerencia?.titulo,
  );
  if (validas.length === 0) {
    return null;
  }
  const grupos = gruposParaTi(validas).map((grupo) => {
    contador += 1;
    const motivo = h('p', { clase: 'chatbot-para-ti__motivo', texto: grupo.titulo });
    motivo.id = `chatbot-para-ti-${contador}`;
    const botones = grupo.sugerencias.map((sugerencia) => {
      const pregunta = sugerencia.pregunta ?? sugerencia.titulo;
      const boton = h('button', {
        clase: 'chatbot-rapidas__opcion',
        texto: sugerencia.titulo ?? pregunta,
        atributos: { type: 'button' },
        datos: { accion: 'sugerencia-para-ti', clave: sugerencia.clave ?? '' },
      });
      boton.addEventListener('click', () => alElegir(pregunta));
      return boton;
    });
    return h('div', {
      clase: 'chatbot-para-ti__grupo',
      atributos: { role: 'group', 'aria-labelledby': motivo.id },
      hijos: [motivo, h('div', { clase: 'chatbot-rapidas__lista', hijos: botones })],
    });
  });
  return h('div', {
    clase: 'chatbot-para-ti',
    hijos: [h('p', { clase: 'chatbot-ventana__texto', texto: TEXTOS_PARA_TI.titulo }), ...grupos],
  });
}
