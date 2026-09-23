// HU-JUE-012 — Pantalla "Mis cofres" del jugador.
// El interceptor común adjunta el JWT como Authorization: Bearer <token>,
// así que aquí no hace falta leer nexus.token manualmente.

import { fetchWithHttpErrorInterceptor } from '../comun/interceptors/http-error.interceptor.js';
import { estadoDeError, estadoVacio, pintarEstado } from '../comun/ui/estado-vista.js';
import { icono } from '../comun/ui/icono.js';

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
  paginacion: document.querySelector('.paginacion'),
};

/**
 * UX-R4.4 — cuando no hay nada que paginar, el control se va entero.
 *
 * Apagar los dos botones (UX-R3.11) dejaba el problema a medias. En telefono
 * el kit le da a `.paginacion__info` el ancho completo para que el control se
 * apile en vez de desplazarse de lado, asi que la fila del medio existe
 * aunque su texto este vacio: al fallar la carga quedaban dos botones grises
 * separados por un hueco en blanco, sin nada que explicara que hacian ahi.
 * Una lista que no existe no se pagina.
 */
function mostrarPaginacion(visible) {
  if (el.paginacion) {
    el.paginacion.hidden = !visible;
  }
}

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
  el.lista.replaceChildren();
  for (const cofre of cofres) {
    const tarjeta = document.createElement('div');
    tarjeta.className = 'cofre-tarjeta';
    // Etiqueta legible para el placeholder de contenido — el valor real
    // sale de RF-JUE-013 y se ajustará cuando el PO lo defina.
    const contenidoLegible = (cofre.contenido || 'COFRE').replace(/_/g, ' ');
    // UX-R2.8 — `contenidoLegible` sale de `cofre.contenido`, que viene del
    // servidor. Se construye el nodo en vez de interpolarlo en una plantilla.
    //
    // UX-R3.11 dejo esta tarjeta sin icono a proposito: aqui habia un emoji,
    // que lo dibuja el sistema operativo y cambia de forma entre Windows,
    // macOS y Android; y coger prestado «trofeo» o «estrella» habria sido peor,
    // porque ya significan otra cosa en este producto. El hueco quedo anotado.
    //
    // UX-R4.4 lo cierra: el sprite ya trae `cofre`, dibujado sobre la misma
    // reticula y con el mismo trazo que los otros treinta.
    const simbolo = icono('cofre', { etiqueta: null, clase: 'icono cofre-simbolo' });

    const titulo = document.createElement('h3');
    titulo.className = 'cofre-titulo';
    titulo.textContent = contenidoLegible;

    const fecha = document.createElement('p');
    fecha.className = 'cofre-fecha';
    fecha.textContent = `Entregado ${formatearFecha(cofre.entregadoEn)}`;

    const info = document.createElement('div');
    info.className = 'cofre-info';
    info.append(titulo, fecha);

    tarjeta.replaceChildren(simbolo, info);
    el.lista.appendChild(tarjeta);
  }
}

function actualizarPaginacion(pagina, totalPaginas) {
  estado.pagina = pagina;
  estado.totalPaginas = Math.max(totalPaginas, 1);
  mostrarPaginacion(estado.totalPaginas > 1);
  el.paginaActual.textContent = `Página ${estado.pagina + 1} de ${estado.totalPaginas}`;
  el.btnAnterior.disabled = estado.pagina <= 0;
  el.btnSiguiente.disabled = estado.pagina >= estado.totalPaginas - 1;
}

async function cargar() {
  el.lista.replaceChildren();
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
      ocultarEstado();
      pintarEstado(
        el.lista,
        estadoVacio({
          titulo: 'Tus cofres son tuyos',
          detalle: 'Hace falta tu sesión iniciada para verlos.',
          accion: { texto: 'Iniciar sesión', href: RUTA_LOGIN },
        }),
      );
      el.btnAnterior.disabled = true;
      el.btnSiguiente.disabled = true;
      el.paginaActual.textContent = '';
      mostrarPaginacion(false);
      return;
    }
    if (!resp.ok) {
      fallar();
      return;
    }
    const datos = await resp.json();
    const cofres = datos.content ?? datos ?? [];
    const totalPaginas = datos.totalPages ?? 1;
    const paginaActual = datos.number ?? estado.pagina;

    if (cofres.length === 0) {
      ocultarEstado();
      pintarEstado(
        el.lista,
        estadoVacio({
          titulo: 'Todavía no has ganado ningún cofre',
          detalle:
            'Acumula 20 créditos en tus partidas durante una semana y el primero es tuyo. ' +
            'Como máximo, dos por semana.',
          accion: { texto: 'Jugar ahora', href: './index.html' },
        }),
      );
    } else {
      ocultarEstado();
      renderCofres(cofres);
    }
    actualizarPaginacion(paginaActual, totalPaginas);
  } catch {
    fallar();
  }
}

/**
 * UX-R3.11 — el fallo era una pildora roja de una linea, «No se pudo cargar la
 * lista de cofres.», sin decir que hacer y sin manera de volver a intentarlo;
 * y la paginacion se quedaba viva debajo, ofreciendo pasar paginas de una lista
 * que no existe. El fallo de la vista entera es un estado de la vista entera
 * (MAPEO-ERRORES §5.1) y apaga la paginacion.
 */
function fallar() {
  ocultarEstado();
  pintarEstado(
    el.lista,
    estadoDeError({
      titulo: 'No pudimos cargar tus cofres',
      detalle: 'El servicio no respondió. Vuelve a intentarlo en un momento.',
      alReintentar: cargar,
    }),
  );
  el.btnAnterior.disabled = true;
  el.btnSiguiente.disabled = true;
  el.paginaActual.textContent = '';
  mostrarPaginacion(false);
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
