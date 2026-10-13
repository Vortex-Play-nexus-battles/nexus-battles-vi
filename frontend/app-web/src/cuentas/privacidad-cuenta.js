/**
 * «Tus datos» — portal de privacidad (HU-PRV-004, RF-PRV-004) y portabilidad
 * (HU-PRV-006, RF-PRV-006), §7.3.6.
 *
 * **Sin servicio nuevo.** Reúne lo que la plataforma guarda de la persona
 * leyendo, con su propio token, las API de cada módulo que ya le responden a
 * ella (los contratos de `contracts/openapi/`); ningún dato se calcula ni se
 * inventa aquí. Cada bloque dice qué datos hay y para qué se usan.
 *
 * **Consulta parcial explícita (CA-03).** Los módulos se piden a la vez y
 * fallan por separado: si uno no responde, su bloque lo dice («No pudimos
 * consultar…») y el resumen lo nombra; el resto se ve completo. Un listado con
 * más registros de los que se reúnen aquí se marca «parcial» y también cuenta
 * como incompleto. Nunca se calla un hueco.
 *
 * **Descarga y reporte.** «Descargar mis datos (JSON)» genera en el navegador
 * un archivo estructurado con lo reunido, la fecha de generación y la lista de
 * módulos incompletos. «Reporte para imprimir» pinta lo mismo en una hoja que
 * `privacidad.css` deja sola al imprimir: el navegador la guarda como PDF.
 * Nada de esto pasa por la dirección ni por `localStorage`: vive en memoria
 * mientras la pestaña está abierta.
 *
 * Las finalidades son una descripción funcional de para qué usa cada módulo
 * esos datos, no un texto legal: la política de tratamiento y su
 * consentimiento son RF-PRV-003 (Sprint 3 del grupo de Cuentas) y están por
 * validar con el PO.
 *
 * @module cuentas/privacidad-cuenta
 */

import { baseDeApi } from '../comun/base-api.js';
import { fetchWithHttpErrorInterceptor } from '../comun/interceptors/http-error.interceptor.js';
import { limpiarAviso, pintarAviso } from '../comun/ui/aviso.js';
import { distintivo } from '../comun/ui/distintivo.js';
import { h, vaciar } from '../comun/ui/dom.js';
import { estadoDeCarga, pintarEstado } from '../comun/ui/estado-vista.js';
import {
  creditos as formatoCreditos,
  fecha,
  fechaHora,
  nombreDelTipo,
  numero,
} from '../comun/ui/formato.js';
import {
  nombreDeModalidad,
  recuentoDeResultados,
  resultadoDePartida,
} from '../comun/ui/juego/partida.js';
import { NOMBRE_DE_SANCION } from '../comun/ui/sancion.js';
import { comoImporte, nombreDelConcepto } from './cuenta.js';
import { ESTADOS_DE_ORDEN } from './tienda-pago.js';
import { aImporte, textoDePrecio } from './tienda-adaptador.js';

/** Identificador del formato del archivo descargable. */
export const FORMATO_DE_EXPORTACION = 'nexus-battles.mis-datos';
export const VERSION_DE_EXPORTACION = 1;

/**
 * Tope técnico de páginas por listado: el portal no se queda pidiendo para
 * siempre a un módulo con miles de registros. Si un listado tiene más, el
 * bloque se marca «parcial» y lo dice. No es un valor de negocio.
 */
export const MAXIMO_DE_PAGINAS = 10;
/** Tamaño de página de los listados que lo admiten (el máximo que aceptan sus contratos). */
const TAMANO_PAGINA = 100;
/** `GET /partidas/mias` acepta hasta 50 (salas-partidas.yaml 1.9.0). */
const TAMANO_PAGINA_PARTIDAS = 50;

export const ESTADOS_DEL_BLOQUE = Object.freeze({
  COMPLETO: 'completo',
  PARCIAL: 'parcial',
  NO_DISPONIBLE: 'no-disponible',
  SIN_CONSULTA: 'sin-consulta',
});

/** Un módulo que no respondió (red caída, 4xx o 5xx). */
export class FuenteNoDisponible extends Error {
  /** @param {number} estado código HTTP, o 0 sin respuesta */
  constructor(estado) {
    super(`HTTP ${estado}`);
    this.name = 'FuenteNoDisponible';
    this.estado = estado;
  }
}

/**
 * GET de una API con el token de la sesión. Sin respuesta, un error o un
 * cuerpo ilegible: {@link FuenteNoDisponible}.
 *
 * @param {string} ruta empieza por `/api/v1`
 * @param {Function} fetchImpl
 */
async function leerJson(ruta, fetchImpl) {
  let respuesta;
  try {
    respuesta = await fetchImpl(`${baseDeApi()}${ruta}`, {
      headers: { Accept: 'application/json' },
      cache: 'no-store',
    });
  } catch {
    throw new FuenteNoDisponible(0);
  }
  if (!respuesta.ok) {
    throw new FuenteNoDisponible(respuesta.status);
  }
  try {
    return await respuesta.json();
  } catch {
    throw new FuenteNoDisponible(respuesta.status);
  }
}

/**
 * Un listado paginado, página a página hasta {@link MAXIMO_DE_PAGINAS}.
 *
 * @param {{ruta: string, fetchImpl: Function, parametros: (pagina: number) => Record<string, string>,
 *   extraer: (cuerpo: any) => {lista: unknown[], paginas: number}}} opciones
 * @returns {Promise<{elementos: unknown[], completo: boolean}>}
 */
export async function leerPaginas({ ruta, fetchImpl, parametros, extraer }) {
  const elementos = [];
  let pagina = 0;
  let paginas = 1;
  while (pagina < paginas && pagina < MAXIMO_DE_PAGINAS) {
    const consulta = new URLSearchParams(parametros(pagina));
    const { lista, paginas: total } = extraer(await leerJson(`${ruta}?${consulta}`, fetchImpl));
    elementos.push(...lista);
    paginas = Number.isInteger(total) && total >= 0 ? total : 1;
    pagina += 1;
  }
  return { elementos, completo: pagina >= paginas };
}

/** `Page` de Spring Data: `content` y `totalPages`. */
const pageDeSpring = (cuerpo) => ({
  lista: Array.isArray(cuerpo?.content) ? cuerpo.content : [],
  paginas: cuerpo?.totalPages,
});

const lista = (valor) => (Array.isArray(valor) ? valor : []);
const siNo = (valor) => (valor ? 'Sí' : 'No');
const textoOGuion = (valor) =>
  valor === null || valor === undefined || valor === '' ? '—' : String(valor);

/** Un importe con su moneda; en créditos, como créditos. */
function importe(monto, moneda) {
  return textoDePrecio(aImporte(monto), moneda) ?? '—';
}

/**
 * Cómo se pagó un pedido: con créditos, o con una tarjeta de la que solo se
 * guardan la marca y los cuatro últimos dígitos (`Orden.medioDePago`).
 *
 * @param {object} orden
 * @returns {string}
 */
function formaDePago(orden) {
  if (orden?.formaDePago === 'CREDITOS') {
    return 'Créditos del juego';
  }
  const tarjeta = orden?.medioDePago;
  if (!tarjeta?.ultimos4) {
    return 'Tarjeta';
  }
  return [
    tarjeta.marca ? `Tarjeta ${tarjeta.marca}` : 'Tarjeta',
    `terminada en ${tarjeta.ultimos4}`,
  ].join(' ');
}

/** Textos de las subastas, los mismos que usa la vista de Subastas. */
const ESTADO_DE_SUBASTA = Object.freeze({
  ACTIVA: 'En curso',
  ADJUDICADA: 'Adjudicada',
  SIN_ADJUDICACION: 'Terminó sin pujas',
  CANCELADA: 'Cancelada',
});
const ESTADO_DE_PUJA = Object.freeze({
  GANANDO: 'Vas ganando',
  SUPERADA: 'Te superaron',
  GANADA: 'La ganaste',
  PERDIDA: 'Se la llevó otro jugador',
  CERRADA: 'Cancelada',
  AUTOMATICA: 'Automática preparada',
});
const RESULTADO_DE_PAGO = Object.freeze({
  APROBADO: 'Aprobado',
  RECHAZADO: 'Rechazado',
  INDETERMINADO: 'Sin confirmar',
});
const ENTREGA_DE_COFRE = Object.freeze({
  PENDIENTE: 'Pendiente',
  ENTREGADO: 'Entregado',
  SIN_CONTENIDO: 'Sin contenido',
});

/**
 * @typedef {{titulo: string, columnas: string[], filas: string[][]}} TablaDeDetalle
 * @typedef {{
 *   id: string, titulo: string, modulo: string, finalidad: string,
 *   leer?: (contexto: {sesion: object, fetchImpl: Function}) => Promise<any>,
 *   resumen?: (datos: any, contexto: {sesion: object}) => Array<{etiqueta: string, valor: string}>,
 *   detalle?: (datos: any) => TablaDeDetalle[],
 *   completo?: (datos: any) => boolean,
 *   sinConsulta?: string,
 * }} Fuente
 */

/**
 * Los módulos que guardan datos de la persona, en el orden en que se ven.
 *
 * @type {ReadonlyArray<Fuente>}
 */
export const FUENTES = Object.freeze([
  {
    id: 'perfil',
    titulo: 'Tu cuenta y tu perfil',
    modulo: 'Cuentas',
    finalidad:
      'Identificarte y mostrar tu perfil. El correo es con lo que entras y a donde llegan los códigos y avisos de tu cuenta; el apodo es el nombre con el que te ven en salas, chat y comentarios; nombres, apellidos, foto y preferencias forman tu perfil.',
    leer: ({ sesion, fetchImpl }) =>
      leerJson(`/api/v1/perfiles/${encodeURIComponent(sesion.uid)}`, fetchImpl),
    resumen: (perfil, { sesion }) => [
      { etiqueta: 'Apodo', valor: textoOGuion(perfil?.apodo) },
      { etiqueta: 'Correo', valor: textoOGuion(perfil?.email) },
      { etiqueta: 'Nombres', valor: textoOGuion(perfil?.nombres) },
      { etiqueta: 'Apellidos', valor: textoOGuion(perfil?.apellidos) },
      { etiqueta: 'Foto de perfil', valor: perfil?.avatar ? 'Sí' : 'No' },
      { etiqueta: 'Preferencias', valor: textoOGuion(perfil?.preferencias) },
      { etiqueta: 'Rol', valor: textoOGuion(sesion?.rol) },
    ],
  },
  {
    id: 'preguntas',
    titulo: 'Preguntas de seguridad',
    modulo: 'Cuentas',
    finalidad:
      'Recuperar tu cuenta si olvidas la contraseña, junto con el código que llega a tu correo. Las respuestas se guardan cifradas y no se muestran, ni siquiera aquí.',
    leer: ({ fetchImpl }) => leerJson('/api/v1/auth/preguntas-seguridad', fetchImpl),
    resumen: (datos) => [
      { etiqueta: 'Preguntas configuradas', valor: numero(lista(datos?.preguntas).length) },
    ],
    detalle: (datos) => [
      {
        titulo: 'Tus preguntas',
        columnas: ['Pregunta'],
        filas: lista(datos?.preguntas).map((pregunta) => [textoOGuion(pregunta?.texto)]),
      },
    ],
  },
  {
    id: 'creditos',
    titulo: 'Créditos del juego',
    modulo: 'Finanzas',
    finalidad:
      'Llevar tu saldo de créditos: lo que apuestas, ganas, compras y pagas en inscripciones. Es un registro financiero: se conserva aunque cierres tu cuenta.',
    leer: async ({ sesion, fetchImpl }) => {
      const uid = encodeURIComponent(sesion.uid);
      const [saldo, movimientos] = await Promise.all([
        leerJson(`/api/v1/creditos/${uid}/saldo`, fetchImpl),
        leerPaginas({
          ruta: `/api/v1/creditos/${uid}/movimientos`,
          fetchImpl,
          parametros: (pagina) => ({ page: String(pagina), size: String(TAMANO_PAGINA) }),
          extraer: pageDeSpring,
        }),
      ]);
      return { saldo, movimientos: movimientos.elementos, completo: movimientos.completo };
    },
    completo: (datos) => datos.completo,
    resumen: (datos) => [
      { etiqueta: 'Disponibles', valor: formatoCreditos(datos.saldo?.saldoDisponible) },
      { etiqueta: 'Apartados en apuestas', valor: formatoCreditos(datos.saldo?.saldoReservado) },
      { etiqueta: 'Movimientos', valor: numero(datos.movimientos.length) },
    ],
    detalle: (datos) => [
      {
        titulo: 'Movimientos de créditos',
        columnas: ['Concepto', 'Importe', 'Estado', 'Cuándo'],
        filas: datos.movimientos.map((movimiento) => [
          nombreDelConcepto(movimiento?.concepto),
          comoImporte(movimiento ?? {}).texto,
          textoOGuion(movimiento?.estado),
          fechaHora(movimiento?.creado),
        ]),
      },
    ],
  },
  {
    id: 'pagos',
    titulo: 'Pagos con dinero real',
    modulo: 'Finanzas',
    finalidad:
      'Dejar constancia de cada pago en dinero real (registro de transacciones). Es un registro financiero: se conserva aunque cierres tu cuenta.',
    leer: ({ fetchImpl }) =>
      leerPaginas({
        ruta: '/api/v1/transacciones/mi-historial',
        fetchImpl,
        parametros: (pagina) => ({ page: String(pagina), size: String(TAMANO_PAGINA) }),
        extraer: pageDeSpring,
      }),
    completo: (datos) => datos.completo,
    resumen: (datos) => [{ etiqueta: 'Pagos registrados', valor: numero(datos.elementos.length) }],
    detalle: (datos) => [
      {
        titulo: 'Pagos',
        columnas: ['Concepto', 'Importe', 'Resultado', 'Cuándo'],
        filas: datos.elementos.map((pago) => [
          textoOGuion(pago?.concepto),
          importe(pago?.monto, pago?.moneda),
          RESULTADO_DE_PAGO[pago?.resultado] ?? textoOGuion(pago?.resultado),
          fechaHora(pago?.creado),
        ]),
      },
    ],
  },
  {
    id: 'compras',
    titulo: 'Compras en la tienda',
    modulo: 'Tienda',
    finalidad:
      'Entregarte lo que compras y llevar tus pedidos. De un pago con tarjeta solo queda su marca y sus cuatro últimos dígitos.',
    leer: ({ fetchImpl }) => leerJson('/api/v1/ordenes', fetchImpl).then(lista),
    resumen: (ordenes) => [{ etiqueta: 'Pedidos', valor: numero(ordenes.length) }],
    detalle: (ordenes) => [
      {
        titulo: 'Pedidos',
        columnas: ['Cuándo', 'Productos', 'Total', 'Forma de pago', 'Estado'],
        filas: ordenes.map((orden) => [
          fechaHora(orden?.creadaEn),
          lista(orden?.lineas)
            .map((linea) => `${linea?.nombre ?? 'Producto'} × ${linea?.cantidad ?? 1}`)
            .join(', ') || '—',
          importe(orden?.total, orden?.moneda),
          formaDePago(orden),
          ESTADOS_DE_ORDEN[orden?.estado] ?? textoOGuion(orden?.estado),
        ]),
      },
    ],
  },
  {
    id: 'deseos',
    titulo: 'Lista de deseos',
    modulo: 'Tienda',
    finalidad: 'Recordar los productos que marcaste para comprar después.',
    leer: ({ fetchImpl }) => leerJson('/api/v1/lista-deseos', fetchImpl).then(lista),
    resumen: (deseos) => [{ etiqueta: 'Productos', valor: numero(deseos.length) }],
    detalle: (deseos) => [
      {
        titulo: 'Productos en tu lista',
        columnas: ['Producto', 'Añadido'],
        filas: deseos.map((deseo) => [textoOGuion(deseo?.nombre), fecha(deseo?.agregadoEn)]),
      },
    ],
  },
  {
    id: 'inventario',
    titulo: 'Tu inventario',
    modulo: 'Inventario',
    finalidad:
      'Guardar los héroes y objetos que te pertenecen y su estado: nivel, experiencia y si están en una subasta o una misión.',
    leer: ({ fetchImpl }) =>
      leerPaginas({
        ruta: '/api/v1/inventario/elementos',
        fetchImpl,
        parametros: (pagina) => ({ pagina: String(pagina) }),
        extraer: (cuerpo) => ({ lista: lista(cuerpo?.elementos), paginas: cuerpo?.totalPaginas }),
      }),
    completo: (datos) => datos.completo,
    resumen: (datos) => [{ etiqueta: 'Elementos', valor: numero(datos.elementos.length) }],
    detalle: (datos) => [
      {
        titulo: 'Elementos',
        columnas: ['Nombre', 'Tipo', 'Nivel', 'Disponible'],
        filas: datos.elementos.map((elemento) => [
          textoOGuion(elemento?.nombrePropio),
          nombreDelTipo(elemento?.tipo),
          textoOGuion(elemento?.nivel),
          siNo(elemento?.disponible),
        ]),
      },
    ],
  },
  {
    id: 'cofres',
    titulo: 'Cofres recibidos',
    modulo: 'Finanzas',
    finalidad: 'Registrar los cofres que ganaste y lo que contenían.',
    leer: ({ fetchImpl }) =>
      leerPaginas({
        ruta: '/api/v1/cofres/mios',
        fetchImpl,
        parametros: (pagina) => ({ page: String(pagina), size: String(TAMANO_PAGINA) }),
        extraer: pageDeSpring,
      }),
    completo: (datos) => datos.completo,
    resumen: (datos) => [{ etiqueta: 'Cofres', valor: numero(datos.elementos.length) }],
    detalle: (datos) => [
      {
        titulo: 'Cofres',
        columnas: ['Cofre', 'Entregado', 'Entrega'],
        filas: datos.elementos.map((cofre) => [
          textoOGuion(cofre?.contenido),
          fechaHora(cofre?.entregadoEn),
          ENTREGA_DE_COFRE[cofre?.estadoEntrega] ?? textoOGuion(cofre?.estadoEntrega),
        ]),
      },
    ],
  },
  {
    id: 'batallas',
    titulo: 'Tus batallas',
    modulo: 'Salas y partidas',
    finalidad: 'Llevar tu historial de partidas y sus resultados: tus estadísticas de juego.',
    leer: ({ fetchImpl }) =>
      leerPaginas({
        ruta: '/api/v1/partidas/mias',
        fetchImpl,
        parametros: (pagina) => ({
          pagina: String(pagina),
          tamano: String(TAMANO_PAGINA_PARTIDAS),
        }),
        extraer: (cuerpo) => ({ lista: lista(cuerpo?.contenido), paginas: cuerpo?.totalPaginas }),
      }),
    completo: (datos) => datos.completo,
    resumen: (datos) => {
      const { victorias, derrotas, empates } = recuentoDeResultados(datos.elementos);
      return [
        { etiqueta: 'Partidas', valor: numero(datos.elementos.length) },
        { etiqueta: 'Victorias', valor: numero(victorias) },
        { etiqueta: 'Derrotas', valor: numero(derrotas) },
        { etiqueta: 'Empates', valor: numero(empates) },
      ];
    },
    detalle: (datos) => [
      {
        titulo: 'Partidas',
        columnas: ['Resultado', 'Héroe', 'Modalidad', 'Cuándo'],
        filas: datos.elementos.map((partida) => [
          resultadoDePartida(partida).texto,
          textoOGuion(partida?.heroe),
          nombreDeModalidad(partida?.modalidad),
          fechaHora(partida?.finalizadaEn ?? partida?.iniciadaEn),
        ]),
      },
    ],
  },
  {
    id: 'misiones',
    titulo: 'Tus misiones',
    modulo: 'Misiones',
    finalidad: 'Llevar tu progreso en misiones, tus mejores tiempos y las épicas que ganaste.',
    leer: ({ fetchImpl }) => leerJson('/api/v1/misiones/historial', fetchImpl),
    resumen: (historial) => {
      const categorias = lista(historial?.porCategoria);
      const suma = (campo) =>
        categorias.reduce((total, categoria) => total + (Number(categoria?.[campo]) || 0), 0);
      return [
        { etiqueta: 'Cumplidas', valor: numero(suma('completadas')) },
        { etiqueta: 'Fallidas', valor: numero(suma('fallidas')) },
        { etiqueta: 'Épicas obtenidas', valor: numero(lista(historial?.epicas).length) },
      ];
    },
    detalle: (historial) => [
      {
        titulo: 'Misiones terminadas',
        columnas: ['Misión', 'Resultado', 'Terminada'],
        filas: lista(historial?.completadas).map((mision) => [
          textoOGuion(mision?.nombre),
          mision?.resultado === 'EXITO' ? 'Éxito' : 'Fallo',
          fechaHora(mision?.terminadaEn),
        ]),
      },
    ],
  },
  {
    id: 'subastas',
    titulo: 'Tus subastas y pujas',
    modulo: 'Subastas',
    finalidad:
      'Publicar tus subastas, registrar tus pujas y entregarte lo que ganas. Son operaciones económicas: su historial se conserva.',
    leer: async ({ fetchImpl }) => {
      const [publicadas, pujas] = await Promise.all([
        leerJson('/api/v1/mis-subastas/publicadas', fetchImpl).then(lista),
        leerJson('/api/v1/mis-pujas', fetchImpl).then(lista),
      ]);
      return { publicadas, pujas };
    },
    resumen: (datos) => [
      { etiqueta: 'Subastas publicadas', valor: numero(datos.publicadas.length) },
      { etiqueta: 'Subastas en las que pujaste', valor: numero(datos.pujas.length) },
    ],
    detalle: (datos) => [
      {
        titulo: 'Subastas publicadas',
        columnas: ['Producto', 'Estado', 'Oferta vigente', 'Termina'],
        filas: datos.publicadas.map((subasta) => [
          textoOGuion(subasta?.nombreProducto),
          ESTADO_DE_SUBASTA[subasta?.estado] ?? textoOGuion(subasta?.estado),
          formatoCreditos(subasta?.ofertaVigente),
          fechaHora(subasta?.fechaFin),
        ]),
      },
      {
        titulo: 'Pujas',
        columnas: ['Producto', 'Tu participación', 'Tu mejor puja', 'Termina'],
        filas: datos.pujas.map((puja) => [
          textoOGuion(puja?.nombreProducto),
          ESTADO_DE_PUJA[puja?.estado] ?? textoOGuion(puja?.estado),
          formatoCreditos(puja?.tuMejorPuja),
          fechaHora(puja?.fechaFin),
        ]),
      },
    ],
  },
  {
    id: 'sanciones',
    titulo: 'Sanciones y advertencias',
    modulo: 'Moderación',
    finalidad:
      'Registrar las advertencias, suspensiones o baneos aplicados a tu cuenta, con su motivo y su vigencia, para que puedas consultarlos y apelarlos.',
    leer: ({ sesion, fetchImpl }) =>
      leerJson(`/api/v1/sanciones/usuarios/${encodeURIComponent(sesion.uid)}`, fetchImpl).then(
        lista,
      ),
    resumen: (sanciones) => [
      { etiqueta: 'Registradas', valor: numero(sanciones.length) },
      {
        etiqueta: 'Vigentes',
        valor: numero(sanciones.filter((sancion) => sancion?.vigente === true).length),
      },
    ],
    detalle: (sanciones) => [
      {
        titulo: 'Sanciones',
        columnas: ['Tipo', 'Motivo', 'Desde', 'Hasta', 'Vigente'],
        filas: sanciones.map((sancion) => [
          NOMBRE_DE_SANCION[sancion?.tipo] ?? textoOGuion(sancion?.tipo),
          textoOGuion(sancion?.motivo),
          fechaHora(sancion?.emitidaEn),
          sancion?.vigenteHasta ? fechaHora(sancion.vigenteHasta) : '—',
          siNo(sancion?.vigente === true),
        ]),
      },
    ],
  },
  {
    id: 'notificaciones',
    titulo: 'Notificaciones',
    modulo: 'Notificaciones',
    finalidad: 'Avisarte de lo que pasa en tu cuenta y en el juego (tu bandeja de avisos).',
    leer: ({ sesion, fetchImpl }) =>
      leerJson(`/api/v1/users/${encodeURIComponent(sesion.uid)}/notifications`, fetchImpl),
    resumen: (bandeja) => [
      { etiqueta: 'Avisos', valor: numero(lista(bandeja?.avisos).length) },
      { etiqueta: 'Sin leer', valor: numero(Number(bandeja?.noLeidas) || 0) },
    ],
    detalle: (bandeja) => [
      {
        titulo: 'Avisos',
        columnas: ['Aviso', 'Cuándo', 'Leído'],
        filas: lista(bandeja?.avisos).map((aviso) => [
          textoOGuion(aviso?.titulo),
          fechaHora(aviso?.creadaEn),
          siNo(aviso?.leida === true),
        ]),
      },
    ],
  },
  {
    id: 'mensajes',
    titulo: 'Mensajes privados',
    modulo: 'Salas y partidas',
    finalidad:
      'Guardar tus conversaciones privadas con otros jugadores. Aquí se resume cada conversación; su contenido lo ves en Mensajes.',
    leer: ({ fetchImpl }) =>
      leerJson('/api/v1/mensajes-directos/conversaciones', fetchImpl).then(lista),
    resumen: (conversaciones) => [
      { etiqueta: 'Conversaciones', valor: numero(conversaciones.length) },
    ],
    detalle: (conversaciones) => [
      {
        titulo: 'Conversaciones',
        columnas: ['Con', 'Último mensaje', 'Sin leer'],
        filas: conversaciones.map((conversacion) => [
          textoOGuion(conversacion?.apodoOtro),
          fechaHora(conversacion?.ultimoMensaje?.fecha),
          numero(Number(conversacion?.noLeidos) || 0),
        ]),
      },
    ],
  },
  {
    id: 'asistente',
    titulo: 'Conversación con el asistente',
    modulo: 'Asistente',
    finalidad: 'Responder tus preguntas sobre el juego dentro de la misma conversación.',
    leer: ({ fetchImpl }) => leerJson('/api/v1/chat/historial', fetchImpl).then(lista),
    resumen: (mensajes) => [{ etiqueta: 'Mensajes', valor: numero(mensajes.length) }],
    detalle: (mensajes) => [
      {
        titulo: 'Mensajes',
        columnas: ['De', 'Mensaje', 'Cuándo'],
        filas: mensajes.map((mensaje) => [
          mensaje?.remitente === 'BOT' ? 'Asistente' : 'Tú',
          textoOGuion(mensaje?.contenido),
          fechaHora(mensaje?.fechaEnvio),
        ]),
      },
    ],
  },
  {
    id: 'comentarios',
    titulo: 'Tus comentarios',
    modulo: 'Comentarios',
    finalidad: 'Publicar tus opiniones y calificaciones de los productos.',
    sinConsulta:
      'El servicio de comentarios todavía no ofrece una consulta de los comentarios de una persona, así que no podemos reunirlos aquí. Los que publicaste se ven en la página de cada producto.',
  },
]);

/**
 * @typedef {{
 *   id: string, titulo: string, modulo: string, finalidad: string,
 *   estado: string, motivo: string|null, datos: any,
 *   resumen: Array<{etiqueta: string, valor: string}>, detalle: TablaDeDetalle[],
 * }} Bloque
 * @typedef {{generadoEn: string, cuenta: {uid: string|null, apodo: string|null, rol: string|null},
 *   bloques: Bloque[]}} Consolidado
 */

/**
 * Un bloque ya leído: completo, parcial o no disponible, sin que el fallo de
 * uno afecte a los demás.
 *
 * @param {Fuente} fuente
 * @param {{sesion: object, fetchImpl: Function}} contexto
 * @returns {Promise<Bloque>}
 */
async function leerBloque(fuente, contexto) {
  const base = {
    id: fuente.id,
    titulo: fuente.titulo,
    modulo: fuente.modulo,
    finalidad: fuente.finalidad,
    datos: null,
    resumen: [],
    detalle: [],
  };
  if (fuente.sinConsulta || !fuente.leer) {
    return { ...base, estado: ESTADOS_DEL_BLOQUE.SIN_CONSULTA, motivo: fuente.sinConsulta ?? null };
  }
  let datos;
  try {
    datos = await fuente.leer(contexto);
  } catch {
    return {
      ...base,
      estado: ESTADOS_DEL_BLOQUE.NO_DISPONIBLE,
      motivo: `No pudimos consultar «${fuente.titulo}» ahora mismo: el módulo de ${fuente.modulo} no respondió.`,
    };
  }
  const completo = fuente.completo ? fuente.completo(datos) !== false : true;
  return {
    ...base,
    estado: completo ? ESTADOS_DEL_BLOQUE.COMPLETO : ESTADOS_DEL_BLOQUE.PARCIAL,
    motivo: completo
      ? null
      : 'Tienes más registros de los que este portal reúne de una vez; se incluyeron los más recientes.',
    datos,
    resumen: fuente.resumen ? fuente.resumen(datos, contexto) : [],
    detalle: fuente.detalle ? fuente.detalle(datos) : [],
  };
}

/**
 * Reúne los datos de todos los módulos a la vez. Nunca falla entero: cada
 * módulo que no responde queda como bloque «no disponible».
 *
 * @param {{sesion: {uid: string, apodo?: string, rol?: string}, fetchImpl?: Function,
 *   ahora?: () => Date, fuentes?: ReadonlyArray<Fuente>}} opciones
 * @returns {Promise<Consolidado>}
 */
export async function consolidarDatos({
  sesion,
  fetchImpl = fetchWithHttpErrorInterceptor,
  ahora = () => new Date(),
  fuentes = FUENTES,
}) {
  const contexto = { sesion, fetchImpl };
  const bloques = await Promise.all(fuentes.map((fuente) => leerBloque(fuente, contexto)));
  return {
    generadoEn: ahora().toISOString(),
    cuenta: { uid: sesion?.uid ?? null, apodo: sesion?.apodo || null, rol: sesion?.rol ?? null },
    bloques,
  };
}

/**
 * Los bloques a los que les falta algo, con el porqué.
 *
 * @param {Consolidado} consolidado
 * @returns {Bloque[]}
 */
export function bloquesIncompletos(consolidado) {
  return consolidado.bloques.filter((bloque) => bloque.estado !== ESTADOS_DEL_BLOQUE.COMPLETO);
}

/**
 * El archivo estructurado (RF-PRV-006): los datos tal como los dio cada
 * módulo, con su finalidad, la fecha de generación y la lista de módulos
 * incompletos.
 *
 * @param {Consolidado} consolidado
 */
export function exportacionJson(consolidado) {
  const incompletos = bloquesIncompletos(consolidado);
  return {
    formato: FORMATO_DE_EXPORTACION,
    version: VERSION_DE_EXPORTACION,
    generadoEn: consolidado.generadoEn,
    cuenta: consolidado.cuenta,
    completo: incompletos.length === 0,
    modulosIncompletos: incompletos.map((bloque) => ({
      modulo: bloque.id,
      titulo: bloque.titulo,
      estado: bloque.estado,
      motivo: bloque.motivo,
    })),
    modulos: Object.fromEntries(
      consolidado.bloques.map((bloque) => [
        bloque.id,
        {
          titulo: bloque.titulo,
          servicio: bloque.modulo,
          finalidad: bloque.finalidad,
          estado: bloque.estado,
          datos: bloque.datos,
        },
      ]),
    ),
  };
}

/**
 * Nombre del archivo: sin datos de la persona, solo la fecha.
 *
 * @param {Consolidado} consolidado
 */
export function nombreDelArchivo(consolidado) {
  return `mis-datos-nexus-battles-${String(consolidado.generadoEn).slice(0, 10)}.json`;
}

/**
 * Descarga el JSON desde el navegador, sin pasar por ningún servidor.
 *
 * @param {Consolidado} consolidado
 * @param {{documento?: Document, urlImpl?: {createObjectURL: Function, revokeObjectURL: Function}}} [opciones]
 * @returns {string} el nombre del archivo
 */
export function descargarJson(consolidado, { documento = document, urlImpl = URL } = {}) {
  const contenido = JSON.stringify(exportacionJson(consolidado), null, 2);
  const archivo = new Blob([contenido], { type: 'application/json' });
  const direccion = urlImpl.createObjectURL(archivo);
  const nombre = nombreDelArchivo(consolidado);
  const enlace = h('a', {
    atributos: { href: direccion, download: nombre },
    clase: 'solo-lectores',
  });
  documento.body.append(enlace);
  enlace.click();
  enlace.remove();
  // En la vuelta siguiente y no en esta: algunos navegadores leen el archivo
  // después del clic, y liberarlo antes cancelaría la descarga (pujas.js y el
  // panel del asistente hacen lo mismo).
  setTimeout(() => urlImpl.revokeObjectURL(direccion), 0);
  return nombre;
}

/** El distintivo de cada estado: el texto lo dice, el color lo refuerza. */
const DISTINTIVO_DEL_BLOQUE = Object.freeze({
  [ESTADOS_DEL_BLOQUE.COMPLETO]: ['Completo', 'completada'],
  [ESTADOS_DEL_BLOQUE.PARCIAL]: ['Parcial', 'aviso'],
  [ESTADOS_DEL_BLOQUE.NO_DISPONIBLE]: ['No disponible', 'suspendido'],
  [ESTADOS_DEL_BLOQUE.SIN_CONSULTA]: ['Sin consulta', 'aviso'],
});

/** @param {Array<{etiqueta: string, valor: string}>} resumen */
function datosDelResumen(resumen) {
  return h('dl', {
    clase: 'tarjeta__datos',
    hijos: resumen.map(({ etiqueta, valor }) =>
      h('div', {
        clase: 'tarjeta__dato',
        hijos: [h('dt', { texto: etiqueta }), h('dd', { texto: valor })],
      }),
    ),
  });
}

/** @param {TablaDeDetalle} tabla */
function tablaDeDetalle(tabla) {
  if (tabla.filas.length === 0) {
    return h('p', { clase: 't-meta', texto: `${tabla.titulo}: ninguno.` });
  }
  return h('div', {
    clase: 'tabla-envoltorio',
    atributos: { tabindex: '0', role: 'region', 'aria-label': tabla.titulo },
    hijos: [
      h('table', {
        clase: 'tabla tabla--datos',
        hijos: [
          h('caption', { clase: 'solo-lectores', texto: tabla.titulo }),
          h('thead', {
            hijos: [
              h('tr', {
                hijos: tabla.columnas.map((columna) =>
                  h('th', { texto: columna, atributos: { scope: 'col' } }),
                ),
              }),
            ],
          }),
          h('tbody', {
            hijos: tabla.filas.map((fila) =>
              h('tr', { hijos: fila.map((celda) => h('td', { texto: celda })) }),
            ),
          }),
        ],
      }),
    ],
  });
}

/**
 * Un bloque del portal en pantalla.
 *
 * @param {Bloque} bloque
 * @returns {HTMLElement}
 */
function bloqueEnPantalla(bloque) {
  const [texto, variante] = DISTINTIVO_DEL_BLOQUE[bloque.estado];
  const hijos = [
    h('div', {
      clase: 'encabezado-seccion',
      hijos: [
        h('div', {
          clase: 'pila pila--ajustada',
          hijos: [
            h('h3', { clase: 't-subtitulo', texto: bloque.titulo }),
            h('p', { clase: 't-meta', texto: `Módulo: ${bloque.modulo}` }),
          ],
        }),
        distintivo(texto, variante),
      ],
    }),
    h('p', { clase: 't-cuerpo', texto: `Para qué se usa: ${bloque.finalidad}` }),
  ];
  if (bloque.motivo) {
    hijos.push(
      h('p', {
        clase: 't-meta',
        datos: { zona: 'motivo-bloque' },
        texto:
          bloque.estado === ESTADOS_DEL_BLOQUE.NO_DISPONIBLE
            ? `${bloque.motivo} El resto de tus datos está completo.`
            : bloque.motivo,
      }),
    );
  }
  if (bloque.resumen.length > 0) {
    hijos.push(datosDelResumen(bloque.resumen));
  }
  const conFilas = bloque.detalle.filter((tabla) => tabla.filas.length > 0);
  if (conFilas.length > 0) {
    const cuantas = conFilas.reduce((total, tabla) => total + tabla.filas.length, 0);
    hijos.push(
      h('details', {
        clase: 'privacidad__detalle',
        hijos: [
          h('summary', { texto: `Ver el detalle (${numero(cuantas)})` }),
          ...bloque.detalle.map(tablaDeDetalle),
        ],
      }),
    );
  }
  return h('article', {
    clase: 'tarjeta tarjeta--hundida pila',
    datos: { fuente: bloque.id, estado: bloque.estado },
    hijos,
  });
}

/**
 * La frase de arriba: cuántos módulos y cuáles faltan (CA-03).
 *
 * @param {Consolidado} consolidado
 * @returns {string}
 */
export function fraseDelResumen(consolidado) {
  const con = (estado) =>
    consolidado.bloques
      .filter((bloque) => bloque.estado === estado)
      .map((bloque) => `«${bloque.titulo}»`)
      .join(', ');
  const consultables = consolidado.bloques.filter(
    (bloque) => bloque.estado !== ESTADOS_DEL_BLOQUE.SIN_CONSULTA,
  ).length;
  const partes = [`Reunimos tus datos de ${numero(consultables)} módulos.`];
  const fallidos = con(ESTADOS_DEL_BLOQUE.NO_DISPONIBLE);
  if (fallidos) {
    partes.push(`Consulta parcial: no pudimos consultar ${fallidos}; el resto está completo.`);
  }
  const parciales = con(ESTADOS_DEL_BLOQUE.PARCIAL);
  if (parciales) {
    partes.push(`De ${parciales} se incluyeron los registros más recientes.`);
  }
  const sinConsulta = con(ESTADOS_DEL_BLOQUE.SIN_CONSULTA);
  if (sinConsulta) {
    partes.push(`${sinConsulta} todavía no se puede reunir aquí.`);
  }
  partes.push(`Generado el ${fechaHora(consolidado.generadoEn)}.`);
  return partes.join(' ');
}

/**
 * Pinta el portal: un bloque por módulo.
 *
 * @param {HTMLElement} zona `[data-zona="bloques-portal"]`
 * @param {Consolidado} consolidado
 */
export function pintarPortal(zona, consolidado) {
  vaciar(zona).append(...consolidado.bloques.map(bloqueEnPantalla));
}

/**
 * La hoja del reporte imprimible (RF-PRV-006, «reporte completo en PDF»):
 * todo lo reunido, con todas sus filas, para que el navegador lo imprima o
 * lo guarde como PDF.
 *
 * @param {HTMLElement} zona `[data-zona="reporte-privacidad"]`
 * @param {Consolidado} consolidado
 */
export function pintarReporte(zona, consolidado) {
  const incompletos = bloquesIncompletos(consolidado);
  const cabecera = [
    h('h1', { texto: 'Tus datos en The Nexus Battles VI' }),
    h('p', {
      clase: 't-meta',
      texto: `Cuenta: ${consolidado.cuenta.apodo ?? '—'} · Generado el ${fechaHora(consolidado.generadoEn)}`,
    }),
  ];
  if (incompletos.length > 0) {
    cabecera.push(
      h('p', {
        clase: 'reporte-privacidad__nota',
        texto: `Este reporte está incompleto: ${incompletos
          .map(
            (bloque) =>
              `${bloque.titulo} (${DISTINTIVO_DEL_BLOQUE[bloque.estado][0].toLowerCase()})`,
          )
          .join(', ')}.`,
      }),
    );
  }
  const secciones = consolidado.bloques.map((bloque) =>
    h('section', {
      clase: 'reporte-privacidad__bloque',
      datos: { fuente: bloque.id },
      hijos: [
        h('h2', { texto: bloque.titulo }),
        h('p', {
          clase: 't-meta',
          texto: `Módulo: ${bloque.modulo} · Para qué se usa: ${bloque.finalidad}`,
        }),
        bloque.motivo ? h('p', { clase: 'reporte-privacidad__nota', texto: bloque.motivo }) : null,
        bloque.resumen.length > 0 ? datosDelResumen(bloque.resumen) : null,
        ...bloque.detalle.map(tablaDeDetalle),
      ],
    }),
  );
  vaciar(zona).append(...cabecera, ...secciones);
}

/**
 * Monta «Tus datos» sobre `[data-zona="portal-privacidad"]`. No pide nada
 * hasta que se llama a `cargar()` (al abrir la pestaña «Privacidad»), y solo
 * la primera vez: volver a la pestaña no vuelve a consultar todo.
 *
 * @param {ParentNode} raiz
 * @param {{sesion: object, fetchImpl?: Function, ahora?: () => Date, fuentes?: ReadonlyArray<Fuente>,
 *   imprimir?: () => void, urlImpl?: {createObjectURL: Function, revokeObjectURL: Function}}} opciones
 * @returns {{cargar: (forzar?: boolean) => Promise<Consolidado|null>, actual: () => Consolidado|null}|null}
 */
export function montarPrivacidad(
  raiz,
  {
    sesion,
    fetchImpl = fetchWithHttpErrorInterceptor,
    ahora = () => new Date(),
    fuentes = FUENTES,
    imprimir = () => window.print(),
    urlImpl = URL,
  },
) {
  const seccion = raiz.querySelector('[data-zona="portal-privacidad"]');
  if (!seccion) {
    return null;
  }
  const zonaBloques = seccion.querySelector('[data-zona="bloques-portal"]');
  const frase = seccion.querySelector('[data-zona="resumen-portal"]');
  const botonDescargar = seccion.querySelector('[data-accion="descargar-datos"]');
  const botonImprimir = seccion.querySelector('[data-accion="imprimir-datos"]');
  const reporte = raiz.querySelector('[data-zona="reporte-privacidad"]');
  const documento = seccion.ownerDocument;

  /** @type {Consolidado|null} */
  let consolidado = null;
  /** @type {Promise<Consolidado|null>|null} */
  let enCurso = null;
  /** El aviso de consulta parcial, encima de los bloques. */
  const zonaAviso = h('div', { datos: { zona: 'aviso-portal' }, atributos: { hidden: true } });
  zonaBloques.before(zonaAviso);

  async function consultar() {
    botonDescargar.disabled = true;
    botonImprimir.disabled = true;
    limpiarAviso(zonaAviso);
    frase.textContent = 'Reuniendo tus datos de cada módulo…';
    pintarEstado(zonaBloques, estadoDeCarga({ filas: 4, etiqueta: 'Reuniendo tus datos…' }));
    consolidado = await consolidarDatos({ sesion, fetchImpl, ahora, fuentes });
    pintarPortal(zonaBloques, consolidado);
    frase.textContent = fraseDelResumen(consolidado);
    const faltan = bloquesIncompletos(consolidado).filter(
      (bloque) => bloque.estado === ESTADOS_DEL_BLOQUE.NO_DISPONIBLE,
    );
    if (faltan.length > 0) {
      pintarAviso(zonaAviso, {
        tono: 'advertencia',
        titulo: 'Consulta parcial',
        detalle: `No pudimos consultar ${faltan.map((bloque) => `«${bloque.titulo}»`).join(', ')}; el resto está completo. Lo que descargues o imprimas dirá qué falta.`,
        accion: {
          texto: 'Volver a consultar',
          nombre: 'reconsultar',
          alPulsar: () => cargar(true),
        },
      });
    }
    botonDescargar.disabled = false;
    botonImprimir.disabled = false;
    return consolidado;
  }

  /** @param {boolean} [forzar] vuelve a consultar aunque ya haya datos */
  function cargar(forzar = false) {
    if (enCurso && !forzar) {
      return enCurso;
    }
    enCurso = consultar();
    return enCurso;
  }

  botonDescargar.addEventListener('click', () => {
    if (consolidado) {
      descargarJson(consolidado, { documento, urlImpl });
    }
  });

  botonImprimir.addEventListener('click', () => {
    if (!consolidado || !reporte) {
      return;
    }
    pintarReporte(reporte, consolidado);
    reporte.hidden = false;
    documento.body.classList.add('imprimiendo-privacidad');
    const terminar = () => {
      documento.body.classList.remove('imprimiendo-privacidad');
      reporte.hidden = true;
      vaciar(reporte);
    };
    // `afterprint` llega al cerrar el diálogo de impresión; si el navegador
    // no lo emite, la hoja sigue oculta en pantalla (privacidad.css).
    window.addEventListener('afterprint', terminar, { once: true });
    imprimir();
  });

  return { cargar, actual: () => consolidado };
}
