import {
  setCurrentRole,
  getCurrentRole,
  checkPermission,
  setPermissionMatrix,
} from './directives/has-permission.directive.js';

import { fetchWithHttpErrorInterceptor } from '../comun/interceptors/http-error.interceptor.js';
import { montarCabecera } from '../comun/cabecera-app.js';
import { cambiarRol, ROLES_DISPONIBLES } from './cambio-rol.js';

const BASE_API = '/api/v1/admin/usuarios';
const MATRIZ_RBAC_API = '/api/v1/rbac/matrix';

const CLAVE_ROL = 'nexus.rolActual';
const CLAVE_USUARIO_ID = 'nexus.usuarioId';
const CLAVE_TOKEN = 'nexus.token';

const PERMISO_GESTIONAR = 'GESTIONAR_CUENTAS';
const PERMISO_SUSPENDER = 'SUSPENDER_USUARIOS';
const PERMISO_BANEAR = 'BANEAR_DEFINITIVAMENTE';
const PERMISO_ASIGNAR_ROL = 'ASIGNAR_ROL';

let usuarioSeleccionado = null;

document.addEventListener('DOMContentLoaded', iniciar);

function montarBarraNavegacion() {
  // Cabecera unica de la aplicacion (HU-UX-001): la sesion la lee ella del login.
  const contenedor = document.createElement('div');
  contenedor.dataset.cabeceraApp = '';
  document.body.prepend(contenedor);
  montarCabecera(contenedor, { vista: 'gestion-usuarios', seccionActiva: 'usuarios' });
}

async function iniciar() {
  montarBarraNavegacion();

  const rolActual = sessionStorage.getItem(CLAVE_ROL) || 'JUGADOR';

  setCurrentRole(rolActual);

  configurarSelectorRoles();

  configurarEventos();

  await cargarMatrizYVerificarAcceso();
}

export async function cargarMatrizYVerificarAcceso({
  fetchImpl = fetchWithHttpErrorInterceptor,
} = {}) {
  const token = sessionStorage.getItem(CLAVE_TOKEN);
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  let seCargo = false;

  try {
    const respuesta = await fetchImpl(MATRIZ_RBAC_API, { headers });

    if (!respuesta.ok) {
      throw new Error(`Error HTTP ${respuesta.status}`);
    }

    const payload = await respuesta.json();
    const matriz = payload?.matrix;

    if (!matriz || typeof matriz !== 'object' || Object.keys(matriz).length === 0) {
      throw new Error('La matriz RBAC recibida no es válida.');
    }

    setPermissionMatrix(matriz);
    seCargo = true;
  } catch (error) {
    console.error('No fue posible cargar la matriz RBAC:', error);
    setPermissionMatrix({});
  }

  verificarAcceso({ matrizDisponible: seCargo });
}

/**
 * Engancha la pantalla. Exportada para que las pruebas puedan comprobar que el
 * formulario de busqueda **esta realmente atado** a algo que llama al
 * servicio: un manejador que existe pero no se registra es indistinguible de
 * un boton muerto para quien lo pulsa.
 */
export function configurarEventos() {
  const formularioBusqueda = document.getElementById('form-buscar-usuario');

  const formularioPerfil = document.getElementById('formulario-perfil-admin');

  const btnVolver = document.getElementById('btn-volver');

  const btnVolverAcceso = document.getElementById('btn-volver-acceso');

  const btnSuspender = document.getElementById('btn-suspender');

  const btnReactivar = document.getElementById('btn-reactivar');

  const btnBanear = document.getElementById('btn-banear');

  const btnRestablecerPassword = document.getElementById('btn-restablecer-password');

  const btnCambiarRol = document.getElementById('btn-cambiar-rol');

  if (formularioBusqueda) {
    formularioBusqueda.addEventListener('submit', buscarUsuario);
  }

  if (formularioPerfil) {
    formularioPerfil.addEventListener('submit', guardarPerfil);
  }

  if (btnVolver) {
    btnVolver.addEventListener('click', volverInicio);
  }

  const btnReintentarBloqueo = document.querySelector('[data-zona="reintentar-bloqueo"]');
  if (btnReintentarBloqueo) {
    // Volver a pedir la matriz, no recargar la pagina: recargar perdería lo
    // que el administrador tuviera escrito en el buscador.
    btnReintentarBloqueo.addEventListener('click', () => {
      cargarMatrizYVerificarAcceso();
    });
  }

  if (btnVolverAcceso) {
    btnVolverAcceso.addEventListener('click', volverInicio);
  }

  if (btnSuspender) {
    btnSuspender.addEventListener('click', suspenderUsuario);
  }

  if (btnReactivar) {
    btnReactivar.addEventListener('click', reactivarUsuario);
  }

  if (btnBanear) {
    btnBanear.addEventListener('click', banearUsuario);
  }

  if (btnRestablecerPassword) {
    btnRestablecerPassword.addEventListener('click', restablecerPassword);
  }

  if (btnCambiarRol) {
    btnCambiarRol.addEventListener('click', cambiarRolUsuarioSeleccionado);
  }
}

function configurarSelectorRoles() {
  const selector = document.getElementById('nuevo-rol');

  if (!selector) {
    return;
  }

  selector.replaceChildren();

  for (const rol of ROLES_DISPONIBLES) {
    const opcion = document.createElement('option');
    opcion.value = rol;
    opcion.textContent = rol;
    selector.append(opcion);
  }
}

/**
 * Deja ver la gestión, o dice por qué no — UX-R3.3.
 *
 * ## Los dos «no» que antes eran el mismo
 *
 * El acceso se decide contra la matriz que publica el servidor
 * (`/api/v1/rbac/matrix`), y la UI falla cerrada: sin matriz, nadie pasa. Eso
 * está bien. Lo que estaba mal era **lo que se le decía a la persona**.
 *
 * Cuando `ms-identidad` no contestaba —en dev no corre—, un administrador
 * legítimo leía «No tienes permisos suficientes para gestionar cuentas». Es
 * falso y además es la clase de mensaje que hace perder una tarde: quien lo
 * lee va a pedir que le revisen el rol, no a mirar si el servicio está caído.
 *
 * Ahora son dos estados distintos, como pide §17:
 *
 *   - matriz cargada y el rol no alcanza → «No tienes acceso a esta sección.»
 *   - matriz sin cargar                  → «Esta función no está disponible
 *                                           temporalmente.» + Reintentar
 *
 * @param {{matrizDisponible?: boolean}} [opciones]
 */
function verificarAcceso({ matrizDisponible = true } = {}) {
  const contenedor = document.getElementById('gestion-contenedor');

  const accesoDenegado = document.getElementById('acceso-denegado');

  if (!contenedor || !accesoDenegado) {
    return;
  }

  const tienePermiso = matrizDisponible && checkPermission(getCurrentRole(), PERMISO_GESTIONAR);

  if (!tienePermiso) {
    contenedor.hidden = true;

    accesoDenegado.hidden = false;

    pintarMotivoDeBloqueo(accesoDenegado, matrizDisponible);

    return;
  }

  contenedor.hidden = false;

  accesoDenegado.hidden = true;
}

/**
 * @param {HTMLElement} seccion
 * @param {boolean} matrizDisponible
 */
function pintarMotivoDeBloqueo(seccion, matrizDisponible) {
  const titulo = seccion.querySelector('[data-zona="titulo-bloqueo"]');
  const detalle = seccion.querySelector('[data-zona="detalle-bloqueo"]');
  const reintentar = seccion.querySelector('[data-zona="reintentar-bloqueo"]');

  if (titulo) {
    titulo.textContent = matrizDisponible
      ? 'No tienes acceso a esta sección.'
      : 'Esta función no está disponible temporalmente.';
  }
  if (detalle) {
    detalle.textContent = matrizDisponible
      ? 'Tu cuenta no tiene habilitada la gestión de cuentas.'
      : 'No pudimos comprobar tus permisos porque el servicio de identidad no responde. No es un problema de tu cuenta.';
  }
  if (reintentar) {
    reintentar.hidden = matrizDisponible;
  }
}

/**
 * Busca de verdad — FI-R3.
 *
 * Lo que habia aqui: el boton guardaba el ID en `sessionStorage`, abria el
 * panel con todos los campos en «-» y escribia «La consulta de sus datos
 * quedara conectada cuando el backend exponga el endpoint administrativo de
 * consulta». Ese endpoint **existe desde hace tiempo**:
 * `AdminGestionUsuarioController` tiene `@GetMapping("/{usuarioId}")` con
 * `@RequirePermission(Action.GESTIONAR_CUENTAS)`, y devuelve un
 * `AdminUsuarioResumenResponse` completo. El comentario del codigo decia lo
 * contrario y nadie volvio a mirarlo.
 *
 * Peor que no traer los datos: el panel se abria igual y las acciones de
 * suspender, banear y restablecer quedaban habilitadas sobre un ID que nadie
 * habia comprobado que existiera. Un digito de mas en el buscador y el
 * administrador estaba a un clic de banear a otra persona sin haber visto ni
 * su apodo.
 *
 * Ahora el panel solo se abre si el usuario existe, y cada desenlace se dice
 * distinto: no existe, no tienes permiso, tu sesion caduco, o el servicio no
 * responde. Los cuatro se confundian en uno.
 *
 * @param {Event} evento
 */
async function buscarUsuario(evento) {
  evento.preventDefault();

  limpiarMensajeBusqueda();

  const input = document.getElementById('usuario-id');

  if (!input) {
    return;
  }

  const usuarioId = input.value.trim();

  if (!usuarioId || !Number.isInteger(Number(usuarioId)) || Number(usuarioId) <= 0) {
    mostrarMensajeBusqueda('Debes ingresar un ID de usuario válido.');

    return;
  }

  const boton = document.getElementById('btn-buscar');
  cambiarEstadoBoton(boton, true, 'Buscando...');

  try {
    const respuesta = await fetchWithHttpErrorInterceptor(`${BASE_API}/${Number(usuarioId)}`);

    if (!respuesta.ok) {
      ocultarPanelUsuario();
      mostrarMensajeBusqueda(await motivoDeBusquedaFallida(respuesta, usuarioId));
      return;
    }

    const datos = await respuesta.json();

    usuarioSeleccionado = { id: Number(datos.id ?? usuarioId) };
    sessionStorage.setItem(CLAVE_USUARIO_ID, String(usuarioSeleccionado.id));

    mostrarPanelUsuario();
    pintarUsuario(datos);
  } catch (error) {
    // Aqui solo llega un fallo de red: el interceptor devuelve la respuesta
    // para cualquier codigo HTTP y solo relanza si `fetch` no llego a
    // responder. Un servicio caido no es un usuario inexistente.
    console.error('Error consultando el usuario:', error);
    ocultarPanelUsuario();
    mostrarMensajeBusqueda(
      'No se pudo consultar el usuario: el servicio de identidad no responde. Vuelve a intentarlo.',
    );
  } finally {
    cambiarEstadoBoton(boton, false, 'Buscar');
  }
}

/**
 * Por que fallo la busqueda, dicho de forma que sirva para actuar.
 *
 * Un 404 y un 403 llevan a cosas distintas: el primero es «ese usuario no
 * esta», el segundo es «tu cuenta no puede mirarlo». Juntarlos en «no se pudo
 * buscar» manda al administrador a revisar lo que no es.
 *
 * @param {Response} respuesta
 * @param {string} usuarioId
 * @returns {Promise<string>}
 */
async function motivoDeBusquedaFallida(respuesta, usuarioId) {
  if (respuesta.status === 404) {
    return `No existe ningún usuario con el ID ${usuarioId}.`;
  }
  if (respuesta.status === 403) {
    // El interceptor ya ensena el aviso de RBAC; aqui se explica en el sitio
    // donde la persona estaba mirando.
    return 'Tu cuenta no tiene permiso para consultar usuarios.';
  }
  if (respuesta.status === 401) {
    return 'Tu sesión caducó. Vuelve a iniciar sesión para gestionar cuentas.';
  }
  return await obtenerMensajeError(respuesta);
}

/**
 * Vuelca `AdminUsuarioResumenResponse` en el panel y en el formulario.
 *
 * Un campo que el servidor no trae se ensena como «-», no como cadena vacia
 * ni como «undefined»: el resumen es de solo lectura y «-» ya significa «sin
 * dato» en esta pantalla.
 *
 * @param {object} datos
 */
function pintarUsuario(datos) {
  establecerTexto('usuario-id-mostrado', datos.id ?? '-');
  establecerTexto('usuario-apodo-mostrado', datos.apodo ?? '-');
  establecerTexto('usuario-email-mostrado', datos.email ?? '-');
  establecerTexto('usuario-rol-mostrado', datos.rolNombre ?? '-');
  establecerTexto('usuario-estado-mostrado', datos.estado ?? '-');

  establecerValor('nombres', datos.nombres ?? '');
  establecerValor('apellidos', datos.apellidos ?? '');
  establecerValor('apodo', datos.apodo ?? '');
  establecerValor('preferencias', datos.preferencias ?? '');
  // `avatar` es un `<input type="file">`: su valor no se puede fijar desde
  // JavaScript y ademas el servidor devuelve una ruta, no un archivo.
  establecerValor('estado', datos.estado ?? 'ACTIVO');
  establecerValor('suspendido-hasta', '');

  // El selector de rol arranca en el rol que el usuario tiene ahora, no en el
  // primero de la lista: dejarlo en JUGADOR invitaba a degradar a un
  // administrador de un solo clic.
  const rol = typeof datos.rolNombre === 'string' ? datos.rolNombre : null;
  establecerValor('nuevo-rol', ROLES_DISPONIBLES.includes(rol) ? rol : ROLES_DISPONIBLES[0]);

  limpiarMensajeCambioRol();
  limpiarMensajePerfil();
}

function ocultarPanelUsuario() {
  usuarioSeleccionado = null;
  const panel = document.getElementById('panel-usuario');
  if (panel) {
    panel.hidden = true;
  }
}

function mostrarPanelUsuario() {
  const panel = document.getElementById('panel-usuario');

  if (!panel) {
    return;
  }

  panel.hidden = false;
}

async function cambiarRolUsuarioSeleccionado() {
  limpiarMensajeCambioRol();

  if (!validarUsuarioSeleccionado()) {
    mostrarMensajeCambioRol('Primero debes seleccionar un usuario.');

    return;
  }

  if (!checkPermission(getCurrentRole(), PERMISO_ASIGNAR_ROL)) {
    mostrarMensajeCambioRol('No tienes permiso para cambiar roles.');

    return;
  }

  const nuevoRol = obtenerValor('nuevo-rol');
  const boton = document.getElementById('btn-cambiar-rol');

  cambiarEstadoBoton(boton, true, 'Cambiando rol...');

  try {
    await cambiarRol(usuarioSeleccionado.id, nuevoRol);

    establecerTexto('usuario-rol-mostrado', nuevoRol);

    mostrarMensajeCambioRol(`Rol actualizado correctamente a ${nuevoRol}.`);
  } catch (error) {
    console.error('Error cambiando rol:', error);

    mostrarMensajeCambioRol(error.message || 'No fue posible cambiar el rol del usuario.');
  } finally {
    cambiarEstadoBoton(boton, false, 'Cambiar rol');
  }
}

async function guardarPerfil(evento) {
  evento.preventDefault();

  if (!validarUsuarioSeleccionado()) {
    return;
  }

  if (!checkPermission(getCurrentRole(), PERMISO_GESTIONAR)) {
    mostrarMensajePerfil('No tienes permisos para modificar cuentas.');

    return;
  }

  const nombres = obtenerValor('nombres');

  const apellidos = obtenerValor('apellidos');

  const apodo = obtenerValor('apodo');

  const archivoAvatar = document.getElementById('avatar')?.files?.[0];

  const preferencias = obtenerValor('preferencias');

  if (!nombres) {
    mostrarMensajePerfil('Los nombres son obligatorios.');

    return;
  }

  if (!apellidos) {
    mostrarMensajePerfil('Los apellidos son obligatorios.');

    return;
  }

  if (!apodo) {
    mostrarMensajePerfil('El apodo es obligatorio.');

    return;
  }

  const confirmado = window.confirm(
    `¿Deseas guardar los cambios del usuario ${usuarioSeleccionado.id}?`,
  );

  if (!confirmado) {
    return;
  }

  const boton = document.getElementById('btn-guardar-perfil');

  cambiarEstadoBoton(boton, true, 'Guardando...');

  try {
    const cuerpoFormData = new FormData();
    cuerpoFormData.append('nombres', nombres);
    cuerpoFormData.append('apellidos', apellidos);
    cuerpoFormData.append('apodo', apodo);
    cuerpoFormData.append('preferencias', preferencias);
    if (archivoAvatar) {
      cuerpoFormData.append('avatar', archivoAvatar);
    }

    // No se pone Content-Type a mano: el navegador arma el multipart/form-data solo.
    const respuesta = await fetchWithHttpErrorInterceptor(
      `${BASE_API}/${usuarioSeleccionado.id}/perfil`,
      {
        method: 'PUT',
        body: cuerpoFormData,
      },
    );

    if (!respuesta.ok) {
      throw new Error(await obtenerMensajeError(respuesta));
    }

    mostrarMensajePerfil('Perfil actualizado correctamente.');

    actualizarResumenUsuario({
      apodo,
    });
  } catch (error) {
    console.error('Error actualizando perfil:', error);

    mostrarMensajePerfil(error.message || 'No fue posible actualizar el perfil.');
  } finally {
    cambiarEstadoBoton(boton, false, 'Guardar cambios');
  }
}

async function suspenderUsuario() {
  if (!validarUsuarioSeleccionado()) {
    return;
  }

  if (!checkPermission(getCurrentRole(), PERMISO_SUSPENDER)) {
    mostrarMensajePerfil('No tienes permisos para suspender usuarios.');

    return;
  }

  const suspendidoHasta = obtenerValor('suspendido-hasta');

  if (!suspendidoHasta) {
    mostrarMensajePerfil('Debes indicar hasta cuándo estará suspendida la cuenta.');

    return;
  }

  const confirmado = window.confirm(`¿Deseas suspender al usuario ${usuarioSeleccionado.id}?`);

  if (!confirmado) {
    return;
  }

  await ejecutarAccionEstado(`/suspender`, 'SUSPENDIDO', 'Cuenta suspendida correctamente.');
}

async function reactivarUsuario() {
  if (!validarUsuarioSeleccionado()) {
    return;
  }

  if (!checkPermission(getCurrentRole(), PERMISO_SUSPENDER)) {
    mostrarMensajePerfil('No tienes permisos para reactivar usuarios.');

    return;
  }

  const confirmado = window.confirm(`¿Deseas reactivar al usuario ${usuarioSeleccionado.id}?`);

  if (!confirmado) {
    return;
  }

  await ejecutarAccionEstado(`/reactivar`, 'ACTIVO', 'Cuenta reactivada correctamente.');
}

async function banearUsuario() {
  if (!validarUsuarioSeleccionado()) {
    return;
  }

  if (!checkPermission(getCurrentRole(), PERMISO_BANEAR)) {
    mostrarMensajePerfil('No tienes permisos para banear definitivamente a este usuario.');

    return;
  }

  const confirmado = window.confirm(
    `Esta acción es permanente. ¿Deseas banear definitivamente al usuario ${usuarioSeleccionado.id}?`,
  );

  if (!confirmado) {
    return;
  }

  await ejecutarAccionEstado(`/banear`, 'BANEADO', 'Cuenta baneada definitivamente.');
}

async function ejecutarAccionEstado(ruta, estado, mensajeExito) {
  try {
    const respuesta = await fetchWithHttpErrorInterceptor(
      `${BASE_API}/${usuarioSeleccionado.id}${ruta}`,
      {
        method: 'PUT',
      },
    );

    if (!respuesta.ok) {
      throw new Error(await obtenerMensajeError(respuesta));
    }

    establecerValor('estado', estado);

    establecerTexto('usuario-estado-mostrado', estado);

    mostrarMensajePerfil(mensajeExito);
  } catch (error) {
    console.error('Error modificando estado:', error);

    mostrarMensajePerfil(error.message || 'No fue posible modificar el estado de la cuenta.');
  }
}

async function restablecerPassword() {
  if (!validarUsuarioSeleccionado()) {
    return;
  }

  if (!checkPermission(getCurrentRole(), PERMISO_GESTIONAR)) {
    mostrarMensajePerfil('No tienes permisos para restablecer contraseñas.');

    return;
  }

  const confirmado = window.confirm(
    `¿Deseas restablecer la contraseña del usuario ${usuarioSeleccionado.id}? El usuario deberá completar el mecanismo seguro de restablecimiento.`,
  );

  if (!confirmado) {
    return;
  }

  try {
    const respuesta = await fetchWithHttpErrorInterceptor(
      `${BASE_API}/${usuarioSeleccionado.id}/restablecer-password`,
      {
        method: 'POST',
      },
    );

    if (!respuesta.ok) {
      throw new Error(await obtenerMensajeError(respuesta));
    }

    mostrarMensajePerfil('El restablecimiento de contraseña fue solicitado correctamente.');
  } catch (error) {
    console.error('Error restableciendo contraseña:', error);

    mostrarMensajePerfil(error.message || 'No fue posible restablecer la contraseña.');
  }
}

function validarUsuarioSeleccionado() {
  if (!usuarioSeleccionado?.id) {
    mostrarMensajePerfil('Primero debes seleccionar un usuario.');

    return false;
  }

  return true;
}

function actualizarResumenUsuario(datos) {
  if (datos.apodo !== undefined) {
    establecerTexto('usuario-apodo-mostrado', datos.apodo);
  }
}

function obtenerValor(id) {
  const elemento = document.getElementById(id);

  return elemento ? elemento.value.trim() : '';
}

function establecerValor(id, valor) {
  const elemento = document.getElementById(id);

  if (elemento) {
    elemento.value = valor ?? '';
  }
}

function establecerTexto(id, texto) {
  const elemento = document.getElementById(id);

  if (elemento) {
    elemento.textContent = String(texto ?? '-');
  }
}

function cambiarEstadoBoton(boton, deshabilitado, texto) {
  if (!boton) {
    return;
  }

  boton.disabled = deshabilitado;

  boton.textContent = texto;
}

function mostrarMensajeBusqueda(mensaje) {
  const elemento = document.getElementById('mensaje-busqueda');

  if (!elemento) {
    return;
  }

  elemento.textContent = mensaje;

  elemento.hidden = false;
}

function limpiarMensajeBusqueda() {
  const elemento = document.getElementById('mensaje-busqueda');

  if (!elemento) {
    return;
  }

  elemento.textContent = '';

  elemento.hidden = true;
}

function limpiarMensajePerfil() {
  const elemento = document.getElementById('mensaje-perfil');

  if (!elemento) {
    return;
  }

  elemento.textContent = '';
  elemento.hidden = true;
}

function mostrarMensajePerfil(mensaje) {
  const elemento = document.getElementById('mensaje-perfil');

  if (!elemento) {
    return;
  }

  elemento.textContent = mensaje;

  elemento.hidden = false;
}

function mostrarMensajeCambioRol(mensaje) {
  const elemento = document.getElementById('mensaje-cambio-rol');

  if (!elemento) {
    return;
  }

  elemento.textContent = mensaje;
  elemento.hidden = false;
}

function limpiarMensajeCambioRol() {
  const elemento = document.getElementById('mensaje-cambio-rol');

  if (!elemento) {
    return;
  }

  elemento.textContent = '';
  elemento.hidden = true;
}

async function obtenerMensajeError(respuesta) {
  try {
    const datos = await respuesta.clone().json();

    if (typeof datos === 'string') {
      return datos;
    }

    return datos.detail || datos.message || datos.title || `Error HTTP ${respuesta.status}`;
  } catch {
    try {
      const texto = await respuesta.clone().text();

      if (texto) {
        return texto;
      }
    } catch {
      // Se utiliza el mensaje genérico.
    }

    return `Error HTTP ${respuesta.status}`;
  }
}

function volverInicio() {
  // El menú principal (index.html) vive en la MISMA carpeta que esta página
  // (frontend/app-web/src/cuentas/), no un nivel arriba: '../index.html' no
  // existe y devolvía 404 al usuario que hacía clic en Volver.
  window.location.href = './index.html';
}
