// HU-JUE-012 — Pantalla "Mis cofres" del jugador.
// El interceptor común adjunta el JWT como Authorization: Bearer <token>,
// así que aquí no hace falta leer nexus.token manualmente.

import { fetchWithHttpErrorInterceptor } from '../comun/interceptors/http-error.interceptor.js';

const BASE_API = '/api/v1/cofres/mios';
const TAMANO_PAGINA = 20;
const RUTA_LOGIN = './login.html';
const RUTA_MENU = './index.html';

const CLAVE_ROL = 'nexus.rolActual';

const estado = { pagina: 0, totalPaginas: 1 };

const el = {
  lista: document.getElementById('cofres-lista'),
  estado: document.getElementById('cofres-estado'),
  btnAnterior: document.getElementById('btn-anterior'),
  btnSiguiente: document.getElementById('btn-siguiente'),
  paginaActual: document.getElementById('cofres-pagina-actual'),
  btnVolver: document.getElementById('btn-volver'),
};

const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];

function formatearFecha(iso) {
  if (!iso) {
    return '—';
  }
  const f = new Date(iso);
  if (Number.isNaN(f.getTime())) {
    return iso;
  }
  const dia = f.getDate();
  const mes = MESES[f.getMonth()];
  const horas = String(f.getHours()).padStart(2, '0');
  const minutos = String(f.getMinutes()).padStart(2, '0');
  return `${dia} ${mes} · ${horas}:${minutos}`;
}

function mostrarEstado(texto, tipo) {
  el.estado.hidden = false;
  el.estado.textContent = texto;
  el.estado.classList.remove('carga', 'error', 'vacio');
  if (tipo) {
    el.estado.classList.add(tipo);
  }
}

function ocultarEstado() {
  el.estado.hidden = true;
  el.estado.textContent = '';
}

function renderCofres(cofres) {
  el.lista.innerHTML = '';
  for (const cofre of cofres) {
    const tarjeta = document.createElement('div');
    tarjeta.className = 'cofre-tarjeta';
    // Etiqueta legible para el placeholder de contenido — el valor real
    // sale de RF-JUE-013 y se ajustará cuando el PO lo defina.
    const contenidoLegible = (cofre.contenido || 'COFRE').replace(/_/g, ' ');
    // UX-R2.8 — `contenidoLegible` sale de `cofre.contenido`, que viene del
    // servidor. Se construye el nodo en vez de interpolarlo en una plantilla.
    const icono = document.createElement('div');
    icono.className = 'cofre-icono';
    icono.setAttribute('aria-hidden', 'true');
    icono.textContent = '🎁';

    const titulo = document.createElement('h3');
    titulo.className = 'cofre-titulo';
    titulo.textContent = contenidoLegible;

    const fecha = document.createElement('p');
    fecha.className = 'cofre-fecha';
    fecha.textContent = `Entregado ${formatearFecha(cofre.entregadoEn)}`;

    const info = document.createElement('div');
    info.className = 'cofre-info';
    info.append(titulo, fecha);

    tarjeta.replaceChildren(icono, info);
    el.lista.appendChild(tarjeta);
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
  el.lista.innerHTML = '';
  mostrarEstado('Cargando...', 'carga');

  const params = new URLSearchParams({
    page: String(estado.pagina),
    size: String(TAMANO_PAGINA),
  });

  try {
    const resp = await fetchWithHttpErrorInterceptor(`${BASE_API}?${params.toString()}`, {
      method: 'GET',
    });
    if (resp.status === 403) {
      mostrarEstado('Debes iniciar sesión para ver tus cofres.', 'error');
      el.btnAnterior.disabled = true;
      el.btnSiguiente.disabled = true;
      return;
    }
    if (!resp.ok) {
      mostrarEstado('No se pudo cargar la lista de cofres.', 'error');
      return;
    }
    const datos = await resp.json();
    const cofres = datos.content ?? datos ?? [];
    const totalPaginas = datos.totalPages ?? 1;
    const paginaActual = datos.number ?? estado.pagina;

    if (cofres.length === 0) {
      mostrarEstado(
        'Todavía no has ganado ningún cofre. Acumula 20 créditos en tus partidas para conseguir el primero.',
        'vacio',
      );
    } else {
      ocultarEstado();
      renderCofres(cofres);
    }
    actualizarPaginacion(paginaActual, totalPaginas);
  } catch {
    mostrarEstado('Error de red al consultar los cofres.', 'error');
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
