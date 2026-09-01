/**
 * Compila la plantilla corporativa MJML a la plantilla Thymeleaf que usa el
 * servicio en tiempo de ejecucion (HU-COR-001, CA-03).
 *
 * La fuente unica es el .mjml. El .html de salida es GENERADO: nunca se edita a
 * mano. Para evitar que alguien lo haga (o que edite el .mjml y olvide
 * recompilar), se escribe tambien el hash de la fuente en un archivo lateral,
 * y una prueba de JUnit falla si los dos dejan de coincidir.
 *
 * Uso:  node tools/compilar-plantillas.mjs
 *   o:  ./gradlew :services:plataforma:correo:compilarPlantillas
 */
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const raizServicio = join(dirname(fileURLToPath(import.meta.url)), '..');
const recursos = join(raizServicio, 'src', 'main', 'resources');

const ORIGEN = join(recursos, 'mjml', 'layout-corporativo.mjml');
const DESTINO = join(recursos, 'templates', 'email', 'layout-corporativo.html');
const HASH = join(recursos, 'mjml', 'layout-corporativo.mjml.sha256');

const AVISO = [
  '<!--',
  '  ARCHIVO GENERADO — NO EDITAR A MANO.',
  '  Fuente: src/main/resources/mjml/layout-corporativo.mjml',
  '  Regenerar: ./gradlew :services:plataforma:correo:compilarPlantillas',
  '-->',
].join('\n');

// Thymeleaf en modo HTML no exige los xmlns, pero declararlos evita avisos del
// editor y deja explicito que la plantilla usa el dialecto de layout.
const ESPACIOS_NOMBRES =
  ' xmlns:th="http://www.thymeleaf.org"' +
  ' xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"';

const fuente = readFileSync(ORIGEN, 'utf8');

// Version fijada a proposito: con "mjml" a secas npx bajaria la ultima
// publicada y la plantilla generada podria cambiar entre ejecuciones.
const VERSION_MJML = 'mjml@5.4.0';

// shell:true es necesario en Windows: npx es un .cmd y spawnSync falla con
// EINVAL si se invoca directamente.
const compilado = execFileSync('npx', ['--yes', VERSION_MJML, `"${ORIGEN}"`, '-s'], {
  encoding: 'utf8',
  maxBuffer: 10 * 1024 * 1024,
  shell: true,
});

if (!compilado.includes('layout:fragment="contenido"')) {
  throw new Error(
    'La plantilla compilada no contiene layout:fragment="contenido". ' +
      'Revisa el bloque mj-raw del .mjml antes de continuar.',
  );
}

const salida = compilado
  // mjml -s antepone un comentario "<!-- FILE: ... -->" con la ruta local del
  // archivo. Se elimina: no aporta nada al correo y filtraria rutas de la
  // maquina de quien compilo al buzon del destinatario.
  .replace(/^\s*<!--\s*FILE:[^>]*-->\s*/i, '')
  .replace(/^(<!doctype html>)/i, `$1\n${AVISO}`)
  .replace(/(<html\b[^>]*?)(>)/i, `$1${ESPACIOS_NOMBRES}$2`);

if (salida.includes('<!-- FILE:') || !salida.startsWith('<!doctype html>')) {
  throw new Error('La salida no empieza por <!doctype html> tras la limpieza.');
}

// Se normalizan los saltos de linea antes de calcular el hash: Git en Windows
// puede convertir LF a CRLF al hacer checkout y el hash cambiaria sin que nadie
// haya tocado el contenido.
const sha = (texto) =>
  createHash('sha256').update(texto.replace(/\r\n/g, '\n'), 'utf8').digest('hex');

const shaFuente = sha(fuente);
const shaGenerado = sha(salida);

writeFileSync(DESTINO, salida, 'utf8');
writeFileSync(
  HASH,
  [
    '# Generado por tools/compilar-plantillas.mjs — no editar a mano.',
    '# PlantillaSincronizadaTest falla si estos hashes dejan de coincidir.',
    `fuente=${shaFuente}`,
    `generado=${shaGenerado}`,
    '',
  ].join('\n'),
  'utf8',
);

console.log(`plantilla generada: ${DESTINO}`);
console.log(`hash fuente:    ${shaFuente}`);
console.log(`hash generado:  ${shaGenerado}`);
