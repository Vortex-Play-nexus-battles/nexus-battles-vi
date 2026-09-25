/**
 * SCRUM-319 - Cuadricula de la vitrina del inventario (HU-INV-001, criterio 1).
 *
 * Construye la rejilla de productos que consume la pagina de inventario.
 * A la resolucion de referencia de 1360 x 768 la rejilla es de 4 x 4, de
 * donde sale el tope de 16: es el mismo numero que el servicio devuelve por
 * pagina en SCRUM-318, y ambos lados lo declaran por separado a proposito,
 * para que un cambio en uno rompa ruidosamente en el otro.
 *
 * ## UX-R2.5 — de tabla a coleccion
 *
 * Hasta aqui la tarjeta era dos parrafos de texto —nombre y tipo— y una fila
 * de botones. Funcionaba y no mentia, pero un inventario de un juego de
 * heroes que se lee como una lista de la compra no da ninguna sensacion de
 * poseer nada.
 *
 * Lo que se anade es SOLO lo que el contrato sostiene:
 *
 *  - **Retrato.** `ElementoInventario` no trae imagen, pero `productos.yaml`
 *    si: `GET /api/v1/productos/{id}` devuelve `imagen`. Se pide despues de
 *    pintar y se rellena cuando llega (ver `retratos.js`); mientras tanto, y
 *    si el catalogo no responde, queda el icono del tipo.
 *  - **Icono por tipo.** Del sprite del kit, que tenia treinta simbolos
 *    dibujados y usaban dos vistas. Un arma y una armadura dejan de ser dos
 *    rectangulos iguales.
 *  - **Marco de heroe.** `.marco-heroe` del kit, que nadie consumia.
 *
 * Lo que **no** se anade, porque no existe en ningun contrato del repositorio
 * —ni en `inventario.yaml`, ni en `productos.yaml`, ni en `heroes.yaml`—:
 * **rareza** y **nivel** del elemento. El kit tiene `distintivo--rareza` y
 * cuatro marcos de color esperandolos; el dia que el contrato los publique,
 * la tarjeta los pinta sin tocar el CSS. Inventarlos aqui seria ensenarle al
 * jugador un dato que el servidor no conoce.
 */

import { icono } from '../../comun/ui/icono.js';

/** Productos por pagina en la resolucion de referencia. */
export const PRODUCTOS_POR_PAGINA = 16;

/** Etiqueta legible de cada tipo del catalogo. */
const NOMBRE_DEL_TIPO = {
  HEROE: 'Héroe',
  HABILIDAD: 'Habilidad',
  ARMA: 'Arma',
  ARMADURA: 'Armadura',
  ITEM: 'Ítem',
  EPICA: 'Épica',
};

/**
 * Simbolo del sprite para cada tipo del catalogo.
 *
 * El enum sale de `ElementoInventario.tipo` en `inventario.yaml`. Un tipo que
 * no este aqui cae en `estrella`, que es neutro: mejor un simbolo generico que
 * un hueco.
 */
export const ICONO_DEL_TIPO = Object.freeze({
  HEROE: 'usuario',
  HABILIDAD: 'rayo',
  ARMA: 'espada',
  ARMADURA: 'escudo',
  ITEM: 'frasco',
  EPICA: 'estrella',
});

/**
 * Devuelve la rejilla de una pagina de inventario.
 *
 * @param {{elementos: Array<object>}} pagina respuesta de SCRUM-318.
 * @param {{alEditar?: Function, alEquipar?: Function, alAbrirDetalle?: Function}} opciones
 * @returns {HTMLUListElement} rejilla lista para insertar en el documento.
 */
export function construirVitrina(pagina, { alEditar, alEquipar, alAbrirDetalle, estadoDe } = {}) {
  if (!pagina || !Array.isArray(pagina.elementos)) {
    throw new TypeError('La página de inventario debe traer una lista de elementos');
  }
  if (pagina.elementos.length > PRODUCTOS_POR_PAGINA) {
    throw new RangeError(
      `La vitrina muestra ${PRODUCTOS_POR_PAGINA} productos por página y llegaron ` +
        `${pagina.elementos.length} elementos: el servicio rompio su contrato`,
    );
  }

  const vitrina = document.createElement('ul');
  vitrina.className = 'vitrina';
  for (const elemento of pagina.elementos) {
    vitrina.appendChild(construirTarjeta(elemento, alEditar, alEquipar, alAbrirDetalle, estadoDe));
  }
  return vitrina;
}

/**
 * El retrato de la tarjeta: marco, icono del tipo y hueco para la imagen.
 *
 * Se usa `.marco-heroe` tambien para los objetos, y no solo para los heroes,
 * porque es el unico marco circular que el kit tiene dibujado y la alternativa
 * era inventar uno identico con otro nombre. El tipo se distingue por el icono
 * y por el texto, no por el marco.
 *
 * @param {object} elemento
 * @returns {HTMLElement}
 */
function construirRetrato(elemento) {
  const marco = document.createElement('div');
  marco.className = 'marco-heroe vitrina__marco';

  const retrato = document.createElement('div');
  retrato.className = 'marco-heroe__retrato';
  // `retratos.js` busca esto para colgar la imagen cuando el catalogo
  // responda. Si no responde, se queda el icono y no pasa nada.
  retrato.dataset.retratoDe = elemento.productoId;

  // Decorativo: el tipo ya va escrito debajo, en `.vitrina__tipo`. Repetirlo
  // aqui obligaria a oirlo dos veces con lector de pantalla.
  retrato.append(
    icono(ICONO_DEL_TIPO[elemento.tipo] ?? 'estrella', {
      clase: 'vitrina__icono',
      etiqueta: null,
    }),
  );

  marco.append(retrato);
  return marco;
}

/**
 * Una tarjeta de producto. El nombre propio lo escribe el jugador, asi que
 * entra por textContent y nunca por innerHTML.
 */
function construirTarjeta(elemento, alEditar, alEquipar, alAbrirDetalle, estadoDe) {
  const tarjeta = document.createElement('li');
  tarjeta.className = 'vitrina__producto';
  tarjeta.dataset.elementoId = elemento.id;
  tarjeta.dataset.productoId = elemento.productoId;
  tarjeta.dataset.tipo = elemento.tipo;

  const disponible = elemento.disponible !== false;
  if (!disponible) {
    tarjeta.classList.add('vitrina__producto--no-disponible');
    tarjeta.setAttribute('aria-label', `${elemento.nombrePropio}, no disponible`);
  }

  const nombre = document.createElement('p');
  nombre.className = 'vitrina__nombre';
  nombre.textContent = elemento.nombrePropio;

  const tipo = document.createElement('p');
  tipo.className = 'vitrina__tipo';
  // La parte de armadura es lo que distingue un casco de unas botas, y venia
  // en el contrato desde el principio sin que la tarjeta la ensenara.
  tipo.textContent = elemento.parteArmadura
    ? `${NOMBRE_DEL_TIPO[elemento.tipo] ?? elemento.tipo} · ${etiquetaDeParte(elemento.parteArmadura)}`
    : (NOMBRE_DEL_TIPO[elemento.tipo] ?? elemento.tipo);

  tarjeta.append(construirRetrato(elemento), nombre, tipo);

  // UXC-1 — con `estadoDe` la tarjeta dice su estado completo (equipado en
  // quien, bloqueado por subasta, disponible) con icono y texto. Sin el, se
  // queda el aviso de siempre para lo no disponible.
  const sello = typeof estadoDe === 'function' ? estadoDe(elemento) : null;
  if (sello) {
    sello.classList.add('vitrina__estado');
    tarjeta.appendChild(sello);
  } else if (!disponible) {
    const estado = document.createElement('span');
    estado.className = 'vitrina__disponibilidad';
    estado.textContent = 'No disponible';
    tarjeta.appendChild(estado);
  }

  const acciones = document.createElement('div');
  acciones.className = 'vitrina__acciones';

  if (typeof alAbrirDetalle === 'function') {
    // HU-INV-007: la ficha se abre desde la tarjeta. Es un boton y no la
    // tarjeta entera para que el teclado lo alcance y para no chocar con
    // los demas botones de la tarjeta (RNF-ACC-002).
    const botonDetalle = document.createElement('button');
    botonDetalle.className = 'vitrina__detalle';
    botonDetalle.type = 'button';
    botonDetalle.textContent = 'Ver detalle';
    botonDetalle.setAttribute('aria-label', `Ver el detalle de ${elemento.nombrePropio}`);
    botonDetalle.addEventListener('click', () => alAbrirDetalle(elemento));
    acciones.appendChild(botonDetalle);
  }

  if (typeof alEditar === 'function') {
    const botonEditar = document.createElement('button');
    botonEditar.className = 'vitrina__editar';
    botonEditar.type = 'button';
    botonEditar.textContent = 'Editar';
    botonEditar.setAttribute('aria-label', `Editar ${elemento.nombrePropio}`);
    botonEditar.disabled = !disponible;
    botonEditar.addEventListener('click', () => alEditar(elemento));
    acciones.appendChild(botonEditar);
  }
  if (elemento.tipo === 'HEROE' && typeof alEquipar === 'function') {
    const botonEquipo = document.createElement('button');
    botonEquipo.className = 'vitrina__equipo';
    botonEquipo.type = 'button';
    botonEquipo.textContent = 'Equipo';
    botonEquipo.setAttribute('aria-label', `Gestionar equipo de ${elemento.nombrePropio}`);
    botonEquipo.disabled = !disponible;
    botonEquipo.addEventListener('click', () => alEquipar(elemento));
    acciones.appendChild(botonEquipo);
  }
  if (acciones.childElementCount > 0) {
    tarjeta.appendChild(acciones);
  }
  return tarjeta;
}

/**
 * Nombre legible de una parte de armadura (`ParteArmadura` del contrato).
 *
 * @param {string} parte
 * @returns {string}
 */
export function etiquetaDeParte(parte) {
  const nombres = {
    CASCO: 'Casco',
    PECHO: 'Pecho',
    GUANTES: 'Guantes',
    BRAZALETES: 'Brazaletes',
    PANTALON: 'Pantalón',
    ZAPATOS: 'Zapatos',
  };
  return nombres[parte] ?? parte;
}
