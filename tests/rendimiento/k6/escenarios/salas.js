/**
 * Escenarios de sala de batalla — RNF-REN-001.
 *
 * Dos operaciones del mismo servicio (`salas-partidas`) elegidas porque son las
 * dos caras del requisito:
 *
 *   - `listar_salas` es la LECTURA mas frecuente del jugador, la primera
 *     pantalla del flujo de juego (RF-JUE-002) y una consulta paginada.
 *   - `crear_sala` es la ESCRITURA de referencia (RF-JUE-001): no es un insert
 *     suelto, sino la cadena completa —comprobar sancion, preguntar al
 *     inventario por el heroe equipado, persistir—. Si la latencia se va a
 *     algun sitio en las escrituras, se va aqui.
 *
 * @module escenarios/salas
 */

import http from 'k6/http';

import { CONFIG, cabecerasCon } from '../lib/entorno.js';
import { campoJson, medir, pausar } from '../lib/medicion.js';

/**
 * Cuerpo de la sala que se crea en cada iteracion.
 *
 * `recompensaCreditos: 0` no es pereza: con recompensa, `CrearSala` reserva
 * creditos en `ms-finanzas` por cada sala (RF-JUE-014). Una corrida `load`
 * vaciaria el saldo de la cuenta de pruebas a los pocos minutos y a partir de
 * ahi todo lo que mediria es el rechazo por saldo insuficiente. Con 0 se mide
 * la escritura de sala, que es lo que se queria medir; la ruta de creditos
 * tiene su propia cobertura en `tests/e2e/apuesta-de-creditos.e2e.spec.js`.
 *
 * `UNO_CONTRA_UNO` y dos participantes: la modalidad mas barata del catalogo,
 * sin heroes de IA que ocupen cupo (HU-SAL-004).
 */
const SALA_DE_PRUEBA = JSON.stringify({
  maximoParticipantes: 2,
  modalidad: 'UNO_CONTRA_UNO',
  recompensaCreditos: 0,
  privada: false,
});

/**
 * Una iteracion del listado de salas.
 *
 * Rota entre las primeras `PAGINAS` paginas en lugar de pedir siempre la 0:
 * medir la misma consulta una y otra vez mide sobre todo la cache del plan de
 * PostgreSQL, no el listado.
 *
 * @param {{token: string}} sesion lo que devolvio `setup()`
 */
export function medirListadoDeSalas(sesion) {
  const pagina = __ITER % CONFIG.paginas;
  const url = CONFIG.baseUrl + '/api/v1/salas?pagina=' + pagina + '&tamano=' + CONFIG.tamanoPagina;

  const respuesta = http.get(url, {
    headers: cabecerasCon(sesion.token),
    tags: { escenario: 'listar_salas' },
  });

  medir('listar_salas', respuesta, {
    'responde 200': (r) => r.status === 200,
    'trae una pagina': (r) => Array.isArray(campoJson(r, 'contenido')),
  });

  pausar();
}

/**
 * Una iteracion de creacion de sala, con su limpieza.
 *
 * La sala creada se cancela inmediatamente (`LIMPIAR_SALAS`, por omision si).
 * Sin eso, un `load` de dos minutos deja miles de salas abiertas en el listado
 * de un entorno compartido con la demo, y ademas falsea el escenario de
 * listado, que empezaria a paginar sobre basura de la propia medicion.
 *
 * La cancelacion va etiquetada como `limpieza` y NO se anota en ninguna serie:
 * no forma parte de lo que se esta midiendo.
 *
 * @param {{token: string}} sesion lo que devolvio `setup()`
 */
export function medirCreacionDeSala(sesion) {
  const cabeceras = cabecerasCon(sesion.token);

  const respuesta = http.post(CONFIG.baseUrl + '/api/v1/salas', SALA_DE_PRUEBA, {
    headers: cabeceras,
    tags: { escenario: 'crear_sala' },
  });

  const creada = medir('crear_sala', respuesta, {
    'responde 201': (r) => r.status === 201,
    'trae identificador de sala': (r) => identificadorDeSala(r) !== undefined,
  });

  if (creada && CONFIG.limpiarSalas) {
    const id = identificadorDeSala(respuesta);
    if (id) {
      http.del(CONFIG.baseUrl + '/api/v1/salas/' + id, null, {
        headers: cabeceras,
        tags: { escenario: 'crear_sala', operacion: 'limpieza' },
      });
    }
  }

  pausar();
}

/**
 * Identificador de la sala recien creada.
 *
 * Se prueba primero la cabecera `Location` —que el contrato garantiza en el
 * 201— y despues el cuerpo. Con las dos vias, un cambio en cualquiera de ellas
 * no deja la limpieza sin saber que cancelar.
 *
 * @param {import('k6/http').Response} respuesta
 * @returns {string|undefined}
 */
function identificadorDeSala(respuesta) {
  const ubicacion = respuesta.headers ? respuesta.headers.Location : undefined;
  if (typeof ubicacion === 'string' && ubicacion.length > 0) {
    const partes = ubicacion.split('/');
    const ultima = partes[partes.length - 1];
    if (ultima) {
      return ultima;
    }
  }
  const delCuerpo = campoJson(respuesta, 'id');
  return typeof delCuerpo === 'string' && delCuerpo.length > 0 ? delCuerpo : undefined;
}
