/**
 * Pestaña «Preguntas sin respuesta» del panel del asistente —
 * ms-chatbot.yaml 1.7.0 (7.4.5 «aprendizaje continuo»).
 *
 * Lo que los jugadores preguntaron y el asistente no supo responder: no la
 * entendió (ofreció soporte) o la respuesta se calificó «no útil». El servicio
 * las agrupa por su texto (sin tildes, mayúsculas ni signos) y aquí salen las
 * más repetidas primero, por período y estado, de 16 en 16.
 *
 * El administrador las atiende:
 *
 * - «Marcar atendida»: un tema de la base ya la cubre. Si alguien la vuelve a
 *   preguntar, regresa sola a pendiente y aquí se dice cuántas veces pasó:
 *   el tema no bastaba.
 * - «Descartar»: es ruido (una prueba, algo que no es del juego).
 * - «Crear tema con esta pregunta»: abre el formulario de tema de la versión
 *   candidata con la pregunta como primera variante en español. Si no hay
 *   candidata, explica cómo crearla.
 *
 * La pregunta de ejemplo ya llega sin contraseñas, tarjetas ni tokens.
 *
 * @module cuentas/panel-chatbot-brechas
 */

import { aviso as crearAviso } from '../comun/ui/aviso.js';
import { conCarga } from '../comun/ui/boton.js';
import { campo } from '../comun/ui/campo.js';
import { distintivo } from '../comun/ui/distintivo.js';
import { h, vaciar } from '../comun/ui/dom.js';
import {
  estadoDeCarga,
  estadoDeError,
  estadoVacio,
  pintarEstado,
} from '../comun/ui/estado-vista.js';
import { fechaHora, numero } from '../comun/ui/formato.js';
import { encabezadoDeSeccion } from '../comun/ui/pagina.js';
import { editarTemaEnDialogo } from './panel-chatbot-base.js';
import { fraseDeErrorDelPanel, textoDeErrorDelPanel } from './panel-chatbot-errores.js';

/** Preguntas por página (RNF-USA-001: listados de 16). */
export const POR_PAGINA = 16;

/** Largo máximo de una variante de pregunta (`Variantes` del contrato). */
export const LARGO_DE_VARIANTE = 200;

/** Estados de una pregunta sin respuesta (`EstadoDeBrecha`) y cómo se pintan. */
export const ESTADOS_DE_BRECHA = Object.freeze({
  PENDIENTE: { texto: 'Pendiente', variante: 'en-progreso', filtro: 'Pendientes' },
  ATENDIDA: { texto: 'Atendida', variante: 'completada', filtro: 'Atendidas' },
  DESCARTADA: { texto: 'Descartada', variante: 'no-disponible', filtro: 'Descartadas' },
});

/** Lo que se dice después de cambiar el estado. */
const HECHO = Object.freeze({
  PENDIENTE: 'La pregunta volvió a pendientes.',
  ATENDIDA: 'Marcada como atendida. Si alguien la vuelve a preguntar, regresará a pendientes.',
  DESCARTADA: 'Pregunta descartada.',
});

/**
 * La pregunta, lista para ser la primera variante de un tema: sin comas (el
 * motor guarda las variantes separadas por coma) y como mucho 200 caracteres.
 *
 * @param {string} pregunta
 * @returns {string}
 */
export function varianteDesdePregunta(pregunta) {
  return String(pregunta ?? '')
    .replaceAll(',', ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, LARGO_DE_VARIANTE)
    .trim();
}

/**
 * Las veces, contadas como las lee el administrador.
 *
 * @param {{escalamientos: number, noUtiles: number}} brecha
 * @returns {string}
 */
export function detalleDeVeces({ escalamientos, noUtiles }) {
  const partes = [];
  if (escalamientos > 0) {
    partes.push(`${numero(escalamientos)} sin entenderla`);
  }
  if (noUtiles > 0) {
    partes.push(`${numero(noUtiles)} «no útil»`);
  }
  return partes.join(' · ');
}

/**
 * Lo que se sabe del estado: quién la marcó y cuándo, o que volvió sola.
 *
 * @param {{estado: string, estadoCambiadoPor?: string|null,
 *          estadoCambiadoEn?: string|null, reaperturas: number}} brecha
 * @returns {string|null}
 */
export function detalleDeEstado(brecha) {
  const partes = [];
  if (brecha.reaperturas > 0) {
    partes.push(
      brecha.reaperturas === 1
        ? 'Volvió a preguntarse después de marcarla atendida'
        : `Volvió a preguntarse ${numero(brecha.reaperturas)} veces después de marcarla atendida`,
    );
  }
  if (brecha.estado !== 'PENDIENTE' && brecha.estadoCambiadoEn) {
    partes.push(
      brecha.estadoCambiadoPor
        ? `Por ${brecha.estadoCambiadoPor} el ${fechaHora(brecha.estadoCambiadoEn)}`
        : `El ${fechaHora(brecha.estadoCambiadoEn)}`,
    );
  }
  return partes.length > 0 ? partes.join(' · ') : null;
}

/**
 * Monta la pestaña.
 *
 * @param {HTMLElement} raiz
 * @param {{cliente: object, editarTema?: typeof editarTemaEnDialogo,
 *          irA?: ((pestana: string) => void)|null}} dependencias `irA`: abre
 *          otra pestaña del panel (la de la base de conocimiento)
 * @returns {{cargar: () => Promise<void>}}
 */
export function montarBrechas(
  raiz,
  { cliente, editarTema: editarTemaConFormulario = editarTemaEnDialogo, irA = null },
) {
  let pagina = 0;

  const estado = campo({
    nombre: 'estado',
    etiqueta: 'Mostrar',
    opciones: [
      ...Object.entries(ESTADOS_DE_BRECHA).map(([valor, { filtro }]) => ({ valor, texto: filtro })),
      { valor: '', texto: 'Todas' },
    ],
    valor: 'PENDIENTE',
  });
  const desde = campo({ nombre: 'desde', etiqueta: 'Desde', tipo: 'date' });
  const hasta = campo({
    nombre: 'hasta',
    etiqueta: 'Hasta',
    tipo: 'date',
    pista: 'Sin fechas, los últimos 30 días.',
  });
  const consultar = h('button', {
    clase: 'boton boton--primario',
    texto: 'Consultar',
    atributos: { type: 'submit' },
  });
  const formulario = h('form', {
    clase: 'panel-chatbot__filtros',
    atributos: { novalidate: true },
    hijos: [estado.elemento, desde.elemento, hasta.elemento, consultar],
  });
  const nota = h('div', { clase: 'panel-chatbot__nota', datos: { zona: 'nota-brechas' } });
  const zona = h('section', { clase: 'pila', datos: { zona: 'brechas' } });
  raiz.append(
    encabezadoDeSeccion({
      titulo: 'Preguntas sin respuesta',
      detalle:
        'Lo que los jugadores preguntaron y el asistente no supo responder: no la entendió o la respuesta no les sirvió. Las más repetidas primero.',
    }),
    formulario,
    nota,
    zona,
  );

  /** @param {{tono?: string, titulo: string, detalle?: string|null, accion?: object|null}|null} texto */
  function avisar(texto) {
    vaciar(nota);
    if (texto) {
      nota.append(crearAviso({ tono: 'info', ...texto }));
    }
  }

  function periodoValido() {
    const invalido =
      desde.control.value && hasta.control.value && hasta.control.value < desde.control.value;
    hasta.marcarError(invalido ? '«Hasta» no puede ser anterior a «Desde».' : null);
    return !invalido;
  }

  async function cargar() {
    if (!periodoValido()) {
      return;
    }
    pintarEstado(zona, estadoDeCarga({ filas: 4, etiqueta: 'Cargando preguntas sin respuesta…' }));
    try {
      const resultado = await cliente.listarBrechas({
        desde: desde.control.value || null,
        hasta: hasta.control.value || null,
        estado: estado.control.value || null,
        pagina,
        tamano: POR_PAGINA,
      });
      // Si lo de esta página cambió de estado (la última pendiente se marcó
      // atendida), se vuelve a la última página que tiene algo, no a un vacío
      // que diga que no hay ninguna.
      const ultima = Math.max(0, Math.ceil((resultado.totalElementos ?? 0) / POR_PAGINA) - 1);
      if ((resultado.contenido ?? []).length === 0 && pagina > ultima) {
        pagina = ultima;
        await cargar();
        return;
      }
      pintarPagina(resultado);
    } catch (error) {
      const texto =
        error?.estado === 400
          ? {
              titulo: 'El período no es válido',
              detalle:
                'Revisa las fechas: «hasta» no puede ser anterior a «desde» y el máximo es un año.',
            }
          : textoDeErrorDelPanel(error, 'No se pudieron cargar las preguntas sin respuesta');
      pintarEstado(zona, estadoDeError({ ...texto, alReintentar: () => cargar() }));
    }
  }

  function pintarPagina({ contenido = [], totalElementos = 0 }) {
    if (contenido.length === 0) {
      const elegido = ESTADOS_DE_BRECHA[estado.control.value];
      vaciar(zona).append(
        estadoVacio({
          titulo: elegido
            ? `No hay preguntas ${elegido.filtro.toLowerCase()} en este período`
            : 'No hay preguntas sin respuesta en este período',
          detalle:
            'Aparecen cuando el asistente no entiende una pregunta o alguien califica una respuesta como «no útil».',
        }),
      );
      return;
    }
    vaciar(zona).append(tabla(contenido), paginacion(totalElementos));
  }

  function tabla(brechas) {
    return h('table', {
      clase: 'tabla-panel',
      datos: { zona: 'tabla-brechas' },
      hijos: [
        h('thead', {
          hijos: [
            h('tr', {
              hijos: ['Pregunta', 'Veces', 'Estado', ''].map((texto) =>
                h('th', { texto, atributos: { scope: 'col' } }),
              ),
            }),
          ],
        }),
        h('tbody', { hijos: brechas.map(fila) }),
      ],
    });
  }

  function fila(brecha) {
    const conocido = ESTADOS_DE_BRECHA[brecha.estado] ?? { texto: brecha.estado, variante: null };
    const detalleEstado = detalleDeEstado(brecha);
    return h('tr', {
      datos: { brecha: brecha.brechaId },
      hijos: [
        h('td', {
          hijos: [
            h('p', { clase: 't-cuerpo', texto: brecha.pregunta }),
            h('p', {
              clase: 't-meta',
              texto: `Primera vez ${fechaHora(brecha.primeraVez)} · última ${fechaHora(brecha.ultimaVez)}`,
            }),
          ],
        }),
        h('td', {
          hijos: [
            h('p', { clase: 't-cuerpo', texto: numero(brecha.total) }),
            h('p', { clase: 't-meta', texto: detalleDeVeces(brecha) }),
          ],
        }),
        h('td', {
          hijos: [
            distintivo(conocido.texto, conocido.variante),
            detalleEstado ? h('p', { clase: 't-meta', texto: detalleEstado }) : null,
          ],
        }),
        h('td', { hijos: [h('div', { clase: 'acciones', hijos: acciones(brecha) })] }),
      ],
    });
  }

  function acciones(brecha) {
    const botones = [];
    if (brecha.estado === 'PENDIENTE') {
      botones.push(
        botonDeEstado(brecha, 'ATENDIDA', 'Marcar atendida', 'boton--contorno'),
        botonDeEstado(brecha, 'DESCARTADA', 'Descartar', 'boton--secundario'),
        botonCrearTema(brecha),
      );
    } else {
      botones.push(botonDeEstado(brecha, 'PENDIENTE', 'Volver a pendiente', 'boton--secundario'));
    }
    return botones;
  }

  function botonDeEstado(brecha, nuevo, texto, variante) {
    const boton = h('button', {
      clase: `boton ${variante} boton--pequeno`,
      texto,
      atributos: { type: 'button', 'aria-label': `${texto}: ${brecha.pregunta}` },
      datos: { accion: `brecha-${nuevo.toLowerCase()}`, brecha: brecha.brechaId },
    });
    boton.addEventListener('click', async () => {
      conCarga(boton, true, 'Guardando…');
      let mensaje;
      try {
        await cliente.cambiarEstadoDeBrecha(brecha.brechaId, nuevo);
        mensaje = { tono: 'exito', titulo: HECHO[nuevo] };
      } catch (error) {
        mensaje = {
          tono: 'error',
          titulo: fraseDeErrorDelPanel(error, 'No se pudo cambiar el estado de la pregunta'),
        };
      }
      await cargar();
      avisar(mensaje);
    });
    return boton;
  }

  function botonCrearTema(brecha) {
    const boton = h('button', {
      clase: 'boton boton--primario boton--pequeno',
      texto: 'Crear tema con esta pregunta',
      atributos: {
        type: 'button',
        'aria-label': `Crear un tema con la pregunta: ${brecha.pregunta}`,
      },
      datos: { accion: 'crear-tema-desde-brecha', brecha: brecha.brechaId },
    });
    boton.addEventListener('click', () => crearTema(brecha, boton));
    return boton;
  }

  async function crearTema(brecha, boton) {
    avisar(null);
    conCarga(boton, true, 'Buscando la candidata…');
    let candidata;
    try {
      const versiones = await cliente.listarVersiones();
      candidata = versiones.find((version) => version.estado === 'BORRADOR') ?? null;
    } catch (error) {
      conCarga(boton, false);
      avisar({
        tono: 'error',
        titulo: fraseDeErrorDelPanel(error, 'No se pudo consultar la versión candidata'),
      });
      return;
    }
    conCarga(boton, false);
    if (!candidata) {
      avisar({
        tono: 'advertencia',
        titulo: 'No hay una versión candidata donde agregar el tema.',
        detalle:
          'Los temas se agregan a una candidata, nunca a la versión en producción. Ve a «Base de conocimiento», pulsa «Crear candidata» y vuelve aquí.',
        accion: irA
          ? {
              texto: 'Ir a «Base de conocimiento»',
              nombre: 'ir-a-la-base',
              alPulsar: () => irA('base'),
            }
          : null,
      });
      return;
    }
    const guardado = await editarTemaConFormulario({
      titulo: 'Crear tema con esta pregunta',
      tema: { variantesEs: [varianteDesdePregunta(brecha.pregunta)] },
      guardar: (datos) => cliente.agregarTema(datos),
    });
    if (guardado) {
      avisar({
        tono: 'exito',
        titulo: `Se agregó el tema a la versión candidata ${candidata.numero}.`,
        detalle:
          'Los jugadores lo verán cuando la despliegues desde «Reentrenamiento». Después, marca esta pregunta como atendida.',
      });
    }
  }

  function paginacion(total) {
    const paginas = Math.max(1, Math.ceil(total / POR_PAGINA));
    const anterior = h('button', {
      clase: 'boton boton--secundario boton--pequeno',
      texto: 'Anterior',
      atributos: { type: 'button', disabled: pagina === 0 },
      datos: { accion: 'pagina-anterior' },
    });
    const siguiente = h('button', {
      clase: 'boton boton--secundario boton--pequeno',
      texto: 'Siguiente',
      atributos: { type: 'button', disabled: pagina >= paginas - 1 },
      datos: { accion: 'pagina-siguiente' },
    });
    anterior.addEventListener('click', () => {
      pagina -= 1;
      cargar();
    });
    siguiente.addEventListener('click', () => {
      pagina += 1;
      cargar();
    });
    return h('div', {
      clase: 'acciones',
      datos: { zona: 'paginacion' },
      hijos: [
        anterior,
        h('span', {
          clase: 't-meta',
          texto: `Página ${numero(pagina + 1)} de ${numero(paginas)} · ${numero(total)} preguntas`,
        }),
        siguiente,
      ],
    });
  }

  formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    pagina = 0;
    avisar(null);
    cargar();
  });
  estado.control.addEventListener('change', () => {
    pagina = 0;
    avisar(null);
    cargar();
  });

  return { cargar };
}
