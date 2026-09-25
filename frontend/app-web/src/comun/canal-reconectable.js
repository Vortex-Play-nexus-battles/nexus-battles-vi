/**
 * Un canal STOMP que se reconecta solo — UXC-2 / UXC-9 (ReconnectBanner).
 *
 * §8 del documento pide 99,95 % de disponibilidad y el Project Charter
 * (riesgo #7) lo concreta: ante una pérdida de conexión en tiempo real,
 * degradación controlada y reconciliación del estado al restablecerse, nunca
 * fallar en silencio. Hasta aquí la vista de combate, al caerse el canal,
 * decía «Recarga la página» y dejaba al jugador haciendo de reconector.
 *
 * Esto envuelve el cliente de `transporte-stomp.js` con la MISMA forma
 * (`suscribir`, `enviar`, `cerrar`, `alCerrar`, `alError`), así que quien lo
 * usa no cambia. Lo que añade:
 *
 *   - al perderse el canal, reintenta con esperas crecientes;
 *   - al volver, rehace todas las suscripciones que había (el servidor no las
 *     recuerda: son de la conexión) y avisa con `alReconectar` para que la
 *     vista relea lo que pudo perderse mientras tanto;
 *   - tras el último intento se queda en «sin conexión» con un `reintentar()`
 *     que la vista ofrece como botón.
 *
 * Mientras no hay canal, `enviar` lanza: una acción mandada a un socket
 * muerto se perdería sin que nadie se enterase.
 *
 * @module comun/canal-reconectable
 */

/** Esperas entre intentos, en milisegundos: 1, 2, 4, 8 y 15 segundos. */
export const ESPERAS_POR_OMISION = Object.freeze([1000, 2000, 4000, 8000, 15000]);

/** Los estados que ve la interfaz. */
export const ESTADOS_DE_CANAL = Object.freeze({
  CONECTADO: 'conectado',
  RECONECTANDO: 'reconectando',
  RECONECTADO: 'reconectado',
  SIN_CONEXION: 'sin-conexion',
});

/**
 * @param {object} opciones
 * @param {() => Promise<object>} opciones.conectar abre un cliente STOMP nuevo
 * @param {(estado: {estado: string, intento?: number, de?: number, espera?: number}) => void} [opciones.alEstado]
 * @param {() => void} [opciones.alReconectar] tras volver: la vista reconcilia
 * @param {readonly number[]} [opciones.esperas]
 * @param {{setTimeout: Function, clearTimeout: Function}} [opciones.reloj] inyectable en pruebas
 * @returns {Promise<object>} la fachada, ya conectada la primera vez
 */
export async function canalReconectable({
  conectar,
  alEstado = () => {},
  alReconectar = () => {},
  esperas = ESPERAS_POR_OMISION,
  reloj = globalThis,
}) {
  /** Las suscripciones vivas, para rehacerlas en cada conexión nueva. */
  const suscripciones = [];
  let cliente = null;
  let intento = 0;
  let cerrado = false;
  let temporizador = null;

  const fachada = {
    alCerrar: null,
    alError: null,
    get conectado() {
      return cliente !== null;
    },
    suscribir(destino, alRecibir) {
      suscripciones.push({ destino, alRecibir });
      return cliente ? cliente.suscribir(destino, alRecibir) : null;
    },
    enviar(destino, cuerpo) {
      if (!cliente) {
        throw new Error('Sin conexión en tiempo real: la acción no se envió.');
      }
      cliente.enviar(destino, cuerpo);
    },
    cerrar() {
      cerrado = true;
      reloj.clearTimeout?.(temporizador);
      cliente?.cerrar();
      cliente = null;
    },
    /** Otra ronda de intentos, a mano (el botón «Reintentar»). */
    reintentar() {
      if (cliente || cerrado) {
        return;
      }
      reloj.clearTimeout?.(temporizador);
      intento = 0;
      programar(0);
    },
  };

  function enlazar(nuevo, { esReconexion }) {
    cliente = nuevo;
    intento = 0;
    const perdido = (motivo) => {
      if (cliente !== nuevo) {
        return;
      }
      cliente = null;
      fachada.alCerrar?.(motivo);
      if (!cerrado) {
        programar();
      }
    };
    nuevo.alCerrar = () => perdido('la conexión se cerró');
    nuevo.alError = (error) => {
      fachada.alError?.(error);
      perdido(error?.message ?? 'error del canal');
    };
    if (esReconexion) {
      for (const { destino, alRecibir } of suscripciones) {
        nuevo.suscribir(destino, alRecibir);
      }
      alEstado({ estado: ESTADOS_DE_CANAL.RECONECTADO });
      alReconectar();
    } else {
      alEstado({ estado: ESTADOS_DE_CANAL.CONECTADO });
    }
  }

  function programar(esperaForzada = null) {
    if (intento >= esperas.length) {
      alEstado({ estado: ESTADOS_DE_CANAL.SIN_CONEXION, de: esperas.length });
      return;
    }
    const espera = esperaForzada ?? esperas[intento];
    intento += 1;
    alEstado({
      estado: ESTADOS_DE_CANAL.RECONECTANDO,
      intento,
      de: esperas.length,
      espera,
    });
    temporizador = reloj.setTimeout(async () => {
      if (cerrado) {
        return;
      }
      try {
        enlazar(await conectar(), { esReconexion: true });
      } catch {
        programar();
      }
    }, espera);
  }

  enlazar(await conectar(), { esReconexion: false });
  return fachada;
}
