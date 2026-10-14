/**
 * Cambios en las políticas de uso — SA-11, §7.3.7 («el sistema debe
 * notificar a los usuarios sobre cambios en políticas de uso que afecten al
 * usuario»), sobre admin-parametros.yaml 1.1.0.
 *
 * Un panel de la consola de parámetros con lo publicado (qué política, qué
 * cambió, desde cuándo rige y cuándo se publicó) y, para quien administra,
 * «Publicar un cambio»: la política, un título, lo que cambia en palabras del
 * jugador, desde cuándo rige y el motivo, que va a la auditoría y no al
 * jugador. Antes de publicar se ve el aviso tal como lo recibirá cada jugador
 * y se dice lo que va a pasar: publicar avisa a todos en su próximo ingreso y
 * no se puede retirar.
 *
 * Las políticas son las dos que nombran las causales de sanción (§7.3.2). El
 * servicio decide permisos y límites; aquí se validan antes para no hacer
 * esperar a nadie, y sus rechazos se cuentan por su `motivo`.
 */

import { fetchWithHttpErrorInterceptor } from '../../comun/interceptors/http-error.interceptor.js';
import { limpiarAviso, pintarAviso } from '../../comun/ui/aviso.js';
import { abrirPanelLateral } from '../../comun/ui/admin/panel-lateral.js';
import { tablaDeDatos } from '../../comun/ui/admin/tabla-de-datos.js';
import { campo } from '../../comun/ui/campo.js';
import { h, vaciar } from '../../comun/ui/dom.js';
import {
  estadoDeCarga,
  estadoDeError,
  estadoVacio,
  pintarEstado,
} from '../../comun/ui/estado-vista.js';
import { fechaHora, numero } from '../../comun/ui/formato.js';
import { respaldoPorEstado } from '../../comun/ui/texto-de-fallo.js';
import { panel } from '../consola/panel.js';

/** Las políticas de uso, como se leen (admin-parametros.yaml, `Politica`). */
export const POLITICAS = Object.freeze({
  TERMINOS_Y_CONDICIONES: 'Términos y condiciones del servicio',
  POLITICA_DE_PRIVACIDAD: 'Política de privacidad',
});

/** El título del aviso que recibe el jugador, el mismo que pone notificaciones. */
const TITULO_DEL_AVISO = Object.freeze({
  TERMINOS_Y_CONDICIONES: 'Actualizamos los términos y condiciones del servicio',
  POLITICA_DE_PRIVACIDAD: 'Actualizamos la política de privacidad',
});

/** Lo que admite el servicio (admin-parametros.yaml, `PublicacionDePolitica`). */
export const LIMITES = Object.freeze({
  titulo: [5, 120],
  descripcion: [20, 2000],
  motivo: [3, 500],
});

/** Una vigencia de hace unos minutos se admite (relojes); más atrás, no. */
const HOLGURA_DEL_PASADO_MS = 5 * 60 * 1000;

const ZONA_DEL_JUEGO = 'America/Bogota';

function baseDeApi() {
  const meta = globalThis.document?.querySelector?.('meta[name="nexus-api-base"]');
  return String(meta?.content ?? '').replace(/\/+$/, '');
}

/** Un rechazo del servicio, decidido por su `motivo` y su estado. */
export class ErrorDePoliticas extends Error {
  constructor(problema, estado) {
    super(respaldoPorEstado(estado));
    this.name = 'ErrorDePoliticas';
    this.estado = estado;
    this.motivo = problema?.motivo ?? null;
    this.detalle = typeof problema?.detail === 'string' ? problema.detail : null;
  }
}

async function pedir(ruta, opciones = {}, fetchImpl = fetchWithHttpErrorInterceptor) {
  const respuesta = await fetchImpl(`${baseDeApi()}${ruta}`, {
    ...opciones,
    headers: { Accept: 'application/json', ...(opciones.headers ?? {}) },
  });
  if (respuesta.ok) {
    return respuesta.json();
  }
  let problema = null;
  try {
    problema = await respuesta.json();
  } catch {
    problema = null;
  }
  throw new ErrorDePoliticas(problema, respuesta.status);
}

export const apiPoliticas = {
  historial: (f) => pedir('/api/v1/parametros/politicas/cambios', {}, f),
  publicar: (cuerpo, f) =>
    pedir(
      '/api/v1/parametros/politicas/cambios',
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(cuerpo),
      },
      f,
    ),
};

/* =========================================================================
   Presentación (pura, probada)
   ========================================================================= */

/**
 * «20 de octubre de 2026, 00:00» en hora de Colombia: la misma forma que usa
 * el aviso que arma notificaciones, armada por partes para que no dependa de
 * cómo cada navegador une la fecha y la hora.
 */
export function fechaDelAviso(valor) {
  const momento = valor instanceof Date ? valor : new Date(valor);
  if (Number.isNaN(momento.getTime())) {
    return '—';
  }
  const partes = Object.fromEntries(
    new Intl.DateTimeFormat('es-CO', {
      day: 'numeric',
      month: 'long',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23',
      timeZone: ZONA_DEL_JUEGO,
    })
      .formatToParts(momento)
      .map((parte) => [parte.type, parte.value]),
  );
  return `${partes.day} de ${partes.month} de ${partes.year}, ${partes.hour}:${partes.minute}`;
}

const conPunto = (texto) => (/[.!?]$/.test(texto) ? texto : `${texto}.`);

/**
 * El aviso tal como lo recibirá cada jugador (notificaciones.yaml 1.5.0,
 * `CAMBIO_POLITICA`), para verlo antes de publicar.
 *
 * @param {{politica?: string, titulo?: string, descripcion?: string, vigenteDesde?: string|Date|null}} cambio
 * @param {number} [ahora]
 * @returns {{titulo: string, cuerpo: string}}
 */
export function avisoDelCambio(cambio, ahora = Date.now()) {
  const titulo = String(cambio?.titulo ?? '').trim();
  const descripcion = String(cambio?.descripcion ?? '').trim();
  const vigencia = cambio?.vigenteDesde ? new Date(cambio.vigenteDesde) : new Date(ahora);
  const partes = [titulo, descripcion].filter(Boolean).map(conPunto);
  partes.push(`Rige desde el ${fechaDelAviso(vigencia)}.`);
  return {
    titulo: TITULO_DEL_AVISO[cambio?.politica] ?? 'Cambio en las políticas de uso',
    cuerpo: partes.join(' '),
  };
}

function largo(texto, [minimo, maximo], nombre) {
  if (texto.length < minimo) {
    return `${nombre} necesita al menos ${numero(minimo)} caracteres.`;
  }
  if (texto.length > maximo) {
    return `${nombre} admite como mucho ${numero(maximo)} caracteres.`;
  }
  return null;
}

/**
 * Lee el formulario: el cuerpo del POST y los errores de cada campo.
 *
 * @param {FormData|Record<string, string>} datos
 * @param {number} [ahora]
 */
export function leerPublicacion(datos, ahora = Date.now()) {
  const leer = (clave) =>
    String((datos instanceof FormData ? datos.get(clave) : datos?.[clave]) ?? '').trim();
  const politica = leer('politica');
  const titulo = leer('titulo');
  const descripcion = leer('descripcion');
  const motivo = leer('motivo');
  const textoVigencia = leer('vigenteDesde');
  const vigencia = textoVigencia ? new Date(textoVigencia) : null;

  const errores = {
    politica: Object.hasOwn(POLITICAS, politica) ? null : 'Elige la política que cambia.',
    titulo: largo(titulo, LIMITES.titulo, 'El título'),
    descripcion: largo(descripcion, LIMITES.descripcion, 'Lo que cambia'),
    motivo: largo(motivo, LIMITES.motivo, 'El motivo'),
    vigenteDesde: null,
  };
  if (vigencia && Number.isNaN(vigencia.getTime())) {
    errores.vigenteDesde = 'Esa fecha no se entiende. Elige otra o déjala vacía.';
  } else if (vigencia && vigencia.getTime() < ahora - HOLGURA_DEL_PASADO_MS) {
    errores.vigenteDesde =
      'No puede regir desde el pasado: los jugadores se enteran desde hoy. Déjala vacía para que rija ya.';
  }
  const cuerpo = { politica, titulo, descripcion, motivo };
  if (vigencia && !errores.vigenteDesde) {
    cuerpo.vigenteDesde = vigencia.toISOString();
  }
  return { cuerpo, errores, valido: Object.values(errores).every((error) => !error) };
}

/** El rechazo del servicio en palabras de quien administra. */
export function textoDelRechazo(error) {
  if (!(error instanceof ErrorDePoliticas)) {
    return 'No pudimos contactar con el servicio de parámetros. Revisa tu conexión e inténtalo de nuevo.';
  }
  if (error.motivo === 'PERMISO_INSUFICIENTE' || error.estado === 403) {
    return 'Tu cuenta no puede publicar cambios en las políticas de uso.';
  }
  if (error.estado === 401) {
    return 'Tu sesión terminó. Vuelve a iniciar sesión para publicar el cambio.';
  }
  if (error.motivo === 'SOLICITUD_INVALIDA' && error.detalle) {
    return error.detalle;
  }
  return error.message;
}

/** La tabla de lo publicado, lo más reciente primero (como lo entrega el servicio). */
export function tablaDeCambios(cambios) {
  return tablaDeDatos({
    titulo: 'Cambios publicados en las políticas de uso',
    resumen: `${numero(cambios.length)} ${cambios.length === 1 ? 'cambio publicado' : 'cambios publicados'}`,
    claveDeFila: (cambio) => cambio.id,
    vacio: {
      titulo: 'Todavía no se ha publicado ningún cambio',
      detalle: 'Lo que publiques aquí llega a la bandeja de cada jugador en su próximo ingreso.',
    },
    columnas: [
      {
        clave: 'politica',
        titulo: 'Política',
        valor: (cambio) =>
          cambio.nombreDeLaPolitica || POLITICAS[cambio.politica] || 'Política de uso',
      },
      {
        clave: 'titulo',
        titulo: 'Cambio',
        principal: true,
        valor: (cambio) =>
          h('span', {
            clase: 'pila pila--ajustada',
            hijos: [
              h('span', { texto: cambio.titulo }),
              h('span', { clase: 't-meta', texto: cambio.descripcion }),
            ],
          }),
      },
      {
        clave: 'vigenteDesde',
        titulo: 'Rige desde',
        tipo: 'fecha',
        valor: (cambio) => fechaHora(cambio.vigenteDesde),
      },
      {
        clave: 'publicadoEn',
        titulo: 'Publicado',
        tipo: 'fecha',
        valor: (cambio) => fechaHora(cambio.publicadoEn),
      },
    ],
    filas: cambios,
  });
}

/* =========================================================================
   DOM
   ========================================================================= */

/** El formulario del panel «Publicar un cambio», con la vista previa del aviso. */
function formularioDePublicacion() {
  const formulario = h('form', {
    clase: 'pila pila--compacta',
    datos: { zona: 'publicacion' },
    atributos: { novalidate: true },
  });
  const politica = campo({
    nombre: 'politica',
    etiqueta: 'Política que cambia',
    requerido: true,
    opciones: [
      { valor: '', texto: 'Elige una política' },
      ...Object.entries(POLITICAS).map(([valor, texto]) => ({ valor, texto })),
    ],
  });
  const titulo = campo({
    nombre: 'titulo',
    etiqueta: 'Título del cambio',
    requerido: true,
    pista: 'Lo primero que leerá el jugador. Por ejemplo: «Reglas del chat».',
    atributos: { maxlength: LIMITES.titulo[1], autocomplete: 'off' },
  });
  const descripcion = campo({
    nombre: 'descripcion',
    etiqueta: 'Qué cambia',
    requerido: true,
    multilinea: true,
    pista: 'En palabras del jugador: qué cambia para él y qué tiene que hacer, si algo.',
    atributos: { maxlength: LIMITES.descripcion[1], rows: 4 },
  });
  const vigencia = campo({
    nombre: 'vigenteDesde',
    etiqueta: 'Rige desde',
    tipo: 'datetime-local',
    pista: 'Vacío: rige desde que lo publiques.',
  });
  const motivo = campo({
    nombre: 'motivo',
    etiqueta: 'Motivo',
    requerido: true,
    multilinea: true,
    pista: 'Queda en el registro de auditoría; los jugadores no lo ven.',
    atributos: { maxlength: LIMITES.motivo[1], rows: 3 },
  });

  const previaTitulo = h('strong', { clase: 'tarjeta__titulo' });
  const previaCuerpo = h('p', { clase: 't-cuerpo' });
  const previa = h('section', {
    clase: 'tarjeta pila pila--ajustada',
    datos: { zona: 'vista-previa-del-aviso' },
    atributos: { 'aria-live': 'polite', 'aria-label': 'Así lo verán los jugadores' },
    hijos: [
      h('p', { clase: 't-meta', texto: 'Así lo verán los jugadores en sus notificaciones' }),
      previaTitulo,
      previaCuerpo,
    ],
  });
  const consecuencia = h('p', {
    clase: 'aviso aviso--advertencia',
    datos: { zona: 'consecuencia' },
    texto:
      'Al publicarlo, cada jugador lo recibirá en su bandeja en su próximo ingreso. Un cambio publicado no se puede retirar: si hay que corregirlo, publica otro.',
  });
  formulario.append(
    politica.elemento,
    titulo.elemento,
    descripcion.elemento,
    vigencia.elemento,
    motivo.elemento,
    previa,
    consecuencia,
  );

  const campos = { politica, titulo, descripcion, vigenteDesde: vigencia, motivo };

  function pintarPrevia() {
    const { cuerpo } = leerPublicacion(new FormData(formulario));
    const aviso = avisoDelCambio(cuerpo);
    previaTitulo.textContent = aviso.titulo;
    previaCuerpo.textContent = aviso.cuerpo;
  }
  formulario.addEventListener('input', pintarPrevia);
  formulario.addEventListener('change', pintarPrevia);
  pintarPrevia();

  return {
    elemento: formulario,
    leer: () => leerPublicacion(new FormData(formulario)),
    /** Pinta cada error en su campo y lleva el foco al primero. */
    marcar(errores) {
      for (const [nombre, control] of Object.entries(campos)) {
        control.marcarError(errores[nombre] ?? null);
      }
      const primero = Object.keys(campos).find((nombre) => errores[nombre]);
      if (primero) {
        formulario.querySelector(`[name="${primero}"]`)?.focus();
      }
      return Boolean(primero);
    },
  };
}

/**
 * El panel «Cambios en las políticas de uso»: se carga por su cuenta y, si el
 * servicio no responde, lo dice y ofrece reintentar solo esta parte.
 *
 * @param {{administra?: boolean, zonaAviso?: HTMLElement|null, fetchImpl?: Function,
 *          abrirPanel?: typeof abrirPanelLateral}} [opciones]
 * @returns {{elemento: HTMLElement, cargar: () => Promise<void>, publicar: () => void}}
 */
export function montarPoliticasDeUso({
  administra = false,
  zonaAviso = null,
  fetchImpl,
  abrirPanel = abrirPanelLateral,
} = {}) {
  const acciones = [];
  if (administra) {
    const boton = h('button', {
      clase: 'boton boton--primario boton--pequeno',
      texto: 'Publicar un cambio',
      atributos: { type: 'button' },
      datos: { accion: 'publicar-cambio-de-politica' },
    });
    boton.addEventListener('click', () => publicar());
    acciones.push(boton);
  }
  const caja = panel({
    id: 'politicas-de-uso',
    titulo: 'Cambios en las políticas de uso',
    descripcion:
      'Lo que se publica aquí llega a la bandeja de notificaciones de cada jugador en su próximo ingreso.',
    acciones,
    ancho: 'completo',
  });
  caja.sello.dataset.estado = 'datos';

  async function cargar() {
    pintarEstado(caja.zona, estadoDeCarga({ filas: 2, etiqueta: 'Cargando los cambios…' }));
    let cambios;
    try {
      cambios = await apiPoliticas.historial(fetchImpl);
    } catch {
      pintarEstado(
        caja.zona,
        estadoDeError({
          titulo: 'No pudimos cargar los cambios publicados',
          detalle: 'El servicio de parámetros no respondió. Vuelve a intentarlo.',
          alReintentar: cargar,
        }),
      );
      return;
    }
    if (!Array.isArray(cambios) || cambios.length === 0) {
      pintarEstado(
        caja.zona,
        estadoVacio({
          titulo: 'Todavía no se ha publicado ningún cambio',
          detalle: administra
            ? 'Cuando cambien los términos o la política de privacidad, publícalo aquí para avisar a los jugadores.'
            : 'Aquí aparecerán los cambios que publique la administración.',
        }),
      );
      return;
    }
    vaciar(caja.zona).append(tablaDeCambios(cambios));
  }

  function publicar() {
    limpiarAviso(zonaAviso);
    const formulario = formularioDePublicacion();
    const error = h('p', {
      clase: 'aviso aviso--error',
      datos: { zona: 'error-de-la-publicacion' },
      atributos: { role: 'alert' },
    });
    error.hidden = true;
    const confirmar = h('button', {
      clase: 'boton boton--primario',
      texto: 'Publicar y avisar a los jugadores',
      atributos: { type: 'button' },
      datos: { accion: 'confirmar-publicacion' },
    });
    const { cerrar } = abrirPanel({
      titulo: 'Publicar un cambio en las políticas de uso',
      subtitulo: 'Los términos y condiciones del servicio o la política de privacidad.',
      contenido: [formulario.elemento, error],
      acciones: [confirmar],
    });

    async function enviar() {
      error.hidden = true;
      const { cuerpo, errores } = formulario.leer();
      if (formulario.marcar(errores)) {
        return;
      }
      confirmar.disabled = true;
      try {
        const publicado = await apiPoliticas.publicar(cuerpo, fetchImpl);
        cerrar?.();
        pintarAviso(zonaAviso, {
          tono: 'exito',
          titulo: 'Cambio publicado',
          detalle: `${publicado?.nombreDeLaPolitica ?? POLITICAS[cuerpo.politica]}: «${publicado?.titulo ?? cuerpo.titulo}». Los jugadores lo verán en sus notificaciones en su próximo ingreso.`,
        });
        await cargar();
      } catch (rechazo) {
        error.textContent = textoDelRechazo(rechazo);
        error.hidden = false;
      } finally {
        confirmar.disabled = false;
      }
    }
    confirmar.addEventListener('click', enviar);
    formulario.elemento.addEventListener('submit', (evento) => {
      evento.preventDefault();
      enviar();
    });
  }

  cargar();
  return { elemento: caja.elemento, cargar, publicar };
}
