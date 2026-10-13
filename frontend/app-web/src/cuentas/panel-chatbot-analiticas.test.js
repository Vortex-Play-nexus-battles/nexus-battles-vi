/**
 * Pestaña de analíticas del panel del asistente — HU-CHA-012 (RF-CHA-012).
 */

import { jest } from '@jest/globals';

import { ErrorDelChatbot } from '../comun/cliente-chatbot.js';
import {
  diaEnZona,
  duracion,
  graficaDeTendencia,
  horasDeAtencion,
  montarAnaliticas,
  nombreDeCategoria,
  textoDeErrorDelPanel,
} from './panel-chatbot-analiticas.js';

const esperar = () => new Promise((resolver) => setTimeout(resolver, 0));

const TABLERO = {
  desde: '2026-09-09T05:00:00Z',
  hasta: '2026-09-11T05:00:00Z',
  zonaHoraria: 'America/Bogota',
  conversaciones: 2,
  preguntas: 3,
  respuestasMedidas: 3,
  escalamientos: 1,
  tasaResolucion: 2 / 3,
  tiempoRespuestaPromedioMs: 1520,
  calificaciones: 0,
  calificacionesUtiles: 0,
  satisfaccion: null,
  temasFrecuentes: [{ clave: 'k', titulo: 'Subastas', respuestas: 2 }],
  tendencia: [
    { dia: '2026-09-09', conversaciones: 1, preguntas: 1, respuestas: 1, escalamientos: 0 },
    { dia: '2026-09-10', conversaciones: 1, preguntas: 2, respuestas: 2, escalamientos: 1 },
  ],
};

function montar(cliente) {
  const raiz = document.createElement('div');
  document.body.replaceChildren(raiz);
  const descargarArchivo = jest.fn();
  const pestana = montarAnaliticas(raiz, { cliente, descargarArchivo });
  return { raiz, pestana, descargarArchivo };
}

test('diaEnZona respeta la zona y el hasta exclusivo', () => {
  expect(diaEnZona('2026-09-09T05:00:00Z', 'America/Bogota')).toBe('2026-09-09');
  expect(diaEnZona('2026-09-11T05:00:00Z', 'America/Bogota', -1)).toBe('2026-09-10');
});

test('duracion usa ms por debajo de un segundo', () => {
  expect(duracion(null)).toBe('—');
  expect(duracion(250)).toBe('250 ms');
  expect(duracion(1520)).toMatch(/^1[,.]5 s$/);
});

test('la gráfica tiene una barra por día, proporcional, y dice su valor', () => {
  const svg = graficaDeTendencia(TABLERO.tendencia);
  const barras = svg.querySelectorAll('.grafica-tendencia__barra');

  expect(barras).toHaveLength(2);
  expect(Number(barras[1].getAttribute('height'))).toBeGreaterThan(
    Number(barras[0].getAttribute('height')),
  );
  expect(barras[1].querySelector('title').textContent).toContain('2 preguntas');
  expect(svg.getAttribute('role')).toBe('img');
});

test('sin fechas pide el período por omisión, lo muestra en el filtro y pinta el tablero', async () => {
  const cliente = { analiticas: jest.fn(async () => TABLERO) };
  const { raiz, pestana } = montar(cliente);

  await pestana.cargar();

  expect(cliente.analiticas).toHaveBeenCalledWith({ desde: null, hasta: null });
  expect(raiz.querySelector('input[name="desde"]').value).toBe('2026-09-09');
  expect(raiz.querySelector('input[name="hasta"]').value).toBe('2026-09-10');
  const cifras = [
    ...raiz.querySelector('.panel-chatbot__cifras').querySelectorAll('.metrica__valor'),
  ].map((nodo) => nodo.textContent);
  expect(cifras).toEqual(['2', '3', '67 %', '—', expect.stringMatching(/s$/)]);
  expect(raiz.textContent).toContain('Nadie calificó respuestas en el período');
  expect(raiz.querySelectorAll('.grafica-tendencia__barra')).toHaveLength(2);
  expect(raiz.querySelector('[data-zona="temas"] tbody').textContent).toContain('Subastas');
});

test('sin actividad no dibuja barras vacías', async () => {
  const quieto = {
    ...TABLERO,
    tendencia: TABLERO.tendencia.map((punto) => ({ ...punto, preguntas: 0 })),
    temasFrecuentes: [],
  };
  const { raiz, pestana } = montar({ analiticas: jest.fn(async () => quieto) });

  await pestana.cargar();

  expect(raiz.querySelector('.grafica-tendencia')).toBeNull();
  expect(raiz.textContent).toContain('Sin preguntas en este período');
});

test('un período al revés no consulta al servicio', async () => {
  const cliente = { analiticas: jest.fn(async () => TABLERO) };
  const { raiz, pestana } = montar(cliente);
  raiz.querySelector('input[name="desde"]').value = '2026-09-10';
  raiz.querySelector('input[name="hasta"]').value = '2026-09-01';

  await pestana.cargar();

  expect(cliente.analiticas).not.toHaveBeenCalled();
  expect(raiz.textContent).toContain('no puede ser anterior');
});

test('si el servicio falla, lo explica y permite reintentar', async () => {
  const analiticas = jest
    .fn()
    .mockRejectedValueOnce(new ErrorDelChatbot(null, 0))
    .mockResolvedValueOnce(TABLERO);
  const { raiz, pestana } = montar({ analiticas });

  await pestana.cargar();
  expect(raiz.textContent).toContain('El servicio del asistente no responde');

  raiz.querySelector('[data-accion="reintentar"]').click();
  await esperar();
  expect(analiticas).toHaveBeenCalledTimes(2);
  expect(raiz.querySelectorAll('.metrica').length).toBeGreaterThan(0);
});

test('Descargar CSV entrega el archivo del servicio', async () => {
  const archivo = { nombre: 'analiticas.csv', contenido: new Blob(['x']) };
  const cliente = {
    analiticas: jest.fn(async () => TABLERO),
    exportarAnaliticas: jest.fn(async () => archivo),
  };
  const { raiz, descargarArchivo } = montar(cliente);
  raiz.querySelector('input[name="desde"]').value = '2026-09-01';

  raiz.querySelector('[data-accion="exportar-analiticas"]').click();
  await esperar();

  expect(cliente.exportarAnaliticas).toHaveBeenCalledWith({ desde: '2026-09-01', hasta: null });
  expect(descargarArchivo).toHaveBeenCalledWith(archivo);
});

test('textoDeErrorDelPanel distingue permiso, período inválido y caída', () => {
  expect(textoDeErrorDelPanel(new ErrorDelChatbot(null, 403)).titulo).toContain('permiso');
  expect(textoDeErrorDelPanel(new ErrorDelChatbot({ title: 'Bad Request' }, 400)).titulo).toBe(
    'El período no es válido',
  );
  expect(textoDeErrorDelPanel(new ErrorDelChatbot(null, 0)).titulo).toContain('no responde');
});

// 1.3.7 (7.4.7): solicitudes de soporte y palabras clave.
const CON_SOPORTE = {
  ...TABLERO,
  tickets: {
    total: 4,
    abiertos: 1,
    enProceso: 1,
    resueltos: 1,
    cerrados: 1,
    horasPromedioDeAtencion: 2.5,
    porCategoria: [
      { categoria: 'SOPORTE_TECNICO', tickets: 3 },
      { categoria: 'SUBASTA_Y_COMERCIO', tickets: 1 },
    ],
  },
  palabrasClave: [{ palabra: 'torneo', preguntas: 5, conversaciones: 3 }],
};

test('horasDeAtencion usa minutos por debajo de una hora y — sin datos', () => {
  expect(horasDeAtencion(null)).toBe('—');
  expect(horasDeAtencion(0.5)).toBe('30 min');
  expect(horasDeAtencion(2.5)).toMatch(/^2[,.]5 h$/);
});

test('nombreDeCategoria usa el nombre que ve el jugador', () => {
  expect(nombreDeCategoria('SOPORTE_TECNICO')).toBe('Problema técnico');
  expect(nombreDeCategoria('OTRA_NUEVA')).toBe('OTRA_NUEVA');
});

test('pinta las solicitudes de soporte y las palabras más usadas', async () => {
  const { raiz, pestana } = montar({ analiticas: jest.fn(async () => CON_SOPORTE) });

  await pestana.cargar();

  const soporte = raiz.querySelector('[data-zona="soporte"]');
  const cifras = [...soporte.querySelectorAll('.metrica__valor')].map((nodo) => nodo.textContent);
  expect(cifras).toEqual(['4', '2', '2', expect.stringMatching(/h$/)]);
  const categorias = [
    ...soporte.querySelectorAll('[data-zona="soporte-por-categoria"] tbody tr'),
  ].map((fila) => fila.textContent);
  expect(categorias).toEqual(['Problema técnico3', 'Subastas y comercio1']);

  const palabras = raiz.querySelector('[data-zona="palabras-clave"]');
  expect(palabras.querySelector('tbody tr').textContent).toBe('torneo53');
});

test('sin solicitudes ni palabras muestra estados vacíos; sin los campos, no pinta las secciones', async () => {
  const vacio = {
    ...TABLERO,
    tickets: {
      total: 0,
      abiertos: 0,
      enProceso: 0,
      resueltos: 0,
      cerrados: 0,
      horasPromedioDeAtencion: null,
      porCategoria: [],
    },
    palabrasClave: [],
  };
  const conVacios = montar({ analiticas: jest.fn(async () => vacio) });
  await conVacios.pestana.cargar();
  expect(conVacios.raiz.textContent).toContain('Nadie pidió soporte en este período');
  expect(conVacios.raiz.textContent).toContain('Todavía no hay solicitudes atendidas');
  expect(conVacios.raiz.textContent).toContain('Todavía no hay palabras que se repitan');

  const antiguo = montar({ analiticas: jest.fn(async () => TABLERO) });
  await antiguo.pestana.cargar();
  expect(antiguo.raiz.querySelector('[data-zona="soporte"]')).toBeNull();
  expect(antiguo.raiz.querySelector('[data-zona="palabras"]')).toBeNull();
});
