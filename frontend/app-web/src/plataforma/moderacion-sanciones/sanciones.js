/**
 * Sanciones y apelaciones — HU-USR-004/005/006/007 y HU-NOT-005.
 *
 * Habla con `contracts/openapi/moderacion-sanciones-admin.yaml` (1.4.0) y con
 * `moderacion-sanciones-consulta.yaml` (para los límites vigentes) por el
 * borde. Aquí vive lo que comparten las dos vistas —el cliente, el error y la
 * presentación de una sanción— y «Mis sanciones» del jugador (sus sanciones
 * con el contador de tiempo restante, CA-03 de HU-USR-005, sus apelaciones y
 * el formulario para apelar dentro de plazo).
 *
 * La consola de moderación (`sanciones-admin.html`) está en
 * `consola-sanciones.js` desde SA-4.
 *
 * Todo error llega como problem details; se decide por `estado` y `motivo`,
 * nunca comparando textos (`shared/ui-kit/MAPEO-ERRORES.md`).
 */

import { fetchWithHttpErrorInterceptor } from '../../comun/interceptors/http-error.interceptor.js';
import { nodo } from '../../comun/ui/dom.js';
import { pintarAviso } from '../../comun/ui/aviso.js';
import { cuentaAtrasRotulada, vigilarCuentasAtras } from '../../comun/ui/cuenta-atras.js';
import { estadoDeCuenta, NOMBRE_DE_SANCION } from '../../comun/ui/sancion.js';
import {
  respaldoPorEstado,
  textoDelServidor,
  tituloDelServidor,
} from '../../comun/ui/texto-de-fallo.js';

export const TIPO = Object.freeze({
  ADVERTENCIA: 'ADVERTENCIA',
  SUSPENSION: 'SUSPENSION',
  BANEO: 'BANEO',
});

export const DECISION = Object.freeze({
  MANTENIDA: 'MANTENIDA',
  REDUCIDA: 'REDUCIDA',
  REVERTIDA: 'REVERTIDA',
});

/** Roles que ven el panel; el baneo lo decide el servicio (moderador → 403). */
export const ROLES_DE_MODERACION = Object.freeze([
  'MODERADOR',
  'ADMINISTRADOR',
  'SUPER_ADMINISTRADOR',
]);
export const ROLES_DE_ADMINISTRACION = Object.freeze(['ADMINISTRADOR', 'SUPER_ADMINISTRADOR']);

function baseDeApi() {
  const meta = globalThis.document?.querySelector?.('meta[name="nexus-api-base"]');
  return String(meta?.content ?? '').replace(/\/+$/, '');
}

/** Error del servicio con la forma del problem details. */
export class ErrorDeSanciones extends Error {
  constructor(problema, estado) {
    // UXC-9 — nunca «Error 503»: el texto del servidor si se lee, y si no
    // la frase que toca a su código.
    const detalle = textoDelServidor(problema, estado, respaldoPorEstado(estado));
    super(detalle);
    this.name = 'ErrorDeSanciones';
    this.estado = estado;
    this.titulo = tituloDelServidor(problema, 'No se pudo completar');
    this.detalle = detalle;
    this.motivo = problema?.motivo ?? null;
  }
}

async function pedir(ruta, opciones = {}, fetchImpl = fetchWithHttpErrorInterceptor) {
  const respuesta = await fetchImpl(`${baseDeApi()}${ruta}`, {
    ...opciones,
    headers: { Accept: 'application/json', ...(opciones.headers ?? {}) },
  });
  if (respuesta.ok) {
    return respuesta.status === 204 ? null : respuesta.json();
  }
  let problema = null;
  try {
    problema = await respuesta.json();
  } catch {
    problema = null;
  }
  throw new ErrorDeSanciones(problema, respuesta.status);
}

const json = (cuerpo) => ({
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(cuerpo),
});

/* ---- API ---- */

export const api = {
  historial: (uid, f) => pedir(`/api/v1/sanciones/usuarios/${encodeURIComponent(uid)}`, {}, f),
  /**
   * Los límites que aplica HOY el servicio (HU-ADM-001 CA-04): rango de la
   * suspensión y plazo de apelación. Son configurables desde admin-parametros,
   * así que la vista los pregunta en vez de llevarlos escritos.
   */
  limites: (f) => pedir('/api/v1/sanciones/limites', {}, f),
  emitir: (cuerpo, f) => pedir('/api/v1/sanciones', json(cuerpo), f),
  apelar: (sancionId, argumento, f) =>
    pedir(`/api/v1/sanciones/${encodeURIComponent(sancionId)}/apelaciones`, json({ argumento }), f),
  apelacionesPendientes: (f) => pedir('/api/v1/apelaciones', {}, f),
  misApelaciones: (f) => pedir('/api/v1/apelaciones?mias=true', {}, f),
  resolver: (apelacionId, cuerpo, f) =>
    pedir(`/api/v1/apelaciones/${encodeURIComponent(apelacionId)}/resolucion`, json(cuerpo), f),
  /** SA-4 — una sanción por su id (la que se apela, en el panel). */
  sancion: (sancionId, f) => pedir(`/api/v1/sanciones/${encodeURIComponent(sancionId)}`, {}, f),
  /** SA-4 — levantar una suspensión o retirar una advertencia vigente (solo administración). */
  levantar: (sancionId, motivo, f) =>
    pedir(`/api/v1/sanciones/${encodeURIComponent(sancionId)}/levantamiento`, json({ motivo }), f),
};

/* ---- Presentación (puro, probado) ---- */

/**
 * Tiempo restante de una suspensión, legible (CA-03 de HU-USR-005).
 *
 * @param {string|null|undefined} vigenteHasta ISO 8601
 * @param {number} [ahora] milisegundos
 * @returns {string} '' si no aplica; 'vencida' si ya paso
 */
export function tiempoRestante(vigenteHasta, ahora = Date.now()) {
  if (!vigenteHasta) {
    return '';
  }
  const fin = new Date(vigenteHasta).getTime();
  if (Number.isNaN(fin)) {
    return '';
  }
  const restante = fin - ahora;
  if (restante <= 0) {
    return 'vencida';
  }
  const minutos = Math.ceil(restante / 60000);
  if (minutos < 60) {
    return `${minutos} min`;
  }
  const horas = Math.floor(minutos / 60);
  if (horas < 48) {
    return `${horas} h ${minutos % 60} min`;
  }
  const dias = Math.floor(horas / 24);
  return `${dias} d ${horas % 24} h`;
}

/**
 * Cómo se describe una sanción en una línea: tipo, vigencia y estado.
 *
 * @param {object} sancion `Sancion` del contrato
 * @param {number} [ahora]
 * @returns {string}
 */
export function descripcionDe(sancion, ahora = Date.now()) {
  const nombre = NOMBRE_DE_SANCION[sancion.tipo] ?? sancion.tipo;
  if (sancion.revertidaEn) {
    return `${nombre} · revertida`;
  }
  if (sancion.tipo === TIPO.SUSPENSION) {
    // UXC-7 — si el servidor dice si sigue vigente, manda él; la fecha solo
    // se mira cuando no lo dice. El tiempo que falta va en su cuenta atrás.
    const vencida =
      typeof sancion.vigente === 'boolean'
        ? !sancion.vigente
        : tiempoRestante(sancion.vigenteHasta, ahora) === 'vencida';
    return vencida ? `${nombre} · vencida` : `${nombre} · activa`;
  }
  if (sancion.tipo === TIPO.BANEO) {
    return `${nombre} · sin fecha fin`;
  }
  return `${nombre} · no restringe tu acceso`;
}

/**
 * Cómo se nombra el plazo de apelación dentro de un texto.
 *
 * El número sale del servicio (`GET /api/v1/sanciones/limites`), nunca de una
 * constante: `sanciones.apelacion.plazo-dias` es configurable y el día que el
 * Product Owner lo baje, un «30 días» escrito a mano deja a la pantalla
 * prometiendo lo que el servicio va a rechazar. Cuando el dato no está —la
 * vista cargó sin sesión, el servicio no respondió— se dice el plazo sin
 * número. Nunca se inventa un 30.
 *
 * @param {number|null|undefined} plazoDias
 * @returns {string}
 */
export function frasePlazoDeApelacion(plazoDias) {
  return Number.isFinite(plazoDias) && plazoDias > 0
    ? `dentro de los ${plazoDias} días siguientes`
    : 'dentro del plazo de apelación vigente';
}

/**
 * Si todavía se puede apelar: vigente, sin apelación abierta y dentro del
 * plazo desde la emisión (misma regla que el servicio, para no ofrecer un
 * botón que va a fallar).
 *
 * `plazoDias` viene de `GET /api/v1/sanciones/limites`. Cuando no se conoce,
 * el plazo **no** se aplica: ofrecer el botón y dejar que el servicio conteste
 * con su motivo es más honesto que esconder una acción legítima por un número
 * que no tenemos. Lo que no se hace nunca es suponer 30.
 *
 * @param {object} sancion
 * @param {object[]} apelaciones del propio usuario
 * @param {number} [ahora]
 * @param {number|null} [plazoDias] plazo vigente, o null si no se conoce
 * @returns {boolean}
 */
export function sePuedeApelar(sancion, apelaciones = [], ahora = Date.now(), plazoDias = null) {
  if (!sancion.vigente || sancion.revertidaEn) {
    return false;
  }
  const emitida = new Date(sancion.emitidaEn).getTime();
  if (Number.isNaN(emitida)) {
    return false;
  }
  const fueraDePlazo =
    Number.isFinite(plazoDias) &&
    plazoDias > 0 &&
    ahora - emitida > plazoDias * 24 * 60 * 60 * 1000;
  if (fueraDePlazo) {
    return false;
  }
  return !apelaciones.some((a) => a.sancionId === sancion.id && a.estado === 'PENDIENTE');
}

/* ---- DOM ---- */

function tonoDe(error) {
  if (!(error instanceof ErrorDeSanciones)) {
    return 'error';
  }
  return error.estado >= 500 ? 'error' : 'advertencia';
}

function avisarError(zona, error) {
  pintarAviso(zona, {
    tono: tonoDe(error),
    titulo:
      error instanceof ErrorDeSanciones ? error.titulo : 'No pudimos contactar con el servicio',
    detalle:
      error instanceof ErrorDeSanciones
        ? error.detalle
        : 'Revisa tu conexión e inténtalo de nuevo.',
  });
}

/**
 * Tarjeta de una sanción (historial y Mis sanciones).
 *
 * @param {object} sancion
 * @param {{ahora?: number, apelar?: (sancion: object) => void, puedeApelar?: boolean}} [opciones]
 */
export function tarjetaDeSancion(
  sancion,
  { ahora = Date.now(), apelar, puedeApelar = false } = {},
) {
  const tarjeta = nodo('article', 'tarjeta pila pila--compacta');
  tarjeta.dataset.sancionId = sancion.id;
  tarjeta.dataset.tipo = sancion.tipo;
  tarjeta.appendChild(nodo('strong', 'tarjeta__titulo', descripcionDe(sancion, ahora)));
  tarjeta.appendChild(nodo('p', 't-cuerpo', `Motivo: ${sancion.motivo}`));
  if (sancion.politica) {
    tarjeta.appendChild(nodo('p', 't-meta', `Política: ${sancion.politica}`));
  }
  const meta = nodo('p', 't-meta');
  meta.dataset.campo = 'meta';
  meta.textContent = `Emitida el ${new Date(sancion.emitidaEn).toLocaleString('es-CO')} por ${sancion.rolEmisor}`;
  tarjeta.appendChild(meta);
  if (sancion.revertidaEn && sancion.motivoReversion) {
    tarjeta.appendChild(nodo('p', 't-meta', `Revertida: ${sancion.motivoReversion}`));
  }
  // UXC-7 — SanctionCountdown: una suspensión activa dice cuánto le falta, y
  // la cuenta atrás corre sola (`vigilarCuentasAtras` en la vista).
  if (descripcionDe(sancion, ahora).endsWith('· activa') && sancion.vigenteHasta) {
    const vence = nodo('p', 't-meta');
    vence.append(
      cuentaAtrasRotulada(sancion.vigenteHasta),
      ` · hasta el ${new Date(sancion.vigenteHasta).toLocaleString('es-CO', {
        weekday: 'long',
        day: 'numeric',
        month: 'long',
        hour: '2-digit',
        minute: '2-digit',
      })}`,
    );
    tarjeta.appendChild(vence);
  }
  if (puedeApelar && typeof apelar === 'function') {
    const boton = nodo('button', 'boton boton--secundario boton--pequeno', 'Apelar');
    boton.type = 'button';
    boton.dataset.accion = 'apelar';
    boton.addEventListener('click', () => apelar(sancion));
    tarjeta.appendChild(boton);
  }
  return tarjeta;
}

/**
 * Tarjeta de una apelación en «Mis sanciones»: lo que se argumentó, cuándo, y
 * la decisión cuando llega (la resuelve el panel, en la consola).
 *
 * @param {object} apelacion
 */
export function tarjetaDeApelacion(apelacion) {
  const tarjeta = nodo('article', 'tarjeta pila pila--compacta');
  tarjeta.dataset.apelacionId = apelacion.id;
  tarjeta.dataset.estado = apelacion.estado;
  tarjeta.appendChild(
    nodo(
      'strong',
      'tarjeta__titulo',
      `Apelación · ${ESTADO_DE_TU_APELACION[apelacion.estado] ?? apelacion.estado.toLowerCase()}`,
    ),
  );
  tarjeta.appendChild(nodo('p', 't-cuerpo', apelacion.argumento));
  tarjeta.appendChild(
    nodo('p', 't-meta', `Presentada el ${new Date(apelacion.creadaEn).toLocaleString('es-CO')}`),
  );
  if (apelacion.estado === 'PENDIENTE' && apelacion.decisionAntesDe) {
    tarjeta.appendChild(
      nodo(
        'p',
        't-meta',
        `El panel decide a más tardar el ${new Date(apelacion.decisionAntesDe).toLocaleDateString(
          'es-CO',
          {
            day: 'numeric',
            month: 'long',
            year: 'numeric',
          },
        )}.`,
      ),
    );
  }
  if (apelacion.decisionMotivo) {
    tarjeta.appendChild(nodo('p', 't-meta', `Decisión: ${apelacion.decisionMotivo}`));
  }
  return tarjeta;
}

/** El estado de una apelación, para quien la presentó. */
const ESTADO_DE_TU_APELACION = Object.freeze({
  PENDIENTE: 'en revisión',
  MANTENIDA: 'la sanción se mantiene',
  REDUCIDA: 'la sanción se redujo',
  REVERTIDA: 'revertida',
});

/**
 * Monta «Mis sanciones» para el jugador.
 *
 * @param {ParentNode} raiz
 * @param {{uid: string, fetchImpl?: Function, ahora?: () => number}} opciones
 */
export function montarMisSanciones(raiz, { uid, fetchImpl, ahora = () => Date.now() }) {
  const zonaAviso = raiz.querySelector('[data-zona="aviso"]');
  const zonaSanciones = raiz.querySelector('[data-zona="sanciones"]');
  const zonaApelaciones = raiz.querySelector('[data-zona="apelaciones"]');
  const zonaIntro = raiz.querySelector('[data-zona="intro"]');
  const formApelar = raiz.querySelector('[data-zona="apelar"]');
  const zonaEstado = raiz.querySelector('[data-zona="estado-cuenta"]');
  let pararCuentas = () => {};

  async function cargar() {
    try {
      // Los límites no son críticos: si no se pueden leer, la vista sigue y
      // habla del plazo sin número. Por eso van con su propio `catch` y no
      // arrastran al `Promise.all` entero.
      const [sanciones, apelaciones, limites] = await Promise.all([
        api.historial(uid, fetchImpl),
        api.misApelaciones(fetchImpl),
        api.limites(fetchImpl).catch(() => null),
      ]);
      const plazoLeido = Number(limites?.apelacionPlazoDias);
      const plazoDias = Number.isFinite(plazoLeido) && plazoLeido > 0 ? plazoLeido : null;
      const frase = frasePlazoDeApelacion(plazoDias);
      if (zonaIntro) {
        zonaIntro.textContent =
          `Tu historial disciplinario. Una sanción vigente se puede apelar ${frase}; ` +
          'el panel de revisión responde con una decisión motivada.';
      }
      zonaSanciones.replaceChildren();
      // UXC-7 — lo primero, si la cuenta está restringida y hasta cuándo.
      if (zonaEstado) {
        zonaEstado.replaceChildren(estadoDeCuenta(sanciones, { titulo: 'Estado de tu cuenta' }));
      }
      if (sanciones.length === 0) {
        zonaSanciones.appendChild(
          nodo('p', 't-meta', 'No tienes ninguna sanción. Tu cuenta está en regla.'),
        );
      }
      sanciones.forEach((s) =>
        zonaSanciones.appendChild(
          tarjetaDeSancion(s, {
            ahora: ahora(),
            puedeApelar: sePuedeApelar(s, apelaciones, ahora(), plazoDias),
            apelar: (sancion) => {
              formApelar.hidden = false;
              formApelar.querySelector('[name="sancionId"]').value = sancion.id;
              formApelar.querySelector('[name="argumento"]').focus();
            },
          }),
        ),
      );
      zonaApelaciones.replaceChildren();
      if (apelaciones.length === 0) {
        zonaApelaciones.appendChild(
          nodo('p', 't-meta', `No has apelado ninguna sanción. Puedes apelar cada una ${frase}.`),
        );
      }
      apelaciones.forEach((a) => zonaApelaciones.appendChild(tarjetaDeApelacion(a)));
      pararCuentas();
      pararCuentas = vigilarCuentasAtras(raiz.querySelector('main') ?? zonaSanciones);
    } catch (error) {
      // UX-R3.8 — el fallo se pinta DONDE iban las sanciones, no solo en el
      // aviso de arriba. Antes quedaban dos tarjetas vacias —«Sanciones» y
      // «Mis apelaciones», con nada dentro— y el unico rastro del problema
      // era una caja amarilla que decia «No se pudo completar» sin mas. La
      // pantalla parecia decir que no tienes ninguna sancion, que es
      // exactamente lo contrario de lo que se sabe.
      console.warn('[sanciones] no se pudo cargar el historial:', error);
      const motivo = nodo(
        'p',
        't-meta',
        'No pudimos consultar tu historial disciplinario ahora mismo. No significa que no tengas sanciones: significa que no lo sabemos.',
      );
      const reintentar = nodo('button', 'boton boton--secundario', 'Reintentar');
      reintentar.type = 'button';
      reintentar.dataset.accion = 'reintentar';
      reintentar.addEventListener('click', () => cargar());
      // Una fila, para que el boton mida su texto: las zonas son `.pila`, que
      // estira a lo ancho a sus hijos, y «Reintentar» salia de lado a lado.
      const acciones = nodo('div', 'fila fila--acciones');
      acciones.appendChild(reintentar);
      zonaSanciones.replaceChildren(motivo, acciones);
      zonaApelaciones.replaceChildren(
        nodo('p', 't-meta', 'Tampoco pudimos consultar tus apelaciones.'),
      );
    }
  }

  formApelar?.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    const form = evento.currentTarget;
    const datos = new FormData(form);
    const boton = form.querySelector('[type="submit"]');
    boton.disabled = true;
    try {
      await api.apelar(
        String(datos.get('sancionId')),
        String(datos.get('argumento') ?? '').trim(),
        fetchImpl,
      );
      pintarAviso(zonaAviso, {
        tono: 'exito',
        titulo: 'Apelación enviada',
        detalle: 'El panel de revisión la atenderá y recibirás la decisión motivada.',
      });
      form.hidden = true;
      form.reset();
      await cargar();
    } catch (error) {
      avisarError(zonaAviso, error);
    } finally {
      boton.disabled = false;
    }
  });

  cargar();
  return { cargar };
}
