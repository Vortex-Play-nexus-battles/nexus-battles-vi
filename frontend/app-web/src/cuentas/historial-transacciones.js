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

function renderFilas(registros) {
  el.tbody.innerHTML = '';
  for (const registro of registros) {
    const tr = document.createElement('tr');
    const comprobante = registro.comprobanteUrl
      ? `<a href="${registro.comprobanteUrl}" target="_blank" rel="noopener">Ver</a>`
      : '—';
    tr.innerHTML = `
      <td class="celda-fecha">${formatearFecha(registro.creado)}</td>
      <td>${registro.concepto ?? '—'}</td>
      <td class="celda-monto">${formatearMonto(registro.monto)}</td>
      <td>${registro.moneda ?? '—'}</td>
      <td><span class="${claseResultado(registro.resultado)}">${registro.resultado ?? '—'}</span></td>
      <td>${comprobante}</td>
    `;
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
