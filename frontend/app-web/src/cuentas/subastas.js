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
 * ## PLAYER-07b — revisión del modo jugador, punto 26
 *
 * «Los filtros de búsqueda y de ordenar por no tienen títulos […], la parte
 * del número de páginas sale el anterior y siguiente desbordados […]. La barra
 * de búsqueda izquierda toca scrolear toda la página para buscar los
 * filtros». Lo que cambia:
 *
 * - **Etiquetas visibles.** Buscar, «Ordenar por» y «Por página» tenían solo
 *   `aria-label`: quien mira no sabía qué era cada caja. Ahora cada una tiene
 *   su `<label for>`, y el panel lleva el título «Filtros» con cuántos hay
 *   puestos.
 * - **La paginación es la compartida** (`comun/paginacion.js`), con flechas
 *   con nombre accesible. La copia de aquí escribía «Anterior» y «Siguiente»
 *   en casillas de 32 px del kit, y el texto se salía de la casilla.
 * - **Los filtros son un panel aparte.** En escritorio, una columna fija
 *   (`sticky`) con su propio desplazamiento solo si no cabe; en un teléfono,
 *   un botón «Filtros» que abre un cajón: diálogo modal, foco dentro, Escape
 *   y botón para cerrar, y el foco vuelve al botón.
 *
 * PENDIENTE, a proposito fuera de este archivo: la suscripcion STOMP al canal
 * `/topic/subastas/listado`. El contador ya es real; lo que sigue sin llegar
 * solo es la puja de otra persona, que exige el canal.
 */

import { montarCabecera } from '../comun/cabecera-app.js';
import { listarSubastas, sugerirSubastas } from './cliente-subastas.js';
import { actualizarTarjeta, construirVitrinaSubastas } from './subastas-vitrina.js';
import { urlDelCanal } from './pujas-api.js';
import { conectarStomp } from '../comun/transporte-stomp.js';
import { CASILLAS_VISIBLES, construirPaginacion } from '../comun/paginacion.js';
import { construirFiltros, leerFiltros } from './subastas-filtros.js';
import { h } from '../comun/ui/dom.js';
import { icono } from '../comun/ui/icono.js';
import { bloquearDesplazamiento } from '../comun/ui/dialogo.js';
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

/**
 * UXC-9 — «selector de tamaño» de 7.7.9. Múltiplos de 16 para que la rejilla
 * de cuatro columnas quede llena; el contrato admite `size` de 1 a 100.
 */
export const TAMANOS_DE_PAGINA = Object.freeze([16, 32, 48]);

/** El canal público del listado (contracts/websocket/subastas.yaml 1.1.0). */
export const CANAL_DEL_LISTADO = '/topic/subastas/listado';

/**
 * PLAYER-07b — desde aquí los filtros son una columna fija; por debajo, un
 * cajón. Es el mismo punto de quiebre que ya tenía el CSS de la vista.
 */
const ESCRITORIO = '(min-width: 900px)';

/**
 * PLAYER-07b — en un teléfono caben cinco casillas de 44 px y las dos
 * flechas en una sola fila; diez partían la paginación en dos.
 */
const TELEFONO = '(max-width: 599px)';
const CASILLAS_EN_TELEFONO = 5;

const estado = {
  pagina: 0,
  filtros: {},
  ordenarPor: 'FECHA_PUBLICACION',
  tamano: TAMANO_PAGINA,
};

/** El canal del listado abierto, para cerrarlo al montar otra vez. */
let canalDelListado = null;

/** Como parar el latido de los contadores de la tanda anterior. */
let detenerContadores = null;

/**
 * PLAYER-07b — cada lectura del listado lleva su número y solo pinta la más
 * reciente. Dos filtros seguidos lanzaban dos peticiones, y si la primera
 * respondía la última se quedaba en pantalla un listado que ya no era el
 * que se había pedido.
 */
let peticionVigente = 0;

/** La última página pintada, para rehacer su paginación al girar el teléfono. */
let ultimaPagina = null;

/** Lo que la pantalla montada necesita fuera de `inicializar`. */
let vista = null;

document.addEventListener('DOMContentLoaded', () => inicializar());

/**
 * Monta la pantalla sobre `#raiz-subastas`.
 *
 * Exportada desde UX-R2.8b: era privada y por eso la vista principal del
 * mercado —la que enseñaba «Error 404» en las cinco anchuras— no tenia ni
 * una prueba. Se sigue enganchando a `DOMContentLoaded` como siempre.
 */
export function inicializar({ conectarCanal = conectarStomp, urlCanal = null } = {}) {
  estado.pagina = 0;
  estado.filtros = {};
  estado.ordenarPor = 'FECHA_PUBLICACION';
  estado.tamano = TAMANO_PAGINA;
  ultimaPagina = null;
  vista?.desmontar();
  vista = null;
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

  const filtros = construirFiltros({ alCambiar: aplicarFiltros });
  // PLAYER-07b — con id, para que el resto de la pantalla pueda nombrarlo.
  filtros.id = 'mercado-filtros-formulario';
  // El evento `reset` llega ANTES de que el navegador vacíe el formulario (lo
  // documenta #390, PR abierta sobre `subastas-filtros.js`), así que el aviso
  // que manda el panel en ese momento trae los filtros viejos y «Limpiar
  // filtros» no limpiaba el listado. Se vuelve a leer en la tarea siguiente,
  // cuando ya está vacío. `aplicarFiltros` no repite una carga si los filtros
  // no cambiaron, así que cuando #390 entre esto no duplica nada.
  filtros.addEventListener('reset', () => {
    setTimeout(() => aplicarFiltros(leerFiltros(filtros)), 0);
  });

  const panel = construirPanelDeFiltros(filtros);
  const abrirFiltros = construirBotonFiltros();

  // Buscar y ordenar son el mismo control compuesto, no dos bloques apilados.
  // PLAYER-07b: van encima de los resultados, en su columna; así el panel de
  // filtros empieza arriba, a la altura del título.
  const controles = h('div', { clase: 'mercado__controles subastas-herramientas' });
  controles.append(construirBarraBusqueda(), construirBarraOrden(abrirFiltros));

  const zonaResultados = document.createElement('div');
  zonaResultados.id = 'subastas-resultados';

  // UXC-9 — lo que cambia fuera de la página que se ve (una subasta nueva,
  // otra página) se avisa aquí, con la forma de verlo; lo que se ve se pone
  // al día solo.
  const novedades = h('div', {
    clase: 'aviso aviso--info mercado__novedades',
    datos: { zona: 'novedades-mercado' },
    atributos: { role: 'status' },
  });
  novedades.hidden = true;
  const columna = h('div', {
    clase: 'mercado__resultados',
    hijos: [controles, novedades, zonaResultados],
  });

  // El velo del cajón va el último: entre el panel y los resultados no puede
  // haber nada (los resultados son lo que viene DESPUÉS de los filtros).
  const velo = h('div', { clase: 'mercado__velo', datos: { zona: 'velo-filtros' } });
  velo.hidden = true;

  const contenido = h('div', {
    clase: 'subastas-contenido',
    hijos: [panel.elemento, columna, velo],
  });
  raiz.appendChild(contenido);

  const cajon = montarCajonDeFiltros({
    panel: panel.elemento,
    boton: abrirFiltros.boton,
    cerrar: panel.cerrar,
    verResultados: panel.verResultados,
    velo,
  });

  // El cajón es cosa del teléfono: si la ventana se ensancha con él abierto,
  // se cierra (en ancho el panel ya está a la vista, en su columna). Y la
  // paginación se rehace con las casillas que caben.
  const esEscritorio = globalThis.matchMedia?.(ESCRITORIO);
  const alCambiarAncho = () => {
    if (esEscritorio?.matches) {
      cajon.cerrar({ devolverFoco: false });
    }
  };
  esEscritorio?.addEventListener?.('change', alCambiarAncho);
  const esTelefono = globalThis.matchMedia?.(TELEFONO);
  const alGirar = () => repintarPaginacion();
  esTelefono?.addEventListener?.('change', alGirar);

  vista = {
    rotulos: { boton: abrirFiltros.texto, activos: panel.activos },
    filtros,
    cajon,
    desmontar() {
      cajon.cerrar({ devolverFoco: false });
      esEscritorio?.removeEventListener?.('change', alCambiarAncho);
      esTelefono?.removeEventListener?.('change', alGirar);
    },
  };
  rotularFiltros();

  cargarYRenderizar();
  escucharElMercado({ conectarCanal, urlCanal: urlCanal ?? urlDelCanal(), novedades });
}

/**
 * Lo que manda el panel al cambiar algo. El texto de busqueda vive aparte
 * (construirBarraBusqueda), asi que se conserva aqui en vez de dejar que el
 * panel lo borre al no conocerlo. Si los filtros no cambiaron, no se vuelve a
 * pedir nada (ver el `reset` de `inicializar`).
 *
 * @param {object} nuevosFiltros lo que devuelve `leerFiltros`
 */
function aplicarFiltros(nuevosFiltros) {
  const siguientes = { ...nuevosFiltros, q: estado.filtros.q };
  if (firmaDeFiltros(siguientes) === firmaDeFiltros(estado.filtros)) {
    return;
  }
  estado.filtros = siguientes;
  estado.pagina = 0;
  rotularFiltros();
  cargarYRenderizar();
}

/**
 * Los filtros como texto comparable: sin los vacíos y con las claves (y los
 * tipos marcados) en orden, para que el orden en que se marcaron no cuente.
 *
 * @param {object} filtros
 * @returns {string}
 */
function firmaDeFiltros(filtros) {
  return JSON.stringify(
    Object.entries(filtros ?? {})
      .filter(([, valor]) => valor !== undefined && valor !== null && valor !== '')
      .map(([clave, valor]) => [clave, Array.isArray(valor) ? [...valor].sort() : valor])
      .sort(([a], [b]) => a.localeCompare(b)),
  );
}

/** Cuántos filtros del panel hay puestos (la búsqueda va aparte). */
function filtrosPuestos() {
  return Object.entries(estado.filtros).filter(
    ([clave, valor]) => clave !== 'q' && valor !== undefined,
  ).length;
}

/**
 * Un panel plegado no puede esconder que hay filtros puestos: quien no vea
 * ni el panel ni la causa se queda mirando «ninguna subasta coincide» sin
 * saber por que. El botón «Filtros» y el título del panel dicen cuántos hay.
 */
function rotularFiltros() {
  if (!vista) {
    return;
  }
  const puestos = filtrosPuestos();
  const cuantos = `${puestos} ${puestos === 1 ? 'activo' : 'activos'}`;
  vista.rotulos.boton.textContent = puestos === 0 ? 'Filtros' : `Filtros · ${cuantos}`;
  vista.rotulos.activos.textContent = cuantos;
  vista.rotulos.activos.hidden = puestos === 0;
}

/**
 * El panel de filtros: título, el formulario de siempre y, para el cajón del
 * teléfono, el botón de cerrar y «Ver resultados».
 *
 * En escritorio es una columna que se queda a la vista mientras se recorren
 * los resultados; si sus grupos no caben en la pantalla, se desplaza él solo,
 * sin arrastrar la página. El CSS decide cuál de las dos formas toma.
 *
 * @param {HTMLFormElement} formulario
 */
function construirPanelDeFiltros(formulario) {
  const titulo = h('h2', {
    clase: 'mercado__filtros-titulo',
    texto: 'Filtros',
    atributos: { id: 'mercado-filtros-titulo' },
  });
  const activos = h('p', { clase: 'mercado__filtros-activos', datos: { zona: 'filtros-activos' } });
  activos.hidden = true;
  const cerrar = h('button', {
    clase: 'boton boton--icono boton--secundario mercado__cerrar-filtros',
    datos: { accion: 'cerrar-filtros' },
    atributos: { type: 'button', 'aria-label': 'Cerrar filtros' },
    hijos: [icono('cerrar', { etiqueta: null })],
  });
  const verResultados = h('button', {
    clase: 'boton boton--primario mercado__ver-resultados',
    texto: 'Ver resultados',
    datos: { accion: 'ver-resultados' },
    atributos: { type: 'button' },
  });

  const elemento = h('aside', {
    clase: 'mercado__filtros',
    atributos: { id: 'mercado-filtros', 'aria-labelledby': 'mercado-filtros-titulo' },
    hijos: [
      h('div', {
        clase: 'mercado__filtros-cabecera',
        hijos: [
          icono('filtro', { clase: 'icono mercado__filtros-icono', etiqueta: null }),
          h('div', { clase: 'mercado__filtros-rotulo', hijos: [titulo, activos] }),
          cerrar,
        ],
      }),
      h('div', { clase: 'mercado__filtros-cuerpo', hijos: [formulario] }),
      h('div', { clase: 'mercado__filtros-pie', hijos: [verResultados] }),
    ],
  });
  return { elemento, activos, cerrar, verResultados };
}

/** El botón «Filtros» de la barra: solo se ve en un teléfono (CSS). */
function construirBotonFiltros() {
  const texto = h('span', { texto: 'Filtros' });
  const boton = h('button', {
    clase: 'boton boton--secundario mercado__abrir-filtros',
    datos: { accion: 'abrir-filtros' },
    atributos: {
      type: 'button',
      'aria-controls': 'mercado-filtros',
      'aria-expanded': 'false',
      'aria-haspopup': 'dialog',
    },
    hijos: [icono('filtro', { etiqueta: null }), texto],
  });
  return { boton, texto };
}

/**
 * El cajón de filtros del teléfono.
 *
 * Mismas garantías que el diálogo del kit (`comun/ui/dialogo.js`), sobre un
 * panel que NO se crea ni se destruye —es el mismo de la columna de
 * escritorio, y el formulario conserva lo marcado—: `role="dialog"` con
 * `aria-modal` mientras está abierto, el foco entra y no se escapa, Escape,
 * la equis, «Ver resultados» y el velo cierran, la página de atrás no se
 * desplaza y, al cerrar, el foco vuelve al botón «Filtros».
 *
 * @param {{panel: HTMLElement, boton: HTMLButtonElement, cerrar: HTMLButtonElement,
 *          verResultados: HTMLButtonElement, velo: HTMLElement}} piezas
 */
function montarCajonDeFiltros({ panel, boton, cerrar, verResultados, velo }) {
  let abierto = false;
  let soltarDesplazamiento = null;

  // `a[href]` y no `[href]`: los iconos del sprite son `<use href>`, y con el
  // selector suelto el «primero» del panel era el icono del título, que no
  // admite foco, y el tabulador no daba la vuelta.
  const enfocables = () =>
    [
      ...panel.querySelectorAll(
        'button, a[href], input, select, textarea, [tabindex]:not([tabindex="-1"])',
      ),
    ].filter((elemento) => !elemento.disabled && !elemento.closest('[hidden]'));

  function alTeclado(evento) {
    if (evento.key === 'Escape') {
      evento.preventDefault();
      cerrarCajon();
      return;
    }
    if (evento.key !== 'Tab') {
      return;
    }
    const lista = enfocables();
    if (lista.length === 0) {
      return;
    }
    const primero = lista[0];
    const ultimo = lista[lista.length - 1];
    if (!panel.contains(document.activeElement)) {
      evento.preventDefault();
      primero.focus();
    } else if (evento.shiftKey && document.activeElement === primero) {
      evento.preventDefault();
      ultimo.focus();
    } else if (!evento.shiftKey && document.activeElement === ultimo) {
      evento.preventDefault();
      primero.focus();
    }
  }

  function abrirCajon() {
    if (abierto) {
      return;
    }
    abierto = true;
    panel.dataset.cajon = 'abierto';
    panel.setAttribute('role', 'dialog');
    panel.setAttribute('aria-modal', 'true');
    boton.setAttribute('aria-expanded', 'true');
    velo.hidden = false;
    soltarDesplazamiento = bloquearDesplazamiento();
    document.addEventListener('keydown', alTeclado);
    cerrar.focus();
  }

  function cerrarCajon({ devolverFoco = true } = {}) {
    if (!abierto) {
      return;
    }
    abierto = false;
    delete panel.dataset.cajon;
    panel.removeAttribute('role');
    panel.removeAttribute('aria-modal');
    boton.setAttribute('aria-expanded', 'false');
    velo.hidden = true;
    document.removeEventListener('keydown', alTeclado);
    soltarDesplazamiento?.();
    soltarDesplazamiento = null;
    if (devolverFoco) {
      boton.focus();
    }
  }

  boton.addEventListener('click', abrirCajon);
  cerrar.addEventListener('click', () => cerrarCajon());
  verResultados.addEventListener('click', () => cerrarCajon());
  velo.addEventListener('click', () => cerrarCajon());

  return {
    abrir: abrirCajon,
    cerrar: cerrarCajon,
    get abierto() {
      return abierto;
    },
  };
}

/**
 * UXC-9 — el listado en vivo (7.7.9: contador y número de pujas en tiempo
 * real). El canal del listado es público: cada cambio llega con el resumen
 * de la subasta. Si está en la página que se ve, su tarjeta se pone al día;
 * si no, se avisa de que hay cambios y se ofrece ponerse al día. Sin canal,
 * el listado sigue siendo el de la última lectura, que es lo que ya era.
 */
function escucharElMercado({ conectarCanal, urlCanal, novedades }) {
  canalDelListado?.cerrar?.();
  canalDelListado = null;
  if (typeof conectarCanal !== 'function') {
    return;
  }
  let fuera = 0;
  Promise.resolve()
    .then(() => conectarCanal({ url: urlCanal }))
    .then((canal) => {
      canalDelListado = canal;
      canal.suscribir(CANAL_DEL_LISTADO, (resumen) => {
        const zona = document.getElementById('subastas-resultados');
        if (!resumen?.id || !zona) {
          return;
        }
        const tarjeta = [...zona.querySelectorAll('[data-subasta-id]')].find(
          (t) => t.dataset.subastaId === String(resumen.id),
        );
        if (tarjeta) {
          actualizarTarjeta(tarjeta, resumen);
          return;
        }
        fuera += 1;
        avisarNovedades(novedades, fuera, () => {
          fuera = 0;
          novedades.hidden = true;
          cargarYRenderizar();
        });
      });
    })
    .catch(() => {
      // Sin canal no hay nada roto que decir: la página no promete tiempo real
      // y el listado es el de la última lectura.
    });
}

/** El aviso de cambios fuera de la vista, con su botón para verlos. */
function avisarNovedades(novedades, cuantos, alActualizar) {
  const boton = h('button', {
    clase: 'boton boton--secundario boton--pequeno',
    texto: 'Actualizar el listado',
    atributos: { type: 'button' },
    datos: { accion: 'actualizar-mercado' },
  });
  boton.addEventListener('click', alActualizar);
  novedades.replaceChildren(
    h('span', {
      texto:
        cuantos === 1
          ? 'Hubo un cambio en una subasta que no está en esta página.'
          : `Hubo ${cuantos} cambios en subastas que no están en esta página.`,
    }),
    boton,
  );
  novedades.hidden = false;
}

/**
 * Una etiqueta visible pegada a su control (PLAYER-07b). Antes solo había
 * `aria-label`: el lector de pantalla lo decía, quien miraba no lo veía.
 *
 * @param {string} texto
 * @param {string} paraId
 */
function etiquetaVisible(texto, paraId) {
  return h('label', { clase: 'mercado__etiqueta', texto, atributos: { for: paraId } });
}

/** Buscador con autocompletado, independiente del de la barra compartida. */
function construirBarraBusqueda() {
  const contenedor = document.createElement('div');
  contenedor.className = 'subastas-busqueda';

  const campo = document.createElement('input');
  campo.type = 'search';
  campo.id = 'subastas-buscar';
  campo.className = 'subastas-busqueda__campo';
  campo.placeholder = 'Nombre, tipo o habilidad…';
  campo.setAttribute('aria-autocomplete', 'list');
  campo.setAttribute('aria-controls', 'subastas-sugerencias');
  campo.autocomplete = 'off';

  // La lupa es adorno: el nombre del campo lo da su etiqueta.
  const caja = h('div', {
    clase: 'subastas-busqueda__caja',
    hijos: [icono('buscar', { clase: 'icono subastas-busqueda__icono', etiqueta: null }), campo],
  });

  const listaSugerencias = document.createElement('ul');
  listaSugerencias.id = 'subastas-sugerencias';
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

  contenedor.append(etiquetaVisible('Buscar subastas', campo.id), caja, listaSugerencias);
  return contenedor;
}

/**
 * Ordenar y tamaño de página, cada uno con su etiqueta. En un teléfono el
 * botón «Filtros» va delante, en la misma fila.
 *
 * @param {{boton: HTMLButtonElement}} abrirFiltros
 */
function construirBarraOrden(abrirFiltros) {
  const contenedor = document.createElement('div');
  contenedor.className = 'subastas-orden';

  const control = document.createElement('select');
  control.id = 'subastas-ordenar';
  control.className = 'subastas-orden__control';

  const opciones = [
    { valor: 'FECHA_PUBLICACION', etiqueta: 'Más recientes' },
    // G5 (7.7.9, ms-subastas-listado 1.2.0): los dos sentidos que faltaban.
    { valor: 'FECHA_PUBLICACION_ASC', etiqueta: 'Más antiguas' },
    { valor: 'PRECIO_ASC', etiqueta: 'Precio: menor a mayor' },
    { valor: 'PRECIO_DESC', etiqueta: 'Precio: mayor a menor' },
    { valor: 'TIEMPO_RESTANTE', etiqueta: 'Termina pronto' },
    { valor: 'TIEMPO_RESTANTE_DESC', etiqueta: 'Termina más tarde' },
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

  // UXC-9 — el tamaño de página (7.7.9 «selector de tamaño»). La etiqueta ya
  // dice «Por página»: cada opción es solo la cifra.
  const tamano = document.createElement('select');
  tamano.id = 'subastas-tamano';
  tamano.className = 'subastas-orden__control';
  tamano.dataset.control = 'tamano-pagina';
  for (const valor of TAMANOS_DE_PAGINA) {
    const opcion = document.createElement('option');
    opcion.value = String(valor);
    opcion.textContent = String(valor);
    tamano.appendChild(opcion);
  }
  tamano.addEventListener('change', () => {
    estado.tamano = Number(tamano.value) || TAMANO_PAGINA;
    estado.pagina = 0;
    cargarYRenderizar();
  });

  contenedor.append(
    abrirFiltros.boton,
    h('div', {
      clase: 'mercado__campo mercado__campo--orden',
      hijos: [etiquetaVisible('Ordenar por', control.id), control],
    }),
    h('div', {
      clase: 'mercado__campo',
      hijos: [etiquetaVisible('Por página', tamano.id), tamano],
    }),
  );
  return contenedor;
}

async function cargarYRenderizar() {
  const zona = document.getElementById('subastas-resultados');
  if (!zona) {
    return;
  }
  const peticion = ++peticionVigente;

  // Cada tanda se lleva por delante el latido de la anterior: si no, cada
  // filtro dejaria un temporizador corriendo sobre elementos ya borrados.
  pararContadores();

  pintarEstado(zona, estadoDeCarga({ filas: 4, etiqueta: 'Cargando subastas…' }));

  let pagina;
  try {
    pagina = await listarSubastas(
      { ...estado.filtros, ordenarPor: estado.ordenarPor },
      estado.pagina,
      estado.tamano,
    );
  } catch (error) {
    if (peticion === peticionVigente) {
      pintarEstado(zona, estadoDelFallo(error));
    }
    return;
  }
  // Llegó tarde: ya se pidió otro listado y es ese el que tiene que verse.
  if (peticion !== peticionVigente) {
    return;
  }

  if (pagina.contenido.length === 0) {
    ultimaPagina = null;
    pintarEstado(zona, estadoSinResultados());
    return;
  }

  const vitrina = construirVitrinaSubastas(pagina, {
    alAbrirDetalle: (subasta) => {
      globalThis.location.href = rutaDePujas(subasta.id);
    },
    // UXC-8 — el botón «Comprar ahora» de la vitrina existía pero nadie le
    // pasaba el manejador, así que no se pintaba nunca. Lleva a la subasta con
    // la confirmación abierta: comprar exige confirmarlo (el contrato rechaza
    // `confirmado: false`), y ahí se ve el precio, el saldo y el objeto.
    alComprarAhora: (subasta) => {
      globalThis.location.href = rutaDePujas(subasta.id, { comprar: true });
    },
  });

  ultimaPagina = pagina;
  zona.replaceChildren(vitrina, construirPaginacionDelMercado(pagina));
  ponerEnHoraLosContadores(zona, pagina.contenido);
}

/**
 * La sala de pujas de una subasta (`pujas.html`), y con `comprar` la
 * confirmación de la compra inmediata ya abierta.
 *
 * @param {string} id
 * @param {{comprar?: boolean}} [opciones]
 * @returns {string}
 */
export function rutaDePujas(id, { comprar = false } = {}) {
  const parametros = new URLSearchParams({ id: String(id) });
  if (comprar) {
    parametros.set('accion', 'comprar');
  }
  return `./pujas.html?${parametros}`;
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
 * El panel es un `<form>`: se vacía con su `reset()` nativo, sin lógica a mano
 * campo por campo. PLAYER-07b: el listado se pide aquí directamente, con los
 * filtros ya vacíos, en vez de esperar al aviso del panel (que en `reset`
 * llega con los filtros viejos; ver `inicializar`). El aviso que llegue
 * después ve los mismos filtros y no vuelve a pedir nada.
 */
function limpiarFiltros() {
  const campo = document.querySelector('.subastas-busqueda__campo');
  if (campo) {
    campo.value = '';
  }
  vista?.filtros.reset();
  estado.filtros = {};
  estado.pagina = 0;
  rotularFiltros();
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
 * La paginacion del mercado es la compartida (`comun/paginacion.js`) —
 * PLAYER-07b. La copia que habia aqui escribia «Anterior» y «Siguiente» en
 * las casillas de 32 px del kit y el texto se salia de la casilla. Las
 * flechas son las del componente, con su nombre («Página anterior»,
 * «Página siguiente») y siempre a la vista: apagadas en los extremos, para que
 * la fila no salte de sitio. Diez casillas como mucho (7.7.9); cinco en un
 * teléfono, para que quepan en una fila.
 *
 * @param {{pagina: number, totalPaginas: number}} pagina
 * @returns {HTMLElement}
 */
function construirPaginacionDelMercado(pagina) {
  const total = Number.isInteger(pagina.totalPaginas) ? Math.max(pagina.totalPaginas, 0) : 0;
  const pedida = Number.isInteger(pagina.pagina) ? pagina.pagina : 0;
  const actual = Math.min(Math.max(pedida, 0), Math.max(total - 1, 0));
  const telefono = globalThis.matchMedia?.(TELEFONO)?.matches ?? false;
  const nav = construirPaginacion(
    { paginaActual: actual, totalPaginas: total },
    (numero) => {
      estado.pagina = numero;
      cargarYRenderizar();
    },
    {
      etiqueta: 'Páginas de subastas',
      flechas: 'siempre',
      casillas: telefono ? CASILLAS_EN_TELEFONO : CASILLAS_VISIBLES,
    },
  );
  nav.classList.add('mercado__paginacion');
  return nav;
}

/** Rehace la paginación que se ve (p. ej. al girar el teléfono). */
function repintarPaginacion() {
  const actual = document.querySelector('#subastas-resultados .mercado__paginacion');
  if (actual && ultimaPagina) {
    actual.replaceWith(construirPaginacionDelMercado(ultimaPagina));
  }
}

function debounce(funcion, esperaMs) {
  let temporizador;
  return (...argumentos) => {
    clearTimeout(temporizador);
    temporizador = setTimeout(() => funcion(...argumentos), esperaMs);
  };
}
