/**
 * HU-SAL-001 — Vista de creacion de sala.
 *
 * Se prueba el comportamiento que ve la persona: el estado de carga, el exito,
 * y sobre todo que un rechazo por campos marque el campo concreto en vez de
 * soltar un aviso general. El requisito exige indicar el motivo.
 */

// Con modulos ES, Jest NO inyecta `jest` como global: hay que importarlo.
// Misma linea que ya tiene inventario.test.js.
import { jest } from '@jest/globals';

import { montarCrearSala, leerFormulario, tonoPara } from './crear-sala.js';
import { ErrorDeApi } from './cliente-salas.js';

const HTML = `
  <form id="f" novalidate>
    <div data-zona="aviso" hidden></div>

    <div class="campo">
      <label class="campo__etiqueta" for="maximoParticipantes">Participantes</label>
      <input class="campo__control" id="maximoParticipantes" name="maximoParticipantes"
             type="number" value="4" />
    </div>

    <div class="campo">
      <label class="campo__etiqueta" for="recompensaCreditos">Recompensa</label>
      <input class="campo__control" id="recompensaCreditos" name="recompensaCreditos"
             type="number" value="0" />
    </div>

    <input type="radio" name="modalidad" value="UNO_CONTRA_UNO" />
    <input type="radio" name="modalidad" value="HASTA_SEIS" checked />
    <input type="checkbox" name="incluirHeroeIA" />
    <input type="checkbox" name="privada" />

    <button type="submit">CREAR SALA</button>
  </form>
`;

function preparar() {
  document.body.innerHTML = HTML;
  return document.getElementById('f');
}

/** Deja que se resuelvan las promesas encadenadas del manejador de submit. */
const asentar = () => new Promise((resolve) => setTimeout(resolve, 0));

describe('leerFormulario', () => {
  test('arma el cuerpo del contrato con los tipos correctos', () => {
    const formulario = preparar();

    expect(leerFormulario(formulario)).toEqual({
      maximoParticipantes: 4,
      modalidad: 'HASTA_SEIS',
      recompensaCreditos: 0,
      incluirHeroeIA: false,
      privada: false,
      tamanoEquipo: null,
    });
  });

  test('no manda tamanoEquipo en una modalidad que no admite equipos', () => {
    const formulario = preparar();
    formulario.querySelector('[value="UNO_CONTRA_UNO"]').checked = true;

    expect(leerFormulario(formulario).tamanoEquipo).toBeNull();
  });
});

describe('tonoPara', () => {
  test('un fallo del sistema es error; lo corregible es advertencia', () => {
    expect(tonoPara(500)).toBe('error');
    expect(tonoPara(503)).toBe('error');
    expect(tonoPara(422)).toBe('advertencia');
    expect(tonoPara(400)).toBe('advertencia');
    expect(tonoPara(401)).toBe('advertencia');
    expect(tonoPara(404)).toBe('info');
  });
});

describe('montarCrearSala', () => {
  test('al crear la sala muestra un aviso de exito y limpia el formulario', async () => {
    const formulario = preparar();
    const crearSalaImpl = jest
      .fn()
      .mockResolvedValue({ id: 'a1', maximoParticipantes: 4, recompensaCreditos: 0 });
    montarCrearSala(formulario, { crearSalaImpl });

    formulario.dispatchEvent(new Event('submit'));
    await asentar();

    const aviso = document.querySelector('.aviso');
    expect(aviso.className).toContain('aviso--exito');
    expect(aviso.textContent).toContain('4 participantes');
    expect(document.querySelector('[data-zona="aviso"]').hidden).toBe(false);
  });

  test('mientras espera, el boton se bloquea y lo dice', async () => {
    const formulario = preparar();
    let resolver;
    const crearSalaImpl = jest.fn(() => new Promise((r) => (resolver = r)));
    montarCrearSala(formulario, { crearSalaImpl });
    const boton = formulario.querySelector('[type="submit"]');

    formulario.dispatchEvent(new Event('submit'));
    await asentar();

    expect(boton.disabled).toBe(true);
    expect(boton.getAttribute('aria-busy')).toBe('true');
    expect(boton.textContent).toMatch(/creando/i);

    resolver({ id: 'a1', maximoParticipantes: 4, recompensaCreditos: 0 });
    await asentar();

    expect(boton.disabled).toBe(false);
    expect(boton.getAttribute('aria-busy')).toBe('false');
  });

  test('al terminar restaura el literal de la vista, no uno impuesto por el modulo', async () => {
    const formulario = preparar();
    const crearSalaImpl = jest
      .fn()
      .mockResolvedValue({ id: 'a1', maximoParticipantes: 4, recompensaCreditos: 0 });
    montarCrearSala(formulario, { crearSalaImpl });
    const boton = formulario.querySelector('[type="submit"]');

    formulario.dispatchEvent(new Event('submit'));
    await asentar();
    expect(boton.textContent).toBe('CREAR SALA');

    // Y una segunda vez: el texto guardado no se contamina con «Creando…».
    formulario.dispatchEvent(new Event('submit'));
    await asentar();
    expect(boton.textContent).toBe('CREAR SALA');
  });

  test('un rechazo por campos marca el campo, no suelta un aviso general', async () => {
    const formulario = preparar();
    const crearSalaImpl = jest.fn().mockRejectedValue(
      new ErrorDeApi(
        {
          type: 'https://nexusbattles.local/errores/parametros-invalidos',
          title: 'Revisa los datos de la sala',
          status: 400,
          detail: 'Hay 1 campo que corregir.',
          errores: [
            {
              campo: 'maximoParticipantes',
              mensaje: 'Esta modalidad admite entre 2 y 6 jugadores.',
            },
          ],
        },
        400,
      ),
    );
    montarCrearSala(formulario, { crearSalaImpl });

    formulario.dispatchEvent(new Event('submit'));
    await asentar();

    const control = formulario.querySelector('[name="maximoParticipantes"]');
    expect(control.closest('.campo').classList.contains('campo--invalido')).toBe(true);
    expect(control.getAttribute('aria-invalid')).toBe('true');
    expect(formulario.querySelector('.campo__error').textContent).toContain('2 y 6');
    expect(document.querySelector('.aviso')).toBeNull();
    expect(document.activeElement).toBe(control);
  });

  test('los errores de campo anteriores se limpian antes del siguiente intento', async () => {
    const formulario = preparar();
    const conError = new ErrorDeApi(
      { status: 400, errores: [{ campo: 'maximoParticipantes', mensaje: 'Fuera de rango.' }] },
      400,
    );
    const crearSalaImpl = jest
      .fn()
      .mockRejectedValueOnce(conError)
      .mockResolvedValueOnce({ id: 'a1', maximoParticipantes: 4, recompensaCreditos: 0 });
    montarCrearSala(formulario, { crearSalaImpl });

    formulario.dispatchEvent(new Event('submit'));
    await asentar();
    expect(formulario.querySelectorAll('.campo__error')).toHaveLength(1);

    formulario.dispatchEvent(new Event('submit'));
    await asentar();
    expect(formulario.querySelectorAll('.campo__error')).toHaveLength(0);
    expect(formulario.querySelectorAll('.campo--invalido')).toHaveLength(0);
  });

  test('los creditos sin integrar salen como aviso de error, con su motivo', async () => {
    const formulario = preparar();
    const crearSalaImpl = jest.fn().mockRejectedValue(
      new ErrorDeApi(
        {
          type: 'https://nexusbattles.local/errores/creditos-sin-integrar',
          title: 'Las apuestas todavia no estan disponibles',
          status: 503,
          detail: 'Por ahora solo se pueden crear salas sin recompensa.',
        },
        503,
      ),
    );
    montarCrearSala(formulario, { crearSalaImpl });

    formulario.dispatchEvent(new Event('submit'));
    await asentar();

    const aviso = document.querySelector('.aviso');
    expect(aviso.className).toContain('aviso--error');
    expect(aviso.textContent).toContain('apuestas todavia no estan disponibles');
    expect(aviso.textContent).toContain('sin recompensa');
    expect(aviso.getAttribute('role')).toBe('alert');
  });

  test('los creditos insuficientes salen como advertencia diciendo cuanto falta', async () => {
    const formulario = preparar();
    const crearSalaImpl = jest.fn().mockRejectedValue(
      new ErrorDeApi(
        {
          type: 'https://nexusbattles.local/errores/creditos-insuficientes',
          title: 'Creditos insuficientes',
          status: 422,
          detail: 'Tienes 240 creditos y necesitas 400 para crear esta sala.',
        },
        422,
      ),
    );
    montarCrearSala(formulario, { crearSalaImpl });

    formulario.dispatchEvent(new Event('submit'));
    await asentar();

    const aviso = document.querySelector('.aviso');
    expect(aviso.className).toContain('aviso--advertencia');
    expect(aviso.textContent).toContain('240');
    expect(aviso.textContent).toContain('400');
  });

  test('una caida de red no deja a la persona sin mensaje', async () => {
    const formulario = preparar();
    const crearSalaImpl = jest.fn().mockRejectedValue(new TypeError('Failed to fetch'));
    montarCrearSala(formulario, { crearSalaImpl });

    formulario.dispatchEvent(new Event('submit'));
    await asentar();

    const aviso = document.querySelector('.aviso');
    expect(aviso.className).toContain('aviso--error');
    expect(aviso.textContent).toMatch(/conexion/i);
    expect(formulario.querySelector('[type="submit"]').disabled).toBe(false);
  });
});
