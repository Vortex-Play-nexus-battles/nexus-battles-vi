/**
 * El resumen de la consola — SA-7 (informe de revisión, punto 1): cifras
 * clave, últimos movimientos y estabilidad del sistema, cada dato de su
 * servicio y solo lo que el rol puede consultar. Los datos son de prueba.
 */

import { jest } from '@jest/globals';

import { fecha } from '../../comun/ui/formato.js';
import { RESULTADO } from './cliente-consola.js';
import {
  cifrasDelRol,
  montarConsola,
  notaDeApelaciones,
  notaDeServicios,
  pintarEstabilidad,
  tonoDePendientes,
} from './consola.js';

const BASE = 'http://localhost:8099/frontend/app-web/src/comun/shell.js';
const UID_RAIZ = 'a1a1a1a1-0000-4000-8000-000000000001';

const datos = (valor) => ({ resultado: RESULTADO.DATOS, datos: valor });

const RESPUESTAS = {
  '/admin/jugadores?page=0&size=1': datos({ content: [], totalElements: 1342 }),
  '/admin/sistema/servicios': datos({
    total: 18,
    operativos: 16,
    caidos: 1,
    lentos: 1,
    noDesplegados: 0,
    noObservables: 0,
    servicios: [
      { servicio: 'srv-torneos', estado: 'OPERATIVO', instante: '2026-10-14T10:00:00Z' },
      { servicio: 'srv-subastas', estado: 'LENTO', detalle: 'No contestó en 2 s', instante: 'x' },
      { servicio: 'srv-correo', estado: 'CAIDO', instante: 'x' },
    ],
  }),
  '/comentarios/moderacion?pagina=0&tamano=1': datos({
    entradas: [],
    total: 3,
    pagina: 0,
    tamano: 1,
  }),
  '/apelaciones': datos([
    { id: 'a2', decisionAntesDe: '2026-10-27T17:00:00Z', vencida: false },
    { id: 'a1', decisionAntesDe: '2026-10-20T17:00:00Z', vencida: false },
  ]),
  // El listado de salas solo trae las que esperan jugadores; con `tamano=1`
  // basta el total de la página.
  '/salas?tamano=1': datos({
    contenido: [{ id: 's3', estado: 'ABIERTA' }],
    pagina: 0,
    tamano: 1,
    totalElementos: 3,
    totalPaginas: 3,
  }),
  '/torneos': datos({ content: [{}, {}, {}, {}], totalElements: 4 }),
  '/admin/auditoria?page=0&size=8&sort=fechaHora,desc&sort=id,desc': datos({
    content: [
      {
        id: 1,
        tipoAccion: 'SUSPENSION',
        administrador: UID_RAIZ,
        afectado: 'comentario:55',
        motivo: 'Spam reiterado',
        fechaHora: '2026-10-14T09:00:00Z',
      },
    ],
  }),
  [`/admin/titulares?uids=${UID_RAIZ}`]: datos([
    { uid: UID_RAIZ, apodo: 'Raíz', estado: 'ACTIVO' },
  ]),
  '/comentarios/moderacion?pagina=0&tamano=8': datos({
    entradas: [
      {
        comentario: { id: 'c1', texto: 'Este comentario tiene insultos', apodoAutor: 'thar_vex' },
        reportes: 4,
        prioridadElevada: true,
        primerReporte: '2026-10-14T08:00:00Z',
      },
    ],
    total: 1,
    pagina: 0,
    tamano: 8,
  }),
};

function montar(rol, respuestas = RESPUESTAS) {
  document.body.innerHTML = `
    <div data-zona="encabezado"></div>
    <section data-zona="indicadores"></section>
    <div data-zona="paneles"></div>
    <section data-zona="accesos" hidden></section>`;
  const consultarApi = jest.fn(
    async (recurso) => respuestas[recurso] ?? { resultado: RESULTADO.SERVICIO_DEGRADADO },
  );
  const consola = montarConsola(
    document,
    { autenticado: true, apodo: 'qa_super', rol },
    { consultarApi, base: BASE, actualizarSola: false },
  );
  return { consultarApi, consola };
}

async function esperarHasta(condicion) {
  for (let intento = 0; intento < 40; intento += 1) {
    if (condicion()) {
      return;
    }
    await new Promise((resolver) => setTimeout(resolver, 0));
  }
  throw new Error('El resumen no terminó de pintarse');
}

const cifra = (etiqueta) => document.querySelector(`[data-indicador="${etiqueta}"]`);

test('el super administrador ve las seis cifras, con su valor real y su enlace', async () => {
  montar('SUPER_ADMINISTRADOR');
  await esperarHasta(() => cifra('Torneos publicados'));

  expect(document.querySelector('h1').textContent).toBe('Control del Nexo');
  expect(cifra('Cuentas registradas').textContent).toContain('1.342');
  expect(cifra('Servicios en línea').textContent).toContain('16 de 18');
  expect(cifra('Servicios en línea').textContent).toContain('1 caído · 1 lento');
  expect(cifra('Servicios en línea').dataset.tono).toBe('advertencia');
  expect(cifra('Comentarios por revisar').textContent).toContain('3');
  expect(cifra('Apelaciones por resolver').textContent).toContain('2');
  expect(cifra('Apelaciones por resolver').textContent).toContain(
    `La próxima vence el ${fecha('2026-10-20T17:00:00Z')}`,
  );
  expect(cifra('Salas abiertas').textContent).toContain('3');
  expect(cifra('Torneos publicados').textContent).toContain('4');
  expect(cifra('Cuentas registradas').querySelector('a').getAttribute('href')).toMatch(
    /cuentas\/gestion-usuarios\.html$/,
  );
});

test('los últimos movimientos son la bitácora central, con apodos y sin identificadores', async () => {
  montar('SUPER_ADMINISTRADOR');
  const movimientos = () => document.querySelector('[data-panel="movimientos"]');
  await esperarHasta(() => movimientos().querySelector('table'));

  expect(movimientos().querySelector('h3').textContent).toBe('Últimos movimientos');
  expect(movimientos().textContent).toContain('Raíz');
  expect(movimientos().textContent).toContain('Spam reiterado');
  expect(movimientos().textContent).not.toContain(UID_RAIZ);
  expect(movimientos().querySelector('a').textContent).toBe('Ver el registro completo');
});

test('la estabilidad pone primero lo caído y lo lento, con lo que dijo la sonda', async () => {
  montar('SUPER_ADMINISTRADOR');
  const estabilidad = () => document.querySelector('[data-panel="estabilidad"]');
  await esperarHasta(() => estabilidad().querySelector('.resumen-consola__atencion'));

  const filas = [...estabilidad().querySelectorAll('li')].map((li) => li.dataset.estado);
  expect(filas).toEqual(['CAIDO', 'LENTO']);
  expect(estabilidad().textContent).toContain('1 de 3 servicios responden con normalidad.');
  expect(estabilidad().textContent).toContain('No contestó en 2 s');
});

test('sin herramientas repetidas: solo accesos rápidos a lo que no está en la barra', async () => {
  montar('SUPER_ADMINISTRADOR');
  const accesos = [...document.querySelectorAll('[data-acceso]')].map((a) => a.textContent);
  expect(accesos).toEqual(['Crear cuenta administrativa', 'Nuevo producto', 'Banners y anuncios']);
  expect(document.body.textContent).not.toMatch(/Herramientas|Tu alcance|Fuente|GET \//);
});

test('el moderador solo ve lo suyo y no se piden datos de administración', async () => {
  const { consultarApi } = montar('MODERADOR');
  await esperarHasta(() => cifra('Apelaciones por resolver'));

  expect(cifrasDelRol('MODERADOR').map((c) => c.id)).toEqual(['comentarios', 'apelaciones']);
  expect(document.querySelectorAll('[data-indicador]')).toHaveLength(2);
  const pedidos = consultarApi.mock.calls.map(([recurso]) => recurso);
  expect(pedidos.some((r) => r.startsWith('/admin/'))).toBe(false);
  expect(document.querySelector('[data-panel="estabilidad"]')).toBeNull();
  const movimientos = document.querySelector('[data-panel="movimientos"]');
  expect(movimientos.querySelector('h3').textContent).toBe('Comentarios por revisar primero');
  await esperarHasta(() => movimientos.textContent.includes('insultos'));
  expect(movimientos.textContent).toContain('De thar_vex · 4 reportes');
  expect(movimientos.querySelector('li').dataset.prioridad).toBe('elevada');
  expect(document.querySelector('[data-zona="accesos"]').hidden).toBe(true);
});

test('un servicio que no responde dice «Sin conexión», nunca un cero inventado', async () => {
  montar('ADMINISTRADOR', {
    ...RESPUESTAS,
    '/torneos': { resultado: RESULTADO.SERVICIO_DEGRADADO },
    '/admin/jugadores?page=0&size=1': { resultado: RESULTADO.SIN_PERMISO },
  });
  await esperarHasta(() => cifra('Torneos publicados'));

  expect(cifra('Torneos publicados').dataset.sinDato).toBe('si');
  expect(cifra('Torneos publicados').textContent).toContain('Sin conexión');
  expect(cifra('Torneos publicados').textContent).not.toMatch(/\b0\b/);
  expect(cifra('Cuentas registradas').textContent).toContain('Sin acceso');
});

test('una apelación fuera de plazo se dice primero y en rojo', async () => {
  montar('MODERADOR', {
    ...RESPUESTAS,
    '/apelaciones': datos([
      { id: 'a1', decisionAntesDe: '2026-10-01T17:00:00Z', vencida: true },
      { id: 'a2', decisionAntesDe: '2026-10-27T17:00:00Z', vencida: false },
    ]),
  });
  await esperarHasta(() => cifra('Apelaciones por resolver'));

  expect(cifra('Apelaciones por resolver').textContent).toContain('1 fuera de plazo');
  expect(cifra('Apelaciones por resolver').dataset.tono).toBe('error');
  expect(notaDeApelaciones([], 0)).toBeNull();
});

test('una página vacía que trae su total dice el total; sin total, cero', async () => {
  montar('SUPER_ADMINISTRADOR', {
    ...RESPUESTAS,
    '/admin/jugadores?page=0&size=1': {
      resultado: RESULTADO.VACIO,
      datos: { content: [], totalElements: 52 },
    },
    '/apelaciones': { resultado: RESULTADO.VACIO, datos: [] },
  });
  await esperarHasta(() => cifra('Torneos publicados'));

  expect(cifra('Cuentas registradas').textContent).toContain('52');
  expect(cifra('Apelaciones por resolver').textContent).toContain('0');
  expect(cifra('Apelaciones por resolver').dataset.tono).toBe('exito');
});

test('textos de las cifras', () => {
  expect(notaDeServicios({ caidos: 0, lentos: 0 })).toBe('Todos responden con normalidad');
  expect(notaDeServicios({ caidos: 2, lentos: 1 })).toBe('2 caídos · 1 lento');
  expect(tonoDePendientes(0)).toBe('exito');
  expect(tonoDePendientes(5)).toBe('advertencia');
  expect(tonoDePendientes(null)).toBeNull();
  const todoBien = pintarEstabilidad({
    servicios: [{ servicio: 'srv-torneos', estado: 'OPERATIVO' }],
  });
  expect(todoBien.map((n) => n.textContent)).toEqual([
    '1 de 1 servicios responden con normalidad.',
    'Ningún servicio está caído ni lento.',
  ]);
});
