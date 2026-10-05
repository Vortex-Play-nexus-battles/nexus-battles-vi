// @ts-check
/**
 * El recorrido de la auditoría de AWS DEV del 30-sep (informe de Santiago,
 * `Informe-revision-Nexus-Battles-VI.pdf`), de punta a punta, con UNA cuenta
 * de jugador recién creada y los servicios reales del banco
 * (`tests/e2e/compose.yml`). Nada simulado: cada paso pasa por el borde.
 *
 * El orden es el suyo: inicio → notificaciones → jugar → batalla contra la IA
 * → chat general → mensaje privado → bloquear → tienda (filtros, lista de
 * deseos, ficha, calificar, opinar, eliminar, reportar) → carrito → pago
 * simulado → inventario → Mi cuenta (foto, estadísticas, pestañas,
 * privacidad) → misiones → subastas → torneos. En cada parada se afirma lo
 * que el informe encontró roto, para que no pueda volver sin que esto falle:
 *
 *   - la alerta al entrar respondía 500 → 200;
 *   - la campana solo contaba al abrir la bandeja → cuenta sola;
 *   - con un filtro, «0 salas abiertas ahora mismo» → «con estos filtros» y
 *     «Quitar filtros»;
 *   - tras el combate, «la partida sigue en curso» y controles vivos → el
 *     desenlace y sin controles, también al recargar;
 *   - el chat dejaba pasar insultos y dibujos con símbolos → no salen, y el
 *     aviso dice cuál regla falló;
 *   - «Bloquear» respondía «No pudimos bloquear» → bloquea de verdad;
 *   - la lista de deseos no se podía ver → «Solo mi lista de deseos»;
 *   - «Eliminar tu comentario» se abría detrás de la ficha → se pulsa encima;
 *   - un reporte ocultaba el comentario para todos → sigue en el hilo;
 *   - lo que ya tienes se podía volver a comprar → no se ofrece, 409;
 *   - la foto de perfil daba 404 → se ve;
 *   - «Tus batallas» sin acción → «Ver resultado»;
 *   - cambiar el #hash no cambiaba de pestaña → cambia;
 *   - misiones 504 y subastas 502 → cargan.
 *
 * Lo que no se recorre aquí, y por qué: el asistente (ms-chatbot no está en
 * este banco; en DEV lo cubre la prueba del profesor), la descarga de datos y
 * el cierre de cuenta (el portal de privacidad es del Grupo 4, HU-PRV-004/005/006,
 * y todavía no existe: aquí se comprueba que la vista lo diga en vez de fallar),
 * y el pago en USD/EUR (D-32: la tasa la fija el PO en Parámetros).
 *
 * Los apodos, el producto y los textos llevan un sufijo de la corrida: repetirla
 * sobre el mismo banco no choca con la anterior.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

import { sesionDe as sesionDelBanco } from './ayudantes/cuentas.js';
import { servicioNoDesplegadoDe } from './ayudantes/no-desplegados.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const CLAVE = 'Contrasena-E2E-2026';
const ADMIN = process.env.E2E_ADMIN ?? 'admin_e2e';
const SUFIJO = Date.now().toString(36);
const VISTAS = '/frontend/app-web/src';
const RUTA = {
  inicio: `${VISTAS}/cuentas/index.html`,
  jugar: `${VISTAS}/plataforma/salas-partidas/batallas.html`,
  sala: `${VISTAS}/plataforma/salas-partidas/sala-batalla.html`,
  chat: `${VISTAS}/plataforma/salas-partidas/chat.html`,
  tienda: `${VISTAS}/cuentas/tienda.html`,
  inventario: `${VISTAS}/contenido/inventario/inventario.html`,
  cuenta: `${VISTAS}/cuentas/perfil.html`,
  misiones: `${VISTAS}/contenido/misiones/misiones.html`,
  subastas: `${VISTAS}/cuentas/subastas.html`,
  torneos: `${VISTAS}/plataforma/torneos/torneos.html`,
};
const IMAGEN = `${VISTAS}/cuentas/avatares/arquero-cazador.jpg`;
/** Un PNG de 1×1: lo mínimo que es una imagen de verdad. */
const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==',
  'base64',
);
const TARJETA = {
  titular: 'Recorrido E2E',
  numeroTarjeta: '4242 4242 4242 4242',
  vencimiento: '12/39',
  codigoSeguridad: '123',
};

function sesionDe(api, apodo) {
  return sesionDelBanco(api, apodo, { clave: CLAVE, base: BORDE });
}

function conToken(token, extra = {}) {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', ...extra };
}

/** Deja la sesión en el navegador como la deja login.js. */
async function conSesion(page, jugador) {
  await page.addInitScript(
    ([token, apodo, uid]) => {
      sessionStorage.setItem('nexus.token', token);
      sessionStorage.setItem('nexus.apodoActual', apodo);
      sessionStorage.setItem('nexus.rolActual', 'JUGADOR');
      sessionStorage.setItem('nexus.usuarioId', uid);
    },
    [jugador.token, jugador.apodo, jugador.claims.uid],
  );
}

/**
 * Las respuestas 5xx de `/api/` mientras dura una parada, sin las de los
 * servicios que el catálogo declara fuera de despliegue (las mismas reglas
 * que la prueba del profesor). El informe contó 401, 404, 500, 502 y 504 en
 * la consola: aquí no puede quedar ningún 5xx.
 */
function vigilarFallos(page) {
  const fallos = [];
  page.on('response', (respuesta) => {
    const ruta = new URL(respuesta.url()).pathname;
    if (
      ruta.startsWith('/api/') &&
      respuesta.status() >= 500 &&
      servicioNoDesplegadoDe(ruta) === null
    ) {
      fallos.push(`${respuesta.request().method()} ${ruta} → ${respuesta.status()}`);
    }
  });
  return fallos;
}

/** Solo letras: la lista negra normaliza los dígitos (0→o, 1→i, 3→e…). */
function soloLetras(texto) {
  return texto.replace(/\d/g, (d) => 'abcdefghij'[Number(d)]);
}

test.describe('El recorrido de la auditoría del 30-sep, con un jugador limpio', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let admin;
  let jugador;
  let otro;
  let productoId;
  let sala;
  let partida;
  const producto = {
    nombre: `Lanza del recorrido E2E ${SUFIJO}`,
    imagen: IMAGEN,
    descripcion: 'Arma del recorrido de la auditoría: se vende en créditos y en dinero real.',
    tipo: 'ARMA',
    tiraje: -1,
    premium: false,
    precioCreditos: 120,
    precioMonedaReal: 30000,
    poderDeAtaque: 9,
    tasaDeCaida: 50,
  };

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    admin = await sesionDe(api, ADMIN);
    expect(admin.claims.rol, 'sembrar.sh deja a admin_e2e como ADMINISTRADOR').toBe(
      'ADMINISTRADOR',
    );
    jugador = await sesionDe(api, `recorrido_${SUFIJO}`);
    otro = await sesionDe(api, `recorrido_otro_${SUFIJO}`);
    expect(jugador.claims.rol).toBe('JUGADOR');

    // El alta de un jugador nuevo (500 créditos y su héroe equipado) termina sola.
    for (const quien of [jugador, otro]) {
      await expect
        .poll(
          async () => {
            const r = await api.get('/api/v1/auth/onboarding', { headers: conToken(quien.token) });
            return r.ok() ? (await r.json()).estado : `HTTP ${r.status()}`;
          },
          { timeout: 60_000, message: `el alta de ${quien.apodo} no terminó` },
        )
        .toBe('COMPLETO');
    }

    // El producto de la tienda de esta corrida, como lo da de alta un administrador.
    const alta = await api.post('/api/v1/productos', {
      headers: conToken(admin.token),
      data: producto,
    });
    expect(alta.status(), await alta.text()).toBe(201);
    productoId = (await alta.json()).id;
  });

  test.afterAll(async () => {
    await api?.dispose();
  });

  test('1 · entrar: la alerta del catálogo responde y la campana cuenta sola', async ({ page }) => {
    // El informe: «GET /productos/alertas/inicio-sesion devolvió 500 justo tras el login».
    const alertas = await api.get('/api/v1/productos/alertas/inicio-sesion', {
      headers: conToken(jugador.token),
    });
    expect(alertas.status(), await alertas.text()).toBe(200);

    // Punto de partida limpio: lo que traiga el alta se da por leído.
    const bandeja = `/api/v1/users/${jugador.claims.uid}/notifications`;
    const previa = await api.get(bandeja, { headers: conToken(jugador.token) });
    expect(previa.status(), await previa.text()).toBe(200);
    for (const aviso of (await previa.json()).avisos.filter((a) => !a.leida)) {
      const leido = await api.post(`${bandeja}/${encodeURIComponent(aviso.id)}/read`, {
        headers: conToken(jugador.token),
      });
      expect(leido.status(), await leido.text()).toBe(200);
    }

    const fallos = vigilarFallos(page);
    await conSesion(page, jugador);
    await page.goto(`${BORDE}${RUTA.inicio}`);
    const campana = page.locator('[data-cabecera-app] .cabecera__campana');
    const contador = page.locator('[data-cabecera-app] [data-zona="contador"]');
    await expect(campana).toHaveAttribute('aria-label', /sin notificaciones sin leer/, {
      timeout: 20_000,
    });

    // «Incluso después de la batalla no llegó aviso»: el número tiene que
    // subir sin abrir la bandeja. Le escribe el otro jugador.
    const mensaje = await api.post(
      `/api/v1/mensajes-directos/conversaciones/${jugador.claims.uid}/mensajes`,
      {
        headers: conToken(otro.token),
        data: { texto: `hola, ¿jugamos? ${SUFIJO}` },
      },
    );
    expect(mensaje.status(), await mensaje.text()).toBe(201);
    await expect(contador, 'la cabecera cuenta el aviso sin abrir la bandeja').toHaveText('1', {
      timeout: 20_000,
    });
    expect(fallos, 'ninguna petición respondió 5xx').toEqual([]);
  });

  test('2 · jugar online: un filtro sin resultados lo dice, y se quita', async ({ page }) => {
    const fallos = vigilarFallos(page);
    await conSesion(page, jugador);
    await page.goto(`${BORDE}${RUTA.jugar}`);
    const subtitulo = page.locator('[data-zona="subtitulo"]');
    await expect(subtitulo).not.toHaveText('Buscando batallas', { timeout: 20_000 });

    // Una sala contra la IA nace LLENA: «contra la IA» + «abierta» no tiene
    // ninguna, y antes la vista decía «0 salas abiertas ahora mismo».
    await page.locator('[name="modalidad"]').selectOption('CONTRA_IA');
    await page.locator('[name="estado"]').selectOption('ABIERTA');
    await expect(subtitulo).toHaveText('Ninguna sala con estos filtros', { timeout: 20_000 });
    const quitar = page.getByRole('button', { name: 'Quitar filtros' });
    await expect(quitar).toBeVisible();
    await quitar.click();
    await expect(page.locator('[name="modalidad"]')).toHaveValue('');
    await expect(page.locator('[name="estado"]')).toHaveValue('');
    await expect(subtitulo).not.toContainText('con estos filtros', { timeout: 20_000 });
    expect(fallos, 'ninguna petición respondió 5xx').toEqual([]);
  });

  test('3 · una batalla contra la IA hasta el final; al recargar sigue terminada', async ({
    page,
  }) => {
    // El combate tarda lo que tarde alguien en caer (reglas del documento).
    test.setTimeout(480_000);
    const creada = await api.post('/api/v1/salas', {
      headers: conToken(jugador.token),
      data: { modalidad: 'CONTRA_IA', maximoParticipantes: 2, recompensaCreditos: 0, heroesIA: 1 },
    });
    expect(creada.status(), await creada.text()).toBe(201);
    sala = await creada.json();
    const inicio = await api.post(`/api/v1/salas/${sala.id}/partida`, {
      headers: conToken(jugador.token),
    });
    expect(inicio.status(), await inicio.text()).toBe(201);
    partida = await inicio.json();

    const fallos = vigilarFallos(page);
    await conSesion(page, jugador);
    await page.goto(`${BORDE}${RUTA.sala}?sala=${sala.id}&partida=${partida.id}`);
    const deLaPartida = async () => {
      const r = await api.get(`/api/v1/partidas/${partida.id}`, {
        headers: conToken(jugador.token),
      });
      expect(r.status(), await r.text()).toBe(200);
      return r.json();
    };
    let golpes = 0;
    while (partida.estado === 'EN_CURSO' && golpes < 150) {
      await expect
        .poll(
          async () => {
            partida = await deLaPartida();
            return (
              partida.estado !== 'EN_CURSO' || partida.turnoActual.idJugador === jugador.claims.uid
            );
          },
          { timeout: 25_000, message: `golpe ${golpes + 1}: la máquina no devuelve el turno` },
        )
        .toBe(true);
      if (partida.estado !== 'EN_CURSO') {
        break;
      }
      const boton = page.locator('[data-zona="acciones"] [data-atacar]').first();
      await expect(boton).toBeEnabled({ timeout: 20_000 });
      const turnoPrevio = partida.turnoActual.numeroTurno;
      await boton.click({ timeout: 5_000 }).catch(() => {});
      await expect
        .poll(
          async () => {
            partida = await deLaPartida();
            return partida.estado !== 'EN_CURSO' || partida.turnoActual.numeroTurno > turnoPrevio;
          },
          { timeout: 25_000, message: `golpe ${golpes + 1}: ni rota el turno ni acaba` },
        )
        .toBe(true);
      golpes += 1;
    }
    expect(partida.estado, `no terminó en ${golpes} golpes`).toBe('FINALIZADA');

    // El informe: tras terminar, «la partida sigue en curso» y especiales con
    // «No es tu turno». Ahora: el desenlace y sin controles…
    const resultado = page.locator('[data-zona="resultado"]');
    await expect(resultado).toHaveText(/has ganado|has perdido|empate/i, { timeout: 20_000 });
    await expect(page.locator('[data-zona="acciones"]')).toBeHidden();
    // …y lo mismo al volver a abrirla: una partida FINALIZADA nace cerrada.
    await page.reload();
    await expect(resultado).toHaveText(/has ganado|has perdido|empate/i, { timeout: 20_000 });
    await expect(page.locator('[data-zona="acciones"]')).toBeHidden();
    await expect(page.locator('body')).not.toContainText('sigue en curso');
    expect(fallos, 'ninguna petición respondió 5xx').toEqual([]);
  });

  test('4 · chat general: lo vetado y un dibujo con símbolos no salen, y se dice por qué', async ({
    page,
  }) => {
    // Un término que el administrador añade ahora y se retira al final: la
    // lista del banco trae los de otras suites.
    const termino = `vetadorec${soloLetras(SUFIJO)}`;
    const alta = await api.post('/api/v1/lista-negra/terminos', {
      headers: conToken(admin.token),
      data: { termino },
    });
    expect([200, 201], await alta.text()).toContain(alta.status());
    try {
      const fallos = vigilarFallos(page);
      await conSesion(page, jugador);
      await page.goto(`${BORDE}${RUTA.chat}`);
      await expect(page.locator('[data-zona="conexion"]')).toHaveAttribute(
        'data-estado-canal',
        'conectado',
        { timeout: 20_000 },
      );
      const formulario = page.locator('#formulario-chat');
      const campo = formulario.locator('#texto');
      const aviso = formulario.locator('[data-zona="aviso"]');

      // Una grosería (aquí, el término vetado de la corrida).
      const insulto = `eres un ${termino}`;
      await campo.fill(insulto);
      await formulario.locator('[type="submit"]').click();
      await expect(aviso).toContainText('Tu mensaje no salió', { timeout: 15_000 });
      await expect(campo, 'el texto vuelve al campo para cambiarlo').toHaveValue(insulto);

      // El bloque de dibujo del informe: más líneas de las que admite un mensaje.
      const dibujo = ['(\\_/)', '(o.o)', '(> <)', ' /|\\', '/ | \\', ' / \\', '/   \\'].join('\n');
      await campo.fill(dibujo);
      await formulario.locator('[type="submit"]').click();
      await expect(aviso).toContainText('Revisa el mensaje', { timeout: 15_000 });
      await expect(aviso, 'dice qué regla falló, no solo el largo').toContainText(
        'demasiadas líneas',
      );
      await expect(page.locator('[data-zona="mensajes"]')).not.toContainText(termino);
      expect(fallos, 'ninguna petición respondió 5xx').toEqual([]);
    } finally {
      const baja = await api.delete(`/api/v1/lista-negra/terminos/${encodeURIComponent(termino)}`, {
        headers: conToken(admin.token),
      });
      expect([200, 204, 404]).toContain(baja.status());
    }
  });

  test('4b · HU-COM-007: el singular de un insulto de la semilla no pasa, y la detección trae su regla', async () => {
    // Informes del 4-oct (RFINAL-02): «gilipolla» entraba al chat con
    // «gilipollas» en la semilla provisional (V7). Ahora el detector declina
    // también el número de los insultos dados de alta en plural, y quien ve el
    // detalle recibe el id de la regla (moderacion-lista-negra.yaml 2.1.0).
    const verificar = (texto, token) =>
      api.post('/api/v1/lista-negra/verificar', {
        headers: token ? conToken(token) : { 'Content-Type': 'application/json' },
        data: { texto, contexto: 'CHAT_GENERAL' },
      });

    const anonimo = await verificar('eres un gilipolla');
    expect(anonimo.status(), await anonimo.text()).toBe(200);
    const sinDetalle = await anonimo.json();
    expect(sinDetalle).toMatchObject({ aprobado: false, accion: 'BLOQUEAR' });
    expect(sinDetalle.reglas, 'sin token no se dice qué regla saltó').toBeUndefined();

    const comoAdmin = await verificar('eres un gilipolla', admin.token);
    expect(comoAdmin.status(), await comoAdmin.text()).toBe(200);
    const conDetalle = await comoAdmin.json();
    expect(conDetalle).toMatchObject({ aprobado: false, categoria: 'OFENSIVO' });
    expect(conDetalle.coincidencias).toContain('gilipollas');
    expect(conDetalle.reglas).toHaveLength(conDetalle.coincidencias.length);
    expect(conDetalle.reglas.every(Number.isInteger)).toBe(true);

    // Y lo corriente sigue entrando (falsos positivos).
    const limpio = await verificar('una polla de agua en el estanque', admin.token);
    expect(await limpio.json()).toMatchObject({ aprobado: true, accion: 'PERMITIR' });
  });

  test('5 · mensaje privado y «Bloquear jugador» de verdad', async ({ page }) => {
    const fallos = vigilarFallos(page);
    await conSesion(page, jugador);
    await page.goto(`${BORDE}${RUTA.chat}#privados`);
    const privados = page.locator('.chat__privados');
    await expect(privados.locator('.mensajes-privados')).toBeVisible({ timeout: 20_000 });
    const conversacion = privados.locator('.conversaciones__item').filter({
      has: page.locator('.conversaciones__apodo', { hasText: new RegExp(`^${otro.apodo}$`) }),
    });
    await expect(conversacion, 'la conversación del paso 1 está en la lista').toBeVisible({
      timeout: 20_000,
    });
    await conversacion.click();
    await expect(privados.locator('#mensajes-privados-con')).toHaveText(otro.apodo);

    // El informe: «Bloquear» pedía confirmación y luego «No pudimos bloquear».
    await privados.locator('[data-accion="bloquear"]').click();
    await page.locator('[role="dialog"] [data-accion="confirmar"]').click();
    const hilo = privados.locator('.mensajes-privados__hilo');
    await expect(hilo.locator('[data-zona="bloqueo"]')).toContainText(
      `Bloqueaste a ${otro.apodo}`,
      { timeout: 15_000 },
    );
    await expect(hilo.locator('#mensaje-privado')).toBeDisabled();

    // Y el servicio lo cumple: el otro ya no le escribe.
    const deOtro = await api.post(
      `/api/v1/mensajes-directos/conversaciones/${jugador.claims.uid}/mensajes`,
      { headers: conToken(otro.token), data: { texto: `¿sigues ahí? ${SUFIJO}` } },
    );
    expect(deOtro.status(), await deOtro.text()).toBe(403);
    expect((await deOtro.json()).type).toMatch(/destinatario-no-admite$/);

    // Se deshace para no dejar el banco con un bloqueo de esta corrida.
    const quitar = await api.delete(
      `/api/v1/mensajes-directos/conversaciones/${otro.claims.uid}/bloqueo`,
      { headers: conToken(jugador.token) },
    );
    expect(quitar.status(), await quitar.text()).toBe(200);
    expect(fallos, 'ninguna petición respondió 5xx').toEqual([]);
  });

  test('6 · tienda: buscar, desear y ver la lista, calificar, opinar, eliminar encima de la ficha y reportar sin ocultar', async ({
    page,
  }) => {
    // La vitrina copia el catálogo 30 s: se espera a que el producto esté.
    await expect
      .poll(
        async () => {
          const r = await api.get(
            `/api/v1/vitrina?busqueda=${encodeURIComponent(SUFIJO)}&size=50`,
            {
              headers: conToken(jugador.token),
            },
          );
          return r.ok() ? (await r.json()).content.some((p) => p.id === productoId) : false;
        },
        { timeout: 45_000, intervals: [1_000, 2_000, 5_000] },
      )
      .toBe(true);
    // Una opinión del otro jugador, que este reportará.
    const ajena = await api.post(`/api/v1/products/${productoId}/comments`, {
      headers: conToken(otro.token),
      data: { texto: `No me gustó nada esta lanza ${SUFIJO}`, estrellas: 2 },
    });
    expect(ajena.status(), await ajena.text()).toBe(201);
    const comentarioAjeno = await ajena.json();

    const fallos = vigilarFallos(page);
    await conSesion(page, jugador);
    await page.goto(`${BORDE}${RUTA.tienda}`);
    await expect(page.locator('#productos-grid .product-card').first()).toBeVisible({
      timeout: 20_000,
    });
    await page.locator('#busqueda-tienda').fill(SUFIJO);
    const tarjeta = page.locator('.product-card', { hasText: producto.nombre });
    await expect(tarjeta).toBeVisible({ timeout: 20_000 });

    // La lista de deseos: el corazón guarda y, ahora, hay dónde verla.
    const deseo = page.waitForResponse(
      (r) => r.url().includes(`/lista-deseos/${productoId}`) && r.request().method() === 'PUT',
    );
    await tarjeta.locator(`[data-deseo="${productoId}"]`).click();
    expect((await deseo).status()).toBe(200);
    await expect(tarjeta.locator(`[data-deseo="${productoId}"]`)).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    await page.locator('#busqueda-tienda').fill('');
    await page.locator('#solo-deseos').check();
    const deseados = page.locator('#productos-grid .product-card');
    await expect(deseados).toHaveCount(1, { timeout: 20_000 });
    await expect(deseados.first()).toContainText(producto.nombre);
    await page.locator('#solo-deseos').uncheck();
    await page.locator('#busqueda-tienda').fill(SUFIJO);

    // La ficha: calificar sin comentar…
    await page.locator(`[data-ver-producto="${productoId}"]`).click();
    const ficha = page.locator('[role="dialog"].ficha');
    await expect(ficha.locator('.ficha__nombre')).toHaveText(producto.nombre);
    const control = ficha.locator('.calificar-producto');
    await expect(control).toHaveAttribute('data-estado', 'pendiente', { timeout: 20_000 });
    await control.locator('label.selector-estrellas__opcion').nth(3).click();
    const calificada = page.waitForResponse(
      (r) => r.url().endsWith(`/products/${productoId}/rating`) && r.request().method() === 'POST',
    );
    await control.locator('[data-accion="calificar"]').click();
    expect((await calificada).status()).toBe(201);
    await expect(control).toHaveAttribute('data-estado', 'calificado');

    // …opinar…
    const texto = `Buena lanza para el recorrido ${SUFIJO}`;
    await ficha.locator('.redactor-comentario textarea').fill(texto);
    const publicada = page.waitForResponse(
      (r) =>
        r.url().includes(`/products/${productoId}/comments`) && r.request().method() === 'POST',
    );
    await ficha.locator('[data-accion="publicar-opinion"]').click();
    expect((await publicada).status()).toBe(201);
    const propia = ficha.locator('.hilo-comentarios .comentario', { hasText: texto });
    await expect(propia).toBeVisible({ timeout: 20_000 });

    // …y eliminar: la confirmación se abre ENCIMA de la ficha y se puede
    // pulsar (el informe: aparecía detrás, había que cerrar la ficha).
    await propia.locator('[data-accion="eliminar-comentario"]').click();
    const confirmacion = page.getByRole('dialog', { name: 'Eliminar tu comentario' });
    await expect(confirmacion).toBeVisible();
    await confirmacion.locator('[data-accion="confirmar"]').click();
    await expect(confirmacion).toBeHidden();
    await expect(propia).toHaveCount(0, { timeout: 20_000 });
    await expect(ficha, 'la ficha sigue abierta y consistente').toBeVisible();
    await expect(control, 'la calificación va aparte y se queda').toHaveAttribute(
      'data-estado',
      'calificado',
    );

    // Reportar el ajeno: el informe vio bajar el contador de 28 a 27 con un
    // solo reporte. Encolar no es ocultar: sigue en el hilo de todos.
    const reporte = await api.post(
      `/api/v1/products/${productoId}/comments/${comentarioAjeno.id}/reportes`,
      {
        headers: conToken(jugador.token),
        data: { categoria: 'SPAM', descripcion: `Reporte del recorrido ${SUFIJO}` },
      },
    );
    expect(reporte.status(), await reporte.text()).toBe(201);
    expect((await reporte.json()).estadoDelComentario).toBe('PUBLICADO');
    const hilo = await api.get(`/api/v1/products/${productoId}/comments`);
    expect(hilo.status()).toBe(200);
    expect((await hilo.json()).comentarios.map((c) => c.id)).toContain(comentarioAjeno.id);
    await page.getByRole('button', { name: 'Cerrar la ficha del producto' }).click();
    await page.locator(`[data-ver-producto="${productoId}"]`).click();
    await expect(
      ficha.locator('.hilo-comentarios .comentario', { hasText: `No me gustó nada esta lanza` }),
    ).toBeVisible({ timeout: 20_000 });
    expect(fallos, 'ninguna petición respondió 5xx').toEqual([]);
  });

  test('7 · carrito y pago simulado; lo comprado ya no se ofrece', async ({ page }) => {
    const fallos = vigilarFallos(page);
    await conSesion(page, jugador);
    await page.goto(`${BORDE}${RUTA.tienda}`);
    await page.locator('#busqueda-tienda').fill(SUFIJO);
    const tarjeta = page.locator('.product-card', { hasText: producto.nombre });
    await expect(tarjeta).toBeVisible({ timeout: 20_000 });
    await expect(page.locator('#cart-items')).toContainText('vacío');

    const alta = page.waitForResponse(
      (r) => r.url().includes('/api/v1/carrito/items') && r.request().method() === 'POST',
    );
    await tarjeta.getByRole('button', { name: `Añadir ${producto.nombre} al carrito` }).click();
    expect((await alta).status()).toBe(200);
    await expect(
      page.locator('#cart-items .cart-item', { hasText: producto.nombre }),
    ).toBeVisible();

    // «No se probó el pago»: aquí se paga con la tarjeta de prueba.
    await page.locator('#btn-pagar').click();
    const dialogo = page.getByRole('dialog', { name: 'Pagar tu compra' });
    await dialogo.getByLabel('Nombre del titular de la tarjeta').fill(TARJETA.titular);
    await dialogo.getByLabel('Número de tarjeta').fill(TARJETA.numeroTarjeta);
    await dialogo.getByLabel('Fecha de vencimiento (MM/AA)').fill(TARJETA.vencimiento);
    await dialogo.getByLabel('Código de seguridad').fill(TARJETA.codigoSeguridad);
    const cobro = page.waitForResponse(
      (r) => r.url().includes('/api/v1/checkout') && r.request().method() === 'POST',
    );
    await dialogo.locator('[data-accion="confirmar-pago"]').click();
    expect((await cobro).status()).toBe(201);
    await expect(dialogo.locator('.pago__resultado')).toContainText('Compra completada');

    // «Ya lo tienes» y aun así se podía añadir: ya no se ofrece, y el
    // servicio tampoco lo admite (RF-CAR-004).
    await page.reload();
    await page.locator('#busqueda-tienda').fill(SUFIJO);
    const propia = page.locator('.product-card', { hasText: producto.nombre });
    await expect(propia).toContainText('Ya lo tienes', { timeout: 20_000 });
    await expect(propia.locator('.btn-add')).toBeDisabled();
    const otraVez = await api.post('/api/v1/carrito/items', {
      headers: conToken(jugador.token),
      data: { productoId, cantidad: 1 },
    });
    expect(otraVez.status(), await otraVez.text()).toBe(409);
    expect((await otraVez.json()).type).toBe('urn:nexus:problema:producto-ya-adquirido');
    expect(fallos, 'ninguna petición respondió 5xx').toEqual([]);
  });

  test('8 · inventario: lo comprado está en la vitrina', async ({ page }) => {
    const fallos = vigilarFallos(page);
    await conSesion(page, jugador);
    await page.goto(`${BORDE}${RUTA.inventario}#objetos`);
    await expect(page.locator('.vitrina__nombre', { hasText: producto.nombre })).toBeVisible({
      timeout: 20_000,
    });
    expect(fallos, 'ninguna petición respondió 5xx').toEqual([]);
  });

  test('9 · Mi cuenta: la foto se ve, «Tus batallas» lleva al resultado y las pestañas siguen la dirección', async ({
    page,
  }) => {
    // La foto: el informe vio 404 y el texto alternativo.
    const subida = await api.put(`/api/v1/perfiles/${jugador.claims.uid}`, {
      headers: { Authorization: `Bearer ${jugador.token}` },
      multipart: {
        nombres: 'Recorrido',
        apellidos: 'De la Auditoría',
        preferencias: 'Combate',
        avatar: { name: 'recorrido.png', mimeType: 'image/png', buffer: PNG },
      },
    });
    expect(subida.status(), await subida.text()).toBe(200);

    const fallos = vigilarFallos(page);
    await conSesion(page, jugador);
    await page.goto(`${BORDE}${RUTA.cuenta}`);
    const foto = page.locator('[data-zona="identidad"] img.avatar-vista-previa');
    await expect(foto).toBeVisible({ timeout: 20_000 });
    await expect
      .poll(() => foto.evaluate((img) => img.complete && img.naturalWidth > 0), {
        timeout: 10_000,
        message: 'la foto carga de verdad, no se queda rota',
      })
      .toBe(true);

    // Pestañas por la dirección: cambiar el #hash cambia de pestaña.
    await page.evaluate(() => {
      window.location.hash = '#estadisticas';
    });
    const estadisticas = page.locator('[data-zona="panel-estadisticas"]');
    await expect(estadisticas).toBeVisible({ timeout: 10_000 });

    // «Tus batallas»: la columna «Acción» salía vacía en las terminadas.
    const tabla = estadisticas.locator('table[data-zona="mis-partidas"]');
    await expect(tabla).toBeVisible({ timeout: 20_000 });
    const fila = tabla.locator(`tbody tr[data-partida="${partida.id}"]`);
    await expect(fila).toBeVisible();
    const ver = fila.getByRole('link', { name: 'Ver resultado' });
    await expect(ver).toHaveAttribute('href', new RegExp(`sala=${sala.id}&partida=${partida.id}`));
    // Y las columnas casan con su encabezado: la de jugadores, cifra, a la derecha en los dos.
    await expect(tabla.locator('thead th', { hasText: 'Jugadores' })).toHaveClass(/tabla__numero/);
    await expect(fila.locator('td').nth(3)).toHaveClass(/tabla__numero/);

    await page.evaluate(() => {
      window.location.hash = '#seguridad';
    });
    await expect(page.locator('[data-zona="panel-seguridad"]')).toBeVisible({ timeout: 10_000 });
    await expect(estadisticas).toBeHidden();

    // Privacidad: el portal es del Grupo 4 y no existe todavía. La vista lo
    // dice con palabras, sin un fallo técnico ni un servicio caído.
    await page.evaluate(() => {
      window.location.hash = '#perfil';
    });
    await expect(page.locator('[data-zona="privacidad"]')).toContainText(
      'Descargarlos o cerrar tu cuenta',
    );
    expect(fallos, 'ninguna petición respondió 5xx').toEqual([]);
  });

  test('10 · misiones, subastas y torneos cargan (el informe: 504, 502 y sin probar)', async ({
    page,
  }) => {
    const fallos = vigilarFallos(page);
    await conSesion(page, jugador);

    await page.goto(`${BORDE}${RUTA.misiones}`);
    const tablon = page.locator('.mision-card, .misiones-categoria .estado-vista--vacio');
    await expect(tablon.first()).toBeVisible({ timeout: 30_000 });
    await expect(page.locator('.misiones-estado[data-estado="sin-abrir"]')).toHaveCount(0);

    await page.goto(`${BORDE}${RUTA.subastas}`);
    await expect(page.locator('.estado-vista--cargando')).toHaveCount(0, { timeout: 45_000 });
    await expect(page.locator('.estado-vista--vacio, .subastas__producto').first()).toBeVisible({
      timeout: 30_000,
    });
    await expect(page.locator('.estado-vista--error')).toHaveCount(0);

    await page.goto(`${BORDE}${RUTA.torneos}`);
    const listado = page.locator('[data-zona="listado"]');
    await expect(listado).not.toBeEmpty({ timeout: 30_000 });
    await expect(listado.locator('.estado-vista--cargando')).toHaveCount(0, { timeout: 30_000 });
    await expect(listado.locator('.estado-vista--error')).toHaveCount(0);
    expect(fallos, 'ninguna petición respondió 5xx').toEqual([]);
  });
});
