// login.js
// Vista de inicio de sesión — HU-AUT-004.
//
// R17 — la entrada como la espera alguien que no conoce el juego:
//   - dice por qué está aquí (sesión terminada, sesión cerrada, cuenta recién
//     creada) en vez de enseñar un formulario mudo;
//   - si otra pestaña de este navegador ya tiene la sesión abierta, la usa y
//     sigue adonde iba, sin pedir la contraseña otra vez;
//   - tras entrar, una cuenta nueva pasa por «Preparando tu cuenta» hasta que
//     el servidor termina de darle sus créditos y su héroe;
//   - la vuelta (`?volver=`) solo acepta rutas de este mismo sitio.

import { destinoTrasEntrar } from '../comun/alta.js';
import { pedirSesionAOtraPestana } from '../comun/canal-sesion.js';
import {
  CLAVE_CORREO_REGISTRADO,
  entrarCon,
  identificadorDeSesion,
  mensajeDelServidor,
  pedirLogin,
} from '../comun/entrada.js';
import {
  MOTIVOS,
  guardarSesion,
  leerSesion,
  olvidarSesion,
  rutaDeVuelta,
} from '../comun/sesion.js';
import { VEREDICTOS, comprobarCredencial } from '../comun/vigilante-sesion.js';
import { setCurrentRole } from './directives/has-permission.directive.js';

// Se reexporta con su nombre de siempre: lo usan las pruebas de esta vista.
export { identificadorDeSesion };

/**
 * Traduce cada código de error del login a un mensaje específico.
 *
 * @param {number} status
 * @param {string} [mensajeServidor]
 */
export function mensajeDeError(status, mensajeServidor) {
  switch (status) {
    case 401:
      return 'Acceso rechazado. El correo o la contraseña son incorrectos, o estas credenciales no están registradas en este ambiente.';

    case 403:
      return mensajeServidor || 'Esta cuenta no puede iniciar sesión en este momento.';

    case 423:
      return mensajeServidor || 'Cuenta bloqueada temporalmente. Intenta más tarde.';

    default:
      return mensajeServidor || 'No se pudo iniciar sesión.';
  }
}

/**
 * Lo que se le dice a quien llega al login, según por qué llegó.
 *
 * @param {string|null} motivo el `?motivo=` de la URL
 * @returns {{tipo: 'advertencia'|'exito'|'info', texto: string}|null}
 */
export function avisoDelMotivo(motivo) {
  switch (motivo) {
    case MOTIVOS.CADUCADA:
      return {
        tipo: 'advertencia',
        texto: 'Tu sesión terminó. Vuelve a entrar y te llevamos a donde estabas.',
      };
    case MOTIVOS.CERRADA:
      return { tipo: 'info', texto: 'Cerraste sesión. ¡Hasta la próxima batalla!' };
    case MOTIVOS.REGISTRADA:
      return {
        tipo: 'exito',
        texto: 'Tu cuenta ya está creada. Entra con tu correo y tu contraseña para empezar.',
      };
    default:
      return null;
  }
}

/**
 * Adopta la sesión que ofrece otra pestaña, pero solo si el emisor la acepta.
 *
 * R18 — antes se adoptaba si el reloj la daba por buena (`exp`), y eso no
 * basta: otra pestaña puede guardar un token que todavía no ha caducado pero
 * que el servidor ya no acepta (identidad redesplegada con otra clave, cambio
 * de contraseña). La vista lo usaba, un servicio lo rechazaba, el vigilante
 * mandaba al login, y el login volvía a pedirlo a la misma pestaña y volvía a
 * adoptarlo: un bucle infinito entre /login y /inicio en el que no se podía ni
 * escribir la contraseña. Medido en dev el 24-sep: 900 peticiones en un minuto.
 *
 * Si el emisor no contesta tampoco se adopta: sin saber si vale, lo prudente
 * es dejar entrar con la contraseña, que es lo que la persona vino a hacer.
 *
 * @param {{token: string, apodo?: string, rol?: string, uid?: string}|null} compartida
 * @param {{comprobar?: Function, guardar?: Function, leer?: Function, olvidar?: Function}} [dependencias]
 * @returns {Promise<boolean>} true si la sesión quedó adoptada
 */
export async function adoptarSesionCompartida(
  compartida,
  {
    comprobar = comprobarCredencial,
    guardar = guardarSesion,
    leer = leerSesion,
    olvidar = olvidarSesion,
  } = {},
) {
  if (!compartida?.token || leer().autenticado) {
    return false;
  }
  if ((await comprobar(compartida.token)) !== VEREDICTOS.VALIDA) {
    return false;
  }
  guardar(compartida);
  if (leer().autenticado) {
    return true;
  }
  olvidar();
  return false;
}

// ---------------------------------------------------------------- la vista

/** @type {HTMLFormElement|null} */
const form = document.getElementById('formLogin');

if (form) {
  iniciarVista(form);
}

/** @param {HTMLFormElement} formulario */
function iniciarVista(formulario) {
  /** @type {HTMLButtonElement} */
  const botonEnviar = document.getElementById('botonEnviar');
  /** @type {HTMLElement} */
  const estadoLogin = document.getElementById('estadoLogin');
  /** @type {HTMLElement} */
  const avisoDispositivo = document.getElementById('avisoDispositivo');
  /** @type {HTMLElement|null} */
  const avisoMotivo = document.getElementById('avisoMotivo');

  const busqueda = globalThis.location?.search ?? '';
  const motivo = new URLSearchParams(busqueda).get('motivo');
  const volver = rutaDeVuelta(busqueda);

  function setEstado(texto, tipo) {
    estadoLogin.textContent = texto;
    estadoLogin.className = `estado ${tipo}`;
    estadoLogin.hidden = false;
  }

  function ocultarEstado() {
    estadoLogin.hidden = true;
  }

  // Ya hay sesión en esta pestaña: el login no tiene nada que hacer aquí.
  const actual = leerSesion();
  if (actual.autenticado) {
    globalThis.location.replace(destinoTrasEntrar(null, volver));
    return;
  }
  if (actual.caducada) {
    olvidarSesion();
  }

  const aviso = avisoDelMotivo(motivo);
  if (aviso && avisoMotivo) {
    avisoMotivo.textContent = aviso.texto;
    avisoMotivo.className = `aviso aviso--${aviso.tipo}`;
    avisoMotivo.hidden = false;
  }

  // El correo que dejó el registro, si no pudo entrar solo. Se usa una vez.
  const correoRegistrado = globalThis.sessionStorage?.getItem(CLAVE_CORREO_REGISTRADO);
  if (correoRegistrado) {
    globalThis.sessionStorage.removeItem(CLAVE_CORREO_REGISTRADO);
    formulario.email.value = correoRegistrado;
    formulario.password.focus();
  }

  // ¿Otra pestaña tiene la sesión abierta? Tras un cierre voluntario no se
  // pregunta: esa sesión se acaba de cerrar en todas.
  if (motivo !== MOTIVOS.CERRADA) {
    pedirSesionAOtraPestana()
      .then((compartida) => adoptarSesionCompartida(compartida))
      .then((adoptada) => {
        if (adoptada) {
          globalThis.location.replace(destinoTrasEntrar(null, volver));
        }
      });
  }

  formulario.addEventListener('submit', async (evento) => {
    evento.preventDefault();

    ocultarEstado();
    avisoDispositivo.hidden = true;

    if (!formulario.checkValidity()) {
      formulario.reportValidity();
      return;
    }

    botonEnviar.disabled = true;
    setEstado('Verificando tus datos…', 'carga');

    try {
      const { respuesta, body } = await pedirLogin({
        email: formulario.email.value.trim(),
        password: formulario.password.value,
      });

      if (!respuesta.ok) {
        setEstado(mensajeDeError(respuesta.status, mensajeDelServidor(body)), 'error');
        return;
      }

      if (body.dispositivoNuevo) {
        avisoDispositivo.hidden = false;
        avisoDispositivo.textContent = 'Detectamos un inicio de sesión desde un dispositivo nuevo.';
      }

      ocultarEstado();

      // Mantener el rol en memoria para esta página.
      setCurrentRole(body.rol);

      // HU-UX-001: si se llegó al login desde una vista privada, se vuelve a
      // ella; una cuenta recién creada pasa antes por «Preparando tu cuenta».
      globalThis.location.href = entrarCon(body, { volver });
    } catch {
      setEstado(
        'No pudimos conectar con el servidor. Inténtalo de nuevo en unos segundos.',
        'error',
      );
    } finally {
      botonEnviar.disabled = false;
    }
  });
}
