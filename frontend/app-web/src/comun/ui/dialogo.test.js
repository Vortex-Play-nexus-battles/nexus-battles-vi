/** Dialogo modal accesible (PR-UX-1). */

import { abrirDialogo, confirmar, confirmarCritico, pedirTexto } from './dialogo.js';
import { boton } from './boton.js';

beforeEach(() => {
  document.body.innerHTML = '';
});

describe('abrirDialogo', () => {
  test('se anuncia como dialogo modal con su titulo asociado', () => {
    const { elemento } = abrirDialogo({ titulo: 'Cancelar la sala', cuerpo: '¿Seguro?' });

    expect(elemento.getAttribute('role')).toBe('dialog');
    expect(elemento.getAttribute('aria-modal')).toBe('true');
    const id = elemento.getAttribute('aria-labelledby');
    expect(document.getElementById(id).textContent).toBe('Cancelar la sala');
  });

  test('Escape cierra y el foco vuelve a donde estaba', () => {
    const disparador = document.createElement('button');
    document.body.append(disparador);
    disparador.focus();

    abrirDialogo({ titulo: 'x', cuerpo: 'y' });
    expect(document.querySelector('.velo')).not.toBeNull();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));

    expect(document.querySelector('.velo')).toBeNull();
    expect(document.activeElement).toBe(disparador);
  });

  test('el clic en el velo cierra, pero el clic dentro no', () => {
    const { elemento } = abrirDialogo({ titulo: 'x', cuerpo: 'y' });

    elemento.click();
    expect(document.querySelector('.velo')).not.toBeNull();

    document.querySelector('.velo').click();
    expect(document.querySelector('.velo')).toBeNull();
  });

  test('con cerrableFuera en falso el velo no cierra', () => {
    abrirDialogo({ titulo: 'x', cuerpo: 'y', cerrableFuera: false });

    document.querySelector('.velo').click();

    expect(document.querySelector('.velo')).not.toBeNull();
  });

  test('el tabulador no se escapa del dialogo', () => {
    const { elemento } = abrirDialogo({
      titulo: 'x',
      cuerpo: 'y',
      acciones: [boton({ texto: 'Aceptar' })],
    });
    const enfocables = elemento.querySelectorAll('button');
    const ultimo = enfocables[enfocables.length - 1];
    ultimo.focus();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }));

    // El foco no se fue a la pagina de detras.
    expect(elemento.contains(document.activeElement)).toBe(true);
  });
});

describe('confirmar', () => {
  test('resuelve true al confirmar y cierra', async () => {
    const respuesta = confirmar({ titulo: 'Banear', mensaje: 'No se puede deshacer' });

    document.querySelector('[data-accion="confirmar"]').click();

    await expect(respuesta).resolves.toBe(true);
    expect(document.querySelector('.velo')).toBeNull();
  });

  test('resuelve false al cancelar', async () => {
    const respuesta = confirmar({ titulo: 'Banear', mensaje: 'No se puede deshacer' });

    document.querySelector('[data-accion="cancelar"]').click();

    await expect(respuesta).resolves.toBe(false);
  });

  test('cerrar sin decidir (Escape, la equis) resuelve false, no deja la promesa colgada', async () => {
    const conEscape = confirmar({ titulo: 'Cancelar la misión', mensaje: 'x' });
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await expect(conEscape).resolves.toBe(false);

    const conEquis = confirmar({ titulo: 'Cancelar la misión', mensaje: 'x' });
    document.querySelector('[data-accion="cerrar"]').click();
    await expect(conEquis).resolves.toBe(false);
    expect(document.querySelector('.velo')).toBeNull();
  });

  test('admite un cuerpo con más que una frase', async () => {
    const lista = document.createElement('ul');
    lista.append(document.createElement('li'));
    const respuesta = confirmar({ titulo: 'Iniciar', cuerpo: lista, peligro: false });

    expect(document.querySelector('.dialogo ul')).toBe(lista);
    expect(document.querySelector('[data-accion="confirmar"]').className).toContain(
      'boton--primario',
    );
    document.querySelector('[data-accion="confirmar"]').click();
    await expect(respuesta).resolves.toBe(true);
  });

  test('una accion peligrosa se pinta como peligrosa', () => {
    confirmar({ titulo: 'Banear', mensaje: 'x' });

    expect(document.querySelector('.dialogo').className).toContain('dialogo--peligro');
    expect(document.querySelector('[data-accion="confirmar"]').className).toContain(
      'boton--peligro',
    );
  });
});

describe('confirmarCritico (§7.3.9, UXC-7)', () => {
  const abrir = () =>
    confirmarCritico({
      titulo: 'Banear definitivamente a nyx',
      mensaje: 'Esta acción es permanente.',
      consecuencias: ['No podrá volver a entrar con esta cuenta.'],
      palabra: 'nyx',
      textoConfirmar: 'Banear',
    });
  const escribir = (texto) => {
    const campo = document.querySelector('[role="dialog"] input');
    campo.value = texto;
    campo.dispatchEvent(new Event('input'));
    return campo;
  };

  test('lista lo que va a pasar y no deja confirmar sin escribir la palabra', async () => {
    const promesa = abrir();
    const caja = document.querySelector('[role="dialog"]');
    const confirmarBoton = caja.querySelector('[data-accion="confirmar"]');

    expect(caja.className).toContain('dialogo--peligro');
    expect(caja.querySelector('label').textContent).toBe('Escribe «nyx» para confirmar');
    expect(caja.querySelectorAll('.confirmacion-critica__consecuencias li')).toHaveLength(1);
    expect(confirmarBoton.disabled).toBe(true);
    escribir('NYX');
    expect(confirmarBoton.disabled).toBe(true);
    escribir(' nyx ');
    expect(confirmarBoton.disabled).toBe(false);
    confirmarBoton.click();
    await expect(promesa).resolves.toBe(true);
    expect(document.querySelector('[role="dialog"]')).toBeNull();
  });

  test('Enter confirma solo si coincide; cerrar sin decidir es que no', async () => {
    const promesa = abrir();
    const campo = escribir('otra');
    campo.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    expect(document.querySelector('[role="dialog"]')).not.toBeNull();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await expect(promesa).resolves.toBe(false);
  });
});

describe('pedirTexto (UXC-7, en lugar de window.prompt)', () => {
  test('trae el valor escrito, con su etiqueta, y devuelve lo que se acepta', async () => {
    const promesa = pedirTexto({ titulo: 'Editar «troll»', etiqueta: 'Término', valor: 'troll' });
    const caja = document.querySelector('[role="dialog"]');
    const campo = caja.querySelector('input');

    expect(caja.querySelector(`label[for="${campo.id}"]`).textContent).toBe('Término');
    expect(campo.value).toBe('troll');
    campo.value = '  troll2  ';
    campo.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    await expect(promesa).resolves.toBe('troll2');
  });

  test('cancelar devuelve null', async () => {
    const promesa = pedirTexto({ titulo: 'Editar', etiqueta: 'Término' });
    document.querySelector('[data-accion="cancelar"]').click();
    await expect(promesa).resolves.toBeNull();
  });
});
