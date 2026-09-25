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
} from './cliente-inventario.js';
import { retratoDeHeroe } from '../../comun/ui/juego/heroe.js';
import { bloqueDeEstadisticas } from '../../comun/ui/juego/estadisticas.js';
import { construirVitrina, PRODUCTOS_POR_PAGINA } from './vitrina.js';
import { pintarRetratos } from './retratos.js';
import { motivoDelRechazo, pintarEquipamiento } from './equipamiento.js';
import { construirCarga, construirVacio, construirError } from './estados-vista.js';
import { abrirFicha } from './ficha-producto.js';
import { construirPaginacion } from '../../comun/paginacion.js';
import { acusar } from '../../comun/ui/acuse.js';
import { montarPestanas } from '../../comun/ui/pestanas.js';
import { consultarProducto as consultarProductoDelCatalogo } from './cliente-productos.js';
import { estadoDeHeroe, estadoDeObjeto, selloDeEstado } from '../../comun/ui/juego/estado-heroe.js';
import { reunirInventario, esHeroe, paginaLocal, mapaDeEquipados } from './coleccion-inventario.js';
import { pintarHeroes } from './heroes-inventario.js';
import { fuenteDeMisiones } from '../misiones/fuente-misiones.js';
import { montarBannerDeMisiones } from '../misiones/banner-misiones.js';
import { complementoDeOpiniones } from '../../plataforma/comentarios/hilo-comentarios.js';

/**
 * UXC-3 — §7.1: el detalle de un producto lleva su calificación promedio y el
 * hilo de comentarios. Va en todas las fichas que abre el inventario, al
 * final, después de lo que dice el catálogo.
 */
const COMPLEMENTOS_DE_LA_FICHA = [complementoDeOpiniones()];

const TIPOS = [
  ['HEROE', 'Héroe'],
  ['HABILIDAD', 'Habilidad'],
  ['ARMA', 'Arma'],
  ['ARMADURA', 'Armadura'],
  ['ITEM', 'Ítem'],
  ['EPICA', 'Épica'],
];

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
      estadoDe,
      // HU-INV-007: la ficha lee el catalogo por su cuenta; el inventario
      // solo guarda la referencia (RF-ADM-10).
      alAbrirDetalle: (elemento) =>
        abrirFicha(elemento.productoId, {
          origen: document.activeElement,
          complementos: COMPLEMENTOS_DE_LA_FICHA,
          // R5: para un heroe, la ficha completa con lo que el jugador tiene de
          // verdad. Para lo demas sobran y se ignoran.
          elementoId: elemento.id,
          identidad,
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

  const busqueda = elementoHtml('form', 'inventario-busqueda');
  busqueda.setAttribute('role', 'search');
  const busquedaCampo = elementoHtml('label', 'inventario-busqueda__campo');
  const busquedaEtiqueta = elementoHtml(
    'span',
    'inventario-busqueda__etiqueta',
    'Buscar productos',
  );
  const busquedaControl = document.createElement('input');
  busquedaControl.className = 'inventario-busqueda__control';
  busquedaControl.type = 'search';
  busquedaControl.name = 'criterio';
  busquedaControl.minLength = 4;
  busquedaControl.autocomplete = 'off';
  busquedaControl.placeholder = 'Buscar por nombre, tipo o producto';
  busquedaCampo.append(busquedaEtiqueta, busquedaControl);
  const botonBuscar = elementoHtml('button', 'inventario-busqueda__buscar', 'Buscar');
  botonBuscar.type = 'submit';
  const botonLimpiar = elementoHtml('button', 'inventario-busqueda__limpiar', 'Limpiar');
  botonLimpiar.type = 'button';
  botonLimpiar.hidden = true;
  busqueda.append(busquedaCampo, botonBuscar, botonLimpiar);
  // UXC-1 — la busqueda vive en la pestana «Objetos», que es donde pagina.
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
  const introHeroes = elementoHtml(
    'p',
    'inventario__intro',
    'Tus héroes, con las cifras que les da lo que llevan puesto. Uno sin equipo no puede entrar a una partida.',
  );
  const heroes = elementoHtml('div', 'inventario-heroes');
  panelHeroes.append(introHeroes, heroes);

  const panelObjetos = elementoHtml('section', 'inventario__panel inventario__panel--objetos');
  const introObjetos = elementoHtml(
    'p',
    'inventario__intro',
    'Armas, armaduras, ítems, habilidades y épicas. Cada uno dice si está equipado, libre o bloqueado.',
  );
  panelObjetos.append(introObjetos, busqueda, contenido, paginacion);

  const panelEquipo = elementoHtml('section', 'inventario__panel inventario__panel--equipamiento');
  const selectorHeroe = elementoHtml('div', 'inventario-equipo__selector');
  selectorHeroe.setAttribute('role', 'group');
  selectorHeroe.setAttribute('aria-label', 'Elige el héroe que quieres equipar');
  const sinHeroeElegido = elementoHtml('div', 'inventario-equipo__sin-heroe');
  panelEquipo.append(selectorHeroe, sinHeroeElegido, equipo);

  // UXC-5 (RF-INV-003) — el banner de misiones disponibles. Nace oculto: si el
  // módulo de misiones no responde, el requisito pide ocultarlo «sin afectar
  // el resto de la vista», y hoy no hay módulo de misiones.
  const bannerMisiones = elementoHtml('div', 'inventario__banner-misiones');
  bannerMisiones.dataset.zona = 'banner-misiones';
  bannerMisiones.hidden = true;

  return {
    elementos: [
      cabecera,
      bannerMisiones,
      editor,
      mensaje,
      zonaPestanas,
      panelHeroes,
      panelObjetos,
      panelEquipo,
    ],
    bannerMisiones,
    zonaPestanas,
    panelHeroes,
    heroes,
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
    consultarProducto = consultarProductoDelCatalogo,
    fuenteMisiones = fuenteDeMisiones(),
    pestanaInicial = 'heroes',
    maxPaginas,
  } = {},
) {
  const vista = construirGestion();
  raiz.replaceChildren(...vista.elementos);

  // RF-INV-003: sin módulo de misiones se queda oculto y no hace ninguna
  // petición; con él, las destacadas o, si no hay, la estrategia.
  montarBannerDeMisiones(vista.bannerMisiones, {
    fuente: fuenteMisiones,
    hrefDe: (mision) => `../misiones/misiones.html?mision=${encodeURIComponent(mision.id)}`,
    hrefTablon: '../misiones/misiones.html',
    hrefEstrategia: '../misiones/misiones.html#estrategia',
  });

  /**
   * UXC-1 — la coleccion entera, partida en heroes y objetos (ver
   * `coleccion-inventario.js`). `completa` es falso si se alcanzo el tope de
   * paginas: entonces se dice, y el selector de equipo vuelve a recorrer el
   * servidor por tandas.
   */
  let coleccion = { elementos: [], completo: true };
  let heroes = [];
  let objetos = [];
  /** heroeId → EquipamientoHeroe (o undefined si no se pudo leer). */
  let equipos = new Map();
  /** elementoId → nombre del heroe que lo lleva. */
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

  /** Sello de estado de cada tarjeta de la vitrina de objetos. */
  function estadoDeTarjeta(elemento) {
    const estado = esHeroe(elemento)
      ? estadoDeHeroe({ elemento, equipamiento: equipos.get(elemento.id) })
      : estadoDeObjeto({ elemento, equipadoEn: equipados.get(elemento.id) ?? null });
    return selloDeEstado(estado.estado, { detalle: estado.detalle });
  }

  /** Una pagina de objetos, cortada sobre la coleccion ya reunida. */
  async function consultarObjetos(_identidad, pagina) {
    return paginaLocal(objetos, pagina);
  }

  /**
   * Pide el inventario entero y repinta las tres pestanas. Se llama al montar
   * y despues de cada escritura (crear, renombrar, equipar).
   */
  async function cargarColeccion() {
    vista.heroes.replaceChildren(construirCarga('Cargando tus héroes...'));
    try {
      coleccion = await reunirInventario(
        consultar,
        identidad,
        maxPaginas ? { maxPaginas } : undefined,
      );
    } catch (fallo) {
      console.error('No se pudo reunir el inventario', fallo);
      const reintentar = {
        texto: 'Reintentar',
        alPulsar: async () => {
          await cargarColeccion();
          await actualizar(paginaEnCurso());
        },
      };
      vista.heroes.replaceChildren(construirError(undefined, undefined, reintentar));
      coleccion = null;
      return false;
    }
    heroes = coleccion.elementos.filter(esHeroe);
    objetos = coleccion.elementos.filter((elemento) => !esHeroe(elemento));
    const leido = await pintarHeroes(vista.heroes, {
      heroes,
      identidad,
      consultarEquipo,
      consultarEstadisticas,
      consultarProducto,
      alVerFicha: (heroe) =>
        abrirFicha(heroe.productoId, {
          origen: document.activeElement,
          complementos: COMPLEMENTOS_DE_LA_FICHA,
          elementoId: heroe.id,
          identidad,
          nombrePropio: heroe.nombrePropio,
        }),
      alEquipar: (heroe) => abrirEquipamiento(heroe),
    });
    equipos = leido.equipos;
    prototipos = leido.prototipos;
    equipados = mapaDeEquipados(heroes, equipos);
    if (!coleccion.completo) {
      vista.heroes.prepend(
        elementoHtml(
          'p',
          'inventario__aviso',
          'Tu inventario es muy grande: aquí están los héroes de sus primeros 400 elementos. ' +
            'La pestaña «Objetos» lo recorre entero, página a página, y ahí también aparecen tus héroes.',
        ),
      );
    }
    pintarSelectorDeHeroe();
    return true;
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
        boton.title = 'Bloqueado por una subasta: no se puede equipar mientras dure.';
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
      // UXC-1 — con la coleccion reunida, todos los objetos son candidatos y
      // cada ranura ocupada encuentra su objeto aunque este en otra pagina.
      elementos: coleccion?.completo ? objetos : (paginaMostrada?.elementos ?? []),
      // FI-R7 — el selector ya no se queda con los dieciseis elementos de la
      // pagina que se esta viendo. Antes, un objeto de la pagina 3 no se podia
      // equipar: no aparecia entre los candidatos y nada decia por que. Con
      // este puerto el dialogo recorre el inventario en tandas hasta juntar
      // candidatos, y para cuando los tiene.
      pedirPagina: coleccion?.completo ? null : (pagina) => consultar(identidad, pagina),
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

    try {
      vista.paginacion.replaceChildren(
        construirPaginacion({ paginaActual: numero, totalPaginas }, (pedida) => {
          actualizar(pedida);
        }),
      );
      if (veniaEnfocado) {
        vista.paginacion.querySelector('[aria-current="page"]')?.focus();
      }
    } catch (fallo) {
      // El servicio devolvio una pagina incoherente con su propio total. No
      // se adivina un control: se deja sin paginar y queda constancia.
      console.error('Paginacion incoherente en la respuesta del inventario', fallo);
      vista.paginacion.replaceChildren();
    }
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

    // UXC-1 — sin busqueda, la vitrina de «Objetos» pagina la coleccion ya
    // reunida (sin heroes). Si no se pudo reunir, o es tan grande que se
    // alcanzo el tope, se vuelve a la pagina del servidor: incluye tambien a
    // los heroes, pero cuenta bien las paginas y no deja nada fuera.
    const consultaSinBusqueda = coleccion?.completo ? consultarObjetos : consultar;
    const consultada = await montarVitrina(vista.contenido, identidad, numero, {
      consultar: busquedaActiva
        ? (jugador, pagina) => buscar(jugador, criterioBusqueda, pagina)
        : consultaSinBusqueda,
      alEditar: abrirEdicion,
      alEquipar: abrirEquipamiento,
      estadoDe: estadoDeTarjeta,
      mensajeCarga: busquedaActiva ? 'Buscando en tu inventario...' : 'Cargando tus objetos...',
      mensajeVacio: busquedaActiva
        ? 'No encontramos productos con ese criterio.'
        : 'Todavía no tienes objetos.',
      detalleVacio: busquedaActiva
        ? 'Prueba con otro nombre, tipo o identificador de producto.'
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
        await cargarColeccion();
        await actualizar(paginaEnCurso());
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
        await cargarColeccion();
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
   * Repinta las cartas de heroe sin volver a reunir el inventario: el equipo
   * cambio, los elementos no.
   */
  async function refrescarHeroes() {
    if (!coleccion) {
      return;
    }
    const leido = await pintarHeroes(vista.heroes, {
      heroes,
      identidad,
      consultarEquipo,
      consultarEstadisticas,
      consultarProducto,
      alVerFicha: (heroe) =>
        abrirFicha(heroe.productoId, {
          origen: document.activeElement,
          complementos: COMPLEMENTOS_DE_LA_FICHA,
          elementoId: heroe.id,
          identidad,
          nombrePropio: heroe.nombrePropio,
        }),
      alEquipar: (heroe) => abrirEquipamiento(heroe),
    });
    equipos = leido.equipos;
    prototipos = leido.prototipos;
    equipados = mapaDeEquipados(heroes, equipos);
    await actualizar(paginaEnCurso());
  }

  await cargarColeccion();
  await actualizar(paginaEnCurso());
}

function cambiarDisponibilidad(boton, disponible) {
  boton.disabled = !disponible;
}
