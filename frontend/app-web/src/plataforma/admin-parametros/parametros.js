/**
 * Parametros del sistema — HU-ADM-001, sobre contracts/openapi/admin-parametros.yaml 1.0.0.
 *
 * El catalogo se pinta como tabla; cada editable tiene su formulario con el
 * rango (o las opciones) que publica el propio servicio, motivo obligatorio y
 * vigencia futura opcional (CA-03). Los inalterables (Charter) se muestran
 * bloqueados y lo dicen (CA-05). El historial de un parametro se abre bajo
 * demanda (CA-01). Todo error llega como problem details y se decide por
 * `motivo`, nunca por el texto (`shared/ui-kit/MAPEO-ERRORES.md`).
 */

import { fetchWithHttpErrorInterceptor } from '../../comun/interceptors/http-error.interceptor.js';
import { nodo } from '../../comun/ui/dom.js';
import { pintarAviso } from '../../comun/ui/aviso.js';
import { campo } from '../../comun/ui/campo.js';

export const ROLES_DE_ADMINISTRACION = Object.freeze(['ADMINISTRADOR', 'SUPER_ADMINISTRADOR']);

function baseDeApi() {
  const meta = globalThis.document?.querySelector?.('meta[name="nexus-api-base"]');
  return String(meta?.content ?? '').replace(/\/+$/, '');
}

export class ErrorDeParametros extends Error {
  constructor(problema, estado) {
    super(problema?.detail ?? problema?.title ?? `Error ${estado}`);
    this.name = 'ErrorDeParametros';
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
    return respuesta.json();
  }
  let problema = null;
  try {
    problema = await respuesta.json();
  } catch {
    problema = null;
  }
  throw new ErrorDeParametros(problema, respuesta.status);
}

export const api = {
  listar: (f) => pedir('/api/v1/parametros', {}, f),
  cambiar: (clave, cuerpo, f) =>
    pedir(
      `/api/v1/parametros/${encodeURIComponent(clave)}`,
      {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(cuerpo),
      },
      f,
    ),
  historial: (clave, f) =>
    pedir(`/api/v1/parametros/${encodeURIComponent(clave)}/historial`, {}, f),
};

/* ---- Presentación (puro, probado) ---- */

/** Texto del rango u opciones que admite un parametro. */
export function reglaDe(parametro) {
  if (parametro.inalterable) {
    return `Inalterable (${parametro.origen})`;
  }
  if (Array.isArray(parametro.opciones) && parametro.opciones.length > 0) {
    return parametro.opciones.join(' | ');
  }
  const partes = [];
  if (parametro.minimo !== null && parametro.minimo !== undefined) {
    partes.push(`min ${parametro.minimo}`);
  }
  if (parametro.maximo !== null && parametro.maximo !== undefined) {
    partes.push(`max ${parametro.maximo}`);
  }
  const tipo = parametro.tipo.toLowerCase();
  return partes.length > 0 ? `${tipo}, ${partes.join(', ')}` : tipo;
}

/** Valor vigente legible: «sin definir (pendiente del PO)» cuando es null. */
export function valorDe(parametro) {
  if (parametro.valor === null || parametro.valor === undefined || parametro.valor === '') {
    return 'sin definir (pendiente del PO)';
  }
  return parametro.unidad ? `${parametro.valor} ${parametro.unidad}` : String(parametro.valor);
}

/** Cuerpo del PUT a partir del formulario; `vigenteDesde` solo si se dio. */
export function cambioDesde(datos) {
  const leer = (clave) => (datos instanceof FormData ? datos.get(clave) : datos[clave]) ?? '';
  const valor = String(leer('valor')).trim();
  const vigencia = String(leer('vigenteDesde')).trim();
  const cuerpo = { valor: valor === '' ? null : valor, motivo: String(leer('motivo')).trim() };
  if (vigencia) {
    cuerpo.vigenteDesde = new Date(vigencia).toISOString();
  }
  return cuerpo;
}

/* ---- DOM ---- */

function avisarError(zona, error) {
  const deNegocio = error instanceof ErrorDeParametros;
  pintarAviso(zona, {
    tono: deNegocio && error.estado < 500 ? 'advertencia' : 'error',
    titulo: deNegocio ? error.titulo : 'No pudimos contactar con el servicio',
    detalle: deNegocio ? error.detalle : 'Revisa tu conexion e intentalo de nuevo.',
  });
}

export function filaDeParametro(parametro, { administra, alCambiar, alVerHistorial } = {}) {
  const fila = nodo('article', 'tarjeta pila pila--ajustada');
  fila.dataset.clave = parametro.clave;
  fila.dataset.inalterable = String(parametro.inalterable);
  const titulo = nodo('strong', 'tarjeta__titulo', parametro.clave);
  fila.appendChild(titulo);
  fila.appendChild(nodo('p', 't-cuerpo', parametro.descripcion));
  const valor = nodo('p', 't-cuerpo');
  valor.dataset.campo = 'valor';
  valor.textContent = `Vigente: ${valorDe(parametro)} · v${parametro.version}`;
  fila.appendChild(valor);
  if (parametro.valorProgramado !== null && parametro.valorProgramado !== undefined) {
    const programado = nodo(
      'p',
      'aviso aviso--info',
      `Programado: ${parametro.valorProgramado} desde ${new Date(parametro.vigenteDesde).toLocaleString('es-CO')}`,
    );
    programado.dataset.campo = 'programado';
    fila.appendChild(programado);
  }
  fila.appendChild(nodo('p', 't-meta', `${reglaDe(parametro)} · origen: ${parametro.origen}`));

  if (parametro.inalterable) {
    const bloqueado = nodo(
      'p',
      't-meta',
      'Fijado por el Project Charter: no se edita desde el panel.',
    );
    bloqueado.dataset.campo = 'bloqueado';
    fila.appendChild(bloqueado);
    return fila;
  }
  if (!administra) {
    return fila;
  }
  const form = nodo('form', 'fila');
  form.dataset.zona = 'cambio';

  // Antes esto era una plantilla dentro de `form.innerHTML`, con
  // `parametro.opciones` y `parametro.unidad` —dos valores que llegan del
  // servicio— interpolados SIN escapar dentro de `<option value="${o}">`. Un
  // valor con una comilla o un `<` rompia el marcado del panel de
  // administracion, y no hay ninguna razon para construirlo asi: `campo()`
  // pone cada opcion con `textContent`, que no interpreta marcado.
  const conOpciones = Array.isArray(parametro.opciones) && parametro.opciones.length > 0;
  const nuevoValor = campo({
    nombre: 'valor',
    etiqueta: parametro.unidad ? `Nuevo valor (${parametro.unidad})` : 'Nuevo valor',
    valor: conOpciones ? (parametro.valor ?? '') : '',
    opciones: conOpciones
      ? parametro.opciones.map((o) => ({ valor: String(o), texto: String(o) }))
      : null,
    atributos: conOpciones
      ? {}
      : {
          placeholder: 'vacio = sin definir',
          inputmode: parametro.tipo === 'ENTERO' || parametro.tipo === 'DECIMAL' ? 'decimal' : null,
        },
  });
  const motivo = campo({
    nombre: 'motivo',
    etiqueta: 'Motivo',
    requerido: true,
    atributos: { minlength: 3, maxlength: 500 },
  });
  const vigenteDesde = campo({
    nombre: 'vigenteDesde',
    etiqueta: 'Vigente desde',
    tipo: 'datetime-local',
    pista: 'Opcional: deja vacio para aplicarlo ya.',
  });
  form.append(nuevoValor.elemento, motivo.elemento, vigenteDesde.elemento);

  const guardar = nodo('button', 'boton boton--primario boton--pequeno', 'Guardar');
  guardar.type = 'submit';
  guardar.dataset.accion = 'guardar';
  const historial = nodo('button', 'boton boton--secundario boton--pequeno', 'Historial');
  historial.type = 'button';
  historial.dataset.accion = 'historial';
  form.append(guardar, historial);

  form.addEventListener('submit', (evento) => {
    evento.preventDefault();
    alCambiar?.(parametro, cambioDesde(new FormData(form)), form);
  });
  form
    .querySelector('[data-accion="historial"]')
    .addEventListener('click', () => alVerHistorial?.(parametro, fila));
  fila.appendChild(form);
  return fila;
}

export function listaDeHistorial(versiones) {
  const ul = nodo('ul', 'pila pila--ajustada');
  ul.dataset.zona = 'historial';
  if (versiones.length === 0) {
    ul.appendChild(nodo('li', 't-meta', 'Sin cambios: valor inicial del catalogo.'));
  }
  versiones.forEach((v) => {
    ul.appendChild(
      nodo(
        'li',
        't-meta',
        `v${v.version} · ${v.valorAnterior ?? 'sin definir'} → ${v.valorNuevo ?? 'sin definir'} · ${v.motivo} · ${new Date(v.cambiadoEn).toLocaleString('es-CO')}${
          Date.parse(v.vigenteDesde) > Date.parse(v.cambiadoEn) + 1000
            ? ` (vigente desde ${new Date(v.vigenteDesde).toLocaleString('es-CO')})`
            : ''
        }`,
      ),
    );
  });
  return ul;
}

/**
 * Monta la vista: `[data-zona="aviso"]`, `[data-zona="catalogo"]`.
 *
 * @param {ParentNode} raiz
 * @param {{rol?: string|null, fetchImpl?: Function}} [opciones]
 */
export function montarParametros(raiz, { rol = null, fetchImpl } = {}) {
  const zonaAviso = raiz.querySelector('[data-zona="aviso"]');
  const zonaCatalogo = raiz.querySelector('[data-zona="catalogo"]');
  const administra = ROLES_DE_ADMINISTRACION.includes(rol);

  if (!administra) {
    pintarAviso(zonaAviso, {
      tono: 'info',
      titulo: 'Solo lectura',
      detalle: 'Configurar parametros es de administracion; tu rol solo consulta.',
    });
  }

  async function cargar() {
    try {
      const catalogo = await api.listar(fetchImpl);
      zonaCatalogo.replaceChildren();
      if (catalogo.length === 0) {
        zonaCatalogo.appendChild(nodo('p', 't-meta', 'El catalogo esta vacio.'));
      }
      catalogo.forEach((p) =>
        zonaCatalogo.appendChild(
          filaDeParametro(p, {
            administra,
            alCambiar: async (parametro, cambio, form) => {
              const boton = form.querySelector('[data-accion="guardar"]');
              boton.disabled = true;
              zonaAviso.hidden = true;
              try {
                const actualizado = await api.cambiar(parametro.clave, cambio, fetchImpl);
                pintarAviso(zonaAviso, {
                  tono: 'exito',
                  titulo:
                    actualizado.valorProgramado !== null &&
                    actualizado.valorProgramado !== undefined
                      ? 'Cambio programado'
                      : 'Parametro actualizado',
                  detalle: `${actualizado.clave} · vigente: ${valorDe(actualizado)} · v${actualizado.version}. Queda versionado y auditado.`,
                });
                await cargar();
              } catch (error) {
                avisarError(zonaAviso, error);
                boton.disabled = false;
              }
            },
            alVerHistorial: async (parametro, fila) => {
              fila.querySelector('[data-zona="historial"]')?.remove();
              try {
                const versiones = await api.historial(parametro.clave, fetchImpl);
                fila.appendChild(listaDeHistorial(versiones));
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

  cargar();
  return { cargar };
}
