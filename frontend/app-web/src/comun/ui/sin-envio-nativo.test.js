/**
 * Guardián: ningún formulario con credenciales se puede enviar de forma
 * nativa — G1 (4-oct).
 *
 * El 4-oct la regresión del feedback (#844) vio en AWS DEV que el formulario
 * de entrada, sin `method`, se enviaba por GET antes de que cargara su guion:
 * `/login?email=…&password=…`. La contraseña acababa en la barra, en el
 * historial, en la bitácora del borde y en el `Referer` de la página
 * siguiente. Este guardián impide que vuelva, en cualquier vista:
 *
 *   1. todo formulario con un campo de contraseña, de código o de correo de
 *      una vista de cuenta lleva `method="post"`;
 *   2. sus botones de envío nacen `disabled` con `data-espera-guion`;
 *   3. el guion de la vista (el `.js` con su mismo nombre, o uno que importe)
 *      los enciende con `formularioListo()` de `comun/ui/formulario-seguro.js`.
 *
 * Qué es «sensible» lo decide el `name` de los campos: `type="password"`, un
 * nombre con `password`, `email`, `codigo` (el del correo; la invitación a
 * una sala privada es `codigoInvitacion` y viaja en la dirección a propósito),
 * las respuestas y las preguntas de seguridad.
 */

import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { basename, dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const SRC = join(dirname(fileURLToPath(import.meta.url)), '..', '..');

function vistas(raiz) {
  const salida = [];
  for (const entrada of readdirSync(raiz)) {
    const completa = join(raiz, entrada);
    if (statSync(completa).isDirectory()) {
      salida.push(...vistas(completa));
    } else if (entrada.endsWith('.html')) {
      salida.push(completa);
    }
  }
  return salida;
}

/** Quita comentarios de línea y de bloque: la documentación no cuenta. */
function sinComentarios(texto) {
  return texto.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:])\/\/.*$/gm, '$1');
}

const NOMBRE_SENSIBLE = /password|contrasena|^(?:email|codigo)$|^respuesta|^preguntaSeguridad/i;

/** @param {HTMLFormElement} formulario */
function esSensible(formulario) {
  return Array.from(formulario.querySelectorAll('input, textarea')).some(
    (campo) =>
      campo.getAttribute('type') === 'password' ||
      NOMBRE_SENSIBLE.test(campo.getAttribute('name') ?? ''),
  );
}

/** @param {HTMLFormElement} formulario */
function botonesDeEnvio(formulario) {
  return Array.from(formulario.querySelectorAll('button, input[type="submit"]')).filter(
    (control) =>
      control.tagName === 'INPUT' || (control.getAttribute('type') ?? 'submit') === 'submit',
  );
}

/** @param {HTMLFormElement} formulario */
function nombreDe(formulario) {
  return (
    formulario.id ||
    formulario.dataset.zona ||
    formulario.closest('[data-zona]')?.getAttribute('data-zona') ||
    '(sin id)'
  );
}

/**
 * ¿Este guion, o uno que importe por ruta relativa, LLAMA a formularioListo?
 * La definición (`function formularioListo(`) no cuenta: si no, cualquier
 * vista que solo la importe pasaría por la del propio módulo.
 */
function enciendeBotones(rutaJs, profundidad = 1) {
  if (!existsSync(rutaJs) || basename(rutaJs) === 'formulario-seguro.js') {
    return false;
  }
  const texto = sinComentarios(readFileSync(rutaJs, 'utf8'));
  if (/(?<!function\s+)\bformularioListo\(/.test(texto)) {
    return true;
  }
  if (profundidad === 0) {
    return false;
  }
  for (const [, ruta] of texto.matchAll(/^import[^'"]*['"](\.{1,2}\/[^'"]+)['"]/gm)) {
    if (enciendeBotones(join(dirname(rutaJs), ruta), profundidad - 1)) {
      return true;
    }
  }
  return false;
}

const INVENTARIO = vistas(SRC).flatMap((ruta) => {
  const documento = new DOMParser().parseFromString(readFileSync(ruta, 'utf8'), 'text/html');
  return Array.from(documento.querySelectorAll('form'))
    .filter(esSensible)
    .map((formulario) => ({
      vista: relative(SRC, ruta).replace(/\\/g, '/'),
      guion: join(dirname(ruta), `${basename(ruta, '.html')}.js`),
      formulario,
    }));
});

test('el inventario encuentra los formularios de cuenta (si no, el guardián no mira nada)', () => {
  const nombres = INVENTARIO.map(({ vista, formulario }) => `${vista}#${nombreDe(formulario)}`);
  expect(nombres).toEqual(
    expect.arrayContaining([
      'cuentas/login.html#formLogin',
      'cuentas/registro.html#formRegistro',
      'cuentas/restablecer-solicitar.html#formSolicitud',
      'cuentas/restablecer-confirmar.html#formCodigo',
      'cuentas/restablecer-confirmar.html#formClave',
      'cuentas/verificar-cuenta.html#formVerificacion',
      'cuentas/crear-cuenta-admin.html#form-crear-cuenta',
      'cuentas/perfil.html#cambiar-password',
      'cuentas/perfil.html#formulario-preguntas',
    ]),
  );
});

describe.each(INVENTARIO.map((item) => [`${item.vista}#${nombreDe(item.formulario)}`, item]))(
  '%s',
  (_nombre, { formulario, guion }) => {
    test('se envía por POST: un envío nativo nunca pone los campos en la dirección', () => {
      expect((formulario.getAttribute('method') ?? '').toLowerCase()).toBe('post');
    });

    test('sus botones de envío nacen apagados hasta que llega el guion', () => {
      const botones = botonesDeEnvio(formulario);
      expect(botones.length).toBeGreaterThan(0);
      for (const boton of botones) {
        expect(boton.hasAttribute('disabled')).toBe(true);
        expect(boton.hasAttribute('data-espera-guion')).toBe(true);
      }
    });

    test('el guion de la vista los enciende con formularioListo()', () => {
      expect(enciendeBotones(guion)).toBe(true);
    });
  },
);
