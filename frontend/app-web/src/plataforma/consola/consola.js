/**
 * Aterrizaje de la consola de operación — UX-R3.0 / UX-R3.3.
 *
 * RF-RBAC-002 (matriz de permisos) · RF-USR-009 · RF-ADM-* · RF-AUD-001.
 *
 * ## Por qué existe
 *
 * El armazón de consola necesitaba un sitio al que llevar. Antes, un
 * administrador que entraba al producto aterrizaba en la home del jugador y
 * tenía que ir a buscar sus herramientas dentro del menú de cuenta, una a
 * una, sin saber cuáles le correspondían a su rol: el menú las pintaba
 * todas y la que no le tocaba fallaba al abrirla.
 *
 * Esta pantalla responde a una sola pregunta —«¿qué puedo operar yo?»— y la
 * responde con la misma matriz que usan los guardas. Si aquí aparece una
 * tarjeta, esa pantalla se abre; si no aparece, el servidor también la habría
 * negado. No hay dos verdades.
 *
 * ## Lo que NO muestra
 *
 * Ni contadores, ni «usuarios activos hoy», ni gráficas de actividad. Nada de
 * eso tiene RF ni endpoint detrás: serían cifras inventadas en la primera
 * pantalla que ve quien opera el sistema. Las métricas reales viven en
 * «Métricas» y en «Técnico», que sí consultan `ms-metricas`.
 *
 * @module plataforma/consola/consola
 */

import { NOMBRE_DE_ROL, SECCIONES_CONSOLA } from '../../comun/shell.js';
import { destinoVisible, urlDeVista } from '../../comun/acceso.js';
import { encabezadoDePagina } from '../../comun/ui/pagina.js';
import { h } from '../../comun/ui/dom.js';

/** Qué hace cada herramienta, en una línea. Es lo que decide si se pulsa. */
const DESCRIPCION = Object.freeze({
  usuarios: 'Perfiles, roles y estado de las cuentas.',
  sanciones: 'Advertencias, suspensiones y apelaciones.',
  'lista-negra': 'Términos y apodos vetados en el registro.',
  parametros: 'Valores de juego y de plataforma en caliente.',
  metricas: 'Indicadores de uso y de negocio del Nexo.',
  tecnico: 'Salud de los servicios, latencia y errores.',
  auditoria: 'Registro de toda acción administrativa.',
});

/**
 * Qué puede hacer cada rol, dicho con las palabras de la Tabla 24 del
 * documento fuente. Aparece al pie para que «no me sale esa opción» tenga
 * respuesta sin abrir un ticket.
 */
const ALCANCE = Object.freeze({
  MODERADOR:
    'Como moderador puedes moderar comentarios, emitir advertencias y aplicar suspensiones temporales. La gestión de usuarios, los parámetros y la auditoría quedan fuera de tu alcance.',
  ADMINISTRADOR:
    'Como administrador puedes gestionar usuarios y productos, sancionar de forma definitiva y ajustar los parámetros de la plataforma. Crear cuentas administrativas y consultar la auditoría son competencia del super administrador.',
  SUPER_ADMINISTRADOR:
    'Como super administrador tienes acceso total, incluida la creación de cuentas administrativas y el registro de auditoría. Toda acción que hagas aquí queda asentada con tu identidad.',
});

/**
 * @param {Document|HTMLElement} raiz
 * @param {{apodo?: string, rol?: string|null}} sesion
 */
export function montarConsola(raiz, sesion, { base } = {}) {
  const rol = sesion?.rol ?? null;

  const zonaEncabezado = raiz.querySelector('[data-zona="encabezado"]');
  if (zonaEncabezado) {
    zonaEncabezado.replaceChildren(
      encabezadoDePagina({
        titulo: 'Control del Nexo',
        descripcion: sesion?.apodo
          ? `Sesión de ${sesion.apodo} · ${NOMBRE_DE_ROL[rol] ?? 'Operación'}`
          : 'Herramientas de operación',
      }),
    );
  }

  const zonaHerramientas = raiz.querySelector('[data-zona="herramientas"]');
  if (zonaHerramientas) {
    const tarjetas = SECCIONES_CONSOLA.filter(
      (seccion) => seccion.vista !== 'consola' && destinoVisible(seccion.vista, sesion),
    ).map((seccion) => {
      const tarjeta = h('a', {
        clase: 'tarjeta tarjeta--pulsable consola__herramienta',
        datos: { herramienta: seccion.id },
      });
      tarjeta.href = urlDeVista(seccion.vista, base);
      tarjeta.append(
        h('h3', { clase: 'tarjeta__titulo', texto: seccion.etiqueta }),
        h('p', { clase: 't-meta', texto: DESCRIPCION[seccion.id] ?? '' }),
      );
      return tarjeta;
    });
    zonaHerramientas.replaceChildren(...tarjetas);
  }

  const zonaAlcance = raiz.querySelector('[data-zona="alcance"]');
  if (zonaAlcance && ALCANCE[rol]) {
    zonaAlcance.replaceChildren(
      h('h2', { clase: 't-subtitulo', texto: 'Tu alcance' }),
      h('p', { clase: 'consola__alcance', texto: ALCANCE[rol] }),
    );
  }
}
