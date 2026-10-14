/**
 * El historial de moderación — §7.3.3 «Histórico de moderación: registro de
 * todas las acciones de moderación realizadas» (SA-4).
 *
 * Cada decisión ya quedaba asentada, pero solo se veía comentario por
 * comentario, desde su detalle. Aquí están todas, de la más reciente a la más
 * antigua, con quién decidió, qué, por qué y entre qué estados, y el
 * comentario sobre el que se decidió (`GET /comentarios/moderacion/historial`,
 * comentarios.yaml 1.14.0). Se filtra por acción, por moderador y por fechas;
 * «Abrir» lleva el comentario al panel de revisión de la misma pantalla.
 *
 * @module plataforma/comentarios/historial-moderacion
 */

import { barraDeFiltros } from '../../comun/ui/admin/barra-de-filtros.js';
import { instanteDelDia } from '../../comun/ui/admin/fechas.js';
import { tablaDeDatos } from '../../comun/ui/admin/tabla-de-datos.js';
import { construirPaginacion } from '../../comun/paginacion.js';
import { h, vaciar } from '../../comun/ui/dom.js';
import {
  estadoDeCarga,
  estadoDeError,
  estadoVacio,
  pintarEstado,
} from '../../comun/ui/estado-vista.js';
import { fechaHora } from '../../comun/ui/formato.js';
import { encabezadoDeSeccion } from '../../comun/ui/pagina.js';
import { ACCIONES, historialDeModeracion } from './cliente-moderacion.js';

/** RNF-USA-001. */
export const FILAS_POR_PAGINA = 16;

/** Lo que pasó en cada acción, dicho como hecho (la acción ya está hecha). */
export const HECHO_DE_ACCION = Object.freeze({
  APROBAR: 'Aprobado',
  OCULTAR: 'Ocultado',
  ELIMINAR: 'Eliminado',
  RESTAURAR: 'Restaurado',
  EDITAR: 'Texto editado',
  MARCAR: 'Marcado para seguimiento',
  DESMARCAR: 'Marca retirada',
});

/** Los estados de un comentario, como se leen. */
const ESTADO = Object.freeze({
  PUBLICADO: 'Publicado',
  EN_REVISION: 'En revisión',
  OCULTO: 'Oculto',
  ELIMINADO: 'Eliminado',
});

/**
 * La consulta a partir de la barra de filtros: los días pasan a instantes de
 * Colombia y «hasta» cuenta el día entero.
 *
 * @param {{accion?: string, moderador?: string, desde?: string, hasta?: string}} valores
 * @param {number} pagina
 */
export function consultaDelHistorial(valores, pagina) {
  return {
    accion: valores.accion || null,
    moderador: valores.moderador || null,
    desde: instanteDelDia(valores.desde),
    hasta: instanteDelDia(valores.hasta, { finDelDia: true }),
    pagina,
    tamano: FILAS_POR_PAGINA,
  };
}

/**
 * El cambio de estado de un asiento: «Publicado → Oculto», o solo el estado
 * cuando no cambió (editar, marcar).
 *
 * @param {{estadoAnterior: string, estadoNuevo: string}} asiento
 * @returns {string}
 */
export function cambioDeEstado({ estadoAnterior, estadoNuevo }) {
  const antes = ESTADO[estadoAnterior] ?? estadoAnterior;
  const despues = ESTADO[estadoNuevo] ?? estadoNuevo;
  return antes === despues ? `Sigue ${String(despues).toLowerCase()}` : `${antes} → ${despues}`;
}

/**
 * Monta el historial en su zona.
 *
 * @param {HTMLElement} zona
 * @param {{consultar?: typeof historialDeModeracion,
 *   alAbrirComentario?: ((comentarioId: string) => void)|null}} [opciones]
 * @returns {{cargar: () => Promise<void>}}
 */
export function montarHistorialDeModeracion(
  zona,
  { consultar = historialDeModeracion, alAbrirComentario = null } = {},
) {
  let valores = {};
  let pagina = 0;
  let pedido = 0;

  const resultados = h('div', {
    clase: 'pila pila--compacta',
    datos: { zona: 'resultados-del-historial' },
  });
  const barra = barraDeFiltros({
    etiqueta: 'Filtrar el historial de moderación',
    busqueda: { nombre: 'moderador', etiqueta: 'Moderador', placeholder: 'Apodo de quien decidió' },
    filtros: [
      {
        nombre: 'accion',
        etiqueta: 'Acción',
        opciones: ACCIONES.map((a) => [a.valor, HECHO_DE_ACCION[a.valor] ?? a.etiqueta]),
        todos: 'Todas las acciones',
      },
      { nombre: 'desde', etiqueta: 'Desde', tipo: 'fecha' },
      { nombre: 'hasta', etiqueta: 'Hasta', tipo: 'fecha' },
    ],
    validar: (leidos) =>
      leidos.desde && leidos.hasta && leidos.desde > leidos.hasta
        ? 'La fecha «desde» es posterior a «hasta»: ninguna decisión puede cumplir las dos.'
        : null,
    alAplicar: (leidos) => {
      valores = leidos;
      pagina = 0;
      cargar();
    },
  });

  vaciar(zona).append(
    encabezadoDeSeccion({
      titulo: 'Historial de moderación',
      detalle:
        'Todas las decisiones, de la más reciente a la más antigua: quién decidió, qué, por qué y ' +
        'sobre qué comentario.',
    }),
    barra.elemento,
    resultados,
  );

  async function cargar() {
    pedido += 1;
    const estePedido = pedido;
    pintarEstado(resultados, estadoDeCarga({ filas: 3, etiqueta: 'Cargando el historial' }));
    let datos;
    try {
      datos = await consultar(consultaDelHistorial(valores, pagina));
    } catch (error) {
      if (estePedido !== pedido) {
        return;
      }
      pintarEstado(
        resultados,
        estadoDeError({
          titulo: 'No pudimos cargar el historial',
          // ErrorDeApi ya trae el texto para personas (UXC-9); un fallo de red, no.
          detalle:
            error?.detalle || 'El servicio de comentarios no respondió. Vuelve a intentarlo.',
          alReintentar: cargar,
        }),
      );
      return;
    }
    if (estePedido !== pedido) {
      return;
    }
    const filas = Array.isArray(datos?.contenido) ? datos.contenido : [];
    if (filas.length === 0) {
      const conFiltros = Object.values(valores).some(Boolean);
      pintarEstado(
        resultados,
        estadoVacio(
          conFiltros
            ? {
                titulo: 'Ninguna decisión coincide con los filtros',
                detalle: 'Quita alguno para ver más.',
              }
            : { titulo: 'Todavía no hay decisiones de moderación' },
        ),
      );
      return;
    }
    const total = Number(datos.total) || filas.length;
    vaciar(resultados).append(
      tablaDeDatos({
        titulo: 'Decisiones de moderación',
        resumen: `${pagina * FILAS_POR_PAGINA + 1}–${pagina * FILAS_POR_PAGINA + filas.length} de ${total} ${
          total === 1 ? 'decisión' : 'decisiones'
        }`,
        claveDeFila: (a) => a.id,
        columnas: [
          { clave: 'fecha', titulo: 'Fecha', tipo: 'fecha', valor: (a) => fechaHora(a.fecha) },
          {
            clave: 'accion',
            titulo: 'Acción',
            principal: true,
            valor: (a) => HECHO_DE_ACCION[a.accion] ?? a.accion,
          },
          { clave: 'apodoModerador', titulo: 'Moderador' },
          {
            clave: 'comentario',
            titulo: 'Comentario',
            valor: (a) =>
              a.extracto
                ? h('span', {
                    clase: 'celda-doble',
                    hijos: [
                      h('span', { texto: `«${a.extracto}»` }),
                      a.apodoAutor
                        ? h('span', { clase: 't-meta', texto: `de ${a.apodoAutor}` })
                        : null,
                    ],
                  })
                : null,
          },
          { clave: 'cambio', titulo: 'Estado', valor: (a) => cambioDeEstado(a) },
          { clave: 'motivo', titulo: 'Motivo' },
          {
            clave: 'acciones',
            titulo: 'Acciones',
            tipo: 'accion',
            valor: (a) => {
              if (typeof alAbrirComentario !== 'function' || !a.comentarioId) {
                return null;
              }
              const boton = h('button', {
                clase: 'boton boton--secundario boton--pequeno',
                texto: 'Abrir',
                atributos: {
                  type: 'button',
                  'aria-label': `Abrir el comentario${a.apodoAutor ? ` de ${a.apodoAutor}` : ''}`,
                },
                datos: { accion: 'abrir-comentario' },
              });
              boton.addEventListener('click', () => alAbrirComentario(a.comentarioId));
              return boton;
            },
          },
        ],
        filas,
      }),
      construirPaginacion(
        { paginaActual: pagina, totalPaginas: Number(datos.totalPaginas) || 1 },
        (nueva) => {
          pagina = nueva;
          cargar();
        },
        { etiqueta: 'Páginas del historial de moderación' },
      ),
    );
  }

  cargar();
  return { cargar };
}
