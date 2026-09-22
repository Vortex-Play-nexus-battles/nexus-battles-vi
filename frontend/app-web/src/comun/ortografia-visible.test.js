/**
 * Lo que se lee en pantalla está escrito en castellano — UX-R3.11.
 *
 * ## El defecto que esta prueba impide que vuelva
 *
 * El barrido de las 32 vistas encontró **101 palabras sin tilde en texto
 * visible, repartidas por 27 ficheros**. No eran un descuido aislado: estaban
 * en el portal —«El codigo llega por correo […] pide otro desde «Recuperar
 * contrasena»», que es de las primeras frases que lee alguien sin cuenta—, en
 * el diálogo de verificación de héroe («Verificacion de heroe», «Tu heroe esta
 * en otra partida») y en los mensajes de error de todos los clientes de
 * servicio («Revisa tu conexion e intentalo de nuevo.», repetido en seis
 * módulos).
 *
 * Un producto en castellano que escribe «conexion», «heroe» y «pagina» se lee
 * como un borrador. Es la diferencia entre parecer un trabajo de clase y
 * parecer un producto, que es justo lo que este bloque venía a corregir.
 *
 * ## Qué mira, y qué NO mira
 *
 * Solo el texto que llega a la pantalla:
 *
 *   - los nodos de texto del marcado (lo que hay entre `>` y `<`);
 *   - los atributos que una persona lee: `placeholder`, `title`, `aria-label`,
 *     `alt`, `value`;
 *   - las cadenas de los módulos que parecen una frase (tres palabras o más).
 *
 * Deja fuera, a propósito:
 *
 *   - los comentarios de código, que van sin tilde en este repositorio;
 *   - los identificadores, los selectores y las claves de objeto, que son
 *     ASCII siempre (`creditosDe`, `[data-zona="creditos"]`, `/api/v1/creditos`);
 *   - las listas de clases del kit (`boton boton--primario boton--pequeno`),
 *     que son identificadores con espacios, no una frase;
 *   - los `.test.js` y `.spec.js`.
 *
 * ## Por qué es una prueba y no una revisión
 *
 * Porque son tres equipos escribiendo copy y porque ya volvió: la mitad de las
 * 101 estaban en cadenas COPIADAS entre módulos («Revisa tu conexion e
 * intentalo de nuevo.» aparecía seis veces, idéntica).
 *
 * ## Dos palabras que NO están en la lista
 *
 * `rechazo`/`rechazó` y `valida`/`válida`: las dos formas son correctas según
 * sean sustantivo o verbo, y una regla automática no sabe cuál toca. Se probó:
 * dejó «el motivo del rechazó» en `crear-sala.html` y «válida defensa» en once
 * nombres de prueba. Fuera de la lista.
 */

import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';

const RAIZ = new URL('../', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');

/** Forma sin tilde → forma correcta. */
const PARES = Object.freeze({
  accion: 'acción',
  activacion: 'activación',
  administracion: 'administración',
  algun: 'algún',
  analisis: 'análisis',
  apelacion: 'apelación',
  aplicacion: 'aplicación',
  aqui: 'aquí',
  arbol: 'árbol',
  asi: 'así',
  autenticacion: 'autenticación',
  basico: 'básico',
  boton: 'botón',
  busqueda: 'búsqueda',
  calificacion: 'calificación',
  campeon: 'campeón',
  catalogo: 'catálogo',
  codigo: 'código',
  comision: 'comisión',
  condicion: 'condición',
  configuracion: 'configuración',
  conexion: 'conexión',
  contrasena: 'contraseña',
  credito: 'crédito',
  creditos: 'créditos',
  descripcion: 'descripción',
  despues: 'después',
  dia: 'día',
  dias: 'días',
  dificil: 'difícil',
  direccion: 'dirección',
  duracion: 'duración',
  economico: 'económico',
  ensena: 'enseña',
  espanol: 'español',
  estan: 'están',
  facil: 'fácil',
  grafico: 'gráfico',
  habias: 'habías',
  heroe: 'héroe',
  heroes: 'héroes',
  historico: 'histórico',
  imagenes: 'imágenes',
  informacion: 'información',
  ingles: 'inglés',
  intentalo: 'inténtalo',
  invalido: 'inválido',
  logico: 'lógico',
  manana: 'mañana',
  maquina: 'máquina',
  maximo: 'máximo',
  metrica: 'métrica',
  metricas: 'métricas',
  minimo: 'mínimo',
  mision: 'misión',
  moderacion: 'moderación',
  multiple: 'múltiple',
  ningun: 'ningún',
  notificacion: 'notificación',
  numero: 'número',
  opcion: 'opción',
  operacion: 'operación',
  pagina: 'página',
  parametro: 'parámetro',
  parametros: 'parámetros',
  peticion: 'petición',
  pequeno: 'pequeño',
  politica: 'política',
  politico: 'político',
  posicion: 'posición',
  practico: 'práctico',
  proximo: 'próximo',
  publicacion: 'publicación',
  publico: 'público',
  quiza: 'quizá',
  rapido: 'rápido',
  razon: 'razón',
  revision: 'revisión',
  sancion: 'sanción',
  seccion: 'sección',
  segun: 'según',
  seleccion: 'selección',
  sesion: 'sesión',
  tambien: 'también',
  tecnica: 'técnica',
  tecnicas: 'técnicas',
  tecnico: 'técnico',
  titulo: 'título',
  todavia: 'todavía',
  transaccion: 'transacción',
  ultimo: 'último',
  unico: 'único',
  vacias: 'vacías',
  vacio: 'vacío',
  validacion: 'validación',
  verificacion: 'verificación',
  version: 'versión',
});

const PATRON = new RegExp(
  `\\b(${Object.keys(PARES)
    .sort((a, b) => b.length - a.length)
    .join('|')})\\b`,
  'i',
);

/**
 * Una lista de clases del kit no es copy: `boton boton--primario boton--pequeno`
 * son identificadores y van en ASCII a propósito.
 */
const CLASES = /^[a-z][a-z0-9_-]*(?:\s+[a-z][a-z0-9_-]*)*$/;

const LETRA = 'A-Za-zÁÉÍÓÚÜÑáéíóúüñ';
const SIGNO = '0-9¿¡«»,;:.()\'’\\-–·%!?"';
const FRASE = new RegExp(`^[${LETRA}¿¡][${LETRA}${SIGNO}]*(?:\\s+[${LETRA}${SIGNO}]+){2,}$`);

function ficheros(extension) {
  const encontrados = [];
  const recorrer = (dir) => {
    for (const entrada of readdirSync(dir)) {
      const completa = join(dir, entrada);
      if (statSync(completa).isDirectory()) {
        recorrer(completa);
      } else if (
        entrada.endsWith(extension) &&
        !entrada.endsWith('.test.js') &&
        !entrada.endsWith('.spec.js')
      ) {
        encontrados.push(completa);
      }
    }
  };
  recorrer(RAIZ);
  return encontrados;
}

/** Nodos de texto y atributos que una persona lee. */
function visibleDeVista(ruta) {
  const html = readFileSync(ruta, 'utf8')
    .replace(/<!--[\s\S]*?-->/g, ' ')
    .replace(/<script[\s\S]*?<\/script>/g, ' ')
    .replace(/<style[\s\S]*?<\/style>/g, ' ');
  return [
    ...[...html.matchAll(/>([^<>]+)</g)].map((m) => m[1]),
    ...[...html.matchAll(/(?:placeholder|title|aria-label|alt|value)="([^"]+)"/g)].map((m) => m[1]),
  ];
}

/** Cadenas del módulo, sin comentarios. */
function visibleDeModulo(ruta) {
  const js = readFileSync(ruta, 'utf8')
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/^\s*\/\/.*$/gm, ' ');
  return [...js.matchAll(/'([^'\n]{6,})'|"([^"\n]{6,})"|`([^`\n]{6,})`/g)].flatMap((m) =>
    m.slice(1).filter(Boolean),
  );
}

function hallazgos(nombre, trozos) {
  const fuera = [];
  for (const crudo of trozos) {
    const frase = crudo.replace(/\s+/g, ' ').trim();
    if (!FRASE.test(frase) || CLASES.test(frase)) {
      continue;
    }
    const encontrado = PATRON.exec(frase);
    if (encontrado) {
      const mal = encontrado[0].toLowerCase();
      fuera.push(`${nombre}: «${encontrado[0]}» → «${PARES[mal]}» en "${frase.slice(0, 80)}"`);
    }
  }
  return fuera;
}

describe('el texto que se ve está escrito en castellano', () => {
  test('el detector salta con una frase real de las que había', () => {
    // Un guardián que no salta no protege nada. Estas dos estaban en el
    // producto: la primera en el portal, la segunda en seis módulos.
    expect(
      hallazgos('x', [
        'El codigo llega por correo, dura poco y solo sirve una vez.',
        'Revisa tu conexion e intentalo de nuevo.',
      ]),
    ).toHaveLength(2);
  });

  test('no confunde una lista de clases del kit con una frase', () => {
    expect(hallazgos('x', ['boton boton--primario boton--pequeno'])).toEqual([]);
  });

  test('ninguna vista escribe una palabra sin tilde en lo que se lee', () => {
    const encontrados = ficheros('.html').flatMap((ruta) =>
      hallazgos(ruta.slice(RAIZ.length), visibleDeVista(ruta)),
    );
    expect(encontrados).toEqual([]);
  });

  test('ningún módulo escribe una palabra sin tilde en lo que se lee', () => {
    const encontrados = ficheros('.js').flatMap((ruta) =>
      hallazgos(ruta.slice(RAIZ.length), visibleDeModulo(ruta)),
    );
    expect(encontrados).toEqual([]);
  });
});
