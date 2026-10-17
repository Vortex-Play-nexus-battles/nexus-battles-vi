// HU-MET-002 · Métricas del mercado de subastas (Santiago Sanabria)
import { test } from '@playwright/test';
import { VISTA, entrar, expect, foto } from './ayudante-qa.js';

const INDICADORES = [/volumen de transacciones/i, /m[aá]s subastados|m[aá]s vendidos/i, /precio promedio/i, /tasa de [ée]xito/i, /usuarios m[aá]s activos/i, /comisiones/i, /tiempo de finalizaci[oó]n/i, /tendencias/i];

test.describe('HU-MET-002 · Métricas del mercado de subastas', () => {
  test('CP-MET-002-01 · El tablero del mercado agrega los 8 indicadores del período', async ({ page }) => {
    await entrar(page, 'admin_e2e');
    await page.goto(VISTA('plataforma/consola/consola.html'));
    await page.getByRole('link', { name: /^Métricas/ }).first().click();
    await page.waitForTimeout(2500);
    await foto(page, 'CP-MET-002-01', 'Consola › Métricas: el administrador busca el tablero del mercado de subastas', { completa: true });
    await page.goto(VISTA('plataforma/consola/control-integral.html'));
    await page.waitForTimeout(2500);
    const panel = page.locator('section, article').filter({ hasText: 'Subastas activas' }).first();
    await foto(page, 'CP-MET-002-01', 'Control integral › Subastas: solo lista las publicaciones activas (no hay indicadores de mercado)', { resaltar: panel });
    const faltan = [];
    for (const ind of INDICADORES) if (!(await page.getByText(ind).count())) faltan.push(ind.source);
    expect(faltan, `indicadores del mercado que no aparecen: ${faltan.join(', ')}`).toEqual([]);
  });

  test('CP-MET-002-02 · Comparación entre períodos y estado vacío de un período sin actividad', async ({ page }) => {
    await entrar(page, 'admin_e2e');
    await page.goto(VISTA('plataforma/metricas-plataforma/panel-metricas.html'));
    await page.waitForTimeout(2500);
    await foto(page, 'CP-MET-002-02', 'Panel de métricas: se busca el selector de período y la comparación del mercado de subastas', { completa: true });
    await expect(page.getByText(/mercado de subastas/i), 'debe existir la sección de métricas del mercado con selector de período').toBeVisible({ timeout: 3000 });
  });
});
