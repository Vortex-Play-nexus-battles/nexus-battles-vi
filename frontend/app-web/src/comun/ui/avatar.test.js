/**
 * Auditoría de DEV del 30-sep: la foto de perfil daba 404 y se veía su texto
 * alternativo. Si una foto falta, queda la inicial.
 */
import { fotoDeCuenta } from './avatar.js';

describe('fotoDeCuenta', () => {
  test('con dirección, la foto con su nombre accesible', () => {
    const foto = fotoDeCuenta({ url: '/avatares-subidos/a.png', apodo: 'ana' });
    expect(foto.tagName).toBe('IMG');
    expect(foto.getAttribute('src')).toBe('/avatares-subidos/a.png');
    expect(foto.getAttribute('alt')).toBe('Avatar de ana');
  });

  test('si la foto no carga, la reemplaza la inicial y no queda el texto alternativo', () => {
    const tarjeta = document.createElement('div');
    const foto = fotoDeCuenta({ url: '/avatares-subidos/no-existe.png', apodo: 'ana' });
    tarjeta.append(foto);

    foto.dispatchEvent(new Event('error'));

    expect(tarjeta.querySelector('img')).toBeNull();
    const inicial = tarjeta.querySelector('[data-avatar="inicial"]');
    expect(inicial.textContent).toBe('A');
    expect(inicial.getAttribute('role')).toBe('img');
    expect(inicial.getAttribute('aria-label')).toBe('Avatar de ana');
  });

  test('sin dirección, directamente la inicial', () => {
    expect(fotoDeCuenta({ apodo: 'bruno' }).textContent).toBe('B');
    expect(fotoDeCuenta().textContent).toBe('?');
  });
});
