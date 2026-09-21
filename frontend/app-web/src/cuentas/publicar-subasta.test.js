import { jest } from '@jest/globals';
import { readFileSync, existsSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { montarPublicacion, validarCondiciones } from './publicar-subasta.js';
import { interpretarProblema } from './cliente-publicacion-subastas.js';

const uid = 'cfe8bfda-38d0-4d0f-b4f3-397890ac6589';
const elemento = {
  id: 'unidad-1',
  productoId: 'a32b510f-9fd1-4a28-8b18-ae4979b41826',
  nombrePropio: 'Espada de luz',
  tipo: 'ARMA',
  disponible: true,
  subastaId: null,
};
const pagina = {
  elementos: [elemento],
  numero: 0,
  tamanio: 16,
  totalElementos: 1,
  totalPaginas: 1,
  ultima: true,
};
const $ = (selector) => document.querySelector(selector);
const cambiar = (selector, valor) => {
  $(selector).value = valor;
  $(selector).dispatchEvent(new Event('input', { bubbles: true }));
};
const marcar = (selector) => {
  $(selector).checked = true;
  $(selector).dispatchEvent(new Event('input', { bubbles: true }));
};
const submit = () =>
  $('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
const vaciar = async () => {
  for (let i = 0; i < 8; i++) {
    await Promise.resolve();
  }
};
function autenticar(claims = { uid, exp: 9999999999 }) {
  sessionStorage.setItem('nexus.token', `e30.${btoa(JSON.stringify(claims))}.firma`);
  sessionStorage.setItem('nexus.apodoActual', 'Guerrero');
}
function completar() {
  cambiar('#producto', elemento.id);
  cambiar('#inicial', '10');
  marcar('#aceptar');
}
async function montar(opciones = {}) {
  const consultar = jest.fn().mockResolvedValue(pagina);
  const publicar = jest.fn().mockResolvedValue({ id: 'subasta-creada', comisionCobrado: 1 });
  const crearClave = jest.fn().mockReturnValue('clave-estable');
  const dependencias = { consultar, publicar, crearClave, ...opciones };
  await montarPublicacion($('#raiz'), dependencias);
  return dependencias;
}
beforeEach(() => {
  sessionStorage.clear();
  document.body.innerHTML = '<div data-cabecera-app></div><main id="raiz"></main>';
  autenticar();
});
afterEach(() => {
  jest.restoreAllMocks();
  delete globalThis.fetch;
});

test.each([null, {}, { uid, exp: 1 }, { sub: uid }])(
  'sin sesión utilizable muestra login y no consulta inventario: %j',
  async (claims) => {
    sessionStorage.clear();
    if (claims) {
      autenticar(claims);
    }
    const { consultar } = await montar();
    expect($('form').hidden).toBe(true);
    expect($('#nexus-rbac-forbidden').textContent).toMatch(/iniciar sesión/);
    expect(consultar).not.toHaveBeenCalled();
  },
);

test('usa cliente real de inventario, elementos y Authorization', async () => {
  globalThis.fetch = jest
    .fn()
    .mockResolvedValue({ ok: true, status: 200, json: async () => pagina });
  await montarPublicacion($('#raiz'));
  expect(globalThis.fetch).toHaveBeenCalledWith(
    '/api/v1/inventario/elementos?pagina=0',
    expect.objectContaining({
      headers: { 'X-User-Name': 'Guerrero', Authorization: expect.stringMatching(/^Bearer /) },
    }),
  );
  expect($('#producto').options[1].textContent).toBe('Espada de luz · ARMA · unidad-1');
  expect($('.cabecera')).not.toBeNull();
});

test('selección solo del inventario y productos no disponibles deshabilitados', async () => {
  await montar({
    consultar: jest.fn().mockResolvedValue({
      ...pagina,
      elementos: [elemento, { ...elemento, id: 'ocupado', disponible: false }],
    }),
  });
  expect($('#producto').options[2].disabled).toBe(true);
  cambiar('#producto', 'ajeno');
  cambiar('#inicial', '10');
  marcar('#aceptar');
  expect($('[type="submit"]').disabled).toBe(true);
  cambiar('#producto', elemento.id);
  expect($('[data-resumen-producto]').textContent).toContain('Espada de luz');
});

test.each([
  ['24H', '1 crédito'],
  ['48H', '3 créditos'],
])('%s muestra comisión visible y resumen', async (duracion, comision) => {
  await montar();
  marcar(`[value="${duracion}"]`);
  expect($('[data-resumen-comision]').textContent).toBe(comision);
  expect($('[data-resumen-duracion]').textContent).toContain(duracion.slice(0, 2));
});

test('precio obligatorio, compra inmediata opcional y resumen confirmado', async () => {
  const { publicar } = await montar();
  cambiar('#producto', elemento.id);
  marcar('#aceptar');
  submit();
  expect(publicar).not.toHaveBeenCalled();
  expect($('#error-inicial').textContent).toMatch(/mayor que cero/);
  cambiar('#inicial', '10');
  expect($('#aceptar').checked).toBe(false);
  expect($('[data-resumen-inicial]').textContent).toBe('10 créditos');
  expect($('[data-resumen-inmediata]').textContent).toBe('Sin compra inmediata');
  cambiar('#inmediata', '9');
  marcar('#aceptar');
  expect($('[type="submit"]').disabled).toBe(true);
  cambiar('#inmediata', '10');
  marcar('#aceptar');
  expect($('[data-resumen-inmediata]').textContent).toBe('10 créditos');
  expect($('[type="submit"]').disabled).toBe(false);
});

test.each(['0', '-1', 'NaN', 'Infinity', ''])('rechaza precio inicial %s', (precio) => {
  expect(validarCondiciones(elemento, '24H', precio, '').inicial).toBeDefined();
});
test('rechaza duración ajena al contrato', () => {
  expect(validarCondiciones(elemento, '72H', '10', '').duracion).toBeDefined();
});

test('publica solo tras aceptación, envía contrato y muestra éxito con vuelta al listado', async () => {
  const { publicar } = await montar();
  completar();
  submit();
  await vaciar();
  expect(publicar).toHaveBeenCalledWith(
    {
      elementoInventarioId: elemento.id,
      productoId: elemento.productoId,
      duracion: '24H',
      precioInicial: 10,
      precioCompraInmediata: null,
    },
    'clave-estable',
  );
  expect($('#nexus-rbac-forbidden').textContent).toMatch(
    /publicada correctamente.*Comisión cobrada: 1/,
  );
  expect($('#raiz a').getAttribute('href')).toBe('./subastas.html');
  expect($('form').hidden).toBe(true);
  expect(sessionStorage.getItem(`nexus.hu-sub-001.intento:${uid}`)).toBeNull();
  submit();
  expect(publicar).toHaveBeenCalledTimes(1);
});

test('previene doble submit y bloquea edición durante petición', async () => {
  let resolver;
  const publicar = jest.fn(
    () =>
      new Promise((resolve) => {
        resolver = resolve;
      }),
  );
  await montar({ publicar });
  completar();
  submit();
  submit();
  expect(publicar).toHaveBeenCalledTimes(1);
  expect($('[type="submit"]').disabled).toBe(true);
  expect($('[data-condiciones]').disabled).toBe(true);
  resolver({ id: 'ok', comisionCobrado: 1 });
  await vaciar();
});

test('resultado incierto conserva clave, cuerpo y confirmación tras recarga', async () => {
  const publicar = jest.fn().mockRejectedValue(interpretarProblema(503, {}));
  const { crearClave } = await montar({ publicar });
  completar();
  submit();
  await vaciar();
  const primerCuerpo = publicar.mock.calls[0][0];
  expect($('[data-condiciones]').disabled).toBe(true);
  submit();
  await vaciar();
  expect(crearClave).toHaveBeenCalledTimes(1);
  expect(publicar.mock.calls[1]).toEqual([primerCuerpo, 'clave-estable']);
  document.body.innerHTML = '<main id="raiz"></main>';
  const segundaVista = await montar();
  expect(segundaVista.consultar).not.toHaveBeenCalled();
  expect($('[data-resumen-producto]').textContent).toContain('Espada de luz');
  submit();
  await vaciar();
  expect(segundaVista.publicar).toHaveBeenCalledWith(primerCuerpo, 'clave-estable');
  expect(segundaVista.crearClave).not.toHaveBeenCalled();
});

test.each([
  [422, 'Creditos insuficientes para publicar la subasta', /créditos suficientes/],
  [403, 'El usuario tiene una sanción activa', /sanción activa/],
  [409, 'El elemento ya tiene una subasta activa', /no está disponible/],
  [500, 'error interno secreto', /confirmar el resultado/],
])('muestra error accesible %s', async (status, detail, texto) => {
  await montar({
    publicar: jest
      .fn()
      .mockRejectedValue(interpretarProblema(status, { type: 'about:blank', detail })),
  });
  completar();
  submit();
  await vaciar();
  expect($('#nexus-rbac-forbidden').textContent).toMatch(texto);
  expect($('#nexus-rbac-forbidden').textContent).not.toContain('secreto');
  expect($('#nexus-rbac-forbidden').getAttribute('aria-live')).toBe('polite');
});

test('sesión caducada antes de enviar bloquea publicación', async () => {
  const { publicar } = await montar();
  completar();
  autenticar({ uid, exp: 1 });
  submit();
  expect(publicar).not.toHaveBeenCalled();
  expect($('[data-sesion]').hidden).toBe(false);
});

test('permite paginar sin fabricar productos', async () => {
  const consultar = jest
    .fn()
    .mockResolvedValueOnce({ ...pagina, totalPaginas: 2, ultima: false })
    .mockResolvedValueOnce({
      ...pagina,
      numero: 1,
      totalPaginas: 2,
      elementos: [{ ...elemento, id: 'segunda-unidad' }],
    });
  await montar({ consultar });
  $('[data-siguiente]').click();
  await vaciar();
  expect(consultar.mock.calls[1][1]).toBe(1);
  expect($('#producto').options[1].value).toBe('segunda-unidad');
  expect($('[data-pagina]').textContent).toBe('Página 2 de 2');
});

test('inventario vacío y fallo de carga tienen estados claros', async () => {
  await montar({
    consultar: jest.fn().mockResolvedValue({ ...pagina, elementos: [], totalPaginas: 0 }),
  });
  expect($('[data-inventario]').textContent).toMatch(/No hay productos/);
  await montar({ consultar: jest.fn().mockRejectedValue(new Error('interno')) });
  expect($('[data-recargar]').hidden).toBe(false);
  expect($('#nexus-rbac-forbidden').textContent).not.toContain('interno');
});

test('nombres del inventario se muestran como texto, sin insertar HTML', async () => {
  await montar({
    consultar: jest.fn().mockResolvedValue({
      ...pagina,
      elementos: [{ ...elemento, nombrePropio: '<img src=x onerror=alert(1)>' }],
    }),
  });
  completar();
  expect($('#raiz img')).toBeNull();
  expect($('[data-resumen-producto]').textContent).toContain('<img');
});

test('auditoría HU-SUB-001: los módulos protegidos coinciden byte a byte con HEAD', () => {
  const raizRepo = new URL('../../../../', import.meta.url);
  const protegidos = [
    'cuentas/subastas.js',
    'cuentas/subastas.html',
    'cuentas/cliente-subastas.js',
    'cuentas/subastas-vitrina.js',
    'cuentas/subastas-filtros.js',
    'cuentas/subastas-busqueda.js',
    'cuentas/pujas.js',
    'cuentas/pujas-api.js',
    'comun/cabecera-app.js',
    'comun/identidad.js',
    'comun/base-api.js',
    'comun/interceptors/http-error.interceptor.js',
    'contenido/inventario/cliente-inventario.js',
  ];
  for (const protegido of protegidos) {
    const ruta = `frontend/app-web/src/${protegido}`;
    const seguido = execFileSync('git', ['ls-files', '--', ruta], {
      cwd: raizRepo,
      encoding: 'utf8',
    }).trim();
    if (!seguido) {
      expect(existsSync(new URL(ruta, raizRepo))).toBe(false);
      continue;
    }
    // Git puede normalizar CRLF en el checkout; el contenido debe ser idéntico.
    const actual = readFileSync(new URL(ruta, raizRepo), 'utf8').replace(/\r\n/g, '\n');
    const original = execFileSync('git', ['show', `HEAD:${ruta}`], {
      cwd: raizRepo,
      encoding: 'utf8',
    }).replace(/\r\n/g, '\n');
    expect(actual).toBe(original);
  }
});

test('compra inmediata incompleta no se confunde con campo opcional vacío', async () => {
  const { publicar } = await montar();
  completar();
  Object.defineProperty($('#inmediata'), 'validity', { value: { badInput: true } });
  $('#inmediata').dispatchEvent(new Event('input', { bubbles: true }));
  marcar('#aceptar');
  submit();
  expect($('[type="submit"]').disabled).toBe(true);
  expect(publicar).not.toHaveBeenCalled();
  expect($('#error-inmediata').textContent).not.toBe('');
});
