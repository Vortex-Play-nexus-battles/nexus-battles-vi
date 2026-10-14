/**
 * Actualización en vivo — SA-2 (informe de revisión, punto 24): dice de
 * cuándo es lo que se ve, se pone al día sola a un ritmo razonable, se para
 * cuando nadie mira y nunca hace dos cargas a la vez.
 */

import { jest } from '@jest/globals';

import { PERIODO_MINIMO_MS, actualizacionPeriodica } from './actualizacion.js';

/** Reloj manual: el tiempo avanza cuando la prueba lo dice. */
function crearReloj() {
  let ahora = 0;
  let siguiente = 1;
  const pendientes = new Map();
  return {
    setTimeout(funcion, ms) {
      const id = siguiente++;
      pendientes.set(id, { funcion, cuando: ahora + ms });
      return id;
    },
    clearTimeout(id) {
      pendientes.delete(id);
    },
    avanzar(ms) {
      ahora += ms;
      for (const [id, { funcion, cuando }] of [...pendientes]) {
        if (cuando <= ahora) {
          pendientes.delete(id);
          funcion();
        }
      }
    },
    pendientes,
  };
}

const vaciar = async () => {
  for (let i = 0; i < 10; i += 1) {
    await Promise.resolve();
  }
};

function montar(opciones = {}) {
  const reloj = crearReloj();
  const documento = new EventTarget();
  documento.visibilityState = 'visible';
  const cargar = opciones.cargar ?? jest.fn(async () => true);
  const vivo = actualizacionPeriodica({
    cargar,
    cadaMs: 30_000,
    nombre: 'la consola',
    documento,
    reloj,
    ahora: () => new Date('2026-10-13T14:32:05'),
    ...opciones,
  });
  document.body.replaceChildren(vivo.elemento);
  return { vivo, cargar, reloj, documento };
}

test('tras cargar bien dice a qué hora, y vuelve a cargar sola al pasar el periodo', async () => {
  const { vivo, cargar, reloj } = montar();

  await vivo.actualizar();
  expect(vivo.elemento.querySelector('[data-zona="marca"]').textContent).toMatch(
    /^Actualizado a las 0?2:32:05/,
  );
  expect(cargar).toHaveBeenCalledTimes(1);

  reloj.avanzar(30_000);
  await vaciar();
  expect(cargar).toHaveBeenCalledTimes(2);
});

test('nunca más a menudo que el mínimo, aunque se pida', async () => {
  const { vivo, cargar, reloj } = montar({ cadaMs: 1_000 });

  await vivo.actualizar();
  reloj.avanzar(PERIODO_MINIMO_MS - 1);
  await vaciar();
  expect(cargar).toHaveBeenCalledTimes(1);
  reloj.avanzar(1);
  await vaciar();
  expect(cargar).toHaveBeenCalledTimes(2);
});

test('si una carga falla lo dice y conserva la hora de lo último bueno', async () => {
  let bien = true;
  const { vivo } = montar({ cargar: jest.fn(async () => bien) });

  await vivo.actualizar();
  bien = false;
  await vivo.actualizar();
  const marca = vivo.elemento.querySelector('[data-zona="marca"]');
  expect(marca.dataset.estado).toBe('fallo');
  expect(marca.textContent).toMatch(/^No se pudo actualizar\. Lo que ves es de las /);
});

test('un rechazo también es «no se pudo», y sin nada bueno antes lo dice sin hora', async () => {
  const { vivo } = montar({
    cargar: jest.fn(async () => {
      throw new Error('caída');
    }),
  });

  await vivo.actualizar();
  expect(vivo.elemento.querySelector('[data-zona="marca"]').textContent).toBe(
    'No se pudo actualizar.',
  );
});

test('nunca dos cargas a la vez: la segunda petición espera a la primera', async () => {
  let soltar;
  const cargar = jest.fn(
    () =>
      new Promise((resolver) => {
        soltar = resolver;
      }),
  );
  const { vivo } = montar({ cargar });

  const primera = vivo.actualizar();
  const segunda = vivo.actualizar();
  expect(cargar).toHaveBeenCalledTimes(1);
  expect(vivo.elemento.querySelector('[data-accion="actualizar"]').disabled).toBe(true);
  expect(vivo.elemento.getAttribute('aria-busy')).toBe('true');
  soltar(true);
  await Promise.all([primera, segunda]);
  expect(vivo.elemento.querySelector('[data-accion="actualizar"]').disabled).toBe(false);
});

test('con la pestaña oculta no se pregunta; al volver, se pone al día', async () => {
  const { vivo, cargar, reloj, documento } = montar();
  await vivo.actualizar();

  documento.visibilityState = 'hidden';
  documento.dispatchEvent(new Event('visibilitychange'));
  reloj.avanzar(120_000);
  await vaciar();
  expect(cargar).toHaveBeenCalledTimes(1);

  documento.visibilityState = 'visible';
  documento.dispatchEvent(new Event('visibilitychange'));
  await vaciar();
  expect(cargar).toHaveBeenCalledTimes(2);
});

test('«Actualizar sola» apagada no programa nada; el botón sigue funcionando', async () => {
  const { vivo, cargar, reloj } = montar({ automatica: false });

  await vivo.actualizar();
  expect(vivo.elemento.querySelector('input[name="actualizacion-automatica"]').checked).toBe(false);
  reloj.avanzar(120_000);
  await vaciar();
  expect(cargar).toHaveBeenCalledTimes(1);

  vivo.elemento.querySelector('[data-accion="actualizar"]').click();
  await vaciar();
  expect(cargar).toHaveBeenCalledTimes(2);
});

test('encender «Actualizar sola» se pone al día en el acto', async () => {
  const { vivo, cargar } = montar({ automatica: false });

  const casilla = vivo.elemento.querySelector('input[name="actualizacion-automatica"]');
  casilla.checked = true;
  casilla.dispatchEvent(new Event('change'));
  await vaciar();
  expect(cargar).toHaveBeenCalledTimes(1);
});

test('detener suelta el temporizador y la escucha de la pestaña', async () => {
  const { vivo, cargar, reloj, documento } = montar();
  await vivo.actualizar();

  vivo.detener();
  reloj.avanzar(120_000);
  documento.dispatchEvent(new Event('visibilitychange'));
  await vaciar();
  expect(cargar).toHaveBeenCalledTimes(1);
  await vivo.actualizar();
  expect(cargar).toHaveBeenCalledTimes(1);
});

test('el botón dice qué actualiza al lector de pantalla', () => {
  const { vivo } = montar();
  expect(vivo.elemento.querySelector('[data-accion="actualizar"]').getAttribute('aria-label')).toBe(
    'Actualizar la consola',
  );
});

test('sin cargar no se monta', () => {
  expect(() => actualizacionPeriodica({})).toThrow(TypeError);
});
