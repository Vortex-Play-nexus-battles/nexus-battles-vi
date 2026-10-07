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
export function sesionDe(apodo, rol = 'JUGADOR', uid = undefined) {
  return { ...sesionSintetica({ apodo, rol, ...(uid ? { uid } : {}) }), apodo, rol };
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
    // UXC-3 — la ficha trae las opiniones del producto: un hilo vacío, que es
    // el estado normal de un producto que nadie ha comentado.
    ...rutasDeOpiniones(),
  ];
}

/** Un producto sin opiniones (comentarios.yaml 1.5.0: 200 con la lista vacía). */
const HILO_VACIO = {
  productoId: 'sin-opiniones',
  comentarios: [],
  pagina: 0,
  tamano: 5,
  total: 0,
  totalPaginas: 0,
  totalCalificaciones: 0,
  calificacionPromedio: null,
};

/** B3 — nadie lo ha calificado: promedio nulo, nunca 0 (`GET /products/{id}/rating`). */
const RESUMEN_VACIO = {
  productoId: 'sin-opiniones',
  promedio: null,
  total: 0,
  distribucion: { 1: 0, 2: 0, 3: 0, 4: 0, 5: 0 },
};

/**
 * B3 — «todavía no calificaste»: el 404 de `GET /products/{id}/rating/mia` es
 * el estado normal de quien aún no opinó, y la ficha le ofrece las estrellas.
 */
const SIN_CALIFICACION_PROPIA = {
  status: 404,
  contentType: 'application/problem+json',
  body: JSON.stringify({
    type: 'https://nexusbattles.local/errores/calificacion-no-encontrada',
    title: 'Todavía no calificaste este producto',
    status: 404,
  }),
};

/**
 * Las opiniones de un producto como las sirve comentarios.yaml 1.5.0: el hilo
 * paginado, el resumen de la calificación, la propia (sin calificar) y las
 * imágenes de las opiniones. La imagen es un avatar que ya está en el
 * repositorio: el laboratorio no sale a internet.
 */
function rutasDeOpiniones({ hilo = HILO_VACIO, resumen = RESUMEN_VACIO } = {}) {
  return [
    ['**/api/v1/products/*/comments*', json(hilo)],
    ['**/api/v1/products/*/rating', json(resumen)],
    ['**/api/v1/products/*/rating/mia', SIN_CALIFICACION_PROPIA],
    [
      '**/api/v1/comentarios/imagenes/*',
      () => ({
        status: 200,
        contentType: 'image/jpeg',
        path: join(
          AQUI_LABORATORIO,
          '..',
          '..',
          'frontend',
          'app-web',
          'src',
          'cuentas',
          'avatares',
          'arquero-cazador.jpg',
        ),
      }),
    ],
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
  base === null
    ? null
    : {
        base,
        cantidadDados,
        caras,
        formula: `${base ? `${base} + ` : ''}${cantidadDados}d${caras}`,
      };

const OCHO_HEROES = [
  [
    'Aquiles de la Ceniza',
    'Guerrero Tanque',
    { poder: 10, vida: 44, defensa: 11, ataque: f(10, 1, 6), dano: f(0, 1, 4), sanar: null },
    4,
  ],
  [
    'Vorn el Filo',
    'Guerrero Armas',
    { poder: 8, vida: 44, defensa: 11, ataque: f(10, 1, 6), dano: f(0, 1, 6), sanar: null },
    2,
  ],
  [
    'Ignis',
    'Mago Fuego',
    { poder: 8, vida: 40, defensa: 10, ataque: f(10, 1, 8), dano: f(0, 1, 8), sanar: null },
    0,
  ],
  [
    'Nieve de Arel',
    'Mago Hielo',
    { poder: 10, vida: 40, defensa: 10, ataque: f(10, 1, 8), dano: f(0, 1, 6), sanar: null },
    1,
  ],
  [
    'Sombra Verde',
    'Pícaro Veneno',
    { poder: 8, vida: 36, defensa: 8, ataque: f(10, 1, 10), dano: f(0, 1, 6), sanar: null },
    2,
  ],
  [
    'Kael',
    'Pícaro Machete',
    { poder: 8, vida: 36, defensa: 8, ataque: f(10, 1, 10), dano: f(0, 1, 8), sanar: null },
    3,
  ],
  [
    'Oyá',
    'Chamán',
    { poder: 10, vida: 28, defensa: 4, ataque: null, dano: null, sanar: f(6, 1, 6) },
    2,
  ],
  [
    'Doctora Lumen',
    'Médico',
    { poder: 10, vida: 28, defensa: 4, ataque: null, dano: null, sanar: f(4, 1, 8) },
    1,
  ],
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
    // UXC-3 — la ficha trae las opiniones del producto (aquí, ninguna).
    ...rutasDeOpiniones(),
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

function escenarioDeCombate(
  id,
  titulo,
  { partida, mensajes = [], exige, canal = {}, interaccion, sala = null, otrosDestinos = {} },
) {
  // Con `sala`, se llega como desde la sala de espera (`?sala=…&partida=…`):
  // es lo que monta el chat grupal de esa sala (revisión del 6-oct, punto 15).
  const consulta = sala ? `sala=${sala}&partida=${ID_PARTIDA}` : `partida=${ID_PARTIDA}`;
  return {
    id,
    titulo,
    ruta: `plataforma/salas-partidas/sala-batalla.html?${consulta}`,
    sesion: () => SESION_COMBATE,
    rutas: [
      [`**/api/v1/partidas/${partida.id ?? ID_PARTIDA}`, json(partida)],
      ...RUTAS_DEL_HEROE_EN_COMBATE,
    ],
    canal: {
      mensajes: { [`/tema/partidas/${ID_PARTIDA}`]: mensajes, ...otrosDestinos },
      ...canal,
    },
    ...(interaccion ? { interaccion } : {}),
    exige,
  };
}

/** La sala del combate del chat grupal (punto 15). DATOS DE LABORATORIO. */
const ID_SALA_COMBATE = 'bbbbbbb9-9999-4999-8999-999999999999';

/** Un mensaje del chat de sala con la forma de `chat.mensaje`. */
function mensajeDeSala(id, autor, texto, minutosAtras) {
  return {
    id,
    tipo: 'chat.mensaje',
    idSala: ID_SALA_COMBATE,
    autor,
    texto,
    logro: null,
    enviadoEn: new Date(Date.now() - minutosAtras * 60_000).toISOString(),
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
    heroe: {
      id: `h-${jugador}`,
      nombre,
      retratoUrl: null,
      nivel: null,
      vidaActual,
      vidaMaxima,
      efectosActivos: [],
    },
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
  // torneos.yaml 1.2.0: quién pagó y en qué va el cobro (la máquina no paga).
  pagadoPor: i >= 6 ? null : `aaaaaaa${i + 1}-1111-4111-8111-111111111111`,
  reservaId: null,
  estadoPago: i >= 6 ? null : 'COBRADO',
  posicion: i + 1,
  derrotas: [1, 3, 5, 7].includes(i + 1) ? 0 : 1,
  eliminado: false,
}));

/** El premio anunciado del torneo de laboratorio (1.2.0; monto provisional, D-24). */
const PREMIO_DEL_TORNEO = {
  creditosPorIntegrante: 500,
  epicaProductoId: 'aaaaaaa1-0000-4000-8000-0000000000e1',
  estado: 'SIN_CAMPEON',
  entregas: [],
};
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
    premio: PREMIO_DEL_TORNEO,
  };
}

/**
 * El mismo torneo, terminado: «Lobos del Alba» (el equipo del capitán de
 * laboratorio) ganó la gran final y el premio ya se entregó.
 */
export function torneoTerminadoConCampeon() {
  const base = torneoEnCurso();
  const jugados = base.encuentros.map((e) => {
    const lados = {
      5: [E(1), E(3), E(1)],
      6: [E(5), E(7), E(5)],
      7: [E(2), E(4), E(2)],
      8: [E(6), E(8), E(6)],
      9: [E(3), E(2), E(3)],
      10: [E(7), E(6), E(6)],
      11: [E(1), E(5), E(1)],
      12: [E(3), E(6), E(3)],
      13: [E(5), E(3), E(5)],
      14: [E(1), E(5), E(1)],
    }[e.numero];
    return lados ? encuentro(e.numero, e.llave, e.ronda, ...lados, 'JUGADO') : e;
  });
  return {
    ...base,
    estado: 'FINALIZADO',
    campeonEquipoId: E(1),
    finalizadoEn: new Date(Date.now() - 600_000).toISOString(),
    encuentros: jugados,
    // Doble eliminación: el campeón acaba invicto y los demás, con dos derrotas.
    equipos: EQUIPOS.map((e, i) => ({ ...e, eliminado: i !== 0, derrotas: i === 0 ? 0 : 2 })),
    premio: {
      ...PREMIO_DEL_TORNEO,
      estado: 'ENTREGADO',
      entregas: [
        {
          uid: 'aaaaaaa1-1111-4111-8111-111111111111',
          estado: 'ENTREGADO',
          creditosEntregados: true,
          epicaEntregada: true,
        },
      ],
    },
  };
}

/* Punto 24 (revisión del 6-oct) — el tablón con más de un torneo y la ruta
   de uno con las inscripciones abiertas: tres equipos registrados, ninguno
   del jugador de laboratorio, y el árbol todavía sin generar. */
const ID_TORNEO_ABIERTO = 'fffffff2-2222-4222-8222-222222222222';
const ID_TORNEO_TERMINADO = 'fffffff3-3333-4333-8333-333333333333';

export function torneoAbierto() {
  return {
    ...torneoEnCurso(),
    id: ID_TORNEO_ABIERTO,
    nombre: 'Copa de la Bruma',
    estado: 'INSCRIPCIONES_ABIERTAS',
    creadoEn: new Date(Date.now() - 2 * 86_400_000).toISOString(),
    inscripcionesCierranEn: new Date(Date.now() + 3 * 86_400_000).toISOString(),
    iniciadoEn: null,
    equiposInscritos: 3,
    equipos: EQUIPOS.slice(1, 4).map((e, i) => ({
      ...e,
      torneoId: ID_TORNEO_ABIERTO,
      posicion: i + 1,
      derrotas: 0,
    })),
    encuentros: [],
  };
}

/** Los tres torneos del tablón: abierto, en curso y terminado. */
function torneosDelTablon() {
  return [
    torneoAbierto(),
    torneoEnCurso(),
    { ...torneoTerminadoConCampeon(), id: ID_TORNEO_TERMINADO, nombre: 'Copa del Ocaso' },
  ];
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
   lista negra (`PaginaDeTerminos` de moderacion-lista-negra.yaml 2.0.x: desde
   B2 ya no es una lista de cadenas) y cola de moderación
   (`ColaDeModeracionResponse` de comentarios.yaml).
   ------------------------------------------------------------------------- */
function terminoVetado(i, termino, categoria, modo, cambios = {}) {
  return {
    id: i,
    termino,
    normalizado: termino.toLowerCase().replace(/[^a-z0-9]/g, ''),
    categoria,
    modo,
    activo: true,
    creadoPor: i < 4 ? 'semilla' : 'qa_moderador',
    creadoEn: new Date(Date.now() - i * 86_400_000).toISOString(),
    actualizadoEn: null,
    ...cambios,
  };
}

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
      // comentarios.yaml 1.5.0: en moderación las estrellas van nulas (la
      // calificación no se modera) y cada comentario trae su marca.
      estrellas: null,
      fechaPublicacion: new Date(Date.now() - i * 7_200_000).toISOString(),
      estado: 'EN_REVISION',
      calificacionDescartada: false,
      editado: i % 3 === 0,
      marcado: i % 3 === 2,
    },
    reportes: [3, 7, 1][i % 3],
    porCategoria: { OFENSIVO: [1, 5, 0][i % 3], SPAM: [2, 2, 1][i % 3] },
    primerReporte: new Date(Date.now() - i * 7_000_000).toISOString(),
    ...cambios,
  };
}

/* ---------------------------------------------------------------------------
   UXC-3/UXC-4 — la tienda, su detalle con opiniones y la portada. DATOS DE
   LABORATORIO: nombres, textos, precios y opiniones inventados para la
   captura, con la forma exacta de ecommerce-carrito.yaml 1.2.0 y
   comentarios.yaml 1.3.0. En produccion todo sale de la vitrina, del
   catalogo y del servicio de comentarios.
   ------------------------------------------------------------------------- */
const SESION_TIENDA = sesionDe('qa_tienda', 'JUGADOR');

const PRODUCTOS_DE_TIENDA = [
  producto({ id: 'aaaaaaa1-0000-4000-8000-000000000001', nombre: 'Yelmo del Alba' }),
  producto({
    id: 'aaaaaaa1-0000-4000-8000-000000000002',
    nombre: 'Amuleto de Brasa',
    tipo: 'ITEM',
    habilidades: 'Fuego +2 durante tres turnos',
    precioOriginal: 20000,
    precioFinal: 16000,
    enPromocion: true,
    porcentajeDescuento: 20,
  }),
  producto({
    id: 'aaaaaaa1-0000-4000-8000-000000000003',
    nombre: 'Pocion sin precio',
    tipo: 'ITEM',
    precioFinal: null,
  }),
  producto({
    id: 'aaaaaaa1-0000-4000-8000-000000000004',
    nombre: 'Guerrero de Obsidiana',
    tipo: 'HEROE',
    imagenUrl: '/frontend/app-web/src/cuentas/avatares/guerrero-tanque.jpg',
    descripcion: 'Un tanque que aguanta la primera oleada.',
    habilidades: null,
    precioFinal: 45000,
    precioOriginal: 45000,
  }),
  producto({
    id: 'aaaaaaa1-0000-4000-8000-000000000005',
    nombre: 'Espada de Vorn',
    tipo: 'ARMA',
    descripcion: 'Filo largo, templado en la niebla.',
    habilidades: 'Ataque +6',
    precioFinal: 32000,
    precioOriginal: 32000,
  }),
];

/**
 * El hilo de un producto: tres opiniones, una con imagen, una editada por
 * moderación y una propia. B3 (comentarios.yaml 1.5.0): del más reciente al
 * más antiguo, paginado, y la imagen es un `id` que se sirve aparte.
 */
function hiloDeLaboratorio({ propio = null } = {}) {
  const comentarios = [
    {
      id: 'c0000000-0000-4000-8000-000000000003',
      productoId: 'aaaaaaa1-0000-4000-8000-000000000001',
      autorId: propio ?? 'c1111111-0000-4000-8000-000000000003',
      apodoAutor: propio ? 'qa_tienda' : 'lumen_9',
      texto: 'Segunda opinión sin estrellas: después de diez partidas sigo contento.',
      imagenes: [],
      estrellas: null,
      fechaPublicacion: '2026-09-22T08:05:00Z',
      estado: 'PUBLICADO',
      editado: false,
    },
    {
      id: 'c0000000-0000-4000-8000-000000000002',
      productoId: 'aaaaaaa1-0000-4000-8000-000000000001',
      autorId: 'c1111111-0000-4000-8000-000000000002',
      apodoAutor: 'kira_del_sur',
      texto: 'Buen objeto, aunque la descripción exagera un poco.\nA mí me sirvió en duelos.',
      imagenes: [],
      estrellas: 3,
      fechaPublicacion: '2026-09-20T11:40:00Z',
      estado: 'PUBLICADO',
      editado: true,
    },
    {
      id: 'c0000000-0000-4000-8000-000000000001',
      productoId: 'aaaaaaa1-0000-4000-8000-000000000001',
      autorId: 'c1111111-0000-4000-8000-000000000001',
      apodoAutor: 'thar_vex',
      texto: 'Llegó con la defensa que promete. Para un tanque, de lo mejor que hay.',
      imagenes: ['f0000000-0000-4000-8000-000000000001'],
      estrellas: 5,
      fechaPublicacion: '2026-09-18T20:15:00Z',
      estado: 'PUBLICADO',
      editado: false,
    },
  ];
  return {
    productoId: 'aaaaaaa1-0000-4000-8000-000000000001',
    comentarios,
    pagina: 0,
    tamano: 5,
    total: comentarios.length,
    totalPaginas: 1,
    totalCalificaciones: 2,
    calificacionPromedio: 4,
  };
}

/** B3 — el resumen que acompaña a ese hilo: las dos calificaciones, 5 y 3. */
const RESUMEN_DE_LABORATORIO = {
  productoId: 'aaaaaaa1-0000-4000-8000-000000000001',
  promedio: 4,
  total: 2,
  distribucion: { 1: 0, 2: 0, 3: 1, 4: 0, 5: 1 },
};

/** El detalle del catalogo de cualquier producto de la tienda de laboratorio. */
function detalleDelCatalogo(peticion) {
  const id = new URL(peticion.request().url()).pathname.split('/').pop();
  const deVitrina = PRODUCTOS_DE_TIENDA.find((p) => p.id === id) ?? PRODUCTOS_DE_TIENDA[0];
  return json({
    id,
    nombre: deVitrina.nombre,
    tipo: deVitrina.tipo,
    descripcion: deVitrina.descripcion,
    imagen: deVitrina.imagenUrl,
    prototipo: deVitrina.tipo === 'HEROE' ? 'Guerrero Tanque' : null,
    defensa: deVitrina.tipo === 'ARMADURA' ? 4 : null,
    parte: deVitrina.tipo === 'ARMADURA' ? 'CASCO' : null,
    tasaDeCaida: deVitrina.tipo === 'HEROE' ? null : 12,
    estado: 'ACTIVO',
    tiraje: -1,
  });
}

const CARRITO_DE_LABORATORIO = {
  id: 9,
  usuarioId: 'qa',
  total: 52000,
  moneda: 'COP',
  items: [
    {
      id: 1,
      cantidad: 2,
      precioUnitario: 18000,
      subtotal: 36000,
      producto: {
        id: 'aaaaaaa1-0000-4000-8000-000000000001',
        nombre: 'Yelmo del Alba',
        moneda: 'COP',
      },
    },
    {
      id: 2,
      cantidad: 1,
      precioUnitario: 16000,
      subtotal: 16000,
      producto: {
        id: 'aaaaaaa1-0000-4000-8000-000000000002',
        nombre: 'Amuleto de Brasa',
        moneda: 'COP',
      },
    },
  ],
};

/**
 * B5 — dos compras del jugador (`Orden` de ecommerce-carrito.yaml 1.4.0): una
 * completada y una rechazada por la pasarela, con su motivo.
 */
const ORDENES_DE_LABORATORIO = [
  {
    id: 'b5000000-0000-4000-8000-000000000001',
    estado: 'COMPLETA',
    moneda: 'COP',
    total: 52000,
    tasaDeCambio: null,
    lineas: [
      {
        productoId: 'aaaaaaa1-0000-4000-8000-000000000001',
        nombre: 'Yelmo del Alba',
        cantidad: 2,
        precioUnitario: 18000,
        subtotal: 36000,
      },
      {
        productoId: 'aaaaaaa1-0000-4000-8000-000000000002',
        nombre: 'Amuleto de Brasa',
        cantidad: 1,
        precioUnitario: 16000,
        subtotal: 16000,
      },
    ],
    medioDePago: { marca: 'VISA', ultimos4: '4242' },
    motivo: null,
    correoConfirmacion: 'ENVIADO',
    creadaEn: '2026-09-24T19:05:00Z',
  },
  {
    id: 'b5000000-0000-4000-8000-000000000002',
    estado: 'RECHAZADA',
    moneda: 'COP',
    total: 32000,
    tasaDeCambio: null,
    lineas: [
      {
        productoId: 'aaaaaaa1-0000-4000-8000-000000000005',
        nombre: 'Espada de Vorn',
        cantidad: 1,
        precioUnitario: 32000,
        subtotal: 32000,
      },
    ],
    medioDePago: { marca: 'VISA', ultimos4: '0002' },
    motivo: 'La pasarela rechazó el pago: fondos insuficientes.',
    correoConfirmacion: 'OMITIDO',
    creadaEn: '2026-09-23T10:40:00Z',
  },
];

/**
 * @param {{hilo?: object, deseados?: string[], extra?: Array<[string, object|Function]>}} [opciones]
 *   `deseados`: ids que la vitrina con sesión marca `enListaDeseos` (B5);
 *   `extra`: más rutas (las órdenes, por ejemplo).
 */
function rutasDeTienda({ hilo = hiloDeLaboratorio(), deseados = [], extra = [] } = {}) {
  const productos = PRODUCTOS_DE_TIENDA.map((p) =>
    deseados.includes(p.id) ? { ...p, enListaDeseos: true } : p,
  );
  return [
    // R16 — la vitrina se mudó a /api/v1/vitrina y sus ids son UUID del
    // catálogo maestro. UXC-4 — se pide entera (`?size=50`). B5 (1.4.0) — dice
    // en qué moneda viene y cuáles ofrece: sin tasas del PO (D-32), solo COP.
    [
      '**/api/v1/vitrina*',
      json({
        content: productos,
        last: true,
        totalPages: 1,
        moneda: 'COP',
        monedasDisponibles: ['COP'],
      }),
    ],
    ['**/api/v1/carrito', json(CARRITO_DE_LABORATORIO)],
    // Lo que ya tiene el jugador: un yelmo (la tarjeta dice «Ya lo tienes»).
    [
      '**/api/v1/inventario/elementos?*',
      json({
        elementos: [
          {
            id: 'e0000000-0000-4000-8000-000000000001',
            productoId: 'aaaaaaa1-0000-4000-8000-000000000001',
            tipo: 'ARMADURA',
            nombrePropio: 'Yelmo del Alba',
            disponible: true,
          },
        ],
        numero: 0,
        totalPaginas: 1,
        ultima: true,
      }),
    ],
    ['**/api/v1/productos/*', detalleDelCatalogo],
    ...rutasDeOpiniones({ hilo, resumen: RESUMEN_DE_LABORATORIO }),
    ...extra,
  ];
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
    [
      'Pare de fuego',
      4,
      '+1 al ataque y retorna el (0dx) daño causado por el oponente en el turno anterior',
    ],
  ],
  'Mago Hielo': [
    ['Lluvia de hielo', 2, '+2 al ataque, +2 de daño'],
    [
      'Cono de hielo',
      6,
      '+2 al daño y afecta el ataque del enemigo en un (1d3) durante los dos turnos siguientes',
    ],
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
  const base = {
    heroe,
    nivel,
    habilidadesValidas: validas,
    comportamientoPorDefecto: 'Ataque básico',
  };
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
    rotaciones: rotaciones.map((r, i) => ({
      prioridad: ['Alta', 'Media', 'Baja'][i],
      pasos: r.pasos,
    })),
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
  const formula = (x) =>
    x ? `${x.base ? `${x.base} + ` : ''}${x.cantidadDados}d${x.caras}` : null;
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

/* ---------------------------------------------------------------------------
   UXC-6 — chat y mensajes privados. DATOS DE LABORATORIO: apodos y textos
   inventados para la captura, con la forma exacta de `MensajeDeChat`
   (contracts/websocket/salas-partidas.yaml 1.4.0). El chat general y el de la
   sala van por el canal simulado; los mensajes privados, por la fuente del
   laboratorio (`laboratorio/fuente-mensajes.js`), porque el banco visual no
   tiene su servicio (B6: el de salas-partidas).
   ------------------------------------------------------------------------- */

/** El `uid` de quien mira en las capturas del chat: firma «sus» mensajes. */
const UID_CHAT = 'cccccccc-6666-4666-8666-000000000001';

/**
 * Sesión sintética con un `uid` fijo. `sesionSintetica` inventa uno cada vez,
 * y el canal simulado necesita saberlo de antemano para que «Tú» salga en la
 * captura.
 */
function sesionDelChat() {
  const b64 = (objeto) => Buffer.from(JSON.stringify(objeto)).toString('base64url');
  const cuerpo = {
    sub: 'qa_chat',
    preferred_username: 'qa_chat',
    uid: UID_CHAT,
    rol: 'JUGADOR',
    exp: Math.floor(Date.now() / 1000) + 8 * 3600,
  };
  return {
    token: `${b64({ alg: 'none', typ: 'JWT' })}.${b64(cuerpo)}.sin-firma`,
    uid: UID_CHAT,
    apodo: 'qa_chat',
    rol: 'JUGADOR',
  };
}

function mensajeDeChat(id, autor, texto, minutosAtras, cambios = {}) {
  return {
    id,
    tipo: 'chat.mensaje',
    idSala: null,
    autor,
    texto,
    logro: null,
    enviadoEn: new Date(Date.now() - minutosAtras * 60_000).toISOString(),
    ...cambios,
  };
}

const EN_EL_CHAT = {
  yo: { id: UID_CHAT, apodo: 'qa_chat' },
  bruma: { id: 'cccccccc-6666-4666-8666-000000000002', apodo: 'Bruma' },
  kael: { id: 'cccccccc-6666-4666-8666-000000000003', apodo: 'Kael_77' },
  nyra: { id: 'cccccccc-6666-4666-8666-000000000004', apodo: 'Nyra' },
};

function historialDelChat(idSala = null) {
  const { yo, bruma, kael, nyra } = EN_EL_CHAT;
  return [
    mensajeDeChat('ch-1', bruma, '¿Alguien para un 1v1 con apuesta pequeña?', 60 * 26, { idSala }),
    mensajeDeChat('ch-2', yo, 'Yo voy. Abro sala en cinco minutos.', 60 * 25 + 50, { idSala }),
    mensajeDeChat('ch-3', kael, 'Por fin cayó el guardián.', 14, {
      idSala,
      tipo: 'chat.logro',
      logro: { mision: 'El Templo Olvidado', titulo: 'Guardián derrotado' },
    }),
    mensajeDeChat('ch-4', nyra, 'Buscamos cuarto para el torneo del sábado. ¿Quién se apunta?', 9, {
      idSala,
    }),
    mensajeDeChat('ch-5', yo, 'Me apunto si es por la tarde.', 4, { idSala }),
  ];
}

/** El canal simulado del chat general: el historial y un mensaje en vivo. */
function canalDelChatGeneral(extra = {}) {
  return {
    mensajes: {
      '/app/chat/general/historial': [historialDelChat()],
      '/tema/chat/general': [
        mensajeDeChat('ch-6', EN_EL_CHAT.bruma, 'Sala creada: «Duelo al atardecer». Os espero.', 0),
      ],
      ...extra,
    },
  };
}

const ID_SALA_DEL_CHAT = 'bbbbbbb1-6666-4666-8666-111111111111';

/** La ruta que cambia la fuente de mensajes privados por la del laboratorio. */
const MENSAJES_DE_LABORATORIO = [
  '**/plataforma/salas-partidas/fuente-mensajes.js',
  {
    status: 200,
    contentType: 'text/javascript; charset=utf-8',
    path: join(AQUI_LABORATORIO, 'laboratorio', 'fuente-mensajes.js'),
  },
];

/** Abre la conversación con Bruma (o la que se pida) en la pestaña de privados. */
function abrirConversacion(apodo = 'Bruma') {
  return async (pagina) => {
    await pagina
      .locator('.conversaciones__item', { hasText: apodo })
      .first()
      .click({ timeout: 10_000 });
    await pagina.locator('.mensajes-privados__con').waitFor({ timeout: 10_000 });
    await pagina.waitForTimeout(400);
  };
}

/*
 * B6 — la fuente REAL de mensajes privados (`fuente-mensajes.js`, el
 * adaptador del servicio), sin sustituir: el banco le contesta con cuerpos
 * con la forma de `salas-partidas.yaml` 1.6.1 (`ResumenDeConversacion`,
 * `MensajeDirecto`) y el canal simulado le entrega por la cola de usuario un
 * `MensajeEntregado` de `mensajes-directos.yaml` 1.0.1. DATOS DE LABORATORIO.
 */
const TALA_EN_EL_CHAT = { id: 'cccccccc-6666-4666-8666-000000000005', apodo: 'Tala' };

function mensajeDirecto(id, de, a, texto, minutosAtras) {
  return {
    id,
    conversacion: `dm:${[de.id, a.id].sort().join(':')}`,
    remitente: de.id,
    apodoRemitente: de.apodo,
    destinatario: a.id,
    texto,
    fecha: new Date(Date.now() - minutosAtras * 60_000).toISOString(),
    leido: true,
    idCliente: null,
  };
}

function rutasDeMensajesDelServicio() {
  const { yo, bruma, kael } = EN_EL_CHAT;
  const conBruma = [
    mensajeDirecto('dm-1', yo, bruma, 'Claro. Llevo el Guerrero Tanque.', 60),
    mensajeDirecto('dm-2', bruma, yo, 'Si ganas, la revancha la elijo yo.', 3),
  ];
  const json = (cuerpo) => ({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(cuerpo),
  });
  return [
    [
      '**/api/v1/mensajes-directos/conversaciones',
      json([
        { uidOtro: bruma.id, apodoOtro: bruma.apodo, ultimoMensaje: conBruma[1], noLeidos: 1 },
        {
          uidOtro: kael.id,
          apodoOtro: kael.apodo,
          ultimoMensaje: mensajeDirecto('dm-3', yo, kael, 'Te paso el código de la sala.', 300),
          noLeidos: 0,
        },
      ]),
    ],
    [
      '**/api/v1/mensajes-directos/conversaciones/*/mensajes*',
      (ruta) => json(ruta.request().url().includes(bruma.id) ? conBruma : []),
    ],
    ['**/api/v1/mensajes-directos/conversaciones/*/leido', { status: 204 }],
  ];
}


/* ---------------------------------------------------------------------------
   UXC-7 — consola y cuenta. DATOS DE LABORATORIO con la forma exacta de
   productos.yaml (ProductoCreado, PaginaDeProductos, ResumenCatalogo) y de
   moderacion-sanciones-admin.yaml (Sancion).
   ------------------------------------------------------------------------- */

const SESION_ADMIN_CATALOGO = () => sesionDe('qa_admin_catalogo', 'ADMINISTRADOR');

function productoDelCatalogo(id, cambios = {}) {
  return {
    id: `cccccc${id}-7777-4777-8777-000000000001`,
    nombre: 'Yelmo del Alba',
    imagen: 'productos/yelmo-del-alba.png',
    descripcion: 'Acero claro, forjado al amanecer.',
    tipo: 'ARMADURA',
    tiraje: 40,
    premium: false,
    precioCreditos: 1200,
    defensa: 4,
    parte: 'CASCO',
    tasaDeCaida: 12.5,
    estado: 'ACTIVO',
    version: 3,
    creadoEn: '2026-09-01T10:00:00Z',
    modificadoEn: '2026-09-20T10:00:00Z',
    ...cambios,
  };
}

const PRODUCTOS_DEL_CATALOGO = [
  productoDelCatalogo('01'),
  productoDelCatalogo('02', {
    nombre: 'Guerrero Tanque',
    tipo: 'HEROE',
    prototipo: 'Guerrero Tanque',
    tiraje: -1,
    precioCreditos: 3000,
    defensa: undefined,
    parte: undefined,
    tasaDeCaida: undefined,
    version: 1,
  }),
  productoDelCatalogo('03', {
    nombre: 'Hacha de Obsidiana',
    tipo: 'ARMA',
    poderDeAtaque: 7,
    tasaDeCaida: 4,
    defensa: undefined,
    parte: undefined,
    estado: 'UNICO',
    tiraje: 1,
    precioCreditos: 5400,
  }),
  productoDelCatalogo('04', {
    nombre: 'Pack del Guardián',
    tipo: 'ITEM',
    efecto: 'Recupera 20 de vida al inicio del combate.',
    defensa: undefined,
    parte: undefined,
    premium: true,
    precioCreditos: undefined,
    precioMonedaReal: 18500,
    tiraje: 200,
  }),
];

function rutasDelCatalogo() {
  return [
    [
      '**/api/v1/productos/estadisticas',
      json({
        total: 5,
        porTipo: { HEROE: 1, HABILIDAD: 0, ARMA: 1, ARMADURA: 1, ITEM: 1, EPICA: 1 },
        porEstado: { ACTIVO: 3, UNICO: 1, SUSPENDIDO: 1 },
      }),
    ],
    [
      /\/api\/v1\/productos\?/,
      json({
        content: PRODUCTOS_DEL_CATALOGO,
        page: 0,
        size: 16,
        totalElements: PRODUCTOS_DEL_CATALOGO.length,
        totalPages: 1,
      }),
    ],
  ];
}

/* ---------------------------------------------------------------------------
   HU-PRD-013 (#854) y RF-NOT-002 (#533) — banners. DATOS DE LABORATORIO con
   la forma del esquema `Banner` de productos.yaml 1.6.0.
   ------------------------------------------------------------------------- */

function bannerDeLaboratorio(id, contenido, cambios = {}) {
  return {
    id: `b0000000-0000-4000-8000-00000000000${id}`,
    contenido,
    publicarDesde: new Date(Date.now() - 3_600_000).toISOString(),
    vigenteHasta: new Date(Date.now() + 3 * 86_400_000).toISOString(),
    retirado: false,
    creadoEn: new Date(Date.now() - 2 * 3_600_000).toISOString(),
    modificadoEn: new Date(Date.now() - 2 * 3_600_000).toISOString(),
    ...cambios,
  };
}

/** Los vigentes que rota la home: dos, para que se pinte como carrusel. */
function bannersVigentes() {
  return [
    bannerDeLaboratorio(
      '1',
      'Temporada de otoño: nuevos prototipos en la tienda hasta el domingo.',
    ),
    bannerDeLaboratorio('2', 'Mantenimiento programado el martes de 2:00 a 3:00 a. m.'),
  ];
}

const UID_SANCIONADO = 'dddddddd-7777-4777-8777-000000000001';

function sancionDeLaboratorio(cambios = {}) {
  return {
    id: 'eeeeeee1-7777-4777-8777-000000000001',
    usuarioId: UID_SANCIONADO,
    tipo: 'SUSPENSION',
    motivo: 'Lenguaje ofensivo reiterado en el chat de sala.',
    politica: 'Normas de convivencia §3',
    comentarioId: null,
    emitidaPor: 'eeeeeee1-7777-4777-8777-0000000000aa',
    rolEmisor: 'MODERADOR',
    emitidaEn: new Date(Date.now() - 20 * 3_600_000).toISOString(),
    vigenteHasta: new Date(Date.now() + 2 * 86_400_000 + 5 * 3_600_000).toISOString(),
    revertidaEn: null,
    motivoReversion: null,
    vigente: true,
    ...cambios,
  };
}

function historialDeSanciones() {
  return [
    sancionDeLaboratorio(),
    sancionDeLaboratorio({
      id: 'eeeeeee1-7777-4777-8777-000000000002',
      tipo: 'ADVERTENCIA',
      motivo: 'Spam en el chat general.',
      politica: null,
      vigenteHasta: null,
      emitidaEn: new Date(Date.now() - 9 * 86_400_000).toISOString(),
      revertidaEn: new Date(Date.now() - 8 * 86_400_000).toISOString(),
      motivoReversion: 'Apelación aceptada: el mensaje era de otra cuenta.',
      vigente: false,
    }),
  ];
}

const LIMITES_DE_SANCION = {
  suspensionMinimaHoras: 1,
  suspensionMaximaHoras: 720,
  suspensionMaximaDias: 30,
  apelacionPlazoDias: 30,
};

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
        '**/api/v1/lista-negra/terminos*',
        json({
          contenido: [
            terminoVetado(1, 'spiderman', 'MARCA', 'SUBCADENA'),
            terminoVetado(2, 'hitler', 'DIRIGENTE', 'SUBCADENA'),
            terminoVetado(3, 'messi', 'CELEBRIDAD', 'PALABRA'),
            terminoVetado(4, 'mussolini', 'POLITICO', 'SUBCADENA'),
            terminoVetado(5, 'culo', 'OFENSIVO', 'PALABRA', { activo: false }),
            terminoVetado(6, 'nexus_oficial', 'OTRO', 'SUBCADENA'),
          ],
          pagina: 0,
          tamano: 16,
          total: 6,
        }),
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
          // salas-partidas 1.10.0 — los apodos de las plazas (revisión del 6-oct).
          apodoAnfitrion: 'Bruma',
          jugadores: [
            { id: SESION_COMBATE.uid, apodo: 'Bruma', anfitrion: true, heroe: 'Sombra de Vael' },
            {
              id: 'cccccc03-3333-4333-8333-333333333333',
              apodo: 'Kael_77',
              anfitrion: false,
              heroe: 'Arquero del Norte',
            },
          ],
        }),
      ],
    ],
    canal: { mensajes: {} },
    exige: [
      '[data-zona="espera"]:not([hidden])',
      '[data-zona="invitacion"]:not([hidden])',
      '[data-accion="iniciar-partida"]',
      // Revisión del 6-oct (puntos 13 y 14): la sala de espera en tres zonas.
      '[data-zona="sala-espera"]:not([hidden])',
      '[data-plaza="ia"]',
      '[data-plaza="libre"]',
      '[data-accion="abrir-invitar"]:not([hidden])',
    ],
  },
  {
    // Revisión del 6-oct (punto 13) — el panel de invitar abierto, con un
    // resultado de la búsqueda por apodo.
    id: 'sala-en-espera-invitar',
    titulo: 'sala de espera con el panel de invitar por apodo abierto',
    ruta: 'plataforma/salas-partidas/sala-batalla.html?sala=bbbbbbb1-1111-4111-8111-111111111111',
    sesion: () => SESION_COMBATE,
    rutas: [
      [
        '**/api/v1/salas/bbbbbbb1-1111-4111-8111-111111111111',
        json({
          id: 'bbbbbbb1-1111-4111-8111-111111111111',
          estado: 'ABIERTA',
          modalidad: 'UNO_CONTRA_UNO',
          maximoParticipantes: 2,
          ocupacion: 1,
          recompensaCreditos: 150,
          incluirHeroeIA: false,
          heroesIA: 0,
          privada: false,
          tamanoEquipo: null,
          idAnfitrion: SESION_COMBATE.uid,
          participantes: [SESION_COMBATE.uid],
          idPartida: null,
          creadaEn: new Date(Date.now() - 60_000).toISOString(),
          apodoAnfitrion: 'Bruma',
          jugadores: [
            { id: SESION_COMBATE.uid, apodo: 'Bruma', anfitrion: true, heroe: 'Sombra de Vael' },
          ],
        }),
      ],
      [
        '**/api/v1/perfiles/publicos?*',
        json([
          { uid: 'cccccc03-3333-4333-8333-333333333333', apodo: 'Kael_77', avatar: null },
          { uid: 'cccccc04-4444-4444-8444-444444444444', apodo: 'Kaelith', avatar: null },
        ]),
      ],
    ],
    canal: { mensajes: {} },
    interaccion: async (pagina) => {
      await pagina.locator('[data-accion="abrir-invitar"]').click();
      await pagina.locator('#buscar-invitado').fill('Kael');
    },
    exige: ['[data-zona="panel-invitar"]:not([hidden])', '[data-invitar="jugador"]'],
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
  // Revisión del modo jugador del 6-oct, punto 19 — el historial ya no ocupa
  // la barra de mando: «Historial» lo abre encima. DATOS DE LABORATORIO.
  escenarioDeCombate('combate-historial', 'combate con el historial abierto', {
    partida: partidaEnCurso({ vidaMia: 48, vidaRival: 51 }),
    mensajes: [
      accionResuelta(SESION_COMBATE.uid, 'CAUSAR_DANO_CRITICO', [
        { idJugador: RIVAL_IA, vidaActual: 51, vidaMaxima: 60, diferencia: -9 },
      ]),
      accionResuelta(RIVAL_IA, 'CAUSAR_DANO', [
        { idJugador: SESION_COMBATE.uid, vidaActual: 48, vidaMaxima: 52, diferencia: -4 },
      ]),
    ],
    interaccion: async (pagina) => {
      await pagina.locator('[data-accion="ver-historial"]').click();
    },
    exige: [
      '.combate__registro-cuerpo[data-abierto="si"] .registro-combate__linea',
      '[data-accion="ver-historial"][aria-expanded="true"]',
    ],
  }),
  // Punto 15 — el chat grupal abierto en pleno combate, en la misma página:
  // panel lateral en escritorio, hoja inferior en móvil. DATOS DE LABORATORIO.
  escenarioDeCombate('combate-chat-grupal', 'combate con el chat grupal abierto', {
    partida: partidaEnCurso(),
    sala: ID_SALA_COMBATE,
    otrosDestinos: {
      // El historial llega como UNA respuesta con la lista entera.
      [`/app/salas/${ID_SALA_COMBATE}/chat/historial`]: [
        [
          mensajeDeSala(
            'c-1',
            { id: 'cccccc07-7777-4777-8777-777777777777', apodo: 'Kael_77' },
            '¡Suerte en la arena!',
            3,
          ),
          mensajeDeSala(
            'c-2',
            { id: SESION_COMBATE.uid, apodo: 'qa_combate' },
            'Igualmente, vamos allá.',
            2,
          ),
        ],
      ],
    },
    interaccion: async (pagina) => {
      await pagina.locator('[data-accion="abrir-chat-grupal"]').click();
    },
    exige: [
      '[data-zona="chat-grupal"]:not([hidden])',
      '#formulario-chat-grupal',
      '[data-zona="chat-grupal"] [data-zona="mensajes"] li',
    ],
  }),
  // Punto 17 — el comienzo: la sala de espera recibe `sala.partida.iniciada`
  // y la presentación cuenta 5, 4, 3, 2, 1, ¡COMBATE! (la hora de inicio se
  // toma al pedir la partida, así la cuenta siempre está entera).
  {
    id: 'combate-cuenta-atras',
    titulo: 'comienzo del combate con la cuenta atrás',
    ruta: `plataforma/salas-partidas/sala-batalla.html?sala=${ID_SALA_COMBATE}`,
    sesion: () => SESION_COMBATE,
    rutas: [
      [
        `**/api/v1/salas/${ID_SALA_COMBATE}`,
        json({
          id: ID_SALA_COMBATE,
          estado: 'ABIERTA',
          modalidad: 'CONTRA_IA',
          maximoParticipantes: 2,
          ocupacion: 2,
          recompensaCreditos: 120,
          incluirHeroeIA: true,
          heroesIA: 1,
          privada: false,
          tamanoEquipo: null,
          idAnfitrion: SESION_COMBATE.uid,
          participantes: [SESION_COMBATE.uid],
          idPartida: null,
          creadaEn: new Date(Date.now() - 60_000).toISOString(),
          apodoAnfitrion: 'qa_combate',
          jugadores: [
            { id: SESION_COMBATE.uid, apodo: 'qa_combate', anfitrion: true, heroe: 'Aquiles' },
          ],
        }),
      ],
      [
        `**/api/v1/partidas/${ID_PARTIDA}`,
        () =>
          json({
            ...partidaEnCurso(),
            iniciadaEn: new Date().toISOString(),
            turnoActual: { idJugador: SESION_COMBATE.uid, numeroTurno: 1, segundosRestantes: null },
          }),
      ],
      ...RUTAS_DEL_HEROE_EN_COMBATE,
    ],
    canal: {
      mensajes: {
        [`/tema/salas/${ID_SALA_COMBATE}`]: [
          {
            tipo: 'sala.partida.iniciada',
            idSala: ID_SALA_COMBATE,
            idPartida: ID_PARTIDA,
            turnoActual: { idJugador: SESION_COMBATE.uid, numeroTurno: 1 },
          },
        ],
      },
    },
    exige: ['[data-zona="cuenta-atras"]', '[data-atacar]:disabled'],
  },
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
      // RF-NOT-002 — el banner rotativo, con dos vigentes para que se audite
      // como carrusel (controles, puntos y región con nombre). `Banner` de
      // productos.yaml 1.6.0.
      ['**/api/v1/banners/vigentes', json(bannersVigentes())],
    ],
    exige: [
      '[data-zona="saldo"]',
      '[data-zona="heroe"]',
      '[data-zona="torneo"]',
      '[data-zona="avisos"]',
      '[data-componente="banner-rotativo"] [data-banner]',
      '[data-accion="pausar-banner"]',
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
    // Revisión del 6-oct (puntos 11 y 12) — crear sala en «Hasta seis» con más
    // héroes de la IA de los que caben: se recorta y se dice por qué.
    id: 'crear-sala-maquinas-de-mas',
    titulo: 'crear sala hasta seis con 7 héroes de la IA escritos',
    ruta: 'plataforma/salas-partidas/crear-sala.html',
    sesion: () => sesionDe('qa_salas', 'JUGADOR'),
    rutas: [],
    interaccion: async (pagina) => {
      await pagina.locator('#modalidad-seis').check();
      await pagina.locator('#maximoParticipantes').fill('6');
      await pagina.locator('#maximoParticipantes').dispatchEvent('change');
      await pagina.locator('#heroesIA').fill('7');
    },
    exige: ['[data-zona="aviso-maquinas"]:not([hidden])', '#modalidad-ia ~ .modalidad__cara'],
  },
  {
    // Revisión del 6-oct (punto 10) — la sala privada pide su código: título,
    // quién la creó y, tras un código que no vale, el error de verdad.
    id: 'batallas-sala-privada',
    titulo: 'listado con el formulario de una sala privada y un código no válido',
    ruta: 'plataforma/salas-partidas/batallas.html',
    sesion: () => sesionDe('qa_salas', 'JUGADOR'),
    rutas: [
      [
        '**/api/v1/salas?*',
        json({
          contenido: [
            {
              ...sala({
                id: 'bbbbbbb3-3333-4333-8333-333333333333',
                estado: 'PRIVADA',
                privada: true,
                ocupacion: 1,
              }),
              apodoAnfitrion: 'Perez_Bro15',
            },
          ],
          pagina: 0,
          tamano: 16,
          totalElementos: 1,
          totalPaginas: 1,
        }),
      ],
      [
        '**/api/v1/salas/*/comprobacion-de-ingreso',
        {
          status: 403,
          contentType: 'application/problem+json',
          body: JSON.stringify({
            type: 'https://nexusbattles.local/errores/sala-privada',
            title: 'Esta sala es privada',
            status: 403,
            detail: 'A una sala privada se entra por invitación, no desde el listado.',
          }),
        },
      ],
    ],
    interaccion: async (pagina) => {
      await pagina.locator('[data-sala]').first().click();
      await pagina.locator('[name="codigoInvitacion"]').fill('ZZZZ-9999');
      await pagina.locator('[data-zona="pedir-codigo"] button[type="submit"]').click();
    },
    exige: ['[data-zona="pedir-codigo"]:not([hidden])', '[data-zona="aviso-codigo"]:not([hidden])'],
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
    titulo: 'tienda con precios, rebaja, lo que ya tienes, lo deseado y carrito con importes',
    ruta: 'cuentas/tienda.html',
    sesion: () => SESION_TIENDA,
    rutas: rutasDeTienda({ deseados: ['aaaaaaa1-0000-4000-8000-000000000005'] }),
    // El descuento y el precio ausente son los dos estados que FI-R2 anadio;
    // UXC-4 anade la marca de «propio», la insignia del carrito y los filtros.
    // B5 — lo deseado se distingue (corazón pulsado y distintivo), cada línea
    // cambia su cantidad, «Pagar» se enciende y la moneda tiene selector.
    exige: [
      '.product-card',
      '.badge-descuento',
      '.precio-ausente',
      '.cart-item',
      '.producto-propio',
      '.insignia-carrito__cuenta',
      '.filtros-tienda',
      '.product-card[data-deseado="si"] .producto-deseado',
      '.deseos--tarjeta[aria-pressed="true"]',
      '.cart-item [data-cantidad-item]',
      '#btn-pagar:not([disabled])',
      '#moneda-tienda option[value="USD"][disabled]',
    ],
  },
  {
    // UXC-3/UXC-4 — el detalle del producto desde la tienda: ficha del
    // catalogo, compra y opiniones (promedio, hilo con imagen y una propia).
    id: 'tienda-detalle-con-opiniones',
    titulo: 'detalle de producto con compra, calificación promedio e hilo de opiniones',
    ruta: 'cuentas/tienda.html',
    sesion: () => SESION_TIENDA,
    rutas: rutasDeTienda({ hilo: hiloDeLaboratorio({ propio: SESION_TIENDA.uid }) }),
    interaccion: async (pagina) => {
      await pagina.locator('[data-ver-producto]').first().click();
      await pagina.locator('.hilo-comentarios .comentario').first().waitFor();
    },
    exige: [
      '.ficha',
      '.ficha__valoracion',
      '.compra-producto',
      // B5 — la lista de deseos ya se guarda en la cuenta: en la tienda el
      // conmutador está activo (aria-pressed), no apagado.
      '.compra-producto .deseos[aria-pressed]',
      '.hilo-comentarios .comentario',
      '.comentario__adjunto',
      '.comentario--propio',
      '.redactor-comentario',
      '.selector-estrellas',
    ],
  },
  {
    // UXC-4 — buscar algo que no hay: se dice y se ofrece limpiar.
    id: 'tienda-sin-coincidencias',
    titulo: 'búsqueda sin resultados, con «Limpiar filtros»',
    ruta: 'cuentas/tienda.html',
    sesion: () => SESION_TIENDA,
    rutas: rutasDeTienda(),
    interaccion: async (pagina) => {
      await pagina.locator('.product-card').first().waitFor();
      await pagina.locator('#busqueda-tienda').fill('dragón de cristal');
      await pagina.locator('[data-accion="limpiar-filtros"]').waitFor();
    },
    exige: ['#productos-grid [data-estado="vacio"]', '[data-accion="limpiar-filtros"]'],
  },
  {
    // UXC-4 — el carrito minimizado: la vitrina a todo el ancho y la
    // insignia con las unidades.
    id: 'tienda-carrito-minimizado',
    titulo: 'carrito minimizado: vitrina a todo el ancho e insignia con unidades',
    ruta: 'cuentas/tienda.html',
    sesion: () => SESION_TIENDA,
    rutas: rutasDeTienda(),
    interaccion: async (pagina) => {
      await pagina.locator('.cart-item').first().waitFor();
      await pagina.locator('#minimizar-carrito').click();
    },
    exige: ['.main-container[data-carrito="minimizado"]', '.insignia-carrito__cuenta'],
  },
  {
    // B5 — «Pagar»: el resumen de la compra y el formulario de §7.5 (titular,
    // número, vencimiento y código), enviado vacío para auditar el estado con
    // errores: cada campo marcado, con su mensaje enlazado y el foco en el
    // primero. No se llega a pagar: sin datos válidos no sale ninguna petición.
    id: 'tienda-pago-con-errores',
    titulo: 'pagar: resumen de la compra y formulario de pago con los errores marcados',
    ruta: 'cuentas/tienda.html',
    sesion: () => SESION_TIENDA,
    rutas: rutasDeTienda(),
    interaccion: async (pagina) => {
      await pagina.locator('#btn-pagar:not([disabled])').click();
      await pagina.locator('[data-accion="confirmar-pago"]').click();
      await pagina.locator('.campo__error:not([hidden])').first().waitFor();
    },
    exige: [
      '.dialogo--pago .pago__resumen .pago__linea',
      '.dialogo--pago .pago__total',
      '.pago__formulario input[autocomplete="cc-name"]',
      '.pago__formulario input[autocomplete="cc-number"][aria-invalid="true"]',
      '.pago__formulario input[autocomplete="cc-exp"]',
      '.pago__formulario input[autocomplete="cc-csc"]',
      '.campo__error:not([hidden])',
    ],
  },
  {
    // B5 — «Mis compras»: una completada (marca y cuatro últimos) y una
    // rechazada, con el motivo de la pasarela.
    id: 'tienda-mis-compras',
    titulo: 'mis compras: una completada y una rechazada con su motivo',
    ruta: 'cuentas/tienda.html',
    sesion: () => SESION_TIENDA,
    rutas: rutasDeTienda({ extra: [['**/api/v1/ordenes', json(ORDENES_DE_LABORATORIO)]] }),
    interaccion: async (pagina) => {
      await pagina.locator('.cart-item').first().waitFor();
      await pagina.locator('#btn-mis-compras').click();
      await pagina.locator('.compras__lista .compra').first().waitFor();
    },
    exige: [
      '.dialogo--compras .compra[data-estado="COMPLETA"] .compra__medio',
      '.dialogo--compras .compra[data-estado="RECHAZADA"] .compra__motivo',
    ],
  },
  {
    // UXC-4 (retroalimentacion del profesor) — la portada con la tienda: sin
    // sesion, productos reales a la vista.
    id: 'portada-con-tienda',
    titulo: 'portada pública con la tienda: productos, rebaja e imagen',
    ruta: 'cuentas/login.html',
    sesion: () => null,
    rutas: rutasDeTienda(),
    exige: ['.vitrina-publica .product-card', '.vitrina-publica .badge-descuento'],
  },
  {
    // UXC-3/UXC-4 — el detalle desde la portada: se leen las opiniones; para
    // comprar u opinar, entrar.
    id: 'portada-detalle-publico',
    titulo: 'detalle público: opiniones de solo lectura y «Entra para comprar»',
    ruta: 'cuentas/login.html',
    sesion: () => null,
    rutas: rutasDeTienda(),
    interaccion: async (pagina) => {
      await pagina.locator('.vitrina-publica [data-ver-producto]').first().click();
      await pagina.locator('.hilo-comentarios .comentario').first().waitFor();
    },
    exige: [
      '.ficha',
      '[data-accion="entrar-para-comprar"]',
      '.hilo-comentarios .comentario',
      '[data-accion="entrar-para-opinar"]',
    ],
  },
  {
    // UXC-3 — la ficha de un objeto del inventario con sus opiniones.
    id: 'inventario-ficha-con-opiniones',
    titulo: 'ficha de un objeto del inventario con valoración e hilo',
    ruta: 'contenido/inventario/inventario.html#objetos',
    sesion: () => sesionDe('qa_opiniones', 'JUGADOR'),
    rutas: [
      ...rutasDeInventario({
        heroeId: 'ddddddd1-1111-4111-8111-111111111111',
        armas: ['ddddddd2-2222-4222-8222-222222222222'],
        armaduras: {},
        items: [],
      }),
      ...rutasDeOpiniones({ hilo: hiloDeLaboratorio(), resumen: RESUMEN_DE_LABORATORIO }),
    ],
    interaccion: async (pagina) => {
      await pagina.locator('#pestana-objetos').click();
      await pagina.locator('.vitrina__detalle').first().click();
      await pagina.locator('.hilo-comentarios .comentario').first().waitFor();
    },
    exige: [
      '.ficha',
      '.ficha__valoracion',
      '.hilo-comentarios .comentario',
      '.redactor-comentario',
    ],
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
    exige: ['.mision-card[data-estado="completada"]', '.mision-card[data-estado="abandonada"]'],
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
  {
    // UXC-6 — el chat general con conversación: «Tú», los demás con su
    // inicial, un logro compartido y separadores de día.
    id: 'chat-general',
    titulo: 'chat general: tú, los demás y el sistema, con palabras y no solo color',
    ruta: 'plataforma/salas-partidas/chat.html',
    sesion: sesionDelChat,
    rutas: [],
    canal: canalDelChatGeneral(),
    exige: [
      '[data-zona="mensajes"] li.mensaje--yo',
      '[data-zona="mensajes"] li.mensaje--otro .mensaje__inicial',
      '.mensaje__logro',
      '.conversacion__dia',
      '.conexion--estable',
    ],
  },
  {
    id: 'chat-sala',
    titulo: 'chat de una sala, con su vuelta a la sala y sin pestañas',
    ruta: `plataforma/salas-partidas/chat.html?sala=${ID_SALA_DEL_CHAT}`,
    sesion: sesionDelChat,
    rutas: [],
    canal: {
      mensajes: {
        [`/app/salas/${ID_SALA_DEL_CHAT}/chat/historial`]: [historialDelChat(ID_SALA_DEL_CHAT)],
      },
    },
    exige: ['[data-zona="volver-a-la-sala"]:not([hidden])', 'li.mensaje--yo'],
  },
  {
    id: 'chat-reconectando',
    titulo: 'chat: se cae el canal, se reintenta solo y el hilo lo cuenta',
    ruta: 'plataforma/salas-partidas/chat.html',
    sesion: sesionDelChat,
    rutas: [],
    canal: { ...canalDelChatGeneral(), cerrarTrasMs: 500, rechazarReconexion: true },
    interaccion: async (pagina) => {
      await pagina.locator('.mensaje--sistema').first().waitFor({ timeout: 10_000 });
    },
    exige: ['.mensaje--sistema', '[data-zona="conexion"][data-estado-canal="reconectando"]'],
  },
  {
    id: 'chat-silenciado',
    titulo: 'chat: silencio por sanción, el campo dice por qué y dónde verlo',
    ruta: 'plataforma/salas-partidas/chat.html',
    sesion: sesionDelChat,
    rutas: [],
    canal: canalDelChatGeneral({
      '/usuario/cola/salas': [
        {
          type: 'https://nexusbattles.local/errores/jugador-silenciado',
          title: 'No puedes escribir en el chat',
          status: 403,
          detail: 'Tienes una sancion activa de silencio.',
        },
      ],
    }),
    exige: ['[data-zona="bloqueo"]:not([hidden])', '[data-zona="bloqueo"] a'],
  },
  {
    // Lo que ve un jugador si el servicio de mensajes privados no responde:
    // aquí no hay servicio, así que la fuente real dice `disponible: false`.
    id: 'chat-privados-sin-abrir',
    titulo:
      'mensajes privados sin servicio que responda: qué pasa, por qué y qué hacer, sin conversaciones de mentira',
    ruta: 'plataforma/salas-partidas/chat.html#privados',
    sesion: sesionDelChat,
    rutas: [],
    canal: canalDelChatGeneral(),
    exige: ['[data-estado="sin-abrir"]', '[data-accion="ir-al-chat-general"]'],
  },
  {
    id: 'chat-privados-conversacion',
    titulo: 'mensajes privados (laboratorio): lista, no leídos y una conversación abierta',
    ruta: 'plataforma/salas-partidas/chat.html?laboratorio=bandeja#privados',
    sesion: sesionDelChat,
    rutas: [MENSAJES_DE_LABORATORIO],
    canal: canalDelChatGeneral(),
    interaccion: abrirConversacion('Bruma'),
    exige: [
      '[data-laboratorio="mensajes"]',
      '.conversaciones__item[aria-current="true"]',
      'li.mensaje--yo .mensaje__entrega',
      'li.mensaje--otro',
      '.conversaciones__no-leidos, .conversaciones__item',
    ],
  },
  {
    id: 'chat-privados-buscar',
    titulo: 'mensajes privados (laboratorio): buscar a un jugador por su apodo',
    ruta: 'plataforma/salas-partidas/chat.html?laboratorio=bandeja#privados',
    sesion: sesionDelChat,
    rutas: [MENSAJES_DE_LABORATORIO],
    canal: canalDelChatGeneral(),
    interaccion: async (pagina) => {
      // Tres letras: el mínimo de la búsqueda por apodo (B6, contrato de perfiles).
      await pagina.locator('#buscar-jugador').fill('bra');
      await pagina.locator('.buscador-jugador__resultado').first().waitFor({ timeout: 10_000 });
    },
    exige: ['.buscador-jugador__resultado'],
  },
  {
    id: 'chat-privados-vacia',
    titulo: 'mensajes privados (laboratorio): todavía sin conversaciones',
    ruta: 'plataforma/salas-partidas/chat.html?laboratorio=vacia#privados',
    sesion: sesionDelChat,
    rutas: [MENSAJES_DE_LABORATORIO],
    canal: canalDelChatGeneral(),
    exige: ['[data-zona="conversaciones"] .estado-vista--vacio'],
  },
  {
    id: 'chat-privados-error',
    titulo: 'mensajes privados (laboratorio): las conversaciones no cargan',
    ruta: 'plataforma/salas-partidas/chat.html?laboratorio=error#privados',
    sesion: sesionDelChat,
    rutas: [MENSAJES_DE_LABORATORIO],
    canal: canalDelChatGeneral(),
    exige: ['[data-zona="conversaciones"] .estado-vista--error'],
  },
  {
    id: 'chat-privados-silencio',
    titulo: 'mensajes privados (laboratorio): tu silencio, con su cuenta atrás',
    ruta: 'plataforma/salas-partidas/chat.html?laboratorio=silencio#privados',
    sesion: sesionDelChat,
    rutas: [MENSAJES_DE_LABORATORIO],
    canal: canalDelChatGeneral(),
    interaccion: abrirConversacion('Bruma'),
    exige: ['[data-zona="restriccion"] .cuenta-atras', '[data-zona="bloqueo"]:not([hidden])'],
  },
  {
    id: 'chat-privados-reconectando',
    titulo: 'mensajes privados (laboratorio): reconectando, sin perder el borrador',
    ruta: 'plataforma/salas-partidas/chat.html?laboratorio=reconectando#privados',
    sesion: sesionDelChat,
    rutas: [MENSAJES_DE_LABORATORIO],
    canal: canalDelChatGeneral(),
    interaccion: async (pagina) => {
      await abrirConversacion('Bruma')(pagina);
      await pagina
        .locator('.mensajes-privados__hilo .mensaje--sistema')
        .waitFor({ timeout: 10_000 });
    },
    exige: [
      '[data-zona="conexion-privados"][data-estado-canal="reconectando"]',
      '.mensajes-privados__hilo .mensaje--sistema',
    ],
  },
  {
    id: 'chat-privados-bloqueada',
    titulo: 'mensajes privados (laboratorio): una conversación que bloqueaste',
    ruta: 'plataforma/salas-partidas/chat.html?laboratorio=bandeja#privados',
    sesion: sesionDelChat,
    rutas: [MENSAJES_DE_LABORATORIO],
    canal: canalDelChatGeneral(),
    interaccion: abrirConversacion('Nyra'),
    exige: [
      '[data-zona="bloqueo"]:not([hidden])',
      '[data-zona="bloqueo"] [data-accion="desbloquear"]',
    ],
  },
  {
    // B6 — la pestaña con su fuente de verdad: la bandeja y el hilo del
    // contrato, lo tuyo «Enviado» (nunca un «Leído» que el servicio no dice)
    // y quien escribe por primera vez entrando en la lista en vivo.
    id: 'chat-privados-servicio',
    titulo:
      'mensajes privados con la fuente del servicio: bandeja, hilo y quien escribe por primera vez',
    ruta: 'plataforma/salas-partidas/chat.html#privados',
    sesion: sesionDelChat,
    rutas: rutasDeMensajesDelServicio(),
    canal: canalDelChatGeneral({
      '/usuario/cola/mensajes-directos': [
        {
          tipo: 'MENSAJE',
          ...mensajeDirecto('dm-9', TALA_EN_EL_CHAT, EN_EL_CHAT.yo, '¿Te apuntas al torneo?', 0),
        },
      ],
    }),
    interaccion: abrirConversacion('Bruma'),
    exige: [
      `.conversaciones__item[data-conversacion="${TALA_EN_EL_CHAT.id}"] .conversaciones__no-leidos`,
      '.mensajes-privados__hilo li.mensaje--otro',
      '.mensajes-privados__hilo li.mensaje--yo .mensaje__entrega[data-entrega="ENVIADO"]',
    ],
  },
  {
    // UXC-7 — el catálogo en la consola: cifras, lista y la acción de cada fila.
    id: 'catalogo-admin',
    titulo: 'consola: los productos del catálogo, con estado y gestión por fila',
    ruta: 'contenido/productos/panel-catalogo.html',
    sesion: SESION_ADMIN_CATALOGO,
    rutas: rutasDelCatalogo(),
    exige: ['.catalogo-admin__tabla tbody tr', '[data-accion="gestionar"]'],
  },
  {
    id: 'catalogo-admin-ficha',
    titulo: 'consola: la ficha de gestión de un producto (ProductAdminSheet)',
    ruta: 'contenido/productos/panel-catalogo.html',
    sesion: SESION_ADMIN_CATALOGO,
    rutas: rutasDelCatalogo(),
    interaccion: async (pagina) => {
      await pagina.locator('[data-accion="gestionar"]').first().click({ timeout: 10_000 });
      await pagina.locator('.dialogo--hoja').waitFor({ timeout: 10_000 });
    },
    exige: ['.dialogo--hoja .hoja-producto__formulario', '[data-accion="suspender"]'],
  },
  {
    // HU-PRD-013 (#854) — la gestión de banners en la consola: uno vigente y
    // uno retirado (su «Retirar» deshabilitado), con sus fechas.
    id: 'banners-admin',
    titulo: 'consola: banners programados, uno vigente y uno retirado',
    ruta: 'contenido/productos/banners.html',
    sesion: SESION_ADMIN_CATALOGO,
    rutas: [
      [
        /\/api\/v1\/banners$/,
        json([
          ...bannersVigentes().slice(0, 1),
          bannerDeLaboratorio('3', 'Doble de créditos el fin de semana pasado.', {
            publicarDesde: new Date(Date.now() - 9 * 86_400_000).toISOString(),
            vigenteHasta: new Date(Date.now() - 7 * 86_400_000).toISOString(),
            retirado: true,
          }),
        ]),
      ],
    ],
    exige: ['.banners__lista article', 'form textarea[name="contenido"]'],
  },
  {
    id: 'mis-sanciones-suspendida',
    titulo: 'mis sanciones: la suspensión activa con su cuenta atrás',
    ruta: 'plataforma/moderacion-sanciones/mis-sanciones.html',
    sesion: () => sesionDe('qa_sancionado', 'JUGADOR'),
    rutas: [
      ['**/api/v1/sanciones/usuarios/*', json(historialDeSanciones())],
      ['**/api/v1/apelaciones*', json([])],
      ['**/api/v1/sanciones/limites', json(LIMITES_DE_SANCION)],
    ],
    exige: ['[data-estado-cuenta="suspendida"] time.cuenta-atras', '[data-accion="apelar"]'],
  },
  {
    id: 'sanciones-admin-linea-de-tiempo',
    titulo: 'consola de sanciones: estado de la cuenta y su historial en línea de tiempo',
    ruta: 'plataforma/moderacion-sanciones/sanciones-admin.html',
    sesion: () => sesionDe('qa_moderador', 'MODERADOR'),
    rutas: [
      ['**/api/v1/sanciones/usuarios/*', json(historialDeSanciones())],
      ['**/api/v1/apelaciones*', json([])],
      ['**/api/v1/sanciones/limites', json(LIMITES_DE_SANCION)],
    ],
    interaccion: async (pagina) => {
      const buscar = pagina.locator('[data-zona="buscar"]');
      await buscar.locator('input').first().fill(UID_SANCIONADO);
      await buscar.locator('[type="submit"]').click();
      await pagina.locator('.linea-tiempo__hecho').first().waitFor({ timeout: 10_000 });
    },
    exige: [
      '[data-estado-cuenta="suspendida"]',
      '.linea-tiempo__hecho--futuro',
      '.linea-tiempo__hecho--exito',
    ],
  },
  {
    id: 'perfil-estado-de-cuenta',
    titulo: 'mi cuenta: el estado de la cuenta antes que el saldo',
    ruta: 'cuentas/perfil.html',
    sesion: () => sesionDe('qa_sancionado', 'JUGADOR'),
    rutas: [
      [
        '**/api/v1/perfiles/*',
        json({
          apodo: 'qa_sancionado',
          email: 'qa@nexus.test',
          nombres: 'Quinn',
          apellidos: 'Arias',
        }),
      ],
      ['**/api/v1/sanciones/usuarios/*', json(historialDeSanciones())],
      ['**/api/v1/creditos/*/saldo', json({ saldoDisponible: 1250, saldoReservado: 100 })],
      ['**/api/v1/creditos/*/movimientos*', json({ content: [], totalPages: 0 })],
    ],
    exige: ['[data-estado-cuenta="suspendida"]', '[data-zona="resumen-saldo"] .metrica'],
  },
  {
    id: 'gestion-usuarios-baneo-critico',
    titulo: 'gestión de usuarios: el baneo pide escribir el apodo (§7.3.9)',
    ruta: 'cuentas/gestion-usuarios.html',
    sesion: () => sesionDe('qa_superadministrador', 'SUPER_ADMINISTRADOR'),
    rutas: [
      [
        '**/api/v1/rbac/matrix',
        json({
          version: '1.1.0',
          matrix: {
            SUPER_ADMINISTRADOR: {
              GESTIONAR_CUENTAS: 'GRANTED',
              SUSPENDER_USUARIOS: 'GRANTED',
              BANEAR_DEFINITIVAMENTE: 'GRANTED',
              ASIGNAR_ROL: 'GRANTED',
            },
          },
        }),
      ],
      [
        '**/api/v1/admin/usuarios/15',
        json({
          id: 15,
          apodo: 'nyx_valiente',
          email: 'nyx@nexus.test',
          estado: 'ACTIVO',
          rolNombre: 'JUGADOR',
          nombres: 'Nyx',
          apellidos: 'Valiente',
          preferencias: '',
        }),
      ],
    ],
    interaccion: async (pagina) => {
      await pagina.locator('#usuario-id').fill('15');
      await pagina.locator('#btn-buscar').click();
      await pagina.locator('#panel-usuario:not([hidden])').waitFor({ timeout: 10_000 });
      await pagina.locator('#btn-banear').click();
      await pagina.locator('.confirmacion-critica').waitFor({ timeout: 10_000 });
    },
    exige: [
      '.confirmacion-critica__consecuencias',
      '[role="dialog"] [data-accion="confirmar"][disabled]',
    ],
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

/* ---------------------------------------------------------------------------
   UXC-8 / UXC-9 — lo tuyo en subastas y torneos, y el aviso de red.
   ------------------------------------------------------------------------- */
const UID_POSTOR = 'bbbbbbb9-9999-4999-8999-999999999999';
const ID_SUBASTA_PROPIA = 'aaaaaaa4-4444-4444-8444-444444444444';

function subastasConLoTuyo() {
  return json({
    contenido: [
      subastaUrgente(),
      subastaTranquila(),
      { ...subastaSinRareza(), precioCompraInmediata: null },
      {
        ...subastaTranquila(),
        id: ID_SUBASTA_PROPIA,
        nombreProducto: 'Yelmo del Vigía',
        rareza: 'comun',
        vendedorId: UID_POSTOR,
        cantidadPujas: 2,
        fechaFin: new Date(Date.now() + 7_200_000).toISOString(),
      },
    ],
    pagina: 0,
    tamano: 16,
    totalElementos: 4,
    totalPaginas: 1,
  });
}

/** `MiParticipacion` según la subasta: ganando en la urgente, superado en la tranquila. */
function participacionDe(ruta) {
  const url = ruta.request().url();
  if (url.includes(subastaUrgente().id)) {
    return json({
      vasGanando: true,
      teSuperaron: false,
      tuOfertaVigente: '1350',
      creditosRetenidos: '1350',
      limiteAutomatico: '2000',
      automaticaActiva: true,
      segundosParaVolverAPujar: 0,
    });
  }
  if (url.includes(subastaTranquila().id)) {
    return json({
      vasGanando: false,
      teSuperaron: true,
      tuOfertaVigente: null,
      creditosRetenidos: '0',
      limiteAutomatico: null,
      automaticaActiva: false,
      segundosParaVolverAPujar: 0,
    });
  }
  return json({
    vasGanando: false,
    teSuperaron: false,
    tuOfertaVigente: null,
    creditosRetenidos: '0',
    limiteAutomatico: null,
    automaticaActiva: false,
    segundosParaVolverAPujar: 0,
  });
}

/*
 * UXC-8 — el panel personal (ms-subastas-panel.yaml 1.0.0) y la ficha
 * (ms-subastas-listado.yaml, `SubastaDetalle`). DATOS DE LABORATORIO: los
 * nombres, las cifras y las fechas son inventados; la forma es la del
 * contrato.
 */
const hace = (horas) => new Date(Date.now() - horas * 3_600_000).toISOString();
const dentroDe = (horas) => new Date(Date.now() + horas * 3_600_000).toISOString();
const PRODUCTO_DE_LA_SUBASTA = 'aaaaaaa1-0000-4000-8000-0000000000c1';

function misPublicaciones() {
  const base = {
    miniaturaUrl: null,
    precioInicial: '500',
    precioCompraInmediata: null,
    fechaPublicacion: hace(30),
    comisionCobrada: '1.50',
    penalizacionCobrada: null,
    cancelable: false,
    penalizacionSiCancela: null,
  };
  return json([
    {
      ...base,
      subastaId: ID_SUBASTA_PROPIA,
      nombreProducto: 'Yelmo del Vigía',
      estado: 'ACTIVA',
      ofertaVigente: '900',
      cantidadPujas: 2,
      fechaFin: dentroDe(2),
      cerradaEn: null,
      vistas: 41,
    },
    {
      ...base,
      subastaId: 'aaaaaaa4-4444-4444-8444-444444444445',
      nombreProducto: 'Brazaletes de Cobre',
      estado: 'ACTIVA',
      ofertaVigente: '300',
      precioInicial: '300',
      cantidadPujas: 0,
      fechaFin: dentroDe(20),
      cerradaEn: null,
      vistas: 7,
      comisionCobrada: '1.00',
      cancelable: true,
      penalizacionSiCancela: '0.50',
    },
    {
      ...base,
      subastaId: 'aaaaaaa4-4444-4444-8444-444444444446',
      nombreProducto: 'Arco de Tejo Antiguo',
      estado: 'ADJUDICADA',
      ofertaVigente: '2300',
      cantidadPujas: 6,
      fechaFin: hace(20),
      cerradaEn: hace(20),
      vistas: 88,
    },
    {
      ...base,
      subastaId: 'aaaaaaa4-4444-4444-8444-444444444447',
      nombreProducto: 'Capa Raída',
      estado: 'SIN_ADJUDICACION',
      ofertaVigente: '500',
      cantidadPujas: 0,
      fechaFin: hace(40),
      cerradaEn: hace(40),
      vistas: 12,
    },
    {
      ...base,
      subastaId: 'aaaaaaa4-4444-4444-8444-444444444448',
      nombreProducto: 'Anillo Opaco',
      estado: 'CANCELADA',
      ofertaVigente: '400',
      cantidadPujas: 0,
      fechaFin: hace(10),
      cerradaEn: hace(60),
      vistas: 3,
      comisionCobrada: '1.00',
      penalizacionCobrada: '0.50',
    },
  ]);
}

function misSeguidas() {
  return json([
    {
      subastaId: subastaTranquila().id,
      nombreProducto: subastaTranquila().nombreProducto,
      miniaturaUrl: null,
      estado: 'ACTIVA',
      ofertaVigente: subastaTranquila().ofertaVigente,
      precioCompraInmediata: null,
      cantidadPujas: subastaTranquila().cantidadPujas,
      fechaFin: subastaTranquila().fechaFin,
      seguidaDesde: hace(5),
    },
    {
      subastaId: 'aaaaaaa4-4444-4444-8444-444444444449',
      nombreProducto: 'Escudo de Roble',
      miniaturaUrl: null,
      estado: 'ADJUDICADA',
      ofertaVigente: '1750',
      precioCompraInmediata: null,
      cantidadPujas: 9,
      fechaFin: hace(3),
      seguidaDesde: hace(50),
    },
  ]);
}

function miHistorial() {
  const mov = (tipo, subastaId, nombreProducto, monto, horas) => ({
    tipo,
    subastaId,
    nombreProducto,
    monto,
    fecha: hace(horas),
  });
  return json({
    movimientos: [
      mov('VENTA', 'aaaaaaa4-4444-4444-8444-444444444446', 'Arco de Tejo Antiguo', '2185', 20),
      mov('COMPRA', 'aaaaaaa4-4444-4444-8444-44444444444a', 'Grebas del Centinela', '1200', 26),
      mov('PENALIZACION', 'aaaaaaa4-4444-4444-8444-444444444448', 'Anillo Opaco', '0.50', 60),
      mov('COMISION', 'aaaaaaa4-4444-4444-8444-444444444448', 'Anillo Opaco', '1.00', 70),
      mov('COMISION', 'aaaaaaa4-4444-4444-8444-444444444446', 'Arco de Tejo Antiguo', '1.50', 72),
    ],
    totalGanado: '2185',
    totalGastado: '1203',
    comisionesPagadas: '3',
    balance: '982',
  });
}

/** La subasta del listado de laboratorio con ese id (la propia incluida). */
function subastaDeLaboratorio(id) {
  const listado = JSON.parse(subastasConLoTuyo().body).contenido;
  return listado.find((s) => s.id === id) ?? subastaTranquila();
}

/** `SubastaDetalle` de la subasta que se pide, con visitas y reputación. */
function fichaDe(ruta) {
  const id = new URL(ruta.request().url()).pathname.split('/').pop();
  const base = subastaDeLaboratorio(id);
  const propia = id === ID_SUBASTA_PROPIA;
  const compra = base.precioCompraInmediata ?? null;
  return json({
    id,
    estado: 'ACTIVA',
    productoId: PRODUCTO_DE_LA_SUBASTA,
    nombreProducto: base.nombreProducto,
    tipoProducto: base.tipoProducto,
    rareza: base.rareza ?? null,
    descripcionCorta: base.descripcionCorta,
    ofertaVigente: base.ofertaVigente,
    pujaMinimaSiguiente: String(Number(base.ofertaVigente) + 50),
    incrementoMinimo: '50',
    precioCompraInmediata: compra,
    compraInmediataDisponible: compra !== null && Number(compra) > Number(base.ofertaVigente),
    cantidadPujas: base.cantidadPujas,
    fechaFin: base.fechaFin,
    esMaestroDeJuego: false,
    metodoPago: 'CREDITOS',
    vendedorId: propia ? UID_POSTOR : 'ccccccc3-3333-4333-8333-333333333333',
    vendedorApodo: propia ? 'qa_postor' : 'Bruma',
    reputacionVendedor: propia
      ? { ventasCompletadas: 1, subastasTerminadas: 3, cancelaciones: 1, tasaDeExito: 0.33 }
      : { ventasCompletadas: 12, subastasTerminadas: 15, cancelaciones: 1, tasaDeExito: 0.8 },
    vistas: propia ? 41 : 128,
  });
}

/**
 * `PujaDelHistorial` (0.4.0): tantas como diga la subasta, de la más reciente
 * a la más antigua, con el postor anonimizado a medias (7.7.11).
 */
function pujasDe(ruta) {
  const id = new URL(ruta.request().url()).pathname.split('/').slice(-2)[0];
  const base = subastaDeLaboratorio(id);
  const postores = ['k***s', 'l***9', 'B***a', null];
  return json(
    Array.from({ length: base.cantidadPujas }, (_, i) => ({
      id: `9999999${i}-0000-4000-8000-000000000000`,
      monto: String(Number(base.ofertaVigente) - i * 50),
      tipo: i % 3 === 1 ? 'AUTOMATICA' : 'MANUAL',
      estado: i === 0 ? 'ACTIVA' : 'SUPERADA',
      creadaEn: hace(i * 0.4 + 0.1),
      esTuya: id === subastaUrgente().id && i === 0,
      postor: postores[i % postores.length],
    })),
  );
}

/** «Mis pujas» (0.4.0): las del listado y dos ya terminadas. */
function misPujas() {
  const participacion = (s, estado, tuMejorPuja) => ({
    subastaId: s.id,
    nombreProducto: s.nombreProducto,
    miniaturaUrl: null,
    estadoSubasta: 'ACTIVA',
    estado,
    tuMejorPuja,
    ofertaVigente: s.ofertaVigente,
    cantidadPujas: s.cantidadPujas,
    fechaFin: s.fechaFin,
    ultimaPujaEn: hace(1),
  });
  return json([
    participacion(subastaUrgente(), 'GANANDO', '1350'),
    participacion(subastaTranquila(), 'SUPERADA', '830'),
    {
      subastaId: 'aaaaaaa4-4444-4444-8444-44444444444a',
      nombreProducto: 'Grebas del Centinela',
      miniaturaUrl: null,
      estadoSubasta: 'ADJUDICADA',
      estado: 'GANADA',
      tuMejorPuja: '1200',
      ofertaVigente: '1200',
      cantidadPujas: 5,
      fechaFin: hace(26),
      ultimaPujaEn: hace(27),
    },
    {
      subastaId: 'aaaaaaa4-4444-4444-8444-44444444444b',
      nombreProducto: 'Amuleto de Bruma',
      miniaturaUrl: null,
      estadoSubasta: 'ADJUDICADA',
      estado: 'PERDIDA',
      tuMejorPuja: '400',
      ofertaVigente: '520',
      cantidadPujas: 8,
      fechaFin: hace(50),
      ultimaPujaEn: hace(51),
    },
  ]);
}

const RUTAS_DE_LO_TUYO = [
  [/\/api\/v1\/subastas\?/, () => subastasConLoTuyo()],
  [/\/api\/v1\/subastas\/[^/?]+\/mi-participacion/, (ruta) => participacionDe(ruta)],
  [/\/api\/v1\/subastas\/[^/?]+\/pujas/, (ruta) => pujasDe(ruta)],
  [/\/api\/v1\/subastas\/[0-9a-f-]{36}$/, (ruta) => fichaDe(ruta)],
  ['**/api/v1/mis-pujas', () => misPujas()],
  [
    '**/api/v1/mis-pujas/resumen',
    json({ creditosRetenidos: '1350', saldoDisponible: '4200', subastasGanando: 1 }),
  ],
  ['**/api/v1/mis-subastas/publicadas', () => misPublicaciones()],
  ['**/api/v1/mis-subastas/seguimiento', () => misSeguidas()],
  ['**/api/v1/mis-subastas/historial', () => miHistorial()],
  ['**/api/v1/mis-subastas/pendientes', json([])],
];

/** El capitán de «Lobos del Alba» (el primer equipo del árbol). */
const UID_CAPITAN = 'aaaaaaa1-1111-4111-8111-111111111111';

export const ESCENARIOS_UXC8 = [
  {
    id: 'mis-subastas-con-lo-tuyo',
    titulo: 'mis subastas: ganando, superada y una publicación propia, con saldo',
    ruta: 'cuentas/pujas.html',
    sesion: () => sesionDe('qa_postor', 'JUGADOR', UID_POSTOR),
    rutas: RUTAS_DE_LO_TUYO,
    interaccion: async (pagina) => {
      await pagina.locator('.tab-btn[data-tab="mis-subastas"]').click();
      await pagina.locator('.fila-mi-subasta.borde-ganando').waitFor({ timeout: 10_000 });
    },
    exige: [
      '.fila-mi-subasta.borde-ganando',
      '.fila-mi-subasta.borde-superada',
      '.fila-publicacion',
      '.barra-segmentada-tramos',
      // UXC-8 — el panel personal: tus publicaciones en cualquier estado, lo
      // que sigues y el historial con su balance.
      '.indice-mis-subastas',
      '.fila-mi-puja[data-resultado="PERDIDA"]',
      '.fila-publicacion--panel[data-estado="ADJUDICADA"]',
      '[data-cancelar-publicacion]',
      '.fila-seguida',
      '.tabla-historial tbody tr',
      '#btn-exportar-historial',
    ],
  },
  {
    id: 'subasta-propia',
    titulo: 'el detalle de una subasta propia: sin pujar y con el motivo',
    ruta: `cuentas/pujas.html?id=${ID_SUBASTA_PROPIA}`,
    sesion: () => sesionDe('qa_postor', 'JUGADOR', UID_POSTOR),
    rutas: [...RUTAS_DE_LO_TUYO, ...rutasDeOpiniones()],
    exige: [
      '.aviso-subasta-propia',
      '.badge-propia',
      '#btn-pujar-manual[disabled]',
      '.ficha-subasta',
    ],
  },
  {
    id: 'subasta-con-opiniones',
    titulo: 'el detalle de una subasta ajena: vendedor, visitas y opiniones del objeto',
    ruta: `cuentas/pujas.html?id=${subastaTranquila().id}`,
    sesion: () => sesionDe('qa_postor', 'JUGADOR', UID_POSTOR),
    rutas: [
      ...RUTAS_DE_LO_TUYO,
      ...rutasDeOpiniones({ hilo: hiloDeLaboratorio(), resumen: RESUMEN_DE_LABORATORIO }),
    ],
    exige: [
      '.ficha-subasta',
      '.historial-postor',
      '#opiniones-subasta .hilo-comentarios',
      '#opiniones-subasta .comentario',
    ],
  },
  {
    id: 'torneo-mi-equipo',
    titulo: 'torneo en curso visto por un capitán: tu torneo, tu encuentro y la transmisión',
    ruta: `plataforma/torneos/torneos.html?torneo=${ID_TORNEO}`,
    sesion: () => sesionDe('qa_capitan', 'JUGADOR', UID_CAPITAN),
    rutas: [
      ['**/api/v1/torneos', json([torneoEnCurso()])],
      [`**/api/v1/torneos/${ID_TORNEO}`, json(torneoEnCurso())],
    ],
    exige: [
      '[data-zona="mi-torneo"]',
      '[data-accion="jugar-mi-encuentro"]',
      '.encuentro--mio',
      '[data-zona="transmision"]',
      '[data-zona="premio"]',
      '[data-zona="pago"]',
    ],
  },
  {
    id: 'torneo-campeon',
    titulo: 'torneo terminado visto por el capitán campeón: su premio entregado',
    ruta: `plataforma/torneos/torneos.html?torneo=${ID_TORNEO}`,
    sesion: () => sesionDe('qa_capitan', 'JUGADOR', UID_CAPITAN),
    rutas: [
      ['**/api/v1/torneos', json([torneoTerminadoConCampeon()])],
      [`**/api/v1/torneos/${ID_TORNEO}`, json(torneoTerminadoConCampeon())],
    ],
    exige: [
      '[data-zona="campeon"]',
      '[data-zona="premio"]',
      '[data-zona="mi-premio"].torneo-mio__premio--exito',
    ],
  },
  {
    // Punto 24 — «muy básico su cuadro inicial»: un solo torneo, su tarjeta
    // a lo ancho como un cartel, con los cupos como barra.
    id: 'torneos-tablon',
    titulo: 'tablón con un torneo abierto: la tarjeta a lo ancho',
    ruta: 'plataforma/torneos/torneos.html',
    sesion: () => sesionDe('qa_torneo', 'JUGADOR'),
    rutas: [['**/api/v1/torneos', json([torneoAbierto()])]],
    exige: ['[data-cuantos="1"] .torneo-card', '.torneo-card [role="progressbar"]'],
  },
  {
    id: 'torneos-tablon-varios',
    titulo: 'tablón con tres torneos: abierto, en curso y terminado',
    ruta: 'plataforma/torneos/torneos.html',
    sesion: () => sesionDe('qa_torneo', 'JUGADOR'),
    rutas: [['**/api/v1/torneos', json(torneosDelTablon())]],
    exige: [
      '.torneo-card[data-estado="INSCRIPCIONES_ABIERTAS"]',
      '.torneo-card[data-estado="EN_CURSO"]',
      '.torneo-card[data-estado="FINALIZADO"]',
    ],
  },
  {
    // Punto 24 — la ruta de un torneo abierto vista por quien aún no tiene
    // equipo: «Mi equipo» es donde está, con el registro.
    id: 'torneo-ruta-inscripciones',
    titulo: 'ruta de un torneo abierto: portada, hitos y registro del equipo',
    ruta: `plataforma/torneos/torneos.html?torneo=${ID_TORNEO_ABIERTO}`,
    sesion: () => sesionDe('qa_torneo', 'JUGADOR'),
    rutas: [
      ['**/api/v1/torneos', json([torneoAbierto()])],
      [`**/api/v1/torneos/${ID_TORNEO_ABIERTO}`, json(torneoAbierto())],
    ],
    exige: [
      '.torneo-portada [role="progressbar"]',
      '.torneo-ruta [data-hito="equipo"][aria-current="step"]',
      '[data-zona="crear-equipo"]',
      '[data-zona="equipos"] .torneo__equipos',
    ],
  },
  {
    id: 'aviso-de-red',
    titulo: 'sin conexión: el aviso de red transversal',
    ruta: 'plataforma/torneos/torneos.html',
    sesion: () => sesionDe('qa_red', 'JUGADOR'),
    rutas: [['**/api/v1/torneos', json([torneoEnCurso()])]],
    interaccion: async (pagina) => {
      await pagina.locator('[data-torneo-id]').first().waitFor({ timeout: 10_000 });
      await pagina.evaluate(() => globalThis.dispatchEvent(new Event('offline')));
    },
    exige: ['.aviso-red--sin-red'],
  },
];

// ---------------------------------------------------------------------------
// UXC-9 — Mi cuenta · Estadísticas (§7.1.1, RF-USR-012)
// ---------------------------------------------------------------------------

const HEROES_DE_LAS_PARTIDAS = [
  'Guerrero Tanque',
  'Mago Fuego',
  'Guerrero Armas',
  'Mago Hielo',
];
const MODALIDADES_DE_LAS_PARTIDAS = ['UNO_CONTRA_UNO', 'CONTRA_IA', 'HASTA_SEIS'];
const RESULTADOS_DE_LAS_PARTIDAS = ['VICTORIA', 'DERROTA', 'VICTORIA', 'EMPATE'];

/**
 * `PaginaDePartidas` (salas-partidas 1.7.0): la primera de dos páginas de
 * dieciséis, la más reciente todavía en curso. Coherente con el contrato:
 * 16 en la página, 23 en total, 2 páginas.
 */
function partidasDelJugador() {
  const contenido = Array.from({ length: 16 }, (_, i) => {
    const enCurso = i === 0;
    const inicio = new Date(Date.UTC(2026, 8, 27, 20, 0) - i * 5 * 3_600_000);
    const modalidad = MODALIDADES_DE_LAS_PARTIDAS[i % 3];
    return {
      id: `eeeeeee${(i % 10).toString(16)}-0000-4000-8000-${String(i).padStart(12, '0')}`,
      idSala: `eeeeeee${(i % 10).toString(16)}-1111-4111-8111-${String(i).padStart(12, '0')}`,
      modalidad,
      estado: enCurso ? 'EN_CURSO' : 'FINALIZADA',
      resultado: enCurso ? null : RESULTADOS_DE_LAS_PARTIDAS[i % 4],
      heroe: HEROES_DE_LAS_PARTIDAS[i % 4],
      participantes: modalidad === 'HASTA_SEIS' ? 4 : 2,
      iniciadaEn: inicio.toISOString(),
      finalizadaEn: enCurso ? null : new Date(inicio.getTime() + 12 * 60_000).toISOString(),
    };
  });
  return { contenido, pagina: 0, tamano: 16, totalElementos: 23, totalPaginas: 2 };
}

/** `HistorialDeMisiones` (misiones 1.0.0) con cifras por categoría y una épica. */
const HISTORIAL_DE_MISIONES_DE_LA_CUENTA = {
  completadas: [
    {
      ejecucionId: 'eeeeeee1-2222-4222-8222-000000000001',
      misionId: 'prologo',
      nombre: 'Prólogo: El Llamado del Nexo',
      categoria: 'HISTORIA',
      terminadaEn: '2026-09-26T10:00:00Z',
      resultado: 'EXITO',
      duracionMs: 3_600_000,
    },
  ],
  porCategoria: [
    { categoria: 'HISTORIA', completadas: 4, fallidas: 1 },
    { categoria: 'DESAFIO', completadas: 2, fallidas: 2 },
    { categoria: 'EXPLORACION', completadas: 3, fallidas: 0 },
  ],
  mejoresTiempos: [],
  epicas: [
    // La misma épica que el laboratorio de misiones (tabla del documento).
    { nombre: 'Velo de Sombras', master: 'Sombra del Olvido', obtenidaEn: '2026-09-24T12:00:00Z' },
  ],
  cadenas: [],
};

/** Una conversación con el asistente (`/chat/historial`, ms-chatbot). */
const CONVERSACION_CON_EL_ASISTENTE = [
  {
    id: 'eeeeeee2-3333-4333-8333-000000000001',
    remitente: 'USUARIO',
    contenido: '¿Cómo pujo en una subasta?',
    adjuntoUrl: null,
    fechaEnvio: '2026-09-27T18:00:00Z',
  },
  {
    id: 'eeeeeee2-3333-4333-8333-000000000002',
    remitente: 'BOT',
    contenido:
      'Abre la subasta y escribe tu puja: tiene que superar la actual por el incremento mínimo.',
    adjuntoUrl: null,
    fechaEnvio: '2026-09-27T18:00:02Z',
  },
];

export const ESCENARIOS_UXC9 = [
  {
    // La ventana del asistente sobre la atmósfera: su texto tiene que salir
    // oscuro sobre su superficie clara (antes el título y las respuestas del
    // bot salían en blanco sobre blanco), y sus controles de minimizar y de
    // tamaño, con nombre.
    id: 'asistente-abierto',
    titulo: 'el asistente abierto con una conversación, minimizable y redimensionable (§7.4)',
    ruta: 'cuentas/perfil.html',
    sesion: () => sesionDe('qa_asistente', 'JUGADOR'),
    rutas: [
      ['**/api/v1/perfiles/*', json({ apodo: 'qa_asistente', email: 'asistente@nexus.test' })],
      ['**/api/v1/sanciones/usuarios/*', json([])],
      ['**/api/v1/creditos/*/saldo', json({ saldoDisponible: 480, saldoReservado: 0 })],
      ['**/api/v1/creditos/*/movimientos*', json({ content: [], totalPages: 0 })],
      // Con * : la ventana pide el historial por páginas (?limite=, 7.4.6).
      ['**/api/v1/chat/historial*', json(CONVERSACION_CON_EL_ASISTENTE)],
    ],
    interaccion: async (pagina) => {
      await pagina.locator('.chatbot-flotante').click();
      await pagina
        .locator('.chatbot-ventana:not([hidden]) .chatbot-ventana__mensaje--bot')
        .first()
        .waitFor({ timeout: 10_000 });
    },
    exige: [
      '.chatbot-ventana:not([hidden]) .chatbot-ventana__mensaje--bot',
      '[data-accion="minimizar-asistente"][aria-expanded="true"]',
      '[data-accion="tamano-asistente"]',
    ],
  },
  {
    id: 'perfil-estadisticas',
    titulo: 'mi cuenta: tus batallas, tus misiones, torneos y logros (§7.1.1)',
    ruta: 'cuentas/perfil.html#estadisticas',
    sesion: () => sesionDe('qa_estadisticas', 'JUGADOR'),
    rutas: [
      [
        '**/api/v1/perfiles/*',
        json({
          apodo: 'qa_estadisticas',
          email: 'estadisticas@nexus.test',
          nombres: 'Elena',
          apellidos: 'Duarte',
        }),
      ],
      ['**/api/v1/sanciones/usuarios/*', json([])],
      ['**/api/v1/creditos/*/saldo', json({ saldoDisponible: 480, saldoReservado: 0 })],
      ['**/api/v1/creditos/*/movimientos*', json({ content: [], totalPages: 0 })],
      ['**/api/v1/partidas/mias*', json(partidasDelJugador())],
      ['**/api/v1/misiones/historial', json(HISTORIAL_DE_MISIONES_DE_LA_CUENTA)],
    ],
    exige: [
      '[data-zona="mis-partidas"] .distintivo--victoria',
      '[data-zona="mis-partidas"] .distintivo--derrota',
      '[data-zona="mis-partidas"] .distintivo--en-juego',
      'nav.paginacion[aria-label="Páginas de tus partidas"]',
      '[data-zona="estadisticas-misiones"] .metrica',
      '[data-zona="estadisticas-pendiente"] .cuenta-notas__nota',
      '[data-zona="suscripciones-pagos"]',
      '[data-zona="privacidad"]',
    ],
  },
];

// Los escenarios de arriba usan ayudantes definidos después del arreglo
// principal; se suman al final, cuando ya existen.
ESCENARIOS.push(...ESCENARIOS_UXC8, ...ESCENARIOS_UXC9);
