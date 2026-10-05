/**
 * Escenario `tienda` — lectura publica de la vitrina (RNF-REN-001).
 *
 * `GET /api/v1/vitrina` es lo primero que ve quien llega a la tienda desde la
 * portada, con sesion o sin ella: la tienda se abre desde la landing (feedback
 * del profesor, ecommerce-carrito.yaml 1.3.0). Es publica, asi que se mide SIN
 * token: el camino del visitante anonimo, que es el de la portada. Con sesion
 * la vitrina ademas marca deseos y propios, y eso es otra consulta.
 *
 * Por el borde, como los demas: ms-ecommerce vive bajo `/ecommerce` y el borde
 * reescribe la ruta, y la vitrina a su vez proyecta el catalogo maestro de
 * contenido/productos, que esta en el otro host. Esa cadena entera es la
 * latencia que sufre el jugador.
 *
 * @module escenarios/tienda
 */

import http from 'k6/http';

import { CONFIG } from '../lib/entorno.js';
import { campoJson, medir, pausar } from '../lib/medicion.js';

/**
 * Una iteracion de la vitrina.
 *
 * Rota entre las primeras `PAGINAS` paginas, como el listado de salas, para no
 * medir siempre la misma consulta caliente. No exige productos: una vitrina
 * vacia es una respuesta valida (depende de lo que haya sembrado cada
 * entorno), y atar la medicion al catalogo convertiria un cambio de semilla
 * en un fallo de rendimiento. Se comprueba que responde una pagina bien formada.
 */
export function medirVitrina() {
  const pagina = __ITER % CONFIG.paginas;
  const url = CONFIG.baseUrl + '/api/v1/vitrina?page=' + pagina + '&size=' + CONFIG.tamanoPagina;

  const respuesta = http.get(url, {
    headers: { Accept: 'application/json' },
    tags: { escenario: 'tienda' },
  });

  medir('tienda', respuesta, {
    'responde 200': (r) => r.status === 200,
    'trae una pagina': (r) => Array.isArray(campoJson(r, 'content')),
  });

  pausar();
}
