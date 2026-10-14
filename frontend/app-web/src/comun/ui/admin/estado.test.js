/**
 * El estado dicho con palabras — SA-2: texto de persona, icono y tono; nunca
 * la constante del contrato ni el color solo.
 */

import {
  ESTADOS_DE_CUENTA_ADMIN,
  ESTADOS_DE_PAGO,
  ESTADOS_DE_SALA,
  ESTADOS_DE_SERVICIO,
  ESTADOS_DE_TORNEO,
  distintivoDeEstado,
  estadoSegun,
  humanizar,
} from './estado.js';

test('cada estado de servicio tiene texto y tono; el que no está roto no va en rojo', () => {
  expect(ESTADOS_DE_SERVICIO.CAIDO).toEqual({ texto: 'Caído', tono: 'error' });
  expect(ESTADOS_DE_SERVICIO.LENTO.tono).toBe('advertencia');
  expect(ESTADOS_DE_SERVICIO.NO_OBSERVABLE.tono).toBe('neutro');
  expect(ESTADOS_DE_SERVICIO.NO_DESPLEGADO.texto).toBe('Fuera de este entorno');
});

test('las cuentas en femenino, las salas y los torneos con su nombre', () => {
  expect(ESTADOS_DE_CUENTA_ADMIN.SUSPENDIDO.texto).toBe('Suspendida');
  expect(ESTADOS_DE_CUENTA_ADMIN.BLOQUEADA.tono).toBe('bloqueado');
  expect(ESTADOS_DE_SALA.EN_JUEGO.texto).toBe('En juego');
  expect(ESTADOS_DE_TORNEO.INSCRIPCIONES_ABIERTAS.texto).toBe('Inscripciones abiertas');
});

test('SA-15: el resultado de un pago en palabras; el que no se confirmó no va en verde', () => {
  expect(ESTADOS_DE_PAGO.APROBADO).toEqual({ texto: 'Aprobado', tono: 'exito' });
  expect(ESTADOS_DE_PAGO.RECHAZADO.tono).toBe('error');
  expect(ESTADOS_DE_PAGO.INDETERMINADO).toEqual({ texto: 'Sin confirmar', tono: 'advertencia' });
});

test('el distintivo lleva icono del sprite (o un punto) y su texto', () => {
  const caido = distintivoDeEstado({ texto: 'Caído', tono: 'error' });
  expect(caido.className).toBe('estado-admin');
  expect(caido.dataset.tono).toBe('error');
  expect(caido.querySelector('svg.estado-admin__icono').getAttribute('aria-hidden')).toBe('true');
  expect(caido.textContent).toBe('Caído');

  const neutro = distintivoDeEstado({ texto: 'Fuera de este entorno' });
  expect(neutro.dataset.tono).toBe('neutro');
  expect(neutro.querySelector('.estado-admin__punto')).not.toBeNull();
});

test('un tono desconocido cae en neutro', () => {
  expect(distintivoDeEstado({ texto: 'x', tono: 'fucsia' }).dataset.tono).toBe('neutro');
});

test('estadoSegun usa el mapa y humaniza lo que no conoce, guardando el código', () => {
  const conocido = estadoSegun('OPERATIVO', ESTADOS_DE_SERVICIO);
  expect(conocido.textContent).toBe('Operativo');
  expect(conocido.dataset.estado).toBe('OPERATIVO');

  const nuevo = estadoSegun('EN_MANTENIMIENTO', ESTADOS_DE_SERVICIO);
  expect(nuevo.textContent).toBe('En mantenimiento');
  expect(nuevo.dataset.tono).toBe('neutro');
});

test('humanizar', () => {
  expect(humanizar('NO_DESPLEGADO')).toBe('No desplegado');
  expect(humanizar(null)).toBe('Sin dato');
  expect(humanizar('  ')).toBe('Sin dato');
});
