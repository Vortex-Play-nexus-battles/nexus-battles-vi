/**
 * TOTP (RFC 6238 sobre RFC 4226) como lo calcula una aplicación de
 * autenticación: HMAC-SHA1, 6 dígitos, pasos de 30 s. Sin dependencias,
 * con `node:crypto`.
 *
 * Lo usan la verificación en dos pasos de una jugadora
 * (`segundo-factor.e2e.spec.js`) y el login de las cuentas administrativas de
 * pruebas, que desde 7.3.9 no entran sin segundo factor (`cuentas.js`).
 *
 * @module tests/e2e/ayudantes/totp
 */

import { createHmac } from 'node:crypto';

const BASE32 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';

/** RFC 4648 sin relleno, como la da el servidor. */
export function bytesDeBase32(texto) {
  const bytes = [];
  let acumulado = 0;
  let bits = 0;
  for (const letra of String(texto).replace(/[\s=]/g, '').toUpperCase()) {
    const valor = BASE32.indexOf(letra);
    if (valor < 0) {
      throw new Error('la clave no es base32');
    }
    acumulado = ((acumulado << 5) | valor) & 0xffff;
    bits += 5;
    if (bits >= 8) {
      bytes.push((acumulado >>> (bits - 8)) & 0xff);
      bits -= 8;
    }
  }
  return Buffer.from(bytes);
}

/** El paso de 30 s de un instante (T0 = 0). */
export function pasoDe(instanteMs = Date.now()) {
  return Math.floor(instanteMs / 1000 / 30);
}

/** El código de 6 dígitos de un paso. */
export function totp(secretoBase32, paso = pasoDe()) {
  const contador = Buffer.alloc(8);
  contador.writeBigUInt64BE(BigInt(paso));
  const resumen = createHmac('sha1', bytesDeBase32(secretoBase32)).update(contador).digest();
  const desplazamiento = resumen[resumen.length - 1] & 0x0f;
  const binario =
    ((resumen[desplazamiento] & 0x7f) << 24) |
    (resumen[desplazamiento + 1] << 16) |
    (resumen[desplazamiento + 2] << 8) |
    resumen[desplazamiento + 3];
  return String(binario % 1_000_000).padStart(6, '0');
}
