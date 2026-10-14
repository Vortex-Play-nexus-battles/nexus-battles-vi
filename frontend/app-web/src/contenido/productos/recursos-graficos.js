/**
 * Recursos gráficos del catálogo — HU-PRD-009, §7.2.3 «gestionar recursos
 * gráficos (imágenes, iconos, animaciones)», productos.yaml 1.10.0.
 *
 * Tres piezas que comparten la forma de enseñar un recurso:
 *
 *   - `selectorDeRecurso`: el campo «Imagen del producto» del alta y de la
 *     ficha. Se sube un archivo o se elige uno de la biblioteca; el valor es la
 *     `url` del recurso, que es lo que el servicio guarda en `imagen`. Sustituye
 *     al campo de texto «Referencia de imagen» (informe de revisión, punto 17),
 *     donde había que escribir una ruta y no se podía subir nada.
 *   - `abrirBibliotecaParaElegir`: el diálogo con la biblioteca, para elegir.
 *   - `montarBibliotecaDeRecursos`: la biblioteca en el panel del catálogo,
 *     para subir, filtrar, ver dónde se usa cada recurso y retirarlo.
 *
 * El formato, el peso y las medidas los decide el servicio por los bytes del
 * archivo; aquí solo se adelanta lo evidente (un archivo que pesa más de lo
 * admitido) para no subirlo en balde, y si el servicio lo rechaza se enseña su
 * mensaje, que dice el requisito incumplido.
 *
 * @module contenido/productos/recursos-graficos
 */

import { construirPaginacion } from '../../comun/paginacion.js';
import { limpiarAviso, pintarAviso } from '../../comun/ui/aviso.js';
import { conCarga } from '../../comun/ui/boton.js';
import { abrirDialogo, confirmarConMotivo } from '../../comun/ui/dialogo.js';
import { distintivo } from '../../comun/ui/distintivo.js';
import { h, vaciar } from '../../comun/ui/dom.js';
import {
  estadoDeCarga,
  estadoDeError,
  estadoVacio,
  pintarEstado,
} from '../../comun/ui/estado-vista.js';
import { fechaHora } from '../../comun/ui/formato.js';
import { listarRecursos, retirarRecurso, subirRecurso } from './cliente-productos.js';

/** RNF-USA-001: dieciséis por página. */
export const RECURSOS_POR_PAGINA = 16;

/** Lo que el selector de archivos deja elegir: lo que el servicio admite. */
export const ACEPTA_RECURSOS = 'image/png,image/jpeg,image/webp,image/gif';

/**
 * Las tres clases, con lo que admite cada una. Los pesos son los valores por
 * omisión del servicio (`application.properties`); si un despliegue los
 * cambia, el servicio sigue teniendo la última palabra y su mensaje lo dice.
 */
export const CLASES_DE_RECURSO = Object.freeze([
  Object.freeze({
    valor: 'IMAGEN',
    texto: 'Imagen',
    conArticulo: 'una imagen',
    pista: 'PNG, JPEG o WebP · hasta 2 MB y 4096 px por lado',
    bytesMaximos: 2 * 1024 * 1024,
  }),
  Object.freeze({
    valor: 'ICONO',
    texto: 'Icono',
    conArticulo: 'un icono',
    pista: 'PNG o WebP · hasta 256 KB y 512 px por lado',
    bytesMaximos: 256 * 1024,
  }),
  Object.freeze({
    valor: 'ANIMACION',
    texto: 'Animación',
    conArticulo: 'una animación',
    pista: 'GIF, PNG animado o WebP animado · hasta 4 MB y 1024 px por lado',
    bytesMaximos: 4 * 1024 * 1024,
  }),
]);

const CLASE_POR_VALOR = Object.freeze(
  Object.fromEntries(CLASES_DE_RECURSO.map((clase) => [clase.valor, clase])),
);

const NOMBRE_DEL_FORMATO = Object.freeze({ PNG: 'PNG', JPEG: 'JPEG', WEBP: 'WebP', GIF: 'GIF' });

/**
 * Un peso legible: «900 bytes», «45 KB», «1,5 MB».
 *
 * @param {number} bytes
 * @returns {string}
 */
export function pesoLegible(bytes) {
  const n = Number(bytes);
  if (!Number.isFinite(n) || n < 0) {
    return '—';
  }
  if (n >= 1024 * 1024) {
    return `${(n / (1024 * 1024)).toLocaleString('es-CO', { maximumFractionDigits: 1 })} MB`;
  }
  if (n >= 1024) {
    return `${Math.round(n / 1024)} KB`;
  }
  return `${n} ${n === 1 ? 'byte' : 'bytes'}`;
}

/**
 * «PNG · 320 × 200 px · 45 KB».
 *
 * @param {{formato?: string, ancho?: number, alto?: number, bytes?: number}} recurso
 * @returns {string}
 */
export function textoDeRecurso(recurso) {
  return [
    NOMBRE_DEL_FORMATO[recurso?.formato] ?? recurso?.formato,
    recurso?.ancho && recurso?.alto ? `${recurso.ancho} × ${recurso.alto} px` : null,
    recurso?.bytes ? pesoLegible(recurso.bytes) : null,
  ]
    .filter(Boolean)
    .join(' · ');
}

/**
 * Lo que se puede decir de un archivo antes de subirlo: si pesa más de lo que
 * admite su clase. El resto (formato, medidas, si está animado) lo comprueba el
 * servicio con los bytes.
 *
 * @param {{size: number}|null} archivo
 * @param {string} clase
 * @returns {string|null} el problema, o null
 */
export function problemaAntesDeSubir(archivo, clase) {
  if (!archivo) {
    return 'Elige un archivo.';
  }
  const definicion = CLASE_POR_VALOR[clase];
  if (!definicion) {
    return 'Elige si es una imagen, un icono o una animación.';
  }
  if (archivo.size > definicion.bytesMaximos) {
    return `El archivo pesa ${pesoLegible(archivo.size)} y ${definicion.conArticulo} puede pesar como máximo ${pesoLegible(definicion.bytesMaximos)}.`;
  }
  return null;
}

/**
 * Una miniatura: la imagen del recurso o, si no hay, el hueco del kit.
 *
 * @param {string|null} url
 * @param {string} [texto] alternativa accesible
 * @returns {HTMLElement}
 */
export function miniaturaDeRecurso(url, texto = '') {
  const caja = h('div', { clase: 'recurso-miniatura' });
  if (url) {
    const imagen = h('img', {
      atributos: { src: url, alt: texto, loading: 'lazy', decoding: 'async' },
    });
    imagen.addEventListener(
      'error',
      () => {
        caja.dataset.imagen = 'rota';
        imagen.replaceWith(h('span', { clase: 'recurso-miniatura__vacia', texto: 'Sin vista' }));
      },
      { once: true },
    );
    caja.append(imagen);
  } else {
    caja.append(h('span', { clase: 'recurso-miniatura__vacia', texto: 'Sin imagen' }));
  }
  return caja;
}

/**
 * La tarjeta de un recurso en una rejilla: miniatura, nombre, clase, formato y
 * medidas, y dónde se usa.
 *
 * @param {object} recurso `RecursoGrafico`
 * @param {{acciones?: HTMLElement[]}} [opciones]
 * @returns {HTMLElement}
 */
export function tarjetaDeRecurso(recurso, { acciones = [] } = {}) {
  const usos = Array.isArray(recurso.usadoEn) ? recurso.usadoEn : [];
  return h('li', {
    clase: 'recurso-tarjeta',
    datos: { recurso: recurso.id, clase: recurso.clase },
    hijos: [
      miniaturaDeRecurso(recurso.url, recurso.nombre ?? ''),
      h('div', {
        clase: 'recurso-tarjeta__datos',
        hijos: [
          h('p', { clase: 'recurso-tarjeta__nombre', texto: recurso.nombre ?? 'Sin nombre' }),
          h('p', {
            clase: 'recurso-tarjeta__distintivos',
            hijos: [
              distintivo(CLASE_POR_VALOR[recurso.clase]?.texto ?? recurso.clase),
              recurso.retiradoEn ? distintivo('Retirado', 'suspendido') : null,
            ],
          }),
          h('p', { clase: 't-meta', texto: textoDeRecurso(recurso) }),
          h('p', {
            clase: 't-meta',
            texto: usos.length
              ? `Imagen de ${usos.map((uso) => uso.nombre).join(', ')}`
              : 'Sin usar en ningún producto',
          }),
          recurso.retiradoEn
            ? h('p', {
                clase: 't-meta',
                texto: `Retirado el ${fechaHora(recurso.retiradoEn)}${recurso.motivoRetiro ? `: ${recurso.motivoRetiro}` : ''}`,
              })
            : null,
        ],
      }),
      acciones.length ? h('div', { clase: 'recurso-tarjeta__acciones', hijos: acciones }) : null,
    ],
  });
}

/** El desplegable de clase, con su pista debajo. */
function desplegableDeClase(nombre, inicial = 'IMAGEN', { conTodas = false } = {}) {
  const control = h('select', {
    clase: 'campo__control',
    atributos: { name: nombre, 'aria-label': 'Clase de recurso' },
    hijos: [
      conTodas ? h('option', { texto: 'Todas', atributos: { value: '' } }) : null,
      ...CLASES_DE_RECURSO.map((clase) =>
        h('option', { texto: clase.texto, atributos: { value: clase.valor } }),
      ),
    ],
  });
  control.value = inicial;
  return control;
}

/**
 * El campo «Imagen del producto»: subir un archivo o elegirlo de la
 * biblioteca. Lleva un `input` oculto con `name` (por omisión `imagen`) para
 * que `construirSolicitudProducto` lo lea como cualquier otro campo.
 *
 * @param {{nombre?: string, valor?: string, productoId?: string|null,
 *          etiqueta?: string, subir?: typeof subirRecurso, listar?: typeof listarRecursos,
 *          fetchImpl?: Function, alCambiar?: (url: string) => void}} [opciones]
 * @returns {{elemento: HTMLElement, control: HTMLInputElement, valor: () => string,
 *            establecer: (url: string, recurso?: object|null) => void,
 *            marcarError: (motivo: string|null) => void}}
 */
export function selectorDeRecurso({
  nombre = 'imagen',
  valor = '',
  productoId = null,
  etiqueta = 'Imagen del producto',
  subir = subirRecurso,
  listar = listarRecursos,
  fetchImpl,
  alCambiar = () => {},
} = {}) {
  const oculto = h('input', { atributos: { type: 'hidden', name: nombre, value: valor ?? '' } });
  const zonaMiniatura = h('div', { clase: 'selector-recurso__miniatura' });
  const descripcion = h('p', { clase: 'selector-recurso__descripcion' });
  const clase = desplegableDeClase(`${nombre}-clase`);
  const pista = h('p', { clase: 'campo__pista', texto: CLASE_POR_VALOR.IMAGEN.pista });
  const error = h('p', { clase: 'campo__error', atributos: { role: 'alert', hidden: true } });
  const archivo = h('input', {
    atributos: { type: 'file', accept: ACEPTA_RECURSOS, hidden: true, 'aria-label': 'Archivo' },
    datos: { accion: 'archivo-recurso' },
  });
  const botonSubir = h('button', {
    clase: 'boton boton--secundario boton--pequeno',
    texto: 'Subir archivo',
    atributos: { type: 'button' },
    datos: { accion: 'subir-recurso' },
  });
  const botonElegir = h('button', {
    clase: 'boton boton--secundario boton--pequeno',
    texto: 'Elegir de la biblioteca',
    atributos: { type: 'button' },
    datos: { accion: 'elegir-recurso' },
  });

  const marcarError = (motivo) => {
    error.textContent = motivo ?? '';
    error.hidden = !motivo;
    elemento.classList.toggle('selector-recurso--error', Boolean(motivo));
  };

  const pintar = (url, recurso = null) => {
    vaciar(zonaMiniatura).append(miniaturaDeRecurso(url, ''));
    if (recurso) {
      descripcion.textContent = `${recurso.nombre ?? 'Archivo'} · ${textoDeRecurso(recurso)}`;
    } else if (url) {
      descripcion.textContent = 'Imagen actual del producto';
    } else {
      descripcion.textContent =
        'Todavía no tiene imagen: sube un archivo o elige uno de la biblioteca.';
    }
  };

  const establecer = (url, recurso = null) => {
    oculto.value = url ?? '';
    pintar(oculto.value, recurso);
    marcarError(null);
    alCambiar(oculto.value);
  };

  clase.addEventListener('change', () => {
    pista.textContent = CLASE_POR_VALOR[clase.value]?.pista ?? '';
  });
  botonSubir.addEventListener('click', () => archivo.click());
  archivo.addEventListener('change', async () => {
    const elegido = archivo.files?.[0] ?? null;
    archivo.value = '';
    if (!elegido) {
      return;
    }
    const problema = problemaAntesDeSubir(elegido, clase.value);
    if (problema) {
      marcarError(problema);
      return;
    }
    conCarga(botonSubir, true, 'Subiendo…');
    try {
      const recurso = await subir(
        elegido,
        { clase: clase.value, productoId },
        fetchImpl ? { fetchImpl } : {},
      );
      establecer(recurso.url, recurso);
    } catch (fallo) {
      marcarError(fallo?.message || 'No se pudo subir el archivo.');
    } finally {
      conCarga(botonSubir, false);
    }
  });
  botonElegir.addEventListener('click', () => {
    abrirBibliotecaParaElegir({
      listar,
      fetchImpl,
      alElegir: (recurso) => establecer(recurso.url, recurso),
    });
  });

  const elemento = h('fieldset', {
    clase: 'selector-recurso',
    datos: { campo: nombre },
    hijos: [
      h('legend', { clase: 'campo__etiqueta', texto: etiqueta }),
      h('div', {
        clase: 'selector-recurso__cuerpo',
        hijos: [
          zonaMiniatura,
          h('div', {
            clase: 'selector-recurso__datos',
            hijos: [
              descripcion,
              h('div', {
                clase: 'selector-recurso__acciones',
                hijos: [clase, botonSubir, botonElegir, archivo],
              }),
              pista,
              error,
            ],
          }),
        ],
      }),
      oculto,
    ],
  });
  pintar(oculto.value);

  return { elemento, control: oculto, valor: () => oculto.value, establecer, marcarError };
}

/**
 * La biblioteca en un diálogo, para elegir un recurso: filtro por clase,
 * dieciséis por página y «Usar» en cada uno.
 *
 * @param {{listar?: typeof listarRecursos, fetchImpl?: Function,
 *          alElegir: (recurso: object) => void}} opciones
 * @returns {{cerrar: () => void}}
 */
export function abrirBibliotecaParaElegir({ listar = listarRecursos, fetchImpl, alElegir }) {
  const filtro = desplegableDeClase('filtro-clase', '', { conTodas: true });
  const zona = h('div', { clase: 'biblioteca-recursos__zona', datos: { zona: 'elegir-recurso' } });
  const zonaPaginacion = h('div');
  let pagina = 0;

  const cuerpo = h('div', {
    clase: 'biblioteca-recursos biblioteca-recursos--elegir',
    hijos: [
      h('label', {
        clase: 'campo',
        hijos: [h('span', { clase: 'campo__etiqueta', texto: 'Clase' }), filtro],
      }),
      zona,
      zonaPaginacion,
    ],
  });
  const dialogo = abrirDialogo({ titulo: 'Elegir de la biblioteca', cuerpo });
  dialogo.elemento?.classList.add('dialogo--ancho');

  async function cargar() {
    pintarEstado(zona, estadoDeCarga({ filas: 4, etiqueta: 'Cargando la biblioteca…' }));
    vaciar(zonaPaginacion);
    try {
      const respuesta = await listar(
        { clase: filtro.value || null, pagina, tamano: RECURSOS_POR_PAGINA },
        fetchImpl ? { fetchImpl } : {},
      );
      const recursos = respuesta?.content ?? [];
      if (!recursos.length) {
        pintarEstado(
          zona,
          estadoVacio({
            titulo: 'La biblioteca está vacía',
            detalle: 'Sube un archivo desde el campo de imagen para empezar.',
          }),
        );
        return;
      }
      vaciar(zona).append(
        h('ul', {
          clase: 'recursos-rejilla',
          hijos: recursos.map((recurso) => {
            const usar = h('button', {
              clase: 'boton boton--primario boton--pequeno',
              texto: 'Usar',
              atributos: {
                type: 'button',
                'aria-label': `Usar ${recurso.nombre ?? 'este recurso'}`,
              },
              datos: { accion: 'usar-recurso', recurso: recurso.id },
            });
            usar.addEventListener('click', () => {
              alElegir(recurso);
              dialogo.cerrar();
            });
            return tarjetaDeRecurso(recurso, { acciones: [usar] });
          }),
        }),
      );
      zonaPaginacion.append(
        construirPaginacion(
          { paginaActual: respuesta.page ?? pagina, totalPaginas: respuesta.totalPages ?? 1 },
          (nueva) => {
            pagina = nueva;
            void cargar();
          },
          { etiqueta: 'Páginas de la biblioteca' },
        ),
      );
    } catch (fallo) {
      pintarEstado(
        zona,
        estadoDeError({
          titulo: 'No se pudo abrir la biblioteca',
          detalle: fallo?.message || 'Vuelve a intentarlo en un momento.',
          alReintentar: () => void cargar(),
        }),
      );
    }
  }

  filtro.addEventListener('change', () => {
    pagina = 0;
    void cargar();
  });
  void cargar();
  return { cerrar: dialogo.cerrar };
}

/**
 * La biblioteca de recursos en el panel del catálogo: subir (con su clase),
 * filtrar por clase, ver los retirados, dónde se usa cada uno y retirarlo con
 * motivo.
 *
 * @param {HTMLElement} raiz
 * @param {{listar?: typeof listarRecursos, subir?: typeof subirRecurso,
 *          retirar?: typeof retirarRecurso, pedirMotivo?: typeof confirmarConMotivo,
 *          fetchImpl?: Function}} [dependencias]
 * @returns {{recargar: () => Promise<void>}}
 */
export function montarBibliotecaDeRecursos(
  raiz,
  {
    listar = listarRecursos,
    subir = subirRecurso,
    retirar = retirarRecurso,
    pedirMotivo = confirmarConMotivo,
    fetchImpl,
  } = {},
) {
  const opciones = fetchImpl ? { fetchImpl } : {};
  const zonaAviso = h('div', { datos: { zona: 'aviso-recursos' }, atributos: { hidden: true } });
  const claseDeSubida = desplegableDeClase('clase-subida');
  const pistaDeSubida = h('p', { clase: 'campo__pista', texto: CLASE_POR_VALOR.IMAGEN.pista });
  const archivo = h('input', {
    atributos: { type: 'file', accept: ACEPTA_RECURSOS, hidden: true, 'aria-label': 'Archivo' },
  });
  const botonSubir = h('button', {
    clase: 'boton boton--primario boton--pequeno',
    texto: 'Subir recurso',
    atributos: { type: 'button' },
    datos: { accion: 'subir-a-biblioteca' },
  });
  const filtro = desplegableDeClase('filtro-biblioteca', '', { conTodas: true });
  const verRetirados = h('input', {
    clase: 'casilla__entrada',
    atributos: { type: 'checkbox', name: 'ver-retirados' },
  });
  const zona = h('div', { datos: { zona: 'biblioteca' } });
  const zonaPaginacion = h('div');
  let pagina = 0;

  claseDeSubida.addEventListener('change', () => {
    pistaDeSubida.textContent = CLASE_POR_VALOR[claseDeSubida.value]?.pista ?? '';
  });
  botonSubir.addEventListener('click', () => archivo.click());
  archivo.addEventListener('change', async () => {
    const elegido = archivo.files?.[0] ?? null;
    archivo.value = '';
    if (!elegido) {
      return;
    }
    const problema = problemaAntesDeSubir(elegido, claseDeSubida.value);
    if (problema) {
      pintarAviso(zonaAviso, { tono: 'advertencia', titulo: 'No se subió', detalle: problema });
      return;
    }
    limpiarAviso(zonaAviso);
    conCarga(botonSubir, true, 'Subiendo…');
    try {
      const recurso = await subir(elegido, { clase: claseDeSubida.value }, opciones);
      pintarAviso(zonaAviso, {
        tono: 'exito',
        titulo: 'Recurso en la biblioteca',
        detalle: `${recurso.nombre ?? 'El archivo'} · ${textoDeRecurso(recurso)}. Ya se puede usar como imagen de un producto.`,
      });
      pagina = 0;
      await cargar();
    } catch (fallo) {
      pintarAviso(zonaAviso, {
        tono: 'advertencia',
        titulo: 'No se subió',
        detalle: fallo?.message || 'No se pudo subir el archivo.',
      });
    } finally {
      conCarga(botonSubir, false);
    }
  });

  async function retirarUno(recurso, boton) {
    const motivo = await pedirMotivo({
      titulo: `¿Retirar «${recurso.nombre ?? 'este recurso'}» de la biblioteca?`,
      mensaje:
        'Deja de aparecer en la biblioteca. No se borra: las versiones anteriores del diseño que lo usan lo siguen mostrando.',
      etiquetaMotivo: 'Motivo del retiro',
      textoConfirmar: 'Retirar',
    });
    if (motivo === null || motivo === undefined) {
      return;
    }
    conCarga(boton, true, 'Retirando…');
    try {
      await retirar(recurso.id, motivo, opciones);
      pintarAviso(zonaAviso, {
        tono: 'exito',
        titulo: 'Recurso retirado',
        detalle: 'Ya no aparece en la biblioteca.',
      });
      await cargar();
    } catch (fallo) {
      conCarga(boton, false);
      pintarAviso(zonaAviso, {
        tono: 'advertencia',
        titulo: 'No se retiró',
        detalle: fallo?.message || 'No se pudo retirar el recurso.',
      });
    }
  }

  async function cargar() {
    pintarEstado(zona, estadoDeCarga({ filas: 4, etiqueta: 'Cargando la biblioteca…' }));
    vaciar(zonaPaginacion);
    try {
      const respuesta = await listar(
        {
          clase: filtro.value || null,
          incluirRetirados: verRetirados.checked,
          pagina,
          tamano: RECURSOS_POR_PAGINA,
        },
        opciones,
      );
      const recursos = respuesta?.content ?? [];
      if (!recursos.length) {
        pintarEstado(
          zona,
          estadoVacio({
            titulo: filtro.value ? 'No hay recursos de esta clase' : 'La biblioteca está vacía',
            detalle: 'Sube imágenes, iconos o animaciones para usarlos en los productos.',
          }),
        );
        return;
      }
      vaciar(zona).append(
        h('ul', {
          clase: 'recursos-rejilla',
          hijos: recursos.map((recurso) => {
            const acciones = [];
            if (!recurso.retiradoEn) {
              const enUso = Array.isArray(recurso.usadoEn) && recurso.usadoEn.length > 0;
              const retirarBoton = h('button', {
                clase: 'boton boton--secundario boton--pequeno',
                texto: 'Retirar',
                atributos: {
                  type: 'button',
                  disabled: enUso,
                  title: enUso
                    ? 'Es la imagen de un producto: ponle otra antes de retirarla.'
                    : null,
                  'aria-label': `Retirar ${recurso.nombre ?? 'este recurso'}`,
                },
                datos: { accion: 'retirar-recurso', recurso: recurso.id },
              });
              retirarBoton.addEventListener('click', () => void retirarUno(recurso, retirarBoton));
              acciones.push(retirarBoton);
            }
            return tarjetaDeRecurso(recurso, { acciones });
          }),
        }),
      );
      zonaPaginacion.append(
        construirPaginacion(
          { paginaActual: respuesta.page ?? pagina, totalPaginas: respuesta.totalPages ?? 1 },
          (nueva) => {
            pagina = nueva;
            void cargar();
          },
          { etiqueta: 'Páginas de la biblioteca' },
        ),
      );
    } catch (fallo) {
      pintarEstado(
        zona,
        estadoDeError({
          titulo: 'No se pudo cargar la biblioteca',
          detalle: fallo?.message || 'Vuelve a intentarlo en un momento.',
          alReintentar: () => void cargar(),
        }),
      );
    }
  }

  filtro.addEventListener('change', () => {
    pagina = 0;
    void cargar();
  });
  verRetirados.addEventListener('change', () => {
    pagina = 0;
    void cargar();
  });

  vaciar(raiz).append(
    h('div', {
      clase: 'biblioteca-recursos__barra',
      hijos: [
        h('div', {
          clase: 'biblioteca-recursos__subida',
          hijos: [
            h('label', {
              clase: 'campo',
              hijos: [
                h('span', { clase: 'campo__etiqueta', texto: 'Clase del archivo' }),
                claseDeSubida,
              ],
            }),
            botonSubir,
            archivo,
            pistaDeSubida,
          ],
        }),
        h('div', {
          clase: 'biblioteca-recursos__filtros',
          hijos: [
            h('label', {
              clase: 'campo',
              hijos: [h('span', { clase: 'campo__etiqueta', texto: 'Mostrar' }), filtro],
            }),
            h('label', {
              clase: 'casilla',
              hijos: [
                verRetirados,
                h('span', { clase: 'casilla__etiqueta', texto: 'Incluir retirados' }),
              ],
            }),
          ],
        }),
      ],
    }),
    zonaAviso,
    zona,
    zonaPaginacion,
  );
  const recargar = cargar;
  void cargar();
  return { recargar };
}
