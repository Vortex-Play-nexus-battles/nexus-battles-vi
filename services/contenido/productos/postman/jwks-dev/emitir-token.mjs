#!/usr/bin/env node
// Emite un JWT RS256 firmado con la clave privada del JWKS de desarrollo.
//
//   node emitir-token.mjs <clave-privada.pem> [--rol ADMINISTRADOR] [--usuario cesar] [--horas 8]
//
// El token lleva realm_access.roles (lo que lee SeguridadConfig de productos)
// y vence en --horas. Se pega en la variable "token" de Postman o se pasa a
// Newman con --env-var token=$(node emitir-token.mjs ...).
import { createPrivateKey, createPublicKey, createHash, sign } from 'node:crypto';
import { readFileSync } from 'node:fs';

const args = process.argv.slice(2);
const ruta = args[0];
if (!ruta || ruta.startsWith('--')) {
  console.error('uso: node emitir-token.mjs <clave-privada.pem> [--rol ROL] [--usuario NOMBRE] [--horas N]');
  process.exit(2);
}
function opcion(nombre, porDefecto) {
  const i = args.indexOf(nombre);
  return i >= 0 && args[i + 1] ? args[i + 1] : porDefecto;
}
const rol = opcion('--rol', 'ADMINISTRADOR');
const usuario = opcion('--usuario', 'dev');
const horas = Number(opcion('--horas', '8'));

const privada = createPrivateKey(readFileSync(ruta, 'utf8'));
const { n, e } = createPublicKey(privada).export({ format: 'jwk' });
const kid = createHash('sha256').update(JSON.stringify({ e, kty: 'RSA', n })).digest('base64url').slice(0, 16);

const b64 = (o) => Buffer.from(JSON.stringify(o)).toString('base64url');
const ahora = Math.floor(Date.now() / 1000);
const cabecera = b64({ alg: 'RS256', typ: 'JWT', kid });
const cuerpo = b64({
  iss: 'jwks-dev',
  sub: usuario,
  preferred_username: usuario,
  realm_access: { roles: [rol] },
  iat: ahora,
  exp: ahora + horas * 3600,
});
const firma = sign('sha256', Buffer.from(`${cabecera}.${cuerpo}`), privada).toString('base64url');
process.stdout.write(`${cabecera}.${cuerpo}.${firma}\n`);
