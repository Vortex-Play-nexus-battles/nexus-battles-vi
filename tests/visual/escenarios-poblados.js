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

import { join, resolve } from 'node:path';

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
   UXC-1 — los ocho prototipos de la Tabla 6 en «Mi inventario». DATOS DE
   LABORATORIO: los nombres propios son inventados para la captura y las
   cifras son las de nivel 1 de la Tabla 6 del documento; en produccion todo
   sale del inventario y del catalogo. Estados reales del contrato: uno sin
   equipo (no puede combatir), uno bloqueado por subasta.
   ------------------------------------------------------------------------- */
const f = (base, cantidadDados, caras) =>
  base === null ? null : { base, cantidadDados, caras, formula: `${base ? `${base} + ` : ''}${cantidadDados}d${caras}` };

const OCHO_HEROES = [
  ['Aquiles de la Ceniza', 'Guerrero Tanque', { poder: 10, vida: 44, defensa: 11, ataque: f(10, 1, 6), dano: f(0, 1, 4), sanar: null }, 4],
  ['Vorn el Filo', 'Guerrero Armas', { poder: 8, vida: 44, defensa: 11, ataque: f(10, 1, 6), dano: f(0, 1, 6), sanar: null }, 2],
  ['Ignis', 'Mago Fuego', { poder: 8, vida: 40, defensa: 10, ataque: f(10, 1, 8), dano: f(0, 1, 8), sanar: null }, 0],
  ['Nieve de Arel', 'Mago Hielo', { poder: 10, vida: 40, defensa: 10, ataque: f(10, 1, 8), dano: f(0, 1, 6), sanar: null }, 1],
  ['Sombra Verde', 'Pícaro Veneno', { poder: 8, vida: 36, defensa: 8, ataque: f(10, 1, 10), dano: f(0, 1, 6), sanar: null }, 2],
  ['Kael', 'Pícaro Machete', { poder: 8, vida: 36, defensa: 8, ataque: f(10, 1, 10), dano: f(0, 1, 8), sanar: null }, 3],
  ['Oyá', 'Chamán', { poder: 10, vida: 28, defensa: 4, ataque: null, dano: null, sanar: f(6, 1, 6) }, 2],
  ['Doctora Lumen', 'Médico', { poder: 10, vida: 28, defensa: 4, ataque: null, dano: null, sanar: f(4, 1, 8) }, 1],
].map(([nombrePropio, prototipo, estadisticas, ranuras], i) => ({
  elemento: {
    id: `he00000${i + 1}-1111-4111-8111-111111111111`,
    productoId: `pe00000${i + 1}-0000-4000-8000-000000000000`,
    tipo: 'HEROE',
    nombrePropio,
    parteArmadura: null,
    // El Picaro Veneno esta publicado en subasta: bloqueado (HU-INV-010).
    disponible: i !== 4,
    subastaId: i === 4 ? 'aaaaaaa2-2222-4222-8222-222222222222' : null,
  },
  prototipo,
  estadisticas,
  ranuras,
}));

const OBJETOS_DE_LABORATORIO = [
  ['ob000001', 'ARMA', 'Hacha de Obsidiana', null],
  ['ob000002', 'ARMA', 'Espada del Alba', null],
  ['ob000003', 'ARMADURA', 'Yelmo del Alba', 'CASCO'],
  ['ob000004', 'ARMADURA', 'Coraza del Centinela', 'PECHO'],
  ['ob000005', 'ITEM', 'Poción de brasa', null],
  ['ob000006', 'HABILIDAD', 'Grito de guerra', null],
  ['ob000007', 'EPICA', 'Reliquia del Nexo', null],
  ['ob000008', 'ARMA', 'Arco en subasta', null],
].map(([id, tipo, nombrePropio, parteArmadura], i) => ({
  id: `${id}-1111-4111-8111-111111111111`,
  productoId: `po00000${i + 1}-0000-4000-8000-000000000000`,
  tipo,
  nombrePropio,
  parteArmadura,
  disponible: i !== 7,
  subastaId: i === 7 ? 'aaaaaaa1-1111-4111-8111-111111111111' : null,
}));

/** El equipo de cada heroe: sus primeras `ranuras` piezas del laboratorio. */
function equipoDeLaboratorio(heroeId) {
  const heroe = OCHO_HEROES.find((h) => h.elemento.id === heroeId);
  const ranuras = heroe?.ranuras ?? 0;
  const armas = [];
  const armaduras = {};
  const items = [];
  // El Tanque lleva el hacha, el yelmo, la coraza y la pocion; el resto, lo que
  // le toque del mismo lote (el laboratorio no valida exclusividad).
  // El Tanque lleva el hacha, el yelmo, la coraza y la pocion; el Armas, la
  // espada. Los demas llevan piezas que no estan en este lote (identificadores
  // propios): cuentan como ranuras ocupadas y no se repiten entre heroes.
  const lotes = {
    'Guerrero Tanque': [0, 2, 3, 4].map((i) => OBJETOS_DE_LABORATORIO[i]),
    'Guerrero Armas': [OBJETOS_DE_LABORATORIO[1]],
  };
  const lote = lotes[heroe?.prototipo] ?? [];
  for (const pieza of lote) {
    if (pieza.tipo === 'ARMA') armas.push(pieza.id);
    else if (pieza.tipo === 'ARMADURA') armaduras[pieza.parteArmadura] = pieza.id;
    else items.push(pieza.id);
  }
  for (let i = lote.length; i < ranuras; i += 1) {
    items.length < 2 ? items.push(`${heroeId}-item-${i}`) : armas.push(`${heroeId}-arma-${i}`);
  }
  return { heroeId, armas, armaduras, items };
}

function rutasDeOchoHeroes() {
  const elementos = [...OCHO_HEROES.map((h) => h.elemento), ...OBJETOS_DE_LABORATORIO];
  const idDe = (ruta, desdeFinal = 0) =>
    new URL(ruta.request().url()).pathname.split('/').slice(-1 - desdeFinal)[0];
  return [
    [
      '**/api/v1/inventario/elementos?*',
      json({
        elementos,
        numero: 0,
        tamanio: 16,
        totalElementos: elementos.length,
        totalPaginas: 1,
        ultima: true,
      }),
    ],
    [
      '**/api/v1/productos/*',
      (ruta) => {
        const id = idDe(ruta);
        const heroe = OCHO_HEROES.find((h) => h.elemento.productoId === id);
        const objeto = OBJETOS_DE_LABORATORIO.find((o) => o.productoId === id);
        return json({
          id,
          nombre: heroe ? heroe.prototipo : (objeto?.nombrePropio ?? 'Producto'),
          tipo: heroe ? 'HEROE' : (objeto?.tipo ?? 'ARMA'),
          prototipo: heroe ? heroe.prototipo : null,
          descripcion: heroe ? `Prototipo ${heroe.prototipo} del catálogo.` : 'Forjado en el Nexo.',
          imagen: null,
          estado: 'ACTIVO',
          tiraje: -1,
        });
      },
    ],
    [
      '**/api/v1/inventario/heroes/*/estadisticas',
      (ruta) => {
        const id = idDe(ruta, 1);
        const heroe = OCHO_HEROES.find((h) => h.elemento.id === id);
        return json({ heroeId: id, ...(heroe?.estadisticas ?? {}) });
      },
    ],
    [
      '**/api/v1/inventario/heroes/*/equipamiento',
      (ruta) => json(equipoDeLaboratorio(idDe(ruta, 1))),
    ],
    [
      '**/api/v1/heroes/*',
      (ruta) => {
        const nombre = decodeURIComponent(idDe(ruta));
        return json({
          nombre,
          tipo: nombre.split(' ')[0],
          descripcion: `Ficha del prototipo ${nombre} (laboratorio).`,
          esSanador: ['Chamán', 'Médico'].includes(nombre),
          estadisticasNivel1: OCHO_HEROES.find((h) => h.prototipo === nombre)?.estadisticas ?? {},
          acciones: [
            { nombre: 'Golpe con escudo', costo: '2 puntos de poder', efecto: '+2 al ataque' },
            { nombre: 'Mano de piedra', costo: '4 puntos de poder', efecto: '+12 a la defensa' },
            {
              nombre: 'Defensa feroz',
              costo: '6 puntos de poder',
              efecto: 'Inmune al daño físico y (3d6) al daño mágico',
            },
          ],
        });
      },
    ],
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

/**
 * UXC-2 — lo que la vista de combate pide para la barra de accion del heroe
 * propio (`comun/heroe-propio.js`): su elemento en el inventario, el producto
 * (prototipo), la ficha del prototipo (Tabla 7) y sus cifras con el equipo.
 * DATOS DE LABORATORIO con los valores de la Tabla 7 para Guerrero Tanque.
 */
const RUTAS_DEL_HEROE_EN_COMBATE = [
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
    '**/api/v1/productos/*',
    json({
      id: 'aaaaaaa1-0000-4000-8000-000000000009',
      nombre: 'Guerrero Tanque',
      tipo: 'HEROE',
      prototipo: 'Guerrero Tanque',
      imagen: null,
      estado: 'ACTIVO',
      tiraje: -1,
    }),
  ],
  [
    '**/api/v1/heroes/*',
    json({
      nombre: 'Guerrero Tanque',
      tipo: 'Guerrero',
      descripcion: 'Aguanta lo que otros no.',
      esSanador: false,
      estadisticasNivel1: { poder: 10, vida: 44, defensa: 11, ataque: '10 + 1d6', dano: '1d4' },
      acciones: [
        { nombre: 'Golpe con escudo', costo: '2 puntos de poder', efecto: '+2 al ataque' },
        { nombre: 'Mano de piedra', costo: '4 puntos de poder', efecto: '+12 a la defensa' },
        {
          nombre: 'Defensa feroz',
          costo: '6 puntos de poder',
          efecto: 'Inmune al daño físico y (3d6) al daño mágico',
        },
      ],
    }),
  ],
  [
    '**/api/v1/inventario/heroes/*/estadisticas',
    json({ heroeId: 'ddddddd1-1111-4111-8111-111111111111', poder: 10, vida: 52, defensa: 13 }),
  ],
];

function escenarioDeCombate(id, titulo, { partida, mensajes = [], exige, canal = {}, interaccion }) {
  return {
    id,
    titulo,
    ruta: `plataforma/salas-partidas/sala-batalla.html?partida=${ID_PARTIDA}`,
    sesion: () => SESION_COMBATE,
    rutas: [[`**/api/v1/partidas/${partida.id ?? ID_PARTIDA}`, json(partida)], ...RUTAS_DEL_HEROE_EN_COMBATE],
    canal: { mensajes: { [`/tema/partidas/${ID_PARTIDA}`]: mensajes }, ...canal },
    ...(interaccion ? { interaccion } : {}),
    exige,
  };
}

/** Un aviso `partida.accion.resuelta` con la forma del contrato. */
function accionResuelta(idEjecutor, categoria, afectados) {
  return {
    tipo: 'partida.accion.resuelta',
    idPartida: ID_PARTIDA,
    idEjecutor,
    accion: { codigo: 'ATAQUE_BASICO', nombre: categoria, icono: null },
    afectados,
  };
}

/** Seis participantes, tres contra tres (HU-SAL-004), para medir el HUD lleno. */
function partidaDeSeis() {
  const base = partidaEnCurso();
  const extra = [
    ['cccccc03-3333-4333-8333-333333333333', 'Nieve de Arel', 40, 40, 1, false],
    ['cccccc04-4444-4444-8444-444444444444', 'Doctora Lumen', 28, 28, 1, false],
    ['cccccc05-5555-4555-8555-555555555555', 'Sombra Verde', 20, 36, 2, true],
    ['cccccc06-6666-4666-8666-666666666666', 'Kael', 36, 36, 2, true],
  ].map(([jugador, nombre, vidaActual, vidaMaxima, equipo, esIA]) => ({
    jugador,
    heroe: { id: `h-${jugador}`, nombre, retratoUrl: null, nivel: null, vidaActual, vidaMaxima, efectosActivos: [] },
    esIA,
    listo: true,
    equipo,
    creditosApostados: 0,
  }));
  base.participantes[0].equipo = 1;
  base.participantes[1].equipo = 2;
  base.participantes.push(...extra);
  return base;
}

/* ---------------------------------------------------------------------------
   Torneo — UX-GAME-5. `Torneo`, `Equipo` y `Encuentro` de torneos.yaml: ocho
   equipos, catorce encuentros (1-6 y 11 ganadores, 7-10, 12 y 13 secundarios,
   14 final), en curso con la primera ronda jugada.
   ------------------------------------------------------------------------- */
const ID_TORNEO = 'fffffff1-1111-4111-8111-111111111111';
const NOMBRES_DE_EQUIPO = [
  'Lobos del Alba',
  'Centinelas',
  'Brasa Negra',
  'Hijos del Nexo',
  'Vanguardia',
  'Eco de Hierro',
  'Máquina 7',
  'Máquina 8',
];
const EQUIPOS = NOMBRES_DE_EQUIPO.map((nombre, i) => ({
  id: `eeeeeee${i + 1}-1111-4111-8111-11111111111${i + 1}`,
  torneoId: ID_TORNEO,
  nombre,
  avatar: 'escudo',
  ia: i >= 6,
  capitanUid: i >= 6 ? null : `aaaaaaa${i + 1}-1111-4111-8111-111111111111`,
  integrantes: i >= 6 ? [] : [`aaaaaaa${i + 1}-1111-4111-8111-111111111111`],
  inscrito: true,
  pagadoPor: null,
  reservaId: null,
  posicion: i + 1,
  derrotas: [1, 3, 5, 7].includes(i + 1) ? 0 : 1,
  eliminado: false,
}));
const E = (i) => EQUIPOS[i - 1].id;

function encuentro(numero, llave, ronda, a, b, ganador, estado) {
  return {
    numero,
    llave,
    ronda,
    equipoA: a,
    equipoB: b,
    ganador,
    partidaId: ganador
      ? `dddddd${String(numero).padStart(2, '0')}-1111-4111-8111-111111111111`
      : null,
    estado,
    registradoPor: ganador ? 'salas-partidas' : null,
    motivo: null,
  };
}

export function torneoEnCurso() {
  return {
    id: ID_TORNEO,
    nombre: 'Copa del Nexo',
    estado: 'EN_CURSO',
    creadoEn: new Date(Date.now() - 5 * 86_400_000).toISOString(),
    inscripcionesCierranEn: new Date(Date.now() - 86_400_000).toISOString(),
    costoInscripcion: 200,
    equiposInscritos: 8,
    cupos: 8,
    campeonEquipoId: null,
    creadoPor: 'aaaaaaa9-1111-4111-8111-111111111111',
    iniciadoEn: new Date(Date.now() - 3600_000).toISOString(),
    finalizadoEn: null,
    motivoCancelacion: null,
    equipos: EQUIPOS,
    encuentros: [
      encuentro(1, 'GANADORES', 1, E(1), E(2), E(1), 'JUGADO'),
      encuentro(2, 'GANADORES', 1, E(3), E(4), E(3), 'JUGADO'),
      encuentro(3, 'GANADORES', 1, E(5), E(6), E(5), 'JUGADO'),
      encuentro(4, 'GANADORES', 1, E(7), E(8), E(7), 'JUGADO'),
      encuentro(5, 'GANADORES', 2, E(1), E(3), null, 'LISTO'),
      encuentro(6, 'GANADORES', 2, E(5), E(7), null, 'LISTO'),
      encuentro(7, 'SECUNDARIOS', 1, E(2), E(4), null, 'LISTO'),
      encuentro(8, 'SECUNDARIOS', 1, E(6), E(8), null, 'LISTO'),
      encuentro(9, 'SECUNDARIOS', 2, null, null, null, 'PENDIENTE'),
      encuentro(10, 'SECUNDARIOS', 2, null, null, null, 'PENDIENTE'),
      encuentro(11, 'GANADORES', 3, null, null, null, 'PENDIENTE'),
      encuentro(12, 'SECUNDARIOS', 3, null, null, null, 'PENDIENTE'),
      encuentro(13, 'SECUNDARIOS', 4, null, null, null, 'PENDIENTE'),
      encuentro(14, 'FINAL', 5, null, null, null, 'PENDIENTE'),
    ],
  };
}

function transaccion(i, cambios = {}) {
  return {
    id: `11111111-1111-4111-8111-${String(i).padStart(12, '0')}`,
    refId: `ref-${1000 + i}`,
    monto: 18500,
    moneda: 'COP',
    concepto: 'Compra en tienda · Yelmo del Alba',
    resultado: 'APROBADO',
    comprobanteUrl: null,
    creado: new Date(Date.now() - i * 86_400_000).toISOString(),
    ...cambios,
  };
}

/* ---------------------------------------------------------------------------
   Consola — UX-GAME-6. Tablas y colas con filas: auditoría
   (`AuditLogResponse`/`PaginaDeAuditoria` de ms-cumplimiento-auditoria.yaml),
   lista negra (lista de cadenas, moderacion-lista-negra.yaml) y cola de
   moderación (`ColaDeModeracionResponse` de comentarios.yaml).
   ------------------------------------------------------------------------- */
function registroDeAuditoria(i, cambios = {}) {
  return {
    id: `33333333-1111-4111-8111-${String(i).padStart(12, '0')}`,
    fechaHora: new Date(Date.now() - i * 5_400_000).toISOString(),
    administrador: 'qa_superadministrador',
    tipoAccion: 'ACTUALIZACION',
    afectado: 'jugador_42',
    valorAnterior: 'JUGADOR',
    valorNuevo: 'MODERADOR',
    motivo: 'Refuerzo del equipo de moderación para el torneo.',
    ipOrigen: '10.20.30.40',
    ...cambios,
  };
}

function comentarioReportado(i, cambios = {}) {
  return {
    comentario: {
      id: `44444444-1111-4111-8111-${String(i).padStart(12, '0')}`,
      productoId: 'aaaaaaa1-0000-4000-8000-000000000001',
      autorId: `55555555-1111-4111-8111-${String(i).padStart(12, '0')}`,
      apodoAutor: ['thar_vex', 'lumen_9', 'kira_del_sur'][i % 3],
      texto: [
        'El yelmo llegó con la defensa que promete; lo recomiendo para tanques.',
        'Precio abusivo para lo que da. No lo compren.',
        'Buen objeto, aunque la descripción exagera el bono de defensa.',
      ][i % 3],
      imagenes: [],
      estrellas: [5, 1, 3][i % 3],
      fechaPublicacion: new Date(Date.now() - i * 7_200_000).toISOString(),
      estado: 'EN_REVISION',
      calificacionDescartada: false,
    },
    reportes: [3, 7, 1][i % 3],
    porCategoria: { OFENSIVO: [1, 5, 0][i % 3], SPAM: [2, 2, 1][i % 3] },
    primerReporte: new Date(Date.now() - i * 7_000_000).toISOString(),
    ...cambios,
  };
}


/* ---------------------------------------------------------------------------
   UXC-5 — misiones.

   La vista de misiones pide sus datos a un PUERTO (`contenido/misiones/
   fuente-misiones.js`) y no a una ruta HTTP, porque el servicio de misiones no
   existe todavía y no se inventa su dirección. Para fotografiar los estados
   que tendrá, el laboratorio sirve en lugar de ese módulo el de
   `laboratorio/fuente-misiones.js`: misiones de ejemplo, marcadas como tales
   en el propio archivo y con un aviso visible en cada captura. Sin esta
   sustitución la vista pinta lo que ve un jugador hoy: el estado honesto.

   El configurador de estrategia sí habla con servicios que existen
   (inventario, productos y héroes): esos se simulan como los demás, con la
   forma de su contrato.
   ------------------------------------------------------------------------- */

// Playwright transpila a CommonJS (hay `__dirname`); la herramienta de
// capturas corre como módulo desde `frontend/app-web` (no lo hay).
const AQUI_LABORATORIO =
  typeof __dirname === 'undefined' ? resolve(process.cwd(), '../../tests/visual') : __dirname;

/** La ruta que cambia la fuente de misiones por la del laboratorio. */
const MISIONES_DE_LABORATORIO = [
  '**/contenido/misiones/fuente-misiones.js',
  {
    status: 200,
    contentType: 'text/javascript; charset=utf-8',
    path: join(AQUI_LABORATORIO, 'laboratorio', 'fuente-misiones.js'),
  },
];

/**
 * Tabla 7 del documento, como la publica el catálogo de héroes
 * (`PrototiposIniciales`): las tres acciones de cada prototipo, en orden de
 * desbloqueo (niveles 1, 4 y 8).
 */
const TABLA_7 = {
  'Guerrero Tanque': [
    ['Golpe con escudo', 2, '+2 al ataque'],
    ['Mano de piedra', 4, '+12 a la defensa'],
    ['Defensa feroz', 6, 'Inmune al daño físico y (3d6) al daño mágico'],
  ],
  'Guerrero Armas': [
    ['Embate sangriento', 4, '+2 al ataque, +1 de daño'],
    ['Lanza de los dioses', 4, '+2 al daño'],
    ['Golpe de tormenta', 6, '+(3d6) al ataque, +2 al daño'],
  ],
  'Mago Fuego': [
    ['Misiles de magma', 2, '+1 al ataque, +2 de daño'],
    ['Vulcano', 6, '+3 al ataque, +(3d9) al daño'],
    ['Pare de fuego', 4, '+1 al ataque y retorna el (0dx) daño causado por el oponente en el turno anterior'],
  ],
  'Mago Hielo': [
    ['Lluvia de hielo', 2, '+2 al ataque, +2 de daño'],
    ['Cono de hielo', 6, '+2 al daño y afecta el ataque del enemigo en un (1d3) durante los dos turnos siguientes'],
    ['Bola de hielo', 4, '+2 al ataque y afecta en (0d4) al daño causado por el oponente'],
  ],
  'Pícaro Veneno': [
    ['Flor de loto', 2, '+(4d8) al daño'],
    ['Agonía', 4, '+(2d9) de daño'],
    ['Piquete', 4, '+1 al ataque por dos turnos, +2 al daño por 1 turno'],
  ],
  'Pícaro Machete': [
    ['Cortada', 2, '+2 al daño por dos turnos'],
    ['Machetazo', 4, '+(2d8) al daño, +1 al ataque'],
    ['Planazo', 4, '+(2d8) al ataque, +1 al daño'],
  ],
  Chamán: [
    ['Toque de la Vida', 2, '+2 de sanación'],
    ['Vínculo Natural', 4, '+2 de sanación por dos turnos'],
    ['Canto del Bosque', 6, 'Sana a todo el grupo +(2d6) durante dos turnos'],
  ],
  Médico: [
    ['Curación Directa', 2, '+2 de sanación'],
    ['Neutralización de Efectos', 4, '+2 y +(2d4) de sanación'],
    ['Reanimación', null, 'Sana el 100% de la vida del compañero'],
  ],
};

/** RC-01: una acción en el nivel 1, dos desde el 4, tres desde el 8. */
function accionesEnNivel(prototipo, nivel) {
  let cuantas = 1;
  if (nivel >= 8) {
    cuantas = 3;
  } else if (nivel >= 4) {
    cuantas = 2;
  }
  return (TABLA_7[prototipo] ?? []).slice(0, cuantas);
}

/**
 * El servicio de héroes, de mentira pero con su regla (`EstrategiaDeCombate`):
 * las habilidades válidas son las desbloqueadas más el ataque básico; una
 * rotación que usa otra se rechaza con el motivo, en 200.
 */
function veredictoDeEstrategia(ruta) {
  const { heroe, nivel = 1, rotaciones = [] } = ruta.request().postDataJSON() ?? {};
  const validas = [...accionesEnNivel(heroe, nivel).map(([nombre]) => nombre), 'Ataque básico'];
  const base = { heroe, nivel, habilidadesValidas: validas, comportamientoPorDefecto: 'Ataque básico' };
  for (const [indice, rotacion] of rotaciones.entries()) {
    const ajena = (rotacion.pasos ?? []).find((paso) => !validas.includes(paso));
    if (ajena) {
      return json({
        ...base,
        valida: false,
        porDefecto: false,
        motivo: `La rotación ${indice + 1} usa una habilidad que ${heroe} no posee en nivel ${nivel}: ${ajena}.`,
      });
    }
  }
  return json({
    ...base,
    valida: true,
    porDefecto: rotaciones.length === 0,
    rotaciones: rotaciones.map((r, i) => ({ prioridad: ['Alta', 'Media', 'Baja'][i], pasos: r.pasos })),
  });
}

/** `GET /heroes/{nombre}/niveles/{nivel}` con las cifras de nivel 1 de la Tabla 6. */
function vistaDeHeroeEnNivel(ruta) {
  const partes = new URL(ruta.request().url()).pathname.split('/');
  const nivel = Number(partes.pop());
  partes.pop();
  const nombre = decodeURIComponent(partes.pop());
  const heroe = OCHO_HEROES.find((h) => h.prototipo === nombre);
  const cifras = heroe?.estadisticas ?? {};
  const formula = (x) => (x ? `${x.base ? `${x.base} + ` : ''}${x.cantidadDados}d${x.caras}` : null);
  return json({
    nombre,
    tipo: nombre.split(' ')[0],
    esSanador: ['Chamán', 'Médico'].includes(nombre),
    nivel,
    estadisticas: {
      poder: cifras.poder,
      vida: cifras.vida,
      defensa: cifras.defensa,
      ataque: formula(cifras.ataque),
      dano: formula(cifras.dano),
      sanar: formula(cifras.sanar),
    },
    accionesDisponibles: accionesEnNivel(nombre, nivel).map(([accion, costo, efecto]) => ({
      nombre: accion,
      costo: costo === null ? 'Sin costo de poder' : `${costo} puntos de poder`,
      efecto,
    })),
    multiplicadorDeEfecto: nivel,
  });
}

/** Inventario, catálogo y héroes para el configurador de estrategia. */
function rutasDeEstrategia() {
  return [
    ...rutasDeOchoHeroes(),
    ['**/api/v1/estrategias/validacion', veredictoDeEstrategia],
    ['**/api/v1/heroes/*/niveles/*', vistaDeHeroeEnNivel],
  ];
}

/** Elige una habilidad en un paso del configurador y deja que la vista reaccione. */
async function elegirPaso(pagina, indice, habilidad) {
  await pagina.locator('.estrategia__paso select').nth(indice).selectOption(habilidad);
}

/** Configura dos pasos y comprueba la estrategia contra el servicio de mentira. */
async function prepararEstrategia(pagina) {
  await pagina.locator('.estrategia__paso select').first().waitFor({ timeout: 15_000 });
  await elegirPaso(pagina, 0, 'Golpe con escudo');
  await pagina.locator('[data-accion="anadir-paso"]').first().click();
  await elegirPaso(pagina, 1, 'Ataque básico');
  await pagina.locator('[data-accion="comprobar-estrategia"]').click();
  await pagina.locator('.estrategia__veredicto .aviso--exito').waitFor({ timeout: 15_000 });
}

export const ESCENARIOS = [
  {
    // UXC-1 — «Mi inventario», pestana Heroes: los ocho prototipos de la Tabla
    // 6, reconocibles por simbolo y nombre, con estado (uno sin equipo, uno
    // bloqueado por subasta) y ranuras ocupadas.
    id: 'inventario-ocho-heroes',
    titulo: 'mi inventario: los ocho prototipos con su estado',
    ruta: 'contenido/inventario/inventario.html#heroes',
    sesion: () => sesionDe('qa_heroes8', 'JUGADOR'),
    rutas: rutasDeOchoHeroes(),
    exige: [
      '.hero-card',
      '.hero-card[data-prototipo="picaro-machete"]',
      '.hero-card[data-estado="NO_ELEGIBLE"]',
      '.hero-card[data-estado="BLOQUEADO"]',
      '.stat-block',
    ],
  },
  {
    // UXC-1 — pestana Objetos: sin heroes, y cada objeto dice si esta
    // equipado (y en quien), libre o bloqueado.
    id: 'inventario-objetos-con-estado',
    titulo: 'mi inventario: objetos equipados, libres y bloqueados',
    ruta: 'contenido/inventario/inventario.html#objetos',
    sesion: () => sesionDe('qa_objetos', 'JUGADOR'),
    rutas: rutasDeOchoHeroes(),
    exige: [
      '.inventario__contenido .vitrina__producto',
      '.vitrina__estado[data-estado="EQUIPADO"]',
      '.vitrina__estado[data-estado="BLOQUEADO"]',
    ],
  },
  {
    // UXC-1 — la ficha de un heroe desde su carta: cifras (StatBlock) y las
    // tres acciones con coste, carga y efecto.
    id: 'inventario-ficha-de-heroe-uxc',
    titulo: 'ficha de héroe con cifras y acciones como cartas',
    ruta: 'contenido/inventario/inventario.html#heroes',
    sesion: () => sesionDe('qa_ficha', 'JUGADOR'),
    rutas: rutasDeOchoHeroes(),
    interaccion: async (pagina) => {
      await pagina.locator('[data-accion="ver-ficha"]').first().click();
    },
    exige: ['.ficha', '.ficha .stat-block', '.ficha__accion', '.ficha__accion-carga'],
  },
  {
    id: 'auditoria-con-registros',
    titulo: 'registro de auditoría con cinco entradas y paginación',
    ruta: 'cuentas/auditoria.html',
    sesion: () => sesionDe('qa_superadministrador', 'SUPER_ADMINISTRADOR'),
    rutas: [
      [
        '**/api/v1/admin/auditoria*',
        json({
          content: [
            registroDeAuditoria(1, { tipoAccion: 'CAMBIO_ROL' }),
            registroDeAuditoria(2, {
              tipoAccion: 'SANCION',
              afectado: 'thar_vex',
              valorAnterior: null,
              valorNuevo: 'SUSPENSION_7_DIAS',
              motivo: 'Lenguaje ofensivo reiterado en el chat de sala.',
            }),
            registroDeAuditoria(3, {
              tipoAccion: 'CREACION',
              afectado: 'Yelmo del Alba',
              valorAnterior: null,
              valorNuevo: 'ACTIVO',
              motivo: 'Alta de producto del catálogo.',
            }),
            registroDeAuditoria(4, {
              tipoAccion: 'ELIMINACION_LOGICA',
              afectado: 'Poción caducada',
              valorAnterior: 'ACTIVO',
              valorNuevo: 'SUSPENDIDO',
              motivo: 'Producto retirado del catálogo.',
            }),
            registroDeAuditoria(5, { tipoAccion: 'APROBACION', afectado: 'comentario 44444444' }),
          ],
          totalElements: 128,
          totalPages: 13,
          number: 0,
          size: 10,
          first: true,
          last: false,
        }),
      ],
    ],
    exige: ['table tbody tr, .tabla__fila'],
  },
  {
    id: 'lista-negra-con-terminos',
    titulo: 'lista negra con términos vetados',
    ruta: 'plataforma/moderacion-sanciones/lista-negra-admin.html',
    sesion: () => sesionDe('qa_moderador', 'MODERADOR'),
    rutas: [
      [
        '**/api/v1/lista-negra/terminos',
        json(['admin', 'moderador', 'nexus_oficial', 'soporte', 'staff', 'sistema']),
      ],
    ],
    exige: ['.lista-terminos__fila, li'],
  },
  {
    id: 'moderacion-con-cola',
    titulo: 'cola de moderación con tres comentarios reportados',
    ruta: 'plataforma/comentarios/moderar-comentarios.html',
    sesion: () => sesionDe('qa_moderador', 'MODERADOR'),
    rutas: [
      [
        '**/api/v1/comentarios/moderacion*',
        json({
          entradas: [comentarioReportado(1), comentarioReportado(2), comentarioReportado(3)],
          total: 3,
          pagina: 0,
          tamano: 16,
        }),
      ],
    ],
    exige: ['[data-comentario], .cola__entrada, article'],
  },
  {
    // UX-GAME-5 — el árbol de doble eliminación con la primera ronda jugada.
    id: 'torneo-en-curso',
    titulo: 'torneo en curso con el árbol de doble eliminación',
    ruta: `plataforma/torneos/torneos.html?torneo=${ID_TORNEO}`,
    sesion: () => sesionDe('qa_torneo', 'JUGADOR'),
    rutas: [
      ['**/api/v1/torneos', json([torneoEnCurso()])],
      [`**/api/v1/torneos/${ID_TORNEO}`, json(torneoEnCurso())],
      [`**/api/v1/torneos/${ID_TORNEO}/equipos`, json(EQUIPOS)],
    ],
    exige: ['.encuentro', '.encuentro__equipo--ganador', '.encuentro--finalizado'],
  },
  {
    // UX-GAME-5 — finanzas: el historial con aprobadas, rechazada e
    // indeterminada (`ResumenTransaccion` de transacciones.yaml).
    id: 'historial-con-movimientos',
    titulo: 'historial de transacciones con tres resultados',
    ruta: 'cuentas/historial-transacciones.html',
    sesion: () => sesionDe('qa_finanzas', 'JUGADOR'),
    rutas: [
      [
        '**/api/v1/transacciones/mi-historial*',
        json({
          content: [
            transaccion(1),
            transaccion(2, {
              concepto: 'Compra de créditos · paquete 500',
              monto: 25000,
              resultado: 'RECHAZADO',
            }),
            transaccion(3, {
              concepto: 'Compra de créditos · paquete 1000',
              monto: 45000,
              resultado: 'INDETERMINADO',
            }),
            transaccion(4, { concepto: 'Compra en tienda · Amuleto de Brasa', monto: 16000 }),
          ],
          number: 0,
          totalPages: 3,
          totalElements: 52,
        }),
      ],
    ],
    exige: ['.tabla, table', '[data-resultado], .estado-aprobado, .sello-estado, .badge'],
  },
  {
    // UX-GAME-5 — cofres ganados (forma con la que responde hoy ms-finanzas,
    // `ResumenCofre`; sin contrato publicado, ver creditos.yaml).
    id: 'cofres-ganados',
    titulo: 'mis cofres con tres cofres entregados',
    ruta: 'cuentas/mis-cofres.html',
    sesion: () => sesionDe('qa_cofres', 'JUGADOR'),
    rutas: [
      [
        '**/api/v1/cofres/mios*',
        json({
          content: [
            {
              id: '22222222-1111-4111-8111-000000000001',
              contenido: 'ARMA_RARA',
              entregadoEn: new Date(Date.now() - 86_400_000).toISOString(),
            },
            {
              id: '22222222-1111-4111-8111-000000000002',
              contenido: 'CREDITOS_50',
              entregadoEn: new Date(Date.now() - 3 * 86_400_000).toISOString(),
            },
            {
              id: '22222222-1111-4111-8111-000000000003',
              contenido: 'ARMADURA_EPICA',
              entregadoEn: new Date(Date.now() - 9 * 86_400_000).toISOString(),
            },
          ],
          number: 0,
          totalPages: 1,
          totalElements: 3,
        }),
      ],
    ],
    exige: ['.cofre-tarjeta'],
  },
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
    exige: [
      '.combate__vidas .barra-vida',
      '[data-atacar]:not(:disabled)',
      '[data-zona="turno"]',
      // UXC-2 — las tres especiales, deshabilitadas con su motivo, y el poder.
      '[data-zona="especiales"] .accion-combate--especial:disabled',
      '.medidor-poder',
      '.registro-combate__linea',
    ],
  }),
  // UXC-2 — lo que pasa, narrado: un critico propio, una evasion del rival,
  // una curacion, un efecto activo y el cambio de turno. DATOS DE LABORATORIO.
  escenarioDeCombate('combate-narrado', 'combate con crítico, evasión, curación y efecto', {
    partida: partidaEnCurso({ vidaMia: 38, vidaRival: 14 }),
    mensajes: [
      accionResuelta(SESION_COMBATE.uid, 'CAUSAR_DANO_CRITICO', [
        {
          idJugador: RIVAL_IA,
          vidaActual: 14,
          vidaMaxima: 60,
          diferencia: -9,
          efectosActivos: [{ codigo: 'VENENO', nombre: 'Veneno', icono: null, turnosRestantes: 2 }],
        },
      ]),
      accionResuelta(RIVAL_IA, 'EVADIR_EL_GOLPE', [
        { idJugador: SESION_COMBATE.uid, vidaActual: 35, vidaMaxima: 52, diferencia: -3 },
      ]),
      accionResuelta(SESION_COMBATE.uid, 'CAUSAR_DANO', [
        { idJugador: SESION_COMBATE.uid, vidaActual: 41, vidaMaxima: 52, diferencia: 6 },
      ]),
      {
        tipo: 'partida.turno.cambiado',
        idPartida: ID_PARTIDA,
        idJugador: SESION_COMBATE.uid,
        numeroTurno: 8,
        segundosParaJugar: null,
      },
    ],
    exige: [
      '.registro-combate__linea--critico',
      '.registro-combate__linea--mitigado',
      '.registro-combate__linea--curacion',
      '.efecto',
      '.impacto',
    ],
  }),
  // UXC-2 — seis participantes, tres contra tres: el HUD con seis barras.
  escenarioDeCombate('combate-seis', 'combate de seis, tres contra tres', {
    partida: partidaDeSeis(),
    exige: ['.combate__vidas .barra-vida:nth-child(6)', '[data-atacar]'],
  }),
  // UXC-2 — el canal se cae y no vuelve: la pildora lo dice y ofrece
  // «Reintentar»; los botones se cierran.
  escenarioDeCombate('combate-sin-canal', 'combate con el canal caído y sin reconexión', {
    partida: partidaEnCurso(),
    canal: { cerrarTrasMs: 400, rechazarReconexion: true },
    exige: ['.conexion--reconectando, .conexion--sin-conexion', '[data-atacar]:disabled'],
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
    // UXC-1 — la vitrina es la pestana «Objetos»; los heroes tienen la suya.
    ruta: 'contenido/inventario/inventario.html#objetos',
    sesion: () => sesionDe('qa_inventario', 'JUGADOR'),
    rutas: rutasDeInventario({
      heroeId: 'ddddddd1-1111-4111-8111-111111111111',
      armas: ['ddddddd2-2222-4222-8222-222222222222'],
      armaduras: { CASCO: 'ddddddd3-3333-4333-8333-333333333333' },
      items: [],
    }),
    exige: [
      '.vitrina__producto',
      '.vitrina__producto--no-disponible',
      "[data-tipo='EPICA']",
      '.hero-card',
    ],
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
      // UXC-1 — el equipo se abre desde la carta del heroe.
      await pagina.locator('[data-accion="equipar"]').first().click();
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
    // La ficha es un dialogo: hay que abrirlo para auditarlo. UXC-1 — desde
    // la carta del heroe, que es donde estan los heroes.
    interaccion: async (pagina) => {
      await pagina.locator('[data-accion="ver-ficha"]').first().click();
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
  // UXC-5 — misiones. Sin la fuente del laboratorio, la vista pinta el estado
  // honesto de hoy; con ella, los estados que tendrá. Ver arriba.
  {
    id: 'misiones-sin-abrir',
    titulo: 'misiones hoy: qué pasa, por qué y qué se puede hacer, sin tarjetas de mentira',
    ruta: 'contenido/misiones/misiones.html',
    sesion: () => sesionDe('qa_misiones', 'JUGADOR'),
    rutas: [],
    exige: [
      '.misiones-estado[data-estado="sin-abrir"]',
      '.misiones-sin-abrir__categoria',
      '.misiones-estado [data-accion="preparar-estrategia"]',
    ],
  },
  {
    id: 'misiones-estrategia',
    titulo: 'misiones sin abrir: el estado honesto y la estrategia comprobada de verdad',
    ruta: 'contenido/misiones/misiones.html#estrategia',
    sesion: () => sesionDe('qa_misiones', 'JUGADOR'),
    rutas: rutasDeEstrategia(),
    interaccion: prepararEstrategia,
    exige: [
      '.misiones-estado[data-estado="sin-abrir"]',
      '.estrategia__heroe',
      '.estrategia__vista-previa .stat-block',
      '.estrategia__rotacion',
      '.estrategia__veredicto .aviso--exito',
    ],
  },
  {
    id: 'misiones-tablon',
    titulo: 'tablón de misiones: banner rotativo y la historia (completada, disponible, bloqueada)',
    ruta: 'contenido/misiones/misiones.html',
    sesion: () => sesionDe('qa_misiones', 'JUGADOR'),
    rutas: [MISIONES_DE_LABORATORIO, ...rutasDeEstrategia()],
    exige: [
      '.banner-misiones__diapositiva',
      '.mision-card[data-estado="disponible"]',
      '.mision-card[data-estado="bloqueada"]',
      '.mision-card[data-estado="completada"]',
      '[data-laboratorio="misiones"]',
    ],
  },
  {
    id: 'misiones-desafio',
    titulo: 'tablón de misiones: desafíos en progreso y fallido',
    ruta: 'contenido/misiones/misiones.html',
    sesion: () => sesionDe('qa_misiones', 'JUGADOR'),
    rutas: [MISIONES_DE_LABORATORIO, ...rutasDeEstrategia()],
    interaccion: async (pagina) => {
      await pagina.locator('[data-pestana="categoria-desafio"]').click();
    },
    exige: [
      '.mision-card[data-estado="en_progreso"] [role="progressbar"]',
      '.mision-card[data-estado="fallida"]',
    ],
  },
  {
    id: 'misiones-exploracion',
    titulo: 'tablón de misiones: exploraciones completada y abandonada',
    ruta: 'contenido/misiones/misiones.html',
    sesion: () => sesionDe('qa_misiones', 'JUGADOR'),
    rutas: [MISIONES_DE_LABORATORIO, ...rutasDeEstrategia()],
    interaccion: async (pagina) => {
      await pagina.locator('[data-pestana="categoria-exploracion"]').click();
    },
    exige: [
      '.mision-card[data-estado="completada"]',
      '.mision-card[data-estado="abandonada"]',
    ],
  },
  {
    id: 'misiones-detalle',
    titulo: 'detalle de «El Templo Olvidado» (§7.8.14) con su configurador',
    ruta: 'contenido/misiones/misiones.html?mision=templo-olvidado',
    sesion: () => sesionDe('qa_misiones', 'JUGADOR'),
    rutas: [MISIONES_DE_LABORATORIO, ...rutasDeEstrategia()],
    exige: [
      '.mision-detalle__cabecera',
      '.mision-enemigo',
      '.mision-jefe',
      '.mision-master',
      '.mision-recompensas',
      '#configurar .estrategia__heroe',
      '[data-accion="iniciar-mision"][aria-disabled="true"]',
    ],
  },
  {
    id: 'misiones-matricula',
    titulo: 'iniciar misión: estrategia comprobada y confirmación con lo que queda bloqueado',
    ruta: 'contenido/misiones/misiones.html?mision=templo-olvidado#configurar',
    sesion: () => sesionDe('qa_misiones', 'JUGADOR'),
    rutas: [MISIONES_DE_LABORATORIO, ...rutasDeEstrategia()],
    interaccion: async (pagina) => {
      await prepararEstrategia(pagina);
      await pagina.locator('[data-accion="iniciar-mision"][aria-disabled="false"]').click();
    },
    exige: ['[role="dialog"] .misiones-confirmacion', '.misiones-confirmacion__advertencia'],
  },
  {
    id: 'misiones-en-curso',
    titulo: 'misiones en curso: tiempo restante, avance, héroe y cancelar',
    ruta: 'contenido/misiones/misiones.html#en-curso',
    sesion: () => sesionDe('qa_misiones', 'JUGADOR'),
    rutas: [MISIONES_DE_LABORATORIO, ...rutasDeEstrategia()],
    exige: [
      '.mision-activa time.cuenta-atras',
      '.mision-activa [role="progressbar"]',
      '[data-accion="cancelar-mision"]',
    ],
  },
  {
    id: 'misiones-historial',
    titulo: 'historial de misiones: por categoría, terminadas, tiempos y épicas',
    ruta: 'contenido/misiones/misiones.html#historial',
    sesion: () => sesionDe('qa_misiones', 'JUGADOR'),
    rutas: [MISIONES_DE_LABORATORIO, ...rutasDeEstrategia()],
    exige: ['.mision-historial__tabla', '.mision-historial .mision-master', '.metrica'],
  },
  {
    id: 'misiones-reporte',
    titulo: 'reporte de una misión completada (§7.8.8)',
    ruta: 'contenido/misiones/misiones.html?reporte=ejecucion-bosque',
    sesion: () => sesionDe('qa_misiones', 'JUGADOR'),
    rutas: [MISIONES_DE_LABORATORIO, ...rutasDeEstrategia()],
    exige: [
      '.mision-reporte__resumen',
      '[data-bloque="combate"] .metrica',
      '[data-bloque="enemigos"]',
      '[data-bloque="recompensas"]',
      '[data-bloque="objetivos"] li[data-cumplido="false"]',
    ],
  },
  {
    id: 'inventario-banner-misiones',
    titulo: 'mi inventario con el banner de misiones disponibles (RF-INV-003)',
    ruta: 'contenido/inventario/inventario.html#heroes',
    sesion: () => sesionDe('qa_banner', 'JUGADOR'),
    rutas: [MISIONES_DE_LABORATORIO, ...rutasDeOchoHeroes()],
    exige: ['.inventario__banner-misiones .banner-misiones__diapositiva', '.hero-card'],
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
