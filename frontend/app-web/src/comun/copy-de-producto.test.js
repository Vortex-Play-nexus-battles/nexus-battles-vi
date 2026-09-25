/**
 * Nada de la entrega académica se ve dentro del juego — UX-R3.1.
 *
 * ## El defecto que esta prueba impide que vuelva
 *
 * La pantalla de inicio de sesión —la primera que ve alguien que todavía no
 * tiene cuenta— decía, encima del nombre del juego:
 *
 *     Proyecto Integrador II · UPB Bucaramanga
 *
 * Y el destino «Misiones» de la barra llevaba este `title`:
 *
 *     Todavía no publicada: HU-MIS (grupo-2)
 *
 * Las dos cosas son ciertas y ninguna es del producto: son el nombre de la
 * asignatura, el de la universidad, el número de una historia de Jira y el de
 * un equipo interno. Nexus Battles VI se presenta como juego; la trazabilidad
 * vive en el repositorio, en los issues y en `docs/`, que es donde la busca
 * quien la necesita.
 *
 * ## Qué mira
 *
 * El texto que puede acabar en pantalla: el marcado de las vistas sin sus
 * comentarios ni sus `<script>`, y las cadenas de los módulos sin sus
 * comentarios. No mira comentarios de código —ahí explicar de dónde sale algo
 * es exactamente lo que hay que hacer— ni los `.test.js`.
 *
 * ## Por qué es una prueba y no una revisión
 *
 * Porque este texto lo escriben tres equipos y vuelve solo. Ya volvió una vez:
 * `registro.html` tenía la misma línea que `login.html`, copiada.
 */

import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';

const RAIZ = new URL('../', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');

/**
 * Lo que no puede leerse dentro del producto. Cada patrón con el motivo por
 * el que está aquí, para que nadie lo borre sin saber qué quitaba.
 */
const PROHIBIDO = Object.freeze([
  { patron: /Proyecto\s+Integrador/i, motivo: 'nombre de la asignatura' },
  { patron: /\bUPB\b/, motivo: 'nombre de la universidad' },
  { patron: /Bucaramanga/i, motivo: 'sede de la universidad' },
  { patron: /\bEmpresa\s+A\b/i, motivo: 'nombre interno de la organización del curso' },
  { patron: /\bgrupo[-\s]?[0-9]\b/i, motivo: 'nombre de un equipo interno' },
  { patron: /\bSprint\s*[0-9]/i, motivo: 'planificación interna' },
  { patron: /\bHU-[A-Z]{2,}(-[0-9]+)?/, motivo: 'identificador de historia de usuario' },
  { patron: /\bRF-[A-Z]{2,}-[0-9]+/, motivo: 'identificador de requisito' },
  { patron: /\bRNF-[A-Z]{2,}-[0-9]+/, motivo: 'identificador de requisito no funcional' },
  { patron: /\bmicroservicio/i, motivo: 'detalle de arquitectura interna' },
  { patron: /\bms-[a-z]+/, motivo: 'nombre de un microservicio' },
  { patron: /proyecto\s+acad[eé]mico/i, motivo: 'marco del curso' },
  // UXC-9 — lo que un jugador no debe leer nunca, ni en un fallo.
  { patron: /\bsrv-[a-z]+/, motivo: 'nombre de un servidor interno' },
  { patron: /\bRN-[A-Z]{2,}-[0-9]+/, motivo: 'identificador de regla de negocio' },
  { patron: /\bHTTP\s?[1-5][0-9]{2}\b/, motivo: 'código HTTP' },
  { patron: /\bError\s+(?:HTTP\s+)?[1-5][0-9]{2}\b/, motivo: 'código HTTP' },
  {
    patron: /\b(?:Bad Gateway|Internal Server Error|Service Unavailable|Gateway Time-?out)\b/,
    motivo: 'frase de estado HTTP',
  },
  { patron: /\b[A-Z][A-Za-z]+Exception\b/, motivo: 'nombre de una excepción' },
]);

/**
 * Lo que parece prohibido y no lo es, porque no se lee: son identificadores
 * que viajan por la red y que el usuario nunca ve.
 *
 * `nexusbattles.upb.edu.co` es el espacio de nombres de los tipos de error
 * (RFC 9457 «problem details»), acordado en el contrato y comparado carácter a
 * carácter contra lo que manda `ms-identidad`. Cambiarlo rompería el mapeo de
 * mensajes; y nadie lo ve, porque lo que se pinta es el mensaje, no el tipo.
 */
const EXENTO = Object.freeze([
  /https?:\/\/nexusbattles\.upb\.edu\.co\/[^\s'"`]*/g,
  // `console.warn('[HU-RBAC-004 - 403]…')` del interceptor: sale por la
  // consola del navegador, no por la pantalla. Quien la abre es quien depura,
  // y ahí el identificador de la historia es justo lo que ayuda.
  /console\.(?:log|warn|error|info|debug)\([\s\S]*?\);/g,
]);

function ficheros(extension) {
  const encontrados = [];
  const recorrer = (dir) => {
    for (const entrada of readdirSync(dir)) {
      const completa = join(dir, entrada);
      if (statSync(completa).isDirectory()) {
        recorrer(completa);
      } else if (entrada.endsWith(extension) && !entrada.endsWith('.test.js')) {
        encontrados.push(completa);
      }
    }
  };
  recorrer(RAIZ);
  return encontrados;
}

/** Marcado sin comentarios ni scripts: lo que de verdad se pinta. */
function textoDeVista(ruta) {
  return readFileSync(ruta, 'utf8')
    .replace(/<!--[\s\S]*?-->/g, ' ')
    .replace(/<script[\s\S]*?<\/script>/g, ' ');
}

/** Módulo sin comentarios: quedan las cadenas, que son las que se leen. */
function textoDeModulo(ruta) {
  return readFileSync(ruta, 'utf8')
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/^\s*\/\/.*$/gm, ' ');
}

function hallazgos(nombre, texto) {
  let limpio = texto;
  for (const exento of EXENTO) {
    limpio = limpio.replace(exento, ' ');
  }
  return PROHIBIDO.filter(({ patron }) => patron.test(limpio)).map(
    ({ patron, motivo }) => `${nombre}: ${limpio.match(patron)[0]} (${motivo})`,
  );
}

test('ninguna vista enseña el nombre de la asignatura, del grupo ni de una historia', () => {
  const encontrados = ficheros('.html').flatMap((ruta) =>
    hallazgos(ruta.slice(RAIZ.length), textoDeVista(ruta)),
  );
  expect(encontrados).toEqual([]);
});

test('ningún módulo pone texto interno en pantalla', () => {
  const encontrados = ficheros('.js').flatMap((ruta) =>
    hallazgos(ruta.slice(RAIZ.length), textoDeModulo(ruta)),
  );
  expect(encontrados).toEqual([]);
});

test('la prueba detecta lo que dice detectar', () => {
  // Un trinquete que no salta no protege nada. Estos son los dos textos
  // reales que había en el producto antes de UX-R3.1.
  expect(hallazgos('x', '<p>Proyecto Integrador II · UPB Bucaramanga</p>')).toHaveLength(3);
  expect(hallazgos('x', "title = 'Todavía no publicada: HU-MIS (grupo-2)'")).toHaveLength(2);
  // Y el espacio de nombres de los errores no salta, porque no se lee.
  expect(hallazgos('x', "'https://nexusbattles.upb.edu.co/errors/cuenta-bloqueada'")).toEqual([]);
  // UXC-9 — los que llegaban por los fallos: códigos, excepciones, reglas y
  // nombres internos.
  expect(hallazgos('x', "detalle: `Error ${estado}` + 'Error 503'")).toHaveLength(1);
  expect(hallazgos('x', "'HTTP 502 · Bad Gateway'")).toHaveLength(2);
  expect(hallazgos('x', "'srv-inventario: NullPointerException'")).toHaveLength(2);
  expect(hallazgos('x', "'(RN-INV-004)'")).toHaveLength(1);
});
