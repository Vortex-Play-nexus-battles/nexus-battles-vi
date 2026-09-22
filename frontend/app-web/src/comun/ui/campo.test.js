/**
 * Pruebas de `campo.js`. Lo que se comprueba no es que el HTML salga bonito,
 * sino lo que un lector de pantalla necesita para que el formulario sea
 * usable: etiqueta asociada, motivo del rechazo anunciado y estado del
 * interruptor de contraseña dicho en voz alta.
 */

import { campo, mejorarContrasena, marcarErrorDe, marcarErroresDeCampo } from './campo.js';

describe('campo()', () => {
  test('asocia la etiqueta al control por for/id, no por proximidad', () => {
    const { elemento, control } = campo({ nombre: 'apodo', etiqueta: 'Apodo' });

    const etiqueta = elemento.querySelector('label');
    expect(etiqueta.getAttribute('for')).toBe(control.id);
    expect(control.id).not.toBe('');
  });

  test('dos campos con el mismo nombre no comparten id', () => {
    const uno = campo({ nombre: 'apodo', etiqueta: 'Apodo' });
    const otro = campo({ nombre: 'apodo', etiqueta: 'Apodo' });

    expect(uno.control.id).not.toBe(otro.control.id);
  });

  test('marcarError dice el motivo y lo enlaza con aria-describedby', () => {
    const { elemento, control, marcarError } = campo({ nombre: 'creditos', etiqueta: 'Apuesta' });

    marcarError('La apuesta no puede superar tu saldo.');

    const error = elemento.querySelector('.campo__error');
    expect(error.textContent).toBe('La apuesta no puede superar tu saldo.');
    expect(error.hidden).toBe(false);
    expect(control.getAttribute('aria-invalid')).toBe('true');
    expect(control.getAttribute('aria-describedby')).toBe(error.id);
    expect(elemento.classList.contains('campo--invalido')).toBe(true);
  });

  test('marcarError(null) deja el campo como estaba', () => {
    const { elemento, control, marcarError } = campo({ nombre: 'creditos', etiqueta: 'Apuesta' });

    marcarError('mal');
    marcarError(null);

    expect(elemento.querySelector('.campo__error').hidden).toBe(true);
    expect(control.getAttribute('aria-invalid')).toBe('false');
    expect(control.hasAttribute('aria-describedby')).toBe(false);
  });

  test('con pista, el error se suma a la pista en aria-describedby y no la reemplaza', () => {
    const { elemento, control, marcarError } = campo({
      nombre: 'password',
      etiqueta: 'Contrasena',
      tipo: 'text',
      pista: 'Al menos 9 caracteres.',
    });

    const pista = elemento.querySelector('.campo__pista');
    expect(control.getAttribute('aria-describedby')).toBe(pista.id);

    marcarError('Muy corta.');

    const descritoPor = control.getAttribute('aria-describedby').split(' ');
    expect(descritoPor).toContain(pista.id);
    expect(descritoPor).toContain(elemento.querySelector('.campo__error').id);
  });

  test('un campo de contrasena trae interruptor que cambia el tipo y anuncia su estado', () => {
    const { elemento, control } = campo({
      nombre: 'password',
      etiqueta: 'Contrasena',
      tipo: 'password',
    });
    const interruptor = elemento.querySelector('[data-accion="ver-password"]');

    expect(control.getAttribute('type')).toBe('password');
    expect(interruptor.getAttribute('aria-pressed')).toBe('false');
    expect(interruptor.getAttribute('aria-controls')).toBe(control.id);

    interruptor.click();

    expect(control.getAttribute('type')).toBe('text');
    expect(interruptor.getAttribute('aria-pressed')).toBe('true');
    expect(interruptor.textContent).toBe('Ocultar');

    interruptor.click();

    expect(control.getAttribute('type')).toBe('password');
    expect(interruptor.textContent).toBe('Ver');
  });

  test('con opciones construye un desplegable y respeta el valor inicial', () => {
    const { control } = campo({
      nombre: 'modalidad',
      etiqueta: 'Modalidad',
      valor: 'CONTRA_IA',
      opciones: [
        { valor: 'UNO_VS_UNO', texto: '1 contra 1' },
        { valor: 'CONTRA_IA', texto: 'Contra la maquina' },
      ],
    });

    expect(control.tagName).toBe('SELECT');
    expect(control.value).toBe('CONTRA_IA');
    expect(control.querySelectorAll('option')).toHaveLength(2);
  });

  test('multilinea construye un textarea con el valor dentro', () => {
    const { control } = campo({
      nombre: 'motivo',
      etiqueta: 'Motivo',
      multilinea: true,
      valor: 'Lenguaje ofensivo',
    });

    expect(control.tagName).toBe('TEXTAREA');
    expect(control.value).toBe('Lenguaje ofensivo');
  });
});

describe('mejorarContrasena()', () => {
  test('envuelve un input del HTML y le pone el mismo interruptor', () => {
    document.body.innerHTML = `
      <div class="campo">
        <label for="password">Contrasena</label>
        <input id="password" type="password" />
      </div>`;
    const control = document.getElementById('password');

    const interruptor = mejorarContrasena(control);

    expect(interruptor).not.toBeNull();
    expect(control.closest('.campo__con-accion')).not.toBeNull();

    interruptor.click();
    expect(control.getAttribute('type')).toBe('text');
  });

  test('no duplica el interruptor si ya lo tiene', () => {
    document.body.innerHTML = '<div class="campo"><input id="password" type="password" /></div>';
    const control = document.getElementById('password');

    mejorarContrasena(control);
    const segundo = mejorarContrasena(control);

    expect(segundo).toBeNull();
    expect(document.querySelectorAll('[data-accion="ver-password"]')).toHaveLength(1);
  });

  test('no toca un campo que no es de contrasena, ni falla sin campo', () => {
    document.body.innerHTML = '<input id="email" type="email" />';

    expect(mejorarContrasena(document.getElementById('email'))).toBeNull();
    expect(mejorarContrasena(null)).toBeNull();
  });
});

describe('marcarErrorDe()', () => {
  test('pone el motivo debajo de un campo escrito en el HTML', () => {
    document.body.innerHTML = `
      <div class="campo">
        <label for="email">Correo</label>
        <input id="email" type="email" />
      </div>`;
    const control = document.getElementById('email');

    marcarErrorDe(control, 'Ese correo ya tiene cuenta.');

    const error = document.querySelector('.campo__error');
    expect(error.textContent).toBe('Ese correo ya tiene cuenta.');
    expect(control.getAttribute('aria-invalid')).toBe('true');
    expect(control.getAttribute('aria-describedby')).toBe(error.id);
  });

  test('reutiliza el mismo parrafo de error en vez de apilar uno por intento', () => {
    document.body.innerHTML = '<div class="campo"><input id="email" /></div>';
    const control = document.getElementById('email');

    marcarErrorDe(control, 'uno');
    marcarErrorDe(control, 'dos');

    expect(document.querySelectorAll('.campo__error')).toHaveLength(1);
    expect(document.querySelector('.campo__error').textContent).toBe('dos');
  });

  test('sin control no revienta', () => {
    expect(() => marcarErrorDe(null, 'algo')).not.toThrow();
  });
});

describe('marcarErroresDeCampo()', () => {
  test('reparte los errores del problem detail y devuelve el primero para enfocarlo', () => {
    const apodo = campo({ nombre: 'apodo', etiqueta: 'Apodo' });
    const email = campo({ nombre: 'email', etiqueta: 'Correo' });
    const campos = new Map([
      ['apodo', apodo],
      ['email', email],
    ]);

    const primero = marcarErroresDeCampo(campos, [
      { campo: 'email', mensaje: 'Ya registrado.' },
      { campo: 'apodo', mensaje: 'Contiene un termino prohibido.' },
    ]);

    expect(primero).toBe(email.control);
    expect(apodo.elemento.querySelector('.campo__error').textContent).toBe(
      'Contiene un termino prohibido.',
    );
  });

  test('limpia los errores previos antes de repartir los nuevos', () => {
    const apodo = campo({ nombre: 'apodo', etiqueta: 'Apodo' });
    const campos = new Map([['apodo', apodo]]);

    marcarErroresDeCampo(campos, [{ campo: 'apodo', mensaje: 'mal' }]);
    marcarErroresDeCampo(campos, []);

    expect(apodo.elemento.querySelector('.campo__error').hidden).toBe(true);
  });

  test('un error de un campo que la vista no pinta no rompe ni se pierde el resto', () => {
    const apodo = campo({ nombre: 'apodo', etiqueta: 'Apodo' });
    const campos = new Map([['apodo', apodo]]);

    const primero = marcarErroresDeCampo(campos, [
      { campo: 'campoQueNoExiste', mensaje: 'x' },
      { campo: 'apodo', mensaje: 'mal' },
    ]);

    expect(primero).toBe(apodo.control);
  });

  test('sin errores no marca nada', () => {
    const apodo = campo({ nombre: 'apodo', etiqueta: 'Apodo' });

    expect(marcarErroresDeCampo(new Map([['apodo', apodo]]), undefined)).toBeNull();
  });
});
