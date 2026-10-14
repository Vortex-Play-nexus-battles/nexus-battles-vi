/**
 * HU-INV-001 - Orquesta la vitrina y sus cuatro estados.
 *
 * Separa la decision de que mostrar (este archivo) de como se dibuja la
 * rejilla (`vitrina.js`) y de como se pide el dato (`cliente-inventario.js`).
 */

import {
  consultarPagina,
  buscarElementos,
  crearElemento,
  modificarElemento,
  consultarEquipamiento,
  consultarEstadisticasDelHeroe,
  equiparElemento,
  desequiparElemento,
  eliminarElemento,
  CATEGORIAS,
} from './cliente-inventario.js';
import { retratoDeHeroe } from '../../comun/ui/juego/heroe.js';
import { bloqueDeEstadisticas } from '../../comun/ui/juego/estadisticas.js';
import { construirVitrina, PRODUCTOS_POR_PAGINA } from './vitrina.js';
import { pintarRetratos } from './retratos.js';
import {
  idsEquipados as idsDeEquipo,
  motivoDelRechazo,
  pintarEquipamiento,
} from './equipamiento.js';
import { construirCarga, construirVacio, construirError } from './estados-vista.js';
import { abrirFicha } from './ficha-producto.js';
import { construirPaginacion } from '../../comun/paginacion.js';
import { acusar } from '../../comun/ui/acuse.js';
import { confirmar as confirmarEnDialogo } from '../../comun/ui/dialogo.js';
import { montarPestanas } from '../../comun/ui/pestanas.js';
import { consultarProducto as consultarProductoDelCatalogo } from './cliente-productos.js';
import { estadoDeHeroe, estadoDeObjeto, selloDeEstado } from '../../comun/ui/juego/estado-heroe.js';
import { reunirInventario, esHeroe, paginaLocal, mapaDeEquipados } from './coleccion-inventario.js';
import { pintarHeroes } from './heroes-inventario.js';
import { pintarHeroesDelNexo } from './heroes-del-nexo.js';
import { listarProductos } from './cliente-productos.js';
import { consultarTablaDeNiveles } from './cliente-heroes.js';
import { fuenteDeMisiones } from '../misiones/fuente-misiones.js';
import { montarBannerDeMisiones } from '../misiones/banner-misiones.js';
import { complementoDeOpiniones } from '../../plataforma/comentarios/hilo-comentarios.js';
import { leerSesion } from '../../comun/sesion.js';

/**
 * Quién da de alta elementos a mano (`POST /inventario/elementos`):
 * inventario.yaml, desde B4, solo SERVICIO, ADMINISTRADOR o
 * SUPER_ADMINISTRADOR. RFINAL-04 (revisión de AWS DEV del 4-oct) — al
 * jugador se le ofrecía «Agregar elemento» con campos técnicos (producto,
 * parte de armadura) que el servidor le rechaza con 403: fuera de su flujo.
 * Lo suyo se consigue en la tienda, las subastas y las misiones.
 */
export const ROLES_QUE_DAN_DE_ALTA = Object.freeze(['ADMINISTRADOR', 'SUPER_ADMINISTRADOR']);

/**
 * UXC-3 — §7.1: el detalle de un producto lleva su calificación promedio y el
 * hilo de comentarios. Va en todas las fichas que abre el inventario, al
 * final, después de lo que dice el catálogo.
 */
const COMPLEMENTOS_DE_LA_FICHA = [complementoDeOpiniones()];

/**
 * §7.1 al pie de la letra (revisión final, 13-oct): con una página aparece la
 * casilla «1», y las flechas solo están si hay páginas ocultas a su lado y al
 * pulsarlas las muestran (ver `comun/paginacion.js`).
 */
const PAGINACION_DE_7_1 = Object.freeze({ unaPagina: 'visible', flechas: 'revelan-ocultas' });

/**
 * Tras repintar un control de paginación, el foco vuelve a donde estaba: a la
 * misma flecha si sigue existiendo, o a la casilla de la página actual
 * (RNF-ACC-002: repintar no puede dejar el foco en el body).
 */
function enfocarControl(contenedor, direccion) {
  const flecha = direccion
    ? contenedor.querySelector(`[data-direccion="${direccion}"]:not([disabled])`)
    : null;
  (flecha ?? contenedor.querySelector('[aria-current="page"]'))?.focus();
}

const TIPOS = [
  ['HEROE', 'Héroe'],
  ['HABILIDAD', 'Habilidad'],
  ['ARMA', 'Arma'],
  ['ARMADURA', 'Armadura'],
  ['ITEM', 'Ítem'],
  ['EPICA', 'Épica'],
];

/** Lo que dice la pestaña «Objetos» sin búsqueda… */
const TEXTO_DE_OBJETOS =
  'Armas, armaduras, ítems, habilidades y épicas. Cada uno dice si está equipado, libre o bloqueado.';

/**
 * …y con una búsqueda puesta (PLAYER-07a): la búsqueda es de todo el
 * inventario, así que en sus resultados también salen héroes.
 */
const TEXTO_DE_RESULTADOS =
  'Resultados de tu búsqueda en todo el inventario: héroes y objetos. «Limpiar» vuelve a tus objetos.';

const PARTES_ARMADURA = [
  ['CASCO', 'Casco'],
  ['PECHO', 'Pecho'],
  ['GUANTES', 'Guantes'],
  ['BRAZALETES', 'Brazaletes'],
  ['PANTALON', 'Pantalón'],
  ['ZAPATOS', 'Zapatos'],
];

/**
 * Pinta el inventario de un jugador dentro de `contenedor`.
 *
 * @param {HTMLElement} contenedor donde se monta la vista.
 * @param {string} identidad jugador autenticado, que viaja en la cabecera.
 * @param {number} numeroPagina pagina pedida, desde cero.
 * @param {{consultar?: Function, alEditar?: Function, sigueVigente?: () => boolean}} opciones
 *   inyeccion para las pruebas. `sigueVigente` permite descartar una respuesta
 *   que llego tarde: sin el, dos cambios de pagina seguidos pueden pintar el
 *   resultado del primero encima del segundo (HU-INV-011).
 * @returns {Promise<object|null>} pagina mostrada o null cuando falla la consulta.
 */
export async function montarVitrina(
  contenedor,
  identidad,
  numeroPagina = 0,
  {
    consultar = consultarPagina,
    alEditar,
    alEquipar,
    alEliminar,
    mensajeCarga,
    mensajeVacio,
    detalleVacio,
    accionVacio,
    estadoDe,
    sigueVigente = () => true,
  } = {},
) {
  contenedor.replaceChildren(construirCarga(mensajeCarga));

  let pagina;
  try {
    pagina = await consultar(identidad, numeroPagina);
  } catch (fallo) {
    // El detalle tecnico es para el equipo; al jugador se le habla en su idioma.
    console.error('No se pudo cargar la vitrina del inventario', fallo);
    if (sigueVigente()) {
      // UX-R3.5 — §18: el estado de error trae su salida. Antes decia «vuelve
      // a intentarlo en un momento» y la unica forma de intentarlo era
      // recargar la pagina entera, que ademas pierde la pagina en la que se
      // estaba. Esto reintenta la misma consulta.
      contenedor.replaceChildren(
        construirError(undefined, undefined, {
          texto: 'Reintentar',
          alPulsar: () =>
            montarVitrina(contenedor, identidad, numeroPagina, {
              consultar,
              alEditar,
              alEquipar,
              mensajeCarga,
              mensajeVacio,
              detalleVacio,
              accionVacio,
              estadoDe,
              sigueVigente,
            }),
        }),
      );
    }
    return null;
  }

  // Mientras se esperaba, el jugador pudo pedir otra pagina. Pintar esta
  // ahora dejaria la vitrina mostrando una pagina que ya nadie pidio.
  if (!sigueVigente()) {
    return pagina;
  }

  if (!pagina || pagina.elementos.length === 0) {
    contenedor.replaceChildren(
      accionVacio === undefined
        ? construirVacio(mensajeVacio, detalleVacio)
        : construirVacio(mensajeVacio, detalleVacio, accionVacio),
    );
    return pagina;
  }

  contenedor.replaceChildren(
    construirVitrina(pagina, {
      alEditar,
      alEquipar,
      alEliminar,
      estadoDe,
      // HU-INV-007: la ficha lee el catalogo por su cuenta; el inventario
      // solo guarda la referencia (RF-ADM-10). Revisión final (13-oct): §7.1
      // pide en el detalle «la información del inventario»: el nombre que le
      // puso el jugador y su estado van en «En tu inventario».
      alAbrirDetalle: (elemento, { origen } = {}) =>
        abrirFicha(elemento.productoId, {
          origen: origen ?? document.activeElement,
          complementos: COMPLEMENTOS_DE_LA_FICHA,
          // R5: para un heroe, la ficha completa con lo que el jugador tiene de
          // verdad. Para lo demas sobran y se ignoran.
          elementoId: elemento.id,
          identidad,
          nombrePropio: elemento.tipo === 'HEROE' ? elemento.nombrePropio : null,
          enTuInventario: {
            nombrePropio: elemento.nombrePropio,
            sello: typeof estadoDe === 'function' ? estadoDe(elemento) : null,
          },
        }),
    }),
  );

  // UX-R2.5 — los retratos llegan DESPUES, uno por producto, porque el
  // inventario no guarda la imagen y `productos.yaml` no tiene consulta por
  // lotes. La vitrina ya esta en pantalla con el icono de cada tipo; esto
  // solo la mejora cuando el catalogo contesta. No se espera: si tardara o
  // fallara, la vista ya esta usable.
  pintarRetratos(contenedor).catch((fallo) =>
    console.warn('No se pudieron traer los retratos del catálogo', fallo),
  );
  return pagina;
}

function elementoHtml(etiqueta, clase, texto) {
  const elemento = document.createElement(etiqueta);
  if (clase) {
    elemento.className = clase;
  }
  if (texto) {
    elemento.textContent = texto;
  }
  return elemento;
}

function campoFormulario(texto, nombre, tipo = 'text') {
  const etiqueta = elementoHtml('label', 'inventario-editor__campo');
  etiqueta.appendChild(elementoHtml('span', 'inventario-editor__etiqueta', texto));
  const control = document.createElement(tipo === 'select' ? 'select' : 'input');
  control.name = nombre;
  control.required = true;
  if (tipo !== 'select') {
    control.type = tipo;
  }
  control.className = 'inventario-editor__control';
  etiqueta.appendChild(control);
  return { etiqueta, control };
}

function construirGestion() {
  const cabecera = elementoHtml('header', 'inventario-cabecera');
  const titulo = elementoHtml('h1', 'vitrina-titulo', 'Mi inventario');
  const botonNuevo = elementoHtml('button', 'inventario__nuevo', 'Agregar elemento');
  botonNuevo.type = 'button';
  cabecera.append(titulo, botonNuevo);

  // PLAYER-07a (revisión del 6-oct, punto 25) — la búsqueda es GENERAL: va
  // arriba, sobre las pestañas, porque busca en todo el inventario (héroes y
  // objetos), no solo en «Objetos». Sus resultados se siguen pintando en la
  // vitrina de «Objetos», que es la que pagina (HU-INV-011), y al buscar se va
  // a esa pestaña. La etiqueta ya no está oculta: quien mira sabe qué busca.
  const busqueda = elementoHtml('form', 'inventario-busqueda inventario-busqueda--general');
  busqueda.setAttribute('role', 'search');
  // La validación del mínimo la hace el envío con la frase del juego: el globo
  // nativo saldría en el idioma del navegador (revisión final, 13-oct).
  busqueda.noValidate = true;
  const busquedaCampo = elementoHtml('label', 'inventario-busqueda__campo');
  const busquedaEtiqueta = elementoHtml(
    'span',
    'inventario-busqueda__etiqueta',
    'Buscar en tu inventario',
  );
  const busquedaControl = document.createElement('input');
  busquedaControl.className = 'inventario-busqueda__control';
  busquedaControl.type = 'search';
  busquedaControl.name = 'criterio';
  busquedaControl.minLength = 4;
  busquedaControl.autocomplete = 'off';
  busquedaControl.maxLength = 100;
  // Lo que de verdad indexa el servicio (inventario.yaml 1.7.0, búsqueda): lo
  // que guarda el inventario y la información del producto en el catálogo.
  busquedaControl.placeholder = 'Nombre, efecto, habilidad o tipo';
  const busquedaPista = elementoHtml(
    'p',
    'inventario-busqueda__pista',
    'Desde 4 letras: busca en el nombre, la descripción, los efectos y las habilidades de tus héroes y objetos.',
  );
  busquedaPista.id = 'inventario-busqueda-pista';
  busquedaControl.setAttribute('aria-describedby', busquedaPista.id);
  busquedaCampo.append(busquedaEtiqueta, busquedaControl);
  const botonBuscar = elementoHtml('button', 'inventario-busqueda__buscar', 'Buscar');
  botonBuscar.type = 'submit';
  const botonLimpiar = elementoHtml('button', 'inventario-busqueda__limpiar', 'Limpiar');
  botonLimpiar.type = 'button';
  botonLimpiar.hidden = true;
  busqueda.append(busquedaCampo, botonBuscar, botonLimpiar, busquedaPista);
  cabecera.append(botonNuevo);

  const editor = elementoHtml('section', 'inventario-editor');
  editor.hidden = true;
  const tituloEditor = elementoHtml('h2', 'inventario-editor__titulo', 'Nuevo elemento');
  const formulario = elementoHtml('form', 'inventario-editor__formulario');
  const producto = campoFormulario('Producto', 'productoId');
  const tipo = campoFormulario('Tipo', 'tipo', 'select');
  for (const [valor, etiqueta] of TIPOS) {
    const opcion = document.createElement('option');
    opcion.value = valor;
    opcion.textContent = etiqueta;
    tipo.control.appendChild(opcion);
  }
  const nombre = campoFormulario('Nombre', 'nombrePropio');
  const parte = campoFormulario('Parte de armadura', 'parteArmadura', 'select');
  for (const [valor, etiqueta] of PARTES_ARMADURA) {
    const opcion = document.createElement('option');
    opcion.value = valor;
    opcion.textContent = etiqueta;
    parte.control.appendChild(opcion);
  }
  parte.etiqueta.hidden = true;
  const acciones = elementoHtml('div', 'inventario-editor__acciones');
  const botonGuardar = elementoHtml('button', 'inventario-editor__guardar', 'Guardar');
  botonGuardar.type = 'submit';
  const botonCancelar = elementoHtml('button', 'inventario-editor__cancelar', 'Cancelar');
  botonCancelar.type = 'button';
  acciones.append(botonGuardar, botonCancelar);
  formulario.append(producto.etiqueta, tipo.etiqueta, nombre.etiqueta, parte.etiqueta, acciones);
  editor.append(tituloEditor, formulario);

  const equipo = elementoHtml('section', 'inventario-equipo');
  equipo.hidden = true;
  const equipoCabecera = elementoHtml('header', 'inventario-equipo__cabecera');
  const equipoTitulo = elementoHtml('h2', 'inventario-equipo__titulo', 'Equipamiento');
  const equipoCerrar = elementoHtml('button', 'inventario-equipo__cerrar', 'Cerrar');
  equipoCerrar.type = 'button';
  equipoCabecera.append(equipoTitulo, equipoCerrar);
  // UX-GAME-3 — el heroe manda en el panel: su retrato, su nombre y sus
  // estadisticas CON lo que lleva puesto (`/inventario/heroes/{id}/estadisticas`,
  // las mismas que la ficha). Se repintan al equipar o desequipar, que es
  // cuando cambian. Si el servicio no responde, el bloque no se pinta: no se
  // inventa un cero.
  const equipoHeroe = elementoHtml('div', 'inventario-equipo__heroe');
  const equipoResumen = elementoHtml('p', 'inventario-equipo__resumen');
  // UX-R2.5 — era un <ul> de filas; ahora contiene los tres grupos de
  // ranuras (`<section>`), y una lista no puede tener secciones dentro.
  const equipoLista = elementoHtml('div', 'inventario-equipo__lista');
  equipo.append(equipoCabecera, equipoHeroe, equipoResumen, equipoLista);

  const mensaje = elementoHtml('p', 'inventario__mensaje');
  mensaje.id = 'nexus-rbac-forbidden';
  mensaje.hidden = true;
  mensaje.setAttribute('role', 'status');
  mensaje.setAttribute('aria-live', 'polite');
  const contenido = elementoHtml('div', 'inventario__contenido');

  // HU-INV-011: el control se inserta una vez y se repinta con cada pagina.
  // Se monta despues de la cuadricula porque es su pie de navegacion.
  const paginacion = elementoHtml('div', 'inventario__paginacion');

  // UXC-1 (feedback del profesor): tres pestanas en vez de una lista mezclada.
  const zonaPestanas = elementoHtml('div', 'inventario__pestanas');
  const panelHeroes = elementoHtml('section', 'inventario__panel inventario__panel--heroes');
  // UXC-9 — §7.8.10 nombra «en torneo» entre los estados del héroe, pero los
  // torneos inscriben jugadores, no héroes (torneos.yaml): ningún servicio
  // bloquea un héroe por un torneo, así que ese sello no se pinta. Se dice
  // aquí, para que su ausencia no parezca un olvido.
  const introHeroes = elementoHtml(
    'p',
    'inventario__intro',
    'Tus héroes, con las cifras que les da lo que llevan puesto. Uno sin equipo no puede entrar a una partida y uno en misión no juega hasta que vuelva. Los torneos inscriben jugadores, no héroes: en un encuentro de torneo juegas con tu héroe equipado, como en cualquier batalla.',
  );
  const heroes = elementoHtml('div', 'inventario-heroes');
  // PLAYER-07a (punto 25) — POSESIÓN y CATÁLOGO, separados: «Mis héroes» son
  // los tuyos; «Héroes del Nexo», los que se pueden conseguir (catálogo de
  // productos), cada uno con si ya es tuyo o dónde conseguirlo. No se regala
  // ninguno: la propiedad la decide el inventario.
  const tituloMisHeroes = elementoHtml('h2', 'inventario__seccion-titulo', 'Mis héroes');
  tituloMisHeroes.id = 'inventario-mis-heroes';
  const misHeroes = elementoHtml('section', 'inventario__seccion inventario__seccion--mis-heroes');
  misHeroes.setAttribute('aria-labelledby', tituloMisHeroes.id);
  // Revisión final (13-oct): «Mis héroes» también pagina de dieciséis en
  // dieciséis, con el mismo control de §7.1, y cada página la da el servidor.
  const paginacionHeroes = elementoHtml(
    'div',
    'inventario__paginacion inventario__paginacion--heroes',
  );
  misHeroes.append(tituloMisHeroes, introHeroes, heroes, paginacionHeroes);
  const tituloNexo = elementoHtml('h2', 'inventario__seccion-titulo', 'Héroes del Nexo');
  tituloNexo.id = 'inventario-heroes-del-nexo';
  const introNexo = elementoHtml(
    'p',
    'inventario__intro',
    'Los héroes que se pueden conseguir en el Nexo. Los que ya tienes salen marcados; los demás se consiguen en la tienda.',
  );
  const heroesDelNexo = elementoHtml('div', 'heroes-del-nexo');
  heroesDelNexo.dataset.zona = 'heroes-del-nexo';
  const seccionNexo = elementoHtml('section', 'inventario__seccion inventario__seccion--nexo');
  seccionNexo.setAttribute('aria-labelledby', tituloNexo.id);
  seccionNexo.append(tituloNexo, introNexo, heroesDelNexo);
  panelHeroes.append(misHeroes, seccionNexo);

  const panelObjetos = elementoHtml('section', 'inventario__panel inventario__panel--objetos');
  const introObjetos = elementoHtml('p', 'inventario__intro', TEXTO_DE_OBJETOS);
  panelObjetos.append(introObjetos, contenido, paginacion);

  const panelEquipo = elementoHtml('section', 'inventario__panel inventario__panel--equipamiento');
  const selectorHeroe = elementoHtml('div', 'inventario-equipo__selector');
  selectorHeroe.setAttribute('role', 'group');
  selectorHeroe.setAttribute('aria-label', 'Elige el héroe que quieres equipar');
  const sinHeroeElegido = elementoHtml('div', 'inventario-equipo__sin-heroe');
  panelEquipo.append(selectorHeroe, sinHeroeElegido, equipo);

  // UXC-5 (RF-INV-003) — el banner de misiones disponibles. Nace oculto: si el
  // módulo de misiones no responde, el requisito pide ocultarlo «sin afectar
  // el resto de la vista».
  //
  // PLAYER-07a (punto 25) — «aparece el mismo comienzo del módulo de
  // misiones y eso no debería estar ahí». §7.1 y RF-INV-003 SÍ piden un
  // banner de misiones en el inventario, así que no se quita: deja de ser lo
  // primero de la página —donde parecía la cabecera de Misiones— y pasa al
  // final, con su propio título y en formato compacto (inventario-jugador.css).
  const bannerMisiones = elementoHtml('div', 'inventario__banner-misiones');
  bannerMisiones.dataset.zona = 'banner-misiones';
  bannerMisiones.hidden = true;
  const tituloMisiones = elementoHtml(
    'h2',
    'inventario__seccion-titulo',
    'Misiones para tus héroes',
  );
  tituloMisiones.id = 'inventario-misiones';
  const seccionMisiones = elementoHtml('section', 'inventario__seccion inventario__misiones');
  seccionMisiones.setAttribute('aria-labelledby', tituloMisiones.id);
  seccionMisiones.hidden = true;
  seccionMisiones.append(tituloMisiones, bannerMisiones);

  return {
    elementos: [
      cabecera,
      busqueda,
      editor,
      mensaje,
      zonaPestanas,
      panelHeroes,
      panelObjetos,
      panelEquipo,
      seccionMisiones,
    ],
    bannerMisiones,
    seccionMisiones,
    zonaPestanas,
    panelHeroes,
    heroes,
    paginacionHeroes,
    heroesDelNexo,
    introObjetos,
    panelObjetos,
    panelEquipo,
    selectorHeroe,
    sinHeroeElegido,
    cabecera,
    botonNuevo,
    busqueda,
    busquedaControl,
    botonBuscar,
    botonLimpiar,
    editor,
    tituloEditor,
    formulario,
    producto,
    tipo,
    nombre,
    parte,
    botonGuardar,
    botonCancelar,
    equipo,
    equipoTitulo,
    equipoCerrar,
    equipoResumen,
    equipoLista,
    equipoHeroe,
    mensaje,
    contenido,
    paginacion,
  };
}

/**
 * Monta la gestion de HU-INV-003 y mantiene la vitrina de HU-INV-001 como
 * fuente visible del estado que quedo persistido.
 */
export async function montarInventario(
  raiz,
  identidad,
  numeroPagina = 0,
  {
    consultar = consultarPagina,
    buscar = buscarElementos,
    crear = crearElemento,
    modificar = modificarElemento,
    consultarEquipo = consultarEquipamiento,
    consultarEstadisticas = consultarEstadisticasDelHeroe,
    equipar = equiparElemento,
    desequipar = desequiparElemento,
    eliminar = eliminarElemento,
    // El dialogo del kit, no el del navegador: UXC-7 los prohibe —no se
    // estilan, bloquean la pagina, no devuelven el foco y se leen mal con
    // lector de pantalla—, y hay una prueba guardiana que lo vigila.
    // Inyectable para que las pruebas decidan sin abrir nada.
    confirmar = confirmarEnDialogo,
    consultarProducto = consultarProductoDelCatalogo,
    listarHeroesDelNexo = () => listarProductos({ tipo: 'HEROE' }),
    fuenteMisiones = fuenteDeMisiones(),
    pestanaInicial = 'heroes',
    maxPaginas,
    rol = leerSesion().rol,
    // Revisión final (13-oct): `inventario.html?busqueda=…` abre la vista con
    // esa búsqueda hecha (enlace directo).
    busquedaInicial = '',
  } = {},
) {
  const vista = construirGestion();
  raiz.replaceChildren(...vista.elementos);
  const daDeAlta = ROLES_QUE_DAN_DE_ALTA.includes(rol);
  vista.botonNuevo.hidden = !daDeAlta;

  // RF-INV-003: sin módulo de misiones se queda oculto y no hace ninguna
  // peticion; con el, las destacadas. Sin misiones que presentar no hay
  // banner (HU-INV-012, criterio 3): el §7.1 pide «un banner de presentacion
  // para las misiones disponibles» y nada mas. Su seccion
  // (con su título) se enseña solo si el banner se enseña.
  montarBannerDeMisiones(vista.bannerMisiones, {
    fuente: fuenteMisiones,
    hrefDe: (mision) => `../misiones/misiones.html?mision=${encodeURIComponent(mision.id)}`,
    hrefTablon: '../misiones/misiones.html',
  })
    .catch(() => 'oculto')
    .then(() => {
      vista.seccionMisiones.hidden = vista.bannerMisiones.hidden;
    });

  /**
   * PLAYER-07a — el catálogo de héroes se pide una vez por visita: al volver a
   * reunir el inventario (crear, renombrar, equipar) solo cambia cuáles son
   * tuyos. Si falla, se olvida, para que «Reintentar» vuelva a pedirlo.
   */
  let catalogoDeHeroes = null;
  const leerCatalogoDeHeroes = () => {
    catalogoDeHeroes ??= listarHeroesDelNexo().catch((fallo) => {
      catalogoDeHeroes = null;
      throw fallo;
    });
    return catalogoDeHeroes;
  };

  /**
   * Revisión final del módulo (13-oct) — §7.1: «distribución responsive para
   * dieciséis productos por página … navegables a través de un control de
   * paginación». Desde inventario.yaml 1.7.0 el servidor pagina cada pestaña
   * por separado (`categoria=HEROES|OBJETOS`): cada vitrina pide SOLO la página
   * que enseña. Antes (UXC-1) se reunía el inventario entero y se cortaba aquí.
   *
   * Si el servidor ignora la categoría —uno anterior a 1.7.0 durante un
   * despliegue, o un doble de prueba que no la distingue— se nota en la primera
   * respuesta (trae elementos de la otra pestaña) y se vuelve al camino de
   * antes: reunir la colección y paginarla aquí. `porCategoria` lo recuerda.
   */
  let porCategoria = null;
  /** Solo en el camino de compatibilidad: la colección reunida entera. */
  let coleccion = null;
  /** Todos los héroes del jugador (selector de equipo, «Ya es tuyo» del Nexo). */
  let heroes = [];
  /** Todos los objetos, solo cuando el equipamiento los necesita como candidatos. */
  let objetos = [];
  let objetosCompletos = false;
  let promesaHeroes = null;
  let promesaObjetos = null;
  /** La página de héroes que enseña «Mis héroes». */
  let paginaHeroesMostrada = null;
  let turnoHeroes = 0;
  /** heroeId → EquipamientoHeroe (o undefined si no se pudo leer). */
  let equipos = new Map();
  /** elementoId → nombre del heroe que lo lleva (respaldo de `equipadoEn`). */
  let equipados = new Map();
  /** heroeId → prototipo del catalogo (para su simbolo). */
  let prototipos = new Map();

  const pestanas = montarPestanas(
    vista.zonaPestanas,
    [
      { id: 'heroes', etiqueta: 'Héroes', panel: vista.panelHeroes },
      { id: 'objetos', etiqueta: 'Objetos', panel: vista.panelObjetos },
      { id: 'equipamiento', etiqueta: 'Equipamiento', panel: vista.panelEquipo },
    ],
    { activa: pestanaInicial },
  );
  // `montarPestanas` deja la lista donde se le pide; el contenedor es suyo.
  vista.zonaPestanas.classList.add('inventario__pestanas--montadas');
  vista.zonaPestanas
    .querySelector('[role="tablist"]')
    ?.setAttribute('aria-label', 'Secciones de Mi inventario');

  /** Quién lleva un objeto: el servidor (1.7.0) o, de respaldo, el equipo leído. */
  function portadorDe(elemento) {
    if (elemento?.equipadoEn?.heroeId) {
      return elemento.equipadoEn;
    }
    for (const [heroeId, equipo] of equipos) {
      if (equipo && idsDeEquipo(equipo).has(elemento?.id)) {
        const heroe =
          heroes.find((h) => h.id === heroeId) ??
          paginaHeroesMostrada?.elementos?.find((h) => h.id === heroeId);
        return { heroeId, nombreHeroe: heroe?.nombrePropio ?? equipados.get(elemento.id) ?? null };
      }
    }
    return null;
  }

  /** Sello de estado de cada tarjeta de la vitrina de objetos. */
  function estadoDeTarjeta(elemento) {
    const estado = esHeroe(elemento)
      ? estadoDeHeroe({ elemento, equipamiento: equipos.get(elemento.id) })
      : estadoDeObjeto({
          elemento,
          equipadoEn: portadorDe(elemento)?.nombreHeroe ?? equipados.get(elemento.id) ?? null,
        });
    return selloDeEstado(estado.estado, { detalle: estado.detalle });
  }

  /**
   * Camino de compatibilidad: el inventario entero, una vez por recarga. La
   * `semilla` es la página 0 que ya trajo la consulta que delató al servidor
   * (ignoró la categoría, así que es la página 0 del inventario entero): no se
   * vuelve a pedir.
   */
  let promesaColeccion = null;
  function reunirColeccion(semilla = null) {
    const consultarConSemilla = (jugador, pagina) =>
      pagina === 0 && semilla ? Promise.resolve(semilla) : consultar(jugador, pagina);
    promesaColeccion ??= reunirInventario(
      consultarConSemilla,
      identidad,
      maxPaginas ? { maxPaginas } : undefined,
    ).then(
      (reunida) => {
        coleccion = reunida;
        return reunida;
      },
      (fallo) => {
        promesaColeccion = null;
        throw fallo;
      },
    );
    return promesaColeccion;
  }

  /** Recuerda si el servidor pagina por categoría (ver `porCategoria`). */
  function recordarSiFiltra(filtra) {
    porCategoria = filtra;
  }

  /**
   * Una página de una pestaña. Con un servidor 1.7.0, su página; sin él, la
   * colección reunida cortada aquí (mismo formato `PaginaInventario`).
   */
  async function paginaDeCategoria(categoria, numero) {
    const quiereHeroes = categoria === CATEGORIAS.HEROES;
    let semilla = null;
    if (porCategoria !== false) {
      const respuesta = await consultar(identidad, numero, { categoria });
      const lote = Array.isArray(respuesta?.elementos) ? respuesta.elementos : [];
      if (!lote.some((elemento) => esHeroe(elemento) !== quiereHeroes)) {
        if (lote.length > 0) {
          recordarSiFiltra(true);
        }
        return respuesta;
      }
      recordarSiFiltra(false);
      semilla = numero === 0 ? respuesta : null;
    }
    const reunida = await reunirColeccion(semilla);
    // Tan grande que se alcanzó el tope: «Objetos» vuelve a la página del
    // servidor, como antes de 1.7.0 (trae también héroes, pero cuenta bien las
    // páginas y no deja nada fuera; el aviso de «Mis héroes» lo dice).
    if (!reunida.completo && !quiereHeroes) {
      return semilla ?? consultar(identidad, numero);
    }
    return paginaLocal(
      reunida.elementos.filter((elemento) => esHeroe(elemento) === quiereHeroes),
      numero,
    );
  }

  /** Todos los de una pestaña (para el selector o los candidatos), página a página. */
  async function todosDeCategoria(categoria) {
    if (porCategoria === false) {
      const reunida = await reunirColeccion();
      const quiereHeroes = categoria === CATEGORIAS.HEROES;
      return {
        elementos: reunida.elementos.filter((elemento) => esHeroe(elemento) === quiereHeroes),
        completo: reunida.completo,
      };
    }
    return reunirInventario(
      (_identidad, pagina) => paginaDeCategoria(categoria, pagina),
      identidad,
      maxPaginas ? { maxPaginas } : undefined,
    );
  }

  /** Todos los héroes: si la página que se ve ya es la única, sin pedir nada más. */
  function asegurarHeroes() {
    promesaHeroes ??= (async () => {
      const pagina = paginaHeroesMostrada;
      const unaSola = pagina && pagina.numero === 0 && (pagina.ultima || pagina.totalPaginas <= 1);
      return unaSola && porCategoria !== false
        ? { elementos: pagina.elementos, completo: true }
        : todosDeCategoria(CATEGORIAS.HEROES);
    })().then(
      (reunidos) => {
        heroes = reunidos.elementos;
        return heroes;
      },
      (fallo) => {
        promesaHeroes = null;
        throw fallo;
      },
    );
    return promesaHeroes;
  }

  /** Todos los objetos, cuando el equipamiento necesita sus candidatos. */
  function asegurarObjetos() {
    promesaObjetos ??= todosDeCategoria(CATEGORIAS.OBJETOS).then(
      (reunidos) => {
        objetos = reunidos.elementos;
        objetosCompletos = reunidos.completo;
        return objetos;
      },
      (fallo) => {
        promesaObjetos = null;
        throw fallo;
      },
    );
    return promesaObjetos;
  }

  function alVerFichaDeHeroe(heroe, { origen } = {}) {
    return abrirFicha(heroe.productoId, {
      origen: origen ?? document.activeElement,
      complementos: COMPLEMENTOS_DE_LA_FICHA,
      elementoId: heroe.id,
      identidad,
      nombrePropio: heroe.nombrePropio,
      nivel: Number.isInteger(heroe.nivel) ? heroe.nivel : null,
      enTuInventario: {
        nombrePropio: heroe.nombrePropio,
        sello: estadoDeTarjeta(heroe),
      },
    });
  }

  /** «Héroes del Nexo» con lo que ya es tuyo marcado. No se espera. */
  function pintarNexo() {
    pintarHeroesDelNexo(vista.heroesDelNexo, {
      listar: leerCatalogoDeHeroes,
      propios: new Set(heroes.map((heroe) => heroe.productoId).filter(Boolean)),
    }).catch((fallo) => console.warn('No se pudieron pintar los héroes del Nexo', fallo));
  }

  /**
   * «Mis héroes»: una página de dieciséis del servidor, sus cartas y su control.
   * Después, sin bloquear la carta, lo que necesita a todos los héroes: el
   * selector de equipo y las marcas del Nexo.
   */
  async function actualizarHeroes(numero = 0) {
    const miTurno = ++turnoHeroes;
    vista.heroes.replaceChildren(construirCarga('Cargando tus héroes...'));
    let pagina;
    try {
      pagina = await paginaDeCategoria(CATEGORIAS.HEROES, numero);
    } catch (fallo) {
      console.error('No se pudieron leer tus héroes', fallo);
      if (miTurno === turnoHeroes) {
        vista.heroes.replaceChildren(
          construirError(undefined, undefined, {
            texto: 'Reintentar',
            alPulsar: () => recargarTodo(),
          }),
        );
        vista.paginacionHeroes.replaceChildren();
      }
      return false;
    }
    if (miTurno !== turnoHeroes) {
      return true;
    }
    // Pedida una página que ya no existe (se retiró el último de la última):
    // se enseña la última que sí.
    if (pagina.elementos.length === 0 && pagina.totalPaginas > 0 && numero >= pagina.totalPaginas) {
      return actualizarHeroes(pagina.totalPaginas - 1);
    }
    paginaHeroesMostrada = pagina;
    const leido = await pintarHeroes(vista.heroes, {
      heroes: pagina.elementos,
      identidad,
      consultarEquipo,
      consultarEstadisticas,
      consultarProducto,
      alVerFicha: (heroe, contexto) => alVerFichaDeHeroe(heroe, contexto),
      alEquipar: (heroe) => abrirEquipamiento(heroe),
      consultarNiveles: () => consultarTablaDeNiveles(),
    });
    if (miTurno !== turnoHeroes) {
      return true;
    }
    equipos = leido.equipos;
    prototipos = leido.prototipos;
    equipados = mapaDeEquipados(pagina.elementos, equipos);
    if (porCategoria === false && coleccion && !coleccion.completo) {
      vista.heroes.prepend(
        elementoHtml(
          'p',
          'inventario__aviso',
          'Tu inventario es muy grande: aquí están los héroes de sus primeros 400 elementos. ' +
            'La pestaña «Objetos» lo recorre entero, página a página, y ahí también aparecen tus héroes.',
        ),
      );
    }
    pintarPaginacionHeroes();
    try {
      await asegurarHeroes();
    } catch (fallo) {
      console.warn('No se pudo reunir la lista de tus héroes', fallo);
    }
    pintarNexo();
    pintarSelectorDeHeroe();
    return true;
  }

  function pintarPaginacionHeroes() {
    const pagina = paginaHeroesMostrada;
    const veniaEnfocado = vista.paginacionHeroes.contains(document.activeElement);
    const direccion = document.activeElement?.dataset?.direccion ?? null;
    try {
      vista.paginacionHeroes.replaceChildren(
        construirPaginacion(
          { paginaActual: pagina?.numero ?? 0, totalPaginas: pagina?.totalPaginas ?? 0 },
          (pedida) => actualizarHeroes(pedida),
          { etiqueta: 'Páginas de tus héroes', ...PAGINACION_DE_7_1 },
        ),
      );
      if (veniaEnfocado) {
        enfocarControl(vista.paginacionHeroes, direccion);
      }
    } catch (fallo) {
      console.error('Paginacion incoherente en los héroes del inventario', fallo);
      vista.paginacionHeroes.replaceChildren();
    }
  }

  /** Vuelve a pedir todo después de una escritura (crear, renombrar, retirar). */
  async function recargarTodo() {
    coleccion = null;
    promesaColeccion = null;
    promesaHeroes = null;
    promesaObjetos = null;
    objetos = [];
    objetosCompletos = false;
    await actualizarHeroes(paginaHeroesMostrada?.numero ?? 0);
    await actualizar(paginaEnCurso());
  }

  /** Los heroes como botones para la pestana de equipamiento. */
  function pintarSelectorDeHeroe() {
    vista.selectorHeroe.replaceChildren();
    if (heroes.length === 0) {
      vista.sinHeroeElegido.replaceChildren(
        construirVacio(
          'Todavía no tienes héroes que equipar.',
          'Cuando tengas uno, aquí eliges qué arma, armadura e ítems lleva.',
        ),
      );
      vista.sinHeroeElegido.hidden = false;
      return;
    }
    for (const heroe of heroes) {
      const boton = elementoHtml('button', 'inventario-equipo__opcion', heroe.nombrePropio);
      boton.type = 'button';
      boton.dataset.heroe = heroe.id;
      boton.setAttribute('aria-pressed', String(heroeSeleccionado?.id === heroe.id));
      boton.disabled = heroe.disponible === false;
      if (boton.disabled) {
        // §7.8.10: en misión tampoco se cambia el equipo (inventario.yaml 1.6.0).
        boton.title = heroe.ejecucionMisionId
          ? 'En una misión: no se puede cambiar su equipo hasta que vuelva.'
          : 'Bloqueado por una subasta: no se puede equipar mientras dure.';
      }
      boton.addEventListener('click', () => abrirEquipamiento(heroe));
      vista.selectorHeroe.append(boton);
    }
    if (!heroeSeleccionado) {
      vista.sinHeroeElegido.replaceChildren(
        construirVacio(
          'Elige un héroe para ver su equipo.',
          'Cada héroe lleva hasta 2 armas, 6 piezas de armadura y 2 ítems.',
          null,
        ),
      );
      vista.sinHeroeElegido.hidden = false;
      vista.equipo.hidden = true;
    }
  }

  /**
   * HU-INV-002: criterio de la busqueda activa, o cadena vacia si no hay.
   * Vive en la vista y no en el control de paginacion, asi que cambiar de
   * pagina no lo toca: eso es lo que cumple el criterio 3 de HU-INV-011.
   */
  let criterioBusqueda = '';
  /**
   * Unica fuente de la pagina en curso: la que el servicio devolvio y el
   * jugador esta viendo. No se lleva un contador aparte, porque dos
   * variables que dicen lo mismo acaban discrepando en cuanto una consulta
   * llega tarde o falla.
   */
  let paginaMostrada = null;
  /** Turno de la ultima consulta pedida, para descartar respuestas tardias. */
  let ultimoTurno = 0;
  let elementoSeleccionado = null;
  let heroeSeleccionado = null;
  let equipoActual = null;

  function mostrarMensaje(texto, esError = false) {
    vista.mensaje.textContent = texto;
    vista.mensaje.hidden = texto === '';
    vista.mensaje.setAttribute('role', esError ? 'alert' : 'status');
  }

  function cerrarEditor() {
    vista.editor.hidden = true;
    vista.formulario.reset();
    elementoSeleccionado = null;
  }

  function abrirCreacion() {
    if (!daDeAlta) {
      return;
    }
    vista.formulario.reset();
    elementoSeleccionado = null;
    vista.tituloEditor.textContent = 'Nuevo elemento';
    vista.producto.etiqueta.hidden = false;
    vista.tipo.etiqueta.hidden = false;
    vista.producto.control.disabled = false;
    vista.tipo.control.disabled = false;
    vista.parte.etiqueta.hidden = true;
    vista.editor.hidden = false;
    mostrarMensaje('');
    vista.producto.control.focus();
  }

  /**
   * Por que no se pudo eliminar, en el idioma del jugador.
   *
   * No se reutiliza `motivoDelRechazo`: ese traduce los codigos del
   * equipamiento y habla de ranuras, que aqui no significan nada. El servicio
   * manda el motivo real en el problem detail —si el elemento esta en una
   * mision, publicado en una subasta o puesto a un heroe— y ese texto ya viene
   * filtrado para que lo lea quien juega, asi que se prefiere. Los codigos
   * quedan de respaldo, nunca a la vista.
   */
  function motivoDeNoPoderEliminar(fallo) {
    if (typeof fallo?.detalle === 'string' && fallo.detalle.trim() !== '') {
      return fallo.detalle;
    }
    switch (fallo?.status) {
      case 403:
        return 'Ese elemento no es tuyo.';
      case 404:
        return 'Ese elemento ya no está en tu inventario.';
      case 409:
        return 'No se puede eliminar: está en una misión, publicado en una subasta o puesto a un héroe.';
      case 503:
        return 'El inventario no responde ahora mismo. Vuelve a intentarlo en un momento.';
      default:
        return 'No pudimos eliminar el elemento. Inténtalo de nuevo.';
    }
  }

  /**
   * HU-INV-008 — retira un elemento del inventario.
   *
   * Se confirma antes porque no se puede deshacer. Quien decide si se puede es
   * el servicio: rechaza el que esta en una mision, el publicado en una
   * subasta, el que el heroe lleva puesto y el que no es de quien pide. Por
   * eso el fallo se traduce con `motivoDeNoPoderEliminar`, que da la razon en
   * el idioma del jugador: el criterio pide explicar el bloqueo, y el cliente
   * tiene dicho que un codigo HTTP no se le ensena a nadie.
   *
   * `confirmar` es el dialogo del kit (`comun/ui/dialogo.js`): recibe
   * `{titulo, mensaje, textoConfirmar}` y devuelve una **promesa**. Hay que
   * esperarla: sin `await`, `!promesa` es siempre falso y el elemento se
   * borraba antes de que el jugador contestara (revision de integracion del
   * 13-oct).
   */
  async function eliminarDelInventario(elemento) {
    const seguro = await confirmar({
      titulo: `¿Eliminar «${elemento.nombrePropio}»?`,
      mensaje: 'Se retira de tu inventario y no se puede deshacer.',
      textoConfirmar: 'Eliminar',
    });
    if (!seguro) {
      return;
    }
    mostrarMensaje('Eliminando...');
    try {
      await eliminar(identidad, elemento.id);
      await recargarTodo();
      mostrarMensaje('Elemento eliminado.');
    } catch (fallo) {
      mostrarMensaje(motivoDeNoPoderEliminar(fallo), true);
    }
  }

  function abrirEdicion(elemento) {
    elementoSeleccionado = elemento;
    vista.tituloEditor.textContent = 'Editar elemento';
    vista.producto.etiqueta.hidden = true;
    vista.tipo.etiqueta.hidden = true;
    vista.producto.control.disabled = true;
    vista.tipo.control.disabled = true;
    vista.parte.etiqueta.hidden = true;
    vista.nombre.control.value = elemento.nombrePropio;
    vista.editor.hidden = false;
    mostrarMensaje('');
    vista.nombre.control.focus();
  }

  /**
   * UX-R2.5 — el panel deja de ser una lista de botones y pasa a ser las diez
   * ranuras del contrato: 2 armas, 6 armaduras (una por `ParteArmadura`) y
   * 2 items. La forma de la pantalla dice los limites que antes habia que
   * leer en un contador.
   *
   * La logica de equipar/desequipar no cambia: sigue siendo el mismo PUT y el
   * mismo DELETE de `inventario.yaml`, y sigue siendo el servidor quien
   * decide. Lo que cambia es que ahora se ve donde va cada cosa, y que los
   * rechazos se traducen uno a uno en vez de caer todos en la misma frase.
   */
  function pintarEquipo() {
    pintarEquipamiento(vista.equipoLista, {
      equipo: equipoActual,
      // Revisión final (13-oct): los candidatos son TODOS los objetos del
      // jugador, reunidos al abrir el equipamiento (no la página de la
      // vitrina), así que cada ranura ocupada encuentra su objeto.
      elementos: objetos,
      // FI-R7 — si el inventario es tan grande que se alcanzó el tope, el
      // dialogo recorre el servidor en tandas desde la primera página.
      pedirPagina: objetosCompletos ? null : pedirPaginaDeCandidatos,
      // Lo que lleva OTRO héroe se ofrece apagado y diciendo quién lo lleva.
      portadorDe,
      alEquipar: (ranura, elemento) => cambiarEquipo(true, elemento),
      alDesequipar: (ranura) => cambiarEquipo(false, ranura.elemento),
      alPintarRetratos: (panel) =>
        pintarRetratos(panel).catch((fallo) =>
          console.warn('No se pudieron traer los retratos del equipo', fallo),
        ),
    });
    // El resumen vive ahora dentro del panel, junto a las ranuras.
    vista.equipoResumen.hidden = true;
  }

  /**
   * FI-R7 — una página del servidor para el selector de una ranura. Con
   * inventario 1.7.0, las de «Objetos»; con uno anterior, las del inventario
   * entero (el selector ya descarta lo que no entra en la ranura, héroes
   * incluidos). Nunca la colección reunida: si se recorre es porque no está
   * entera.
   */
  function pedirPaginaDeCandidatos(pagina) {
    return porCategoria === false
      ? consultar(identidad, pagina)
      : paginaDeCategoria(CATEGORIAS.OBJETOS, pagina);
  }

  /**
   * Equipa o desequipa, y repinta con lo que devuelva el servicio.
   *
   * La respuesta de los dos endpoints es el `EquipamientoHeroe` completo, asi
   * que no hace falta adivinar el estado nuevo: se usa el que manda el
   * servidor, que es el unico que sabe la verdad.
   *
   * @param {boolean} equipando
   * @param {object} elemento
   */
  /**
   * Marca la ranura que acaba de recibir un objeto (UX-R2.10).
   *
   * Se busca por el NOMBRE del objeto, que es lo que `ranura()` escribe en
   * `.ranura__etiqueta` cuando esta ocupada. Es indirecto, si — la
   * alternativa era que `pintarEquipamiento` devolviera un indice de
   * ranuras, y eso acopla el panel a una animacion. Si no se encuentra, no
   * pasa nada: el mensaje de texto ya dijo lo que ocurrio.
   */
  function acusarRanuraDe(elemento) {
    const nombre = elemento?.nombrePropio;
    if (!nombre) {
      return;
    }
    const etiqueta = [...vista.equipoLista.querySelectorAll('.ranura__etiqueta')].find(
      (n) => n.textContent === nombre,
    );
    const caja = etiqueta?.closest('.ranura');
    if (caja) {
      acusar(caja, { tipo: 'equipar' });
    }
  }

  async function cambiarEquipo(equipando, elemento) {
    if (!elemento || !heroeSeleccionado) {
      return;
    }
    mostrarMensaje(equipando ? 'Equipando…' : 'Desequipando…');
    try {
      equipoActual = equipando
        ? await equipar(identidad, heroeSeleccionado.id, elemento.id)
        : await desequipar(identidad, heroeSeleccionado.id, elemento.id);
      pintarEquipo();
      // Las cifras dependen de lo que lleva puesto: se vuelven a pedir.
      pintarHeroeDelEquipo(heroeSeleccionado);
      mostrarMensaje(equipando ? 'Elemento equipado.' : 'Elemento desequipado.');
      // UXC-1 — lo equipado cambia el estado de la carta del heroe y el sello
      // de cada objeto: se guarda lo que devolvio el servidor y se repinta.
      equipos.set(heroeSeleccionado.id, equipoActual);
      equipados = mapaDeEquipados(heroes, equipos);
      // Los candidatos guardan quién los lleva (`equipadoEn`): se pone al día
      // con lo que devolvió el servidor para este héroe.
      const puestos = idsDeEquipo(equipoActual);
      objetos = objetos.map((objeto) => {
        if (puestos.has(objeto.id)) {
          return {
            ...objeto,
            equipadoEn: {
              heroeId: heroeSeleccionado.id,
              nombreHeroe: heroeSeleccionado.nombrePropio,
            },
          };
        }
        if (objeto.equipadoEn?.heroeId === heroeSeleccionado.id) {
          const { equipadoEn: _quitado, ...resto } = objeto;
          return resto;
        }
        return objeto;
      });
      pintarEquipo();

      // UX-R2.10 — la ranura acusa lo que acaba de recibir.
      //
      // Hasta aquí, equipar repintaba el panel entero y el único rastro era
      // una línea de texto debajo. Entre diez ranuras idénticas, **cuál**
      // cambió no se veía. El acuse marca la que acaba de moverse; el
      // mensaje de texto sigue donde estaba, así que con
      // `prefers-reduced-motion` no se pierde nada.
      if (equipando) {
        acusarRanuraDe(elemento);
      }
    } catch (fallo) {
      console.error('No se pudo cambiar el equipamiento', fallo);
      mostrarMensaje(motivoDelRechazo(fallo), true);
    }
  }

  /**
   * UX-GAME-3 — la cabecera del panel: retrato, nombre y estadisticas del
   * heroe con su equipo puesto. Se pinta primero sin cifras (el retrato y el
   * nombre ya se saben) y se completa cuando responden las estadisticas; si
   * no responden, se queda sin cifras y no dice nada falso.
   */
  async function pintarHeroeDelEquipo(heroe) {
    const prototipo = prototipos.get(heroe.id) ?? null;
    const retrato = retratoDeHeroe({ nombre: heroe.nombrePropio, prototipo }, { conNombre: false });
    const nombre = elementoHtml('p', 'inventario-equipo__nombre-heroe', heroe.nombrePropio);
    const identidadHeroe = elementoHtml('div', 'inventario-equipo__identidad');
    identidadHeroe.append(
      nombre,
      elementoHtml('p', 'inventario-equipo__rol-heroe', prototipo ?? 'Héroe'),
    );
    vista.equipoHeroe.replaceChildren(retrato, identidadHeroe);

    let estadisticas;
    try {
      estadisticas = await consultarEstadisticas(identidad, heroe.id);
    } catch (fallo) {
      console.warn('No se pudieron traer las estadísticas del héroe', fallo);
      return;
    }
    if (heroeSeleccionado?.id !== heroe.id) {
      return;
    }
    // UXC-1 — el mismo bloque de cifras que la carta y la ficha (StatBlock).
    const lista = bloqueDeEstadisticas(estadisticas, { compacto: true });
    if (!lista) {
      return;
    }
    lista.classList.add('inventario-equipo__estadisticas');
    vista.equipoHeroe.querySelector('.inventario-equipo__estadisticas')?.remove();
    vista.equipoHeroe.append(lista);
  }

  async function abrirEquipamiento(heroe) {
    heroeSeleccionado = heroe;
    // UXC-1 — el equipo vive en su pestana: se va a ella y se marca el heroe.
    pestanas.mostrar('equipamiento');
    for (const opcion of vista.selectorHeroe.querySelectorAll('[data-heroe]')) {
      opcion.setAttribute('aria-pressed', String(opcion.dataset.heroe === heroe.id));
    }
    vista.sinHeroeElegido.hidden = true;
    vista.equipoTitulo.textContent = `Equipamiento de ${heroe.nombrePropio}`;
    mostrarMensaje('Cargando equipamiento...');
    const panelDelEquipo = vista.equipo;
    try {
      // Los candidatos: todos los objetos del jugador (una vez por recarga).
      await asegurarObjetos();
      equipoActual = await consultarEquipo(identidad, heroe.id);
      panelDelEquipo.hidden = false;
      pintarEquipo();
      pintarHeroeDelEquipo(heroe);
      mostrarMensaje('');
    } catch (fallo) {
      console.error('No se pudo consultar el equipamiento', fallo);
      mostrarMensaje('No pudimos cargar el equipamiento. Inténtalo de nuevo.', true);
    }
  }

  /**
   * HU-INV-011: repinta el control a partir de la pagina que de verdad se
   * esta mostrando, no de la que se pidio. Si la consulta fallo, el jugador
   * sigue viendo la anterior y el control debe decir esa misma.
   */
  function pintarPaginacion() {
    const totalPaginas = paginaMostrada?.totalPaginas ?? 0;
    const numero = paginaMostrada?.numero ?? 0;

    // RNF-ACC-002: repintar el control lo destruye entero, y con el se iria
    // el foco al body. Quien cambio de pagina con el teclado se quedaria sin
    // sitio y tendria que tabular otra vez desde arriba. Si el foco estaba
    // dentro, se devuelve a la casilla de la pagina que ahora se muestra.
    const veniaEnfocado = vista.paginacion.contains(document.activeElement);
    const direccion = document.activeElement?.dataset?.direccion ?? null;

    try {
      vista.paginacion.replaceChildren(
        construirPaginacion(
          { paginaActual: numero, totalPaginas },
          (pedida) => {
            actualizar(pedida);
          },
          { etiqueta: 'Páginas del inventario', ...PAGINACION_DE_7_1 },
        ),
      );
      if (veniaEnfocado) {
        enfocarControl(vista.paginacion, direccion);
      }
    } catch (fallo) {
      // El servicio devolvio una pagina incoherente con su propio total. No
      // se adivina un control: se deja sin paginar y queda constancia.
      console.error('Paginacion incoherente en la respuesta del inventario', fallo);
      vista.paginacion.replaceChildren();
    }
  }

  /**
   * RF-INV-002, excepción: «solicitud de una página inexistente, que debe
   * redirigir a la última página válida». Con el servidor paginando (1.7.0),
   * `?pagina=99` o la página de la que se acaba de retirar lo último llegaban
   * vacías y la vitrina decía «Todavía no tienes objetos». Se pide la última
   * que existe antes de pintar nada.
   */
  async function conUltimaPaginaValida(consulta, jugador, pagina) {
    const respuesta = await consulta(jugador, pagina);
    const total = Number.isInteger(respuesta?.totalPaginas) ? respuesta.totalPaginas : 0;
    const vacia = Array.isArray(respuesta?.elementos) && respuesta.elementos.length === 0;
    if (vacia && total > 0 && pagina >= total) {
      return consulta(jugador, total - 1);
    }
    return respuesta;
  }

  /** La pagina que el jugador esta viendo ahora mismo. */
  function paginaEnCurso() {
    return paginaMostrada?.numero ?? numeroPagina;
  }

  async function actualizar(numero) {
    // Cada consulta lleva su turno. Si el jugador pide otra pagina antes de
    // que llegue esta, la respuesta tardia se descarta entera: ni pinta la
    // vitrina ni mueve el control. Gana siempre lo ultimo que se pidio.
    const miTurno = ++ultimoTurno;
    const sigueVigente = () => miTurno === ultimoTurno;

    // HU-INV-002: con una busqueda activa la vitrina pagina sobre sus
    // resultados. El criterio se lee aqui en cada consulta, asi que el control
    // de paginacion lo conserva sin necesidad de conocerlo.
    const busquedaActiva = criterioBusqueda !== '';

    // Revisión final (13-oct) — sin busqueda, la vitrina de «Objetos» pide al
    // servidor su página de dieciséis (`categoria=OBJETOS`, inventario 1.7.0).
    const consultaSinBusqueda = (_jugador, pagina) => paginaDeCategoria(CATEGORIAS.OBJETOS, pagina);
    const consulta = busquedaActiva
      ? (jugador, pagina) => buscar(jugador, criterioBusqueda, pagina)
      : consultaSinBusqueda;
    const consultada = await montarVitrina(vista.contenido, identidad, numero, {
      consultar: (jugador, pagina) => conUltimaPaginaValida(consulta, jugador, pagina),
      alEditar: abrirEdicion,
      alEquipar: abrirEquipamiento,
      alEliminar: eliminarDelInventario,
      estadoDe: estadoDeTarjeta,
      mensajeCarga: busquedaActiva ? 'Buscando en tu inventario...' : 'Cargando tus objetos...',
      mensajeVacio: busquedaActiva
        ? 'No encontramos productos con ese criterio.'
        : 'Todavía no tienes objetos.',
      detalleVacio: busquedaActiva
        ? 'Prueba con otra palabra: un nombre, un efecto, una habilidad o un tipo.'
        : 'Las armas, armaduras e ítems que consigas aparecen aquí. Se consiguen en la tienda y en las subastas.',
      accionVacio: busquedaActiva
        ? null
        : { texto: 'Ir a la tienda', href: '../../cuentas/tienda.html' },
      sigueVigente,
    });

    if (!sigueVigente()) {
      return consultada;
    }

    // La pagina mostrada solo avanza si la consulta trajo algo: asi el
    // control nunca marca una pagina que el jugador no esta viendo.
    if (consultada) {
      paginaMostrada = consultada;
    }
    pintarPaginacion();
    return consultada;
  }

  vista.botonNuevo.addEventListener('click', abrirCreacion);
  vista.busqueda.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    const criterio = vista.busquedaControl.value.trim();
    if (criterio.length < 4) {
      mostrarMensaje('Ingresa al menos cuatro caracteres para buscar.', true);
      vista.busquedaControl.focus();
      return;
    }

    criterioBusqueda = criterio;
    vista.busquedaControl.value = criterio;
    vista.busqueda.classList.add('inventario-busqueda--activa');
    vista.botonLimpiar.hidden = false;
    // PLAYER-07a — la búsqueda es de todo el inventario; sus resultados se
    // ven en la vitrina de «Objetos», que es la que pagina.
    pestanas.mostrar('objetos');
    vista.introObjetos.textContent = TEXTO_DE_RESULTADOS;
    cambiarDisponibilidad(vista.botonBuscar, false);
    mostrarMensaje(`Buscando "${criterio}"...`);
    try {
      const resultado = await actualizar(0);
      if (resultado) {
        const cantidad = resultado.totalElementos ?? resultado.elementos.length;
        mostrarMensaje(
          `${cantidad} ${cantidad === 1 ? 'resultado' : 'resultados'} para "${criterio}".`,
        );
      } else {
        mostrarMensaje('No pudimos realizar la búsqueda. Inténtalo de nuevo.', true);
      }
    } finally {
      cambiarDisponibilidad(vista.botonBuscar, true);
    }
  });
  vista.botonLimpiar.addEventListener('click', async () => {
    criterioBusqueda = '';
    vista.busquedaControl.value = '';
    vista.busqueda.classList.remove('inventario-busqueda--activa');
    vista.botonLimpiar.hidden = true;
    vista.introObjetos.textContent = TEXTO_DE_OBJETOS;
    mostrarMensaje('');
    await actualizar(0);
    vista.busquedaControl.focus();
  });
  vista.botonCancelar.addEventListener('click', cerrarEditor);
  vista.equipoCerrar.addEventListener('click', () => {
    vista.equipo.hidden = true;
    heroeSeleccionado = null;
    equipoActual = null;
    // UXC-1 — cerrar deja la pestana esperando a que se elija otro heroe, y
    // repinta las cartas: lo equipado pudo cambiar su estado.
    pintarSelectorDeHeroe();
    refrescarHeroes();
  });
  vista.tipo.control.addEventListener('change', () => {
    vista.parte.etiqueta.hidden = vista.tipo.control.value !== 'ARMADURA';
    vista.parte.control.required = vista.tipo.control.value === 'ARMADURA';
  });
  vista.formulario.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    cambiarDisponibilidad(vista.botonGuardar, false);
    mostrarMensaje('Guardando...');

    try {
      if (elementoSeleccionado) {
        await modificar(identidad, elementoSeleccionado.id, {
          nombrePropio: vista.nombre.control.value,
        });
        cerrarEditor();
        await recargarTodo();
        mostrarMensaje('Elemento actualizado.');
      } else {
        const totalAntes = paginaMostrada?.totalElementos ?? 0;
        await crear(identidad, {
          productoId: vista.producto.control.value,
          tipo: vista.tipo.control.value,
          nombrePropio: vista.nombre.control.value,
          parteArmadura:
            vista.tipo.control.value === 'ARMADURA' ? vista.parte.control.value : undefined,
        });
        cerrarEditor();
        coleccion = null;
        promesaColeccion = null;
        promesaHeroes = null;
        promesaObjetos = null;
        await actualizarHeroes(paginaHeroesMostrada?.numero ?? 0);
        await actualizar(
          criterioBusqueda === '' ? Math.floor(totalAntes / PRODUCTOS_POR_PAGINA) : 0,
        );
        mostrarMensaje('Elemento creado.');
      }
    } catch (fallo) {
      console.error('No se pudo guardar el elemento del inventario', fallo);
      // El servidor ya explica el rechazo en espanol (problem detail): p. ej.
      // un producto que no existe en el catalogo o un tipo que no coincide.
      const mensaje =
        fallo?.status === 403
          ? 'No tienes permiso para modificar ese inventario.'
          : (fallo?.detalle ??
            'No pudimos guardar el elemento. Revisa los datos e inténtalo de nuevo.');
      mostrarMensaje(mensaje, true);
    } finally {
      cambiarDisponibilidad(vista.botonGuardar, true);
    }
  });

  /**
   * Repinta las cartas de heroe y la vitrina: el equipo cambio (quién lleva
   * qué), los elementos no.
   */
  async function refrescarHeroes() {
    await actualizarHeroes(paginaHeroesMostrada?.numero ?? 0);
    await actualizar(paginaEnCurso());
  }

  await actualizarHeroes(0);
  await actualizar(paginaEnCurso());

  // Revisión final (13-oct): enlace directo a una búsqueda
  // (`inventario.html?busqueda=…`), con el mismo mínimo de cuatro letras.
  buscarAlEntrar(busquedaInicial);

  function buscarAlEntrar(texto) {
    const inicial = typeof texto === 'string' ? texto.trim() : '';
    if (inicial.length < 4) {
      return;
    }
    vista.busquedaControl.value = inicial;
    vista.busqueda.dispatchEvent(new Event('submit', { cancelable: true }));
  }
}

function cambiarDisponibilidad(boton, disponible) {
  boton.disabled = !disponible;
}
