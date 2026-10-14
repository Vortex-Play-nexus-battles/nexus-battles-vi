/**
 * «Preferencias» dentro de la ventana del asistente (7.4.5).
 */

import { jest } from '@jest/globals';

import { ErrorDelChatbot } from '../cliente-chatbot.js';
import {
  IDIOMAS,
  NIVELES,
  NIVEL_EFECTIVO,
  POR_DEFECTO,
  TEXTOS_PREFERENCIAS,
  avisoDeErrorAlGuardar,
  crearPanelPreferencias,
} from './preferencias-chatbot.js';

const esperar = () => new Promise((resolver) => setTimeout(resolver, 0));

function clienteFalso(sobrescribir = {}) {
  return {
    preferencias: jest.fn(async () => ({ idioma: 'EN', nivelDetalle: 'BREVE' })),
    guardarPreferencias: jest.fn(async (datos) => datos),
    ...sobrescribir,
  };
}

async function montar({ cliente = clienteFalso(), autenticado = true } = {}) {
  const alVolver = jest.fn();
  const panel = crearPanelPreferencias({
    cliente,
    sesion: () => ({ autenticado }),
    alVolver,
  });
  document.body.append(panel.elemento);
  panel.abrir();
  await esperar();
  const el = panel.elemento;
  return {
    panel,
    cliente,
    alVolver,
    el,
    idioma: el.querySelector('select[name="idioma"]'),
    nivel: el.querySelector('select[name="nivelDetalle"]'),
    formulario: el.querySelector('form'),
    aviso: el.querySelector('.chatbot-preferencias__aviso'),
  };
}

beforeEach(() => {
  document.body.replaceChildren();
});

test('ofrece los valores del contrato, con etiquetas', () => {
  expect(IDIOMAS.map((i) => i.valor)).toEqual(['AUTOMATICO', 'ES', 'EN']);
  // 1.15.0 (D-CHA-13): «Automático», el de quien nunca eligió.
  expect(NIVELES.map((n) => n.valor)).toEqual(['AUTOMATICO', 'BREVE', 'NORMAL', 'DETALLADO']);
  expect(POR_DEFECTO).toEqual({ idioma: 'AUTOMATICO', nivelDetalle: 'AUTOMATICO' });
});

test('al abrir carga las guardadas y las muestra', async () => {
  const vista = await montar();

  expect(vista.el.hidden).toBe(false);
  expect(vista.cliente.preferencias).toHaveBeenCalledTimes(1);
  expect(vista.idioma.value).toBe('EN');
  expect(vista.nivel.value).toBe('BREVE');
  expect(vista.idioma.disabled).toBe(false);
  expect(vista.el.querySelector(`label[for="${vista.idioma.id}"]`).textContent).toBe(
    TEXTOS_PREFERENCIAS.idioma,
  );
});

test('un valor que no conoce se muestra como el de por defecto', async () => {
  const vista = await montar({
    cliente: clienteFalso({
      preferencias: jest.fn(async () => ({ idioma: 'FR', nivelDetalle: 'EXTREMO' })),
    }),
  });
  expect(vista.idioma.value).toBe('AUTOMATICO');
  expect(vista.nivel.value).toBe('AUTOMATICO');
});

test('si no se pueden cargar, lo dice y deja elegir con las de por defecto', async () => {
  const vista = await montar({
    cliente: clienteFalso({
      preferencias: jest.fn(async () => {
        throw new ErrorDelChatbot(null, 0);
      }),
    }),
  });

  expect(vista.aviso.hidden).toBe(false);
  expect(vista.aviso.textContent).toContain(TEXTOS_PREFERENCIAS.errorAlCargar);
  expect(vista.idioma.value).toBe('AUTOMATICO');
  expect(vista.idioma.disabled).toBe(false);
});

test('guardar manda lo elegido y confirma, distinto para jugador y visitante', async () => {
  const jugador = await montar();
  jugador.idioma.value = 'ES';
  jugador.nivel.value = 'DETALLADO';
  jugador.formulario.dispatchEvent(new Event('submit', { cancelable: true }));
  await esperar();

  expect(jugador.cliente.guardarPreferencias).toHaveBeenCalledWith({
    idioma: 'ES',
    nivelDetalle: 'DETALLADO',
  });
  expect(jugador.aviso.textContent).toContain(TEXTOS_PREFERENCIAS.guardadas);
  expect(jugador.aviso.textContent).toContain(TEXTOS_PREFERENCIAS.guardadasJugador);

  document.body.replaceChildren();
  const visitante = await montar({ autenticado: false });
  visitante.formulario.dispatchEvent(new Event('submit', { cancelable: true }));
  await esperar();
  expect(visitante.aviso.textContent).toContain(TEXTOS_PREFERENCIAS.guardadasVisitante);
});

test('no manda dos veces si se pulsa mientras guarda', async () => {
  let resolver;
  const guardarPreferencias = jest.fn(
    () =>
      new Promise((r) => {
        resolver = r;
      }),
  );
  const vista = await montar({ cliente: clienteFalso({ guardarPreferencias }) });

  vista.formulario.dispatchEvent(new Event('submit', { cancelable: true }));
  vista.formulario.dispatchEvent(new Event('submit', { cancelable: true }));
  expect(guardarPreferencias).toHaveBeenCalledTimes(1);

  resolver(POR_DEFECTO);
  await esperar();
  expect(vista.aviso.textContent).toContain(TEXTOS_PREFERENCIAS.guardadas);
});

test('un error al guardar se explica por el motivo y el estado', async () => {
  const vista = await montar({
    cliente: clienteFalso({
      guardarPreferencias: jest.fn(async () => {
        throw new ErrorDelChatbot({ motivo: 'LIMITE_DE_FRECUENCIA' }, 429);
      }),
    }),
  });
  vista.formulario.dispatchEvent(new Event('submit', { cancelable: true }));
  await esperar();
  expect(vista.aviso.textContent).toContain(TEXTOS_PREFERENCIAS.muchas);

  expect(avisoDeErrorAlGuardar(new ErrorDelChatbot(null, 503)).titulo).toBe(
    TEXTOS_PREFERENCIAS.noDisponible,
  );
  expect(avisoDeErrorAlGuardar(new ErrorDelChatbot(null, 500)).titulo).toBe(
    TEXTOS_PREFERENCIAS.error,
  );
});

test('«Volver al chat» avisa a la ventana y el foco empieza en el idioma', async () => {
  const vista = await montar();
  vista.panel.enfocar();
  expect(document.activeElement).toBe(vista.idioma);

  vista.el.querySelector('[data-accion="volver-al-chat"]').click();
  expect(vista.alVolver).toHaveBeenCalledTimes(1);
});

// 1.15.0 (7.4.5, D-CHA-13): con «Automático», el nivel con que se le responde hoy.
test('con «Automático» dice con qué nivel se le responde hoy, y solo de lo guardado', async () => {
  const vista = await montar({
    cliente: clienteFalso({
      preferencias: jest.fn(async () => ({
        idioma: 'AUTOMATICO',
        nivelDetalle: 'AUTOMATICO',
        nivelDetalleEfectivo: 'DETALLADO',
      })),
      guardarPreferencias: jest.fn(async (datos) => ({
        ...datos,
        nivelDetalleEfectivo: datos.nivelDetalle === 'AUTOMATICO' ? 'BREVE' : datos.nivelDetalle,
      })),
    }),
  });
  const efectivo = vista.el.querySelector('[data-nivel-efectivo]');

  expect(vista.nivel.value).toBe('AUTOMATICO');
  expect(efectivo.hidden).toBe(false);
  expect(efectivo.textContent).toBe(NIVEL_EFECTIVO.DETALLADO);
  // El lector de pantalla lo oye con el campo.
  expect(vista.nivel.getAttribute('aria-describedby').split(' ')).toContain(efectivo.id);

  // Otro nivel elegido (aún sin guardar): ya no es verdad, se oculta.
  vista.nivel.value = 'NORMAL';
  vista.nivel.dispatchEvent(new Event('change'));
  expect(efectivo.hidden).toBe(true);
  vista.nivel.value = 'AUTOMATICO';
  vista.nivel.dispatchEvent(new Event('change'));
  expect(efectivo.textContent).toBe(NIVEL_EFECTIVO.DETALLADO);

  // Guardado: el que diga el servidor.
  vista.formulario.dispatchEvent(new Event('submit', { cancelable: true }));
  await esperar();
  expect(efectivo.textContent).toBe(NIVEL_EFECTIVO.BREVE);

  vista.nivel.value = 'BREVE';
  vista.formulario.dispatchEvent(new Event('submit', { cancelable: true }));
  await esperar();
  expect(efectivo.hidden).toBe(true);
  vista.nivel.value = 'AUTOMATICO';
  vista.nivel.dispatchEvent(new Event('change'));
  expect(efectivo.hidden).toBe(true);
});

test('un nivel efectivo que no conoce, o que no llega, no se cuenta', async () => {
  const desconocido = await montar({
    cliente: clienteFalso({
      preferencias: jest.fn(async () => ({
        idioma: 'ES',
        nivelDetalle: 'AUTOMATICO',
        nivelDetalleEfectivo: 'constructor',
      })),
    }),
  });
  expect(desconocido.el.querySelector('[data-nivel-efectivo]').hidden).toBe(true);

  document.body.replaceChildren();
  const sinEl = await montar({
    cliente: clienteFalso({
      preferencias: jest.fn(async () => ({ idioma: 'ES', nivelDetalle: 'AUTOMATICO' })),
    }),
  });
  expect(sinEl.nivel.value).toBe('AUTOMATICO');
  expect(sinEl.el.querySelector('[data-nivel-efectivo]').hidden).toBe(true);
});

// RF-CHA-010, flujo alternativo: «restablecer sus preferencias conversacionales».
test('«Restablecer» vuelve a las de quien nunca eligió y las guarda', async () => {
  const vista = await montar();
  expect(vista.idioma.value).toBe('EN');
  const restablecer = vista.el.querySelector('[data-accion="restablecer-preferencias"]');
  expect(restablecer.textContent).toBe(TEXTOS_PREFERENCIAS.restablecer);
  expect(restablecer.type).toBe('button');

  restablecer.click();
  await esperar();

  expect(vista.cliente.guardarPreferencias).toHaveBeenCalledWith(POR_DEFECTO);
  expect(vista.idioma.value).toBe('AUTOMATICO');
  expect(vista.nivel.value).toBe('AUTOMATICO');
  expect(vista.aviso.textContent).toContain(TEXTOS_PREFERENCIAS.guardadas);
});

test('mientras guarda, «Restablecer» no cambia nada ni manda otra vez', async () => {
  let resolver;
  const guardarPreferencias = jest.fn(
    () =>
      new Promise((r) => {
        resolver = r;
      }),
  );
  const vista = await montar({ cliente: clienteFalso({ guardarPreferencias }) });
  vista.nivel.value = 'DETALLADO';
  vista.formulario.dispatchEvent(new Event('submit', { cancelable: true }));

  vista.el.querySelector('[data-accion="restablecer-preferencias"]').click();
  expect(guardarPreferencias).toHaveBeenCalledTimes(1);
  expect(vista.nivel.value).toBe('DETALLADO');

  resolver({ idioma: 'EN', nivelDetalle: 'DETALLADO' });
  await esperar();
  expect(vista.nivel.value).toBe('DETALLADO');
});
