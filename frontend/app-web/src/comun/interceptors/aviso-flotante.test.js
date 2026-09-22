/**
 * El aviso de acceso denegado (HU-RBAC-004).
 *
 * Lo que se afirma aquí es lo que antes no se cumplía: que el texto del error
 * —que viene del servidor— se pinta como **texto**, no como marcado.
 *
 * El interceptor lo construía así:
 *
 * ```js
 * toast.innerHTML = `<span…>⛔</span> <span>${mensaje}</span>`;
 * ```
 *
 * `mensaje` es el `detail` o el `title` del problem detail que devuelve el
 * servicio, y esto es el interceptor que usan **todas** las vistas del
 * producto. Cualquier texto de error con marcado dentro se interpretaba.
 */

import { jest } from '@jest/globals';

import { fetchWithHttpErrorInterceptor } from './http-error.interceptor.js';

/** Respuesta 403 con problem details, como la devuelven los servicios. */
function prohibido(detail) {
  return Promise.resolve({
    ok: false,
    status: 403,
    headers: { get: () => 'application/problem+json' },
    clone() {
      return this;
    },
    json: () => Promise.resolve({ title: 'Acceso denegado', detail, status: 403 }),
    text: () => Promise.resolve(''),
  });
}

beforeEach(() => {
  document.body.innerHTML = '';
  jest.useFakeTimers();
});

afterEach(() => {
  jest.useRealTimers();
});

describe('aviso de acceso denegado', () => {
  test('el texto del servidor se pinta como TEXTO, no como marcado', async () => {
    globalThis.fetch = jest.fn(() => prohibido('<img src=x onerror=alert(1)> sin permiso'));

    await fetchWithHttpErrorInterceptor('/api/v1/lo-que-sea');

    const aviso = document.getElementById('nexus-rbac-toast');
    expect(aviso).not.toBeNull();
    expect(aviso.querySelector('img')).toBeNull();
    expect(aviso.textContent).toContain('<img');
  });

  test('usa el componente del kit en vez de estilos en linea', async () => {
    globalThis.fetch = jest.fn(() => prohibido('No tienes permiso.'));

    await fetchWithHttpErrorInterceptor('/api/v1/lo-que-sea');

    const aviso = document.getElementById('nexus-rbac-toast');
    expect(aviso.classList.contains('aviso')).toBe(true);
    expect(aviso.classList.contains('aviso--error')).toBe(true);
    expect(aviso.classList.contains('aviso-flotante')).toBe(true);
    expect(aviso.getAttribute('style')).toBeNull();
  });

  test('se anuncia al momento: es un rechazo, no una nota', async () => {
    globalThis.fetch = jest.fn(() => prohibido('No tienes permiso.'));

    await fetchWithHttpErrorInterceptor('/api/v1/lo-que-sea');

    expect(document.getElementById('nexus-rbac-toast').getAttribute('role')).toBe('alert');
  });

  test('aparece, y a los 4,5 s se retira solo', async () => {
    globalThis.fetch = jest.fn(() => prohibido('No tienes permiso.'));

    await fetchWithHttpErrorInterceptor('/api/v1/lo-que-sea');
    const aviso = document.getElementById('nexus-rbac-toast');
    expect(aviso.dataset.visible).toBe('si');

    jest.advanceTimersByTime(4500);

    expect(aviso.dataset.visible).toBeUndefined();
  });

  test('dos rechazos seguidos reutilizan el mismo aviso, no lo apilan', async () => {
    globalThis.fetch = jest.fn(() => prohibido('Primero.'));
    await fetchWithHttpErrorInterceptor('/api/v1/uno');
    globalThis.fetch = jest.fn(() => prohibido('Segundo.'));
    await fetchWithHttpErrorInterceptor('/api/v1/dos');

    expect(document.querySelectorAll('#nexus-rbac-toast')).toHaveLength(1);
    expect(document.getElementById('nexus-rbac-toast').textContent).toBe('Segundo.');
  });

  test('si la vista trae su propio hueco, se usa ese y no flota nada', async () => {
    document.body.innerHTML = '<p id="nexus-rbac-forbidden" hidden></p>';
    globalThis.fetch = jest.fn(() => prohibido('No tienes permiso.'));

    await fetchWithHttpErrorInterceptor('/api/v1/lo-que-sea');

    const hueco = document.getElementById('nexus-rbac-forbidden');
    expect(hueco.hidden).toBe(false);
    expect(hueco.textContent).toBe('No tienes permiso.');
    expect(document.getElementById('nexus-rbac-toast')).toBeNull();
  });
});
