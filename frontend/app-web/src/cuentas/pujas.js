/**
 * Subastas - Módulo interactivo de pujas y compras inmediatas (HU-SUB-004)
 *
 * Cumple con PILA_T_1 y .claude/rules/frontend-web.md:
 * - HTML5 + CSS3 + ES2022 nativo sin dependencias de frameworks
 * - 4 estados obligatorios (RNF-USA-003): carga, éxito, vacío, error
 * - Resolución de contraste WCAG 2.2 AA y fichas de tokens
 * - Reglas de 7.7 (incremento, límites, intervalo) dichas por el servidor
 *   (GET /subastas/reglas, B8), no escritas a mano
 * - Vistas integradas: Explorar, Mis subastas, Detalle y Cierre múltiple
 * - Sistema de avisos cruzados en vivo tipo toast (esquina inferior derecha)
 */

import { conectarStomp } from '../comun/transporte-stomp.js';
import { cuerpoDelToken } from '../comun/identidad.js';
import { iconoHtml } from '../comun/ui/icono.js';
// UX-R2.8c — esta vista se pinta con plantillas de cadena y `innerHTML`, y
// no escapaba NADA: el nombre del objeto, su descripcion, el apodo del
// vendedor y el del pujador salen del servidor y los escribe otra persona.
// Una subasta llamada `<img src=x onerror=…>` se ejecutaba al abrir «Mis
// pujas», con la sesion puesta, en la pantalla que mueve creditos.
//
// El `innerHTML` que queda es deliberado —reescribir 2.300 lineas de golpe
// en una vista de dinero es peor idea que cerrar el agujero hoy— y por eso
// va con saneamiento EXPLICITO en cada interpolacion que lleve datos.
// `sin-innerhtml.test.js` lo tiene anotado; la estructura se mueve en R2.10.
import { esc } from '../comun/ui/escapar.js';
import { nombreDelTipo } from '../comun/ui/formato.js';
import { urlDeLogin } from '../comun/sesion.js';
import { acusar } from '../comun/ui/acuse.js';
import { textoDeError } from '../comun/ui/texto-de-fallo.js';
import { confirmar } from '../comun/ui/dialogo.js';
import { montarHiloDeComentarios } from '../plataforma/comentarios/hilo-comentarios.js';
// B8 — los textos de los motivos del servidor viven en un solo sitio: la
// pantalla explica con las mismas palabras por que no ofrece cancelar.
import { mensajePara } from './pujas-api.js';

/**
 * UXC-8 — cuántas publicaciones terminadas y cuántos movimientos del
 * historial se ven de entrada; el resto, con «Ver todas».
 */
const TERMINADAS_A_LA_VISTA = 5;
const MOVIMIENTOS_A_LA_VISTA = 10;

/** Canal que publica ms-subastas en cada cambio (SubastaRealtimePublisher). */
export const CANAL_SUBASTAS = '/topic/subastas/listado';

/**
 * Estado del canal en vivo — FI-R11.
 *
 * Las mismas tres variantes que usa la bandeja de notificaciones
 * (`plataforma/notificaciones/bandeja.js`) y las mismas clases del kit
 * (`.conexion--*`), a proposito: la persona ya aprendio lo que significa esa
 * pildora ambar en la campana, y ensenarle otra distinta aqui seria pedirle que
 * lo aprenda dos veces.
 *
 * Que habia antes: nada. `abrirCanalEnVivo` devolvia `null` en silencio si el
 * WebSocket no abria, y no habia reconexion ninguna. La pantalla decia lo mismo
 * con canal y sin el, y el sondeo de 5 s tapaba el hueco lo justo para que
 * nadie lo notara — que es peor, porque en una subasta que cierra en diez
 * segundos la diferencia entre tiempo real y cinco segundos de retraso es la
 * subasta.
 */
export const ESTADO_CANAL = Object.freeze({
  CONECTANDO: 'reconectando',
  ESTABLE: 'estable',
  RECONECTANDO: 'reconectando',
  SIN_CONEXION: 'sin-conexion',
});

/** Lo que se lee en la pildora. */
export const TEXTO_CANAL = Object.freeze({
  [ESTADO_CANAL.ESTABLE]: 'Pujas al instante',
  [ESTADO_CANAL.RECONECTANDO]: 'Reconectando…',
  [ESTADO_CANAL.SIN_CONEXION]: 'Las pujas pueden tardar unos segundos',
});

/**
 * Espera creciente entre reintentos. La misma escalera que la bandeja: empieza
 * en un segundo porque una caida de red suele durar menos que eso, y se para en
 * treinta para no castigar a un servidor que esta reiniciando.
 */
export const ESPERAS_DE_RECONEXION = Object.freeze([1000, 2000, 5000, 10000, 30000]);

/**
 * Donde guarda el login el JWT. Misma clave que lee `pujas-api.js` para las
 * llamadas REST: la sesion es una sola, y el canal en vivo (R9.6) tiene que
 * acreditarse con el mismo token que ya usa todo lo demas de esta pantalla.
 */
const CLAVE_TOKEN_SESION = 'nexus.token';

/**
 * El simbolo del sprite que le toca a cada rareza. Escala igual que la rareza:
 * escudo, espada, fuego, trofeo.
 *
 * Aqui habia una paleta: cada rareza traia `fondo`, `texto` y `borde` escritos
 * a mano —`#E7EAF0`, `#57627A`, `#9FABC9`…— y se inyectaban como `style` en la
 * ficha. Eran los mismos valores que `--rareza-*` del kit, duplicados en
 * JavaScript, y por estar en linea se saltaban `prefers-contrast: more`: quien
 * pide mas contraste seguia viendo el tinte del dos por ciento. Ahora el color
 * lo pone la clase y el icono hereda `currentColor`.
 */
export const ICONO_RAREZA = Object.freeze({
  comun: 'escudo',
  rara: 'espada',
  epica: 'fuego',
  legendaria: 'trofeo',
});

/**
 * Como se dice una rareza que puede no venir — FI-R1.
 *
 * `rareza` esta en `SubastaResumen`, pero **no es obligatoria y no trae
 * enumeracion**: el contrato dice `type: string` y nada mas. Hasta ahora el
 * cliente rellenaba 'comun' cuando faltaba —inventarse un escalon real del
 * juego— y la vista hacia `sub.rareza.toUpperCase()` sin guarda, que revienta
 * en cuanto llega sin ella.
 *
 * Lo que no se sabe no se dice: sin rareza no se pinta distintivo, y la ficha
 * usa su variante neutra. Una rareza que llegue con un nombre que el juego no
 * conoce se trata igual, porque adivinar cual de los cuatro escalones quiso
 * decir el servidor seria lo mismo que inventarla.
 *
 * @param {unknown} rareza
 * @returns {{conocida: boolean, clase: string, texto: string|null, simbolo: string}}
 */
export function nivelRequeridoVisible(nivel) {
  // UXC-8 — el contrato de subastas no trae nivel: escribir «sin dato» en
  // cada tarjeta era ruido. Sin nivel no se dice nada.
  return typeof nivel === 'number' && Number.isFinite(nivel) ? `Nivel req. ${nivel}` : '';
}

export function rarezaVisible(rareza) {
  const limpia = typeof rareza === 'string' ? rareza.toLowerCase().trim() : null;
  if (limpia && Object.hasOwn(ICONO_RAREZA, limpia)) {
    return {
      conocida: true,
      clase: limpia,
      texto: limpia.toUpperCase(),
      simbolo: ICONO_RAREZA[limpia],
    };
  }
  return { conocida: false, clase: 'desconocida', texto: null, simbolo: 'escudo' };
}

/**
 * Datos de ejemplo. **Son un banco de pruebas, no un modo de demostracion.**
 * La pantalla ya no tiene ningun camino hasta aqui: pujas.html monta siempre
 * contra el servicio real. Solo los usa pujas.test.js, para poder ejercitar el
 * renderizado y las reglas de la vista sin levantar backend ni base de datos.
 *
 * No volver a colgarlos de la pagina. Pintar datos inventados cuando el
 * servicio no responde engana justo cuando mas importa saberlo.
 */
export const SUBASTAS_INICIALES = [
  {
    id: 'hacha-obsidiana',
    nombre: 'Hacha de Obsidiana Fracturada',
    tipo: 'Arma · Dos manos',
    descripcion:
      'Hoja de obsidiana templada en el Abismo. Su filo no se degrada, pero cada golpe cobra una fracción de la vitalidad de quien la empuña.',
    rareza: 'epica',
    nivel: 24,
    vendedor: 'kaelthas_vx',
    oferta: 1350,
    compraInmediata: 2800,
    mediaMercado: 1800,
    segundosRestantes: 8,
    ganando: true,
    superado: false,
    autoLimite: 2000,
    esperaSegundos: 0,
    retenido: 1350,
    rival: 'draconis_91',
    rivales: 3,
    // B8 — cada subasta lleva el incremento con el que se publico (el
    // servidor lo da en su ficha). El de estos ejemplos es de ejemplo.
    incrementoMinimo: 50,
    aporte: { poder: 14, vida: 0, defensa: -3 },
    historial: [
      { apodo: 'andres_nv', monto: 1350, tipo: 'Manual', cuando: 'hace 9 s', esTu: true },
      { apodo: 'draconis_91', monto: 1300, tipo: 'Automática', cuando: 'hace 25 s', esTu: false },
      { apodo: 'kael_vortex', monto: 1200, tipo: 'Automática', cuando: 'hace 1 min', esTu: false },
      { apodo: 'andres_nv', monto: 1150, tipo: 'Manual', cuando: 'hace 2 min', esTu: true },
    ],
  },
  {
    id: 'grebas-centinela',
    nombre: 'Grebas del Centinela Caído',
    tipo: 'Armadura · Piernas',
    descripcion: 'Placas recogidas del campo de Vael. Pesan, pero aguantan lo que nada.',
    rareza: 'rara',
    nivel: 20,
    vendedor: 'valeria_iron',
    oferta: 880,
    compraInmediata: 1500,
    mediaMercado: 900,
    segundosRestantes: 96,
    ganando: false,
    superado: true,
    autoLimite: 1200,
    esperaSegundos: 0,
    retenido: 0,
    rival: 'thar_vex',
    rivales: 2,
    // B8 — cada subasta lleva el incremento con el que se publico (el
    // servidor lo da en su ficha). El de estos ejemplos es de ejemplo.
    incrementoMinimo: 50,
    aporte: { poder: 0, vida: 90, defensa: 18 },
    historial: [
      { apodo: 'thar_vex', monto: 880, tipo: 'Automática', cuando: 'hace 12 s', esTu: false },
      { apodo: 'andres_nv', monto: 800, tipo: 'Manual', cuando: 'hace 45 s', esTu: true },
    ],
  },
  {
    id: 'amuleto-brasa',
    nombre: 'Amuleto de Brasa Eterna',
    tipo: 'Accesorio · Cuello',
    descripcion:
      'Gema ígnea que late con el calor de las forjas primigenias. Otorga gran afinidad arcana y salud.',
    rareza: 'legendaria',
    nivel: 25,
    vendedor: 'aerith_moon',
    oferta: 2400,
    compraInmediata: 4800,
    mediaMercado: 2600,
    segundosRestantes: 640,
    ganando: true,
    superado: false,
    autoLimite: 2000,
    esperaSegundos: 0,
    retenido: 2400,
    rival: 'valkyria_99',
    rivales: 4,
    // B8 — cada subasta lleva el incremento con el que se publico (el
    // servidor lo da en su ficha). El de estos ejemplos es de ejemplo.
    incrementoMinimo: 50,
    aporte: { poder: 25, vida: 50, defensa: 5 },
    historial: [
      { apodo: 'andres_nv', monto: 2400, tipo: 'Automática', cuando: 'hace 1 min', esTu: true },
      { apodo: 'valkyria_99', monto: 2350, tipo: 'Manual', cuando: 'hace 2 min', esTu: false },
    ],
  },
  {
    id: 'daga-hueso',
    nombre: 'Daga de Hueso Pulido',
    tipo: 'Arma · Una mano',
    descripcion: 'Tallada en colmillo de behemoth. Ligera y letal en ataques furtivos.',
    rareza: 'comun',
    nivel: 12,
    vendedor: 'zephyr_mage',
    oferta: 310,
    compraInmediata: 700,
    mediaMercado: 400,
    segundosRestantes: 1820,
    ganando: false,
    superado: false,
    autoLimite: 0,
    esperaSegundos: 0,
    retenido: 0,
    rival: 'novato_12',
    rivales: 1,
    // B8 — cada subasta lleva el incremento con el que se publico (el
    // servidor lo da en su ficha). El de estos ejemplos es de ejemplo.
    incrementoMinimo: 50,
    aporte: { poder: 8, vida: 10, defensa: 0 },
    historial: [
      { apodo: 'novato_12', monto: 310, tipo: 'Manual', cuando: 'hace 5 min', esTu: false },
    ],
  },
  {
    id: 'yelmo-vigia',
    nombre: 'Yelmo del Vigía',
    tipo: 'Armadura · Cabeza',
    descripcion:
      'Casco de acero templado con visor blindado. Mejora la resistencia y percepción táctica.',
    rareza: 'rara',
    nivel: 18,
    vendedor: 'barkeep_tom',
    oferta: 1100,
    compraInmediata: 2200,
    mediaMercado: 1400,
    segundosRestantes: 5400,
    ganando: false,
    superado: false,
    autoLimite: 1500,
    esperaSegundos: 0,
    retenido: 0,
    rival: 'ignis_red',
    rivales: 2,
    // B8 — cada subasta lleva el incremento con el que se publico (el
    // servidor lo da en su ficha). El de estos ejemplos es de ejemplo.
    incrementoMinimo: 50,
    aporte: { poder: 6, vida: 60, defensa: 14 },
    historial: [
      { apodo: 'ignis_red', monto: 1100, tipo: 'Manual', cuando: 'hace 10 min', esTu: false },
    ],
  },
];

/**
 * Heroes de ejemplo. **Banco de pruebas, no el heroe de nadie.**
 *
 * FI-R1 — hasta ahora esto era el valor por defecto del controlador, y
 * `pujas.html` nunca pasa `heroes`: el selector de la ficha de subasta
 * ofrecia «Kaelen (Niv. 26 - Guerrero)» y «Lyra (Niv. 21 - Exploradora)» a
 * cualquiera que abriera la pantalla, y la tabla de comparacion sumaba el
 * aporte del objeto a las estadisticas de un heroe inventado. Ahora el
 * defecto es `[]` y estos dos solo entran cuando alguien los pasa a proposito
 * (pruebas y laboratorio visual).
 */
export const HEROES_BASE = [
  {
    id: 'kaelen',
    nombre: 'Kaelen',
    nivel: 26,
    clase: 'Guerrero',
    stats: { poder: 142, vida: 890, defensa: 64, ataque: '10 + 1d6', dano: '8 + 2d4' },
  },
  {
    id: 'lyra',
    nombre: 'Lyra',
    nivel: 21,
    clase: 'Exploradora',
    stats: { poder: 118, vida: 640, defensa: 48, ataque: '7 + 1d8', dano: '6 + 1d6' },
  },
];

export const CONFIG_REGLAS = {
  // Sin creditosTotales: el saldo lo sabe ms-finanzas y llega en
  // /mis-pujas/resumen. Tenerlo aqui significaba validar las pujas contra una
  // cifra inventada, que es peor que no ensenarla: bloqueaba o dejaba pasar
  // pujas segun un numero que no tenia nada que ver con el dinero del jugador.
  //
  // B8 — el incremento era 50 escrito a mano, y el servicio no aplicaba 50:
  // aplicaba el de cada subasta. Ahora lo dice el servidor (GET
  // /subastas/reglas, parametro `subastas.incremento-minimo` de
  // admin-parametros) y, mientras el Product Owner no lo fije, NO hay
  // incremento: null, nunca una cifra inventada. Los tres limites de abajo
  // son los del documento (7.7.10) y solo sirven de respaldo si las reglas no
  // llegan; con servidor se sustituyen por lo que el diga.
  incrementoMinimo: null,
  intervaloSegundos: 5,
  // Publicaciones activas a la vez por vendedor (7.7.10), no subastas en las
  // que se puja: pujar no tiene ese tope (ms-subastas-pujas.yaml 0.4.0).
  maxSubastasSimultaneas: 10,
  maxPujasActivas: 50,
};

/**
 * Desenlaces de ejemplo. **Banco de pruebas, no el historial de nadie.**
 *
 * FI-R1 — ms-subastas no expone hoy ningun recurso de cierres: `/mis-pujas`
 * solo tiene `/resumen` (MisPujasController) y ni el contrato de listado ni
 * el de pujas declaran un desenlace. Con estos tres como valor por defecto,
 * la pestana «Cierre multiple» ensenaba a todo el mundo el mismo «3
 * CERRARON», el mismo hacha adjudicada y el mismo balance. Ahora el defecto
 * es `[]` y la vista dice que no hay datos de cierre.
 */
export const EVENTOS_CIERRE_DEFAULT = [
  {
    id: 'hacha-obsidiana',
    nombre: 'Hacha de Obsidiana Fracturada',
    rareza: 'epica',
    tipoDesenlace: 'adjudicada',
    montoFinal: 1350,
    montoCobrado: 1350,
    montoDevuelto: 0,
    ganador: 'andres_nv',
    esGanador: true,
    motivo: '¡Adjudicada a tu inventario! La mejor oferta se mantuvo hasta el cierre.',
  },
  {
    id: 'grebas-centinela',
    nombre: 'Grebas del Centinela Caído',
    rareza: 'rara',
    tipoDesenlace: 'superada_rival',
    montoFinal: 1240,
    montoCobrado: 0,
    montoDevuelto: 880,
    ganador: 'thar_vex',
    esGanador: false,
    motivo: 'Ganó thar_vex con 1.240 cr · su automática respondió',
  },
  {
    id: 'amuleto-brasa',
    nombre: 'Amuleto de Brasa Eterna',
    rareza: 'legendaria',
    tipoDesenlace: 'superada_tope',
    montoFinal: 2450,
    topePropio: 2400,
    montoCobrado: 0,
    montoDevuelto: 2400,
    ganador: 'valkyria_99',
    esGanador: false,
    motivo: 'Tu automática paró en su tope de 2.400 cr · cerró en 2.450 cr',
  },
];

// =========================================================================
// Funciones de cálculo y lógica pura (probables con Jest sin DOM)
// =========================================================================

export function formatearCreditos(n) {
  // UXC-8 — «no se sabe» no es cero: un saldo o un precio que no vino se
  // pinta como raya, nunca como «0 cr» (que se lee «no tienes nada»).
  if (n === null || n === undefined || Number.isNaN(Number(n))) {
    return '—';
  }
  return Number(n).toLocaleString('es-CO');
}

/**
 * UXC-8 — el tipo del producto en palabras, no la constante del contrato.
 *
 * @param {string|null|undefined} tipo
 */
export function tipoLegible(tipo) {
  return nombreDelTipo(tipo);
}

/**
 * UXC-8 — un momento del historial como se lee: «hoy, 10:02» o «22 sept, 10:02».
 *
 * @param {string} iso
 */
export function momentoLegible(iso) {
  const fecha = new Date(iso);
  if (Number.isNaN(fecha.getTime())) {
    return '';
  }
  const hoy = new Date();
  const mismoDia = fecha.toDateString() === hoy.toDateString();
  const hora = fecha.toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' });
  return mismoDia
    ? `hoy, ${hora}`
    : `${fecha.toLocaleDateString('es-CO', { day: 'numeric', month: 'short' })}, ${hora}`;
}

export function formatearTiempo(seg) {
  if (seg <= 0) {
    return 'Cerrada';
  }
  if (seg >= 3600) {
    const h = Math.floor(seg / 3600);
    const m = Math.floor((seg % 3600) / 60);
    return `${h} h ${m} m`;
  }
  const m = Math.floor(seg / 60);
  const s = seg % 60;
  return `${m}:${String(s).padStart(2, '0')}`;
}

export function calcularSaldoRetenido(subastas = []) {
  return subastas.reduce((acc, sub) => acc + (sub.retenido || 0), 0);
}

export function calcularSaldoLibre(total, subastas = []) {
  return Math.max(0, total - calcularSaldoRetenido(subastas));
}

export function calcularMinimoPuja(ofertaActual, incremento = CONFIG_REGLAS.incrementoMinimo) {
  // B8 — sin el `|| 50` de antes: un incremento desconocido no es 50.
  return (Number(ofertaActual) || 0) + (Number(incremento) || 0);
}

/**
 * Lo minimo que se puede ofrecer ahora en una subasta (7.7.2 y 7.7.6), o null
 * si no se puede saber desde aqui.
 *
 * <ul>
 *   <li>Con la ficha del servidor (`pujaMinimaSiguiente`), esa: no hay nada
 *       que calcular.</li>
 *   <li>Sin pujas todavia, el precio minimo que fijo el vendedor (la oferta
 *       vigente): la primera puja no suma incremento. Hasta B8 se le sumaban
 *       50, y el servidor tampoco lo exigia bien.</li>
 *   <li>Con pujas, oferta + incremento: el de ESTA subasta si se sabe, si no
 *       el vigente.</li>
 * </ul>
 *
 * Null significa «lo decide el servidor»: con el incremento sin configurar
 * (decision del PO pendiente) la pantalla no inventa uno.
 *
 * @param {{oferta: number, rivales?: number, pujaMinimaSiguiente?: number|null,
 *   incrementoMinimo?: number|null}} subasta
 * @param {number|null} [incremento] el vigente segun las reglas
 * @returns {number|null}
 */
export function minimoDePuja(subasta, incremento = CONFIG_REGLAS.incrementoMinimo) {
  if (!subasta) {
    return null;
  }
  if (subasta.pujaMinimaSiguiente !== null && subasta.pujaMinimaSiguiente !== undefined) {
    return Number(subasta.pujaMinimaSiguiente);
  }
  if (subasta.rivales === 0) {
    return Number(subasta.oferta) || 0;
  }
  const propio = subasta.incrementoMinimo ?? incremento;
  const paso = Number(propio);
  return propio !== null && propio !== undefined && Number.isFinite(paso) && paso > 0
    ? (Number(subasta.oferta) || 0) + paso
    : null;
}

export function validarPuja(
  monto,
  subasta,
  saldoLibre,
  incremento = CONFIG_REGLAS.incrementoMinimo,
  esperaRestante = 0,
) {
  if (!subasta) {
    return { valida: false, motivo: 'Subasta no encontrada.' };
  }
  if (subasta.segundosRestantes <= 0) {
    return { valida: false, motivo: 'La subasta está cerrada.' };
  }
  if (esperaRestante > 0) {
    return {
      valida: false,
      motivo: `Debes esperar ${esperaRestante} s antes de volver a pujar en esta subasta.`,
    };
  }
  const min = minimoDePuja(subasta, incremento);
  // Null: no se sabe desde aqui (incremento sin configurar). No se bloquea: el
  // servidor aplica el de la subasta y responde OFERTA_INSUFICIENTE si no llega.
  if (min !== null && monto < min) {
    return {
      valida: false,
      motivo:
        subasta.rivales === 0
          ? `La primera oferta debe ser de al menos ${formatearCreditos(min)} cr (precio mínimo).`
          : `La oferta debe ser de al menos ${formatearCreditos(min)} cr (+${formatearCreditos(min - subasta.oferta)} cr).`,
    };
  }
  // Saldo desconocido: no se bloquea. El servidor es la autoridad sobre el
  // dinero y responde SALDO_INSUFICIENTE si no alcanza. Frenar aqui por no
  // haber podido preguntar le negaria al jugador una puja que si puede pagar,
  // que es peor que dejarle intentarlo y recibir un no con motivo.
  if (saldoLibre === null || saldoLibre === undefined) {
    return { valida: true };
  }
  const disponibleParaEsta = saldoLibre + (subasta.retenido || 0);
  if (monto > disponibleParaEsta) {
    return {
      valida: false,
      motivo: `Saldo insuficiente. Tienes ${formatearCreditos(disponibleParaEsta)} cr disponibles para esta subasta.`,
    };
  }
  return { valida: true };
}

export function validarLimiteAuto(
  limite,
  subasta,
  saldoLibre,
  incremento = CONFIG_REGLAS.incrementoMinimo,
) {
  if (!subasta) {
    return { valida: false, motivo: 'Subasta no encontrada.' };
  }
  const min = minimoDePuja(subasta, incremento);
  if (min !== null && limite < min) {
    return {
      valida: false,
      motivo: `El tope de puja automática debe ser al menos ${formatearCreditos(min)} cr.`,
    };
  }
  // Saldo desconocido: no se bloquea. El servidor es la autoridad sobre el
  // dinero y responde SALDO_INSUFICIENTE si no alcanza. Frenar aqui por no
  // haber podido preguntar le negaria al jugador una puja que si puede pagar,
  // que es peor que dejarle intentarlo y recibir un no con motivo.
  if (saldoLibre === null || saldoLibre === undefined) {
    return { valida: true };
  }
  const disponibleParaEsta = saldoLibre + (subasta.retenido || 0);
  if (limite > disponibleParaEsta) {
    return {
      valida: false,
      motivo: `Saldo insuficiente para ese tope. Máximo disponible: ${formatearCreditos(disponibleParaEsta)} cr.`,
    };
  }
  return { valida: true };
}

export function calcularComparacionHeroe(heroe, item) {
  // FI-R1 — `evaluado` separa «no se pudo comparar» de «compara bien». Antes
  // faltando datos se devolvia `nivelInsuficiente: false`, y la vista leia ese
  // false como un veredicto: pintaba «Compatible: <heroe> cumple el nivel
  // requerido» citando RN-INV-004 sin haber comprobado nada. Sin heroe
  // conectado el texto salia literalmente «Compatible: undefined cumple».
  //
  // El `|| { poder: 0, vida: 0, defensa: 0 }` de `aporte` hacia lo mismo con
  // la tabla: un objeto del que no se sabe que aporta pasaba a aportar cero,
  // y la columna «Diferencia» quedaba en «igual» para las tres filas.
  const faltanDatos =
    !heroe ||
    !item ||
    !heroe.stats ||
    !Number.isFinite(heroe.nivel) ||
    !Number.isFinite(item.nivel) ||
    !item.aporte;
  if (faltanDatos) {
    return { evaluado: false, comparaciones: [], nivelInsuficiente: false, deltaNivel: 0 };
  }
  const nivelInsuficiente = heroe.nivel < item.nivel;
  const deltaNivel = item.nivel - heroe.nivel;
  const st = heroe.stats;
  const ap = item.aporte;

  const comparaciones = [
    { stat: 'Poder', actual: st.poder, nuevo: st.poder + ap.poder, delta: ap.poder },
    { stat: 'Vida', actual: st.vida, nuevo: st.vida + ap.vida, delta: ap.vida },
    { stat: 'Defensa', actual: st.defensa, nuevo: st.defensa + ap.defensa, delta: ap.defensa },
  ];

  return { evaluado: true, comparaciones, nivelInsuficiente, deltaNivel };
}

export function calcularSumaTopesAuto(subastas = []) {
  return subastas.reduce((acc, sub) => acc + (Number(sub.autoLimite) || 0), 0);
}

export function verificarSobreCompromiso(total, subastas = []) {
  const sumaTopes = calcularSumaTopesAuto(subastas);
  // UXC-8 — sin saldo no se sabe: `sumaTopes > null` es `sumaTopes > 0`, y
  // la alarma «tus automáticas prometen más de lo que tienes» saltaba con
  // cualquier automática puesta cuando el saldo no había llegado.
  const sobreCompromiso = total !== null && total !== undefined && sumaTopes > total;
  const faltante = sobreCompromiso ? sumaTopes - total : 0;
  return { sobreCompromiso, sumaTopes, total, faltante };
}

export function calcularBalanceNetoCierre(
  eventosCierre = [],
  saldoTotal = null,
  retenidoEnOtras = 0,
) {
  const cobrado = eventosCierre.reduce((acc, ev) => acc + (Number(ev.montoCobrado) || 0), 0);
  const devuelto = eventosCierre.reduce((acc, ev) => acc + (Number(ev.montoDevuelto) || 0), 0);
  // saldoTotal null = no se sabe. Se propaga como null en vez de convertirse
  // en cero, para que la pantalla lo muestre como desconocido.
  const saldoLibre =
    saldoTotal === null || saldoTotal === undefined
      ? null
      : Math.max(0, saldoTotal - retenidoEnOtras - cobrado);
  return {
    cobrado,
    devuelto,
    neto: devuelto - cobrado,
    saldoLibre,
  };
}

export function generarConsejoTactico(eventoCierre, saldoLibre = null) {
  if (!eventoCierre) {
    return null;
  }
  const nombre = eventoCierre.nombre || 'el objeto';
  const tope = eventoCierre.topePropio || 0;
  const montoFinal = eventoCierre.montoFinal || 0;
  const diferencia = Math.max(0, montoFinal - tope);
  const margenRecomendado = diferencia > 0 ? diferencia * 2 : 100;
  const nombreCorto = nombre.includes('Amuleto')
    ? 'Amuleto'
    : nombre.split(' ')[0].replace(/^(el|la|los|las)\s+/i, '');

  return {
    titulo: `El ${nombreCorto} se te escapó por ${formatearCreditos(diferencia)} cr.`,
    cuerpo: `Tu tope estaba en ${formatearCreditos(tope)} y cerró en ${formatearCreditos(montoFinal)}. Con ${formatearCreditos(margenRecomendado)} cr más de margen era tuyo${
      saldoLibre === null || saldoLibre === undefined
        ? '.'
        : ` — y tenías ${formatearCreditos(saldoLibre)} libres.`
    }`,
    diferencia,
    margenRecomendado,
  };
}

/**
 * Elige entre tres valores segun como este un tope: alcanzado, en aviso, o con
 * margen. Existe para no repetir el mismo ternario anidado en cada color,
 * ancho de barra y texto de los medidores.
 *
 * @param {{topeAlcanzado: boolean, alerta: boolean}} estado
 */
export function segunTope(estado, critico, aviso, normal) {
  if (estado.topeAlcanzado) {
    return critico;
  }
  if (estado.alerta) {
    return aviso;
  }
  return normal;
}

/**
 * A donde vuelve el boton de atras, segun desde donde se abrio el detalle.
 *
 * La vista y el texto no se corresponden una a una: se vuelve a 'explorar' o a
 * 'lista' segun de donde se venga, pero el boton dice lo mismo en los dos
 * casos, porque para el jugador son la misma pantalla.
 *
 * @param {string} origenVista
 * @returns {{vista: string, texto: string}}
 */
export function destinoDeVuelta(origenVista) {
  if (origenVista === 'mis-subastas') {
    return { vista: 'mis-subastas', texto: '\u2190 Volver a mis subastas' };
  }
  if (origenVista === 'cierre-multiple') {
    return { vista: 'cierre-multiple', texto: '\u2190 Volver a cierre m\u00faltiple' };
  }
  return {
    vista: origenVista === 'explorar' ? 'explorar' : 'lista',
    texto: '\u2190 Volver al listado de subastas',
  };
}

/**
 * Elige entre tres valores segun el signo de un delta (sube, baja, igual).
 */
export function segunDelta(delta, positivo, negativo, cero) {
  if (delta > 0) {
    return positivo;
  }
  if (delta < 0) {
    return negativo;
  }
  return cero;
}

/**
 * UXC-8 — ¿pujas en esta subasta? Solo por lo que dijo el servidor de tu
 * participación (`MiParticipacion`): estar en el listado del mercado no es
 * participar. La pestaña «Mis subastas» contaba el mercado entero como tuyo
 * y avisaba «Has llegado al tope» a quien no había pujado nunca.
 *
 * @param {object} sub
 * @returns {boolean}
 */
export function pujasEn(sub) {
  return Boolean(
    sub && (sub.ganando || sub.superado || (sub.retenido || 0) > 0 || (sub.autoLimite || 0) > 0),
  );
}

/**
 * Los dos topes de 7.7.10 que ve el jugador.
 *
 * UXC-8 — el primero medía «subastas en las que participas: N de 10», y
 * ese tope no existe: `ms-subastas-pujas.yaml` 0.4.0 lo dejó claro (las 10
 * subastas activas son las PUBLICACIONES de un vendedor; pujar ya no emite
 * `LIMITE_SUBASTAS_ACTIVAS`) y el servidor cuenta las publicaciones activas
 * al publicar. Avisar «has llegado al tope» a quien puja en diez subastas era
 * frenarle con una regla inventada. El segundo, las pujas activas, son las
 * tuyas que siguen siendo la oferta vigente (lo que el servidor cuenta).
 *
 * @param {{publicaciones?: number, ganando?: number}} cuentas tus publicaciones
 *   en curso y tus pujas que van ganando
 * @param {object} [config] `maxSubastasSimultaneas` (publicaciones) y
 *   `maxPujasActivas`, de GET /subastas/reglas o su respaldo
 */
export function calcularEstadoTopesConcurrencia(
  { publicaciones = 0, ganando = 0 } = {},
  config = CONFIG_REGLAS,
) {
  const maxPublicaciones = config.maxSubastasSimultaneas || 10;
  const maxPujas = config.maxPujasActivas || 50;
  const nPublicaciones = Number(publicaciones) || 0;
  const nPujasGanando = Number(ganando) || 0;

  const ratioPublicaciones = nPublicaciones / maxPublicaciones;
  const ratioPujas = nPujasGanando / maxPujas;

  const alertaPublicaciones = ratioPublicaciones >= 0.8;
  const alertaPujas = ratioPujas >= 0.8;
  const quedanPublicaciones = maxPublicaciones - nPublicaciones;
  const quedanPujas = maxPujas - nPujasGanando;

  return {
    subastas: {
      actual: nPublicaciones,
      max: maxPublicaciones,
      ratio: ratioPublicaciones,
      alerta: alertaPublicaciones,
      topeAlcanzado: nPublicaciones >= maxPublicaciones,
      pista: segunTope(
        { topeAlcanzado: nPublicaciones >= maxPublicaciones, alerta: alertaPublicaciones },
        'Has llegado al tope: no puedes publicar otra hasta que termine alguna.',
        `Aviso de tope (80%): puedes publicar ${quedanPublicaciones} más.`,
        'Margen de sobra.',
      ),
    },
    pujas: {
      actual: nPujasGanando,
      max: maxPujas,
      ratio: ratioPujas,
      alerta: alertaPujas,
      topeAlcanzado: nPujasGanando >= maxPujas,
      pista: segunTope(
        { topeAlcanzado: nPujasGanando >= maxPujas, alerta: alertaPujas },
        `Has llegado al tope de ${maxPujas} pujas activas: espera a que alguna termine o te superen.`,
        `Aviso de tope (80%): te quedan ${quedanPujas} pujas.`,
        'Margen de sobra.',
      ),
    },
  };
}

/**
 * UXC-8 — de lo guardado de una participación a los campos que pinta la
 * vista. La espera se recalcula con el reloj: guardada en segundos se
 * quedaría congelada en la cifra de cuando se preguntó.
 *
 * @param {{ganando: boolean, superado: boolean, retenido: number, autoLimite: number, esperaHasta: number}} conocida
 * @param {number} [ahora]
 */
export function vistaDeParticipacion(conocida, ahora = Date.now()) {
  return {
    ganando: conocida.ganando,
    superado: conocida.superado,
    retenido: conocida.retenido,
    autoLimite: conocida.autoLimite,
    siguiendo: Boolean(conocida.siguiendo),
    esperaSegundos: Math.max(0, Math.ceil((conocida.esperaHasta - ahora) / 1000)),
    participacionCargada: true,
  };
}

/**
 * B8 — de la ficha del servidor a los campos que pinta la vista.
 *
 * @param {object} ficha `GET /subastas/{id}`
 */
export function vistaDeFicha(ficha) {
  const numero = (v) => (v === null || v === undefined ? null : Number(v));
  const reputacion = ficha.reputacionVendedor;
  return {
    estado: ficha.estado || null,
    pujaMinimaSiguiente: numero(ficha.pujaMinimaSiguiente),
    incrementoMinimo: numero(ficha.incrementoMinimo),
    compraInmediataDisponible: Boolean(ficha.compraInmediataDisponible),
    rivales: numero(ficha.cantidadPujas),
    vendedorApodo: ficha.vendedorApodo || null,
    // UXC-8 — lo que la ficha sabe y el listado no: el producto (para sus
    // opiniones), las visitas y la reputación del vendedor por sus subastas
    // ya terminadas. Null es «no vino», nunca cero.
    productoId: ficha.productoId || null,
    vistas: numero(ficha.vistas),
    reputacionVendedor: reputacion
      ? {
          ventasCompletadas: Number(reputacion.ventasCompletadas) || 0,
          subastasTerminadas: Number(reputacion.subastasTerminadas) || 0,
          cancelaciones: Number(reputacion.cancelaciones) || 0,
        }
      : null,
  };
}

/**
 * UXC-8 — la reputación del vendedor en una frase, con las cifras del
 * servidor tal cual. `tasaDeExito` no se convierte en estrellas: cómo se
 * traduce, si se traduce, lo decide el Product Owner (ms-subastas-listado).
 *
 * @param {{ventasCompletadas: number, subastasTerminadas: number, cancelaciones: number}|null} reputacion
 * @returns {string} vacío si no se sabe
 */
export function textoDeReputacion(reputacion) {
  if (!reputacion) {
    return '';
  }
  const { ventasCompletadas, subastasTerminadas, cancelaciones } = reputacion;
  if (subastasTerminadas === 0) {
    return 'Todavía no ha terminado ninguna subasta.';
  }
  const ventas = `Vendió ${ventasCompletadas} de ${subastasTerminadas} ${subastasTerminadas === 1 ? 'subasta terminada' : 'subastas terminadas'}`;
  if (cancelaciones === 0) {
    return `${ventas}.`;
  }
  return `${ventas} · canceló ${cancelaciones}.`;
}

/**
 * UXC-8 — el estado de una subasta en palabras, con la variante de su
 * distintivo. El color nunca va solo: el texto dice lo mismo.
 */
const ESTADOS_DE_SUBASTA = Object.freeze({
  ACTIVA: { texto: 'En curso', variante: 'info' },
  ADJUDICADA: { texto: 'Adjudicada', variante: 'exito' },
  SIN_ADJUDICACION: { texto: 'Terminó sin pujas', variante: 'neutral' },
  CANCELADA: { texto: 'Cancelada', variante: 'advertencia' },
});

/**
 * @param {string} estado EstadoSubasta del contrato
 * @param {{propia?: boolean}} [opciones] en lo tuyo, «adjudicada» es «vendida»
 * @returns {{texto: string, variante: string}}
 */
export function estadoDeSubasta(estado, { propia = false } = {}) {
  if (propia && estado === 'ADJUDICADA') {
    return { texto: 'Vendida', variante: 'exito' };
  }
  return ESTADOS_DE_SUBASTA[estado] ?? { texto: 'Sin estado', variante: 'neutral' };
}

/** Lo que falta hasta una fecha, en segundos (nunca negativo; null si no hay fecha). */
function segundosHasta(fecha, ahora) {
  const fin = fecha ? new Date(fecha).getTime() : Number.NaN;
  return Number.isNaN(fin) ? null : Math.max(0, Math.round((fin - ahora) / 1000));
}

/** Un decimal del contrato (cadena) como número; null si no vino. */
function cifra(valor) {
  return valor === null || valor === undefined || Number.isNaN(Number(valor))
    ? null
    : Number(valor);
}

/**
 * UXC-8 — una `MiPublicacion` de `GET /mis-subastas/publicadas` como la pinta
 * «Tus publicaciones». Si se puede cancelar y cuánto costaría lo dice el
 * servidor; aquí no se calcula nada.
 *
 * @param {object} p MiPublicacion (ms-subastas-panel.yaml 1.0.0)
 * @param {number} [ahora]
 */
export function vistaDePublicacion(p, ahora = Date.now()) {
  return {
    id: p.subastaId,
    nombre: p.nombreProducto || 'Objeto sin nombre',
    miniaturaUrl: p.miniaturaUrl || null,
    estado: p.estado,
    oferta: cifra(p.ofertaVigente),
    precioInicial: cifra(p.precioInicial),
    pujas: Number(p.cantidadPujas) || 0,
    segundosRestantes: p.estado === 'ACTIVA' ? segundosHasta(p.fechaFin, ahora) : 0,
    fechaFin: p.fechaFin || null,
    cerradaEn: p.cerradaEn || null,
    vistas: cifra(p.vistas),
    comision: cifra(p.comisionCobrada),
    penalizacion: cifra(p.penalizacionCobrada),
    cancelable: p.cancelable === true,
    penalizacionSiCancela: cifra(p.penalizacionSiCancela),
  };
}

/**
 * UXC-8 — una `SubastaSeguida` de `GET /mis-subastas/seguimiento`.
 *
 * @param {object} s SubastaSeguida (ms-subastas-panel.yaml 1.0.0)
 * @param {number} [ahora]
 */
export function vistaDeSeguida(s, ahora = Date.now()) {
  return {
    id: s.subastaId,
    nombre: s.nombreProducto || 'Objeto sin nombre',
    miniaturaUrl: s.miniaturaUrl || null,
    estado: s.estado,
    oferta: cifra(s.ofertaVigente),
    compraInmediata: cifra(s.precioCompraInmediata),
    pujas: Number(s.cantidadPujas) || 0,
    segundosRestantes: s.estado === 'ACTIVA' ? segundosHasta(s.fechaFin, ahora) : 0,
    fechaFin: s.fechaFin || null,
    seguidaDesde: s.seguidaDesde || null,
  };
}

/**
 * UXC-8 — el estado de tu participación (`Participacion.estado`, 0.4.0) en
 * palabras, con su distintivo.
 */
const RESULTADOS_DE_PUJA = Object.freeze({
  GANANDO: { texto: 'Vas ganando', variante: 'exito' },
  SUPERADA: { texto: 'Te superaron', variante: 'error' },
  GANADA: { texto: 'La ganaste', variante: 'exito' },
  PERDIDA: { texto: 'Se la llevó otro jugador', variante: 'neutral' },
  CERRADA: { texto: 'Cancelada', variante: 'advertencia' },
  AUTOMATICA: { texto: 'Automática preparada', variante: 'info' },
});

/** @param {string} estado */
export function resultadoDePuja(estado) {
  return RESULTADOS_DE_PUJA[estado] ?? { texto: 'Sin estado', variante: 'neutral' };
}

/**
 * UXC-8 — una `Participacion` de `GET /mis-pujas` (ms-subastas-pujas.yaml
 * 0.4.0): «Mis pujas», también las de subastas ya cerradas.
 *
 * @param {object} p Participacion
 * @param {number} [ahora]
 */
export function vistaDeMiPuja(p, ahora = Date.now()) {
  const abierta = p.estadoSubasta === 'ACTIVA';
  return {
    id: p.subastaId,
    nombre: p.nombreProducto || 'Objeto sin nombre',
    miniaturaUrl: p.miniaturaUrl || null,
    estadoSubasta: p.estadoSubasta,
    estado: p.estado,
    tuMejorPuja: cifra(p.tuMejorPuja),
    oferta: cifra(p.ofertaVigente),
    pujas: Number(p.cantidadPujas) || 0,
    fechaFin: p.fechaFin || null,
    segundosRestantes: abierta ? segundosHasta(p.fechaFin, ahora) : 0,
    ultimaPujaEn: p.ultimaPujaEn || null,
  };
}

/** Cómo se llama cada movimiento del historial y si suma o resta. */
const MOVIMIENTOS = Object.freeze({
  COMPRA: { texto: 'Compra', entra: false },
  VENTA: { texto: 'Venta', entra: true },
  COMISION: { texto: 'Comisión', entra: false },
  PENALIZACION: { texto: 'Penalización', entra: false },
});

/**
 * UXC-8 — el `Historial` de `GET /mis-subastas/historial`, con los
 * movimientos del más reciente al más antiguo. Qué suma y qué resta lo dice
 * el contrato: la venta es la ganancia obtenida; compra, comisión y
 * penalización son gasto.
 *
 * @param {object} h Historial (ms-subastas-panel.yaml 1.0.0)
 */
export function vistaDeHistorial(h) {
  const movimientos = (Array.isArray(h?.movimientos) ? h.movimientos : [])
    .map((m) => ({
      tipo: m.tipo,
      texto: MOVIMIENTOS[m.tipo]?.texto ?? 'Movimiento',
      entra: MOVIMIENTOS[m.tipo]?.entra ?? false,
      id: m.subastaId,
      nombre: m.nombreProducto || 'Objeto sin nombre',
      monto: cifra(m.monto),
      fecha: m.fecha || null,
    }))
    .sort((a, b) => new Date(b.fecha ?? 0).getTime() - new Date(a.fecha ?? 0).getTime());
  return {
    movimientos,
    totalGanado: cifra(h?.totalGanado),
    totalGastado: cifra(h?.totalGastado),
    comisionesPagadas: cifra(h?.comisionesPagadas),
    balance: cifra(h?.balance),
  };
}

/**
 * Guarda un texto como archivo en el equipo de quien mira.
 *
 * El CSV lleva delante la marca de orden de bytes: sin ella, la hoja de
 * cálculo más usada lo abre como Latin-1 y «Penalización» sale destrozado. El
 * contenido es el que dio el servidor, sin tocar.
 *
 * @param {string} texto
 * @param {string} nombre
 * @param {{documento?: Document, url?: typeof URL}} [entorno]
 */
export function descargarTexto(
  texto,
  nombre,
  { documento = globalThis.document, url = globalThis.URL } = {},
) {
  if (!documento?.body || typeof url?.createObjectURL !== 'function') {
    throw new Error('Tu navegador no deja guardar el archivo desde aquí.');
  }
  const direccion = url.createObjectURL(
    new Blob(['﻿', String(texto ?? '')], { type: 'text/csv;charset=utf-8' }),
  );
  const enlace = documento.createElement('a');
  enlace.href = direccion;
  enlace.download = nombre;
  enlace.hidden = true;
  documento.body.append(enlace);
  enlace.click();
  enlace.remove();
  setTimeout(() => url.revokeObjectURL?.(direccion), 0);
}

/**
 * Una cifra con signo para el historial: «+1.200 cr», «−350 cr».
 *
 * @param {number|null} monto
 * @param {boolean} entra
 */
export function montoConSigno(monto, entra) {
  if (monto === null || monto === undefined) {
    return '—';
  }
  if (monto === 0) {
    return '0 cr';
  }
  return `${entra ? '+' : '−'}${formatearCreditos(Math.abs(monto))} cr`;
}

/**
 * La ficha guardada de una subasta, sobre la subasta que se pinta.
 *
 * @param {object} sub
 * @param {ReturnType<typeof vistaDeFicha>|undefined} guardada
 */
function aplicarFicha(sub, guardada) {
  if (!sub || !guardada) {
    return;
  }
  sub.estado = guardada.estado || sub.estado;
  sub.pujaMinimaSiguiente = guardada.pujaMinimaSiguiente;
  sub.incrementoMinimo = guardada.incrementoMinimo;
  sub.compraInmediataDisponible = guardada.compraInmediataDisponible;
  sub.rivales = guardada.rivales ?? sub.rivales;
  sub.vendedorApodo = guardada.vendedorApodo;
  sub.productoId = guardada.productoId ?? sub.productoId ?? null;
  sub.vistas = guardada.vistas;
  sub.reputacionVendedor = guardada.reputacionVendedor;
  sub.fichaCargada = true;
}

/**
 * El historial guardado de una subasta, sobre la subasta que se pinta.
 *
 * @param {object} sub
 * @param {{fallido: boolean, lista: object[]}|undefined} guardado
 */
function aplicarHistorial(sub, guardado) {
  if (!sub || !guardado) {
    return;
  }
  sub.historial = guardado.lista;
  sub.historialCargado = !guardado.fallido;
  sub.historialFallido = guardado.fallido;
}

// =========================================================================
// Controlador y renderizador interactivo DOM
// =========================================================================

export class ControladorSubastas {
  constructor({
    contenedor,
    // UXC-8 — sin datos de ejemplo por omisión: el banco de pruebas lo pide
    // quien lo necesita (pujas.test.js), nunca la pantalla.
    subastas = [],
    heroes = [],
    config = CONFIG_REGLAS,
    eventosCierre = [],
    api = null,
    subastaInicialId = null,
    // UXC-8 — `?accion=comprar`: llegar desde la vitrina con la compra
    // inmediata ya pedida. Abre la confirmación; comprar sigue exigiendo
    // confirmarla (el contrato rechaza `confirmado: false`).
    accionInicial = null,
    urlCanal = null,
    conectarCanal = conectarStomp,
    // R9.6 — de donde sale el JWT que acredita el CONNECT del canal. Misma
    // clave que usa pujas-api.js para las llamadas REST: la sesion es una
    // sola. Inyectable para que las pruebas no dependan de sessionStorage.
    leerToken = () => globalThis.sessionStorage?.getItem(CLAVE_TOKEN_SESION) || null,
    // B8 — quien mira, por su `uid` estable (ADR-002), para saber si una
    // subasta es suya (cancelar solo se ofrece al vendedor). Sale del mismo
    // token; el servidor vuelve a comprobarlo.
    leerUid = null,
    // FI-R11 — inyectables para que las pruebas no esperen treinta segundos de
    // verdad ni dependan de temporizadores reales.
    esperas = ESPERAS_DE_RECONEXION,
    esperar = (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
    // UXC-9 — la consulta periódica que prometía la píldora («las pujas
    // pueden tardar unos segundos») y que no existía: sin canal, el listado
    // no se movía hasta que el jugador hacía algo. Null la apaga.
    sondeoMs = 5000,
    // UXC-8 — dónde van las opiniones del objeto (fuera del contenedor que se
    // repinta: un hilo con un comentario a medio escribir no puede
    // rehacerse cada cinco segundos) y quién las monta. Sin zona, no hay.
    zonaOpiniones = null,
    montarOpiniones = montarHiloDeComentarios,
    // UXC-8 — la confirmación de cancelar desde «Tus publicaciones».
    confirmarAccion = confirmar,
  } = {}) {
    // Subasta que hay que abrir en detalle nada mas cargar. Viene de ?id= en la
    // URL: es la forma de que el listado de HU-SUB-011 entregue una subasta
    // concreta a esta pantalla sin que las dos compartan estado.
    this.subastaInicialId = subastaInicialId;
    this.accionInicial = accionInicial;
    // UXC-8 — lo que el servidor dijo de tu participación, por subasta. El
    // listado se relee entero con cada cambio y sin esto «Mis subastas»
    // olvidaría dónde pujas a la primera recarga.
    this.participaciones = new Map();
    // Y el historial de cada subasta abierta en detalle: sin guardarlo, cada
    // relectura del listado (el sondeo, cada 5 s sin canal) lo borraba y lo
    // volvía a pedir, y la lista parpadeaba.
    this.historiales = new Map();
    // B8 — y la ficha (GET /subastas/{id}): puja mínima exacta, incremento de
    // la subasta, si la compra inmediata sigue y el apodo del vendedor.
    this.fichas = new Map();
    this.participacionesRevisadas = false;
    this.consultandoParticipaciones = false;
    // UXC-8 — la subasta que acabas de comprar: sale del listado de abiertas
    // en cuanto se adjudica, y sin guardarla el «¡Es tuya!» no tenía dónde
    // pintarse (la compra salía bien y la pantalla volvía al listado muda).
    this.subastaCerrada = null;
    this.sondeoMs = sondeoMs;
    this.sondeoId = null;
    // Sin api, el controlador funciona con los datos de ejemplo: es como lo
    // ejercitan las pruebas unitarias, que no deben depender de que haya un
    // servidor levantado. Con api, manda el servidor.
    this.api = api;
    // Lo que el jugador tiene en juego en total. Null mientras no se sepa: la
    // pantalla lo muestra como no disponible en vez de poner un cero, que se
    // leeria como "no tienes nada retenido".
    this.resumen = null;
    this.enviando = false;
    this.contenedor = contenedor;
    this.subastas = JSON.parse(JSON.stringify(subastas));
    this.heroes = JSON.parse(JSON.stringify(heroes));
    this.config = Object.assign({}, CONFIG_REGLAS, config);
    this.eventosCierre = JSON.parse(JSON.stringify(eventosCierre));
    // FI-R1 — sin heroes no hay heroe activo. Antes caia en 'kaelen', el id
    // del primer heroe del banco de pruebas, aunque no se hubiera cargado
    // ninguno: `getHeroeActivo()` devolvia undefined y la ficha comparaba
    // contra la nada diciendo que todo cuadraba.
    this.heroeId = this.heroes[0]?.id || null;
    this.vista = 'lista'; // 'lista' | 'explorar' | 'mis-subastas' | 'detalle' | 'cierre-multiple'
    this.origenVista = 'explorar';
    this.subastaActivaId = null;
    this.confirmandoCompra = false;
    this.resultadoCierre = null;
    this.montoPersonalizado = null;
    this.limiteAutoPersonalizado = null;
    this.estadoDatos = 'exito'; // 'carga' | 'exito' | 'vacio' | 'error'
    this.mensajeError = null;
    this.intervalId = null;
    this.avisoCruzado = null; // { id, nombre, oferta, rival, segundosRestantes }
    // Si ya se movio el foco al dialogo abierto. Evita robarselo al usuario en
    // cada repintado mientras el modal sigue en pantalla.
    this.modalEnfocado = false;

    // Canal en vivo (HU-SUB-011 lo publica en /topic/subastas/listado). Es un
    // ANADIDO al sondeo, no un sustituto: el riesgo #7 del acta exige
    // degradacion controlada a consulta periodica si el tiempo real se cae.
    this.urlCanal = urlCanal;
    this.conectarCanal = conectarCanal;
    this.leerToken = leerToken;
    this.canal = null;
    // FI-R11 — sin canal pedido, el estado es «sin conexion» y la pildora no se
    // pinta: una pantalla que no quiere tiempo real no tiene por que disculparse
    // por no tenerlo. Las pruebas unitarias caen aqui.
    this.estadoCanal = urlCanal ? ESTADO_CANAL.CONECTANDO : ESTADO_CANAL.SIN_CONEXION;
    this.esperas = esperas;
    this.esperar = esperar;
    this.leerUid = leerUid || (() => cuerpoDelToken(this.leerToken?.())?.uid || null);
    // B8 — las reglas vigentes segun el servidor (GET /subastas/reglas). Null
    // mientras no lleguen: la pantalla usa el respaldo de CONFIG_REGLAS, que
    // no tiene incremento.
    this.reglas = null;
    // B8 — «Productos pendientes de recoger» (7.7.9), del servidor.
    this.pendientes = [];
    this.confirmandoCancelacion = false;
    // UXC-8 — el panel personal (ms-subastas-panel.yaml): lo que publicaste
    // en cualquier estado, lo que sigues y tu historial. Null es «todavía no
    // llegó»; `fallos` dice qué parte no se pudo traer, para ofrecer
    // reintentarla sin tapar las demás.
    this.panel = {
      pujas: null,
      publicadas: null,
      seguidas: null,
      historial: null,
      fallos: [],
      cargando: false,
    };
    this.verTodo = { pujas: false, publicadas: false, historial: false };
    this.exportando = false;
    this.zonaOpiniones = zonaOpiniones;
    this.montarOpiniones = montarOpiniones;
    this.confirmarAccion = confirmarAccion;
    /** El hilo montado y de qué producto es: se monta una vez por producto. */
    this.opiniones = null;
    /** Promesa del ciclo de reconexion en curso: nunca dos a la vez. */
    this.reconexion = null;
    this.vivo = true;
  }

  iniciar() {
    if (this.api) {
      this.estadoDatos = 'carga';
      this.render();
      // Sin await: si el canal tarda o no levanta, la pantalla ya funciona con
      // el sondeo. Encadenarlo aqui retrasaria el primer pintado por algo que
      // es opcional.
      //
      // FI-R11 — y si no abre, se reintenta. Antes se devolvia null y ahi
      // moria: el canal no volvia ni cuando el servidor si.
      this.abrirCanalEnVivo().then((canal) => {
        if (!canal && this.vivo && this.urlCanal) {
          this.reconectar();
        }
      });
      this.iniciarSondeo();
      return this.recargar().then(() => this.consultarParticipaciones());
    }
    this.iniciarTemporizador();
    this.render();
    return Promise.resolve();
  }

  /**
   * Relee el listado del servidor. Se llama al arrancar y despues de cada
   * operacion que cambia algo, en vez de tocar el estado local: el servidor es
   * quien sabe cual es la oferta vigente, y adivinarla en el cliente es
   * exactamente como se pintan pantallas que mienten.
   */
  async recargar() {
    if (!this.api) {
      return;
    }
    try {
      const [subastas, resumen, reglas, pendientes] = await Promise.all([
        this.api.listar(),
        // Si falla, se sigue sin resumen en vez de tumbar el listado entero.
        this.intentar(() => this.api.miResumen()),
        // B8 — las reglas y los pendientes, igual: opcionales. Sin reglas se
        // queda el respaldo (sin incremento); sin pendientes, la lista vacia.
        this.intentar(() => this.api.reglas()),
        this.leerToken?.() ? this.intentar(() => this.api.pendientes()) : null,
      ]);
      this.subastas = subastas;
      this.resumen = resumen;
      this.reaplicarParticipaciones();
      this.aplicarReglas(reglas);
      this.pendientes = Array.isArray(pendientes) ? pendientes : [];
      this.estadoDatos = subastas.length ? 'exito' : 'vacio';
      this.mensajeError = null;

      if (this.subastaInicialId) {
        // UXC-8 — se busca en las páginas del listado (16 por página) hasta
        // dar con ella o agotarlas: el detalle se pinta con la forma del
        // listado, y la ficha (GET /subastas/{id}, B8) lo completa después.
        const pedida =
          subastas.find((s) => s.id === this.subastaInicialId) ??
          (await this.buscarEnOtrasPaginas(this.subastaInicialId));
        this.subastaInicialId = null;
        if (pedida) {
          if (!this.subastas.some((s) => s.id === pedida.id)) {
            this.subastas = [...this.subastas, pedida];
            this.estadoDatos = 'exito';
          }
          this.origenVista = 'explorar';
          this.subastaActivaId = pedida.id;
          this.vista = 'detalle';
        } else {
          // Llego un enlace a una subasta que ya no esta en el listado: se
          // cerro o se adjudico. Mejor decirlo que abrir un detalle vacio.
          this.accionInicial = null;
          this.mensajeError =
            'Esa subasta ya no está disponible: puede que haya terminado o que alguien la comprara. Aquí tienes las que siguen en curso.';
        }
      }
      // UXC-8 — `this.subastas` y no `subastas`: una subasta abierta desde un
      // enlace puede venir de otra página del listado, y comparar con la
      // primera la sacaba del detalle en la misma recarga que la abría.
      if (
        this.subastaActivaId &&
        !this.subastas.some((s) => s.id === this.subastaActivaId) &&
        this.subastaCerrada?.id !== this.subastaActivaId
      ) {
        // La subasta que se estaba mirando se cerro o se adjudico mientras
        // tanto: volver a la lista es mejor que dejar una pantalla de detalle
        // sobre algo que ya no existe.
        this.vista = this.origenVista || 'explorar';
        this.subastaActivaId = null;
      }
    } catch (fallo) {
      this.estadoDatos = 'error';
      this.mensajeError = textoDeError(fallo, 'No se pudo cargar el listado de subastas.');
    }
    this.iniciarTemporizador();
    this.render();

    // Si al terminar la recarga estamos en un detalle, hay que traer tambien
    // lo que solo sabe el detalle. Se hace aqui y no solo en abrirDetalle
    // porque hay dos caminos mas que dejan la vista en 'detalle' sin pasar por
    // ahi: llegar con ?id= desde el listado, y refrescar despues de pujar.
    if (this.vista === 'detalle' && this.subastaActivaId) {
      await this.cargarDetalle(this.subastaActivaId);
      this.atenderAccionInicial();
    }
  }

  /**
   * UXC-8 — `?accion=comprar`: se abre la confirmación de la compra
   * inmediata, una sola vez y solo si la subasta la admite y no es tuya.
   */
  atenderAccionInicial() {
    const accion = this.accionInicial;
    this.accionInicial = null;
    const sub = this.getSubastaActiva();
    if (
      accion === 'comprar' &&
      sub &&
      sub.compraInmediata !== null &&
      sub.compraInmediata !== undefined &&
      !this.esMiSubasta(sub) &&
      sub.segundosRestantes > 0
    ) {
      this.solicitarCompraInmediata();
    }
  }

  /**
   * Ejecuta una operacion contra el servidor y refresca. El mensaje que ve el
   * jugador sale del campo 'motivo' del problem+json, no del texto libre: ese
   * texto es para depurar.
   */
  /**
   * Trae del servidor lo que solo se sabe de una subasta concreta: su historial
   * y la situacion del jugador que mira. Antes esto se suponia, y lo que se
   * mostraba era siempre "no vas ganando" y "sin puja automatica", aunque
   * fuera falso.
   *
   * Si falla, se deja el detalle con lo que ya se sabe del listado en vez de
   * romper la pantalla: poder pujar es mas importante que ver el historial.
   */
  async cargarDetalle(id) {
    if (!this.api) {
      return;
    }
    const sub = this.subastas.find((s) => s.id === id);
    if (!sub) {
      return;
    }

    // Cada consulta por su cuenta y sin dejar escapar el fallo: el historial es
    // publico y la participacion necesita sesion, asi que una puede fallar sin
    // la otra. Y si fallan las dos, el detalle se pinta con lo del listado —
    // poder pujar importa mas que ver el historial.
    const [historial, participacion, ficha] = await Promise.all([
      this.intentar(() => this.api.historial(id)),
      this.intentar(() => this.api.miParticipacion(id)),
      // B8 — la ficha dice la puja minima exacta y el incremento de ESTA
      // subasta, que el listado no trae.
      this.intentar(() => this.api.ficha(id)),
    ]);

    if (ficha) {
      // UXC-8 — se guarda: el listado se relee entero (el sondeo, el canal) y
      // sin esto el mínimo exacto y la compra agotada se perdían en cada
      // relectura hasta volver a pedir la ficha.
      this.fichas.set(id, vistaDeFicha(ficha));
    }

    if (historial) {
      this.historiales.set(id, {
        fallido: false,
        lista: historial.map((p) => ({
          // UXC-8 — 0.4.0 trae el postor anonimizado a medias («a***s»,
          // 7.7.11); sin él (pujas anteriores), «Otro jugador».
          apodo: p.esTuya ? 'Tú' : p.postor || 'Otro jugador',
          monto: Number(p.monto),
          tipo: p.tipo === 'AUTOMATICA' ? 'Automática' : 'Manual',
          cuando: p.creadaEn,
          esTu: p.esTuya,
        })),
      });
    } else if (!this.historiales.get(id)?.lista.length) {
      this.historiales.set(id, { fallido: true, lista: [] });
    }
    // La subasta de ahora, no la de antes de esperar: una recarga pudo
    // cambiar el objeto mientras llegaban las respuestas.
    const actual = this.subastas.find((s) => s.id === id) ?? sub;
    aplicarFicha(actual, this.fichas.get(id));
    aplicarHistorial(actual, this.historiales.get(id));

    if (participacion) {
      this.aplicarParticipacion(id, participacion);
    }

    this.render();
  }

  /**
   * UXC-8 — guarda lo que dijo el servidor de tu participación en una
   * subasta y lo pinta en la subasta que haya ahora en el listado (puede ser
   * otro objeto si hubo una recarga mientras tanto).
   *
   * Si ibas ganando y ahora te superaron, y no la estás mirando, sale el aviso
   * cruzado: hasta ahora solo existía en el camino de las pruebas.
   *
   * @param {string} id
   * @param {object} participacion `MiParticipacion` del contrato
   */
  aplicarParticipacion(id, participacion) {
    const antes = this.participaciones.get(id);
    const espera = Number(participacion.segundosParaVolverAPujar || 0);
    const conocida = {
      ganando: Boolean(participacion.vasGanando),
      superado: Boolean(participacion.teSuperaron),
      // `creditosRetenidos` es el nombre del contrato (MiParticipacion);
      // `retenidoAqui` lo mandaba una versión anterior del servicio.
      retenido: Number(participacion.creditosRetenidos ?? participacion.retenidoAqui ?? 0),
      autoLimite: participacion.automaticaActiva ? Number(participacion.limiteAutomatico || 0) : 0,
      // B8 — si la sigues (lista de seguimiento, 7.7.9).
      siguiendo: Boolean(participacion.siguiendo),
      // La espera corre: se guarda cuándo termina, no cuántos segundos faltaban.
      esperaHasta: espera > 0 ? Date.now() + espera * 1000 : 0,
    };
    this.participaciones.set(id, conocida);
    const sub = this.subastas.find((s) => s.id === id);
    if (sub) {
      Object.assign(sub, vistaDeParticipacion(conocida));
    }
    const laEstoyMirando = this.vista === 'detalle' && this.subastaActivaId === id;
    if (sub && antes?.ganando && conocida.superado && !laEstoyMirando) {
      this.avisoCruzado = {
        id,
        nombre: sub.nombre,
        oferta: sub.oferta,
        rival: sub.rival || 'otro jugador',
        segundosRestantes: sub.segundosRestantes,
      };
    }
    return conocida;
  }

  /** Vuelve a poner lo que se sabía (participación e historial) tras releer el listado. */
  reaplicarParticipaciones() {
    for (const sub of this.subastas) {
      const conocida = this.participaciones.get(sub.id);
      if (conocida) {
        Object.assign(sub, vistaDeParticipacion(conocida));
      }
      aplicarFicha(sub, this.fichas.get(sub.id));
      aplicarHistorial(sub, this.historiales.get(sub.id));
    }
  }

  /**
   * UXC-8 — pregunta tu participación en las subastas abiertas cargadas que
   * todavía no se sabe. El contrato no publica «dónde pujo» como lista, así
   * que es la única forma de que «Mis subastas» diga la verdad. Cuatro
   * consultas a la vez como mucho y solo con sesión.
   *
   * @param {{tambienGanando?: boolean}} [opciones] volver a preguntar también
   *   por las que ibas ganando (el sondeo, para enterarse de que te superaron)
   */
  async consultarParticipaciones({ tambienGanando = false } = {}) {
    if (!this.api || !this.leerToken?.() || this.consultandoParticipaciones) {
      return;
    }
    const cola = this.subastas
      .filter((s) => {
        const conocida = this.participaciones.get(s.id);
        return !conocida || (tambienGanando && conocida.ganando);
      })
      .map((s) => s.id);
    if (cola.length === 0) {
      this.marcarParticipaciones({ consultando: false, revisadas: true });
      return;
    }
    this.marcarParticipaciones({ consultando: true, revisadas: this.participacionesRevisadas });
    if (this.vista === 'mis-subastas') {
      this.render();
    }
    const trabajador = async () => {
      while (cola.length > 0) {
        const id = cola.shift();
        const participacion = await this.intentar(() => this.api.miParticipacion(id));
        if (participacion) {
          this.aplicarParticipacion(id, participacion);
        }
      }
    };
    await Promise.all(Array.from({ length: Math.min(4, cola.length) }, trabajador));
    this.marcarParticipaciones({ consultando: false, revisadas: true });
    this.render();
  }

  /** Un solo sitio para las dos marcas (ESLint: require-atomic-updates). */
  marcarParticipaciones({ consultando, revisadas }) {
    this.consultandoParticipaciones = consultando;
    this.participacionesRevisadas = revisadas;
  }

  /**
   * UXC-9 — degradación controlada (riesgo #7): sin canal en vivo se relee
   * el mercado cada `sondeoMs`. No con la pestaña oculta, ni a mitad de una
   * operación, ni con la confirmación de compra abierta.
   */
  iniciarSondeo() {
    if (!this.sondeoMs || this.sondeoId) {
      return;
    }
    this.sondeoId = setInterval(() => this.sondear(), this.sondeoMs);
  }

  pararSondeo() {
    if (this.sondeoId) {
      clearInterval(this.sondeoId);
      this.sondeoId = null;
    }
  }

  sondear() {
    const oculta = globalThis.document?.visibilityState === 'hidden';
    if (
      !this.vivo ||
      !this.api ||
      this.enviando ||
      this.confirmandoCompra ||
      oculta ||
      this.estadoCanal === ESTADO_CANAL.ESTABLE
    ) {
      return null;
    }
    return this.recargar().then(() => this.consultarParticipaciones({ tambienGanando: true }));
  }

  /**
   * UXC-8 — busca una subasta por su id en las páginas siguientes del
   * listado (la primera ya está cargada). Se para al primer hueco o al tope.
   *
   * @param {string} id
   * @param {{paginasMaximas?: number}} [opciones]
   * @returns {Promise<object|null>}
   */
  async buscarEnOtrasPaginas(id, { paginasMaximas = 8 } = {}) {
    for (let pagina = 1; pagina < paginasMaximas; pagina += 1) {
      const lote = await this.intentar(() => this.api.listar({ page: pagina }));
      if (!Array.isArray(lote) || lote.length === 0) {
        return null;
      }
      const encontrada = lote.find((s) => s.id === id);
      if (encontrada) {
        return encontrada;
      }
      if (lote.length < 16) {
        return null;
      }
    }
    return null;
  }

  /**
   * Ejecuta una consulta opcional y devuelve null si falla, sea por red, por
   * sesion o porque el metodo ni siquiera exista. Envuelve tambien la llamada
   * para que un fallo sincrono no se escape como rechazo sin capturar.
   */
  async intentar(consulta) {
    try {
      return await consulta();
    } catch {
      return null;
    }
  }

  /**
   * B8 — las reglas de 7.7 que dice el servidor sustituyen a las escritas a
   * mano. El incremento solo si esta configurado: si no, null y la pantalla
   * dice que es una decision del PO pendiente, sin inventar una cifra.
   */
  aplicarReglas(reglas) {
    if (!reglas) {
      return;
    }
    this.reglas = reglas;
    this.config.incrementoMinimo =
      reglas.incrementoMinimoConfigurado && reglas.incrementoMinimo !== null
        ? Number(reglas.incrementoMinimo)
        : null;
    if (Number.isFinite(Number(reglas.intervaloMinimoSegundos))) {
      this.config.intervaloSegundos = Number(reglas.intervaloMinimoSegundos);
    }
    if (Number.isFinite(Number(reglas.maxPujasActivasPorJugador))) {
      this.config.maxPujasActivas = Number(reglas.maxPujasActivasPorJugador);
    }
    // UXC-8 — el tope de publicaciones activas (7.7.10), también del servidor.
    if (Number.isFinite(Number(reglas.maxSubastasActivasPorJugador))) {
      this.config.maxSubastasSimultaneas = Number(reglas.maxSubastasActivasPorJugador);
    }
  }

  /** Si la subasta la publico quien mira (su `uid` es el vendedor). */
  esMiSubasta(sub) {
    // UXC-8 — el listado ya la marca (`esPropia`, por `vendedorId`) sin
    // pintar el identificador; si no, se compara el vendedor con quien mira.
    if (sub?.esPropia) {
      return true;
    }
    const uid = this.leerUid?.();
    const vendedor = sub?.vendedorId ?? sub?.vendedor;
    return Boolean(uid && vendedor && vendedor === uid);
  }

  /**
   * B8 — 7.7.10: si esta subasta se puede cancelar ahora y, si no, por que.
   * Solo sin pujas y fuera de las ultimas horas que digan las reglas (6). El
   * servidor lo vuelve a comprobar al cancelar; esto evita ofrecer un boton
   * que siempre va a fallar.
   *
   * @returns {{cancelable: boolean, motivo: string|null}}
   */
  estadoDeCancelacion(sub) {
    const horas = Number(this.reglas?.cancelacionProhibidaUltimasHoras ?? 6);
    if (Number(sub?.rivales) > 0) {
      return { cancelable: false, motivo: mensajePara('CANCELACION_CON_PUJAS') };
    }
    if (Number(sub?.segundosRestantes) <= horas * 3600) {
      return { cancelable: false, motivo: mensajePara('CANCELACION_FUERA_DE_PLAZO') };
    }
    return { cancelable: true, motivo: null };
  }

  /** B8 — el boton de cancelar, solo para el vendedor y mientras la subasta siga abierta. */
  generarHtmlCancelar(sub, { conSesion, cerrada }) {
    if (!conSesion || cerrada || !this.esMiSubasta(sub)) {
      return '';
    }
    const { cancelable, motivo } = this.estadoDeCancelacion(sub);
    if (cancelable) {
      return '<button type="button" class="btn btn-peligro-sm" id="btn-cancelar-subasta">Cancelar subasta</button>';
    }
    return `<button type="button" class="btn btn-peligro-sm" id="btn-cancelar-subasta" disabled aria-describedby="motivo-no-cancelable">Cancelar subasta</button>
          <span class="texto-pista" id="motivo-no-cancelable">${esc(motivo)}</span>`;
  }

  /** B8 — lista de seguimiento (7.7.9): seguir o dejar de seguir la subasta abierta. */
  alternarSeguimiento() {
    const sub = this.getSubastaActiva();
    if (!this.api || !sub) {
      return Promise.resolve(false);
    }
    const siguiendo = Boolean(sub.siguiendo);
    return this.ejecutarContraElServidor(() =>
      siguiendo ? this.api.dejarDeSeguir(sub.id) : this.api.seguir(sub.id),
    );
  }

  /** B8 — 7.7.10: cancelar es con confirmacion, porque cobra la penalizacion. */
  solicitarCancelacion() {
    this.limpiarError();
    this.confirmandoCancelacion = true;
    this.render();
  }

  descartarCancelacion() {
    this.confirmandoCancelacion = false;
    this.render();
  }

  confirmarCancelacion() {
    const sub = this.getSubastaActiva();
    this.confirmandoCancelacion = false;
    if (!this.api || !sub) {
      this.render();
      return Promise.resolve(false);
    }
    return this.ejecutarContraElServidor(() => this.api.cancelar(sub.id));
  }

  /** B8 — recoger un producto ganado (7.7.9). */
  recogerPendiente(subastaId) {
    if (!this.api || !subastaId) {
      return Promise.resolve(false);
    }
    return this.ejecutarContraElServidor(() => this.api.recoger(subastaId));
  }

  /** B8 — «Recoger todo»: lo que falle sigue pendiente, y se dice. */
  recogerTodosLosPendientes() {
    if (!this.api) {
      return Promise.resolve(false);
    }
    return this.ejecutarContraElServidor(async () => {
      const resultado = await this.api.recogerTodo();
      if (resultado?.fallidos?.length) {
        throw new Error(
          `No se pudieron recoger ${resultado.fallidos.length} producto(s); siguen pendientes. Vuelve a intentarlo en un momento.`,
        );
      }
    });
  }

  // ------------------------------------------------ UXC-8 · panel personal

  /**
   * Trae lo que publicaste (en cualquier estado), lo que sigues y tu
   * historial (`ms-subastas-panel.yaml`). Cada parte llega o falla por su
   * cuenta: si el historial no responde, tus publicaciones se ven igual.
   * Si hay dos lecturas a la vez, manda la última. Nunca rechaza.
   */
  async cargarPanel() {
    if (!this.api || !this.leerToken?.()) {
      return;
    }
    const partes = [
      ['pujas', 'misPujas'],
      ['publicadas', 'misPublicaciones'],
      ['seguidas', 'misSeguidas'],
      ['historial', 'miHistorial'],
    ].filter(([, metodo]) => typeof this.api[metodo] === 'function');
    if (!partes.length) {
      return;
    }
    const version = this.empezarLecturaDelPanel();
    const resultados = await Promise.all(
      partes.map(async ([nombre, metodo]) => {
        try {
          return { nombre, datos: await this.api[metodo](), fallo: false };
        } catch {
          return { nombre, datos: null, fallo: true };
        }
      }),
    );
    this.guardarPanel(resultados, version);
  }

  /** @returns {number} la versión de esta lectura */
  empezarLecturaDelPanel() {
    this.versionDelPanel = (this.versionDelPanel ?? 0) + 1;
    this.panel = { ...this.panel, cargando: true };
    // «Trayendo…» donde va cada parte, en vez de un hueco hasta que llegue.
    if (this.vista === 'mis-subastas') {
      this.render();
    }
    return this.versionDelPanel;
  }

  guardarPanel(resultados, version) {
    if (version !== this.versionDelPanel) {
      return;
    }
    const ahora = Date.now();
    const panel = { ...this.panel, cargando: false, fallos: [] };
    for (const { nombre, datos, fallo } of resultados) {
      // Un fallo no borra lo que ya se sabía: se dice que no se pudo
      // actualizar y se ofrece reintentarlo.
      if (fallo) {
        panel.fallos.push(nombre);
      } else if (nombre === 'historial') {
        panel.historial = vistaDeHistorial(datos);
      } else {
        const aVista = {
          pujas: vistaDeMiPuja,
          publicadas: vistaDePublicacion,
          seguidas: vistaDeSeguida,
        }[nombre];
        panel[nombre] = (Array.isArray(datos) ? datos : []).map((d) => aVista(d, ahora));
      }
    }
    this.panel = panel;
    if (this.vista === 'mis-subastas') {
      this.render();
    }
  }

  /**
   * Abre una subasta desde el panel. Si no está en la página cargada del
   * listado, se busca en las siguientes; si ya no está abierta, se dice.
   *
   * @param {string} id
   */
  async abrirSubastaDelPanel(id) {
    if (!id) {
      return;
    }
    if (this.subastas.some((s) => s.id === id)) {
      this.abrirDetalle(id);
      return;
    }
    const encontrada = this.api ? await this.buscarEnOtrasPaginas(id) : null;
    if (encontrada) {
      this.agregarSubasta(encontrada);
      this.abrirDetalle(id);
      return;
    }
    this.mostrarError(
      'Esa subasta ya no está en curso: puede que haya terminado hace un momento. Actualizamos tu panel.',
    );
    await this.cargarPanel();
  }

  agregarSubasta(sub) {
    if (!this.subastas.some((s) => s.id === sub.id)) {
      this.subastas = [...this.subastas, sub];
    }
  }

  /**
   * Cancelar una publicación desde «Tus publicaciones» (7.7.10). El botón
   * solo sale si el servidor dice que se puede, y lo que cuesta también lo
   * dice él (`penalizacionSiCancela`); aquí no se calcula.
   *
   * @param {string} subastaId
   * @returns {Promise<boolean>}
   */
  async cancelarPublicacion(subastaId) {
    const publicacion = this.panel.publicadas?.find((p) => p.id === subastaId);
    if (!this.api || !publicacion?.cancelable) {
      return false;
    }
    const coste = publicacion.penalizacionSiCancela;
    const porcentaje = Number(this.reglas?.penalizacionCancelacionPorcentaje);
    const deQue = Number.isFinite(porcentaje)
      ? ` (el ${porcentaje} % de la comisión que pagaste al publicarla)`
      : '';
    let mensaje = `«${publicacion.nombre}» vuelve a tu inventario y pagas una penalización: ${this.textoPenalizacion()}.`;
    let textoConfirmar = 'Cancelar y pagar la penalización';
    if (coste === 0) {
      mensaje = `«${publicacion.nombre}» vuelve a tu inventario. No pagas penalización.`;
      textoConfirmar = 'Cancelar la subasta';
    } else if (coste !== null) {
      mensaje = `«${publicacion.nombre}» vuelve a tu inventario y pagas ${formatearCreditos(coste)} cr de penalización${deQue}.`;
      textoConfirmar = `Cancelar y pagar ${formatearCreditos(coste)} cr`;
    }
    const confirmado = await this.confirmarAccion({
      titulo: 'Cancelar la subasta',
      mensaje,
      textoConfirmar,
      textoCancelar: 'No cancelar',
    });
    if (!confirmado) {
      return false;
    }
    return this.ejecutarContraElServidor(() => this.api.cancelar(subastaId));
  }

  /** Quitar una subasta de tu lista de seguimiento desde el panel. */
  dejarDeSeguirDesdeElPanel(subastaId) {
    if (!this.api || !subastaId) {
      return Promise.resolve(false);
    }
    return this.ejecutarContraElServidor(() => this.api.dejarDeSeguir(subastaId));
  }

  /**
   * «Exportar historial» (7.7.9): el CSV que arma el servidor, guardado como
   * archivo. Si falla, se dice y no se descarga nada a medias.
   *
   * @returns {Promise<boolean>}
   */
  async exportarHistorial() {
    if (!this.api?.exportarHistorial || this.exportando) {
      return false;
    }
    this.cambiarExportando(true);
    try {
      const csv = await this.api.exportarHistorial();
      descargarTexto(csv, 'historial-subastas.csv');
      const boton = this.contenedor?.querySelector('#btn-exportar-historial');
      if (boton) {
        acusar(boton, { tipo: 'exito', texto: 'Historial descargado' });
      }
      return true;
    } catch (fallo) {
      this.mostrarError(
        textoDeError(
          fallo,
          'No pudimos preparar tu historial para descargarlo. Inténtalo de nuevo en un momento.',
        ),
      );
      return false;
    } finally {
      this.cambiarExportando(false);
    }
  }

  cambiarExportando(exportando) {
    this.exportando = exportando;
    const boton = this.contenedor?.querySelector('#btn-exportar-historial');
    if (boton) {
      boton.disabled = exportando;
      boton.textContent = exportando ? 'Preparando el archivo…' : 'Exportar CSV';
    }
  }

  /**
   * UXC-9 — «Compartir» (7.7.9): el enlace a esta subasta, con el menú de
   * compartir del sistema si lo hay y, si no, copiado al portapapeles. No
   * necesita servidor: `pujas.html?id=` ya abre la subasta a cualquiera.
   *
   * @returns {Promise<boolean>}
   */
  async compartirSubasta() {
    const sub = this.getSubastaActiva();
    if (!sub) {
      return false;
    }
    const base = globalThis.location?.href ?? 'http://localhost/';
    const enlace = new URL(`./pujas.html?id=${encodeURIComponent(sub.id)}`, base).href;
    const boton = this.contenedor?.querySelector('#btn-compartir');
    const navegador = globalThis.navigator;
    try {
      if (typeof navegador?.share === 'function') {
        await navegador.share({ title: sub.nombre, text: `Subasta de ${sub.nombre}`, url: enlace });
        return true;
      }
      await navegador.clipboard.writeText(enlace);
      if (boton) {
        acusar(boton, { tipo: 'exito', texto: 'Enlace copiado' });
      }
      return true;
    } catch (fallo) {
      // Cerrar el menú de compartir no es un fallo que haya que contar.
      if (fallo?.name !== 'AbortError') {
        this.mostrarError(
          'No pudimos copiar el enlace desde aquí. Copia la dirección de esta página para compartirla.',
        );
      }
      return false;
    }
  }

  /** «Ver todas» / «Ver menos» en las listas largas del panel. */
  alternarVerTodo(lista) {
    this.verTodo = { ...this.verTodo, [lista]: !this.verTodo[lista] };
    this.render();
  }

  /**
   * UXC-8 — las opiniones del objeto de la subasta abierta, bajo el detalle
   * (§7.1: el detalle de un producto lleva su calificación y sus
   * comentarios). El producto sale de la ficha (`productoId`). Viven fuera del
   * contenedor que se repinta y se montan una vez por producto: el sondeo
   * repinta cada cinco segundos, y un comentario a medio escribir no puede
   * perderse por eso.
   */
  pintarOpiniones() {
    const zona = this.zonaOpiniones;
    if (!zona) {
      return;
    }
    const enDetalle =
      this.vista === 'detalle' && this.estadoDatos !== 'carga' && this.estadoDatos !== 'error';
    const productoId = enDetalle ? (this.getSubastaActiva()?.productoId ?? null) : null;
    if (!productoId) {
      zona.hidden = true;
      return;
    }
    if (this.opiniones?.productoId !== productoId) {
      this.montarOpiniones(zona, {
        productoId: String(productoId),
        titulo: 'Opiniones de este objeto',
        nivel: 2,
      });
      this.opiniones = { productoId };
    }
    zona.hidden = false;
  }

  /**
   * Penalizacion de cancelar a 24 h y a 48 h segun las reglas vigentes: el
   * porcentaje (7.7.10) de la comision de la Tabla 25. Null si no llegaron.
   */
  textoPenalizacion() {
    const reglas = this.reglas;
    if (!reglas || !Array.isArray(reglas.duraciones)) {
      return 'el 50 % de la comisión que pagaste al publicar';
    }
    const porcentaje = Number(reglas.penalizacionCancelacionPorcentaje);
    const partes = reglas.duraciones.map(
      (d) =>
        `${formatearCreditos((Number(d.comision) * porcentaje) / 100)} cr si era de ${d.horas} h`,
    );
    return `el ${porcentaje} % de la comisión que pagaste (${partes.join(', ')})`;
  }

  async ejecutarContraElServidor(operacion, { acuse: acuseTras = null } = {}) {
    if (this.enviando) {
      return false;
    }
    this.limpiarError();
    this.enviando = true;
    this.render();
    try {
      await operacion();
      // recargar() ya vuelve a traer el detalle si seguimos en el: el listado
      // no refleja si TU vas ganando ni tu limite, y sin eso la pantalla se
      // quedaria diciendo lo de antes de pujar.
      await this.recargar();
      // UXC-8 — y en «Mis subastas», el panel: cancelar, dejar de seguir o
      // recoger cambian lo que dice.
      if (this.vista === 'mis-subastas') {
        await this.cargarPanel();
      }
      // El acuse va DESPUES de recargar, sobre el nodo ya repintado: si fuera
      // antes, el repintado se lo llevaria por delante. Y solo si la
      // operacion salio bien — un acuse tras un rechazo seria una mentira.
      if (acuseTras) {
        const destino = this.contenedor?.querySelector(acuseTras.selector);
        if (destino) {
          acusar(destino, { tipo: acuseTras.tipo, texto: acuseTras.texto });
        }
      }
      return true;
    } catch (fallo) {
      const mensaje = textoDeError(fallo, 'No se pudo completar la operación.');
      await this.recargar();
      this.mostrarError(mensaje);
      return false;
    } finally {
      this.enviando = false;
    }
  }

  mostrarError(mensaje) {
    this.mensajeError = mensaje;
    const alerta = this.contenedor?.querySelector('#alerta-pujas');
    if (alerta) {
      alerta.textContent = mensaje;
      alerta.hidden = false;
      alerta.removeAttribute('style');
    } else if (this.contenedor) {
      this.render();
    }
  }

  limpiarError() {
    this.mensajeError = null;
    const alerta = this.contenedor?.querySelector('#alerta-pujas');
    if (alerta) {
      alerta.textContent = '';
      alerta.hidden = true;
      alerta.style.display = 'none';
    }
  }

  generarHtmlAlerta() {
    const hayError = Boolean(this.mensajeError);
    // UXC-8 / B8 — escapado: el mensaje puede traer texto del servidor, y
    // esta alerta va también en el estado vacío (con los pendientes de
    // recoger). Hoy los mensajes son textos propios (mensajePara), pero la
    // plantilla no debería depender de que siga siendo así.
    return `<div id="alerta-pujas" class="alerta alerta-error alerta-pujas" role="alert" ${hayError ? '' : 'hidden style="display: none;"'}>${hayError ? esc(this.mensajeError) : ''}</div>`;
  }

  /**
   * Abre el canal en vivo y se suscribe al listado. Nunca rechaza: si el
   * servidor no tiene WebSocket, el navegador lo bloquea o el frame llega
   * ilegible, la pantalla sigue con el sondeo de 5 s y el jugador no se entera.
   * Un canal opcional no puede tumbar la pantalla.
   */
  /**
   * Cambia el estado del canal y lo refleja en la pildora — FI-R11.
   *
   * Se repinta solo la pildora, no la vista entera: un repintado completo en
   * medio de una puja le robaria el foco al campo del monto y perderia lo que
   * la persona estuviera escribiendo. Es el mismo motivo por el que el
   * temporizador toca los relojes por `data-tiempo-subasta` y no repinta.
   *
   * @param {string} nuevo una de las variantes de ESTADO_CANAL
   */
  cambiarEstadoCanal(nuevo) {
    if (this.estadoCanal === nuevo) {
      return;
    }
    this.estadoCanal = nuevo;
    this.pintarEstadoCanal();
  }

  pintarEstadoCanal() {
    const zona = this.contenedor?.querySelector('[data-zona="conexion-subastas"]');
    if (!zona) {
      return;
    }
    // Sin canal pedido no se pinta nada: no hay promesa que romper.
    if (!this.urlCanal) {
      zona.hidden = true;
      return;
    }
    zona.hidden = false;
    zona.className = `conexion conexion--${this.estadoCanal}`;
    zona.textContent = TEXTO_CANAL[this.estadoCanal] ?? this.estadoCanal;
  }

  /**
   * Abre el canal y se suscribe. Es el UNICO sitio que lo hace: el ciclo de
   * reconexion llama aqui, no repite el cableado. Dos copias del mismo enganche
   * es como se acaba teniendo un canal que se suscribe y otro que no.
   *
   * Nunca rechaza: si el servidor no tiene WebSocket, el navegador lo bloquea o
   * el token no vale, la pantalla sigue con el sondeo de 5 s y **lo dice**.
   *
   * @returns {Promise<object|null>} el canal abierto, o null
   */
  async abrirCanalEnVivo() {
    if (!this.urlCanal || !this.conectarCanal || this.canal) {
      return null;
    }
    try {
      // R9.6 — el CONNECT va acreditado. Hasta R9.6 este canal se abria sin
      // token: era el unico de los cuatro de la casa que no lo mandaba. El
      // navegador no puede poner cabeceras en el handshake del WebSocket, asi
      // que el sitio donde va es la cabecera `Authorization` del frame CONNECT,
      // igual que en cliente-chat.js.
      //
      // Sin sesion se conecta igual, y a proposito: el listado de subastas es
      // publico y quien no ha entrado tiene derecho a verlo actualizarse.
      // Mandar `Bearer null` seria peor que no mandar nada.
      const token = this.leerToken?.();
      const cabeceras = token ? { Authorization: `Bearer ${token}` } : {};
      const canal = await this.conectarCanal({ url: this.urlCanal, cabeceras });
      canal.suscribir(CANAL_SUBASTAS, (cuerpo) => this.alLlegarActualizacion(cuerpo));
      this.canal = canal;
      // FI-R11 — un canal que se muere en silencio es peor que uno que no abre:
      // la pantalla seguiria diciendo «al instante» mientras las pujas de los
      // demas pasan sin que nadie las vea. Al cerrarse se reconecta, y mientras
      // tanto se dice.
      canal.alCerrar = () => {
        if (this.canal === canal) {
          this.canal = null;
          if (this.vivo) {
            this.reconectar();
          }
        }
      };
      this.cambiarEstadoCanal(ESTADO_CANAL.ESTABLE);
      return canal;
    } catch {
      // Ni con un token invalido se finge conexion: el CONNECT falla y el
      // estado lo dice. Poner «estable» aqui seria prometer tiempo real a quien
      // no lo tiene.
      this.cambiarEstadoCanal(ESTADO_CANAL.SIN_CONEXION);
      return null;
    }
  }

  /**
   * Vuelve a intentarlo con espera creciente — FI-R11.
   *
   * No sustituye al sondeo de 5 s: ese sigue corriendo y es lo que mantiene la
   * pantalla al dia mientras no hay canal (riesgo #7 del acta: degradacion
   * controlada, no apagon). Esto recupera la inmediatez cuando el servidor
   * vuelve, que antes no pasaba nunca — una vez caido el canal, se quedaba
   * caido hasta que alguien recargara la pagina.
   *
   * Un solo ciclo a la vez: sin esta guarda, un canal que se abre y se cierra
   * varias veces seguidas deja varios ciclos compitiendo, cada uno con su
   * propia escalera de esperas.
   *
   * @returns {Promise<void>}
   */
  reconectar() {
    if (!this.reconexion) {
      this.reconexion = this.cicloDeReconexion().finally(() => {
        this.reconexion = null;
      });
    }
    return this.reconexion;
  }

  async cicloDeReconexion() {
    let intento = 0;
    while (this.vivo && !this.canal && this.urlCanal) {
      this.cambiarEstadoCanal(ESTADO_CANAL.RECONECTANDO);
      await this.esperar(this.esperas[Math.min(intento, this.esperas.length - 1)]);
      if (!this.vivo) {
        return;
      }
      if (await this.abrirCanalEnVivo()) {
        // Al volver se relee una vez: mientras no habia canal pudieron cambiar
        // ofertas que el sondeo no alcanzo a traer.
        await this.recargar();
        return;
      }
      intento += 1;
    }
  }

  /**
   * Un cambio publicado por el servidor. Se usa para decidir SI releer, no
   * para pintar directamente lo que llega: el mensaje trae el resumen de la
   * subasta, pero no sabe si esa puja es tuya ni cuanto llevas retenido, y
   * pintarlo a ciegas dejaria la pantalla diciendo "no vas ganando" justo
   * despues de que ganaras.
   */
  alLlegarActualizacion(cuerpo) {
    let actualizada;
    try {
      actualizada = typeof cuerpo === 'string' ? JSON.parse(cuerpo) : cuerpo;
    } catch {
      return false;
    }
    if (!actualizada || !actualizada.id) {
      return false;
    }

    const esLaQueMiro = this.vista === 'detalle' && actualizada.id === this.subastaActivaId;
    const laTengoEnLista = this.subastas.some((sub) => sub.id === actualizada.id);
    if (!esLaQueMiro && !laTengoEnLista) {
      return false;
    }

    // UXC-8 — si ibas ganando esa, te pueden haber superado: se pregunta tu
    // participación después de releer (el mensaje del canal no lo dice).
    const ibaGanando = this.participaciones.get(actualizada.id)?.ganando;
    this.recargar().then(() => {
      if (ibaGanando && !esLaQueMiro) {
        return this.intentar(() => this.api?.miParticipacion(actualizada.id)).then((p) => {
          if (p) {
            this.aplicarParticipacion(actualizada.id, p);
            this.render();
          }
        });
      }
      return undefined;
    });
    return true;
  }

  cerrarCanalEnVivo() {
    if (!this.canal) {
      return;
    }
    try {
      this.canal.cerrar();
    } catch {
      // Cerrar un canal ya caido no es un problema que deba propagarse.
    }
    this.canal = null;
  }

  /** Solo el reloj. Lo que `iniciarTemporizador` necesita reiniciar. */
  pararTemporizador() {
    if (this.intervalId) {
      clearInterval(this.intervalId);
      this.intervalId = null;
    }
  }

  /** El reloj y el canal. Sigue existiendo porque lo llaman desde fuera. */
  destruir() {
    this.pararTemporizador();
    this.pararSondeo();
    this.cerrarCanalEnVivo();
  }

  /**
   * Suelta la pantalla del todo — FI-R11.
   *
   * `destruir()` no vale para esto: lo llama `iniciarTemporizador` en cada
   * recarga para no dejar dos intervalos, asi que si apagara la reconexion, la
   * primera recarga la mataria. Esto es lo que llama la pagina al irse.
   */
  desmontar() {
    this.vivo = false;
    this.destruir();
  }

  iniciarTemporizador() {
    // FI-R11 — aqui se llamaba a `destruir()`, que ADEMAS de parar el reloj
    // cerraba el canal en vivo. Y `recargar()` termina llamando a este metodo,
    // asi que la secuencia real de cada carga era: abrir el canal, pedir el
    // listado, y cerrar el canal que se acababa de abrir. Con cada recarga
    // posterior, lo mismo.
    //
    // O sea que el tiempo real de las subastas estaba apagado en la practica
    // desde que existe, y no se noto porque el sondeo de 5 s tapaba el hueco:
    // el sintoma era que las pujas tardaban unos segundos, que es exactamente
    // lo que uno espera de un sondeo y lo que nadie va a investigar.
    this.pararTemporizador();
    this.intervalId = setInterval(() => {
      let cambio = false;
      this.subastas.forEach((sub) => {
        if (sub.segundosRestantes > 0) {
          sub.segundosRestantes -= 1;
          cambio = true;
        }
        if (sub.esperaSegundos > 0) {
          sub.esperaSegundos -= 1;
          cambio = true;
        }
      });
      if (this.avisoCruzado) {
        const subAviso = this.subastas.find((s) => s.id === this.avisoCruzado.id);
        if (subAviso) {
          this.avisoCruzado.segundosRestantes = subAviso.segundosRestantes;
        } else if (this.avisoCruzado.segundosRestantes > 0) {
          this.avisoCruzado.segundosRestantes -= 1;
        }
        cambio = true;
      }
      // UXC-8 — en «Mis subastas» también corren los relojes del panel
      // (publicaciones y seguidas), que no están en el listado.
      if ((cambio || this.vista === 'mis-subastas') && this.contenedor) {
        this.actualizarTiemposEnDOM();
      }
    }, 1000);
  }

  /**
   * Saldo libre segun el servidor, o null si todavia no se sabe.
   *
   * Null NO es cero: cero le diria al jugador que esta arruinado cuando lo
   * unico que pasa es que no se ha podido preguntar. Quien lo consuma tiene
   * que distinguirlos.
   */
  getSaldoLibre() {
    if (
      !this.resumen ||
      this.resumen.saldoDisponible === null ||
      this.resumen.saldoDisponible === undefined
    ) {
      return null;
    }
    return Number(this.resumen.saldoDisponible);
  }

  /** Retenido real cuando el servidor lo dio; si no, lo que se pueda sumar de lo cargado. */
  getRetenidoReal() {
    return this.resumen ? Number(this.resumen.creditosRetenidos || 0) : this.getSaldoRetenido();
  }

  /** En cuantas subastas va ganando. Null si no se sabe todavia. */
  getSubastasGanando() {
    return this.resumen ? this.resumen.subastasGanando : null;
  }

  getSaldoRetenido() {
    return calcularSaldoRetenido(this.subastas);
  }

  /**
   * Saldo bruto: lo libre mas lo retenido en pujas vivas. Null si no se sabe
   * lo libre, porque entonces el total tampoco se sabe.
   */
  getSaldoTotal() {
    const libre = this.getSaldoLibre();
    return libre === null ? null : libre + this.getRetenidoReal();
  }

  getSubastaActiva() {
    return (
      this.subastas.find((s) => s.id === this.subastaActivaId) ||
      (this.subastaCerrada?.id === this.subastaActivaId ? this.subastaCerrada : null) ||
      this.subastas[0]
    );
  }

  getHeroeActivo() {
    return this.heroes.find((h) => h.id === this.heroeId) || this.heroes[0] || null;
  }

  cambiarVista(nuevaVista) {
    this.limpiarError();
    this.vista = nuevaVista;
    this.render();
  }

  abrirExplorar() {
    this.limpiarError();
    this.vista = 'explorar';
    this.subastaCerrada = null;
    this.resultadoCierre = null;
    this.render();
  }

  abrirMisSubastas() {
    this.limpiarError();
    this.vista = 'mis-subastas';
    this.render();
    // Sin await: la pestaña se pinta ya y se completa al llegar las respuestas
    // (tu participación en cada subasta abierta y, UXC-8, el panel personal).
    return Promise.all([this.consultarParticipaciones(), this.cargarPanel()]);
  }

  abrirCierreMultiple() {
    this.limpiarError();
    this.vista = 'cierre-multiple';
    this.render();
  }

  abrirDetalle(id, opciones = {}) {
    this.limpiarError();
    if (this.vista !== 'detalle') {
      this.origenVista = this.vista;
    }
    // Con servidor, el detalle trae datos que el listado no tiene: el historial
    // y la situacion propia. Se piden al abrir, no al cargar la lista, para no
    // hacer dos consultas por cada subasta que solo se esta mirando de pasada.
    if (this.api && id) {
      // Sin await: el detalle se pinta ya con lo que traia el listado y estos
      // datos llegan despues. cargarDetalle no rechaza nunca, asi que esta
      // promesa suelta no puede acabar en un unhandled rejection.
      this.cargarDetalle(id);
    }
    this.subastaActivaId = id || this.subastaActivaId || this.subastas[0]?.id;
    this.vista = 'detalle';
    this.confirmandoCompra = false;
    this.resultadoCierre = opciones.resultadoCierre || null;
    this.montoPersonalizado = null;
    this.limiteAutoPersonalizado = null;
    this.render();
  }

  volverALista() {
    this.limpiarError();
    this.vista = destinoDeVuelta(this.origenVista).vista;
    this.subastaActivaId = null;
    this.confirmandoCompra = false;
    this.resultadoCierre = null;
    this.render();
  }

  seleccionarHeroe(id) {
    this.heroeId = id;
    this.render();
  }

  lanzarAvisoCruzado(subastaOId) {
    const sub =
      typeof subastaOId === 'string' ? this.subastas.find((s) => s.id === subastaOId) : subastaOId;
    if (!sub) {
      return;
    }
    this.avisoCruzado = {
      id: sub.id,
      nombre: sub.nombre,
      oferta: sub.oferta,
      rival: sub.rival || 'rival',
      segundosRestantes: sub.segundosRestantes,
    };
    this.render();
  }

  descartarAvisoCruzado() {
    this.avisoCruzado = null;
    this.render();
  }

  irDesdeAvisoCruzado() {
    if (!this.avisoCruzado) {
      return;
    }
    const id = this.avisoCruzado.id;
    this.avisoCruzado = null;
    this.abrirDetalle(id);
  }

  pujar(monto) {
    this.limpiarError();
    const sub = this.getSubastaActiva();

    if (this.api) {
      // No se revalidan aqui las reglas de negocio. El servidor las aplica
      // dentro del lock de la subasta, que es donde se decide de verdad quien
      // gana la carrera; repetirlas en el cliente solo abre la puerta a que
      // rechace algo que el servidor habria aceptado. Lo unico que se filtra
      // es lo que ni siquiera es un monto.
      if (!Number.isFinite(monto) || monto <= 0) {
        this.mostrarError('Escribe un monto válido.');
        return Promise.resolve(false);
      }
      return this.ejecutarContraElServidor(() => this.api.pujar(sub.id, monto), {
        // UX-R2.10 — una puja aceptada repintaba la pantalla entera con el
        // importe nuevo y sin decir nada mas. Entre un numero que cambia y
        // otro que no, en una tarjeta llena de cifras, no se nota. El acuse
        // marca el importe y escribe cuanto se ofrecio; la cifra es texto
        // con `aria-live`, asi que con `prefers-reduced-motion` puesto se
        // pierde el brillo pero no el dato.
        acuse: { selector: '.precio-actual', tipo: 'puja', texto: `+${formatearCreditos(monto)}` },
      });
    }

    const saldoLibre = this.getSaldoLibre();
    const validacion = validarPuja(
      monto,
      sub,
      saldoLibre,
      this.config.incrementoMinimo,
      sub.esperaSegundos,
    );

    if (!validacion.valida) {
      this.mostrarError(validacion.motivo);
      return false;
    }

    sub.oferta = monto;
    sub.retenido = monto;
    sub.ganando = true;
    sub.superado = false;
    sub.esperaSegundos = this.config.intervaloSegundos;
    sub.historial.unshift({
      apodo: 'andres_nv',
      monto,
      tipo: 'Manual',
      cuando: 'ahora',
      esTu: true,
    });

    // Simulación de respuesta automática de rival tras 4 s
    if (sub.rival && sub.segundosRestantes > 10) {
      setTimeout(() => {
        if (sub.ganando && sub.segundosRestantes > 5) {
          const contraoferta = minimoDePuja(sub, this.config.incrementoMinimo) ?? sub.oferta;
          sub.oferta = contraoferta;
          sub.ganando = false;
          sub.superado = true;
          sub.retenido = 0; // Créditos restituidos
          sub.historial.unshift({
            apodo: sub.rival,
            monto: contraoferta,
            tipo: 'Automática',
            cuando: 'ahora',
            esTu: false,
          });
          // Si el usuario no está viendo esta subasta en detalle, se dispara el aviso cruzado tipo toast
          if (this.vista !== 'detalle' || this.subastaActivaId !== sub.id) {
            this.avisoCruzado = {
              id: sub.id,
              nombre: sub.nombre,
              oferta: contraoferta,
              rival: sub.rival,
              segundosRestantes: sub.segundosRestantes,
            };
          }
          if (this.contenedor) {
            this.render();
          }
        }
      }, 4000);
    }

    this.render();
    return true;
  }

  configurarAutoPuja(limite) {
    this.limpiarError();
    const sub = this.getSubastaActiva();

    if (this.api) {
      if (!Number.isFinite(limite) || limite <= 0) {
        this.mostrarError('Escribe un límite válido.');
        return Promise.resolve(false);
      }
      return this.ejecutarContraElServidor(() => this.api.configurarAutomatica(sub.id, limite));
    }

    const saldoLibre = this.getSaldoLibre();
    const validacion = validarLimiteAuto(limite, sub, saldoLibre, this.config.incrementoMinimo);

    if (!validacion.valida) {
      this.mostrarError(validacion.motivo);
      return false;
    }

    sub.autoLimite = limite;
    this.render();
    return true;
  }

  desactivarAutoPuja() {
    this.limpiarError();
    const sub = this.getSubastaActiva();
    if (!sub) {
      return undefined;
    }

    if (this.api) {
      return this.ejecutarContraElServidor(() => this.api.desactivarAutomatica(sub.id));
    }

    sub.autoLimite = 0;
    this.render();
    return undefined;
  }

  solicitarCompraInmediata() {
    this.limpiarError();
    this.confirmandoCompra = true;
    this.render();
  }

  cancelarCompraInmediata() {
    this.confirmandoCompra = false;
    this.render();
  }

  confirmarCompraInmediata() {
    this.limpiarError();
    const sub = this.getSubastaActiva();

    if (this.api) {
      this.confirmandoCompra = false;
      // UXC-8 — la subasta comprada se adjudica y sale del listado en la
      // recarga que sigue a la compra. Se guarda antes para poder enseñar el
      // «¡Es tuya!» con su nombre y su precio; si la compra falla, se suelta.
      this.subastaCerrada = { ...sub, segundosRestantes: 0 };
      this.subastaActivaId = sub.id;
      return this.ejecutarContraElServidor(() => this.api.comprarAhora(sub.id)).then((exito) => {
        if (exito) {
          this.vista = 'detalle';
          this.subastaActivaId = sub.id;
          this.resultadoCierre = 'comprada';
          this.render();
          this.contenedor?.querySelector('.cierre-victoria')?.focus?.();
        } else {
          this.soltarSubastaCerrada();
        }
        return exito;
      });
    }

    // El null se comprueba ANTES de sumar: en JavaScript `null + 0` es 0, asi
    // que sumar primero convertiria "no se sabe" en "no tiene nada" y
    // bloquearia la compra.
    const libre = this.getSaldoLibre();
    const disponibleParaEsta =
      libre === null || libre === undefined ? null : libre + (sub.retenido || 0);

    // Mismo criterio que en validarPuja: si no se sabe el saldo, decide el
    // servidor. Bloquear aqui por no haber podido preguntar le negaria una
    // compra que si puede pagar.
    if (disponibleParaEsta !== null && disponibleParaEsta < sub.compraInmediata) {
      this.confirmandoCompra = false;
      this.render();
      this.mostrarError('No dispones de saldo suficiente para comprar de inmediato.');
      return false;
    }

    sub.oferta = sub.compraInmediata;
    sub.retenido = sub.compraInmediata;
    sub.ganando = true;
    sub.superado = false;
    sub.segundosRestantes = 0;
    this.confirmandoCompra = false;
    this.resultadoCierre = 'comprada';
    this.render();
    return true;
  }

  /**
   * Una compra que no salió: la subasta vuelve a ser la del listado (o, si ya
   * no está, se vuelve a la lista con el motivo del rechazo a la vista).
   */
  soltarSubastaCerrada() {
    const id = this.subastaCerrada?.id;
    this.subastaCerrada = null;
    if (id && !this.subastas.some((s) => s.id === id) && this.vista === 'detalle') {
      this.vista = this.origenVista || 'explorar';
      this.subastaActivaId = null;
      this.render();
    }
  }

  actualizarTiemposEnDOM() {
    if (!this.contenedor) {
      return;
    }
    const elementosTiempo = this.contenedor.querySelectorAll('[data-tiempo-subasta]');
    elementosTiempo.forEach((el) => {
      const id = el.getAttribute('data-tiempo-subasta');
      const sub =
        this.subastas.find((s) => s.id === id) ||
        (this.avisoCruzado && this.avisoCruzado.id === id ? this.avisoCruzado : null);
      // UXC-8 — lo del panel que no está en el listado lleva su fecha de
      // fin: el reloj se calcula con ella.
      let restantes = sub ? sub.segundosRestantes : null;
      if (restantes === null && el.dataset.fin) {
        restantes = segundosHasta(el.dataset.fin, Date.now());
      }
      if (restantes !== null) {
        el.textContent = formatearTiempo(restantes);
        if (restantes <= 10 && restantes > 0) {
          el.classList.add('tiempo-urgente', 'animacion-latido');
        } else {
          el.classList.remove('tiempo-urgente', 'animacion-latido');
        }
      }
    });
  }

  render() {
    if (!this.contenedor) {
      return;
    }
    // UXC-8 — fuera del contenedor: no se repinta con él.
    this.pintarOpiniones();

    if (this.estadoDatos === 'carga') {
      this.contenedor.innerHTML = `
        <div class="estado-contenedor estado-carga" role="status">
          <div class="spinner"></div>
          <p>Cargando subastas activas...</p>
        </div>
      `;
      return;
    }

    if (this.estadoDatos === 'error') {
      // UX-R2.8c — «Reintentar» no reintentaba. Ponia `estadoDatos = 'exito'`
      // y volvia a pintar con `this.subastas`, que tras un fallo esta vacio:
      // la rama de abajo se encargaba del resto y el jugador acababa viendo
      // **«No hay subastas en curso»**. Un servicio caido presentado como un
      // mercado vacio, que es justo lo que no puede pasar.
      //
      // Ahora vuelve a pedir los datos de verdad, y mientras tanto ensena el
      // estado de carga.
      this.contenedor.innerHTML = `
        <div class="estado-contenedor estado-error" role="alert">
          <h3 class="titulo-mediano">El mercado no responde</h3>
          <p>${esc(this.mensajeError) || 'No fue posible conectar con el servicio de subastas.'}</p>
          <button class="btn btn-primario" id="btn-reintentar">Reintentar</button>
        </div>
      `;
      this.contenedor.querySelector('#btn-reintentar')?.addEventListener('click', () => {
        this.estadoDatos = 'carga';
        this.render();
        this.recargar();
      });
      return;
    }

    // UXC-8 — el mercado vacío solo tapa las vistas del mercado. Tu compra
    // recién hecha (que ya no está entre las abiertas), «Mis subastas» y los
    // cierres se siguen enseñando aunque no quede ninguna abierta.
    // B8 — y sin subastas en curso puede seguir habiendo algo ganado por
    // recoger (7.7.9): esconderlo dejaría el producto sin botón hasta que
    // alguien publique otra subasta.
    const miraLaComprada =
      this.vista === 'detalle' && this.subastaCerrada?.id === this.subastaActivaId;
    const vistaPropia = this.vista === 'mis-subastas' || this.vista === 'cierre-multiple';
    const mercadoVacio = this.estadoDatos === 'vacio' || this.subastas.length === 0;
    if (mercadoVacio && !miraLaComprada && !vistaPropia) {
      this.contenedor.innerHTML = `
        ${this.generarHtmlAlerta()}
        <div class="estado-contenedor estado-vacio">
          <h3 class="titulo-mediano">No hay subastas en curso</h3>
          <p>Cuando los jugadores publiquen objetos en venta, aparecerán aquí para pujar. También puedes poner a la venta algo tuyo.</p>
          <a class="btn btn-primario" href="./publicar-subasta.html">Publicar una subasta</a>
        </div>
        ${this.generarHtmlPendientes()}
      `;
      this.conectarEventos();
      return;
    }

    // Null cuando el servidor todavia no dio el saldo. Las vistas lo pintan
    // como desconocido; ninguna lo convierte en cero.
    const total = this.getSaldoTotal();
    const retenido = this.getSaldoRetenido();
    const libre = this.getSaldoLibre();
    const superadas = this.subastas.filter((s) => s.superado).length;

    let contenidoHtml = '';

    if (this.vista === 'mis-subastas') {
      contenidoHtml = this.generarHtmlMisSubastas({ total, libre, superadas });
    } else if (this.vista === 'cierre-multiple') {
      contenidoHtml = this.generarHtmlCierreMultiple({ total, libre });
    } else if (this.vista === 'detalle') {
      contenidoHtml = this.generarHtmlDetalle({ total, retenido, libre, superadas });
    } else {
      // 'lista' | 'explorar'
      contenidoHtml = this.generarHtmlExplorar({ superadas });
    }

    if (this.avisoCruzado) {
      contenidoHtml += this.generarHtmlToastCruzado();
    }

    // UXC-8 — el sondeo y el canal repintan: quien estaba escribiendo un
    // monto no puede perder el foco cada cinco segundos.
    const activo = globalThis.document?.activeElement;
    const idConFoco = activo && this.contenedor.contains(activo) && activo.id ? activo.id : null;

    this.contenedor.innerHTML = contenidoHtml;
    this.conectarEventos();
    this.prepararModal();

    if (idConFoco && !this.confirmandoCompra) {
      const mismo = globalThis.document.getElementById(idConFoco);
      if (mismo && this.contenedor.contains(mismo) && !mismo.disabled) {
        mismo.focus({ preventScroll: true });
      }
    }
  }

  /**
   * Lleva el foco al dialogo de compra y lo mantiene dentro mientras este
   * abierto.
   *
   * El marcado ya declaraba aria-modal="true", o sea que PROMETIA modalidad,
   * pero no la implementaba: el foco se quedaba detras del overlay, no habia
   * forma de cerrarlo con Escape y tabulando se salia al fondo. En una pantalla
   * donde el siguiente boton gasta creditos, eso significa poder confirmar una
   * compra sin haber llegado a oir de que.
   *
   * Se llama despues de cada render porque el modal se crea y se destruye con
   * el innerHTML; el foco se mueve una sola vez, al aparecer.
   */
  prepararModal() {
    // B8 — tambien el de cancelar la subasta, con el mismo trato de foco.
    const modal = this.contenedor.querySelector('#modal-compra-inmediata, #modal-cancelar-subasta');
    if (!modal) {
      this.modalEnfocado = false;
      return;
    }

    const focalizables = modal.querySelectorAll(
      'button:not([disabled]), [href], input:not([disabled]), select, textarea, [tabindex]:not([tabindex="-1"])',
    );
    if (!focalizables.length) {
      return;
    }

    if (!this.modalEnfocado) {
      // Al primer control y no al de confirmar: abrir un dialogo con el foco
      // puesto en el boton que gasta el dinero invita a confirmarlo sin leer.
      focalizables[0].focus();
      this.modalEnfocado = true;
    }

    modal.addEventListener('keydown', (evento) => {
      if (evento.key === 'Escape') {
        evento.preventDefault();
        if (modal.id === 'modal-cancelar-subasta') {
          this.descartarCancelacion();
        } else {
          this.cancelarCompraInmediata();
        }
        return;
      }
      if (evento.key !== 'Tab') {
        return;
      }

      const primero = focalizables[0];
      const ultimo = focalizables[focalizables.length - 1];
      if (evento.shiftKey && document.activeElement === primero) {
        evento.preventDefault();
        ultimo.focus();
      } else if (!evento.shiftKey && document.activeElement === ultimo) {
        evento.preventDefault();
        primero.focus();
      }
    });
  }

  generarHtmlPestanas({ superadas = 0 } = {}) {
    const esExplorar = this.vista === 'lista' || this.vista === 'explorar';
    const esMisSubastas = this.vista === 'mis-subastas';
    const esCierre = this.vista === 'cierre-multiple';
    const esDetalle = this.vista === 'detalle';
    const subActiva = this.getSubastaActiva();

    return `
      <!-- FI-R11 · el estado del canal, con las mismas clases y el mismo tono
           que la campana de notificaciones. Va junto a las pestanas porque esta
           en las tres vistas que traen datos del servidor. -->
      <span
        class="conexion conexion--${this.estadoCanal}"
        data-zona="conexion-subastas"
        role="status"
        aria-live="polite"
        ${this.urlCanal ? '' : 'hidden'}
        >${this.urlCanal ? (TEXTO_CANAL[this.estadoCanal] ?? this.estadoCanal) : ''}</span
      >
      <nav class="subastas-tabs" role="tablist" aria-label="Secciones de subastas">
        <button type="button" role="tab" class="tab-btn ${esExplorar ? 'tab-btn--activo' : ''}" data-tab="explorar" aria-selected="${esExplorar}">
          Explorar subastas
        </button>
        <button type="button" role="tab" class="tab-btn ${esMisSubastas ? 'tab-btn--activo' : ''}" data-tab="mis-subastas" aria-selected="${esMisSubastas}">
          Mis subastas
          ${this.insigniaDeMisSubastas(superadas)}
        </button>
        <button type="button" role="tab" class="tab-btn ${esCierre ? 'tab-btn--activo' : ''}" data-tab="cierre-multiple" aria-selected="${esCierre}">
          Cierre múltiple
          ${this.eventosCierre.length > 0 ? `<span class="badge-tab-neutral">${this.eventosCierre.length}</span>` : ''}
        </button>
        <button type="button" role="tab" class="tab-btn ${esDetalle ? 'tab-btn--activo' : ''}" data-tab="detalle" aria-selected="${esDetalle}">
          ${esDetalle && subActiva ? `Detalle: ${subActiva.nombre.split(' ')[0]}` : 'Detalle de subasta'}
        </button>
      </nav>
    `;
  }

  /**
   * UXC-8 — la cifra de la pestaña: dónde te superaron si hay alguna; si no,
   * en cuántas pujas. Nunca el tamaño del mercado, y nada mientras no se sepa.
   */
  insigniaDeMisSubastas(superadas) {
    if (superadas > 0) {
      return `<span class="badge-tab-aviso" title="Te superaron en ${superadas}">${superadas}</span>`;
    }
    if (!this.participacionConocida()) {
      return '';
    }
    const cuantas = this.subastas.filter(pujasEn).length;
    return cuantas > 0 ? `<span class="badge-tab-neutral">${cuantas}</span>` : '';
  }

  /** Sin servidor (pruebas) se sabe; con servidor, cuando ya se preguntó. */
  participacionConocida() {
    return !this.api || this.participacionesRevisadas;
  }

  /** Con servidor y sin sesión no hay «tus» subastas que enseñar. */
  sinSesion() {
    return Boolean(this.api) && !this.leerToken?.();
  }

  generarHtmlExplorar({ superadas }) {
    return `
      <div class="subastas-app">
        ${this.generarHtmlPestanas({ superadas })}
        ${this.generarHtmlAlerta()}

        <!-- Resumen de Saldos y Participación -->
        <header class="panel-resumen">
          <div class="resumen-titular">
            <div>
              <span class="eyebrow">MERCADO EN VIVO</span>
              <h1 class="titulo-grande">Subastas y Pujas</h1>
            </div>
            <div class="resumen-saldo-total">
              <span class="etiqueta-saldo">Retenido en pujas</span>
              <span class="valor-saldo cifra">${formatearCreditos(this.getRetenidoReal())} cr</span>
            </div>
          </div>

          <div class="resumen-metadatos">
            <div class="chip-info">
              <span class="punto-color punto-retenido"></span>
              <span>Retenido en pujas: <strong>${formatearCreditos(this.getRetenidoReal())} cr</strong></span>
            </div>
            ${
              this.getSubastasGanando() === null
                ? ''
                : `<div class="chip-info"><span>Vas ganando en: <strong>${this.getSubastasGanando()}</strong></span></div>`
            }
            ${
              this.participacionConocida() && !this.sinSesion()
                ? `<div class="chip-info">
              <span>Participas en: <strong>${this.subastas.filter(pujasEn).length} ${this.subastas.filter(pujasEn).length === 1 ? 'subasta' : 'subastas'}</strong></span>
            </div>`
                : ''
            }
          </div>
        </header>

        <!-- Cuadrícula de Subastas -->
        <section class="seccion-subastas" aria-label="Listado de subastas activas">
          <div class="encabezado-listado">
            <h2 class="titulo-seccion">Subastas en curso (${this.subastas.length})</h2>
            <span class="texto-pista">Ordenadas por tiempo restante · Selecciona una para ver el detalle y pujar</span>
          </div>

          <div class="grid-subastas">
            ${[...this.subastas]
              .sort((a, b) => (a.segundosRestantes || 0) - (b.segundosRestantes || 0))
              .map((sub) => this.generarTarjetaSubasta(sub))
              .join('')}
          </div>
        </section>
      </div>
    `;
  }

  generarTarjetaSubasta(sub) {
    const urgente = sub.segundosRestantes <= 10 && sub.segundosRestantes > 0;
    const rz = rarezaVisible(sub.rareza);
    let badgeEstado = '<span class="badge badge-neutral">Sin pujar</span>';
    let claseBorde = '';
    let textoBoton = 'Ver subasta';
    let claseBoton = 'btn-contorno';

    // UXC-8 — quién va delante, con lo que se sabe: el contrato del listado
    // trae cuántas pujas hay, no quién las hizo.
    let textoPostor = 'Sin pujas todavía';
    if (sub.ganando) {
      textoPostor = 'Tu puja lidera';
    } else if (sub.rival) {
      textoPostor = `Mejor postor: ${esc(sub.rival)}`;
    } else if (sub.rivales > 0) {
      textoPostor = `${sub.rivales} ${sub.rivales === 1 ? 'puja' : 'pujas'}`;
    }

    if (this.esMiSubasta(sub)) {
      badgeEstado = `<span class="badge badge-propia">${iconoHtml('usuario', { clase: 'icono icono--menudo' })} Tu subasta</span>`;
      claseBorde = 'borde-sin-puja';
    } else if (sub.ganando) {
      badgeEstado = '<span class="badge badge-exito">Vas ganando</span>';
      claseBorde = 'tarjeta-ganando borde-ganando';
    } else if (sub.superado) {
      badgeEstado = '<span class="badge badge-error">Te superaron</span>';
      claseBorde = 'tarjeta-superada borde-superada';
    } else {
      claseBorde = 'borde-sin-puja';
    }

    if (sub.superado) {
      textoBoton = 'Recuperarla';
      claseBoton = 'btn-primario btn-recuperar';
    } else if (urgente) {
      textoBoton = 'Ir ahora';
      claseBoton = 'btn-primario btn-ir-ahora animacion-latido';
    } else {
      textoBoton = 'Ver subasta';
      claseBoton = 'btn-contorno btn-ver-subasta';
    }

    return `
      <article class="tarjeta tarjeta-subasta ${claseBorde} ${urgente ? 'urgente' : ''}" data-id="${sub.id}">
        <div class="tarjeta-cabecera">
          ${rz.conocida ? `<span class="badge badge-${rz.clase}">${rz.texto}</span>` : ''}
          ${badgeEstado}
        </div>

        <div class="tarjeta-cuerpo">
          <h3 class="tarjeta-titulo">${esc(sub.nombre)}</h3>
          <p class="tarjeta-subtitulo">${esc(tipoLegible(sub.tipo))}${nivelRequeridoVisible(sub.nivel) ? ` · ${nivelRequeridoVisible(sub.nivel)}` : ''}</p>
          <p class="tarjeta-desc">${esc(sub.descripcion)}</p>
        </div>

        <div class="tarjeta-finanzas">
          <div class="columna-oferta">
            <span class="etiqueta-sm">Oferta actual</span>
            <span class="monto-destacado cifra">${formatearCreditos(sub.oferta)} cr</span>
            <span class="postor-texto ${sub.ganando ? 'texto-exito' : ''}">
              ${textoPostor}
            </span>
          </div>
          <div class="columna-tiempo">
            <span class="etiqueta-sm">Tiempo restante</span>
            <span class="tiempo-cifra cifra ${urgente ? 'tiempo-urgente animacion-latido' : ''}" data-tiempo-subasta="${sub.id}">
              ${formatearTiempo(sub.segundosRestantes)}
            </span>
            ${sub.compraInmediata === null || sub.compraInmediata === undefined ? '' : `<span class="compra-ya-texto">Comprar ya: ${formatearCreditos(sub.compraInmediata)} cr</span>`}
          </div>
        </div>

        <div class="tarjeta-acciones">
          <button type="button" class="btn ${claseBoton} btn-abrir" data-abrir="${sub.id}">
            ${textoBoton}
          </button>
        </div>
      </article>
    `;
  }

  /**
   * «Mis subastas» — UXC-8.
   *
   * Hasta aquí esta pestaña pintaba el MERCADO ENTERO como si fuera tuyo: las
   * dieciséis subastas del listado aparecían «en curso» a tu nombre, el medidor
   * decía «16 de 10 · Has llegado al tope» a quien no había pujado nunca y el
   * saldo, sin respuesta del servidor, salía como una barra verde de «libre».
   *
   * Ahora enseña lo que el servidor dijo: dónde pujas (vas ganando o te
   * superaron, con lo retenido y tu automática; se pregunta subasta por
   * subasta, porque no hay una lista de «mis pujas») y, del panel personal
   * (`ms-subastas-panel.yaml`), lo que publicaste en cualquier estado, lo que
   * sigues y tu historial con su balance y su exportación.
   */
  generarHtmlMisSubastas({ total, libre, superadas }) {
    const cabecera = `
        ${this.generarHtmlPestanas({ superadas })}
        ${this.generarHtmlAlerta()}

        <div class="mis-subastas-cabecera">
          <h1 class="titulo-grande">Mis subastas</h1>
          <p class="texto-pista">Dónde estás pujando, cuánto tienes retenido, lo que tienes a la venta, lo que sigues y tu historial.</p>
        </div>`;

    if (this.sinSesion()) {
      return `
      <div class="subastas-app vista-mis-subastas">
        ${cabecera}
        <div class="estado-contenedor estado-vacio" data-estado="sin-sesion">
          <h2 class="titulo-mediano">Entra para ver tus pujas</h2>
          <p>Tus pujas y tus publicaciones van con tu cuenta. El mercado lo puedes mirar sin entrar.</p>
          <a class="btn btn-primario" href="${esc(urlDeLogin({ volver: globalThis.location?.href ?? null }))}">Entrar</a>
        </div>
      </div>`;
    }

    const participando = this.subastas.filter(pujasEn);
    const retenidoReal = this.getRetenidoReal();
    const sobreCompromiso = verificarSobreCompromiso(total, participando);
    // Tus publicaciones en curso: las del panel si llegó (todas), si no las
    // tuyas del listado cargado.
    const publicacionesEnCurso = Array.isArray(this.panel.publicadas)
      ? this.panel.publicadas.filter((p) => p.estado === 'ACTIVA').length
      : this.subastas.filter((s) => this.esMiSubasta(s)).length;
    const estadoTopes = calcularEstadoTopesConcurrencia(
      {
        publicaciones: publicacionesEnCurso,
        ganando: this.getSubastasGanando() ?? participando.filter((s) => s.ganando).length,
      },
      this.config,
    );
    const porUrgencia = [...participando].sort((a, b) => a.segundosRestantes - b.segundosRestantes);
    const revisando = this.consultandoParticipaciones && !this.participacionesRevisadas;

    return `
      <div class="subastas-app vista-mis-subastas">
        ${cabecera}
        ${this.generarHtmlIndiceMisSubastas(porUrgencia.length + this.pujasAbiertasFueraDelListado(porUrgencia))}

        ${this.generarHtmlPanelCreditos({ total, libre, retenidoReal, participando, sobreCompromiso })}

        <!-- Medidores de los topes de 7.7.10: solo lo tuyo -->
        <section class="grid-topes-concurrencia" aria-label="Topes de publicaciones y de pujas activas">
          ${this.generarHtmlTope({
            nombre: 'Tus publicaciones en curso',
            estado: estadoTopes.subastas,
          })}
          ${this.generarHtmlTope({
            nombre: 'Pujas tuyas que van ganando',
            estado: estadoTopes.pujas,
          })}
        </section>

        ${this.generarHtmlPendientes()}

        ${this.generarHtmlSeccionPujas(porUrgencia, revisando)}

        ${this.generarHtmlSeccionPublicaciones()}
        ${this.generarHtmlSeccionSeguidas()}
        ${this.generarHtmlSeccionHistorial()}
      </div>
    `;
  }

  /** Cuántas de tus pujas en curso (según el servidor) no están en el listado cargado. */
  pujasAbiertasFueraDelListado(porUrgencia) {
    if (!Array.isArray(this.panel.pujas)) {
      return 0;
    }
    const enVivo = new Set(porUrgencia.map((s) => s.id));
    return this.panel.pujas.filter((p) => p.estadoSubasta === 'ACTIVA' && !enVivo.has(p.id)).length;
  }

  /**
   * «Donde pujas» (7.7.9 «Mis pujas»). Las abiertas del listado van con todo
   * lo que se sabe de ellas (retenido, tu automática, la espera); con
   * `GET /mis-pujas` (0.4.0) se suman las abiertas de otras páginas y, aparte,
   * las ya terminadas: ganadas, perdidas o canceladas.
   *
   * @param {object[]} porUrgencia las del listado en las que participas
   * @param {boolean} revisando si todavía se pregunta subasta por subasta
   */
  generarHtmlSeccionPujas(porUrgencia, revisando) {
    const delPanel = Array.isArray(this.panel.pujas) ? this.panel.pujas : null;
    const enVivo = new Set(porUrgencia.map((s) => s.id));
    const abiertasFuera = (delPanel ?? [])
      .filter((p) => p.estadoSubasta === 'ACTIVA' && !enVivo.has(p.id))
      .sort((a, b) => (a.segundosRestantes ?? 0) - (b.segundosRestantes ?? 0));
    const terminadas = (delPanel ?? []).filter((p) => p.estadoSubasta !== 'ACTIVA');
    const enCurso = porUrgencia.length + abiertasFuera.length;

    // Con la lista del servidor ya se sabe si no hay ninguna: no hace falta
    // esperar a terminar de preguntar subasta por subasta.
    let lista = this.generarHtmlSinPujas(revisando && !delPanel);
    if (enCurso > 0) {
      lista = `<div class="lista-mis-subastas">
            ${porUrgencia.map((sub) => this.generarFilaMiSubasta(sub)).join('')}
            ${abiertasFuera.map((p) => this.generarFilaDeMiPuja(p)).join('')}
          </div>`;
    }

    let deTerminadas = '';
    if (terminadas.length > 0) {
      const visibles = this.verTodo.pujas ? terminadas : terminadas.slice(0, TERMINADAS_A_LA_VISTA);
      const conmutador =
        terminadas.length > TERMINADAS_A_LA_VISTA
          ? `<button type="button" class="btn btn-texto btn-ver-todo" data-ver-todo="pujas" aria-expanded="${this.verTodo.pujas}">${this.verTodo.pujas ? 'Ver solo las más recientes' : `Ver las ${terminadas.length} terminadas`}</button>`
          : '';
      deTerminadas = `
          <h3 class="subtitulo-mis-subastas">Pujas terminadas</h3>
          <div class="lista-mis-subastas">
            ${visibles.map((p) => this.generarFilaDeMiPuja(p)).join('')}
          </div>
          ${conmutador}`;
    }

    let aviso = '';
    if (this.panel.fallos.includes('pujas')) {
      aviso = this.generarHtmlFalloDelPanel(
        delPanel
          ? 'No pudimos actualizar tus pujas: lo que ves puede haber cambiado.'
          : 'No pudimos traer todas tus pujas: aquí están las que vimos en las subastas abiertas del mercado.',
      );
    }
    const contador = `${enCurso} en curso${terminadas.length ? ` · ${terminadas.length} ${terminadas.length === 1 ? 'terminada' : 'terminadas'}` : ''}`;

    return `
        <!-- Donde pujas, por lo que se acaba antes -->
        <section class="seccion-mis-subastas" id="seccion-pujas" aria-labelledby="titulo-seccion-pujas" aria-busy="${revisando}">
          <div class="encabezado-mis-subastas">
            <h2 class="titulo-seccion" id="titulo-seccion-pujas">Donde pujas</h2>
            <span class="contador-mis-subastas">${contador}</span>
            <div style="flex-grow: 1;"></div>
            <span class="texto-pista">Ordenadas por lo que se acaba antes</span>
          </div>
          ${aviso}
          ${this.generarHtmlAlcance()}
          ${lista}
          ${deTerminadas}
        </section>`;
  }

  /**
   * Una participación según `GET /mis-pujas`: cómo te fue (o cómo vas), tu
   * mejor puja y la oferta. Si la ganaste al vencer y falta recogerla, el
   * botón para hacerlo.
   *
   * @param {ReturnType<typeof vistaDeMiPuja>} p
   */
  generarFilaDeMiPuja(p) {
    const resultado = resultadoDePuja(p.estado);
    const abierta = p.estadoSubasta === 'ACTIVA';
    const nombre = esc(p.nombre);
    const meta = [];
    if (p.tuMejorPuja !== null) {
      meta.push(`Tu mejor puja: ${formatearCreditos(p.tuMejorPuja)} cr`);
    }
    if (p.ultimaPujaEn) {
      meta.push(`Última: ${esc(momentoLegible(p.ultimaPujaEn))}`);
    }
    const porRecoger = p.estado === 'GANADA' && this.pendientes.some((x) => x.subastaId === p.id);
    let acciones = '<p class="texto-pista fila-publicacion__cierre">Ya terminó.</p>';
    if (abierta) {
      acciones = `
          <div class="reloj-fila" data-tiempo-subasta="${esc(p.id)}" data-fin="${esc(p.fechaFin)}">${formatearTiempo(p.segundosRestantes ?? 0)}</div>
          <button type="button" class="btn btn-contorno btn-sm" data-abrir-panel="${esc(p.id)}" aria-label="Ver la subasta de ${nombre}">Ver subasta</button>`;
    } else if (porRecoger) {
      acciones = `
          <p class="texto-pista fila-publicacion__cierre">Ganada y pagada: falta recogerla.</p>
          <button type="button" class="btn btn-primario btn-sm btn-recoger-pendiente" data-subasta="${esc(p.id)}" aria-label="Recoger ${nombre}" ${this.enviando ? 'disabled' : ''}>Recoger</button>`;
    }
    let simbolo = abierta ? 'reloj' : 'escudo';
    if (p.estado === 'GANADA') {
      simbolo = 'trofeo';
    }
    return `
      <article class="fila-mi-subasta fila-publicacion fila-mi-puja borde-sin-puja" data-panel data-id="${esc(p.id)}" data-estado="${esc(p.estadoSubasta)}" data-resultado="${esc(p.estado)}">
        <div class="ficha-rareza ficha-rareza--grande ficha-rareza--desconocida">
          ${iconoHtml(simbolo)}
        </div>
        <div class="fila-info-principal">
          <h3 class="fila-nombre">${nombre}</h3>
          <div class="fila-badges">
            <span class="badge badge-${resultado.variante}">${resultado.texto}</span>
          </div>
          ${meta.length ? `<p class="texto-pista fila-publicacion__meta">${meta.join(' · ')}</p>` : ''}
        </div>
        <div class="fila-oferta">
          <div class="etiqueta-sm">${abierta ? 'Oferta vigente' : 'Oferta final'}</div>
          <div class="fila-oferta-monto cifra">${formatearCreditos(p.oferta)} cr</div>
          <div class="postor-texto">${p.pujas > 0 ? `${p.pujas} ${p.pujas === 1 ? 'puja' : 'pujas'}` : 'Sin pujas'}</div>
        </div>
        <div class="fila-acciones-tiempo fila-publicacion__acciones">
          ${acciones}
        </div>
      </article>
    `;
  }

  /**
   * UXC-8 — con el panel personal la pestaña crece: un índice arriba lleva a
   * cada parte sin recorrer la página entera.
   *
   * @param {number} pujando
   */
  generarHtmlIndiceMisSubastas(pujando) {
    if (!this.panelDisponible('misPublicaciones')) {
      return '';
    }
    const cuantas = (lista) =>
      Array.isArray(lista) ? ` <span class="cifra">(${lista.length})</span>` : '';
    const enlaces = [
      `<a class="indice-mis-subastas__enlace" href="#seccion-pujas">Donde pujas <span class="cifra">(${pujando})</span></a>`,
      this.pendientes.length
        ? `<a class="indice-mis-subastas__enlace" href="#seccion-pendientes">Pendientes de recoger <span class="cifra">(${this.pendientes.length})</span></a>`
        : '',
      `<a class="indice-mis-subastas__enlace" href="#seccion-publicaciones">Tus publicaciones${cuantas(this.panel.publicadas)}</a>`,
      this.panelDisponible('misSeguidas')
        ? `<a class="indice-mis-subastas__enlace" href="#seccion-seguidas">Siguiendo${cuantas(this.panel.seguidas)}</a>`
        : '',
      this.panelDisponible('miHistorial')
        ? '<a class="indice-mis-subastas__enlace" href="#seccion-historial">Historial</a>'
        : '',
    ];
    return `
        <nav class="indice-mis-subastas" aria-label="Partes de Mis subastas">
          ${enlaces.filter(Boolean).join('')}
        </nav>`;
  }

  /**
   * Lo que no llegó del panel, dicho donde iba y con la forma de volver a
   * pedirlo. Lo demás del panel se sigue viendo.
   *
   * @param {string} texto
   */
  generarHtmlFalloDelPanel(texto) {
    return `
          <div class="alerta alerta-advertencia aviso-panel" role="status">
            <span>${esc(texto)}</span>
            <button type="button" class="btn btn-texto btn-sm" data-reintentar-panel ${this.panel.cargando ? 'disabled' : ''}>Reintentar</button>
          </div>`;
  }

  /** Si el servidor (la api) sabe dar esa parte del panel. */
  panelDisponible(metodo) {
    return Boolean(this.api) && typeof this.api[metodo] === 'function';
  }

  /** «Leyendo…» mientras el panel todavía no llegó. */
  generarHtmlCargandoDelPanel(texto) {
    return `<p class="texto-pista nota-alcance" role="status">${esc(texto)}</p>`;
  }

  /**
   * «Tus publicaciones»: con el panel, todas (en curso primero, por lo que
   * se acaba antes; luego las terminadas, de la más reciente a la más
   * antigua) con sus visitas, lo cobrado y, si se puede, cancelar. Sin el
   * panel (sin servidor, o mientras llega, o si falló), las tuyas que siguen
   * abiertas en el listado.
   */
  generarHtmlSeccionPublicaciones() {
    const delServidor = this.panel.publicadas;
    const fallo = this.panel.fallos.includes('publicadas');
    const conServidor = Array.isArray(delServidor);
    let contador = '';
    let cuerpo = '';
    let aviso = '';
    let conBotonDePublicar = true;

    if (conServidor) {
      const abiertas = delServidor
        .filter((p) => p.estado === 'ACTIVA')
        .sort((a, b) => (a.segundosRestantes ?? 0) - (b.segundosRestantes ?? 0));
      const terminadas = delServidor.filter((p) => p.estado !== 'ACTIVA');
      const visibles = this.verTodo.publicadas
        ? terminadas
        : terminadas.slice(0, TERMINADAS_A_LA_VISTA);
      contador = `${abiertas.length} en curso · ${terminadas.length} ${terminadas.length === 1 ? 'terminada' : 'terminadas'}`;
      if (delServidor.length === 0) {
        conBotonDePublicar = false;
        cuerpo = `
          <div class="estado-contenedor estado-vacio estado-vacio--compacto" data-estado="sin-publicaciones">
            <h3 class="titulo-mediano">Todavía no has publicado nada</h3>
            <p>Pon a la venta un objeto de tu inventario: aquí verás sus pujas, sus visitas y cómo termina.</p>
            <a class="btn btn-primario" href="./publicar-subasta.html">Publicar una subasta</a>
          </div>`;
      } else {
        const quedan = terminadas.length - TERMINADAS_A_LA_VISTA;
        let conmutador = '';
        if (quedan > 0) {
          conmutador = `<button type="button" class="btn btn-texto btn-ver-todo" data-ver-todo="publicadas" aria-expanded="${this.verTodo.publicadas}">${this.verTodo.publicadas ? 'Ver solo las más recientes' : `Ver las ${terminadas.length} terminadas`}</button>`;
        }
        cuerpo = `<div class="lista-mis-subastas">
            ${[...abiertas, ...visibles].map((p) => this.generarFilaDePublicacionDelServidor(p)).join('')}
          </div>
          ${conmutador}`;
      }
      if (fallo) {
        aviso = this.generarHtmlFalloDelPanel(
          'No pudimos actualizar tus publicaciones: lo que ves puede haber cambiado.',
        );
      }
    } else {
      const abiertas = this.subastas.filter((s) => this.esMiSubasta(s));
      contador = `${abiertas.length} ${abiertas.length === 1 ? 'abierta' : 'abiertas'}`;
      cuerpo =
        abiertas.length > 0
          ? `<div class="lista-mis-subastas">
            ${abiertas.map((sub) => this.generarFilaPublicacion(sub)).join('')}
          </div>`
          : `<p class="texto-pista nota-publicaciones">No tienes nada a la venta entre las subastas abiertas. Publica un objeto de tu inventario y aparecerá aquí mientras siga abierto.</p>`;
      if (fallo) {
        aviso = this.generarHtmlFalloDelPanel(
          'No pudimos traer todas tus publicaciones: aquí están las tuyas que siguen abiertas en el mercado.',
        );
      } else if (this.panel.cargando) {
        aviso = this.generarHtmlCargandoDelPanel('Trayendo todas tus publicaciones…');
      }
    }

    return `
        <!-- Lo que publicaste -->
        <section class="seccion-mis-subastas" id="seccion-publicaciones" aria-labelledby="titulo-seccion-publicaciones">
          <div class="encabezado-mis-subastas">
            <h2 class="titulo-seccion" id="titulo-seccion-publicaciones">Tus publicaciones</h2>
            <span class="contador-mis-subastas">${contador}</span>
            <div style="flex-grow: 1;"></div>
            ${conBotonDePublicar ? '<a class="btn btn-contorno btn-sm" href="./publicar-subasta.html">Publicar una subasta</a>' : ''}
          </div>
          ${aviso}
          ${cuerpo}
        </section>`;
  }

  /**
   * Una publicación tuya según el panel: su estado, lo que lleva, sus
   * visitas y lo cobrado; en curso, su reloj y, si el servidor lo permite,
   * cancelar.
   *
   * @param {ReturnType<typeof vistaDePublicacion>} p
   */
  generarFilaDePublicacionDelServidor(p) {
    const estado = estadoDeSubasta(p.estado, { propia: true });
    const abierta = p.estado === 'ACTIVA';
    const urgente =
      abierta &&
      p.segundosRestantes !== null &&
      p.segundosRestantes <= 10 &&
      p.segundosRestantes > 0;
    const meta = [];
    if (p.vistas !== null) {
      meta.push(
        `${iconoHtml('ojo', { clase: 'icono icono--menudo' })} ${formatearCreditos(p.vistas)} ${p.vistas === 1 ? 'visita' : 'visitas'}`,
      );
    }
    if (p.comision !== null) {
      meta.push(`Comisión: ${formatearCreditos(p.comision)} cr`);
    }
    if (p.penalizacion) {
      meta.push(`Penalización: ${formatearCreditos(p.penalizacion)} cr`);
    }
    let etiquetaOferta = 'Oferta vigente';
    if (p.estado === 'ADJUDICADA') {
      etiquetaOferta = 'Vendida por';
    } else if (p.pujas === 0) {
      etiquetaOferta = 'Precio mínimo';
    }
    const nombre = esc(p.nombre);
    let acciones = `<p class="texto-pista fila-publicacion__cierre">Terminó: ${esc(momentoLegible(p.cerradaEn || p.fechaFin))}</p>`;
    if (abierta) {
      acciones = `
          <div class="reloj-fila ${urgente ? 'animacion-latido' : ''}" data-tiempo-subasta="${esc(p.id)}" data-fin="${esc(p.fechaFin)}">${formatearTiempo(p.segundosRestantes ?? 0)}</div>
          <button type="button" class="btn btn-contorno btn-sm" data-abrir-panel="${esc(p.id)}" aria-label="Ver la subasta de ${nombre}">Ver subasta</button>
          ${p.cancelable ? `<button type="button" class="btn btn-peligro-sm" data-cancelar-publicacion="${esc(p.id)}" aria-label="Cancelar la subasta de ${nombre}">Cancelar…</button>` : ''}`;
    }
    return `
      <article class="fila-mi-subasta fila-publicacion fila-publicacion--panel borde-sin-puja ${urgente ? 'urgente' : ''}" data-panel data-id="${esc(p.id)}" data-estado="${esc(p.estado)}">
        <div class="ficha-rareza ficha-rareza--grande ficha-rareza--desconocida">
          ${iconoHtml('moneda')}
        </div>
        <div class="fila-info-principal">
          <h3 class="fila-nombre">${nombre}</h3>
          <div class="fila-badges">
            <span class="badge badge-${estado.variante}">${estado.texto}</span>
          </div>
          ${meta.length ? `<p class="texto-pista fila-publicacion__meta">${meta.join(' · ')}</p>` : ''}
        </div>
        <div class="fila-oferta">
          <div class="etiqueta-sm">${etiquetaOferta}</div>
          <div class="fila-oferta-monto cifra">${formatearCreditos(p.oferta)} cr</div>
          <div class="postor-texto">${p.pujas > 0 ? `${p.pujas} ${p.pujas === 1 ? 'puja' : 'pujas'}` : 'Sin pujas'}</div>
        </div>
        <div class="fila-acciones-tiempo fila-publicacion__acciones">
          ${acciones}
        </div>
      </article>
    `;
  }

  /**
   * «Siguiendo» (7.7.9): tu lista de seguimiento, con lo que va cada una y
   * la forma de dejarla. Solo con servidor: sin él no hay lista.
   */
  generarHtmlSeccionSeguidas() {
    if (!this.panelDisponible('misSeguidas')) {
      return '';
    }
    const seguidas = this.panel.seguidas;
    const fallo = this.panel.fallos.includes('seguidas');
    let cuerpo = '';
    if (Array.isArray(seguidas) && seguidas.length === 0) {
      cuerpo = `
          <div class="estado-contenedor estado-vacio estado-vacio--compacto" data-estado="sin-seguidas">
            <h3 class="titulo-mediano">No sigues ninguna subasta</h3>
            <p>Abre una subasta y pulsa «Seguir esta subasta»: te avisaremos cuando alguien puje, cuando termine y una hora antes del cierre.</p>
            <button type="button" class="btn btn-primario" data-ir-a-explorar>Explorar subastas</button>
          </div>`;
    } else if (Array.isArray(seguidas)) {
      cuerpo = `<div class="lista-mis-subastas">
            ${seguidas.map((s) => this.generarFilaSeguida(s)).join('')}
          </div>`;
    } else if (!fallo && this.panel.cargando) {
      cuerpo = this.generarHtmlCargandoDelPanel('Trayendo tu lista de seguimiento…');
    }
    let aviso = '';
    if (fallo) {
      aviso = this.generarHtmlFalloDelPanel(
        Array.isArray(seguidas)
          ? 'No pudimos actualizar tu lista de seguimiento: lo que ves puede haber cambiado.'
          : 'No pudimos traer tu lista de seguimiento ahora mismo.',
      );
    }
    const contador = Array.isArray(seguidas)
      ? `<span class="contador-mis-subastas">${seguidas.length} ${seguidas.length === 1 ? 'subasta' : 'subastas'}</span>`
      : '';
    return `
        <!-- Lo que sigues -->
        <section class="seccion-mis-subastas" id="seccion-seguidas" aria-labelledby="titulo-seccion-seguidas">
          <div class="encabezado-mis-subastas">
            <h2 class="titulo-seccion" id="titulo-seccion-seguidas">Siguiendo</h2>
            ${contador}
          </div>
          ${aviso}
          ${cuerpo}
        </section>`;
  }

  /**
   * Una subasta de tu lista de seguimiento. Si además pujas en ella, se dice
   * cómo vas (lo que ya se sabe de tu participación).
   *
   * @param {ReturnType<typeof vistaDeSeguida>} s
   */
  generarFilaSeguida(s) {
    const estado = estadoDeSubasta(s.estado);
    const abierta = s.estado === 'ACTIVA';
    const tuya = this.participaciones.get(s.id);
    let comoVas = '';
    if (abierta && tuya?.ganando) {
      comoVas = '<span class="badge badge-exito">Vas ganando</span>';
    } else if (abierta && tuya?.superado) {
      comoVas = '<span class="badge badge-error">Te superaron</span>';
    }
    const nombre = esc(s.nombre);
    const tiempo = abierta
      ? `<div class="reloj-fila" data-tiempo-subasta="${esc(s.id)}" data-fin="${esc(s.fechaFin)}">${formatearTiempo(s.segundosRestantes ?? 0)}</div>
          <button type="button" class="btn btn-contorno btn-sm" data-abrir-panel="${esc(s.id)}" aria-label="Ver la subasta de ${nombre}">Ver subasta</button>`
      : '<p class="texto-pista fila-publicacion__cierre">Ya no está en curso.</p>';
    return `
      <article class="fila-mi-subasta fila-publicacion fila-seguida borde-sin-puja" data-panel data-id="${esc(s.id)}" data-estado="${esc(s.estado)}">
        <div class="ficha-rareza ficha-rareza--grande ficha-rareza--desconocida">
          ${iconoHtml('estrella')}
        </div>
        <div class="fila-info-principal">
          <h3 class="fila-nombre">${nombre}</h3>
          <div class="fila-badges">
            <span class="badge badge-${estado.variante}">${estado.texto}</span>
            ${comoVas}
          </div>
          ${s.seguidaDesde ? `<p class="texto-pista fila-publicacion__meta">La sigues desde: ${esc(momentoLegible(s.seguidaDesde))}</p>` : ''}
        </div>
        <div class="fila-oferta">
          <div class="etiqueta-sm">${s.pujas === 0 ? 'Precio mínimo' : 'Oferta vigente'}</div>
          <div class="fila-oferta-monto cifra">${formatearCreditos(s.oferta)} cr</div>
          <div class="postor-texto">${s.pujas > 0 ? `${s.pujas} ${s.pujas === 1 ? 'puja' : 'pujas'}` : 'Sin pujas'}</div>
        </div>
        <div class="fila-acciones-tiempo fila-publicacion__acciones">
          ${tiempo}
          <button type="button" class="btn btn-texto btn-sm" data-dejar-de-seguir="${esc(s.id)}" aria-label="Dejar de seguir la subasta de ${nombre}">Dejar de seguir</button>
        </div>
      </article>
    `;
  }

  /**
   * «Historial de transacciones» (7.7.9): lo ganado, lo gastado y el
   * balance que da el servidor, los movimientos del más reciente al más
   * antiguo y «Exportar CSV». Solo con servidor.
   */
  generarHtmlSeccionHistorial() {
    if (!this.panelDisponible('miHistorial')) {
      return '';
    }
    const historial = this.panel.historial;
    const fallo = this.panel.fallos.includes('historial');
    const movimientos = historial?.movimientos ?? [];
    let cuerpo = '';
    if (historial && movimientos.length === 0) {
      cuerpo = `
          <div class="estado-contenedor estado-vacio estado-vacio--compacto" data-estado="sin-movimientos">
            <h3 class="titulo-mediano">Todavía no tienes movimientos</h3>
            <p>Cuando ganes una subasta, vendas algo o pagues una comisión al publicar, aquí verás cada movimiento con su monto y tu balance.</p>
          </div>`;
    } else if (historial) {
      const visibles = this.verTodo.historial
        ? movimientos
        : movimientos.slice(0, MOVIMIENTOS_A_LA_VISTA);
      const conmutador =
        movimientos.length > MOVIMIENTOS_A_LA_VISTA
          ? `<button type="button" class="btn btn-texto btn-ver-todo" data-ver-todo="historial" aria-expanded="${this.verTodo.historial}">${this.verTodo.historial ? 'Ver solo los más recientes' : `Ver los ${movimientos.length} movimientos`}</button>`
          : '';
      cuerpo = `
          ${this.generarHtmlResumenDelHistorial(historial)}
          <div class="tabla-historial-marco" role="region" aria-label="Movimientos del historial" tabindex="0">
            <table class="tabla-historial" role="table">
              <caption class="solo-lectores">Tus movimientos en subastas, del más reciente al más antiguo</caption>
              <thead role="rowgroup">
                <tr role="row">
                  <th scope="col" role="columnheader">Fecha</th>
                  <th scope="col" role="columnheader">Movimiento</th>
                  <th scope="col" role="columnheader">Objeto</th>
                  <th scope="col" role="columnheader" class="tabla-historial__monto">Monto</th>
                </tr>
              </thead>
              <tbody role="rowgroup">
                ${visibles
                  .map(
                    (m) => `
                <tr role="row" data-tipo="${esc(m.tipo)}">
                  <td role="cell" class="tabla-historial__fecha">${esc(momentoLegible(m.fecha))}</td>
                  <td role="cell" class="tabla-historial__tipo"><span class="badge ${m.entra ? 'badge-exito' : 'badge-neutral'}">${m.texto}</span></td>
                  <td role="cell" class="tabla-historial__objeto">${esc(m.nombre)}</td>
                  <td role="cell" class="tabla-historial__monto cifra ${m.entra ? 'monto-entra' : 'monto-sale'}">${montoConSigno(m.monto, m.entra)}</td>
                </tr>`,
                  )
                  .join('')}
              </tbody>
            </table>
          </div>
          ${conmutador}`;
    } else if (!fallo && this.panel.cargando) {
      cuerpo = this.generarHtmlCargandoDelPanel('Trayendo tu historial…');
    }
    let aviso = '';
    if (fallo) {
      aviso = this.generarHtmlFalloDelPanel(
        historial
          ? 'No pudimos actualizar tu historial: lo que ves puede haber cambiado.'
          : 'No pudimos traer tu historial ahora mismo.',
      );
    }
    const exportar =
      movimientos.length > 0
        ? `<button type="button" class="btn btn-contorno btn-sm" id="btn-exportar-historial" ${this.exportando ? 'disabled' : ''}>${this.exportando ? 'Preparando el archivo…' : 'Exportar CSV'}</button>`
        : '';
    const contador = historial
      ? `<span class="contador-mis-subastas">${movimientos.length} ${movimientos.length === 1 ? 'movimiento' : 'movimientos'}</span>`
      : '';
    return `
        <!-- Tu historial -->
        <section class="seccion-mis-subastas" id="seccion-historial" aria-labelledby="titulo-seccion-historial">
          <div class="encabezado-mis-subastas">
            <h2 class="titulo-seccion" id="titulo-seccion-historial">Historial</h2>
            ${contador}
            <div style="flex-grow: 1;"></div>
            ${exportar}
          </div>
          ${aviso}
          ${cuerpo}
        </section>`;
  }

  /**
   * Las cuatro cifras del historial tal como las da el servidor. El signo va
   * escrito; el color lo acompaña.
   *
   * @param {ReturnType<typeof vistaDeHistorial>} historial
   */
  generarHtmlResumenDelHistorial(historial) {
    const balance = historial.balance;
    let claseBalance = '';
    if (balance > 0) {
      claseBalance = 'monto-entra';
    } else if (balance < 0) {
      claseBalance = 'monto-sale';
    }
    return `
          <dl class="resumen-historial">
            <div class="resumen-historial__dato">
              <dt>Ganado en ventas</dt>
              <dd class="cifra monto-entra">${montoConSigno(historial.totalGanado, true)}</dd>
            </div>
            <div class="resumen-historial__dato">
              <dt>Gastado</dt>
              <dd class="cifra monto-sale">${montoConSigno(historial.totalGastado, false)}</dd>
              <dd class="resumen-historial__pista">Compras, comisiones y penalizaciones</dd>
            </div>
            <div class="resumen-historial__dato">
              <dt>Comisiones y penalizaciones</dt>
              <dd class="cifra">${formatearCreditos(historial.comisionesPagadas)} cr</dd>
            </div>
            <div class="resumen-historial__dato resumen-historial__dato--balance">
              <dt>Balance</dt>
              <dd class="cifra ${claseBalance}">${balance === null ? '—' : montoConSigno(Math.abs(balance), balance >= 0)}</dd>
            </div>
          </dl>`;
  }

  /**
   * El saldo, lo retenido y lo libre. Sin saldo del servidor no se dibuja la
   * barra: repartir un total que no se sabe es inventarlo.
   */
  generarHtmlPanelCreditos({ total, libre, retenidoReal, participando, sobreCompromiso }) {
    const saldoConocido = total !== null && total !== undefined;
    const conRetencion = participando.filter((s) => (s.retenido || 0) > 0);
    // El tramo i-esimo de la barra de credito comprometido. La rampa sale del
    // oro del producto oscureciendose contra el cromo: es monotona, se nota el
    // orden y no entra ningun color nuevo. El color no lleva informacion por
    // si solo — cada tramo tiene su `title` y su entrada en la leyenda.
    const tramoColor = (i) =>
      `color-mix(in srgb, var(--credito-oro) ${100 - (i % 6) * 15}%, var(--cromo))`;
    const ancho = (monto) => `${total > 0 ? ((monto / total) * 100).toFixed(1) : '0'}%`;
    const tramos = conRetencion.map((sub, i) => ({
      ancho: ancho(sub.retenido),
      color: tramoColor(i),
      titulo: `${sub.nombre}: ${formatearCreditos(sub.retenido)} cr`,
      leyenda: `${sub.nombre.split(' ')[0]} · ${formatearCreditos(sub.retenido)} cr`,
    }));
    // Lo que el servidor dice retenido y no está en las subastas cargadas.
    const sumado = conRetencion.reduce((acc, s) => acc + (s.retenido || 0), 0);
    const enOtras = Math.max(0, (retenidoReal || 0) - sumado);
    if (enOtras > 0) {
      tramos.push({
        ancho: ancho(enOtras),
        color: 'var(--texto-3)',
        titulo: `En otras subastas: ${formatearCreditos(enOtras)} cr`,
        leyenda: `Otras subastas · ${formatearCreditos(enOtras)} cr`,
      });
    }
    if (saldoConocido) {
      tramos.push({
        ancho: ancho(libre),
        color: 'var(--exito)',
        titulo: `Libre: ${formatearCreditos(libre)} cr`,
        leyenda: `Libre · ${formatearCreditos(libre)} cr`,
      });
    }

    return `
        <section class="panel-creditos-segmentada" aria-label="Tus créditos en las subastas">
          <h2 class="titulo-mediano">Tus créditos</h2>

          <div class="creditos-tarjetas-grid">
            <div class="tarjeta-credito tarjeta-credito-total">
              <div class="tarjeta-credito-etiqueta">Tienes en total</div>
              <div class="tarjeta-credito-valor cifra">${formatearCreditos(total)} cr</div>
            </div>
            <div class="tarjeta-credito tarjeta-credito-retenido">
              <div class="tarjeta-credito-etiqueta">Retenido en subastas</div>
              <div class="tarjeta-credito-valor tarjeta-credito-valor--retenido cifra">${formatearCreditos(retenidoReal)} cr</div>
            </div>
            <div class="tarjeta-credito ${saldoConocido && libre < 500 ? 'tarjeta-credito-libre--baja' : 'tarjeta-credito-libre'}">
              <div class="tarjeta-credito-etiqueta">Libre para pujar</div>
              <div class="tarjeta-credito-valor ${saldoConocido && libre < 500 ? 'tarjeta-credito-valor--libre-baja' : 'tarjeta-credito-valor--libre'} cifra">${formatearCreditos(libre)} cr</div>
            </div>
          </div>

          ${
            saldoConocido
              ? `
          <div class="barra-segmentada-tramos" role="img"
               aria-label="${esc(`${formatearCreditos(retenidoReal)} cr retenidos de ${formatearCreditos(total)} cr`)}">
            ${tramos
              .map(
                (t) => `
              <div class="tramo-subasta" style="width: ${t.ancho}; background: ${t.color};" title="${esc(t.titulo)}"></div>
            `,
              )
              .join('')}
          </div>

          <div class="leyenda-tramos">
            ${tramos
              .map(
                (t) => `
              <span class="item-leyenda">
                <span class="leyenda-punto" style="background: ${t.color};"></span>
                <span>${esc(t.leyenda)}</span>
              </span>
            `,
              )
              .join('')}
          </div>`
              : `
          <p class="texto-pista nota-saldo" data-zona="saldo-desconocido">
            Tu saldo no llegó ahora mismo, así que no podemos repartirlo. Puedes pujar igual: al hacerlo, el mercado comprueba si te alcanza.
          </p>`
          }

          ${
            sobreCompromiso.sobreCompromiso
              ? `
            <div class="alerta-sobrecompromiso" role="alert">
              <div class="sobrecompromiso-icono">${iconoHtml('alerta')}</div>
              <div>
                <div class="sobrecompromiso-titulo">Tus automáticas prometen más de lo que tienes</div>
                <div class="sobrecompromiso-texto">
                  Si todas llegaran a su tope harían falta <strong class="cifra">${formatearCreditos(sobreCompromiso.sumaTopes)} cr</strong>,
                  y solo tienes <strong class="cifra">${formatearCreditos(total)} cr</strong>. Las últimas en responder fallarán. Baja algún tope o desactiva una.
                </div>
              </div>
            </div>
          `
              : ''
          }
        </section>`;
  }

  /** Un medidor de tope; el color siempre va con la cifra y el texto. */
  generarHtmlTope({ nombre, estado }) {
    const color = segunTope(estado, 'var(--error)', 'var(--advertencia)', 'var(--exito)');
    return `
          <div class="tarjeta-tope ${segunTope(estado, 'tarjeta-tope--critico', 'tarjeta-tope--alerta', '')}">
            <div class="tope-cabecera">
              <span class="tope-nombre">${nombre}</span>
              <span class="tope-cifra cifra" style="color: ${color}">
                ${estado.actual} de ${estado.max}
              </span>
            </div>
            <div class="tope-barra-fondo">
              <div class="tope-barra-progreso" style="width: ${Math.min(100, estado.ratio * 100)}%; background: ${color};"></div>
            </div>
            <div class="tope-alerta-texto" style="color: ${segunTope(estado, 'var(--error)', 'var(--advertencia)', 'var(--texto-3)')}">
              ${estado.pista}
            </div>
          </div>`;
  }

  /** Con servidor, cuántas subastas abiertas se revisaron (el alcance real). */
  generarHtmlAlcance() {
    // Con «Mis pujas» del servidor la lista es completa: no hay alcance que
    // explicar.
    if (!this.api || Array.isArray(this.panel.pujas)) {
      return '';
    }
    if (this.consultandoParticipaciones && !this.participacionesRevisadas) {
      return `<p class="texto-pista nota-alcance" role="status">Revisando tus pujas en las ${this.subastas.length} subastas abiertas…</p>`;
    }
    return `<p class="texto-pista nota-alcance">Revisamos tus pujas en las ${this.subastas.length} subastas abiertas del mercado.</p>`;
  }

  /** Vacío con salida: qué pasa, por qué y qué hacer. */
  generarHtmlSinPujas(revisando) {
    if (revisando) {
      return '';
    }
    return `
          <div class="estado-contenedor estado-vacio estado-vacio--compacto" data-estado="sin-pujas">
            <h3 class="titulo-mediano">No estás pujando en ninguna subasta abierta</h3>
            <p>Cuando pujes o dejes una puja automática, la subasta aparecerá aquí con lo que tienes retenido.</p>
            <button type="button" class="btn btn-primario" id="btn-mis-a-explorar">Explorar subastas</button>
          </div>`;
  }

  /** Una subasta tuya a la venta: cómo va, sin botones de pujar. */
  generarFilaPublicacion(sub) {
    const urgente = sub.segundosRestantes <= 10 && sub.segundosRestantes > 0;
    const rz = rarezaVisible(sub.rareza);
    const pujas = Number(sub.rivales) || 0;
    return `
      <article class="fila-mi-subasta fila-publicacion borde-sin-puja ${urgente ? 'urgente' : ''}" data-id="${esc(sub.id)}">
        <div class="ficha-rareza ficha-rareza--grande ficha-rareza--${rz.clase}">
          ${iconoHtml(rz.simbolo)}
        </div>
        <div class="fila-info-principal">
          <h3 class="fila-nombre">${esc(sub.nombre)}</h3>
          <div class="fila-badges">
            ${rz.conocida ? `<span class="badge badge-${rz.clase}">${rz.texto}</span>` : ''}
            <span class="badge badge-propia">${iconoHtml('usuario', { clase: 'icono icono--menudo' })} Tu subasta</span>
          </div>
        </div>
        <div class="fila-oferta">
          <div class="etiqueta-sm">Oferta vigente</div>
          <div class="fila-oferta-monto cifra">${formatearCreditos(sub.oferta)} cr</div>
          <div class="postor-texto">${pujas > 0 ? `${pujas} ${pujas === 1 ? 'puja' : 'pujas'}` : 'Sin pujas todavía'}</div>
        </div>
        <div class="fila-acciones-tiempo">
          <div class="reloj-fila ${urgente ? 'animacion-latido' : ''}" data-tiempo-subasta="${esc(sub.id)}">${formatearTiempo(sub.segundosRestantes)}</div>
          <button type="button" class="btn btn-contorno btn-ver-subasta">Ver subasta</button>
        </div>
      </article>
    `;
  }

  /**
   * B8 — «Productos pendientes de recoger» (7.7.9): lo ganado al vencer una
   * subasta, ya pagado, que se recoge antes de 7 dias. Solo si hay algo.
   */
  generarHtmlPendientes() {
    if (!this.pendientes.length) {
      return '';
    }
    const dias = this.reglas?.diasParaRecoger ?? 7;
    return `
        <section class="seccion-mis-subastas seccion-pendientes" id="seccion-pendientes" aria-label="Productos pendientes de recoger">
          <div class="encabezado-mis-subastas">
            <h2 class="titulo-seccion">Pendientes de recoger</h2>
            <span class="contador-mis-subastas">${this.pendientes.length} producto(s)</span>
            <button type="button" class="btn btn-contorno btn-recoger-todo" id="btn-recoger-todo" ${this.enviando ? 'disabled' : ''}>Recoger todo</button>
          </div>
          <p class="texto-pista">Ganaste y pagaste estos productos. Tienes ${formatearCreditos(dias)} días para recogerlos.</p>
          <ul class="lista-mis-subastas">
            ${this.pendientes
              .map(
                (p) => `
              <li class="fila-mi-subasta fila-pendiente" data-pendiente="${esc(p.subastaId)}">
                <span><strong>${esc(p.nombreProducto || 'Producto ganado')}</strong> · ${formatearCreditos(Number(p.montoPagado))} cr</span>
                <span class="texto-pista">Vence: ${esc(new Date(p.venceEn).toLocaleString('es-CO'))}</span>
                <button type="button" class="btn btn-primario btn-recoger-pendiente" data-subasta="${esc(p.subastaId)}" ${this.enviando ? 'disabled' : ''}>Recoger</button>
              </li>
            `,
              )
              .join('')}
          </ul>
        </section>`;
  }

  generarFilaMiSubasta(sub) {
    const urgente = sub.segundosRestantes <= 10 && sub.segundosRestantes > 0;
    const rz = rarezaVisible(sub.rareza);

    let claseBorde = 'borde-sin-puja';
    let badgeEstado = '<span class="badge badge-neutral">Sin pujar</span>';
    let textoBoton = 'Ver subasta';
    let claseBoton = 'btn-contorno btn-ver-subasta';

    if (sub.ganando) {
      claseBorde = 'borde-ganando';
      badgeEstado = '<span class="badge badge-exito">Vas ganando</span>';
    } else if (sub.superado) {
      claseBorde = 'borde-superada';
      badgeEstado = '<span class="badge badge-error">Te superaron</span>';
    }

    if (sub.superado) {
      textoBoton = 'Recuperarla';
      claseBoton = 'btn-primario btn-recuperar';
    } else if (urgente) {
      textoBoton = 'Ir ahora';
      claseBoton = 'btn-primario btn-ir-ahora animacion-latido';
    } else {
      textoBoton = 'Ver subasta';
      claseBoton = 'btn-contorno btn-ver-subasta';
    }

    return `
      <article class="fila-mi-subasta ${claseBorde} ${urgente ? 'urgente' : ''}" data-id="${sub.id}">
        <div class="ficha-rareza ficha-rareza--grande ficha-rareza--${rz.clase}">
          ${iconoHtml(rz.simbolo)}
        </div>

        <div class="fila-info-principal">
          <h3 class="fila-nombre">${esc(sub.nombre)}</h3>
          <div class="fila-badges">
            ${rz.conocida ? `<span class="badge badge-${rz.clase}">${rz.texto}</span>` : ''}
            ${badgeEstado}
            ${
              sub.autoLimite > 0
                ? `
              <span class="chip-automatica-tope" title="Puja automática configurada">
                ${iconoHtml('rayo', { clase: 'icono icono--menudo' })} hasta ${formatearCreditos(sub.autoLimite)} cr
              </span>
            `
                : ''
            }
          </div>
        </div>

        <div class="fila-oferta">
          <div class="etiqueta-sm">Oferta vigente</div>
          <div class="fila-oferta-monto cifra" style="color: var(--advertencia);">${formatearCreditos(sub.oferta)} cr</div>
          <div class="postor-texto ${sub.ganando ? 'texto-exito' : ''}">
            ${this.textoDeLaFila(sub)}
          </div>
        </div>

        <div class="fila-retenido">
          <div class="etiqueta-sm">Tú tienes retenido</div>
          <div class="fila-retenido-monto cifra" style="color: ${sub.retenido > 0 ? 'var(--advertencia)' : 'var(--texto-3)'};">
            ${sub.retenido > 0 ? `${formatearCreditos(sub.retenido)} cr` : '—'}
          </div>
        </div>

        <div class="fila-acciones-tiempo">
          <div class="reloj-fila ${urgente ? 'animacion-latido' : ''}" data-tiempo-subasta="${sub.id}">
            ${iconoHtml('reloj', { clase: 'icono icono--menudo' })} <span class="cifra">${formatearTiempo(sub.segundosRestantes)}</span>
          </div>
          <button type="button" class="btn ${claseBoton}" data-abrir="${sub.id}">
            ${textoBoton}
          </button>
        </div>
      </article>
    `;
  }

  generarHtmlCierreMultiple({ total }) {
    const eventos = this.eventosCierre;
    // FI-R1 — sin desenlaces no se dice «0 CERRARON». Cero cierres es una
    // afirmacion sobre lo que paso con tus subastas, y hoy nadie la sostiene:
    // ms-subastas no publica ningun recurso de cierres (MisPujasController
    // solo tiene /resumen). Lo que corresponde decir es que no hay de donde
    // sacarlo.
    if (eventos.length === 0) {
      return `
      <div class="subastas-app vista-cierre-multiple">
        ${this.generarHtmlPestanas({ superadas: 0 })}
        ${this.generarHtmlAlerta()}

        <div class="estado-contenedor estado-vacio">
          <h1 class="titulo-mediano">Todavía no hay resultados de cierre</h1>
          <p>
            El mercado no guarda todavía cómo terminaron las subastas en las que pujaste.
            Si alguien compra de inmediato una en la que pujabas, o tu puja automática se
            detiene, te llega un aviso a tus notificaciones.
          </p>
          <div class="acciones-vacio">
            <a class="btn btn-primario" href="../plataforma/notificaciones/notificaciones.html">Ver mis notificaciones</a>
            <button type="button" class="btn btn-contorno" id="btn-cierre-a-mis-subastas">Ver mis subastas</button>
          </div>
        </div>
      </div>
    `;
    }
    const balance = calcularBalanceNetoCierre(eventos, total);
    const ganadas = eventos.filter((e) => e.esGanador).length;
    const superadas = eventos.filter((e) => !e.esGanador).length;

    const eventoConTope =
      eventos.find((e) => e.tipoDesenlace === 'superada_tope') || eventos.find((e) => !e.esGanador);
    const consejo = generarConsejoTactico(eventoConTope, balance.saldoLibre);

    return `
      <div class="subastas-app vista-cierre-multiple">
        ${this.generarHtmlPestanas({ superadas: 0 })}
        ${this.generarHtmlAlerta()}

        <div class="panel-cierre-multiple">
          <div class="cierre-titular-bloque">
            <h1 class="cierre-titular">${eventos.length} CERRARON</h1>
            <p class="cierre-subtitulo">Ganaste ${ganadas}, te superaron en ${superadas}. Esto es lo que cambió en tu saldo.</p>
          </div>

          <!-- Resumen de Balance Neto -->
          <div class="cierre-neto-grid" aria-label="Balance financiero neto del cierre">
            <div class="caja-neto">
              <span class="cifra-neto cifra-neto--cobrado cifra">−${formatearCreditos(balance.cobrado)}</span>
              <span class="etiqueta-neto">cobrado</span>
            </div>
            <div class="caja-neto">
              <span class="cifra-neto cifra-neto--devuelto cifra">+${formatearCreditos(balance.devuelto)}</span>
              <span class="etiqueta-neto">devuelto</span>
            </div>
            <div class="caja-neto caja-neto--libre">
              <span class="cifra-neto cifra-neto--libre cifra">${formatearCreditos(balance.saldoLibre)}</span>
              <span class="etiqueta-neto">libre ahora</span>
            </div>
          </div>

          <!-- Filas de desenlaces -->
          <div class="lista-eventos-cierre">
            ${eventos
              .map((ev) => {
                const rz = rarezaVisible(ev.rareza);
                let claseEvento = 'evento--superada-rival';
                let montoHtml = `<div class="evento-cifra cifra" style="color: var(--exito);">+${formatearCreditos(ev.montoDevuelto)}</div><div class="etiqueta-sm">devuelto</div>`;
                let btnAccion = `<button type="button" class="btn btn-contorno btn-sm btn-buscar-parecidas" data-id="${ev.id}">Parecidas</button>`;

                if (ev.tipoDesenlace === 'adjudicada') {
                  claseEvento = 'evento--adjudicada';
                  montoHtml = `<div class="evento-cifra cifra" style="color: var(--advertencia);">−${formatearCreditos(ev.montoCobrado)}</div><div class="etiqueta-sm">cobrado</div>`;
                  btnAccion = `<button type="button" class="btn btn-acento btn-sm btn-ver-adjudicada" data-id="${ev.id}">Ver</button>`;
                } else if (ev.tipoDesenlace === 'superada_tope') {
                  claseEvento = 'evento--superada-tope';
                }

                return `
                <div class="fila-evento-cierre ${claseEvento}">
                  <div class="ficha-rareza ficha-rareza--${rz.clase}">
                    ${iconoHtml(rz.simbolo)}
                  </div>
                  <div class="evento-info">
                    <h3 class="evento-titulo">${esc(ev.nombre)}</h3>
                    <div class="evento-motivo">${esc(ev.motivo)}</div>
                  </div>
                  <div class="evento-monto">
                    ${montoHtml}
                  </div>
                  <div class="evento-accion">
                    ${btnAccion}
                  </div>
                </div>
              `;
              })
              .join('')}
          </div>

          <!-- Caja de Consejo Táctico Personalizado -->
          ${
            consejo
              ? `
            <div class="caja-consejo-tactico" role="region" aria-label="Consejo táctico">
              <div class="consejo-icono">${iconoHtml('rayo')}</div>
              <div class="consejo-contenido">
                <div class="consejo-titulo">${esc(consejo.titulo)}</div>
                <div>${consejo.cuerpo}</div>
              </div>
            </div>
          `
              : ''
          }

          <!-- Botones de Navegación del Cierre -->
          <div class="cierre-acciones-pie">
            <button type="button" class="btn btn-primario" id="btn-cierre-a-mis-subastas">Ver mis subastas</button>
            <button type="button" class="btn btn-contorno" id="btn-cierre-a-explorar">Al listado</button>
          </div>
        </div>
      </div>
    `;
  }

  generarHtmlDetalle({ libre, superadas = 0 }) {
    const sub = this.getSubastaActiva();
    const hero = this.getHeroeActivo();
    const comp = calcularComparacionHeroe(hero, sub);
    const rz = rarezaVisible(sub.rareza);
    // B8 — la puja minima la dice la ficha del servidor; sin ella se calcula
    // (la primera puja es el precio minimo, sin incremento). Null: no se sabe
    // desde aqui (incremento sin configurar) y los campos arrancan en la
    // oferta vigente; el servidor dira si no llega.
    const minimo = minimoDePuja(sub, this.config.incrementoMinimo);
    const minPuja = minimo ?? sub.oferta;
    let etiquetaMinimo = 'mínimo';
    if (sub.rivales === 0 && minimo !== null) {
      etiquetaMinimo = 'precio mínimo';
    } else if (minimo !== null) {
      etiquetaMinimo = `+${formatearCreditos(minimo - sub.oferta)}`;
    }
    const compraAgotada = sub.compraInmediataDisponible === false;
    const conSesion = Boolean(this.api && this.leerToken?.());
    // Null si no se sabe el saldo: entonces decide el servidor al pujar.
    const disponibleAqui =
      libre === null || libre === undefined ? null : libre + (sub.retenido || 0);
    const faltaParaComprar =
      disponibleAqui !== null &&
      sub.compraInmediata !== null &&
      sub.compraInmediata !== undefined &&
      disponibleAqui < sub.compraInmediata;
    let textoCompra = 'Comprarla ya';
    if (compraAgotada) {
      textoCompra = 'Ya no disponible';
    } else if (faltaParaComprar) {
      textoCompra = `Faltan ${formatearCreditos(sub.compraInmediata - disponibleAqui)} cr`;
    }
    const cerrada = sub.segundosRestantes <= 0 || this.resultadoCierre !== null;
    const urgente = sub.segundosRestantes <= 10 && !cerrada;
    // UXC-8 — en tu propia subasta no se puja (el servidor lo rechaza con
    // PUJA_PROPIA): se dice antes, en vez de dejar pulsar y rechazar.
    const bloqueada = cerrada || this.esMiSubasta(sub);
    const hayCompraInmediata = sub.compraInmediata !== null && sub.compraInmediata !== undefined;

    const subastasOtras = this.subastas.filter((s) => s.id !== sub.id);
    const otrasConRetenido = subastasOtras.filter((s) => (s.retenido || 0) > 0).length;
    // Con resumen del servidor, lo retenido en otras sale de ahí (cubre las
    // que no están cargadas); sin él, de lo que se sabe.
    const retenidoEnOtras = this.resumen
      ? Math.max(0, this.getRetenidoReal() - (sub.retenido || 0))
      : subastasOtras.reduce((acc, s) => acc + (s.retenido || 0), 0);

    const textoVolver = destinoDeVuelta(this.origenVista).texto;

    return `
      <div class="subastas-app vista-detalle">
        ${this.generarHtmlPestanas({ superadas })}
        ${this.generarHtmlAlerta()}

        <!-- Barra de navegación contextual -->
        <div class="barra-volver">
          <button type="button" class="btn btn-texto" id="btn-volver">
            ${textoVolver}
          </button>
          <span class="separador-pipe">|</span>
          <span class="saldo-contextual">Retenido en esta subasta: <strong>${formatearCreditos(sub.retenido || 0)} cr</strong></span>
          ${
            conSesion
              ? `<button type="button" class="btn btn-texto" id="btn-seguir" aria-pressed="${Boolean(sub.siguiendo)}">
            ${sub.siguiendo ? 'Dejar de seguir' : 'Seguir esta subasta'}
          </button>`
              : ''
          }
          <button type="button" class="btn btn-texto" id="btn-compartir">Compartir</button>
          ${this.generarHtmlCancelar(sub, { conSesion, cerrada })}
        </div>

        <!-- Alerta de resultado final si cerró o se adjudicó -->
        ${
          this.resultadoCierre === 'comprada' || this.resultadoCierre === 'adjudicada'
            ? `
          <div class="alerta cierre-victoria" role="alert" tabindex="-1">
            <h2 class="titulo-grande cierre-victoria__titulo">¡ES TUYA!</h2>
            <p class="cierre-victoria__texto">
              ${this.resultadoCierre === 'comprada' ? '¡Has comprado este objeto de inmediato!' : 'La subasta cerró exitosamente y el objeto ha sido adjudicado a tu inventario.'}
            </p>
            <div class="cierre-victoria__cifras">
              <div class="cierre-victoria__dato">
                <span class="cierre-victoria__etiqueta">Monto pagado:</span>
                <strong class="cifra cierre-victoria__monto">${formatearCreditos(this.resultadoCierre === 'comprada' ? sub.compraInmediata : sub.oferta)} cr</strong>
              </div>
              ${
                libre === null || libre === undefined
                  ? ''
                  : `<div class="cierre-victoria__dato">
                <span class="cierre-victoria__etiqueta">Saldo libre resultante:</span>
                <strong class="cifra cierre-victoria__monto">${formatearCreditos(libre)} cr</strong>
              </div>`
              }
            </div>
            <div class="cierre-victoria__acciones">
              <button type="button" class="btn btn-primario" id="btn-resultado-mis-subastas">Ver mis subastas</button>
              <button type="button" class="btn btn-contorno" id="btn-resultado-explorar">Al listado</button>
            </div>
          </div>
        `
            : ''
        }

        <div class="detalle-grid">
          <!-- Columna Izquierda: Información del Objeto y Comparativa con Héroe -->
          <section class="columna-info-objeto">
            <div class="panel-objeto">
              <div class="objeto-badges">
                ${rz.conocida ? `<span class="badge badge-${rz.clase}">${rz.texto}</span>` : ''}
                ${this.insigniaDeVendedor(sub)}
                ${
                  Number.isFinite(sub.nivel)
                    ? `<span class="badge ${comp.nivelInsuficiente ? 'badge-error' : 'badge-exito'}">
                  Req. Nivel ${sub.nivel}
                </span>`
                    : ''
                }
              </div>

              <h1 class="titulo-grande titulo-objeto">${esc(sub.nombre)}</h1>
              <p class="objeto-tipo">${esc(tipoLegible(sub.tipo))}</p>
              <p class="objeto-descripcion">${esc(sub.descripcion)}</p>
              ${this.generarHtmlDatosDeLaFicha(sub)}

              <!-- Comparativa con el héroe. FI-R1 — este bloque entero depende de
                   datos que el contrato de subastas NO trae (nivel requerido y
                   aporte del objeto) y de un héroe que esta pantalla todavia no
                   carga de ningún servicio. Mientras falten, se dice; no se
                   rellena con un héroe de ejemplo ni con ceros. -->
              ${
                comp.evaluado
                  ? `
              <div class="selector-heroes-seccion">
                <span class="etiqueta-sm">Comparar compatibilidad con héroe activo:</span>
                <div class="selector-heroes-botones" role="radiogroup" aria-label="Elegir héroe">
                  ${this.heroes
                    .map(
                      (h) => `
                    <button type="button" class="btn-heroe-chip ${h.id === this.heroeId ? 'heroe-elegido' : ''}" data-heroe="${h.id}" role="radio" aria-checked="${h.id === this.heroeId}">
                      <strong>${esc(h.nombre)}</strong> (Niv. ${esc(h.nivel)} · ${esc(h.clase)})
                    </button>
                  `,
                    )
                    .join('')}
                </div>
              </div>

              ${
                comp.nivelInsuficiente
                  ? `
                <div class="alerta alerta-advertencia" role="alert">
                  <strong>${iconoHtml('alerta', { clase: 'icono icono--menudo' })} Nivel insuficiente:</strong> ${esc(hero.nombre)} es nivel ${esc(hero.nivel)}. Le faltan ${comp.deltaNivel} niveles para poder equipar este objeto.
                </div>
              `
                  : `
                <div class="alerta alerta-exito-suave">
                  <strong>${iconoHtml('check', { clase: 'icono icono--menudo' })} Compatible:</strong> ${esc(hero.nombre)} cumple el nivel requerido para equipar este objeto.
                </div>
              `
              }

              <div class="tabla-comparacion-contenedor">
                <table class="tabla-comparacion">
                  <thead>
                    <tr>
                      <th>Atributo</th>
                      <th>Actual (${esc(hero.nombre)})</th>
                      <th>Con ${esc(sub.nombre.split(' ')[0])}</th>
                      <th>Diferencia</th>
                    </tr>
                  </thead>
                  <tbody>
                    ${comp.comparaciones
                      .map(
                        (c) => `
                      <tr>
                        <td><strong>${c.stat}</strong></td>
                        <td class="cifra">${c.actual}</td>
                        <td class="cifra">${c.nuevo}</td>
                        <td>
                          <span class="badge ${segunDelta(c.delta, 'badge-exito', 'badge-error', 'badge-neutral')}">
                            ${segunDelta(c.delta, `+${c.delta}`, c.delta, 'igual')}
                          </span>
                        </td>
                      </tr>
                    `,
                      )
                      .join('')}
                  </tbody>
                </table>
              </div>`
                  : this.avisoSinComparativa(sub)
              }
            </div>

            <!-- Historial de Pujas -->
            <div class="panel-historial">
              <h3 class="titulo-mediano">Historial de pujas (${sub.historial.length})</h3>
              ${this.vacioDelHistorial(sub)}
              <ul class="lista-historial">
                ${sub.historial
                  .map(
                    (p) => `
                  <li class="item-historial ${p.esTu ? 'historial-propio' : ''}">
                    <span class="historial-postor ${p.esTu ? 'postor-tu' : ''}">${p.esTu ? 'Tú' : esc(p.apodo)}</span>
                    <span class="historial-tipo">${esc(p.tipo)}</span>
                    <time class="historial-cuando" datetime="${esc(p.cuando)}">${esc(momentoLegible(p.cuando) || p.cuando)}</time>
                    <span class="historial-monto cifra"><strong>${formatearCreditos(p.monto)} cr</strong></span>
                  </li>
                `,
                  )
                  .join('')}
              </ul>
            </div>
          </section>

          <!-- Columna Derecha: Panel de Acción y Pujas -->
          <section class="columna-acciones-puja">
            <div class="panel-puja ${urgente ? 'panel-urgente' : ''}">
              <div class="cabecera-panel-puja">
                <div>
                  <span class="etiqueta-sm">Oferta actual</span>
                  <div class="precio-actual cifra">${formatearCreditos(sub.oferta)} cr</div>
                  <span class="postor-actual ${sub.ganando ? 'texto-exito' : ''}">
                    ${this.textoDelPostor(sub)}
                  </span>
                </div>
                <div class="reloj-cierre">
                  <span class="etiqueta-sm">Cierre en</span>
                  <div class="tiempo-cierre cifra ${urgente ? 'tiempo-urgente animacion-latido' : ''}" data-tiempo-subasta="${sub.id}">
                    ${formatearTiempo(sub.segundosRestantes)}
                  </div>
                </div>
              </div>

              ${
                this.esMiSubasta(sub)
                  ? `<p class="alerta alerta-informativa aviso-subasta-propia" role="note">
                <strong>Es tu subasta.</strong> Aquí ves cómo va; no puedes pujar en ella ni comprarla.
              </p>`
                  : ''
              }

              <!-- Atajos de Puja Rápida -->
              <div class="seccion-bloque">
                <span class="etiqueta-sm">Atajos de puja en un toque (+incremento):</span>
                <div class="atajos-grid">
                  <button type="button" class="btn btn-contorno btn-atajo" data-monto="${minPuja}" ${bloqueada ? 'disabled' : ''}>
                    Pujar ${formatearCreditos(minPuja)} cr (${etiquetaMinimo})
                  </button>
                  <button type="button" class="btn btn-contorno btn-atajo" data-monto="${sub.oferta + 100}" ${bloqueada ? 'disabled' : ''}>
                    Pujar ${formatearCreditos(sub.oferta + 100)} cr (+100)
                  </button>
                  <button type="button" class="btn btn-contorno btn-atajo" data-monto="${sub.oferta + 200}" ${bloqueada ? 'disabled' : ''}>
                    Pujar ${formatearCreditos(sub.oferta + 200)} cr (+200)
                  </button>
                </div>
              </div>

              <!-- Oferta Manual -->
              <div class="seccion-bloque">
                <label for="input-monto-puja" class="etiqueta-sm">Oferta manual (mínimo ${minimo === null ? 'según el servidor' : `${formatearCreditos(minPuja)} cr`}):</label>
                <div class="campo-con-boton">
                  <input type="number" id="input-monto-puja" class="input-estandar" min="${minPuja}" step="10" value="${this.montoPersonalizado || minPuja}" ${bloqueada ? 'disabled' : ''}>
                  <button type="button" id="btn-pujar-manual" class="btn btn-primario" ${bloqueada ? 'disabled' : ''}>
                    ${sub.esperaSegundos > 0 ? `Espera ${sub.esperaSegundos} s` : 'Pujar'}
                  </button>
                </div>
                <span class="texto-pista">Intervalo regulado de ${formatearCreditos(this.config.intervaloSegundos)} s entre pujas del mismo jugador en esta subasta.</span>
                ${this.generarHtmlNotaIncremento(sub)}
              </div>

              <!-- Compra Inmediata: solo si la subasta la admite -->
              ${
                hayCompraInmediata
                  ? `
              <div class="seccion-bloque bloque-compra-inmediata">
                <div class="fila-compra">
                  <div>
                    <span class="etiqueta-sm">Compra directa</span>
                    <div class="precio-compra cifra">${formatearCreditos(sub.compraInmediata)} cr</div>
                  </div>
                  <button type="button" id="btn-solicitar-compra" class="btn btn-acento" ${bloqueada || compraAgotada || faltaParaComprar ? 'disabled' : ''}>
                    ${textoCompra}
                  </button>
                </div>
                ${
                  compraAgotada
                    ? '<p class="texto-pista">Una puja ya alcanzó el precio de compra inmediata: comprar por debajo le quitaría el objeto al mejor postor.</p>'
                    : ''
                }
              </div>`
                  : ''
              }

              <!-- Puja Automática -->
              <div class="seccion-bloque bloque-automatica">
                <h4 class="titulo-pequeno">Puja automática con tope máximo</h4>
                <p class="texto-pista">El sistema pujará automáticamente el incremento mínimo cuando otro jugador te supere, hasta tu límite.</p>
                ${
                  sub.autoLimite > 0
                    ? `
                  <div class="auto-activa-aviso">
                    <span>Tope activo: <strong>${formatearCreditos(sub.autoLimite)} cr</strong></span>
                    <button type="button" id="btn-desactivar-auto" class="btn btn-peligro-sm">Desactivar</button>
                  </div>
                `
                    : `
                  <div class="campo-con-boton">
                    <label for="input-limite-auto" class="etiqueta-sm">Tope de puja automática (mínimo ${minimo === null ? 'según el servidor' : `${formatearCreditos(minPuja)} cr`}):</label>
                    <input type="number" id="input-limite-auto" class="input-estandar" placeholder="Ej. ${formatearCreditos(minPuja + 400)}" min="${minPuja}" step="50" ${bloqueada ? 'disabled' : ''}>
                    <button type="button" id="btn-activar-auto" class="btn btn-contorno" ${bloqueada ? 'disabled' : ''}>
                      Activar
                    </button>
                  </div>
                `
                }
              </div>
            </div>

            <!-- Desglose de Retención Contextual (Main.dc.html) -->
            <div class="panel-resumen-sidebar" style="background: var(--superficie); border: 1px solid var(--borde); border-radius: 8px; padding: 16px 20px; margin-top: 16px;">
              <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px;">
                <span style="font-size: 13px; color: var(--texto-2);">Libre para pujar</span>
                <span class="cifra" style="font-size: 16px; font-weight: 600; color: var(--exito);">${formatearCreditos(libre)} cr</span>
              </div>
              <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px;">
                <span style="font-size: 13px; color: var(--texto-2);">Retenido aquí</span>
                <span class="cifra" style="font-size: 16px; font-weight: 600; color: ${sub.retenido > 0 ? 'var(--advertencia)' : 'var(--texto-3)'};">${sub.retenido > 0 ? `${formatearCreditos(sub.retenido)} cr` : '—'}</span>
              </div>
              <div style="display: flex; align-items: center; justify-content: space-between;">
                <span style="font-size: 13px; color: var(--texto-2);">Retenido en otras (${otrasConRetenido})</span>
                <span class="cifra retenido-en-otras">${formatearCreditos(retenidoEnOtras)} cr</span>
              </div>
              <div style="height: 1px; background: var(--fondo); margin: 12px 0;"></div>
              <button type="button" class="btn btn-contorno" id="btn-ver-todas-mis-subastas" style="width: 100%; min-height: 32px; font-size: 13px; font-weight: 600;">
                Ver todas mis subastas
              </button>
            </div>
          </section>
        </div>

        <!-- Modal de Confirmación de Compra Inmediata -->
        ${
          this.confirmandoCompra
            ? `
          <div class="modal-overlay" id="modal-compra-inmediata" role="dialog" aria-modal="true" aria-labelledby="modal-titulo">
            <div class="modal-tarjeta">
              <h2 id="modal-titulo" class="titulo-mediano">Confirmar compra inmediata</h2>
              <p>Estás a punto de comprar <strong>${esc(sub.nombre)}</strong> de forma directa por <strong>${formatearCreditos(sub.compraInmediata)} cr</strong>.</p>
              <p class="texto-pista">Esta acción cerrará la subasta inmediatamente y transferirá el objeto a tu cuenta.</p>
              <div class="modal-acciones">
                <button type="button" class="btn btn-contorno" id="btn-cancelar-compra">Cancelar</button>
                <button type="button" class="btn btn-acento" id="btn-confirmar-compra">Confirmar compra por ${formatearCreditos(sub.compraInmediata)} cr</button>
              </div>
            </div>
          </div>
        `
            : ''
        }

        <!-- B8 — Confirmación de cancelación (7.7.10) -->
        ${
          this.confirmandoCancelacion
            ? `
          <div class="modal-overlay" id="modal-cancelar-subasta" role="dialog" aria-modal="true" aria-labelledby="modal-cancelar-titulo">
            <div class="modal-tarjeta">
              <h2 id="modal-cancelar-titulo" class="titulo-mediano">Cancelar la subasta</h2>
              <p>Vas a cancelar la subasta de <strong>${esc(sub.nombre)}</strong>. El objeto vuelve a tu inventario.</p>
              <p class="texto-pista">Cancelar cuesta ${esc(this.textoPenalizacion())}. Solo se puede si nadie ha pujado y faltan más de ${formatearCreditos(this.reglas?.cancelacionProhibidaUltimasHoras ?? 6)} horas para el cierre.</p>
              <div class="modal-acciones">
                <button type="button" class="btn btn-contorno" id="btn-descartar-cancelacion">No cancelar</button>
                <button type="button" class="btn btn-peligro-sm" id="btn-confirmar-cancelacion">Cancelar y pagar la penalización</button>
              </div>
            </div>
          </div>
        `
            : ''
        }
      </div>
    `;
  }

  /**
   * Sin nivel ni aporte en el contrato no hay comparativa con el héroe; se
   * dice, salvo en tu propia subasta, donde no vas a pujar.
   */
  avisoSinComparativa(sub) {
    if (this.esMiSubasta(sub)) {
      return '';
    }
    return `
              <div class="alerta alerta-informativa aviso-sin-comparativa" role="note">
                <strong>${iconoHtml('alerta', { clase: 'icono icono--menudo' })} Sin comparativa de héroe.</strong>
                La subasta no dice qué nivel pide ni qué aporta este objeto a un héroe.
                Revisa el objeto en tu inventario antes de pujar.
              </div>`;
  }

  /**
   * UXC-9 — el historial vacío dice por qué: nadie ha pujado, o no se pudo
   * traer (y entonces no se afirma que no haya pujas).
   */
  vacioDelHistorial(sub) {
    if (sub.historial.length > 0) {
      return '';
    }
    const pujas = Number(sub.rivales) || 0;
    if (pujas === 0) {
      return this.esMiSubasta(sub)
        ? '<p class="texto-pista historial-vacio">Nadie ha pujado todavía.</p>'
        : '<p class="texto-pista historial-vacio">Nadie ha pujado todavía: la primera oferta puede ser la tuya.</p>';
    }
    if (this.api && !sub.historialCargado && !sub.historialFallido) {
      return '<p class="texto-pista historial-vacio" role="status">Cargando el historial…</p>';
    }
    return `<p class="texto-pista historial-vacio">Esta subasta lleva ${pujas} ${pujas === 1 ? 'puja' : 'pujas'}, pero no pudimos traer el detalle ahora. Vuelve a abrirla en un momento.</p>`;
  }

  /** De quién es la subasta: tuya, de un vendedor con nombre o nada. */
  /**
   * UXC-8 — lo que dice la ficha y el listado no: cuántos la han visto y
   * cómo le ha ido al vendedor con sus subastas terminadas (7.7.9). Sin
   * ficha, nada: no se rellena con ceros.
   */
  generarHtmlDatosDeLaFicha(sub) {
    if (!sub.fichaCargada) {
      return '';
    }
    const partes = [];
    if (sub.vistas !== null && sub.vistas !== undefined) {
      partes.push(
        `<span class="ficha-subasta__dato">${iconoHtml('ojo', { clase: 'icono icono--menudo' })} ${formatearCreditos(sub.vistas)} ${sub.vistas === 1 ? 'jugador la ha visto' : 'jugadores la han visto'}</span>`,
      );
    }
    const reputacion = this.esMiSubasta(sub) ? '' : textoDeReputacion(sub.reputacionVendedor);
    if (reputacion) {
      partes.push(
        `<span class="ficha-subasta__dato">${iconoHtml('usuario', { clase: 'icono icono--menudo' })} ${esc(reputacion)}</span>`,
      );
    }
    return partes.length ? `<p class="ficha-subasta texto-pista">${partes.join('')}</p>` : '';
  }

  insigniaDeVendedor(sub) {
    if (this.esMiSubasta(sub)) {
      return `<span class="badge badge-propia">${iconoHtml('usuario', { clase: 'icono icono--menudo' })} Tu subasta</span>`;
    }
    // B8 — la ficha trae el apodo; el listado solo el identificador, que no
    // se pinta.
    const nombre = sub.vendedorApodo || sub.vendedor;
    return nombre ? `<span class="badge badge-neutral">Vendedor: ${esc(nombre)}</span>` : '';
  }

  /** Quién va delante en una fila de «Donde pujas». */
  textoDeLaFila(sub) {
    if (sub.ganando) {
      return 'Tu puja';
    }
    return sub.rival ? `de ${esc(sub.rival)}` : 'de otro jugador';
  }

  /** Quién va delante en el detalle, con lo que se sabe. */
  textoDelPostor(sub) {
    if (sub.ganando) {
      return 'Vas ganando tú';
    }
    if (sub.rival) {
      return `Mejor postor: ${esc(sub.rival)}`;
    }
    const pujas = Number(sub.rivales) || 0;
    return pujas > 0
      ? `${pujas} ${pujas === 1 ? 'puja' : 'pujas'} hasta ahora`
      : 'Nadie ha pujado todavía';
  }

  /**
   * B8 / D-43 — el incremento minimo, el que dice el servidor: el de esta
   * subasta (el que regia al publicarse) o, si no se sabe, el vigente de
   * GET /subastas/reglas (5 creditos en admin-parametros). Nunca una cifra
   * escrita aqui. Sin configurar, se dice que falta, sin inventar uno.
   */
  generarHtmlNotaIncremento(sub) {
    const propio =
      sub.incrementoMinimo === null || sub.incrementoMinimo === undefined
        ? null
        : Number(sub.incrementoMinimo);
    const vigente =
      this.reglas?.incrementoMinimoConfigurado && this.reglas.incrementoMinimo !== null
        ? Number(this.reglas.incrementoMinimo)
        : null;
    const incremento = propio ?? vigente;
    if (incremento !== null && Number.isFinite(incremento)) {
      return `<p class="texto-pista" data-incremento-minimo>Incremento mínimo: ${formatearCreditos(incremento)} ${incremento === 1 ? 'crédito' : 'créditos'}</p>`;
    }
    if (this.reglas && !this.reglas.incrementoMinimoConfigurado) {
      return '<p class="texto-pista" data-incremento-minimo="sin-configurar">El incremento mínimo entre pujas no está configurado en administración.</p>';
    }
    return '';
  }

  generarHtmlToastCruzado() {
    if (!this.avisoCruzado) {
      return '';
    }
    return `
      <aside class="toast-cruzado-flotante" role="alert" aria-live="polite">
        <div class="toast-cruzado-cabecera">
          <div class="toast-titulo-contenedor">
            <span class="toast-icono">${iconoHtml('alerta')}</span>
            <strong class="toast-titulo">¡Te superaron en otra subasta!</strong>
          </div>
          <button type="button" class="btn-cerrar-toast" aria-label="Cerrar aviso cruzado">×</button>
        </div>
        <p class="toast-mensaje">
          <strong>${esc(this.avisoCruzado.nombre)}</strong> · ahora <strong class="cifra">${formatearCreditos(this.avisoCruzado.oferta)} cr</strong> ·
          quedan <span class="cifra" data-tiempo-subasta="${this.avisoCruzado.id}">${formatearTiempo(this.avisoCruzado.segundosRestantes)}</span>
        </p>
        <div class="toast-acciones">
          <button type="button" class="btn btn-primario btn-sm btn-toast-ir" data-id="${this.avisoCruzado.id}">Ir</button>
          <button type="button" class="btn btn-contorno btn-sm btn-toast-descartar">Descartar</button>
        </div>
      </aside>
    `;
  }

  conectarEventos() {
    // Pestañas de navegación
    this.contenedor.querySelectorAll('.tab-btn[data-tab]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const tab = btn.getAttribute('data-tab');
        if (tab === 'explorar') {
          this.abrirExplorar();
        } else if (tab === 'mis-subastas') {
          this.abrirMisSubastas();
        } else if (tab === 'detalle') {
          this.abrirDetalle(this.subastaActivaId || this.subastas[0]?.id);
        } else if (tab === 'cierre-multiple') {
          this.abrirCierreMultiple();
        }
      });
    });

    // Botones de resultado de compra o adjudicación
    this.contenedor.querySelector('#btn-resultado-mis-subastas')?.addEventListener('click', () => {
      this.abrirMisSubastas();
    });
    this.contenedor.querySelector('#btn-resultado-explorar')?.addEventListener('click', () => {
      this.abrirExplorar();
    });

    // UXC-8 — el vacío de «Donde pujas» lleva al mercado.
    this.contenedor.querySelector('#btn-mis-a-explorar')?.addEventListener('click', () => {
      this.abrirExplorar();
    });

    // Botón de ver todas mis subastas desde el sidebar de detalle
    this.contenedor.querySelector('#btn-ver-todas-mis-subastas')?.addEventListener('click', () => {
      this.abrirMisSubastas();
    });

    // Abrir detalle desde tarjeta en Explorar o Mis subastas
    this.contenedor
      .querySelectorAll('.btn-abrir, .btn-ver-subasta, .btn-recuperar, .btn-ir-ahora')
      .forEach((btn) => {
        btn.addEventListener('click', (e) => {
          e.stopPropagation();
          const id =
            btn.getAttribute('data-abrir') || btn.closest('[data-id]')?.getAttribute('data-id');
          if (id) {
            this.abrirDetalle(id);
          }
        });
      });

    this.contenedor
      .querySelectorAll('.tarjeta-subasta, .fila-mi-subasta:not([data-panel])')
      .forEach((tarj) => {
        tarj.addEventListener('click', () => {
          const id = tarj.getAttribute('data-id');
          if (id) {
            this.abrirDetalle(id);
          }
        });
      });
    // UXC-8 — las filas del panel: solo las que siguen en curso abren la
    // subasta (una terminada no tiene detalle que pujar).
    this.contenedor
      .querySelectorAll('.fila-mi-subasta[data-panel][data-estado="ACTIVA"]')
      .forEach((fila) => {
        fila.addEventListener('click', () =>
          this.abrirSubastaDelPanel(fila.getAttribute('data-id')),
        );
      });

    // Cierre múltiple
    this.contenedor.querySelector('#btn-cierre-a-mis-subastas')?.addEventListener('click', () => {
      this.abrirMisSubastas();
    });

    this.contenedor.querySelector('#btn-cierre-a-explorar')?.addEventListener('click', () => {
      this.abrirExplorar();
    });

    this.contenedor.querySelectorAll('.btn-ver-adjudicada').forEach((btn) => {
      btn.addEventListener('click', () => {
        const id = btn.getAttribute('data-id');
        if (id) {
          this.abrirDetalle(id, { resultadoCierre: 'adjudicada' });
        }
      });
    });

    this.contenedor.querySelectorAll('.btn-buscar-parecidas').forEach((btn) => {
      btn.addEventListener('click', () => {
        this.abrirExplorar();
      });
    });

    // Toast cruzado
    this.contenedor.querySelector('.btn-toast-ir')?.addEventListener('click', () => {
      this.irDesdeAvisoCruzado();
    });

    this.contenedor.querySelector('.btn-toast-descartar')?.addEventListener('click', () => {
      this.descartarAvisoCruzado();
    });

    this.contenedor.querySelector('.btn-cerrar-toast')?.addEventListener('click', () => {
      this.descartarAvisoCruzado();
    });

    // Volver a la lista
    const btnVolver = this.contenedor.querySelector('#btn-volver');
    if (btnVolver) {
      btnVolver.addEventListener('click', () => this.volverALista());
    }

    // Selector de héroe
    this.contenedor.querySelectorAll('.btn-heroe-chip').forEach((btn) => {
      btn.addEventListener('click', () => {
        const heroeId = btn.getAttribute('data-heroe');
        this.seleccionarHeroe(heroeId);
      });
    });

    // Atajos de puja rápida
    this.contenedor.querySelectorAll('.btn-atajo').forEach((btn) => {
      btn.addEventListener('click', () => {
        const monto = Number(btn.getAttribute('data-monto'));
        this.pujar(monto);
      });
    });

    // Input de puja manual
    const inputPuja = this.contenedor.querySelector('#input-monto-puja');
    const btnPujarManual = this.contenedor.querySelector('#btn-pujar-manual');
    if (inputPuja && btnPujarManual) {
      inputPuja.addEventListener('input', (e) => {
        this.montoPersonalizado = Number(e.target.value);
      });
      btnPujarManual.addEventListener('click', () => {
        const monto = Number(inputPuja.value);
        this.pujar(monto);
      });
    }

    // Compra inmediata
    const btnSolicitarCompra = this.contenedor.querySelector('#btn-solicitar-compra');
    if (btnSolicitarCompra) {
      btnSolicitarCompra.addEventListener('click', () => this.solicitarCompraInmediata());
    }

    const btnCancelarCompra = this.contenedor.querySelector('#btn-cancelar-compra');
    if (btnCancelarCompra) {
      btnCancelarCompra.addEventListener('click', () => this.cancelarCompraInmediata());
    }

    const btnConfirmarCompra = this.contenedor.querySelector('#btn-confirmar-compra');
    if (btnConfirmarCompra) {
      btnConfirmarCompra.addEventListener('click', () => this.confirmarCompraInmediata());
    }

    // Puja automática
    const btnActivarAuto = this.contenedor.querySelector('#btn-activar-auto');
    const inputLimiteAuto = this.contenedor.querySelector('#input-limite-auto');
    if (btnActivarAuto && inputLimiteAuto) {
      btnActivarAuto.addEventListener('click', () => {
        const limite = Number(inputLimiteAuto.value);
        this.configurarAutoPuja(limite);
      });
    }

    const btnDesactivarAuto = this.contenedor.querySelector('#btn-desactivar-auto');
    if (btnDesactivarAuto) {
      btnDesactivarAuto.addEventListener('click', () => this.desactivarAutoPuja());
    }

    // B8 — seguimiento, cancelacion y pendientes de recoger
    this.contenedor
      .querySelector('#btn-seguir')
      ?.addEventListener('click', () => this.alternarSeguimiento());
    this.contenedor
      .querySelector('#btn-compartir')
      ?.addEventListener('click', () => this.compartirSubasta());
    this.contenedor
      .querySelector('#btn-cancelar-subasta')
      ?.addEventListener('click', () => this.solicitarCancelacion());
    this.contenedor
      .querySelector('#btn-descartar-cancelacion')
      ?.addEventListener('click', () => this.descartarCancelacion());
    this.contenedor
      .querySelector('#btn-confirmar-cancelacion')
      ?.addEventListener('click', () => this.confirmarCancelacion());
    this.contenedor.querySelectorAll('.btn-recoger-pendiente').forEach((btn) => {
      btn.addEventListener('click', () => this.recogerPendiente(btn.getAttribute('data-subasta')));
    });
    this.contenedor
      .querySelector('#btn-recoger-todo')
      ?.addEventListener('click', () => this.recogerTodosLosPendientes());

    this.conectarEventosDelPanel();
  }

  /** UXC-8 — los botones del panel personal de «Mis subastas». */
  conectarEventosDelPanel() {
    const cada = (selector, accion) => {
      this.contenedor.querySelectorAll(selector).forEach((boton) => {
        boton.addEventListener('click', (evento) => {
          // La fila entera también abre la subasta: el botón no le pasa el clic.
          evento.stopPropagation();
          accion(boton);
        });
      });
    };
    cada('[data-abrir-panel]', (b) =>
      this.abrirSubastaDelPanel(b.getAttribute('data-abrir-panel')),
    );
    cada('[data-cancelar-publicacion]', (b) =>
      this.cancelarPublicacion(b.getAttribute('data-cancelar-publicacion')),
    );
    cada('[data-dejar-de-seguir]', (b) =>
      this.dejarDeSeguirDesdeElPanel(b.getAttribute('data-dejar-de-seguir')),
    );
    cada('[data-reintentar-panel]', () => this.cargarPanel());
    cada('[data-ver-todo]', (b) => this.alternarVerTodo(b.getAttribute('data-ver-todo')));
    cada('[data-ir-a-explorar]', () => this.abrirExplorar());
    cada('#btn-exportar-historial', () => this.exportarHistorial());
  }
}

export function montarSubastas(contenedor, opciones = {}) {
  const ctrl = new ControladorSubastas(Object.assign({ contenedor }, opciones));
  ctrl.iniciar();
  return ctrl;
}
