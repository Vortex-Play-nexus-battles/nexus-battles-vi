/**
 * «Preferencias» dentro de la ventana del asistente (7.4.5).
 */

import { jest } from '@jest/globals';

import { ErrorDelChatbot } from '../cliente-chatbot.js';
import {
  IDIOMAS,
  NIVELES,
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
  expect(NIVELES.map((n) => n.valor)).toEqual(['BREVE', 'NORMAL', 'DETALLADO']);
  expect(POR_DEFECTO).toEqual({ idioma: 'AUTOMATICO', nivelDetalle: 'NORMAL' });
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
  expect(vista.nivel.value).toBe('NORMAL');
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
