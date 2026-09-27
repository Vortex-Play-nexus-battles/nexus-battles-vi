/**
 * LABORATORIO — fuente de mensajes privados de ejemplo. NO ES DEL PRODUCTO.
 *
 * El laboratorio visual sirve este archivo EN LUGAR DE
 * `frontend/app-web/src/plataforma/salas-partidas/fuente-mensajes.js` (ver
 * `escenarios-poblados.js`, escenarios `chat-privados-*`), para fotografiar y
 * auditar con axe los estados que pidió el docente: lista, vacío, error,
 * buscar jugador, conversación con «Tú», el otro jugador y el sistema,
 * entrega (enviado y leído), reconexión, bloqueo, cuenta sancionada y el
 * silencio propio con su cuenta atrás.
 *
 * En producción esa fuente dice `disponible: false` (no hay servicio ni
 * contrato de mensajes privados) y la pestaña lo cuenta. Todos los apodos y
 * textos de aquí son DATOS DE LABORATORIO.
 *
 * El escenario elige la variante con `?laboratorio=` en la URL de la vista:
 * `bandeja` (por omisión), `vacia`, `error`, `silencio`, `reconectando`.
 */

import { usuarioIdDeSesion } from '../../comun/identidad.js';

/* Aviso visible en cada captura: esto es el laboratorio, no el producto. */
if (typeof document !== 'undefined' && document.body) {
  const aviso = document.createElement('p');
  aviso.setAttribute('role', 'note');
  aviso.dataset.laboratorio = 'mensajes';
  aviso.textContent =
    'Laboratorio visual: conversaciones de ejemplo. En producción la pestaña «Mensajes privados» dice que todavía no están abiertos, porque no existe su servicio.';
  aviso.style.cssText =
    'margin:0;padding:8px 16px;background:#FBF0DE;color:#5C3100;font:600 13px/1.4 Inter,system-ui,sans-serif;text-align:center;border-bottom:2px solid #8A4A00';
  document.body.prepend(aviso);
}

const VARIANTE =
  new URLSearchParams(globalThis.location?.search ?? '').get('laboratorio') ?? 'bandeja';
const MINUTO = 60_000;
const ahora = Date.now();
const hace = (minutos) => new Date(ahora - minutos * MINUTO).toISOString();
const yo = () => ({ id: usuarioIdDeSesion() ?? 'uid-laboratorio', apodo: 'Tú' });

const BRUMA = { id: 'lab-bruma', apodo: 'Bruma' };
const KAEL = { id: 'lab-kael', apodo: 'Kael_77' };
const NYRA = { id: 'lab-nyra', apodo: 'Nyra' };
const ORYM = { id: 'lab-orym', apodo: 'Orym' };
const TALA = { id: 'lab-tala', apodo: 'Tala' };
const BRASA = { id: 'lab-brasa', apodo: 'Brasa_Roja' };
const RAGNAR = { id: 'lab-ragnar', apodo: 'Ragnar' };
const JUGADORES = [BRUMA, KAEL, NYRA, ORYM, TALA, BRASA, RAGNAR];

function conversaciones() {
  return [
    {
      id: 'lab-c-bruma',
      con: BRUMA,
      ultimo: { texto: 'Si ganas, la revancha la elijo yo.', enviadoEn: hace(3), deMi: false },
      noLeidos: 2,
      estado: 'ACTIVA',
    },
    {
      id: 'lab-c-kael',
      con: KAEL,
      ultimo: { texto: 'Te paso el código de la sala.', enviadoEn: hace(60 * 5), deMi: true },
      noLeidos: 0,
      estado: 'ACTIVA',
    },
    {
      id: 'lab-c-nyra',
      con: NYRA,
      ultimo: { texto: 'Buena partida.', enviadoEn: hace(60 * 26), deMi: false },
      noLeidos: 0,
      estado: 'BLOQUEADA',
    },
    {
      id: 'lab-c-orym',
      con: ORYM,
      ultimo: { texto: '¿Torneo el sábado?', enviadoEn: hace(60 * 24 * 4), deMi: false },
      noLeidos: 0,
      estado: 'CUENTA_SANCIONADA',
    },
  ];
}

function hiloDe(id) {
  if (id === 'lab-c-bruma') {
    return [
      {
        id: 'lb-1',
        tipo: 'mensaje',
        autor: BRUMA,
        texto: '¿Te animas a un 1v1 esta noche?',
        enviadoEn: hace(60 * 25),
      },
      {
        id: 'lb-2',
        tipo: 'mensaje',
        autor: yo(),
        texto: 'Claro. Llevo el Guerrero Tanque.',
        enviadoEn: hace(60 * 24 + 50),
        entrega: 'LEIDO',
      },
      {
        id: 'lb-3',
        tipo: 'mensaje',
        autor: BRUMA,
        texto: 'Perfecto. Sala privada a las 8, te paso el código por aquí.',
        enviadoEn: hace(12),
      },
      {
        id: 'lb-4',
        tipo: 'mensaje',
        autor: yo(),
        texto: 'Hecho. ¿Apuesta de 50 créditos?',
        enviadoEn: hace(8),
        entrega: 'ENVIADO',
      },
      {
        id: 'lb-5',
        tipo: 'mensaje',
        autor: BRUMA,
        texto: 'Si ganas, la revancha la elijo yo.',
        enviadoEn: hace(3),
      },
    ];
  }
  if (id === 'lab-c-kael') {
    return [
      {
        id: 'lk-1',
        tipo: 'mensaje',
        autor: KAEL,
        texto: '¿Tienes sitio en tu equipo de torneo?',
        enviadoEn: hace(60 * 6),
      },
      {
        id: 'lk-2',
        tipo: 'mensaje',
        autor: yo(),
        texto: 'Te paso el código de la sala.',
        enviadoEn: hace(60 * 5),
        entrega: 'LEIDO',
      },
    ];
  }
  if (id === 'lab-c-nyra') {
    return [
      {
        id: 'ln-1',
        tipo: 'mensaje',
        autor: NYRA,
        texto: 'Buena partida.',
        enviadoEn: hace(60 * 26),
      },
    ];
  }
  if (id === 'lab-c-orym') {
    return [
      {
        id: 'lo-1',
        tipo: 'mensaje',
        autor: ORYM,
        texto: '¿Torneo el sábado?',
        enviadoEn: hace(60 * 24 * 4),
      },
    ];
  }
  return [];
}

const oyentes = new Set();
const emitir = (evento) => oyentes.forEach((oyente) => oyente(evento));

export class MensajesSinAbrir extends Error {}

export const FUENTE_SIN_SERVICIO = Object.freeze({
  disponible: false,
  bandeja: async () => {
    throw new MensajesSinAbrir();
  },
  buscarJugadores: async () => [],
  conversacionCon: async () => {
    throw new MensajesSinAbrir();
  },
  hilo: async () => ({ mensajes: [] }),
  enviar: async () => {
    throw new MensajesSinAbrir();
  },
  marcarLeida: async () => {},
  bloquear: async () => {
    throw new MensajesSinAbrir();
  },
  escuchar: () => () => {},
});

const LABORATORIO = {
  disponible: true,
  async bandeja() {
    if (VARIANTE === 'error') {
      throw Object.assign(new Error('laboratorio'), {
        detalle: 'El servicio de mensajes no respondió. Vuelve a intentarlo en un momento.',
      });
    }
    if (VARIANTE === 'vacia') {
      return { conversaciones: [], restriccion: null };
    }
    return {
      conversaciones: conversaciones(),
      restriccion:
        VARIANTE === 'silencio'
          ? {
              tipo: 'SILENCIO',
              hasta: new Date(ahora + 2 * 86_400_000 + 5 * 3_600_000).toISOString(),
              motivo: 'Lenguaje ofensivo reiterado',
            }
          : null,
    };
  },
  async buscarJugadores(texto) {
    return JUGADORES.filter((j) => j.apodo.toLowerCase().includes(texto.toLowerCase()));
  },
  async conversacionCon(jugadorId) {
    const existente = conversaciones().find((c) => c.con.id === jugadorId);
    if (existente) {
      return existente;
    }
    const jugador = JUGADORES.find((j) => j.id === jugadorId);
    return { id: `lab-c-${jugadorId}`, con: jugador, ultimo: null, noLeidos: 0, estado: 'ACTIVA' };
  },
  async hilo(id) {
    if (VARIANTE === 'reconectando') {
      setTimeout(() => emitir({ tipo: 'canal', estado: 'reconectando', intento: 2, de: 5 }), 200);
    }
    return { mensajes: hiloDe(id) };
  },
  async enviar(_conversacionId, texto) {
    return {
      id: `lab-${Date.now()}`,
      tipo: 'mensaje',
      autor: yo(),
      texto,
      enviadoEn: new Date().toISOString(),
      entrega: 'ENVIADO',
    };
  },
  async marcarLeida() {},
  async bloquear(jugadorId, bloquear) {
    const resumen = await this.conversacionCon(jugadorId);
    return { ...resumen, estado: bloquear ? 'BLOQUEADA' : 'ACTIVA' };
  },
  escuchar(oyente) {
    oyentes.add(oyente);
    return () => oyentes.delete(oyente);
  },
  reintentar() {
    emitir({ tipo: 'canal', estado: 'reconectado' });
  },
};

export function fuenteDeMensajes() {
  return LABORATORIO;
}
