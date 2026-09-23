/**
 * Los tres perfiles de carga de la suite.
 *
 * Todos usan el modelo CERRADO de k6 (`constant-vus`): un numero fijo de
 * usuarios virtuales que esperan la respuesta antes de pedir otra vez. La
 * alternativa —`constant-arrival-rate`, modelo abierto— se descarto a
 * proposito: si el servidor se ralentiza, el modelo abierto sigue inyectando
 * peticiones al mismo ritmo y k6 levanta mas VUs para conseguirlo, que es
 * exactamente como se tumba un host pequeno. El modelo cerrado se auto-frena:
 * cuando el servicio tarda mas, la carga ofrecida baja sola. En un entorno de
 * DEV compartido con la demo, esa propiedad vale mas que la pureza del modelo.
 *
 * `duracionSegundos` es POR ESCENARIO, y los escenarios corren EN SERIE (ver
 * `rendimiento.js`). Una corrida completa dura, como minimo,
 * `duracionSegundos x 4`.
 *
 * @module lib/perfiles
 */

/**
 * Techo de usuarios virtuales por omision.
 *
 * El host de plataforma en DEV es un `t3.small`: 2 vCPU y 2 GiB de RAM, con
 * ~5 JVMs de Spring Boot (unos 320 MB de `mem_limit` cada una), PostgreSQL,
 * Redis, Mailpit y el borde nginx encima —ver
 * `infrastructure/entornos/plataforma/README.md`—. Con 2 vCPU, 20 peticiones
 * simultaneas ya son diez veces el numero de nucleos: suficiente para que
 * aparezca el encolamiento que interesa medir, y todavia lejos del punto en
 * que la instancia se va a swap y el OOM killer elige victima (que, segun ese
 * mismo README, puede ser `salas-partidas`, o sea la demo).
 *
 * `load` NO es una prueba de estres. Buscar el punto de rotura es otro
 * ejercicio, necesita un entorno desechable y no se hace contra DEV.
 */
export const TECHO_DE_VUS_POR_OMISION = 25;

/**
 * @typedef {Object} Perfil
 * @property {number} vus usuarios virtuales simultaneos
 * @property {number} duracionSegundos duracion de CADA escenario
 * @property {number} pausaMs pausa entre iteraciones de un mismo VU
 * @property {string} proposito para que sirve este perfil
 */

/** @type {Record<string, Perfil>} */
export const PERFILES = {
  /**
   * Comprobar que la suite y el entorno funcionan. No mide nada publicable:
   * con un VU y 20 s por escenario no hay muestras para un p99 honesto.
   * Es el perfil del banco E2E local y el que corre solo en CI.
   */
  smoke: {
    vus: 1,
    duracionSegundos: 20,
    pausaMs: 0,
    proposito: 'Comprobar que la suite corre y el entorno responde. No es una medicion.',
  },

  /**
   * La medicion de referencia de RNF-REN-001. Carga baja y sostenida: lo que
   * interesa es la latencia del sistema haciendo su trabajo, no su limite.
   * Con 5 VUs y 60 s por escenario salen del orden de miles de muestras, que
   * es lo que hace falta para que el p95 signifique algo (ADR-006).
   */
  baseline: {
    vus: 5,
    duracionSegundos: 60,
    pausaMs: 500,
    proposito: 'Medicion de referencia de RNF-REN-001 con carga baja y sostenida.',
  },

  /**
   * El techo que este proyecto se permite contra DEV. Sirve para ver como se
   * degrada la latencia cuando hay concurrencia real, no para romper nada.
   */
  load: {
    vus: 20,
    duracionSegundos: 120,
    pausaMs: 200,
    proposito: 'Concurrencia alta dentro de lo que aguanta el t3.small de plataforma en DEV.',
  },
};
