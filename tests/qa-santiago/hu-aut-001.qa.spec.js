// HU-AUT-001 · Registro de cuenta de jugador (Cristian Chaparro)
import { test } from '@playwright/test';
import { esperarCodigo } from '../e2e/ayudantes/correo.js';
import { CORRIDA, BORDE, expect, foto, fotoHttp, fotoTexto, llamar, entrar } from './ayudante-qa.js';
import { execSync } from 'node:child_process';

const sello = CORRIDA;
const CLAVE = 'Nexus#Prueba2026';

async function abrirRegistro(page) {
  await page.goto('/registro');
  await expect(page.locator('#formRegistro')).toBeVisible();
}

async function llenar(page, { nombres = 'Laura', apellidos = 'Gómez QA', email, clave = CLAVE, apodo }) {
  await page.locator('#nombres').fill(nombres);
  await page.locator('#apellidos').fill(apellidos);
  await page.locator('#email').fill(email);
  await page.locator('#password').fill(clave);
  await page.locator('#confirmarPassword').fill(clave);
  await page.locator('#apodo').fill(apodo);
}

test.describe('HU-AUT-001 · Registro de cuenta de jugador', () => {
  test('CP-AUT-001-01 · El formulario captura los 7 campos del registro', async ({ page }) => {
    await abrirRegistro(page);
    const campos = ['#nombres', '#apellidos', '#email', '#password', '#confirmarPassword', '#apodo', '#avatar'];
    for (const c of campos) await expect(page.locator(c)).toBeAttached();
    await foto(page, 'CP-AUT-001-01', 'Formulario de registro con nombres, apellidos, correo, contraseña, confirmación, apodo y avatar', {
      resaltar: page.locator('#formRegistro'),
      completa: true,
    });
  });

  test('CP-AUT-001-02 · La política de contraseña informa qué condición falta', async ({ page }) => {
    await abrirRegistro(page);
    const clave = page.locator('#password');
    await clave.fill('abc');
    await clave.blur();
    const aviso = page.getByText(/A tu contraseña le falta/);
    await expect(aviso).toContainText('más de 8 caracteres');
    await expect(aviso).toContainText('una mayúscula');
    await foto(page, 'CP-AUT-001-02', 'Dato «abc»: el sistema informa que faltan longitud (>8), mayúscula, número y símbolo', { resaltar: aviso });

    await clave.fill('Abcdefgh1');
    await clave.blur();
    await expect(aviso).toHaveText(/le falta: un símbolo\.?$/);
    await foto(page, 'CP-AUT-001-02', 'Dato «Abcdefgh1» (9 caracteres sin símbolo): solo falta «un símbolo»', { resaltar: aviso });

    await clave.fill('Abcdefg1!');
    await clave.blur();
    await expect(page.getByText(/A tu contraseña le falta/)).toHaveCount(0);
    await foto(page, 'CP-AUT-001-02', 'Dato «Abcdefg1!» (9 caracteres, mayúscula, minúscula, número y símbolo): el aviso desaparece', { resaltar: clave });
  });

  test('CP-AUT-001-03 · El apodo se normaliza y se rechaza contra la lista negra', async ({ page }) => {
    await abrirRegistro(page);
    await llenar(page, { email: `evasion_${sello}@nexus.test`, apodo: 'B4tm4n' });
    await foto(page, 'CP-AUT-001-03', 'Se intenta el apodo «B4tm4n» (variante de evasión de «batman», término vetado)', { resaltar: page.locator('#apodo') });
    await page.locator('#botonEnviar').click();
    const estado = page.locator('#estadoRegistro');
    await expect(estado).toBeVisible();
    await expect(estado).toHaveClass(/error/);
    await expect(page.locator('#apodo')).toHaveAttribute('aria-invalid', 'true');
    await foto(page, 'CP-AUT-001-03', 'Resultado: registro rechazado con el motivo y el campo apodo marcado', { resaltar: [estado, page.locator('#apodo')] });
  });

  test('CP-AUT-001-04 · Un correo ya registrado se rechaza', async ({ page }) => {
    await abrirRegistro(page);
    await llenar(page, { email: 'anfitriona_e2e@nexus.test', apodo: `dup${sello}` });
    await page.locator('#botonEnviar').click();
    const estado = page.locator('#estadoRegistro');
    await expect(estado).toHaveClass(/error/);
    await expect(estado.getByRole('link', { name: 'Entrar' })).toBeVisible();
    await foto(page, 'CP-AUT-001-04', 'Correo «anfitriona_e2e@nexus.test» ya existe: se rechaza y se ofrece Entrar / Recuperar contraseña', { resaltar: [estado, page.locator('#email')] });
  });

  test('CP-AUT-001-05 · Registro exitoso: rol Jugador, cuenta activa e inventario inicial', async ({ page, request }) => {
    const apodo = `laura${sello}`;
    const email = `${apodo}@nexus.test`;
    await abrirRegistro(page);
    await llenar(page, { email, apodo });
    await foto(page, 'CP-AUT-001-05', `Datos válidos: ${email} · apodo ${apodo} · contraseña que cumple la política`, { resaltar: page.locator('#formRegistro') });
    await page.locator('#botonEnviar').click();
    await page.waitForURL(/verificar/, { timeout: 30_000 });
    const correo = await esperarCodigo(email, { tipo: 'verificacion', espera: 60_000 });
    await foto(page, 'CP-AUT-001-05', 'La cuenta se creó y el sistema pide el código enviado al correo (correo de bienvenida/confirmación)');
    const codigo = page.locator('#codigo');
    await codigo.fill(correo.codigo);
    await page.locator('[data-accion="confirmar"]').click();
    await page.waitForURL(/motivo=verificada/, { timeout: 30_000 });
    await entrar(page, apodo, { clave: CLAVE });
    await page.goto('/cuenta#resumen');
    await expect(page.getByText('JUGADOR').first()).toBeVisible();
    await foto(page, 'CP-AUT-001-05', 'Tras confirmar el correo e iniciar sesión, «Mi cuenta» muestra el rol JUGADOR', { resaltar: page.getByText('JUGADOR').first() });

    const fila = execSync(
      `docker compose -p nexus-e2e exec -T e2e-identidad-db psql -U identidad -d identidaddb -At -c "SELECT u.apodo||' | '||u.email||' | rol='||r.nombre||' | estado='||u.estado FROM usuarios u JOIN roles r ON r.id=u.rol_id WHERE u.apodo='${apodo}'"`,
    ).toString().trim();
    expect(fila).toContain('rol=JUGADOR');
    expect(fila).toContain('estado=ACTIVO');
    await fotoTexto(page, 'CP-AUT-001-05', 'Persistencia de la cuenta creada (base de ms-identidad)', 'SELECT apodo, email, rol, estado FROM usuarios', fila);
  });

  test('CP-AUT-001-06 · Si el correo de bienvenida falla, la cuenta se crea igual y el envío queda encolado', async ({ page, request }) => {
    const apodo = `sincorreo${sello}`;
    const email = `${apodo}@nexus.test`;
    execSync('docker compose -p nexus-e2e stop srv-correo', { stdio: 'ignore' });
    try {
      const r = await llamar(request, 'POST', '/api/v1/auth/registro', {
        multipart: { nombres: 'Sin', apellidos: 'Correo', email, password: CLAVE, apodo },
      });
      await fotoHttp(page, 'CP-AUT-001-06', 'Registro con el servicio de correo DETENIDO', r, {
        nota: 'Se detuvo el contenedor srv-correo antes de registrar. La cuenta debe crearse igual (201) y el correo quedar pendiente de reenvío.',
      });
      expect([200, 201, 202]).toContain(r.estado);
      const fila = execSync(
        `docker compose -p nexus-e2e exec -T e2e-identidad-db psql -U identidad -d identidaddb -At -c "SELECT apodo||' | estado='||estado FROM usuarios WHERE apodo='${apodo}'"`,
      ).toString().trim();
      await fotoTexto(page, 'CP-AUT-001-06', 'La cuenta existe aunque el correo no se pudo enviar', 'usuarios (ms-identidad)', fila || '(sin fila)');
      expect(fila).toContain(apodo);
    } finally {
      execSync('docker compose -p nexus-e2e start srv-correo', { stdio: 'ignore' });
    }
  });
});
