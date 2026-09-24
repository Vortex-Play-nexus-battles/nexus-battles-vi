/**
 * «Preparando tu cuenta» — R17.
 *
 * Lo primero que ve una cuenta recién creada. El servicio de identidad le
 * está pidiendo a finanzas los créditos de bienvenida y al inventario el
 * héroe inicial con su equipo; esta pantalla cuenta cómo va, paso a paso,
 * con lo que dice el servidor (`GET /api/v1/auth/onboarding`).
 *
 * Tres promesas, y las tres se prueban en `preparando.test.js`:
 *
 * 1. **No se inventa nada.** Un paso sale «Listo» solo si el servidor dice
 *    `HECHO`. Los créditos del resumen final son los que acreditó finanzas.
 * 2. **Nunca deja a nadie atascado.** Si algo falla, se dice qué y cuándo se
 *    reintenta solo, se ofrece reintentar ya y se deja entrar igualmente.
 * 3. **No molesta a quien no tiene nada que preparar.** Una cuenta sin alta
 *    (anterior a esta versión, o de administración) sigue de largo.
 *
 * @module cuentas/preparando
 */

import { exigirAcceso } from '../comun/acceso.js';
import {
  ESTADOS_ALTA,
  ESTADOS_PASO,
  FalloDelAlta,
  consultarAlta,
  reintentarAlta,
} from '../comun/alta.js';
import { montarCabecera } from '../comun/cabecera-app.js';
import {
  MOTIVOS,
  RUTAS,
  olvidarSesion,
  resolver,
  rutaDeVuelta,
  rutaSegura,
  urlDeLogin,
} from '../comun/sesion.js';
import { clases, h, vaciar } from '../comun/ui/dom.js';
import { creditos as formatoCreditos } from '../comun/ui/formato.js';

/** Esperas entre consultas: cortas al principio, que es cuando suele terminar. */
export const PAUSAS_MS = Object.freeze([1000, 1500, 2000, 3000, 5000]);

/** Espera tras un fallo al consultar, o mientras el alta espera su reintento. */
export const PAUSA_LARGA_MS = 5000;

/** A partir de aquí se ofrece entrar sin esperar a que termine. */
export const TARDA_DEMASIADO_MS = 45_000;

/** @param {number} intento consultas ya hechas */
export function pausaTras(intento) {
  return PAUSAS_MS[Math.min(Math.max(intento, 0), PAUSAS_MS.length - 1)];
}

/**
 * Cuándo vuelve a intentarlo el servidor, dicho con palabras.
 *
 * @param {string|null|undefined} siguienteIntento ISO-8601
 * @param {number} [ahora] milisegundos
 * @returns {string}
 */
export function cuandoReintenta(siguienteIntento, ahora = Date.now()) {
  const momento = Date.parse(siguienteIntento ?? '');
  if (Number.isNaN(momento)) {
    return 'en breve';
  }
  const segundos = Math.round((momento - ahora) / 1000);
  if (segundos <= 5) {
    return 'en unos segundos';
  }
  if (segundos < 60) {
    return `en ${segundos} segundos`;
  }
  const minutos = Math.round(segundos / 60);
  return minutos === 1 ? 'en un minuto' : `en ${minutos} minutos`;
}

/**
 * El estado de un paso en palabras: quien no distingue el color o el icono
 * del marcador lo lee aquí.
 *
 * @param {{estado: string}} paso
 * @param {boolean} enCurso
 */
export function estadoDelPaso(paso, enCurso) {
  if (paso.estado === ESTADOS_PASO.HECHO) {
    return 'Listo';
  }
  if (paso.estado === ESTADOS_PASO.ERROR) {
    return 'Todavía no se pudo completar';
  }
  return enCurso ? 'En curso…' : 'Pendiente';
}

/**
 * Lo esencial del alta para decidir qué pintar.
 *
 * @param {object|null} alta la respuesta del servidor
 * @returns {{fase: 'preparando'|'error'|'listo'|'sin-alta', hechos: number, total: number}}
 */
export function faseDelAlta(alta) {
  const pasos = Array.isArray(alta?.pasos) ? alta.pasos : [];
  const hechos = pasos.filter((paso) => paso.estado === ESTADOS_PASO.HECHO).length;
  let fase = 'preparando';
  if (alta?.estado === ESTADOS_ALTA.NO_APLICA) {
    fase = 'sin-alta';
  } else if (alta?.listo === true || alta?.estado === ESTADOS_ALTA.COMPLETO) {
    fase = 'listo';
  } else if (alta?.estado === ESTADOS_ALTA.ERROR_REINTENTABLE) {
    fase = 'error';
  }
  return { fase, hechos, total: pasos.length };
}

/** Ruta (del propio sitio) de una URL absoluta, para usarla como vuelta. */
function rutaDe(url) {
  try {
    const destino = new URL(url);
    return rutaSegura(`${destino.pathname}${destino.search}${destino.hash}`, destino.origin);
  } catch {
    return null;
  }
}

/**
 * Monta la pantalla sobre el marcado de `preparando.html` y empieza a
 * consultar. Todo lo que toca el mundo exterior se puede inyectar.
 *
 * @param {ParentNode} raiz
 * @param {{
 *   destino: string,
 *   consultar?: typeof consultarAlta,
 *   reintentar?: typeof reintentarAlta,
 *   reloj?: {setTimeout: Function, clearTimeout: Function},
 *   ahora?: () => number,
 *   reemplazar?: (url: string) => void,
 *   alSinSesion?: () => void,
 *   documento?: Document,
 * }} opciones
 * @returns {{detener: () => void, primeraConsulta: Promise<void>}}
 */
export function montarPreparacion(
  raiz,
  {
    destino,
    consultar = consultarAlta,
    reintentar = reintentarAlta,
    reloj = globalThis,
    ahora = () => Date.now(),
    reemplazar = (url) => {
      globalThis.location.replace(url);
    },
    alSinSesion,
    documento = globalThis.document,
  },
) {
  const zona = (nombre) => raiz.querySelector(`[data-zona="${nombre}"]`);
  const z = {
    titulo: zona('titulo'),
    resumen: zona('resumen'),
    valor: zona('valor-progreso'),
    riel: zona('riel-progreso'),
    relleno: zona('relleno-progreso'),
    pasos: zona('pasos'),
    aviso: zona('aviso'),
    avisoTitulo: zona('aviso-titulo'),
    avisoDetalle: zona('aviso-detalle'),
    listo: zona('listo'),
    resumenListo: zona('resumen-listo'),
    empezar: zona('empezar'),
    reintentar: zona('reintentar'),
    continuar: zona('continuar'),
  };
  z.empezar.href = destino;
  z.continuar.href = destino;

  const alSalirSinSesion =
    alSinSesion ??
    (() => {
      olvidarSesion();
      reemplazar(urlDeLogin({ volver: rutaDe(destino), motivo: MOTIVOS.CADUCADA }));
    });

  const inicio = ahora();
  let consultas = 0;
  let fallosSeguidos = 0;
  let temporizador = null;
  let detenido = false;

  function detener() {
    detenido = true;
    reloj.clearTimeout(temporizador);
  }

  function programar(milisegundos) {
    reloj.clearTimeout(temporizador);
    if (!detenido) {
      temporizador = reloj.setTimeout(ciclo, milisegundos);
    }
  }

  function decir(texto) {
    // Solo si cambia: repetir el mismo texto en una región viva lo vuelve a
    // leer en voz alta cada pocos segundos.
    if (z.resumen.textContent !== texto) {
      z.resumen.textContent = texto;
    }
  }

  function mostrarAviso(titulo, detalle) {
    z.avisoTitulo.textContent = titulo;
    z.avisoDetalle.textContent = detalle;
    z.aviso.hidden = false;
  }

  function ocultarAviso() {
    z.aviso.hidden = true;
  }

  function pintarProgreso(hechos, total) {
    const cuenta = total || 4;
    z.valor.textContent = `${hechos} de ${cuenta}`;
    z.riel.setAttribute('aria-valuemax', String(cuenta));
    z.riel.setAttribute('aria-valuenow', String(hechos));
    z.riel.setAttribute('aria-valuetext', `${hechos} de ${cuenta} pasos listos`);
    z.relleno.style.width = `${Math.round((hechos / cuenta) * 100)}%`;
  }

  function pintarPasos(alta) {
    vaciar(z.pasos);
    const activa =
      alta.estado === ESTADOS_ALTA.EN_PROCESO || alta.estado === ESTADOS_ALTA.PENDIENTE;
    let yaHayUnoEnCurso = false;
    (alta.pasos ?? []).forEach((paso, indice) => {
      const hecho = paso.estado === ESTADOS_PASO.HECHO;
      const fallido = paso.estado === ESTADOS_PASO.ERROR;
      const enCurso = activa && !hecho && !fallido && !yaHayUnoEnCurso;
      yaHayUnoEnCurso = yaHayUnoEnCurso || enCurso;

      let marca = String(indice + 1);
      if (hecho) {
        marca = '✓';
      } else if (fallido) {
        marca = '!';
      }

      const cuerpo = h('div', { clase: 'alta-paso__cuerpo' });
      cuerpo.append(
        h('span', { clase: 'paso__etiqueta', texto: paso.titulo }),
        h('span', { clase: 'alta-paso__estado', texto: estadoDelPaso(paso, enCurso) }),
      );
      if (fallido && paso.motivo) {
        cuerpo.append(h('span', { clase: 'alta-paso__motivo', texto: paso.motivo }));
      }

      const elemento = h('li', {
        clase: clases(
          'paso',
          'alta-paso',
          hecho && 'paso--completado',
          enCurso && 'paso--actual',
          fallido && 'alta-paso--error',
        ),
        datos: { paso: paso.paso, estado: paso.estado },
      });
      elemento.append(
        h('span', { clase: 'paso__marcador', texto: marca, atributos: { 'aria-hidden': 'true' } }),
        cuerpo,
      );
      z.pasos.append(elemento);
    });
  }

  function pintarListo(alta) {
    ocultarAviso();
    vaciar(z.resumenListo);
    if (alta.creditosIniciales !== null && alta.creditosIniciales !== undefined) {
      z.resumenListo.append(
        h('dt', { texto: 'Créditos de bienvenida' }),
        h('dd', {
          texto: `${formatoCreditos(alta.creditosIniciales)} créditos`,
          datos: { zona: 'creditos-iniciales' },
        }),
      );
    }
    if (alta.heroeInicial) {
      z.resumenListo.append(
        h('dt', { texto: 'Héroe inicial' }),
        h('dd', { texto: 'Equipado y listo para combatir', datos: { zona: 'heroe-inicial' } }),
      );
    }
    z.listo.hidden = false;
    z.empezar.hidden = false;
    z.reintentar.hidden = true;
    z.continuar.hidden = true;
    z.titulo.textContent = '¡Tu cuenta está lista!';
    decir('Todo listo: ya puedes jugar.');
    if (documento) {
      documento.title = 'Tu cuenta está lista — NEXUS BATTLES VI';
    }
    // El foco va al título, que ahora lo anuncia: quien usa lector de
    // pantalla se entera sin tener que recorrer la página buscando qué cambió.
    z.titulo.focus();
  }

  /** @returns {'preparando'|'error'|'listo'|'sin-alta'} */
  function pintar(alta) {
    const { fase, hechos, total } = faseDelAlta(alta);
    if (fase === 'sin-alta') {
      return fase;
    }
    pintarProgreso(hechos, total);
    pintarPasos(alta);

    if (fase === 'listo') {
      pintarListo(alta);
      return fase;
    }

    if (fase === 'error') {
      mostrarAviso(
        'Algo no salió a la primera',
        `Lo reintentamos solos ${cuandoReintenta(alta.siguienteIntento, ahora())}. También puedes reintentarlo ahora, o entrar ya: podrás jugar en cuanto tu héroe esté listo.`,
      );
      decir(`Preparando tu cuenta: ${hechos} de ${total} pasos listos. Uno necesita otro intento.`);
      z.reintentar.hidden = false;
      z.continuar.hidden = false;
      return fase;
    }

    decir(`Preparando tu cuenta: ${hechos} de ${total} pasos listos.`);
    z.reintentar.hidden = true;
    if (ahora() - inicio >= TARDA_DEMASIADO_MS) {
      mostrarAviso(
        'Está tardando más de lo normal',
        'Seguimos en ello. Si prefieres, entra ya: podrás jugar en cuanto tu héroe esté listo.',
      );
      z.continuar.hidden = false;
    } else {
      ocultarAviso();
    }
    return fase;
  }

  function manejarFallo(fallo) {
    if (fallo instanceof FalloDelAlta && fallo.tipo === 'sin-sesion') {
      detener();
      alSalirSinSesion();
      return;
    }
    fallosSeguidos += 1;
    mostrarAviso(
      'No pudimos consultar tu cuenta',
      'Lo intentamos de nuevo en unos segundos. Tu cuenta ya está creada: no se pierde nada.',
    );
    if (fallosSeguidos >= 2) {
      z.continuar.hidden = false;
    }
    programar(PAUSA_LARGA_MS);
  }

  function seguir(alta) {
    const fase = pintar(alta);
    if (fase === 'sin-alta') {
      detener();
      reemplazar(destino);
      return;
    }
    if (fase === 'listo') {
      detener();
      return;
    }
    programar(fase === 'error' ? PAUSA_LARGA_MS : pausaTras(consultas));
  }

  async function ciclo() {
    if (detenido) {
      return;
    }
    consultas += 1;
    let alta;
    try {
      alta = await consultar();
    } catch (fallo) {
      manejarFallo(fallo);
      return;
    }
    fallosSeguidos = 0;
    seguir(alta);
  }

  z.reintentar.addEventListener('click', async () => {
    z.reintentar.disabled = true;
    reloj.clearTimeout(temporizador);
    decir('Reintentando…');
    let alta;
    try {
      alta = await reintentar();
    } catch (fallo) {
      z.reintentar.disabled = false;
      manejarFallo(fallo);
      return;
    }
    z.reintentar.disabled = false;
    consultas = 0;
    seguir(alta);
  });

  return { detener, primeraConsulta: ciclo() };
}

// ---------------------------------------------------------------- arranque

function arrancar() {
  // Pide sesión: es el alta de ESTA cuenta. Sin sesión, al login.
  const sesion = exigirAcceso('preparando');
  if (!sesion) {
    return;
  }
  montarCabecera(document.querySelector('[data-cabecera-app]'), { vista: 'preparando' });
  const volver = rutaDeVuelta(globalThis.location.search);
  const destino = volver
    ? new URL(volver, globalThis.location.origin).href
    : resolver(RUTAS.inicio);
  montarPreparacion(document, { destino });
}

if (document.body?.dataset.vista === 'preparando') {
  arrancar();
}
