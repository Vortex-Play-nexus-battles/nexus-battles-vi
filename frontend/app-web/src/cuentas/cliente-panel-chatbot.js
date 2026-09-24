/**
 * Acceso HTTP al panel de administración del asistente — HU-CHA-012.
 *
 * Habla con `/api/v1/chatbot/admin/**` de `contracts/openapi/ms-chatbot.yaml`
 * (analíticas, base de conocimiento y reentrenamiento). Todas las rutas exigen
 * token con rol ADMINISTRADOR o SUPER_ADMINISTRADOR; la vista ya lo comprueba
 * con `exigirAcceso`, y el servicio lo vuelve a comprobar.
 *
 * Reutiliza de `comun/cliente-chatbot.js` la base de la API (mismo origen, o
 * `localStorage['nexus.chatbot.base']` en desarrollo) y `ErrorDelChatbot`.
 *
 * @module cuentas/cliente-panel-chatbot
 */

import { ErrorDelChatbot, leerProblema, rutaDelChatbot } from '../comun/cliente-chatbot.js';
import { leerSesion } from '../comun/sesion.js';

const PANEL = '/chatbot/admin';
const BASE_CONOCIMIENTO = `${PANEL}/base-conocimiento`;

/**
 * Nombre de archivo de una cabecera `Content-Disposition`.
 *
 * @param {string|null} cabecera
 * @param {string} alternativa
 * @returns {string}
 */
export function nombreDeArchivo(cabecera, alternativa) {
  const coincidencia = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(cabecera ?? '');
  if (!coincidencia) {
    return alternativa;
  }
  try {
    return decodeURIComponent(coincidencia[1].trim());
  } catch {
    return coincidencia[1].trim();
  }
}

/**
 * Parámetros `desde`/`hasta` (fechas ISO, `AAAA-MM-DD`) de un período.
 *
 * @param {{desde?: string|null, hasta?: string|null}} [periodo]
 * @returns {string} con `?` delante, o vacío
 */
export function consultaDePeriodo({ desde = null, hasta = null } = {}) {
  const parametros = new URLSearchParams();
  if (desde) {
    parametros.set('desde', desde);
  }
  if (hasta) {
    parametros.set('hasta', hasta);
  }
  const texto = parametros.toString();
  return texto ? `?${texto}` : '';
}

/**
 * @param {{fetch?: typeof fetch, almacenLocal?: Storage|null,
 *          sesion?: () => {token: string|null}}} [opciones]
 */
export function crearClientePanelChatbot({
  fetch: peticion = (...argumentos) => globalThis.fetch(...argumentos),
  almacenLocal,
  sesion = () => leerSesion(),
} = {}) {
  async function enviar(metodo, recurso, cuerpo) {
    const cabeceras = { Accept: 'application/json' };
    const token = sesion()?.token;
    if (token) {
      cabeceras.Authorization = `Bearer ${token}`;
    }
    if (cuerpo !== undefined) {
      cabeceras['Content-Type'] = 'application/json';
    }

    let respuesta;
    try {
      respuesta = await peticion(rutaDelChatbot(recurso, almacenLocal), {
        method: metodo,
        headers: cabeceras,
        body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
      });
    } catch {
      throw new ErrorDelChatbot(null, 0, { rutaFija: true });
    }
    if (!respuesta.ok) {
      // Las rutas del panel existen siempre; un 404 sin cuerpo del servicio
      // es que el asistente no está (rutaFija). Un 404 con problem+json del
      // servicio («no existe ese tema») trae título y no cuenta como tal.
      const problema = await leerProblema(respuesta);
      throw new ErrorDelChatbot(problema, respuesta.status, { rutaFija: !problema });
    }
    return respuesta;
  }

  async function json(metodo, recurso, cuerpo) {
    const respuesta = await enviar(metodo, recurso, cuerpo);
    return respuesta.status === 204 ? null : respuesta.json();
  }

  async function archivo(recurso, alternativa) {
    const respuesta = await enviar('GET', recurso);
    return {
      nombre: nombreDeArchivo(respuesta.headers?.get?.('Content-Disposition') ?? null, alternativa),
      contenido: await respuesta.blob(),
    };
  }

  return {
    // --- Analíticas (RF-CHA-012)
    analiticas: (periodo) => json('GET', `${PANEL}/analiticas${consultaDePeriodo(periodo)}`),
    exportarAnaliticas: (periodo) =>
      archivo(
        `${PANEL}/analiticas/exportacion${consultaDePeriodo(periodo)}`,
        'analiticas-chatbot.csv',
      ),

    // --- Base de conocimiento (RF-CHA-013)
    listarVersiones: () => json('GET', `${BASE_CONOCIMIENTO}/versiones`),
    exportarVersion: (versionId) =>
      archivo(
        `${BASE_CONOCIMIENTO}/versiones/${encodeURIComponent(versionId)}/exportacion`,
        'base-conocimiento.json',
      ),
    crearCandidata: (descripcion) =>
      json('POST', `${BASE_CONOCIMIENTO}/borrador`, { descripcion: descripcion || null }),
    descartarCandidata: () => json('DELETE', `${BASE_CONOCIMIENTO}/borrador`),
    listarTemas: () => json('GET', `${BASE_CONOCIMIENTO}/borrador/temas`),
    agregarTema: (datos) => json('POST', `${BASE_CONOCIMIENTO}/borrador/temas`, datos),
    editarTema: (temaId, datos) =>
      json('PUT', `${BASE_CONOCIMIENTO}/borrador/temas/${encodeURIComponent(temaId)}`, datos),
    eliminarTema: (temaId) =>
      json('DELETE', `${BASE_CONOCIMIENTO}/borrador/temas/${encodeURIComponent(temaId)}`),
    importarTemas: (archivoExportado) =>
      json('PUT', `${BASE_CONOCIMIENTO}/borrador/importacion`, archivoExportado),

    // --- Reentrenamiento (RF-CHA-014)
    evaluarCandidata: () => json('POST', `${BASE_CONOCIMIENTO}/borrador/evaluacion`),
    desplegarCandidata: () => json('POST', `${BASE_CONOCIMIENTO}/borrador/despliegue`),
    revertir: () => json('POST', `${BASE_CONOCIMIENTO}/produccion/reversion`),
    listarCasos: () => json('GET', `${BASE_CONOCIMIENTO}/casos-evaluacion`),
    crearCaso: (datos) => json('POST', `${BASE_CONOCIMIENTO}/casos-evaluacion`, datos),
    editarCaso: (casoId, datos) =>
      json('PUT', `${BASE_CONOCIMIENTO}/casos-evaluacion/${encodeURIComponent(casoId)}`, datos),
    eliminarCaso: (casoId) =>
      json('DELETE', `${BASE_CONOCIMIENTO}/casos-evaluacion/${encodeURIComponent(casoId)}`),
  };
}

/**
 * Ofrece un archivo para descargar (CSV de analíticas, JSON de una versión).
 *
 * @param {{nombre: string, contenido: Blob}} archivo
 * @param {Document} [documento]
 */
export function descargar({ nombre, contenido }, documento = globalThis.document) {
  const url = URL.createObjectURL(contenido);
  const enlace = documento.createElement('a');
  enlace.href = url;
  enlace.download = nombre;
  enlace.hidden = true;
  documento.body.append(enlace);
  enlace.click();
  enlace.remove();
  setTimeout(() => URL.revokeObjectURL(url), 0);
}
