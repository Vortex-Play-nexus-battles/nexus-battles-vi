/**
 * Sanciones y apelaciones — HU-USR-004/005/006/007 y HU-NOT-005.
 *
 * Habla con `contracts/openapi/moderacion-sanciones-admin.yaml` (1.0.0)
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
 * Si todavía se puede apelar: vigente, sin apelación abierta y dentro de los
 * 30 días desde la emisión (misma regla que el servicio, para no ofrecer un
 * botón que va a fallar).
 *
 * @param {object} sancion
 * @param {object[]} apelaciones del propio usuario
 * @param {number} [ahora]
 * @returns {boolean}
 */
export function sePuedeApelar(sancion, apelaciones = [], ahora = Date.now()) {
  if (!sancion.vigente || sancion.revertidaEn) {
    return false;
  }
  const emitida = new Date(sancion.emitidaEn).getTime();
  if (Number.isNaN(emitida) || ahora - emitida > 30 * 24 * 60 * 60 * 1000) {
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

function nodo(etiqueta, clase, texto) {
  const el = document.createElement(etiqueta);
  if (clase) {
    el.className = clase;
  }
  if (texto !== undefined) {
    el.textContent = texto;
  }
  return el;
}

export function pintarAviso(zona, { tono, titulo, detalle }) {
  zona.className = `aviso aviso--${tono}`;
  zona.replaceChildren(
    nodo('strong', 'aviso__titulo', titulo),
    nodo('p', 'aviso__detalle', detalle ?? ''),
  );
  zona.hidden = false;
}

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
        : 'Revisa tu conexion e intentalo de nuevo.',
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
    tarjeta.appendChild(nodo('p', 't-meta', `Politica: ${sancion.politica}`));
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
    nodo('strong', 'tarjeta__titulo', `Apelacion · ${apelacion.estado.toLowerCase()}`),
  );
  tarjeta.appendChild(nodo('p', 't-cuerpo', apelacion.argumento));
  tarjeta.appendChild(
    nodo(
      'p',
      't-meta',
      `Sancion ${apelacion.sancionId} · abierta el ${new Date(apelacion.creadaEn).toLocaleString('es-CO')}`,
    ),
  );
  if (apelacion.decisionMotivo) {
    tarjeta.appendChild(nodo('p', 't-meta', `Decision: ${apelacion.decisionMotivo}`));
  }
  if (apelacion.estado === 'PENDIENTE' && typeof resolver === 'function') {
    const form = nodo('form', 'pila pila--compacta');
    form.dataset.zona = 'resolucion';
    form.innerHTML = `
      <label class="campo"><span class="campo__etiqueta">Decision</span>
        <select class="campo__control" name="decision" required>
          <option value="MANTENIDA">Mantener</option>
          <option value="REDUCIDA">Reducir (suspension)</option>
          <option value="REVERTIDA">Revertir</option>
        </select></label>
      <label class="campo"><span class="campo__etiqueta">Nueva fecha fin (solo reducir)</span>
        <input class="campo__control" type="datetime-local" name="nuevaVigencia" /></label>
      <label class="campo"><span class="campo__etiqueta">Motivacion</span>
        <textarea class="campo__control" name="motivo" required rows="2"></textarea></label>
      <button class="boton boton--primario boton--pequeno" type="submit" data-accion="resolver">Resolver</button>`;
    form.addEventListener('submit', (evento) => {
      evento.preventDefault();
      const datos = new FormData(form);
      const local = String(datos.get('nuevaVigencia') ?? '');
      const nuevaVigencia = local ? new Date(local).toISOString() : null;
      resolver(
        apelacion,
        String(datos.get('decision')),
        String(datos.get('motivo') ?? '').trim(),
        nuevaVigencia,
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
      titulo: 'Este panel es de moderacion',
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
    formEmitir.querySelectorAll('[data-solo]').forEach((campo) => {
      campo.hidden = campo.dataset.solo !== tipo;
    });
  };
  selectorTipo?.addEventListener('change', sincronizarCampos);
  sincronizarCampos();

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
      zonaHistorial.replaceChildren();
      avisarError(zonaAviso, error);
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
                  titulo: 'Apelacion resuelta',
                  detalle: `Decision: ${decision.toLowerCase()}. El jugador queda avisado.`,
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
      avisarError(zonaAviso, error);
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
        detalle: 'Marca la confirmacion para continuar.',
      });
      return;
    }
    const boton = formEmitir.querySelector('[type="submit"]');
    boton.disabled = true;
    try {
      const sancion = await api.emitir(cuerpo, fetchImpl);
      pintarAviso(zonaAviso, {
        tono: 'exito',
        titulo: 'Sancion emitida',
        detalle: `${descripcionDe(sancion, ahora())}. El jugador recibira el aviso.`,
      });
      formEmitir.querySelector('[name="motivo"]').value = '';
      await cargarHistorial(cuerpo.usuarioId);
    } catch (error) {
      avisarError(zonaAviso, error);
    } finally {
      boton.disabled = false;
    }
  });

  cargarApelaciones();
  return { cargarHistorial, cargarApelaciones };
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
  const formApelar = raiz.querySelector('[data-zona="apelar"]');

  async function cargar() {
    try {
      const [sanciones, apelaciones] = await Promise.all([
        api.historial(uid, fetchImpl),
        api.misApelaciones(fetchImpl),
      ]);
      zonaSanciones.replaceChildren();
      if (sanciones.length === 0) {
        zonaSanciones.appendChild(nodo('p', 't-meta', 'No tienes sanciones. Sigue asi.'));
      }
      sanciones.forEach((s) =>
        zonaSanciones.appendChild(
          tarjetaDeSancion(s, {
            ahora: ahora(),
            puedeApelar: sePuedeApelar(s, apelaciones, ahora()),
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
        zonaApelaciones.appendChild(nodo('p', 't-meta', 'No has apelado ninguna sancion.'));
      }
      apelaciones.forEach((a) => zonaApelaciones.appendChild(tarjetaDeApelacion(a)));
    } catch (error) {
      avisarError(zonaAviso, error);
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
        titulo: 'Apelacion enviada',
        detalle: 'El panel de revision la atendera y recibiras la decision motivada.',
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
