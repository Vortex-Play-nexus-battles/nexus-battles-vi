/**
 * Reporte e historial de misiones — UXC-5 (MissionReport + MissionHistory).
 *
 * §7.8.8. El reporte de una misión terminada tiene cinco bloques: resumen
 * general (misión, tiempo, resultado, héroe y nivel), estadísticas de combate,
 * enemigos derrotados, recompensas obtenidas y objetivos cumplidos. El
 * historial reúne las misiones completadas con su fecha, las cifras por
 * categoría, los mejores tiempos, la colección de épicas de Máster y el
 * progreso en las cadenas narrativas.
 *
 * Las cifras se pintan como llegan: el reporte lo escribe la simulación y aquí
 * no se suma, no se redondea y no se deduce nada.
 *
 * @module contenido/misiones/reporte-mision
 */

import { h } from '../../comun/ui/dom.js';
import { icono } from '../../comun/ui/icono.js';
import { distintivo, distintivoDeRareza } from '../../comun/ui/distintivo.js';
import { creditos, fecha, numero } from '../../comun/ui/formato.js';
import { tarjetaDeCifra } from '../../comun/ui/tarjeta.js';
import { estadoVacio } from '../../comun/ui/estado-vista.js';
import { identidadDePrototipo } from '../../comun/ui/juego/prototipos.js';
import { CATEGORIAS, categoriaDe, textoDeTiempo } from './modelo-misiones.js';
import { chipDeCategoria } from './tablon-misiones.js';

/**
 * «Éxito» o «Fallo», con icono y palabra.
 *
 * @param {'EXITO'|'FALLO'} resultado
 * @returns {HTMLElement}
 */
export function selloDeResultado(resultado) {
  const exito = resultado === 'EXITO';
  const sello = distintivo(exito ? 'Éxito' : 'Fallo', exito ? 'activo' : 'suspendido');
  sello.classList.add('mision-estado');
  sello.prepend(
    icono(exito ? 'check' : 'alerta', { etiqueta: null, clase: 'icono mision-estado__icono' }),
  );
  return sello;
}

function bloque(titulo, id, hijos) {
  return h('section', {
    clase: 'tarjeta mision-reporte__bloque',
    datos: { bloque: id },
    atributos: { 'aria-labelledby': `reporte-${id}` },
    hijos: [
      h('h2', {
        clase: 'mision-detalle__subtitulo',
        texto: titulo,
        atributos: { id: `reporte-${id}` },
      }),
      ...hijos,
    ],
  });
}

/**
 * El reporte de una misión terminada.
 *
 * @param {import('./fuente-misiones.js').ReporteMision} reporte
 * @param {{hrefTablon: string, hrefHistorial: string, hrefDe: Function}} rutas
 * @returns {HTMLElement}
 */
export function reporteDeMision(reporte, { hrefTablon, hrefHistorial, hrefDe }) {
  const heroe = reporte.heroe ?? {};
  const identidad = heroe.prototipo ? identidadDePrototipo(heroe.prototipo) : null;
  const combate = reporte.combate ?? {};
  const recompensas = reporte.recompensas ?? {};

  const resumen = h('header', {
    clase: 'tarjeta mision-reporte__resumen',
    hijos: [
      h('div', {
        clase: 'mision-card__chips',
        hijos: [chipDeCategoria(reporte.mision?.categoria), selloDeResultado(reporte.resultado)],
      }),
      h('p', { clase: 'mision-reporte__antetitulo', texto: 'Reporte de misión' }),
      h('h1', {
        clase: 'mision-detalle__nombre',
        texto: reporte.mision?.nombre ?? 'Misión',
        atributos: { tabindex: '-1' },
      }),
      h('dl', {
        clase: 'mision-detalle__datos',
        hijos: [
          ['Resultado', reporte.resultado === 'EXITO' ? 'Éxito' : 'Fallo'],
          ['Tiempo total', textoDeTiempo(reporte.duracionMs)],
          ['Terminó', reporte.terminadaEn ? fecha(reporte.terminadaEn) : null],
          [
            'Héroe',
            [
              heroe.nombre,
              identidad?.nombre,
              Number.isFinite(heroe.nivel) ? `nivel ${heroe.nivel}` : null,
            ]
              .filter(Boolean)
              .join(' · '),
          ],
        ]
          .filter(([, valor]) => valor)
          .map(([etiqueta, valor]) =>
            h('div', { hijos: [h('dt', { texto: etiqueta }), h('dd', { texto: valor })] }),
          ),
      }),
      h('div', {
        clase: 'mision-detalle__acciones',
        hijos: [
          reporte.mision?.id
            ? h('a', {
                clase: 'boton boton--primario boton--pequeno',
                texto: 'Ver la misión',
                atributos: { href: hrefDe({ id: reporte.mision.id }) },
              })
            : null,
          h('a', {
            clase: 'boton boton--secundario boton--pequeno',
            texto: 'Ir al historial',
            atributos: { href: hrefHistorial },
          }),
        ],
      }),
    ],
  });

  const cifrasDeCombate = [
    ['Encuentros', combate.encuentros],
    ['Daño infligido', combate.danoInfligido],
    ['Daño recibido', combate.danoRecibido],
    ['Turnos', combate.turnos],
    ['Críticos', combate.criticos],
  ].filter(([, valor]) => Number.isFinite(valor));
  const habilidades = Array.isArray(combate.habilidadesMasUsadas)
    ? combate.habilidadesMasUsadas
    : [];

  const bloqueCombate =
    cifrasDeCombate.length > 0 || habilidades.length > 0
      ? bloque('Estadísticas de combate', 'combate', [
          h('div', {
            clase: 'mision-reporte__cifras',
            hijos: cifrasDeCombate.map(([etiqueta, valor]) =>
              tarjetaDeCifra({ etiqueta, valor: numero(valor) }),
            ),
          }),
          habilidades.length > 0
            ? h('div', {
                hijos: [
                  h('h3', { clase: 'mision-objetivos__titulo', texto: 'Habilidades más usadas' }),
                  h('ol', {
                    clase: 'mision-reporte__habilidades',
                    hijos: habilidades.map((habilidad) =>
                      h('li', {
                        hijos: [
                          h('span', { texto: habilidad.nombre }),
                          h('span', {
                            clase: 'mision-reporte__usos',
                            texto:
                              habilidad.usos === 1 ? '1 vez' : `${numero(habilidad.usos)} veces`,
                          }),
                        ],
                      }),
                    ),
                  }),
                ],
              })
            : null,
        ])
      : null;

  const derrotados = Array.isArray(reporte.enemigosDerrotados) ? reporte.enemigosDerrotados : [];
  const masters = Array.isArray(reporte.mastersDerrotados) ? reporte.mastersDerrotados : [];
  const bloqueEnemigos = bloque('Enemigos', 'enemigos', [
    derrotados.length > 0
      ? h('ul', {
          clase: 'mision-enemigos',
          hijos: derrotados.map((enemigo) =>
            h('li', {
              clase: 'mision-enemigo',
              hijos: [
                h('p', {
                  clase: 'mision-enemigo__nombre',
                  hijos: [
                    h('span', { texto: enemigo.nombre }),
                    h('span', {
                      clase: 'mision-enemigo__cantidad',
                      texto: `×${enemigo.cantidad}`,
                      atributos: { 'aria-label': `${enemigo.cantidad} derrotados` },
                    }),
                  ],
                }),
              ],
            }),
          ),
        })
      : h('p', { texto: 'No derrotó a ningún enemigo.' }),
    h('p', {
      clase: 'mision-reporte__jefe',
      hijos: [
        icono(reporte.jefeDerrotado ? 'trofeo' : 'alerta', { etiqueta: null }),
        h('span', {
          texto: reporte.jefeDerrotado ? 'Derrotó al jefe final.' : 'El jefe final no cayó.',
        }),
      ],
    }),
    masters.length > 0
      ? h('ul', {
          clase: 'mision-masters',
          hijos: masters.map((master) =>
            h('li', {
              clase: 'mision-master',
              hijos: [
                h('p', {
                  clase: 'mision-master__nombre',
                  hijos: [
                    icono('estrella', { etiqueta: null }),
                    h('span', { texto: `Máster derrotado: ${master.nombre}` }),
                  ],
                }),
                master.epica ? h('p', { texto: `Épica obtenida: ${master.epica}` }) : null,
              ],
            }),
          ),
        })
      : null,
  ]);

  const productos = Array.isArray(recompensas.productos) ? recompensas.productos : [];
  const epicas = Array.isArray(recompensas.epicas) ? recompensas.epicas : [];
  const bloqueRecompensas = bloque('Recompensas obtenidas', 'recompensas', [
    h('div', {
      clase: 'mision-reporte__cifras',
      hijos: [
        Number.isFinite(recompensas.creditos)
          ? tarjetaDeCifra({ etiqueta: 'Créditos', valor: creditos(recompensas.creditos) })
          : null,
        Number.isFinite(recompensas.experiencia)
          ? tarjetaDeCifra({ etiqueta: 'Experiencia', valor: numero(recompensas.experiencia) })
          : null,
      ],
    }),
    productos.length > 0
      ? h('ul', {
          clase: 'mision-reporte__productos',
          atributos: { 'aria-label': 'Productos obtenidos' },
          hijos: productos.map((producto) =>
            h('li', {
              hijos: [
                icono('cofre', { etiqueta: null }),
                h('span', { texto: producto.nombre }),
                distintivoDeRareza(producto.rareza),
              ],
            }),
          ),
        })
      : null,
    epicas.length > 0
      ? h('p', {
          clase: 'mision-reporte__epicas',
          hijos: [
            icono('estrella', { etiqueta: null }),
            h('span', { texto: `Épicas: ${epicas.join(', ')}` }),
          ],
        })
      : null,
  ]);

  const objetivos = Array.isArray(reporte.objetivos) ? reporte.objetivos : [];
  const bloqueObjetivos =
    objetivos.length > 0
      ? bloque('Objetivos', 'objetivos', [
          h('ul', {
            clase: 'mision-objetivos__lista mision-reporte__objetivos',
            hijos: objetivos.map((objetivo) =>
              h('li', {
                datos: { cumplido: String(objetivo.cumplido === true) },
                hijos: [
                  icono(objetivo.cumplido ? 'check' : 'cerrar', { etiqueta: null }),
                  h('span', {
                    texto: `${objetivo.cumplido ? 'Cumplido' : 'No cumplido'}: ${objetivo.texto}`,
                  }),
                  objetivo.cumplido && objetivo.bonificacion
                    ? h('span', {
                        clase: 'mision-reporte__bonificacion',
                        texto: objetivo.bonificacion,
                      })
                    : null,
                ],
              }),
            ),
          }),
        ])
      : null;

  return h('article', {
    clase: 'mision-reporte',
    datos: { ejecucion: reporte.ejecucionId, resultado: String(reporte.resultado).toLowerCase() },
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
      resumen,
      h('div', {
        clase: 'mision-detalle__cuerpo',
        hijos: [bloqueCombate, bloqueEnemigos, bloqueRecompensas, bloqueObjetivos],
      }),
    ],
  });
}

/**
 * El historial.
 *
 * @param {import('./fuente-misiones.js').HistorialDeMisiones} historial
 * @param {{hrefReporte: Function, hrefTablon: string}} rutas
 * @returns {HTMLElement}
 */
export function historialDeMisiones(historial, { hrefReporte, hrefTablon }) {
  const completadas = Array.isArray(historial?.completadas) ? historial.completadas : [];
  if (completadas.length === 0) {
    return estadoVacio({
      titulo: 'Todavía no has terminado ninguna misión',
      detalle:
        'Cada misión que termines, con éxito o sin él, queda aquí con su reporte, sus mejores tiempos y las épicas que ganes.',
      accion: { texto: 'Elegir una misión', href: hrefTablon },
    });
  }

  const porCategoria = Array.isArray(historial.porCategoria) ? historial.porCategoria : [];
  const cifras = h('div', {
    clase: 'mision-reporte__cifras',
    hijos: CATEGORIAS.map((categoria) => {
      const dato = porCategoria.find((c) => c.categoria === categoria.id);
      return tarjetaDeCifra({
        etiqueta: categoria.etiqueta,
        valor: numero(dato?.completadas ?? 0),
        detalle:
          dato && dato.fallidas > 0
            ? `${dato.fallidas === 1 ? '1 fallida' : `${dato.fallidas} fallidas`}`
            : 'completadas',
      });
    }),
  });

  const tabla = h('table', {
    clase: 'tabla mision-historial__tabla',
    hijos: [
      h('caption', { clase: 'solo-lectores', texto: 'Misiones terminadas' }),
      h('thead', {
        hijos: [
          h('tr', {
            hijos: ['Misión', 'Categoría', 'Terminó', 'Resultado', 'Tiempo', 'Reporte'].map((t) =>
              h('th', { texto: t, atributos: { scope: 'col' } }),
            ),
          }),
        ],
      }),
      h('tbody', {
        hijos: completadas.map((fila) =>
          h('tr', {
            hijos: [
              h('th', { texto: fila.nombre, atributos: { scope: 'row' } }),
              h('td', { texto: categoriaDe(fila.categoria)?.etiqueta ?? '—' }),
              h('td', { texto: fecha(fila.terminadaEn) }),
              h('td', { hijos: [selloDeResultado(fila.resultado)] }),
              h('td', { texto: textoDeTiempo(fila.duracionMs) ?? '—' }),
              h('td', {
                hijos: [
                  h('a', {
                    texto: 'Ver reporte',
                    atributos: {
                      href: hrefReporte(fila.ejecucionId),
                      'aria-label': `Ver el reporte de ${fila.nombre} (${fecha(fila.terminadaEn)})`,
                    },
                  }),
                ],
              }),
            ],
          }),
        ),
      }),
    ],
  });

  const mejores = Array.isArray(historial.mejoresTiempos) ? historial.mejoresTiempos : [];
  const epicas = Array.isArray(historial.epicas) ? historial.epicas : [];
  const cadenas = Array.isArray(historial.cadenas) ? historial.cadenas : [];

  return h('div', {
    clase: 'mision-historial pila',
    hijos: [
      h('section', {
        clase: 'tarjeta mision-reporte__bloque',
        atributos: { 'aria-labelledby': 'historial-resumen' },
        hijos: [
          h('h2', {
            clase: 'mision-detalle__subtitulo',
            texto: 'Por categoría',
            atributos: { id: 'historial-resumen' },
          }),
          cifras,
        ],
      }),
      h('section', {
        clase: 'tarjeta mision-reporte__bloque',
        atributos: { 'aria-labelledby': 'historial-terminadas' },
        hijos: [
          h('h2', {
            clase: 'mision-detalle__subtitulo',
            texto: 'Misiones terminadas',
            atributos: { id: 'historial-terminadas' },
          }),
          // A 375 px la tabla no cabe: se desplaza dentro de su caja, que
          // entonces tiene que poder recibir el foco para moverla con teclado.
          h('div', {
            clase: 'mision-historial__desplazable',
            atributos: {
              tabindex: '0',
              role: 'region',
              'aria-label': 'Tabla de misiones terminadas',
            },
            hijos: [tabla],
          }),
        ],
      }),
      h('div', {
        clase: 'mision-historial__laterales',
        hijos: [
          mejores.length > 0
            ? h('section', {
                clase: 'tarjeta mision-reporte__bloque',
                atributos: { 'aria-labelledby': 'historial-tiempos' },
                hijos: [
                  h('h2', {
                    clase: 'mision-detalle__subtitulo',
                    texto: 'Mejores tiempos',
                    atributos: { id: 'historial-tiempos' },
                  }),
                  h('ol', {
                    clase: 'mision-reporte__habilidades',
                    hijos: mejores.map((m) =>
                      h('li', {
                        hijos: [
                          h('span', { texto: m.nombre }),
                          h('span', {
                            clase: 'mision-reporte__usos',
                            texto: textoDeTiempo(m.duracionMs),
                          }),
                        ],
                      }),
                    ),
                  }),
                ],
              })
            : null,
          h('section', {
            clase: 'tarjeta mision-reporte__bloque',
            atributos: { 'aria-labelledby': 'historial-epicas' },
            hijos: [
              h('h2', {
                clase: 'mision-detalle__subtitulo',
                texto: 'Épicas de Máster',
                atributos: { id: 'historial-epicas' },
              }),
              epicas.length > 0
                ? h('ul', {
                    clase: 'mision-masters',
                    hijos: epicas.map((epica) =>
                      h('li', {
                        clase: 'mision-master',
                        hijos: [
                          h('p', {
                            clase: 'mision-master__nombre',
                            hijos: [
                              icono('estrella', { etiqueta: null }),
                              h('span', { texto: epica.nombre }),
                            ],
                          }),
                          h('p', {
                            clase: 't-meta',
                            texto: `De ${epica.master}, el ${fecha(epica.obtenidaEn)}`,
                          }),
                        ],
                      }),
                    ),
                  })
                : h('p', {
                    texto:
                      'Aún no has derrotado a ningún Máster. Aparecen al azar durante las misiones.',
                  }),
            ],
          }),
          cadenas.length > 0
            ? h('section', {
                clase: 'tarjeta mision-reporte__bloque',
                atributos: { 'aria-labelledby': 'historial-cadenas' },
                hijos: [
                  h('h2', {
                    clase: 'mision-detalle__subtitulo',
                    texto: 'Progreso de la historia',
                    atributos: { id: 'historial-cadenas' },
                  }),
                  h('ul', {
                    clase: 'mision-historial__cadenas',
                    hijos: cadenas.map((cadena) =>
                      h('li', {
                        hijos: [
                          h('span', { texto: cadena.nombre }),
                          h('span', {
                            clase: 'mision-reporte__usos',
                            texto: `${cadena.completadas} de ${cadena.total}`,
                          }),
                        ],
                      }),
                    ),
                  }),
                ],
              })
            : null,
        ],
      }),
    ],
  });
}
