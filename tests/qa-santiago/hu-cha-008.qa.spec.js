// HU-CHA-008 · Consultas y acciones asistidas para usuarios autenticados (Andrés Núñez)
import { test } from '@playwright/test';
import { CORRIDA, abrirChat, aceptarDialogo, datosQa, entrar, expect, foto, fotoHttp, llamar, preguntar, sesion } from './ayudante-qa.js';

const datos = datosQa();

test.describe('HU-CHA-008 · Consultas y acciones asistidas para usuarios autenticados', () => {
  test('CP-CHA-008-01 · Consulta en tiempo real inventario, subastas, notificaciones, misiones y torneos del usuario', async ({ page, request }) => {
    const s = await sesion(request, 'comprador_qa');
    const inventario = await llamar(request, 'GET', '/api/v1/inventario/elementos?pagina=0', { token: s.token });
    const total = inventario.cuerpo.totalElementos ?? inventario.cuerpo.elementos?.length;
    await entrar(page, 'comprador_qa');
    await abrirChat(page);
    const r1 = await preguntar(page, 'que tengo en mi inventario');
    await expect(r1).toContainText(`Tienes ${total} elemento`);
    await foto(page, 'CP-CHA-008-01', `«que tengo en mi inventario»: responde con el inventario real del jugador (${total} elementos) y botón «Ir a Mi inventario»`, { resaltar: r1 });
    const r2 = await preguntar(page, 'como van mis subastas');
    await expect(r2).toContainText(/Vas ganando \d+ subasta|retenidos/);
    await foto(page, 'CP-CHA-008-01', '«como van mis subastas»: subastas que va ganando, créditos retenidos y saldo disponible', { resaltar: r2 });
    const r3 = await preguntar(page, 'mis notificaciones');
    await expect(r3).toContainText(/notificaci/i);
    await foto(page, 'CP-CHA-008-01', '«mis notificaciones»: cuántas tiene sin leer y las más recientes', { resaltar: r3 });
    const r4 = await preguntar(page, 'mis misiones');
    await expect(r4).toContainText(/misiones/i);
    const r5 = await preguntar(page, 'estado de mis torneos');
    await expect(r5).toContainText(/torneo/i);
    await foto(page, 'CP-CHA-008-01', '«mis misiones» y «estado de mis torneos»: responde con el estado real (sin misiones en curso / sin torneos abiertos)', { resaltar: [r4, r5] });
  });

  test('CP-CHA-008-02 · Respeta la autorización: un visitante no obtiene datos de cuenta', async ({ page }) => {
    await page.goto('/');
    await abrirChat(page);
    const r = await preguntar(page, 'que tengo en mi inventario');
    await expect(r).not.toContainText(/Tienes \d+ elemento/);
    await foto(page, 'CP-CHA-008-02', 'Visitante sin sesión pregunta «que tengo en mi inventario»: no recibe datos personales, pero tampoco se le pide iniciar sesión', { resaltar: r });
    await expect(r, 'al visitante se le debe exigir iniciar sesión').toContainText(/inicia sesi|iniciar sesi/i);
  });

  test('CP-CHA-008-03 · Ayuda a navegar y genera un reporte de actividad', async ({ page }) => {
    await entrar(page, 'comprador_qa');
    await abrirChat(page);
    const r = await preguntar(page, 'genera un reporte de mi actividad');
    await expect(r).toContainText('resumen de tu actividad');
    await foto(page, 'CP-CHA-008-03', '«genera un reporte de mi actividad»: resumen de inventario, subastas, créditos y notificaciones', { resaltar: r });
    const n = await preguntar(page, 'llevame a mi inventario');
    const boton = n.locator('.chatbot-enriquecido__enlace', { hasText: /inventario/i }).first();
    await expect(boton).toBeVisible();
    await foto(page, 'CP-CHA-008-03', '«llevame a mi inventario»: botón de acción rápida «Ir a Mi inventario»', { resaltar: boton });
    await boton.click();
    await page.waitForURL(/inventario/, { timeout: 15_000 });
    await page.waitForTimeout(1500);
    await foto(page, 'CP-CHA-008-03', 'Al pulsarlo, el chatbot lleva al jugador a la sección «Mi inventario»');
  });

  test('CP-CHA-008-04 · No ejecuta operaciones económicas (pujas o compras)', async ({ page, request }) => {
    const sub = datos.subastas.find((x) => x.vendedor === 'vendedor1_qa');
    const antes = (await llamar(request, 'GET', `/api/v1/subastas/${sub.id}`)).cuerpo.cantidadPujas;
    const s = await sesion(request, 'comprador_qa');
    const saldoAntes = (await llamar(request, 'GET', `http://localhost:8093/api/v1/creditos/${s.claims.uid}/saldo`, { token: s.token })).cuerpo.saldoDisponible;
    await entrar(page, 'comprador_qa');
    await abrirChat(page);
    const r = await preguntar(page, `puja 300 creditos en la subasta de ${sub.nombre}`);
    await foto(page, 'CP-CHA-008-04', `Se le pide al chatbot «puja 300 creditos en la subasta de ${sub.nombre}»: solo orienta, no puja`, { resaltar: r });
    const r2 = await preguntar(page, 'compra el arco del viento con mi tarjeta');
    await foto(page, 'CP-CHA-008-04', '«compra el arco del viento con mi tarjeta»: tampoco ejecuta compras', { resaltar: r2 });
    const despues = (await llamar(request, 'GET', `/api/v1/subastas/${sub.id}`)).cuerpo.cantidadPujas;
    const saldoDespues = (await llamar(request, 'GET', `http://localhost:8093/api/v1/creditos/${s.claims.uid}/saldo`, { token: s.token })).cuerpo.saldoDisponible;
    await fotoHttp(page, 'CP-CHA-008-04', `Verificación: pujas de la subasta ${antes} → ${despues}; saldo ${saldoAntes} → ${saldoDespues} (sin cambios)`, await llamar(request, 'GET', `/api/v1/subastas/${sub.id}`));
    expect(despues).toBe(antes);
    expect(saldoDespues).toBe(saldoAntes);
  });

  test('CP-CHA-008-05 · Reconoce al usuario, recuerda la conversación entre sesiones y permite restablecer preferencias y borrar el historial', async ({ page, request }) => {
    const apodo = `memoria${CORRIDA}`;
    await sesion(request, apodo);
    await entrar(page, apodo);
    const ventana = await abrirChat(page);
    await expect(ventana).toContainText(`Hola, ${apodo}`);
    await preguntar(page, 'como subo de nivel');
    await foto(page, 'CP-CHA-008-05', `El chatbot saluda a «${apodo}» por su apodo y ofrece consultar su cuenta`, { resaltar: ventana });
    await page.evaluate(() => sessionStorage.clear());
    await entrar(page, apodo);
    const otra = await abrirChat(page);
    await expect(otra).toContainText('como subo de nivel');
    await foto(page, 'CP-CHA-008-05', 'Tras cerrar sesión y volver a entrar, el chatbot conserva la conversación anterior', { resaltar: otra });
    await page.locator('[data-accion="preferencias-asistente"]').click();
    const prefs = page.locator('[data-chatbot-preferencias]');
    await expect(prefs).toBeVisible();
    await prefs.locator('[name="idioma"]').selectOption('EN');
    await prefs.locator('[name="nivelDetalle"]').selectOption('BREVE');
    await prefs.getByRole('button', { name: /guardar/i }).click();
    await page.waitForTimeout(800);
    await foto(page, 'CP-CHA-008-05', 'Preferencias del asistente: idioma Inglés, respuestas breves', { resaltar: prefs });
    const volver = page.locator('[data-chatbot-preferencias] [data-accion="volver-al-chat"]');
    if (await volver.isVisible().catch(() => false)) await volver.click();
    const enIngles = await preguntar(page, 'como me registro');
    await expect(enIngles).toContainText('To create your account');
    await foto(page, 'CP-CHA-008-05', 'Con la preferencia guardada, la misma pregunta en español se responde en inglés (personalización)', { resaltar: enIngles });
    await page.locator('[data-accion="preferencias-asistente"]').click();
    await prefs.locator('[name="idioma"]').selectOption('AUTOMATICO');
    await prefs.locator('[name="nivelDetalle"]').selectOption('NORMAL');
    await prefs.getByRole('button', { name: /guardar/i }).click();
    await page.waitForTimeout(800);
    await foto(page, 'CP-CHA-008-05', 'El usuario restablece sus preferencias (Automático / Normal)', { resaltar: prefs });
    if (await volver.isVisible().catch(() => false)) await volver.click();
    await page.locator('[data-accion="borrar-conversacion"]').click();
    if (await page.getByRole('dialog').last().isVisible({ timeout: 1500 }).catch(() => false)) await aceptarDialogo(page);
    await page.waitForTimeout(1000);
    await page.reload();
    const limpia = await abrirChat(page);
    await expect(limpia).not.toContainText('como subo de nivel');
    await foto(page, 'CP-CHA-008-05', '«Borrar conversación»: tras recargar, el historial quedó eliminado', { resaltar: limpia });
  });
});
