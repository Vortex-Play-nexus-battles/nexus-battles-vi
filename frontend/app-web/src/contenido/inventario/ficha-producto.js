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
    ['tasaDeCaida', 'Tasa de caida'],
  ],
  ARMADURA: [
    ['defensa', 'Defensa'],
    ['parte', 'Parte'],
    ['tasaDeCaida', 'Tasa de caida'],
  ],
  ITEM: [
    ['efecto', 'Efecto'],
    ['tasaDeCaida', 'Tasa de caida'],
  ],
  EPICA: [
    ['turnosRecarga', 'Turnos de recarga'],
    ['efectoGeneral', 'Efecto general'],
    ['efectoPotenciado', 'Efecto potenciado'],
  ],
});

/** Etiqueta legible de cada tipo del catalogo. */
const NOMBRE_DEL_TIPO = {
  HEROE: 'Héroe',
  HABILIDAD: 'Habilidad',
  ARMA: 'Arma',
  ARMADURA: 'Armadura',
  ITEM: 'Ítem',
  EPICA: 'Épica',
};

let secuencia = 0;

/**
 * Construye la ficha de un producto del catalogo.
 *
 * @param {object} producto tal como lo devuelve el servicio de productos.
 * @returns {HTMLElement} dialogo listo para insertar en el documento.
 */
export function construirFicha(producto) {
  if (!producto || typeof producto !== 'object') {
    throw new TypeError('La ficha necesita un producto del catalogo');
  }

  const ficha = document.createElement('article');
  ficha.className = 'ficha';
  ficha.setAttribute('role', 'dialog');
  ficha.setAttribute('aria-modal', 'true');

  const idNombre = `ficha-nombre-${(secuencia += 1)}`;
  ficha.setAttribute('aria-labelledby', idNombre);

  const imagen = document.createElement('img');
  imagen.className = 'ficha__imagen';
  imagen.src = producto.imagen ?? '';
  // El texto alternativo es el nombre: quien no ve la imagen sigue sabiendo
  // que producto esta mirando (RNF-ACC-002).
  imagen.alt = producto.nombre ?? '';

  const nombre = document.createElement('h2');
  nombre.className = 'ficha__nombre';
  nombre.id = idNombre;
  nombre.textContent = producto.nombre ?? '';

  const tipo = document.createElement('p');
  tipo.className = 'ficha__tipo';
  tipo.textContent = NOMBRE_DEL_TIPO[producto.tipo] ?? producto.tipo ?? '';

  // Texto que escribe el administrador: entra por textContent, nunca por
  // innerHTML.
  const descripcion = document.createElement('p');
  descripcion.className = 'ficha__descripcion';
  descripcion.textContent = producto.descripcion ?? '';

  ficha.append(imagen, nombre, tipo, descripcion, construirAtributos(producto));

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
export async function abrirFicha(productoId, { consultarProducto, origen } = {}) {
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
    console.error('No se pudo cargar el producto del catalogo', fallo);
    reemplazarContenido(
      capa,
      construirError('No pudimos cargar este producto.', 'Vuelve a intentarlo en un momento.'),
    );
    return;
  }

  if (abierta === null || abierta.capa !== capa) {
    return; // Se cerro mientras se consultaba.
  }
  reemplazarContenido(capa, construirFicha(producto));
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
