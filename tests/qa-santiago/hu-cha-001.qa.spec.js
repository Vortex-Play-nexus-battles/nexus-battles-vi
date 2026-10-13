// HU-CHA-001 · Ventana de chat del chatbot (Julián Reyes)
import { test } from '@playwright/test';
import { VISTA, abrirChat, entrar, esperar, expect, foto, preguntar } from './ayudante-qa.js';

const icono = (page) => page.locator('button.chatbot-flotante');

async function enEsquina(page) {
  const caja = await icono(page).boundingBox();
  const vp = page.viewportSize();
  return caja && caja.x > vp.width * 0.8 && caja.y > vp.height * 0.75;
}

test.describe('HU-CHA-001 · Ventana de chat del chatbot', () => {
  test('CP-CHA-001-01 · Ícono flotante persistente en la esquina inferior derecha de todas las vistas', async ({ page }) => {
    const publicas = [['/', 'Portada (visitante)'], ['/login', 'Inicio de sesión'], ['/registro', 'Registro'], ['/subastas', 'Subastas (visitante)']];
    for (const [ruta, nombre] of publicas) {
      await page.goto(ruta);
      await expect(icono(page)).toBeVisible();
      expect(await enEsquina(page), `${nombre}: el ícono debe estar abajo a la derecha`).toBe(true);
      if (ruta === '/' || ruta === '/subastas') await foto(page, 'CP-CHA-001-01', `${nombre}: ícono flotante del asistente abajo a la derecha`, { resaltar: icono(page) });
    }
    await entrar(page, 'comprador_qa');
    for (const [ruta, nombre] of [['/inicio', 'Inicio del jugador'], [VISTA('cuentas/tienda.html'), 'Tienda'], ['/cuenta#resumen', 'Mi cuenta'], ['/inventario', 'Mi inventario']]) {
      await page.goto(ruta);
      await expect(icono(page)).toBeVisible();
      expect(await enEsquina(page)).toBe(true);
      if (nombre === 'Tienda') await foto(page, 'CP-CHA-001-01', `${nombre} (jugador con sesión): el ícono se mantiene en la misma posición`, { resaltar: icono(page) });
    }
    await page.mouse.wheel(0, 3000);
    await esperar(500);
    await expect(icono(page)).toBeInViewport();
    await foto(page, 'CP-CHA-001-01', 'Tras desplazarse hasta abajo, el ícono sigue fijo en pantalla', { resaltar: icono(page) });
  });

  test('CP-CHA-001-02 · Ventana con mensajes diferenciados, indicador de escritura, marcas de tiempo, autocompletado y sugerencias', async ({ page }) => {
    await entrar(page, 'comprador_qa');
    const ventana = await abrirChat(page);
    const rapidas = page.locator('.chatbot-rapidas');
    await expect(rapidas).toBeVisible();
    await foto(page, 'CP-CHA-001-02', 'Ventana abierta: cabecera (Preferencias, Soporte, Borrar conversación, minimizar, cerrar), sugerencias rápidas y campo de texto', { resaltar: ventana });
    const entrada = page.locator('.chatbot-ventana__entrada');
    await entrada.pressSequentially('como pu', { delay: 120 });
    const auto = page.locator('.chatbot-autocompletar__opcion').first();
    await expect(auto).toBeVisible();
    await foto(page, 'CP-CHA-001-02', 'Autocompletado mientras se escribe «como pu»', { resaltar: page.locator('.chatbot-autocompletar') });
    await entrada.fill('');
    await page.route('**/api/v1/chat/mensajes', async (r) => { await esperar(2500); await r.continue(); });
    await entrada.fill('como subo de nivel');
    await entrada.press('Enter');
    const escribiendo = page.locator('.chatbot-ventana__escribiendo');
    await expect(escribiendo).toBeVisible();
    await foto(page, 'CP-CHA-001-02', 'Indicador «El asistente está escribiendo…» mientras llega la respuesta', { resaltar: escribiendo });
    await expect(escribiendo).toHaveCount(0, { timeout: 15_000 });
    await page.unroute('**/api/v1/chat/mensajes');
    const usuario = page.locator('.chatbot-ventana__mensaje').filter({ hasText: 'como subo de nivel' }).last();
    const bot = page.locator('.chatbot-ventana__mensaje--bot').last();
    await expect(bot.locator('.chatbot-ventana__meta').first()).toContainText(/\d{1,2}:\d{2}/);
    const claseUsuario = await usuario.getAttribute('class');
    expect(claseUsuario).not.toContain('--bot');
    await foto(page, 'CP-CHA-001-02', 'Mensaje del usuario y respuesta del bot con estilos distintos y marca de tiempo', { resaltar: [usuario, bot] });
  });

  test('CP-CHA-001-03 · Ventana minimizable y redimensionable; limpiar historial; transferencia a soporte humano', async ({ page }) => {
    await entrar(page, 'comprador_qa');
    const ventana = await abrirChat(page);
    await preguntar(page, 'que es nexus battles');
    const antes = await ventana.boundingBox();
    const asa = page.locator('[data-accion="tamano-asistente"]');
    await asa.focus();
    for (let i = 0; i < 6; i += 1) await page.keyboard.press('ArrowLeft');
    for (let i = 0; i < 4; i += 1) await page.keyboard.press('ArrowUp');
    await esperar(500);
    const despues = await ventana.boundingBox();
    await foto(page, 'CP-CHA-001-03', `Redimensionada con su esquina (${Math.round(antes.width)}×${Math.round(antes.height)} → ${Math.round(despues.width)}×${Math.round(despues.height)} px)`, { resaltar: ventana });
    expect(despues.width * despues.height).toBeGreaterThan(antes.width * antes.height);
    await page.locator('[data-accion="minimizar-asistente"]').click();
    await esperar(500);
    await expect(page.locator('.chatbot-ventana__registro')).toBeHidden();
    await foto(page, 'CP-CHA-001-03', 'Minimizada a su barra de título sin perder la conversación', { resaltar: ventana });
    await page.locator('[data-accion="minimizar-asistente"]').click();
    await page.locator('[data-accion="hablar-con-soporte"]').first().click();
    const soporte = page.locator('[data-chatbot-soporte]');
    await expect(soporte).toBeVisible();
    await foto(page, 'CP-CHA-001-03', '«Soporte»: transferencia a soporte humano (formulario de solicitud y solicitudes del jugador)', { resaltar: soporte });
    await page.locator('[data-chatbot-soporte] [data-accion="volver-al-chat"]').click();
    await page.locator('[data-accion="borrar-conversacion"]').click();
    if (await page.getByRole('dialog').last().isVisible({ timeout: 1500 }).catch(() => false)) await page.getByRole('dialog').last().getByRole('button').last().click();
    await esperar(1000);
    await expect(page.locator('.chatbot-ventana__registro')).not.toContainText('juego de rol por turnos');
    await foto(page, 'CP-CHA-001-03', '«Borrar conversación»: el historial queda vacío', { resaltar: ventana });
  });

  test('CP-CHA-001-04 · Funciona para visitantes y registrados; al visitante que pide datos personales se le exige autenticarse', async ({ page }) => {
    await page.goto('/');
    const ventana = await abrirChat(page);
    await expect(ventana).toContainText(/visitante/i);
    const general = await preguntar(page, 'como funciona el combate');
    await expect(general).toContainText(/turno/i);
    await foto(page, 'CP-CHA-001-04', 'Visitante sin sesión: el asistente responde preguntas generales', { resaltar: general });
    await entrar(page, 'comprador_qa');
    const v2 = await abrirChat(page);
    await expect(v2).toContainText('Hola, comprador_qa');
    const r = await preguntar(page, 'como van mis subastas');
    await expect(r).toContainText(/Vas ganando|retenidos/);
    await foto(page, 'CP-CHA-001-04', 'Jugador registrado: saludo por su apodo y respuesta con sus propios datos', { resaltar: r });
  });

  test('CP-CHA-001-05 · Adjuntar capturas de pantalla en la conversación', async ({ page }) => {
    await entrar(page, 'comprador_qa');
    const ventana = await abrirChat(page);
    await foto(page, 'CP-CHA-001-05', 'Se busca el control para adjuntar una captura en el formulario del asistente', { resaltar: page.locator('.chatbot-ventana__formulario') });
    await expect(ventana.locator('input[type="file"], [data-accion*="adjuntar"]'), 'la ventana debe permitir adjuntar capturas (CA2)').not.toHaveCount(0, { timeout: 3000 });
  });

  test('CP-CHA-001-06 · Al visitante que pregunta por información personal se le exige autenticarse', async ({ page }) => {
    await page.goto('/');
    await abrirChat(page);
    const r1 = await preguntar(page, 'como van mis subastas');
    const r2 = await preguntar(page, 'que tengo en mi inventario');
    await foto(page, 'CP-CHA-001-06', 'Visitante: «como van mis subastas» y «que tengo en mi inventario» reciben una respuesta genérica o «no entendí», sin pedir iniciar sesión', { resaltar: [r1, r2] });
    await expect(r1, 'debe pedir iniciar sesión').toContainText(/inicia sesi|iniciar sesi/i);
    await expect(r2, 'debe pedir iniciar sesión').toContainText(/inicia sesi|iniciar sesi/i);
  });
});
