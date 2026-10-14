/**
 * Resumen de la consola — la primera pantalla de quien opera el Nexo.
 *
 * SA-7 (informe de revisión, punto 1): «poner un resumen del sistema, sus
 * últimos movimientos y la estabilidad del sistema», en vez de repetir en
 * tarjetas los mismos destinos que ya tiene la barra de la consola.
 *
 *   - **Cifras clave**, cada una del servicio dueño del dato y solo las que el
 *     rol puede consultar: cuentas registradas, servicios en línea, comentarios
 *     por revisar, apelaciones por resolver, salas en juego y torneos.
 *   - **Últimos movimientos**: las acciones más recientes de la bitácora
 *     central (§7.3.5), con apodos y nombres. Solo el Super Administrador lee
 *     la auditoría; a los demás roles se les enseñan los comentarios
 *     reportados más recientes, que sí pueden atender.
 *   - **Estabilidad**: qué servicios no están bien, primero, y cuántos sí.
 *   - **Accesos rápidos** a lo que no está en la barra (crear una cuenta
 *     administrativa, un producto, los banners).
 *
 * Nada de lo que se ve es de ejemplo: lo que no responde dice «Sin dato» o
 * «Sin conexión», nunca un cero inventado. Se actualiza sola cada minuto, como
 * el control integral.
 *
 * @module plataforma/consola/consola
 */

import { NOMBRE_DE_ROL } from '../../comun/shell.js';
import { ROLES, destinoVisible, urlDeVista } from '../../comun/acceso.js';
import { actualizacionPeriodica } from '../../comun/ui/admin/actualizacion.js';
import { rejillaDeIndicadores, tarjetaDeIndicador } from '../../comun/ui/admin/indicador.js';
import { nombreDelServicio } from '../../comun/ui/admin/servicios.js';
import { distintivoDeEstado, ESTADOS_DE_SERVICIO } from '../../comun/ui/admin/estado.js';
import { h, vaciar } from '../../comun/ui/dom.js';
import { fecha, fechaHora, numero } from '../../comun/ui/formato.js';
import { encabezadoDePagina } from '../../comun/ui/pagina.js';
import { consultar, filasDe, RESULTADO, totalDe } from './cliente-consola.js';
import { nombreDeAccion } from '../../comun/ui/admin/nombres.js';
import { tablaDeDatos } from '../../comun/ui/admin/tabla-de-datos.js';
import {
  celdaDeSobre,
  momentoDe,
  quienDe,
  recortado,
  sobreDe,
  uidsDe,
} from './entradas-de-auditoria.js';
import { panel, pintarDesenlace } from './panel.js';
import { directorioDeTitulares } from './titulares.js';

/** Cada cuánto se repinta sola, como el control integral. */
export const PERIODO_DEL_RESUMEN_MS = 60_000;

/** Cuántos movimientos se enseñan; el registro completo está en Auditoría. */
export const MOVIMIENTOS_EN_EL_RESUMEN = 8;

/**
 * El tono de algo que hay que atender: pendiente si hay alguno, bien si hay
 * cero y nada si no se sabe.
 *
 * @param {number|null} cuantos
 * @returns {'advertencia'|'exito'|null}
 */
export function tonoDePendientes(cuantos) {
  if (typeof cuantos !== 'number') {
    return null;
  }
  return cuantos > 0 ? 'advertencia' : 'exito';
}

/** Qué servicios se cuentan como «necesitan atención», en ese orden. */
const ORDEN_DE_ATENCION = Object.freeze({ CAIDO: 0, LENTO: 1 });

const ES_DE_ADMINISTRACION = new Set([ROLES.ADMINISTRADOR, ROLES.SUPER_ADMINISTRADOR]);

/**
 * Las cifras del resumen. Cada una dice de dónde sale, qué rol la puede ver y
 * cómo se lee su respuesta; `vista` es a dónde lleva su enlace.
 */
export const CIFRAS_DEL_RESUMEN = Object.freeze([
  {
    id: 'cuentas',
    etiqueta: 'Cuentas registradas',
    recurso: '/admin/jugadores?page=0&size=1',
    administracion: true,
    vista: 'gestion-usuarios',
    textoDelEnlace: 'Ver usuarios',
    leer: (datos) => ({ valor: totalDe(datos) }),
  },
  {
    id: 'servicios',
    etiqueta: 'Servicios en línea',
    recurso: '/admin/sistema/servicios',
    administracion: true,
    vista: 'tablero-tecnico',
    textoDelEnlace: 'Ver el tablero técnico',
    leer: (datos) => {
      const total = Number.isFinite(datos?.total) ? datos.total : null;
      const operativos = Number.isFinite(datos?.operativos) ? datos.operativos : null;
      const malos = (Number(datos?.caidos) || 0) + (Number(datos?.lentos) || 0);
      return {
        valor: total !== null && operativos !== null ? `${operativos} de ${total}` : null,
        detalle: notaDeServicios(datos),
        tono: total === null ? null : tonoDePendientes(malos),
      };
    },
  },
  {
    id: 'comentarios',
    etiqueta: 'Comentarios por revisar',
    // comentarios.yaml pagina con `pagina`/`tamano`; `total` es la cola entera.
    recurso: '/comentarios/moderacion?pagina=0&tamano=1',
    administracion: false,
    vista: 'moderar-comentarios',
    textoDelEnlace: 'Abrir la moderación',
    leer: (datos) => {
      const valor = totalDe(datos);
      return { valor, tono: tonoDePendientes(valor) };
    },
  },
  {
    // Todas las pendientes (moderacion-sanciones-admin.yaml, `GET /apelaciones`
    // del panel), de la que vence antes a la que vence después: §7.3.2 da
    // quince días hábiles para decidir, y lo urgente es lo que ya venció o
    // está por vencer.
    id: 'apelaciones',
    etiqueta: 'Apelaciones por resolver',
    recurso: '/apelaciones',
    administracion: false,
    vista: 'sanciones-admin',
    textoDelEnlace: 'Ir a sanciones',
    leer: (datos) => {
      const valor = totalDe(datos);
      const pendientes = filasDe(datos);
      const vencidas = pendientes.filter((apelacion) => apelacion?.vencida === true).length;
      return {
        valor,
        detalle: notaDeApelaciones(pendientes, vencidas),
        tono: vencidas > 0 ? 'error' : tonoDePendientes(valor),
      };
    },
  },
  {
    // El listado de salas (salas-partidas.yaml) solo trae las que esperan
    // jugadores —abiertas, llenas o privadas—: las que ya están en juego no
    // aparecen aunque se pidan. La cifra dice eso y no una que no hay de dónde
    // sacar. `tamano=1`: solo hace falta el total de la página.
    id: 'salas',
    etiqueta: 'Salas abiertas',
    recurso: '/salas?tamano=1',
    administracion: true,
    vista: 'control-integral',
    textoDelEnlace: 'Ver salas y partidas',
    leer: (datos) => ({ valor: totalDe(datos) }),
  },
  {
    id: 'torneos',
    etiqueta: 'Torneos publicados',
    recurso: '/torneos',
    administracion: true,
    vista: 'control-integral',
    textoDelEnlace: 'Ver torneos',
    leer: (datos) => ({ valor: totalDe(datos) }),
  },
]);

/**
 * «2 fuera de plazo» o «La próxima vence el 20 oct 2026»: lo que hay que
 * atender primero. Sin pendientes, nada.
 *
 * @param {Array<{decisionAntesDe?: string, vencida?: boolean}>} pendientes
 * @param {number} vencidas
 * @returns {string|null}
 */
export function notaDeApelaciones(pendientes, vencidas) {
  if (vencidas > 0) {
    return `${numero(vencidas)} fuera de plazo`;
  }
  const proxima = pendientes
    .map((apelacion) => apelacion?.decisionAntesDe)
    .filter((plazo) => Number.isFinite(Date.parse(plazo)))
    .sort((a, b) => Date.parse(a) - Date.parse(b))[0];
  return proxima ? `La próxima vence el ${fecha(proxima)}` : null;
}

/** «1 lento · 2 caídos»: solo lo que no está bien, y solo si hay algo. */
export function notaDeServicios(datos) {
  const partes = [];
  if (Number(datos?.caidos) > 0) {
    partes.push(`${numero(datos.caidos)} ${datos.caidos === 1 ? 'caído' : 'caídos'}`);
  }
  if (Number(datos?.lentos) > 0) {
    partes.push(`${numero(datos.lentos)} ${datos.lentos === 1 ? 'lento' : 'lentos'}`);
  }
  return partes.length ? partes.join(' · ') : 'Todos responden con normalidad';
}

/**
 * Las cifras que este rol puede ver. Las de administración no se piden
 * siquiera para un moderador: el servicio diría 403 y la tarjeta solo
 * ocuparía sitio.
 *
 * @param {string|null} rol
 */
export function cifrasDelRol(rol) {
  return CIFRAS_DEL_RESUMEN.filter(
    (cifra) => !cifra.administracion || ES_DE_ADMINISTRACION.has(rol),
  );
}

/** La tarjeta de una cifra según lo que respondió su servicio. */
export function tarjetaDeCifra(cifra, desenlace, { base } = {}) {
  const enlace = cifra.vista
    ? { texto: cifra.textoDelEnlace, href: urlDeVista(cifra.vista, base) }
    : null;
  if (desenlace?.resultado === RESULTADO.DATOS || desenlace?.resultado === RESULTADO.VACIO) {
    // Una página sin filas puede traer su total: se lee igual, y solo vale
    // cero si la respuesta vacía no dice cuántos hay.
    const lectura = cifra.leer(desenlace.datos);
    return tarjetaDeIndicador({
      etiqueta: cifra.etiqueta,
      valor: lectura.valor ?? (desenlace.resultado === RESULTADO.VACIO ? 0 : null),
      detalle: lectura.detalle ?? null,
      tono: lectura.tono ?? null,
      enlace,
    });
  }
  return tarjetaDeIndicador({
    etiqueta: cifra.etiqueta,
    valor: null,
    detalle: desenlace?.resultado === RESULTADO.SIN_PERMISO ? 'Sin acceso' : 'Sin conexión',
    tono: desenlace?.resultado === RESULTADO.SIN_PERMISO ? null : 'error',
    enlace,
  });
}

/**
 * La estabilidad: lo que necesita atención primero (caídos y lentos, con lo
 * que dijo la sonda) y cuántos van bien.
 *
 * @param {*} datos `RespuestaDelSistema` de metricas-plataforma.yaml
 * @returns {HTMLElement[]}
 */
export function pintarEstabilidad(datos) {
  const servicios = Array.isArray(datos?.servicios) ? datos.servicios : [];
  const atencion = servicios
    .filter((s) => s?.estado in ORDEN_DE_ATENCION)
    .sort((a, b) => ORDEN_DE_ATENCION[a.estado] - ORDEN_DE_ATENCION[b.estado]);
  const operativos = servicios.filter((s) => s?.estado === 'OPERATIVO').length;
  const resumen = h('p', {
    clase: 'resumen-consola__estabilidad',
    texto: `${numero(operativos)} de ${numero(servicios.length)} servicios responden con normalidad.`,
  });
  if (!atencion.length) {
    return [
      resumen,
      h('p', {
        clase: 't-meta',
        texto: 'Ningún servicio está caído ni lento.',
      }),
    ];
  }
  return [
    resumen,
    h('ul', {
      clase: 'resumen-consola__atencion',
      hijos: atencion.map((servicio) =>
        h('li', {
          datos: { servicio: servicio.servicio, estado: servicio.estado },
          hijos: [
            h('span', {
              clase: 'resumen-consola__servicio',
              texto: nombreDelServicio(servicio.servicio),
            }),
            distintivoDeEstado(ESTADOS_DE_SERVICIO[servicio.estado] ?? { texto: servicio.estado }),
            servicio.detalle ? h('span', { clase: 't-meta', texto: servicio.detalle }) : null,
          ],
        }),
      ),
    }),
  ];
}

/**
 * Los últimos movimientos de la bitácora central, compactos para el resumen:
 * cuándo, quién, qué hizo —con su motivo debajo— y sobre qué. El registro
 * completo, con sus filtros y su exportación, está en Auditoría.
 *
 * @param {*} datos página de `GET /admin/auditoria`
 * @param {ReturnType<typeof directorioDeTitulares>} titulares
 * @returns {HTMLElement}
 */
export function pintarMovimientos(datos, titulares) {
  const filas = filasDe(datos);
  return tablaDeDatos({
    titulo: 'Últimos movimientos',
    resumen:
      filas.length === 1
        ? 'La acción más reciente'
        : `Las ${numero(filas.length)} acciones más recientes`,
    claveDeFila: (entrada) => entrada.id,
    columnas: [
      {
        clave: 'fechaHora',
        titulo: 'Cuándo',
        tipo: 'fecha',
        principal: true,
        valor: (entrada) => {
          const momento = momentoDe(entrada);
          return momento ? fechaHora(momento) : null;
        },
      },
      {
        clave: 'administrador',
        titulo: 'Quién',
        valor: (entrada) => {
          const quien = quienDe(entrada.administrador, titulares);
          return quien.identificado
            ? quien.texto
            : h('span', { clase: 'tabla-admin__sin-dato', texto: quien.texto });
        },
      },
      {
        clave: 'tipoAccion',
        titulo: 'Qué hizo',
        valor: (entrada) =>
          h('span', {
            clase: 'celda-doble',
            hijos: [
              h('span', { texto: nombreDeAccion(entrada.tipoAccion) }),
              entrada.motivo
                ? h('span', { clase: 't-meta', texto: recortado(entrada.motivo, 60) })
                : null,
            ],
          }),
      },
      {
        clave: 'afectado',
        titulo: 'Sobre',
        valor: (entrada) => celdaDeSobre(sobreDe(entrada.afectado, titulares)),
      },
    ],
    filas,
  });
}

/**
 * Los comentarios reportados que encabezan la cola, para los roles que no leen
 * la auditoría: cada entrada de `GET /comentarios/moderacion` (comentarios.yaml,
 * `EntradaDeCola`) trae el comentario, cuántas personas lo reportaron y cuándo
 * llegó el primer reporte.
 */
export function pintarReportesRecientes(datos) {
  const entradas = filasDe(datos).slice(0, MOVIMIENTOS_EN_EL_RESUMEN);
  return h('ul', {
    clase: 'resumen-consola__lista',
    hijos: entradas.map((entrada) => {
      const comentario = entrada?.comentario ?? {};
      const reportes = Number(entrada?.reportes) || 0;
      return h('li', {
        datos: entrada?.prioridadElevada ? { prioridad: 'elevada' } : {},
        hijos: [
          h('span', {
            clase: 'resumen-consola__texto',
            texto: String(comentario.texto ?? '').slice(0, 120) || 'Comentario sin texto',
          }),
          h('span', {
            clase: 't-meta',
            texto: [
              comentario.apodoAutor ? `De ${comentario.apodoAutor}` : null,
              reportes ? `${numero(reportes)} ${reportes === 1 ? 'reporte' : 'reportes'}` : null,
              entrada?.primerReporte ? fechaHora(entrada.primerReporte) : null,
            ]
              .filter(Boolean)
              .join(' · '),
          }),
        ],
      });
    }),
  });
}

/**
 * Monta el resumen sobre sus zonas.
 *
 * @param {Document|HTMLElement} raiz
 * @param {{apodo?: string, rol?: string|null}} sesion
 * @param {{consultarApi?: typeof consultar, base?: string, actualizarSola?: boolean}} [dependencias]
 */
export function montarConsola(
  raiz,
  sesion,
  { consultarApi = consultar, base, actualizarSola = true } = {},
) {
  const rol = sesion?.rol ?? null;
  const esSuper = rol === ROLES.SUPER_ADMINISTRADOR;

  const zonaIndicadores = raiz.querySelector('[data-zona="indicadores"]');
  const zonaPaneles = raiz.querySelector('[data-zona="paneles"]');
  const zonaAccesos = raiz.querySelector('[data-zona="accesos"]');

  const cifras = cifrasDelRol(rol);
  const titulares = directorioDeTitulares({ consultarApi });

  // Últimos movimientos: la bitácora para el Super Administrador; los
  // comentarios reportados para quien no la puede leer.
  const movimientos = panel({
    id: 'movimientos',
    titulo: esSuper ? 'Últimos movimientos' : 'Comentarios por revisar primero',
    acciones: [
      esSuper
        ? h('a', {
            clase: 'panel-admin__enlace',
            texto: 'Ver el registro completo',
            atributos: { href: urlDeVista('auditoria', base) },
          })
        : h('a', {
            clase: 'panel-admin__enlace',
            texto: 'Abrir la moderación',
            atributos: { href: urlDeVista('moderar-comentarios', base) },
          }),
    ],
  });
  const estabilidad = ES_DE_ADMINISTRACION.has(rol)
    ? panel({
        id: 'estabilidad',
        titulo: 'Estabilidad del sistema',
        acciones: [
          h('a', {
            clase: 'panel-admin__enlace',
            texto: 'Ver el estado de cada servicio',
            atributos: { href: urlDeVista('tablero-tecnico', base) },
          }),
        ],
      })
    : null;

  async function cargarMovimientos() {
    if (esSuper) {
      const desenlace = await consultarApi(
        `/admin/auditoria?page=0&size=${MOVIMIENTOS_EN_EL_RESUMEN}&sort=fechaHora,desc&sort=id,desc`,
      );
      if (desenlace.resultado === RESULTADO.DATOS) {
        await titulares.nombrar(uidsDe(filasDe(desenlace.datos)));
      }
      pintarDesenlace(movimientos, desenlace, (datos) => pintarMovimientos(datos, titulares), {
        alReintentar: cargarMovimientos,
        vacio: { titulo: 'Todavía no hay acciones registradas' },
      });
      return desenlace;
    }
    const desenlace = await consultarApi(
      `/comentarios/moderacion?pagina=0&tamano=${MOVIMIENTOS_EN_EL_RESUMEN}`,
    );
    pintarDesenlace(movimientos, desenlace, pintarReportesRecientes, {
      alReintentar: cargarMovimientos,
      vacio: { titulo: 'No hay comentarios reportados' },
    });
    return desenlace;
  }

  async function cargarEstabilidad() {
    if (!estabilidad) {
      return null;
    }
    const desenlace = await consultarApi('/admin/sistema/servicios');
    pintarDesenlace(estabilidad, desenlace, pintarEstabilidad, { alReintentar: cargarEstabilidad });
    return desenlace;
  }

  async function cargarCifras() {
    const desenlaces = await Promise.all(cifras.map((cifra) => consultarApi(cifra.recurso)));
    zonaIndicadores?.replaceChildren(
      rejillaDeIndicadores(
        cifras.map((cifra, i) => tarjetaDeCifra(cifra, desenlaces[i], { base })),
        { zona: 'cifras' },
      ),
    );
    return desenlaces;
  }

  const actualizacion = actualizacionPeriodica({
    nombre: 'el resumen',
    cadaMs: PERIODO_DEL_RESUMEN_MS,
    automatica: actualizarSola,
    cargar: async () => {
      const [desenlacesDeCifras, ...otros] = await Promise.all([
        cargarCifras(),
        cargarMovimientos(),
        cargarEstabilidad(),
      ]);
      return [...desenlacesDeCifras, ...otros]
        .filter(Boolean)
        .some((d) => d.resultado === RESULTADO.DATOS || d.resultado === RESULTADO.VACIO);
    },
  });

  raiz.querySelector('[data-zona="encabezado"]')?.replaceChildren(
    encabezadoDePagina({
      titulo: 'Control del Nexo',
      descripcion: sesion?.apodo
        ? `${sesion.apodo} · ${NOMBRE_DE_ROL[rol] ?? 'Operación'}`
        : (NOMBRE_DE_ROL[rol] ?? 'Operación'),
      acciones: [actualizacion.elemento],
    }),
  );

  if (zonaPaneles) {
    vaciar(zonaPaneles).append(...[movimientos.elemento, estabilidad?.elemento].filter(Boolean));
  }

  // Accesos rápidos: solo lo que no está ya en la barra de la consola.
  const accesos = [
    { vista: 'crear-cuenta-admin', texto: 'Crear cuenta administrativa' },
    { vista: 'productos', texto: 'Nuevo producto' },
    { vista: 'banners', texto: 'Banners y anuncios' },
  ].filter((acceso) => destinoVisible(acceso.vista, sesion));
  if (zonaAccesos) {
    zonaAccesos.hidden = accesos.length === 0;
    zonaAccesos.replaceChildren(
      ...(accesos.length
        ? [
            h('h2', { clase: 't-subtitulo', texto: 'Accesos rápidos' }),
            h('div', {
              clase: 'resumen-consola__accesos',
              hijos: accesos.map((acceso) =>
                h('a', {
                  clase: 'boton boton--secundario',
                  texto: acceso.texto,
                  datos: { acceso: acceso.vista },
                  atributos: { href: urlDeVista(acceso.vista, base) },
                }),
              ),
            }),
          ]
        : []),
    );
  }

  actualizacion.actualizar();
  return { actualizacion, cargarCifras, cargarMovimientos, cargarEstabilidad };
}
