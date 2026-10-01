import { jest } from '@jest/globals';
import {
  crearVistaAlertasCatalogo,
  mostrarAlertasCatalogoAlIniciarSesion,
} from './alertas-catalogo.js';

const ALERTA = {
  id: 'alerta-1',
  productoId: 'producto-1',
  productoNombre: 'Espada solar',
  tipo: 'CAMBIO_BALANCE',
  descripcion: 'Se actualizo el balance de Espada solar.',
  implementadaEn: '2026-09-11T15:30:00Z',
};

beforeEach(() => {
  document.body.innerHTML = '';
});

test('muestra el producto, la descripcion y la fecha de implementacion', () => {
  const vista = crearVistaAlertasCatalogo([ALERTA]);

  expect(vista.querySelector('h3').textContent).toBe('Espada solar');
  expect(vista.textContent).toContain('Se actualizo el balance de Espada solar.');
  expect(vista.querySelector('time').dateTime).toBe('2026-09-11T15:30:00.000Z');
  expect(vista.querySelector('time').textContent).toContain('Implementado el');
});

test('no crea un dialogo cuando el jugador no tiene cambios pendientes', async () => {
  const consultar = jest.fn().mockResolvedValue([]);

  const mostrado = await mostrarAlertasCatalogoAlIniciarSesion({ consultar });

  expect(mostrado).toBe(false);
  expect(document.querySelector('.velo')).toBeNull();
});

test('permite continuar despues de revisar las alertas', async () => {
  const consultar = jest.fn().mockResolvedValue([ALERTA]);
  const resultado = mostrarAlertasCatalogoAlIniciarSesion({ consultar });

  await Promise.resolve();
  await Promise.resolve();
  document.querySelector('[data-accion="continuar"]').click();

  await expect(resultado).resolves.toBe(true);
  expect(document.querySelector('.velo')).toBeNull();
});

test('un fallo al consultar no bloquea el inicio de sesion', async () => {
  const consultar = jest.fn().mockRejectedValue(new Error('Servicio no disponible'));

  await expect(mostrarAlertasCatalogoAlIniciarSesion({ consultar })).resolves.toBe(false);
});
