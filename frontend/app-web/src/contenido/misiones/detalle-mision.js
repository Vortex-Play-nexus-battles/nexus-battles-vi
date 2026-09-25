/**
 * Detalle de una misión — UXC-5 (MissionDetail).
 *
 * §7.8.9, «Vista de detalle de misión»: la narrativa completa con su
 * escenario y sus objetivos principales y secundarios; los enemigos, el jefe
 * final con sus estadísticas, la probabilidad de encontrar un Máster y qué
 * épica trae cada uno; la tabla de recompensas (garantizadas, potenciales con
 * su probabilidad, por objetivos secundarios y de primera vez); el
 * configurador de estrategia y los botones de iniciar o repetir, agregar a
 * favoritas y compartir.
 *
 * Todo lo que se pinta viene de la misión: aquí no se calcula ninguna
 * recompensa ni se deduce ninguna probabilidad. Lo que la misión no traiga,
 * no aparece —una sección vacía con un guion sería una afirmación falsa—.
 *
 * @module contenido/misiones/detalle-mision
 */

import { h, vaciar } from '../../comun/ui/dom.js';
import { icono } from '../../comun/ui/icono.js';
import { aviso } from '../../comun/ui/aviso.js';
import { identidadDePrototipo } from '../../comun/ui/juego/prototipos.js';
import { numero } from '../../comun/ui/formato.js';
import { ESCALONES, categoriaDe, textoDeDuracion, textoDeProbabilidad } from './modelo-misiones.js';
import { chipDeCategoria, indicadorDeDificultad, selloDeMision } from './tablon-misiones.js';

/** Estados desde los que se puede mandar a un héroe, y cómo se llama el botón. */
const TEXTO_DE_INICIO = Object.freeze({
  DISPONIBLE: 'Iniciar misión',
  COMPLETADA: 'Repetir misión',
  FALLIDA: 'Reintentar misión',
  ABANDONADA: 'Reintentar misión',
});

/**
 * ¿Se puede mandar a un héroe a esta misión, según el estado que trae?
 *
 * @param {{estado?: string}} mision
 * @returns {boolean}
 */
export function admiteInicio(mision) {
  return Object.hasOwn(TEXTO_DE_INICIO, String(mision?.estado));
}

/**
 * @param {string} titulo
 * @param {string} id
 * @param {Array<Node|null>} hijos
 * @returns {HTMLElement}
 */
function seccion(titulo, id, hijos) {
  return h('section', {
    clase: 'tarjeta mision-detalle__seccion',
    datos: { seccion: id },
    atributos: { 'aria-labelledby': `mision-seccion-${id}` },
    hijos: [
      h('h2', {
        clase: 'mision-detalle__subtitulo',
        texto: titulo,
        atributos: { id: `mision-seccion-${id}` },
      }),
      ...hijos,
    ],
  });
}

/** Párrafos de un texto largo, separados por líneas en blanco. */
function parrafos(texto, clase) {
  return String(texto ?? '')
    .split(/\n\s*\n/)
    .map((trozo) => trozo.trim())
    .filter(Boolean)
    .map((trozo) => h('p', { clase, texto: trozo }));
}

function listaDeObjetivos(titulo, objetivos, iconoDeLista) {
  if (!Array.isArray(objetivos) || objetivos.length === 0) {
    return null;
  }
  return h('div', {
    clase: 'mision-objetivos__grupo',
    hijos: [
      h('h3', { clase: 'mision-objetivos__titulo', texto: titulo }),
      h('ul', {
        clase: 'mision-objetivos__lista',
        hijos: objetivos.map((objetivo) =>
          h('li', {
            hijos: [icono(iconoDeLista, { etiqueta: null }), h('span', { texto: objetivo })],
          }),
        ),
      }),
    ],
  });
}

function tarjetaDeEnemigo(enemigo) {
  return h('li', {
    clase: 'mision-enemigo',
    hijos: [
      h('p', {
        clase: 'mision-enemigo__nombre',
        hijos: [
          h('span', { texto: enemigo.nombre }),
          Number.isFinite(enemigo.cantidad)
            ? h('span', {
                clase: 'mision-enemigo__cantidad',
                texto: `×${enemigo.cantidad}`,
                atributos: { 'aria-label': `${enemigo.cantidad} en total` },
              })
            : null,
        ],
      }),
      enemigo.descripcion
        ? h('p', { clase: 'mision-enemigo__descripcion', texto: enemigo.descripcion })
        : null,
    ],
  });
}

function tarjetaDeJefe(jefe) {
  const identidad = jefe.prototipo ? identidadDePrototipo(jefe.prototipo) : null;
  return h('div', {
    clase: 'mision-jefe',
    hijos: [
      h('div', {
        clase: 'mision-jefe__emblema',
        atributos: { 'aria-hidden': 'true' },
        hijos: [icono(identidad?.icono ?? 'trofeo', { etiqueta: null })],
      }),
      h('div', {
        clase: 'mision-jefe__cuerpo',
        hijos: [
          h('h3', { clase: 'mision-jefe__titulo', texto: 'Jefe final' }),
          h('p', { clase: 'mision-jefe__nombre', texto: jefe.nombre }),
          h('dl', {
            clase: 'mision-jefe__datos',
            hijos: [
              identidad
                ? h('div', {
                    hijos: [h('dt', { texto: 'Prototipo' }), h('dd', { texto: identidad.nombre })],
                  })
                : null,
              Number.isFinite(jefe.vida)
                ? h('div', {
                    hijos: [
                      h('dt', {
                        hijos: [icono('corazon', { etiqueta: null }), h('span', { texto: 'Vida' })],
                      }),
                      h('dd', { texto: numero(jefe.vida) }),
                    ],
                  })
                : null,
            ],
          }),
          jefe.descripcion
            ? h('p', { clase: 'mision-jefe__descripcion', texto: jefe.descripcion })
            : null,
        ],
      }),
    ],
  });
}

function tarjetaDeMaster(master) {
  const identidad = master.prototipo ? identidadDePrototipo(master.prototipo) : null;
  const epica = master.epica ?? {};
  return h('li', {
    clase: 'mision-master',
    hijos: [
      h('p', {
        clase: 'mision-master__nombre',
        hijos: [
          icono(identidad?.icono ?? 'estrella', { etiqueta: null }),
          h('span', { texto: master.nombre }),
          identidad
            ? h('span', { clase: 'mision-master__prototipo', texto: identidad.nombre })
            : null,
        ],
      }),
      epica.nombre
        ? h('div', {
            clase: 'mision-master__epica',
            hijos: [
              h('p', {
                clase: 'mision-master__epica-nombre',
                hijos: [
                  icono('estrella', { etiqueta: null }),
                  h('span', { texto: `Épica: ${epica.nombre}` }),
                ],
              }),
              h('dl', {
                hijos: [
                  epica.efectoGeneral
                    ? h('div', {
                        hijos: [
                          h('dt', { texto: 'Para todos los héroes' }),
                          h('dd', { texto: epica.efectoGeneral }),
                        ],
                      })
                    : null,
                  epica.efectoPotenciado
                    ? h('div', {
                        hijos: [
                          h('dt', {
                            texto: identidad ? `Solo ${identidad.nombre}` : 'Efecto épico',
                          }),
                          h('dd', { texto: epica.efectoPotenciado }),
                        ],
                      })
                    : null,
                ],
              }),
            ],
          })
        : null,
    ],
  });
}

/**
 * La tabla de recompensas. Una fila por recompensa, con cuándo se gana.
 *
 * @param {import('./fuente-misiones.js').Mision} mision
 * @returns {HTMLElement|null}
 */
export function tablaDeRecompensas(mision) {
  const r = mision.recompensas ?? {};
  const grupos = [
    {
      titulo: 'Garantizadas',
      filas: (r.garantizadas ?? []).map((texto) => [texto, 'Al completar la misión']),
    },
    {
      titulo: 'Potenciales',
      filas: (r.potenciales ?? []).map((p) => [
        p.nombre,
        [textoDeProbabilidad(p.probabilidad), p.detalle].filter(Boolean).join(' · ') ||
          'Con probabilidad',
      ]),
    },
    {
      titulo: 'Por objetivos secundarios',
      filas: (r.porObjetivos ?? []).map((b) => [b.recompensa, `Si cumples: ${b.objetivo}`]),
    },
    {
      titulo: 'Primera vez',
      filas: (r.primeraVez ?? []).map((texto) => [texto, 'Solo la primera vez que la completas']),
    },
  ].filter((grupo) => grupo.filas.length > 0);

  if (grupos.length === 0) {
    return null;
  }
  return h('table', {
    clase: 'tabla mision-recompensas',
    hijos: [
      h('caption', { clase: 'solo-lectores', texto: `Recompensas de ${mision.nombre}` }),
      h('thead', {
        hijos: [
          h('tr', {
            hijos: [
              h('th', { texto: 'Recompensa', atributos: { scope: 'col' } }),
              h('th', { texto: 'Cuándo se gana', atributos: { scope: 'col' } }),
            ],
          }),
        ],
      }),
      ...grupos.map((grupo) =>
        h('tbody', {
          hijos: [
            h('tr', {
              clase: 'mision-recompensas__grupo',
              hijos: [
                h('th', {
                  texto: grupo.titulo,
                  atributos: { scope: 'rowgroup', colspan: '2' },
                }),
              ],
            }),
            ...grupo.filas.map(([recompensa, cuando]) =>
              h('tr', {
                hijos: [
                  h('td', {
                    hijos: [icono('cofre', { etiqueta: null }), h('span', { texto: recompensa })],
                  }),
                  h('td', { texto: cuando }),
                ],
              }),
            ),
          ],
        }),
      ),
    ],
  });
}

/**
 * Comparte la dirección de la misión: con la hoja del sistema si la hay, si
 * no copiándola; y si tampoco se puede copiar, la enseña para copiarla a mano.
 *
 * @param {{titulo: string, url: string, zona: HTMLElement, navegador?: Navigator}} opciones
 * @returns {Promise<'compartida'|'copiada'|'a-mano'|'cancelada'>}
 */
export async function compartir({ titulo, url, zona, navegador = globalThis.navigator }) {
  vaciar(zona);
  if (typeof navegador?.share === 'function') {
    try {
      await navegador.share({ title: titulo, url });
      return 'compartida';
    } catch (fallo) {
      if (fallo?.name === 'AbortError') {
        return 'cancelada';
      }
    }
  }
  if (typeof navegador?.clipboard?.writeText === 'function') {
    try {
      await navegador.clipboard.writeText(url);
      zona.append(
        aviso({
          tono: 'exito',
          titulo: 'Enlace copiado',
          detalle: 'Pégalo en el chat o donde quieras.',
        }),
      );
      return 'copiada';
    } catch {
      // Sin permiso para el portapapeles: se enseña para copiarlo a mano.
    }
  }
  const campo = h('input', {
    clase: 'campo__control',
    atributos: { type: 'text', readonly: true, value: url, 'aria-label': 'Enlace de la misión' },
  });
  zona.append(
    h('div', {
      clase: 'campo mision-detalle__enlace',
      hijos: [h('p', { clase: 'campo__pista', texto: 'Copia este enlace:' }), campo],
    }),
  );
  campo.focus();
  campo.select();
  return 'a-mano';
}

/**
 * El detalle.
 *
 * @param {import('./fuente-misiones.js').Mision} mision
 * @param {object} opciones
 * @param {string} opciones.hrefTablon
 * @param {(ejecucionId: string) => string} opciones.hrefReporte
 * @param {string} opciones.hrefEnCurso
 * @param {{elemento: HTMLElement}|null} [opciones.configurador]
 * @param {() => Promise<void>} [opciones.alIniciar] la página confirma y matricula
 * @param {(favorita: boolean) => Promise<void>} [opciones.alMarcarFavorita]
 * @param {string} opciones.urlParaCompartir
 * @returns {{elemento: HTMLElement, habilitarInicio: (listo: boolean) => void,
 *   ocupado: (activo: boolean) => void, zonaAviso: HTMLElement}}
 */
export function detalleDeMision(
  mision,
  {
    hrefTablon,
    hrefReporte,
    hrefEnCurso,
    configurador = null,
    alIniciar = async () => {},
    alMarcarFavorita = async () => {},
    urlParaCompartir,
  },
) {
  const categoria = categoriaDe(mision.categoria);
  const zonaAviso = h('div', { clase: 'mision-detalle__aviso', datos: { zona: 'aviso' } });
  const zonaCompartir = h('div', {
    clase: 'mision-detalle__compartir',
    datos: { zona: 'compartir' },
  });

  /* ------------------------------------------------------------- acciones */

  const puedeIniciar = admiteInicio(mision) && configurador !== null;
  const textoInicio = TEXTO_DE_INICIO[mision.estado] ?? 'Iniciar misión';
  const idMotivoInicio = `mision-${mision.id}-motivo-inicio`;
  const botonIniciar = puedeIniciar
    ? h('button', {
        clase: 'boton boton--primario boton--grande',
        datos: { accion: 'iniciar-mision' },
        atributos: {
          type: 'button',
          'aria-disabled': 'true',
          'aria-describedby': idMotivoInicio,
        },
        hijos: [icono('espada', { etiqueta: null }), h('span', { texto: textoInicio })],
      })
    : null;
  const motivoInicio = puedeIniciar
    ? h('p', {
        clase: 'mision-detalle__motivo-inicio',
        texto: 'Primero elige el héroe y comprueba su estrategia.',
        atributos: { id: idMotivoInicio },
      })
    : null;
  botonIniciar?.addEventListener('click', async () => {
    if (botonIniciar.getAttribute('aria-disabled') === 'true') {
      // Deshabilitado con `aria-disabled` y no con `disabled`: sigue en el
      // orden del teclado y dice por qué no se puede todavía.
      configurador?.elemento.querySelector('input, select, button')?.focus();
      return;
    }
    await alIniciar();
  });

  let favorita = mision.favorita === true;
  const botonFavorita = h('button', {
    clase: 'boton boton--secundario boton--pequeno',
    datos: { accion: 'favorita' },
    atributos: { type: 'button' },
  });
  const pintarFavorita = () => {
    botonFavorita.setAttribute('aria-pressed', String(favorita));
    botonFavorita.replaceChildren(
      icono('estrella', { etiqueta: null }),
      h('span', { texto: favorita ? 'En tus favoritas' : 'Agregar a favoritas' }),
    );
  };
  pintarFavorita();
  botonFavorita.addEventListener('click', async () => {
    const antes = favorita;
    favorita = !antes;
    pintarFavorita();
    try {
      await alMarcarFavorita(favorita);
    } catch (fallo) {
      console.error('No se pudo marcar la misión como favorita', fallo);
      revertirFavorita(antes);
      vaciar(zonaAviso).append(
        aviso({
          tono: 'error',
          titulo: 'No pudimos guardar tu favorita',
          detalle: 'Inténtalo de nuevo en un momento.',
        }),
      );
    }
  });
  function revertirFavorita(valor) {
    favorita = valor;
    pintarFavorita();
  }

  const botonCompartir = h('button', {
    clase: 'boton boton--secundario boton--pequeno',
    datos: { accion: 'compartir' },
    atributos: { type: 'button' },
    hijos: [icono('chat', { etiqueta: null }), h('span', { texto: 'Compartir' })],
  });
  botonCompartir.addEventListener('click', () =>
    compartir({ titulo: mision.nombre, url: urlParaCompartir, zona: zonaCompartir }),
  );

  /* ------------------------------------------------------------- cabecera */

  const datos = [
    ['Dificultad', indicadorDeDificultad(mision.dificultad)],
    ['Duración', textoDeDuracion(mision.duracionHoras)],
    [
      'Nivel recomendado',
      Number.isFinite(mision.nivelRecomendado) ? String(mision.nivelRecomendado) : null,
    ],
    ['Escalón', mision.escalon ? ESCALONES[mision.escalon] : null],
    ['Encuentros', Number.isFinite(mision.encuentros) ? String(mision.encuentros) : null],
  ].filter(([, valor]) => valor);

  const cabecera = h('header', {
    clase: 'tarjeta mision-detalle__cabecera',
    hijos: [
      h('div', {
        clase: 'mision-detalle__arte',
        atributos: { 'aria-hidden': 'true' },
        hijos: [
          mision.imagen
            ? h('img', {
                clase: 'mision-detalle__imagen',
                atributos: { src: mision.imagen, alt: '' },
              })
            : icono(categoria?.icono ?? 'mapa', {
                clase: 'icono mision-detalle__emblema',
                etiqueta: null,
              }),
        ],
      }),
      h('div', {
        clase: 'mision-detalle__identidad',
        hijos: [
          h('div', {
            clase: 'mision-card__chips',
            hijos: [chipDeCategoria(mision.categoria), selloDeMision(mision.estado)],
          }),
          h('h1', {
            clase: 'mision-detalle__nombre',
            texto: mision.nombre,
            atributos: { tabindex: '-1' },
          }),
          mision.descripcionBreve
            ? h('p', { clase: 'mision-detalle__resumen', texto: mision.descripcionBreve })
            : null,
          h('dl', {
            clase: 'mision-detalle__datos',
            hijos: datos.map(([etiqueta, valor]) =>
              h('div', {
                hijos: [
                  h('dt', { texto: etiqueta }),
                  h('dd', {
                    hijos: [typeof valor === 'string' ? h('span', { texto: valor }) : valor],
                  }),
                ],
              }),
            ),
          }),
          mision.estado === 'BLOQUEADA'
            ? h('p', {
                clase: 'mision-detalle__bloqueo',
                hijos: [
                  icono('candado', { etiqueta: null }),
                  h('span', {
                    texto: mision.motivoBloqueo ?? 'Todavía no puedes empezarla.',
                  }),
                ],
              })
            : null,
          Array.isArray(mision.requisitosPrevios) && mision.requisitosPrevios.length > 0
            ? h('p', {
                clase: 'mision-detalle__requisitos',
                texto: `Antes hay que completar: ${mision.requisitosPrevios.join(', ')}.`,
              })
            : null,
          h('div', {
            clase: 'mision-detalle__acciones',
            hijos: [
              puedeIniciar
                ? h('a', {
                    clase: 'boton boton--primario boton--pequeno',
                    texto: textoInicio,
                    datos: { accion: 'ir-a-configurar' },
                    atributos: { href: '#configurar' },
                  })
                : null,
              mision.estado === 'EN_PROGRESO'
                ? h('a', {
                    clase: 'boton boton--primario boton--pequeno',
                    texto: 'Ver progreso',
                    datos: { accion: 'ver-progreso' },
                    atributos: { href: hrefEnCurso },
                  })
                : null,
              mision.ultimaEjecucionId
                ? h('a', {
                    clase: 'boton boton--secundario boton--pequeno',
                    texto: 'Ver el último reporte',
                    datos: { accion: 'ver-reporte' },
                    atributos: { href: hrefReporte(mision.ultimaEjecucionId) },
                  })
                : null,
              botonFavorita,
              botonCompartir,
            ],
          }),
          zonaCompartir,
        ],
      }),
    ],
  });

  /* ------------------------------------------------------------- secciones */

  const narrativa = seccion('Historia', 'narrativa', [
    ...parrafos(mision.narrativa, 'mision-detalle__narrativa'),
    mision.escenario
      ? h('div', {
          clase: 'mision-detalle__escenario',
          hijos: [
            h('h3', { texto: 'Escenario' }),
            ...parrafos(mision.escenario, 'mision-detalle__narrativa'),
          ],
        })
      : null,
  ]);

  const objetivos = mision.objetivos ?? {};
  const bloqueObjetivos =
    (objetivos.principales?.length ?? 0) + (objetivos.secundarios?.length ?? 0) > 0
      ? seccion('Objetivos', 'objetivos', [
          h('div', {
            clase: 'mision-objetivos',
            hijos: [
              listaDeObjetivos('Principales', objetivos.principales, 'objetivo'),
              listaDeObjetivos('Secundarios', objetivos.secundarios, 'estrella'),
            ],
          }),
        ])
      : null;

  const enemigos = Array.isArray(mision.enemigos) ? mision.enemigos : [];
  const bloqueEnemigos =
    enemigos.length > 0 || mision.jefe
      ? seccion('Enemigos', 'enemigos', [
          enemigos.length > 0
            ? h('ul', { clase: 'mision-enemigos', hijos: enemigos.map(tarjetaDeEnemigo) })
            : null,
          mision.jefe ? tarjetaDeJefe(mision.jefe) : null,
        ])
      : null;

  const masters = Array.isArray(mision.masters) ? mision.masters : [];
  const probabilidad = textoDeProbabilidad(mision.probabilidadMaster);
  const bloqueMaster =
    masters.length > 0 || probabilidad
      ? seccion('Enemigo Máster', 'master', [
          probabilidad
            ? h('p', {
                clase: 'mision-master__probabilidad',
                texto: `Probabilidad de que aparezca: ${probabilidad}. Si lo derrotas, su épica es tuya.`,
              })
            : null,
          masters.length > 0
            ? h('ul', { clase: 'mision-masters', hijos: masters.map(tarjetaDeMaster) })
            : null,
        ])
      : null;

  const tabla = tablaDeRecompensas(mision);
  const bloqueRecompensas = tabla ? seccion('Recompensas', 'recompensas', [tabla]) : null;

  let bloqueConfigurar = null;
  if (puedeIniciar) {
    const duracion = textoDeDuracion(mision.duracionHoras);
    bloqueConfigurar = h('section', {
      clase: 'mision-detalle__configurar',
      atributos: { id: 'configurar', 'aria-labelledby': 'mision-seccion-configurar' },
      hijos: [
        h('h2', {
          clase: 'mision-detalle__subtitulo mision-detalle__subtitulo--atmosfera',
          texto: 'Prepara la misión',
          atributos: { id: 'mision-seccion-configurar', tabindex: '-1' },
        }),
        configurador.elemento,
        h('div', {
          clase: 'tarjeta mision-detalle__inicio',
          hijos: [
            h('p', {
              clase: 'mision-detalle__advertencia',
              hijos: [
                icono('candado', { etiqueta: null }),
                h('span', {
                  texto: `Mientras dure la misión${duracion ? ` (${duracion})` : ''}, tu héroe no podrá jugar en línea, entrar en torneos ni cambiar su equipamiento.`,
                }),
              ],
            }),
            botonIniciar,
            motivoInicio,
          ],
        }),
      ],
    });
  } else if (mision.estado === 'EN_PROGRESO') {
    bloqueConfigurar = seccion('Tu héroe ya está en esta misión', 'en-curso', [
      h('p', { texto: 'Mira cuánto le queda y cómo va en «En curso».' }),
      h('a', {
        clase: 'boton boton--primario boton--pequeno',
        texto: 'Ver progreso',
        atributos: { href: hrefEnCurso },
      }),
    ]);
  }

  const elemento = h('article', {
    clase: 'mision-detalle',
    datos: { mision: mision.id, estado: String(mision.estado ?? '').toLowerCase() },
    hijos: [
      h('nav', {
        clase: 'mision-detalle__migas',
        atributos: { 'aria-label': 'Volver' },
        hijos: [
          h('a', {
            clase: 'mision-detalle__volver',
            atributos: { href: hrefTablon },
            hijos: [
              icono('chevron', { etiqueta: null }),
              h('span', { texto: 'Tablón de misiones' }),
            ],
          }),
        ],
      }),
      zonaAviso,
      cabecera,
      h('div', {
        clase: 'mision-detalle__cuerpo',
        hijos: [narrativa, bloqueObjetivos, bloqueEnemigos, bloqueMaster, bloqueRecompensas],
      }),
      bloqueConfigurar,
    ],
  });

  return {
    elemento,
    zonaAviso,
    /**
     * Mientras se matricula (no mientras se confirma): el botón espera.
     *
     * @param {boolean} activo
     */
    ocupado(activo) {
      if (!botonIniciar) {
        return;
      }
      if (activo) {
        botonIniciar.setAttribute('aria-busy', 'true');
      } else {
        botonIniciar.removeAttribute('aria-busy');
      }
    },
    /**
     * @param {boolean} listo hay héroe y estrategia comprobada
     */
    habilitarInicio(listo) {
      if (!botonIniciar) {
        return;
      }
      botonIniciar.setAttribute('aria-disabled', String(!listo));
      motivoInicio.textContent = listo
        ? 'Tu héroe y su estrategia están listos.'
        : 'Primero elige el héroe y comprueba su estrategia.';
    },
  };
}
