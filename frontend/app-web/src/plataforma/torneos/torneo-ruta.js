/**
 * El torneo como una ruta — revisión del modo jugador del 6-oct, punto 24.
 *
 * «El módulo de torneos se ve muy básico… quiero algo parecido a misiones…
 * Cuando uno le da a ver torneo ahí mismo se despliega de forma sencilla y
 * básica, algo más como una ruta y más llamativo».
 *
 * El detalle deja de desplegarse debajo del listado: es su propia vista
 * (`?torneo=<id>`), con su portada, y lo que había en él —tu equipo, tu
 * próximo encuentro, el árbol, tu camino y el premio— se ordena como los
 * hitos de un recorrido:
 *
 *   1. Cómo se juega       — el formato, la inscripción y los cupos
 *   2. Mi equipo           — registrarlo, inscribirlo, o cómo va
 *   3. Próximo encuentro   — contra quién y cómo jugarlo
 *   4. El árbol            — la doble eliminación, la transmisión y los equipos
 *   5. Mi camino           — lo que ya jugó tu equipo
 *   6. Resultado y premio  — el campeón y lo que se lleva
 *
 * Cada hito dice si es **donde estás** (`aria-current="step"`), si ya tiene
 * algo que enseñar o si todavía no: el árbol no existe hasta que se cierran
 * las inscripciones y el resultado, hasta la gran final. Con palabras además
 * del color.
 *
 * Aquí solo hay presentación: el contenido de cada hito lo construye
 * `torneos.js` con los datos del contrato (torneos.yaml 1.2.0) y nada se
 * inventa — ni marcadores, ni fechas, ni equipos.
 *
 * @module plataforma/torneos/torneo-ruta
 */

import { h } from '../../comun/ui/dom.js';
import { fechaHora } from '../../comun/ui/formato.js';
import { icono } from '../../comun/ui/icono.js';

/** Los hitos de la ruta, en su orden de lectura. */
export const HITOS_DEL_TORNEO = Object.freeze([
  { id: 'descripcion', titulo: 'Cómo se juega', icono: 'mapa' },
  { id: 'equipo', titulo: 'Mi equipo', icono: 'usuarios' },
  { id: 'proximo', titulo: 'Próximo encuentro', icono: 'espada' },
  { id: 'arbol', titulo: 'El árbol', icono: 'bandera' },
  { id: 'camino', titulo: 'Mi camino', icono: 'objetivo' },
  { id: 'resultado', titulo: 'Resultado y premio', icono: 'trofeo' },
]);

/** Lo que se lee junto al hito, además de su marca. */
export const ESTADOS_DEL_HITO = Object.freeze({
  actual: 'Estás aquí',
  disponible: null,
  pendiente: 'Pendiente',
});

const TERMINADO = new Set(['FINALIZADO', 'CANCELADO']);

/**
 * Si el equipo de quien mira sigue jugando este torneo.
 *
 * @param {{estado: string}} torneo
 * @param {{inscrito?: boolean, eliminado?: boolean}|null} equipo
 * @returns {boolean}
 */
function sigueEnJuego(torneo, equipo) {
  return torneo.estado === 'EN_CURSO' && Boolean(equipo?.inscrito) && !equipo.eliminado;
}

/**
 * El hito en el que está quien mira, según la fase del torneo y su equipo.
 *
 * @param {{estado: string}} torneo
 * @param {{inscrito?: boolean, eliminado?: boolean}|null} equipo el de quien mira
 * @returns {string} id de `HITOS_DEL_TORNEO`
 */
export function hitoActual(torneo, equipo) {
  if (TERMINADO.has(torneo.estado)) {
    return 'resultado';
  }
  if (torneo.estado === 'INSCRIPCIONES_ABIERTAS') {
    return 'equipo';
  }
  return sigueEnJuego(torneo, equipo) ? 'proximo' : 'arbol';
}

/**
 * El estado de cada hito: `actual` (uno), `disponible` si ya tiene algo que
 * enseñar, `pendiente` si todavía no.
 *
 * @param {{estado: string, encuentros?: Array<object>}} torneo
 * @param {{id: string, inscrito?: boolean, eliminado?: boolean}|null} equipo
 * @returns {Record<string, 'actual'|'disponible'|'pendiente'>}
 */
export function estadosDeLaRuta(torneo, equipo) {
  const encuentros = torneo.encuentros ?? [];
  const jugo =
    Boolean(equipo) &&
    encuentros.some(
      (e) => e.estado === 'JUGADO' && (e.equipoA === equipo.id || e.equipoB === equipo.id),
    );
  const estados = {
    descripcion: 'disponible',
    equipo: 'disponible',
    proximo: sigueEnJuego(torneo, equipo) ? 'disponible' : 'pendiente',
    arbol: encuentros.length > 0 ? 'disponible' : 'pendiente',
    camino: jugo ? 'disponible' : 'pendiente',
    resultado: TERMINADO.has(torneo.estado) ? 'disponible' : 'pendiente',
  };
  estados[hitoActual(torneo, equipo)] = 'actual';
  return estados;
}

/**
 * La ruta: una lista ordenada de hitos, cada uno con su número, su estado y
 * su contenido. Los títulos son `h2`: la portada lleva el `h1` del torneo.
 *
 * @param {{nombre: string, estado: string, encuentros?: Array<object>}} torneo
 * @param {object|null} equipo el de quien mira
 * @param {Record<string, Array<Node|null|undefined>>} contenidos por id de hito
 * @param {{zona?: string|null}} [opciones] `data-zona` de la lista (la de
 *   «mi-torneo» cuando quien mira tiene equipo)
 * @returns {HTMLOListElement}
 */
export function rutaDelTorneo(torneo, equipo, contenidos, { zona = null } = {}) {
  const estados = estadosDeLaRuta(torneo, equipo);
  return h('ol', {
    clase: 'torneo-ruta',
    datos: zona ? { zona } : {},
    atributos: { 'aria-label': `La ruta de ${torneo.nombre}` },
    hijos: HITOS_DEL_TORNEO.map((hito, indice) => {
      const estado = estados[hito.id];
      const idTitulo = `torneo-hito-${hito.id}`;
      const etiqueta = ESTADOS_DEL_HITO[estado];
      return h('li', {
        clase: 'torneo-ruta__hito',
        datos: { hito: hito.id, estado },
        atributos: estado === 'actual' ? { 'aria-current': 'step' } : {},
        hijos: [
          h('span', {
            clase: 'torneo-ruta__marca',
            atributos: { 'aria-hidden': 'true' },
            hijos: [
              icono(hito.icono, { etiqueta: null, clase: 'icono torneo-ruta__icono' }),
              h('span', { clase: 'torneo-ruta__numero', texto: String(indice + 1) }),
            ],
          }),
          h('section', {
            clase: 'tarjeta torneo-ruta__cuerpo',
            atributos: { 'aria-labelledby': idTitulo },
            hijos: [
              h('div', {
                clase: 'torneo-ruta__cabecera',
                hijos: [
                  h('h2', {
                    clase: 'torneo-ruta__titulo',
                    texto: hito.titulo,
                    atributos: { id: idTitulo },
                  }),
                  etiqueta
                    ? h('span', {
                        clase: `torneo-ruta__estado torneo-ruta__estado--${estado}`,
                        texto: etiqueta,
                      })
                    : null,
                ],
              }),
              ...(contenidos[hito.id] ?? []),
            ],
          }),
        ],
      });
    }),
  });
}

/** Una fila `dt`/`dd` dentro de un `div`, para las listas de datos. */
function par(etiqueta, valor, iconoDelDato = null) {
  return h('div', {
    hijos: [
      h('dt', { texto: etiqueta }),
      h('dd', {
        hijos: [
          iconoDelDato ? icono(iconoDelDato, { etiqueta: null }) : null,
          h('span', { texto: valor }),
        ],
      }),
    ],
  });
}

/** El costo de inscripción en palabras: «Gratis» o «200 créditos». */
export function textoDeInscripcion(torneo) {
  return torneo.costoInscripcion > 0 ? `${torneo.costoInscripcion} créditos` : 'Gratis';
}

/**
 * Los equipos inscritos sobre los cupos, como barra con su cifra escrita
 * (`progressbar` con su nombre, igual que el progreso de una misión).
 *
 * @param {{nombre: string, equiposInscritos: number, cupos: number}} torneo
 * @returns {HTMLElement}
 */
export function cuposDelTorneo(torneo) {
  const cupos = Math.max(Number(torneo.cupos) || 0, 1);
  const inscritos = Math.min(Math.max(Number(torneo.equiposInscritos) || 0, 0), cupos);
  const relleno = h('div', { clase: 'progreso__relleno' });
  relleno.style.width = `${Math.round((inscritos / cupos) * 100)}%`;
  return h('div', {
    clase: 'progreso torneo-cupos',
    datos: { zona: 'cupos' },
    hijos: [
      h('div', {
        clase: 'progreso__cabecera',
        hijos: [
          h('span', { clase: 'progreso__etiqueta', texto: 'Equipos inscritos' }),
          h('span', { clase: 'progreso__valor', texto: `${inscritos} de ${cupos}` }),
        ],
      }),
      h('div', {
        clase: 'progreso__riel',
        atributos: {
          role: 'progressbar',
          'aria-valuemin': '0',
          'aria-valuemax': String(cupos),
          'aria-valuenow': String(inscritos),
          'aria-valuetext': `${inscritos} de ${cupos} equipos`,
          'aria-label': `Equipos inscritos en ${torneo.nombre}`,
        },
        hijos: [relleno],
      }),
    ],
  });
}

/**
 * «Cómo se juega»: el formato del torneo (RF-TOR-001 y RF-TOR-004) y lo que
 * cuesta y cuándo cierra, con los datos del propio torneo.
 *
 * @param {{estado: string, costoInscripcion: number, equiposInscritos: number, cupos: number,
 *   inscripcionesCierranEn?: string}} torneo
 * @returns {HTMLElement}
 */
export function descripcionDelTorneo(torneo) {
  const datos = [
    par('Formato', 'Doble eliminación', 'bandera'),
    par('Equipos', `${torneo.cupos} equipos de dos`, 'usuarios'),
    par('Inscripción', textoDeInscripcion(torneo), 'moneda'),
  ];
  if (torneo.estado === 'INSCRIPCIONES_ABIERTAS' && torneo.inscripcionesCierranEn) {
    datos.push(par('Inscripciones hasta', fechaHora(torneo.inscripcionesCierranEn), 'reloj'));
  }
  return h('div', {
    clase: 'torneo-ruta__descripcion pila pila--ajustada',
    datos: { zona: 'como-se-juega' },
    hijos: [
      h('p', {
        clase: 't-cuerpo',
        // RF-TOR-004: la gran final es un solo encuentro, sin revancha (ver
        // el javadoc de `Arbol`); por eso no se dice «hasta perder dos».
        texto:
          'Quien pierde un encuentro baja a la llave de segunda oportunidad; quien vuelve a perder queda fuera. El ganador de cada llave juega la gran final, a un solo encuentro.',
      }),
      h('dl', { clase: 'torneo-ruta__datos', hijos: datos }),
    ],
  });
}

/**
 * El duelo del próximo encuentro: tu equipo contra el rival, para verlo de un
 * vistazo. Es un adorno: la frase «Contra …» del bloque ya lo dice, así que
 * va oculto a los lectores de pantalla para no leerlo dos veces.
 *
 * @param {string} tuyo nombre de tu equipo
 * @param {string} rival nombre del rival («Rival por decidir» si no lo hay)
 * @returns {HTMLElement}
 */
export function dueloDelEncuentro(tuyo, rival) {
  const lado = (nombre, clase) =>
    h('span', {
      clase: `torneo-duelo__lado ${clase}`,
      hijos: [
        h('span', {
          clase: 'torneo-duelo__inicial',
          texto: (nombre || '?').trim().charAt(0).toUpperCase(),
        }),
        h('span', { clase: 'torneo-duelo__nombre', texto: nombre }),
      ],
    });
  return h('div', {
    clase: 'torneo-duelo',
    atributos: { 'aria-hidden': 'true' },
    hijos: [
      lado(tuyo, 'torneo-duelo__lado--tuyo'),
      h('span', { clase: 'torneo-duelo__vs', texto: 'VS' }),
      lado(rival, 'torneo-duelo__lado--rival'),
    ],
  });
}
