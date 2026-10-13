// HU-CHA-012 · Panel de administración y analítica del chatbot (Juan Diego)
import { test } from '@playwright/test';
import fs from 'node:fs';
import { CORRIDA, RAIZ, VISTA, abrirChat, aceptarDialogo, entrar, esperar, expect, foto, fotoTexto, preguntar } from './ayudante-qa.js';

const KB_REPO = `${RAIZ}/services/cuentas/ms-chatbot/contenido/temas-7-4-2.json`;
const TITULO = `Horario de atención de soporte ${CORRIDA}`;

async function panel(page, pestana) {
  await page.goto(VISTA(`cuentas/panel-chatbot.html#${pestana}`));
  await page.locator(`#pestana-${pestana}`).click();
  await page.waitForTimeout(1800);
}

async function candidataNueva(page) {
  await panel(page, 'base');
  const descartar = page.locator('[data-accion="descartar-candidata"]');
  if (await descartar.isVisible().catch(() => false)) {
    await descartar.click();
    await aceptarDialogo(page);
    await page.waitForTimeout(1500);
  }
  await page.getByRole('button', { name: 'Crear candidata' }).click();
  await expect(page.locator('[data-accion="agregar-tema"]')).toBeVisible();
  await page.waitForTimeout(800);
}

const fila = (page, titulo) => page.locator('[data-zona="temas-candidata"] tr, [data-zona="candidata"] tr').filter({ hasText: titulo }).first();

test.describe('HU-CHA-012 · Panel de administración y analítica del chatbot', () => {
  test.describe.configure({ mode: 'serial' });

  test('CP-CHA-012-01 · Analíticas por período: conversaciones, temas, resolución, tiempo, satisfacción, escalamientos y tendencia, exportables', async ({ page }) => {
    await entrar(page, 'superadmin_qa');
    await panel(page, 'analiticas');
    for (const t of ['Conversaciones', 'Tasa de resolución', 'Satisfacción', 'Tiempo de respuesta promedio', 'Preguntas por día', 'Temas más consultados', 'Solicitudes de soporte']) await expect(page.getByText(t).first()).toBeVisible();
    await foto(page, 'CP-CHA-012-01', 'Analíticas del asistente: conversaciones, preguntas, tasa de resolución (con escalamientos), satisfacción, tiempo de respuesta, tendencia por día y temas frecuentes', { completa: true });
    const descarga = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Descargar CSV' }).click();
    const archivo = await descarga;
    const texto = fs.readFileSync(await archivo.path(), 'utf8');
    await fotoTexto(page, 'CP-CHA-012-01', `«Descargar CSV» exporta el período consultado (${archivo.suggestedFilename()})`, archivo.suggestedFilename(), texto.slice(0, 1800));
    expect(texto.length).toBeGreaterThan(50);
  });

  test('CP-CHA-012-02 · El administrador agrega un tema con variantes ES/EN, intención y prioridad; lo edita y elimina otro', async ({ page }) => {
    await entrar(page, 'superadmin_qa');
    await candidataNueva(page);
    await page.locator('[data-accion="agregar-tema"]').click();
    const d = page.getByRole('dialog');
    await d.getByLabel('Título').fill(TITULO);
    await d.getByLabel(/Intención/).selectOption({ label: 'Soporte técnico' });
    await d.getByLabel('Tipo de respuesta').selectOption({ label: 'Directa' });
    await d.getByLabel(/Variantes de pregunta en español/).fill(`horario de soporte ${CORRIDA}\na que hora atiende soporte ${CORRIDA}`);
    await d.getByLabel(/Variantes de pregunta en inglés/).fill(`support hours ${CORRIDA}`);
    await d.getByLabel(/Respuesta en español/).fill('Soporte humano atiende todos los días de 8:00 a. m. a 8:00 p. m. (hora de Colombia).');
    const en = d.getByLabel(/Respuesta en inglés/);
    if (await en.count()) await en.fill('Human support is available every day from 8:00 a.m. to 8:00 p.m. (Colombia time).');
    const prioridad = d.getByLabel(/Prioridad/);
    if (await prioridad.count()) await prioridad.fill('5');
    await foto(page, 'CP-CHA-012-02', 'Nuevo tema en la versión candidata: título, intención (Soporte técnico), tipo, variantes en español e inglés, respuesta y prioridad 5', { resaltar: d });
    await d.getByRole('button', { name: 'Guardar tema' }).click();
    await expect(fila(page, TITULO)).toBeVisible();
    await foto(page, 'CP-CHA-012-02', 'El tema aparece en la candidata con sus variantes y su prioridad', { resaltar: fila(page, TITULO) });
    await fila(page, TITULO).locator('[data-accion="editar-tema"]').click();
    await d.getByLabel(/Respuesta en español/).fill('Soporte humano atiende de lunes a domingo, de 7:00 a. m. a 9:00 p. m. (hora de Colombia).');
    if (await prioridad.count()) await prioridad.fill('8');
    await d.getByRole('button', { name: 'Guardar tema' }).click();
    await page.waitForTimeout(1200);
    await foto(page, 'CP-CHA-012-02', 'Edición del tema: nueva respuesta y prioridad 8', { resaltar: fila(page, TITULO) });
    const TEMPORAL = `Tema temporal QA ${CORRIDA}`;
    await page.locator('[data-accion="agregar-tema"]').click();
    await d.getByLabel('Título').fill(TEMPORAL);
    await d.getByLabel(/Variantes de pregunta en español/).fill(`pregunta temporal ${CORRIDA}`);
    await d.getByLabel(/Respuesta en español/).fill('Respuesta temporal.');
    await d.getByRole('button', { name: 'Guardar tema' }).click();
    await expect(fila(page, TEMPORAL)).toBeVisible();
    await fila(page, TEMPORAL).locator('[data-accion="eliminar-tema"]').click();
    if (await page.getByRole('dialog').isVisible({ timeout: 1500 }).catch(() => false)) await aceptarDialogo(page);
    await expect(fila(page, TEMPORAL)).toHaveCount(0);
    await foto(page, 'CP-CHA-012-02', `Eliminación de un tema («${TEMPORAL}») de la candidata`, { resaltar: page.locator('[data-zona="candidata"]').first() });
  });

  test('CP-CHA-012-03 · Exportar la base de conocimiento', async ({ page }) => {
    await entrar(page, 'superadmin_qa');
    await panel(page, 'base');
    const descarga = page.waitForEvent('download');
    await page.locator('[data-zona="versiones"] [data-accion="exportar-version"]').first().click();
    const archivo = await descarga;
    const json = JSON.parse(fs.readFileSync(await archivo.path(), 'utf8'));
    const temas = json.temas ?? json;
    await foto(page, 'CP-CHA-012-03', 'Tabla de versiones de la base con su botón «Exportar»', { resaltar: page.locator('[data-zona="versiones"]') });
    await fotoTexto(page, 'CP-CHA-012-03', `«Exportar» descarga ${archivo.suggestedFilename()} con ${temas.length} temas (incluye el tema nuevo)`, archivo.suggestedFilename(), JSON.stringify(json, null, 2).slice(0, 1500));
    expect(JSON.stringify(json)).toContain(TITULO);
  });

  test('CP-CHA-012-04 · La candidata se valida contra producción antes de desplegarse y se puede revertir', async ({ page }) => {
    await entrar(page, 'superadmin_qa');
    await panel(page, 'reentrenamiento');
    await page.locator('[data-accion="evaluar"]').click();
    await page.waitForTimeout(3000);
    await foto(page, 'CP-CHA-012-04', 'Evaluar sin desplegar: los casos de evaluación se corren contra la candidata y contra producción', { completa: true });
    await page.locator('[data-accion="desplegar"]').click();
    if (await page.getByRole('dialog').isVisible({ timeout: 2000 }).catch(() => false)) await aceptarDialogo(page);
    await page.waitForTimeout(3000);
    await foto(page, 'CP-CHA-012-04', 'La candidata no acierta menos que producción: se despliega', { completa: true });
    await page.goto('/');
    await abrirChat(page);
    const r = await preguntar(page, `a que hora atiende soporte ${CORRIDA}`);
    await expect(r).toContainText('7:00 a. m. a 9:00 p. m.');
    await foto(page, 'CP-CHA-012-04', 'En producción, el asistente ya responde con el tema nuevo', { resaltar: r });
    await entrar(page, 'superadmin_qa');
    await panel(page, 'reentrenamiento');
    await page.locator('[data-accion="revertir"]').click();
    if (await page.getByRole('dialog').isVisible({ timeout: 2000 }).catch(() => false)) await aceptarDialogo(page);
    await page.waitForTimeout(2500);
    await foto(page, 'CP-CHA-012-04', '«Revertir a la versión anterior»: vuelve a producción la versión previa', { completa: true });
    await page.goto('/');
    await abrirChat(page);
    const r2 = await preguntar(page, `a que hora atiende soporte ${CORRIDA}`);
    await expect(r2).not.toContainText('7:00 a. m. a 9:00 p. m.');
    await foto(page, 'CP-CHA-012-04', 'Tras revertir, el asistente vuelve a responder con la versión anterior', { resaltar: r2 });
  });

  test('CP-CHA-012-05 · Una candidata con desempeño inferior no se despliega', async ({ page }) => {
    await entrar(page, 'superadmin_qa');
    await candidataNueva(page);
    for (const t of ['Cómo crear una cuenta', 'Recuperar mi contraseña', 'Héroes disponibles', 'Cómo funciona el Torneo']) {
      const f = fila(page, t);
      if (!(await f.count())) continue;
      await f.locator('[data-accion="eliminar-tema"]').click();
      if (await page.getByRole('dialog').isVisible({ timeout: 1500 }).catch(() => false)) await aceptarDialogo(page);
      await page.waitForTimeout(800);
    }
    await foto(page, 'CP-CHA-012-05', 'Candidata degradada: se eliminan 4 temas que cubren casos de evaluación', { resaltar: page.locator('[data-zona="candidata"]').first() });
    await panel(page, 'reentrenamiento');
    const produccion = await page.getByText(/En producción: versión \d+/).innerText();
    await page.locator('[data-accion="desplegar"]').click();
    if (await page.getByRole('dialog').isVisible({ timeout: 2000 }).catch(() => false)) await aceptarDialogo(page);
    await page.waitForTimeout(3000);
    await foto(page, 'CP-CHA-012-05', 'Al intentar desplegarla, la evaluación detecta que acierta menos que producción y NO la despliega', { completa: true });
    await page.reload();
    await panel(page, 'reentrenamiento');
    await expect(page.getByText(/En producción: versión \d+/)).toHaveText(produccion);
  });

  test('CP-CHA-012-06 · Importar una base de conocimiento desde un archivo JSON', async ({ page }) => {
    const archivoRepo = JSON.parse(fs.readFileSync(KB_REPO, 'utf8'));
    await entrar(page, 'superadmin_qa');
    await candidataNueva(page);
    const antes = await page.locator('[data-accion="editar-tema"]').count();
    const selector = page.waitForEvent('filechooser');
    await page.locator('[data-accion="importar-temas"]').click();
    await (await selector).setFiles(KB_REPO);
    if (await page.getByRole('dialog').isVisible({ timeout: 2000 }).catch(() => false)) await aceptarDialogo(page);
    await page.waitForTimeout(2500);
    const despues = await page.locator('[data-accion="editar-tema"]').count();
    await expect(fila(page, 'Guía para nuevos jugadores')).toBeVisible();
    await foto(page, 'CP-CHA-012-06', `«Importar JSON» (temas-7-4-2.json, ${archivoRepo.temas.length} temas): la candidata pasa de ${antes} a ${despues} temas, p. ej. «Guía para nuevos jugadores»`, { resaltar: fila(page, 'Guía para nuevos jugadores') });
    expect(despues).toBe(archivoRepo.temas.length);
    await page.locator('[data-accion="descartar-candidata"]').click();
    await aceptarDialogo(page);
  });
});
