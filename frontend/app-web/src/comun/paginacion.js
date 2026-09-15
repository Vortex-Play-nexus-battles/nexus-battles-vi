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
 * @param {{paginaActual: number, totalPaginas: number}} estado
 * @param {(pagina: number) => void} alCambiarPagina
 * @returns {HTMLElement} nav listo para insertar; oculto si no hay que paginar
 */
export function construirPaginacion({ paginaActual, totalPaginas }, alCambiarPagina) {
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
      `paginacion: la pagina ${paginaActual} no existe en un total de ${totalPaginas}`,
    );
  }

  const control = document.createElement('nav');
  control.className = 'paginacion';
  control.setAttribute('aria-label', 'Paginacion del inventario');

  // Una sola pagina no se pagina. Se devuelve el nodo oculto en lugar de null
  // para que el llamador lo inserte una vez y solo cambie su contenido.
  if (totalPaginas <= 1) {
    control.hidden = true;
    return control;
  }

  const info = document.createElement('p');
  info.className = 'paginacion__info';
  info.textContent = `Pagina ${paginaActual + 1} de ${totalPaginas}`;

  const paginas = document.createElement('div');
  paginas.className = 'paginacion__paginas';

  const { inicio, fin } = calcularVentana(paginaActual, totalPaginas);

  if (inicio > 0) {
    paginas.appendChild(
      construirFlecha('anterior', '‹', 'Pagina anterior', () => alCambiarPagina(paginaActual - 1)),
    );
  }

  for (let indice = inicio; indice < fin; indice += 1) {
    paginas.appendChild(construirCasilla(indice, paginaActual, alCambiarPagina));
  }

  if (fin < totalPaginas) {
    paginas.appendChild(
      construirFlecha('siguiente', '›', 'Pagina siguiente', () =>
        alCambiarPagina(paginaActual + 1),
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

/** Flecha de navegacion. El simbolo es decorativo: el nombre va en aria-label. */
function construirFlecha(direccion, simbolo, etiqueta, alPulsar) {
  const flecha = document.createElement('button');
  flecha.type = 'button';
  flecha.className = 'paginacion__pagina';
  flecha.dataset.direccion = direccion;
  flecha.textContent = simbolo;
  flecha.setAttribute('aria-label', etiqueta);
  flecha.addEventListener('click', alPulsar);
  return flecha;
}
