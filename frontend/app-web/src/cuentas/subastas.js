/**
 * HU-SUB-011 - Controlador de la pagina de subastas.
 *
 * Ensambla la barra de navegacion compartida, el buscador con
 * autocompletado, el orden, los filtros y la vitrina -- coordinando los
 * modulos que ya existen por separado (cliente-subastas, subastas-vitrina,
 * subastas-filtros). Mismo patron que login.js junto a login.html.
 *
 * ## UX-R2.8b — tres cosas que esta pantalla hacia mal
 *
 * 1. **Un servicio caido se leia como un mercado vacio.** El estado de error
 *    era un `<p>` gris con el mensaje y, debajo, el `error.message` tal cual:
 *    el laboratorio visual (#600) detectaba «Error 404» en las CINCO
 *    anchuras. Ahora es `estadoDeError` del kit, con boton de **Reintentar**,
 *    y el detalle tecnico no aparece nunca: si el error no viene del contrato
 *    (no trae `estado`), se usa una frase generica en vez de «Failed to
 *    fetch».
 *
 * 2. **El contador no contaba.** `subastas-vitrina.js` calcula «42m» al
 *    pintar la tarjeta y deja escrito en un comentario que mantenerlo al dia
 *    le toca a quien llama. Nadie lo hacia, asi que un lote decia «2m»
 *    durante media hora. Ahora se adopta cada contador con
 *    `comun/ui/cuenta-atras.js`, que late de verdad. La vitrina **no se
 *    toca**: es un modulo protegido y ademas esto es exactamente lo que su
 *    comentario pedia.
 *
 * 3. **Tres estados propios y una paginacion propia** donde el kit ya tenia
 *    ambos. `.estado`/`.estado-carga`/`.estado-error` y
 *    `.subastas-paginacion__*` eran copias locales de `.estado-vista` y
 *    `.paginacion`.
 *
 * PENDIENTE, a proposito fuera de este archivo: la suscripcion STOMP al canal
 * `/topic/subastas/listado`. El contador ya es real; lo que sigue sin llegar
 * solo es la puja de otra persona, que exige el canal.
 */

import { montarCabecera } from '../comun/cabecera-app.js';
import { listarSubastas, sugerirSubastas } from './cliente-subastas.js';
import { construirVitrinaSubastas } from './subastas-vitrina.js';
import { construirFiltros } from './subastas-filtros.js';
import { h } from '../comun/ui/dom.js';
import { encabezadoDePagina } from '../comun/ui/pagina.js';
import {
  estadoDeCarga,
  estadoDeError,
  estadoVacio,
  pintarEstado,
} from '../comun/ui/estado-vista.js';
import { adoptarCuentaAtras, vigilarCuentasAtras } from '../comun/ui/cuenta-atras.js';

const TAMANO_PAGINA = 16;
const ESPERA_DEBOUNCE_MS = 300;

const estado = {
  pagina: 0,
  filtros: {},
  ordenarPor: 'FECHA_PUBLICACION',
};

/** Como parar el latido de los contadores de la tanda anterior. */
let detenerContadores = null;

document.addEventListener('DOMContentLoaded', inicializar);

/**
 * Monta la pantalla sobre `#raiz-subastas`.
 *
 * Exportada desde UX-R2.8b: era privada y por eso la vista principal del
 * mercado —la que enseñaba «Error 404» en las cinco anchuras— no tenia ni
 * una prueba. Se sigue enganchando a `DOMContentLoaded` como siempre.
 */
export function inicializar() {
  estado.pagina = 0;
  estado.filtros = {};
  estado.ordenarPor = 'FECHA_PUBLICACION';
  const raiz = document.getElementById('raiz-subastas');
  if (!raiz) {
    throw new Error('subastas.html debe traer un elemento con id="raiz-subastas"');
  }

  // Cabecera unica de la aplicacion (HU-UX-001). Subasta es publica: un
  // visitante ve el listado; pujar exige sesion.
  // Va fuera de la raiz (a todo el ancho, como en las demas vistas); si la
  // pagina no trae el contenedor, se crea dentro para no quedarse sin barra.
  let cabecera = document.querySelector('[data-cabecera-app]');
  if (!cabecera) {
    cabecera = document.createElement('div');
    cabecera.dataset.cabeceraApp = '';
    raiz.appendChild(cabecera);
  }
  montarCabecera(cabecera, { vista: 'subastas', seccionActiva: 'subasta' });

  // El titulo y «Publicar subasta» iban sueltos, uno debajo del otro, con el
  // boton primario flotando a la izquierda como si fuera un parrafo mas. El
  // encabezado del kit los pone donde estan en el resto de la aplicacion.
  raiz.appendChild(
    encabezadoDePagina({
      titulo: 'Subastas activas',
      descripcion: 'Compra y vende objetos con el resto de jugadores.',
      acciones: [
        h('a', {
          clase: 'boton boton--primario',
          texto: 'Publicar subasta',
          atributos: { href: './publicar-subasta.html' },
        }),
      ],
    }),
  );

  // Buscar y ordenar son el mismo control compuesto, no dos bloques apilados.
  const controles = h('div', { clase: 'mercado__controles' });
  controles.append(construirBarraBusqueda(), construirBarraOrden());
  raiz.appendChild(controles);

  const contenido = document.createElement('div');
  contenido.className = 'subastas-contenido';

  const filtros = construirFiltros({
    alCambiar: (nuevosFiltros) => {
      // El texto de busqueda vive aparte (construirBarraBusqueda), asi
      // que se preserva aqui en vez de dejar que el panel de filtros lo
      // borre al no conocerlo.
      estado.filtros = { ...nuevosFiltros, q: estado.filtros.q };
      estado.pagina = 0;
      rotularFiltros();
      cargarYRenderizar();
    },
  });

  // UX-R4.8 — En pantalla ancha el panel es una columna fija a la izquierda y
  // no estorba. A 375 px la rejilla pasa a una sola columna y el panel se
  // apila ENCIMA de los resultados: veinticuatro controles, unos 1.400 px de
  // filtros antes de la primera subasta. Quien entra al mercado publico —y
  // puede entrar sin sesion— ve una pared de casillas, no un objeto.
  //
  // Se pliega con `<details>`, que trae el teclado y el lector de pantalla
  // hechos. En ancho se abre y su resumen se esconde, asi que ahi no cambia
  // nada de lo que ya habia.
  const panelFiltros = document.createElement('details');
  panelFiltros.className = 'subastas-filtros__plegable';
  const resumenFiltros = document.createElement('summary');
  resumenFiltros.className = 'subastas-filtros__resumen';

  /**
   * Un panel plegado no puede esconder que hay filtros puestos: quien no vea
   * ni el panel ni la causa se queda mirando «ninguna subasta coincide» sin
   * saber por que. El resumen dice cuantos hay.
   */
  function rotularFiltros() {
    const puestos = Object.keys(estado.filtros).filter((clave) => clave !== 'q').length;
    resumenFiltros.textContent = puestos === 0 ? 'Filtros' : `Filtros · ${puestos} activos`;
  }
  rotularFiltros();

  panelFiltros.append(resumenFiltros, filtros);

  // El estado abierto lo decide la anchura, no el usuario: en ancho el
  // resumen ni se ve, asi que dejarlo cerrado escondería el panel entero.
  const esAncho = globalThis.matchMedia?.('(min-width: 900px)');
  const ajustarPliegue = () => {
    panelFiltros.open = esAncho ? esAncho.matches : true;
  };
  ajustarPliegue();
  esAncho?.addEventListener?.('change', ajustarPliegue);

  const zonaResultados = document.createElement('div');
  zonaResultados.id = 'subastas-resultados';

  contenido.append(panelFiltros, zonaResultados);
  raiz.appendChild(contenido);

  cargarYRenderizar();
}

/** Buscador con autocompletado, independiente del de la barra compartida. */
function construirBarraBusqueda() {
  const contenedor = document.createElement('div');
  contenedor.className = 'subastas-busqueda';

  const campo = document.createElement('input');
  campo.type = 'search';
  campo.className = 'subastas-busqueda__campo';
  campo.placeholder = 'Buscar por nombre, tipo o habilidad...';
  campo.setAttribute('aria-label', 'Buscar subastas');
  campo.setAttribute('aria-autocomplete', 'list');

  const listaSugerencias = document.createElement('ul');
  listaSugerencias.className = 'subastas-busqueda__sugerencias';
  listaSugerencias.hidden = true;

  let idPeticionVigente = 0;

  const buscar = debounce(async (texto) => {
    if (texto.trim() === '') {
      ocultarSugerencias();
      estado.filtros = { ...estado.filtros, q: undefined };
      estado.pagina = 0;
      cargarYRenderizar();
      return;
    }

    const idPeticion = ++idPeticionVigente;
    let sugerencias;
    try {
      sugerencias = await sugerirSubastas(texto);
    } catch {
      // Un fallo en las sugerencias no debe romper la barra de busqueda:
      // el usuario igual puede presionar Enter para buscar el texto tal
      // cual, solo se queda sin lista de autocompletado.
      sugerencias = [];
    }
    if (idPeticion !== idPeticionVigente) {
      return; // Llego una respuesta vieja despues de una tecla mas reciente.
    }
    mostrarSugerencias(sugerencias);
  }, ESPERA_DEBOUNCE_MS);

  campo.addEventListener('input', () => buscar(campo.value));

  campo.addEventListener('keydown', (evento) => {
    if (evento.key === 'Enter') {
      evento.preventDefault();
      confirmarBusqueda(campo.value);
    }
    if (evento.key === 'Escape') {
      ocultarSugerencias();
    }
  });

  function mostrarSugerencias(sugerencias) {
    listaSugerencias.replaceChildren();
    if (sugerencias.length === 0) {
      ocultarSugerencias();
      return;
    }
    for (const sugerencia of sugerencias) {
      const item = document.createElement('li');
      const boton = document.createElement('button');
      boton.type = 'button';
      boton.className = 'subastas-busqueda__sugerencia';
      boton.textContent = sugerencia;
      boton.addEventListener('click', () => {
        campo.value = sugerencia;
        confirmarBusqueda(sugerencia);
      });
      item.appendChild(boton);
      listaSugerencias.appendChild(item);
    }
    listaSugerencias.hidden = false;
  }

  function ocultarSugerencias() {
    listaSugerencias.hidden = true;
    listaSugerencias.replaceChildren();
  }

  function confirmarBusqueda(texto) {
    ocultarSugerencias();
    estado.filtros = { ...estado.filtros, q: texto.trim() || undefined };
    estado.pagina = 0;
    cargarYRenderizar();
  }

  contenedor.append(campo, listaSugerencias);
  return contenedor;
}

function construirBarraOrden() {
  const contenedor = document.createElement('div');
  contenedor.className = 'subastas-orden';

  const control = document.createElement('select');
  control.className = 'subastas-orden__control';
  control.setAttribute('aria-label', 'Ordenar subastas por');

  const opciones = [
    { valor: 'FECHA_PUBLICACION', etiqueta: 'Más recientes' },
    { valor: 'PRECIO_ASC', etiqueta: 'Precio: menor a mayor' },
    { valor: 'PRECIO_DESC', etiqueta: 'Precio: mayor a menor' },
    { valor: 'TIEMPO_RESTANTE', etiqueta: 'Termina pronto' },
    { valor: 'PUJAS', etiqueta: 'Más pujas' },
    { valor: 'POPULARIDAD', etiqueta: 'Más popular' },
  ];
  for (const opcion of opciones) {
    const elemento = document.createElement('option');
    elemento.value = opcion.valor;
    elemento.textContent = opcion.etiqueta;
    control.appendChild(elemento);
  }

  control.addEventListener('change', () => {
    estado.ordenarPor = control.value;
    estado.pagina = 0;
    cargarYRenderizar();
  });

  contenedor.appendChild(control);
  return contenedor;
}

async function cargarYRenderizar() {
  const zona = document.getElementById('subastas-resultados');
  if (!zona) {
    return;
  }

  // Cada tanda se lleva por delante el latido de la anterior: si no, cada
  // filtro dejaria un temporizador corriendo sobre elementos ya borrados.
  pararContadores();

  pintarEstado(zona, estadoDeCarga({ filas: 4, etiqueta: 'Cargando subastas…' }));

  let pagina;
  try {
    pagina = await listarSubastas(
      { ...estado.filtros, ordenarPor: estado.ordenarPor },
      estado.pagina,
      TAMANO_PAGINA,
    );
  } catch (error) {
    pintarEstado(zona, estadoDelFallo(error));
    return;
  }

  if (pagina.contenido.length === 0) {
    pintarEstado(zona, estadoSinResultados());
    return;
  }

  const vitrina = construirVitrinaSubastas(pagina, {
    alAbrirDetalle: (subasta) => {
      // La vista de detalle todavia no existe (ver pendientes de HU-SUB-011).
      globalThis.location.href = `./pujas.html?id=${subasta.id}`;
    },
  });

  zona.replaceChildren(vitrina, construirPaginacion(pagina));
  ponerEnHoraLosContadores(zona, pagina.contenido);
}

/**
 * Un mercado caido NO es un mercado vacio.
 *
 * El detalle solo sale si el error viene del contrato (`error.estado`, que
 * pone `cliente-subastas.js`): esos mensajes ya estan escritos para leerse.
 * Un `TypeError` de red trae «Failed to fetch», que no se le ensena a nadie.
 *
 * @param {Error & {estado?: number}} error
 * @returns {HTMLElement}
 */
function estadoDelFallo(error) {
  const esDeSesion = error.estado === 401 || error.estado === 403;
  return estadoDeError({
    titulo: esDeSesion ? 'No podemos mostrarte las subastas' : 'El mercado no responde',
    detalle:
      typeof error.estado === 'number'
        ? error.message
        : 'No pudimos conectar con el mercado. Revisa tu conexión e inténtalo otra vez.',
    alReintentar: () => cargarYRenderizar(),
  });
}

/**
 * Vacio util: distingue «no hay nada» de «tu filtro no encuentra nada», que
 * son dos situaciones con dos salidas distintas.
 *
 * @returns {HTMLElement}
 */
function estadoSinResultados() {
  const hayFiltro =
    Boolean(estado.filtros.q) ||
    Object.entries(estado.filtros).some(([clave, valor]) => clave !== 'q' && valor !== undefined);

  if (hayFiltro) {
    return estadoVacio({
      titulo: 'Ninguna subasta coincide con lo que buscas',
      detalle: 'Prueba con menos filtros o con otro término.',
      icono: '⌕',
      accion: { texto: 'Quitar los filtros', alPulsar: limpiarFiltros },
    });
  }
  return estadoVacio({
    titulo: 'Todavía no hay subastas activas',
    detalle: 'Cuando alguien publique un lote aparecerá aquí. También puedes publicar tú.',
    icono: '◇',
    accion: { texto: 'Publicar subasta', href: './publicar-subasta.html' },
  });
}

/**
 * Deja la busqueda y los filtros como al entrar.
 *
 * El panel de filtros es un `<form>` con un boton `type="reset"` nativo, y
 * escucha su propio evento `reset` para avisar. Asi que basta con pulsarlo:
 * el panel se vacia y el `alCambiar` que ya esta enganchado recarga solo.
 * Se evita a proposito tener dos caminos distintos para lo mismo.
 */
function limpiarFiltros() {
  const campo = document.querySelector('.subastas-busqueda__campo');
  if (campo) {
    campo.value = '';
  }
  estado.filtros = {};
  estado.pagina = 0;

  const limpiarPanel = document.querySelector('.subastas-filtros__limpiar');
  if (limpiarPanel) {
    limpiarPanel.click(); // Dispara `reset` → `alCambiar` → `cargarYRenderizar`.
    return;
  }
  cargarYRenderizar();
}

/**
 * Adopta los contadores que pinto la vitrina y los pone a latir.
 *
 * `subastas-vitrina.js` es un modulo protegido: no se toca. Lo que hace falta
 * —la fecha de cierre de cada lote— ya esta en la respuesta, y la tarjeta
 * lleva `data-subasta-id`, asi que se emparejan sin tocar aquel archivo.
 *
 * @param {HTMLElement} zona
 * @param {Array<object>} subastas
 */
function ponerEnHoraLosContadores(zona, subastas) {
  // Por indice de `data-subasta-id` y no con un selector: un id de subasta es
  // texto del servidor y meterlo en un `querySelector` obliga a escaparlo
  // (`CSS.escape`, que ademas no existe en todos los entornos). Recorrer las
  // tarjetas que ya estan en el DOM no tiene ese problema.
  const porId = new Map(subastas.map((s) => [String(s.id), s]));
  for (const tarjeta of zona.querySelectorAll('[data-subasta-id]')) {
    const subasta = porId.get(tarjeta.dataset.subastaId);
    const contador = tarjeta.querySelector('.subastas__contador');
    if (subasta?.fechaFin && contador) {
      adoptarCuentaAtras(contador, subasta.fechaFin);
    }
  }
  detenerContadores = vigilarCuentasAtras(zona);
}

function pararContadores() {
  if (detenerContadores) {
    detenerContadores();
    detenerContadores = null;
  }
}

/**
 * Paginacion sobre `.paginacion` del kit (UX-R2.8b).
 *
 * Era un bloque `subastas-paginacion__*` propio, calcado del que ya vive en
 * `componentes.css` desde PR-UX-6. El estado activo ademas se marcaba con una
 * clase modificadora ADEMAS de `aria-current`; el kit estiliza directamente
 * `[aria-current='page']`, asi que la clase sobraba y podia desincronizarse.
 */
function construirPaginacion(pagina) {
  const nav = h('nav', {
    clase: 'paginacion',
    atributos: { 'aria-label': 'Paginación de subastas' },
  });

  const irA = (numeroPagina) => {
    estado.pagina = numeroPagina;
    cargarYRenderizar();
  };

  const paginas = h('div', { clase: 'paginacion__paginas' });
  paginas.append(construirBotonPagina('Anterior', pagina.pagina - 1, pagina.pagina > 0, irA));

  for (const numero of paginasVisibles(pagina.pagina, pagina.totalPaginas)) {
    if (numero === '...') {
      paginas.append(h('span', { clase: 'paginacion__info', texto: '…' }));
      continue;
    }
    const boton = h('button', {
      clase: 'paginacion__pagina',
      // Vista 1-indexada; el backend es 0-indexado.
      texto: String(numero + 1),
      atributos: {
        type: 'button',
        'aria-current': numero === pagina.pagina ? 'page' : null,
        'aria-label': `Página ${numero + 1}`,
      },
    });
    boton.addEventListener('click', () => irA(numero));
    paginas.append(boton);
  }

  paginas.append(
    construirBotonPagina(
      'Siguiente',
      pagina.pagina + 1,
      pagina.pagina + 1 < pagina.totalPaginas,
      irA,
    ),
  );

  nav.append(
    h('p', {
      clase: 'paginacion__info',
      texto: `Página ${pagina.pagina + 1} de ${Math.max(pagina.totalPaginas, 1)}`,
    }),
    paginas,
  );
  return nav;
}

function construirBotonPagina(etiqueta, numeroDestino, habilitado, irA) {
  const boton = h('button', {
    clase: 'paginacion__pagina',
    texto: etiqueta,
    atributos: { type: 'button' },
  });
  boton.disabled = !habilitado;
  if (habilitado) {
    boton.addEventListener('click', () => irA(numeroDestino));
  }
  return boton;
}

/** Ventana de paginas a mostrar: primera, ultima, y vecinas de la actual. */
function paginasVisibles(actual, total) {
  const paginas = new Set([0, total - 1, actual - 1, actual, actual + 1]);
  const ordenadas = [...paginas].filter((p) => p >= 0 && p < total).sort((a, b) => a - b);

  const resultado = [];
  for (let i = 0; i < ordenadas.length; i++) {
    if (i > 0 && ordenadas[i] - ordenadas[i - 1] > 1) {
      resultado.push('...');
    }
    resultado.push(ordenadas[i]);
  }
  return resultado;
}

function debounce(funcion, esperaMs) {
  let temporizador;
  return (...argumentos) => {
    clearTimeout(temporizador);
    temporizador = setTimeout(() => funcion(...argumentos), esperaMs);
  };
}
