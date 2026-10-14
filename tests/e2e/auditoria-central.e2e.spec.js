// @ts-check
/**
 * La bitácora central — §7.3.5 del documento oficial (SA-5) — de punta a
 * punta por el borde, con los servicios reales del banco.
 *
 * Cada acción administrativa llega a ms-cumplimiento con quién la hizo, desde
 * qué dirección, sobre qué, cómo estaba, cómo quedó y por qué:
 *
 *   1. una advertencia que emite la moderadora desde la consola de sanciones
 *      (moderacion-sanciones, por su cola de auditoría), una sola vez;
 *   2. el alta, la edición y la baja de un término de la lista negra, cada
 *      una con su motivo;
 *   3. un cambio de parámetro (admin-parametros, desde su versión), con su
 *      valor anterior y el nuevo;
 *   4. la decisión de moderación de un comentario (comentarios, desde su
 *      asiento), con los dos estados;
 *   5. una suspensión desde la gestión de usuarios: la registra ms-identidad
 *      con el motivo del panel y moderacion-sanciones no la repite;
 *   6. solo el Super Administrador la lee y la exporta (PDF y CSV, y cada
 *      exportación queda registrada); los demás roles no, y nadie la escribe
 *      con su token de persona.
 *
 * Las entregas son asíncronas (cada servicio reintenta desde su cola cada 2 s
 * en el banco): se espera con `expect.poll`, nunca con pausas fijas.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

import {
  CLAVE_DEL_BANCO,
  cuentaAdministrativaDeRol,
  sesionDe as sesionDelBanco,
} from './ayudantes/cuentas.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const SUFIJO = Date.now().toString(36);
const ACEPTA = 'application/problem+json, application/json';
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const IP = /^[0-9a-fA-F:.]{3,45}$/;
const PARAMETRO = 'chat.historial.tamano';
const ESPERA = { timeout: 30_000, intervals: [500, 1000, 2000] };

function sesionDe(api, apodo) {
  return sesionDelBanco(api, apodo, { clave: CLAVE_DEL_BANCO, base: BORDE });
}

function como(sesion, extra = {}) {
  return {
    headers: {
      Accept: ACEPTA,
      'Content-Type': 'application/json',
      Authorization: `Bearer ${sesion.token}`,
      ...extra,
    },
  };
}

/** Mañana a esta hora, como lo escribe el campo `datetime-local` del panel. */
function manana() {
  const fecha = new Date(Date.now() + 24 * 3600_000);
  const dos = (n) => String(n).padStart(2, '0');
  return `${fecha.getFullYear()}-${dos(fecha.getMonth() + 1)}-${dos(fecha.getDate())}T${dos(
    fecha.getHours(),
  )}:${dos(fecha.getMinutes())}`;
}

test.describe('§7.3.5 · la bitácora central recibe toda acción administrativa', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let moderadora;
  let admin;
  let superAdmin;
  let jugador;
  let autora;

  /**
   * Las entradas de la bitácora sobre `afectado`, como Super Administrador, la
   * más reciente primero.
   */
  async function bitacora(filtros) {
    const consulta = new URLSearchParams({ size: '100', sort: 'fechaHora,desc', ...filtros });
    const r = await api.get(`/api/v1/admin/auditoria?${consulta}`, como(superAdmin));
    expect(r.status(), await r.text()).toBe(200);
    return (await r.json()).content ?? [];
  }

  /** Espera hasta que haya `cuantas` entradas que cumplan `condicion` y las devuelve. */
  async function esperarEntradas(filtros, condicion, cuantas = 1) {
    let encontradas = [];
    await expect
      .poll(async () => {
        encontradas = (await bitacora(filtros)).filter(condicion);
        return encontradas.length;
      }, ESPERA)
      .toBeGreaterThanOrEqual(cuantas);
    return encontradas;
  }

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    moderadora = await sesionDe(api, cuentaAdministrativaDeRol('MODERADOR').apodo);
    admin = await sesionDe(api, cuentaAdministrativaDeRol('ADMINISTRADOR').apodo);
    superAdmin = await sesionDe(api, cuentaAdministrativaDeRol('SUPER_ADMINISTRADOR').apodo);
    jugador = await sesionDe(api, `auditado_${SUFIJO}`);
    autora = await sesionDe(api, `autora_${SUFIJO}`);
    expect(jugador.claims.uid).toMatch(UUID);
  });

  test.afterAll(async () => {
    await api?.dispose();
  });

  test('1 · una advertencia de la consola llega con quién, sobre quién, qué y desde dónde, una sola vez', async () => {
    const motivo = `Insultos en el chat general ${SUFIJO}`;
    const r = await api.post('/api/v1/sanciones', {
      ...como(moderadora),
      data: {
        usuarioId: jugador.claims.uid,
        tipo: 'ADVERTENCIA',
        causal: 'LENGUAJE_OFENSIVO',
        motivo,
      },
    });
    expect(r.status(), await r.text()).toBe(201);

    const [entrada] = await esperarEntradas(
      { afectado: jugador.claims.uid },
      (e) => e.motivo === motivo,
    );
    expect(entrada.tipoAccion).toBe('SANCION');
    expect(entrada.administrador, 'el uid del token de la moderadora').toBe(moderadora.claims.uid);
    expect(entrada.afectado).toBe(jugador.claims.uid);
    expect(entrada.valorNuevo).toMatch(/^Advertencia · Causal: /);
    expect(entrada.ipOrigen, 'la que fija el borde').toMatch(IP);
    expect(Date.parse(entrada.ocurridoEn)).toBeLessThanOrEqual(Date.parse(entrada.fechaHora));
    expect(entrada.referencia).toMatch(/^moderacion-sanciones:/);

    // Ni la entrega repetida ni el reintento la duplican.
    await new Promise((listo) => setTimeout(listo, 4000));
    const todas = (await bitacora({ afectado: jugador.claims.uid })).filter(
      (e) => e.motivo === motivo,
    );
    expect(todas).toHaveLength(1);
  });

  test('2 · la lista negra: alta, edición y baja, cada una con su motivo y sus valores', async () => {
    const termino = `pruebaauditoria${SUFIJO}`;
    const alta = await api.post('/api/v1/lista-negra/terminos', {
      ...como(admin),
      data: { termino, categoria: 'OTRO', modo: 'SUBCADENA', motivo: `Alta de prueba ${SUFIJO}` },
    });
    expect(alta.status(), await alta.text()).toBe(201);
    const edicion = await api.put(`/api/v1/lista-negra/terminos/${termino}`, {
      ...como(admin),
      data: { termino, activo: false, motivo: `Apagado de prueba ${SUFIJO}` },
    });
    expect(edicion.status(), await edicion.text()).toBe(200);
    const baja = await api.delete(
      `/api/v1/lista-negra/terminos/${termino}?motivo=${encodeURIComponent(`Baja de prueba ${SUFIJO}`)}`,
      como(admin),
    );
    expect(baja.status(), await baja.text()).toBe(204);

    const entradas = await esperarEntradas({ afectado: `lista-negra:${termino}` }, () => true, 3);
    const porTipo = Object.fromEntries(entradas.map((e) => [e.tipoAccion, e]));
    expect(Object.keys(porTipo).sort()).toEqual(['ACTUALIZACION', 'CREACION', 'ELIMINACION']);
    expect(porTipo.CREACION.motivo).toBe(`Alta de prueba ${SUFIJO}`);
    expect(porTipo.CREACION.valorNuevo).toBe(
      `«${termino}» · Otro · dentro de otras palabras · activo`,
    );
    expect(porTipo.ACTUALIZACION.valorAnterior).toMatch(/· activo$/);
    expect(porTipo.ACTUALIZACION.valorNuevo).toMatch(/· apagado$/);
    expect(porTipo.ELIMINACION.motivo).toBe(`Baja de prueba ${SUFIJO}`);
    expect(porTipo.ELIMINACION.valorNuevo).toBeNull();
    for (const e of entradas) {
      expect(e.administrador).toBe(admin.claims.uid);
      expect(e.ipOrigen).toMatch(IP);
    }

    // Un motivo de más de 500 caracteres no se acepta: no cabría en la bitácora.
    const largo = await api.post('/api/v1/lista-negra/terminos', {
      ...como(admin),
      data: { termino: `otro${termino}`, motivo: 'x'.repeat(501) },
    });
    expect(largo.status()).toBe(400);
  });

  test('3 · un cambio de parámetro llega desde su versión, con lo que había y lo que queda', async () => {
    const antes = await api.get(`/api/v1/parametros/${PARAMETRO}/valor`);
    expect(antes.status(), await antes.text()).toBe(200);
    const valorAnterior = (await antes.json()).valor;
    const nuevo = String(Number(valorAnterior ?? 50) === 60 ? 61 : 60);
    const motivo = `Ajuste de prueba ${SUFIJO}`;

    const cambio = await api.put(`/api/v1/parametros/${PARAMETRO}`, {
      ...como(admin),
      data: { valor: nuevo, motivo },
    });
    expect(cambio.status(), await cambio.text()).toBe(200);
    try {
      const [entrada] = await esperarEntradas(
        { afectado: `parametro:${PARAMETRO}` },
        (e) => e.motivo === motivo,
      );
      expect(entrada.tipoAccion).toBe('ACTUALIZACION');
      expect(entrada.administrador).toBe(admin.claims.uid);
      expect(entrada.valorAnterior).toBe(valorAnterior ?? 'sin definir');
      expect(entrada.valorNuevo).toBe(nuevo);
      expect(entrada.ipOrigen, 'ya no sale sin IP (antes ms-cumplimiento la rechazaba)').toMatch(
        IP,
      );
      expect(entrada.referencia).toMatch(/^admin-parametros:version-\d+$/);
    } finally {
      // El catálogo queda como estaba para los demás specs.
      if (valorAnterior !== null && valorAnterior !== undefined) {
        await api.put(`/api/v1/parametros/${PARAMETRO}`, {
          ...como(admin),
          data: { valor: valorAnterior, motivo: `Restaurar tras el E2E ${SUFIJO}` },
        });
      }
    }
  });

  test('4 · ocultar un comentario llega desde su asiento, con los dos estados', async () => {
    const producto = await api.post('/api/v1/productos', {
      ...como(admin),
      data: {
        nombre: `Producto de auditoría E2E ${SUFIJO}`,
        imagen: '/frontend/app-web/src/cuentas/avatares/arquero-cazador.jpg',
        descripcion: 'Producto de prueba del E2E de la bitácora central.',
        tipo: 'ARMA',
        tiraje: -1,
        premium: false,
        precioCreditos: 10,
        poderDeAtaque: 5,
        tasaDeCaida: 10,
      },
    });
    expect(producto.status(), await producto.text()).toBe(201);
    const { id: productoId } = await producto.json();
    const publicado = await api.post(`/api/v1/products/${productoId}/comments`, {
      ...como(autora),
      data: { texto: `Comentario que se va a ocultar ${SUFIJO}`, estrellas: 3 },
    });
    expect(publicado.status(), await publicado.text()).toBe(201);
    const comentario = await publicado.json();
    const motivo = `Fuera de tema ${SUFIJO}`;

    const decision = await api.post(`/api/v1/comentarios/moderacion/${comentario.id}/decision`, {
      ...como(moderadora),
      data: { accion: 'OCULTAR', motivo },
    });
    expect(decision.status(), await decision.text()).toBe(200);

    const [entrada] = await esperarEntradas(
      { afectado: `comentario:${comentario.id}` },
      () => true,
    );
    expect(entrada.tipoAccion).toBe('ACTUALIZACION');
    expect(entrada.administrador).toBe(moderadora.claims.uid);
    expect(entrada.valorAnterior).toBe('Publicado');
    expect(entrada.valorNuevo).toBe('Oculto');
    expect(entrada.motivo).toBe(motivo);
    expect(entrada.ipOrigen).toMatch(IP);
    expect(entrada.referencia).toMatch(/^comentarios:/);
  });

  test('5 · una suspensión desde la gestión de usuarios queda una vez, con el motivo del panel', async () => {
    const motivo = `Suspensión por reincidencia ${SUFIJO}`;
    const r = await api.put(`/api/v1/admin/usuarios/${jugador.usuarioId}/suspender`, {
      ...como(admin),
      data: { suspendidoHasta: manana(), motivo, causal: 'ACOSO' },
    });
    expect(r.status(), await r.text()).toBe(204);

    const [entrada] = await esperarEntradas(
      { afectado: jugador.claims.uid },
      (e) => e.tipoAccion === 'SUSPENSION',
    );
    expect(entrada.motivo, 'el motivo de quien actúa, no una etiqueta fija').toBe(motivo);
    expect(entrada.valorNuevo).toContain('causal ACOSO');

    // moderacion-sanciones recibió la orden de ms-identidad, sin el borde
    // delante: no la vuelve a registrar.
    await new Promise((listo) => setTimeout(listo, 4000));
    const suspensiones = (await bitacora({ afectado: jugador.claims.uid })).filter(
      (e) => e.tipoAccion === 'SUSPENSION',
    );
    expect(suspensiones).toHaveLength(1);

    const reactivacion = await api.put(
      `/api/v1/admin/usuarios/${jugador.usuarioId}/reactivar`,
      como(admin),
    );
    expect(reactivacion.status(), await reactivacion.text()).toBe(204);
  });

  test('6 · solo el Super Administrador la lee y la exporta; nadie la escribe con su token', async () => {
    for (const quien of [moderadora, admin, jugador]) {
      const r = await api.get('/api/v1/admin/auditoria?size=1', como(quien));
      expect(r.status(), `${quien.claims.rol} no lee la bitácora`).toBe(403);
      const exportar = await api.get('/api/v1/admin/auditoria/exportar?formato=csv', como(quien));
      expect(exportar.status()).toBe(403);
    }
    const sinToken = await api.get('/api/v1/admin/auditoria');
    expect(sinToken.status()).toBe(401);

    const escrita = await api.post('/api/v1/admin/auditoria/eventos', {
      ...como(superAdmin),
      data: {
        tipoAccion: 'OTRO',
        administradorId: superAdmin.claims.uid,
        afectado: 'nada',
        motivo: 'una persona no escribe la bitácora',
        ipOrigen: '127.0.0.1',
      },
    });
    expect([401, 403, 404], 'ni con el token del Super Administrador').toContain(escrita.status());

    const csv = await api.get(
      `/api/v1/admin/auditoria/exportar?formato=csv&afectado=${jugador.claims.uid}`,
      como(superAdmin),
    );
    expect(csv.status(), await csv.text()).toBe(200);
    expect(csv.headers()['content-type']).toContain('text/csv');
    expect(csv.headers()['content-disposition']).toMatch(/auditoria-.*\.csv/);
    const texto = await csv.text();
    expect(texto.replace(/^﻿/, '').split('\r\n')[0]).toBe(
      // 1.4.0 (SA-18): nombre_afectado, al final.
      'id,fecha_hora,ocurrido_en,administrador,tipo_accion,afectado,valor_anterior,valor_nuevo,motivo,ip_origen,nombre_afectado',
    );
    expect(texto).toContain(`Insultos en el chat general ${SUFIJO}`);

    const pdf = await api.get(
      `/api/v1/admin/auditoria/exportar?afectado=${jugador.claims.uid}`,
      como(superAdmin),
    );
    expect(pdf.status()).toBe(200);
    expect(pdf.headers()['content-type']).toContain('application/pdf');
    expect((await pdf.body()).subarray(0, 4).toString()).toBe('%PDF');

    const raro = await api.get('/api/v1/admin/auditoria/exportar?formato=xml', como(superAdmin));
    expect(raro.status()).toBe(400);

    // Cada exportación queda registrada, con su formato.
    const exportaciones = await esperarEntradas(
      { tipoAccion: 'EXPORTACION', administradorId: superAdmin.claims.uid },
      (e) => /en (CSV|PDF)$/.test(e.motivo),
      2,
    );
    expect(exportaciones.map((e) => e.motivo)).toEqual(
      expect.arrayContaining([
        'Exportación del registro de auditoría en CSV',
        'Exportación del registro de auditoría en PDF',
      ]),
    );
  });
});
