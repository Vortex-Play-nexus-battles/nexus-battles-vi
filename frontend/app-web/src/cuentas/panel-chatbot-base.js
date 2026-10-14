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
 * ms-chatbot.yaml 1.6.0 (7.4.10): la misma respuesta puede atender otras
 * intenciones además de su categoría («También responde a», hasta 5). Un
 * título repetido en la candidata, un archivo demasiado grande o un cambio
 * hecho a la vez por otro administrador llegan con su `motivo` y se explican.
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

/** Cuántas intenciones puede atender un tema además de su categoría (1.6.0). */
export const MAXIMO_DE_INTENCIONES_ADICIONALES = 5;

/** 409 `CAMBIO_CONCURRENTE` (1.6.0): otro administrador cambió las versiones a la vez. */
export const TEXTO_CAMBIO_CONCURRENTE =
  'Otro administrador cambió la base de conocimiento al mismo tiempo. Se muestra el estado actual: revísalo y vuelve a intentarlo.';

/**
 * @param {unknown} error
 * @returns {boolean} si el servicio respondió 409 `CAMBIO_CONCURRENTE`
 */
export function esCambioConcurrente(error) {
  return error?.problema?.motivo === 'CAMBIO_CONCURRENTE';
}

/**
 * Texto de un fallo de esta pestaña: por el `motivo` (ms-chatbot.yaml
 * 1.6.0), luego por el estado.
 *
 * @param {{estado?: number, detalle?: string|null,
 *          problema?: {motivo?: string}|null}} error
 * @param {string} [siRechazado] texto de un 400 que no trae detalle
 * @returns {string}
 */
export function textoDeErrorDeLaBase(error, siRechazado = 'El servicio rechazó los datos.') {
  const motivo = error?.problema?.motivo;
  if (motivo === 'TEMA_DUPLICADO') {
    return error.detalle ?? 'Ya hay un tema con ese título en la candidata.';
  }
  if (motivo === 'CAMBIO_CONCURRENTE') {
    return TEXTO_CAMBIO_CONCURRENTE;
  }
  if (motivo === 'IMPORTACION_DEMASIADO_GRANDE' || error?.estado === 413) {
    // El 413 del borde (más de 10 MB) llega sin cuerpo del servicio.
    return (
      error.detalle ?? 'El archivo es demasiado grande: se importan como máximo 500 temas y 8 MB.'
    );
  }
  if (error?.estado === 400) {
    return error.detalle ?? siRechazado;
  }
  return textoDeErrorDelPanel(error).titulo;
}

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
 * Desde 1.6.0 también las intenciones adicionales: como mucho
 * {@link MAXIMO_DE_INTENCIONES_ADICIONALES} y nunca la categoría principal
 * (el servicio responde 400 en los dos casos).
 *
 * @param {{titulo: string, categoria?: string, intencionesAdicionales?: string[],
 *          variantesEs: string[], variantesEn: string[],
 *          respuestaEs: string, prioridad: number}} datos
 * @returns {Record<string, string>}
 */
export function erroresDelTema(datos) {
  const errores = {};
  if (!datos.titulo.trim()) {
    errores.titulo = 'Escribe un título.';
  }
  const intenciones = datos.intencionesAdicionales ?? [];
  if (intenciones.length > MAXIMO_DE_INTENCIONES_ADICIONALES) {
    errores.intencionesAdicionales = `Marca como máximo ${MAXIMO_DE_INTENCIONES_ADICIONALES} intenciones además de la principal.`;
  } else if (intenciones.includes(datos.categoria)) {
    errores.intencionesAdicionales =
      'La intención principal ya la atiende: no la marques también aquí.';
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

let gruposDeIntenciones = 0;

/**
 * Casillas «También responde a» (ms-chatbot.yaml 1.6.0): otras intenciones
 * que atiende la misma respuesta, con los nombres de {@link CATEGORIAS}. La
 * de la categoría principal se desactiva, y se desmarca, mientras lo sea.
 *
 * @param {{principal: string, marcadas?: string[]}} opciones
 * @returns {{elemento: HTMLElement, leer: () => string[],
 *           cambiarPrincipal: (categoria: string) => void,
 *           marcarError: (motivo: string|null) => void}}
 */
export function casillasDeIntenciones({ principal, marcadas = [] }) {
  gruposDeIntenciones += 1;
  const idPista = `intenciones-${gruposDeIntenciones}-pista`;
  const idError = `intenciones-${gruposDeIntenciones}-error`;

  const casillas = Object.entries(CATEGORIAS).map(([valor, texto]) => {
    const entrada = h('input', {
      clase: 'casilla__entrada',
      atributos: { type: 'checkbox', name: 'intencionesAdicionales', value: valor },
    });
    entrada.checked = marcadas.includes(valor);
    const etiqueta = h('label', {
      clase: 'casilla',
      hijos: [entrada, h('span', { clase: 'casilla__etiqueta', texto })],
    });
    return { valor, entrada, etiqueta };
  });
  const pista = h('p', {
    clase: 'campo__pista',
    texto: `Otras intenciones que atiende la misma respuesta, hasta ${MAXIMO_DE_INTENCIONES_ADICIONALES}. La principal es la de arriba.`,
    atributos: { id: idPista },
  });
  const error = h('p', { clase: 'campo__error', atributos: { id: idError, hidden: true } });
  const elemento = h('fieldset', {
    clase: 'campo panel-chatbot__intenciones',
    datos: { campo: 'intencionesAdicionales' },
    atributos: { 'aria-describedby': idPista },
    hijos: [
      h('legend', { clase: 'campo__etiqueta', texto: 'También responde a' }),
      h('div', { clase: 'panel-chatbot__casillas', hijos: casillas.map((c) => c.etiqueta) }),
      pista,
      error,
    ],
  });

  function cambiarPrincipal(categoria) {
    for (const casilla of casillas) {
      const esLaPrincipal = casilla.valor === categoria;
      casilla.entrada.disabled = esLaPrincipal;
      if (esLaPrincipal) {
        casilla.entrada.checked = false;
      }
    }
  }
  cambiarPrincipal(principal);

  return {
    elemento,
    leer: () => casillas.filter((c) => c.entrada.checked).map((c) => c.valor),
    cambiarPrincipal,
    marcarError(motivo) {
      elemento.classList.toggle('campo--invalido', Boolean(motivo));
      error.textContent = motivo ?? '';
      error.hidden = !motivo;
      elemento.setAttribute('aria-describedby', motivo ? `${idPista} ${idError}` : idPista);
    },
  };
}

/**
 * Nombres legibles de las intenciones adicionales de un tema, en el orden del
 * contrato.
 *
 * @param {string[]|undefined} intenciones
 * @returns {string[]}
 */
export function nombresDeIntenciones(intenciones) {
  return (intenciones ?? []).map((intencion) => CATEGORIAS[intencion] ?? intencion);
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

    // 1.6.0: «También responde a», justo debajo de la intención principal.
    const intenciones = casillasDeIntenciones({
      principal: campos.categoria.control.value,
      marcadas: tema?.intencionesAdicionales ?? [],
    });
    campos.categoria.control.addEventListener('change', () =>
      intenciones.cambiarPrincipal(campos.categoria.control.value),
    );
    const elementos = Object.entries(campos).flatMap(([nombre, control]) =>
      nombre === 'categoria' ? [control.elemento, intenciones.elemento] : [control.elemento],
    );

    const formulario = h('form', {
      clase: 'pila panel-chatbot__formulario-tema',
      atributos: { novalidate: true },
      hijos: [...elementos, casilla, aviso],
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
        intencionesAdicionales: intenciones.leer(),
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
      intenciones.marcarError(errores.intencionesAdicionales ?? null);
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
        // 400 (con su motivo), 409 TEMA_DUPLICADO (dice con qué tema
        // choca) o 409 CAMBIO_CONCURRENTE: el diálogo sigue abierto.
        mostrarAviso(textoDeErrorDeLaBase(error, 'El servicio rechazó los datos del tema.'));
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
        problema = textoDeErrorDeLaBase(error);
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
        problema = textoDeErrorDeLaBase(error);
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
          problema = textoDeErrorDeLaBase(error);
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
          h('td', {
            hijos: [
              h('p', { clase: 't-cuerpo', texto: CATEGORIAS[tema.categoria] ?? tema.categoria }),
              // 1.6.0: las demás intenciones que atiende la misma respuesta.
              tema.intencionesAdicionales?.length
                ? h('p', {
                    clase: 't-meta',
                    texto: `También: ${nombresDeIntenciones(tema.intencionesAdicionales).join(' · ')}`,
                  })
                : null,
            ],
          }),
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
      // 1.6.0: también 409 TEMA_DUPLICADO (dos temas del archivo con el
      // mismo título) y 413 (más de 500 temas o de 8 MB). No se escribió nada.
      resultado = textoDeErrorDeLaBase(error, 'El archivo tiene temas que el servicio no acepta.');
    }
    await recargarYAvisar(resultado);
  }

  return { cargar };
}
