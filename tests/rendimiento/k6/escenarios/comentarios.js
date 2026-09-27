/**
 * Escenario `comentarios` — lectura publica del hilo de un producto
 * (RNF-REN-001).
 *
 * `GET /api/v1/products/{id}/comments` es el hilo que pinta la ficha de
 * producto (7.1 del documento del curso: detalle con calificacion y comentarios;
 * comentarios.yaml 1.4.0). Leerlo es publico, asi que se mide SIN token, y
 * paginado como lo pide la ficha: 10 por pagina.
 *
 * El producto sale de `PRODUCTO_COMENTARIOS`: por omision `p-heroe-e2e`, el
 * heroe que siembra `tests/e2e/sembrar.sh`, que existe en el banco. Contra otro
 * entorno hay que darle un producto que exista alli (en DEV, uno del catalogo
 * oficial). El hilo puede estar vacio y es una respuesta valida: se comprueba
 * que llega un hilo bien formado, no que tenga comentarios.
 *
 * @module escenarios/comentarios
 */

import http from 'k6/http';

import { CONFIG } from '../lib/entorno.js';
import { campoJson, medir, pausar } from '../lib/medicion.js';

/** Comentarios por pagina: los de la ficha de producto. */
const TAMANO_HILO = 10;

/**
 * Una iteracion del hilo de comentarios. Rota entre las primeras `PAGINAS`
 * paginas, como los demas escenarios de lectura.
 */
export function medirHiloDeComentarios() {
  const pagina = __ITER % CONFIG.paginas;
  const url =
    CONFIG.baseUrl +
    '/api/v1/products/' +
    encodeURIComponent(CONFIG.productoComentarios) +
    '/comments?pagina=' +
    pagina +
    '&tamano=' +
    TAMANO_HILO;

  const respuesta = http.get(url, {
    headers: { Accept: 'application/json' },
    tags: { escenario: 'comentarios' },
  });

  medir('comentarios', respuesta, {
    'responde 200': (r) => r.status === 200,
    'trae el hilo': (r) => Array.isArray(campoJson(r, 'comentarios')),
  });

  pausar();
}
