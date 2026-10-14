/**
 * Vista de administración de banners — HU-PRD-013 (#854) y SA-18.
 *
 * Lo que fija esta suite: retirar se confirma con el diálogo del kit (no con
 * `window.confirm()`, que `sin-dialogos-nativos.test.js` prohíbe) y con su
 * motivo; las fechas salen del formateador común; y cada banner dice quién lo
 * publicó y tiene su historial (§7.2.5, §7.3.5).
 */

import { jest } from '@jest/globals';

import { fechaHora } from '../../comun/ui/formato.js';
import { autoriaDe, listaDelHistorial, montarBanners } from './banners.js';

const asentar = () => new Promise((resolver) => setTimeout(resolver, 0));

const VIGENTE = {
  id: 'b-1',
  contenido: 'Temporada de héroes',
  publicarDesde: '2026-10-01T15:00:00Z',
  vigenteHasta: '2099-10-31T23:00:00Z',
  retirado: false,
};
const RETIRADO = { ...VIGENTE, id: 'b-2', contenido: 'Anuncio viejo', retirado: true };
const ANA = '8f14e45f-ceea-467f-a0e6-9ea7c2b1e7a1';
const LUIS = '5a0c3e1e-7b8d-4f60-9d8e-2c4b1a6f9e10';

/** El directorio de apodos, sin red. */
const titulares = {
  nombrar: jest.fn().mockResolvedValue(true),
  de: (uid) => ({ [ANA]: { apodo: 'ana_admin' }, [LUIS]: { apodo: 'luis_admin' } })[uid] ?? null,
};

function api(banners = [VIGENTE, RETIRADO]) {
  return {
    listarBanners: jest.fn().mockResolvedValue(banners),
    crearBanner: jest.fn().mockResolvedValue({}),
    editarBanner: jest.fn().mockResolvedValue({}),
    retirarBanner: jest.fn().mockResolvedValue(null),
    historialDeBanner: jest.fn().mockResolvedValue([]),
  };
}

function botonDe(contenido, texto) {
  const tarjeta = [...document.querySelectorAll('.banners__lista article')].find((t) =>
    t.textContent.includes(contenido),
  );
  return [...tarjeta.querySelectorAll('button')].find((b) => b.textContent === texto);
}

function botonRetirarDe(contenido) {
  const tarjeta = [...document.querySelectorAll('.banners__lista article')].find((t) =>
    t.textContent.includes(contenido),
  );
  return [...tarjeta.querySelectorAll('button')].find((b) => b.textContent === 'Retirar');
}

beforeEach(() => {
  document.body.innerHTML = '<main id="banners-app"></main>';
});

test('lista cada banner con su estado y sus fechas en el formato común', async () => {
  montarBanners(document.getElementById('banners-app'), api(), { titulares });
  await asentar();

  const lista = document.querySelector('.banners__lista').textContent;
  expect(lista).toContain('Temporada de héroes');
  expect(lista).toContain('Vigente');
  expect(lista).toContain('Retirado');
  expect(lista).toContain(fechaHora(VIGENTE.publicarDesde));
  expect(lista).toContain(fechaHora(VIGENTE.vigenteHasta));
  expect(botonRetirarDe('Anuncio viejo').disabled).toBe(true);
});

test('retirar pide el motivo con el diálogo del kit y, si se cancela, no toca el servidor', async () => {
  const servicio = api();
  const pedirMotivo = jest.fn().mockResolvedValue(null);
  montarBanners(document.getElementById('banners-app'), servicio, { pedirMotivo, titulares });
  await asentar();

  botonRetirarDe('Temporada de héroes').click();
  await asentar();

  expect(pedirMotivo).toHaveBeenCalledWith(VIGENTE);
  expect(servicio.retirarBanner).not.toHaveBeenCalled();
});

test('al confirmar, retira ese banner con su motivo y vuelve a pedir la lista', async () => {
  const servicio = api();
  montarBanners(document.getElementById('banners-app'), servicio, {
    pedirMotivo: jest.fn().mockResolvedValue('El evento se canceló'),
    titulares,
  });
  await asentar();

  botonRetirarDe('Temporada de héroes').click();
  await asentar();
  await asentar();

  expect(servicio.retirarBanner).toHaveBeenCalledWith('b-1', { motivo: 'El evento se canceló' });
  expect(servicio.listarBanners).toHaveBeenCalledTimes(2);
});

test('el diálogo por omisión es el del kit, con su motivo, no el del navegador', async () => {
  const nativo = jest.fn(() => true);
  window.confirm = nativo;
  montarBanners(document.getElementById('banners-app'), api(), { titulares });
  await asentar();

  botonRetirarDe('Temporada de héroes').click();
  await asentar();

  expect(nativo).not.toHaveBeenCalled();
  const dialogo = document.querySelector('[role="dialog"], [role="alertdialog"]');
  expect(dialogo).not.toBeNull();
  expect(dialogo.textContent).toContain('Retirar');
  expect(dialogo.textContent).toContain('Motivo del retiro');
});

test('un fallo que no es para personas no llega a la pantalla', async () => {
  const servicio = api();
  servicio.listarBanners.mockRejectedValue(
    new SyntaxError('Unexpected token < in JSON at position 0'),
  );
  montarBanners(document.getElementById('banners-app'), servicio, { titulares });
  await asentar();

  const texto = document.getElementById('banners-app').textContent;
  expect(texto).not.toMatch(/Unexpected|JSON/);
  expect(texto).toContain('No se pudieron cargar los banners');
});

describe('SA-18: quién publicó cada banner y su historial', () => {
  test('la tarjeta dice quién lo publicó y quién lo editó, por su apodo', async () => {
    const editado = {
      ...VIGENTE,
      creadoPor: ANA,
      modificadoPor: LUIS,
      creadoEn: '2026-10-01T15:00:00Z',
      modificadoEn: '2026-10-05T16:30:00Z',
    };
    montarBanners(document.getElementById('banners-app'), api([editado]), { titulares });
    await asentar();

    expect(titulares.nombrar).toHaveBeenCalledWith([ANA, LUIS]);
    const autoria = document.querySelector('.banners__autoria').textContent;
    expect(autoria).toBe(
      `Publicado por ana_admin · Última edición por luis_admin el ${fechaHora('2026-10-05T16:30:00Z')}`,
    );
    expect(autoria).not.toContain(ANA);
  });

  test('un banner anterior, sin autores, no inventa ninguno', () => {
    expect(autoriaDe(VIGENTE, titulares)).toBeNull();
    expect(
      autoriaDe(
        { ...VIGENTE, creadoPor: ANA, modificadoPor: ANA, creadoEn: 'x', modificadoEn: 'x' },
        titulares,
      ),
    ).toBe('Publicado por ana_admin');
    expect(autoriaDe({ ...RETIRADO, creadoPor: ANA, modificadoPor: 'otra' }, null)).toMatch(
      /^Publicado por Cuenta sin apodo · Retirado por Cuenta sin apodo el /,
    );
  });

  test('«Historial» lo carga al abrirlo, con cada cambio, su autor y su motivo', async () => {
    const servicio = api([VIGENTE]);
    servicio.historialDeBanner.mockResolvedValue([
      {
        id: 'op-2',
        fecha: '2026-10-06T10:00:00Z',
        autor: LUIS,
        tipo: 'MODIFICACION_BANNER',
        valorAnterior: '«Temporada»',
        valorNuevo: '«Temporada de héroes»',
        descripcion: 'Edición de un banner',
      },
      {
        id: 'op-1',
        fecha: '2026-10-01T15:00:00Z',
        autor: ANA,
        tipo: 'ALTA_BANNER',
        valorNuevo: '«Temporada»',
        descripcion: 'Publicación de un banner',
      },
    ]);
    montarBanners(document.getElementById('banners-app'), servicio, { titulares });
    await asentar();

    const boton = botonDe('Temporada de héroes', 'Historial');
    expect(boton.getAttribute('aria-expanded')).toBe('false');
    boton.click();
    await asentar();
    await asentar();

    expect(servicio.historialDeBanner).toHaveBeenCalledWith('b-1');
    expect(boton.getAttribute('aria-expanded')).toBe('true');
    const panel = document.getElementById(boton.getAttribute('aria-controls'));
    expect(panel.hidden).toBe(false);
    const entradas = [...panel.querySelectorAll('li')].map((li) => li.textContent);
    expect(entradas[0]).toContain('Edición');
    expect(entradas[0]).toContain('luis_admin');
    expect(entradas[0]).toContain('Antes: «Temporada»');
    expect(entradas[1]).toContain('Publicación');
    expect(entradas[1]).not.toContain('Antes:');

    // Cerrar y volver a abrir no lo vuelve a pedir.
    boton.click();
    boton.click();
    await asentar();
    expect(servicio.historialDeBanner).toHaveBeenCalledTimes(1);
  });

  test('un historial vacío lo dice, y uno que no carga se puede reintentar', async () => {
    expect(listaDelHistorial([], titulares).textContent).toBe(
      'Este banner no tiene cambios registrados todavía.',
    );
    const servicio = api([VIGENTE]);
    servicio.historialDeBanner.mockRejectedValueOnce(
      new SyntaxError('Unexpected token < in JSON at position 0'),
    );
    montarBanners(document.getElementById('banners-app'), servicio, { titulares });
    await asentar();

    botonDe('Temporada de héroes', 'Historial').click();
    await asentar();
    const panel = document.querySelector('.banners__historial');
    expect(panel.textContent).toContain('No se pudo cargar el historial del banner.');

    [...panel.querySelectorAll('button')].find((b) => b.textContent === 'Reintentar').click();
    await asentar();
    await asentar();
    expect(panel.textContent).toContain('Este banner no tiene cambios registrados todavía.');
  });

  test('un retiro con motivo lo muestra en el historial', () => {
    const lista = listaDelHistorial(
      [
        {
          id: 'op-3',
          fecha: '2026-10-07T10:00:00Z',
          autor: ANA,
          tipo: 'RETIRO_BANNER',
          valorAnterior: '«x»',
          valorNuevo: '«x» · Retirado',
          motivo: 'El evento se canceló',
        },
      ],
      titulares,
    );
    expect(lista.textContent).toContain('Retiro');
    expect(lista.textContent).toContain('Motivo: El evento se canceló');
    expect(lista.textContent).not.toContain('Antes:');
  });
});
