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
//
// B1 (identidad 2.0.0) — un rechazo se explica por su `type`, nunca por el
// texto (el login pide `Accept: application/problem+json` desde R17, en
// `comun/entrada.js`):
//   - `cuenta-no-verificada`: lleva a confirmar el correo, o pide otro código;
//   - `cuenta-suspendida`: dice hasta cuándo, con el tiempo restante contando
//     en vivo (7.3.2, «contador visible del tiempo restante de suspensión»);
//   - `cuenta-baneada`: un aviso formal, sin salidas que no existen.
// Y tras verificar el correo (`?motivo=verificada`) la primera entrada pasa
// por «Preparando tu cuenta»: el alta del jugador empieza al verificar.
//
// HU-AUT-007 (identidad 2.2.0) — con la verificación en dos pasos activa, la
// contraseña correcta no abre la sesión: el servidor responde 403 con un
// desafío y `login-segundo-paso.js` pide el código (o guía la activación, si
// el rol la exige). La entrada, cuando por fin hay sesión, es la misma.

import { destinoTrasEntrar } from '../comun/alta.js';
import { pedirSesionAOtraPestana } from '../comun/canal-sesion.js';
import { CLAVES_DEL_CORREO, recordar, tipoDelProblema } from '../comun/codigo-de-correo.js';
import {
  CLAVE_CORREO_REGISTRADO,
  entrarCon,
  identificadorDeSesion,
  mensajeDelServidor,
  pedirLogin,
} from '../comun/entrada.js';
import {
  MOTIVOS,
  MOTIVOS_DE_VERIFICACION,
  guardarSesion,
  leerSesion,
  olvidarSesion,
  resolver,
  RUTAS,
  rutaDeVuelta,
  urlDeVerificacion,
} from '../comun/sesion.js';
import { tonoPorEstado } from '../comun/ui/aviso.js';
import { cuentaAtras, vigilarCuentasAtras } from '../comun/ui/cuenta-atras.js';
import { h } from '../comun/ui/dom.js';
import { fechaHora } from '../comun/ui/formato.js';
import { formularioListo, sinCredencialesEnLaDireccion } from '../comun/ui/formulario-seguro.js';
import { anotarEnvio, reenviarCodigo } from '../comun/verificacion.js';
import { VEREDICTOS, comprobarCredencial } from '../comun/vigilante-sesion.js';
import { mostrarAlertasCatalogoAlIniciarSesion } from '../contenido/productos/alertas-catalogo.js';
import { setCurrentRole } from './directives/has-permission.directive.js';
import { desafioDelRechazo, montarSegundoPaso } from './login-segundo-paso.js';
import { PROBLEMAS_DEL_SEGUNDO_FACTOR } from './segundo-factor.js';

// Se reexporta con su nombre de siempre: lo usan las pruebas de esta vista.
export { identificadorDeSesion };

/**
 * Traduce cada código de error del login a un mensaje específico.
 *
 * Es el respaldo para un rechazo SIN `type` (un servicio que todavía responde
 * texto plano): con `type`, manda `rechazoDelLogin`.
 *
 * @param {number} status
 * @param {string} [mensajeServidor]
 */
export function mensajeDeError(status, mensajeServidor) {
  // UXC-9 — sin respuesta o con el servidor caído, nunca su texto.
  if (!status || status >= 500) {
    return status
      ? 'No pudimos iniciar sesión ahora mismo. Inténtalo de nuevo en unos minutos.'
      : 'No pudimos conectar. Revisa tu conexión e inténtalo otra vez.';
  }
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

/** Los motivos de rechazo del login, tal como los nombra el contrato (2.0.0). */
export const PROBLEMAS_DEL_LOGIN = Object.freeze({
  CREDENCIALES_INVALIDAS: 'credenciales-invalidas',
  NO_VERIFICADA: 'cuenta-no-verificada',
  SUSPENDIDA: 'cuenta-suspendida',
  BANEADA: 'cuenta-baneada',
  INACTIVA: 'cuenta-inactiva',
  BLOQUEADA: 'cuenta-bloqueada',
  // 2.2.0 — el rol exige segundo factor y el servicio no puede comprobarlo.
  SEGUNDO_FACTOR_NO_DISPONIBLE: PROBLEMAS_DEL_SEGUNDO_FACTOR.NO_DISPONIBLE,
});

/**
 * Qué decir cuando el login no deja entrar, decidido por el `type` del
 * rechazo (MAPEO-ERRORES §2). Sin `type`, por el estado, como antes.
 *
 * @param {number} estado código HTTP
 * @param {unknown} cuerpo el cuerpo ya leído
 * @returns {{caso: 'credenciales'|'no-verificada'|'suspendida'|'baneada'|'inactiva'|'bloqueada'|'otro',
 *   tono: 'error'|'advertencia'|'info', titulo: string, detalle: string|null,
 *   suspendidoHasta?: string|null}}
 */
export function rechazoDelLogin(estado, cuerpo) {
  const tono = tonoPorEstado(estado);
  switch (tipoDelProblema(cuerpo)) {
    case PROBLEMAS_DEL_LOGIN.CREDENCIALES_INVALIDAS:
      return { caso: 'credenciales', tono, titulo: mensajeDeError(401), detalle: null };
    case PROBLEMAS_DEL_LOGIN.NO_VERIFICADA:
      return {
        caso: 'no-verificada',
        tono,
        titulo: 'Falta confirmar tu correo',
        detalle:
          'Tu cuenta todavía no está activa. Escribe el código que te enviamos al registrarte o pide uno nuevo.',
      };
    case PROBLEMAS_DEL_LOGIN.SUSPENDIDA: {
      const hasta = typeof cuerpo?.suspendidoHasta === 'string' ? cuerpo.suspendidoHasta : null;
      const cuando = hasta ? fechaHora(hasta) : '—';
      return {
        caso: 'suspendida',
        tono,
        titulo: 'Tu cuenta está suspendida',
        // Punto y coma, no punto: la hora ya acaba en «a. m.» o «p. m.».
        detalle:
          cuando === '—'
            ? 'No puedes entrar mientras dure la suspensión. Tu inventario y tu progreso se conservan.'
            : `No puedes entrar hasta el ${cuando}; tu inventario y tu progreso se conservan.`,
        suspendidoHasta: cuando === '—' ? null : hasta,
      };
    }
    case PROBLEMAS_DEL_LOGIN.BANEADA:
      return {
        caso: 'baneada',
        tono,
        titulo: 'Esta cuenta está inhabilitada de forma definitiva',
        detalle:
          mensajeDelServidor(cuerpo) ??
          'La cuenta fue dada de baja por incumplir las normas del juego y ya no puede iniciar sesión.',
      };
    case PROBLEMAS_DEL_LOGIN.INACTIVA:
      return {
        caso: 'inactiva',
        tono,
        titulo: 'Esta cuenta está inactiva',
        detalle: mensajeDelServidor(cuerpo) ?? 'No puede iniciar sesión en este momento.',
      };
    case PROBLEMAS_DEL_LOGIN.BLOQUEADA:
      return {
        caso: 'bloqueada',
        tono,
        titulo: 'Cuenta bloqueada temporalmente',
        detalle:
          'Hubo demasiados intentos fallidos. Espera unos minutos o recupera tu contraseña si no la recuerdas.',
      };
    case PROBLEMAS_DEL_LOGIN.SEGUNDO_FACTOR_NO_DISPONIBLE:
      return {
        caso: 'otro',
        tono,
        titulo: 'No podemos comprobar tu verificación en dos pasos ahora mismo',
        detalle:
          'Tu cuenta la necesita para entrar y el servicio no está disponible. Inténtalo de nuevo más tarde.',
      };
    default:
      return {
        caso: 'otro',
        tono,
        titulo: mensajeDeError(estado, mensajeDelServidor(cuerpo)),
        detalle: null,
      };
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
    case MOTIVOS.VERIFICADA:
      return {
        tipo: 'exito',
        texto: 'Tu correo quedó verificado. Entra con tu contraseña y preparamos tu cuenta.',
      };
    case MOTIVOS.RESTABLECIDA:
      return {
        tipo: 'exito',
        texto: 'Tu contraseña nueva quedó guardada. Entra con ella.',
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

/** Prefijo de lo que se lee en voz alta del contador de la suspensión. */
const PREFIJO_SUSPENSION = 'La suspensión termina en';

/**
 * Pinta el rechazo dentro de su zona y devuelve cómo detener lo que quede
 * vivo (el contador de la suspensión).
 *
 * Construido con nodos (`h`), nunca con `innerHTML`: el detalle puede venir
 * del servidor.
 *
 * @param {HTMLElement} zona `[data-zona="rechazo"]`
 * @param {ReturnType<typeof rechazoDelLogin>} rechazo
 * @param {{acciones?: HTMLElement|null, anunciar?: boolean}} [opciones]
 * @returns {() => void}
 */
export function pintarRechazo(zona, rechazo, { acciones = null, anunciar = true } = {}) {
  const caja = h('div', {
    clase: `aviso aviso--${rechazo.tono}`,
    // Si el foco va a ir a la propia zona, el rol sobra: el lector lee lo que
    // recibe el foco, y con `alert` además lo leería dos veces.
    atributos: anunciar ? { role: 'alert' } : {},
  });
  const cuerpo = h('div', { clase: 'aviso__cuerpo' });
  cuerpo.append(h('p', { clase: 'aviso__titulo', texto: rechazo.titulo }));
  if (rechazo.detalle) {
    cuerpo.append(h('p', { clase: 'aviso__detalle', texto: rechazo.detalle }));
  }
  let detener = () => {};
  if (rechazo.caso === 'suspendida' && rechazo.suspendidoHasta) {
    const linea = h('p', { clase: 'aviso__detalle', datos: { zona: 'tiempo-restante' } });
    linea.append(
      'Tiempo restante: ',
      cuentaAtras(rechazo.suspendidoHasta, { prefijo: PREFIJO_SUSPENSION }),
    );
    cuerpo.append(linea);
    detener = vigilarCuentasAtras(cuerpo, { prefijo: PREFIJO_SUSPENSION });
  }
  if (acciones) {
    cuerpo.append(acciones);
  }
  caja.append(cuerpo);
  zona.replaceChildren(caja);
  zona.hidden = false;
  return detener;
}

// ---------------------------------------------------------------- la vista

/** @type {HTMLFormElement|null} */
const form = document.getElementById('formLogin');

if (form) {
  // G1 — lo que una versión vieja de esta página, ya en caché, pudo dejar en
  // la barra con un envío nativo (`?email=…&password=…`) sale antes de nada.
  sinCredencialesEnLaDireccion();
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
  /** @type {HTMLElement|null} */
  const zonaRechazo = document.querySelector('[data-zona="rechazo"]');

  const busqueda = globalThis.location?.search ?? '';
  const motivo = new URLSearchParams(busqueda).get('motivo');
  const volver = rutaDeVuelta(busqueda);
  let detenerRechazo = () => {};

  function setEstado(texto, tipo) {
    estadoLogin.textContent = texto;
    estadoLogin.className = `estado ${tipo}`;
    estadoLogin.hidden = false;
  }

  function ocultarEstado() {
    estadoLogin.hidden = true;
  }

  function limpiarRechazo() {
    detenerRechazo();
    detenerRechazo = () => {};
    if (zonaRechazo) {
      zonaRechazo.replaceChildren();
      zonaRechazo.hidden = true;
    }
  }

  /**
   * Las dos salidas de una cuenta sin verificar: escribir el código que ya
   * llegó, o pedir otro. El correo se deja en `sessionStorage` para que la
   * verificación lo traiga escrito; nunca en la URL.
   */
  function salidasDeVerificacion(email) {
    const acciones = h('div', { clase: 'fila fila--envuelta' });
    const escribir = h('a', {
      clase: 'boton boton--primario',
      texto: 'Confirmar mi correo',
      datos: { accion: 'confirmar-correo' },
    });
    escribir.href = urlDeVerificacion({ motivo: MOTIVOS_DE_VERIFICACION.LOGIN });
    const pedirOtro = h('button', {
      clase: 'boton boton--secundario',
      texto: 'Enviarme un código nuevo',
      atributos: { type: 'button' },
      datos: { accion: 'reenviar-codigo' },
    });
    pedirOtro.addEventListener('click', async () => {
      pedirOtro.disabled = true;
      pedirOtro.setAttribute('aria-busy', 'true');
      let resultado = null;
      try {
        resultado = await reenviarCodigo(email);
      } catch {
        resultado = null;
      }
      pedirOtro.removeAttribute('aria-busy');
      if (resultado?.ok) {
        anotarEnvio();
        globalThis.location.href = urlDeVerificacion({
          motivo: MOTIVOS_DE_VERIFICACION.REENVIADO,
        });
        return;
      }
      pedirOtro.disabled = false;
      setEstado(
        resultado?.estado === 429
          ? 'Hiciste varias solicitudes seguidas. Espera un momento y vuelve a intentarlo.'
          : 'No pudimos pedir otro código. Inténtalo de nuevo en unos segundos.',
        'error',
      );
    });
    acciones.append(escribir, pedirOtro);
    return { acciones, enfocar: escribir };
  }

  /** Enseña el rechazo del servidor y lleva el foco a donde se puede actuar. */
  function mostrarRechazo(estado, cuerpo, email) {
    const rechazo = rechazoDelLogin(estado, cuerpo);
    if (!zonaRechazo) {
      setEstado(rechazo.titulo, 'error');
      return;
    }
    if (rechazo.caso === 'no-verificada') {
      recordar(CLAVES_DEL_CORREO.porVerificar, email);
      const { acciones, enfocar } = salidasDeVerificacion(email);
      detenerRechazo = pintarRechazo(zonaRechazo, rechazo, { acciones });
      enfocar.focus();
      return;
    }
    if (rechazo.caso === 'credenciales') {
      detenerRechazo = pintarRechazo(zonaRechazo, rechazo);
      formulario.password.focus();
      return;
    }
    if (rechazo.caso === 'bloqueada') {
      const recuperar = h('a', {
        clase: 'boton boton--secundario',
        texto: 'Recuperar mi contraseña',
      });
      recuperar.href = resolver(RUTAS.recuperar);
      detenerRechazo = pintarRechazo(zonaRechazo, rechazo, { acciones: recuperar });
      recuperar.focus();
      return;
    }
    detenerRechazo = pintarRechazo(zonaRechazo, rechazo, { anunciar: false });
    zonaRechazo.focus();
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

  // El correo que dejó el registro, la verificación o la recuperación. Se usa
  // una vez.
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

  // G1 — un solo envío a la vez. El botón se apaga en el mismo turno que el
  // `submit` (un segundo clic o un segundo Enter ya no encuentran botón
  // activo), y tras entrar se queda apagado mientras la página se va: antes
  // el `finally` lo encendía justo después de `location.assign`.
  let enviando = false;
  /** Tras un rechazo o un fallo de red: se puede volver a intentar. */
  function liberarEnvio() {
    enviando = false;
    botonEnviar.disabled = false;
  }

  /** HU-AUT-007 — la contraseña ya cumplió: no se queda en el formulario. */
  function olvidarContrasena() {
    formulario.password.value = '';
  }

  /**
   * Entra con la sesión ya emitida: la del login de siempre o la del segundo
   * paso (HU-AUT-007), que es la misma `LoginResponse`.
   *
   * @param {{token: string, rol?: string, dispositivoNuevo?: boolean}} body
   */
  async function completarEntrada(body) {
    if (body.dispositivoNuevo) {
      avisoDispositivo.hidden = false;
      avisoDispositivo.textContent = 'Detectamos un inicio de sesión desde un dispositivo nuevo.';
    }

    ocultarEstado();

    // Mantener el rol en memoria para esta página.
    setCurrentRole(body.rol);

    // HU-UX-001: si se llegó al login desde una vista privada, se vuelve a
    // ella; una cuenta recién creada pasa antes por «Preparando tu cuenta».
    // UXC-4 — la vuelta se lee AHORA y no al cargar: «Entra para comprar»,
    // en la tienda de la portada, la escribe en la dirección sin recargar.
    // B1: la primera entrada tras verificar el correo pasa por «Preparando
    // tu cuenta», siempre.
    const destino = entrarCon(body, {
      volver: rutaDeVuelta(globalThis.location?.search ?? '') ?? volver,
      cuentaNueva: motivo === MOTIVOS.VERIFICADA,
    });
    await mostrarAlertasCatalogoAlIniciarSesion();
    globalThis.location.assign(destino);
  }

  // HU-AUT-007 — el segundo paso (o el enrolamiento obligatorio). Al volver
  // al primero, la contraseña se escribe otra vez; si volvió por un rechazo
  // (desafío caducado, cuenta bloqueada o sancionada), se dice aquí arriba.
  const segundoPaso = montarSegundoPaso(document, {
    alEntrar: (body) => completarEntrada(body),
    alVolver: (rechazo) => {
      liberarEnvio();
      olvidarContrasena();
      if (rechazo && zonaRechazo) {
        detenerRechazo = pintarRechazo(
          zonaRechazo,
          { ...rechazo, caso: 'otro' },
          { anunciar: false },
        );
        zonaRechazo.focus();
        return;
      }
      formulario.password.focus();
    },
  });

  formulario.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    if (enviando) {
      return;
    }

    ocultarEstado();
    limpiarRechazo();
    avisoDispositivo.hidden = true;

    if (!formulario.checkValidity()) {
      formulario.reportValidity();
      return;
    }

    const email = formulario.email.value.trim();
    enviando = true;
    botonEnviar.disabled = true;
    setEstado('Verificando tus datos…', 'carga');

    let seVa = false;
    try {
      const { respuesta, body } = await pedirLogin({
        email,
        password: formulario.password.value,
      });

      if (!respuesta.ok) {
        ocultarEstado();
        // HU-AUT-007 — un 403 con desafío no es un rechazo: falta el segundo
        // factor. La contraseña ya cumplió y no se queda en el formulario.
        const desafio = desafioDelRechazo(respuesta.status, body);
        if (desafio && segundoPaso) {
          olvidarContrasena();
          if (desafio.tipo === PROBLEMAS_DEL_SEGUNDO_FACTOR.ENROLAMIENTO_REQUERIDO) {
            segundoPaso.enrolar(desafio);
          } else {
            segundoPaso.verificar(desafio);
          }
          return;
        }
        mostrarRechazo(respuesta.status, body, email);
        return;
      }

      await completarEntrada(body);
      seVa = true;
    } catch {
      setEstado(
        'No pudimos conectar con el servidor. Inténtalo de nuevo en unos segundos.',
        'error',
      );
    } finally {
      if (!seVa) {
        liberarEnvio();
      }
    }
  });

  // G1 — ahora sí: la vista ya escucha `submit`, el botón se puede encender.
  formularioListo(formulario);
}
