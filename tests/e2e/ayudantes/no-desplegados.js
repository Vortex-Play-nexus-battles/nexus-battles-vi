/**
 * Lo que DEV no despliega, leído del catálogo de servicios.
 *
 * `infrastructure/despliegue/servicios.json` dice qué servicio NO se levanta en
 * DEV (`desplegableDev: false`) y por qué (`motivoFueraDeDev`): hoy ms-subastas
 * y ms-chatbot por capacidad del host de plataforma, y misiones porque en el
 * host de contenido le falta su credencial de servicio (README de misiones,
 * «Despliegue»). Sus rutas en el borde responden 502 o 504, y eso es la verdad
 * de ese entorno, no un fallo nuevo.
 *
 * Las pruebas contra DEV (canarios, prueba del profesor) no deben ponerse rojas
 * por ellas —la vista tiene que decirlo, y eso se comprueba aparte—, pero
 * tampoco deben tolerar a mano una lista escrita en la prueba: cuando un
 * servicio pase a `desplegableDev: true`, su 5xx vuelve a contar solo.
 *
 * El prefijo de cada servicio sale de su `pruebaBorde` (la ruta con la que el
 * borde lo prueba): los tres primeros segmentos, `/api/v1/<recurso>`.
 *
 * 29-sep (topología, fase 1): un servicio también puede estar desplegado y
 * sano en su host sin que el borde le llegue todavía —misiones y ms-subastas
 * en el host de contenido, a la espera de que el grupo de seguridad de la
 * cuenta del Grupo 2 admita sus puertos—. El catálogo lo dice con
 * `accesoPendiente` (el motivo), y su 5xx se anota igual que el de un servicio
 * fuera de DEV. Al quitar ese campo, su 5xx vuelve a contar solo.
 */

import fs from 'node:fs';
import path from 'node:path';

// Los specs corren como CommonJS (no hay "type": "module" en tests/).
const AQUI =
  typeof __dirname === 'undefined'
    ? path.resolve(process.cwd(), '../../tests/e2e/ayudantes')
    : __dirname;
const CATALOGO = path.resolve(AQUI, '../../../infrastructure/despliegue/servicios.json');

/**
 * Prefijos `/api/v1/<recurso>` de los servicios que DEV no despliega o a los
 * que el borde todavía no llega (`accesoPendiente`).
 *
 * @param {string} [catalogo] ruta del catálogo (para las pruebas del ayudante)
 * @returns {{servicio: string, prefijo: string}[]}
 */
export function rutasNoDesplegadasEnDev(catalogo = CATALOGO) {
  const servicios = JSON.parse(fs.readFileSync(catalogo, 'utf8'));
  const lista = Array.isArray(servicios) ? servicios : (servicios.servicios ?? []);
  return lista
    .filter(
      (s) =>
        (s.desplegableDev === false || typeof s.accesoPendiente === 'string') &&
        typeof s.pruebaBorde === 'string',
    )
    .map((s) => ({
      servicio: s.nombre,
      prefijo: s.pruebaBorde.split('?')[0].split('/').slice(0, 4).join('/'),
    }));
}

/**
 * El servicio no desplegado al que pertenece una ruta, o `null`.
 *
 * @param {string} ruta p. ej. `/api/v1/misiones/destacadas`
 * @param {{servicio: string, prefijo: string}[]} [noDesplegadas]
 * @returns {string|null}
 */
export function servicioNoDesplegadoDe(ruta, noDesplegadas = rutasNoDesplegadasEnDev()) {
  const encontrado = noDesplegadas.find(
    ({ prefijo }) => ruta === prefijo || ruta.startsWith(`${prefijo}/`),
  );
  return encontrado ? encontrado.servicio : null;
}
