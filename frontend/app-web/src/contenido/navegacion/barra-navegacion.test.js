/**
 * HU-INV-004 - Barra superior de navegacion.
 * Fuente: Proyecto Integrador II, secciones 7.1-7.1.1, pp. 34-35.
 */
import { construirBarra, SECCIONES } from './barra-navegacion.js';

function accesos(barra) {
  return [...barra.querySelectorAll('.barra__acceso')];
}

describe('Barra superior de navegacion', () => {

  // --- Criterio 1 ---------------------------------------------------------

  test('presenta las seis secciones del alcance del grupo, en orden', () => {
    const barra = construirBarra();

    expect(accesos(barra).map((a) => a.textContent)).toEqual([
      'Jugar online', 'Misiones', 'Torneo', 'Mi inventario', 'Subasta', 'Mi Cuenta',
    ]);
  });

  test('presenta la busqueda de productos', () => {
    const barra = construirBarra();
    const busqueda = barra.querySelector('.barra__busqueda input');

    expect(busqueda).not.toBeNull();
    expect(busqueda.type).toBe('search');
    expect(busqueda.getAttribute('aria-label')).toMatch(/buscar productos/i);
  });

  test('no inventa secciones fuera de las declaradas', () => {
    expect(SECCIONES).toHaveLength(6);
    expect(accesos(construirBarra())).toHaveLength(SECCIONES.length);
  });

  // --- Criterio 2 ---------------------------------------------------------

  test('marca como activa la seccion indicada, y solo esa', () => {
    const barra = construirBarra({ seccionActiva: 'inventario' });

    const activos = accesos(barra).filter(
      (a) => a.classList.contains('barra__acceso--activo'));
    expect(activos).toHaveLength(1);
    expect(activos[0].textContent).toBe('Mi inventario');
    expect(activos[0].getAttribute('aria-current')).toBe('page');
  });

  test('sin seccion activa no marca ninguna', () => {
    const barra = construirBarra();

    expect(accesos(barra).filter(
      (a) => a.classList.contains('barra__acceso--activo'))).toHaveLength(0);
  });

  test('pulsar un acceso navega a la ruta de su seccion', () => {
    const visitadas = [];
    const barra = construirBarra({ navegar: (ruta) => visitadas.push(ruta) });

    accesos(barra).find((a) => a.textContent === 'Misiones').click();

    expect(visitadas).toEqual(['/misiones']);
  });

  // --- Criterio 3 ---------------------------------------------------------

  test('un visitante que abre Mi Cuenta ve unicamente la opcion de registro', () => {
    const barra = construirBarra({ sesion: { autenticado: false } });

    accesos(barra).find((a) => a.textContent === 'Mi Cuenta').click();

    const opciones = [...barra.querySelectorAll('.barra__opcion-cuenta')];
    expect(opciones.map((o) => o.textContent)).toEqual(['Registrarse']);
  });

  test('el visitante no navega fuera al abrir Mi Cuenta: se le ofrece registrarse', () => {
    const visitadas = [];
    const barra = construirBarra({
      sesion: { autenticado: false }, navegar: (r) => visitadas.push(r),
    });

    accesos(barra).find((a) => a.textContent === 'Mi Cuenta').click();

    expect(visitadas).toEqual([]);
  });

  test('un jugador autenticado ve las opciones de su cuenta, no la de registro', () => {
    const barra = construirBarra({ sesion: { autenticado: true } });

    accesos(barra).find((a) => a.textContent === 'Mi Cuenta').click();

    const opciones = [...barra.querySelectorAll('.barra__opcion-cuenta')]
      .map((o) => o.textContent);
    expect(opciones).not.toContain('Registrarse');
    expect(opciones.length).toBeGreaterThan(0);
  });

  test('sin sesion declarada se asume visitante, no jugador', () => {
    const barra = construirBarra();

    accesos(barra).find((a) => a.textContent === 'Mi Cuenta').click();

    expect([...barra.querySelectorAll('.barra__opcion-cuenta')]
      .map((o) => o.textContent)).toEqual(['Registrarse']);
  });
});
