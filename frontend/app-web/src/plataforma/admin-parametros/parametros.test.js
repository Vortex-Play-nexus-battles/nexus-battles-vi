/** Parametros del sistema — HU-ADM-001: presentación pura y montaje contra un servicio simulado. */

import { jest } from '@jest/globals';

import { cambioDesde, listaDeHistorial, montarParametros, reglaDe, valorDe } from './parametros.js';

const asentar = () => new Promise((resolve) => setTimeout(resolve, 0));

const parametro = (extra = {}) => ({
  clave: 'sanciones.suspension.maxima-dias',
  descripcion: 'Duracion maxima de una suspension temporal',
  tipo: 'ENTERO',
  valor: '30',
  unidad: 'dias',
  minimo: 1,
  maximo: 365,
  opciones: null,
  inalterable: false,
  origen: 'D-20 / HU-USR-005',
  version: 1,
  actualizadoPor: null,
  actualizadoEn: null,
  valorProgramado: null,
  vigenteDesde: null,
  ...extra,
});

function servicio(rutas) {
  return jest.fn(async (url, opciones = {}) => {
    const metodo = opciones.method ?? 'GET';
    const clave = `${metodo} ${new URL(url, 'http://x').pathname}`;
    const respuesta = rutas[clave];
    if (!respuesta) {
      throw new Error(`sin ruta simulada para ${clave}`);
    }
    const { estado = 200, cuerpo = null } =
      typeof respuesta === 'function' ? respuesta(opciones) : respuesta;
    return { ok: estado >= 200 && estado < 300, status: estado, json: async () => cuerpo };
  });
}

describe('presentacion', () => {
  test('reglaDe: rango, opciones, tipo suelto e inalterable', () => {
    expect(reglaDe(parametro())).toBe('entero, min 1, max 365');
    expect(reglaDe(parametro({ opciones: ['LIBERAR', 'CONSUMIR'], tipo: 'TEXTO' }))).toBe(
      'LIBERAR | CONSUMIR',
    );
    expect(reglaDe(parametro({ minimo: null, maximo: null, tipo: 'BOOLEANO' }))).toBe('booleano');
    expect(reglaDe(parametro({ inalterable: true, origen: 'Charter' }))).toBe(
      'Inalterable (Charter)',
    );
  });

  test('valorDe con unidad y sin definir', () => {
    expect(valorDe(parametro())).toBe('30 dias');
    expect(valorDe(parametro({ valor: null }))).toBe('sin definir (pendiente del PO)');
    expect(valorDe(parametro({ unidad: null, valor: 'LIBERAR' }))).toBe('LIBERAR');
  });

  test('cambioDesde: vacio es null, la vigencia solo si se dio', () => {
    expect(cambioDesde({ valor: ' 15 ', motivo: ' Sprint Review ' })).toEqual({
      valor: '15',
      motivo: 'Sprint Review',
    });
    expect(cambioDesde({ valor: '', motivo: 'sin limite' })).toEqual({
      valor: null,
      motivo: 'sin limite',
    });
    const conFecha = cambioDesde({ valor: '7', motivo: 'x', vigenteDesde: '2026-12-01T10:00' });
    expect(conFecha.vigenteDesde).toMatch(/^2026-12-01T/);
  });

  test('listaDeHistorial marca la vigencia futura y el catalogo sin cambios', () => {
    expect(listaDeHistorial([]).textContent).toMatch(/Sin cambios/);
    const ul = listaDeHistorial([
      {
        version: 2,
        valorAnterior: '30',
        valorNuevo: '15',
        motivo: 'ajuste',
        cambiadoEn: '2026-10-01T10:00:00Z',
        vigenteDesde: '2026-10-01T10:00:00Z',
      },
      {
        version: 3,
        valorAnterior: '15',
        valorNuevo: null,
        motivo: 'sin definir',
        cambiadoEn: '2026-10-01T11:00:00Z',
        vigenteDesde: '2026-10-05T00:00:00Z',
      },
    ]);
    const items = [...ul.querySelectorAll('li')].map((li) => li.textContent);
    expect(items[0]).toMatch(/v2 · 30 → 15 · ajuste/);
    expect(items[0]).not.toMatch(/vigente desde/);
    expect(items[1]).toMatch(/15 → sin definir/);
    expect(items[1]).toMatch(/vigente desde/);
  });
});

const VISTA = `<div data-zona="aviso" hidden></div><section data-zona="catalogo"></section>`;

describe('vista', () => {
  beforeEach(() => {
    document.body.innerHTML = VISTA;
  });

  test('un jugador ve el catalogo en solo lectura; el inalterable esta bloqueado para todos', async () => {
    const fetchImpl = servicio({
      'GET /api/v1/parametros': {
        cuerpo: [
          parametro(),
          parametro({
            clave: 'torneos.cupos',
            inalterable: true,
            valor: '8',
            unidad: 'equipos',
            minimo: null,
            maximo: null,
            origen: 'Charter',
          }),
        ],
      },
    });
    montarParametros(document, { rol: 'JUGADOR', fetchImpl });
    await asentar();
    expect(document.querySelector('.aviso--info').textContent).toMatch(/Solo lectura/);
    expect(document.querySelectorAll('[data-zona="catalogo"] article')).toHaveLength(2);
    expect(document.querySelector('[data-zona="cambio"]')).toBeNull();
    expect(
      document.querySelector('[data-clave="torneos.cupos"] [data-campo="bloqueado"]').textContent,
    ).toMatch(/Charter/);
  });

  test('el administrador cambia un valor con motivo, ve el historial y programa una vigencia', async () => {
    let actual = parametro();
    const fetchImpl = servicio({
      'GET /api/v1/parametros': () => ({ cuerpo: [actual] }),
      'PUT /api/v1/parametros/sanciones.suspension.maxima-dias': (opciones) => {
        const cambio = JSON.parse(opciones.body);
        if (cambio.valor === '999') {
          return {
            estado: 400,
            cuerpo: {
              title: 'Valor fuera de rango o de tipo distinto',
              detail: 'no puede ser mayor que 365',
              motivo: 'VALOR_INVALIDO',
            },
          };
        }
        actual = parametro({
          valor: cambio.vigenteDesde ? actual.valor : cambio.valor,
          version: actual.version + 1,
          valorProgramado: cambio.vigenteDesde ? cambio.valor : null,
          vigenteDesde: cambio.vigenteDesde ?? null,
        });
        return { cuerpo: actual };
      },
      'GET /api/v1/parametros/sanciones.suspension.maxima-dias/historial': {
        cuerpo: [
          {
            version: 2,
            valorAnterior: '30',
            valorNuevo: '15',
            motivo: 'Sprint Review',
            cambiadoEn: '2026-10-01T10:00:00Z',
            vigenteDesde: '2026-10-01T10:00:00Z',
          },
        ],
      },
    });
    montarParametros(document, { rol: 'ADMINISTRADOR', fetchImpl });
    await asentar();
    let form = document.querySelector('[data-zona="cambio"]');
    expect(form).not.toBeNull();

    form.querySelector('[name="valor"]').value = '999';
    form.querySelector('[name="motivo"]').value = 'a ver';
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await asentar();
    await asentar();
    expect(document.querySelector('.aviso--advertencia .aviso__titulo').textContent).toMatch(
      /fuera de rango/,
    );

    form.querySelector('[name="valor"]').value = '15';
    form.querySelector('[name="motivo"]').value = 'Sprint Review';
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await asentar();
    await asentar();
    await asentar();
    const llamada = fetchImpl.mock.calls.find(
      (c) => c[1]?.method === 'PUT' && String(c[1].body).includes('"15"'),
    );
    expect(JSON.parse(llamada[1].body)).toEqual({ valor: '15', motivo: 'Sprint Review' });
    expect(document.querySelector('.aviso--exito').textContent).toMatch(/Parametro actualizado/);
    expect(document.querySelector('[data-campo="valor"]').textContent).toBe(
      'Vigente: 15 dias · v2',
    );

    document.querySelector('[data-accion="historial"]').click();
    await asentar();
    await asentar();
    expect(document.querySelector('[data-zona="historial"]').textContent).toMatch(/v2 · 30 → 15/);

    form = document.querySelector('[data-zona="cambio"]');
    form.querySelector('[name="valor"]').value = '7';
    form.querySelector('[name="motivo"]').value = 'Desde diciembre';
    form.querySelector('[name="vigenteDesde"]').value = '2026-12-01T10:00';
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await asentar();
    await asentar();
    await asentar();
    expect(document.querySelector('.aviso--exito .aviso__titulo').textContent).toBe(
      'Cambio programado',
    );
    expect(document.querySelector('[data-campo="programado"]').textContent).toMatch(
      /Programado: 7/,
    );
    expect(document.querySelector('[data-campo="valor"]').textContent).toMatch(
      /Vigente: 15 dias · v3/,
    );
  });

  test('un parametro con opciones se edita con un select preseleccionado', async () => {
    const fetchImpl = servicio({
      'GET /api/v1/parametros': {
        cuerpo: [
          parametro({
            clave: 'salas.apuestas.si-gana-la-maquina',
            tipo: 'TEXTO',
            valor: 'LIBERAR',
            unidad: null,
            minimo: null,
            maximo: null,
            opciones: ['LIBERAR', 'CONSUMIR'],
          }),
        ],
      },
    });
    montarParametros(document, { rol: 'SUPER_ADMINISTRADOR', fetchImpl });
    await asentar();
    const select = document.querySelector('[data-zona="cambio"] select[name="valor"]');
    expect(select).not.toBeNull();
    expect(select.value).toBe('LIBERAR');
    expect([...select.options].map((o) => o.value)).toEqual(['LIBERAR', 'CONSUMIR']);
  });
});
