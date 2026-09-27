/**
 * Pestaña «Reentrenamiento» del panel del asistente — HU-CHA-012
 * (RF-CHA-014).
 *
 * El asistente es un motor de reglas: «reentrenarlo» es desplegar una nueva
 * versión de su base de conocimiento. Aquí se hace con red de seguridad:
 *
 * 1. **Casos de evaluación**: preguntas con el tema que debería responderlas
 *    (o «debe escalar», si el asistente no debería saberlo).
 * 2. **Evaluar**: se corren los casos contra la candidata y contra producción,
 *    con la misma lógica con la que el asistente responde.
 * 3. **Desplegar**: solo si la candidata no acierta menos que producción. Si
 *    rinde peor, el servicio responde 409 con los dos resultados y aquí se
 *    muestran los casos que fallan.
 * 4. **Revertir**: vuelve a la versión que estuvo en producción justo antes.
 *
 * @module cuentas/panel-chatbot-reentrenamiento
 */

import { conCarga } from '../comun/ui/boton.js';
import { campo } from '../comun/ui/campo.js';
import { abrirDialogo, confirmar } from '../comun/ui/dialogo.js';
import { distintivo } from '../comun/ui/distintivo.js';
import { h, vaciar } from '../comun/ui/dom.js';
import {
  estadoDeCarga,
  estadoDeError,
  estadoVacio,
  pintarEstado,
} from '../comun/ui/estado-vista.js';
import { numero, porcentaje } from '../comun/ui/formato.js';
import { encabezadoDeSeccion } from '../comun/ui/pagina.js';
import { tarjetaDeCifra } from '../comun/ui/tarjeta.js';
import { textoDeArchivo } from './panel-chatbot-base.js';
import { textoDeErrorDelPanel } from './panel-chatbot-analiticas.js';

/** Texto del «tema esperado» cuando el caso pide escalar. */
export const DEBE_ESCALAR = 'Debe escalar a soporte';

/**
 * Nombre legible de una clave de tema: su título si se conoce.
 *
 * @param {string|null} clave
 * @param {Map<string, string>} titulos clave → título
 * @param {string} [siNula] texto cuando la clave es null
 * @returns {string}
 */
export function nombreDeTema(clave, titulos, siNula = DEBE_ESCALAR) {
  if (clave === null || clave === undefined) {
    return siNula;
  }
  return titulos.get(clave) ?? clave;
}

/**
 * Tarjetas y veredicto de una comparación candidata vs. producción.
 *
 * @param {{candidata: object, produccion: object, candidataApta: boolean}} comparacion
 * @param {Map<string, string>} titulos
 * @returns {HTMLElement}
 */
export function resultadoDeEvaluacion(comparacion, titulos) {
  const { candidata, produccion, candidataApta } = comparacion;
  const cifra = (etiqueta, resultado) =>
    tarjetaDeCifra({
      etiqueta,
      valor: `${numero(resultado.aciertos)} de ${numero(resultado.casosEvaluados)}`,
      detalle: `Aciertos: ${porcentaje(resultado.tasaAcierto)}`,
    });

  const veredicto = h('div', {
    clase: `aviso ${candidataApta ? 'aviso--exito' : 'aviso--error'}`,
    atributos: { role: candidataApta ? 'status' : 'alert' },
    hijos: [
      h('div', {
        clase: 'aviso__cuerpo',
        hijos: [
          h('p', {
            clase: 'aviso__titulo',
            texto: candidataApta
              ? 'La candidata no rinde peor: se puede desplegar.'
              : 'La candidata rinde peor que producción: no se puede desplegar.',
          }),
        ],
      }),
    ],
  });

  const caja = h('div', {
    clase: 'pila',
    datos: { zona: 'resultado-evaluacion' },
    hijos: [
      h('div', {
        clase: 'panel-chatbot__cifras',
        hijos: [
          cifra(`Candidata (versión ${candidata.numero})`, candidata),
          cifra(`Producción (versión ${produccion.numero})`, produccion),
        ],
      }),
      veredicto,
    ],
  });

  if (candidata.fallos.length > 0) {
    caja.append(
      h('p', { clase: 't-etiqueta', texto: 'Casos que falla la candidata' }),
      h('table', {
        clase: 'tabla-panel',
        datos: { zona: 'fallos' },
        hijos: [
          h('thead', {
            hijos: [
              h('tr', {
                hijos: ['Pregunta', 'Se esperaba', 'Respondió'].map((texto) =>
                  h('th', { texto, atributos: { scope: 'col' } }),
                ),
              }),
            ],
          }),
          h('tbody', {
            hijos: candidata.fallos.map((fallo) =>
              h('tr', {
                hijos: [
                  h('td', { texto: fallo.pregunta }),
                  h('td', { texto: nombreDeTema(fallo.temaClaveEsperada, titulos) }),
                  h('td', {
                    texto: nombreDeTema(fallo.temaClaveObtenido, titulos, 'Escaló a soporte'),
                  }),
                ],
              }),
            ),
          }),
        ],
      }),
    );
  }
  return caja;
}

/**
 * Formulario de un caso de evaluación en un diálogo.
 *
 * @param {{titulo: string, caso?: object|null, temas: Array<{clave: string, titulo: string}>,
 *          guardar: (datos: object) => Promise<void>}} opciones
 * @returns {Promise<boolean>} true si se guardó
 */
export function editarCasoEnDialogo({ titulo, caso = null, temas, guardar }) {
  return new Promise((resolver) => {
    const pregunta = campo({
      nombre: 'pregunta',
      etiqueta: 'Pregunta de prueba',
      pista: 'Escríbela como la haría un jugador. Mejor si es una pregunta real.',
      multilinea: true,
      valor: caso?.pregunta ?? '',
      atributos: { maxlength: 1000, rows: 2 },
    });
    const esperado = campo({
      nombre: 'temaClaveEsperada',
      etiqueta: 'Qué debería responder el asistente',
      opciones: [
        { valor: '', texto: DEBE_ESCALAR },
        ...temas.map((tema) => ({ valor: tema.clave, texto: tema.titulo })),
      ],
      valor: caso?.temaClaveEsperada ?? '',
    });
    const activo = h('input', {
      clase: 'casilla__entrada',
      atributos: { type: 'checkbox', name: 'activo' },
    });
    activo.checked = caso?.activo ?? true;
    const aviso = h('p', { clase: 'campo__error', atributos: { role: 'alert', hidden: true } });

    const formulario = h('form', {
      clase: 'pila',
      atributos: { novalidate: true },
      hijos: [
        pregunta.elemento,
        esperado.elemento,
        h('label', {
          clase: 'casilla',
          hijos: [activo, h('span', { texto: 'Activo (se usa al evaluar)' })],
        }),
        aviso,
      ],
    });
    const cancelar = h('button', {
      clase: 'boton boton--secundario',
      texto: 'Cancelar',
      atributos: { type: 'button' },
      datos: { accion: 'cancelar' },
    });
    const aceptar = h('button', {
      clase: 'boton boton--primario',
      texto: 'Guardar caso',
      atributos: { type: 'button' },
      datos: { accion: 'guardar-caso' },
    });
    const { cerrar } = abrirDialogo({
      titulo,
      cuerpo: formulario,
      acciones: [cancelar, aceptar],
      cerrableFuera: false,
    });

    function mostrarAviso(texto) {
      aviso.textContent = texto ?? '';
      aviso.hidden = !texto;
    }

    async function enviar() {
      const texto = pregunta.control.value.trim();
      pregunta.marcarError(texto ? null : 'Escribe la pregunta.');
      if (!texto) {
        return;
      }
      mostrarAviso(null);
      conCarga(aceptar, true, 'Guardando…');
      try {
        await guardar({
          pregunta: texto,
          temaClaveEsperada: esperado.control.value || null,
          activo: activo.checked,
        });
        cerrar();
        resolver(true);
      } catch (error) {
        conCarga(aceptar, false);
        mostrarAviso(
          error?.estado === 400
            ? 'El servicio no acepta ese caso: revisa la pregunta y el tema.'
            : textoDeErrorDelPanel(error).titulo,
        );
      }
    }

    formulario.addEventListener('submit', (evento) => {
      evento.preventDefault();
      enviar();
    });
    aceptar.addEventListener('click', enviar);
    cancelar.addEventListener('click', () => {
      cerrar();
      resolver(false);
    });
  });
}

/**
 * Temas de la versión que se está editando (la candidata) o, si no hay, de la
 * de producción. Sirven para elegir el «tema esperado» y para nombrar claves.
 *
 * @returns {Promise<Array<{clave: string, titulo: string}>>}
 */
async function temasDeReferencia(cliente, candidata, produccion) {
  if (candidata) {
    return cliente.listarTemas();
  }
  if (!produccion) {
    return [];
  }
  const archivo = await cliente.exportarVersion(produccion.id);
  return JSON.parse(await textoDeArchivo(archivo.contenido)).temas ?? [];
}

/**
 * Monta la pestaña.
 *
 * @param {HTMLElement} raiz
 * @param {{cliente: object, confirmarAccion?: typeof confirmar,
 *          editarCaso?: typeof editarCasoEnDialogo}} dependencias
 * @returns {{cargar: () => Promise<void>}}
 */
export function montarReentrenamiento(
  raiz,
  {
    cliente,
    confirmarAccion = confirmar,
    editarCaso: editarCasoConFormulario = editarCasoEnDialogo,
  },
) {
  const nota = h('p', { clase: 'panel-chatbot__nota t-meta', atributos: { role: 'status' } });
  const zonaDespliegue = h('section', { clase: 'pila', datos: { zona: 'despliegue' } });
  const zonaProduccion = h('section', { clase: 'pila', datos: { zona: 'produccion' } });
  const zonaCasos = h('section', { clase: 'pila', datos: { zona: 'casos' } });
  raiz.append(nota, zonaDespliegue, zonaProduccion, zonaCasos);

  let titulos = new Map();
  let temas = [];

  function avisar(texto) {
    nota.textContent = texto ?? '';
  }

  async function recargarYAvisar(texto = null) {
    await cargar();
    avisar(texto);
  }

  async function cargar() {
    pintarEstado(zonaDespliegue, estadoDeCarga({ filas: 2, etiqueta: 'Cargando…' }));
    vaciar(zonaProduccion);
    vaciar(zonaCasos);
    try {
      const [versiones, casos] = await Promise.all([
        cliente.listarVersiones(),
        cliente.listarCasos(),
      ]);
      const candidata = versiones.find((version) => version.estado === 'BORRADOR') ?? null;
      const produccion = versiones.find((version) => version.estado === 'PRODUCCION') ?? null;
      const referencia = await temasDeReferencia(cliente, candidata, produccion);
      actualizarTemas(referencia);
      pintarDespliegue(candidata);
      pintarProduccion(produccion, versiones);
      pintarCasos(casos);
    } catch (error) {
      pintarEstado(
        zonaDespliegue,
        estadoDeError({ ...textoDeErrorDelPanel(error), alReintentar: () => cargar() }),
      );
    }
  }

  function actualizarTemas(lista) {
    temas = lista.map((tema) => ({ clave: tema.clave, titulo: tema.titulo }));
    titulos = new Map(temas.map((tema) => [tema.clave, tema.titulo]));
  }

  // ------------------------------------------------ evaluar y desplegar

  function pintarDespliegue(candidata) {
    const encabezado = encabezadoDeSeccion({
      titulo: 'Evaluar y desplegar la candidata',
      detalle:
        'Se corren los casos de evaluación contra la candidata y contra producción. Solo se despliega si la candidata no acierta menos.',
    });
    if (!candidata) {
      vaciar(zonaDespliegue).append(
        encabezado,
        estadoVacio({
          titulo: 'No hay una versión candidata',
          detalle: 'Créala en «Base de conocimiento» y vuelve aquí para evaluarla y desplegarla.',
        }),
      );
      return;
    }

    const resultado = h('div', { datos: { zona: 'resultado' } });
    const evaluar = h('button', {
      clase: 'boton boton--secundario',
      texto: 'Evaluar sin desplegar',
      atributos: { type: 'button' },
      datos: { accion: 'evaluar' },
    });
    const desplegar = h('button', {
      clase: 'boton boton--primario',
      texto: `Desplegar la versión ${candidata.numero}`,
      atributos: { type: 'button' },
      datos: { accion: 'desplegar' },
    });

    evaluar.addEventListener('click', async () => {
      avisar(null);
      conCarga(evaluar, true, 'Evaluando…');
      try {
        const comparacion = await cliente.evaluarCandidata();
        vaciar(resultado).append(resultadoDeEvaluacion(comparacion, titulos));
      } catch (error) {
        avisar(textoDeFalloDeEvaluacion(error));
      } finally {
        conCarga(evaluar, false);
      }
    });

    desplegar.addEventListener('click', async () => {
      const seguro = await confirmarAccion({
        titulo: `¿Desplegar la versión ${candidata.numero}?`,
        mensaje:
          'Se evalúa primero. Si no rinde peor, pasa a producción y el asistente responde con ella desde el siguiente mensaje. Podrás revertir.',
        textoConfirmar: 'Desplegar',
        peligro: false,
      });
      if (!seguro) {
        return;
      }
      avisar(null);
      conCarga(desplegar, true, 'Desplegando…');
      try {
        const version = await cliente.desplegarCandidata();
        await recargarYAvisar(
          `La versión ${version.numero} está en producción: el asistente ya responde con ella.`,
        );
      } catch (error) {
        conCarga(desplegar, false);
        if (error?.estado === 409 && error.problema?.candidata) {
          // Rinde peor: el cuerpo trae los dos resultados y los casos fallidos.
          vaciar(resultado).append(
            resultadoDeEvaluacion({ ...error.problema, candidataApta: false }, titulos),
          );
          avisar('No se desplegó: la candidata rinde peor que producción.');
          return;
        }
        avisar(textoDeFalloDeEvaluacion(error));
      }
    });

    vaciar(zonaDespliegue).append(
      encabezado,
      h('div', { clase: 'acciones', hijos: [evaluar, desplegar] }),
      resultado,
    );
  }

  function textoDeFalloDeEvaluacion(error) {
    if (error?.estado === 409) {
      return 'No se pudo evaluar: se necesita una candidata y al menos un caso de evaluación activo.';
    }
    return textoDeErrorDelPanel(error).titulo;
  }

  // ------------------------------------------------------------- revertir

  function pintarProduccion(produccion, versiones) {
    const hayAnterior = versiones.some((version) => version.estado === 'RETIRADA');
    const revertir = h('button', {
      clase: 'boton boton--peligro',
      texto: 'Revertir a la versión anterior',
      atributos: { type: 'button', disabled: !hayAnterior },
      datos: { accion: 'revertir' },
    });
    revertir.addEventListener('click', async () => {
      const seguro = await confirmarAccion({
        titulo: '¿Revertir a la versión anterior?',
        mensaje:
          'La versión en producción se retira y vuelve la que estaba antes. El asistente responde con ella desde el siguiente mensaje.',
        textoConfirmar: 'Revertir',
      });
      if (!seguro) {
        return;
      }
      let mensaje;
      try {
        const restaurada = await cliente.revertir();
        mensaje = `Se restauró la versión ${restaurada.numero}.`;
      } catch (error) {
        mensaje =
          error?.estado === 409
            ? 'No hay una versión anterior a la cual revertir.'
            : textoDeErrorDelPanel(error).titulo;
      }
      await recargarYAvisar(mensaje);
    });

    vaciar(zonaProduccion).append(
      encabezadoDeSeccion({
        titulo: produccion
          ? `En producción: versión ${produccion.numero}`
          : 'No hay una versión en producción',
        detalle: hayAnterior
          ? 'Si una versión nueva empeoró las respuestas, puedes volver a la anterior.'
          : 'Todavía no hay una versión anterior a la cual volver.',
        acciones: [revertir],
      }),
    );
  }

  // ----------------------------------------------------- casos de prueba

  function pintarCasos(casos) {
    const agregar = h('button', {
      clase: 'boton boton--primario',
      texto: 'Agregar caso',
      atributos: { type: 'button' },
      datos: { accion: 'agregar-caso' },
    });
    agregar.addEventListener('click', async () => {
      const guardado = await editarCasoConFormulario({
        titulo: 'Agregar caso de evaluación',
        temas,
        guardar: (datos) => cliente.crearCaso(datos),
      });
      if (guardado) {
        await recargarYAvisar('Caso agregado.');
      }
    });

    const activos = casos.filter((caso) => caso.activo).length;
    zonaCasos.append(
      encabezadoDeSeccion({
        titulo: 'Casos de evaluación',
        detalle: `${numero(activos)} activos de ${numero(casos.length)}. Agrega preguntas reales de los jugadores: son las que de verdad dicen si una versión es mejor.`,
        acciones: [agregar],
      }),
      casos.length === 0
        ? estadoVacio({
            titulo: 'No hay casos de evaluación',
            detalle: 'Sin casos no se puede desplegar: cualquier candidata pasaría la evaluación.',
          })
        : tablaDeCasos(casos),
    );
  }

  function tablaDeCasos(casos) {
    const filas = casos.map((caso) => {
      const editar = h('button', {
        clase: 'boton boton--contorno boton--pequeno',
        texto: 'Editar',
        atributos: { type: 'button', 'aria-label': `Editar el caso ${caso.pregunta}` },
        datos: { accion: 'editar-caso', caso: caso.id },
      });
      const eliminar = h('button', {
        clase: 'boton boton--peligro boton--pequeno',
        texto: 'Eliminar',
        atributos: { type: 'button', 'aria-label': `Eliminar el caso ${caso.pregunta}` },
        datos: { accion: 'eliminar-caso', caso: caso.id },
      });
      editar.addEventListener('click', async () => {
        const guardado = await editarCasoConFormulario({
          titulo: 'Editar caso de evaluación',
          caso,
          temas,
          guardar: (datos) => cliente.editarCaso(caso.id, datos),
        });
        if (guardado) {
          await recargarYAvisar('Caso actualizado.');
        }
      });
      eliminar.addEventListener('click', async () => {
        const seguro = await confirmarAccion({
          titulo: '¿Eliminar el caso?',
          mensaje: `«${caso.pregunta}» dejará de usarse al evaluar.`,
          textoConfirmar: 'Eliminar',
        });
        if (!seguro) {
          return;
        }
        let mensaje = null;
        try {
          await cliente.eliminarCaso(caso.id);
        } catch (error) {
          mensaje = textoDeErrorDelPanel(error).titulo;
        }
        await recargarYAvisar(mensaje);
      });

      return h('tr', {
        datos: { caso: caso.id },
        hijos: [
          h('td', { texto: caso.pregunta }),
          h('td', { texto: nombreDeTema(caso.temaClaveEsperada, titulos) }),
          h('td', {
            hijos: [
              caso.activo
                ? distintivo('Activo', 'activo')
                : distintivo('Inactivo', 'no-disponible'),
            ],
          }),
          h('td', { hijos: [h('div', { clase: 'acciones', hijos: [editar, eliminar] })] }),
        ],
      });
    });

    return h('table', {
      clase: 'tabla-panel',
      datos: { zona: 'tabla-casos' },
      hijos: [
        h('thead', {
          hijos: [
            h('tr', {
              hijos: ['Pregunta', 'Qué debería responder', 'Estado', ''].map((texto) =>
                h('th', { texto, atributos: { scope: 'col' } }),
              ),
            }),
          ],
        }),
        h('tbody', { hijos: filas }),
      ],
    });
  }

  return { cargar };
}
