/**
 * Pestaña «Base de conocimiento» del panel del asistente — HU-CHA-012
 * (RF-CHA-013).
 *
 * La versión en producción NUNCA se edita desde aquí. Se trabaja sobre una
 * versión candidata (copia de producción): se agregan, editan o eliminan
 * temas, o se reemplaza todo importando un archivo. La candidata llega a los
 * jugadores solo al desplegarla desde «Reentrenamiento», después de
 * evaluarla.
 *
 * Un tema es: su intención (categoría), las variantes de pregunta con las que
 * un jugador lo pediría, la respuesta, y una prioridad para desempatar.
 *
 * @module cuentas/panel-chatbot-base
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
import { fechaHora, numero } from '../comun/ui/formato.js';
import { encabezadoDeSeccion } from '../comun/ui/pagina.js';
import { textoDeErrorDelPanel } from './panel-chatbot-analiticas.js';

/** Categorías del contrato (`Categoria`), con su nombre para leer. */
export const CATEGORIAS = Object.freeze({
  PRODUCTO: 'Productos',
  MECANICA_JUEGO: 'Mecánicas del juego',
  MODALIDAD_JUEGO: 'Modalidades de juego',
  CUENTA_Y_REGISTRO: 'Cuenta y registro',
  SUBASTA_Y_COMERCIO: 'Subastas y comercio',
  SOPORTE_TECNICO: 'Soporte técnico',
  POLITICAS_Y_TERMINOS: 'Políticas y términos',
  FAQ_GENERAL: 'Preguntas frecuentes',
});

/** Tipos de respuesta del contrato (`TipoRespuesta`). */
export const TIPOS_DE_RESPUESTA = Object.freeze({
  DIRECTA: 'Directa',
  PASO_A_PASO: 'Paso a paso',
  SUGERENCIA_PROACTIVA: 'Sugerencia proactiva',
  CONTEXTUAL: 'Contextual',
});

/** Estados de una versión (`EstadoVersion`) y cómo se pintan. */
export const ESTADOS_DE_VERSION = Object.freeze({
  BORRADOR: { texto: 'Candidata', variante: 'en-progreso' },
  PRODUCCION: { texto: 'En producción', variante: 'activo' },
  RETIRADA: { texto: 'Retirada', variante: 'no-disponible' },
});

/**
 * Variantes escritas una por línea → lista limpia.
 *
 * @param {string} texto
 * @returns {string[]}
 */
export function variantesDesdeTexto(texto) {
  return String(texto ?? '')
    .split('\n')
    .map((linea) => linea.trim())
    .filter(Boolean);
}

/**
 * Valida los datos de un tema antes de enviarlos. Devuelve los errores por
 * campo; vacío si todo está bien.
 *
 * El motor guarda las variantes separadas por coma: una variante con coma se
 * partiría en dos sin que el administrador lo note (el servicio también la
 * rechaza, pero es mejor decirlo antes de enviar).
 *
 * @param {{titulo: string, variantesEs: string[], variantesEn: string[],
 *          respuestaEs: string, prioridad: number}} datos
 * @returns {Record<string, string>}
 */
export function erroresDelTema(datos) {
  const errores = {};
  if (!datos.titulo.trim()) {
    errores.titulo = 'Escribe un título.';
  }
  if (datos.variantesEs.length === 0) {
    errores.variantesEs = 'Escribe al menos una variante en español.';
  }
  if ([...datos.variantesEs, ...datos.variantesEn].some((variante) => variante.includes(','))) {
    errores[datos.variantesEs.some((v) => v.includes(',')) ? 'variantesEs' : 'variantesEn'] =
      'Una variante no puede llevar comas: escríbela en dos líneas.';
  }
  if (!datos.respuestaEs.trim()) {
    errores.respuestaEs = 'Escribe la respuesta en español.';
  }
  if (!Number.isInteger(datos.prioridad) || datos.prioridad < 0) {
    errores.prioridad = 'La prioridad es un número entero desde 0.';
  }
  return errores;
}

/**
 * Lee y comprueba un archivo de exportación antes de importarlo.
 *
 * @param {string} texto contenido del archivo
 * @returns {{temas: object[]}} el mismo objeto, listo para enviar
 * @throws {Error} con un mensaje para el administrador
 */
export function leerArchivoDeExportacion(texto) {
  let datos;
  try {
    datos = JSON.parse(texto);
  } catch {
    throw new Error('El archivo no es un JSON válido.');
  }
  if (!datos || !Array.isArray(datos.temas) || datos.temas.length === 0) {
    throw new Error('El archivo no trae temas: usa uno descargado con «Exportar».');
  }
  return datos;
}

/**
 * Texto de un archivo elegido por el usuario. `File.text()` no existe en
 * navegadores viejos; `FileReader` sí.
 *
 * @param {Blob} archivo
 * @returns {Promise<string>}
 */
export function textoDeArchivo(archivo) {
  if (typeof archivo.text === 'function') {
    return archivo.text();
  }
  return new Promise((resolver, rechazar) => {
    const lector = new FileReader();
    lector.onload = () => resolver(String(lector.result ?? ''));
    lector.onerror = () => rechazar(lector.error);
    lector.readAsText(archivo);
  });
}

function selector({ nombre, etiqueta, opciones, valor }) {
  return campo({
    nombre,
    etiqueta,
    valor,
    opciones: Object.entries(opciones).map(([clave, texto]) => ({ valor: clave, texto })),
  });
}

/**
 * Formulario de un tema en un diálogo. Resuelve con los datos listos para el
 * servicio, o con null si se cancela. `guardar` hace la llamada: si falla,
 * el diálogo sigue abierto y muestra el motivo.
 *
 * @param {{titulo: string, tema?: object|null,
 *          guardar: (datos: object) => Promise<void>}} opciones
 * @returns {Promise<boolean>} true si se guardó
 */
export function editarTemaEnDialogo({ titulo, tema = null, guardar }) {
  return new Promise((resolver) => {
    const campos = {
      titulo: campo({
        nombre: 'titulo',
        etiqueta: 'Título',
        valor: tema?.titulo ?? '',
        requerido: true,
        atributos: { maxlength: 150 },
      }),
      categoria: selector({
        nombre: 'categoria',
        etiqueta: 'Intención (categoría)',
        opciones: CATEGORIAS,
        valor: tema?.categoria ?? 'FAQ_GENERAL',
      }),
      tipoRespuesta: selector({
        nombre: 'tipoRespuesta',
        etiqueta: 'Tipo de respuesta',
        opciones: TIPOS_DE_RESPUESTA,
        valor: tema?.tipoRespuesta ?? 'DIRECTA',
      }),
      variantesEs: campo({
        nombre: 'variantesEs',
        etiqueta: 'Variantes de pregunta en español',
        pista: 'Una por línea, sin comas. Son las frases con las que un jugador lo preguntaría.',
        multilinea: true,
        valor: (tema?.variantesEs ?? []).join('\n'),
      }),
      variantesEn: campo({
        nombre: 'variantesEn',
        etiqueta: 'Variantes de pregunta en inglés (opcional)',
        multilinea: true,
        valor: (tema?.variantesEn ?? []).join('\n'),
      }),
      respuestaEs: campo({
        nombre: 'respuestaEs',
        etiqueta: 'Respuesta en español',
        multilinea: true,
        valor: tema?.respuestaEs ?? '',
        atributos: { maxlength: 4000 },
      }),
      respuestaEn: campo({
        nombre: 'respuestaEn',
        etiqueta: 'Respuesta en inglés (opcional)',
        multilinea: true,
        valor: tema?.respuestaEn ?? '',
        atributos: { maxlength: 4000 },
      }),
      prioridad: campo({
        nombre: 'prioridad',
        etiqueta: 'Prioridad',
        tipo: 'number',
        pista:
          'Si dos temas coinciden igual de bien con una pregunta, responde el de mayor prioridad.',
        valor: String(tema?.prioridad ?? 0),
        atributos: { min: 0, step: 1 },
      }),
    };
    const activo = h('input', {
      clase: 'casilla__entrada',
      atributos: { type: 'checkbox', name: 'activo' },
    });
    activo.checked = tema?.activo ?? true;
    const casilla = h('label', {
      clase: 'casilla',
      hijos: [activo, h('span', { texto: 'Activo (el asistente lo usa para responder)' })],
    });
    const aviso = h('p', { clase: 'campo__error', atributos: { role: 'alert', hidden: true } });

    const formulario = h('form', {
      clase: 'pila panel-chatbot__formulario-tema',
      atributos: { novalidate: true },
      hijos: [...Object.values(campos).map((c) => c.elemento), casilla, aviso],
    });

    const cancelar = h('button', {
      clase: 'boton boton--secundario',
      texto: 'Cancelar',
      atributos: { type: 'button' },
      datos: { accion: 'cancelar' },
    });
    const aceptar = h('button', {
      clase: 'boton boton--primario',
      texto: 'Guardar tema',
      atributos: { type: 'button' },
      datos: { accion: 'guardar-tema' },
    });

    const { elemento, cerrar } = abrirDialogo({
      titulo,
      cuerpo: formulario,
      acciones: [cancelar, aceptar],
      cerrableFuera: false,
    });
    elemento.classList.add('dialogo--ancho');

    function leer() {
      return {
        titulo: campos.titulo.control.value.trim(),
        categoria: campos.categoria.control.value,
        tipoRespuesta: campos.tipoRespuesta.control.value,
        variantesEs: variantesDesdeTexto(campos.variantesEs.control.value),
        variantesEn: variantesDesdeTexto(campos.variantesEn.control.value),
        respuestaEs: campos.respuestaEs.control.value.trim(),
        respuestaEn: campos.respuestaEn.control.value.trim() || null,
        prioridad: Number(campos.prioridad.control.value || 0),
        activo: activo.checked,
      };
    }

    function mostrarAviso(texto) {
      aviso.textContent = texto ?? '';
      aviso.hidden = !texto;
    }

    async function enviar() {
      const datos = leer();
      const errores = erroresDelTema(datos);
      for (const [nombre, control] of Object.entries(campos)) {
        control.marcarError(errores[nombre] ?? null);
      }
      if (Object.keys(errores).length > 0) {
        mostrarAviso('Revisa los campos marcados.');
        return;
      }
      mostrarAviso(null);
      conCarga(aceptar, true, 'Guardando…');
      try {
        await guardar(datos);
        cerrar();
        resolver(true);
      } catch (error) {
        conCarga(aceptar, false);
        mostrarAviso(
          error?.estado === 400
            ? (error.detalle ?? 'El servicio rechazó los datos del tema.')
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
 * Monta la pestaña.
 *
 * @param {HTMLElement} raiz
 * @param {{cliente: object, descargarArchivo: Function,
 *          confirmarAccion?: typeof confirmar,
 *          editarTema?: typeof editarTemaEnDialogo}} dependencias
 * @returns {{cargar: () => Promise<void>}}
 */
export function montarBaseConocimiento(
  raiz,
  {
    cliente,
    descargarArchivo,
    confirmarAccion = confirmar,
    editarTema: editarTemaConFormulario = editarTemaEnDialogo,
  },
) {
  const nota = h('p', { clase: 'panel-chatbot__nota t-meta', atributos: { role: 'status' } });
  const zonaVersiones = h('section', { clase: 'pila', datos: { zona: 'versiones' } });
  const zonaCandidata = h('section', { clase: 'pila', datos: { zona: 'candidata' } });
  raiz.append(nota, zonaCandidata, zonaVersiones);

  function avisar(texto) {
    nota.textContent = texto ?? '';
  }

  // La nota de resultado (p. ej. «Se importaron 12 temas») se escribe
  // DESPUÉS de recargar: cargar() no la toca.
  async function recargarYAvisar(texto = null) {
    await cargar();
    avisar(texto);
  }

  async function cargar() {
    pintarEstado(zonaCandidata, estadoDeCarga({ filas: 3, etiqueta: 'Cargando versiones…' }));
    vaciar(zonaVersiones);
    try {
      const versiones = await cliente.listarVersiones();
      const candidata = versiones.find((version) => version.estado === 'BORRADOR') ?? null;
      pintarVersiones(versiones);
      if (candidata) {
        await pintarCandidata(candidata);
      } else {
        pintarSinCandidata();
      }
    } catch (error) {
      pintarEstado(
        zonaCandidata,
        estadoDeError({ ...textoDeErrorDelPanel(error), alReintentar: () => cargar() }),
      );
    }
  }

  // ------------------------------------------------------------- versiones

  function pintarVersiones(versiones) {
    const filas = versiones.map((version) => {
      const estado = ESTADOS_DE_VERSION[version.estado] ?? { texto: version.estado };
      const exportar = h('button', {
        clase: 'boton boton--contorno boton--pequeno',
        texto: 'Exportar',
        atributos: {
          type: 'button',
          'aria-label': `Exportar la versión ${version.numero}`,
        },
        datos: { accion: 'exportar-version', version: version.id },
      });
      exportar.addEventListener('click', async () => {
        conCarga(exportar, true, 'Preparando…');
        try {
          descargarArchivo(await cliente.exportarVersion(version.id));
        } catch (error) {
          avisar(textoDeErrorDelPanel(error).titulo);
        } finally {
          conCarga(exportar, false);
        }
      });
      return h('tr', {
        hijos: [
          h('td', { texto: numero(version.numero) }),
          h('td', { hijos: [distintivo(estado.texto, estado.variante)] }),
          h('td', { texto: version.descripcion ?? '—' }),
          h('td', { texto: fechaHora(version.fechaCreacion) }),
          h('td', { texto: fechaHora(version.fechaDespliegue) }),
          h('td', { hijos: [exportar] }),
        ],
      });
    });

    zonaVersiones.append(
      encabezadoDeSeccion({
        titulo: 'Versiones',
        detalle: 'La que está en producción es la que responde a los jugadores.',
      }),
      h('table', {
        clase: 'tabla-panel',
        hijos: [
          h('thead', {
            hijos: [
              h('tr', {
                hijos: ['Versión', 'Estado', 'Descripción', 'Creada', 'Desplegada', ''].map(
                  (texto) => h('th', { texto, atributos: { scope: 'col' } }),
                ),
              }),
            ],
          }),
          h('tbody', { hijos: filas }),
        ],
      }),
    );
  }

  // ------------------------------------------------------ sin candidata

  function pintarSinCandidata() {
    const descripcion = campo({
      nombre: 'descripcion',
      etiqueta: 'Qué vas a cambiar (opcional)',
      atributos: { maxlength: 500 },
    });
    const crear = h('button', {
      clase: 'boton boton--primario',
      texto: 'Crear candidata',
      atributos: { type: 'submit' },
    });
    const formulario = h('form', {
      clase: 'panel-chatbot__filtros',
      atributos: { novalidate: true },
      hijos: [descripcion.elemento, crear],
    });
    formulario.addEventListener('submit', async (evento) => {
      evento.preventDefault();
      conCarga(crear, true, 'Creando…');
      let problema = null;
      try {
        await cliente.crearCandidata(descripcion.control.value.trim());
      } catch (error) {
        problema = textoDeErrorDelPanel(error).titulo;
      }
      // Tanto si se creó como si otro administrador se adelantó (409), lo
      // que hay que mostrar es el estado real.
      await recargarYAvisar(problema);
    });

    vaciar(zonaCandidata).append(
      encabezadoDeSeccion({
        titulo: 'Versión candidata',
        detalle:
          'Para cambiar lo que sabe el asistente, crea una candidata: es una copia de la versión en producción que puedes editar sin afectar a los jugadores.',
      }),
      estadoVacio({ titulo: 'No hay una versión candidata' }),
      formulario,
    );
  }

  // ------------------------------------------------------- con candidata

  async function pintarCandidata(candidata) {
    const temas = await cliente.listarTemas();

    const agregar = h('button', {
      clase: 'boton boton--primario',
      texto: 'Agregar tema',
      atributos: { type: 'button' },
      datos: { accion: 'agregar-tema' },
    });
    const archivo = h('input', {
      clase: 'solo-lectores',
      atributos: { type: 'file', accept: 'application/json,.json', 'aria-label': 'Archivo JSON' },
    });
    const importar = h('button', {
      clase: 'boton boton--secundario',
      texto: 'Importar JSON',
      atributos: { type: 'button' },
      datos: { accion: 'importar-temas' },
    });
    const descartar = h('button', {
      clase: 'boton boton--peligro',
      texto: 'Descartar candidata',
      atributos: { type: 'button' },
      datos: { accion: 'descartar-candidata' },
    });

    agregar.addEventListener('click', async () => {
      const guardado = await editarTemaConFormulario({
        titulo: 'Agregar tema',
        guardar: (datos) => cliente.agregarTema(datos),
      });
      if (guardado) {
        await cargar();
      }
    });

    importar.addEventListener('click', () => archivo.click());
    archivo.addEventListener('change', async () => {
      const elegido = archivo.files?.[0];
      archivo.value = '';
      if (elegido) {
        await importarArchivo(elegido);
      }
    });

    descartar.addEventListener('click', async () => {
      const seguro = await confirmarAccion({
        titulo: '¿Descartar la candidata?',
        mensaje: `Se borrará la versión ${candidata.numero} con todos sus cambios. La versión en producción no se toca.`,
        textoConfirmar: 'Descartar',
      });
      if (!seguro) {
        return;
      }
      let problema = null;
      try {
        await cliente.descartarCandidata();
      } catch (error) {
        problema = textoDeErrorDelPanel(error).titulo;
      }
      await recargarYAvisar(problema);
    });

    vaciar(zonaCandidata).append(
      encabezadoDeSeccion({
        titulo: `Versión candidata ${candidata.numero}`,
        detalle: candidata.descripcion ?? 'Sin descripción.',
        acciones: [agregar, importar, descartar],
      }),
      archivo,
      tablaDeTemas(temas),
    );
  }

  function tablaDeTemas(temas) {
    if (temas.length === 0) {
      return estadoVacio({
        titulo: 'La candidata no tiene temas',
        detalle: 'Agrega uno o importa un archivo exportado.',
      });
    }
    const filas = temas.map((tema) => {
      const editar = h('button', {
        clase: 'boton boton--contorno boton--pequeno',
        texto: 'Editar',
        atributos: { type: 'button', 'aria-label': `Editar el tema ${tema.titulo}` },
        datos: { accion: 'editar-tema', tema: tema.id },
      });
      const eliminar = h('button', {
        clase: 'boton boton--peligro boton--pequeno',
        texto: 'Eliminar',
        atributos: { type: 'button', 'aria-label': `Eliminar el tema ${tema.titulo}` },
        datos: { accion: 'eliminar-tema', tema: tema.id },
      });

      editar.addEventListener('click', async () => {
        const guardado = await editarTemaConFormulario({
          titulo: 'Editar tema',
          tema,
          guardar: (datos) => cliente.editarTema(tema.id, datos),
        });
        if (guardado) {
          await cargar();
        }
      });
      eliminar.addEventListener('click', async () => {
        const seguro = await confirmarAccion({
          titulo: '¿Eliminar el tema?',
          mensaje: `«${tema.titulo}» se quitará de la candidata. Producción no cambia hasta que despliegues.`,
          textoConfirmar: 'Eliminar',
        });
        if (!seguro) {
          return;
        }
        let problema = null;
        try {
          await cliente.eliminarTema(tema.id);
        } catch (error) {
          problema = textoDeErrorDelPanel(error).titulo;
        }
        await recargarYAvisar(problema);
      });

      return h('tr', {
        datos: { tema: tema.id },
        hijos: [
          h('td', {
            hijos: [
              h('p', { clase: 't-cuerpo', texto: tema.titulo }),
              h('p', { clase: 't-meta', texto: (tema.variantesEs ?? []).join(' · ') }),
            ],
          }),
          h('td', { texto: CATEGORIAS[tema.categoria] ?? tema.categoria }),
          h('td', { texto: numero(tema.prioridad) }),
          h('td', {
            hijos: [
              tema.activo
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
      datos: { zona: 'temas-candidata' },
      hijos: [
        h('thead', {
          hijos: [
            h('tr', {
              hijos: ['Tema y variantes', 'Intención', 'Prioridad', 'Estado', ''].map((texto) =>
                h('th', { texto, atributos: { scope: 'col' } }),
              ),
            }),
          ],
        }),
        h('tbody', { hijos: filas }),
      ],
    });
  }

  async function importarArchivo(elegido) {
    avisar(null);
    let datos;
    try {
      datos = leerArchivoDeExportacion(await textoDeArchivo(elegido));
    } catch (error) {
      avisar(error.message);
      return;
    }
    const seguro = await confirmarAccion({
      titulo: '¿Reemplazar los temas de la candidata?',
      mensaje: `Se borrarán todos los temas de la candidata y se cargarán los ${datos.temas.length} del archivo.`,
      textoConfirmar: 'Importar',
    });
    if (!seguro) {
      return;
    }
    let resultado;
    try {
      const importados = await cliente.importarTemas(datos);
      resultado = `Se importaron ${importados.length} temas.`;
    } catch (error) {
      resultado =
        error?.estado === 400
          ? (error.detalle ?? 'El archivo tiene temas que el servicio no acepta.')
          : textoDeErrorDelPanel(error).titulo;
    }
    await recargarYAvisar(resultado);
  }

  return { cargar };
}
