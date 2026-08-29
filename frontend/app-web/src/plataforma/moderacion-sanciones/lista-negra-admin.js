/**
 * Panel de administración de la lista negra de términos prohibidos (HU-ADM-002).
 * Vanilla JS ES2022, sin framework — consume el servicio moderacion-sanciones.
 */

import { fetchWithHttpErrorInterceptor } from '../../comun/interceptors/http-error.interceptor.js';

const BASE_URL = '/api/v1/lista-negra/terminos';

const elementoCarga = document.getElementById('estado-carga');
const elementoError = document.getElementById('estado-error');
const elementoExito = document.getElementById('estado-exito');
const elementoVacio = document.getElementById('estado-vacio');
const listaTerminos = document.getElementById('lista-terminos');
const formulario = document.getElementById('form-agregar-termino');
const inputTermino = document.getElementById('input-termino');
const botonAgregar = document.getElementById('boton-agregar');

/**
 * Oculta los estados posibles (carga/error/vacío/lista) antes de mostrar uno.
 */
function ocultarEstados() {
  elementoCarga.style.display = 'none';
  elementoError.style.display = 'none';
  elementoVacio.style.display = 'none';
  listaTerminos.style.display = 'none';
}

function mostrarCarga() {
  ocultarEstados();
  elementoCarga.style.display = 'block';
}

/**
 * @param {string} mensaje
 */
function mostrarError(mensaje) {
  ocultarEstados();
  elementoError.textContent = mensaje;
  elementoError.style.display = 'block';
}

/**
 * Mensaje de éxito (CA-01), visible hasta la siguiente acción del usuario.
 * @param {string} mensaje
 */
function mostrarExito(mensaje) {
  elementoExito.textContent = mensaje;
  elementoExito.style.display = 'block';
}

function ocultarExito() {
  elementoExito.style.display = 'none';
}

/**
 * @param {string[]} terminos
 */
function mostrarTerminos(terminos) {
  ocultarEstados();

  if (terminos.length === 0) {
    elementoVacio.style.display = 'block';
    return;
  }

  listaTerminos.innerHTML = '';
  for (const termino of terminos) {
    listaTerminos.appendChild(crearFilaTermino(termino));
  }
  listaTerminos.style.display = 'block';
}

/**
 * @param {string} termino
 * @returns {HTMLLIElement}
 */
function crearFilaTermino(termino) {
  const item = document.createElement('li');

  const texto = document.createElement('span');
  texto.textContent = termino;
  item.appendChild(texto);

  const acciones = document.createElement('div');
  acciones.className = 'acciones-termino';

  const botonEditar = document.createElement('button');
  botonEditar.type = 'button';
  botonEditar.textContent = 'Editar';
  botonEditar.addEventListener('click', () => editarTermino(termino));

  const botonEliminar = document.createElement('button');
  botonEliminar.type = 'button';
  botonEliminar.className = 'boton-eliminar';
  botonEliminar.textContent = 'Eliminar';
  botonEliminar.addEventListener('click', () => eliminarTermino(termino));

  acciones.appendChild(botonEditar);
  acciones.appendChild(botonEliminar);
  item.appendChild(acciones);

  return item;
}

async function cargarTerminos() {
  mostrarCarga();
  try {
    const respuesta = await fetchWithHttpErrorInterceptor(BASE_URL);
    if (!respuesta.ok) {
      mostrarError('No se pudo cargar la lista negra. Intenta de nuevo.');
      return;
    }
    const terminos = await respuesta.json();
    mostrarTerminos(terminos);
  } catch {
    mostrarError('No se pudo conectar con el servicio de moderación.');
  }
}

/**
 * @param {SubmitEvent} evento
 */
async function agregarTermino(evento) {
  evento.preventDefault();
  ocultarExito();
  const termino = inputTermino.value.trim();
  if (!termino) {
    return;
  }

  botonAgregar.disabled = true;
  try {
    const respuesta = await fetchWithHttpErrorInterceptor(BASE_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ termino }),
    });

    if (!respuesta.ok) {
      mostrarError('No se pudo agregar el término.');
      return;
    }

    // eslint-disable-next-line require-atomic-updates -- inputTermino es un nodo DOM fijo, no estado async compartido
    inputTermino.value = '';
    await cargarTerminos();
    mostrarExito(`Término "${termino}" agregado correctamente.`);
  } catch {
    mostrarError('No se pudo conectar con el servicio de moderación.');
  } finally {
    botonAgregar.disabled = false;
  }
}

/**
 * @param {string} terminoActual
 */
async function editarTermino(terminoActual) {
  ocultarExito();
  const terminoNuevo = window.prompt('Editar término:', terminoActual);
  if (!terminoNuevo || !terminoNuevo.trim() || terminoNuevo.trim() === terminoActual) {
    return;
  }

  try {
    const respuesta = await fetchWithHttpErrorInterceptor(
      `${BASE_URL}/${encodeURIComponent(terminoActual)}`,
      {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ termino: terminoNuevo.trim() }),
      },
    );

    if (!respuesta.ok) {
      mostrarError('No se pudo editar el término.');
      return;
    }

    await cargarTerminos();
    mostrarExito(`Término actualizado a "${terminoNuevo.trim()}".`);
  } catch {
    mostrarError('No se pudo conectar con el servicio de moderación.');
  }
}

/**
 * @param {string} termino
 */
async function eliminarTermino(termino) {
  ocultarExito();
  const confirmado = window.confirm(`¿Eliminar el término "${termino}" de la lista negra?`);
  if (!confirmado) {
    return;
  }

  try {
    const respuesta = await fetchWithHttpErrorInterceptor(
      `${BASE_URL}/${encodeURIComponent(termino)}`,
      { method: 'DELETE' },
    );

    if (!respuesta.ok) {
      mostrarError('No se pudo eliminar el término.');
      return;
    }

    await cargarTerminos();
    mostrarExito(`Término "${termino}" eliminado correctamente.`);
  } catch {
    mostrarError('No se pudo conectar con el servicio de moderación.');
  }
}

/**
 * CA-03: un usuario sin permisos que intente entrar a esta ruta debe ser
 * redirigido a la pantalla principal. El interceptor compartido (comun/) ya
 * dispara este evento en cada 403 -- no hace falta tocar ese archivo, solo
 * escucharlo desde esta vista.
 */
window.addEventListener('nexus:rbac-forbidden', () => {
  window.location.href = '/';
});

formulario.addEventListener('submit', agregarTermino);
cargarTerminos();
