#!/usr/bin/env node
// Genera el par de claves RSA del JWKS de desarrollo de productos.
//
//   node generar-claves.mjs <ruta-de-la-clave-privada.pem>
//
// Deja la clave PRIVADA en esa ruta (permisos 600; NUNCA dentro del
// repositorio) y jwks.json al lado; imprime el JWKS publico por stdout, que es
// lo que se pega en docker-compose.contenido.yml (servicio jwks-dev). Quien
// tenga la clave privada puede emitir tokens de ADMINISTRADOR para productos
// en el entorno de desarrollo: es una herramienta de pruebas, no reemplaza al
// Keycloak de cuentas.
import { generateKeyPairSync, createHash } from 'node:crypto';
import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

const ruta = process.argv[2];
if (!ruta) {
  console.error('uso: node generar-claves.mjs <ruta-de-la-clave-privada.pem>');
  process.exit(2);
}

const { publicKey, privateKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
const { n, e } = publicKey.export({ format: 'jwk' });
// kid = huella RFC 7638 de la clave publica, recortada: estable y sin secretos.
const kid = createHash('sha256').update(JSON.stringify({ e, kty: 'RSA', n })).digest('base64url').slice(0, 16);
const jwks = { keys: [{ kty: 'RSA', use: 'sig', alg: 'RS256', kid, n, e }] };

writeFileSync(ruta, privateKey.export({ type: 'pkcs8', format: 'pem' }), { mode: 0o600 });
writeFileSync(join(dirname(ruta), 'jwks.json'), JSON.stringify(jwks, null, 2) + '\n');
process.stdout.write(JSON.stringify(jwks, null, 2) + '\n');
