/**
 * Las identidades con las que se revisa el producto — UX-R3.9.
 *
 * ## Por qué hacía falta
 *
 * `identidad.js` sabe registrar **un jugador** por los mecanismos reales del
 * sistema, y eso resolvía UX-R2: casi todo lo que había que mirar era de
 * jugador. UX-R3 separó la consola de la aplicación, y de pronto hay una
 * trastienda entera de pantallas que solo existen para tres roles que **no se
 * pueden crear registrándose** — y está bien que no se pueda: RF-RBAC-003 dice que asignar
 * roles es «exclusivamente» del super administrador.
 *
 * El laboratorio lo estaba resolviendo de la peor manera posible: con backend
 * real, `sesionAdmin = sesionJugador`. Es decir, las ocho vistas de
 * administración se fotografiaban con un jugador, salía el estado de permiso
 * denegado, y eso se archivaba como «la captura de la vista de auditoría».
 *
 * ## Cómo se consiguen ahora
 *
 * Por los mecanismos reales, en cadena, igual que los conseguiría una
 * persona:
 *
 *   1. **Jugador** — `POST /api/v1/auth/registro`. Cualquiera puede.
 *   2. **Moderador y administrador** — `POST /api/v1/admin/cuentas`, que es
 *      el endpoint que usa la pantalla «Crear cuenta administrativa» y que
 *      exige el permiso `CREAR_ADMIN_MODERADOR`. Hace falta, por tanto, una
 *      sesión de super administrador.
 *   3. **Super administrador** — este NO se puede crear desde fuera, y es
 *      correcto que no se pueda. El arnés espera encontrarlo en el entorno.
 *
 * ## Credenciales
 *
 * Nada está escrito en el repositorio. El jugador se registra con apodo y
 * contraseña generados al azar en cada corrida. El super administrador de
 * arranque se lee de dos variables de entorno:
 *
 *     UX_SUPERADMIN_EMAIL=...  UX_SUPERADMIN_PASSWORD=...
 *
 * Si no están, el arnés **lo dice y sigue** con lo que sí puede conseguir. No
 * inventa un rol ni falsifica un token para colarse: una captura de una vista
 * administrativa hecha con un rol falso no demuestra nada.
 *
 * ## Sin backend
 *
 * El modo por omisión del laboratorio sirve `src/` con un servidor estático y
 * sin API. Ahí no hay a quién registrarse, así que cada persona recibe un JWT
 * **sin firma válida**, solo para que el guard del navegador deje pintar el
 * armazón de la vista. Ese token no abre nada: cualquier servicio real lo
 * rechaza. Las capturas de ese modo se etiquetan `sin-servicios`.
 *
 * @module tests/visual/personas
 */

import { apodoEfimero, claveEfimera, identidadReal, sesionSintetica } from './identidad.js';

/**
 * Las cuatro personas del producto, en el orden en que se consiguen.
 *
 * `rol` es el del catálogo de RF-RBAC-001. `crearComo` dice con qué otra
 * persona hay que estar autenticado para crearla: `null` significa que se
 * consigue sola.
 */
export const PERSONAS = Object.freeze([
  { id: 'anonimo', rol: null, crearComo: null },
  { id: 'jugador', rol: 'JUGADOR', crearComo: null },
  { id: 'moderador', rol: 'MODERADOR', crearComo: 'superadministrador' },
  { id: 'administrador', rol: 'ADMINISTRADOR', crearComo: 'superadministrador' },
  { id: 'superadministrador', rol: 'SUPER_ADMINISTRADOR', crearComo: 'entorno' },
]);

/** Cómo se llama en pantalla cada rol. Para el informe, no para decidir nada. */
export const NOMBRE = Object.freeze({
  anonimo: 'Visitante',
  jugador: 'Jugador',
  moderador: 'Moderador',
  administrador: 'Administrador',
  superadministrador: 'Super administrador',
});

/**
 * Inicia sesión con el super administrador de arranque, si el entorno lo da.
 *
 * @param {import('@playwright/test').APIRequestContext} api
 * @returns {Promise<{apodo: string, token: string, uid: string, rol: string}|null>}
 */
export async function superAdministradorDelEntorno(api) {
  const email = process.env.UX_SUPERADMIN_EMAIL;
  const password = process.env.UX_SUPERADMIN_PASSWORD;
  if (!email || !password) {
    return null;
  }

  const entrada = await api.post('/api/v1/auth/login', { data: { email, password } });
  if (!entrada.ok()) {
    throw new Error(
      `UX_SUPERADMIN_EMAIL está puesto pero el login falló (${entrada.status()}). ` +
        'O la contraseña no es esa, o esa cuenta ya no existe en este entorno.',
    );
  }
  const cuerpo = await entrada.json();
  if (cuerpo.rol !== 'SUPER_ADMINISTRADOR') {
    throw new Error(
      `La cuenta de UX_SUPERADMIN_EMAIL tiene rol ${cuerpo.rol}, no SUPER_ADMINISTRADOR. ` +
        'El arnés no puede crear cuentas administrativas con ella.',
    );
  }
  return conClaims(cuerpo, cuerpo.apodo ?? email);
}

/**
 * Crea una cuenta administrativa por el mismo endpoint que usa la pantalla
 * «Crear cuenta administrativa», y entra con ella.
 *
 * @param {import('@playwright/test').APIRequestContext} api
 * @param {{token: string}} superAdmin sesión con permiso CREAR_ADMIN_MODERADOR
 * @param {'MODERADOR'|'ADMINISTRADOR'} rol
 * @returns {Promise<{apodo: string, token: string, uid: string, rol: string}>}
 */
export async function crearIdentidadAdministrativa(api, superAdmin, rol) {
  const apodo = apodoEfimero(`qa_${rol.toLowerCase()}`);
  const clave = claveEfimera();
  const email = `${apodo}@nexus.test`;

  const alta = await api.post('/api/v1/admin/cuentas', {
    headers: { Authorization: `Bearer ${superAdmin.token}` },
    multipart: {
      nombres: 'Revisión',
      apellidos: 'Visual',
      email,
      password: clave,
      apodo,
      rol,
    },
  });
  if (!alta.ok()) {
    throw new Error(
      `No se pudo crear la cuenta ${rol} (${alta.status()}). ` +
        'Comprueba que el super administrador del entorno conserva CREAR_ADMIN_MODERADOR.',
    );
  }

  const entrada = await api.post('/api/v1/auth/login', { data: { email, password: clave } });
  if (!entrada.ok()) {
    throw new Error(`La cuenta ${rol} se creó pero no puede entrar (${entrada.status()}).`);
  }
  const cuerpo = await entrada.json();
  return conClaims(cuerpo, apodo);
}

/**
 * Consigue las cinco personas, cada una por el camino que le toca.
 *
 * Nunca lanza por no poder conseguir una persona administrativa: devuelve lo
 * que hay y una lista de avisos. Un laboratorio que se cae entero porque
 * falta una variable de entorno es un laboratorio que nadie corre.
 *
 * @param {import('@playwright/test').APIRequestContext|null} api `null` = sin backend
 * @returns {Promise<{sesiones: Record<string, object|null>, avisos: string[], reales: boolean}>}
 */
export async function conseguirPersonas(api) {
  if (!api) {
    return { sesiones: sesionesSinteticas(), avisos: [], reales: false };
  }

  const sesiones = { anonimo: null };
  const avisos = [];

  sesiones.jugador = await identidadReal(api, { apodo: apodoEfimero('qa_jugador') });

  let superAdmin = null;
  try {
    superAdmin = await superAdministradorDelEntorno(api);
  } catch (error) {
    avisos.push(String(error.message));
  }

  if (!superAdmin) {
    avisos.push(
      'Sin UX_SUPERADMIN_EMAIL / UX_SUPERADMIN_PASSWORD no se pueden crear las identidades ' +
        'administrativas: RF-RBAC-003 reserva la asignación de roles al super administrador, y ' +
        'el arnés no se salta esa regla. Las vistas de trastienda se capturan con el jugador, ' +
        'que es lo que un jugador ve de ellas: la pantalla de acceso denegado.',
    );
    sesiones.superadministrador = null;
    sesiones.administrador = null;
    sesiones.moderador = null;
    return { sesiones, avisos, reales: true };
  }

  sesiones.superadministrador = superAdmin;
  for (const rol of ['ADMINISTRADOR', 'MODERADOR']) {
    const clave = rol.toLowerCase();
    try {
      sesiones[clave] = await crearIdentidadAdministrativa(api, superAdmin, rol);
    } catch (error) {
      sesiones[clave] = null;
      avisos.push(String(error.message));
    }
  }

  return { sesiones, avisos, reales: true };
}

/**
 * Las cinco personas sin backend: tokens que solo sirven para pintar.
 *
 * @returns {Record<string, object|null>}
 */
export function sesionesSinteticas() {
  const sesiones = { anonimo: null };
  for (const persona of PERSONAS) {
    if (!persona.rol) {
      continue;
    }
    const apodo = `qa_${persona.id}`;
    sesiones[persona.id] = {
      ...sesionSintetica({ apodo, rol: persona.rol }),
      apodo,
      rol: persona.rol,
    };
  }
  return sesiones;
}

/**
 * Con qué persona hay que abrir una vista, y con cuál NO debe verse.
 *
 * La primera es para la captura; la segunda es la comprobación de §25: que
 * un jugador no vea la trastienda. Sale de la misma matriz que usan los
 * guardas, así que no puede desviarse de ella.
 *
 * @param {{persona: string}} vista entrada de `VISTAS`
 * @returns {{para: string, denegadaPara: string[]}}
 */
export function personasDe(vista) {
  const orden = ['jugador', 'moderador', 'administrador', 'superadministrador'];
  const alcanza = orden.indexOf(vista.persona);
  return {
    para: vista.persona,
    // Todas las que se quedan cortas, más el visitante cuando la vista no es
    // pública.
    denegadaPara: alcanza <= 0 ? [] : orden.slice(0, alcanza),
  };
}

/** @param {object} cuerpo respuesta de `POST /api/v1/auth/login` */
function conClaims(cuerpo, apodoPorDefecto) {
  const claims = JSON.parse(Buffer.from(cuerpo.token.split('.')[1], 'base64url').toString('utf8'));
  return {
    apodo: cuerpo.apodo ?? apodoPorDefecto,
    token: cuerpo.token,
    uid: claims.uid ?? cuerpo.usuarioId ?? '',
    rol: cuerpo.rol,
  };
}
