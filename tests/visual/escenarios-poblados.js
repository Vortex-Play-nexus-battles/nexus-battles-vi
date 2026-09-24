/**
 * Escenarios poblados del laboratorio visual — FI-R15 / UX-GAME.
 *
 * Cada escenario abre una vista con sesión sintética e intercepta su red con
 * `page.route`, devolviendo cuerpos **con la forma del contrato** (ver
 * `contracts/openapi/`). No se toca ningún módulo: la vista pide lo de siempre
 * y recibe una respuesta válida. `exige` es la lista de selectores que tienen
 * que haberse pintado para que el escenario signifique algo — un banco que no
 * llega a pintar es un verde vacío.
 *
 * Los usan `axe-con-datos.spec.js` (accesibilidad con datos) y las capturas
 * pobladas de `auditoria-visual.spec.js`. Vive aparte para que ningún spec
 * tenga que importar otro spec.
 */

import { sesionSintetica } from './identidad.js';

/**
 * Sesión sintética completa para una vista poblada. `sesionSintetica` devuelve
 * solo `{ token, uid }`; `inyectarSesion` además escribe el apodo y el rol en
 * `sessionStorage`, y sin ellos la cabecera pintaba «undefined» en todas las
 * capturas con datos (UX-GAME-3).
 */
export function sesionDe(apodo, rol = 'JUGADOR') {
  return { ...sesionSintetica({ apodo, rol }), apodo, rol };
}

export const json = (cuerpo) => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(cuerpo),
});

/** Una subasta a punto de cerrar: contador urgente, rareza y credito en juego. */
function subastaUrgente() {
  return {
    id: 'aaaaaaa1-1111-4111-8111-111111111111',
    nombreProducto: 'Hacha de Obsidiana Fracturada',
    tipoProducto: 'ARMA',
    descripcionCorta: 'Filo negro, mella de guerra.',
    rareza: 'epica',
    vendedorId: 'thar_vex',
    ofertaVigente: '1350',
    precioCompraInmediata: '2100',
    // Ocho segundos: por debajo del umbral de diez que enciende el latido, el
    // color de urgencia y el boton «Ir ahora».
    fechaFin: new Date(Date.now() + 8000).toISOString(),
    cantidadPujas: 7,
    miniaturaUrl: null,
    esMaestroDeJuego: false,
  };
}

function subastaTranquila() {
  return {
    ...subastaUrgente(),
    id: 'aaaaaaa2-2222-4222-8222-222222222222',
    nombreProducto: 'Grebas del Centinela Caido',
    rareza: 'rara',
    ofertaVigente: '880',
    fechaFin: new Date(Date.now() + 3_600_000).toISOString(),
  };
}

/** Una sin rareza, para que el camino neutro de FI-R1 tambien pase por axe. */
function subastaSinRareza() {
  return {
    ...subastaTranquila(),
    id: 'aaaaaaa3-3333-4333-8333-333333333333',
    nombreProducto: 'Objeto sin catalogar',
    rareza: undefined,
  };
}

/** Un inventario de jugador con los cinco tipos, uno bloqueado por subasta. */
function elementoDeInventario(cambios = {}) {
  return {
    id: 'ddddddd0-0000-4000-8000-000000000000',
    productoId: 'aaaaaaa1-0000-4000-8000-000000000009',
    tipo: 'ARMA',
    nombrePropio: 'Objeto',
    parteArmadura: null,
    disponible: true,
    subastaId: null,
    ...cambios,
  };
}

export function paginaDeInventario() {
  const elementos = [
    elementoDeInventario({
      id: 'ddddddd1-1111-4111-8111-111111111111',
      tipo: 'HEROE',
      nombrePropio: 'Aquiles de la Ceniza',
    }),
    elementoDeInventario({
      id: 'ddddddd2-2222-4222-8222-222222222222',
      productoId: 'aaaaaaa1-0000-4000-8000-000000000010',
      tipo: 'ARMA',
      nombrePropio: 'Hacha de Obsidiana',
    }),
    elementoDeInventario({
      id: 'ddddddd3-3333-4333-8333-333333333333',
      productoId: 'aaaaaaa1-0000-4000-8000-000000000011',
      tipo: 'ARMADURA',
      parteArmadura: 'CASCO',
      nombrePropio: 'Yelmo del Alba',
    }),
    elementoDeInventario({
      id: 'ddddddd4-4444-4444-8444-444444444444',
      productoId: 'aaaaaaa1-0000-4000-8000-000000000012',
      tipo: 'ARMADURA',
      parteArmadura: 'PECHO',
      nombrePropio: 'Coraza del Centinela',
    }),
    elementoDeInventario({
      id: 'ddddddd5-5555-4555-8555-555555555555',
      productoId: 'aaaaaaa1-0000-4000-8000-000000000013',
      tipo: 'ITEM',
      nombrePropio: 'Poción de brasa',
    }),
    elementoDeInventario({
      id: 'ddddddd6-6666-4666-8666-666666666666',
      productoId: 'aaaaaaa1-0000-4000-8000-000000000014',
      tipo: 'HABILIDAD',
      nombrePropio: 'Grito de guerra',
    }),
    elementoDeInventario({
      id: 'ddddddd7-7777-4777-8777-777777777777',
      productoId: 'aaaaaaa1-0000-4000-8000-000000000015',
      tipo: 'EPICA',
      nombrePropio: 'Reliquia del Nexo',
    }),
    elementoDeInventario({
      id: 'ddddddd8-8888-4888-8888-888888888888',
      productoId: 'aaaaaaa1-0000-4000-8000-000000000016',
      tipo: 'ARMA',
      nombrePropio: 'Espada larga en subasta',
      disponible: false,
      subastaId: 'aaaaaaa2-2222-4222-8222-222222222222',
    }),
  ];
  return {
    elementos,
    numero: 0,
    tamanio: 16,
    totalElementos: elementos.length,
    totalPaginas: 1,
    ultima: true,
  };
}

/** Rutas comunes del inventario poblado: catálogo, estadísticas, acciones. */
function rutasDeInventario(equipamiento) {
  return [
    ['**/api/v1/inventario/elementos?*', json(paginaDeInventario())],
    [
      '**/api/v1/productos/*',
      (peticion) => {
        const id = new URL(peticion.request().url()).pathname.split('/').pop();
        const esHeroe = id.endsWith('0009');
        return json({
          id,
          nombre: esHeroe ? 'Guerrero Tanque' : 'Producto del catálogo',
          tipo: esHeroe ? 'HEROE' : 'ARMA',
          prototipo: esHeroe ? 'Guerrero Tanque' : null,
          descripcion: esHeroe ? 'Aguanta lo que otros no.' : 'Forjado en el Nexo.',
          imagen: null,
          estado: 'ACTIVO',
          tiraje: -1,
        });
      },
    ],
    [
      '**/api/v1/inventario/heroes/*/estadisticas',
      json({
        heroeId: 'ddddddd1-1111-4111-8111-111111111111',
        poder: 10,
        vida: 52,
        defensa: 13,
        ataque: { base: 10, cantidadDados: 1, caras: 6 },
        dano: null,
        sanar: null,
      }),
    ],
    [
      '**/api/v1/heroes/Guerrero%20Tanque',
      json({
        nombre: 'Guerrero Tanque',
        tipo: 'TANQUE',
        descripcion: 'Aguanta lo que otros no.',
        esSanador: false,
        acciones: [
          { nombre: 'Golpe de escudo', costo: '2 puntos de poder', efecto: 'Daño directo.' },
          { nombre: 'Muro', costo: '3 puntos de poder', efecto: 'Sube la defensa un turno.' },
        ],
      }),
    ],
    ['**/api/v1/inventario/heroes/*/equipamiento', json(equipamiento)],
  ];
}

/* ---------------------------------------------------------------------------
   Combate — UX-GAME-4. La partida sale de `GET /partidas/{id}` (Partida de
   salas-partidas.yaml; `jugador` viaja como uid, tal como lo emite el
   servicio) y los avisos por el canal simulado (`canal-simulado.js`).
   ------------------------------------------------------------------------- */

/** La misma sesión para los tres escenarios de combate: el uid tiene que
    coincidir con el participante «yo» de la partida. */
const SESION_COMBATE = sesionDe('qa_combate', 'JUGADOR');
const ID_PARTIDA = 'cccccc01-1111-4111-8111-111111111111';
const RIVAL_IA = 'cccccc02-2222-4222-8222-222222222222';

function partidaEnCurso({ turnoDe = SESION_COMBATE.uid, vidaMia = 41, vidaRival = 23 } = {}) {
  return {
    id: ID_PARTIDA,
    idSala: 'bbbbbbb1-1111-4111-8111-111111111111',
    estado: 'EN_CURSO',
    participantes: [
      {
        jugador: SESION_COMBATE.uid,
        heroe: {
          id: 'ddddddd1-1111-4111-8111-111111111111',
          nombre: 'Aquiles de la Ceniza',
          retratoUrl: null,
          nivel: null,
          vidaActual: vidaMia,
          vidaMaxima: 52,
          efectosActivos: [],
        },
        esIA: false,
        listo: true,
        equipo: null,
        creditosApostados: 120,
      },
      {
        jugador: RIVAL_IA,
        heroe: {
          id: 'ia-guerrero-1',
          nombre: 'Centinela de Hierro',
          retratoUrl: null,
          nivel: null,
          vidaActual: vidaRival,
          vidaMaxima: 60,
          efectosActivos: [],
        },
        esIA: true,
        listo: true,
        equipo: null,
        creditosApostados: 0,
      },
    ],
    turnoActual: { idJugador: turnoDe, numeroTurno: 7, segundosRestantes: 42 },
    recompensaEnJuego: 120,
    iniciadaEn: new Date(Date.now() - 300_000).toISOString(),
  };
}

function finalizada({ ganadores, reparto, recompensa }) {
  return {
    tipo: 'partida.finalizada',
    idPartida: ID_PARTIDA,
    ganadores,
    reparto,
    recompensa,
  };
}

function escenarioDeCombate(id, titulo, { partida, mensajes = [], exige }) {
  return {
    id,
    titulo,
    ruta: `plataforma/salas-partidas/sala-batalla.html?partida=${ID_PARTIDA}`,
    sesion: () => SESION_COMBATE,
    rutas: [[`**/api/v1/partidas/${ID_PARTIDA}`, json(partida)]],
    canal: { mensajes: { [`/tema/partidas/${ID_PARTIDA}`]: mensajes } },
    exige,
  };
}

export const ESCENARIOS = [
  {
    // UX-GAME-4 — la sala de espera (lobby) del anfitrion: ocupacion, codigo
    // de invitacion y el boton de arrancar. `Sala` de salas-partidas.yaml.
    id: 'sala-en-espera',
    titulo: 'sala de espera del anfitrión con código de invitación',
    ruta: 'plataforma/salas-partidas/sala-batalla.html?sala=bbbbbbb1-1111-4111-8111-111111111111',
    sesion: () => SESION_COMBATE,
    rutas: [
      [
        '**/api/v1/salas/bbbbbbb1-1111-4111-8111-111111111111',
        json({
          id: 'bbbbbbb1-1111-4111-8111-111111111111',
          estado: 'ABIERTA',
          modalidad: 'HASTA_SEIS',
          maximoParticipantes: 6,
          ocupacion: 3,
          recompensaCreditos: 320,
          incluirHeroeIA: true,
          heroesIA: 1,
          privada: true,
          tamanoEquipo: null,
          idAnfitrion: SESION_COMBATE.uid,
          participantes: [SESION_COMBATE.uid, 'cccccc03-3333-4333-8333-333333333333'],
          idPartida: null,
          creadaEn: new Date(Date.now() - 120_000).toISOString(),
          codigoInvitacion: 'NEXO-7K2Q',
        }),
      ],
    ],
    canal: { mensajes: {} },
    exige: [
      '[data-zona="espera"]:not([hidden])',
      '[data-zona="invitacion"]:not([hidden])',
      '[data-accion="iniciar-partida"]',
    ],
  },
  escenarioDeCombate('combate-mi-turno', 'combate 1 contra la máquina, en mi turno', {
    partida: partidaEnCurso(),
    exige: ['.combate__vidas .barra-vida', '[data-atacar]:not(:disabled)', '[data-zona="turno"]'],
  }),
  escenarioDeCombate('combate-turno-rival', 'combate 1 contra la máquina, turno del rival', {
    partida: partidaEnCurso({ turnoDe: RIVAL_IA }),
    exige: [
      '.combate__vidas .barra-vida',
      '[data-atacar]:disabled',
      '.accion-combate--fuera-de-turno',
    ],
  }),
  escenarioDeCombate('combate-victoria', 'resultado: victoria con apuesta y recompensa', {
    partida: partidaEnCurso({ vidaRival: 0 }),
    mensajes: [
      finalizada({
        ganadores: [SESION_COMBATE.uid],
        reparto: [
          { idJugador: SESION_COMBATE.uid, creditos: 120, experiencia: null, cofre: null },
          { idJugador: RIVAL_IA, creditos: -120, experiencia: null, cofre: null },
        ],
        recompensa: [{ idJugador: SESION_COMBATE.uid, creditos: 2, ganador: true, cofre: null }],
      }),
    ],
    exige: ['.panel-resultado', '[data-zona="resultado"]:not([hidden])'],
  }),
  escenarioDeCombate('combate-derrota', 'resultado: derrota con la apuesta perdida', {
    partida: partidaEnCurso({ vidaMia: 0 }),
    mensajes: [
      finalizada({
        ganadores: [RIVAL_IA],
        reparto: [
          { idJugador: SESION_COMBATE.uid, creditos: -120, experiencia: null, cofre: null },
          { idJugador: RIVAL_IA, creditos: 120, experiencia: null, cofre: null },
        ],
        recompensa: [{ idJugador: SESION_COMBATE.uid, creditos: 1, ganador: false, cofre: null }],
      }),
    ],
    exige: ['.panel-resultado', '[data-zona="resultado"]:not([hidden])'],
  }),
  escenarioDeCombate('combate-empate', 'resultado: empate, nadie quedó en pie', {
    partida: partidaEnCurso({ vidaMia: 0, vidaRival: 0 }),
    mensajes: [
      finalizada({
        ganadores: [],
        reparto: [
          { idJugador: SESION_COMBATE.uid, creditos: 0, experiencia: null, cofre: null },
          { idJugador: RIVAL_IA, creditos: 0, experiencia: null, cofre: null },
        ],
        recompensa: [{ idJugador: SESION_COMBATE.uid, creditos: 1, ganador: false, cofre: null }],
      }),
    ],
    exige: ['.panel-resultado', '[data-zona="resultado"]:not([hidden])'],
  }),
  {
    // UX-GAME-3 — la vitrina con los cinco tipos, un objeto bloqueado por
    // subasta y el acento lateral por tipo.
    id: 'inventario-vitrina',
    titulo: 'vitrina del inventario con los cinco tipos y un objeto en subasta',
    ruta: 'contenido/inventario/inventario.html',
    sesion: () => sesionDe('qa_inventario', 'JUGADOR'),
    rutas: rutasDeInventario({
      heroeId: 'ddddddd1-1111-4111-8111-111111111111',
      armas: ['ddddddd2-2222-4222-8222-222222222222'],
      armaduras: { CASCO: 'ddddddd3-3333-4333-8333-333333333333' },
      items: [],
    }),
    exige: ['.vitrina__producto', '.vitrina__producto--no-disponible', "[data-tipo='HEROE']"],
  },
  {
    // UX-GAME-3 — el panel de equipamiento: heroe, diez ranuras (dos armas,
    // seis piezas, dos items), dos ocupadas.
    id: 'inventario-equipamiento',
    titulo: 'panel de equipamiento con un arma y un casco puestos',
    ruta: 'contenido/inventario/inventario.html',
    sesion: () => sesionDe('qa_equipo', 'JUGADOR'),
    rutas: rutasDeInventario({
      heroeId: 'ddddddd1-1111-4111-8111-111111111111',
      armas: ['ddddddd2-2222-4222-8222-222222222222'],
      armaduras: { CASCO: 'ddddddd3-3333-4333-8333-333333333333' },
      items: [],
    }),
    interaccion: async (pagina) => {
      await pagina.locator('.vitrina__equipo').first().click();
    },
    exige: ['.ranura', '.ranura--vacia', '.equipamiento__resumen'],
  },
  {
    // UX-GAME-3 — la home con todo lo que el backend publica: saldo (Saldo de
    // creditos.yaml), heroe equipado (inventario.yaml), torneo abierto
    // (TorneoResumen de torneos.yaml) y bandeja (BandejaResponse de
    // notificaciones.yaml).
    id: 'home-poblada',
    titulo: 'home con saldo, héroe equipado, torneo abierto y avisos',
    ruta: 'cuentas/index.html',
    sesion: () => sesionDe('qa_home', 'JUGADOR'),
    rutas: [
      [
        '**/api/v1/creditos/*/saldo',
        json({ jugadorUid: 'qa', saldoBruto: 1450, saldoReservado: 320, saldoDisponible: 1130 }),
      ],
      [
        '**/api/v1/inventario/elementos?*',
        json({
          elementos: [
            {
              id: 'ddddddd1-1111-4111-8111-111111111111',
              productoId: 'aaaaaaa1-0000-4000-8000-000000000009',
              tipo: 'HEROE',
              nombrePropio: 'Aquiles de la Ceniza',
              parteArmadura: null,
              disponible: true,
              subastaId: null,
            },
          ],
          numero: 0,
          tamanio: 16,
          totalElementos: 1,
          totalPaginas: 1,
          ultima: true,
        }),
      ],
      [
        '**/api/v1/inventario/heroes/*/equipamiento',
        json({
          heroeId: 'ddddddd1-1111-4111-8111-111111111111',
          armas: ['eeeeeee1-1111-4111-8111-111111111111'],
          armaduras: { CASCO: 'eeeeeee2-2222-4222-8222-222222222222' },
          items: [],
        }),
      ],
      [
        '**/api/v1/torneos',
        json([
          {
            id: 'fffffff1-1111-4111-8111-111111111111',
            nombre: 'Copa del Nexo',
            estado: 'INSCRIPCIONES_ABIERTAS',
            creadoEn: new Date(Date.now() - 86_400_000).toISOString(),
            inscripcionesCierranEn: new Date(Date.now() + 3 * 86_400_000).toISOString(),
            costoInscripcion: 200,
            equiposInscritos: 5,
            cupos: 8,
            campeonEquipoId: null,
          },
        ]),
      ],
      [
        '**/api/v1/users/*/notifications*',
        json({
          usuarioId: 'qa',
          noLeidas: 2,
          avisos: [
            {
              id: 'n-1',
              tipo: 'TORNEO',
              titulo: 'Se abrió la Copa del Nexo',
              cuerpo: 'Quedan tres cupos. Inscribe a tu equipo antes del cierre.',
              creadaEn: new Date(Date.now() - 7_200_000).toISOString(),
              leida: true,
            },
            {
              id: 'n-2',
              tipo: 'SUBASTA',
              titulo: 'Te superaron en una subasta',
              cuerpo: 'Otro postor ofreció más por Hacha de Obsidiana Fracturada.',
              creadaEn: new Date(Date.now() - 3_600_000).toISOString(),
              leida: false,
            },
            {
              id: 'n-3',
              tipo: 'SALA',
              titulo: 'Tu sala se llenó',
              cuerpo: 'Seis jugadores listos. El anfitrión puede iniciar el combate.',
              creadaEn: new Date(Date.now() - 600_000).toISOString(),
              leida: false,
            },
          ],
        }),
      ],
    ],
    exige: [
      '[data-zona="saldo"]',
      '[data-zona="heroe"]',
      '[data-zona="torneo"]',
      '[data-zona="avisos"]',
    ],
  },
  {
    id: 'pujas-activas',
    titulo: 'subastas con una puja urgente, rareza y credito comprometido',
    ruta: 'cuentas/pujas.html',
    sesion: () => sesionDe('qa_pujas', 'JUGADOR'),
    rutas: [
      [
        '**/api/v1/subastas?*',
        // Funcion, no cuerpo fijo: `fechaFin` se calcula al responder, para
        // que los ocho segundos cuenten desde que la vista pide y no desde
        // que se cargo este modulo (con mas escenarios delante, el contador
        // llegaba ya vencido y el latido no salia).
        () =>
          json({
            contenido: [subastaUrgente(), subastaTranquila(), subastaSinRareza()],
            pagina: 0,
            tamano: 16,
            totalElementos: 3,
            totalPaginas: 1,
          }),
      ],
      [
        '**/api/v1/mis-pujas/resumen',
        // `MiResumen` de ms-subastas-pujas.yaml: el banco decía `subastasConPuja`
        // y `saldoTotal`, que no existen, y la vista pintaba «Vas ganando en:
        // undefined».
        json({ creditosRetenidos: '2230', saldoDisponible: null, subastasGanando: 2 }),
      ],
    ],
    // Lo que tiene que haberse pintado para que la prueba signifique algo.
    exige: ['.tarjeta-subasta', '.badge-epica', '.animacion-latido'],
  },
  {
    id: 'batallas-con-salas',
    titulo: 'listado de batallas con salas abiertas, llenas y privadas',
    ruta: 'plataforma/salas-partidas/batallas.html',
    sesion: () => sesionDe('qa_salas', 'JUGADOR'),
    rutas: [
      [
        '**/api/v1/salas?*',
        json({
          contenido: [
            sala({ id: 'bbbbbbb1-1111-4111-8111-111111111111', ocupacion: 4 }),
            sala({
              id: 'bbbbbbb2-2222-4222-8222-222222222222',
              estado: 'LLENA',
              ocupacion: 6,
            }),
            sala({
              id: 'bbbbbbb3-3333-4333-8333-333333333333',
              estado: 'PRIVADA',
              privada: true,
              ocupacion: 1,
            }),
          ],
          pagina: 0,
          tamano: 12,
          totalElementos: 3,
          totalPaginas: 1,
        }),
      ],
    ],
    exige: ['[data-sala]', '.distintivo--llena', '.distintivo--privada'],
  },
  {
    // R16.1 sobre la superficie que R5 estreno: la ficha de un heroe con sus
    // estadisticas reales y las acciones de su prototipo. Es marcado nuevo
    // —dos secciones, un titulo y una lista— dentro de un dialogo modal, que
    // es donde se concentran los `role` mal puestos y los contrastes de texto
    // secundario.
    id: 'inventario-con-ficha-de-heroe',
    titulo: 'ficha de un heroe con sus estadisticas y las acciones del prototipo',
    ruta: 'contenido/inventario/inventario.html',
    sesion: () => sesionDe('qa_heroes', 'JUGADOR'),
    rutas: [
      [
        '**/api/v1/inventario/elementos?*',
        json({
          elementos: [
            {
              id: 'ddddddd1-1111-4111-8111-111111111111',
              productoId: 'aaaaaaa1-0000-4000-8000-000000000009',
              tipo: 'HEROE',
              nombrePropio: 'Aquiles de la Ceniza',
              parteArmadura: null,
              disponible: true,
              subastaId: null,
            },
          ],
          numero: 0,
          tamanio: 16,
          totalElementos: 1,
          totalPaginas: 1,
          ultima: true,
        }),
      ],
      [
        '**/api/v1/productos/aaaaaaa1-0000-4000-8000-000000000009',
        json({
          id: 'aaaaaaa1-0000-4000-8000-000000000009',
          nombre: 'Guerrero Tanque',
          tipo: 'HEROE',
          prototipo: 'Guerrero Tanque',
          descripcion: 'Aguanta lo que otros no.',
          imagen: null,
          estado: 'ACTIVO',
          tiraje: -1,
        }),
      ],
      // Las estadisticas del heroe DEL JUGADOR, con su equipamiento aplicado.
      [
        '**/api/v1/inventario/heroes/*/estadisticas',
        json({
          heroeId: 'ddddddd1-1111-4111-8111-111111111111',
          poder: 10,
          vida: 52,
          defensa: 13,
          ataque: { base: 10, cantidadDados: 1, caras: 6 },
          dano: null,
          sanar: null,
        }),
      ],
      // Y las acciones DEL PROTOTIPO, del catalogo de heroes.
      [
        '**/api/v1/heroes/Guerrero%20Tanque',
        json({
          nombre: 'Guerrero Tanque',
          tipo: 'TANQUE',
          descripcion: 'Aguanta lo que otros no.',
          esSanador: false,
          acciones: [
            { nombre: 'Golpe de escudo', costo: '2 puntos de poder', efecto: 'Dano directo.' },
            { nombre: 'Muro', costo: '3 puntos de poder', efecto: 'Sube la defensa un turno.' },
            { nombre: 'Embate', costo: '5 puntos de poder', efecto: 'Dano en area.' },
          ],
        }),
      ],
      [
        '**/api/v1/inventario/heroes/*/equipamiento',
        json({
          heroeId: 'ddddddd1-1111-4111-8111-111111111111',
          armas: [],
          armaduras: {},
          items: [],
        }),
      ],
    ],
    // La ficha es un dialogo: hay que abrirlo para auditarlo.
    interaccion: async (pagina) => {
      await pagina.locator('.vitrina__detalle').first().click();
    },
    exige: ['.ficha', '.ficha__seccion', '.ficha__acciones', '.ficha__seccion-nota'],
  },
  {
    id: 'tienda-con-catalogo',
    titulo: 'tienda con precios, rebaja y carrito con importes',
    ruta: 'cuentas/tienda.html',
    sesion: () => sesionDe('qa_tienda', 'JUGADOR'),
    rutas: [
      [
        // R16 — la vitrina se mudó a /api/v1/vitrina (ecommerce-carrito.yaml
        // 1.2.0) y sus ids son los UUID del catálogo maestro. Con la ruta vieja
        // la vista no pintaba nada y el escenario se ponía rojo en `exige`, que
        // es justo para lo que está. La rebaja y el precio ausente ya no los
        // manda la vitrina 1.2.0, pero la tarjeta los sigue sabiendo pintar y
        // su accesibilidad se sigue auditando.
        '**/api/v1/vitrina*',
        json({
          content: [
            producto({ id: 'aaaaaaa1-0000-4000-8000-000000000001', nombre: 'Yelmo del Alba' }),
            producto({
              id: 'aaaaaaa1-0000-4000-8000-000000000002',
              nombre: 'Amuleto de Brasa',
              precioOriginal: 20000,
              precioFinal: 16000,
              enPromocion: true,
              porcentajeDescuento: 20,
            }),
            producto({
              id: 'aaaaaaa1-0000-4000-8000-000000000003',
              nombre: 'Pocion sin precio',
              precioFinal: null,
            }),
          ],
        }),
      ],
      [
        '**/api/v1/carrito',
        json({
          id: 9,
          usuarioId: 'qa',
          total: 36000,
          items: [
            {
              id: 1,
              cantidad: 2,
              precioUnitario: 18000,
              subtotal: 36000,
              producto: { nombre: 'Yelmo del Alba', moneda: 'COP' },
            },
          ],
        }),
      ],
    ],
    // El descuento y el precio ausente son los dos estados que FI-R2 anadio.
    exige: ['.product-card', '.badge-descuento', '.precio-ausente', '.cart-item'],
  },
];

function sala(cambios = {}) {
  return {
    id: 'bbbbbbb1-1111-4111-8111-111111111111',
    estado: 'ABIERTA',
    modalidad: 'HASTA_SEIS',
    maximoParticipantes: 6,
    ocupacion: 4,
    recompensaCreditos: 320,
    incluirHeroeIA: true,
    heroesIA: 1,
    privada: false,
    tamanoEquipo: null,
    idAnfitrion: 'ccccccc1-1111-4111-8111-111111111111',
    participantes: [],
    idPartida: null,
    creadaEn: new Date().toISOString(),
    ...cambios,
  };
}

function producto(cambios = {}) {
  return {
    id: 'aaaaaaa1-0000-4000-8000-000000000001',
    nombre: 'Yelmo del Alba',
    imagenUrl: null,
    descripcion: 'Acero claro, forjado al amanecer.',
    habilidades: 'Defensa +4',
    tipo: 'ARMADURA',
    precioFinal: 18500,
    precioOriginal: 18500,
    moneda: 'COP',
    enPromocion: false,
    porcentajeDescuento: 0,
    esPropio: false,
    enListaDeseos: false,
    ...cambios,
  };
}
