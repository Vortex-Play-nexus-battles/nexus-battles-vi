// HU-PAG-002 — Historial de transacciones en moneda real.
// El interceptor común adjunta el JWT como Authorization: Bearer <token>,
// así que aquí no hace falta leer nexus.token manualmente.

import { fetchWithHttpErrorInterceptor } from '../comun/interceptors/http-error.interceptor.js';

const BASE_API = '/api/v1/transacciones/mi-historial';
const TAMANO_PAGINA = 20;
const RUTA_LOGIN = './login.html';
const RUTA_MENU = './index.html';

const CLAVE_ROL = 'nexus.rolActual';

const estado = {
  pagina: 0,
  totalPaginas: 1,
};

const el = {
  tbody: document.getElementById('historial-tbody'),
  estado: document.getElementById('historial-estado'),
  btnAnterior: document.getElementById('btn-anterior'),
  btnSiguiente: document.getElementById('btn-siguiente'),
  paginaActual: document.getElementById('historial-pagina-actual'),
  btnVolver: document.getElementById('btn-volver'),
};

const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];

function formatearFecha(isoString) {
  if (!isoString) {
    return '—';
  }
  const fecha = new Date(isoString);
  if (Number.isNaN(fecha.getTime())) {
    return isoString;
  }
  const dia = fecha.getDate();
  const mes = MESES[fecha.getMonth()];
  const horas = String(fecha.getHours()).padStart(2, '0');
  const minutos = String(fecha.getMinutes()).padStart(2, '0');
  return `${dia} ${mes} · ${horas}:${minutos}`;
}

function formatearMonto(monto) {
  if (monto === null || monto === undefined) {
    return '—';
  }
  return new Intl.NumberFormat('es-CO', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(monto);
}

function claseResultado(resultado) {
  return `badge badge--${String(resultado || 'otro').toLowerCase()}`;
}

function mostrarEstado(texto, tipo) {
  el.estado.hidden = false;
  el.estado.textContent = texto;
  el.estado.classList.remove('carga', 'error', 'vacio', 'exito');
  if (tipo) {
    el.estado.classList.add(tipo);
  }
}

function ocultarEstado() {
  el.estado.hidden = true;
  el.estado.textContent = '';
}

/**
 * Una celda con texto. `textContent` y nunca `innerHTML`: el concepto y la
 * moneda vienen del servidor.
 *
 * @param {string} texto
 * @param {string} [clase]
 * @returns {HTMLTableCellElement}
 */
function celda(texto, clase) {
  const td = document.createElement('td');
  if (clase) {
    td.className = clase;
  }
  td.textContent = texto;
  return td;
}

/**
 * El enlace al comprobante, si lo hay y si es seguro seguirlo.
 *
 * UX-R2.8 — esto era `` `<a href="${registro.comprobanteUrl}">` `` dentro de
 * un `innerHTML`. Dos agujeros en una linea: la URL entraba sin escapar en un
 * atributo, y **un `comprobanteUrl` con `javascript:` se ejecutaba al pulsar
 * «Ver»**. El valor lo pone el proveedor de pagos, no el jugador, pero un
 * comprobante es exactamente el sitio por el que un atacante intentaria
 * colarse.
 *
 * Ahora se construye el nodo, se asigna por propiedad —que no interpreta
 * marcado— y **solo se acepta http(s)**.
 *
 * @param {string|null|undefined} url
 * @returns {HTMLAnchorElement|Text}
 */
function enlaceDeComprobante(url) {
  if (!url) {
    return document.createTextNode('—');
  }
  let destino;
  try {
    destino = new URL(url, globalThis.location?.href ?? 'https://localhost');
  } catch {
    return document.createTextNode('—');
  }
  if (destino.protocol !== 'http:' && destino.protocol !== 'https:') {
    console.warn('Comprobante con un esquema que no se sigue:', destino.protocol);
    return document.createTextNode('—');
  }

  const a = document.createElement('a');
  a.href = destino.href;
  a.target = '_blank';
  a.rel = 'noopener noreferrer';
  a.textContent = 'Ver';
  return a;
}

function renderFilas(registros) {
  el.tbody.replaceChildren();
  for (const registro of registros) {
    const tr = document.createElement('tr');

    const resultado = document.createElement('span');
    resultado.className = claseResultado(registro.resultado);
    resultado.textContent = registro.resultado ?? '—';
    const celdaResultado = document.createElement('td');
    celdaResultado.append(resultado);

    const celdaComprobante = document.createElement('td');
    celdaComprobante.append(enlaceDeComprobante(registro.comprobanteUrl));

    tr.append(
      celda(formatearFecha(registro.creado), 'celda-fecha'),
      celda(registro.concepto ?? '—'),
      celda(formatearMonto(registro.monto), 'celda-monto'),
      celda(registro.moneda ?? '—'),
      celdaResultado,
      celdaComprobante,
    );
    el.tbody.appendChild(tr);
  }
}

function actualizarPaginacion(pagina, totalPaginas) {
  estado.pagina = pagina;
  estado.totalPaginas = Math.max(totalPaginas, 1);
  el.paginaActual.textContent = `Página ${estado.pagina + 1} de ${estado.totalPaginas}`;
  el.btnAnterior.disabled = estado.pagina <= 0;
  el.btnSiguiente.disabled = estado.pagina >= estado.totalPaginas - 1;
}

async function cargar() {
  el.tbody.innerHTML = '';
  mostrarEstado('Cargando...', 'carga');

  const params = new URLSearchParams({
    page: String(estado.pagina),
    size: String(TAMANO_PAGINA),
  });

  try {
    const respuesta = await fetchWithHttpErrorInterceptor(`${BASE_API}?${params.toString()}`, {
      method: 'GET',
    });

    if (respuesta.status === 403) {
      // El interceptor común ya mostró el toast; aquí solo apagamos los botones.
      mostrarEstado('Debes iniciar sesión para ver tu historial.', 'error');
      el.btnAnterior.disabled = true;
      el.btnSiguiente.disabled = true;
      return;
    }

    if (!respuesta.ok) {
      mostrarEstado('No se pudo cargar el historial.', 'error');
      return;
    }

    const datos = await respuesta.json();
    const registros = datos.content ?? datos ?? [];
    const totalPaginas = datos.totalPages ?? 1;
    const paginaActual = datos.number ?? estado.pagina;

    if (registros.length === 0) {
      mostrarEstado('Aún no tienes transacciones registradas.', 'vacio');
    } else {
      ocultarEstado();
      renderFilas(registros);
    }
    actualizarPaginacion(paginaActual, totalPaginas);
  } catch {
    mostrarEstado('Error de red al consultar el historial.', 'error');
  }
}

if (!sessionStorage.getItem(CLAVE_ROL)) {
  window.location.href = RUTA_LOGIN;
} else {
  el.btnAnterior.addEventListener('click', () => {
    if (estado.pagina > 0) {
      estado.pagina -= 1;
      cargar();
    }
  });
  el.btnSiguiente.addEventListener('click', () => {
    if (estado.pagina < estado.totalPaginas - 1) {
      estado.pagina += 1;
      cargar();
    }
  });
  el.btnVolver.addEventListener('click', () => {
    window.location.href = RUTA_MENU;
  });
  cargar();
}
