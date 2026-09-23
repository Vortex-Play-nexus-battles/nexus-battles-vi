/**
 * Escenario `busqueda_inventario` — HU-REN-003 CA-01.
 *
 * CA-01 pide que las busquedas esten indexadas y respondan dentro del objetivo
 * de latencia. `GET /api/v1/inventario/elementos/busqueda` es la busqueda real
 * del jugador sobre su vitrina: filtra por criterio y pagina, y es la unica
 * operacion de texto libre del flujo que este bloque expone.
 *
 * Se mide por el borde, no contra `inventario` directamente: el servicio vive
 * en el host de Contenido y la peticion cruza nginx y la red entre las dos
 * instancias. Esa es la latencia que sufre el jugador y la que fija
 * RNF-REN-001 («extremo a extremo»).
 *
 * @module escenarios/inventario
 */

import http from 'k6/http';

import { CONFIG, cabecerasCon } from '../lib/entorno.js';
import { campoJson, medir, pausar } from '../lib/medicion.js';

/**
 * Una iteracion de busqueda en el inventario del jugador.
 *
 * El criterio sale de `CRITERIO_BUSQUEDA` porque depende de que hay sembrado
 * en cada entorno. La comprobacion NO exige resultados: un inventario vacio es
 * una respuesta valida, y atar la medicion a la semilla de datos convertiria
 * un cambio en `sembrar.sh` en un fallo de rendimiento. Lo que se comprueba es
 * que la busqueda respondio y devolvio una pagina bien formada.
 *
 * @param {{token: string}} sesion lo que devolvio `setup()`
 */
export function medirBusquedaDeInventario(sesion) {
  const pagina = __ITER % CONFIG.paginas;
  const url =
    CONFIG.baseUrl +
    '/api/v1/inventario/elementos/busqueda?criterio=' +
    encodeURIComponent(CONFIG.criterioBusqueda) +
    '&pagina=' +
    pagina;

  const respuesta = http.get(url, {
    headers: cabecerasCon(sesion.token),
    tags: { escenario: 'busqueda_inventario' },
  });

  medir('busqueda_inventario', respuesta, {
    'responde 200': (r) => r.status === 200,
    'trae una pagina': (r) => Array.isArray(campoJson(r, 'elementos')),
  });

  pausar();
}
