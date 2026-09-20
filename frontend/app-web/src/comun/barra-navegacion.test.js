/**
 * HU-INV-004 - Barra superior de navegacion.
 * Fuente: Proyecto Integrador II, secciones 7.1-7.1.1, pp. 34-35.
 */
import { construirBarra, destinoDe, SECCIONES } from './barra-navegacion.js';

function accesos(barra) {
  return [...barra.querySelectorAll('.barra__acceso')];
}

describe('Barra superior de navegacion', () => {
  // --- Criterio 1 ---------------------------------------------------------

  test('presenta las seis secciones del alcance del grupo, en orden', () => {
    const barra = construirBarra();

    expect(accesos(barra).map((a) => a.textContent)).toEqual([
      'Jugar online',
      'Misiones',
      'Torneo',
      'Mi inventario',
      'Subasta',
      'Mi Cuenta',
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

    const activos = accesos(barra).filter((a) => a.classList.contains('barra__acceso--activo'));
    expect(activos).toHaveLength(1);
    expect(activos[0].textContent).toBe('Mi inventario');
    expect(activos[0].getAttribute('aria-current')).toBe('page');
  });

  test('sin seccion activa no marca ninguna', () => {
    const barra = construirBarra();

    expect(
      accesos(barra).filter((a) => a.classList.contains('barra__acceso--activo')),
    ).toHaveLength(0);
  });

  test('pulsar un acceso navega a la ruta de su seccion', () => {
    const visitadas = [];
    const barra = construirBarra({ navegar: (ruta) => visitadas.push(ruta) });

    accesos(barra)
      .find((a) => a.textContent === 'Misiones')
      .click();

    expect(visitadas).toEqual(['/misiones']);
  });

  // --- Los accesos llevan a una vista que existe --------------------------
  //
  // Las rutas de SECCIONES son identificadores logicos: ninguna corresponde a
  // un archivo del repo ni a una `location` del borde. La navegacion por
  // omision las mandaba tal cual a `location.href`, asi que los cinco accesos
  // daban 404 en las nueve vistas que montan la barra sin pasar su propio
  // `navegar`. Esto fija a donde van de verdad.

  describe('destinoDe', () => {
    const BASE = 'http://ejemplo/frontend/app-web/src/comun/barra-navegacion.js';

    test.each([
      ['/jugar', '/frontend/app-web/src/plataforma/salas-partidas/batallas.html'],
      ['/misiones', '/frontend/app-web/src/cuentas/index.html'],
      ['/torneo', '/frontend/app-web/src/plataforma/salas-partidas/batallas.html'],
      ['/inventario', '/frontend/app-web/src/contenido/inventario/inventario.html'],
      ['/subasta', '/frontend/app-web/src/cuentas/subastas.html'],
      ['/cuenta', '/frontend/app-web/src/cuentas/perfil.html'],
    ])('%s lleva a una vista que existe', (ruta, esperado) => {
      expect(new URL(destinoDe(ruta, BASE)).pathname).toBe(esperado);
    });

    test('las seis secciones tienen destino: ninguna se queda en el identificador', () => {
      for (const seccion of SECCIONES) {
        expect(destinoDe(seccion.ruta, BASE)).not.toBe(seccion.ruta);
      }
    });

    test('funciona igual servido desde src/, no solo desde el borde', () => {
      // `npm run dev` sirve `src` como raiz; el borde sirve el repo entero.
      // Resolver contra la URL del modulo acierta en los dos.
      const desdeSrc = 'http://localhost:8080/comun/barra-navegacion.js';

      expect(new URL(destinoDe('/inventario', desdeSrc)).pathname).toBe(
        '/contenido/inventario/inventario.html',
      );
    });

    test('una ruta desconocida se devuelve tal cual, sin inventar destino', () => {
      expect(destinoDe('/otra-cosa', BASE)).toBe('/otra-cosa');
    });
  });

  // --- Criterio 3 ---------------------------------------------------------

  test('un visitante que abre Mi Cuenta ve unicamente la opcion de registro', () => {
    const barra = construirBarra({ sesion: { autenticado: false } });

    accesos(barra)
      .find((a) => a.textContent === 'Mi Cuenta')
      .click();

    const opciones = [...barra.querySelectorAll('.barra__opcion-cuenta')];
    expect(opciones.map((o) => o.textContent)).toEqual(['Registrarse']);
  });

  test('el visitante no navega fuera al abrir Mi Cuenta: se le ofrece registrarse', () => {
    const visitadas = [];
    const barra = construirBarra({
      sesion: { autenticado: false },
      navegar: (r) => visitadas.push(r),
    });

    accesos(barra)
      .find((a) => a.textContent === 'Mi Cuenta')
      .click();

    expect(visitadas).toEqual([]);
  });

  test('un jugador autenticado ve las opciones de su cuenta, no la de registro', () => {
    const barra = construirBarra({ sesion: { autenticado: true } });

    accesos(barra)
      .find((a) => a.textContent === 'Mi Cuenta')
      .click();

    const opciones = [...barra.querySelectorAll('.barra__opcion-cuenta')].map((o) => o.textContent);
    expect(opciones).not.toContain('Registrarse');
    expect(opciones.length).toBeGreaterThan(0);
  });

  test('sin sesion declarada se asume visitante, no jugador', () => {
    const barra = construirBarra();

    accesos(barra)
      .find((a) => a.textContent === 'Mi Cuenta')
      .click();

    expect([...barra.querySelectorAll('.barra__opcion-cuenta')].map((o) => o.textContent)).toEqual([
      'Registrarse',
    ]);
  });
});
