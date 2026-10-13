// HU-PRV-004 · Portal de privacidad y portabilidad de datos (Santiago Sanabria)
import { test } from '@playwright/test';
import fs from 'node:fs';
import { CORRIDA, EVIDENCIAS, SALIDA, comprar, contenedor, datosQa, entrar, esperarSano, expect, foto, fotoTexto, sesion } from './ayudante-qa.js';

const datos = datosQa();
const TITULAR = `privacidad${CORRIDA}`;

async function abrirPortal(page) {
  await page.goto('/cuenta#privacidad');
  await expect(page.locator('[data-zona="portal-privacidad"]')).toBeVisible();
  await expect(page.locator('[data-zona="resumen-portal"]')).toContainText(/Reunimos|Consulta/);
  await page.waitForLoadState('networkidle').catch(() => {});
}

test.describe('HU-PRV-004 · Portal de privacidad y portabilidad de datos', () => {
  test.beforeAll(async ({ request }) => {
    const s = await sesion(request, TITULAR);
    if (!fs.existsSync(`${SALIDA}/estado-${TITULAR}`)) {
      await comprar(request, s, datos.productosTienda[9].id);
      fs.writeFileSync(`${SALIDA}/estado-${TITULAR}`, 'ok');
    }
  });

  test('CP-PRV-004-01 · El portal consolida los datos personales de los módulos y su finalidad', async ({ page }) => {
    await entrar(page, TITULAR);
    await abrirPortal(page);
    const bloques = page.locator('[data-zona="bloques-portal"]');
    await expect(bloques).toContainText('Para qué se usa');
    await expect(bloques).toContainText('Módulo: Cuentas');
    await expect(bloques).toContainText('Módulo: Finanzas');
    await expect(bloques).toContainText('Módulo: Tienda');
    await foto(page, 'CP-PRV-004-01', 'Mi Cuenta › Privacidad: datos agrupados por módulo con su finalidad de tratamiento («Para qué se usa»)', { resaltar: page.locator('[data-zona="resumen-portal"]') });
    await foto(page, 'CP-PRV-004-01', 'Detalle consolidado: cuenta y perfil, créditos, pagos en dinero real, compras de la tienda, inventario, etc.', { completa: true });
  });

  test('CP-PRV-004-02 · Descarga de los datos en JSON (inventario, estadísticas, transacciones…)', async ({ page }) => {
    await entrar(page, TITULAR);
    await abrirPortal(page);
    const boton = page.locator('[data-accion="descargar-datos"]');
    const descarga = page.waitForEvent('download');
    await boton.click();
    const archivo = await descarga;
    const ruta = await archivo.path();
    const contenido = JSON.parse(fs.readFileSync(ruta, 'utf8'));
    const texto = JSON.stringify(contenido);
    expect(texto).toContain(TITULAR);
    expect(texto).toMatch(/inventario/i);
    expect(texto).toMatch(/transacc|pagos/i);
    await foto(page, 'CP-PRV-004-02', `Se pulsa «Descargar mis datos (JSON)»: el navegador descarga «${archivo.suggestedFilename()}»`, { resaltar: boton });
    await fotoTexto(page, 'CP-PRV-004-02', 'Contenido del archivo descargado (extracto)', archivo.suggestedFilename(), JSON.stringify(contenido, null, 2).slice(0, 2600));
  });

  test('CP-PRV-004-03 · Reporte imprimible para guardar en PDF', async ({ page }) => {
    await entrar(page, TITULAR);
    await abrirPortal(page);
    await page.evaluate(() => { window.print = () => { document.body.dataset.impreso = 'si'; }; });
    await page.locator('[data-accion="imprimir-datos"]').click();
    await expect(page.locator('body')).toHaveAttribute('data-impreso', 'si');
    await page.emulateMedia({ media: 'print' });
    const reporte = page.locator('[data-zona="reporte-privacidad"]');
    await expect(reporte).toBeVisible();
    const pdf = `${EVIDENCIAS}/reporte-privacidad.pdf`;
    await page.pdf({ path: pdf, format: 'A4' });
    expect(fs.statSync(pdf).size).toBeGreaterThan(5000);
    await foto(page, 'CP-PRV-004-03', 'Vista de impresión: solo la hoja del reporte, lista para «Guardar como PDF»', { completa: true });
  });

  test('CP-PRV-004-04 · Si un módulo no está disponible, se informa que la consulta es parcial', async ({ page, request }) => {
    await entrar(page, TITULAR);
    contenedor('stop', 'srv-ms-finanzas');
    try {
      await abrirPortal(page);
      const resumen = page.locator('[data-zona="resumen-portal"]');
      await expect(resumen).toContainText(/parcial|no pudimos consultar/i);
      await foto(page, 'CP-PRV-004-04', 'Con ms-finanzas detenido: el portal avisa «Consulta parcial» y marca el módulo como no disponible', { resaltar: resumen });
      const bloque = page.locator('[data-zona="bloques-portal"]').getByText(/no disponible|no respondi/i).first();
      await expect(bloque).toBeVisible();
      await foto(page, 'CP-PRV-004-04', 'Bloque del módulo de finanzas marcado como no disponible; el resto se muestra completo', { resaltar: bloque });
    } finally {
      contenedor('start', 'srv-ms-finanzas');
      await esperarSano(request, 'http://localhost:8093/api/v1/actuator/health');
    }
  });

  test('CP-PRV-004-05 · Descarga de los datos en XML', async ({ page }) => {
    await entrar(page, TITULAR);
    await abrirPortal(page);
    await foto(page, 'CP-PRV-004-05', 'Botones de descarga del portal: se busca la opción XML', { resaltar: page.locator('[data-zona="portal-privacidad"]').getByRole('button').first() });
    await expect(page.getByRole('button', { name: /XML/ }), 'el portal debe ofrecer la descarga en XML (CA2)').toBeVisible({ timeout: 3000 });
  });
});
