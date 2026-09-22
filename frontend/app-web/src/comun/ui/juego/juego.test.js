/**
 * Componentes de juego — UX-R2.2.
 *
 * Lo que se comprueba aqui no es que el HTML salga bonito, sino las tres cosas
 * que se rompen en silencio y nadie ve hasta la demo:
 *
 *  1. Que el marcado sea **el que el CSS del kit espera**. Un componente que
 *     genera `.marco-heroe__foto` cuando el CSS dice `.marco-heroe__imagen` se
 *     ve como un hueco y no falla nada.
 *  2. Que el estado **no dependa solo del color**. Rareza, turno, bloqueo y
 *     desenlace tienen que estar tambien en texto o en un atributo aria.
 *  3. Que lo que se pulsa sea **pulsable de verdad** con teclado.
 */

import { jest } from '@jest/globals';

import { icono, ICONOS, rutaDelSprite } from '../icono.js';
import { retratoDeHeroe, tarjetaDeHeroe } from './heroe.js';
import { ranura, grupoDeRanuras } from './ranura.js';
import { accionDeCombate, actualizarTurno, barraDeVida, indicadorDeTurno } from './combate.js';
import { panelDeResultado } from './resultado.js';
import { distintivoDeCreditos } from './credito.js';

describe('icono', () => {
  test('apunta al sprite del kit con el simbolo pedido', () => {
    const svg = icono('espada', { etiqueta: 'Atacar' });
    const href = svg.querySelector('use').getAttribute('href');

    expect(href).toContain('shared/ui-kit/iconos/sprite.svg#espada');
    expect(svg.getAttribute('aria-label')).toBe('Atacar');
    expect(svg.getAttribute('role')).toBe('img');
  });

  test('sin etiqueta queda marcado como decorativo, no como imagen sin nombre', () => {
    const svg = icono('escudo');
    expect(svg.getAttribute('aria-hidden')).toBe('true');
    expect(svg.hasAttribute('aria-label')).toBe(false);
  });

  test('un simbolo que no esta en el sprite falla en vez de pintar un hueco', () => {
    expect(() => icono('dragon')).toThrow(/no esta en el sprite/);
  });

  test('la ruta del sprite no depende de quien importe el modulo', () => {
    expect(rutaDelSprite()).toMatch(/shared\/ui-kit\/iconos\/sprite\.svg$/);
    expect(ICONOS).toContain('moneda');
  });
});

describe('retratoDeHeroe', () => {
  test('usa las clases que el kit tiene dibujadas', () => {
    const nodo = retratoDeHeroe({ nombre: 'Arquero del Norte', nivel: 7, rareza: 'EPICA' });

    expect(nodo.className).toBe('marco-heroe marco-heroe--epica');
    expect(nodo.querySelector('.marco-heroe__retrato')).not.toBeNull();
    expect(nodo.querySelector('.marco-heroe__nivel').textContent).toBe('7');
    expect(nodo.querySelector('.marco-heroe__nombre').textContent).toBe('Arquero del Norte');
  });

  test('la rareza y el nivel van en el nombre accesible, no solo en el borde', () => {
    const nodo = retratoDeHeroe({ nombre: 'Golem', nivel: 3, rareza: 'LEGENDARIA' });
    expect(nodo.getAttribute('aria-label')).toBe('Golem, nivel 3, legendaria');
  });

  test('sin imagen pinta la inicial en vez de dejar el circulo vacio', () => {
    const nodo = retratoDeHeroe({ nombre: 'maga de bronce' });
    expect(nodo.querySelector('.marco-heroe__inicial').textContent).toBe('M');
    expect(nodo.querySelector('img')).toBeNull();
  });

  test('con imagen, el alt va vacio para que el lector no repita el nombre', () => {
    const nodo = retratoDeHeroe({ nombre: 'Golem', imagen: '/g.png' });
    const img = nodo.querySelector('.marco-heroe__imagen');
    expect(img.getAttribute('alt')).toBe('');
    expect(img.getAttribute('loading')).toBe('lazy');
  });

  test('una rareza que el kit no conoce no rompe: se queda en el marco base', () => {
    expect(retratoDeHeroe({ nombre: 'X', rareza: 'MITICA' }).className).toBe('marco-heroe');
  });
});

describe('tarjetaDeHeroe', () => {
  test('sin accion es un article, no un boton falso', () => {
    const nodo = tarjetaDeHeroe({ nombre: 'Golem', rareza: 'RARA' });
    expect(nodo.tagName).toBe('ARTICLE');
  });

  test('si se puede pulsar, es un <button> que responde al teclado', () => {
    const elegido = jest.fn();
    const heroe = { nombre: 'Golem', rareza: 'RARA' };
    const nodo = tarjetaDeHeroe(heroe, { alPulsar: elegido });

    expect(nodo.tagName).toBe('BUTTON');
    expect(nodo.getAttribute('type')).toBe('button');
    // Un <button> ya responde a Enter y espacio por el agente de usuario: lo
    // que hay que comprobar es que el manejador este puesto.
    nodo.click();
    expect(elegido).toHaveBeenCalledWith(heroe);
  });

  test('la rareza se escribe, no solo se colorea', () => {
    const nodo = tarjetaDeHeroe({ nombre: 'Golem', rareza: 'LEGENDARIA' });
    expect(nodo.textContent).toContain('Legendaria');
  });

  test('las estadisticas se pintan tal como llegan: no se calcula nada', () => {
    const nodo = tarjetaDeHeroe({
      nombre: 'Golem',
      estadisticas: [
        { etiqueta: 'Ataque', valor: 42 },
        { etiqueta: 'Defensa', valor: 17 },
      ],
    });
    const cifras = [...nodo.querySelectorAll('.tarjeta-heroe__cifra')].map((n) => n.textContent);
    expect(cifras).toEqual(['42', '17']);
  });

  test('seleccionada se anuncia con aria-pressed', () => {
    const nodo = tarjetaDeHeroe({ nombre: 'G' }, { alPulsar: () => {}, seleccionada: true });
    expect(nodo.getAttribute('aria-pressed')).toBe('true');
  });
});

describe('ranura', () => {
  test('vacia lleva la variante del kit y ofrece elegir', () => {
    const nodo = ranura({ etiqueta: 'Arma 1' });
    expect(nodo.className).toContain('ranura--vacia');
    expect(nodo.querySelector('.ranura__caja').getAttribute('aria-label')).toBe(
      'Arma 1: vacia. Elegir objeto',
    );
  });

  test('ocupada escribe el nombre del objeto: un icono de espada no distingue dos espadas', () => {
    const nodo = ranura({
      etiqueta: 'Arma 1',
      objeto: { nombre: 'Hoja de Alba', icono: 'espada' },
    });
    expect(nodo.className).not.toContain('ranura--vacia');
    expect(nodo.querySelector('.ranura__etiqueta').textContent).toBe('Hoja de Alba');
  });

  test('bloqueada dice POR QUE, no solo se apaga', () => {
    const nodo = ranura({ etiqueta: 'Anillo', bloqueo: 'Necesitas nivel 12' });
    const caja = nodo.querySelector('.ranura__caja');

    expect(nodo.className).toContain('ranura--bloqueada');
    expect(caja.disabled).toBe(true);
    expect(caja.getAttribute('title')).toBe('Necesitas nivel 12');
    expect(caja.getAttribute('aria-label')).toContain('Necesitas nivel 12');
  });

  test('bloqueada no llama al manejador aunque le disparen el click', () => {
    const elegir = jest.fn();
    const nodo = ranura({ etiqueta: 'Anillo', bloqueo: 'Nivel 12', alElegir: elegir });
    nodo.querySelector('.ranura__caja').dispatchEvent(new Event('click'));
    expect(elegir).not.toHaveBeenCalled();
  });

  test('se elige con el teclado porque la caja es un <button>, no un <div>', () => {
    const elegir = jest.fn();
    const nodo = ranura({ etiqueta: 'Casco', alElegir: elegir });
    const caja = nodo.querySelector('.ranura__caja');

    expect(caja.tagName).toBe('BUTTON');
    caja.click();
    expect(elegir).toHaveBeenCalledWith({ etiqueta: 'Casco', objeto: null });
  });

  test('el grupo se anuncia como grupo de controles, no como lista', () => {
    const grupo = grupoDeRanuras('Armas', [{ etiqueta: 'Arma 1' }, { etiqueta: 'Arma 2' }]);
    const rejilla = grupo.querySelector('.grupo-ranuras__rejilla');

    expect(rejilla.getAttribute('role')).toBe('group');
    expect(rejilla.getAttribute('aria-label')).toBe('Armas');
    expect(rejilla.querySelectorAll('.ranura')).toHaveLength(2);
  });
});

describe('barraDeVida', () => {
  test('genera el marcado exacto que espera el modulo del kit', () => {
    const nodo = barraDeVida({ nombre: 'Golem', idJugador: 'u-1' });

    // Estos cuatro selectores son los que consulta shared/ui-kit/js/barra-vida.js.
    expect(nodo.className).toBe('barra-vida');
    expect(nodo.querySelector('.barra-vida__nombre').textContent).toBe('Golem');
    expect(nodo.querySelector('.barra-vida__pista .barra-vida__relleno')).not.toBeNull();
    expect(nodo.querySelector('.barra-vida__valor')).not.toBeNull();
    expect(nodo.dataset.jugador).toBe('u-1');
  });

  test('marca a la IA y al equipo con texto, porque en una partida de seis hace falta', () => {
    const nodo = barraDeVida({ nombre: 'Centinela', esIA: true, equipo: 2 });
    expect(nodo.dataset.ia).toBe('true');
    expect(nodo.dataset.equipo).toBe('2');
    expect(nodo.querySelector('[data-etiqueta]').textContent).toBe('IA · Equipo 2');
  });

  test('no pinta el valor: eso lo hace quien conoce los umbrales', () => {
    const nodo = barraDeVida({ nombre: 'Golem' });
    expect(nodo.querySelector('.barra-vida__valor').textContent).toBe('');
    expect(nodo.dataset.estado).toBeUndefined();
  });
});

describe('accionDeCombate', () => {
  test('lleva icono y nombre, que es lo que pide el kit', () => {
    const nodo = accionDeCombate({ nombre: 'Atacar', icono: 'espada', coste: 3 });

    expect(nodo.className).toBe('accion-combate');
    expect(nodo.querySelector('.accion-combate__icono')).not.toBeNull();
    expect(nodo.querySelector('.accion-combate__etiqueta').textContent).toBe('Atacar');
    expect(nodo.querySelector('.accion-combate__coste').textContent).toBe('3');
  });

  test('fuera de turno: el motivo se lee, no se deduce del gris', () => {
    const nodo = accionDeCombate({
      nombre: 'Atacar',
      icono: 'espada',
      impedimento: 'No es tu turno',
    });

    expect(nodo.className).toContain('accion-combate--fuera-de-turno');
    expect(nodo.disabled).toBe(true);
    expect(nodo.getAttribute('title')).toBe('No es tu turno');
    expect(nodo.getAttribute('aria-label')).toContain('No es tu turno');
  });

  test('sin poder usa su propia variante, que el kit distingue', () => {
    const nodo = accionDeCombate({
      nombre: 'Rayo',
      icono: 'rayo',
      impedimento: 'Te faltan 2 de poder',
      causa: 'poder',
    });
    expect(nodo.className).toContain('accion-combate--sin-poder');
  });

  test('bloqueada no ejecuta la accion', () => {
    const usar = jest.fn();
    const nodo = accionDeCombate(
      { nombre: 'Atacar', icono: 'espada', impedimento: 'No es tu turno' },
      usar,
    );
    nodo.dispatchEvent(new Event('click'));
    expect(usar).not.toHaveBeenCalled();
  });

  test('disponible si la ejecuta', () => {
    const usar = jest.fn();
    const accion = { nombre: 'Atacar', icono: 'espada' };
    accionDeCombate(accion, usar).click();
    expect(usar).toHaveBeenCalledWith(accion);
  });
});

describe('indicadorDeTurno', () => {
  test('se anuncia solo, porque el turno cambia sin que el jugador toque nada', () => {
    const nodo = indicadorDeTurno({ texto: 'Te toca', propio: true, ronda: 4 });

    expect(nodo.className).toBe('turno-actual');
    expect(nodo.getAttribute('aria-live')).toBe('polite');
    expect(nodo.dataset.mio).toBe('true');
    expect(nodo.textContent).toContain('Te toca');
    expect(nodo.textContent).toContain('Ronda 4');
  });

  test('cambiar de turno reusa el nodo: no se pierde el foco ni se anuncia dos veces', () => {
    const nodo = indicadorDeTurno({ texto: 'Turno de Ana', ronda: 1 });
    const texto = nodo.querySelector('.turno-actual__texto');

    actualizarTurno(nodo, { texto: 'Te toca', propio: true, ronda: 2 });

    expect(nodo.querySelector('.turno-actual__texto')).toBe(texto);
    expect(texto.textContent).toBe('Te toca');
    expect(nodo.dataset.mio).toBe('true');
    expect(nodo.querySelector('.turno-actual__ronda').textContent).toBe('Ronda 2');
  });

  test('actualizarTurno se niega a trabajar sobre algo que no es un elemento', () => {
    expect(() => actualizarTurno(null, { texto: 'x' })).toThrow(TypeError);
  });
});

describe('panelDeResultado', () => {
  test('ganar y perder se distinguen por la palabra, no solo por el color', () => {
    expect(panelDeResultado({ victoria: true }).textContent).toContain('VICTORIA');
    expect(panelDeResultado({ victoria: false }).textContent).toContain('DERROTA');
  });

  test('usa la clase que activa --t-display-tam, la ficha reservada para esto', () => {
    const nodo = panelDeResultado({ victoria: true });
    expect(nodo.querySelector('.panel-resultado__palabra')).not.toBeNull();
    expect(nodo.className).toContain('panel-resultado--victoria');
  });

  test('anuncia el desenlace sin secuestrar el foco', () => {
    const nodo = panelDeResultado({ victoria: false });
    expect(nodo.getAttribute('role')).toBe('status');
    expect(nodo.getAttribute('aria-live')).toBe('polite');
  });

  test('los creditos llevan signo y se marcan para el color', () => {
    const gana = panelDeResultado({ victoria: true, creditos: 1250 });
    expect(gana.querySelector('.panel-resultado__creditos').textContent).toBe('+1.250');
    expect(gana.querySelector('.panel-resultado__creditos').dataset.signo).toBe('positivo');

    const pierde = panelDeResultado({ victoria: false, creditos: -350 });
    expect(pierde.querySelector('.panel-resultado__creditos').textContent).toBe('-350');
  });

  test('sin dato de creditos no se inventa un cero', () => {
    expect(
      panelDeResultado({ victoria: true }).querySelector('.panel-resultado__creditos'),
    ).toBeNull();
    expect(
      panelDeResultado({ victoria: true, creditos: 0 }).querySelector('.panel-resultado__creditos'),
    ).toBeNull();
  });

  test('las acciones de despues se pintan si las hay', () => {
    const boton = document.createElement('button');
    const nodo = panelDeResultado({ victoria: true, acciones: [boton] });
    expect(nodo.querySelector('.panel-resultado__acciones').children).toHaveLength(1);
  });
});

describe('distintivoDeCreditos', () => {
  test('la cifra lleva separador de miles y el lector oye «creditos»', () => {
    const nodo = distintivoDeCreditos(1250);
    expect(nodo.querySelector('.distintivo-credito__cifra').textContent).toBe('1.250');
    expect(nodo.getAttribute('aria-label')).toBe('1.250 creditos');
  });

  test('el icono es decorativo: el color oro es marca, no informacion', () => {
    const svg = distintivoDeCreditos(10).querySelector('svg');
    expect(svg.getAttribute('aria-hidden')).toBe('true');
  });

  test('el contexto entra en el nombre accesible', () => {
    const nodo = distintivoDeCreditos(500, { contexto: 'Apuesta' });
    expect(nodo.getAttribute('aria-label')).toBe('Apuesta: 500 creditos');
    expect(nodo.textContent).toContain('Apuesta');
  });

  test('sin dato dice que no lo hay en vez de ensenar un cero falso', () => {
    const nodo = distintivoDeCreditos(null);
    expect(nodo.querySelector('.distintivo-credito__cifra').textContent).toBe('—');
    expect(nodo.getAttribute('aria-label')).toBe('sin dato de creditos');
  });

  test('con signo, solo los positivos lo llevan', () => {
    expect(
      distintivoDeCreditos(40, { conSigno: true }).querySelector('.distintivo-credito__cifra')
        .textContent,
    ).toBe('+40');
    expect(
      distintivoDeCreditos(-40, { conSigno: true }).querySelector('.distintivo-credito__cifra')
        .textContent,
    ).toBe('-40');
  });
});
