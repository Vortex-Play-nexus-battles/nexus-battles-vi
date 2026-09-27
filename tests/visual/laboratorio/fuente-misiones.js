/**
 * LABORATORIO — fuente de misiones de ejemplo. NO ES DEL PRODUCTO.
 *
 * El laboratorio visual sirve este archivo EN LUGAR DE
 * `frontend/app-web/src/contenido/misiones/fuente-misiones.js` (ver
 * `escenarios-poblados.js`, escenarios `misiones-*`), para poder fotografiar y
 * auditar con axe los estados que la vista de misiones tendrá cuando exista su
 * servicio. En producción esa fuente dice `disponible: false` y la vista pinta
 * el estado honesto; ninguna de estas misiones llega nunca al producto.
 *
 * De dónde sale cada dato:
 *
 *   - «El Templo Olvidado» es, palabra por palabra, el ejemplo de misión del
 *     documento del curso (§7.8.14): categoría, nivel, duración, dificultad,
 *     narrativa, objetivos, enemigos, jefe, Máster con su épica y recompensas.
 *   - Las demás misiones, el héroe en misión, los reportes y el historial son
 *     DATOS DE LABORATORIO, inventados para que cada estado de §7.8.7
 *     (disponible, bloqueada, en progreso, completada, fallida y abandonada)
 *     tenga algo que pintar. Sus cifras no son del juego.
 *
 * La forma de cada objeto es la de los tipos de `fuente-misiones.js`.
 */

/* Aviso visible en cada captura: esto es el laboratorio, no el producto. */
if (typeof document !== 'undefined' && document.body) {
  const aviso = document.createElement('p');
  aviso.setAttribute('role', 'note');
  aviso.dataset.laboratorio = 'misiones';
  aviso.textContent =
    'Laboratorio visual: misiones de ejemplo. «El Templo Olvidado» es la del documento del curso (§7.8.14); las demás son datos de prueba. En producción esta vista no tiene misiones hasta que exista su servicio.';
  aviso.style.cssText =
    'margin:0;padding:8px 16px;background:#FBF0DE;color:#5C3100;font:600 13px/1.4 Inter,system-ui,sans-serif;text-align:center;border-bottom:2px solid #8A4A00';
  document.body.prepend(aviso);
}

const HORA = 3_600_000;
const ahora = Date.now();
const iso = (desplazamientoMs) => new Date(ahora + desplazamientoMs).toISOString();

/** §7.8.14 — el ejemplo del documento, tal cual. */
const TEMPLO = {
  id: 'templo-olvidado',
  nombre: 'El Templo Olvidado',
  categoria: 'HISTORIA',
  descripcionBreve:
    'Un antiguo templo en el Bosque Sombrío, custodiado por criaturas corrompidas y un guardián milenario.',
  imagen: null,
  dificultad: 'NORMAL',
  duracionHoras: 12,
  nivelRecomendado: 15,
  recompensasDestacadas: [
    '50 créditos',
    '1 Cofre de Bronce',
    'Armadura «Piel del Guardián» (20 %)',
  ],
  estado: 'DISPONIBLE',
  destacada: true,
  nueva: true,
  favorita: false,
  escalon: 'NORMAL',
  narrativa:
    'En las profundidades del Bosque Sombrío yace un antiguo templo dedicado a los Dioses Olvidados. Según las leyendas, el templo alberga poderosos artefactos y conocimientos ancestrales.\n\nSin embargo, está custodiado por criaturas corrompidas y un guardián milenario que protege sus secretos.',
  objetivos: {
    principales: [
      'Derrotar al Guardián del Templo (jefe final).',
      'Explorar las 5 cámaras del templo.',
    ],
    secundarios: [
      'Completar la misión sin que la vida del héroe baje del 50 %.',
      'Derrotar al Máster si aparece.',
      'Encontrar los 3 fragmentos del Sello Antiguo.',
    ],
  },
  enemigos: [
    {
      nombre: 'Sombras Corrompidas',
      cantidad: 10,
      descripcion: 'Enemigos básicos con ataque moderado.',
    },
    { nombre: 'Guardianes de Piedra', cantidad: 5, descripcion: 'Enemigos con alta defensa.' },
    { nombre: 'Espectros Ancestrales', cantidad: 3, descripcion: 'Enemigos con ataques mágicos.' },
  ],
  jefe: {
    nombre: 'El Guardián Eterno',
    prototipo: 'Guerrero Tanque',
    vida: 100,
    descripcion: 'Guerrero Tanque con habilidades potenciadas.',
  },
  // El documento escribe «0.15 % (15 % de probabilidad)»: como proporción,
  // 0,15 es un 15 %.
  probabilidadMaster: 0.15,
  masters: [
    {
      nombre: 'Sombra del Olvido',
      prototipo: 'Pícaro Veneno',
      epica: {
        nombre: 'Velo de Sombras',
        efectoGeneral: '+2 a la defensa para todos los héroes.',
        efectoPotenciado:
          'El héroe se vuelve intangible durante 1 turno, evitando todo el daño recibido y causando envenenamiento al atacante (+3 de daño por veneno durante 2 turnos).',
      },
    },
  ],
  recompensas: {
    garantizadas: ['50 créditos', '1 Cofre de Bronce (contiene ítems comunes)'],
    potenciales: [
      { nombre: 'Fragmento del Sello Antiguo', probabilidad: 0.6, detalle: 'cada uno, 3 en total' },
      { nombre: 'Armadura «Piel del Guardián»', probabilidad: 0.2 },
      { nombre: 'Arma «Espada del Templo»', probabilidad: 0.15 },
    ],
    primeraVez: ['10 créditos adicionales', 'Título «Explorador del Templo»'],
  },
};

/** DATOS DE LABORATORIO: una misión por estado de §7.8.7. */
function deLaboratorio(cambios) {
  return {
    imagen: null,
    nivelRecomendado: null,
    recompensasDestacadas: [],
    destacada: false,
    nueva: false,
    favorita: false,
    escalon: null,
    narrativa:
      'Misión de laboratorio: existe para que este estado del tablón tenga algo que enseñar. Sus datos no son del juego.',
    objetivos: { principales: ['Llegar al final de la misión.'], secundarios: [] },
    enemigos: [],
    jefe: null,
    probabilidadMaster: null,
    masters: [],
    recompensas: { garantizadas: [], potenciales: [] },
    ...cambios,
  };
}

const MISIONES = [
  deLaboratorio({
    id: 'prologo',
    nombre: 'Prólogo: El Llamado del Nexo',
    categoria: 'HISTORIA',
    descripcionBreve: 'Misión de laboratorio: la primera de la historia, ya completada.',
    dificultad: 'FACIL',
    duracionHoras: 4,
    recompensasDestacadas: ['20 créditos'],
    estado: 'COMPLETADA',
    ultimaEjecucionId: 'ejecucion-prologo',
  }),
  TEMPLO,
  deLaboratorio({
    id: 'camaras-del-sello',
    nombre: 'Las Cámaras del Sello',
    categoria: 'HISTORIA',
    descripcionBreve: 'Misión de laboratorio: sigue a «El Templo Olvidado» en la historia.',
    dificultad: 'DIFICIL',
    duracionHoras: 18,
    estado: 'BLOQUEADA',
    motivoBloqueo: 'Completa «El Templo Olvidado» para desbloquearla.',
    requisitosPrevios: ['El Templo Olvidado'],
  }),
  deLaboratorio({
    id: 'torre-de-ceniza',
    nombre: 'Asedio a la Torre de Ceniza',
    categoria: 'DESAFIO',
    descripcionBreve: 'Misión de laboratorio: un desafío de tiempo limitado, ahora en curso.',
    dificultad: 'EXTREMO',
    duracionHoras: 8,
    recompensasDestacadas: ['Recompensa premium'],
    estado: 'EN_PROGRESO',
    progreso: 0.4,
    destacada: true,
    disponibleHasta: iso(3 * 24 * HORA),
  }),
  deLaboratorio({
    id: 'paso-helado',
    nombre: 'Emboscada en el Paso Helado',
    categoria: 'DESAFIO',
    descripcionBreve: 'Misión de laboratorio: el último intento terminó con el héroe derrotado.',
    dificultad: 'DIFICIL',
    duracionHoras: 6,
    estado: 'FALLIDA',
    ultimaEjecucionId: 'ejecucion-paso-helado',
  }),
  deLaboratorio({
    id: 'bosque-sombrio',
    nombre: 'Rutas del Bosque Sombrío',
    categoria: 'EXPLORACION',
    descripcionBreve: 'Misión de laboratorio: una exploración larga, ya completada.',
    dificultad: 'NORMAL',
    duracionHoras: 36,
    recompensasDestacadas: ['Recompensas acumulativas'],
    estado: 'COMPLETADA',
    ultimaEjecucionId: 'ejecucion-bosque',
  }),
  deLaboratorio({
    id: 'marea-de-espectros',
    nombre: 'Marea de Espectros',
    categoria: 'EXPLORACION',
    descripcionBreve: 'Misión de laboratorio: cancelada por el jugador a mitad de camino.',
    dificultad: 'FACIL',
    duracionHoras: 24,
    estado: 'ABANDONADA',
  }),
];

const TRAMOS = {
  HASTA_12: (h) => h <= 12,
  DE_12_A_24: (h) => h > 12 && h <= 24,
  MAS_DE_24: (h) => h > 24,
};

const resumen = ({
  narrativa: _n,
  objetivos: _o,
  enemigos: _e,
  jefe: _j,
  masters: _m,
  recompensas: _r,
  ...resto
}) => resto;

const REPORTES = {
  'ejecucion-bosque': {
    ejecucionId: 'ejecucion-bosque',
    mision: { id: 'bosque-sombrio', nombre: 'Rutas del Bosque Sombrío', categoria: 'EXPLORACION' },
    resultado: 'EXITO',
    duracionMs: 36 * HORA,
    terminadaEn: iso(-2 * 24 * HORA),
    heroe: { nombre: 'Vorn el Filo', prototipo: 'Guerrero Armas', nivel: 1 },
    combate: {
      encuentros: 12,
      danoInfligido: 860,
      danoRecibido: 410,
      turnos: 74,
      habilidadesMasUsadas: [
        { nombre: 'Embate sangriento', usos: 22 },
        { nombre: 'Ataque básico', usos: 41 },
      ],
      criticos: 5,
    },
    enemigosDerrotados: [
      { nombre: 'Sombras Corrompidas', cantidad: 8 },
      { nombre: 'Espectros Ancestrales', cantidad: 4 },
    ],
    mastersDerrotados: [{ nombre: 'Sombra del Olvido', epica: 'Velo de Sombras' }],
    jefeDerrotado: true,
    recompensas: {
      creditos: 40,
      productos: [{ nombre: 'Cofre de Bronce', rareza: 'COMUN' }],
      epicas: ['Velo de Sombras'],
      experiencia: 96,
    },
    objetivos: [
      { texto: 'Recorrer las rutas del bosque.', cumplido: true, bonificacion: null },
      { texto: 'Derrotar al Máster si aparece.', cumplido: true, bonificacion: 'Épica obtenida' },
      { texto: 'Terminar sin que la vida baje del 50 %.', cumplido: false },
    ],
  },
  'ejecucion-paso-helado': {
    ejecucionId: 'ejecucion-paso-helado',
    mision: { id: 'paso-helado', nombre: 'Emboscada en el Paso Helado', categoria: 'DESAFIO' },
    resultado: 'FALLO',
    duracionMs: 4 * HORA + 20 * 60_000,
    terminadaEn: iso(-24 * HORA),
    heroe: { nombre: 'Vorn el Filo', prototipo: 'Guerrero Armas', nivel: 1 },
    combate: {
      encuentros: 3,
      danoInfligido: 210,
      danoRecibido: 250,
      turnos: 19,
      habilidadesMasUsadas: [{ nombre: 'Ataque básico', usos: 12 }],
      criticos: 1,
    },
    enemigosDerrotados: [{ nombre: 'Guardianes de Piedra', cantidad: 2 }],
    mastersDerrotados: [],
    jefeDerrotado: false,
    recompensas: { creditos: 0, productos: [], epicas: [], experiencia: 18 },
    objetivos: [{ texto: 'Cruzar el paso.', cumplido: false }],
  },
  'ejecucion-prologo': {
    ejecucionId: 'ejecucion-prologo',
    mision: { id: 'prologo', nombre: 'Prólogo: El Llamado del Nexo', categoria: 'HISTORIA' },
    resultado: 'EXITO',
    duracionMs: 4 * HORA,
    terminadaEn: iso(-5 * 24 * HORA),
    heroe: { nombre: 'Vorn el Filo', prototipo: 'Guerrero Armas', nivel: 1 },
    combate: {
      encuentros: 2,
      danoInfligido: 120,
      danoRecibido: 30,
      turnos: 11,
      habilidadesMasUsadas: [{ nombre: 'Embate sangriento', usos: 4 }],
      criticos: 0,
    },
    enemigosDerrotados: [{ nombre: 'Sombras Corrompidas', cantidad: 2 }],
    mastersDerrotados: [],
    jefeDerrotado: true,
    recompensas: { creditos: 20, productos: [], epicas: [], experiencia: 24 },
    objetivos: [{ texto: 'Responder al llamado.', cumplido: true }],
  },
};

const ACTIVAS = [
  {
    ejecucionId: 'ejecucion-torre',
    misionId: 'torre-de-ceniza',
    nombre: 'Asedio a la Torre de Ceniza',
    categoria: 'DESAFIO',
    heroe: {
      id: 'he000002-1111-4111-8111-111111111111',
      nombre: 'Vorn el Filo',
      prototipo: 'Guerrero Armas',
    },
    iniciadaEn: iso(-3 * HORA),
    terminaEn: iso(5 * HORA),
    progreso: 0.4,
    penalizacion: null,
  },
];

const FUENTE_DE_LABORATORIO = Object.freeze({
  disponible: true,
  async tablero({ categoria, dificultad = '', estado = '', duracion = '', pagina = 0 }) {
    const lista = MISIONES.filter(
      (m) =>
        m.categoria === categoria &&
        (!dificultad || m.dificultad === dificultad) &&
        (!estado || m.estado === estado) &&
        (!duracion || TRAMOS[duracion](m.duracionHoras)),
    );
    const totalPaginas = Math.ceil(lista.length / 16);
    return {
      misiones: lista.slice(pagina * 16, pagina * 16 + 16).map(resumen),
      total: lista.length,
      pagina,
      totalPaginas,
    };
  },
  async destacadas() {
    return MISIONES.filter((m) => m.destacada).map(resumen);
  },
  async detalle(id) {
    const mision = MISIONES.find((m) => m.id === id);
    if (!mision) {
      throw Object.assign(new Error('no existe'), { status: 404 });
    }
    return mision;
  },
  async activas() {
    return ACTIVAS;
  },
  async historial() {
    const completadas = Object.values(REPORTES).map((r) => ({
      ejecucionId: r.ejecucionId,
      misionId: r.mision.id,
      nombre: r.mision.nombre,
      categoria: r.mision.categoria,
      terminadaEn: r.terminadaEn,
      resultado: r.resultado,
      duracionMs: r.duracionMs,
    }));
    return {
      completadas,
      porCategoria: [
        { categoria: 'HISTORIA', completadas: 1, fallidas: 0 },
        { categoria: 'DESAFIO', completadas: 0, fallidas: 1 },
        { categoria: 'EXPLORACION', completadas: 1, fallidas: 0 },
      ],
      mejoresTiempos: [
        { misionId: 'prologo', nombre: 'Prólogo: El Llamado del Nexo', duracionMs: 4 * HORA },
        { misionId: 'bosque-sombrio', nombre: 'Rutas del Bosque Sombrío', duracionMs: 36 * HORA },
      ],
      epicas: [
        { nombre: 'Velo de Sombras', master: 'Sombra del Olvido', obtenidaEn: iso(-2 * 24 * HORA) },
      ],
      cadenas: [{ nombre: 'Crónicas del Templo', completadas: 1, total: 3 }],
    };
  },
  async reporte(ejecucionId) {
    const reporte = REPORTES[ejecucionId];
    if (!reporte) {
      throw Object.assign(new Error('no existe'), { status: 404 });
    }
    return reporte;
  },
  async matricular({ misionId, heroeId }) {
    const mision = MISIONES.find((m) => m.id === misionId);
    return {
      ...ACTIVAS[0],
      ejecucionId: `ejecucion-${misionId}`,
      misionId,
      nombre: mision?.nombre ?? misionId,
      heroe: { ...ACTIVAS[0].heroe, id: heroeId },
    };
  },
  async cancelar() {},
  async marcarFavorita() {},
});

export class MisionesSinAbrir extends Error {}

/** Mismo nombre que la de producción, para que la vista no note el cambio. */
export const FUENTE_SIN_SERVICIO = FUENTE_DE_LABORATORIO;

export function fuenteDeMisiones() {
  return FUENTE_DE_LABORATORIO;
}
