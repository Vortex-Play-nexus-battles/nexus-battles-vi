/**
 * Home del jugador — el destino al que lleva iniciar sesión (HU-UX-001).
 *
 * Hasta ahora `index.html` era una rejilla de enlaces: catorce tarjetas con un
 * título y una frase, sin un solo dato del jugador. Esta vista responde a
 * «¿qué hago ahora?» con lo que el backend ya publica: su saldo, su héroe
 * equipado, el torneo en curso, lo que le llegó sin leer, y una acción
 * principal para jugar.
 *
 * **Reglas que se imponen aquí:**
 *
 *  1. **No se inventa nada.** Cada dato sale de un endpoint que existe. Si el
 *     servicio no está en el entorno (en el host de dev no corren ms-finanzas
 *     ni inventario, #430/#435), la tarjeta lo dice con su nombre de persona y
 *     ofrece reintentar. Nunca se pinta un cero como si fuera el saldo real.
 *  2. **Cada bloque falla solo.** Se piden en paralelo y un 502 en créditos no
 *     deja la home en blanco.
 *  3. **La sesión ya la comprueba el shell** (`exigirSesion`), así que aquí no
 *     se repite la redirección.
 */

import { fetchWithHttpErrorInterceptor } from '../comun/interceptors/http-error.interceptor.js';
import { baseDeApi } from '../comun/base-api.js';
import { h, vaciar } from '../comun/ui/dom.js';
import { creditos as formatoCreditos, cuantoFalta } from '../comun/ui/formato.js';
import { boton } from '../comun/ui/boton.js';
import { distintivo } from '../comun/ui/distintivo.js';
import { tarjeta, tarjetaDeCifra } from '../comun/ui/tarjeta.js';
import { retratoDeHeroe } from '../comun/ui/juego/heroe.js';
import { distintivoDeCreditos } from '../comun/ui/juego/credito.js';
import {
  estadoDeCarga,
  estadoDeError,
  estadoVacio,
  pintarEstado,
} from '../comun/ui/estado-vista.js';

/** Accesos fijos de la home. Rutas reales del repo, ninguna inventada. */
export const ACCESOS = Object.freeze([
  {
    id: 'batallas',
    titulo: 'Batallas',
    detalle: 'Salas abiertas ahora',
    destino: '../plataforma/salas-partidas/batallas.html',
  },
  {
    id: 'inventario',
    titulo: 'Inventario',
    detalle: 'Tus héroes y objetos',
    destino: '../contenido/inventario/inventario.html',
  },
  {
    id: 'torneos',
    titulo: 'Torneos',
    detalle: 'Equipos y árbol',
    destino: '../plataforma/torneos/torneos.html',
  },
  { id: 'subastas', titulo: 'Subastas', detalle: 'Pujas en vivo', destino: './subastas.html' },
  { id: 'tienda', titulo: 'Tienda', detalle: 'Compra con créditos', destino: './tienda.html' },
  {
    id: 'comentarios',
    titulo: 'Comunidad',
    detalle: 'Opiniones de productos',
    destino: '../plataforma/comentarios/publicar-comentario.html',
  },
]);

/**
 * Una llamada que puede no estar disponible en este entorno.
 *
 * @param {string} ruta
 * @param {Function} fetchImpl
 * @returns {Promise<{ok: true, datos: unknown}|{ok: false, estado: number}>}
 */
async function pedir(ruta, fetchImpl) {
  try {
    const respuesta = await fetchImpl(`${baseDeApi()}${ruta}`, {
      method: 'GET',
      headers: { Accept: 'application/json' },
    });
    if (!respuesta.ok) {
      return { ok: false, estado: respuesta.status };
    }
    return { ok: true, datos: await respuesta.json() };
  } catch {
    // Ni siquiera hubo respuesta: el servicio no está o no hay red.
    return { ok: false, estado: 0 };
  }
}

/**
 * Qué decirle a quien mira cuando un bloque no se pudo cargar. Nunca el
 * código: el jugador no tiene por qué saber qué es un 502.
 *
 * @param {number} estado
 * @param {string} queEs por ejemplo «el saldo»
 * @returns {string}
 */
export function motivoDeIndisponibilidad(estado, queEs) {
  if (estado === 401 || estado === 403) {
    return `Tu sesión no alcanza para ver ${queEs}.`;
  }
  if (estado === 0 || estado >= 500) {
    return `Este servicio no está disponible ahora mismo. ${
      queEs.charAt(0).toUpperCase() + queEs.slice(1)
    } vuelve en cuanto responda.`;
  }
  return `No pudimos traer ${queEs}.`;
}

/**
 * Saldo del jugador (HU-JUE-014 / HU-JUE-012, `creditos.yaml`).
 *
 * @returns {Promise<HTMLElement>}
 */
async function bloqueDeSaldo(uid, fetchImpl, alReintentar) {
  const respuesta = await pedir(`/api/v1/creditos/${encodeURIComponent(uid)}/saldo`, fetchImpl);
  if (!respuesta.ok) {
    return estadoDeError({
      titulo: 'Tus créditos no están disponibles',
      detalle: motivoDeIndisponibilidad(respuesta.estado, 'el saldo'),
      alReintentar,
    });
  }
  const saldo = respuesta.datos;
  const caja = h('div', { clase: 'home__rejilla', datos: { zona: 'saldo' } });
  caja.append(
    tarjetaDeCifra({
      etiqueta: 'Créditos disponibles',
      // UX-R2.2 — la cifra era un número suelto, indistinguible de cualquier
      // otro dato. Los créditos son la moneda del juego: se ven como moneda.
      valor: distintivoDeCreditos(saldo.saldoDisponible, { tam: 'grande' }),
      detalle: 'Lo que puedes apostar ahora',
    }),
    tarjetaDeCifra({
      etiqueta: 'Apartado en apuestas',
      valor: distintivoDeCreditos(saldo.saldoReservado, { tam: 'grande' }),
      detalle: 'Vuelve si la sala se cancela',
    }),
  );
  return caja;
}

/**
 * Comprueba si un héroe lleva algo puesto.
 *
 * Es la misma regla que aplica la puerta de héroe del servidor
 * (`ClienteInventarioHeroes`): equipado es llevar un arma, una armadura o un
 * ítem. No hay ningún campo `equipado` en `inventario.yaml` — lo único que
 * publica el inventario al respecto es este endpoint.
 *
 * @returns {Promise<boolean|null>} `null` si el inventario no contesta
 */
async function llevaEquipo(idHeroe, fetchImpl) {
  const r = await pedir(
    `/api/v1/inventario/heroes/${encodeURIComponent(idHeroe)}/equipamiento`,
    fetchImpl,
  );
  if (!r.ok) {
    return null;
  }
  const e = r.datos ?? {};
  return (
    (e.armas?.length ?? 0) > 0 ||
    Object.keys(e.armaduras ?? {}).length > 0 ||
    (e.items?.length ?? 0) > 0
  );
}

/**
 * Héroe equipado (HU-SAL-003, `inventario.yaml`): es lo que decide con qué
 * entras al combate, así que es lo primero que hay que poder mirar.
 *
 * ## Por qué esto no era lo que parecía
 *
 * Hasta UX-R2.2 este bloque buscaba `elemento.equipado` y leía
 * `heroe.nombre`. Ninguno de los dos campos existe: `ElementoInventario`
 * declara `nombrePropio` y `disponible`, y «equipado» no es un campo sino el
 * resultado de preguntar por el equipamiento del héroe. Contra la API real el
 * bloque acababa SIEMPRE en «No tienes un héroe equipado», tuviera uno o no.
 * La prueba no lo veía porque su doble devolvía los campos inventados.
 *
 * Se aplica la misma regla que el servidor, sin adivinar otra.
 */
async function bloqueDeHeroe(fetchImpl, alReintentar) {
  const respuesta = await pedir('/api/v1/inventario/elementos?pagina=0', fetchImpl);
  if (!respuesta.ok) {
    return estadoDeError({
      titulo: 'Tu inventario no está disponible',
      detalle: motivoDeIndisponibilidad(respuesta.estado, 'tu héroe equipado'),
      alReintentar,
    });
  }
  const crudos = respuesta.datos?.elementos;
  const elementos = Array.isArray(crudos) ? crudos : [];
  // Solo los primeros: la home no es el inventario, y cada candidato cuesta
  // una llamada más. Quien tenga muchos héroes los gestiona en su vista.
  const candidatos = elementos
    .filter((e) => e.tipo === 'HEROE' && e.disponible !== false)
    .slice(0, 3);

  let heroe = null;
  for (const candidato of candidatos) {
    if (await llevaEquipo(candidato.id, fetchImpl)) {
      heroe = candidato;
      break;
    }
  }

  if (!heroe) {
    return estadoVacio({
      titulo: 'No tienes un héroe equipado',
      detalle: 'Sin héroe equipado no puedes entrar a una batalla.',
      accion: { texto: 'Ir al inventario', href: '../contenido/inventario/inventario.html' },
    });
  }

  // UX-R2.2 — era una tarjeta de texto con la palabra «Héroe» dentro. Ahora es
  // el retrato con marco que el kit tenía dibujado desde el principio.
  return tarjeta({
    titulo: heroe.nombrePropio,
    subtitulo: 'Tu héroe equipado',
    distintivos: [distintivo('Equipado', 'activo')],
    medios: retratoDeHeroe({ nombre: heroe.nombrePropio }, { conNombre: false }),
    acciones: [
      boton({
        texto: 'Ver inventario',
        variante: 'secundario',
        href: '../contenido/inventario/inventario.html',
      }),
    ],
    atributosDeDatos: { zona: 'heroe' },
  });
}

/** Torneo en curso o con inscripciones abiertas (HU-TOR-008, lectura pública). */
async function bloqueDeTorneo(fetchImpl, alReintentar) {
  const respuesta = await pedir('/api/v1/torneos', fetchImpl);
  if (!respuesta.ok) {
    return estadoDeError({
      titulo: 'Los torneos no están disponibles',
      detalle: motivoDeIndisponibilidad(respuesta.estado, 'el torneo'),
      alReintentar,
    });
  }
  // `Array.isArray` y no `?? []`: si el servicio devuelve otra forma, la home
  // lo trata como «no hay torneo» en vez de reventar y dejar el bloque roto.
  const torneos = Array.isArray(respuesta.datos) ? respuesta.datos : [];
  const vivo =
    torneos.find((t) => t.estado === 'EN_CURSO') ??
    torneos.find((t) => t.estado === 'INSCRIPCIONES_ABIERTAS');
  if (!vivo) {
    return estadoVacio({
      titulo: 'No hay ningún torneo abierto',
      detalle: 'Se abre uno cada 91 días. Cuando empiece, aparece aquí.',
      accion: { texto: 'Ver torneos', href: '../plataforma/torneos/torneos.html' },
    });
  }
  const abierto = vivo.estado === 'INSCRIPCIONES_ABIERTAS';
  return tarjeta({
    titulo: vivo.nombre,
    subtitulo: abierto ? 'Inscripciones abiertas' : 'En curso',
    distintivos: [distintivo(abierto ? 'Abierta' : 'En juego', abierto ? 'abierta' : 'en-juego')],
    datos: [
      { etiqueta: 'Equipos', valor: `${vivo.equiposInscritos} de ${vivo.cupos}` },
      { etiqueta: 'Inscripción', valor: formatoCreditos(vivo.costoInscripcion) },
      ...(abierto ? [{ etiqueta: 'Cierra', valor: cuantoFalta(vivo.inscripcionesCierranEn) }] : []),
    ],
    acciones: [
      boton({
        texto: abierto ? 'Inscribir mi equipo' : 'Ver el árbol',
        href: `../plataforma/torneos/torneos.html?torneo=${vivo.id}`,
      }),
    ],
    atributosDeDatos: { zona: 'torneo' },
  });
}

/** Avisos sin leer (HU-NOT-005/006, `notificaciones.yaml`). */
async function bloqueDeAvisos(uid, fetchImpl, alReintentar) {
  const respuesta = await pedir(
    `/api/v1/users/${encodeURIComponent(uid)}/notifications?page=0&size=3`,
    fetchImpl,
  );
  if (!respuesta.ok) {
    return estadoDeError({
      titulo: 'Tus avisos no están disponibles',
      detalle: motivoDeIndisponibilidad(respuesta.estado, 'las notificaciones'),
      alReintentar,
    });
  }
  const bandeja = respuesta.datos?.contenido ?? respuesta.datos?.content;
  const avisos = Array.isArray(bandeja) ? bandeja : [];
  if (avisos.length === 0) {
    return estadoVacio({
      titulo: 'Nada nuevo por ahora',
      detalle: 'Aquí llegan los avisos de tus partidas, sanciones y torneos.',
    });
  }
  const lista = h('ul', { clase: 'pila pila--ajustada', datos: { zona: 'avisos' } });
  for (const aviso of avisos.slice(0, 3)) {
    lista.append(
      h('li', {
        clase: 'tarjeta pila pila--ajustada',
        hijos: [
          h('strong', { texto: aviso.titulo ?? aviso.title ?? 'Aviso' }),
          h('p', { clase: 't-meta', texto: aviso.mensaje ?? aviso.cuerpo ?? '' }),
        ],
      }),
    );
  }
  return lista;
}

/**
 * Monta la home.
 *
 * @param {ParentNode} raiz
 * @param {{sesion: {uid: string|null, apodo: string|null, rol: string|null},
 *          fetchImpl?: Function}} opciones
 */
export function montarHome(raiz, { sesion, fetchImpl = fetchWithHttpErrorInterceptor }) {
  const zonaSaludo = raiz.querySelector('[data-zona="saludo"]');
  const zonas = {
    saldo: raiz.querySelector('[data-zona="bloque-saldo"]'),
    heroe: raiz.querySelector('[data-zona="bloque-heroe"]'),
    torneo: raiz.querySelector('[data-zona="bloque-torneo"]'),
    avisos: raiz.querySelector('[data-zona="bloque-avisos"]'),
  };
  const zonaAccesos = raiz.querySelector('[data-zona="accesos"]');

  if (zonaSaludo) {
    zonaSaludo.textContent = sesion.apodo ? `Hola, ${sesion.apodo}` : 'Hola';
  }

  pintarAccesos(zonaAccesos);

  /** Cada bloque se pide por su cuenta: uno caído no tumba la home. */
  async function cargar(clave, construir) {
    const zona = zonas[clave];
    if (!zona) {
      return;
    }
    pintarEstado(zona, estadoDeCarga({ filas: 2 }));
    const contenido = await construir();
    vaciar(zona).append(contenido);
  }

  function cargarTodo() {
    cargar('saldo', () =>
      bloqueDeSaldo(sesion.uid, fetchImpl, () =>
        cargar('saldo', () => bloqueDeSaldo(sesion.uid, fetchImpl, cargarTodo)),
      ),
    );
    cargar('heroe', () =>
      bloqueDeHeroe(fetchImpl, () => cargar('heroe', () => bloqueDeHeroe(fetchImpl, cargarTodo))),
    );
    cargar('torneo', () =>
      bloqueDeTorneo(fetchImpl, () =>
        cargar('torneo', () => bloqueDeTorneo(fetchImpl, cargarTodo)),
      ),
    );
    cargar('avisos', () =>
      bloqueDeAvisos(sesion.uid, fetchImpl, () =>
        cargar('avisos', () => bloqueDeAvisos(sesion.uid, fetchImpl, cargarTodo)),
      ),
    );
  }

  cargarTodo();
  return { recargar: cargarTodo };
}

/** @param {HTMLElement|null} zona */
function pintarAccesos(zona) {
  if (!zona) {
    return;
  }
  vaciar(zona);
  for (const acceso of ACCESOS) {
    zona.append(
      tarjeta({
        titulo: acceso.titulo,
        subtitulo: acceso.detalle,
        href: acceso.destino,
        atributosDeDatos: { acceso: acceso.id },
      }),
    );
  }
}
