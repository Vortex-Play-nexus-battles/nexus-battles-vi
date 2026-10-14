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
import { formularioListo, sinCredencialesEnLaDireccion } from '../comun/ui/formulario-seguro.js';
import { confirmar } from '../comun/ui/dialogo.js';
import { respaldoPorEstado, textoDeError, textoDelServidor } from '../comun/ui/texto-de-fallo.js';
import { RUTAS, resolver } from '../comun/sesion.js';

const BASE_API = '/api/v1/admin/cuentas';
const MATRIZ_RBAC_API = '/api/v1/rbac/matrix';
const CLAVE_TOKEN = 'nexus.token';

const CLAVE_ROL = 'nexus.rolActual';

const PERMISO_CREAR = 'CREAR_ADMIN_MODERADOR';

const ROLES_PERMITIDOS = ['MODERADOR', 'ADMINISTRADOR'];

/** El nombre de cada rol para leerlo. */
const NOMBRE_DE_ROL = Object.freeze({
  MODERADOR: 'Moderador',
  ADMINISTRADOR: 'Administrador',
});

/**
 * Cómo se lee cada acción de la matriz (rbac.yaml). Los permisos que se
 * muestran salen de la matriz que publica el servidor, no de una lista
 * escrita aquí: hasta 7.3.9 esta vista decía que un Moderador «gestiona
 * cuentas», cosa que la Tabla 24 no le da.
 */
export const NOMBRE_DE_ACCION = Object.freeze({
  CREAR_CUENTA_JUGADOR: 'Crear una cuenta de jugador',
  MODIFICAR_PERFIL_PROPIO: 'Editar su propio perfil',
  PUBLICAR_COMENTARIOS: 'Publicar comentarios',
  ELIMINAR_COMENTARIO_PROPIO: 'Eliminar sus comentarios',
  MODERAR_COMENTARIOS: 'Moderar comentarios',
  EMITIR_ADVERTENCIAS: 'Emitir advertencias',
  SUSPENDER_USUARIOS: 'Suspender cuentas',
  BANEAR_DEFINITIVAMENTE: 'Banear definitivamente',
  CREAR_ADMIN_MODERADOR: 'Crear cuentas administrativas',
  GESTIONAR_PRODUCTOS: 'Gestionar los productos de la tienda',
  ASIGNAR_ROL: 'Cambiar roles',
  GESTIONAR_CUENTAS: 'Gestionar cuentas de otros usuarios',
  RESTABLECER_SEGUNDO_FACTOR: 'Restablecer la verificación en dos pasos',
});

/** La matriz que publicó el servidor, para decir qué puede hacer cada rol. */
let matrizPublicada = {};

/**
 * Las acciones que la matriz da a un rol, en palabras. Una permitida solo de
 * forma temporal (el Moderador suspende, pero no para siempre) lo dice.
 *
 * @param {Record<string, Record<string, string>>} matriz
 * @param {string} rol
 * @returns {string[]}
 */
export function permisosDelRol(matriz, rol) {
  const fila = matriz?.[rol];
  if (!fila || typeof fila !== 'object') {
    return [];
  }
  const permisos = [];
  for (const [accion, nombre] of Object.entries(NOMBRE_DE_ACCION)) {
    if (fila[accion] === 'GRANTED') {
      permisos.push(nombre);
    } else if (fila[accion] === 'TEMPORARY') {
      permisos.push(`${nombre} (solo de forma temporal)`);
    }
  }
  return permisos;
}

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
  // G1 — lo que un envío nativo de una versión vieja pudo dejar en la barra.
  sinCredencialesEnLaDireccion();
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
    matrizPublicada = matriz;
    seCargo = true;
  } catch (error) {
    console.error('No fue posible cargar la matriz RBAC:', error);
    setPermissionMatrix({});
  }

  verificarAcceso({ matrizDisponible: seCargo });
  actualizarPermisosVisuales();
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
    // G1 — la vista ya escucha `submit`: el botón se puede encender.
    formularioListo(formulario);
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

  if (!ROLES_PERMITIDOS.includes(rolSeleccionado)) {
    contenedorPermisos.append(
      h('p', { clase: 't-meta', texto: 'Elige un rol para ver lo que podrá hacer.' }),
    );

    return;
  }

  const permisos = permisosDelRol(matrizPublicada, rolSeleccionado);
  if (permisos.length === 0) {
    contenedorPermisos.append(
      h('p', {
        clase: 't-meta',
        texto: 'No pudimos leer los permisos del rol. Vuelve a intentarlo en un momento.',
      }),
    );
    return;
  }
  for (const permiso of permisos) {
    contenedorPermisos.append(distintivo(permiso));
  }
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

  const rol = document.getElementById('rol')?.value;

  const archivoAvatar = document.getElementById('avatar')?.files?.[0];

  const error = validarDatos(nombres, apellidos, apodo, email, rol);

  if (error) {
    mostrarError(error);

    return;
  }

  const confirmado = await confirmar({
    titulo: 'Crear cuenta administrativa',
    mensaje:
      `Se creará la cuenta «${apodo}» como ${NOMBRE_DE_ROL[rol] ?? rol} y se enviará el correo ` +
      `de activación a ${email}. Podrás cambiarle el rol después; la creación queda en la ` +
      'auditoría.',
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

    mostrarResultado(usuarioCreado, { apodo, rol, email });
  } catch (fallo) {
    // Se llama 'fallo' y no 'error' porque en la linea 311 ya hay un
    // 'error' con el mensaje de validacion. Con los dos llamados igual,
    // dentro de este bloque no habia forma de leer el de fuera.
    console.error('Error creando cuenta administrativa:', fallo);

    mostrarError(textoDeError(fallo, 'No fue posible crear la cuenta administrativa.'));
  } finally {
    cambiarEstadoBoton(false);
  }
}

function validarDatos(nombres, apellidos, apodo, email, rol) {
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
    // UXC-9 — nunca «Error HTTP 502» ni la página de un proxy: el texto del
    // servidor solo si se puede leer (comun/ui/texto-de-fallo.js).
    return textoDelServidor(datos, respuesta.status, respaldoPorEstado(respuesta.status));
  } catch {
    try {
      const texto = await respuesta.clone().text();
      return textoDelServidor(texto, respuesta.status, respaldoPorEstado(respuesta.status));
    } catch {
      // Se utiliza el mensaje genérico.
    }

    return respaldoPorEstado(respuesta.status);
  }
}

async function obtenerJsonSeguro(respuesta) {
  try {
    return await respuesta.json();
  } catch {
    return null;
  }
}

function mostrarResultado(usuarioCreado, { apodo, rol, email }) {
  const formularioContenedor = document.getElementById('formulario-contenedor');

  const resultado = document.getElementById('resultado-creacion');

  const resultadoTexto = document.getElementById('resultado-texto');

  if (!formularioContenedor || !resultado || !resultadoTexto) {
    return;
  }

  const nombre = usuarioCreado?.apodo ?? apodo;
  const nombreDeRol = NOMBRE_DE_ROL[usuarioCreado?.rol ?? rol] ?? rol;
  const correo = usuarioCreado?.email ?? email;

  resultadoTexto.textContent =
    `La cuenta «${nombre}» quedó creada como ${nombreDeRol}. Enviamos a ${correo} el ` +
    'correo para activarla y elegir su contraseña.';

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
  // Desde la trastienda se vuelve a la consola, no al menú del jugador.
  window.location.href = resolver(RUTAS.consola);
}
