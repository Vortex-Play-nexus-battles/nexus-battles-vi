// HU-PRV-003 · Consentimiento explícito para el tratamiento de datos (Juan Diego)
import { test } from '@playwright/test';
import { entrar, expect, foto, fotoTexto, sql } from './ayudante-qa.js';

test.describe('HU-PRV-003 · Consentimiento explícito para el tratamiento de datos', () => {
  test('CP-PRV-003-01 · El registro presenta la política y recoge el consentimiento diferenciado por finalidad, con fecha y versión', async ({ page }) => {
    await page.goto('/registro');
    await expect(page.locator('#formRegistro')).toBeVisible();
    await foto(page, 'CP-PRV-003-01', 'Formulario de registro: se buscan la política de tratamiento de datos y las casillas de consentimiento por finalidad', { resaltar: page.locator('#formRegistro'), completa: true });
    const tablas = sql('identidad', `SELECT coalesce(string_agg(table_name, ', '), '(ninguna)') FROM information_schema.tables WHERE table_schema='public' AND table_name ILIKE '%consent%';`);
    await fotoTexto(page, 'CP-PRV-003-01', 'Persistencia del consentimiento (fecha y versión de la política) en ms-identidad', "tablas con 'consent' en el esquema de identidad", tablas);
    await expect(page.locator('#formRegistro input[type="checkbox"]'), 'el registro debe pedir el consentimiento por finalidad').not.toHaveCount(0, { timeout: 3000 });
  });

  test('CP-PRV-003-02 · El usuario revoca parcialmente el consentimiento de finalidades secundarias (publicidad)', async ({ page }) => {
    await entrar(page, 'comprador_qa');
    await page.goto('/cuenta#privacidad');
    await page.waitForTimeout(2500);
    await foto(page, 'CP-PRV-003-02', 'Mi Cuenta › Privacidad: se busca el panel de consentimientos por finalidad', { completa: true });
    await expect(page.getByText(/consentimiento|publicidad/i).first(), 'debe existir el panel para revocar el consentimiento de publicidad').toBeVisible({ timeout: 3000 });
  });

  test('CP-PRV-003-03 · Negar el consentimiento a finalidades esenciales impide la prestación del servicio', async ({ page }) => {
    await page.goto('/registro');
    await foto(page, 'CP-PRV-003-03', 'No hay forma de negar las finalidades esenciales: el registro no las presenta', { resaltar: page.locator('#formRegistro') });
    await expect(page.getByText(/finalidades? esencial|tratamiento de (tus )?datos/i), 'el registro debe informar las finalidades esenciales').toBeVisible({ timeout: 3000 });
  });
});
