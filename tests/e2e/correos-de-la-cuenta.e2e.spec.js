// @ts-check
/**
 * Los correos de la cuenta llegan de verdad — §7.3.2 (advertencia, suspensión
 * y baneo con aviso al correo), §7.3.7 (cada aviso con la acción, el motivo,
 * las políticas violadas y el proceso de apelación) y §7.3.9 (aviso de
 * accesos desde un dispositivo no reconocido).
 *
 * Hasta aquí estos correos solo se probaban contra dobles HTTP (el servicio
 * de correo recibía la petición). Aquí se leen en el buzón de pruebas del
 * banco (Mailpit, `ayudantes/correo.js`): lo que la jugadora encuentra en su
 * correo, con el servicio de correo, su cola y su SMTP reales.
 *
 *   1. advertencia de la moderadora: el correo dice la acción, la causal, el
 *      motivo, la política y hasta cuándo se puede apelar;
 *   2. suspensión de 2 horas: además, cuándo termina;
 *   3. baneo del administrador (con confirmación): definitivo, el inventario
 *      congelado, el motivo y cómo apelar desde la pantalla de entrada;
 *   4. otra jugadora entra desde un dispositivo nuevo: le llega el aviso con
 *      la dirección IP y la hora.
 *
 * Las jugadoras se crean aquí con el sufijo de la corrida.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

import {
  CLAVE_DEL_BANCO,
  cuentaAdministrativaDeRol,
  iniciarSesion,
  sesionDe as sesionDelBanco,
} from './ayudantes/cuentas.js';
import { correosPara, correosVistos, leerCorreo } from './ayudantes/correo.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const SUFIJO = Date.now().toString(36);

/** Los asuntos que fija el servicio de correo (correo.yaml, `CorreoSancionRequest`). */
const ASUNTO = Object.freeze({
  ADVERTENCIA: 'Recibiste una advertencia en The Nexus Battles VI',
  SUSPENSION: 'Tu cuenta de The Nexus Battles VI está suspendida',
  BANEO: 'Tu cuenta de The Nexus Battles VI fue baneada de forma definitiva',
  ACCESO: 'Acceso desde un dispositivo no reconocido',
});

function sesionDe(api, apodo) {
  return sesionDelBanco(api, apodo, { clave: CLAVE_DEL_BANCO, base: BORDE });
}

/**
 * Espera en el buzón un correo nuevo (que no estaba en `vistos`) con ese
 * asunto, y lo devuelve completo: asunto, texto y HTML.
 */
async function esperarCorreo(email, asunto, vistos) {
  let id = null;
  await expect
    .poll(
      async () => {
        const encontrados = await correosPara(email, { base: BORDE }).catch(() => []);
        id = encontrados.find((m) => m.Subject === asunto && !vistos.has(m.ID))?.ID ?? null;
        return id ? 'en el buzón' : 'todavía no';
      },
      {
        timeout: 60_000,
        intervals: [1_000, 2_000, 3_000],
        message: `«${asunto}» no llegó a ${email}: mirar CORREO_URL de quien lo envía y el SMTP del banco`,
      },
    )
    .toBe('en el buzón');
  return leerCorreo(id, { base: BORDE });
}

/**
 * El texto del correo en una línea, para buscar frases sin depender de los
 * saltos. Las plantillas son HTML (MJML): si no viene parte de texto, se lee
 * el HTML sin etiquetas.
 */
function enUnaLinea(correo) {
  const texto = String(correo?.Text ?? '').trim();
  const fuente = texto
    ? texto
    : String(correo?.HTML ?? '')
        .replace(/<style[\s\S]*?<\/style>/gi, ' ')
        .replace(/<[^>]+>/g, ' ')
        .replace(/&nbsp;/g, ' ')
        .replace(/&laquo;/g, '«')
        .replace(/&raquo;/g, '»')
        .replace(/&amp;/g, '&')
        .replace(/&#(\d+);/g, (_, n) => String.fromCodePoint(Number(n)));
  return fuente.replace(/\s+/g, ' ');
}

test.describe('Los correos de la cuenta llegan al buzón (§7.3.2, §7.3.7, §7.3.9)', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let moderadora;
  let admin;
  let jugadora;

  const emitir = (quien, cuerpo) =>
    api.post('/api/v1/sanciones', {
      headers: { Authorization: `Bearer ${quien.token}`, 'Content-Type': 'application/json' },
      data: { usuarioId: jugadora.claims.uid, ...cuerpo },
    });

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    moderadora = await sesionDe(api, cuentaAdministrativaDeRol('MODERADOR').apodo);
    admin = await sesionDe(api, cuentaAdministrativaDeRol('ADMINISTRADOR').apodo);
    jugadora = await sesionDe(api, `correos_${SUFIJO}`);
    expect(jugadora.claims.rol).toBe('JUGADOR');
  });

  test.afterAll(async () => {
    await api?.dispose();
  });

  test('1 · advertencia: la acción, la causal, el motivo, la política y hasta cuándo apelar', async () => {
    const vistos = await correosVistos(jugadora.email, { base: BORDE });
    const r = await emitir(moderadora, {
      tipo: 'ADVERTENCIA',
      causal: 'LENGUAJE_OFENSIVO',
      motivo: `Insultos en el chat de la sala (E2E ${SUFIJO})`,
      politica: 'Términos y condiciones del servicio, sección 4',
    });
    expect(r.status(), await r.text()).toBe(201);

    const correo = await esperarCorreo(jugadora.email, ASUNTO.ADVERTENCIA, vistos);
    const texto = enUnaLinea(correo);
    expect(texto).toContain('recibió una advertencia');
    expect(texto).toContain('no restringe tu acceso');
    expect(texto).toContain('Uso de lenguaje ofensivo o abusivo en comentarios o chat');
    expect(texto).toContain(`Insultos en el chat de la sala (E2E ${SUFIJO})`);
    expect(texto).toContain('Términos y condiciones del servicio, sección 4');
    expect(texto).toMatch(/puedes apelar esta decisión hasta el/i);
    expect(texto).toContain('15 días hábiles');
  });

  test('2 · suspensión de 2 horas: además, cuándo termina', async () => {
    const vistos = await correosVistos(jugadora.email, { base: BORDE });
    const r = await emitir(moderadora, {
      tipo: 'SUSPENSION',
      causal: 'ACOSO',
      motivo: `Reincidencia tras la advertencia (E2E ${SUFIJO})`,
      duracionHoras: 2,
    });
    expect(r.status(), await r.text()).toBe(201);

    const correo = await esperarCorreo(jugadora.email, ASUNTO.SUSPENSION, vistos);
    const texto = enUnaLinea(correo);
    expect(texto).toContain('fue suspendida temporalmente');
    expect(texto).toContain('tu inventario y tu progreso se mantienen intactos');
    expect(texto).toContain('Acoso a otros jugadores');
    expect(texto).toContain(`Reincidencia tras la advertencia (E2E ${SUFIJO})`);
    expect(texto).toMatch(/Fin de la suspensión: \S/);
    expect(texto).toMatch(/puedes apelar esta decisión hasta el/i);
  });

  test('3 · baneo con confirmación: definitivo, inventario congelado y cómo apelar desde la entrada', async () => {
    const vistos = await correosVistos(jugadora.email, { base: BORDE });
    const r = await emitir(admin, {
      tipo: 'BANEO',
      causal: 'FRAUDE',
      motivo: `Fraude comprobado en subastas (E2E ${SUFIJO})`,
      confirmacion: true,
    });
    expect(r.status(), await r.text()).toBe(201);

    const correo = await esperarCorreo(jugadora.email, ASUNTO.BANEO, vistos);
    const texto = enUnaLinea(correo);
    expect(texto).toContain('quedó inhabilitada de forma permanente');
    expect(texto).toContain('tu inventario queda congelado');
    expect(texto).toContain('Intentos de fraude o manipulación del sistema');
    expect(texto).toContain(`Fraude comprobado en subastas (E2E ${SUFIJO})`);
    expect(texto).toContain('Apelar esta sanción');
    expect(texto).toContain('15 días hábiles');
  });

  test('4 · un acceso desde un dispositivo no reconocido avisa con la IP y la hora', async () => {
    const otra = await sesionDe(api, `acceso_${SUFIJO}`);
    // Su primera entrada ya es desde un equipo que no conocía: ese aviso
    // llega primero, y el que importa es el de la segunda.
    await esperarCorreo(otra.email, ASUNTO.ACCESO, new Set());
    const vistos = await correosVistos(otra.email, { base: BORDE });

    const desdeOtroEquipo = await apiRequest.newContext({
      baseURL: BORDE,
      extraHTTPHeaders: { 'User-Agent': `NexusE2E/1.0 (equipo nuevo ${SUFIJO})` },
    });
    try {
      const entrada = await iniciarSesion(desdeOtroEquipo, otra.email, CLAVE_DEL_BANCO);
      expect(entrada.estado, entrada.texto).toBe(200);
    } finally {
      await desdeOtroEquipo.dispose();
    }

    const correo = await esperarCorreo(otra.email, ASUNTO.ACCESO, vistos);
    const texto = enUnaLinea(correo);
    expect(texto).toContain('desde un dispositivo o una ubicación que no reconocemos');
    expect(texto).toMatch(/Dirección IP: [0-9a-f.:]+/i);
    expect(texto).toMatch(/Fecha y hora: \d/);
    expect(texto).toContain('cambia tu contraseña');
  });
});
