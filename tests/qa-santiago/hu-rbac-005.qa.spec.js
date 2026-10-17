// HU-RBAC-005 · Separación de permisos entre ambientes (Edwin Pedraza)
import { test } from '@playwright/test';
import { generateKeyPairSync, createSign } from 'node:crypto';
import fs from 'node:fs';
import { RAIZ, esperar, expect, foto, fotoHtml, fotoHttp, fotoTexto, junitMaven, llamar, sesion, sql } from './ayudante-qa.js';

const GITHUB = 'https://api.github.com/repos/Vortex-Play-nexus-battles/nexus-battles-vi';
const IDENTIDAD = `${RAIZ}/services/cuentas/ms-identidad/src/main/resources`;

/** JWT con los mismos claims de un usuario real, firmado por la llave de OTRO ambiente. */
function tokenDeOtroAmbiente(claims) {
  const { privateKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
  const b64 = (o) => Buffer.from(JSON.stringify(o)).toString('base64url');
  const cabecera = { alg: 'RS256', typ: 'JWT', kid: 'produccion-2026' };
  const cuerpo = { ...claims, iss: 'ms-identidad-produccion', iat: Math.floor(Date.now() / 1000), exp: Math.floor(Date.now() / 1000) + 3600 };
  const firma = createSign('RSA-SHA256').update(`${b64(cabecera)}.${b64(cuerpo)}`).sign(privateKey).toString('base64url');
  return `${b64(cabecera)}.${b64(cuerpo)}.${firma}`;
}

test.describe('HU-RBAC-005 · Separación de permisos entre ambientes', () => {
  test('CP-RBAC-005-01 · Las credenciales y permisos de desarrollo y producción están separados', async ({ page }) => {
    test.setTimeout(300_000);
    const dev = fs.readFileSync(`${IDENTIDAD}/application-dev.properties`, 'utf8').split('\n').filter((l) => /datasource\.(url|username|password)/.test(l)).join('\n');
    const prod = fs.readFileSync(`${IDENTIDAD}/application-prod.properties`, 'utf8').split('\n').filter((l) => /datasource\.(url|username|password)/.test(l)).join('\n');
    await page.goto('about:blank');
    await fotoHtml(page, 'CP-RBAC-005-01', 'Configuración de ms-identidad por ambiente: almacenes de credenciales distintos',
      `<table><tr><th>application-dev.properties</th><th>application-prod.properties</th></tr><tr><td><pre>${dev}</pre></td><td><pre>${prod}</pre></td></tr></table>`,
      { nota: 'Desarrollo usa su propia base y usuario; producción no trae ningún valor en el repositorio: todo llega por variables de entorno del ambiente (regla 10).' });
    const { reporte } = junitMaven('services/cuentas/ms-identidad', 'EnvironmentProfileIsolationTest');
    await fotoTexto(page, 'CP-RBAC-005-01', 'Prueba unitaria de aislamiento de perfiles (credenciales separadas, prod sin secretos, dev por defecto)', 'mvnw test -Dtest=EnvironmentProfileIsolationTest', reporte);
    expect(reporte).toMatch(/Failures: 0, Errors: 0/);
  });

  test('CP-RBAC-005-02 · Una credencial de otro ambiente se rechaza con mensaje explícito y queda auditada', async ({ page, request }) => {
    const correo = 'admin.produccion@nexusbattles.upb.edu.co';
    await page.goto('/login');
    await page.locator('#email').fill(correo);
    await page.locator('#password').fill('Produccion#2026');
    await page.locator('#botonEnviar').click();
    const rechazo = page.locator('[data-zona="rechazo"]');
    await expect(rechazo).toContainText(/no están registradas en este ambiente/);
    await foto(page, 'CP-RBAC-005-02', `Credencial de PRODUCCIÓN (${correo}) usada en DESARROLLO: «…estas credenciales no están registradas en este ambiente»`, { resaltar: rechazo });
    let filas = '';
    for (let i = 0; i < 8 && !filas; i += 1) {
      await esperar(1000);
      filas = sql('cumplimiento', `SELECT to_char(fecha_hora,'HH24:MI:SS')||' | '||tipo_accion||' | afectado='||afectado||' | motivo='||motivo||' | ip='||ip_origen FROM audit_log WHERE afectado='${correo}' ORDER BY fecha_hora DESC LIMIT 2;`);
    }
    await fotoTexto(page, 'CP-RBAC-005-02', 'El intento queda auditado con motivo CREDENCIALES_INVALIDAS_ENTORNO', 'audit_log (ms-cumplimiento)', filas || '(sin filas)');
    expect(filas).toContain('CREDENCIALES_INVALIDAS_ENTORNO');

    const real = await sesion(request, 'admin_e2e');
    const ajeno = tokenDeOtroAmbiente(real.claims);
    const pruebas = [
      await llamar(request, 'GET', '/api/v1/admin/usuarios/1', { token: ajeno }),
      await llamar(request, 'GET', '/api/v1/chatbot/admin/analiticas', { token: ajeno }),
      await llamar(request, 'GET', '/api/v1/carrito', { token: ajeno }),
    ];
    await fotoHttp(page, 'CP-RBAC-005-02', 'Token emitido por la llave de otro ambiente (mismos datos de admin_e2e): los servicios de desarrollo lo rechazan', pruebas);
    for (const p of pruebas) expect([401, 403]).toContain(p.estado);
  });

  test('CP-RBAC-005-03 · La promoción entre ambientes solo ocurre por el flujo de CI/CD', async ({ page, request }) => {
    const cd = fs.readFileSync(`${RAIZ}/.github/workflows/cd.yml`, 'utf8').split('\n');
    const extracto = cd
      .map((l, i) => [i + 1, l])
      .filter(([, l]) => /^(on:|  push:|    branches:|  workflow_dispatch:|jobs:|  [a-z][a-z0-9-]+:\s*$|    (needs|environment):)/.test(l) && !/^  (inputs|steps|env|outputs|strategy|with|permissions|concurrency):/.test(l))
      .map(([n, l]) => `${String(n).padStart(4)}  ${l}`)
      .join('\n');
    await page.goto('about:blank');
    await fotoTexto(page, 'CP-RBAC-005-03', 'cd.yml: despliegue encadenado dev → test → producción, cada etapa con su «environment» protegido', '.github/workflows/cd.yml (extracto)', extracto);
    const runs = await request.get(`${GITHUB}/actions/workflows/345987503/runs?per_page=6&branch=develop`);
    const datos = await runs.json();
    const filas = (datos.workflow_runs ?? []).map((r) => `<tr><td>${r.run_number}</td><td>${r.event}</td><td>${r.head_branch}</td><td>${r.conclusion ?? r.status}</td><td>${r.created_at}</td><td>${r.head_commit?.message?.split('\n')[0].slice(0, 70) ?? ''}</td></tr>`).join('');
    await fotoHtml(page, 'CP-RBAC-005-03', 'Últimas ejecuciones del flujo «CD - Distribución continua» en GitHub Actions (rama develop)',
      `<table><tr><th>#</th><th>Evento</th><th>Rama</th><th>Resultado</th><th>Fecha</th><th>Commit</th></tr>${filas}</table>`,
      { nota: 'Cada despliegue lo dispara el flujo (push a develop o ejecución manual del flujo); no hay pasos de despliegue fuera del pipeline.' });
    expect((datos.workflow_runs ?? []).length).toBeGreaterThan(0);
    expect(cd.join('\n')).toMatch(/environment: test/);
    expect(cd.join('\n')).toMatch(/environment: produccion/);
  });
});
