/**
 * Seguridad administrativa en AWS DEV — 7.3.1, 7.3.5 y 7.3.9 (SA-1).
 *
 * El banco (`seguridad-administrativa.e2e.spec.js`) recorre todo con su
 * configuración de pruebas. Esto comprueba en el entorno desplegado lo que
 * depende de SU configuración y no se puede dar por bueno desde el banco:
 *
 *   - el segundo factor es obligatorio para los tres roles administrativos
 *     (la contraseña sola no abre sesión);
 *   - el token administrativo dura los **15 minutos** que fija DEV (en el
 *     banco son 30) y se renueva conservando la autenticación;
 *   - la jerarquía de 7.3.1 y la matriz RBAC se aplican en los endpoints, no
 *     solo en la interfaz: un jugador, un moderador y un administrador
 *     reciben 403 donde no les toca;
 *   - el endpoint de demostración que «baneaba» sin banear ya no existe.
 *
 * Entra con las cuentas administrativas de PRUEBAS de DEV (`ADMINS_QA`, con
 * el secreto TOTP ya enrolado), que llegan por `E2E_ADMINS_QA`. No toca a
 * ninguna persona real ni cambia nada de esas cuentas: todo lo que se
 * intenta sobre ellas es lo que el servidor tiene que rechazar.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

import {
  cuentaAdministrativaDeRol,
  iniciarSesion,
  respetandoElLimite,
  sesionDe,
  tipoDe,
} from './ayudantes/cuentas.js';

const AWS = process.env.E2E_AWS ?? 'http://35.168.124.119';
const CLAVE = 'Contrasena-Smoke-2026';
/** Lo que fija DEV (application.properties de ms-identidad). */
const MINUTOS_EN_DEV = Number(process.env.E2E_MINUTOS_SESION_ADMINISTRATIVA ?? 15);
const ACEPTA = 'application/problem+json, application/json';

function como(sesion, extra = {}) {
  return { headers: { Accept: ACEPTA, Authorization: `Bearer ${sesion.token}`, ...extra } };
}

async function leer(respuesta) {
  const texto = await respuesta.text();
  let cuerpo = null;
  try {
    cuerpo = JSON.parse(texto);
  } catch {
    cuerpo = texto;
  }
  return { estado: respuesta.status(), cuerpo, tipo: tipoDe(cuerpo), texto };
}

test.describe('Seguridad administrativa en AWS DEV (7.3.9)', () => {
  test.describe.configure({ mode: 'serial' });
  // Sin las cuentas de pruebas en este entorno no hay con quién entrar: se
  // dice en el informe en vez de dar un verde que no probó nada.
  test.skip(
    !process.env.E2E_ADMINS_QA,
    'E2E_ADMINS_QA no está definida: este entorno no tiene cuentas administrativas de pruebas',
  );

  let api;
  let moderador;
  let admin;
  let superAdmin;
  let jugador;

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: AWS, ignoreHTTPSErrors: true });
    moderador = await sesionDe(api, cuentaAdministrativaDeRol('MODERADOR').apodo, { base: AWS });
    admin = await sesionDe(api, cuentaAdministrativaDeRol('ADMINISTRADOR').apodo, { base: AWS });
    superAdmin = await sesionDe(api, cuentaAdministrativaDeRol('SUPER_ADMINISTRADOR').apodo, {
      base: AWS,
    });
    jugador = await sesionDe(api, `smoke_sa_${Date.now()}`, { clave: CLAVE, base: AWS });
  });

  test.afterAll(async () => {
    await api?.dispose();
  });

  test('la contraseña sola no abre una sesión administrativa', async () => {
    for (const rol of ['MODERADOR', 'ADMINISTRADOR', 'SUPER_ADMINISTRADOR']) {
      const cuenta = cuentaAdministrativaDeRol(rol);
      const login = await iniciarSesion(api, cuenta.email, cuenta.clave);
      expect(login.estado, rol).toBe(403);
      expect(login.tipo).toBe('segundo-factor-requerido');
      expect(login.cuerpo.token).toBeUndefined();
    }
  });

  test('el token administrativo dura 15 minutos y se renueva conservando la autenticación', async () => {
    for (const sesion of [moderador, admin, superAdmin]) {
      expect(sesion.claims.amr).toEqual(['pwd', 'otp']);
      expect(sesion.claims.exp - sesion.claims.iat).toBe(MINUTOS_EN_DEV * 60);
    }
    // El de un jugador no cambia.
    expect(jugador.claims.exp - jugador.claims.iat).toBe(24 * 3600);

    const r = await respetandoElLimite(() =>
      api.post('/api/v1/auth/sesion/renovacion', como(superAdmin)),
    );
    expect(r.status(), await r.text()).toBe(200);
    const { token } = await r.json();
    const nuevo = JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString('utf8'));
    expect(nuevo.auth_time).toBe(superAdmin.claims.auth_time);
    expect(nuevo.amr).toEqual(['pwd', 'otp']);
  });

  test('la matriz RBAC se aplica en los endpoints: un jugador no entra a la trastienda', async () => {
    for (const ruta of [
      '/api/v1/admin/jugadores',
      '/api/v1/rbac/matrix',
      '/api/v1/admin/auditoria',
      '/api/v1/apelaciones',
    ]) {
      const r = await api.get(ruta, como(jugador));
      expect(r.status(), `${ruta} con token de jugador`).toBe(403);
    }
    // Un moderador no gestiona cuentas ni consulta la auditoría.
    for (const ruta of ['/api/v1/admin/jugadores', '/api/v1/admin/auditoria']) {
      const r = await api.get(ruta, como(moderador));
      expect(r.status(), `${ruta} con token de moderador`).toBe(403);
    }
    // Un administrador no consulta la auditoría (solo Super Administrador).
    expect((await api.get('/api/v1/admin/auditoria', como(admin))).status()).toBe(403);
    // El Super Administrador sí, con su sesión de dos pasos.
    expect((await api.get('/api/v1/admin/auditoria?size=1', como(superAdmin))).status()).toBe(200);
  });

  test('nadie actúa sobre un rol igual o superior', async () => {
    const json = { 'Content-Type': 'application/json' };
    const manana = new Date(Date.now() + 24 * 3600_000).toISOString().slice(0, 16);

    const suspension = await leer(
      await api.put(`/api/v1/admin/usuarios/${superAdmin.usuarioId}/suspender`, {
        ...como(admin, json),
        data: { suspendidoHasta: manana, motivo: 'Comprobación de jerarquía del smoke' },
      }),
    );
    expect(suspension.estado, suspension.texto).toBe(403);
    expect(suspension.tipo).toBe('jerarquia-insuficiente');

    const advertencia = await leer(
      await api.post('/api/v1/sanciones', {
        ...como(moderador, json),
        data: {
          usuarioId: admin.claims.uid,
          tipo: 'ADVERTENCIA',
          motivo: 'Comprobación de jerarquía del smoke',
        },
      }),
    );
    expect(advertencia.estado, advertencia.texto).toBe(403);
    expect(advertencia.cuerpo.motivo).toBe('JERARQUIA_INSUFICIENTE');

    const restablecer = await api.post(
      `/api/v1/admin/usuarios/${moderador.usuarioId}/segundo-factor/restablecimiento`,
      { ...como(admin, json), data: { motivo: 'Comprobación del smoke' } },
    );
    expect(restablecer.status(), 'restablecer el segundo factor es del Super Administrador').toBe(
      403,
    );
  });

  test('el endpoint de demostración que «baneaba» sin banear ya no existe', async () => {
    const r = await api.post('/api/v1/admin/ban', {
      ...como(superAdmin, { 'Content-Type': 'application/json' }),
      data: { userId: 'nadie' },
    });
    expect(r.status()).toBe(404);
  });
});
