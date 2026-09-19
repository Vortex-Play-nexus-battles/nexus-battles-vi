// Pruebas de las herramientas del JWKS de desarrollo (node --test).
// Fijan el contrato: generar-claves.mjs deja la clave privada en un archivo
// fuera del repositorio y devuelve el JWKS publico con el formato que lee
// Spring (kty/n/e/kid/alg/use); emitir-token.mjs firma un JWT RS256 con esa
// clave, con realm_access.roles y vencimiento, verificable con el JWKS.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createPublicKey, verify } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, readFileSync, statSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const aqui = dirname(fileURLToPath(import.meta.url));
const carpeta = mkdtempSync(join(tmpdir(), 'jwks-dev-'));
const pem = join(carpeta, 'privada.pem');

function correr(script, ...args) {
  return execFileSync(process.execPath, [join(aqui, script), ...args], { encoding: 'utf8' }).trim();
}
function b64url(s) { return Buffer.from(s, 'base64url').toString('utf8'); }

test('generar-claves.mjs deja la clave privada en el archivo y devuelve un JWKS con una clave RSA publica', () => {
  const jwks = JSON.parse(correr('generar-claves.mjs', pem));
  assert.equal(jwks.keys.length, 1);
  const k = jwks.keys[0];
  assert.equal(k.kty, 'RSA');
  assert.equal(k.alg, 'RS256');
  assert.equal(k.use, 'sig');
  assert.ok(k.kid && k.n && k.e, 'kid, n y e presentes');
  assert.ok(!('d' in k), 'el JWKS no lleva la parte privada');
  assert.match(readFileSync(pem, 'utf8'), /BEGIN PRIVATE KEY/);
});

test('emitir-token.mjs firma un JWT RS256 con rol ADMINISTRADOR, verificable con el JWKS', () => {
  const jwks = JSON.parse(readFileSync(join(carpeta, 'jwks.json'), 'utf8'));
  const token = correr('emitir-token.mjs', pem, '--usuario', 'cesar', '--horas', '2');
  const [h, p, s] = token.split('.');
  assert.equal(token.split('.').length, 3);
  const cabecera = JSON.parse(b64url(h));
  assert.equal(cabecera.alg, 'RS256');
  assert.equal(cabecera.kid, jwks.keys[0].kid);
  const cuerpo = JSON.parse(b64url(p));
  assert.deepEqual(cuerpo.realm_access.roles, ['ADMINISTRADOR']);
  assert.equal(cuerpo.preferred_username, 'cesar');
  const ahora = Math.floor(Date.now() / 1000);
  assert.ok(cuerpo.exp > ahora + 3500 && cuerpo.exp <= ahora + 7200 + 5, 'vence en ~2 horas');
  assert.ok(cuerpo.iat <= ahora);
  const publica = createPublicKey({ key: jwks.keys[0], format: 'jwk' });
  assert.ok(verify('sha256', Buffer.from(`${h}.${p}`), publica, Buffer.from(s, 'base64url')), 'firma valida');
});

test('emitir-token.mjs acepta otro rol y vence en 8 horas por defecto', () => {
  const token = correr('emitir-token.mjs', pem, '--rol', 'SUPER_ADMINISTRADOR');
  const cuerpo = JSON.parse(b64url(token.split('.')[1]));
  assert.deepEqual(cuerpo.realm_access.roles, ['SUPER_ADMINISTRADOR']);
  const ahora = Math.floor(Date.now() / 1000);
  assert.ok(cuerpo.exp > ahora + 8 * 3600 - 10 && cuerpo.exp <= ahora + 8 * 3600 + 5);
});

test('generar-claves.mjs tambien deja jwks.json junto a la clave privada', () => {
  assert.ok(statSync(join(carpeta, 'jwks.json')).isFile());
});
