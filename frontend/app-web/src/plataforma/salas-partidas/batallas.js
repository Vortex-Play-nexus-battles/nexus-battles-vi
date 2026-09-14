/**
 * HU-SAL-002 · RF-JUE-002 — Listado de batallas e ingreso a una sala.
 *
 * Vista: Pantalla 2 · Batallas del sistema de diseno (nodo 19:29).
 *
 * La tarjeta no se inventa: el conjunto `Tarjeta de sala` tiene exactamente dos
 * propiedades, la variante de estado y una unica linea de texto que los ocho
 * ejemplos de la pantalla resuelven como
 * «4 de 6 jugadores · 320 creditos · Con heroe de la IA». Ni nombre de sala, ni
 * apodo del anfitrion, ni heroe, ni indicador de preparacion: nada de eso
 * aparece en el diseno ni lo pide RF-JUE-002.
 *
 * NO hay busqueda, y tampoco la hay ya en la maqueta. La Pantalla 2 tenia un
 * campo «Buscar una sala por nombre...» que quedo huerfano al retirar el nombre
 * de la tarjeta en HU-SAL-001. Se elimino de Figma en HU-SAL-002 en vez de
 * dejarlo pintado: un control que promete una funcion inexistente es peor que
 * no tenerlo. Quedan los dos filtros con respaldo, modalidad y estado.
 */

import { listarSalas, ingresarASala } from './cliente-salas.js';
import { seguirSala, estadoDesdeFicha } from './canal-sala.js';

/** Etiqueta de la insignia por estado. Son las del componente `Insignia`. */
const ETIQUETA_DE_ESTADO = {
  ABIERTA: 'Abierta',
  LLENA: 'Llena',
  EN_JUEGO: 'En juego',
  PRIVADA: 'Privada',
};

/** Sufijo de la clase `.distintivo--<estado>` del ui-kit. */
const CLASE_DE_ESTADO = {
  ABIERTA: 'abierta',
  LLENA: 'llena',
  EN_JUEGO: 'en-juego',
  PRIVADA: 'privada',
};

/**
 * Linea de metadatos de la tarjeta, calcada de los ejemplos del diseno.
 *
 * El sufijo de la IA solo aparece cuando la hay: en las ocho tarjetas de la
 * Pantalla 2, las seis sin heroe de la IA no dicen nada al respecto.
 *
 * @param {{ocupacion: number, maximoParticipantes: number,
 *          recompensaCreditos: number, incluirHeroeIA: boolean}} sala
 * @returns {string}
 */
export function metaDeLaSala(sala) {
  const base =
    `${sala.ocupacion} de ${sala.maximoParticipantes} jugadores` +
    ` · ${sala.recompensaCreditos} creditos`;
  return sala.incluirHeroeIA ? `${base} · Con heroe de la IA` : base;
}

/**
 * Texto del subtitulo, con el numero real de salas.
 *
 * @param {number} total
 * @returns {string}
 */
export function subtituloDeSalas(total) {
  return total === 1 ? '1 sala abierta ahora mismo' : `${total} salas abiertas ahora mismo`;
}

/**
 * Texto de la paginacion. El componente `Paginacion` lo justifica asi: «el
 * jugador necesita saber si merece la pena seguir pasando paginas».
 *
 * @param {{contenido: Array, totalElementos: number}} pagina
 * @returns {string}
 */
export function textoDePaginacion(pagina) {
  return `Mostrando ${pagina.contenido.length} de ${pagina.totalElementos} salas`;
}

/**
 * Construye la tarjeta de una sala.
 *
 * Una sala llena va atenuada y no es pulsable — lo dice la descripcion del
 * componente. Se resuelve con `.tarjeta--bloqueada` y `disabled`, no solo con
 * opacidad: quitar el color sin quitar el foco dejaria un boton invisible pero
 * alcanzable con el teclado.
 *
 * @param {object} sala
 * @param {Document} doc
 * @returns {HTMLButtonElement}
 */
function tarjetaDeSala(sala, doc) {
  const pulsable = sala.estado !== 'LLENA';

  const tarjeta = doc.createElement('button');
  tarjeta.type = 'button';
  tarjeta.className = pulsable
    ? 'tarjeta tarjeta--pulsable pila pila--compacta'
    : 'tarjeta tarjeta--bloqueada pila pila--compacta';
  tarjeta.dataset.sala = sala.id;
  tarjeta.dataset.estado = sala.estado;
  if (!pulsable) {
    tarjeta.disabled = true;
  }

  const insignia = doc.createElement('span');
  insignia.className = `distintivo distintivo--${CLASE_DE_ESTADO[sala.estado] ?? 'abierta'}`;
  insignia.textContent = ETIQUETA_DE_ESTADO[sala.estado] ?? sala.estado;

  const meta = doc.createElement('span');
  meta.className = 'tarjeta__meta';
  meta.textContent = metaDeLaSala(sala);

  tarjeta.append(insignia, meta);
  return tarjeta;
}

/**
 * Ficha de la sala tal como se pinta, a partir de su ficha de la API y del
 * estado que va llegando por el canal. Una sala que se llena en vivo pasa a
 * LLENA y deja de ser pulsable, igual que si hubiera llegado asi del listado.
 *
 * @param {object} sala ficha original de `GET /salas`
 * @param {{ocupacion: {actual: number, maximo: number}}} estado estado del canal
 * @returns {object}
 */
export function fichaEnVivo(sala, estado) {
  const llena = estado.ocupacion.actual >= estado.ocupacion.maximo;
  return {
    ...sala,
    ocupacion: estado.ocupacion.actual,
    estado: llena && sala.estado === 'ABIERTA' ? 'LLENA' : sala.estado,
  };
}

/**
 * Monta la vista del listado de batallas.
 *
 * El canal en tiempo real es opcional y se inyecta ya resuelto: `conectarCanal`
 * devuelve una promesa con un cliente `{suscribir(destino, alRecibir)}` o con
 * `null` cuando no hay sesion. Sin canal la vista funciona igual, solo que no
 * se actualiza sola; con el, cada tarjeta publica del listado sigue su sala y
 * la propia sala a la que se acaba de entrar tambien, sea publica o privada.
 * Las privadas ajenas no se siguen desde el listado: el servidor las rechaza
 * (solo sus participantes pueden), y pedirlo seria provocar un ERROR seguro.
 *
 * @param {HTMLElement} raiz elemento que contiene la vista
 * @param {{listar?: Function, ingresar?: Function, alEntrar?: Function,
 *          conectarCanal?: () => Promise<{suscribir: Function}|null>}} [puertos]
 *        dependencias inyectables; por defecto las del cliente HTTP real y sin canal
 * @returns {{refrescar: Function}}
 */
export function montarBatallas(raiz, puertos = {}) {
  const {
    listar = listarSalas,
    ingresar = ingresarASala,
    alEntrar = () => {},
    conectarCanal = () => Promise.resolve(null),
  } = puertos;

  const doc = raiz.ownerDocument;
  const zonaSalas = raiz.querySelector('[data-zona="salas"]');
  const zonaEstado = raiz.querySelector('[data-zona="estado"]');
  const zonaPaginacion = raiz.querySelector('[data-zona="paginacion"]');
  const subtitulo = raiz.querySelector('[data-zona="subtitulo"]');
  const zonaCanal = raiz.querySelector('[data-zona="canal"]');
  const filtroModalidad = raiz.querySelector('[name="modalidad"]');
  const filtroEstado = raiz.querySelector('[name="estado"]');

  let paginaActual = 0;

  /* -- Canal en tiempo real ------------------------------------------- */

  let canal = null;
  /** Fichas de la pagina actual por id, para repintar la tarjeta que cambie. */
  const fichas = new Map();
  /** Salas ya suscritas en esta sesion: el cliente no expone UNSUBSCRIBE. */
  const seguidas = new Set();

  function marcarCanal(texto, estadoCanal) {
    if (!zonaCanal) {
      return;
    }
    zonaCanal.textContent = texto;
    zonaCanal.dataset.estado = estadoCanal;
    zonaCanal.hidden = false;
  }

  function repintarTarjeta(sala, estado) {
    const actual = zonaSalas.querySelector(`[data-sala="${sala.id}"]`);
    if (!actual) {
      return;
    }
    const nueva = fichaEnVivo(sala, estado);
    fichas.set(sala.id, nueva);
    actual.replaceWith(tarjetaDeSala(nueva, doc));
  }

  function seguir(sala) {
    if (!canal || seguidas.has(sala.id)) {
      return;
    }
    seguidas.add(sala.id);
    seguirSala(estadoDesdeFicha(sala), {
      suscribir: (destino, alRecibir) => canal.suscribir(destino, alRecibir),
      alCambiar: (estado) => repintarTarjeta(fichas.get(sala.id) ?? sala, estado),
    });
  }

  function seguirVisibles() {
    for (const sala of fichas.values()) {
      if (!sala.privada) {
        seguir(sala);
      }
    }
  }

  conectarCanal()
    .then((cliente) => {
      if (!cliente) {
        marcarCanal(
          'Sin canal en tiempo real: inicia sesion para ver los cambios al instante.',
          'sin-sesion',
        );
        return;
      }
      canal = cliente;
      if (typeof cliente === 'object' && 'alCerrar' in cliente) {
        cliente.alCerrar = () => {
          canal = null;
          marcarCanal(
            'Canal en tiempo real desconectado. Recarga para volver a seguir las salas.',
            'cerrado',
          );
        };
      }
      marcarCanal('Canal en tiempo real conectado: las salas se actualizan solas.', 'conectado');
      seguirVisibles();
    })
    .catch((error) => {
      marcarCanal(
        `Canal en tiempo real no disponible: ${error?.message ?? 'no se pudo conectar'}.`,
        'error',
      );
    });

  /** Muestra uno de los cuatro estados de RNF-USA-003 y oculta la rejilla. */
  function mostrarEstado(claseExtra, titulo, detalle) {
    zonaSalas.hidden = true;
    zonaPaginacion.hidden = true;
    zonaEstado.hidden = false;
    zonaEstado.className = `estado-vista ${claseExtra}`;
    zonaEstado.innerHTML = '';

    const encabezado = doc.createElement('p');
    encabezado.className = 'estado-vista__titulo';
    encabezado.textContent = titulo;

    const cuerpo = doc.createElement('p');
    cuerpo.className = 't-cuerpo';
    cuerpo.textContent = detalle;

    zonaEstado.append(encabezado, cuerpo);
  }

  function pintar(pagina) {
    zonaEstado.hidden = true;
    zonaSalas.hidden = false;
    zonaSalas.innerHTML = '';

    subtitulo.textContent = subtituloDeSalas(pagina.totalElementos);

    if (pagina.contenido.length === 0) {
      mostrarEstado(
        'estado-vista--vacio',
        'No hay batallas abiertas',
        'Crea una sala y espera a que alguien se una.',
      );
      subtitulo.textContent = subtituloDeSalas(0);
      return;
    }

    fichas.clear();
    for (const sala of pagina.contenido) {
      fichas.set(sala.id, sala);
      zonaSalas.append(tarjetaDeSala(sala, doc));
    }
    seguirVisibles();

    pintarPaginacion(pagina);
  }

  function pintarPaginacion(pagina) {
    zonaPaginacion.hidden = false;
    zonaPaginacion.innerHTML = '';

    const info = doc.createElement('span');
    info.className = 'paginacion__info';
    info.textContent = textoDePaginacion(pagina);

    const paginas = doc.createElement('div');
    paginas.className = 'paginacion__paginas';

    for (let i = 0; i < pagina.totalPaginas; i += 1) {
      const boton = doc.createElement('button');
      boton.type = 'button';
      boton.className = 'paginacion__pagina';
      boton.textContent = String(i + 1);
      boton.dataset.pagina = String(i);
      if (i === pagina.pagina) {
        boton.setAttribute('aria-current', 'page');
      }
      paginas.append(boton);
    }

    zonaPaginacion.append(info, paginas);
  }

  async function refrescar() {
    mostrarEstado('estado-vista--cargando', 'Buscando batallas', 'Un momento.');
    try {
      pintar(
        await listar({
          pagina: paginaActual,
          modalidad: filtroModalidad?.value,
          estado: filtroEstado?.value,
        }),
      );
    } catch (error) {
      mostrarEstado(
        'estado-vista--error',
        error.titulo ?? 'No se pudo cargar el listado',
        error.detalle ?? error.message,
      );
    }
  }

  raiz.addEventListener('click', async (evento) => {
    const boton = evento.target.closest('[data-pagina]');
    if (boton) {
      paginaActual = Number(boton.dataset.pagina);
      await refrescar();
      return;
    }

    const tarjeta = evento.target.closest('[data-sala]');
    if (!tarjeta || tarjeta.disabled) {
      return;
    }

    try {
      const dentro = await ingresar(tarjeta.dataset.sala);
      // Ya se es participante: ahora si se puede seguir la sala aunque sea
      // privada, y la tarjeta refleja la entrada sin esperar al canal.
      if (dentro && dentro.id) {
        fichas.set(dentro.id, dentro);
        const actual = zonaSalas.querySelector(`[data-sala="${dentro.id}"]`);
        if (actual) {
          actual.replaceWith(tarjetaDeSala(dentro, doc));
        }
        seguir(dentro);
      }
      alEntrar(dentro);
    } catch (error) {
      // Los tres rechazos del contrato -403 privada, 404 no existe, 409 llena-
      // llegan aqui ya interpretados por el cliente. La vista los muestra tal
      // cual: el texto lo redacta el servicio, que es quien sabe el motivo.
      mostrarEstado(
        'estado-vista--error',
        error.titulo ?? 'No pudiste entrar',
        error.detalle ?? error.message,
      );
    }
  });

  for (const filtro of [filtroModalidad, filtroEstado]) {
    filtro?.addEventListener('change', () => {
      paginaActual = 0;
      refrescar();
    });
  }

  refrescar();
  return { refrescar };
}
