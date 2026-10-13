/**
 * Invitar por apodo — revisión del modo jugador del 6-oct, punto 13.
 *
 * Lo que se fija: solo el anfitrión, con plazas libres; se busca a partir de
 * 3 letras, sin enseñar identificadores; quien ya está dentro no sale; la
 * invitación dice a quién se envió (o que ya la tenía) y un rechazo dice su
 * motivo; Escape cierra y devuelve el foco.
 */

import { jest } from '@jest/globals';

import { montarInvitarJugador, textoDeInvitacion } from './invitar-jugador.js';
import { ErrorDeApi } from './cliente-salas.js';

const SALA = '77777777-7777-7777-7777-777777777777';
const YO = '11111111-1111-1111-1111-111111111111';
const BRUNO = '22222222-2222-2222-2222-222222222222';
const CARLA = '33333333-3333-3333-3333-333333333333';

const HTML = `
  <section>
    <button type="button" data-accion="abrir-invitar" aria-expanded="false">Invitar jugador</button>
    <div data-zona="panel-invitar" hidden></div>
  </section>
`;

const sala = (cambios = {}) => ({
  id: SALA,
  participantes: [YO],
  ocupacion: 1,
  maximoParticipantes: 2,
  ...cambios,
});

const cola = async () => {
  for (let i = 0; i < 10; i += 1) {
    await Promise.resolve();
  }
};

function montar({ esAnfitrion = true, salaInicial = sala(), buscar, invitar } = {}) {
  document.body.innerHTML = HTML;
  const puertos = {
    buscar:
      buscar ??
      jest.fn().mockResolvedValue([
        { uid: BRUNO, apodo: 'Perez_Bro15' },
        { uid: CARLA, apodo: 'Pereira' },
      ]),
    invitar:
      invitar ??
      jest.fn().mockResolvedValue({ idJugador: BRUNO, apodo: 'Perez_Bro15', enviada: true }),
  };
  const vista = montarInvitarJugador(document, {
    sala: salaInicial,
    esAnfitrion,
    yo: YO,
    demora: 0,
    ...puertos,
  });
  return {
    vista,
    abrir: document.querySelector('[data-accion="abrir-invitar"]'),
    panel: document.querySelector('[data-zona="panel-invitar"]'),
    ...puertos,
  };
}

function escribir(panel, texto) {
  const campo = panel.querySelector('input[name="apodo"]');
  campo.value = texto;
  campo.dispatchEvent(new Event('input'));
}

const esperar = () => new Promise((resolver) => setTimeout(resolver, 5));

describe('montarInvitarJugador', () => {
  test('a quien no es anfitrión no se le ofrece', () => {
    const { vista, abrir } = montar({ esAnfitrion: false });
    expect(vista).toBeNull();
    expect(abrir.hidden).toBe(true);
  });

  test('el botón abre el panel con el campo enfocado y lo anuncia (aria-expanded)', () => {
    const { abrir, panel } = montar();
    expect(abrir.hidden).toBe(false);

    abrir.click();

    expect(panel.hidden).toBe(false);
    expect(abrir.getAttribute('aria-expanded')).toBe('true');
    expect(document.activeElement).toBe(panel.querySelector('input[name="apodo"]'));
  });

  test('con menos de 3 letras no se busca', async () => {
    const { abrir, panel, buscar } = montar();
    abrir.click();

    escribir(panel, 'Pe');
    await esperar();

    expect(buscar).not.toHaveBeenCalled();
  });

  test('busca por apodo y lista apodos con «Invitar», nunca identificadores', async () => {
    const { abrir, panel, buscar } = montar();
    abrir.click();

    escribir(panel, 'Per');
    await esperar();
    await cola();

    expect(buscar).toHaveBeenCalledWith('Per', { yo: YO });
    const resultados = [...panel.querySelectorAll('[data-invitar="jugador"]')];
    expect(resultados.map((b) => b.textContent)).toEqual(['Perez_Bro15Invitar', 'PereiraInvitar']);
    expect(panel.textContent).not.toContain(BRUNO);
    expect(resultados[0].getAttribute('aria-label')).toBe('Invitar a Perez_Bro15');
  });

  test('quien ya está dentro no sale en la lista', async () => {
    const { abrir, panel } = montar({ salaInicial: sala({ participantes: [YO, CARLA] }) });
    abrir.click();

    escribir(panel, 'Per');
    await esperar();
    await cola();

    const apodos = [...panel.querySelectorAll('.buscador-jugador__apodo')].map(
      (a) => a.textContent,
    );
    expect(apodos).toEqual(['Perez_Bro15']);
  });

  test('invitar dice a quién se envió y marca el resultado', async () => {
    const { abrir, panel, invitar } = montar();
    abrir.click();
    escribir(panel, 'Per');
    await esperar();
    await cola();

    panel.querySelector('[data-invitar="jugador"]').click();
    await cola();

    expect(invitar).toHaveBeenCalledWith(SALA, BRUNO);
    expect(panel.querySelector('[data-zona="acuse-invitacion"]').textContent).toBe(
      'Invitación enviada a Perez_Bro15.',
    );
    expect(panel.querySelector('[data-invitar="jugador"]').textContent).toContain('Invitado');
  });

  test('si ya la tenía, lo dice sin presentarlo como error', () => {
    expect(textoDeInvitacion({ apodo: 'Perez_Bro15', enviada: false })).toBe(
      'Perez_Bro15 ya tenía tu invitación.',
    );
  });

  test('un rechazo del servicio dice su motivo, en tono de error, y deja reintentar', async () => {
    const invitar = jest.fn().mockRejectedValue(
      new ErrorDeApi(
        {
          type: 'https://nexusbattles.local/errores/invitacion-no-permitida',
          title: 'No se pudo invitar',
          detail: 'La sala está completa.',
          status: 409,
        },
        409,
      ),
    );
    const { abrir, panel } = montar({ invitar });
    abrir.click();
    escribir(panel, 'Per');
    await esperar();
    await cola();

    const boton = panel.querySelector('[data-invitar="jugador"]');
    boton.click();
    await cola();

    const acuse = panel.querySelector('[data-zona="acuse-invitacion"]');
    expect(acuse.textContent).toBe('La sala está completa.');
    expect(acuse.dataset.tono).toBe('error');
    expect(boton.disabled).toBe(false);
  });

  test('si la búsqueda no responde, se dice sin romper nada', async () => {
    const { abrir, panel } = montar({ buscar: jest.fn().mockRejectedValue(new Error('caído')) });
    abrir.click();
    escribir(panel, 'Per');
    await esperar();
    await cola();

    expect(panel.textContent).toContain('No pudimos buscar ahora');
  });

  test('Escape cierra el panel y devuelve el foco al botón', () => {
    const { abrir, panel } = montar();
    abrir.click();

    panel.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));

    expect(panel.hidden).toBe(true);
    expect(abrir.getAttribute('aria-expanded')).toBe('false');
    expect(document.activeElement).toBe(abrir);
  });

  test('sin plazas libres no se ofrece invitar, y vuelve cuando alguien sale', () => {
    const { vista, abrir } = montar({
      salaInicial: sala({ ocupacion: 2, maximoParticipantes: 2 }),
    });
    expect(abrir.hidden).toBe(true);

    vista.actualizar({ ocupacion: { actual: 1, maximo: 2 }, participantes: [YO] });

    expect(abrir.hidden).toBe(false);
  });
});
