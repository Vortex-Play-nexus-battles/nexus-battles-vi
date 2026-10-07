/**
 * Preparar una misión, paso a paso — revisión del modo jugador del 6-oct,
 * puntos 22 y 23.
 *
 * Lo que se prueba: los cinco pasos en su orden, un paso a la vista (por
 * `data-paso`), que no se avanza sin héroe ni sin estrategia comprobada y se
 * dice por qué, el indicador de pasos accesible, y que el último paso enseña
 * el resumen con lo que queda bloqueado junto a «Iniciar misión».
 */

import { asistenteDeMision, PASOS_DE_PREPARACION, resumenDeMatricula } from './asistente-mision.js';

const MISION = { nombre: 'El Templo Olvidado', duracionHoras: 12 };

/** Un configurador de mentira: dice qué héroe hay y si la estrategia vale. */
function configuradorFalso() {
  let heroe = null;
  let valida = false;
  return {
    elemento: document.createElement('section'),
    heroe: () => heroe,
    estrategia: () =>
      valida && heroe
        ? { heroeId: heroe.id, heroeNombre: heroe.nombre, rotaciones: [{ pasos: ['Embate'] }] }
        : null,
    elegir(nombre) {
      heroe = { id: 'h-1', nombre };
    },
    validar() {
      valida = true;
    },
  };
}

function montar(configurador = configuradorFalso()) {
  const botonIniciar = document.createElement('button');
  botonIniciar.dataset.accion = 'iniciar-mision';
  const motivoInicio = document.createElement('p');
  const asistente = asistenteDeMision({
    mision: MISION,
    configurador,
    botonIniciar,
    motivoInicio,
    hrefEquipamiento: '../inventario/inventario.html#equipamiento',
  });
  document.body.replaceChildren(asistente.elemento);
  return { asistente, configurador, botonIniciar };
}

const siguiente = () => document.querySelector('[data-accion="paso-siguiente"]');
const anterior = () => document.querySelector('[data-accion="paso-anterior"]');

describe('asistenteDeMision', () => {
  test('cinco pasos: héroe, estadísticas, rotaciones, comprobación y confirmación', () => {
    expect(PASOS_DE_PREPARACION.map((p) => p.id)).toEqual([
      'heroe',
      'estadisticas',
      'rotaciones',
      'comprobar',
      'confirmar',
    ]);
    const { asistente } = montar();
    const marcas = [...asistente.elemento.querySelectorAll('.mision-asistente__marca')];
    expect(marcas.map((m) => m.textContent)).toEqual([
      '1Héroe',
      '2Estadísticas',
      '3Rotaciones',
      '4Comprobación',
      '5Confirmación',
    ]);
    expect(marcas[0].getAttribute('aria-current')).toBe('step');
    expect(asistente.elemento.querySelector('ol').getAttribute('aria-label')).toBe(
      'Pasos para preparar la misión',
    );
  });

  test('empieza en el héroe, sin «Anterior», y dice en qué paso está', () => {
    const { asistente } = montar();
    expect(asistente.paso()).toBe('heroe');
    expect(asistente.elemento.dataset.paso).toBe('heroe');
    expect(anterior().hidden).toBe(true);
    expect(asistente.elemento.querySelector('.mision-asistente__contador').textContent).toBe(
      'Paso 1 de 5',
    );
    expect(asistente.elemento.querySelector('.mision-asistente__titulo').textContent).toBe(
      'Elige tu héroe',
    );
  });

  test('sin héroe no se sigue y se dice por qué; con él, sí', () => {
    const { asistente, configurador } = montar();
    expect(siguiente().getAttribute('aria-disabled')).toBe('true');
    const motivo = document.getElementById(siguiente().getAttribute('aria-describedby'));
    expect(motivo.textContent).toBe('Elige un héroe para seguir.');

    siguiente().click();
    expect(asistente.paso()).toBe('heroe');

    configurador.elegir('Aquiles');
    asistente.actualizar();
    expect(siguiente().getAttribute('aria-disabled')).toBe('false');
    expect(motivo.textContent).toBe('');
    siguiente().click();
    expect(asistente.paso()).toBe('estadisticas');
    expect(document.activeElement).toBe(
      asistente.elemento.querySelector('.mision-asistente__titulo'),
    );
  });

  test('en Estadísticas está el enlace para cambiar el equipamiento; en los demás, no', () => {
    const { asistente, configurador } = montar();
    const equipo = asistente.elemento.querySelector('[data-accion="cambiar-equipamiento"]');
    expect(equipo.hidden).toBe(true);
    configurador.elegir('Aquiles');
    asistente.irA('estadisticas');
    expect(equipo.hidden).toBe(false);
    expect(equipo.getAttribute('href')).toBe('../inventario/inventario.html#equipamiento');
    asistente.irA('rotaciones');
    expect(equipo.hidden).toBe(true);
  });

  test('sin estrategia comprobada no se pasa a confirmar', () => {
    const { asistente, configurador } = montar();
    configurador.elegir('Aquiles');
    asistente.irA('comprobar');
    expect(siguiente().getAttribute('aria-disabled')).toBe('true');
    expect(document.getElementById(siguiente().getAttribute('aria-describedby')).textContent).toBe(
      'Comprueba la estrategia para seguir: tiene que ser válida.',
    );
    siguiente().click();
    expect(asistente.paso()).toBe('comprobar');

    configurador.validar();
    asistente.actualizar();
    siguiente().click();
    expect(asistente.paso()).toBe('confirmar');
  });

  test('confirmar: el resumen con lo que queda bloqueado y «Iniciar misión»; sin «Siguiente»', () => {
    const { asistente, configurador, botonIniciar } = montar();
    configurador.elegir('Aquiles');
    configurador.validar();
    asistente.irA('confirmar');

    expect(siguiente().hidden).toBe(true);
    expect(anterior().hidden).toBe(false);
    const confirmar = asistente.elemento.querySelector('[data-zona="confirmar"]');
    expect(confirmar.contains(botonIniciar)).toBe(true);
    expect(confirmar.textContent).toContain('El Templo Olvidado');
    expect(confirmar.textContent).toContain('Aquiles queda bloqueado 12 horas');

    anterior().click();
    expect(asistente.paso()).toBe('comprobar');
  });

  test('las marcas dicen qué está hecho, qué toca y qué falta', () => {
    const { asistente, configurador } = montar();
    configurador.elegir('Aquiles');
    asistente.irA('rotaciones');
    const estados = [...asistente.elemento.querySelectorAll('.mision-asistente__marca')].map(
      (m) => m.dataset.estado,
    );
    expect(estados).toEqual(['hecho', 'hecho', 'actual', 'pendiente', 'pendiente']);
  });

  test('un paso que no existe no mueve nada', () => {
    const { asistente } = montar();
    asistente.irA('volar');
    expect(asistente.paso()).toBe('heroe');
  });
});

test('resumenDeMatricula: sin rotaciones, el ataque básico', () => {
  const resumen = resumenDeMatricula(MISION, { heroe: 'Aquiles', rotaciones: [] });
  expect(resumen.textContent).toContain('Ataque básico');
  expect(resumen.querySelector('.misiones-confirmacion__advertencia').textContent).toContain(
    'no podrá jugar en línea, entrar en torneos ni cambiar su equipamiento',
  );
});
