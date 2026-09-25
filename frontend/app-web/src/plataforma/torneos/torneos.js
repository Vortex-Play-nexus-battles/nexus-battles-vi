/**
 * Torneos — HU-TOR-008 (menú «Torneo»), sobre contracts/openapi/torneos.yaml 1.0.0.
 *
 * Una sola vista: el listado de torneos y, al elegir uno, su detalle con
 * los equipos, el árbol de ocho (RF-TOR-004) y las acciones que corresponden
 * a quien mira: el espectador consulta; el jugador registra su equipo y lo
 * inscribe; el administrador crea, inicia y cancela (D-21).
 *
 * Todo error llega como problem details y se decide por `motivo`, nunca
 * comparando textos (`shared/ui-kit/MAPEO-ERRORES.md`).
 */

import { fetchWithHttpErrorInterceptor } from '../../comun/interceptors/http-error.interceptor.js';
import { h, nodo } from '../../comun/ui/dom.js';
import { pintarAviso } from '../../comun/ui/aviso.js';
import { campo } from '../../comun/ui/campo.js';
import { estadoDeCarga, estadoDeError, estadoVacio } from '../../comun/ui/estado-vista.js';
import { pedirTexto } from '../../comun/ui/dialogo.js';
import { distintivo } from '../../comun/ui/distintivo.js';
import { icono } from '../../comun/ui/icono.js';
import { fechaHora } from '../../comun/ui/formato.js';
import { textoDelServidor } from '../../comun/ui/texto-de-fallo.js';

export const ROLES_DE_ADMINISTRACION = Object.freeze(['ADMINISTRADOR', 'SUPER_ADMINISTRADOR']);

/**
 * Nombres de las llaves del árbol, en el orden en que se pintan.
 *
 * UXC-8 — decían «Llave de ganadores (1-6 y 11)» y «Llave de secundarios
 * (7-10, 12 y 13)»: la numeración interna de RF-TOR-004 escrita para quien
 * juega. La de secundarios es la segunda oportunidad: quien pierde una vez
 * baja ahí, y a la segunda derrota queda fuera.
 */
export const LLAVES = Object.freeze([
  ['GANADORES', 'Llave de ganadores'],
  ['SECUNDARIOS', 'Llave de segunda oportunidad'],
  ['FINAL', 'Gran final'],
]);

/** El nombre de una llave suelta, para frases («Llave de ganadores»). */
export function nombreDeLlave(llave) {
  return LLAVES.find(([clave]) => clave === llave)?.[1] ?? 'Encuentro del torneo';
}

/**
 * El nombre de una ronda del árbol, según cuántos encuentros tiene y si es
 * la última de su llave. Con ocho equipos la de ganadores son cuartos,
 * semifinales y su final; la de segunda oportunidad se numera.
 *
 * @param {string} llave
 * @param {number} ronda
 * @param {{cantidad: number, ultima: boolean}} forma
 * @returns {string}
 */
export function nombreDeRonda(llave, ronda, { cantidad, ultima }) {
  if (llave === 'FINAL') {
    return 'Gran final';
  }
  if (llave === 'GANADORES') {
    if (cantidad >= 4) {
      return 'Cuartos de final';
    }
    if (cantidad === 2) {
      return 'Semifinales';
    }
    if (ultima) {
      return 'Final de ganadores';
    }
  }
  if (llave === 'SECUNDARIOS' && ultima && cantidad === 1) {
    return 'Final de segunda oportunidad';
  }
  return ronda > 0 ? `Ronda ${ronda}` : 'Ronda';
}

function baseDeApi() {
  const meta = globalThis.document?.querySelector?.('meta[name="nexus-api-base"]');
  return String(meta?.content ?? '').replace(/\/+$/, '');
}

/**
 * UXC-9 — lo que se le dice a quien juega por cada `motivo` estable del
 * contrato: qué pasó y qué hacer. El texto del servidor solo se usa si el
 * motivo no está aquí y si se puede leer (`textoDelServidor`).
 */
export const MOTIVOS = Object.freeze({
  VENTANA_DE_91_DIAS: {
    titulo: 'Todavía no se puede crear otro torneo',
    detalle: 'Se abre un torneo cada 91 días y ya hay uno dentro de esa ventana.',
  },
  PERMISO_INSUFICIENTE: {
    titulo: 'Tu cuenta no puede hacer esto',
    detalle:
      'Solo la administración crea, inicia o cancela torneos; solo el capitán cambia a su compañero.',
  },
  ESTADO_NO_PERMITE: {
    titulo: 'El torneo ya cambió de fase',
    detalle:
      'Las inscripciones se cerraron o el torneo ya empezó. Vuelve a abrirlo para ver cómo está.',
  },
  JUGADOR_YA_EN_EQUIPO: {
    titulo: 'Ya estás en un equipo de este torneo',
    detalle: 'Cada jugador va en un solo equipo por torneo. Revisa tu equipo en el detalle.',
  },
  NOMBRE_RECHAZADO: {
    titulo: 'Ese nombre o ese avatar no se admiten',
    detalle: 'Llevan palabras que el juego no permite en nombres públicos. Elige otros.',
  },
  LISTA_NEGRA_NO_DISPONIBLE: {
    titulo: 'No pudimos revisar el nombre ahora',
    detalle: 'Sin esa revisión no se registra ningún equipo. Inténtalo de nuevo en un momento.',
  },
  CUPO_AGOTADO: {
    titulo: 'El torneo está completo',
    detalle: 'Ya hay ocho equipos inscritos. Espera al siguiente torneo.',
  },
  YA_INSCRITO: {
    titulo: 'Tu equipo ya está inscrito',
    detalle: 'No hace falta volver a inscribirlo: su posición está en el árbol.',
  },
  CREDITOS_INSUFICIENTES: {
    titulo: 'No te alcanzan los créditos',
    detalle:
      'La inscripción se paga en créditos al inscribir al equipo. Consigue más y vuelve a intentarlo.',
  },
  INTEGRANTE_SANCIONADO: {
    titulo: 'Un integrante tiene una sanción activa',
    detalle: 'Con una sanción activa no se puede inscribir el equipo. Revisa «Mis sanciones».',
  },
  LIBRO_NO_DISPONIBLE: {
    titulo: 'Los créditos no responden ahora',
    detalle: 'No se cobró ni se reservó nada. Inténtalo de nuevo en un momento.',
  },
  SANCIONES_NO_DISPONIBLES: {
    titulo: 'No pudimos revisar las sanciones',
    detalle: 'Sin esa revisión no se inscribe ningún equipo. Inténtalo de nuevo en un momento.',
  },
  SIN_EQUIPOS: {
    titulo: 'No hay equipos inscritos',
    detalle: 'El torneo no arranca sin al menos un equipo de jugadores inscrito.',
  },
  NO_ENCONTRADO: {
    titulo: 'Ese torneo ya no está',
    detalle: 'Puede que lo hayan cancelado. Vuelve al listado de torneos.',
  },
});

export class ErrorDeTorneos extends Error {
  constructor(problema, estado) {
    const motivo = problema?.motivo ?? null;
    const conocido = motivo ? MOTIVOS[motivo] : null;
    const legible = textoDelServidor(problema, estado, '');
    const respaldo =
      estado >= 500
        ? 'Los torneos no responden ahora mismo. Vuelve a intentarlo en un momento.'
        : 'No se pudo completar. Vuelve a intentarlo.';
    const detalle = conocido?.detalle ?? (legible || respaldo);
    super(detalle);
    this.name = 'ErrorDeTorneos';
    this.estado = estado;
    this.titulo = conocido?.titulo ?? 'No se pudo completar';
    this.detalle = detalle;
    this.motivo = motivo;
    this.proximaFechaPosible = problema?.proximaFechaPosible ?? null;
  }
}

async function pedir(ruta, opciones = {}, fetchImpl = fetchWithHttpErrorInterceptor) {
  const respuesta = await fetchImpl(`${baseDeApi()}${ruta}`, {
    ...opciones,
    headers: { Accept: 'application/json', ...(opciones.headers ?? {}) },
  });
  if (respuesta.ok) {
    return respuesta.status === 204 ? null : respuesta.json();
  }
  let problema = null;
  try {
    problema = await respuesta.json();
  } catch {
    problema = null;
  }
  throw new ErrorDeTorneos(problema, respuesta.status);
}

const json = (cuerpo, method = 'POST') => ({
  method,
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(cuerpo),
});

export const api = {
  listar: (f) => pedir('/api/v1/torneos', {}, f),
  obtener: (id, f) => pedir(`/api/v1/torneos/${encodeURIComponent(id)}`, {}, f),
  crear: (cuerpo, f) => pedir('/api/v1/torneos', json(cuerpo), f),
  cancelar: (id, motivo, f) =>
    pedir(`/api/v1/torneos/${encodeURIComponent(id)}/cancelacion`, json({ motivo }), f),
  crearEquipo: (id, cuerpo, f) =>
    pedir(`/api/v1/torneos/${encodeURIComponent(id)}/equipos`, json(cuerpo), f),
  inscribir: (id, equipoId, f) =>
    pedir(
      `/api/v1/torneos/${encodeURIComponent(id)}/equipos/${encodeURIComponent(equipoId)}/inscripcion`,
      { method: 'POST' },
      f,
    ),
  iniciar: (id, f) =>
    pedir(`/api/v1/torneos/${encodeURIComponent(id)}/inicio`, { method: 'POST' }, f),
};

/* ---- Presentación (puro, probado) ---- */

export const ESTADOS = Object.freeze({
  INSCRIPCIONES_ABIERTAS: 'Inscripciones abiertas',
  EN_CURSO: 'En curso',
  FINALIZADO: 'Finalizado',
  CANCELADO: 'Cancelado',
});

/** @returns {string} texto del estado y de la ocupación de un torneo */
export function resumenDe(torneo) {
  const estado = ESTADOS[torneo.estado] ?? torneo.estado;
  const cupos = `${torneo.equiposInscritos} de ${torneo.cupos} equipos`;
  const costo = torneo.costoInscripcion > 0 ? `${torneo.costoInscripcion} créditos` : 'gratuito';
  return `${estado} · ${cupos} · ${costo}`;
}

/** El equipo del jugador en ese torneo, si lo tiene. */
export function miEquipo(torneo, uid) {
  return torneo.equipos.find((e) => !e.ia && e.integrantes.includes(uid)) ?? null;
}

/**
 * Qué puede hacer quien mira (CA-03 de HU-TOR-008: los botones lo dicen).
 *
 * @returns {{crearEquipo: boolean, inscribir: boolean, motivo: string}}
 */
export function accionesDe(torneo, uid) {
  if (!uid) {
    return { crearEquipo: false, inscribir: false, motivo: 'Inicia sesión para inscribirte.' };
  }
  if (torneo.estado !== 'INSCRIPCIONES_ABIERTAS') {
    return { crearEquipo: false, inscribir: false, motivo: 'Las inscripciones están cerradas.' };
  }
  const equipo = miEquipo(torneo, uid);
  if (!equipo) {
    if (torneo.equiposInscritos >= torneo.cupos) {
      return { crearEquipo: false, inscribir: false, motivo: 'Cupo agotado.' };
    }
    return { crearEquipo: true, inscribir: false, motivo: 'Registra tu equipo de dos.' };
  }
  if (equipo.inscrito) {
    return {
      crearEquipo: false,
      inscribir: false,
      motivo: `Ya estás inscrito con «${equipo.nombre}» (posición ${equipo.posicion}).`,
    };
  }
  return {
    crearEquipo: false,
    inscribir: true,
    motivo: `Tu equipo «${equipo.nombre}» falta por inscribirse.`,
  };
}

/** Nombre corto de un equipo por id (o «por definir»). */
export function nombreDe(torneo, equipoId) {
  if (!equipoId) {
    return 'por definir';
  }
  const equipo = torneo.equipos.find((e) => e.id === equipoId);
  // UXC-9 — antes, los ocho primeros caracteres del identificador.
  return equipo?.nombre || 'Equipo sin nombre';
}

/** Los encuentros de una llave, en orden. */
export function encuentrosDe(torneo, llave) {
  return torneo.encuentros.filter((e) => e.llave === llave).sort((a, b) => a.numero - b.numero);
}

/* ---- DOM ---- */

function avisarError(zona, error) {
  const deNegocio = error instanceof ErrorDeTorneos;
  let detalle = deNegocio ? error.detalle : 'Revisa tu conexión e inténtalo de nuevo.';
  if (deNegocio && error.proximaFechaPosible) {
    detalle += ` Próxima fecha posible: ${new Date(error.proximaFechaPosible).toLocaleDateString('es-CO')}.`;
  }
  pintarAviso(zona, {
    tono: deNegocio && error.estado < 500 ? 'advertencia' : 'error',
    titulo: deNegocio ? error.titulo : 'No pudimos conectar con los torneos',
    detalle,
  });
}

export function tarjetaDeTorneo(torneo, { alAbrir } = {}) {
  // `.tarjeta--pulsable` esta documentada en el kit como «tarjeta que ademas
  // es un boton o un enlace» y pone `cursor: pointer` sobre toda la tarjeta.
  // Aqui estaba puesta sobre un `<article>` sin manejador: el cursor cambiaba
  // a mano en toda la superficie y solo funcionaba el boton pequeno de abajo.
  // Una afordancia que miente es peor que ninguna.
  const tarjeta = nodo('article', 'tarjeta pila pila--compacta');
  tarjeta.dataset.torneoId = torneo.id;
  tarjeta.dataset.estado = torneo.estado;
  const cabecera = nodo('div', 'torneo__tarjeta-cabecera');
  cabecera.append(nodo('strong', 'tarjeta__titulo', torneo.nombre), distintivoDeTorneo(torneo));
  tarjeta.appendChild(cabecera);
  tarjeta.appendChild(nodo('p', 't-cuerpo', resumenDe(torneo)));
  // UXC-8 — la tarjeta decía «Inscripciones hasta…» también de un torneo
  // terminado o cancelado. Ahora dice lo que toca a cada fase.
  tarjeta.appendChild(nodo('p', 't-meta', faseDe(torneo)));
  const boton = nodo('button', 'boton boton--secundario boton--pequeno', 'Ver torneo');
  boton.type = 'button';
  boton.dataset.accion = 'abrir';
  boton.addEventListener('click', () => alAbrir?.(torneo));
  tarjeta.appendChild(boton);
  return tarjeta;
}

/** El distintivo de la fase, con variante del kit: el color no va solo. */
export function distintivoDeTorneo(torneo) {
  const variante = {
    INSCRIPCIONES_ABIERTAS: 'abierta',
    EN_CURSO: 'en-juego',
    FINALIZADO: 'completada',
    CANCELADO: 'bloqueada',
  }[torneo.estado];
  return distintivo(ESTADOS[torneo.estado] ?? 'Torneo', variante ?? null);
}

/**
 * Lo que toca decir de un torneo según su fase.
 *
 * @param {object} torneo `TorneoResumen`
 * @returns {string}
 */
export function faseDe(torneo) {
  switch (torneo.estado) {
    case 'INSCRIPCIONES_ABIERTAS':
      return `Inscripciones hasta ${fechaHora(torneo.inscripcionesCierranEn)}`;
    case 'EN_CURSO':
      return 'Se está jugando: abre el torneo para ver el árbol y los resultados.';
    case 'FINALIZADO':
      return torneo.campeonEquipoId
        ? 'Terminado y con campeón: abre el torneo para ver quién ganó.'
        : 'Terminado.';
    case 'CANCELADO':
      return 'Cancelado: las inscripciones se devolvieron.';
    default:
      return '';
  }
}

export function tarjetaDeEquipo(torneo, equipo, uid) {
  const tarjeta = nodo('article', 'tarjeta pila pila--ajustada');
  tarjeta.dataset.equipoId = equipo.id;
  if (equipo.ia) {
    tarjeta.dataset.ia = 'true';
  }
  const titulo = nodo('strong', 'tarjeta__titulo', equipo.nombre);
  if (uid && equipo.integrantes.includes(uid)) {
    titulo.textContent += ' (tu equipo)';
  }
  tarjeta.appendChild(titulo);
  let estado = 'Registrado, sin inscribir';
  if (equipo.ia) {
    estado = 'Equipo de la máquina';
  } else if (equipo.inscrito) {
    estado = `Inscrito · posición ${equipo.posicion}`;
  }
  tarjeta.appendChild(nodo('p', 't-meta', estado));
  if (torneo.estado !== 'INSCRIPCIONES_ABIERTAS') {
    tarjeta.appendChild(
      nodo('p', 't-meta', equipo.eliminado ? 'Eliminado' : `Derrotas: ${equipo.derrotas}`),
    );
  }
  return tarjeta;
}

/**
 * HU-TOR-004 CA-04: un encuentro LISTO en el que juega mi equipo se juega en
 * una sala de batalla vinculada; al terminar, salas-partidas informa el
 * ganador a torneos. Aqui solo se decide si mostrar el acceso.
 *
 * @returns {boolean}
 */
export function puedoJugar(torneo, encuentro, uid) {
  if (encuentro.estado !== 'LISTO' || torneo.estado !== 'EN_CURSO') {
    return false;
  }
  const equipo = miEquipo(torneo, uid);
  return Boolean(equipo) && (equipo.id === encuentro.equipoA || equipo.id === encuentro.equipoB);
}

/** Ruta relativa de crear sala con el encuentro prefijado (misma carpeta `plataforma/`). */
export function rutaDeSalaDelEncuentro(torneo, encuentro) {
  const parametros = new URLSearchParams({
    torneo: torneo.id,
    encuentro: String(encuentro.numero),
  });
  return `../salas-partidas/crear-sala.html?${parametros}`;
}

/** Estados del encuentro, en palabras y con la variante del componente. */
const ESTADO_DEL_ENCUENTRO = Object.freeze({
  PENDIENTE: { texto: 'Pendiente', clase: '' },
  LISTO: { texto: 'Listo para jugarse', clase: ' encuentro--en-curso' },
  JUGADO: { texto: 'Jugado', clase: ' encuentro--finalizado' },
});

/**
 * Un encuentro del arbol, con el componente `Encuentro` del sistema de diseno.
 *
 * **Por que cambio.** El kit trae ese componente completo desde el Figma —
 * cabecera con el estado, una fila por equipo, el ganador distinguido por
 * **peso** de letra y no solo por color (`.encuentro__equipo--ganador`)— y
 * esta vista lo ignoraba: ponia la clase `.encuentro` sobre un `<li>` y le
 * metia una frase corrida, «Encuentro 5: Los Dragones vs Los Lobos → gana Los
 * Dragones», dentro de una caja de 220 px pensada para dos filas. El resultado
 * era un arbol de doble eliminacion imposible de leer de un vistazo.
 *
 * El marcador (`.encuentro__marcador`) se queda fuera a proposito: el contrato
 * no trae puntuacion por encuentro, y un hueco vacio es mas honesto que un
 * cero inventado.
 *
 * @param {object} torneo
 * @param {object} encuentro esquema `Encuentro` del contrato
 * @param {string|null} [uid] quien mira, para ofrecerle jugar el suyo
 * @returns {HTMLElement}
 */
export function tarjetaDeEncuentro(torneo, encuentro, uid = null) {
  const estado = ESTADO_DEL_ENCUENTRO[encuentro.estado] ?? { texto: encuentro.estado, clase: '' };
  const mio = uid ? miEquipo(torneo, uid) : null;
  const juegaMiEquipo = Boolean(mio) && [encuentro.equipoA, encuentro.equipoB].includes(mio.id);
  const tarjeta = nodo(
    'article',
    `encuentro${estado.clase}${juegaMiEquipo ? ' encuentro--mio' : ''}`,
  );
  tarjeta.dataset.numero = String(encuentro.numero);
  tarjeta.dataset.estado = encuentro.estado;

  const cabecera = nodo('header', 'encuentro__cabecera');
  cabecera.appendChild(
    nodo('span', undefined, encuentro.numero === 14 ? 'Final' : `Encuentro ${encuentro.numero}`),
  );
  cabecera.appendChild(nodo('span', undefined, estado.texto));
  tarjeta.appendChild(cabecera);

  for (const lado of ['equipoA', 'equipoB']) {
    const id = encuentro[lado];
    const gana = Boolean(encuentro.ganador) && id === encuentro.ganador;
    const fila = nodo('div', `encuentro__equipo${gana ? ' encuentro__equipo--ganador' : ''}`);
    fila.dataset.lado = lado === 'equipoA' ? 'a' : 'b';
    fila.appendChild(nodo('span', undefined, nombreDe(torneo, id)));
    if (mio && id === mio.id) {
      // UXC-8 — «Mi camino» en el árbol: tu equipo se dice con texto, no solo
      // con el borde del encuentro.
      fila.appendChild(nodo('span', 'encuentro__tuyo', 'Tu equipo'));
    }
    if (gana) {
      // El peso de la letra solo lo ve quien mira la pantalla: para un lector
      // de pantalla hay que decirlo.
      fila.appendChild(nodo('span', 'solo-lectores', ' (gana este encuentro)'));
    }
    tarjeta.appendChild(fila);
  }

  if (puedoJugar(torneo, encuentro, uid)) {
    const pie = nodo('div', 'encuentro__equipo');
    const enlace = nodo('a', 'boton boton--primario boton--pequeno', 'Crear sala del encuentro');
    enlace.href = rutaDeSalaDelEncuentro(torneo, encuentro);
    enlace.dataset.accion = 'jugar-encuentro';
    pie.appendChild(enlace);
    tarjeta.appendChild(pie);
  }
  return tarjeta;
}

/**
 * Los encuentros en los que juega un equipo, en orden de número.
 *
 * @param {object} torneo
 * @param {string} equipoId
 */
export function encuentrosDeEquipo(torneo, equipoId) {
  return torneo.encuentros
    .filter((e) => e.equipoA === equipoId || e.equipoB === equipoId)
    .sort((a, b) => a.numero - b.numero);
}

/**
 * El próximo encuentro de un equipo: el primero que no se ha jugado.
 *
 * @returns {object|null}
 */
export function proximoEncuentro(torneo, equipoId) {
  return encuentrosDeEquipo(torneo, equipoId).find((e) => e.estado !== 'JUGADO') ?? null;
}

/**
 * Dónde está tu equipo, en una frase.
 *
 * @param {object} torneo
 * @param {object} equipo
 * @returns {string}
 */
export function situacionDeEquipo(torneo, equipo) {
  if (torneo.campeonEquipoId && torneo.campeonEquipoId === equipo.id) {
    return 'Campeón del torneo';
  }
  if (equipo.eliminado) {
    return 'Eliminado tras dos derrotas';
  }
  if (torneo.estado === 'INSCRIPCIONES_ABIERTAS') {
    return equipo.inscrito
      ? `Inscrito · posición ${equipo.posicion} del árbol`
      : 'Registrado, falta inscribirlo';
  }
  if (torneo.estado === 'EN_CURSO') {
    return equipo.derrotas > 0
      ? 'Sigue en juego, en la llave de segunda oportunidad'
      : 'Sigue en juego, sin derrotas';
  }
  return equipo.inscrito ? 'Participó en el torneo' : 'No llegó a inscribirse';
}

/**
 * «Tu torneo» — UXC-8: Mi equipo, Próximo encuentro y Mi camino.
 *
 * Solo con lo que trae el contrato: el compañero es un identificador sin
 * apodo, así que se dice «tu compañero»; el marcador no existe, así que el
 * camino dice victoria o derrota y contra quién.
 *
 * @param {object} torneo
 * @param {object} equipo el de quien mira
 * @param {string} uid
 * @returns {HTMLElement}
 */
export function panelDeMiTorneo(torneo, equipo, uid) {
  const soyCapitan = equipo.capitanUid === uid;
  const conCompanero = equipo.integrantes.length > 1;

  const bloqueEquipo = h('article', {
    clase: 'torneo-mio__bloque pila pila--ajustada',
    datos: { zona: 'mi-equipo' },
    hijos: [
      h('h4', { clase: 't-etiqueta', texto: 'Mi equipo' }),
      h('p', {
        clase: 'torneo-mio__equipo',
        hijos: [
          h('span', {
            clase: 'torneo-mio__inicial',
            texto: (equipo.nombre || '?').trim().charAt(0).toUpperCase(),
            atributos: { 'aria-hidden': 'true' },
          }),
          h('strong', { texto: equipo.nombre }),
        ],
      }),
      h('p', {
        clase: 't-meta',
        texto: `${soyCapitan ? 'Tú (capitán)' : 'Tú'}${conCompanero ? ` y ${soyCapitan ? 'tu compañero' : 'tu capitán'}` : ', sin compañero todavía'}`,
      }),
      h('p', {
        clase: 't-cuerpo',
        datos: { zona: 'situacion' },
        texto: situacionDeEquipo(torneo, equipo),
      }),
    ],
  });

  const siguiente = proximoEncuentro(torneo, equipo.id);
  const bloqueProximo = h('article', {
    clase: 'torneo-mio__bloque pila pila--ajustada',
    datos: { zona: 'proximo-encuentro' },
    hijos: [h('h4', { clase: 't-etiqueta', texto: 'Próximo encuentro' })],
  });
  if (siguiente && torneo.estado === 'EN_CURSO') {
    const rival = siguiente.equipoA === equipo.id ? siguiente.equipoB : siguiente.equipoA;
    bloqueProximo.append(
      h('p', {
        clase: 't-cuerpo',
        texto: rival ? `Contra ${nombreDe(torneo, rival)}` : 'Rival por decidir',
      }),
      h('p', {
        clase: 't-meta',
        texto: `${siguiente.numero === 14 ? 'Gran final' : `Encuentro ${siguiente.numero}`} · ${nombreDeLlave(siguiente.llave)}`,
      }),
    );
    if (puedoJugar(torneo, siguiente, uid)) {
      const enlace = nodo('a', 'boton boton--primario boton--pequeno', 'Crear sala del encuentro');
      enlace.href = rutaDeSalaDelEncuentro(torneo, siguiente);
      enlace.dataset.accion = 'jugar-mi-encuentro';
      bloqueProximo.append(enlace);
    } else {
      bloqueProximo.append(
        h('p', {
          clase: 't-meta',
          texto: 'Se podrá jugar cuando el otro encuentro decida a tu rival.',
        }),
      );
    }
  } else {
    let texto = 'El árbol se genera al cerrar las inscripciones: aquí verás tu primer rival.';
    if (torneo.estado === 'EN_CURSO' && equipo.eliminado) {
      texto = 'Tu equipo ya no juega más encuentros en este torneo.';
    } else if (torneo.estado === 'FINALIZADO') {
      texto = 'El torneo terminó: no quedan encuentros por jugar.';
    } else if (torneo.estado === 'CANCELADO') {
      texto = 'El torneo se canceló.';
    } else if (!equipo.inscrito) {
      texto = 'Inscribe a tu equipo para entrar en el árbol.';
    }
    bloqueProximo.append(h('p', { clase: 't-meta', texto }));
  }

  const jugados = encuentrosDeEquipo(torneo, equipo.id).filter((e) => e.estado === 'JUGADO');
  const bloqueCamino = h('article', {
    clase: 'torneo-mio__bloque pila pila--ajustada',
    datos: { zona: 'mi-camino' },
    hijos: [h('h4', { clase: 't-etiqueta', texto: 'Mi camino' })],
  });
  if (jugados.length === 0) {
    bloqueCamino.append(
      h('p', { clase: 't-meta', texto: 'Todavía no has jugado ningún encuentro de este torneo.' }),
    );
  } else {
    const lista = h('ol', { clase: 'torneo-mio__camino' });
    for (const encuentro of jugados) {
      const rival = encuentro.equipoA === equipo.id ? encuentro.equipoB : encuentro.equipoA;
      const gano = encuentro.ganador === equipo.id;
      lista.append(
        h('li', {
          clase: `torneo-mio__paso torneo-mio__paso--${gano ? 'victoria' : 'derrota'}`,
          datos: { numero: String(encuentro.numero) },
          hijos: [
            icono(gano ? 'check' : 'cerrar', { clase: 'icono icono--menudo' }),
            h('span', {
              texto: `${gano ? 'Victoria' : 'Derrota'} contra ${nombreDe(torneo, rival)}`,
            }),
            h('span', {
              clase: 't-meta',
              texto: ` · ${encuentro.numero === 14 ? 'Gran final' : nombreDeLlave(encuentro.llave)}`,
            }),
          ],
        }),
      );
    }
    bloqueCamino.append(lista);
  }

  return h('section', {
    clase: 'tarjeta torneo-mio pila pila--compacta',
    datos: { zona: 'mi-torneo' },
    atributos: { 'aria-label': 'Tu torneo' },
    hijos: [
      h('h3', { texto: 'Tu torneo' }),
      h('div', {
        clase: 'torneo-mio__rejilla',
        hijos: [bloqueEquipo, bloqueProximo, bloqueCamino],
      }),
    ],
  });
}

/**
 * La transmisión — UXC-8, dicha como es: el contrato no la tiene todavía
 * (RF-TOR-006). Se usa el marco `.transmision` del kit en su variante sin
 * señal, con qué pasa, por qué y cómo seguir el torneo mientras tanto.
 *
 * @param {object} torneo
 * @param {{alActualizar?: Function}} [opciones]
 * @returns {HTMLElement}
 */
export function panelDeTransmision(torneo, { alActualizar } = {}) {
  const enCurso = torneo.estado === 'EN_CURSO';
  const actualizar =
    enCurso && alActualizar
      ? nodo('button', 'boton boton--secundario boton--pequeno', 'Actualizar resultados')
      : null;
  if (actualizar) {
    actualizar.type = 'button';
    actualizar.dataset.accion = 'actualizar-torneo';
    actualizar.addEventListener('click', () => alActualizar());
  }
  return h('section', {
    clase: 'transmision transmision--sin-senal',
    datos: { zona: 'transmision' },
    atributos: { 'aria-label': 'Transmisión del torneo' },
    hijos: [
      h('div', {
        clase: 'transmision__lienzo',
        hijos: [
          h('span', { clase: 'transmision__etiqueta', texto: 'Sin transmisión' }),
          icono('transmision', { clase: 'icono transmision__simbolo' }),
        ],
      }),
      h('div', {
        clase: 'transmision__comentarios pila pila--ajustada',
        hijos: [
          h('strong', {
            texto: enCurso
              ? 'Este torneo no se transmite en vivo'
              : 'Este torneo no tiene grabación',
          }),
          h('p', {
            clase: 't-meta',
            texto: enCurso
              ? 'Los encuentros todavía no se pueden ver desde el juego. Sigue el torneo en el árbol: cada resultado aparece al actualizar.'
              : 'Los encuentros no se grabaron. El árbol de abajo cuenta cómo terminó cada uno.',
          }),
          actualizar,
        ],
      }),
    ],
  });
}

/**
 * Los encuentros de una llave agrupados por ronda, que es lo que da forma al
 * arbol. Una lista plana de catorce encuentros no se parece a un cuadro de
 * doble eliminacion; el contrato ya trae `ronda` y no se estaba usando.
 *
 * @param {Array<object>} encuentros ya filtrados por llave y ordenados
 * @returns {Array<{ronda: number, encuentros: Array<object>}>}
 */
export function porRonda(encuentros) {
  const rondas = new Map();
  for (const encuentro of encuentros) {
    const clave = Number.isInteger(encuentro.ronda) ? encuentro.ronda : 0;
    if (!rondas.has(clave)) {
      rondas.set(clave, []);
    }
    rondas.get(clave).push(encuentro);
  }
  return [...rondas.entries()]
    .sort((a, b) => a[0] - b[0])
    .map(([ronda, lista]) => ({ ronda, encuentros: lista }));
}

/**
 * Monta la vista completa.
 *
 * @param {ParentNode} raiz zonas `[data-zona=...]`: aviso, listado, detalle, crear-torneo
 * @param {{uid?: string|null, rol?: string|null, fetchImpl?: Function, torneoInicial?: string|null}} [opciones]
 */
export function montarTorneos(
  raiz,
  { uid = null, rol = null, fetchImpl, torneoInicial = null } = {},
) {
  const zonaAviso = raiz.querySelector('[data-zona="aviso"]');
  const zonaListado = raiz.querySelector('[data-zona="listado"]');
  const zonaDetalle = raiz.querySelector('[data-zona="detalle"]');
  const formCrear = raiz.querySelector('[data-zona="crear-torneo"]');
  const administra = ROLES_DE_ADMINISTRACION.includes(rol);

  if (formCrear) {
    formCrear.hidden = !administra;
  }

  async function cargarListado() {
    try {
      zonaListado.replaceChildren(estadoDeCarga({ filas: 2, etiqueta: 'Buscando torneos…' }));
      const torneos = await api.listar(fetchImpl);
      zonaListado.replaceChildren();

      // UX-R2.7 — el vacio era un parrafo gris de una linea: «Todavia no hay
      // torneos publicados». Cierto y completamente inutil — el jugador se
      // queda mirando una pantalla con un titulo y nada mas, sin saber que
      // hacer ni cuando volver. Ahora se dice que NO hay torneo, que el
      // formato es por temporadas, y se ofrece lo unico que si puede hacer
      // ahora mismo: jugar una sala.
      //
      // No se inventa una fecha: `torneos.yaml` no publica cuando empieza el
      // siguiente, y poner «vuelve en X dias» seria adivinarlo.
      if (torneos.length === 0) {
        zonaListado.appendChild(
          estadoVacio({
            titulo: 'No hay ningún torneo abierto',
            detalle:
              'Los torneos se abren por temporadas. Cuando haya uno, aparecerá aquí con sus ' +
              'equipos, sus cupos y el árbol de encuentros.',
            accion: {
              texto: 'Jugar una batalla',
              href: '../salas-partidas/batallas.html',
            },
          }),
        );
        return;
      }
      torneos.forEach((t) =>
        zonaListado.appendChild(tarjetaDeTorneo(t, { alAbrir: (x) => abrir(x.id) })),
      );
    } catch (error) {
      // El fallo se pinta DONDE iban los torneos, no solo en el aviso de
      // arriba: hasta ahora quedaba un encabezado «Torneos» huerfano con
      // setecientos pixeles de vacio debajo, y el unico rastro del problema
      // era una caja amarilla que decia «No se pudo completar» sin mas.
      zonaListado.replaceChildren(
        estadoDeError({
          titulo: 'Los torneos no están disponibles',
          // El detalle del servidor solo si trae uno util; si no, el motivo en
          // el idioma del producto. Sin detalle, la tarjeta quedaba con un
          // titulo y un boton y ninguna razon (§17).
          detalle:
            (error instanceof ErrorDeTorneos && error.estado < 500 && error.detalle) ||
            'El servicio de torneos no responde ahora mismo. Vuelve a intentarlo en un momento.',
          alReintentar: () => cargarListado(),
        }),
      );
      // UX-R3.6 — y NO se avisa tambien arriba. El aviso flotante es para los
      // fallos de una accion (inscribir un equipo, abrir un torneo), donde el
      // contenido sigue siendo valido y hay que decir que fallo lo que se
      // acaba de pulsar. Cuando lo que falla es la carga del listado, el
      // propio listado ya lo dice, con su motivo y su boton de reintentar; el
      // aviso solo anadia una caja amarilla con «No se pudo completar» y nada
      // mas, encima del mensaje bueno. Dos avisos del mismo fallo, y el peor
      // primero.
      console.warn('[torneos] no se pudo cargar el listado:', error);
    }
  }

  async function abrir(torneoId) {
    try {
      const torneo = await api.obtener(torneoId, fetchImpl);
      pintarDetalle(torneo);
    } catch (error) {
      avisarError(zonaAviso, error);
    }
  }

  function pintarDetalle(torneo) {
    zonaDetalle.replaceChildren();
    zonaDetalle.hidden = false;
    zonaDetalle.dataset.torneoId = torneo.id;
    zonaDetalle.dataset.estado = torneo.estado;
    zonaDetalle.appendChild(nodo('h2', undefined, torneo.nombre));
    zonaDetalle.appendChild(nodo('p', 't-cuerpo', resumenDe(torneo)));
    if (torneo.campeonEquipoId) {
      const campeon = nodo(
        'p',
        'aviso aviso--exito',
        `Campeón: ${nombreDe(torneo, torneo.campeonEquipoId)}`,
      );
      campeon.dataset.zona = 'campeon';
      zonaDetalle.appendChild(campeon);
    }
    if (torneo.motivoCancelacion) {
      zonaDetalle.appendChild(nodo('p', 't-meta', `Cancelado: ${torneo.motivoCancelacion}`));
    }

    // Acciones del jugador (HU-TOR-003 / HU-TOR-002).
    const acciones = accionesDe(torneo, uid);
    const zonaAcciones = nodo('div', 'pila pila--compacta');
    zonaAcciones.dataset.zona = 'acciones';
    zonaAcciones.appendChild(nodo('p', 't-meta', acciones.motivo));
    if (acciones.crearEquipo) {
      zonaAcciones.appendChild(formularioDeEquipo(torneo));
    }
    if (acciones.inscribir) {
      const equipo = miEquipo(torneo, uid);
      const boton = nodo(
        'button',
        'boton boton--primario',
        torneo.costoInscripcion > 0
          ? `Inscribir «${equipo.nombre}» por ${torneo.costoInscripcion} créditos`
          : `Inscribir «${equipo.nombre}»`,
      );
      boton.type = 'button';
      boton.dataset.accion = 'inscribir';
      boton.addEventListener('click', async () => {
        boton.disabled = true;
        try {
          const inscrito = await api.inscribir(torneo.id, equipo.id, fetchImpl);
          pintarAviso(zonaAviso, {
            tono: 'exito',
            titulo: 'Inscripción confirmada',
            detalle: `Tu equipo ocupa la posición ${inscrito.posicion} del árbol.`,
          });
          await abrir(torneo.id);
          await cargarListado();
        } catch (error) {
          avisarError(zonaAviso, error);
          boton.disabled = false;
        }
      });
      zonaAcciones.appendChild(boton);
    }
    zonaDetalle.appendChild(zonaAcciones);

    // Acciones del administrador (HU-TOR-001 / HU-ADM-005).
    if (administra && torneo.estado === 'INSCRIPCIONES_ABIERTAS') {
      const zonaAdmin = nodo('div', 'fila');
      zonaAdmin.dataset.zona = 'administracion';
      const iniciar = nodo('button', 'boton boton--acento', 'Cerrar inscripciones e iniciar');
      iniciar.type = 'button';
      iniciar.dataset.accion = 'iniciar';
      iniciar.addEventListener('click', async () => {
        iniciar.disabled = true;
        try {
          await api.iniciar(torneo.id, fetchImpl);
          pintarAviso(zonaAviso, {
            tono: 'exito',
            titulo: 'Torneo iniciado',
            detalle: 'Las posiciones vacías se completaron con la máquina y el árbol está listo.',
          });
          await abrir(torneo.id);
          await cargarListado();
        } catch (error) {
          avisarError(zonaAviso, error);
          iniciar.disabled = false;
        }
      });
      const cancelar = nodo('button', 'boton boton--peligro', 'Cancelar torneo');
      cancelar.type = 'button';
      cancelar.dataset.accion = 'cancelar';
      cancelar.addEventListener('click', async () => {
        // UXC-7/9 — era `globalThis.prompt?.()`: el diálogo del navegador, sin
        // estilo ni foco gestionado, y el guardián no lo veía por el `?.`.
        const motivo =
          (await pedirTexto({
            titulo: `¿Cancelar «${torneo.nombre}»?`,
            etiqueta: 'Motivo de la cancelación',
            pista: 'Lo verán los equipos. Se devuelven todas las inscripciones.',
            textoConfirmar: 'Cancelar el torneo',
            textoCancelar: 'Volver',
            maximo: 500,
          })) ?? '';
        if (!motivo.trim()) {
          return;
        }
        try {
          await api.cancelar(torneo.id, motivo.trim(), fetchImpl);
          pintarAviso(zonaAviso, {
            tono: 'info',
            titulo: 'Torneo cancelado',
            detalle: 'Las inscripciones se devolvieron.',
          });
          await abrir(torneo.id);
          await cargarListado();
        } catch (error) {
          avisarError(zonaAviso, error);
        }
      });
      zonaAdmin.append(iniciar, cancelar);
      zonaDetalle.appendChild(zonaAdmin);
    }

    // UXC-8 — lo tuyo primero: tu equipo, tu próximo encuentro y tu camino.
    const mio = uid ? miEquipo(torneo, uid) : null;
    if (mio) {
      zonaDetalle.appendChild(panelDeMiTorneo(torneo, mio, uid));
    }
    // UXC-8 — la transmisión, dicha como es: todavía no hay (RF-TOR-006).
    if (torneo.estado === 'EN_CURSO' || torneo.estado === 'FINALIZADO') {
      zonaDetalle.appendChild(panelDeTransmision(torneo, { alActualizar: () => abrir(torneo.id) }));
    }

    // Equipos (CA-01/02 de HU-TOR-008).
    const equipos = nodo('section', 'pila pila--compacta');
    equipos.dataset.zona = 'equipos';
    equipos.appendChild(nodo('h3', undefined, `Equipos (${torneo.equipos.length})`));
    if (torneo.equipos.length === 0) {
      equipos.appendChild(
        nodo(
          'p',
          't-meta',
          torneo.estado === 'INSCRIPCIONES_ABIERTAS'
            ? 'Todavía no hay equipos registrados. Registra el tuyo con un compañero para ser el primero.'
            : 'Este torneo no tuvo equipos registrados.',
        ),
      );
    }
    // UX-GAME-5 — ocho tarjetas a lo ancho ocupaban una pantalla entera antes
    // del arbol; en rejilla caben en dos filas.
    const rejilla = nodo('div', 'torneo__equipos');
    torneo.equipos.forEach((e) => rejilla.appendChild(tarjetaDeEquipo(torneo, e, uid)));
    equipos.appendChild(rejilla);
    zonaDetalle.appendChild(equipos);

    // Árbol (HU-TOR-004 CA-03).
    const arbol = nodo('section', 'pila pila--compacta');
    arbol.dataset.zona = 'arbol';
    arbol.appendChild(nodo('h3', undefined, 'Árbol del torneo'));
    if (torneo.encuentros.length === 0) {
      arbol.appendChild(
        nodo(
          'p',
          't-meta',
          torneo.estado === 'CANCELADO'
            ? 'El torneo se canceló antes de empezar: no llegó a tener árbol.'
            : 'El árbol se genera al cerrar las inscripciones: entonces verás contra quién juega cada equipo.',
        ),
      );
    }
    LLAVES.forEach(([llave, titulo]) => {
      const lista = encuentrosDe(torneo, llave);
      if (lista.length === 0) {
        return;
      }
      arbol.appendChild(nodo('h4', 't-etiqueta', titulo));
      const cuadro = nodo('div', 'arbol-torneo');
      cuadro.dataset.llave = llave;
      const rondas = porRonda(lista);
      rondas.forEach(({ ronda, encuentros }, indice) => {
        const columna = nodo('div', 'arbol-torneo__ronda');
        columna.dataset.ronda = String(ronda);
        // La gran final ya la nombra su llave: repetirlo encima del único
        // encuentro no dice nada nuevo.
        if (llave !== 'FINAL') {
          columna.appendChild(
            nodo(
              'p',
              't-meta',
              nombreDeRonda(llave, ronda, {
                cantidad: encuentros.length,
                ultima: indice === rondas.length - 1,
              }),
            ),
          );
        }
        encuentros.forEach((e) => columna.appendChild(tarjetaDeEncuentro(torneo, e, uid)));
        cuadro.appendChild(columna);
      });
      arbol.appendChild(cuadro);
    });
    zonaDetalle.appendChild(arbol);
  }

  function formularioDeEquipo(torneo) {
    const form = nodo('form', 'tarjeta pila pila--compacta');
    form.dataset.zona = 'crear-equipo';
    form.appendChild(nodo('h3', undefined, 'Registrar mi equipo'));
    // Con `campo()` en vez de innerHTML: etiqueta asociada por for/id y pista
    // bajo el control. La del companero importa — pedir un uid a secas es
    // pedir un dato que nadie se sabe de memoria; ahora dice donde sacarlo.
    for (const uno of [
      campo({
        nombre: 'nombre',
        etiqueta: 'Nombre del equipo',
        requerido: true,
        atributos: { minlength: 3, maxlength: 40 },
        pista: 'Entre 3 y 40 caracteres. Pasa por la lista negra de terminos prohibidos.',
      }),
      campo({
        nombre: 'avatar',
        etiqueta: 'Avatar del equipo',
        requerido: true,
        atributos: { maxlength: 300 },
        pista: 'Identificador o dirección de la imagen.',
      }),
      campo({
        nombre: 'companeroUid',
        etiqueta: 'Identificador de tu compañero',
        requerido: true,
        pista: 'Pídeselo a tu compañero: lo ve en Mi Cuenta, pestaña Seguridad.',
      }),
    ]) {
      form.appendChild(uno.elemento);
    }
    const enviar = nodo('button', 'boton boton--primario', 'Registrar equipo');
    enviar.type = 'submit';
    form.appendChild(enviar);
    form.addEventListener('submit', async (evento) => {
      evento.preventDefault();
      const datos = new FormData(form);
      const boton = form.querySelector('[type="submit"]');
      boton.disabled = true;
      try {
        await api.crearEquipo(
          torneo.id,
          {
            nombre: String(datos.get('nombre') ?? '').trim(),
            avatar: String(datos.get('avatar') ?? '').trim(),
            companeroUid: String(datos.get('companeroUid') ?? '').trim(),
          },
          fetchImpl,
        );
        pintarAviso(zonaAviso, {
          tono: 'exito',
          titulo: 'Equipo registrado',
          detalle: 'Ahora inscríbelo para ocupar su posición en el árbol.',
        });
        await abrir(torneo.id);
      } catch (error) {
        avisarError(zonaAviso, error);
      } finally {
        boton.disabled = false;
      }
    });
    return form;
  }

  formCrear?.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    const form = evento.currentTarget;
    const datos = new FormData(form);
    const boton = form.querySelector('[type="submit"]');
    boton.disabled = true;
    zonaAviso.hidden = true;
    try {
      const cierre = String(datos.get('inscripcionesCierranEn') ?? '');
      const torneo = await api.crear(
        {
          nombre: String(datos.get('nombre') ?? '').trim(),
          inscripcionesCierranEn: cierre ? new Date(cierre).toISOString() : null,
          costoInscripcion: Number(datos.get('costoInscripcion') ?? 0) || 0,
        },
        fetchImpl,
      );
      pintarAviso(zonaAviso, {
        tono: 'exito',
        titulo: 'Torneo creado',
        detalle: `«${torneo.nombre}» tiene las inscripciones abiertas.`,
      });
      form.reset();
      await cargarListado();
      pintarDetalle(torneo);
    } catch (error) {
      avisarError(zonaAviso, error);
    } finally {
      boton.disabled = false;
    }
  });

  cargarListado().then(() => {
    if (torneoInicial) {
      return abrir(torneoInicial);
    }
    return undefined;
  });
  return { cargarListado, abrir };
}
