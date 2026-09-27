/**
 * UXC-5 — el reporte de una misión terminada y el historial (§7.8.8).
 */

import { historialDeMisiones, reporteDeMision, selloDeResultado } from './reporte-mision.js';

const REPORTE = {
  ejecucionId: 'e-1',
  mision: { id: 'templo', nombre: 'El Templo Olvidado', categoria: 'HISTORIA' },
  resultado: 'EXITO',
  duracionMs: 12 * 3_600_000,
  terminadaEn: '2026-09-24T20:00:00Z',
  heroe: { nombre: 'Aquiles', prototipo: 'Guerrero Armas', nivel: 1 },
  combate: {
    encuentros: 18,
    danoInfligido: 1240,
    danoRecibido: 380,
    turnos: 96,
    habilidadesMasUsadas: [
      { nombre: 'Embate sangriento', usos: 31 },
      { nombre: 'Ataque básico', usos: 1 },
    ],
    criticos: 7,
  },
  enemigosDerrotados: [
    { nombre: 'Sombras Corrompidas', cantidad: 10 },
    { nombre: 'Guardianes de Piedra', cantidad: 5 },
  ],
  mastersDerrotados: [{ nombre: 'Sombra del Olvido', epica: 'Velo de Sombras' }],
  jefeDerrotado: true,
  recompensas: {
    creditos: 60,
    productos: [{ nombre: 'Cofre de Bronce', rareza: 'COMUN' }],
    epicas: ['Velo de Sombras'],
    experiencia: 120,
  },
  objetivos: [
    { texto: 'Derrotar al Guardián del Templo.', cumplido: true, bonificacion: null },
    { texto: 'Encontrar los 3 fragmentos del Sello Antiguo.', cumplido: false },
  ],
};

const rutas = {
  hrefTablon: 'misiones.html',
  hrefHistorial: 'misiones.html#historial',
  hrefDe: (m) => `misiones.html?mision=${m.id}`,
  hrefReporte: (id) => `misiones.html?reporte=${id}`,
};

test('el reporte trae sus cinco bloques, con las cifras tal cual llegan', () => {
  const reporte = reporteDeMision(REPORTE, rutas);

  expect(reporte.querySelector('h1').textContent).toBe('El Templo Olvidado');
  expect(reporte.querySelector('.mision-estado').textContent).toBe('Éxito');
  expect(reporte.textContent).toContain('12 h');
  expect(reporte.textContent).toContain('Aquiles · Guerrero Armas · nivel 1');
  const cifras = [...reporte.querySelectorAll('[data-bloque="combate"] .metrica')].map(
    (m) => m.textContent,
  );
  expect(cifras).toContain('Daño infligido1.240');
  expect(reporte.textContent).toContain('1 vez');
  expect(reporte.textContent).toContain('Derrotó al jefe final.');
  expect(reporte.textContent).toContain('Máster derrotado: Sombra del Olvido');
  expect(reporte.querySelector('[data-bloque="recompensas"]').textContent).toContain('Comun');
  const objetivos = [...reporte.querySelectorAll('[data-bloque="objetivos"] li')];
  expect(objetivos.map((o) => o.dataset.cumplido)).toEqual(['true', 'false']);
  expect(objetivos[1].textContent).toContain('No cumplido: Encontrar los 3 fragmentos');
});

test('un fallo se dice con palabra e icono, no solo con color', () => {
  const sello = selloDeResultado('FALLO');
  expect(sello.textContent).toBe('Fallo');
  expect(sello.querySelector('svg')).not.toBeNull();
});

test('historial vacío: qué quedará aquí y el camino al tablón', () => {
  const vacio = historialDeMisiones({ completadas: [] }, rutas);
  expect(vacio.textContent).toContain('Todavía no has terminado ninguna misión');
  expect(vacio.querySelector('a').getAttribute('href')).toBe('misiones.html');
});

test('historial: por categoría, tabla con su reporte, épicas y progreso de la historia', () => {
  const historial = historialDeMisiones(
    {
      completadas: [
        {
          ejecucionId: 'e-1',
          misionId: 'templo',
          nombre: 'El Templo Olvidado',
          categoria: 'HISTORIA',
          terminadaEn: '2026-09-24T20:00:00Z',
          resultado: 'EXITO',
          duracionMs: 12 * 3_600_000,
        },
      ],
      porCategoria: [{ categoria: 'HISTORIA', completadas: 1, fallidas: 0 }],
      mejoresTiempos: [
        { misionId: 'templo', nombre: 'El Templo Olvidado', duracionMs: 43_200_000 },
      ],
      epicas: [
        {
          nombre: 'Velo de Sombras',
          master: 'Sombra del Olvido',
          obtenidaEn: '2026-09-24T20:00:00Z',
        },
      ],
      cadenas: [{ nombre: 'Crónicas del Bosque Sombrío', completadas: 1, total: 3 }],
    },
    rutas,
  );

  expect([...historial.querySelectorAll('.metrica')].map((m) => m.textContent)).toEqual([
    'Historia1completadas',
    'Desafío0completadas',
    'Exploración0completadas',
  ]);
  const enlace = historial.querySelector('tbody a');
  expect(enlace.getAttribute('href')).toBe('misiones.html?reporte=e-1');
  expect(historial.querySelector('[role="region"]').getAttribute('tabindex')).toBe('0');
  expect(historial.textContent).toContain('Velo de Sombras');
  expect(historial.textContent).toContain('1 de 3');
});
