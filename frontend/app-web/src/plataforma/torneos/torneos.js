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
import { nodo } from '../../comun/ui/dom.js';
import { pintarAviso } from '../../comun/ui/aviso.js';
import { campo } from '../../comun/ui/campo.js';
import { estadoDeCarga, estadoDeError, estadoVacio } from '../../comun/ui/estado-vista.js';

export const ROLES_DE_ADMINISTRACION = Object.freeze(['ADMINISTRADOR', 'SUPER_ADMINISTRADOR']);

/** Nombres de las llaves del árbol, en el orden en que se pintan. */
export const LLAVES = Object.freeze([
  ['GANADORES', 'Llave de ganadores (1-6 y 11)'],
  ['SECUNDARIOS', 'Llave de secundarios (7-10, 12 y 13)'],
  ['FINAL', 'Final'],
]);

function baseDeApi() {
  const meta = globalThis.document?.querySelector?.('meta[name="nexus-api-base"]');
  return String(meta?.content ?? '').replace(/\/+$/, '');
}

export class ErrorDeTorneos extends Error {
  constructor(problema, estado) {
    super(problema?.detail ?? problema?.title ?? `Error ${estado}`);
    this.name = 'ErrorDeTorneos';
    this.estado = estado;
    this.titulo = problema?.title ?? 'No se pudo completar';
    this.detalle = problema?.detail ?? '';
    this.motivo = problema?.motivo ?? null;
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
  return equipo ? equipo.nombre : equipoId.slice(0, 8);
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
    detalle += ` Proxima fecha posible: ${new Date(error.proximaFechaPosible).toLocaleDateString('es-CO')}.`;
  }
  pintarAviso(zona, {
    tono: deNegocio && error.estado < 500 ? 'advertencia' : 'error',
    titulo: deNegocio ? error.titulo : 'No pudimos contactar con el servicio',
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
  tarjeta.appendChild(nodo('strong', 'tarjeta__titulo', torneo.nombre));
  tarjeta.appendChild(nodo('p', 't-cuerpo', resumenDe(torneo)));
  tarjeta.appendChild(
    nodo(
      'p',
      't-meta',
      `Inscripciones hasta ${new Date(torneo.inscripcionesCierranEn).toLocaleString('es-CO')}`,
    ),
  );
  const boton = nodo('button', 'boton boton--secundario boton--pequeno', 'Ver torneo');
  boton.type = 'button';
  boton.dataset.accion = 'abrir';
  boton.addEventListener('click', () => alAbrir?.(torneo));
  tarjeta.appendChild(boton);
  return tarjeta;
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
  const tarjeta = nodo('article', `encuentro${estado.clase}`);
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
        `Campeon: ${nombreDe(torneo, torneo.campeonEquipoId)}`,
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
        const motivo =
          globalThis.prompt?.('Motivo de la cancelacion (se devuelven las inscripciones):') ?? '';
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

    // Equipos (CA-01/02 de HU-TOR-008).
    const equipos = nodo('section', 'pila pila--compacta');
    equipos.dataset.zona = 'equipos';
    equipos.appendChild(nodo('h3', undefined, `Equipos (${torneo.equipos.length})`));
    if (torneo.equipos.length === 0) {
      equipos.appendChild(nodo('p', 't-meta', 'Todavía no hay equipos registrados.'));
    }
    torneo.equipos.forEach((e) => equipos.appendChild(tarjetaDeEquipo(torneo, e, uid)));
    zonaDetalle.appendChild(equipos);

    // Árbol (HU-TOR-004 CA-03).
    const arbol = nodo('section', 'pila pila--compacta');
    arbol.dataset.zona = 'arbol';
    arbol.appendChild(nodo('h3', undefined, 'Arbol del torneo'));
    if (torneo.encuentros.length === 0) {
      arbol.appendChild(nodo('p', 't-meta', 'El árbol se genera al cerrar las inscripciones.'));
    }
    LLAVES.forEach(([llave, titulo]) => {
      const lista = encuentrosDe(torneo, llave);
      if (lista.length === 0) {
        return;
      }
      arbol.appendChild(nodo('h4', 't-etiqueta', titulo));
      const cuadro = nodo('div', 'arbol-torneo');
      cuadro.dataset.llave = llave;
      for (const { ronda, encuentros } of porRonda(lista)) {
        const columna = nodo('div', 'arbol-torneo__ronda');
        columna.dataset.ronda = String(ronda);
        if (ronda > 0) {
          columna.appendChild(nodo('p', 't-meta', `Ronda ${ronda}`));
        }
        encuentros.forEach((e) => columna.appendChild(tarjetaDeEncuentro(torneo, e, uid)));
        cuadro.appendChild(columna);
      }
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
        pista: 'Cada quien ve el suyo en Mi Cuenta, pestana Seguridad.',
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
