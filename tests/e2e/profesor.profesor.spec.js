/**
 * R17 · La prueba del profesor.
 *
 * Una persona que nunca ha visto el juego abre la dirección pública, crea su
 * cuenta y juega, sin que nadie prepare nada antes. Es el criterio de
 * aceptación con el que se cierra R17, en los veinte pasos que fija la
 * directiva, y se ejecuta contra el entorno desplegado (AWS DEV) o contra el
 * banco de `tests/e2e/compose.yml`: la misma prueba, sin atajos.
 *
 *   PROFESOR_URL=http://<host> npx playwright test --config=playwright.profesor.config.js
 *
 * Reglas que esta prueba se impone:
 *
 *   - **Solo la interfaz.** Todo lo que hace el profesor lo hace con clics y
 *     teclado: no hay SQL, ni semillas, ni `curl` administrativo, ni tokens
 *     editados. Lo único que se lee por fuera de la pantalla son los avisos del
 *     navegador (errores de página y respuestas 5xx), para el informe.
 *   - **Una cuenta nueva en cada corrida y en cada anchura**, desechable y
 *     reconocible como de QA (`qa_prof_…@nexus.test`). Su estado inicial sale
 *     solo del alta del jugador (R17.1).
 *   - **La contraseña no se deja en ningún sitio.** Se genera al vuelo, no se
 *     imprime, no va en ningún título de paso (por eso no se usa `fill` para
 *     ella: su valor aparecería en el informe) y la configuración apaga la
 *     traza y el vídeo, que la guardarían.
 *   - **Lo que no existe se dice.** Subastas puede no estar desplegado (falta
 *     de capacidad en AWS) y Misiones no tiene módulo: la prueba exige que la
 *     pantalla lo diga con honestidad, no que el módulo exista.
 *
 * En escritorio (1440) se recorren los veinte pasos. En móvil (375) y tableta
 * (768) solo la vertical de entrada —registro, alta, sesión y vuelta—, que es
 * lo que pide la directiva para esas anchuras.
 */

import { randomBytes } from 'node:crypto';

import { AxeBuilder } from '@axe-core/playwright';
import { test, expect } from '@playwright/test';

// ------------------------------------------------------------------ rutas

/** Direcciones limpias del borde (R17.3). La prueba no acepta las antiguas. */
const EN = {
  login: /\/login(?:[?#]|$)/,
  registro: /\/registro(?:[?#]|$)/,
  preparando: /\/preparando(?:[?#]|$)/,
  inicio: /\/inicio(?:[?#]|$)/,
  cuenta: /\/cuenta(?:[?#]|$)/,
  inventario: /\/inventario(?:[?#]|$)/,
  jugar: /\/jugar(?:[?#]|$)/,
  torneos: /\/torneos(?:[?#]|$)/,
  subastas: /\/subastas(?:[?#]|$)/,
  sala: /sala-batalla\.html\?sala=/,
};

const NORMAS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];
const GRAVES = new Set(['serious', 'critical']);

/** El proyecto de escritorio es el que recorre los veinte pasos. */
const ESCRITORIO = 'escritorio-1440';

// ------------------------------------------------------------- utilidades

/** Apodo y correo desechables, reconocibles como de QA y distintos cada vez. */
function cuentaDesechable(proyecto) {
  const sufijo = `${Date.now().toString(36).slice(-6)}${randomBytes(2).toString('hex')}`;
  const apodo = `qa_prof_${proyecto.slice(0, 3)}${sufijo}`.slice(0, 24);
  return { apodo, email: `${apodo}@nexus.test` };
}

/**
 * Una contraseña que cumple la política (RF-AUT-002: más de 8, mayúscula,
 * minúscula, dígito y símbolo), distinta en cada corrida. No se imprime nunca.
 */
function claveDesechable() {
  return `Qa-${randomBytes(12).toString('base64url')}-7z`;
}

/**
 * Escribe un secreto en un campo sin que su valor quede en el informe: `fill`
 * lo pondría en el título del paso. Se emite lo mismo que al teclear (input y
 * change), que es lo que escuchan las vistas.
 */
async function escribirSecreto(campo, valor) {
  await campo.focus();
  await campo.evaluate((el, v) => {
    el.value = v;
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }, valor);
}

/** Cifra de un texto como «1.500» o «+500»: solo los dígitos. */
function cifra(texto) {
  return Number(String(texto ?? '').replace(/\D/g, ''));
}

/** Ruta y consulta de la página, sin el host: lo que va al informe. */
function rutaDe(url) {
  try {
    const u = new URL(url);
    return `${u.pathname}${u.search}`;
  } catch {
    return String(url);
  }
}

/** axe sobre lo que se ve: sin hallazgos serios ni críticos. */
async function sinBarrerasGraves(page, donde) {
  const resultado = await new AxeBuilder({ page }).withTags(NORMAS).analyze();
  const graves = resultado.violations.filter((v) => GRAVES.has(v.impact));
  // Con el elemento: «1× aria-prohibited-attr» sin decir cuál no se puede
  // arreglar sin volver a correr la prueba.
  expect(
    graves.map(
      (v) =>
        `${v.id} [${v.impact}] ${v.nodes.length}× — ${v.help} — ` +
        v.nodes.map((n) => `${n.target.join(' ')} ${n.html.slice(0, 160)}`).join(' | '),
    ),
    `axe en ${donde}`,
  ).toEqual([]);
  return resultado.violations.length;
}

/** Todas las imágenes visibles cargaron (el logotipo, los retratos). */
async function imagenesCargadas(page) {
  return page.evaluate(() =>
    [...document.images]
      .filter((i) => i.getBoundingClientRect().width > 0)
      .every((i) => i.complete && i.naturalWidth > 0),
  );
}

/**
 * Va a un destino de la barra del juego como lo haría una persona. En móvil
 * la barra está plegada tras su botón: se abre primero.
 */
async function irA(page, seccion) {
  const destino = page.locator(`header[data-cabecera-app] a[data-seccion="${seccion}"]`);
  if (!(await destino.isVisible())) {
    const alternar = page.locator('[data-zona="alternar-nav"]');
    if (await alternar.isVisible()) {
      await alternar.click();
    }
  }
  await destino.click();
}

/** Abre el menú de la cuenta (el avatar) y pulsa una de sus opciones. */
async function menuDeCuenta(page, opcion) {
  await page.locator('header[data-cabecera-app] [data-zona="cuenta"]').click();
  const menu = page.locator('[data-zona="menu-cuenta"]');
  await expect(menu).toBeVisible();
  if (opcion === 'cerrar-sesion') {
    await menu.locator('[data-zona="cerrar-sesion"]').click();
  } else {
    await menu.getByRole('menuitem', { name: opcion }).click();
  }
}

/** Una de las cifras del resumen de «Mi cuenta», por su etiqueta. */
async function cifraDeMiCuenta(page, etiqueta) {
  const tarjeta = page
    .locator('[data-zona="resumen-saldo"] .metrica--cifra')
    .filter({ hasText: etiqueta });
  await expect(tarjeta).toBeVisible({ timeout: 30_000 });
  return cifra(await tarjeta.locator('.metrica__valor').textContent());
}

/** Saldo disponible que enseña «Mi cuenta». */
async function saldoEnMiCuenta(page) {
  return cifraDeMiCuenta(page, 'Créditos disponibles');
}

/** Lo que «Mi cuenta» dice que está apartado en apuestas (HU-JUE-014). */
async function apartadoEnMiCuenta(page) {
  return cifraDeMiCuenta(page, 'Apartado en apuestas');
}

/**
 * FASE 28 de la directiva: si el saldo inicial permite apostar, la primera
 * partida se juega con una cantidad segura. Diez créditos, o la décima parte
 * de lo acreditado si el alta diera menos: nunca lo que deje la cuenta a cero.
 */
function apuestaSegura(saldoInicial) {
  return Math.max(0, Math.min(10, Math.floor(saldoInicial / 10)));
}

/** Los movimientos de la pestaña «Historial» de «Mi cuenta». */
async function movimientosEnMiCuenta(page) {
  await page.locator('#pestana-historial').click();
  const filas = page.locator('[data-zona="movimientos"] tbody tr');
  await expect(filas.first()).toBeVisible({ timeout: 30_000 });
  return filas.allInnerTexts();
}

// ------------------------------------------------------------- bitácora

/**
 * Lleva la cuenta de cada paso y de lo que el navegador dijo mientras tanto.
 * Es la evidencia del informe: se adjunta a la prueba y se imprime una línea
 * `PROFESOR|…` por paso, que el flujo de CI pasa al resumen de la corrida.
 */
function abrirBitacora(page, context, proyecto) {
  const pasos = [];
  const incidencias = [];
  let pasoEnCurso = 0;

  context.on('response', (respuesta) => {
    const url = new URL(respuesta.url());
    if (url.pathname.startsWith('/api/') && respuesta.status() >= 500) {
      incidencias.push({
        paso: pasoEnCurso,
        tipo: 'http',
        detalle: `${respuesta.request().method()} ${url.pathname} → ${respuesta.status()}`,
      });
    }
  });
  context.on('weberror', (error) => {
    const mensaje = String(error.error()?.message ?? error.error());
    // La guarda de las vistas privadas corta su guion a propósito al mandar
    // al login («sin sesion: redirigiendo al login»): no es un fallo.
    if (!/sin sesi[oó]n/i.test(mensaje)) {
      incidencias.push({ paso: pasoEnCurso, tipo: 'pagina', detalle: mensaje.slice(0, 200) });
    }
  });
  context.on('console', (mensaje) => {
    // «Failed to load resource» es el navegador contando una respuesta 4xx/5xx
    // que ya se registra arriba con su ruta; aquí interesa lo que dice el código.
    if (mensaje.type() === 'error' && !/Failed to load resource/i.test(mensaje.text())) {
      incidencias.push({
        paso: pasoEnCurso,
        tipo: 'consola',
        detalle: mensaje.text().slice(0, 200),
      });
    }
  });

  async function paso(numero, titulo, cuerpo) {
    pasoEnCurso = numero;
    const registro = { paso: numero, titulo, estado: 'OK', detalle: '', ms: 0, ruta: '' };
    pasos.push(registro);
    const inicio = Date.now();
    try {
      await test.step(`${numero}. ${titulo}`, async () => {
        const detalle = await cuerpo();
        if (detalle) {
          registro.detalle = String(detalle);
        }
      });
    } catch (error) {
      registro.estado = 'FALLO';
      registro.detalle = String(error?.message ?? error)
        .split('\n')[0]
        .slice(0, 300);
      throw error;
    } finally {
      registro.ms = Date.now() - inicio;
      registro.ruta = rutaDe(page.url());
    }
  }

  async function cerrar(testInfo) {
    const informe = { proyecto, pasos, incidencias };
    await testInfo.attach('bitacora-del-profesor.json', {
      body: JSON.stringify(informe, null, 2),
      contentType: 'application/json',
    });
    for (const p of pasos) {
      const limpio = (texto) => String(texto).replaceAll('|', '/').replaceAll('\n', ' ');
      console.log(
        `PROFESOR|${proyecto}|${p.paso}|${limpio(p.titulo)}|${p.estado}|${limpio(p.detalle)}|${p.ms}`,
      );
    }
    for (const i of incidencias) {
      console.log(`PROFESOR-INCIDENCIA|${proyecto}|${i.paso}|${i.tipo}|${i.detalle}`);
    }
  }

  return { paso, cerrar, incidencias };
}

async function capturar(page, testInfo, nombre) {
  const ruta = testInfo.outputPath(`${nombre}.png`);
  await page.screenshot({ path: ruta });
  await testInfo.attach(nombre, { path: ruta, contentType: 'image/png' });
}

// ------------------------------------------------ pasos compartidos (1 a 7)

/**
 * Los pasos de la entrada, comunes a las tres anchuras: de la dirección
 * pública a «Mi cuenta» con el saldo inicial. Devuelve lo que se aprendió.
 */
async function entrarPorPrimeraVez(page, testInfo, { paso }, cuenta, clave) {
  const aprendido = { creditosIniciales: 0, heroeInicial: '' };

  await paso(1, 'Entrar a la URL pública', async () => {
    await page.goto('/');
    await expect(page).toHaveURL(EN.login);
    expect(new URL(page.url()).pathname, 'la dirección es la limpia, no la del fichero').toBe(
      '/login',
    );
    await expect(page.locator('#formLogin')).toBeVisible();
    expect(await imagenesCargadas(page), 'el logotipo y las imágenes cargan').toBe(true);
    const avisos = await sinBarrerasGraves(page, 'login');
    await capturar(page, testInfo, '01-login');
    return `/ → /login; axe sin graves (${avisos} avisos menores)`;
  });

  await paso(2, 'Pulsar «Crear cuenta»', async () => {
    const enCabecera = page.getByRole('link', { name: 'Crear cuenta', exact: true });
    const enElPie = page.getByRole('link', { name: 'Crear una cuenta', exact: true });
    await ((await enCabecera.isVisible()) ? enCabecera : enElPie).click();
    await expect(page).toHaveURL(EN.registro);
    await expect(page.locator('#formRegistro, form').first()).toBeVisible();
    await sinBarrerasGraves(page, 'registro');
    await capturar(page, testInfo, '02-registro');
    return new URL(page.url()).pathname;
  });

  await paso(3, 'Registrar un usuario completamente nuevo', async () => {
    await page.fill('#nombres', 'Profesora');
    await page.fill('#apellidos', 'QA Automatizada');
    await page.fill('#apodo', cuenta.apodo);
    await page.fill('#email', cuenta.email);

    // Primero una contraseña débil: la pantalla dice qué le falta, con la
    // política real del servidor, antes de enviar nada.
    const campoClave = page.locator('#password');
    await escribirSecreto(campoClave, 'corta');
    await campoClave.blur();
    const motivo = page.locator('.campo--invalido .campo__error').first();
    await expect(motivo).toContainText('A tu contraseña le falta');
    const pista = (await motivo.textContent()).trim();

    await escribirSecreto(campoClave, clave);
    await escribirSecreto(page.locator('#confirmarPassword'), clave);
    await campoClave.blur();
    await expect(page.locator('.campo--invalido')).toHaveCount(0);
    await page.click('#botonEnviar');
    return `apodo ${cuenta.apodo}; la débil se rechazó en pantalla («${pista}»)`;
  });

  await paso(4, 'Iniciar sesión (entra sola al crear la cuenta)', async () => {
    await page.waitForURL(EN.preparando, { timeout: 30_000 });
    const conSesion = await page.evaluate(() => Boolean(sessionStorage.getItem('nexus.token')));
    expect(conSesion, 'la sesión quedó abierta sin volver a escribir la contraseña').toBe(true);
    return 'registro → /preparando con la sesión abierta';
  });

  await paso(5, 'Esperar el alta real («Preparando tu cuenta»)', async () => {
    await expect(page.locator('[data-zona="pasos"] li')).toHaveCount(4);
    await expect(page.locator('[data-zona="titulo"]')).toHaveText('¡Tu cuenta está lista!', {
      timeout: 90_000,
    });
    await expect(page.locator('[data-zona="pasos"] li[data-estado="HECHO"]')).toHaveCount(4);
    aprendido.creditosIniciales = cifra(
      await page.locator('[data-zona="creditos-iniciales"]').textContent(),
    );
    // El resumen dice que hay héroe y que va equipado; su nombre se ve en el
    // inventario (paso 9).
    aprendido.heroeInicial = (
      (await page.locator('[data-zona="heroe-inicial"]').textContent()) ?? ''
    ).trim();
    expect(aprendido.creditosIniciales).toBeGreaterThan(0);
    expect(aprendido.heroeInicial, 'el resumen enseña el héroe inicial').not.toBe('');
    await sinBarrerasGraves(page, 'preparando tu cuenta');
    await capturar(page, testInfo, '05-cuenta-lista');

    await page.click('[data-zona="empezar"]');
    await page.waitForURL(EN.inicio, { timeout: 20_000 });
    await expect(page.locator('[data-zona="saludo"]')).toContainText(cuenta.apodo);
    await sinBarrerasGraves(page, 'inicio (primera sesión)');
    await capturar(page, testInfo, '05-inicio');
    return `4/4 pasos hechos; ${aprendido.creditosIniciales} créditos; héroe inicial: «${aprendido.heroeInicial}»; → /inicio`;
  });

  await paso(6, 'Ver «Mi cuenta»', async () => {
    await irA(page, 'cuenta');
    await expect(page).toHaveURL(EN.cuenta);
    await expect(page.locator('[data-zona="identidad"]')).toContainText(cuenta.apodo, {
      timeout: 30_000,
    });
    await expect(page.getByText('No pudimos cargar tu perfil')).toHaveCount(0);
    return 'perfil real con el apodo y el rol';
  });

  await paso(7, 'Comprobar el saldo inicial > 0', async () => {
    const saldo = await saldoEnMiCuenta(page);
    expect(saldo).toBeGreaterThan(0);
    expect(saldo, 'lo que dice «Mi cuenta» es lo que se acreditó en el alta').toBe(
      aprendido.creditosIniciales,
    );
    await capturar(page, testInfo, '07-mi-cuenta');
    aprendido.saldoInicial = saldo;
    return `${saldo} créditos disponibles`;
  });

  return aprendido;
}

// ------------------------------------------------------------------ pruebas

test.describe('R17 · la prueba del profesor', () => {
  test('los veinte pasos, de la URL pública a la persistencia', async ({
    page,
    context,
  }, testInfo) => {
    test.skip(testInfo.project.name !== ESCRITORIO, 'los veinte pasos se recorren en escritorio');
    test.setTimeout(12 * 60_000);

    const cuenta = cuentaDesechable(testInfo.project.name);
    const clave = claveDesechable();
    const bitacora = abrirBitacora(page, context, testInfo.project.name);
    const { paso } = bitacora;

    try {
      const aprendido = await entrarPorPrimeraVez(page, testInfo, bitacora, cuenta, clave);
      let movimientosAntes = [];
      let saldoTrasJugar = 0;
      let nombreDelHeroe = aprendido.heroeInicial;

      await paso(8, 'Comprobar el movimiento inicial', async () => {
        movimientosAntes = await movimientosEnMiCuenta(page);
        const bono = movimientosAntes.find((fila) => /Créditos de bienvenida/.test(fila));
        expect(bono, 'el abono del alta está en el historial').toBeTruthy();
        // La fila es «Concepto · Importe · Estado · Cuándo»; el importe va con signo.
        expect(cifra(bono.match(/\+\s*([\d.,]+)/)?.[1])).toBe(aprendido.creditosIniciales);
        return `«Créditos de bienvenida» +${aprendido.creditosIniciales}`;
      });

      await paso(9, 'Comprobar el héroe inicial (ficha con estadísticas)', async () => {
        await irA(page, 'inventario');
        await expect(page).toHaveURL(EN.inventario);
        // UXC-1 — «Mi inventario» separa héroes y objetos: el héroe inicial es
        // una carta de la pestaña «Héroes», con su prototipo y su estado.
        const heroe = page.locator('.inventario-heroes [data-heroe]');
        await expect(heroe).toHaveCount(1, { timeout: 30_000 });
        nombreDelHeroe = ((await heroe.locator('.hero-card__nombre').textContent()) ?? '').trim();
        await heroe.getByRole('button', { name: /^Ver la ficha de / }).click();
        const ficha = page.locator('[role="dialog"].ficha');
        await expect(ficha).toBeVisible();
        await expect(ficha.locator('.ficha__nombre')).not.toBeEmpty();
        // Lo que diga el catálogo; las estadísticas con el equipo puesto se
        // comprueban en el paso 12, en el panel del héroe.
        const atributos = await ficha.locator('.ficha__atributo').allInnerTexts();
        await capturar(page, testInfo, '09-ficha-del-heroe');
        await page.locator('.ficha__cerrar').click();
        await expect(ficha).toBeHidden();
        return (
          `1 héroe: «${nombreDelHeroe}»; ficha del catálogo` +
          (atributos.length ? `: ${atributos.join(' · ').replaceAll('\n', ' ')}` : '')
        );
      });

      await paso(10, 'Comprobar el inventario', async () => {
        // UXC-1 — un héroe en «Héroes» y el kit en «Objetos», cada uno con su
        // estado (equipado, libre o bloqueado).
        const heroes = await page.locator('.inventario-heroes [data-heroe]').count();
        await page.locator('#pestana-objetos').click();
        const elementos = page.locator('.inventario__contenido li.vitrina__producto');
        await expect(elementos.first()).toBeVisible({ timeout: 30_000 });
        const total = await elementos.count();
        const tipos = await elementos.evaluateAll((lis) => lis.map((li) => li.dataset.tipo));
        expect(heroes).toBe(1);
        expect(total).toBeGreaterThan(0);
        expect(tipos.filter((t) => t === 'HEROE')).toHaveLength(0);
        await capturar(page, testInfo, '10-inventario');
        return `1 héroe y ${total} objetos (${tipos.join(', ')})`;
      });

      await paso(11, 'Entrar a «Jugar online»', async () => {
        await irA(page, 'jugar');
        await expect(page).toHaveURL(EN.jugar);
        await expect(page.locator('[data-zona="subtitulo"]')).not.toHaveText('Buscando batallas', {
          timeout: 30_000,
        });
        return (await page.locator('[data-zona="subtitulo"]').textContent())?.trim();
      });

      await paso(12, 'Seleccionar el héroe', async () => {
        // El héroe que juega es el equipado (RF-JUE-001, HU-SAL-003); se elige
        // y se equipa en «Mi inventario → Equipo». El alta lo deja listo.
        await irA(page, 'inventario');
        const heroe = page.locator('.inventario-heroes [data-heroe]');
        await heroe.getByRole('button', { name: /^Gestionar el equipamiento de / }).click();
        await expect(page.locator('.inventario-equipo__estadisticas')).toBeVisible({
          timeout: 30_000,
        });
        const estadisticas = await page
          .locator('.inventario-equipo__estadisticas .stat-block__cifra')
          .allInnerTexts();
        await capturar(page, testInfo, '12-heroe-seleccionado');
        await irA(page, 'jugar');
        await expect(page).toHaveURL(EN.jugar);
        return `«${nombreDelHeroe}» con su equipo: ${estadisticas.join(' · ').replaceAll('\n', ' ')}`;
      });

      const apuesta = apuestaSegura(aprendido.saldoInicial);
      await paso(13, 'Crear una sala (contra la IA, con una apuesta segura)', async () => {
        await page.getByRole('link', { name: 'Crear sala' }).first().click();
        await expect(page).toHaveURL(/crear-sala\.html/);
        await page.locator('#modalidad-ia').check();
        await expect(page.locator('[data-zona="nota-contra-ia"]')).toBeVisible();
        // «Recompensa en créditos»: lo que se aparta de la cuenta al crear la
        // sala y se liquida al terminar (HU-JUE-014).
        await page.locator('#recompensaCreditos').fill(String(apuesta));
        await page.click('#formulario-crear-sala [type="submit"]');
        // Creada la sala, el aviso lo dice y ofrece entrar a ella (R18.6).
        const aviso = page.locator('#formulario-crear-sala [data-zona="aviso"]');
        await expect(aviso).toContainText('Sala creada', { timeout: 30_000 });
        await aviso.locator('[data-accion="entrar-a-la-sala"]').click();
        await page.waitForURL(EN.sala, { timeout: 30_000 });
        const idSala = new URL(page.url()).searchParams.get('sala') ?? '';
        await expect(page.locator('[data-accion="iniciar-partida"]')).toBeVisible({
          timeout: 30_000,
        });
        await capturar(page, testInfo, '13-sala-de-espera');
        return (
          `sala ${idSala.slice(0, 8)}… contra la IA con ${apuesta} créditos en juego ` +
          `(de ${aprendido.saldoInicial}); «Sala creada» → «Entrar a la sala» → sala de espera`
        );
      });

      let desenlace = '';
      await paso(14, 'Jugar una partida', async () => {
        await page.click('[data-accion="iniciar-partida"]');
        await expect(page.locator('[data-barra-vida]')).toHaveCount(2, { timeout: 30_000 });
        const mia = page.locator('[data-barra-vida]:not([data-ia])');
        const rival = page.locator('[data-barra-vida][data-ia="true"]');
        await expect(mia.locator('.barra-vida__nombre')).toHaveText(nombreDelHeroe);
        const vida = async (barra) => Number(await barra.getAttribute('aria-valuenow'));
        const alEmpezar = { mia: await vida(mia), rival: await vida(rival) };
        // La partida queda en la dirección: un F5 vuelve al combate (R17.4).
        await expect(page).toHaveURL(/[?&]partida=/);

        const resultado = page.locator('[data-zona="resultado"]');
        const ataque = page.locator('[data-zona="acciones"] [data-atacar]').first();
        // HU-JUE-017 CA-04: al arrancar, la presentación de los héroes cubre el
        // campo hasta que se entra al combate (o hasta el primer aviso del
        // canal, si abre el rival). Una persona pulsa «Entrar al combate».
        const entrar = page.locator('[data-accion="entrar-al-combate"]');
        const limite = Date.now() + 5 * 60_000;
        let golpes = 0;
        let recargada = false;
        let presentacion = false;
        while (Date.now() < limite && !(await resultado.isVisible())) {
          if (await entrar.isVisible()) {
            presentacion = true;
            // El primer aviso del canal también la cierra: si se adelanta al
            // clic, no pasa nada.
            await entrar.click({ timeout: 5_000 }).catch(() => {});
            await expect(entrar).toBeHidden();
          } else if ((await ataque.isVisible()) && (await ataque.isEnabled())) {
            // Entre verlo habilitado y pulsarlo, la IA puede jugar o la partida
            // terminar: el clic se intenta un rato corto y el bucle vuelve a
            // mirar (mismo patrón que torneos.e2e.spec.js).
            const golpeo = await ataque
              .click({ timeout: 5_000 })
              .then(() => true)
              .catch(() => false);
            if (!golpeo) {
              continue;
            }
            golpes += 1;
            if (!recargada) {
              // Un F5 en pleno combate no devuelve a la sala de espera: la
              // vista vuelve a pintar la partida desde la dirección, sin
              // «Iniciar combate» a la vista.
              await page.waitForTimeout(1_500);
              await page.reload();
              recargada = true;
              await expect(page).toHaveURL(/[?&]partida=/);
              await expect(page.locator('[data-barra-vida]')).toHaveCount(2, { timeout: 30_000 });
              await expect(page.locator('[data-accion="iniciar-partida"]')).toBeHidden();
            }
          } else {
            // No es mi turno: la IA juega sola. Se espera sin martillear.
            await page.waitForTimeout(500);
          }
        }
        await expect(resultado).toBeVisible({ timeout: 60_000 });
        desenlace = (
          await resultado
            .locator(
              '.panel-resultado__palabra, .panel-resultado__detalle, .panel-resultado__creditos',
            )
            .allInnerTexts()
        )
          .map((parte) => parte.replace(/\s+/g, ' ').trim())
          .join(' · ');
        const alTerminar = { mia: await vida(mia), rival: await vida(rival) };
        // Alguien cayó: al menos una de las dos barras bajó desde el principio.
        expect(
          alTerminar.mia < alEmpezar.mia || alTerminar.rival < alEmpezar.rival,
          'las barras de vida se movieron',
        ).toBe(true);
        return (
          `${presentacion ? 'presentación de los héroes → «Entrar al combate»; ' : ''}` +
          `${golpes} golpes; vida propia ${alEmpezar.mia}→${alTerminar.mia}, ` +
          `rival ${alEmpezar.rival}→${alTerminar.rival}; un F5 a mitad volvió al combate`
        );
      });

      await paso(15, 'Ver el resultado', async () => {
        const resultado = page.locator('[data-zona="resultado"]');
        await expect(resultado).toContainText(/has ganado|has perdido|empate/i);
        // El desenlace ocupa toda la pantalla, barra incluida: tiene que llevar
        // sus propias salidas (R17.4) o el profesor se queda sin camino.
        await expect(resultado.locator('[data-accion="volver-a-jugar"]')).toBeVisible();
        await expect(resultado.locator('[data-accion="ver-mi-cuenta"]')).toBeVisible();
        await capturar(page, testInfo, '15-resultado');
        return `${desenlace.slice(0, 160)} → salidas «Volver a Jugar online» y «Ver mi cuenta»`;
      });

      await paso(16, 'Revisar la cuenta y el historial', async () => {
        // Desde el propio panel del desenlace, como lo haría una persona.
        await page.locator('[data-zona="resultado"] [data-accion="ver-mi-cuenta"]').click();
        await expect(page).toHaveURL(EN.cuenta);
        // La apuesta se liquida justo detrás del final (HU-JUE-014). Se espera
        // a que no quede nada apartado, recargando como lo haría una persona:
        // una reserva que no se suelta es dinero del jugador retenido.
        await expect
          .poll(
            async () => {
              await page.reload();
              return apartadoEnMiCuenta(page);
            },
            { timeout: 90_000, intervals: [2_000, 5_000], message: 'la apuesta sigue apartada' },
          )
          .toBe(0);
        saldoTrasJugar = await saldoEnMiCuenta(page);
        // Contra la IA la apuesta nunca va a otra persona: se devuelve, o se
        // cobra si la política vigente (D-02, `salas.apuestas.si-gana-la-maquina`)
        // es CONSUMIR y ganó la máquina. La recompensa por jugar (HU-JUE-012)
        // puede sumar.
        expect(saldoTrasJugar).toBeGreaterThanOrEqual(aprendido.saldoInicial - apuesta);
        const movimientos = await movimientosEnMiCuenta(page);
        expect(movimientos.length).toBeGreaterThanOrEqual(movimientosAntes.length);
        await capturar(page, testInfo, '16-historial');
        return (
          `saldo ${saldoTrasJugar} (antes ${aprendido.saldoInicial}; apuesta de ${apuesta} ` +
          `liquidada, 0 apartado); ${movimientos.length} movimientos`
        );
      });

      await paso(17, 'Cerrar sesión', async () => {
        await menuDeCuenta(page, 'cerrar-sesion');
        await page.waitForURL(/\/login\?.*motivo=cerrada/, { timeout: 20_000 });
        await expect(page.locator('#avisoMotivo')).toContainText('Cerraste sesión');
        const token = await page.evaluate(() => sessionStorage.getItem('nexus.token'));
        expect(token, 'la sesión se borró').toBeNull();
        // «Atrás» no enseña la pantalla privada: vuelve a pedir la entrada.
        await page.goBack();
        await expect(page).toHaveURL(EN.login, { timeout: 20_000 });
        await expect(page.locator('header[data-cabecera-app] [data-zona="cuenta"]')).toHaveCount(0);
        return 'sesión cerrada; «Atrás» vuelve al login';
      });

      await paso(18, 'Volver a iniciar sesión', async () => {
        await page.goto('/login');
        await page.fill('#email', cuenta.email);
        await escribirSecreto(page.locator('#password'), clave);
        await page.click('#botonEnviar');
        // Cuenta lista: directo al inicio, sin pasar otra vez por la preparación.
        await page.waitForURL(EN.inicio, { timeout: 30_000 });
        await expect(page.locator('[data-zona="saludo"]')).toContainText(cuenta.apodo);
        return 'correo y contraseña → /inicio';
      });

      await paso(19, 'Comprobar la persistencia', async () => {
        await irA(page, 'cuenta');
        const saldo = await saldoEnMiCuenta(page);
        // Lo mismo que antes de salir; como mucho, más, si la recompensa por
        // jugar (HU-JUE-012, con reintento) se acreditó mientras tanto.
        expect(saldo, 'el saldo persiste').toBeGreaterThanOrEqual(saldoTrasJugar);
        const movimientos = await movimientosEnMiCuenta(page);
        expect(movimientos.some((fila) => /Créditos de bienvenida/.test(fila))).toBe(true);
        await irA(page, 'inventario');
        // UXC-1 — el héroe vive en la pestaña «Héroes», como en el paso 9.
        const heroe = page.locator('.inventario-heroes [data-heroe]');
        await expect(heroe).toHaveCount(1, { timeout: 30_000 });
        await expect(heroe.locator('.hero-card__nombre')).toHaveText(nombreDelHeroe);
        // Y una pestaña nueva usa la misma sesión, sin pedir la contraseña.
        const otra = await context.newPage();
        await otra.goto('/cuenta');
        await expect(otra).toHaveURL(EN.cuenta, { timeout: 20_000 });
        await expect(otra.locator('[data-zona="identidad"]')).toContainText(cuenta.apodo, {
          timeout: 30_000,
        });
        await otra.close();
        return `saldo ${saldo}, ${movimientos.length} movimientos, el mismo héroe; otra pestaña entra sola`;
      });

      await paso(20, 'Recorrer tienda, torneos, subastas y misiones', async () => {
        const estado = {};

        // Tienda (HU-CAR-001): el catálogo carga y se puede añadir al carrito.
        await menuDeCuenta(page, 'Tienda');
        // Un catálogo vacío es VACÍO VÁLIDO si la pantalla lo dice; un fallo no.
        const productos = page.locator('#productos-grid .product-card');
        const catalogoVacio = page.locator('#productos-grid .estado-vista--vacio');
        await expect(productos.first().or(catalogoVacio)).toBeVisible({ timeout: 30_000 });
        if (await productos.count()) {
          await page.locator('#productos-grid .btn-add:not([disabled])').first().click();
          await expect(page.locator('#cart-items .cart-item').first()).toBeVisible({
            timeout: 30_000,
          });
          estado.tienda = `FUNCIONAL (${await productos.count()} productos; carrito con 1)`;
        } else {
          estado.tienda = 'VACÍO VÁLIDO — el catálogo lo dice';
        }

        // Torneos: el listado carga, con torneos o con su estado vacío.
        await irA(page, 'torneo');
        await expect(page).toHaveURL(EN.torneos);
        const listado = page.locator('[data-zona="listado"]');
        await expect(listado).not.toBeEmpty({ timeout: 30_000 });
        await expect(listado.locator('.estado-vista--cargando')).toHaveCount(0, {
          timeout: 30_000,
        });
        await expect(listado.locator('.estado-vista--error'), 'el listado carga').toHaveCount(0);
        estado.torneos = (await listado.locator('.estado-vista--vacio').count())
          ? 'VACÍO VÁLIDO'
          : 'FUNCIONAL';

        // Subastas: en AWS puede no estar desplegado por capacidad. Vale si lo
        // dice (estado de error con «Reintentar»); no vale una pantalla en
        // blanco, un spinner eterno ni un 502 crudo.
        await irA(page, 'subasta');
        await expect(page).toHaveURL(EN.subastas);
        await expect(page.locator('.estado-vista--cargando')).toHaveCount(0, { timeout: 45_000 });
        const error = page.locator('.estado-vista--error');
        const lotes = page.locator('.estado-vista--vacio, .subastas__producto');
        await expect(error.or(lotes).first()).toBeVisible({ timeout: 30_000 });
        if (await error.isVisible()) {
          await expect(error.getByRole('button', { name: /reintentar/i })).toBeVisible();
          estado.subastas = `BLOQUEADO — la pantalla lo dice: «${(
            (await error.locator('.estado-vista__titulo').textContent()) ?? ''
          ).trim()}»`;
        } else {
          estado.subastas = 'FUNCIONAL';
        }

        // Misiones: sin módulo. La barra lo anuncia en vez de llevar a nada.
        const misiones = page.locator('header[data-cabecera-app] a[data-seccion="misiones"]');
        await expect(misiones).toHaveAttribute('aria-disabled', 'true');
        await expect(misiones).not.toHaveAttribute('href', /.+/);
        estado.misiones = `NO IMPLEMENTADO — «${await misiones.getAttribute('title')}»`;

        await capturar(page, testInfo, '20-subastas');
        return Object.entries(estado)
          .map(([modulo, veredicto]) => `${modulo}: ${veredicto}`)
          .join(' · ');
      });

      // Ningún error de página en todo el recorrido, y ningún 5xx fuera de
      // subastas (que puede no estar desplegado y lo dice).
      const graves = bitacora.incidencias.filter(
        (i) => i.tipo === 'pagina' || (i.tipo === 'http' && !/\/api\/v1\/subastas/.test(i.detalle)),
      );
      expect(graves, 'errores de página o 5xx durante el recorrido').toEqual([]);
    } finally {
      await bitacora.cerrar(testInfo);
    }
  });

  test('la vertical de entrada en esta anchura', async ({ page, context }, testInfo) => {
    test.skip(testInfo.project.name === ESCRITORIO, 'en escritorio se recorren los veinte pasos');
    test.setTimeout(5 * 60_000);

    const cuenta = cuentaDesechable(testInfo.project.name);
    const clave = claveDesechable();
    const bitacora = abrirBitacora(page, context, testInfo.project.name);
    const { paso } = bitacora;

    try {
      const aprendido = await entrarPorPrimeraVez(page, testInfo, bitacora, cuenta, clave);

      await paso(17, 'Cerrar sesión', async () => {
        await menuDeCuenta(page, 'cerrar-sesion');
        await page.waitForURL(/\/login\?.*motivo=cerrada/, { timeout: 20_000 });
        await expect(page.locator('#avisoMotivo')).toContainText('Cerraste sesión');
        return 'sesión cerrada';
      });

      await paso(18, 'Volver a iniciar sesión', async () => {
        await page.fill('#email', cuenta.email);
        await escribirSecreto(page.locator('#password'), clave);
        await page.click('#botonEnviar');
        await page.waitForURL(EN.inicio, { timeout: 30_000 });
        await sinBarrerasGraves(page, 'inicio (vuelta)');
        return '→ /inicio';
      });

      await paso(19, 'Comprobar la persistencia', async () => {
        await irA(page, 'cuenta');
        expect(await saldoEnMiCuenta(page)).toBe(aprendido.saldoInicial);
        await capturar(page, testInfo, '19-mi-cuenta');
        return `saldo ${aprendido.saldoInicial}`;
      });

      const errores = bitacora.incidencias.filter((i) => i.tipo === 'pagina' || i.tipo === 'http');
      expect(errores, 'errores de página o 5xx durante la entrada').toEqual([]);
    } finally {
      await bitacora.cerrar(testInfo);
    }
  });
});
