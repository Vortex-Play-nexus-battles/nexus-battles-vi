/**
 * Los ocho prototipos de héroe, reconocibles a simple vista — UXC-1.
 *
 * ## Qué es y qué no es
 *
 * §6.1.1 del documento fija ocho prototipos (Tabla 6): Guerrero Tanque,
 * Guerrero Armas, Mago Fuego, Mago Hielo, Pícaro Veneno, Pícaro Machete,
 * Chamán y Médico. Hasta aquí todos se pintaban igual —un círculo con la
 * inicial—, así que un Chamán y un Guerrero Tanque eran indistinguibles en el
 * inventario, en el combate y en cualquier selector.
 *
 * Este módulo **solo da identidad visual**: la familia escrita y un símbolo
 * del sprite. No guarda ni una cifra: las estadísticas las publica el
 * catálogo de héroes (`heroes.yaml`) y las del héroe propio el inventario.
 * Duplicarlas aquí sería tener dos fuentes que discrepan el día que el
 * negocio las ajuste («se anticipa la adición de más personajes», nota del
 * §6.1.1).
 *
 * El nombre del prototipo viene del catálogo de productos (campo `prototipo`)
 * o del de héroes (`nombre`), con o sin tildes. Uno que no esté aquí no se
 * inventa: se reconoce como «prototipo sin identificar» con un símbolo
 * neutro, que es la verdad.
 *
 * ## No solo color
 *
 * Cada prototipo lleva símbolo propio y su nombre escrito; la familia además
 * se escribe. El color de familia es un refuerzo, nunca el único canal
 * (XAG 102, RNF-ACC).
 */

/** Familias de la Tabla 6, en su orden. */
export const FAMILIAS = Object.freeze({
  GUERRERO: 'Guerrero',
  MAGO: 'Mago',
  PICARO: 'Pícaro',
  SANADOR: 'Sanador',
});

/**
 * Los ocho de la Tabla 6. `clave` es el nombre sin tildes ni mayúsculas, que
 * es como se comparan; `nombre` es como se escribe.
 */
export const PROTOTIPOS = Object.freeze([
  { clave: 'guerrero tanque', nombre: 'Guerrero Tanque', familia: 'GUERRERO', icono: 'escudo' },
  { clave: 'guerrero armas', nombre: 'Guerrero Armas', familia: 'GUERRERO', icono: 'espada' },
  { clave: 'mago fuego', nombre: 'Mago Fuego', familia: 'MAGO', icono: 'fuego' },
  { clave: 'mago hielo', nombre: 'Mago Hielo', familia: 'MAGO', icono: 'copo' },
  { clave: 'picaro veneno', nombre: 'Pícaro Veneno', familia: 'PICARO', icono: 'gota' },
  { clave: 'picaro machete', nombre: 'Pícaro Machete', familia: 'PICARO', icono: 'daga' },
  { clave: 'chaman', nombre: 'Chamán', familia: 'SANADOR', icono: 'hoja' },
  { clave: 'medico', nombre: 'Médico', familia: 'SANADOR', icono: 'cruz' },
]);

/**
 * Minúsculas, sin tildes y con los espacios normalizados.
 *
 * @param {string|null|undefined} texto
 * @returns {string}
 */
export function normalizarNombre(texto) {
  return String(texto ?? '')
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/\s+/g, ' ')
    .trim();
}

/**
 * La identidad de un prototipo, o la de «sin identificar».
 *
 * @param {string|null|undefined} nombre como lo publique el catálogo
 * @returns {{conocido: boolean, clave: string, nombre: string, familia: string|null,
 *   etiquetaFamilia: string|null, icono: string, sanador: boolean}}
 */
export function identidadDePrototipo(nombre) {
  const clave = normalizarNombre(nombre);
  const encontrado = PROTOTIPOS.find((p) => p.clave === clave);
  if (!encontrado) {
    return {
      conocido: false,
      clave,
      nombre: String(nombre ?? '').trim() || 'Prototipo sin identificar',
      familia: null,
      etiquetaFamilia: null,
      icono: 'usuario',
      sanador: false,
    };
  }
  return {
    conocido: true,
    clave: encontrado.clave,
    nombre: encontrado.nombre,
    familia: encontrado.familia,
    etiquetaFamilia: FAMILIAS[encontrado.familia],
    icono: encontrado.icono,
    sanador: encontrado.familia === 'SANADOR',
  };
}
