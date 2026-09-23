/**
 * Lo que se lee en pantalla está escrito en castellano — UX-R3.11.
 *
 * ## El defecto que esta prueba impide que vuelva
 *
 * El barrido de las 32 vistas encontró **125 palabras sin tilde en texto
 * visible, repartidas por 36 ficheros de producto**.
 *
 * (La primera medida fue de 101 en 27, con un diccionario más corto. 125/36 es
 * lo que da ESTE fichero, con la lista de abajo, corriendo contra el árbol de
 * antes del arreglo: `git archive <commit anterior> frontend/app-web/src`. Se
 * deja el número que se puede reproducir, no el que salió primero.)
 *
 * No eran un descuido aislado: estaban
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
 *   - las cadenas de los módulos que parecen una frase (tres palabras o más);
 *   - las plantillas, con sus `${…}` rellenados, para que una frase partida por
 *     una interpolación siga leyéndose como una frase;
 *   - y las cadenas de los `<script>` **del propio marcado**. Esto último se
 *     añadió después: la primera versión los descartaba enteros y por eso se le
 *     escapó `titulo: 'La sala se cerro'` en `sala-batalla.html`, que sí se
 *     pinta. Lo encontró una prueba de extremo a extremo que esperaba ese texto,
 *     no el guardián.
 *
 * Deja fuera, a propósito:
 *
 *   - los comentarios de código, que van sin tilde en este repositorio;
 *   - `throw new Error('sin sesion: redirigiendo al login')` y sus veinte
 *     copias: eso detiene el módulo mientras el navegador redirige, y nadie lo
 *     lee nunca;
 *   - los `console.*`, que salen por la consola del navegador y no por la
 *     pantalla. Misma exención que en `copy-de-producto.test.js`;
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
 * ## Lo que esta lista NO puede decidir
 *
 * `rechazo`/`rechazó` y `valida`/`válida`: las dos formas son correctas según
 * sean sustantivo o verbo, y una regla automática no sabe cuál toca. Se probó:
 * dejó «el motivo del rechazó» en `crear-sala.html` y «válida defensa» en once
 * nombres de prueba. Fuera de la lista.
 *
 * Por lo mismo quedan fuera las terceras personas del pasado que coinciden con
 * otra palabra: `cerro`/`cerró` (un cerro es un cerro), `cancelo`/`canceló`
 * («yo cancelo»), `expulsara`/`expulsará` (subjuntivo). Las tres estaban mal
 * escritas en `canal-sala.js` y `sala-de-espera.js` y **este guardián no las
 * habría encontrado**: las encontró una prueba de extremo a extremo que
 * esperaba el texto viejo. Conviene saber qué no cubre una prueba.
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
  anfitrion: 'anfitrión',
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

/**
 * Un valor de CSS tampoco es copy.
 *
 * `color-mix(in srgb, var(--credito-oro) 85%, var(--cromo))` tiene tres
 * palabras separadas por espacios y pasa por frase, pero lo que lleva dentro
 * son nombres de propiedad personalizada: `--credito-oro` se llama asi porque
 * los identificadores del repositorio van en ASCII a proposito, igual que las
 * clases. Ponerle la tilde lo rompe.
 *
 * Es la misma familia de fallo que ya costo caro una vez: la pasada masiva de
 * tildes renombro identificadores y rutas de API porque nadie distinguia el
 * texto que se lee del que solo ejecuta la maquina.
 */
const VALOR_CSS =
  /var\(--|^(?:color-mix|calc|clamp|min|max|linear-gradient|radial-gradient|rgba?|hsla?|translate|rotate|scale|cubic-bezier)\(/;

/**
 * Un hueco de plantilla se sustituye por una palabra antes de mirar la frase.
 *
 * Se añadió después, y por lo mismo que el resto de este fichero: `Campeon:
 * ${nombreDe(...)}` no pasaba por «frase» —los `$`, `{` y `}` no están entre
 * los caracteres que se aceptan— y por eso el campeón del torneo se anunciaba
 * sin tilde en la vista de torneos hasta que **lo encontró una prueba de
 * extremo a extremo**, no este guardián. Rellenando el hueco, la plantilla se
 * lee como lo que es: una frase.
 */
const HUECO = /\$\{[^{}]*\}/g;

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

/**
 * `throw new Error(...)` dentro de la guarda de una vista: detiene el módulo
 * mientras el navegador redirige. No se lee. Veinte vistas lo tienen idéntico.
 */
const DIAGNOSTICO = [
  /throw new Error\([\s\S]*?\);/g,
  // Un `console.*` sale por la consola del navegador, no por la pantalla.
  // Misma exención que en `copy-de-producto.test.js`, por la misma razón.
  /console\.(?:log|warn|error|info|debug)\([\s\S]*?\);/g,
];

/** Nodos de texto, atributos que una persona lee, y las cadenas de sus scripts. */
function visibleDeVista(ruta) {
  const crudo = readFileSync(ruta, 'utf8').replace(/<!--[\s\S]*?-->/g, ' ');
  const guiones = [...crudo.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g)].map((m) => m[1]);
  const html = crudo
    .replace(/<script[\s\S]*?<\/script>/g, ' ')
    .replace(/<style[\s\S]*?<\/style>/g, ' ');
  return [
    ...[...html.matchAll(/>([^<>]+)</g)].map((m) => m[1]),
    ...[...html.matchAll(/(?:placeholder|title|aria-label|alt|value)="([^"]+)"/g)].map((m) => m[1]),
    ...guiones.flatMap((guion) => cadenasDe(sinDiagnostico(guion))),
  ];
}

function sinDiagnostico(js) {
  return DIAGNOSTICO.reduce((texto, patron) => texto.replace(patron, ' '), js);
}

/** Las cadenas de un trozo de JavaScript, sin sus comentarios. */
function cadenasDe(js) {
  const limpio = js.replace(/\/\*[\s\S]*?\*\//g, ' ').replace(/^\s*\/\/.*$/gm, ' ');
  return [...limpio.matchAll(/'([^'\n]{6,})'|"([^"\n]{6,})"|`([^`\n]{6,})`/g)].flatMap((m) =>
    m.slice(1).filter(Boolean),
  );
}

/** Cadenas del módulo, sin comentarios. */
function visibleDeModulo(ruta) {
  return cadenasDe(sinDiagnostico(readFileSync(ruta, 'utf8')));
}

function hallazgos(nombre, trozos) {
  const fuera = [];
  for (const crudo of trozos) {
    const frase = crudo.replace(HUECO, 'dato').replace(/\s+/g, ' ').trim();
    if (!FRASE.test(frase) || CLASES.test(frase) || VALOR_CSS.test(frase)) {
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
