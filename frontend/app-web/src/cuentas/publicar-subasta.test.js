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
// UXC-9 — la cabecera del jugador lleva ahora la búsqueda de productos
// (RF-INV-008), que también es un <form>: el de publicar es el de la vista.
const formulario = () => $('#raiz form');
const submit = () =>
  formulario().dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
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
  // PLAYER-07b — el catálogo de productos (GET /api/v1/productos/{id}), de
  // donde sale el nombre que se lee. Por omisión responde con el mismo nombre
  // que el inventario, para que las pruebas de antes sigan diciendo lo mismo.
  const consultarProducto = jest.fn(async (id) => ({ id, nombre: 'Espada de luz' }));
  const dependencias = { consultar, publicar, crearClave, consultarProducto, ...opciones };
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
    expect(formulario().hidden).toBe(true);
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
  // UXC-8 — el tipo en palabras y sin el identificador interno del elemento.
  expect($('#producto').options[1].textContent).toBe('Espada de luz · Arma');
  expect($('#producto').options[1].value).toBe('unidad-1');
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
  // B8 — 7.7.2: la compra inmediata tiene que SUPERAR el precio minimo; igual ya no vale.
  cambiar('#inmediata', '10');
  marcar('#aceptar');
  expect($('[type="submit"]').disabled).toBe(true);
  cambiar('#inmediata', '11');
  marcar('#aceptar');
  expect($('[data-resumen-inmediata]').textContent).toBe('11 créditos');
  expect($('[type="submit"]').disabled).toBe(false);
});

test('B8: la compra inmediata igual al precio inicial se rechaza con el motivo del documento', () => {
  expect(validarCondiciones(elemento, '24H', '10', '10').inmediata).toMatch(
    /superior al precio inicial/,
  );
  expect(validarCondiciones(elemento, '24H', '10', '10.5').inmediata).toBeUndefined();
});

test('B8: las comisiones salen de las reglas del servidor, no de la pantalla', async () => {
  const consultarReglas = jest.fn().mockResolvedValue({
    duraciones: [
      { codigo: '24H', horas: 24, comision: '2' },
      { codigo: '48H', horas: 48, comision: '5' },
    ],
    incrementoMinimoConfigurado: true,
    incrementoMinimo: '5',
  });
  await montar({ consultarReglas });
  expect(consultarReglas).toHaveBeenCalled();
  expect($('[data-comision="24H"]').textContent).toBe('Comisión: 2 créditos');
  marcar('[value="48H"]');
  expect($('[data-resumen-comision]').textContent).toBe('5 créditos');
  // D-43 — el incremento lo dice el servidor y se muestra tal cual.
  expect($('[data-incremento-minimo]').hidden).toBe(false);
  expect($('[data-incremento-minimo]').textContent).toBe('Incremento mínimo: 5 créditos');
});

test('B8: sin incremento configurado se dice antes y no se deja publicar', async () => {
  const consultarReglas = jest.fn().mockResolvedValue({
    duraciones: [
      { codigo: '24H', horas: 24, comision: '1' },
      { codigo: '48H', horas: 48, comision: '3' },
    ],
    incrementoMinimoConfigurado: false,
    incrementoMinimo: null,
  });
  const { publicar } = await montar({ consultarReglas });
  expect($('[data-incremento-minimo]').hidden).toBe(false);
  expect($('[data-incremento-minimo]').textContent).toMatch(
    /no está configurado en administración/,
  );
  expect($('[data-incremento-minimo]').textContent).not.toMatch(/DECISIÓN PO/);
  completar();
  expect($('[type="submit"]').disabled).toBe(true);
  submit();
  await vaciar();
  expect(publicar).not.toHaveBeenCalled();
});

test('B8: si las reglas no llegan se sigue con el respaldo de la Tabla 25', async () => {
  await montar({ consultarReglas: jest.fn().mockResolvedValue(null) });
  expect($('[data-comision="48H"]').textContent).toBe('Comisión: 3 créditos');
  completar();
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
  expect($('#nexus-rbac-forbidden').textContent).toMatch(/Subasta publicada.*Comisión cobrada: 1/);
  // UXC-9 — sin el identificador en el texto; en su lugar, el camino a verla.
  expect($('#nexus-rbac-forbidden').textContent).not.toContain('subasta-creada');
  expect($('[data-accion="ver-publicada"]').getAttribute('href')).toBe(
    './pujas.html?id=subasta-creada',
  );
  expect($('#raiz a').getAttribute('href')).toBe('./subastas.html');
  expect(formulario().hidden).toBe(true);
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
  // UXC-9 — qué pasa y adónde ir, sin «Página 0 de 0».
  expect($('[data-inventario]').textContent).toMatch(/Tu inventario está vacío/);
  expect($('[data-sin-inventario]').hidden).toBe(false);
  expect($('[data-sin-inventario] a').getAttribute('href')).toBe('./tienda.html');
  expect($('.publicacion__paginacion').hidden).toBe(true);
  await montar({ consultar: jest.fn().mockRejectedValue(new Error('interno')) });
  expect($('[data-recargar]').hidden).toBe(false);
  expect($('#nexus-rbac-forbidden').textContent).not.toContain('interno');
});

test.each([
  // PLAYER-07b — el nombre sale del catálogo, y el del inventario es su
  // respaldo cuando el catálogo no responde: los dos se pintan como texto.
  [
    'el del inventario',
    jest.fn(async () => {
      throw new Error('sin catálogo');
    }),
  ],
  ['el del catálogo', jest.fn(async (id) => ({ id, nombre: '<img src=x onerror=alert(1)>' }))],
])(
  'nombres del inventario se muestran como texto, sin insertar HTML: %s',
  async (_caso, consultarProducto) => {
    await montar({
      consultar: jest.fn().mockResolvedValue({
        ...pagina,
        elementos: [{ ...elemento, nombrePropio: '<img src=x onerror=alert(1)>' }],
      }),
      consultarProducto,
    });
    completar();
    expect($('#raiz img')).toBeNull();
    expect($('[data-resumen-producto]').textContent).toContain('<img');
  },
);

test('auditoría HU-SUB-001: los módulos protegidos coinciden byte a byte con HEAD', () => {
  const raizRepo = new URL('../../../../', import.meta.url);
  const protegidos = [
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

/*
 * PLAYER-07b (punto 27) — «Mejorar el nombre del producto ya que sale un
 * código raro al montar la subasta». La confirmación decía
 * «Espada de luz · ARMA · <id del elemento>». El nombre sale ahora del
 * catálogo; los identificadores siguen viajando en la solicitud y no se pintan.
 */
describe('PLAYER-07b — el nombre del producto al montar la subasta', () => {
  const UUID_EN_TEXTO = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i;
  // Como llegan algunos objetos entregados sin nombre: el inventario guardó
  // como «nombre» el propio identificador del producto.
  const conCodigo = {
    ...elemento,
    id: '7c9e6679-7425-40de-944b-e07fc1f90ae7',
    nombrePropio: elemento.productoId,
  };
  const delCatalogo = (nombre) => jest.fn(async (id) => ({ id, nombre }));
  const conElementos = (elementos) => jest.fn().mockResolvedValue({ ...pagina, elementos });

  test('la confirmación dice el nombre del catálogo y el tipo en palabras, sin identificadores', async () => {
    const consultarProducto = delCatalogo('Espada de una mano');
    const { publicar } = await montar({ consultar: conElementos([conCodigo]), consultarProducto });

    expect(consultarProducto).toHaveBeenCalledWith(conCodigo.productoId);
    expect($('#producto').options[1].textContent).toBe('Espada de una mano · Arma');
    cambiar('#producto', conCodigo.id);
    expect($('[data-resumen-producto]').textContent).toBe('Espada de una mano · Arma');
    expect($('[data-resumen-producto]').dataset.origenNombre).toBe('catalogo');
    expect($('#raiz').textContent).not.toMatch(UUID_EN_TEXTO);
    expect($('#raiz').textContent).not.toMatch(/\bARMA\b/);
    expect($('[data-aviso-catalogo]').hidden).toBe(true);

    // El identificador sigue viajando para la operación: solo deja de verse.
    cambiar('#inicial', '10');
    marcar('#aceptar');
    submit();
    await vaciar();
    expect(publicar).toHaveBeenCalledWith(
      expect.objectContaining({
        elementoInventarioId: conCodigo.id,
        productoId: conCodigo.productoId,
      }),
      'clave-estable',
    );
  });

  test('manda el nombre del catálogo (el que verá quien puje), no el que guardó el inventario', async () => {
    // El inventario puede guardar otro nombre: uno de pruebas («Arma 1») o uno
    // que se copió antes de que el catálogo lo cambiara.
    await montar({
      consultar: conElementos([{ ...elemento, nombrePropio: 'Arma 1' }]),
      consultarProducto: delCatalogo('Espada de una mano'),
    });

    expect($('#producto').options[1].textContent).toBe('Espada de una mano · Arma');
    expect($('#raiz').textContent).not.toContain('Arma 1');
  });

  test('dos copias del mismo producto se distinguen sin enseñar su identificador', async () => {
    await montar({
      consultar: conElementos([elemento, { ...elemento, id: 'unidad-2' }]),
      consultarProducto: delCatalogo('Espada de una mano'),
    });

    expect([...$('#producto').options].slice(1).map((o) => o.textContent)).toEqual([
      'Espada de una mano · Arma · copia 1',
      'Espada de una mano · Arma · copia 2',
    ]);
  });

  test('cada producto se pide una sola vez al catálogo, también al cambiar de página', async () => {
    const consultarProducto = delCatalogo('Espada de una mano');
    const consultar = jest
      .fn()
      .mockResolvedValueOnce({
        ...pagina,
        totalPaginas: 2,
        elementos: [
          elemento,
          { ...elemento, id: 'unidad-2' },
          {
            ...elemento,
            id: 'unidad-3',
            tipo: 'ARMADURA',
            productoId: 'b43c621a-0ae2-4b39-9c29-bf5a8a0c2937',
          },
        ],
      })
      .mockResolvedValueOnce({ ...pagina, numero: 1, totalPaginas: 2, elementos: [elemento] });
    await montar({ consultar, consultarProducto });

    expect(consultarProducto).toHaveBeenCalledTimes(2);
    $('[data-siguiente]').click();
    await vaciar();
    expect(consultarProducto).toHaveBeenCalledTimes(2);
    expect($('#producto').options[1].textContent).toBe('Espada de una mano · Arma');
  });

  test('si el catálogo no responde, se dice y se usa el nombre del inventario, nunca un código', async () => {
    const consultarProducto = jest.fn(async (id) => {
      throw new Error(`El catalogo de productos respondio 503 al pedir ${id}`);
    });
    const { publicar } = await montar({
      consultar: conElementos([elemento, conCodigo]),
      consultarProducto,
    });

    expect([...$('#producto').options].slice(1).map((o) => o.textContent)).toEqual([
      'Espada de luz · Arma',
      'Objeto sin nombre · Arma',
    ]);
    expect($('[data-aviso-catalogo]').hidden).toBe(false);
    expect($('[data-aviso-catalogo]').textContent).toMatch(/catálogo de productos/);
    expect($('#raiz').textContent).not.toMatch(UUID_EN_TEXTO);
    expect($('#raiz').textContent).not.toMatch(/503|respondio/);

    cambiar('#producto', conCodigo.id);
    expect($('[data-resumen-producto]').textContent).toBe('Objeto sin nombre · Arma');
    expect($('[data-resumen-producto]').dataset.origenNombre).toBe('sin-nombre');
    cambiar('#producto', elemento.id);
    expect($('[data-resumen-producto]').dataset.origenNombre).toBe('inventario');

    // Es presentación: sin el nombre del catálogo se puede publicar igual.
    cambiar('#inicial', '10');
    marcar('#aceptar');
    expect($('[type="submit"]').disabled).toBe(false);
    submit();
    await vaciar();
    expect(publicar).toHaveBeenCalledTimes(1);
  });

  test('«Reintentar» vuelve a preguntar al catálogo y pone su nombre', async () => {
    let caido = true;
    const consultarProducto = jest.fn(async (id) => {
      if (caido) {
        throw new Error('sin red');
      }
      return { id, nombre: 'Espada de una mano' };
    });
    await montar({ consultarProducto });
    expect($('[data-aviso-catalogo]').hidden).toBe(false);

    caido = false;
    $('[data-reintentar-catalogo]').click();
    await vaciar();

    expect($('#producto').options[1].textContent).toBe('Espada de una mano · Arma');
    expect($('[data-aviso-catalogo]').hidden).toBe(true);
    expect($('[data-reintentar-catalogo]').disabled).toBe(false);
  });

  test('un catálogo lento no deja la pantalla sin inventario: su nombre llega después', async () => {
    jest.useFakeTimers();
    try {
      let responder;
      const consultarProducto = jest.fn(
        () =>
          new Promise((resolver) => {
            responder = resolver;
          }),
      );
      const montaje = montar({ consultarProducto });
      await jest.advanceTimersByTimeAsync(3000);
      await montaje;

      // Mientras tanto, el nombre del inventario (que aquí es un nombre).
      expect($('#producto').options[1].textContent).toBe('Espada de luz · Arma');
      expect($('[data-inventario]').textContent).toBe('Selecciona un elemento para continuar.');

      responder({ nombre: 'Espada de una mano' });
      await jest.advanceTimersByTimeAsync(0);
      expect($('#producto').options[1].textContent).toBe('Espada de una mano · Arma');
    } finally {
      jest.useRealTimers();
    }
  });

  test('la publicación pendiente se guarda con el nombre que se leyó, sin identificadores', async () => {
    const publicar = jest.fn().mockRejectedValue(interpretarProblema(503, {}));
    await montar({
      publicar,
      consultar: conElementos([conCodigo]),
      consultarProducto: delCatalogo('Espada de una mano'),
    });
    cambiar('#producto', conCodigo.id);
    cambiar('#inicial', '10');
    marcar('#aceptar');
    submit();
    await vaciar();

    const guardado = JSON.parse(sessionStorage.getItem(`nexus.hu-sub-001.intento:${uid}`));
    expect(guardado.nombre).toBe('Espada de una mano · Arma');
    expect(guardado.solicitud.elementoInventarioId).toBe(conCodigo.id);
    expect($('[data-resumen-producto]').textContent).toBe('Espada de una mano · Arma');
  });

  test.each([
    [
      'con el catálogo caído, el nombre guardado sin el identificador',
      jest.fn(async () => {
        throw new Error('sin red');
      }),
      'Espada de luz · Arma',
      'guardado',
    ],
    [
      'con el catálogo, su nombre',
      jest.fn(async (id) => ({ id, nombre: 'Espada de una mano' })),
      'Espada de una mano · Arma',
      'catalogo',
    ],
  ])(
    'una publicación pendiente del formato anterior se enseña limpia: %s',
    async (_caso, consultarProducto, esperado, origen) => {
      sessionStorage.setItem(
        `nexus.hu-sub-001.intento:${uid}`,
        JSON.stringify({
          clave: 'clave-vieja',
          nombre: `Espada de luz · ARMA · ${conCodigo.id}`,
          solicitud: {
            elementoInventarioId: conCodigo.id,
            productoId: conCodigo.productoId,
            duracion: '24H',
            precioInicial: 10,
            precioCompraInmediata: null,
          },
        }),
      );
      await montar({ consultarProducto });
      await vaciar();

      expect($('[data-resumen-producto]').textContent).toBe(esperado);
      expect($('[data-resumen-producto]').dataset.origenNombre).toBe(origen);
      expect($('#raiz').textContent).not.toMatch(UUID_EN_TEXTO);
    },
  );
});

test('Subastas activas ofrece Publicar subasta sin sesión y conserva la carga del listado', async () => {
  sessionStorage.clear();
  document.body.innerHTML = '<div data-cabecera-app></div><main id="raiz-subastas"></main>';
  globalThis.fetch = jest.fn().mockResolvedValue({
    ok: true,
    json: async () => ({ contenido: [], pagina: 0, totalPaginas: 0 }),
  });
  const escuchar = jest.spyOn(document, 'addEventListener');
  await import('./subastas.js');
  const inicializar = escuchar.mock.calls.find(([evento]) => evento === 'DOMContentLoaded')[1];
  try {
    document.dispatchEvent(new Event('DOMContentLoaded'));
    await vaciar();

    // UX-R2.8b — lo que esta prueba protege es el COMPORTAMIENTO: que sin
    // sesion se ofrezca publicar y que el listado se pida igual. Las
    // afirmaciones que ataban ese comportamiento a una posición concreta del
    // DOM (`previousElementSibling`, `nextElementSibling`) se cambiaron por
    // las del encabezado del kit, que es donde vive ahora el par
    // titulo + accion. El estado vacio tambien pasa a ser el del kit.
    const enlace = $('#raiz-subastas .encabezado-pagina a');
    expect(enlace).not.toBeNull();
    expect(enlace.textContent).toBe('Publicar subasta');
    expect(enlace.getAttribute('href')).toBe('./publicar-subasta.html');
    expect(enlace.hidden).toBe(false);
    expect($('#raiz-subastas .encabezado-pagina h1').textContent).toBe('Subastas activas');
    expect($('#raiz-subastas .subastas-busqueda')).not.toBeNull();
    expect($('.cabecera [data-seccion="subasta"]').getAttribute('aria-current')).toBe('page');
    expect($('.subastas-filtros')).not.toBeNull();
    expect($('.subastas-orden__control')).not.toBeNull();
    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/v1/subastas?page=0&size=16&ordenarPor=FECHA_PUBLICACION',
    );
    expect($('#subastas-resultados [data-estado="vacio"]')).not.toBeNull();
  } finally {
    document.removeEventListener('DOMContentLoaded', inicializar);
  }
});
