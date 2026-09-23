/**
 * Política de acceso — UX-R3.0.
 *
 * RF-RBAC-001 (cuatro roles) · RF-RBAC-002 (matriz) · RF-RBAC-003 (solo el
 * super administrador asigna roles) · RF-RBAC-004 (el servidor manda).
 *
 * Lo que se comprueba aquí es la promesa de §15 del bloque: **ninguna
 * pantalla privada queda alcanzable por URL solo porque no aparezca en la
 * navegación**. Antes de esto, las ocho vistas de administración se abrían
 * con cualquier sesión.
 */

import { existsSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';

import { jest } from '@jest/globals';

import {
  ACCESO,
  MATRIZ,
  ROLES,
  ROLES_DE_ADMINISTRACION,
  ROLES_DE_MODERACION,
  VEREDICTO,
  armazonDeVista,
  destinoVisible,
  esRolDeTrastienda,
  exigirAcceso,
  exigirSesion,
  pintarSinPermiso,
  puedeVer,
  urlDeVista,
  vistaDeRuta,
} from './acceso.js';
import { CLAVES } from './sesion.js';

const BASE = 'http://localhost:8099/frontend/app-web/src/comun/acceso.js';
const UID = '7a1e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55';

function tokenCon(claims) {
  const cuerpo = btoa(JSON.stringify(claims))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
  return `acceso.${cuerpo}.firma`;
}

function sesionDe(rol, { caducada = false } = {}) {
  const exp = Math.floor(Date.now() / 1000) + (caducada ? -60 : 3600);
  sessionStorage.setItem(CLAVES.token, tokenCon({ uid: UID, sub: 'Ana', rol, exp }));
  sessionStorage.setItem(CLAVES.apodo, 'Ana');
  sessionStorage.setItem(CLAVES.rol, rol);
}

beforeEach(() => {
  sessionStorage.clear();
  document.body.innerHTML = '';
  delete document.documentElement.dataset.acceso;
});

describe('catálogo de roles (RF-RBAC-001)', () => {
  test('son los cuatro del documento, ni uno más', () => {
    expect(Object.keys(ROLES)).toEqual([
      'JUGADOR',
      'MODERADOR',
      'ADMINISTRADOR',
      'SUPER_ADMINISTRADOR',
    ]);
  });

  test('moderación incluye a los administradores; administración no incluye al moderador', () => {
    // Era el error que arrastraban los cuatro `ROLES_DE_ADMINISTRACION`
    // sueltos: el mismo nombre significaba dos conjuntos distintos.
    expect(ROLES_DE_MODERACION).toContain('MODERADOR');
    expect(ROLES_DE_ADMINISTRACION).not.toContain('MODERADOR');
    expect(ROLES_DE_MODERACION).toEqual(expect.arrayContaining([...ROLES_DE_ADMINISTRACION]));
  });

  test('solo los roles de trastienda operan el sistema', () => {
    expect(esRolDeTrastienda('JUGADOR')).toBe(false);
    expect(esRolDeTrastienda(null)).toBe(false);
    expect(esRolDeTrastienda('MODERADOR')).toBe(true);
    expect(esRolDeTrastienda('SUPER_ADMINISTRADOR')).toBe(true);
  });
});

describe('la matriz cubre lo que hay en disco', () => {
  test('toda vista del repositorio está en la matriz, y toda entrada existe', () => {
    const raiz = new URL('../', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
    const encontradas = [];
    const recorrer = (dir, prefijo = '') => {
      for (const entrada of readdirSync(dir)) {
        const completa = join(dir, entrada);
        if (statSync(completa).isDirectory()) {
          recorrer(completa, `${prefijo}${entrada}/`);
        } else if (entrada.endsWith('.html')) {
          encontradas.push(`${prefijo}${entrada}`);
        }
      }
    };
    recorrer(raiz);

    const enLaMatriz = Object.values(MATRIZ).map((v) => v.ruta);
    // Una vista nueva sin entrada en la matriz se abriría con la política por
    // omisión, que es «solo sesión»: si es administrativa, queda abierta a
    // cualquier jugador. Por eso esto falla en vez de avisar.
    expect(encontradas.filter((r) => !enLaMatriz.includes(r))).toEqual([]);
    expect(enLaMatriz.filter((r) => !existsSync(join(raiz, r)))).toEqual([]);
  });

  test('cada entrada declara un armazón conocido', () => {
    for (const [id, entrada] of Object.entries(MATRIZ)) {
      expect(['publico', 'jugador', 'admin']).toContain(entrada.armazon);
      expect(armazonDeVista(id)).toBe(entrada.armazon);
    }
  });

  test('el portal de entrada es público y ninguna otra vista de cuenta lo es', () => {
    const publicas = Object.entries(MATRIZ)
      .filter(([, v]) => v.acceso === ACCESO.PUBLICA)
      .map(([id]) => id)
      .sort();
    expect(publicas).toEqual([
      'login',
      'productos',
      'pujas',
      'registro',
      'restablecer-confirmar',
      'restablecer-solicitar',
      'subastas',
      'torneos',
    ]);
  });
});

describe('puedeVer (RF-RBAC-002)', () => {
  const anonimo = { autenticado: false, rol: null };
  const jugador = { autenticado: true, rol: 'JUGADOR' };
  const moderador = { autenticado: true, rol: 'MODERADOR' };
  const admin = { autenticado: true, rol: 'ADMINISTRADOR' };
  const superAdmin = { autenticado: true, rol: 'SUPER_ADMINISTRADOR' };

  test('una vista pública se ve sin sesión', () => {
    expect(puedeVer('login', anonimo).veredicto).toBe(VEREDICTO.VISIBLE);
    expect(puedeVer('torneos', anonimo).veredicto).toBe(VEREDICTO.VISIBLE);
  });

  test('una vista de jugador sin sesión redirige, no deniega', () => {
    // La diferencia importa: redirigir lleva a un sitio útil (el login);
    // denegar diría «no puedes» a quien sí puede en cuanto entre.
    expect(puedeVer('inventario', anonimo).veredicto).toBe(VEREDICTO.REDIRIGE);
  });

  test('un jugador NO ve ninguna vista de trastienda', () => {
    const trastienda = Object.entries(MATRIZ)
      .filter(([, v]) => v.armazon === 'admin')
      .map(([id]) => id);
    expect(trastienda.length).toBeGreaterThanOrEqual(8);
    for (const id of trastienda) {
      expect(puedeVer(id, jugador).veredicto).toBe(VEREDICTO.DENEGADA);
    }
  });

  test('el moderador entra en moderación y no en administración', () => {
    expect(puedeVer('sanciones-admin', moderador).veredicto).toBe(VEREDICTO.VISIBLE);
    expect(puedeVer('lista-negra-admin', moderador).veredicto).toBe(VEREDICTO.VISIBLE);
    expect(puedeVer('gestion-usuarios', moderador).veredicto).toBe(VEREDICTO.DENEGADA);
    expect(puedeVer('parametros-admin', moderador).veredicto).toBe(VEREDICTO.DENEGADA);
  });

  test('el administrador no crea administradores ni lee la auditoría (RF-RBAC-003)', () => {
    expect(puedeVer('gestion-usuarios', admin).veredicto).toBe(VEREDICTO.VISIBLE);
    expect(puedeVer('crear-cuenta-admin', admin).veredicto).toBe(VEREDICTO.DENEGADA);
    expect(puedeVer('auditoria', admin).veredicto).toBe(VEREDICTO.DENEGADA);
  });

  test('el super administrador llega a todo', () => {
    for (const id of Object.keys(MATRIZ)) {
      expect(puedeVer(id, superAdmin).veredicto).toBe(VEREDICTO.VISIBLE);
    }
  });

  test('una vista desconocida se trata con el mínimo privilegio', () => {
    // RF-RBAC-001, Excepciones: «rol no reconocido; cuenta sin rol asignado,
    // que debe tratarse con el mínimo privilegio».
    expect(puedeVer('vista-que-no-existe', anonimo).veredicto).toBe(VEREDICTO.REDIRIGE);
    expect(puedeVer('gestion-usuarios', { autenticado: true, rol: null }).veredicto).toBe(
      VEREDICTO.DENEGADA,
    );
  });

  test('destinoVisible oculta lo denegado y conserva lo que solo pide sesión', () => {
    expect(destinoVisible('gestion-usuarios', jugador)).toBe(false);
    expect(destinoVisible('inventario', anonimo)).toBe(true);
  });
});

describe('vistaDeRuta y urlDeVista', () => {
  test('reconoce la vista servida por el borde y por npm run dev', () => {
    expect(vistaDeRuta('/frontend/app-web/src/cuentas/login.html')).toBe('login');
    expect(vistaDeRuta('/cuentas/login.html')).toBe('login');
    expect(vistaDeRuta('/cuentas/gestion-usuarios.html?pagina=2')).toBe('gestion-usuarios');
    expect(vistaDeRuta('/otra/cosa.html')).toBeNull();
  });

  test('la URL de una vista sale igual desde cualquier base servida', () => {
    expect(urlDeVista('batallas', BASE)).toBe(
      'http://localhost:8099/frontend/app-web/src/plataforma/salas-partidas/batallas.html',
    );
    expect(urlDeVista('batallas', 'http://localhost:4321/src/comun/acceso.js')).toBe(
      'http://localhost:4321/src/plataforma/salas-partidas/batallas.html',
    );
  });
});

describe('guardas de ruta (§16)', () => {
  test('exigirSesion sin sesión lleva al login con la ruta de vuelta', () => {
    const navegar = jest.fn();
    const resultado = exigirSesion({
      navegar,
      base: BASE,
      ubicacion: { pathname: '/cuentas/perfil.html', search: '?t=1' },
    });
    expect(resultado).toBeNull();
    expect(navegar).toHaveBeenCalledTimes(1);
    const url = new URL(navegar.mock.calls[0][0]);
    expect(url.pathname).toBe('/frontend/app-web/src/cuentas/login.html');
    expect(url.searchParams.get('volver')).toBe('/cuentas/perfil.html?t=1');
  });

  test('exigirSesion con sesión caducada la borra y lo dice en el motivo', () => {
    sesionDe('JUGADOR', { caducada: true });
    const navegar = jest.fn();
    exigirSesion({ navegar, base: BASE, ubicacion: { pathname: '/x.html', search: '' } });
    expect(new URL(navegar.mock.calls[0][0]).searchParams.get('motivo')).toBe('caducada');
    expect(sessionStorage.getItem(CLAVES.token)).toBeNull();
  });

  test('exigirAcceso deja pasar a quien tiene el rol', () => {
    sesionDe('ADMINISTRADOR');
    const navegar = jest.fn();
    const sesion = exigirAcceso('gestion-usuarios', {
      navegar,
      base: BASE,
      ubicacion: { pathname: '/cuentas/gestion-usuarios.html', search: '' },
    });
    expect(sesion.rol).toBe('ADMINISTRADOR');
    expect(navegar).not.toHaveBeenCalled();
    expect(document.querySelector('[data-zona="sin-permiso"]')).toBeNull();
  });

  test('un jugador que escribe la URL de administración NO ve la pantalla', () => {
    // El hallazgo de §15. Antes esta vista se montaba entera y solo la API
    // negaba los datos: la interfaz ya había contado qué columnas tiene y qué
    // acciones ofrece. La guarda no rebota al login —la sesión es válida—
    // sino que explica (§17) y ofrece una salida.
    sesionDe('JUGADOR');
    const navegar = jest.fn();
    document.body.append(document.createElement('table'));

    const sesion = exigirAcceso('gestion-usuarios', {
      navegar,
      base: BASE,
      ubicacion: { pathname: '/cuentas/gestion-usuarios.html', search: '' },
    });

    expect(sesion).toBeNull();
    expect(navegar).not.toHaveBeenCalled();
    expect(document.querySelector('table')).toBeNull();
    const aviso = document.querySelector('[data-zona="sin-permiso"]');
    expect(aviso).not.toBeNull();
    expect(aviso.textContent).toContain('No tienes acceso a esta sección.');
    // Ningún código crudo a la vista (§17).
    expect(aviso.textContent).not.toMatch(/\b(401|403|500|502)\b/);
    expect(aviso.querySelector('a').textContent).toBe('Volver a mi inicio');
  });

  test('un moderador que escribe la URL de parámetros tampoco la ve', () => {
    sesionDe('MODERADOR');
    const sesion = exigirAcceso('parametros-admin', {
      navegar: jest.fn(),
      base: BASE,
      ubicacion: { pathname: '/plataforma/admin-parametros/parametros-admin.html', search: '' },
    });
    expect(sesion).toBeNull();
    expect(document.querySelector('[data-zona="sin-permiso"]')).not.toBeNull();
  });

  test('una vista pública no exige nada y devuelve la sesión que haya', () => {
    const navegar = jest.fn();
    expect(exigirAcceso('login', { navegar, base: BASE }).autenticado).toBe(false);
    expect(navegar).not.toHaveBeenCalled();
  });

  test('la pantalla sin permiso se anuncia a los lectores de pantalla', () => {
    pintarSinPermiso(document, { base: BASE, rolesAdmitidos: ROLES_DE_ADMINISTRACION });
    expect(document.querySelector('[data-zona="sin-permiso"]').getAttribute('role')).toBe('alert');
  });
});

describe('la interrupción es definitiva', () => {
  test('marca el documento para que ningún módulo monte encima', () => {
    // `gestion-usuarios.html` tiene su guarda en un `<script type="module">` y
    // monta la cabecera desde `gestion-usuarios.js`, otro módulo. Lanzar una
    // excepción detiene el primero y no el segundo: la cabecera de consola se
    // pintaba encima de la pantalla de «sin acceso».
    sesionDe('JUGADOR');
    exigirAcceso('gestion-usuarios', {
      navegar: jest.fn(),
      base: BASE,
      ubicacion: { pathname: '/cuentas/gestion-usuarios.html', search: '' },
    });
    expect(document.documentElement.dataset.acceso).toBe('denegado');
  });

  test('una vista permitida no deja marca', () => {
    sesionDe('ADMINISTRADOR');
    exigirAcceso('gestion-usuarios', {
      navegar: jest.fn(),
      base: BASE,
      ubicacion: { pathname: '/cuentas/gestion-usuarios.html', search: '' },
    });
    expect(document.documentElement.dataset.acceso).toBeUndefined();
  });
});
