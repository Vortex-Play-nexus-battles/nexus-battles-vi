/**
 * Pestaña de analíticas del panel del asistente — HU-CHA-012 (RF-CHA-012).
 */

import { jest } from '@jest/globals';

import { ErrorDelChatbot } from '../comun/cliente-chatbot.js';
import {
diaEnZona,
duracion,
graficaDeTendencia,
montarAnaliticas,
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
  const cifras = [...raiz.querySelectorAll('.metrica__valor')].map((nodo) => nodo.textContent);
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
