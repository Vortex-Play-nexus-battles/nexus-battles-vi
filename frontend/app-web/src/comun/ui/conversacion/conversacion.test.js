/**
 * UXC-6 — la conversación compartida: globo, hilo y redactor.
 */

import { jest } from '@jest/globals';

import {
  burbujaDeMensaje,
  cuandoFue,
  etiquetaDeDia,
  inicialDe,
  mensajeDelSistema,
  quienEscribe,
} from './mensaje.js';
import { hiloDeConversacion } from './hilo.js';
import { prepararRedactor, redactorDeMensaje } from './redactor.js';

const YO = 'uid-yo';
const AHORA = new Date(2026, 8, 22, 18, 30); // martes 22 de septiembre de 2026, 18:30

const de = (id, apodo, texto, enviadoEn, extra = {}) => ({
  id,
  tipo: 'chat.mensaje',
  autor: { id: apodo === 'yo' ? YO : `uid-${apodo}`, apodo },
  texto,
  enviadoEn,
  ...extra,
});

const esperar = async () => {
  for (let i = 0; i < 4; i += 1) {
    await Promise.resolve();
  }
};

describe('el globo', () => {
  test('quién escribe: tuyo por uid, del sistema por tipo, y si no, de otro', () => {
    expect(quienEscribe({ autor: { id: YO } }, YO)).toBe('yo');
    expect(quienEscribe({ autor: { id: 'otro' } }, YO)).toBe('otro');
    expect(quienEscribe({ tipo: 'sistema', autor: { id: YO } }, YO)).toBe('sistema');
    // Sin sesión conocida nada es tuyo: mejor «otro» que un «Tú» falso.
    expect(quienEscribe({ autor: { id: YO } }, null)).toBe('otro');
  });

  test('tres señales además del color: palabra, posición (clase) y forma', () => {
    const mio = burbujaDeMensaje(de('1', 'yo', 'hola', AHORA.toISOString()), { miId: YO });
    const ajeno = burbujaDeMensaje(de('2', 'Bruma', 'buenas', AHORA.toISOString()), { miId: YO });
    const sistema = mensajeDelSistema('Conexión recuperada.', { tono: 'exito', cuando: AHORA });

    expect(mio.className).toContain('mensaje--yo');
    expect(mio.querySelector('.mensaje__autor').textContent).toBe('Tú');
    expect(mio.querySelector('.mensaje__inicial')).toBeNull();

    expect(ajeno.className).toContain('mensaje--otro');
    expect(ajeno.querySelector('.mensaje__autor').textContent).toBe('Bruma');
    expect(ajeno.querySelector('.mensaje__inicial').getAttribute('aria-hidden')).toBe('true');

    expect(sistema.className).toContain('mensaje--sistema');
    expect(sistema.className).toContain('mensaje--exito');
    expect(sistema.querySelector('.mensaje__autor').textContent).toBe('Sistema');
  });

  test('la hora va en un <time> con el momento completo', () => {
    const globo = burbujaDeMensaje(de('1', 'Bruma', 'x', '2026-09-22T15:02:00Z'));
    const hora = globo.querySelector('time');
    expect(hora.getAttribute('datetime')).toBe('2026-09-22T15:02:00.000Z');
    expect(hora.textContent).toMatch(/\d{1,2}:\d{2}/);
    expect(hora.getAttribute('title')).toMatch(/septiembre/);
  });

  test('lo tuyo dice su entrega con palabras, y «No se envió» trae «Reintentar»', () => {
    const alReintentar = jest.fn();
    const enviado = burbujaDeMensaje(de('1', 'yo', 'x', AHORA.toISOString()), {
      miId: YO,
      entrega: 'ENVIADO',
    });
    const fallido = burbujaDeMensaje(de('2', 'yo', 'y', AHORA.toISOString()), {
      miId: YO,
      entrega: 'FALLIDO',
      alReintentar,
    });
    const ajeno = burbujaDeMensaje(de('3', 'Bruma', 'z', AHORA.toISOString()), {
      miId: YO,
      entrega: 'ENVIADO',
    });

    expect(enviado.querySelector('.mensaje__entrega').textContent).toBe('Enviado');
    expect(fallido.querySelector('.mensaje__entrega').textContent).toContain('No se envió');
    fallido.querySelector('[data-accion="reintentar-mensaje"]').click();
    expect(alReintentar).toHaveBeenCalled();
    // La entrega es de lo tuyo: en un mensaje ajeno no se pinta.
    expect(ajeno.querySelector('.mensaje__entrega')).toBeNull();
  });

  test('el texto va como texto: nada de marcado del servidor', () => {
    const globo = burbujaDeMensaje(
      de('1', 'Bruma', '<img src=x onerror=alert(1)>', AHORA.toISOString()),
    );
    expect(globo.querySelector('img')).toBeNull();
    expect(globo.querySelector('.mensaje__texto').textContent).toBe('<img src=x onerror=alert(1)>');
  });

  test('días y momentos en palabras', () => {
    expect(etiquetaDeDia(new Date(2026, 8, 22, 9), AHORA)).toBe('Hoy');
    expect(etiquetaDeDia(new Date(2026, 8, 21, 23), AHORA)).toBe('Ayer');
    expect(etiquetaDeDia(new Date(2026, 8, 14, 12), AHORA)).toMatch(/^Lunes,? 14 de septiembre$/);
    expect(cuandoFue(new Date(2026, 8, 21, 10), AHORA)).toBe('Ayer');
    expect(cuandoFue(new Date(2026, 7, 1, 10), AHORA)).toMatch(/^1 (de )?ago$/);
    expect(inicialDe(' bruma')).toBe('B');
    expect(inicialDe('')).toBe('?');
  });
});

describe('el hilo', () => {
  function montar(opciones = {}) {
    document.body.innerHTML = '<div class="conversacion__cuerpo"><ol></ol></div>';
    const lista = document.querySelector('ol');
    return {
      lista,
      hilo: hiloDeConversacion(lista, { miId: YO, ahora: () => AHORA, ...opciones }),
    };
  }
  const textos = (lista) =>
    [...lista.querySelectorAll('li')].map((li) =>
      li.classList.contains('conversacion__dia')
        ? `— ${li.textContent} —`
        : li.querySelector('.mensaje__texto').textContent,
    );

  test('es una región con nombre que se puede enfocar para leer con el teclado', () => {
    const { lista } = montar({ nombre: 'Mensajes con Bruma' });
    expect(lista.getAttribute('tabindex')).toBe('0');
    expect(lista.getAttribute('aria-label')).toBe('Mensajes con Bruma');
  });

  test('separa por días y ordena por momento', () => {
    const { lista, hilo } = montar();
    hilo.reemplazar([
      de('2', 'Bruma', 'hoy', new Date(2026, 8, 22, 10).toISOString()),
      de('1', 'Bruma', 'ayer', new Date(2026, 8, 21, 22).toISOString()),
    ]);
    expect(textos(lista)).toEqual(['— Ayer —', 'ayer', '— Hoy —', 'hoy']);
    expect(hilo.cantidad()).toBe(2);
  });

  test('al sincronizar, lo que falta se intercala en su momento y lo repetido no se duplica', () => {
    const { lista, hilo } = montar();
    hilo.reemplazar([de('1', 'Bruma', 'uno', new Date(2026, 8, 22, 10).toISOString())]);
    hilo.sistema('Se cortó la conexión.'); // 18:30
    const nuevos = hilo.sincronizar([
      de('1', 'Bruma', 'uno', new Date(2026, 8, 22, 10).toISOString()),
      de('2', 'Bruma', 'dos', new Date(2026, 8, 22, 11).toISOString()),
    ]);
    expect(nuevos).toBe(1);
    expect(textos(lista)).toEqual(['— Hoy —', 'uno', 'dos', 'Se cortó la conexión.']);
  });

  test('un mensaje tuyo pendiente se sustituye por su confirmación, sin moverse', () => {
    const { lista, hilo } = montar();
    hilo.agregar(de(null, 'yo', 'va', AHORA.toISOString()), { entrega: 'ENVIANDO', clave: 'p-1' });
    expect(lista.querySelector('.mensaje__entrega').textContent).toBe('Enviando…');

    hilo.actualizar('p-1', de('srv-9', 'yo', 'va', AHORA.toISOString()), {
      entrega: 'ENVIADO',
      id: 'srv-9',
    });
    expect(lista.querySelectorAll('li.mensaje')).toHaveLength(1);
    expect(lista.querySelector('.mensaje__entrega').textContent).toBe('Enviado');
    expect(hilo.tiene('srv-9')).toBe(true);
    expect(hilo.tiene('p-1')).toBe(false);
  });

  test('si subiste a leer, lo nuevo no te arrastra: aparece «Hay mensajes nuevos»', () => {
    const { lista, hilo } = montar();
    hilo.reemplazar([de('1', 'Bruma', 'uno', AHORA.toISOString())]);
    Object.defineProperty(lista, 'scrollHeight', { value: 1000, configurable: true });
    Object.defineProperty(lista, 'clientHeight', { value: 300, configurable: true });
    lista.scrollTop = 100;

    hilo.agregar(de('2', 'Bruma', 'dos', AHORA.toISOString()));
    hilo.agregar(de('3', 'Bruma', 'tres', AHORA.toISOString()));

    const boton = document.querySelector('[data-accion="ver-mensajes-nuevos"]');
    expect(boton.hidden).toBe(false);
    expect(boton.textContent).toBe('Hay 2 mensajes nuevos');
    expect(lista.scrollTop).toBe(100);
    boton.click();
    expect(boton.hidden).toBe(true);
    expect(document.activeElement).toBe(lista);
  });

  test('lo tuyo siempre baja al final, aunque estuvieras arriba', () => {
    const { lista, hilo } = montar();
    hilo.reemplazar([de('1', 'Bruma', 'uno', AHORA.toISOString())]);
    Object.defineProperty(lista, 'scrollHeight', { value: 1000, configurable: true });
    Object.defineProperty(lista, 'clientHeight', { value: 300, configurable: true });
    lista.scrollTop = 100;

    hilo.agregar(de('2', 'yo', 'mío', AHORA.toISOString()));
    expect(lista.scrollTop).toBe(1000);
  });

  test('avisa de cuántos mensajes hay, para pintar el vacío', () => {
    const alCambiar = jest.fn();
    const { hilo } = montar({ alCambiar });
    hilo.reemplazar([]);
    expect(alCambiar).toHaveBeenLastCalledWith(0);
    hilo.agregar(de('1', 'Bruma', 'uno', AHORA.toISOString()));
    expect(alCambiar).toHaveBeenLastCalledWith(1);
  });
});

describe('el redactor', () => {
  function montar(alEnviar = jest.fn(() => true)) {
    document.body.innerHTML = '';
    const formulario = redactorDeMensaje({ id: 'm', etiqueta: 'Mensaje para Bruma', maximo: 20 });
    document.body.append(formulario);
    const redactor = prepararRedactor(formulario, { maximo: 20, alEnviar });
    return {
      formulario,
      redactor,
      alEnviar,
      campo: formulario.querySelector('textarea'),
      boton: formulario.querySelector('[type="submit"]'),
    };
  }

  test('el campo tiene nombre, pista y cuenta de caracteres', () => {
    const { campo, formulario } = montar();
    expect(formulario.querySelector('label').textContent).toBe('Mensaje para Bruma');
    expect(campo.getAttribute('aria-describedby')).toContain('m-pista');
    campo.value = 'hola';
    campo.dispatchEvent(new Event('input'));
    expect(formulario.querySelector('[data-zona="cuenta"]').textContent).toBe('4/20');
    campo.value = 'x'.repeat(19);
    campo.dispatchEvent(new Event('input'));
    expect(formulario.querySelector('.redactor-mensaje__cuenta--cerca')).not.toBeNull();
  });

  test('envía recortado y limpia; si alEnviar dice que no salió, el texto se queda', async () => {
    const alEnviar = jest.fn().mockReturnValueOnce(true).mockReturnValueOnce(false);
    const { formulario, campo } = montar(alEnviar);

    campo.value = '  hola  ';
    formulario.dispatchEvent(new Event('submit', { cancelable: true }));
    await esperar();
    expect(alEnviar).toHaveBeenCalledWith('hola');
    expect(campo.value).toBe('');

    campo.value = 'otra';
    formulario.dispatchEvent(new Event('submit', { cancelable: true }));
    await esperar();
    expect(campo.value).toBe('otra');
  });

  test('un texto vacío no se envía', async () => {
    const { formulario, campo, alEnviar } = montar();
    campo.value = '   ';
    formulario.dispatchEvent(new Event('submit', { cancelable: true }));
    await esperar();
    expect(alEnviar).not.toHaveBeenCalled();
  });

  test('esperando conexión: no envía pero se puede seguir escribiendo, y lo dice', () => {
    const { redactor, campo, boton, formulario } = montar();
    redactor.esperarConexion(true);
    expect(boton.disabled).toBe(true);
    expect(campo.disabled).toBe(false);
    expect(formulario.querySelector('[data-zona="espera"]').textContent).toContain('no se pierde');
    redactor.esperarConexion(false);
    expect(boton.disabled).toBe(false);
    expect(formulario.querySelector('[data-zona="espera"]').hidden).toBe(true);
  });

  test('bloqueado: el campo se sustituye por el motivo, el enlace y la cuenta atrás', () => {
    const { redactor, campo, boton, formulario } = montar();
    const hasta = new Date(Date.now() + 2 * 86_400_000).toISOString();
    redactor.bloquear({
      titulo: 'Tienes un silencio activo',
      detalle: 'Mientras dure la sanción tus mensajes no salen.',
      enlace: { texto: 'Ver mis sanciones', href: '/sanciones' },
      hasta,
    });

    const bloqueo = formulario.querySelector('[data-zona="bloqueo"]');
    expect(bloqueo.hidden).toBe(false);
    expect(bloqueo.textContent).toContain('Tienes un silencio activo');
    expect(bloqueo.querySelector('a').getAttribute('href')).toBe('/sanciones');
    expect(bloqueo.querySelector('time').getAttribute('datetime')).toBe(hasta);
    expect(campo.disabled).toBe(true);
    expect(boton.disabled).toBe(true);
    expect(redactor.bloqueado).toBe(true);

    redactor.desbloquear();
    expect(bloqueo.hidden).toBe(true);
    expect(campo.disabled).toBe(false);
    expect(boton.disabled).toBe(false);
  });

  test('restaurar devuelve el texto solo si el campo está vacío', () => {
    const { redactor, campo } = montar();
    redactor.restaurar('lo que no salió');
    expect(campo.value).toBe('lo que no salió');
    campo.value = 'otra cosa';
    redactor.restaurar('lo que no salió');
    expect(campo.value).toBe('otra cosa');
  });
});
