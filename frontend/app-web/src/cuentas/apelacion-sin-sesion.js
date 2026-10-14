/**
 * Apelar sin sesión — SA-4 (ms-identidad-auth 2.4.0, §7.3.2).
 *
 * §7.3.2 da a todo sancionado el derecho a apelar, y una cuenta suspendida o
 * baneada no puede entrar. Cuando escribe bien su contraseña, el login
 * responde el 403 `cuenta-suspendida` o `cuenta-baneada` con un código de un
 * solo uso (`apelacion.codigo`). Con él, y solo desde esta pantalla:
 *
 *   - se ve la sanción que impide entrar: causal, motivo, política, desde
 *     cuándo y, si es una suspensión, hasta cuándo;
 *   - se ve la última apelación de esa sanción y la fecha límite del panel
 *     (15 días hábiles), o hasta cuándo se puede apelar;
 *   - se presenta UNA apelación. El panel de moderación y administración la
 *     resuelve y la decisión llega al correo.
 *
 * El código vive en una variable de este módulo: nunca en la dirección, ni en
 * `localStorage` ni en `sessionStorage`. No es una sesión ni un token: no
 * abre nada más que esta pantalla, caduca a los 30 minutos y se gasta al
 * apelar.
 *
 * @module cuentas/apelacion-sin-sesion
 */

import { baseDeApi } from '../comun/base-api.js';
import { tipoDelProblema } from '../comun/codigo-de-correo.js';
import { cuerpoDe } from '../comun/entrada.js';
import { limpiarAviso, pintarAviso } from '../comun/ui/aviso.js';
import { conCarga } from '../comun/ui/boton.js';
import { marcarErrorDe } from '../comun/ui/campo.js';
import { h, vaciar } from '../comun/ui/dom.js';
import { fechaHora } from '../comun/ui/formato.js';
import { formularioListo } from '../comun/ui/formulario-seguro.js';
import { nombreDeCausal } from '../comun/ui/sancion.js';

/** Rutas del contrato. Literales enteros: el guardián de rutas los lee. */
export const RUTAS_DE_APELACION = Object.freeze({
  consulta: '/api/v1/auth/apelacion/consulta',
  apelacion: '/api/v1/auth/apelacion',
});

/** Los `type` de rechazo de estas rutas (ms-identidad-auth 2.4.0). */
export const PROBLEMAS_DE_LA_APELACION = Object.freeze({
  CODIGO_INVALIDO: 'codigo-de-apelacion-invalido',
  NO_PROCEDE: 'apelacion-no-procede',
  NO_DISPONIBLE: 'moderacion-no-disponible',
  DATOS_INVALIDOS: 'datos-invalidos',
  DEMASIADAS: 'demasiadas-peticiones',
});

/** Lo que admite el servidor en el argumento (§7.3.2, como toda apelación). */
export const ARGUMENTO_MAXIMO = 2000;

/** Un rechazo del servidor, ya leído. La vista decide por `estado` y `tipo`. */
export class FalloDeLaApelacion extends Error {
  /**
   * @param {{estado: number, tipo?: string|null}} datos
   */
  constructor({ estado, tipo = null }) {
    super(`HTTP ${estado}`);
    this.name = 'FalloDeLaApelacion';
    this.estado = estado;
    this.tipo = tipo;
  }
}

/**
 * El código para apelar que trae un rechazo del login, si lo trae: solo en
 * el 403 de una cuenta suspendida o baneada, y solo como problem details.
 *
 * @param {number} estado
 * @param {unknown} cuerpo
 * @returns {{codigo: string, expiraEn: string|null}|null}
 */
export function codigoDeApelacion(estado, cuerpo) {
  if (estado !== 403 || !cuerpo || typeof cuerpo !== 'object') {
    return null;
  }
  const tipo = tipoDelProblema(cuerpo);
  if (tipo !== 'cuenta-suspendida' && tipo !== 'cuenta-baneada') {
    return null;
  }
  const apelacion = /** @type {{apelacion?: {codigo?: unknown, expiraEn?: unknown}}} */ (cuerpo)
    .apelacion;
  if (!apelacion || typeof apelacion.codigo !== 'string' || !apelacion.codigo) {
    return null;
  }
  return {
    codigo: apelacion.codigo,
    expiraEn: typeof apelacion.expiraEn === 'string' ? apelacion.expiraEn : null,
  };
}

const ACEPTA = 'application/json, application/problem+json';

/**
 * POST con cuerpo JSON y `fetch` directo, como `pedirLogin`: aquí no hay
 * sesión que adjuntar, y el aviso flotante del interceptor repetiría encima
 * el rechazo que ya dice la pantalla.
 */
async function enviar(fetchImpl, ruta, cuerpo) {
  const respuesta = await fetchImpl(`${baseDeApi()}${ruta}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: ACEPTA },
    body: JSON.stringify(cuerpo),
  });
  const leido = await cuerpoDe(respuesta);
  if (!respuesta.ok) {
    throw new FalloDeLaApelacion({ estado: respuesta.status, tipo: tipoDelProblema(leido) });
  }
  return leido;
}

/**
 * La sanción que impide entrar y cómo va su apelación. No gasta el código.
 *
 * @param {string} codigo
 * @param {{fetchImpl?: typeof fetch}} [opciones]
 */
export function consultarApelacion(codigo, { fetchImpl = globalThis.fetch } = {}) {
  return enviar(fetchImpl, RUTAS_DE_APELACION.consulta, { codigo });
}

/**
 * Presenta la apelación. Un éxito gasta el código.
 *
 * @param {{codigo: string, sancionId: string, argumento: string}} datos
 * @param {{fetchImpl?: typeof fetch}} [opciones]
 */
export function presentarApelacion(
  { codigo, sancionId, argumento },
  { fetchImpl = globalThis.fetch } = {},
) {
  return enviar(fetchImpl, RUTAS_DE_APELACION.apelacion, {
    codigo,
    sancionId,
    argumento: String(argumento ?? '').trim(),
  });
}

/**
 * Por qué no vale el argumento, o `null` si vale.
 *
 * @param {unknown} texto
 * @returns {string|null}
 */
export function motivoDelArgumento(texto) {
  const limpio = String(texto ?? '').trim();
  if (!limpio) {
    return 'Cuéntale al panel por qué crees que la sanción no corresponde.';
  }
  if (limpio.length > ARGUMENTO_MAXIMO) {
    return `Como mucho ${ARGUMENTO_MAXIMO} caracteres.`;
  }
  return null;
}

/** Cómo se nombra la decisión del panel. */
const DECISION = Object.freeze({
  MANTENIDA: 'la sanción se mantiene',
  REDUCIDA: 'la sanción se redujo',
  REVERTIDA: 'la sanción se revirtió',
});

/**
 * Lo que dice la pantalla de la sanción y de su apelación, sin tocar el DOM.
 *
 * @param {{sancion?: object|null, apelableHasta?: string|null, puedeApelar?: boolean,
 *   motivoNoApelable?: string|null, apelacion?: object|null}} estado
 * @returns {{titulo: string, datos: Array<[string, string]>,
 *   aviso: {tono: 'info'|'advertencia'|'exito', titulo: string, detalle: string|null}|null,
 *   puedeApelar: boolean, plazo: string|null, sinSancion: boolean}}
 */
export function resumenDeLaApelacion(estado) {
  const sancion = estado?.sancion ?? null;
  if (!sancion) {
    return {
      titulo: 'Tu cuenta ya no tiene una sanción que te impida entrar',
      datos: [],
      aviso: {
        tono: 'exito',
        titulo: 'Puedes volver a entrar',
        detalle: 'Escribe otra vez tu correo y tu contraseña.',
      },
      puedeApelar: false,
      plazo: null,
      sinSancion: true,
    };
  }
  const baneo = sancion.tipo === 'BANEO';
  const titulo = baneo
    ? 'Inhabilitación definitiva'
    : `Suspensión hasta el ${fechaHora(sancion.vigenteHasta)}`;
  /** @type {Array<[string, string]>} */
  const datos = [];
  const causal = nombreDeCausal(sancion.causal);
  if (causal) {
    datos.push(['Causal', causal]);
  }
  datos.push(['Motivo', String(sancion.motivo ?? '')]);
  if (sancion.politica) {
    datos.push(['Política', String(sancion.politica)]);
  }
  datos.push(['Desde', fechaHora(sancion.emitidaEn)]);

  const apelacion = estado?.apelacion ?? null;
  const resuelta = apelacion && DECISION[apelacion.estado] ? apelacion : null;
  const decision = resuelta
    ? `Tu última apelación se resolvió${resuelta.resueltaEn ? ` el ${fechaHora(resuelta.resueltaEn)}` : ''}: ${DECISION[resuelta.estado]}.`
    : null;
  let aviso = null;
  if (apelacion?.estado === 'PENDIENTE') {
    aviso = apelacion.vencida
      ? {
          tono: 'advertencia',
          titulo: 'Tu apelación sigue en revisión',
          detalle: `La presentaste el ${fechaHora(apelacion.creadaEn)}. El plazo del panel para responderte venció el ${fechaHora(apelacion.decisionAntesDe)}; tu apelación sigue abierta y la decisión te llegará al correo.`,
        }
      : {
          tono: 'info',
          titulo: 'Tu apelación está en revisión',
          detalle: `La presentaste el ${fechaHora(apelacion.creadaEn)}. El panel te responderá a más tardar el ${fechaHora(apelacion.decisionAntesDe)}, y la decisión te llegará al correo.`,
        };
  } else if (estado?.motivoNoApelable === 'FUERA_DE_PLAZO') {
    aviso = {
      tono: 'advertencia',
      titulo: 'El plazo para apelar terminó',
      detalle:
        [
          estado.apelableHasta
            ? `Se podía apelar hasta el ${fechaHora(estado.apelableHasta)}.`
            : null,
          decision,
        ]
          .filter(Boolean)
          .join(' ') || null,
    };
  } else if (resuelta) {
    // Una apelación resuelta no cierra la puerta si aún hay plazo: se dice la
    // decisión y, debajo, se puede volver a apelar.
    aviso = {
      tono: 'info',
      titulo: decision,
      detalle: resuelta.decisionMotivo ? `Decisión del panel: ${resuelta.decisionMotivo}` : null,
    };
  }
  const puedeApelar = estado?.puedeApelar === true;
  return {
    titulo,
    datos,
    aviso,
    puedeApelar,
    plazo:
      puedeApelar && estado.apelableHasta
        ? `Puedes apelar hasta el ${fechaHora(estado.apelableHasta)}.`
        : null,
    sinSancion: false,
  };
}

/**
 * Qué decir cuando falla una petición, decidido por el estado y el `type`.
 *
 * @param {unknown} error
 * @returns {{tono: 'error'|'advertencia', titulo: string, detalle: string|null,
 *   volverAEntrar: boolean, consultarDeNuevo: boolean}}
 */
export function falloDeLaApelacion(error) {
  const estado = error instanceof FalloDeLaApelacion ? error.estado : 0;
  const tipo = error instanceof FalloDeLaApelacion ? error.tipo : null;
  if (estado === 401 || tipo === PROBLEMAS_DE_LA_APELACION.CODIGO_INVALIDO) {
    return {
      tono: 'advertencia',
      titulo: 'El código para apelar caducó',
      detalle: 'Vuelve a escribir tu correo y tu contraseña para obtener otro.',
      volverAEntrar: true,
      consultarDeNuevo: false,
    };
  }
  if (estado === 422 || estado === 404 || tipo === PROBLEMAS_DE_LA_APELACION.NO_PROCEDE) {
    return {
      tono: 'advertencia',
      titulo: 'No se pudo presentar la apelación',
      detalle: 'La situación de tu sanción cambió. Aquí abajo tienes cómo está ahora.',
      volverAEntrar: false,
      consultarDeNuevo: true,
    };
  }
  if (estado === 429) {
    return {
      tono: 'advertencia',
      titulo: 'Hiciste varias solicitudes seguidas',
      detalle: 'Espera un momento y vuelve a intentarlo.',
      volverAEntrar: false,
      consultarDeNuevo: false,
    };
  }
  if (estado === 400) {
    return {
      tono: 'advertencia',
      titulo: 'Revisa lo que escribiste',
      detalle: `El argumento no puede quedar vacío ni pasar de ${ARGUMENTO_MAXIMO} caracteres.`,
      volverAEntrar: false,
      consultarDeNuevo: false,
    };
  }
  return {
    tono: 'error',
    titulo: 'No pudimos registrar tu apelación ahora',
    detalle: 'Inténtalo de nuevo en unos segundos.',
    volverAEntrar: false,
    consultarDeNuevo: false,
  };
}

/**
 * Monta la pantalla sobre la sección `[data-zona="apelacion"]` de
 * `login.html`.
 *
 * @param {ParentNode} raiz
 * @param {{
 *   alVolver?: () => void,
 *   consultar?: typeof consultarApelacion,
 *   presentar?: typeof presentarApelacion,
 * }} [opciones]
 * @returns {{abrir: (codigo: {codigo: string}) => Promise<void>, volver: () => void}|null}
 *   `null` si la vista no tiene la sección
 */
export function montarApelacionSinSesion(
  raiz,
  { alVolver = () => {}, consultar = consultarApelacion, presentar = presentarApelacion } = {},
) {
  const seccion = raiz.querySelector('[data-zona="apelacion"]');
  if (!seccion) {
    return null;
  }
  const titulo = seccion.querySelector('h2');
  const zonaSancion = seccion.querySelector('[data-zona="sancion-apelada"]');
  const zonaAviso = seccion.querySelector('[data-zona="aviso-apelacion"]');
  const formulario = seccion.querySelector('form');
  const plazo = formulario.querySelector('[data-zona="plazo-apelacion"]');
  const argumento = formulario.querySelector('[name="argumento"]');
  const botonEnviar = formulario.querySelector('button[type="submit"]');
  const contador = formulario.querySelector('[data-zona="contador-argumento"]');
  const botonVolver = seccion.querySelector('[data-accion="volver-desde-apelacion"]');

  /** Lo del primer paso que se aparta mientras dura la apelación, con cómo estaba. */
  const apartados = new Map();
  /** @type {string|null} */
  let codigo = null;
  /** @type {string|null} */
  let sancionId = null;
  let enviando = false;

  function apartarPrimerPaso() {
    for (const elemento of raiz.querySelectorAll('[data-paso="primero"]')) {
      if (!apartados.has(elemento)) {
        apartados.set(elemento, elemento.hidden);
      }
      elemento.hidden = true;
    }
  }

  function devolverPrimerPaso() {
    for (const [elemento, oculto] of apartados) {
      elemento.hidden = oculto;
    }
    apartados.clear();
  }

  function olvidar() {
    codigo = null;
    sancionId = null;
  }

  function soltarEnvio() {
    enviando = false;
  }

  // Aparte, como `soltarEnvio`: tras la respuesta del servidor el argumento
  // se borra a propósito (require-atomic-updates).
  function vaciarArgumento() {
    argumento.value = '';
    contar();
  }

  function contar() {
    contador.textContent = `${argumento.value.length} de ${ARGUMENTO_MAXIMO}`;
  }

  function limpiar() {
    olvidar();
    soltarEnvio();
    vaciar(zonaSancion);
    limpiarAviso(zonaAviso);
    argumento.value = '';
    marcarErrorDe(argumento, null);
    contar();
    conCarga(botonEnviar, false);
    formulario.hidden = true;
  }

  function volver() {
    limpiar();
    seccion.hidden = true;
    devolverPrimerPaso();
    alVolver();
  }

  /** La sanción y su apelación, con el formulario si se puede apelar. */
  function pintar(estado) {
    const resumen = resumenDeLaApelacion(estado);
    sancionId = estado?.sancion?.id ?? null;
    const lista = h('dl', { clase: 'cuenta-notas' });
    for (const [etiqueta, valor] of resumen.datos) {
      lista.append(
        h('div', {
          clase: 'cuenta-notas__nota',
          hijos: [h('dt', { texto: etiqueta }), h('dd', { texto: valor })],
        }),
      );
    }
    zonaSancion.replaceChildren(
      h('p', { clase: 't-subtitulo', texto: resumen.titulo, datos: { zona: 'titulo-sancion' } }),
      ...(resumen.datos.length ? [lista] : []),
    );
    if (resumen.aviso) {
      pintarAviso(zonaAviso, resumen.aviso);
    } else {
      limpiarAviso(zonaAviso);
    }
    formulario.hidden = !resumen.puedeApelar;
    plazo.textContent = resumen.plazo ?? '';
    return resumen;
  }

  async function cargar() {
    zonaSancion.replaceChildren(
      h('p', {
        clase: 'estado carga',
        texto: 'Cargando tu sanción…',
        atributos: { role: 'status' },
      }),
    );
    limpiarAviso(zonaAviso);
    formulario.hidden = true;
    const actual = codigo;
    let estado;
    try {
      estado = await consultar(actual);
    } catch (error) {
      vaciar(zonaSancion);
      mostrarFallo(falloDeLaApelacion(error));
      return null;
    }
    return pintar(estado);
  }

  function mostrarFallo(fallo) {
    const caja = pintarAviso(zonaAviso, {
      tono: fallo.tono,
      titulo: fallo.titulo,
      detalle: fallo.detalle,
      accion: fallo.volverAEntrar
        ? { texto: 'Volver a la entrada', nombre: 'volver-a-entrar', alPulsar: volver }
        : null,
    });
    zonaAviso.focus();
    return caja;
  }

  async function abrir({ codigo: valor }) {
    limpiar();
    codigo = valor;
    apartarPrimerPaso();
    seccion.hidden = false;
    titulo.focus();
    const resumen = await cargar();
    if (resumen?.puedeApelar) {
      argumento.focus();
    }
  }

  argumento.addEventListener('input', () => {
    contar();
    marcarErrorDe(argumento, null);
  });

  formulario.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    if (enviando || !codigo || !sancionId) {
      return;
    }
    limpiarAviso(zonaAviso);
    const motivo = motivoDelArgumento(argumento.value);
    marcarErrorDe(argumento, motivo);
    if (motivo) {
      argumento.focus();
      return;
    }
    enviando = true;
    conCarga(botonEnviar, true, 'Enviando…');
    let creada;
    try {
      creada = await presentar({ codigo, sancionId, argumento: argumento.value });
    } catch (error) {
      soltarEnvio();
      conCarga(botonEnviar, false);
      const fallo = falloDeLaApelacion(error);
      if (fallo.consultarDeNuevo) {
        await cargar();
      }
      mostrarFallo(fallo);
      return;
    }
    // El código ya se gastó: no queda nada que hacer con él.
    olvidar();
    soltarEnvio();
    conCarga(botonEnviar, false);
    formulario.hidden = true;
    vaciarArgumento();
    pintarAviso(zonaAviso, {
      tono: 'exito',
      titulo: 'Recibimos tu apelación',
      detalle: `Un panel de moderadores y administradores la revisará y te responderá a más tardar el ${fechaHora(creada?.decisionAntesDe)}. La decisión te llegará al correo.`,
    });
    zonaAviso.focus();
  });

  botonVolver.addEventListener('click', volver);
  formularioListo(formulario);
  contar();

  return { abrir, volver };
}
