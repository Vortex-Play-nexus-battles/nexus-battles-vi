/**
 * Pruebas de `pestanas.js`. El patrón ARIA de tabs no es decorativo: sin
 * `aria-selected`, sin `tabindex` rotatorio y sin flechas, unas pestañas son
 * botones que cambian la pantalla sin decirlo a quien no la ve.
 */

import { montarPestanas } from './pestanas.js';

function panel(texto) {
  const nodo = document.createElement('section');
  nodo.textContent = texto;
  return nodo;
}

function tresPestanas() {
  return [
    { id: 'resumen', etiqueta: 'Resumen', panel: panel('resumen') },
    { id: 'perfil', etiqueta: 'Perfil', panel: panel('perfil') },
    { id: 'historial', etiqueta: 'Historial', panel: panel('historial') },
  ];
}

beforeEach(() => {
  document.body.innerHTML = '<div id="raiz"></div>';
  window.history.replaceState(null, '', ' ');
});

describe('montarPestanas()', () => {
  test('monta el tablist, los tabs y los paneles enlazados entre si', () => {
    const raiz = document.getElementById('raiz');

    montarPestanas(raiz, tresPestanas(), { hash: false });

    const lista = raiz.querySelector('[role="tablist"]');
    expect(lista).not.toBeNull();
    const tabs = raiz.querySelectorAll('[role="tab"]');
    expect(tabs).toHaveLength(3);
    for (const tab of tabs) {
      const panelDelTab = raiz.querySelector(`[aria-labelledby="${tab.id}"]`);
      expect(panelDelTab).not.toBeNull();
      expect(panelDelTab.getAttribute('role')).toBe('tabpanel');
    }
  });

  test('sin pestana pedida abre la primera y esconde las demas', () => {
    const raiz = document.getElementById('raiz');
    const pestanas = tresPestanas();

    montarPestanas(raiz, pestanas, { hash: false });

    expect(pestanas[0].panel.hidden).toBe(false);
    expect(pestanas[1].panel.hidden).toBe(true);
    expect(pestanas[2].panel.hidden).toBe(true);
  });

  test('solo la pestana activa entra en el orden de tabulacion', () => {
    const raiz = document.getElementById('raiz');

    montarPestanas(raiz, tresPestanas(), { hash: false, activa: 'perfil' });

    const tabs = [...raiz.querySelectorAll('[role="tab"]')];
    expect(tabs.map((t) => t.getAttribute('tabindex'))).toEqual(['-1', '0', '-1']);
    expect(tabs.map((t) => t.getAttribute('aria-selected'))).toEqual(['false', 'true', 'false']);
  });

  test('un clic cambia de panel y avisa con alCambiar', () => {
    const raiz = document.getElementById('raiz');
    const pestanas = tresPestanas();
    const visto = [];

    montarPestanas(raiz, pestanas, { hash: false, alCambiar: (id) => visto.push(id) });
    raiz.querySelector('[data-pestana="historial"]').click();

    expect(pestanas[2].panel.hidden).toBe(false);
    expect(pestanas[0].panel.hidden).toBe(true);
    expect(visto).toEqual(['resumen', 'historial']);
  });

  test('las flechas recorren las pestanas y dan la vuelta', () => {
    const raiz = document.getElementById('raiz');
    const control = montarPestanas(raiz, tresPestanas(), { hash: false });
    const lista = raiz.querySelector('[role="tablist"]');

    lista.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    expect(control.activa()).toBe('perfil');

    lista.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft', bubbles: true }));
    expect(control.activa()).toBe('resumen');

    lista.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft', bubbles: true }));
    expect(control.activa()).toBe('historial');
  });

  test('Inicio y Fin saltan a los extremos', () => {
    const raiz = document.getElementById('raiz');
    const control = montarPestanas(raiz, tresPestanas(), { hash: false, activa: 'perfil' });
    const lista = raiz.querySelector('[role="tablist"]');

    lista.dispatchEvent(new KeyboardEvent('keydown', { key: 'End', bubbles: true }));
    expect(control.activa()).toBe('historial');

    lista.dispatchEvent(new KeyboardEvent('keydown', { key: 'Home', bubbles: true }));
    expect(control.activa()).toBe('resumen');
  });

  test('una tecla cualquiera no mueve la pestana', () => {
    const raiz = document.getElementById('raiz');
    const control = montarPestanas(raiz, tresPestanas(), { hash: false });

    raiz
      .querySelector('[role="tablist"]')
      .dispatchEvent(new KeyboardEvent('keydown', { key: 'a', bubbles: true }));

    expect(control.activa()).toBe('resumen');
  });

  test('el hash de la URL manda sobre la pestana por omision', () => {
    window.history.replaceState(null, '', '#historial');
    const raiz = document.getElementById('raiz');

    const control = montarPestanas(raiz, tresPestanas(), { activa: 'perfil' });

    expect(control.activa()).toBe('historial');
  });

  test('un hash que no existe no deja la vista sin pestana', () => {
    window.history.replaceState(null, '', '#inventado');
    const raiz = document.getElementById('raiz');

    const control = montarPestanas(raiz, tresPestanas(), { activa: 'perfil' });

    expect(control.activa()).toBe('perfil');
  });

  test('cambiar de pestana reescribe el hash sin llenar el historial', () => {
    const raiz = document.getElementById('raiz');
    const largoAntes = window.history.length;

    const control = montarPestanas(raiz, tresPestanas());
    control.mostrar('perfil');

    expect(window.location.hash).toBe('#perfil');
    expect(window.history.length).toBe(largoAntes);
  });

  test('mostrar() desde fuera mueve la pestana igual que un clic', () => {
    const raiz = document.getElementById('raiz');
    const pestanas = tresPestanas();

    const control = montarPestanas(raiz, pestanas, { hash: false });
    control.mostrar('perfil');

    expect(pestanas[1].panel.hidden).toBe(false);
    expect(raiz.querySelector('[data-pestana="perfil"]').getAttribute('aria-selected')).toBe(
      'true',
    );
  });
});
