import {
  setCurrentRole,
  getCurrentRole,
  checkPermission,
  setPermissionMatrix,
  applyHasPermissionDirective,
} from './directives/has-permission.directive.js';

import { fetchWithHttpErrorInterceptor } from '../comun/interceptors/http-error.interceptor.js';
import { montarCabecera } from '../comun/cabecera-app.js';
import { h, vaciar } from '../comun/ui/dom.js';
import { distintivo } from '../comun/ui/distintivo.js';
import { confirmar } from '../comun/ui/dialogo.js';

const BASE_API = '/api/v1/admin/cuentas';
const MATRIZ_RBAC_API = '/api/v1/rbac/matrix';
const CLAVE_TOKEN = 'nexus.token';

const CLAVE_ROL = 'nexus.rolActual';

const PERMISO_CREAR = 'CREAR_ADMIN_MODERADOR';

const ROLES_PERMITIDOS = ['MODERADOR', 'ADMINISTRADOR'];

const PERMISOS_POR_ROL = {
  MODERADOR: ['GESTIONAR_CUENTAS', 'SUSPENDER_USUARIOS'],

  ADMINISTRADOR: ['GESTIONAR_CUENTAS', 'SUSPENDER_USUARIOS', 'BANEAR_DEFINITIVAMENTE'],
};

document.addEventListener('DOMContentLoaded', iniciar);

function montarBarraNavegacion() {
  // Cabecera unica de la aplicacion (HU-UX-001): la sesion la lee ella del
  // login. El hueco ya viene en el HTML; si falta (una prueba que monta solo
  // el formulario), se crea para no quedarse sin navegacion.
  let contenedor = document.querySelector('[data-cabecera-app]');
  if (!contenedor) {
    contenedor = document.createElement('div');
    contenedor.dataset.cabeceraApp = '';
    document.body.prepend(contenedor);
  }
  montarCabecera(contenedor, { vista: 'crear-cuenta-admin', seccionActiva: 'usuarios' });
}

function iniciar() {
  montarBarraNavegacion();

  const rolGuardado = sessionStorage.getItem(CLAVE_ROL) || 'JUGADOR';

  setCurrentRole(rolGuardado);

  aplicarDirectivas();

  configurarEventos();

  cargarMatrizYVerificarAcceso();

  actualizarPermisosVisuales();
}

/**
 * UX-R3.11 — esta vista NO cargaba la matriz RBAC.
 *
 * `checkPermission` lee `permissionMatrix`, que empieza vacia y se llena desde
 * `GET /api/v1/rbac/matrix`. Aqui nadie la pedia, asi que la comprobacion caia
 * siempre del lado cerrado. Y ademas se llamaba con UN argumento
 * -`checkPermission(PERMISO_CREAR)`- cuando la firma es `(rol, accion)`: el
 * permiso entraba como rol y la accion llegaba `undefined`.
 *
 * Las dos cosas juntas significan que **nadie podia crear una cuenta
 * administrativa desde esta pantalla**, tampoco un super administrador: la
 * unica vista que cumple RF-RBAC-003 estaba muerta para todos los roles. Se vio
 * en el barrido: la captura con persona SUPER_ADMINISTRADOR devolvia «Acceso
 * restringido».
 */
export async function cargarMatrizYVerificarAcceso({
  fetchImpl = fetchWithHttpErrorInterceptor,
} = {}) {
  const token = sessionStorage.getItem(CLAVE_TOKEN);
  const cabeceras = token ? { Authorization: `Bearer ${token}` } : {};
  let seCargo = false;

  try {
    const respuesta = await fetchImpl(MATRIZ_RBAC_API, { headers: cabeceras });
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

function aplicarDirectivas() {
  applyHasPermissionDirective();
}

function configurarEventos() {
  const formulario = document.getElementById('form-crear-cuenta');

  const selectorRol = document.getElementById('rol');

  const btnVolverHeader = document.getElementById('btn-volver-header');

  const btnCancelarAcceso = document.getElementById('btn-cancelar-acceso');

  const btnCancelar = document.getElementById('btn-cancelar');

  const btnNuevaCuenta = document.getElementById('btn-nueva-cuenta');

  const btnVolverResultado = document.getElementById('btn-volver-resultado');

  if (formulario) {
    formulario.addEventListener('submit', manejarCreacionCuenta);
  }

  if (selectorRol) {
    selectorRol.addEventListener('change', actualizarPermisosVisuales);
  }

  if (btnVolverHeader) {
    btnVolverHeader.addEventListener('click', volverInicio);
  }

  if (btnCancelarAcceso) {
    btnCancelarAcceso.addEventListener('click', volverInicio);
  }

  if (btnCancelar) {
    btnCancelar.addEventListener('click', limpiarFormulario);
  }

  if (btnNuevaCuenta) {
    btnNuevaCuenta.addEventListener('click', mostrarFormularioNuevaCuenta);
  }

  if (btnVolverResultado) {
    btnVolverResultado.addEventListener('click', volverInicio);
  }
}

function verificarAcceso({ matrizDisponible = true } = {}) {
  const accesoDenegado = document.getElementById('acceso-denegado');

  const formularioContenedor = document.getElementById('formulario-contenedor');

  if (!accesoDenegado || !formularioContenedor) {
    return;
  }

  const tienePermiso = matrizDisponible && checkPermission(getCurrentRole(), PERMISO_CREAR);

  if (!tienePermiso) {
    accesoDenegado.hidden = false;

    formularioContenedor.hidden = true;

    // Un servicio caido y un rol insuficiente no son lo mismo, y confundirlos
    // manda a alguien a pedir que le revisen el rol en vez de a mirar si el
    // servicio responde (la misma correccion que en gestion-usuarios).
    pintarMotivoDeBloqueo(accesoDenegado, matrizDisponible);

    return;
  }

  accesoDenegado.hidden = true;

  formularioContenedor.hidden = false;
}

/**
 * @param {HTMLElement} seccion
 * @param {boolean} matrizDisponible
 */
function pintarMotivoDeBloqueo(seccion, matrizDisponible) {
  const titulo = seccion.querySelector('[data-zona="titulo-bloqueo"]');
  const detalle = seccion.querySelector('[data-zona="detalle-bloqueo"]');
  if (titulo) {
    titulo.textContent = matrizDisponible
      ? 'No tienes acceso a esta sección.'
      : 'Esta función no está disponible temporalmente.';
  }
  if (detalle) {
    detalle.textContent = matrizDisponible
      ? 'Crear cuentas administrativas es del super administrador.'
      : 'No pudimos comprobar tus permisos porque el servicio de identidad no responde. ' +
        'No es un problema de tu cuenta.';
  }
}

function actualizarPermisosVisuales() {
  const selectorRol = document.getElementById('rol');

  const contenedorPermisos = document.getElementById('permisos');

  if (!selectorRol || !contenedorPermisos) {
    return;
  }

  const rolSeleccionado = selectorRol.value;

  vaciar(contenedorPermisos);

  if (!rolSeleccionado || !PERMISOS_POR_ROL[rolSeleccionado]) {
    contenedorPermisos.append(
      h('p', { clase: 't-meta', texto: 'Selecciona un rol para consultar sus permisos.' }),
    );

    return;
  }

  for (const permiso of PERMISOS_POR_ROL[rolSeleccionado]) {
    contenedorPermisos.append(distintivo(formatearPermiso(permiso)));
  }
}

function formatearPermiso(permiso) {
  return permiso
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/\b\w/g, (letra) => letra.toUpperCase());
}

async function manejarCreacionCuenta(evento) {
  evento.preventDefault();

  const rolActual = getCurrentRole();

  const tienePermiso = checkPermission(rolActual, PERMISO_CREAR);

  if (!tienePermiso) {
    mostrarError('No tienes permisos para crear cuentas administrativas.');

    return;
  }

  const nombres = document.getElementById('nombres')?.value.trim();

  const apellidos = document.getElementById('apellidos')?.value.trim();

  const apodo = document.getElementById('apodo')?.value.trim();

  const email = document.getElementById('email')?.value.trim();

  const password = document.getElementById('password')?.value;

  const rol = document.getElementById('rol')?.value;

  const archivoAvatar = document.getElementById('avatar')?.files?.[0];

  const error = validarDatos(nombres, apellidos, apodo, email, password, rol);

  if (error) {
    mostrarError(error);

    return;
  }

  const confirmado = await confirmar({
    titulo: 'Crear cuenta administrativa',
    mensaje: `Se creará la cuenta "${apodo}" con rol ${rol}. Podrás cambiarle el rol después, pero la cuenta no se puede borrar desde esta vista.`,
    textoConfirmar: 'Crear cuenta',
    peligro: false,
  });

  if (!confirmado) {
    return;
  }

  ocultarMensajes();

  cambiarEstadoBoton(true);

  try {
    const cuerpo = new FormData();
    cuerpo.append('nombres', nombres);
    cuerpo.append('apellidos', apellidos);
    cuerpo.append('email', email);
    cuerpo.append('password', password);
    cuerpo.append('apodo', apodo);
    cuerpo.append('rolNombre', rol);

    // El avatar es opcional al crear una cuenta admin, igual que en el registro normal.
    if (archivoAvatar) {
      cuerpo.append('avatar', archivoAvatar);
    }

    // No se pone Content-Type a mano: el navegador arma el multipart/form-data solo.
    const respuesta = await fetchWithHttpErrorInterceptor(BASE_API, {
      method: 'POST',
      body: cuerpo,
    });

    if (!respuesta.ok) {
      const mensaje = await obtenerMensajeError(respuesta);

      throw new Error(mensaje);
    }

    const usuarioCreado = await obtenerJsonSeguro(respuesta);

    mostrarResultado(usuarioCreado, apodo, rol);
  } catch (fallo) {
    // Se llama 'fallo' y no 'error' porque en la linea 311 ya hay un
    // 'error' con el mensaje de validacion. Con los dos llamados igual,
    // dentro de este bloque no habia forma de leer el de fuera.
    console.error('Error creando cuenta administrativa:', fallo);

    mostrarError(fallo.message || 'No fue posible crear la cuenta administrativa.');
  } finally {
    cambiarEstadoBoton(false);
  }
}

function validarDatos(nombres, apellidos, apodo, email, password, rol) {
  if (!nombres) {
    return 'Debes ingresar los nombres.';
  }

  if (!apellidos) {
    return 'Debes ingresar los apellidos.';
  }

  if (!apodo) {
    return 'Debes ingresar un apodo.';
  }

  if (!email) {
    return 'Debes ingresar un correo electrónico.';
  }

  if (!validarEmail(email)) {
    return 'Ingresa un correo electrónico válido.';
  }

  if (!password) {
    return 'Debes ingresar una contraseña inicial.';
  }

  if (password.length < 9) {
    return 'La contraseña debe tener al menos 9 caracteres.';
  }

  if (!ROLES_PERMITIDOS.includes(rol)) {
    return 'Debes seleccionar un rol administrativo válido.';
  }

  return null;
}

function validarEmail(email) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
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

async function obtenerJsonSeguro(respuesta) {
  try {
    return await respuesta.json();
  } catch {
    return null;
  }
}

function mostrarResultado(usuarioCreado, apodo, rol) {
  const formularioContenedor = document.getElementById('formulario-contenedor');

  const resultado = document.getElementById('resultado-creacion');

  const resultadoTexto = document.getElementById('resultado-texto');

  if (!formularioContenedor || !resultado || !resultadoTexto) {
    return;
  }

  let identificador = 'No disponible';

  if (usuarioCreado) {
    identificador = usuarioCreado.id ?? usuarioCreado.usuarioId ?? 'No disponible';
  }

  resultadoTexto.textContent =
    `La cuenta "${apodo}" fue creada con el rol ${rol}. ` + `ID de usuario: ${identificador}.`;

  formularioContenedor.hidden = true;

  resultado.hidden = false;
}

function limpiarFormulario() {
  const formulario = document.getElementById('form-crear-cuenta');

  if (!formulario) {
    return;
  }

  formulario.reset();

  actualizarPermisosVisuales();

  ocultarMensajes();
}

function mostrarFormularioNuevaCuenta() {
  const formularioContenedor = document.getElementById('formulario-contenedor');

  const resultado = document.getElementById('resultado-creacion');

  if (formularioContenedor) {
    formularioContenedor.hidden = false;
  }

  if (resultado) {
    resultado.hidden = true;
  }

  limpiarFormulario();
}

function cambiarEstadoBoton(cargando) {
  const boton = document.getElementById('btn-crear');

  if (!boton) {
    return;
  }

  boton.disabled = cargando;

  boton.textContent = cargando ? 'Creando cuenta...' : 'Crear cuenta';
}

function mostrarError(mensaje) {
  const elemento = document.getElementById('mensaje-error');

  const exito = document.getElementById('mensaje-exito');

  if (exito) {
    exito.hidden = true;
  }

  if (!elemento) {
    return;
  }

  elemento.textContent = mensaje;

  elemento.hidden = false;
}

function ocultarMensajes() {
  const error = document.getElementById('mensaje-error');

  const exito = document.getElementById('mensaje-exito');

  if (error) {
    error.hidden = true;
  }

  if (exito) {
    exito.hidden = true;
  }
}

function volverInicio() {
  // El menú principal (index.html) vive en la MISMA carpeta que esta página
  // (frontend/app-web/src/cuentas/), no un nivel arriba: '../index.html' no
  // existe y devolvía 404 al usuario que hacía clic en Volver.
  window.location.href = './index.html';
}
