/**
 * SCRUM-319 - Cuadricula de la vitrina del inventario (HU-INV-001, criterio 1).
 *
 * Construye la rejilla de productos que consume la pagina de inventario.
 * A la resolucion de referencia de 1360 x 768 la rejilla es de 4 x 4, de
 * donde sale el tope de 16: es el mismo numero que el servicio devuelve por
 * pagina en SCRUM-318, y ambos lados lo declaran por separado a proposito,
 * para que un cambio en uno rompa ruidosamente en el otro.
 */

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
 * Devuelve la rejilla de una pagina de inventario.
 *
 * @param {{elementos: Array<object>}} pagina respuesta de SCRUM-318.
 * @returns {HTMLUListElement} rejilla lista para insertar en el documento.
 */
export function construirVitrina(pagina) {
  if (!pagina || !Array.isArray(pagina.elementos)) {
    throw new TypeError('La pagina de inventario debe traer una lista de elementos');
  }
  if (pagina.elementos.length > PRODUCTOS_POR_PAGINA) {
    throw new RangeError(
      `La vitrina muestra ${PRODUCTOS_POR_PAGINA} productos por pagina y llegaron ` +
        `${pagina.elementos.length} elementos: el servicio rompio su contrato`,
    );
  }

  const vitrina = document.createElement('ul');
  vitrina.className = 'vitrina';
  for (const elemento of pagina.elementos) {
    vitrina.appendChild(construirTarjeta(elemento));
  }
  return vitrina;
}

/**
 * Una tarjeta de producto. El nombre propio lo escribe el jugador, asi que
 * entra por textContent y nunca por innerHTML.
 */
function construirTarjeta(elemento) {
  const tarjeta = document.createElement('li');
  tarjeta.className = 'vitrina__producto';
  tarjeta.dataset.productoId = elemento.productoId;

  const nombre = document.createElement('p');
  nombre.className = 'vitrina__nombre';
  nombre.textContent = elemento.nombrePropio;

  const tipo = document.createElement('p');
  tipo.className = 'vitrina__tipo';
  tipo.textContent = NOMBRE_DEL_TIPO[elemento.tipo] ?? elemento.tipo;

  tarjeta.append(nombre, tipo);
  return tarjeta;
}
