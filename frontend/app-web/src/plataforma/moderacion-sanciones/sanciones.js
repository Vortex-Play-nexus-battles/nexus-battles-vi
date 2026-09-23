/**
 * Sanciones y apelaciones — HU-USR-004/005/006/007 y HU-NOT-005.
 *
 * Habla con `contracts/openapi/moderacion-sanciones-admin.yaml` (1.0.0) y con
 * `moderacion-sanciones-consulta.yaml` (1.3.0, para los límites vigentes)
 * por el borde. Dos vistas lo montan:
 *
 *   - `sanciones-admin.html` (moderación): historial de un usuario por su
 *     uid, emitir advertencia / suspensión / baneo (con confirmación) y el
 *     panel de apelaciones pendientes con su resolución.
 *   - `mis-sanciones.html` (jugador): sus sanciones con contador de tiempo
 *     restante (CA-03 de HU-USR-005), sus apelaciones y el formulario para
 *     apelar dentro de plazo.
 *
 * Todo error llega como problem details; se decide por `estado` y `motivo`,
 * nunca comparando textos (`shared/ui-kit/MAPEO-ERRORES.md`).
 */

import { fetchWithHttpErrorInterceptor } from '../../comun/interceptors/http-error.interceptor.js';
import { nodo } from '../../comun/ui/dom.js';
import { pintarAviso } from '../../comun/ui/aviso.js';
import { estadoDeError, pintarEstado } from '../../comun/ui/estado-vista.js';
import { campo } from '../../comun/ui/campo.js';

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
    super(problema?.detail ?? problema?.title ?? `Error ${estado}`);
    this.name = 'ErrorDeSanciones';
    this.estado = estado;
    this.titulo = problema?.title ?? 'No se pudo completar';
    this.detalle = problema?.detail ?? '';
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
  const nombre =
    { ADVERTENCIA: 'Advertencia', SUSPENSION: 'Suspension', BANEO: 'Baneo definitivo' }[
      sancion.tipo
    ] ?? sancion.tipo;
  if (sancion.revertidaEn) {
    return `${nombre} · revertida`;
  }
  if (sancion.tipo === TIPO.SUSPENSION) {
    const resto = tiempoRestante(sancion.vigenteHasta, ahora);
    return resto === 'vencida' ? `${nombre} · vencida` : `${nombre} · quedan ${resto}`;
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
 * La etiqueta del campo de duración de una suspensión, con el rango que aplica
 * hoy el servicio. Sin límites conocidos, la etiqueta no promete ningún rango:
 * el servicio sigue validando y su 400 dice el real.
 *
 * @param {{suspensionMinimaHoras?: number, suspensionMaximaDias?: number}|null} limites
 * @returns {string}
 */
export function textoDeRangoDeSuspension(limites) {
  const horas = Number(limites?.suspensionMinimaHoras);
  const dias = Number(limites?.suspensionMaximaDias);
  if (!Number.isFinite(horas) || !Number.isFinite(dias) || horas <= 0 || dias <= 0) {
    return 'Duración en horas';
  }
  const enHoras = `${horas} ${horas === 1 ? 'hora' : 'horas'}`;
  const enDias = `${dias} ${dias === 1 ? 'día' : 'días'}`;
  return `Duración en horas (${enHoras} a ${enDias})`;
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

/**
 * Cuerpo de la solicitud de emisión a partir del formulario. Sin inventar:
 * solo los campos del contrato, y `duracionHoras` solo para suspensión.
 *
 * @param {FormData|Record<string,string>} datos
 * @returns {object}
 */
export function solicitudDesde(datos) {
  const leer = (clave) => (datos instanceof FormData ? datos.get(clave) : datos[clave]) ?? '';
  const tipo = String(leer('tipo'));
  const cuerpo = {
    usuarioId: String(leer('usuarioId')).trim(),
    tipo,
    motivo: String(leer('motivo')).trim(),
  };
  const politica = String(leer('politica')).trim();
  if (politica) {
    cuerpo.politica = politica;
  }
  const comentarioId = String(leer('comentarioId')).trim();
  if (comentarioId) {
    cuerpo.comentarioId = comentarioId;
  }
  if (tipo === TIPO.SUSPENSION) {
    const horas = Number(leer('duracionHoras'));
    if (Number.isFinite(horas) && horas > 0) {
      cuerpo.duracionHoras = Math.floor(horas);
    }
  }
  if (tipo === TIPO.BANEO) {
    cuerpo.confirmacion =
      leer('confirmacion') === 'on' ||
      leer('confirmacion') === true ||
      leer('confirmacion') === 'true';
  }
  return cuerpo;
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
 * Tarjeta de una apelación (panel y Mis sanciones).
 *
 * @param {object} apelacion
 * @param {{resolver?: (apelacion: object, decision: string, motivo: string, nuevaVigencia: string|null) => void}} [opciones]
 */
export function tarjetaDeApelacion(apelacion, { resolver } = {}) {
  const tarjeta = nodo('article', 'tarjeta pila pila--compacta');
  tarjeta.dataset.apelacionId = apelacion.id;
  tarjeta.dataset.estado = apelacion.estado;
  tarjeta.appendChild(
    nodo('strong', 'tarjeta__titulo', `Apelación · ${apelacion.estado.toLowerCase()}`),
  );
  tarjeta.appendChild(nodo('p', 't-cuerpo', apelacion.argumento));
  tarjeta.appendChild(
    nodo(
      'p',
      't-meta',
      `Sanción ${apelacion.sancionId} · abierta el ${new Date(apelacion.creadaEn).toLocaleString('es-CO')}`,
    ),
  );
  if (apelacion.decisionMotivo) {
    tarjeta.appendChild(nodo('p', 't-meta', `Decisión: ${apelacion.decisionMotivo}`));
  }
  if (apelacion.estado === 'PENDIENTE' && typeof resolver === 'function') {
    const form = nodo('form', 'pila pila--compacta');
    form.dataset.zona = 'resolucion';
    // Con `campo()` en vez de una plantilla en `innerHTML`: etiqueta asociada
    // por for/id y el motivo del rechazo en su sitio, igual que en el resto
    // de formularios del producto.
    const decision = campo({
      nombre: 'decision',
      etiqueta: 'Decision',
      requerido: true,
      opciones: [
        { valor: 'MANTENIDA', texto: 'Mantener' },
        { valor: 'REDUCIDA', texto: 'Reducir (suspension)' },
        { valor: 'REVERTIDA', texto: 'Revertir' },
      ],
    });
    const nuevaVigencia = campo({
      nombre: 'nuevaVigencia',
      etiqueta: 'Nueva fecha fin',
      tipo: 'datetime-local',
      pista: 'Solo al reducir la sanción.',
    });
    const motivacion = campo({
      nombre: 'motivo',
      etiqueta: 'Motivacion',
      requerido: true,
      multilinea: true,
    });
    const resolverBoton = nodo('button', 'boton boton--primario boton--pequeno', 'Resolver');
    resolverBoton.type = 'submit';
    resolverBoton.dataset.accion = 'resolver';
    form.append(decision.elemento, nuevaVigencia.elemento, motivacion.elemento, resolverBoton);
    form.addEventListener('submit', (evento) => {
      evento.preventDefault();
      const datos = new FormData(form);
      const local = String(datos.get('nuevaVigencia') ?? '');
      resolver(
        apelacion,
        String(datos.get('decision')),
        String(datos.get('motivo') ?? '').trim(),
        local ? new Date(local).toISOString() : null,
      );
    });
    tarjeta.appendChild(form);
  }
  return tarjeta;
}

/**
 * Monta el panel de moderación.
 *
 * @param {ParentNode} raiz documento con las zonas `[data-zona=...]`
 * @param {{rol?: string|null, fetchImpl?: Function, ahora?: () => number}} [opciones]
 */
export function montarPanelDeModeracion(
  raiz,
  { rol = null, fetchImpl, ahora = () => Date.now() } = {},
) {
  const zonaAviso = raiz.querySelector('[data-zona="aviso"]');
  const formBuscar = raiz.querySelector('[data-zona="buscar"]');
  const formEmitir = raiz.querySelector('[data-zona="emitir"]');
  const zonaHistorial = raiz.querySelector('[data-zona="historial"]');
  const zonaApelaciones = raiz.querySelector('[data-zona="apelaciones"]');
  const selectorTipo = formEmitir?.querySelector('[name="tipo"]');
  const opcionBaneo = formEmitir?.querySelector('[name="tipo"] option[value="BANEO"]');

  if (!ROLES_DE_MODERACION.includes(rol)) {
    pintarAviso(zonaAviso, {
      tono: 'advertencia',
      titulo: 'Este panel es de moderación',
      detalle: 'Tu rol no permite emitir ni revisar sanciones.',
    });
    raiz.querySelectorAll('form button, form input, form select, form textarea').forEach((c) => {
      c.disabled = true;
    });
    return;
  }
  // El moderador solo emite temporales (Tabla 24): no se le ofrece el baneo.
  if (opcionBaneo && !ROLES_DE_ADMINISTRACION.includes(rol)) {
    opcionBaneo.disabled = true;
    opcionBaneo.textContent = 'Baneo definitivo (solo administradores)';
  }

  const sincronizarCampos = () => {
    const tipo = selectorTipo?.value;
    formEmitir.querySelectorAll('[data-solo]').forEach((zona) => {
      zona.hidden = zona.dataset.solo !== tipo;
    });
  };
  selectorTipo?.addEventListener('change', sincronizarCampos);
  sincronizarCampos();

  /**
   * El rango de la suspensión sale del servicio, no del HTML.
   *
   * El `max="720"` que había escrito en la vista eran 30 días convertidos a
   * mano: con `sanciones.suspension.maxima-dias` en 2, el formulario aceptaba
   * 720 horas y el servidor respondía 400. Ahora el tope, el mínimo y la
   * etiqueta salen de `GET /api/v1/sanciones/limites`, que es la misma fuente
   * que usa la validación.
   */
  async function aplicarLimites() {
    const duracion = formEmitir?.querySelector('[name="duracionHoras"]');
    const etiqueta = raiz.querySelector('[data-campo="rango-suspension"]');
    let limites;
    try {
      limites = await api.limites(fetchImpl);
    } catch (error) {
      // Sin límites el campo se queda sin tope y la etiqueta sin rango: el
      // servicio sigue validando, y su 400 dice el rango real. Inventar un
      // tope aquí sería volver al defecto que esto corrige.
      console.warn('[sanciones] no se pudieron leer los límites vigentes:', error);
      return null;
    }
    if (etiqueta) {
      etiqueta.textContent = textoDeRangoDeSuspension(limites);
    }
    if (duracion) {
      const minimo = Number(limites?.suspensionMinimaHoras);
      const maximo = Number(limites?.suspensionMaximaHoras);
      if (Number.isFinite(minimo) && minimo > 0) {
        duracion.min = String(minimo);
        if (Number(duracion.value) < minimo) {
          duracion.value = String(minimo);
        }
      }
      if (Number.isFinite(maximo) && maximo > 0) {
        duracion.max = String(maximo);
        if (Number(duracion.value) > maximo) {
          duracion.value = String(maximo);
        }
      }
    }
    return limites;
  }

  async function cargarHistorial(uid) {
    zonaHistorial.replaceChildren(nodo('p', 't-meta', 'Cargando…'));
    try {
      const sanciones = await api.historial(uid, fetchImpl);
      zonaHistorial.replaceChildren();
      if (sanciones.length === 0) {
        zonaHistorial.appendChild(nodo('p', 't-meta', 'Este usuario no tiene sanciones.'));
      }
      sanciones.forEach((s) => zonaHistorial.appendChild(tarjetaDeSancion(s, { ahora: ahora() })));
    } catch (error) {
      const deNegocio = error instanceof ErrorDeSanciones;
      pintarEstado(
        zonaHistorial,
        estadoDeError({
          titulo: deNegocio ? error.titulo : 'No pudimos cargar el historial',
          detalle: deNegocio
            ? error.detalle
            : 'El servicio de moderación no respondió. Vuelve a intentarlo.',
          alReintentar: () => cargarHistorial(uid),
        }),
      );
    }
  }

  async function cargarApelaciones() {
    if (!zonaApelaciones) {
      return;
    }
    try {
      const pendientes = await api.apelacionesPendientes(fetchImpl);
      zonaApelaciones.replaceChildren();
      if (pendientes.length === 0) {
        zonaApelaciones.appendChild(nodo('p', 't-meta', 'No hay apelaciones pendientes.'));
      }
      pendientes.forEach((a) =>
        zonaApelaciones.appendChild(
          tarjetaDeApelacion(a, {
            resolver: async (apelacion, decision, motivo, nuevaVigencia) => {
              try {
                await api.resolver(apelacion.id, { decision, motivo, nuevaVigencia }, fetchImpl);
                pintarAviso(zonaAviso, {
                  tono: 'exito',
                  titulo: 'Apelación resuelta',
                  detalle: `Decisión: ${decision.toLowerCase()}. El jugador queda avisado.`,
                });
                await cargarApelaciones();
              } catch (error) {
                avisarError(zonaAviso, error);
              }
            },
          }),
        ),
      );
    } catch (error) {
      // UX-R3.11 — igual que en parametros: el fallo de una zona se cuenta EN
      // esa zona y con reintento, no como una pildora suelta encima de una
      // pantalla vacia.
      zonaAviso.hidden = true;
      const deNegocio = error instanceof ErrorDeSanciones;
      pintarEstado(
        zonaApelaciones,
        estadoDeError({
          titulo: deNegocio ? error.titulo : 'No pudimos cargar las apelaciones',
          detalle: deNegocio
            ? error.detalle
            : 'El servicio de moderación no respondió. Vuelve a intentarlo.',
          alReintentar: cargarApelaciones,
        }),
      );
    }
  }

  formBuscar?.addEventListener('submit', (evento) => {
    evento.preventDefault();
    const uid = String(new FormData(formBuscar).get('usuarioId') ?? '').trim();
    if (uid) {
      formEmitir.querySelector('[name="usuarioId"]').value = uid;
      cargarHistorial(uid);
    }
  });

  formEmitir?.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    zonaAviso.hidden = true;
    const cuerpo = solicitudDesde(new FormData(formEmitir));
    if (cuerpo.tipo === TIPO.BANEO && !cuerpo.confirmacion) {
      pintarAviso(zonaAviso, {
        tono: 'advertencia',
        titulo: 'El baneo es definitivo',
        detalle: 'Marca la confirmación para continuar.',
      });
      return;
    }
    const boton = formEmitir.querySelector('[type="submit"]');
    boton.disabled = true;
    try {
      const sancion = await api.emitir(cuerpo, fetchImpl);
      pintarAviso(zonaAviso, {
        tono: 'exito',
        titulo: 'Sanción emitida',
        detalle: `${descripcionDe(sancion, ahora())}. El jugador recibirá el aviso.`,
      });
      formEmitir.querySelector('[name="motivo"]').value = '';
      await cargarHistorial(cuerpo.usuarioId);
    } catch (error) {
      avisarError(zonaAviso, error);
    } finally {
      boton.disabled = false;
    }
  });

  aplicarLimites();
  cargarApelaciones();
  return { cargarHistorial, cargarApelaciones, aplicarLimites };
}

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
