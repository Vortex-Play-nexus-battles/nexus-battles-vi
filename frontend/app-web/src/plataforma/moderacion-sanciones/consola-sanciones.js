/**
 * La consola de sanciones — SA-4 (§7.3.2, §7.3.8; informe de revisión, puntos
 * 12 y 20).
 *
 * Lo que había: un campo para escribir el `uid` de una cuenta, un formulario
 * de emisión con la duración solo en horas y sin causal, y la lista de
 * apelaciones pendientes en tarjetas sueltas, sin plazo. No se podía ver qué
 * se había sancionado en la plataforma sin saber antes a quién mirar, y el
 * informe lo señaló dos veces: la navegación de sanciones (punto 20) y las
 * sanciones del mes (punto 12).
 *
 * Ahora, tres pestañas y lo que cuenta arriba:
 *
 *   - **Sanciones**: todas, de la más reciente a la más antigua, con filtros
 *     por cuenta, tipo, causal (las ocho de §7.3.2), estado y fechas, 16 por
 *     página (`GET /api/v1/sanciones`, moderacion-sanciones-admin 1.4.0). Cada
 *     fila abre su detalle; una suspensión vigente se levanta desde ahí, con
 *     motivo, si quien mira administra.
 *   - **Apelaciones**: las del panel, con la fecha en que tiene que decidir
 *     cada una (quince días hábiles, `decisionAntesDe`) y las que ya se pasaron
 *     de plazo marcadas. Se resuelven en el panel lateral, motivadas.
 *   - **Emitir sanción**: primero se busca la cuenta (apodo o `uid`) y se ve su
 *     estado y su historial; luego tipo, causal obligatoria, duración en horas,
 *     días o semanas dentro del rango que aplica hoy el servicio, motivo y
 *     política. Antes de emitir, una confirmación que dice lo que va a pasar;
 *     el baneo, además, pide escribir el apodo.
 *
 * Quién es cada cuenta lo dice ms-identidad (`GET /api/v1/admin/titulares`,
 * ms-identidad-admin 1.9.0): el apodo, el estado y el rol, sin correo. Lo leen
 * los tres roles de moderación; la ficha completa solo la abre quien gestiona
 * cuentas (Tabla 24).
 *
 * Ningún número de esta pantalla se inventa: los indicadores son totales que
 * devuelve el servicio, y si una consulta falla se dice «Sin dato».
 *
 * @module plataforma/moderacion-sanciones/consola-sanciones
 */

import { consultar, RESULTADO } from '../consola/cliente-consola.js';
import { directorioDeTitulares } from '../consola/titulares.js';
import { panel, pintarDesenlace } from '../consola/panel.js';
import { urlDeVista } from '../../comun/acceso.js';
import { construirPaginacion } from '../../comun/paginacion.js';
import { NOMBRE_DE_ROL } from '../../comun/shell.js';
import { pintarAviso } from '../../comun/ui/aviso.js';
import { barraDeFiltros } from '../../comun/ui/admin/barra-de-filtros.js';
import { inicioDelMes, instanteDelDia } from '../../comun/ui/admin/fechas.js';
import {
  distintivoDeEstado,
  estadoSegun,
  ESTADOS_DE_CUENTA_ADMIN,
} from '../../comun/ui/admin/estado.js';
import { rejillaDeIndicadores, tarjetaDeIndicador } from '../../comun/ui/admin/indicador.js';
import { abrirPanelLateral, listaDeDatos } from '../../comun/ui/admin/panel-lateral.js';
import { tablaDeDatos } from '../../comun/ui/admin/tabla-de-datos.js';
import { confirmar, confirmarConMotivo, confirmarCritico } from '../../comun/ui/dialogo.js';
import { h, vaciar } from '../../comun/ui/dom.js';
import {
  estadoDeCarga,
  estadoDeError,
  estadoVacio,
  pintarEstado,
} from '../../comun/ui/estado-vista.js';
import { fechaHora } from '../../comun/ui/formato.js';
import { lineaDeTiempo } from '../../comun/ui/linea-de-tiempo.js';
import { encabezadoDePagina } from '../../comun/ui/pagina.js';
import { montarPestanas } from '../../comun/ui/pestanas.js';
import {
  estadoDeCuenta,
  hechosDeSanciones,
  NOMBRE_DE_CAUSAL,
  NOMBRE_DE_SANCION,
  nombreDeCausal,
} from '../../comun/ui/sancion.js';
import {
  api,
  ErrorDeSanciones,
  ROLES_DE_ADMINISTRACION,
  ROLES_DE_MODERACION,
  TIPO,
} from './sanciones.js';

/** RNF-USA-001: dieciséis por página. */
export const POR_PAGINA = 16;

const UID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Las unidades de la duración de una suspensión (§7.3.2: «horas, días o semanas»). */
export const UNIDADES_DE_DURACION = Object.freeze({
  HORAS: { horas: 1, singular: 'hora', plural: 'horas' },
  DIAS: { horas: 24, singular: 'día', plural: 'días' },
  SEMANAS: { horas: 168, singular: 'semana', plural: 'semanas' },
});

/** El estado de una sanción, dicho para quien modera. */
export const ESTADOS_DE_LA_SANCION = Object.freeze({
  VIGENTE: { texto: 'Vigente', tono: 'advertencia' },
  CUMPLIDA: { texto: 'Cumplida', tono: 'neutro' },
  REVERTIDA: { texto: 'Revertida', tono: 'info' },
});

/** El estado de una apelación. */
export const ESTADOS_DE_LA_APELACION = Object.freeze({
  PENDIENTE: { texto: 'Por resolver', tono: 'advertencia' },
  MANTENIDA: { texto: 'Sanción mantenida', tono: 'neutro' },
  REDUCIDA: { texto: 'Sanción reducida', tono: 'info' },
  REVERTIDA: { texto: 'Sanción revertida', tono: 'exito' },
});

/** Las tres vistas de la consola, en su orden. */
export const PESTANAS_DE_SANCIONES = Object.freeze([
  { id: 'sanciones', etiqueta: 'Sanciones' },
  { id: 'apelaciones', etiqueta: 'Apelaciones' },
  { id: 'emitir', etiqueta: 'Emitir sanción' },
]);

/** Las causales de §7.3.2 como opciones de un desplegable, en su orden. */
export const OPCIONES_DE_CAUSAL = Object.freeze(
  Object.entries(NOMBRE_DE_CAUSAL).map(([valor, texto]) => [valor, texto]),
);

const OPCIONES_DE_TIPO = Object.freeze([
  [TIPO.ADVERTENCIA, NOMBRE_DE_SANCION.ADVERTENCIA],
  [TIPO.SUSPENSION, NOMBRE_DE_SANCION.SUSPENSION],
  [TIPO.BANEO, NOMBRE_DE_SANCION.BANEO],
]);

const OPCIONES_DE_ESTADO = Object.freeze(
  Object.entries(ESTADOS_DE_LA_SANCION).map(([valor, { texto }]) => [valor, texto]),
);

/* =========================================================================
   Lo que se calcula (puro, probado)
   ========================================================================= */

/**
 * El estado de una sanción: revertida, vigente o cumplida.
 *
 * @param {{revertidaEn?: string|null, vigente?: boolean}} sancion `Sancion`
 * @returns {'VIGENTE'|'CUMPLIDA'|'REVERTIDA'}
 */
export function estadoDeLaSancion(sancion) {
  if (sancion?.revertidaEn) {
    return 'REVERTIDA';
  }
  return sancion?.vigente ? 'VIGENTE' : 'CUMPLIDA';
}

/**
 * Las horas de una duración escrita en una unidad.
 *
 * @param {number|string} cantidad
 * @param {keyof typeof UNIDADES_DE_DURACION} unidad
 * @returns {number|null} null si no es un entero positivo o la unidad no existe
 */
export function duracionEnHoras(cantidad, unidad) {
  const valor = Number(cantidad);
  const factor = UNIDADES_DE_DURACION[unidad]?.horas;
  if (!factor || !Number.isInteger(valor) || valor <= 0) {
    return null;
  }
  return valor * factor;
}

/**
 * El rango de la suspensión que aplica hoy el servicio, en horas
 * (`GET /api/v1/sanciones/limites`). Sin límites conocidos, null: el servicio
 * sigue validando y su respuesta dice el rango real.
 *
 * @param {{suspensionMinimaHoras?: number, suspensionMaximaHoras?: number,
 *   suspensionMaximaDias?: number}|null} limites
 * @returns {{minimo: number, maximo: number}|null}
 */
export function rangoDeLaSuspension(limites) {
  const minimo = Number(limites?.suspensionMinimaHoras);
  const maximoEnHoras = Number(limites?.suspensionMaximaHoras);
  const maximoEnDias = Number(limites?.suspensionMaximaDias);
  let maximo = NaN;
  if (Number.isFinite(maximoEnHoras) && maximoEnHoras > 0) {
    maximo = maximoEnHoras;
  } else if (Number.isFinite(maximoEnDias) && maximoEnDias > 0) {
    maximo = maximoEnDias * 24;
  }
  if (!Number.isFinite(minimo) || minimo <= 0 || !Number.isFinite(maximo) || maximo < minimo) {
    return null;
  }
  return { minimo, maximo };
}

/**
 * Una cantidad de horas dicha en la unidad más grande que la divide:
 * 24 → «1 día», 336 → «2 semanas», 30 → «30 horas».
 *
 * @param {number} horas
 * @returns {string}
 */
export function duracionLegible(horas) {
  const total = Number(horas);
  if (!Number.isFinite(total) || total <= 0) {
    return '';
  }
  for (const unidad of ['SEMANAS', 'DIAS', 'HORAS']) {
    const { horas: factor, singular, plural } = UNIDADES_DE_DURACION[unidad];
    if (total % factor === 0) {
      const cantidad = total / factor;
      return `${cantidad} ${cantidad === 1 ? singular : plural}`;
    }
  }
  return `${total} horas`;
}

/**
 * Por qué una duración no se puede emitir, o null si se puede.
 *
 * @param {number|null} horas {@link duracionEnHoras}
 * @param {{minimo: number, maximo: number}|null} rango {@link rangoDeLaSuspension}
 * @returns {string|null}
 */
export function motivoDeDuracionInvalida(horas, rango) {
  if (horas === null) {
    return 'Escribe la duración como un número entero mayor que cero.';
  }
  if (rango && (horas < rango.minimo || horas > rango.maximo)) {
    return `La suspensión tiene que durar entre ${duracionLegible(rango.minimo)} y ${duracionLegible(rango.maximo)}.`;
  }
  return null;
}

// SA-4 — los días de los filtros, en `comun/ui/admin/fechas.js` (los usa también
// el historial de moderación); se reexportan para quien ya los importaba de aquí.
export { inicioDelMes, instanteDelDia };

/**
 * La consulta del listado global con los filtros puestos.
 *
 * @param {{tipo?: string, causal?: string, estado?: string, desde?: string,
 *   hasta?: string}} filtros
 * @param {{usuarioId?: string|null, pagina?: number, tamano?: number}} [opciones]
 * @returns {string} el recurso, sin `/api/v1`
 */
export function recursoDelListado(
  filtros,
  { usuarioId = null, pagina = 0, tamano = POR_PAGINA } = {},
) {
  const parametros = new URLSearchParams();
  for (const clave of ['tipo', 'causal', 'estado']) {
    const valor = String(filtros?.[clave] ?? '').trim();
    if (valor) {
      parametros.set(clave, valor);
    }
  }
  if (usuarioId) {
    parametros.set('usuarioId', usuarioId);
  }
  const desde = instanteDelDia(filtros?.desde);
  if (desde) {
    parametros.set('desde', desde);
  }
  const hasta = instanteDelDia(filtros?.hasta, { finDelDia: true });
  if (hasta) {
    parametros.set('hasta', hasta);
  }
  parametros.set('pagina', String(pagina));
  parametros.set('tamano', String(tamano));
  return `/sanciones?${parametros}`;
}

/**
 * Lo que queda para que el panel decida una apelación, o que ya se pasó.
 *
 * @param {{estado: string, decisionAntesDe?: string, vencida?: boolean}} apelacion
 * @param {number} ahora
 * @returns {{texto: string, tono: string}}
 */
export function plazoDelPanel(apelacion, ahora) {
  const limite = new Date(apelacion?.decisionAntesDe ?? '').getTime();
  if (apelacion?.estado !== 'PENDIENTE') {
    return { texto: 'Resuelta', tono: 'neutro' };
  }
  if (apelacion?.vencida || (Number.isFinite(limite) && limite < ahora)) {
    return { texto: 'Fuera de plazo', tono: 'error' };
  }
  if (!Number.isFinite(limite)) {
    return { texto: 'Sin fecha límite', tono: 'neutro' };
  }
  const dias = Math.ceil((limite - ahora) / (24 * 60 * 60 * 1000));
  if (dias <= 1) {
    return { texto: 'Vence hoy', tono: 'advertencia' };
  }
  return { texto: `Quedan ${dias} días`, tono: dias <= 3 ? 'advertencia' : 'info' };
}

/**
 * El cuerpo de `POST /api/v1/sanciones` a partir del formulario.
 *
 * @param {{tipo: string, causal: string, motivo: string, politica?: string,
 *   cantidad?: string|number, unidad?: string}} datos
 * @param {{uid: string}} titular la cuenta elegida
 * @param {{comentarioId?: string|null}} [opciones]
 * @returns {object}
 */
export function solicitudDeEmision(datos, titular, { comentarioId = null } = {}) {
  const cuerpo = {
    usuarioId: titular.uid,
    tipo: String(datos.tipo),
    causal: String(datos.causal),
    motivo: String(datos.motivo ?? '').trim(),
  };
  const politica = String(datos.politica ?? '').trim();
  if (politica) {
    cuerpo.politica = politica;
  }
  if (comentarioId) {
    cuerpo.comentarioId = comentarioId;
  }
  if (cuerpo.tipo === TIPO.SUSPENSION) {
    cuerpo.duracionHoras = duracionEnHoras(datos.cantidad, datos.unidad);
  }
  if (cuerpo.tipo === TIPO.BANEO) {
    cuerpo.confirmacion = true;
  }
  return cuerpo;
}

/**
 * Lo que va a pasar si se emite, en frases, para la confirmación.
 *
 * @param {string} tipo
 * @param {{horas?: number|null, ahora?: number}} [opciones]
 * @returns {string[]}
 */
export function consecuenciasDeLaEmision(tipo, { horas = null, ahora = Date.now() } = {}) {
  const avisos = 'Recibe un aviso y un correo con la causal, el motivo y cómo apelar.';
  if (tipo === TIPO.ADVERTENCIA) {
    return ['Queda registrada en su historial; no restringe su acceso.', avisos];
  }
  if (tipo === TIPO.SUSPENSION) {
    const hasta = horas ? fechaHora(ahora + horas * 60 * 60 * 1000) : null;
    return [
      hasta
        ? `No podrá entrar al juego hasta el ${hasta}.`
        : 'No podrá entrar al juego mientras dure la suspensión.',
      'Sus sesiones abiertas se cierran; su inventario y su progreso se conservan.',
      avisos,
    ];
  }
  return [
    'No podrá volver a entrar con esta cuenta y su inventario queda congelado.',
    'Solo una apelación aceptada por el panel puede revertirlo.',
    avisos,
  ];
}

/* =========================================================================
   Quién es cada cuenta: `directorioDeTitulares` vive en la consola
   (../consola/titulares.js), porque lo usan también la bitácora de auditoría
   y la consola de control integral.
   ========================================================================= */

export { directorioDeTitulares };

/* =========================================================================
   La consola
   ========================================================================= */

/**
 * Monta la consola de sanciones.
 *
 * `?usuario=<uid>` (desde la ficha o la cola de moderación) abre «Emitir
 * sanción» con esa cuenta ya elegida; `?comentario=<id>` liga la sanción al
 * comentario que se estaba moderando.
 *
 * @param {ParentNode} raiz documento con `[data-zona=encabezado|aviso|indicadores|consola]`
 * @param {{rol?: string|null, fetchImpl?: Function, consultarApi?: typeof consultar,
 *   ahora?: () => number, busqueda?: string}} [opciones]
 */
export function montarConsolaDeSanciones(
  raiz,
  {
    rol = null,
    fetchImpl,
    consultarApi = (recurso) => consultar(recurso, fetchImpl ? { buscar: fetchImpl } : {}),
    ahora = () => Date.now(),
    busqueda = globalThis.location?.search ?? '',
  } = {},
) {
  const zonaEncabezado = raiz.querySelector('[data-zona="encabezado"]');
  const zonaAviso = raiz.querySelector('[data-zona="aviso"]');
  const zonaIndicadores = raiz.querySelector('[data-zona="indicadores"]');
  const zonaConsola = raiz.querySelector('[data-zona="consola"]');

  zonaEncabezado?.replaceChildren(
    encabezadoDePagina({
      titulo: 'Sanciones',
      descripcion:
        'Advertencias, suspensiones y baneos definitivos con su causal, y las apelaciones que ' +
        'el panel tiene que resolver. Nada se borra: una sanción se revierte y queda constancia.',
    }),
  );

  if (!ROLES_DE_MODERACION.includes(rol)) {
    pintarAviso(zonaAviso, {
      tono: 'advertencia',
      titulo: 'Esta consola es de moderación',
      detalle: 'Tu rol no permite emitir ni revisar sanciones.',
    });
    return null;
  }

  const administra = ROLES_DE_ADMINISTRACION.includes(rol);
  const parametros = new URLSearchParams(busqueda);
  const usuarioPedido = UID.test(parametros.get('usuario') ?? '')
    ? parametros.get('usuario')
    : null;
  const comentarioPedido = (parametros.get('comentario') ?? '').trim() || null;
  const titulares = directorioDeTitulares({ consultarApi });

  const contexto = {
    rol,
    administra,
    titulares,
    consultarApi,
    fetchImpl,
    ahora,
    zonaAviso,
    comentarioPedido,
    /** Repinta lo que depende de una sanción nueva, levantada o de una apelación resuelta. */
    alCambiar: () => {},
  };

  const indicadores = montarIndicadores(zonaIndicadores, contexto);
  const listado = panelDelListado(contexto);
  const apelaciones = panelDeApelaciones(contexto);
  const emision = panelDeEmision(contexto);

  contexto.alCambiar = () => {
    indicadores.cargar();
    listado.cargar();
    apelaciones.cargar();
  };
  contexto.emitirA = (titular) => {
    pestanas.mostrar('emitir');
    emision.elegir(titular);
  };

  vaciar(zonaConsola);
  const contenedor = h('div', { clase: 'consola-sanciones', datos: { zona: 'pestanas' } });
  zonaConsola.append(contenedor);
  const paneles = {
    sanciones: listado.elemento,
    apelaciones: apelaciones.elemento,
    emitir: emision.elemento,
  };
  const pestanas = montarPestanas(
    contenedor,
    PESTANAS_DE_SANCIONES.map(({ id, etiqueta }) => ({
      id,
      etiqueta,
      panel: h('section', {
        clase: 'consola-integral__seccion',
        datos: { seccion: id },
        hijos: [h('h2', { clase: 'solo-lectores', texto: etiqueta }), paneles[id]],
      }),
    })),
    { activa: usuarioPedido ? 'emitir' : 'sanciones' },
  );

  indicadores.cargar();
  listado.cargar();
  apelaciones.cargar();
  emision.cargarLimites();
  if (usuarioPedido) {
    emision.elegirPorUid(usuarioPedido);
  }

  return { pestanas, indicadores, listado, apelaciones, emision };
}

/* -------------------------------------------------------------------------
   Indicadores
   ------------------------------------------------------------------------- */

/**
 * Cuatro cifras de verdad: vigentes, emitidas este mes, apelaciones por
 * resolver y las que ya se pasaron de plazo.
 */
function montarIndicadores(zona, { consultarApi, ahora }) {
  async function cargar() {
    if (!zona) {
      return;
    }
    const [vigentes, delMes, pendientes] = await Promise.all([
      consultarApi('/sanciones?estado=VIGENTE&pagina=0&tamano=1'),
      consultarApi(
        `/sanciones?desde=${encodeURIComponent(inicioDelMes(ahora()))}&pagina=0&tamano=1`,
      ),
      consultarApi('/apelaciones?estado=PENDIENTES'),
    ]);
    const total = (d) => {
      if (d.resultado === RESULTADO.VACIO) {
        return 0;
      }
      return d.resultado === RESULTADO.DATOS ? Number(d.datos?.total) : null;
    };
    const lista = (d) => {
      if (d.resultado === RESULTADO.VACIO) {
        return [];
      }
      return d.resultado === RESULTADO.DATOS && Array.isArray(d.datos) ? d.datos : null;
    };
    const porResolver = lista(pendientes);
    const fueraDePlazo = porResolver ? porResolver.filter((a) => a.vencida).length : null;
    zona.replaceChildren(
      rejillaDeIndicadores(
        [
          tarjetaDeIndicador({
            etiqueta: 'Sanciones vigentes',
            valor: total(vigentes),
            detalle: 'Advertencias, suspensiones en curso y baneos que no se han revertido.',
          }),
          tarjetaDeIndicador({
            etiqueta: 'Emitidas este mes',
            valor: total(delMes),
            detalle: 'Desde el día 1, de cualquier tipo, también las que ya se revirtieron.',
          }),
          tarjetaDeIndicador({
            etiqueta: 'Apelaciones por resolver',
            valor: porResolver ? porResolver.length : null,
            detalle: 'El panel decide cada una en quince días hábiles desde que se presentó.',
            tono: porResolver?.length ? 'advertencia' : null,
          }),
          tarjetaDeIndicador({
            etiqueta: 'Fuera de plazo',
            valor: fueraDePlazo,
            detalle: 'Apelaciones que pasaron sus quince días hábiles sin decisión.',
            tono: fueraDePlazo ? 'error' : null,
          }),
        ],
        { zona: 'cifras-de-sanciones' },
      ),
    );
  }
  return { cargar };
}

/* -------------------------------------------------------------------------
   La cuenta, como se lee en una fila
   ------------------------------------------------------------------------- */

/**
 * El apodo de la cuenta, con enlace a su ficha si quien mira gestiona cuentas.
 *
 * @param {string} uid
 * @param {{titulares: ReturnType<typeof directorioDeTitulares>, administra: boolean}} contexto
 * @returns {Node}
 */
function cuentaDe(uid, { titulares, administra }) {
  const titular = titulares.de(uid);
  if (!titular) {
    return h('span', { clase: 'tabla-admin__sin-dato', texto: 'Cuenta sin identificar' });
  }
  const ficha = administra ? urlDeFicha(titular.uid) : null;
  if (!ficha) {
    return h('span', { texto: titular.apodo });
  }
  return h('a', { texto: titular.apodo, atributos: { href: ficha } });
}

/** La ficha administrativa de una cuenta (`?usuario=<uid>`), o null si la vista no existe. */
function urlDeFicha(uid) {
  const ficha = urlDeVista('ficha-usuario');
  if (!ficha) {
    return null;
  }
  const destino = new URL(ficha);
  destino.searchParams.set('usuario', uid);
  return destino.href;
}

/** Quien emitió, por su rol (la cuenta concreta queda en la auditoría). */
function emisorDe(sancion) {
  return NOMBRE_DE_ROL[sancion?.rolEmisor] ?? 'Moderación';
}

/* -------------------------------------------------------------------------
   Sanciones: el listado global
   ------------------------------------------------------------------------- */

function panelDelListado(contexto) {
  const { consultarApi, titulares } = contexto;
  const partes = panel({
    id: 'listado-de-sanciones',
    titulo: 'Todas las sanciones',
    descripcion: 'De la más reciente a la más antigua.',
    ancho: 'completo',
  });

  let filtros = {};
  let usuarioId = null;
  let pagina = 0;
  let pedido = 0;

  const zonaEleccion = h('div', {
    clase: 'pila pila--compacta',
    datos: { zona: 'eleccion-de-cuenta' },
  });
  zonaEleccion.hidden = true;

  const barra = barraDeFiltros({
    etiqueta: 'Buscar y filtrar sanciones',
    busqueda: { nombre: 'cuenta', etiqueta: 'Cuenta', placeholder: 'Apodo o uid de la cuenta' },
    filtros: [
      { nombre: 'tipo', etiqueta: 'Tipo', opciones: OPCIONES_DE_TIPO, todos: 'Todos los tipos' },
      {
        nombre: 'causal',
        etiqueta: 'Causal',
        opciones: OPCIONES_DE_CAUSAL,
        todos: 'Todas las causales',
      },
      {
        nombre: 'estado',
        etiqueta: 'Estado',
        opciones: OPCIONES_DE_ESTADO,
        todos: 'Todos los estados',
      },
      { nombre: 'desde', etiqueta: 'Emitidas desde', tipo: 'fecha' },
      { nombre: 'hasta', etiqueta: 'Emitidas hasta', tipo: 'fecha' },
    ],
    validar: (valores) => {
      if (valores.cuenta && !UID.test(valores.cuenta) && valores.cuenta.length < 2) {
        return 'Escribe al menos dos letras del apodo.';
      }
      if (valores.desde && valores.hasta && valores.desde > valores.hasta) {
        return 'La fecha «desde» es posterior a «hasta»: ninguna sanción puede cumplir las dos.';
      }
      return null;
    },
    alAplicar: (valores) => aplicar(valores),
  });

  const cuerpo = partes.zona;
  partes.elemento.insertBefore(
    h('div', { clase: 'panel-admin__filtros', hijos: [barra.elemento, zonaEleccion] }),
    cuerpo,
  );

  async function aplicar(valores) {
    filtros = valores;
    pagina = 0;
    zonaEleccion.hidden = true;
    vaciar(zonaEleccion);
    const texto = valores.cuenta;
    if (!texto) {
      usuarioId = null;
      await cargar();
      return;
    }
    if (UID.test(texto)) {
      usuarioId = texto.toLowerCase();
      await cargar();
      return;
    }
    pintarEstado(cuerpo, estadoDeCarga({ filas: 2, etiqueta: 'Buscando la cuenta' }));
    const desenlace = await titulares.buscar(texto);
    if (desenlace.resultado === RESULTADO.VACIO) {
      pintarDesenlace(partes, { resultado: RESULTADO.VACIO }, () => [], {
        vacio: {
          titulo: `Ninguna cuenta coincide con «${texto}»`,
          detalle: 'Revisa el apodo o escribe su uid completo.',
        },
      });
      return;
    }
    if (desenlace.resultado !== RESULTADO.DATOS) {
      pintarDesenlace(partes, desenlace, () => [], { alReintentar: () => aplicar(valores) });
      return;
    }
    if (desenlace.datos.length === 1) {
      usuarioId = desenlace.datos[0].uid;
      await cargar();
      return;
    }
    // Varias: se elige, no se adivina.
    zonaEleccion.append(
      h('p', { clase: 't-meta', texto: `Varias cuentas coinciden con «${texto}». Elige una:` }),
      h('div', {
        clase: 'fila fila--envuelta',
        hijos: desenlace.datos.map((titular) => {
          const boton = h('button', {
            clase: 'boton boton--secundario boton--pequeno',
            texto: titular.apodo,
            atributos: { type: 'button' },
            datos: { accion: 'elegir-cuenta', uid: titular.uid },
          });
          boton.addEventListener('click', () => {
            usuarioId = titular.uid;
            barra.fijar({ cuenta: titular.apodo });
            zonaEleccion.hidden = true;
            cargar();
          });
          return boton;
        }),
      }),
    );
    zonaEleccion.hidden = false;
    pintarDesenlace(partes, { resultado: RESULTADO.VACIO }, () => [], {
      vacio: { titulo: 'Elige la cuenta', detalle: 'El listado se filtra en cuanto la elijas.' },
    });
  }

  async function cargar() {
    pedido += 1;
    const estePedido = pedido;
    const desenlace = await consultarApi(recursoDelListado(filtros, { usuarioId, pagina }));
    if (estePedido !== pedido) {
      return;
    }
    if (desenlace.resultado === RESULTADO.DATOS) {
      const nombrados = await titulares.nombrar(desenlace.datos.contenido.map((s) => s.usuarioId));
      if (estePedido !== pedido) {
        return;
      }
      if (!nombrados) {
        pintarAviso(contexto.zonaAviso, {
          tono: 'advertencia',
          titulo: 'No se pudieron cargar los apodos de las cuentas',
          detalle:
            'El listado está completo, pero las cuentas salen sin nombre. Vuelve a intentarlo en un momento.',
        });
      }
    }
    const conFiltros = Object.values(filtros).some(Boolean) || Boolean(usuarioId);
    pintarDesenlace(partes, desenlace, (datos) => pintarPagina(datos), {
      alReintentar: cargar,
      vacio: conFiltros
        ? {
            titulo: 'Ninguna sanción coincide con los filtros',
            detalle: 'Quita alguno para ver más.',
          }
        : { titulo: 'Todavía no se ha emitido ninguna sanción' },
    });
  }

  function pintarPagina(datos) {
    const filas = Array.isArray(datos?.contenido) ? datos.contenido : [];
    const total = Number(datos?.total) || filas.length;
    const desdeFila = pagina * POR_PAGINA + 1;
    const hastaFila = pagina * POR_PAGINA + filas.length;
    const tabla = tablaDeDatos({
      titulo: 'Sanciones emitidas',
      resumen: `${desdeFila}–${hastaFila} de ${total} ${total === 1 ? 'sanción' : 'sanciones'}`,
      claveDeFila: (s) => s.id,
      columnas: [
        {
          clave: 'emitidaEn',
          titulo: 'Emitida',
          tipo: 'fecha',
          valor: (s) => fechaHora(s.emitidaEn),
        },
        {
          clave: 'usuarioId',
          titulo: 'Cuenta',
          principal: true,
          valor: (s) => cuentaDe(s.usuarioId, contexto),
        },
        { clave: 'tipo', titulo: 'Tipo', valor: (s) => NOMBRE_DE_SANCION[s.tipo] ?? s.tipo },
        { clave: 'causal', titulo: 'Causal', valor: (s) => nombreDeCausal(s.causal) },
        {
          clave: 'estado',
          titulo: 'Estado',
          tipo: 'estado',
          valor: (s) => distintivoDeLaSancion(s),
        },
        { clave: 'emisor', titulo: 'Emitida por', valor: (s) => emisorDe(s) },
        {
          clave: 'acciones',
          titulo: 'Acciones',
          tipo: 'accion',
          valor: (s) => {
            const boton = h('button', {
              clase: 'boton boton--secundario boton--pequeno',
              texto: 'Ver detalle',
              atributos: {
                type: 'button',
                'aria-label': `Ver el detalle de la sanción de ${titulares.de(s.usuarioId)?.apodo ?? 'esta cuenta'}`,
              },
              datos: { accion: 'ver-sancion' },
            });
            boton.addEventListener('click', () => abrirDetalleDeSancion(s, contexto));
            return boton;
          },
        },
      ],
      filas,
    });
    const paginacion = construirPaginacion(
      { paginaActual: pagina, totalPaginas: Number(datos?.totalPaginas) || 1 },
      (nueva) => {
        pagina = nueva;
        cargar();
      },
      { etiqueta: 'Páginas de sanciones' },
    );
    return [tabla, paginacion];
  }

  return { elemento: partes.elemento, cargar, aplicar, barra };
}

/** El distintivo del estado de una sanción, con la fecha fin de una suspensión vigente. */
function distintivoDeLaSancion(sancion) {
  const codigo = estadoDeLaSancion(sancion);
  const distintivo = estadoSegun(codigo, ESTADOS_DE_LA_SANCION);
  if (codigo === 'VIGENTE' && sancion.tipo === TIPO.SUSPENSION && sancion.vigenteHasta) {
    return h('span', {
      clase: 'estado-con-fecha',
      hijos: [
        distintivo,
        h('span', { clase: 't-meta', texto: `hasta el ${fechaHora(sancion.vigenteHasta)}` }),
      ],
    });
  }
  return distintivo;
}

/**
 * El detalle de una sanción en el panel lateral, con lo que se puede hacer
 * con ella.
 */
function abrirDetalleDeSancion(sancion, contexto) {
  const { titulares, administra } = contexto;
  const titular = titulares.de(sancion.usuarioId);
  const codigo = estadoDeLaSancion(sancion);
  const acciones = [];

  const otraSancion = h('button', {
    clase: 'boton boton--secundario',
    texto: 'Emitir otra sanción a esta cuenta',
    atributos: { type: 'button' },
    datos: { accion: 'emitir-a-la-cuenta' },
  });
  acciones.push(otraSancion);

  const sePuedeLevantar = administra && codigo === 'VIGENTE' && sancion.tipo !== TIPO.BANEO;
  const levantar = sePuedeLevantar
    ? h('button', {
        clase: 'boton boton--peligro',
        texto:
          sancion.tipo === TIPO.ADVERTENCIA ? 'Retirar la advertencia' : 'Levantar la suspensión',
        atributos: { type: 'button' },
        datos: { accion: 'levantar' },
      })
    : null;
  if (levantar) {
    acciones.push(levantar);
  }

  const contenido = [
    listaDeDatos(
      [
        ['Cuenta', cuentaDe(sancion.usuarioId, contexto)],
        ['Tipo', NOMBRE_DE_SANCION[sancion.tipo] ?? sancion.tipo],
        ['Causal', nombreDeCausal(sancion.causal)],
        ['Motivo', sancion.motivo],
        ['Política', sancion.politica],
        ['Estado', distintivoDeLaSancion(sancion)],
        ['Emitida', `${fechaHora(sancion.emitidaEn)} · ${emisorDe(sancion)}`],
        sancion.tipo === TIPO.SUSPENSION ? ['Termina', fechaHora(sancion.vigenteHasta)] : null,
        sancion.revertidaEn
          ? [
              'Revertida',
              `${fechaHora(sancion.revertidaEn)} · ${sancion.motivoReversion ?? ''}`.trim(),
            ]
          : null,
      ].filter(Boolean),
    ),
  ];
  if (sancion.tipo === TIPO.BANEO && codigo === 'VIGENTE') {
    contenido.push(
      h('p', {
        clase: 't-meta',
        texto:
          'Un baneo definitivo no se levanta a mano: solo lo revierte una apelación aceptada por el panel.',
      }),
    );
  }

  const { cerrar } = abrirPanelLateral({
    titulo: `${NOMBRE_DE_SANCION[sancion.tipo] ?? 'Sanción'} de ${titular?.apodo ?? 'la cuenta'}`,
    subtitulo: nombreDeCausal(sancion.causal),
    contenido,
    acciones,
  });

  otraSancion.addEventListener('click', () => {
    cerrar();
    contexto.emitirA(titular ?? { uid: sancion.usuarioId, apodo: null });
  });
  levantar?.addEventListener('click', async () => {
    const motivo = await confirmarConMotivo({
      titulo:
        sancion.tipo === TIPO.ADVERTENCIA
          ? `¿Retirar la advertencia de ${titular?.apodo ?? 'esta cuenta'}?`
          : `¿Levantar la suspensión de ${titular?.apodo ?? 'esta cuenta'}?`,
      consecuencias: [
        sancion.tipo === TIPO.SUSPENSION
          ? 'Podrá volver a entrar enseguida, si no tiene otra sanción que se lo impida.'
          : 'Deja de contar en su historial como vigente.',
        'La sanción no se borra: queda revertida, con tu motivo.',
        'Recibe un aviso en la aplicación.',
      ],
      etiquetaMotivo: 'Motivo',
      textoConfirmar: sancion.tipo === TIPO.ADVERTENCIA ? 'Retirar' : 'Levantar',
    });
    if (motivo === null) {
      return;
    }
    try {
      await api.levantar(sancion.id, motivo, contexto.fetchImpl);
      cerrar();
      pintarAviso(contexto.zonaAviso, {
        tono: 'exito',
        titulo: sancion.tipo === TIPO.ADVERTENCIA ? 'Advertencia retirada' : 'Suspensión levantada',
        detalle: `${titular?.apodo ?? 'La cuenta'} queda avisada.`,
      });
      contexto.alCambiar();
    } catch (error) {
      avisarFallo(contexto.zonaAviso, error);
    }
  });
}

/* -------------------------------------------------------------------------
   Apelaciones
   ------------------------------------------------------------------------- */

/** Lo que la confirmación dice de cada decisión. */
const MENSAJE_DE_LA_DECISION = Object.freeze({
  REVERTIDA: () =>
    'La sanción queda revertida y la cuenta recupera el acceso si no tiene otra que se lo impida.',
  REDUCIDA: (cuerpo) => `La suspensión terminará el ${fechaHora(cuerpo.nuevaVigencia)}.`,
  MANTENIDA: () => 'La sanción sigue como está.',
});

const OPCIONES_DE_APELACION = Object.freeze([
  ['PENDIENTES', 'Por resolver'],
  ['RESUELTAS', 'Resueltas'],
  ['TODAS', 'Todas'],
]);

function panelDeApelaciones(contexto) {
  const { consultarApi, titulares, ahora } = contexto;
  const partes = panel({
    id: 'apelaciones',
    titulo: 'Apelaciones',
    descripcion:
      'El panel decide cada apelación en quince días hábiles desde que se presentó. Las que se ' +
      'pasaron de plazo van marcadas.',
    ancho: 'completo',
  });
  let estado = 'PENDIENTES';
  let pagina = 0;
  /** @type {Map<string, object>} */
  const sanciones = new Map();

  const barra = barraDeFiltros({
    etiqueta: 'Filtrar apelaciones',
    filtros: [
      {
        nombre: 'estado',
        etiqueta: 'Mostrar',
        opciones: OPCIONES_DE_APELACION,
        todos: 'Por resolver',
      },
    ],
    valores: { estado: '' },
    alAplicar: (valores) => {
      estado = valores.estado || 'PENDIENTES';
      pagina = 0;
      cargar();
    },
  });
  partes.elemento.insertBefore(
    h('div', { clase: 'panel-admin__filtros', hijos: [barra.elemento] }),
    partes.zona,
  );

  async function cargar() {
    const desenlace = await consultarApi(`/apelaciones?estado=${estado}`);
    let lista = [];
    if (desenlace.resultado === RESULTADO.DATOS && Array.isArray(desenlace.datos)) {
      // Las que vencen antes, primero: es el orden en que hay que decidirlas.
      lista = [...desenlace.datos].sort((a, b) =>
        a.estado === 'PENDIENTE' && b.estado === 'PENDIENTE'
          ? String(a.decisionAntesDe).localeCompare(String(b.decisionAntesDe))
          : String(b.creadaEn).localeCompare(String(a.creadaEn)),
      );
      const visibles = lista.slice(pagina * POR_PAGINA, (pagina + 1) * POR_PAGINA);
      await Promise.all([
        titulares.nombrar(visibles.map((a) => a.usuarioId)),
        ...visibles
          .filter((a) => !sanciones.has(a.sancionId))
          .map(async (a) => {
            try {
              sanciones.set(a.sancionId, await api.sancion(a.sancionId, contexto.fetchImpl));
            } catch {
              // Sin la sanción, la fila dice «Sin dato» en su tipo y causal.
            }
          }),
      ]);
    }
    pintarDesenlace(partes, desenlace, () => pintarPagina(lista), {
      alReintentar: cargar,
      vacio:
        estado === 'PENDIENTES'
          ? {
              titulo: 'No hay apelaciones por resolver',
              detalle: 'Cuando un jugador apele, aparecerá aquí.',
            }
          : { titulo: 'No hay apelaciones' },
    });
  }

  function pintarPagina(lista) {
    const visibles = lista.slice(pagina * POR_PAGINA, (pagina + 1) * POR_PAGINA);
    const tabla = tablaDeDatos({
      titulo: 'Apelaciones',
      resumen: `${lista.length} ${lista.length === 1 ? 'apelación' : 'apelaciones'}`,
      claveDeFila: (a) => a.id,
      columnas: [
        {
          clave: 'creadaEn',
          titulo: 'Presentada',
          tipo: 'fecha',
          valor: (a) => fechaHora(a.creadaEn),
        },
        {
          clave: 'usuarioId',
          titulo: 'Cuenta',
          principal: true,
          valor: (a) => cuentaDe(a.usuarioId, contexto),
        },
        {
          clave: 'sancion',
          titulo: 'Sanción apelada',
          valor: (a) => {
            const s = sanciones.get(a.sancionId);
            if (!s) {
              return null;
            }
            // El tipo, y debajo la causal en pequeño: en una sola línea la
            // causal larga estiraba la columna y la fila entera.
            return h('span', {
              clase: 'celda-doble',
              hijos: [
                h('span', { texto: NOMBRE_DE_SANCION[s.tipo] ?? s.tipo }),
                nombreDeCausal(s.causal)
                  ? h('span', { clase: 't-meta', texto: nombreDeCausal(s.causal) })
                  : null,
              ],
            });
          },
        },
        {
          clave: 'decisionAntesDe',
          titulo: 'Decidir antes del',
          tipo: 'fecha',
          valor: (a) => fechaHora(a.decisionAntesDe),
        },
        {
          clave: 'plazo',
          titulo: 'Plazo',
          tipo: 'estado',
          valor: (a) => distintivoDeEstado(plazoDelPanel(a, ahora())),
        },
        {
          clave: 'estado',
          titulo: 'Estado',
          tipo: 'estado',
          valor: (a) => estadoSegun(a.estado, ESTADOS_DE_LA_APELACION),
        },
        {
          clave: 'acciones',
          titulo: 'Acciones',
          tipo: 'accion',
          valor: (a) => {
            const pendiente = a.estado === 'PENDIENTE';
            const boton = h('button', {
              clase: `boton ${pendiente ? 'boton--primario' : 'boton--secundario'} boton--pequeno`,
              texto: pendiente ? 'Resolver' : 'Ver decisión',
              atributos: { type: 'button' },
              datos: { accion: pendiente ? 'resolver' : 'ver-decision' },
            });
            boton.addEventListener('click', () =>
              abrirApelacion(a, sanciones.get(a.sancionId) ?? null, contexto),
            );
            return boton;
          },
        },
      ],
      filas: visibles,
    });
    const paginacion = construirPaginacion(
      { paginaActual: pagina, totalPaginas: Math.max(1, Math.ceil(lista.length / POR_PAGINA)) },
      (nueva) => {
        pagina = nueva;
        cargar();
      },
      { etiqueta: 'Páginas de apelaciones' },
    );
    return [tabla, paginacion];
  }

  return { elemento: partes.elemento, cargar };
}

/**
 * Una apelación en el panel lateral: el argumento del jugador, la sanción y,
 * si sigue pendiente, la decisión motivada.
 */
function abrirApelacion(apelacion, sancion, contexto) {
  const { titulares, ahora } = contexto;
  const titular = titulares.de(apelacion.usuarioId);
  const pendiente = apelacion.estado === 'PENDIENTE';
  const datos = listaDeDatos(
    [
      ['Cuenta', cuentaDe(apelacion.usuarioId, contexto)],
      ['Sanción', sancion ? (NOMBRE_DE_SANCION[sancion.tipo] ?? sancion.tipo) : null],
      ['Causal', sancion ? nombreDeCausal(sancion.causal) : null],
      ['Motivo de la sanción', sancion?.motivo],
      sancion?.tipo === TIPO.SUSPENSION ? ['Termina', fechaHora(sancion.vigenteHasta)] : null,
      ['Presentada', fechaHora(apelacion.creadaEn)],
      [
        'Decidir antes del',
        h('span', {
          hijos: [
            fechaHora(apelacion.decisionAntesDe),
            ' ',
            distintivoDeEstado(plazoDelPanel(apelacion, ahora())),
          ],
        }),
      ],
      ['Estado', estadoSegun(apelacion.estado, ESTADOS_DE_LA_APELACION)],
      apelacion.decisionMotivo ? ['Decisión', apelacion.decisionMotivo] : null,
      apelacion.resueltaEn ? ['Resuelta', fechaHora(apelacion.resueltaEn)] : null,
      apelacion.nuevaVigencia ? ['Nueva fecha fin', fechaHora(apelacion.nuevaVigencia)] : null,
    ].filter(Boolean),
  );
  const argumento = h('section', {
    clase: 'pila pila--compacta',
    hijos: [
      h('h3', { clase: 't-subtitulo', texto: 'Lo que dice el jugador' }),
      h('blockquote', { clase: 'cita-admin', texto: apelacion.argumento }),
    ],
  });

  const contenido = [argumento, datos];
  const acciones = [];
  let formulario = null;
  if (pendiente) {
    formulario = formularioDeResolucion(sancion);
    contenido.push(formulario.elemento);
    acciones.push(formulario.boton);
  }
  const { cerrar } = abrirPanelLateral({
    titulo: `Apelación de ${titular?.apodo ?? 'la cuenta'}`,
    subtitulo: pendiente
      ? 'Por resolver'
      : (ESTADOS_DE_LA_APELACION[apelacion.estado]?.texto ?? null),
    contenido,
    acciones,
  });

  formulario?.alResolver(async (cuerpo) => {
    const decision = { MANTENIDA: 'mantener', REDUCIDA: 'reducir', REVERTIDA: 'revertir' }[
      cuerpo.decision
    ];
    const confirmado = await confirmar({
      titulo: `¿${decision.charAt(0).toUpperCase()}${decision.slice(1)} la sanción de ${titular?.apodo ?? 'esta cuenta'}?`,
      mensaje: MENSAJE_DE_LA_DECISION[cuerpo.decision](cuerpo),
      textoConfirmar: 'Resolver',
      peligro: false,
    });
    if (!confirmado) {
      return;
    }
    try {
      await api.resolver(apelacion.id, cuerpo, contexto.fetchImpl);
      cerrar();
      pintarAviso(contexto.zonaAviso, {
        tono: 'exito',
        titulo: 'Apelación resuelta',
        detalle: `${titular?.apodo ?? 'El jugador'} recibe la decisión con tu motivación.`,
      });
      contexto.alCambiar();
    } catch (error) {
      formulario.mostrarError(error);
    }
  });
}

/**
 * El formulario de la decisión: mantener, reducir (solo una suspensión) o
 * revertir, con su motivación; reducir pide la nueva fecha fin.
 */
function formularioDeResolucion(sancion) {
  const id = `resolucion-${Math.random().toString(36).slice(2, 8)}`;
  const opciones = [
    ['MANTENIDA', 'Mantener la sanción'],
    sancion?.tipo === TIPO.SUSPENSION ? ['REDUCIDA', 'Reducir la suspensión'] : null,
    ['REVERTIDA', 'Revertir la sanción'],
  ].filter(Boolean);
  const decision = h('select', {
    clase: 'desplegable__control',
    atributos: { id: `${id}-decision`, name: 'decision', required: true },
    hijos: [
      h('option', { texto: 'Elige la decisión', atributos: { value: '' } }),
      ...opciones.map(([valor, texto]) => h('option', { texto, atributos: { value: valor } })),
    ],
  });
  const nuevaVigencia = h('input', {
    clase: 'campo__control',
    atributos: { id: `${id}-vigencia`, name: 'nuevaVigencia', type: 'datetime-local' },
  });
  const campoVigencia = h('label', {
    clase: 'campo',
    atributos: { for: `${id}-vigencia` },
    hijos: [
      h('span', { clase: 'campo__etiqueta', texto: 'Nueva fecha fin' }),
      nuevaVigencia,
      h('span', {
        clase: 'campo__pista',
        texto: sancion?.vigenteHasta
          ? `Antes del ${fechaHora(sancion.vigenteHasta)}, que es cuando termina ahora.`
          : 'Antes de la fecha en que termina ahora.',
      }),
    ],
  });
  campoVigencia.hidden = true;
  const motivo = h('textarea', {
    clase: 'campo__control',
    atributos: { id: `${id}-motivo`, name: 'motivo', rows: 4, maxlength: 1000, required: true },
  });
  const error = h('p', {
    clase: 'campo__error',
    atributos: { role: 'alert' },
    datos: { zona: 'error-resolucion' },
  });
  error.hidden = true;
  const elemento = h('form', {
    clase: 'pila pila--compacta',
    atributos: { novalidate: true, 'aria-label': 'Resolver la apelación' },
    datos: { zona: 'resolucion' },
    hijos: [
      h('h3', { clase: 't-subtitulo', texto: 'Decisión del panel' }),
      h('label', {
        clase: 'desplegable',
        atributos: { for: `${id}-decision` },
        hijos: [h('span', { clase: 'campo__etiqueta', texto: 'Decisión' }), decision],
      }),
      campoVigencia,
      h('label', {
        clase: 'campo',
        atributos: { for: `${id}-motivo` },
        hijos: [
          h('span', { clase: 'campo__etiqueta', texto: 'Motivación' }),
          motivo,
          h('span', {
            clase: 'campo__pista',
            texto: 'El jugador la recibe tal cual. Mínimo 10 caracteres.',
          }),
        ],
      }),
      error,
    ],
  });
  const boton = h('button', {
    clase: 'boton boton--primario',
    texto: 'Resolver apelación',
    atributos: { type: 'button' },
    datos: { accion: 'enviar-resolucion' },
  });
  decision.addEventListener('change', () => {
    campoVigencia.hidden = decision.value !== 'REDUCIDA';
  });

  let alResolver = null;
  function mostrarError(problema) {
    error.textContent =
      problema instanceof ErrorDeSanciones
        ? problema.detalle
        : 'No pudimos contactar con el servicio. Vuelve a intentarlo.';
    error.hidden = false;
  }
  function enviar() {
    error.hidden = true;
    const valor = decision.value;
    const texto = motivo.value.trim();
    if (!valor) {
      error.textContent = 'Elige la decisión.';
      error.hidden = false;
      decision.focus();
      return;
    }
    if (texto.length < 10) {
      error.textContent = 'Escribe la motivación (al menos 10 caracteres): el jugador la recibe.';
      error.hidden = false;
      motivo.focus();
      return;
    }
    const cuerpo = { decision: valor, motivo: texto };
    if (valor === 'REDUCIDA') {
      if (!nuevaVigencia.value) {
        error.textContent = 'Indica la nueva fecha fin de la suspensión.';
        error.hidden = false;
        nuevaVigencia.focus();
        return;
      }
      cuerpo.nuevaVigencia = new Date(nuevaVigencia.value).toISOString();
    }
    alResolver?.(cuerpo);
  }
  boton.addEventListener('click', enviar);
  elemento.addEventListener('submit', (evento) => {
    evento.preventDefault();
    enviar();
  });
  return {
    elemento,
    boton,
    mostrarError,
    alResolver: (funcion) => {
      alResolver = funcion;
    },
  };
}

/* -------------------------------------------------------------------------
   Emitir
   ------------------------------------------------------------------------- */

function panelDeEmision(contexto) {
  const { titulares, rol, administra, ahora } = contexto;
  const partes = panel({
    id: 'emitir',
    titulo: 'Emitir una sanción',
    descripcion: 'Primero la cuenta: su estado y su historial están a la vista antes de decidir.',
    ancho: 'completo',
  });
  let limites = null;
  /** @type {{uid: string, apodo: string|null, estado?: string, rol?: string|null}|null} */
  let titular = null;

  const zonaResultados = h('div', {
    clase: 'pila pila--compacta',
    datos: { zona: 'resultados-de-cuenta' },
  });
  const zonaCuenta = h('div', { clase: 'pila pila--compacta', datos: { zona: 'cuenta-elegida' } });
  const formulario = formularioDeEmision({ rol });
  formulario.elemento.hidden = true;

  const buscador = barraDeFiltros({
    etiqueta: 'Buscar la cuenta a sancionar',
    busqueda: { nombre: 'cuenta', etiqueta: 'Cuenta', placeholder: 'Apodo o uid de la cuenta' },
    validar: (valores) => {
      if (!valores.cuenta) {
        return 'Escribe el apodo o el uid de la cuenta.';
      }
      return !UID.test(valores.cuenta) && valores.cuenta.length < 2
        ? 'Escribe al menos dos letras del apodo.'
        : null;
    },
    alAplicar: (valores) => buscar(valores.cuenta),
  });

  vaciar(partes.zona).append(buscador.elemento, zonaResultados, zonaCuenta, formulario.elemento);
  partes.sello.dataset.estado = RESULTADO.DATOS;

  async function buscar(texto) {
    pintarEstado(zonaResultados, estadoDeCarga({ filas: 2, etiqueta: 'Buscando la cuenta' }));
    const desenlace = await titulares.buscar(texto);
    if (desenlace.resultado === RESULTADO.VACIO) {
      pintarEstado(
        zonaResultados,
        estadoVacio({
          titulo: `Ninguna cuenta coincide con «${texto}»`,
          detalle: 'Revisa el apodo o escribe su uid completo.',
        }),
      );
      return;
    }
    if (desenlace.resultado !== RESULTADO.DATOS) {
      pintarEstado(
        zonaResultados,
        estadoDeError({
          titulo: 'No pudimos buscar la cuenta',
          detalle:
            desenlace.motivo || 'El servicio de identidad no respondió. Vuelve a intentarlo.',
          alReintentar: () => buscar(texto),
        }),
      );
      return;
    }
    if (desenlace.datos.length === 1) {
      vaciar(zonaResultados);
      elegir(desenlace.datos[0]);
      return;
    }
    vaciar(zonaResultados).append(
      tablaDeDatos({
        titulo: 'Cuentas que coinciden',
        resumen: `${desenlace.datos.length} cuentas coinciden: elige la que quieres sancionar.`,
        claveDeFila: (t) => t.uid,
        columnas: [
          { clave: 'apodo', titulo: 'Apodo', principal: true },
          { clave: 'rol', titulo: 'Rol', valor: (t) => NOMBRE_DE_ROL[t.rol] ?? null },
          {
            clave: 'estado',
            titulo: 'Estado',
            tipo: 'estado',
            valor: (t) => estadoSegun(t.estado, ESTADOS_DE_CUENTA_ADMIN),
          },
          {
            clave: 'acciones',
            titulo: 'Acciones',
            tipo: 'accion',
            valor: (t) => {
              const boton = h('button', {
                clase: 'boton boton--secundario boton--pequeno',
                texto: 'Elegir',
                atributos: { type: 'button', 'aria-label': `Elegir a ${t.apodo}` },
                datos: { accion: 'elegir-cuenta' },
              });
              boton.addEventListener('click', () => {
                vaciar(zonaResultados);
                elegir(t);
              });
              return boton;
            },
          },
        ],
        filas: desenlace.datos,
      }),
    );
  }

  async function elegirPorUid(uid) {
    pintarEstado(zonaCuenta, estadoDeCarga({ filas: 2, etiqueta: 'Cargando la cuenta' }));
    const nombrada = await titulares.nombrar([uid]);
    const conocido = titulares.de(uid);
    if (!conocido) {
      pintarEstado(
        zonaCuenta,
        nombrada
          ? estadoVacio({ titulo: 'Esa cuenta no existe', detalle: 'Búscala por su apodo.' })
          : estadoDeError({
              titulo: 'No pudimos identificar la cuenta',
              detalle: 'El servicio de identidad no respondió. Vuelve a intentarlo.',
              alReintentar: () => elegirPorUid(uid),
            }),
      );
      return;
    }
    buscador.fijar({ cuenta: conocido.apodo });
    elegir(conocido);
  }

  async function elegir(elegido) {
    titular = elegido?.apodo ? elegido : (titulares.de(elegido?.uid) ?? elegido);
    if (!titular?.apodo && titular?.uid) {
      await elegirPorUid(titular.uid);
      return;
    }
    formulario.elemento.hidden = false;
    formulario.preparar({ titular, limites });
    await pintarCuenta();
  }

  async function pintarCuenta() {
    const ficha = administra ? urlDeFicha(titular.uid) : null;
    const enlaceFicha = ficha
      ? h('a', {
          clase: 'boton boton--contorno boton--pequeno',
          texto: 'Abrir su ficha',
          atributos: { href: ficha },
          datos: { accion: 'abrir-ficha' },
        })
      : null;
    vaciar(zonaCuenta).append(
      h('div', {
        clase: 'fila fila--envuelta fila--entre',
        hijos: [
          h('div', {
            clase: 'pila pila--ajustada',
            hijos: [
              h('h3', {
                clase: 't-subtitulo',
                texto: titular.apodo,
                datos: { zona: 'apodo-elegido' },
              }),
              h('div', {
                clase: 'fila fila--envuelta',
                hijos: [
                  titular.estado ? estadoSegun(titular.estado, ESTADOS_DE_CUENTA_ADMIN) : null,
                  titular.rol
                    ? h('span', { clase: 't-meta', texto: NOMBRE_DE_ROL[titular.rol] ?? '' })
                    : null,
                ],
              }),
            ],
          }),
          enlaceFicha,
        ],
      }),
    );
    const historial = h('div', { clase: 'pila pila--compacta', datos: { zona: 'historial' } });
    zonaCuenta.append(historial);
    pintarEstado(historial, estadoDeCarga({ filas: 2, etiqueta: 'Cargando su historial' }));
    try {
      const sanciones = await api.historial(titular.uid, contexto.fetchImpl);
      historial.replaceChildren(
        estadoDeCuenta(sanciones, { persona: 'tercera', titulo: 'Estado de la cuenta' }),
      );
      historial.append(
        sanciones.length === 0
          ? h('p', { clase: 't-meta', texto: 'No tiene sanciones.' })
          : lineaDeTiempo(hechosDeSanciones(sanciones, { ahora: ahora() }), {
              etiqueta: 'Historial de sanciones de la cuenta',
            }),
      );
    } catch (error) {
      pintarEstado(
        historial,
        estadoDeError({
          titulo:
            error instanceof ErrorDeSanciones ? error.titulo : 'No pudimos cargar su historial',
          detalle:
            error instanceof ErrorDeSanciones
              ? error.detalle
              : 'El servicio de moderación no respondió. Vuelve a intentarlo.',
          alReintentar: pintarCuenta,
        }),
      );
    }
  }

  async function cargarLimites() {
    try {
      limites = await api.limites(contexto.fetchImpl);
    } catch (error) {
      // Sin límites el campo no promete ningún rango: el servicio sigue
      // validando y su respuesta dice el real. Inventar uno sería peor.
      console.warn('[sanciones] no se pudieron leer los límites vigentes:', error);
      limites = null;
    }
    formulario.preparar({ titular, limites });
  }

  formulario.alEmitir(async (datos) => {
    if (!titular) {
      return;
    }
    const cuerpo = solicitudDeEmision(datos, titular, { comentarioId: contexto.comentarioPedido });
    const confirmado = await confirmarEmision(cuerpo, titular, ahora());
    if (!confirmado) {
      return;
    }
    formulario.ocupado(true);
    try {
      const sancion = await api.emitir(cuerpo, contexto.fetchImpl);
      pintarAviso(contexto.zonaAviso, {
        tono: 'exito',
        titulo: 'Sanción emitida',
        detalle: `${NOMBRE_DE_SANCION[sancion.tipo] ?? 'Sanción'} para ${titular.apodo}. Recibe el aviso y el correo con la causal y cómo apelar.`,
      });
      formulario.limpiar();
      contexto.alCambiar();
      await pintarCuenta();
    } catch (error) {
      formulario.mostrarError(error);
    } finally {
      formulario.ocupado(false);
    }
  });

  return { elemento: partes.elemento, elegir, elegirPorUid, cargarLimites, buscar };
}

/**
 * La confirmación de la emisión: lo que va a pasar, en frases. El baneo pide
 * además escribir el apodo de la cuenta (§7.3.9, acción crítica).
 */
function confirmarEmision(cuerpo, titular, ahora) {
  const consecuencias = consecuenciasDeLaEmision(cuerpo.tipo, {
    horas: cuerpo.duracionHoras,
    ahora,
  });
  const causal = nombreDeCausal(cuerpo.causal);
  if (cuerpo.tipo === TIPO.BANEO) {
    return confirmarCritico({
      titulo: `Banear definitivamente a ${titular.apodo}`,
      mensaje: `Causal: ${causal}. Esta acción es permanente.`,
      consecuencias,
      palabra: titular.apodo,
      textoConfirmar: 'Banear',
    });
  }
  const cuerpoDelDialogo = h('div', {
    clase: 'dialogo__cuerpo confirmacion-critica',
    hijos: [
      h('p', { texto: `Causal: ${causal}.` }),
      h('ul', {
        clase: 'confirmacion-critica__consecuencias',
        hijos: consecuencias.map((texto) => h('li', { texto })),
      }),
    ],
  });
  return confirmar({
    titulo:
      cuerpo.tipo === TIPO.SUSPENSION
        ? `¿Suspender a ${titular.apodo} durante ${duracionLegible(cuerpo.duracionHoras)}?`
        : `¿Advertir a ${titular.apodo}?`,
    cuerpo: cuerpoDelDialogo,
    textoConfirmar: cuerpo.tipo === TIPO.SUSPENSION ? 'Suspender' : 'Emitir la advertencia',
    peligro: cuerpo.tipo === TIPO.SUSPENSION,
  });
}

/**
 * El formulario de la emisión: tipo, causal obligatoria, duración (solo la
 * suspensión), motivo y política.
 *
 * @param {{rol: string}} opciones
 */
function formularioDeEmision({ rol }) {
  const id = `emision-${Math.random().toString(36).slice(2, 8)}`;
  const moderador = !ROLES_DE_ADMINISTRACION.includes(rol);

  const tipo = h('select', {
    clase: 'desplegable__control',
    atributos: { id: `${id}-tipo`, name: 'tipo', required: true },
    hijos: OPCIONES_DE_TIPO.map(([valor, texto]) =>
      h('option', {
        texto: valor === TIPO.BANEO && moderador ? `${texto} (solo administración)` : texto,
        atributos: { value: valor, disabled: valor === TIPO.BANEO && moderador },
      }),
    ),
  });
  const causal = h('select', {
    clase: 'desplegable__control',
    atributos: { id: `${id}-causal`, name: 'causal', required: true },
    hijos: [
      h('option', { texto: 'Elige la causal', atributos: { value: '' } }),
      ...OPCIONES_DE_CAUSAL.map(([valor, texto]) =>
        h('option', { texto, atributos: { value: valor } }),
      ),
    ],
  });
  const cantidad = h('input', {
    clase: 'campo__control',
    atributos: {
      id: `${id}-cantidad`,
      name: 'cantidad',
      type: 'number',
      min: 1,
      step: 1,
      inputmode: 'numeric',
    },
  });
  cantidad.value = '1';
  const unidad = h('select', {
    clase: 'desplegable__control',
    atributos: { id: `${id}-unidad`, name: 'unidad' },
    hijos: Object.entries(UNIDADES_DE_DURACION).map(([valor, { plural }]) =>
      h('option', {
        texto: plural.charAt(0).toUpperCase() + plural.slice(1),
        atributos: { value: valor },
      }),
    ),
  });
  unidad.value = 'DIAS';
  const pistaDuracion = h('span', { clase: 'campo__pista', datos: { campo: 'rango-suspension' } });
  const zonaDuracion = h('fieldset', {
    clase: 'campo campo--grupo',
    datos: { solo: TIPO.SUSPENSION },
    hijos: [
      h('legend', { clase: 'campo__etiqueta', texto: 'Duración' }),
      h('div', {
        clase: 'fila fila--envuelta',
        hijos: [
          h('label', {
            clase: 'solo-lectores',
            texto: 'Cantidad',
            atributos: { for: `${id}-cantidad` },
          }),
          cantidad,
          h('label', {
            clase: 'solo-lectores',
            texto: 'Unidad',
            atributos: { for: `${id}-unidad` },
          }),
          unidad,
        ],
      }),
      pistaDuracion,
    ],
  });
  const motivo = h('textarea', {
    clase: 'campo__control',
    atributos: { id: `${id}-motivo`, name: 'motivo', rows: 4, maxlength: 1000, required: true },
  });
  const politica = h('input', {
    clase: 'campo__control',
    atributos: { id: `${id}-politica`, name: 'politica', type: 'text', maxlength: 200 },
  });
  const error = h('p', {
    clase: 'campo__error',
    atributos: { role: 'alert' },
    datos: { zona: 'error-emision' },
  });
  error.hidden = true;
  const enviar = h('button', {
    clase: 'boton boton--primario',
    texto: 'Revisar y emitir',
    atributos: { type: 'submit' },
    datos: { accion: 'emitir' },
  });

  const titulo = h('h3', {
    clase: 't-subtitulo formulario-sancion__titulo',
    texto: 'Nueva sanción',
    datos: { zona: 'titulo-de-la-emision' },
  });

  const elemento = h('form', {
    clase: 'pila pila--compacta formulario-sancion',
    atributos: { novalidate: true, 'aria-label': 'Emitir la sanción' },
    datos: { zona: 'emitir' },
    hijos: [
      titulo,
      h('label', {
        clase: 'desplegable',
        atributos: { for: `${id}-tipo` },
        hijos: [h('span', { clase: 'campo__etiqueta', texto: 'Tipo de sanción' }), tipo],
      }),
      h('label', {
        clase: 'desplegable',
        atributos: { for: `${id}-causal` },
        hijos: [
          h('span', { clase: 'campo__etiqueta', texto: 'Causal' }),
          causal,
          h('span', {
            clase: 'campo__pista',
            texto: 'Una de las causales del reglamento de la comunidad.',
          }),
        ],
      }),
      zonaDuracion,
      h('label', {
        clase: 'campo',
        atributos: { for: `${id}-motivo` },
        hijos: [
          h('span', { clase: 'campo__etiqueta', texto: 'Motivo' }),
          motivo,
          h('span', {
            clase: 'campo__pista',
            texto:
              'El hecho concreto: qué hizo y dónde. La cuenta lo recibe tal cual. Mínimo 10 caracteres.',
          }),
        ],
      }),
      h('label', {
        clase: 'campo',
        atributos: { for: `${id}-politica` },
        hijos: [
          h('span', { clase: 'campo__etiqueta', texto: 'Política violada (opcional)' }),
          politica,
        ],
      }),
      error,
      h('div', { clase: 'acciones', hijos: [enviar] }),
    ],
  });

  let rango = null;
  const sincronizar = () => {
    zonaDuracion.hidden = tipo.value !== TIPO.SUSPENSION;
  };
  tipo.addEventListener('change', sincronizar);
  sincronizar();

  let alEmitir = null;
  elemento.addEventListener('submit', (evento) => {
    evento.preventDefault();
    error.hidden = true;
    const datos = {
      tipo: tipo.value,
      causal: causal.value,
      motivo: motivo.value,
      politica: politica.value,
      cantidad: cantidad.value,
      unidad: unidad.value,
    };
    if (!datos.causal) {
      return falla('Elige la causal: toda sanción se emite por una de las del reglamento.', causal);
    }
    if (datos.tipo === TIPO.SUSPENSION) {
      const motivoDeDuracion = motivoDeDuracionInvalida(
        duracionEnHoras(datos.cantidad, datos.unidad),
        rango,
      );
      if (motivoDeDuracion) {
        return falla(motivoDeDuracion, cantidad);
      }
    }
    if (datos.motivo.trim().length < 10) {
      return falla(
        'Escribe el motivo (al menos 10 caracteres): la cuenta lo recibe con la sanción.',
        motivo,
      );
    }
    alEmitir?.(datos);
    return undefined;
  });

  function falla(texto, control) {
    error.textContent = texto;
    error.hidden = false;
    control.focus();
  }

  return {
    elemento,
    /** @param {{titular: object|null, limites: object|null}} estado */
    preparar({ titular, limites }) {
      titulo.textContent = titular?.apodo ? `Nueva sanción para ${titular.apodo}` : 'Nueva sanción';
      rango = rangoDeLaSuspension(limites);
      pistaDuracion.textContent = rango
        ? `Entre ${duracionLegible(rango.minimo)} y ${duracionLegible(rango.maximo)}.`
        : 'En horas, días o semanas.';
    },
    alEmitir: (funcion) => {
      alEmitir = funcion;
    },
    ocupado(siONo) {
      enviar.disabled = siONo;
      enviar.textContent = siONo ? 'Emitiendo…' : 'Revisar y emitir';
    },
    mostrarError(problema) {
      error.textContent =
        problema instanceof ErrorDeSanciones
          ? problema.detalle
          : 'No pudimos contactar con el servicio de moderación. Vuelve a intentarlo.';
      error.hidden = false;
    },
    limpiar() {
      motivo.value = '';
      politica.value = '';
      causal.value = '';
      error.hidden = true;
    },
  };
}

/* -------------------------------------------------------------------------
   Utilidades
   ------------------------------------------------------------------------- */

function avisarFallo(zona, error) {
  pintarAviso(zona, {
    tono: error instanceof ErrorDeSanciones && error.estado < 500 ? 'advertencia' : 'error',
    titulo:
      error instanceof ErrorDeSanciones ? error.titulo : 'No pudimos contactar con el servicio',
    detalle:
      error instanceof ErrorDeSanciones
        ? error.detalle
        : 'Revisa tu conexión e inténtalo de nuevo.',
  });
}
