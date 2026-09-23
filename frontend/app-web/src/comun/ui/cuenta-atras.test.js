/**
 * Cuenta atras — UX-R2.8b.
 *
 * Lo que se comprueba aqui es que el contador **cuenta**, que es justo lo que
 * no hacia: la vitrina de subastas calculaba el tiempo restante una vez, al
 * pintar, y lo dejaba congelado. Por eso casi todas las pruebas adelantan el
 * reloj y vuelven a mirar el texto.
 */

import { jest } from '@jest/globals';
import {
  adoptarCuentaAtras,
  cuentaAtras,
  enPalabras,
  latir,
  textoDe,
  vigilarCuentasAtras,
} from './cuenta-atras.js';

const SEGUNDO = 1000;
const MINUTO = 60 * SEGUNDO;
const HORA = 60 * MINUTO;
const DIA = 24 * HORA;

/** Una fecha ISO a N milisegundos del ahora de la prueba. */
function dentroDe(ms, ahora = Date.now()) {
  return new Date(ahora + ms).toISOString();
}

describe('textoDe', () => {
  test('la unidad cambia con lo que queda, no al reves', () => {
    expect(textoDe(3 * DIA)).toBe('3d');
    expect(textoDe(5 * HORA)).toBe('5h');
    expect(textoDe(4 * MINUTO + 31 * SEGUNDO)).toBe('04:31');
    expect(textoDe(9 * SEGUNDO)).toBe('00:09');
  });

  test('una subasta vencida dice «Finalizada», no un número negativo', () => {
    expect(textoDe(-1)).toBe('Finalizada');
    expect(textoDe(0)).toBe('Finalizada');
  });

  test('los minutos y segundos van con dos cifras para que no bailen', () => {
    // Sin el relleno, «5:7» y «10:12» tienen anchuras distintas y el contador
    // salta de sitio cada segundo.
    expect(textoDe(5 * MINUTO + 7 * SEGUNDO)).toBe('05:07');
  });
});

describe('enPalabras', () => {
  test('lo que se oye no es «04:31»', () => {
    expect(enPalabras(4 * MINUTO + 31 * SEGUNDO)).toBe('4 minutos y 31 segundos');
    expect(enPalabras(1 * MINUTO)).toBe('1 minuto');
    expect(enPalabras(1 * DIA)).toBe('1 día');
    expect(enPalabras(2 * HORA)).toBe('2 horas');
    expect(enPalabras(1 * SEGUNDO)).toBe('1 segundo');
  });
});

describe('cuentaAtras', () => {
  // `cuentaAtras` lee el reloj por su cuenta al construir el elemento. Sin
  // congelarlo, entre el `dentroDe(...)` de la prueba y esa lectura pasan un
  // par de milisegundos, y «2h» exactas se convierten en «1h» por el
  // redondeo hacia abajo -- que es el comportamiento correcto (queda algo
  // menos de 2 h), pero hace la prueba inestable. Se fija el reloj.
  let ahoraFijo;
  beforeEach(() => {
    ahoraFijo = Date.parse('2026-09-22T12:00:00.000Z');
    jest.spyOn(Date, 'now').mockReturnValue(ahoraFijo);
  });
  afterEach(() => {
    jest.restoreAllMocks();
  });

  test('es un <time> con la fecha real, no solo el texto abreviado', () => {
    const fin = dentroDe(2 * HORA, ahoraFijo);
    const elemento = cuentaAtras(fin);

    expect(elemento.tagName).toBe('TIME');
    expect(elemento.getAttribute('datetime')).toBe(fin);
    expect(elemento.textContent).toBe('2h');
  });

  test('lo que lee un lector de pantalla esta en palabras', () => {
    const elemento = cuentaAtras(dentroDe(4 * MINUTO + 31 * SEGUNDO, ahoraFijo));
    expect(elemento.getAttribute('aria-label')).toBe('Termina en 4 minutos y 31 segundos');
  });

  test('una fecha ilegible no pinta «NaN» en la pantalla', () => {
    const elemento = cuentaAtras('no-es-una-fecha');
    expect(elemento.textContent).toBe('—');
    expect(elemento.textContent).not.toMatch(/NaN|Invalid/);
  });
});

describe('latir', () => {
  test('el numero baja de verdad al pasar el tiempo', () => {
    const ahora = Date.parse('2026-09-22T12:00:00.000Z');
    const raiz = document.createElement('div');
    raiz.append(cuentaAtras(dentroDe(10 * MINUTO, ahora)));

    latir(raiz, { ahora });
    const antes = raiz.firstChild.textContent;

    latir(raiz, { ahora: ahora + 3 * MINUTO });
    const despues = raiz.firstChild.textContent;

    expect(antes).toBe('10:00');
    expect(despues).toBe('07:00');
  });

  test('por debajo de diez minutos se marca urgente, y por encima no', () => {
    const ahora = Date.now();
    const raiz = document.createElement('div');
    raiz.append(cuentaAtras(dentroDe(20 * MINUTO, ahora)));

    latir(raiz, { ahora });
    expect(raiz.firstChild.classList.contains('cuenta-atras--urgente')).toBe(false);

    latir(raiz, { ahora: ahora + 15 * MINUTO });
    expect(raiz.firstChild.classList.contains('cuenta-atras--urgente')).toBe(true);
  });

  test('la urgencia no se codifica solo en movimiento', () => {
    // Si «urgente» fuera un parpadeo, `prefers-reduced-motion` lo borraria y
    // con el la informacion. Es una clase que cambia color y peso.
    const raiz = document.createElement('div');
    raiz.append(cuentaAtras(dentroDe(MINUTO)));
    latir(raiz);
    expect(raiz.firstChild.className).toContain('cuenta-atras--urgente');
  });

  test('al vencer deja de contar y lo dice', () => {
    const ahora = Date.now();
    const raiz = document.createElement('div');
    raiz.append(cuentaAtras(dentroDe(MINUTO, ahora)));

    const vivas = latir(raiz, { ahora: ahora + 2 * MINUTO });

    expect(vivas).toBe(0);
    expect(raiz.firstChild.textContent).toBe('Finalizada');
    expect(raiz.firstChild.className).toContain('cuenta-atras--finalizada');
    expect(raiz.firstChild.className).not.toContain('--urgente');
  });

  test('devuelve cuantas siguen contando, no cuantas hay', () => {
    const ahora = Date.now();
    const raiz = document.createElement('div');
    raiz.append(
      cuentaAtras(dentroDe(HORA, ahora)),
      cuentaAtras(dentroDe(-HORA, ahora)),
      cuentaAtras(dentroDe(2 * HORA, ahora)),
    );

    expect(latir(raiz, { ahora })).toBe(2);
  });
});

describe('adoptarCuentaAtras', () => {
  test('pone a contar un elemento que pinto otro modulo', () => {
    // Es el caso real: `subastas-vitrina.js` es un modulo protegido y crea
    // el `<span class="subastas__contador">42m</span>` congelado.
    const ahora = Date.now();
    const ajeno = document.createElement('span');
    ajeno.className = 'subastas__contador';
    ajeno.textContent = '42m';

    adoptarCuentaAtras(ajeno, dentroDe(30 * MINUTO, ahora));

    expect(ajeno.className).toContain('subastas__contador');
    expect(ajeno.className).toContain('cuenta-atras');

    const raiz = document.createElement('div');
    raiz.append(ajeno);
    latir(raiz, { ahora: ahora + 25 * MINUTO });
    expect(ajeno.textContent).toBe('05:00');
  });
});

describe('vigilarCuentasAtras', () => {
  /** Temporizador de mentira: guarda la ultima llamada y la deja disparar. */
  function relojDeMentira() {
    const llamadas = [];
    return {
      llamadas,
      fijar: (fn, ms) => {
        llamadas.push({ fn, ms });
        return llamadas.length;
      },
      quitar: jest.fn(),
      disparar() {
        const ultima = llamadas.at(-1);
        ultima.fn();
      },
    };
  }

  test('un solo temporizador para toda la página, no uno por tarjeta', () => {
    const raiz = document.createElement('div');
    for (let i = 0; i < 16; i++) {
      raiz.append(cuentaAtras(dentroDe(2 * HORA)));
    }

    const reloj = relojDeMentira();
    vigilarCuentasAtras(raiz, { temporizador: reloj });

    expect(reloj.llamadas).toHaveLength(1);
  });

  test('late cada segundo si algo esta en su ultima hora, cada 30 s si no', () => {
    const lejos = document.createElement('div');
    lejos.append(cuentaAtras(dentroDe(5 * HORA)));
    const cerca = document.createElement('div');
    cerca.append(cuentaAtras(dentroDe(10 * MINUTO)));

    const relojLejos = relojDeMentira();
    vigilarCuentasAtras(lejos, { temporizador: relojLejos });
    const relojCerca = relojDeMentira();
    vigilarCuentasAtras(cerca, { temporizador: relojCerca });

    expect(relojLejos.llamadas[0].ms).toBe(30_000);
    expect(relojCerca.llamadas[0].ms).toBe(1000);
  });

  test('si todas han vencido no se vuelve a programar nada', () => {
    const raiz = document.createElement('div');
    raiz.append(cuentaAtras(dentroDe(-HORA)));

    const reloj = relojDeMentira();
    vigilarCuentasAtras(raiz, { temporizador: reloj });

    expect(reloj.llamadas).toHaveLength(0);
  });

  test('detener corta el latido y no deja nada corriendo', () => {
    const raiz = document.createElement('div');
    raiz.append(cuentaAtras(dentroDe(2 * HORA)));

    const reloj = relojDeMentira();
    const detener = vigilarCuentasAtras(raiz, { temporizador: reloj });
    detener();
    reloj.disparar(); // Como si el temporizador llegara tarde.

    expect(reloj.quitar).toHaveBeenCalled();
    // No se programo una vuelta mas despues de detener.
    expect(reloj.llamadas).toHaveLength(1);
  });
});
