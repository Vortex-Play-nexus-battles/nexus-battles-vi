/**
 * SCRUM-320 - Los cuatro estados de una vista que consulta datos.
 *
 * `RNF-USA-003` los exige en toda vista que consulte datos, y la pila
 * aprobada pide centralizarlos "para que veinte modulos no los implementen
 * de veinte formas". Su hogar definitivo es `shared/ui-kit`, hoy vacio;
 * mientras tanto viven aqui y se mudan sin cambiar la interfaz.
 *
 * Regla del cliente, dictada en clase el 2026-08-13: *"uno como usuario
 * jamas deberia ver un status de HTML"*. Por eso ningun texto de estos
 * estados lleva codigos del protocolo ni detalle tecnico: eso va a la
 * consola, para el equipo.
 */

/** Estado de carga: la consulta esta en vuelo. */
export function construirCarga(mensaje = 'Cargando tu inventario...') {
  return construirEstado('estado-carga', mensaje, { rol: 'status' });
}

/**
 * Estado vacio: la consulta respondio bien y no hay nada que mostrar.
 * No es un error y no debe parecerlo.
 *
 * UX-R3.5 — «cuando consigas héroes, armas o ítems aparecerán aquí» explica
 * qué pasa y deja a quien lo lee esperando a que le pase algo. §18: un estado
 * vacío dice también qué puede hacer. Los héroes se consiguen en la tienda,
 * que es una pantalla que existe y a la que se llega desde aquí.
 *
 * @param {string} [mensaje]
 * @param {string} [detalle]
 * @param {{texto: string, href?: string, alPulsar?: () => void}|null} [accion]
 */
export function construirVacio(
  mensaje = 'Todavía no tienes productos en tu inventario.',
  detalle = 'Los héroes, las armas y los ítems que consigas aparecerán aquí. Puedes empezar por la tienda.',
  accion = { texto: 'Ir a la tienda', href: '../../cuentas/tienda.html' },
) {
  return construirEstado('estado-vacio', mensaje, { detalle, accion });
}

/**
 * Estado de error: la consulta fallo. El texto es para el jugador, no para
 * el equipo, asi que no nombra codigos ni servicios.
 *
 * UX-R3.5 — «vuelve a intentarlo en un momento» obligaba a recargar la página
 * entera. Reintentar es una acción, y por tanto un botón.
 *
 * @param {string} [mensaje]
 * @param {string} [detalle]
 * @param {{texto: string, href?: string, alPulsar?: () => void}|null} [accion]
 */
export function construirError(
  mensaje = 'No pudimos cargar tu inventario en este momento.',
  detalle = 'Puede ser un problema pasajero del servicio.',
  accion = null,
) {
  return construirEstado('estado-error', mensaje, { detalle, rol: 'alert', accion });
}

/**
 * @param {string} clase
 * @param {string} mensaje
 * @param {{detalle?: string, rol?: string,
 *          accion?: {texto: string, href?: string, alPulsar?: () => void}|null}} [opciones]
 */
function construirEstado(clase, mensaje, { detalle, rol, accion } = {}) {
  const bloque = document.createElement('div');
  bloque.className = `estado ${clase}`;
  if (rol) {
    bloque.setAttribute('role', rol);
  }

  const principal = document.createElement('p');
  principal.className = 'estado__mensaje';
  principal.textContent = mensaje;
  bloque.appendChild(principal);

  if (detalle) {
    const secundario = document.createElement('p');
    secundario.className = 'estado__detalle';
    secundario.textContent = detalle;
    bloque.appendChild(secundario);
  }

  if (accion) {
    const control = document.createElement(accion.href ? 'a' : 'button');
    control.className = 'boton boton--primario';
    control.textContent = accion.texto;
    if (accion.href) {
      control.href = accion.href;
    } else {
      control.type = 'button';
      control.dataset.accion = 'reintentar';
      control.addEventListener('click', accion.alPulsar);
    }
    bloque.appendChild(control);
  }

  return bloque;
}
