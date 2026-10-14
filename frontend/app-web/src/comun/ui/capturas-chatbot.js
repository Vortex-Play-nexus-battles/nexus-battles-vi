/**
 * Las capturas de pantalla de una solicitud de soporte (ms-chatbot.yaml
 * 1.14.0, §7.4.6 «Opción de adjuntar capturas de pantalla para soporte
 * técnico»): el selector para adjuntarlas, en la ventana del asistente, y la
 * lista para verlas, ahí (las propias) y en el panel de soporte (las de
 * cualquier solicitud).
 *
 * Una captura no es pública: su contenido se pide con el token o la sesión de
 * quien puede verla (`leer`) y se pinta con un enlace de objeto
 * (`URL.createObjectURL`) que se libera al repintar (`crearRegistroDeImagenes`).
 * No se descarga sola: solo al pulsar «Ver captura», porque cada una puede
 * pesar 2 MB.
 *
 * Lo que decide el servicio, no esta vista: qué es una imagen (la firma de
 * sus bytes), si cabe y si la solicitud la admite. Aquí solo se avisa antes
 * de subir lo que seguro no pasa (otro tipo, más de 2 MB), para no hacer
 * esperar a nadie por un rechazo cantado.
 *
 * @module comun/ui/capturas-chatbot
 */

import { conCarga } from './boton.js';
import { h } from './dom.js';
import { numero } from './formato.js';
import { icono } from './icono.js';

/** D-CHA-12: capturas por solicitud. */
export const MAXIMO_DE_CAPTURAS = 3;

/** D-CHA-12: lo que pesa como mucho una captura. */
export const TAMANO_MAXIMO_DE_CAPTURA = 2 * 1024 * 1024;

/** Lo que admite el selector de archivos (el servicio lo vuelve a mirar por los bytes). */
export const FORMATOS_DE_CAPTURA = 'image/png,image/jpeg';

const TIPOS = new Set(FORMATOS_DE_CAPTURA.split(','));

const NOMBRE_DEL_FORMATO = Object.freeze({ 'image/png': 'PNG', 'image/jpeg': 'JPEG' });

export const TEXTOS_CAPTURAS = Object.freeze({
  lista: (asunto) => `Capturas de «${asunto}»`,
  ver: (n) => `Ver captura ${n}`,
  ocultar: (n) => `Ocultar captura ${n}`,
  cargando: 'Cargando…',
  alternativa: (n, asunto) => `Captura ${n} de la solicitud «${asunto}»`,
  fallo: 'No se pudo cargar la captura. Vuelve a intentarlo.',
});

/**
 * Lo que pesa, como lo lee una persona: «512 B», «340 KB», «1,4 MB».
 *
 * @param {number} bytes
 * @returns {string}
 */
export function pesoLegible(bytes) {
  if (bytes < 1024) {
    return `${numero(bytes)} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${numero(Math.round(bytes / 1024))} KB`;
  }
  return `${numero(bytes / (1024 * 1024), 1)} MB`;
}

/**
 * «PNG · 1280 × 720 · 340 KB».
 *
 * @param {{tipo?: string, ancho?: number, alto?: number, tamano?: number}} captura
 * @returns {string}
 */
export function descripcionDeCaptura({ tipo, ancho, alto, tamano }) {
  const partes = [NOMBRE_DEL_FORMATO[tipo] ?? 'Imagen'];
  if (ancho > 0 && alto > 0) {
    // Las medidas, como se escriben las de una pantalla: «1280 × 720».
    partes.push(`${ancho} × ${alto}`);
  }
  if (tamano > 0) {
    partes.push(pesoLegible(tamano));
  }
  return partes.join(' · ');
}

/**
 * Lo que seguro no pasa, dicho antes de subirlo: un archivo vacío, de otro
 * tipo o de más de 2 MB. Un archivo sin tipo (el sistema no lo reconoce) se
 * deja subir: lo decide el servicio por sus bytes.
 *
 * @param {{size: number, type?: string}|null} archivo
 * @returns {'vacia'|'tipo'|'tamano'|null}
 */
export function rechazoDeCaptura(archivo) {
  if (!archivo || !(archivo.size > 0)) {
    return 'vacia';
  }
  if (archivo.type && !TIPOS.has(archivo.type)) {
    return 'tipo';
  }
  if (archivo.size > TAMANO_MAXIMO_DE_CAPTURA) {
    return 'tamano';
  }
  return null;
}

/**
 * Los enlaces de objeto de las capturas pintadas, para liberarlos de una vez
 * al repintar o al cerrar.
 *
 * @param {{crearUrl?: (contenido: Blob) => string, liberarUrl?: (url: string) => void}} [opciones]
 * @returns {{crear: (contenido: Blob) => string, liberar: () => void}}
 */
export function crearRegistroDeImagenes({
  crearUrl = (contenido) => globalThis.URL?.createObjectURL?.(contenido) ?? '',
  liberarUrl = (url) => globalThis.URL?.revokeObjectURL?.(url),
} = {}) {
  const urls = new Set();
  return {
    crear(contenido) {
      const url = crearUrl(contenido);
      if (url) {
        urls.add(url);
      }
      return url;
    },
    liberar() {
      for (const url of urls) {
        liberarUrl(url);
      }
      urls.clear();
    },
  };
}

let contador = 0;

/** Lo que da `accion`, o null si falla (también si falla antes de prometer nada). */
async function sinFallar(accion) {
  try {
    return await accion();
  } catch {
    return null;
  }
}

/**
 * Las capturas de una solicitud: cada una con «Ver captura N», que la pide y
 * la enseña debajo (y la vuelve a ocultar), y lo que es (formato, medidas y
 * peso).
 *
 * @param {{capturas: Array<{capturaId: string, tipo?: string, ancho?: number,
 *          alto?: number, tamano?: number}>, asunto: string,
 *          leer: (capturaId: string) => Promise<Blob>,
 *          registro: {crear: (contenido: Blob) => string}}} opciones
 * @returns {HTMLUListElement}
 */
export function listaDeCapturas({ capturas, asunto, leer, registro }) {
  return h('ul', {
    clase: 'capturas-chatbot',
    datos: { zona: 'lista-capturas' },
    atributos: { 'aria-label': TEXTOS_CAPTURAS.lista(asunto) },
    hijos: capturas.map((captura, indice) =>
      elementoDeCaptura(captura, indice + 1, { asunto, leer, registro }),
    ),
  });
}

function elementoDeCaptura(captura, cual, { asunto, leer, registro }) {
  contador += 1;
  const idImagen = `captura-chatbot-${contador}`;
  const marco = h('div', {
    clase: 'capturas-chatbot__imagen',
    atributos: { id: idImagen, hidden: true },
  });
  const fallo = h('p', {
    clase: 'capturas-chatbot__fallo',
    texto: TEXTOS_CAPTURAS.fallo,
    atributos: { role: 'alert', hidden: true },
  });
  const ver = h('button', {
    clase: 'boton boton--secundario boton--pequeno',
    texto: TEXTOS_CAPTURAS.ver(cual),
    datos: { accion: 'ver-captura', captura: captura.capturaId },
    atributos: { type: 'button', 'aria-expanded': 'false', 'aria-controls': idImagen },
  });

  function mostrar(visible) {
    marco.hidden = !visible;
    ver.setAttribute('aria-expanded', String(visible));
    ver.textContent = visible ? TEXTOS_CAPTURAS.ocultar(cual) : TEXTOS_CAPTURAS.ver(cual);
  }

  function pintar(contenido) {
    marco.replaceChildren(
      h('img', {
        atributos: {
          src: registro.crear(contenido),
          alt: TEXTOS_CAPTURAS.alternativa(cual, asunto),
          decoding: 'async',
        },
      }),
    );
    mostrar(true);
  }

  async function cargar() {
    fallo.hidden = true;
    conCarga(ver, true, TEXTOS_CAPTURAS.cargando);
    const contenido = await sinFallar(() => leer(captura.capturaId));
    conCarga(ver, false);
    if (contenido) {
      pintar(contenido);
    } else {
      fallo.hidden = false;
    }
  }

  ver.addEventListener('click', () => {
    if (ver.getAttribute('aria-expanded') === 'true') {
      mostrar(false);
    } else if (marco.hasChildNodes()) {
      mostrar(true);
    } else {
      cargar();
    }
  });

  return h('li', {
    clase: 'capturas-chatbot__captura',
    datos: { captura: captura.capturaId },
    hijos: [
      h('div', {
        clase: 'capturas-chatbot__fila',
        hijos: [
          ver,
          h('span', { clase: 'capturas-chatbot__datos', texto: descripcionDeCaptura(captura) }),
        ],
      }),
      fallo,
      marco,
    ],
  });
}

/**
 * El botón para elegir una captura: una entrada de archivo escondida a la
 * vista (no al teclado) seguida de su etiqueta con aspecto de botón, que es
 * donde se pinta el foco, como en el redactor de comentarios. La pista va
 * debajo y la entrada la anuncia (`aria-describedby`): es el aviso de lo que
 * no debe verse en la captura, ANTES de elegirla.
 *
 * @param {{texto: string, pista: string, alElegir: (archivo: File) => void}} opciones
 * @returns {{elemento: HTMLElement, entrada: HTMLInputElement,
 *            ocupado: (activo: boolean, textoDeEspera?: string) => void}}
 */
export function selectorDeCaptura({ texto, pista, alElegir }) {
  contador += 1;
  const id = `captura-chatbot-archivo-${contador}`;
  const idPista = `${id}-pista`;
  const entrada = h('input', {
    clase: 'capturas-chatbot__archivo solo-lectores',
    datos: { accion: 'elegir-captura' },
    atributos: { id, type: 'file', accept: FORMATOS_DE_CAPTURA, 'aria-describedby': idPista },
  });
  const rotulo = h('span', { texto });
  const etiqueta = h('label', {
    clase: 'boton boton--secundario boton--pequeno capturas-chatbot__adjuntar',
    atributos: { for: id },
    hijos: [icono('imagen', { clase: 'icono', etiqueta: null }), rotulo],
  });
  entrada.addEventListener('change', () => {
    const archivo = entrada.files?.[0] ?? null;
    // Vacía la entrada: así se puede volver a elegir el mismo archivo.
    entrada.value = '';
    if (archivo) {
      alElegir(archivo);
    }
  });
  const elemento = h('div', {
    clase: 'capturas-chatbot__selector',
    hijos: [
      entrada,
      etiqueta,
      h('p', { clase: 'campo__pista', texto: pista, atributos: { id: idPista } }),
    ],
  });
  return {
    elemento,
    entrada,
    ocupado(activo, textoDeEspera = texto) {
      entrada.disabled = activo;
      etiqueta.setAttribute('aria-busy', String(activo));
      rotulo.textContent = activo ? textoDeEspera : texto;
    },
  };
}
