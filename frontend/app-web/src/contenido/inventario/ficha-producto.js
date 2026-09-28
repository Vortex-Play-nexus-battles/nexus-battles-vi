/**
 * HU-INV-007 - Ficha de detalle del producto del catalogo.
 *
 * Fuente: *Proyecto Integrador II*, seccion 7.1, p. 34.
 *
 * El criterio pide "habilidades y efectos", pero el contrato de productos no
 * define listas genericas con esos nombres: **cada tipo trae sus propios
 * atributos**. La tabla de abajo los declara tal como aparecen en
 * `contracts/openapi/productos.yaml`, para que la ficha pinte lo que el
 * catalogo realmente devuelve y no un modelo inventado.
 *
 * El inventario del jugador no puede alimentar esta ficha: guarda solo la
 * referencia `productoId`, nunca una copia de los atributos, para que un
 * cambio del administrador se propague a todas las instancias (RF-ADM-10).
 */

import { construirCarga, construirError } from './estados-vista.js';
import { consultarProducto as leerDelCatalogo } from './cliente-productos.js';
import { construirDetalleDeHeroe } from './detalle-heroe.js';
import { icono } from '../../comun/ui/icono.js';
import { ICONO_DEL_TIPO } from './vitrina.js';
import { identidadDePrototipo } from '../../comun/ui/juego/prototipos.js';
import { NOMBRE_DEL_TIPO } from '../../comun/ui/formato.js';

/**
 * Atributos visibles de cada tipo, en el orden en que se muestran.
 * Espejo de los esquemas por tipo del contrato de productos.
 */
export const ATRIBUTOS_POR_TIPO = Object.freeze({
  HEROE: [['prototipo', 'Prototipo']],
  HABILIDAD: [
    ['costoPoder', 'Costo de poder'],
    ['multiplicadorNivel', 'Multiplicador por nivel'],
    ['turnosCarga', 'Turnos de carga'],
  ],
  ARMA: [
    ['poderDeAtaque', 'Poder de ataque'],
    ['tasaDeCaida', 'Tasa de caída'],
  ],
  ARMADURA: [
    ['defensa', 'Defensa'],
    ['parte', 'Parte'],
    ['tasaDeCaida', 'Tasa de caída'],
  ],
  ITEM: [
    ['efecto', 'Efecto'],
    ['tasaDeCaida', 'Tasa de caída'],
  ],
  EPICA: [
    ['turnosRecarga', 'Turnos de recarga'],
    ['efectoGeneral', 'Efecto general'],
    ['efectoPotenciado', 'Efecto potenciado'],
  ],
});

/** Etiqueta legible de cada tipo del catalogo: vive en `comun/ui/formato.js` (UXC-8). */
export { NOMBRE_DEL_TIPO };

let secuencia = 0;

/**
 * Construye la ficha de un producto del catalogo.
 *
 * @param {object} producto tal como lo devuelve el servicio de productos.
 * @returns {HTMLElement} dialogo listo para insertar en el documento.
 */
export function construirFicha(producto, { nombrePropio = null, contexto = 'inventario' } = {}) {
  if (!producto || typeof producto !== 'object') {
    throw new TypeError('La ficha necesita un producto del catálogo');
  }
  // UXC-1 — la ficha de un heroe PROPIO se titula con su nombre («Aquiles») y
  // dice debajo de que prototipo sale; el simbolo del prototipo ocupa el hueco
  // de la imagen si el catalogo no la tiene.
  const esHeroePropio = producto.tipo === 'HEROE' && Boolean(nombrePropio);
  const identidad = producto.tipo === 'HEROE' ? identidadDePrototipo(producto.prototipo) : null;

  const ficha = document.createElement('article');
  ficha.className = 'ficha';
  ficha.setAttribute('role', 'dialog');
  ficha.setAttribute('aria-modal', 'true');
  // UXC-1 — con las cifras y las acciones en cartas la ficha se desplaza, y
  // una region que se desplaza tiene que alcanzarse con el teclado (axe:
  // scrollable-region-focusable).
  ficha.setAttribute('tabindex', '0');

  const idNombre = `ficha-nombre-${(secuencia += 1)}`;
  ficha.setAttribute('aria-labelledby', idNombre);

  let imagen;
  if (producto.imagen) {
    imagen = document.createElement('img');
    imagen.className = 'ficha__imagen';
    imagen.src = producto.imagen;
    // El texto alternativo es el nombre: quien no ve la imagen sigue sabiendo
    // que producto esta mirando (RNF-ACC-002).
    imagen.alt = producto.nombre ?? '';
  } else {
    // UX-GAME-3 — sin imagen en el catalogo no se pinta un `<img src="">`,
    // que el navegador ensena como icono roto con el nombre al lado. Va el
    // icono del tipo sobre la misma superficie que ocuparia la imagen.
    imagen = document.createElement('div');
    imagen.className = 'ficha__imagen ficha__imagen--ausente';
    const simbolo = identidad?.conocido
      ? identidad.icono
      : (ICONO_DEL_TIPO[producto.tipo] ?? 'estrella');
    imagen.append(icono(simbolo, { clase: 'ficha__icono-tipo', etiqueta: null }));
  }

  const nombre = document.createElement('h2');
  nombre.className = 'ficha__nombre';
  nombre.id = idNombre;
  nombre.textContent = esHeroePropio ? nombrePropio : (producto.nombre ?? '');

  const tipo = document.createElement('p');
  tipo.className = 'ficha__tipo';
  const nombreDelTipo = NOMBRE_DEL_TIPO[producto.tipo] ?? producto.tipo ?? '';
  tipo.textContent =
    identidad?.conocido && producto.tipo === 'HEROE'
      ? `${nombreDelTipo} · ${identidad.nombre}${identidad.sanador ? ' · Sanador' : ''}`
      : nombreDelTipo;

  // Texto que escribe el administrador: entra por textContent, nunca por
  // innerHTML.
  const descripcion = document.createElement('p');
  descripcion.className = 'ficha__descripcion';
  descripcion.textContent = producto.descripcion ?? '';

  ficha.append(imagen, nombre, tipo);

  // RN-27 (seccion 7.2.1): un producto suspendido no admite nuevas
  // adquisiciones, pero **permanece en los inventarios de quienes ya lo
  // poseen**. El flujo alternativo de la ficha pide mostrarla con el
  // indicador de no disponible, no ocultarla ni responder que no existe.
  if (producto.estado === 'SUSPENDIDO') {
    ficha.appendChild(construirNoDisponible(contexto));
  }

  ficha.append(descripcion, construirAtributos(producto));

  if (producto.tiraje !== undefined) {
    const tiraje = document.createElement('p');
    tiraje.className = 'ficha__tiraje';
    // -1 es la convencion del contrato para "sin tope"; al jugador se le
    // dice con palabras.
    tiraje.textContent =
      producto.tiraje === -1 ? 'Tiraje ilimitado' : `Tiraje limitado a ${producto.tiraje} unidades`;
    ficha.appendChild(tiraje);
  }

  return ficha;
}

/**
 * Aviso de producto retirado del catalogo.
 *
 * Dice las dos cosas que el jugador necesita saber: que ya no se puede
 * adquirir, y que el suyo no desaparece. Sin la segunda, el aviso se lee
 * como una perdida.
 */
function construirNoDisponible(contexto = 'inventario') {
  const aviso = document.createElement('p');
  aviso.className = 'ficha__no-disponible';
  // Se anuncia a los lectores de pantalla sin interrumpir (RNF-ACC-002).
  aviso.setAttribute('role', 'status');
  // UXC-4 — desde la tienda o la portada no se habla de «tu inventario»: quien
  // mira quizá no lo tiene. Lo que importa ahí es que ahora no se vende.
  aviso.textContent =
    contexto === 'inventario'
      ? 'No disponible para nuevas adquisiciones. Sigue en tu inventario y puedes seguir usándolo.'
      : 'Este producto no está a la venta ahora mismo.';
  return aviso;
}

function construirAtributos(producto) {
  const lista = document.createElement('dl');
  lista.className = 'ficha__atributos';

  // Un tipo que el catalogo agregue despues no rompe la ficha: se muestra
  // lo comun y no se inventan atributos.
  for (const [campo, etiqueta] of ATRIBUTOS_POR_TIPO[producto.tipo] ?? []) {
    const valor = producto[campo];
    if (valor === undefined || valor === null || valor === '') {
      continue;
    }
    lista.appendChild(construirAtributo(etiqueta, valor));
  }
  return lista;
}

function construirAtributo(etiqueta, valor) {
  const fila = document.createElement('div');
  fila.className = 'ficha__atributo';

  const termino = document.createElement('dt');
  termino.className = 'ficha__etiqueta';
  termino.textContent = etiqueta;

  const definicion = document.createElement('dd');
  definicion.className = 'ficha__valor';
  definicion.textContent = String(valor);

  fila.append(termino, definicion);
  return fila;
}

/* --------------------------------------------------------------------------
 * Apertura y cierre.
 * ------------------------------------------------------------------------ */

/** Ficha abierta, si la hay. Solo puede haber una. */
let abierta = null;

/**
 * Abre la ficha de un producto sobre la vista actual.
 *
 * @param {string} productoId referencia guardada en el inventario.
 * @param {object} opciones
 * @param {(id: string) => Promise<object>} opciones.consultarProducto
 *        lectura del catalogo. **Se inyecta**: el servicio de productos aun
 *        no publica `GET /api/v1/productos/{id}`, asi que no hay valor por
 *        omision que pudiera funcionar.
 * @param {HTMLElement} [opciones.origen] elemento al que vuelve el foco.
 * @returns {Promise<void>} resuelve con la ficha en su estado final.
 */
export async function abrirFicha(
  productoId,
  {
    consultarProducto = leerDelCatalogo,
    origen,
    // R5: con estos dos, y solo si el producto es un heroe, la ficha se
    // completa con las estadisticas del heroe del jugador y las acciones de su
    // prototipo. Opcionales a proposito: la ficha de un arma no los necesita, y
    // sin ellos se comporta como antes.
    elementoId = null,
    identidad = null,
    nombrePropio = null,
    detalleDeHeroe = construirDetalleDeHeroe,
    // UXC-3/UXC-4 — lo que cada vista añade al final de la ficha: las
    // opiniones de la comunidad, el bloque de compra de la tienda. Cada uno es
    // `(producto, {ficha}) => Node|null`; uno que falle no tumba la ficha.
    complementos = [],
    // «inventario» (por omision), «tienda» o «portada»: cambia solo las frases
    // que hablan de lo que el jugador tiene.
    contexto = 'inventario',
  } = {},
) {
  cerrarFicha();

  const devolverFocoA = origen ?? document.activeElement;
  const capa = document.createElement('div');
  capa.className = 'ficha-capa';
  capa.appendChild(construirCerrar());
  capa.appendChild(construirCarga('Cargando el producto...'));
  document.body.appendChild(capa);

  const alPulsarTecla = (evento) => {
    if (evento.key === 'Escape') {
      cerrarFicha();
    }
  };
  document.addEventListener('keydown', alPulsarTecla);
  abierta = { capa, devolverFocoA, alPulsarTecla };

  // El foco entra en la ficha para que el teclado no se quede en la vista
  // de atras (RNF-ACC-002).
  capa.querySelector('.ficha__cerrar').focus();

  let producto;
  try {
    producto = await consultarProducto(productoId);
  } catch (fallo) {
    console.error('No se pudo cargar el producto del catálogo', fallo);
    reemplazarContenido(
      capa,
      construirError('No pudimos cargar este producto.', 'Vuelve a intentarlo en un momento.'),
    );
    return;
  }

  if (abierta === null || abierta.capa !== capa) {
    return; // Se cerro mientras se consultaba.
  }
  const ficha = construirFicha(producto, { nombrePropio, contexto });
  reemplazarContenido(capa, ficha);
  const zonaDeComplementos = anadirComplementos(ficha, producto, complementos);

  // Y despues, sin hacer esperar a la ficha: son dos servicios mas y el detalle
  // del producto ya es util sin ellos. Mismo criterio que los retratos de la
  // vitrina. Si fallan, no se pinta nada; no se rellena con ceros.
  if (producto.tipo !== 'HEROE' || !elementoId || !identidad) {
    return;
  }
  let bloques;
  try {
    bloques = await detalleDeHeroe({
      identidad,
      heroeId: elementoId,
      prototipo: producto.prototipo ?? null,
    });
  } catch (fallo) {
    console.error('No se pudo completar el detalle del héroe', fallo);
    return;
  }
  if (abierta === null || abierta.capa !== capa || bloques.length === 0) {
    return; // Se cerro mientras se consultaba, o no llego nada que pintar.
  }
  // Las cifras y las acciones del heroe van antes que los complementos (la
  // compra, las opiniones): primero que es, despues que opinan de el.
  if (zonaDeComplementos) {
    zonaDeComplementos.before(...bloques);
  } else {
    ficha.append(...bloques);
  }
}

/**
 * Pinta los complementos al final de la ficha, dentro de una zona propia.
 *
 * @param {HTMLElement} ficha
 * @param {object} producto
 * @param {Array<(producto: object, contexto: {ficha: HTMLElement}) => Node|null>} complementos
 * @returns {HTMLElement|null} la zona, o `null` si no hay ninguno
 */
function anadirComplementos(ficha, producto, complementos) {
  if (!Array.isArray(complementos) || complementos.length === 0) {
    return null;
  }
  const zona = document.createElement('div');
  zona.className = 'ficha__complementos';
  ficha.append(zona);
  for (const complemento of complementos) {
    try {
      const nodo = complemento(producto, { ficha });
      if (nodo) {
        zona.append(nodo);
      }
    } catch (fallo) {
      console.error('No se pudo completar la ficha', fallo);
    }
  }
  return zona;
}

/** Cierra la ficha abierta y devuelve el foco a donde estaba. */
export function cerrarFicha() {
  if (abierta === null) {
    return;
  }
  const { capa, devolverFocoA, alPulsarTecla } = abierta;
  abierta = null;
  document.removeEventListener('keydown', alPulsarTecla);
  capa.remove();
  if (devolverFocoA && typeof devolverFocoA.focus === 'function') {
    devolverFocoA.focus();
  }
}

function construirCerrar() {
  const boton = document.createElement('button');
  boton.type = 'button';
  boton.className = 'ficha__cerrar';
  boton.setAttribute('aria-label', 'Cerrar la ficha del producto');
  boton.textContent = '\u00d7';
  boton.addEventListener('click', () => cerrarFicha());
  return boton;
}

/**
 * Sustituye el contenido dejando el boton de cerrar **en su sitio**.
 *
 * No se usa `replaceChildren` incluyendo el boton: sacarlo del documento y
 * volver a insertarlo le quita el foco, y el teclado se quedaria sin punto
 * de entrada justo cuando aparece el producto.
 */
function reemplazarContenido(capa, contenido) {
  const cerrar = capa.querySelector('.ficha__cerrar');
  for (const hijo of [...capa.children]) {
    if (hijo !== cerrar) {
      hijo.remove();
    }
  }
  capa.appendChild(contenido);
}
