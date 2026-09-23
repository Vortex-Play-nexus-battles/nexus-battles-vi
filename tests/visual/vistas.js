/**
 * Catálogo de las vistas del producto — UX-R2.1, reescrito en UX-R3.0.
 *
 * ## Por qué ya no es una lista
 *
 * Era una lista escrita a mano con un campo `acceso` que decía quién podía
 * ver cada vista. Existía una segunda lista igual —la de los guardas de cada
 * pantalla— y una tercera —la del menú de cuenta—. Las tres se contradecían:
 * el laboratorio creía que `gestion-usuarios` era `admin`, el guard de esa
 * vista solo pedía sesión, y el menú la ofrecía a los moderadores.
 *
 * Ahora hay una sola: `MATRIZ`, en `comun/acceso.js`. Aquí se adapta al
 * vocabulario del laboratorio y se comprueba contra el disco, como antes.
 */

import { ACCESO, MATRIZ } from '../../frontend/app-web/src/comun/matriz-acceso.js';

/**
 * Prefijo de la parte web dentro de la raíz servida.
 *
 * Las vistas enlazan el kit con `../../../../shared/ui-kit/...`, que sale de
 * `src/` y llega a la raíz del monorepo. Por eso el servidor tiene que servir
 * **la raíz**, no `src/`: es lo que hace el borde real (`root /srv/nexus` con
 * `location /frontend/` y `location /shared/` en `borde-dev.conf`). Servir
 * `src/` deja los tres CSS del kit en 404 y la página se pinta sin estilos.
 */
export const PREFIJO_WEB = 'frontend/app-web/src';

/** Resoluciones de la matriz. Las cinco del plan UX-R2, sin cambios en R3. */
export const PANTALLAS = Object.freeze([
  { nombre: 'desktop', ancho: 1440, alto: 900 },
  { nombre: 'laptop', ancho: 1280, alto: 800 },
  { nombre: 'laptop-min', ancho: 1024, alto: 768 },
  { nombre: 'tablet', ancho: 768, alto: 1024 },
  { nombre: 'movil', ancho: 375, alto: 812 },
]);

/** Objetivo táctil mínimo en móvil (WCAG 2.5.5 nivel AAA es 44×44). */
export const OBJETIVO_TACTIL = 44;

/**
 * Con qué identidad hay que abrir cada nivel de acceso.
 *
 * El laboratorio no razona sobre roles: pregunta «¿qué persona necesito?» y
 * la matriz responde. Un nivel nuevo en `acceso.js` sin persona aquí rompe la
 * prueba de cobertura, que es lo que hay que hacer.
 */
const PERSONA_POR_NIVEL = Object.freeze({
  [ACCESO.PUBLICA]: 'anonimo',
  [ACCESO.SESION]: 'jugador',
  [ACCESO.MODERACION]: 'moderador',
  [ACCESO.ADMINISTRACION]: 'administrador',
  [ACCESO.SUPERADMINISTRACION]: 'superadministrador',
});

/**
 * El grupo es para ordenar el informe, no para decidir nada. Sale del armazón
 * y de la carpeta, que es como las mira una persona.
 */
function grupoDe(id, entrada) {
  if (entrada.armazon === 'publico') {
    return 'entrada';
  }
  if (entrada.armazon === 'admin') {
    return 'admin';
  }
  if (entrada.ruta.includes('salas-partidas/')) {
    return 'batalla';
  }
  if (entrada.ruta.includes('inventario') || entrada.ruta.includes('productos')) {
    return 'coleccion';
  }
  if (entrada.ruta.includes('torneos')) {
    return 'torneo';
  }
  if (/subasta|puja|tienda/.test(entrada.ruta)) {
    return 'mercado';
  }
  if (/comentario|notificacion|sanciones/.test(entrada.ruta)) {
    return 'comunidad';
  }
  return 'cuenta';
}

export const VISTAS = Object.freeze(
  Object.entries(MATRIZ).map(([id, entrada]) =>
    Object.freeze({
      id,
      ruta: entrada.ruta,
      armazon: entrada.armazon,
      nivel: entrada.acceso,
      persona: PERSONA_POR_NIVEL[entrada.acceso],
      // Vocabulario heredado del laboratorio de UX-R2, para no reescribir
      // todas sus comprobaciones de golpe.
      acceso:
        entrada.acceso === ACCESO.PUBLICA
          ? 'publica'
          : entrada.armazon === 'admin'
            ? 'admin'
            : 'jugador',
      grupo: grupoDe(id, entrada),
    }),
  ),
);

/** Las que se pueden fotografiar sin sesión. */
export const PUBLICAS = VISTAS.filter((v) => v.acceso === 'publica');

/** Las que necesitan que el arnés consiga identidad. */
export const PRIVADAS = VISTAS.filter((v) => v.acceso !== 'publica');

/** Agrupadas por la persona con la que hay que abrirlas. */
export const POR_PERSONA = Object.freeze(
  VISTAS.reduce((acumulado, vista) => {
    (acumulado[vista.persona] ??= []).push(vista);
    return acumulado;
  }, {}),
);
