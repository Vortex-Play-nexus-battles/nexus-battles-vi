/**
 * HU-INV-011 - Control de paginacion.
 *
 * Fuente: issue #43 (SCRUM-272), criterios migrados de Jira.
 *
 * Vive en `src/comun/` y no en el dominio del grupo porque la seccion 10.2 de
 * la guia de la empresa nombra la paginacion entre los componentes
 * compartidos: "lo que se repite en dos vistas sube a src/comun/... no se
 * copian y pegan". Es la misma razon por la que la barra de HU-INV-004 vive
 * aqui.
 *
 * Las clases del marcado (`paginacion`, `paginacion__info`,
 * `paginacion__paginas`, `paginacion__pagina`) ya estan definidas en
 * `shared/ui-kit/css/componentes.css`, derivadas del sistema de diseno. Este
 * modulo aporta el comportamiento, no los estilos: no se duplica ni un color.
 *
 * El indice de pagina es **desde cero** en toda la interfaz interna, porque
 * asi lo entrega el servicio (SCRUM-318). Al jugador se le muestra desde uno.
 *
 * @module paginacion
 */

/** Casillas numeradas visibles a la vez. Criterio 1: "hasta 10". */
export const CASILLAS_VISIBLES = 10;

/**
 * Rango de paginas visibles, centrado en la actual y recortado a los bordes.
 *
 * Se centra en vez de avanzar por bloques para que el jugador conserve la
 * referencia de donde esta: saltar de "1-10" a "11-20" de golpe hace perder
 * el contexto que el criterio 2 quiere evitar.
 *
 * @param {number} paginaActual indice desde cero
 * @param {number} totalPaginas
 * @param {number} [maximo=CASILLAS_VISIBLES]
 * @returns {{inicio: number, fin: number}} rango [inicio, fin) desde cero
 */
export function calcularVentana(paginaActual, totalPaginas, maximo = CASILLAS_VISIBLES) {
  if (totalPaginas <= maximo) {
    return { inicio: 0, fin: totalPaginas };
  }

  const mitad = Math.floor(maximo / 2);
  const ultimoInicioPosible = totalPaginas - maximo;
  const inicio = Math.min(Math.max(paginaActual - mitad, 0), ultimoInicioPosible);

  return { inicio, fin: inicio + maximo };
}

/**
 * Construye el control de paginacion.
 *
 * Emite unicamente el indice pedido: no conoce filtros ni texto de busqueda,
 * y por eso no puede perderlos. Conservarlos es responsabilidad de quien lo
 * monta, que vuelve a consultar con los mismos criterios cambiando solo la
 * pagina (criterio 3).
 *
 * UXC-9 — el nombre del control lo da quien lo monta. Antes todas las vistas
 * que lo reutilizan (tienda, tablón de misiones, catálogo) anunciaban
 * «Paginacion del inventario» a quien usa lector de pantalla, estuviera donde
 * estuviera.
 *
 * PLAYER-07b — dos ajustes opcionales, para que el mercado de subastas deje su
 * copia propia (con «Anterior» y «Siguiente» escritos en casillas de 32 px,
 * que se desbordaban) y use este control. Sin pasarlos, todo sigue como pide
 * RF-INV-002 para el inventario:
 *
 *   - `flechas: 'siempre'` pinta las dos flechas aunque no haya páginas
 *     ocultas, apagadas en los extremos. En un listado que se recorre página a
 *     página (el mercado) la flecha es el gesto principal y no puede aparecer y
 *     desaparecer según cuántas páginas haya.
 *   - `casillas` baja el número de casillas visibles (nunca por encima de
 *     diez): en un teléfono, diez casillas de 44 px no caben en una fila.
 *
 * Revisión final del módulo Inventario (13-oct) — dos ajustes más, también
 * opcionales, para cumplir §7.1 al pie de la letra («si hay una página, aparece
 * la casilla uno (1) … si hay más páginas a la izquierda, aparece una flecha a
 * la izquierda **para mostrar las opciones ocultas**»):
 *
 *   - `unaPagina: 'visible'` pinta la casilla «1» cuando hay una sola página
 *     (por omisión, `'oculta'`, el control no se ve: así lo esperan las otras
 *     vistas que lo usan).
 *   - `flechas: 'revelan-ocultas'`: como `'si-hay-ocultas'`, las flechas solo
 *     aparecen si hay páginas ocultas a su lado, pero al pulsarlas llevan a la
 *     **primera página oculta** de ese lado, no a la contigua. La ventana se
 *     recentra sobre ella y las opciones ocultas quedan a la vista en una sola
 *     pulsación (con ±1, desde la página 25 hacían falta cinco para ver la 15).
 *
 * @param {{paginaActual: number, totalPaginas: number}} estado
 * @param {(pagina: number) => void} alCambiarPagina
 * @param {{etiqueta?: string, flechas?: 'si-hay-ocultas'|'siempre'|'revelan-ocultas',
 *   casillas?: number, unaPagina?: 'oculta'|'visible'}} [opciones]
 *   `etiqueta` es el nombre accesible del control
 * @returns {HTMLElement} nav listo para insertar; oculto si no hay que paginar
 */
export function construirPaginacion(
  { paginaActual, totalPaginas },
  alCambiarPagina,
  {
    etiqueta = 'Paginación',
    flechas = 'si-hay-ocultas',
    casillas = CASILLAS_VISIBLES,
    unaPagina = 'oculta',
  } = {},
) {
  if (!Number.isInteger(totalPaginas) || totalPaginas < 0) {
    throw new RangeError(
      `paginacion: totalPaginas debe ser un entero no negativo y llego ${totalPaginas}`,
    );
  }
  if (!Number.isInteger(paginaActual) || paginaActual < 0) {
    throw new RangeError(
      `paginacion: paginaActual debe ser un entero no negativo y llego ${paginaActual}`,
    );
  }
  // Con cero paginas la unica actual posible es la cero: el inventario vacio
  // no es un error, es el estado vacio de HU-INV-001.
  if (totalPaginas > 0 && paginaActual >= totalPaginas) {
    throw new RangeError(
      `paginación: la página ${paginaActual} no existe en un total de ${totalPaginas}`,
    );
  }

  const control = document.createElement('nav');
  control.className = 'paginacion';
  control.setAttribute('aria-label', etiqueta);

  // Una sola pagina no se pagina. Se devuelve el nodo oculto en lugar de null
  // para que el llamador lo inserte una vez y solo cambie su contenido. Con
  // `unaPagina: 'visible'` (§7.1) la casilla «1» se ve igual; sin paginas
  // (vista vacia) nunca.
  if (totalPaginas === 0 || (totalPaginas === 1 && unaPagina !== 'visible')) {
    control.hidden = true;
    return control;
  }

  const info = document.createElement('p');
  info.className = 'paginacion__info';
  info.textContent = `Página ${paginaActual + 1} de ${totalPaginas}`;

  const paginas = document.createElement('div');
  paginas.className = 'paginacion__paginas';

  // Nunca mas de diez (criterio 1), y al menos una: un valor raro no puede
  // dejar el control sin casillas.
  const maximo = Math.min(Math.max(Math.trunc(Number(casillas)) || 1, 1), CASILLAS_VISIBLES);
  const { inicio, fin } = calcularVentana(paginaActual, totalPaginas, maximo);
  const siempre = flechas === 'siempre';
  const revelan = flechas === 'revelan-ocultas';

  if (inicio > 0 || siempre) {
    // `revelan-ocultas`: la flecha lleva a la ultima pagina oculta de la
    // izquierda (la inmediatamente anterior a la ventana), que al recentrarse
    // deja a la vista las que estaban escondidas.
    const destino = revelan ? inicio - 1 : paginaActual - 1;
    paginas.appendChild(
      construirFlecha(
        'anterior',
        '‹',
        revelan ? `Mostrar páginas anteriores (ir a la ${destino + 1})` : 'Página anterior',
        () => alCambiarPagina(destino),
        { apagada: paginaActual === 0 },
      ),
    );
  }

  for (let indice = inicio; indice < fin; indice += 1) {
    paginas.appendChild(construirCasilla(indice, paginaActual, alCambiarPagina));
  }

  if (fin < totalPaginas || siempre) {
    const destino = revelan ? fin : paginaActual + 1;
    paginas.appendChild(
      construirFlecha(
        'siguiente',
        '›',
        revelan ? `Mostrar páginas siguientes (ir a la ${destino + 1})` : 'Página siguiente',
        () => alCambiarPagina(destino),
        { apagada: paginaActual + 1 >= totalPaginas },
      ),
    );
  }

  control.append(info, paginas);
  return control;
}

/** Una casilla numerada. El numero se muestra desde uno. */
function construirCasilla(indice, paginaActual, alCambiarPagina) {
  const casilla = document.createElement('button');
  casilla.type = 'button';
  casilla.className = 'paginacion__pagina';
  casilla.textContent = String(indice + 1);

  if (indice === paginaActual) {
    casilla.setAttribute('aria-current', 'page');
  }

  casilla.addEventListener('click', () => {
    // Volver a pedir la pagina que ya se muestra seria una consulta inutil
    // al servicio y un parpadeo en la vitrina.
    if (indice === paginaActual) {
      return;
    }
    alCambiarPagina(indice);
  });

  return casilla;
}

/**
 * Flecha de navegacion. El simbolo es decorativo: el nombre va en aria-label.
 *
 * Con `apagada` (solo en el modo `flechas: 'siempre'`, en la primera o la
 * ultima pagina) queda deshabilitada en vez de desaparecer: la fila no salta
 * de sitio al llegar a un extremo, y no pide una pagina que no existe.
 */
function construirFlecha(direccion, simbolo, etiqueta, alPulsar, { apagada = false } = {}) {
  const flecha = document.createElement('button');
  flecha.type = 'button';
  flecha.className = 'paginacion__pagina';
  flecha.dataset.direccion = direccion;
  flecha.textContent = simbolo;
  flecha.setAttribute('aria-label', etiqueta);
  flecha.disabled = apagada;
  if (!apagada) {
    flecha.addEventListener('click', alPulsar);
  }
  return flecha;
}
