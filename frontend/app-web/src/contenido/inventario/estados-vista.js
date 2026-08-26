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
 */
export function construirVacio(
  mensaje = 'Todavía no tienes productos en tu inventario.',
  detalle = 'Cuando consigas héroes, armas o ítems aparecerán aquí.') {
  return construirEstado('estado-vacio', mensaje, { detalle });
}

/**
 * Estado de error: la consulta fallo. El texto es para el jugador, no para
 * el equipo, asi que no nombra codigos ni servicios.
 */
export function construirError(
  mensaje = 'No pudimos cargar tu inventario en este momento.',
  detalle = 'Vuelve a intentarlo en un momento.') {
  return construirEstado('estado-error', mensaje, { detalle, rol: 'alert' });
}

function construirEstado(clase, mensaje, { detalle, rol } = {}) {
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
  return bloque;
}
