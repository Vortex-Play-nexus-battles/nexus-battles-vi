/**
 * Tablero de metricas tecnicas (HU-MET-004) y de usuarios y moderacion
 * (HU-MET-001), sobre contracts/openapi/metricas-plataforma.yaml 1.5.0.
 *
 * **No calcula nada.** Alertas, brechas y agregados vienen del servicio; esta
 * pantalla los pinta y los deja exportar (CA-02 de las dos historias).
 * Sigue `shared/ui-kit/MAPEO-ERRORES.md`: se decide por `type`, nunca por el
 * texto.
 */

import { baseDeApi, ErrorDeMetricas } from './cliente-metricas.js';
import { fetchWithHttpErrorInterceptor } from '../../comun/interceptors/http-error.interceptor.js';

async function pedir(
  ruta,
  fetchImpl = fetchWithHttpErrorInterceptor,
  aceptar = 'application/json',
) {
  const respuesta = await fetchImpl(`${baseDeApi()}${ruta}`, {
    method: 'GET',
    headers: { Accept: aceptar },
  });
  if (!respuesta.ok) {
    let problema = null;
    try {
      problema = await respuesta.json();
    } catch {
      problema = null;
    }
    throw new ErrorDeMetricas(problema, respuesta.status);
  }
  return aceptar === 'text/plain' ? respuesta.text() : respuesta.json();
}

export const api = {
  tecnicas: (f) => pedir('/api/v1/tecnicas', f),
  tecnicasTexto: (f) => pedir('/api/v1/tecnicas/informe/texto', f, 'text/plain'),
  moderacion: (desde, hasta, f) => {
    const q = new URLSearchParams();
    if (desde) {
      q.set('desde', desde);
    }
    if (hasta) {
      q.set('hasta', hasta);
    }
    const consulta = q.toString();
    return pedir(`/api/v1/moderacion${consulta ? `?${consulta}` : ''}`, f);
  },
};

/* ---- Presentación (puro, probado) ---- */

/** @returns {string} porcentaje legible o «n/d» */
export function porcentaje(fraccion) {
  return typeof fraccion === 'number' ? `${Math.round(fraccion * 100)} %` : 'n/d';
}

/** @returns {string} milisegundos redondeados o «n/d» */
export function ms(valor) {
  return typeof valor === 'number' ? `${Math.round(valor)} ms` : 'n/d';
}

/**
 * Fila de la tabla tecnica para un servicio: valores y si cada uno esta en
 * alerta segun los umbrales que publica el propio servicio.
 */
export function filaDe(metricas, umbrales) {
  return {
    servicio: metricas.servicio,
    brecha: metricas.brecha ?? null,
    cpu: porcentaje(metricas.cpu),
    cpuEnAlerta: typeof metricas.cpu === 'number' && metricas.cpu > umbrales.cpu,
    memoria:
      typeof metricas.memoriaMb === 'number' ? `${Math.round(metricas.memoriaMb)} MB` : 'n/d',
    peticiones: String(metricas.peticiones ?? 0),
    promedio: ms(metricas.promedioMs),
    maximo: ms(metricas.maximoMs),
    maximoEnAlerta:
      typeof metricas.maximoMs === 'number' && metricas.maximoMs > umbrales.latenciaMs,
    errores: String(metricas.errores5xx ?? 0),
    tasaDeError:
      typeof metricas.tasaDeError === 'number'
        ? `${(metricas.tasaDeError * 100).toFixed(2)} %`
        : 'n/d',
  };
}

/** Resumen de una linea del tablero de moderacion. */
export function resumenDeModeracion(tablero) {
  const s = tablero.sanciones;
  const tipos = s.porTipo ?? {};
  return `${s.total} sanciones (${tipos.ADVERTENCIA ?? 0} advertencias, ${tipos.SUSPENSION ?? 0} suspensiones, ${tipos.BANEO ?? 0} baneos) · ${s.moderadoresActivos} moderadores activos · ${s.revertidas} revertidas`;
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

function celda(texto, enAlerta = false) {
  const td = nodo('td', enAlerta ? 'celda--alerta' : undefined, texto);
  if (enAlerta) {
    td.dataset.alerta = 'true';
  }
  return td;
}

export function tablaTecnica(tablero) {
  const tabla = nodo('table', 'tabla');
  tabla.dataset.zona = 'tabla-tecnica';
  const cabecera = nodo('thead');
  const fila = nodo('tr');
  ['Servicio', 'CPU', 'Memoria', 'Peticiones', 'Media', 'Maxima', 'Errores 5xx', 'Tasa'].forEach(
    (t) => fila.appendChild(nodo('th', undefined, t)),
  );
  cabecera.appendChild(fila);
  tabla.appendChild(cabecera);
  const cuerpo = nodo('tbody');
  tablero.servicios.forEach((m) => {
    const f = filaDe(m, tablero.umbrales);
    const tr = nodo('tr');
    tr.dataset.servicio = f.servicio;
    if (f.brecha) {
      tr.dataset.brecha = 'true';
      tr.appendChild(celda(f.servicio));
      const td = celda(`Brecha de observabilidad: ${f.brecha}`, true);
      td.colSpan = 7;
      tr.appendChild(td);
    } else {
      tr.append(
        celda(f.servicio),
        celda(f.cpu, f.cpuEnAlerta),
        celda(f.memoria),
        celda(f.peticiones),
        celda(f.promedio),
        celda(f.maximo, f.maximoEnAlerta),
        celda(f.errores),
        celda(f.tasaDeError),
      );
    }
    cuerpo.appendChild(tr);
  });
  tabla.appendChild(cuerpo);
  return tabla;
}

function listaDeAlertas(alertas, vacio) {
  const ul = nodo('ul', 'pila pila--ajustada');
  ul.dataset.zona = 'alertas';
  if (alertas.length === 0) {
    ul.appendChild(nodo('li', 't-meta', vacio));
  }
  alertas.forEach((a) => {
    const texto = typeof a === 'string' ? a : `${a.servicio} [${a.metrica}]: ${a.detalle}`;
    ul.appendChild(nodo('li', 'aviso aviso--advertencia', texto));
  });
  return ul;
}

function pintarError(zona, error) {
  const deNegocio = error instanceof ErrorDeMetricas;
  zona.replaceChildren(nodo('div', 'aviso aviso--error'));
  zona.firstChild.append(
    nodo(
      'strong',
      'aviso__titulo',
      deNegocio ? error.titulo : 'No pudimos contactar con el servicio de metricas',
    ),
    nodo(
      'p',
      'aviso__detalle',
      deNegocio ? error.message : 'Revisa tu conexion e intentalo de nuevo.',
    ),
  );
}

/**
 * Monta la vista: zonas `[data-zona="tecnicas"]`, `[data-zona="moderacion"]`,
 * formulario `[data-zona="periodo"]` y botones `[data-accion]`.
 */
export function montarTableroTecnico(raiz, { fetchImpl, descargar } = {}) {
  const zonaTecnicas = raiz.querySelector('[data-zona="tecnicas"]');
  const zonaModeracion = raiz.querySelector('[data-zona="moderacion"]');
  const formPeriodo = raiz.querySelector('[data-zona="periodo"]');
  const guardar =
    descargar ??
    ((nombre, contenido, tipo) => {
      const enlace = document.createElement('a');
      enlace.href = URL.createObjectURL(new Blob([contenido], { type: tipo }));
      enlace.download = nombre;
      enlace.click();
      URL.revokeObjectURL(enlace.href);
    });
  let ultimoTecnico = null;
  let ultimaModeracion = null;

  async function cargarTecnicas() {
    zonaTecnicas.replaceChildren(nodo('p', 't-meta', 'Recolectando metricas de los servicios…'));
    try {
      ultimoTecnico = await api.tecnicas(fetchImpl);
      zonaTecnicas.replaceChildren();
      zonaTecnicas.appendChild(
        nodo(
          'p',
          't-meta',
          `Generado ${new Date(ultimoTecnico.generadoEn).toLocaleString('es-CO')} · umbrales: CPU ${porcentaje(ultimoTecnico.umbrales.cpu)}, latencia ${ultimoTecnico.umbrales.latenciaMs} ms, disponibilidad ${ultimoTecnico.umbrales.disponibilidadPorcentaje} %`,
        ),
      );
      zonaTecnicas.appendChild(tablaTecnica(ultimoTecnico));
      if (ultimoTecnico.brechas.length > 0) {
        const brechas = nodo(
          'p',
          'aviso aviso--advertencia',
          `Brechas de observabilidad (${ultimoTecnico.brechas.length}): ${ultimoTecnico.brechas.join('; ')}`,
        );
        brechas.dataset.zona = 'brechas';
        zonaTecnicas.appendChild(brechas);
      }
      zonaTecnicas.appendChild(nodo('h3', undefined, 'Alertas'));
      zonaTecnicas.appendChild(
        listaDeAlertas(ultimoTecnico.alertas, 'Sin alertas sobre los umbrales.'),
      );
    } catch (error) {
      pintarError(zonaTecnicas, error);
    }
  }

  async function cargarModeracion(desde, hasta) {
    zonaModeracion.replaceChildren(nodo('p', 't-meta', 'Consultando moderacion…'));
    try {
      ultimaModeracion = await api.moderacion(desde, hasta, fetchImpl);
      zonaModeracion.replaceChildren();
      const s = ultimaModeracion.sanciones;
      zonaModeracion.appendChild(
        nodo(
          'p',
          't-meta',
          `Periodo ${new Date(s.desde).toLocaleDateString('es-CO')} – ${new Date(s.hasta).toLocaleDateString('es-CO')}`,
        ),
      );
      const resumen = nodo('p', 't-cuerpo', resumenDeModeracion(ultimaModeracion));
      resumen.dataset.zona = 'resumen-moderacion';
      zonaModeracion.appendChild(resumen);
      const dias = nodo('ul', 'pila pila--ajustada');
      dias.dataset.zona = 'por-dia';
      if ((s.porDia ?? []).length === 0) {
        dias.appendChild(nodo('li', 't-meta', 'Sin sanciones en el periodo.'));
      }
      (s.porDia ?? []).forEach((d) =>
        dias.appendChild(nodo('li', undefined, `${d.fecha}: ${d.emitidas}`)),
      );
      zonaModeracion.appendChild(dias);
      const ap = s.apelaciones ?? {};
      zonaModeracion.appendChild(
        nodo(
          'p',
          't-meta',
          `Apelaciones: ${ap.PENDIENTE ?? 0} pendientes, ${ap.MANTENIDA ?? 0} mantenidas, ${ap.REDUCIDA ?? 0} reducidas, ${ap.REVERTIDA ?? 0} revertidas`,
        ),
      );
      zonaModeracion.appendChild(nodo('h3', undefined, 'Alertas'));
      zonaModeracion.appendChild(
        listaDeAlertas(
          ultimaModeracion.alertas,
          ultimaModeracion.alertasConfiguradas
            ? 'Sin alertas: ningun dia supera el umbral.'
            : 'Sin umbral configurado (decision pendiente del PO, D-25): se publican los conteos, no se evalua ninguna alerta.',
        ),
      );
      const pendientes = nodo('ul', 'pila pila--ajustada');
      pendientes.dataset.zona = 'pendientes';
      (ultimaModeracion.pendientes ?? []).forEach((p) =>
        pendientes.appendChild(nodo('li', 't-meta', `Pendiente: ${p}`)),
      );
      zonaModeracion.appendChild(pendientes);
    } catch (error) {
      pintarError(zonaModeracion, error);
    }
  }

  raiz
    .querySelector('[data-accion="recargar-tecnicas"]')
    ?.addEventListener('click', () => cargarTecnicas());
  raiz.querySelector('[data-accion="exportar-tecnicas"]')?.addEventListener('click', async () => {
    try {
      const texto = await api.tecnicasTexto(fetchImpl);
      guardar('metricas-tecnicas.txt', texto, 'text/plain');
    } catch (error) {
      pintarError(zonaTecnicas, error);
    }
  });
  raiz.querySelector('[data-accion="exportar-moderacion"]')?.addEventListener('click', () => {
    if (ultimaModeracion) {
      guardar(
        'metricas-moderacion.json',
        JSON.stringify(ultimaModeracion, null, 2),
        'application/json',
      );
    }
  });
  formPeriodo?.addEventListener('submit', (evento) => {
    evento.preventDefault();
    const datos = new FormData(formPeriodo);
    const desde = String(datos.get('desde') ?? '');
    const hasta = String(datos.get('hasta') ?? '');
    cargarModeracion(
      desde ? new Date(desde).toISOString() : null,
      hasta ? new Date(hasta).toISOString() : null,
    );
  });

  cargarTecnicas();
  cargarModeracion(null, null);
  return { cargarTecnicas, cargarModeracion };
}
