/**
 * Centro de ayuda — ms-chatbot.yaml 1.8.0 (7.4.3).
 */

import { jest } from '@jest/globals';

import { ErrorDelChatbot } from '../comun/cliente-chatbot.js';
import {
  TEXTOS_AYUDA,
  filtrar,
  montarCentroDeAyuda,
  nombreDeCategoria,
  normalizar,
  temaCoincide,
} from './ayuda.js';

const esperar = () => new Promise((resolver) => setTimeout(resolver, 0));

const CENTRO = {
  categorias: [
    {
      categoria: 'CUENTA_Y_REGISTRO',
      ancla: 'categoria-cuenta-y-registro',
      temas: [
        {
          titulo: 'Recuperar la contraseña',
          respuestaEs:
            'Para recuperarla: 1) Ve a «¿Olvidaste tu contraseña?». 2) Escribe tu correo.',
          respuestaEn: 'To recover it: 1) Go to the login. 2) Type your email.',
          pasosEs: ['Ve a «¿Olvidaste tu contraseña?».', 'Escribe tu correo.'],
          pasosEn: ['Go to the login.', 'Type your email.'],
        },
        {
          titulo: 'Cambiar el apodo',
          respuestaEs: 'Desde Mi Cuenta, en «Perfil».',
          respuestaEn: null,
          pasosEs: [],
          pasosEn: [],
        },
      ],
    },
    {
      categoria: 'SUBASTA_Y_COMERCIO',
      ancla: 'categoria-subasta-y-comercio',
      temas: [
        {
          titulo: 'Cómo pujar',
          respuestaEs: 'Entra a la subasta y escribe tu oferta.',
          respuestaEn: 'Open the auction and bid.',
          pasosEs: [],
          pasosEn: [],
        },
      ],
    },
    {
      categoria: 'FAQ_GENERAL',
      ancla: 'categoria-faq-general',
      temas: [
        {
          titulo: 'Qué es el juego',
          respuestaEs: 'Un juego de batallas por turnos.',
          respuestaEn: null,
          pasosEs: [],
          pasosEn: [],
        },
      ],
    },
  ],
};

function montar({ centroDeAyuda = jest.fn(async () => CENTRO), hash = '' } = {}) {
  const raiz = document.createElement('div');
  document.body.replaceChildren(raiz);
  const vista = montarCentroDeAyuda(raiz, { cliente: { centroDeAyuda }, ubicacion: { hash } });
  return { raiz, vista, centroDeAyuda };
}

const buscador = (raiz) => raiz.querySelector('input[name="buscar"]');
const titulos = (raiz) =>
  [...raiz.querySelectorAll('.ayuda__pregunta')].map((resumen) => resumen.textContent);

function buscar(raiz, texto) {
  buscador(raiz).value = texto;
  buscador(raiz).dispatchEvent(new Event('input'));
}

describe('funciones puras', () => {
  test('normalizar quita tildes, mayúsculas y espacios de más', () => {
    expect(normalizar('  Contraseña   OLVIDADA ')).toBe('contrasena olvidada');
    expect(normalizar(null)).toBe('');
  });

  test('nombreDeCategoria usa el nombre del jugador; la general es «Preguntas generales»', () => {
    expect(nombreDeCategoria('CUENTA_Y_REGISTRO')).toBe('Mi cuenta');
    expect(nombreDeCategoria('FAQ_GENERAL')).toBe('Preguntas generales');
    expect(nombreDeCategoria('OTRA')).toBe('OTRA');
  });

  test('un tema coincide si cada palabra está en su título o en su respuesta, en los dos idiomas', () => {
    const tema = CENTRO.categorias[0].temas[0];
    expect(temaCoincide(tema, '')).toBe(true);
    expect(temaCoincide(tema, 'contrasena correo')).toBe(true);
    expect(temaCoincide(tema, 'email')).toBe(true);
    expect(temaCoincide(tema, 'contrasena subasta')).toBe(false);
  });

  test('filtrar deja fuera las categorías que se quedan sin temas', () => {
    expect(filtrar(CENTRO.categorias, 'subasta').map((grupo) => grupo.categoria)).toEqual([
      'SUBASTA_Y_COMERCIO',
    ]);
    expect(filtrar(CENTRO.categorias, '')).toHaveLength(3);
  });
});

describe('los cuatro estados', () => {
  test('mientras carga muestra el esqueleto y oculta el buscador', () => {
    const { raiz, vista } = montar({ centroDeAyuda: jest.fn(() => new Promise(() => {})) });

    vista.cargar();

    expect(raiz.querySelector('[data-zona="ayuda"] [data-estado="cargando"]')).not.toBeNull();
    expect(buscador(raiz).closest('.campo').hidden).toBe(true);
  });

  test('pinta las categorías con su ancla, el índice y los temas', async () => {
    const { raiz, vista, centroDeAyuda } = montar();

    await vista.cargar();

    expect(centroDeAyuda).toHaveBeenCalledTimes(1);
    expect(raiz.querySelector('h1').textContent).toBe(TEXTOS_AYUDA.titulo);
    const secciones = [...raiz.querySelectorAll('section.ayuda__categoria')];
    expect(secciones.map((seccion) => seccion.id)).toEqual([
      'categoria-cuenta-y-registro',
      'categoria-subasta-y-comercio',
      'categoria-faq-general',
    ]);
    expect(secciones[0].querySelector('h2').textContent).toBe('Mi cuenta');
    const indice = [...raiz.querySelectorAll('[data-zona="indice-ayuda"] a')];
    expect(indice.map((enlace) => enlace.getAttribute('href'))).toEqual([
      '#categoria-cuenta-y-registro',
      '#categoria-subasta-y-comercio',
      '#categoria-faq-general',
    ]);
    expect(indice[0].textContent).toBe('Mi cuenta (2)');
    expect(raiz.querySelector('[data-zona="resumen-ayuda"]').textContent).toBe(
      '4 temas en 3 categorías',
    );
    expect(titulos(raiz)).toEqual([
      'Recuperar la contraseña',
      'Cambiar el apodo',
      'Cómo pujar',
      'Qué es el juego',
    ]);
  });

  test('un tema paso a paso trae la introducción y sus pasos; el inglés, aparte', async () => {
    const { raiz, vista } = montar();
    await vista.cargar();

    const tema = raiz.querySelector('details.ayuda__tema');
    expect(tema.open).toBe(false);
    expect(tema.querySelector('.ayuda__respuesta').textContent).toBe('Para recuperarla:');
    expect([...tema.querySelectorAll('.ayuda__pasos li')].map((li) => li.textContent)).toEqual([
      'Ve a «¿Olvidaste tu contraseña?».',
      'Escribe tu correo.',
    ]);
    const ingles = tema.querySelector('.ayuda__ingles');
    expect(ingles.querySelector('summary').textContent).toBe(TEXTOS_AYUDA.enIngles);
    expect(ingles.querySelector('[lang="en"]').textContent).toContain('To recover it');
    // Sin inglés no hay «Leer en inglés».
    expect(
      raiz.querySelectorAll('details.ayuda__tema')[1].querySelector('.ayuda__ingles'),
    ).toBeNull();
  });

  test('sin temas publicados lo dice y no muestra el buscador', async () => {
    const { raiz, vista } = montar({ centroDeAyuda: jest.fn(async () => ({ categorias: [] })) });

    await vista.cargar();

    expect(raiz.textContent).toContain(TEXTOS_AYUDA.vacio);
    expect(buscador(raiz).closest('.campo').hidden).toBe(true);
    expect(raiz.querySelector('[data-zona="indice-ayuda"]').hidden).toBe(true);
  });

  test('si el servicio falla lo dice y deja reintentar', async () => {
    const centroDeAyuda = jest
      .fn()
      .mockRejectedValueOnce(new ErrorDelChatbot(null, 503))
      .mockResolvedValueOnce(CENTRO);
    const { raiz, vista } = montar({ centroDeAyuda });

    await vista.cargar();
    const error = raiz.querySelector('[data-estado="error"]');
    expect(error.textContent).toContain(TEXTOS_AYUDA.errorTitulo);

    raiz.querySelector('[data-accion="reintentar"]').click();
    await esperar();

    expect(centroDeAyuda).toHaveBeenCalledTimes(2);
    expect(titulos(raiz)).toHaveLength(4);
  });
});

describe('buscar', () => {
  test('filtra aquí mismo, abre lo que coincide y lo cuenta', async () => {
    const { raiz, vista, centroDeAyuda } = montar();
    await vista.cargar();

    buscar(raiz, 'Contraseña');

    expect(titulos(raiz)).toEqual(['Recuperar la contraseña']);
    expect(raiz.querySelector('details.ayuda__tema').open).toBe(true);
    expect(raiz.querySelector('[data-zona="resumen-ayuda"]').textContent).toBe(
      '1 tema coincide con «Contraseña»',
    );
    expect([...raiz.querySelectorAll('[data-zona="indice-ayuda"] a')]).toHaveLength(1);
    expect(centroDeAyuda).toHaveBeenCalledTimes(1);
  });

  test('una sola letra no filtra', async () => {
    const { raiz, vista } = montar();
    await vista.cargar();

    buscar(raiz, 'c');

    expect(titulos(raiz)).toHaveLength(4);
  });

  test('sin coincidencias lo dice y «Ver todos los temas» vuelve a la lista', async () => {
    const { raiz, vista } = montar();
    await vista.cargar();

    buscar(raiz, 'dragones');
    expect(raiz.textContent).toContain('Ningún tema coincide con «dragones»');
    expect(raiz.querySelector('[data-zona="indice-ayuda"]').hidden).toBe(true);

    raiz.querySelector('[data-accion="ver-todos"]').click();

    expect(buscador(raiz).value).toBe('');
    expect(titulos(raiz)).toHaveLength(4);
    expect(document.activeElement).toBe(buscador(raiz));
  });
});

describe('anclas', () => {
  test('con #categoria-... el foco va al título de esa categoría', async () => {
    const { raiz, vista } = montar({ hash: '#categoria-subasta-y-comercio' });

    await vista.cargar();

    expect(document.activeElement).toBe(raiz.querySelector('#categoria-subasta-y-comercio h2'));
  });

  test('un ancla que no es de una categoría no mueve nada', async () => {
    const { vista } = montar({ hash: '#"><script>' });

    await vista.cargar();

    expect(document.activeElement).toBe(document.body);
  });
});
