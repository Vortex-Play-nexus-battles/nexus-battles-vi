/**
 * Sala de espera — HU-SAL-006 (salir o cancelar antes de que empiece).
 *
 * Vive dentro de `sala-batalla.html`, en el tramo entre entrar a la sala y
 * que el anfitrion arranque el combate. Hace tres cosas y ninguna mas: decir
 * cuanta gente hay, ofrecer la salida que corresponde a quien mira, y dejar
 * de ofrecerla cuando ya no aplica.
 *
 * Que boton se ofrece lo decide el papel de quien mira, que la vista SI
 * conoce desde que `GET /salas/{id}` devuelve `idAnfitrion`:
 *   - anfitrion  → «Cancelar sala», con confirmacion (CA-05): expulsa a los
 *                  demas y devuelve los creditos de todos.
 *   - cualquier otro participante → «Salir de la sala», sin confirmacion.
 * Nunca los dos, y ninguno cuando la partida ya empezo (CA-03): el servidor
 * responderia 409 igual, pero ofrecer un boton que va a fallar es una
 * trampa.
 *
 * Sin DOM global: todo entra por parametros para que se pruebe con jsdom y
 * con dobles del cliente HTTP.
 *
 * @module sala-de-espera
 */

/** Clave de sessionStorage con la que el listado se entera de por que se volvio. */
export const CLAVE_AVISO_DEL_LISTADO = 'nexus.avisoDeSala';

/**
 * Texto de la confirmacion de cancelar (CA-05). Cuenta a los demas, no al
 * anfitrion: a el no se le expulsa, se va.
 *
 * @param {{ocupacion?: number}} sala
 * @returns {string}
 */
export function textoDeConfirmacion(sala) {
  const otros = Math.max(0, Number(sala?.ocupacion ?? 1) - 1);
  if (otros === 0) {
    return '¿Cancelar la sala? Todavia no ha entrado nadie mas.';
  }
  const gente = otros === 1 ? '1 participante' : `${otros} participantes`;
  return `¿Cancelar la sala? Se expulsara a ${gente}.`;
}

/**
 * Texto de la ocupacion, para la sala de espera.
 *
 * @param {{actual: number, maximo: number}} ocupacion
 * @returns {string}
 */
export function textoDeOcupacion(ocupacion) {
  const actual = Number(ocupacion?.actual ?? 0);
  const maximo = Number(ocupacion?.maximo ?? 0);
  return `${actual} de ${maximo} jugadores en la sala`;
}

/**
 * Deja al listado un aviso para que lo muestre al llegar. Se usa al salir,
 * al cancelar y cuando el anfitrion cancela y a uno lo devuelven.
 *
 * @param {Storage} storage
 * @param {{tono: 'info'|'exito'|'advertencia'|'error', titulo: string, detalle?: string}} aviso
 */
export function dejarAvisoParaElListado(storage, aviso) {
  try {
    storage.setItem(CLAVE_AVISO_DEL_LISTADO, JSON.stringify(aviso));
  } catch {
    // Sin almacenamiento no hay aviso; volver al listado sigue funcionando.
  }
}

/**
 * Recoge (y borra) el aviso que dejo la sala de espera. Se muestra una vez.
 *
 * @param {Storage} storage
 * @returns {{tono: string, titulo: string, detalle?: string} | null}
 */
export function recogerAvisoDelListado(storage) {
  try {
    const crudo = storage.getItem(CLAVE_AVISO_DEL_LISTADO);
    if (!crudo) {
      return null;
    }
    storage.removeItem(CLAVE_AVISO_DEL_LISTADO);
    const aviso = JSON.parse(crudo);
    return aviso && typeof aviso.titulo === 'string' ? aviso : null;
  } catch {
    return null;
  }
}

/**
 * La salida al listado, que solo puede ocurrir una vez por pagina.
 *
 * Al anfitrion que cancela le llega el mismo hecho por dos caminos: la
 * respuesta de su boton y su propio `sala.cancelada` por el canal, en el
 * orden que toque. Dos asignaciones seguidas a `location.href` abortan la
 * primera navegacion (`net::ERR_ABORTED`), asi que solo cuenta la primera
 * llamada; las demas no hacen nada.
 *
 * @param {Storage} storage donde dejar el aviso para el listado
 * @param {(destino: string) => void} navegar normalmente `href => location.href = href`
 * @param {string} [destino]
 * @returns {(aviso: {tono: string, titulo: string, detalle?: string}) => boolean}
 *   `true` si esta llamada fue la que navego
 */
export function salidaAlListado(storage, navegar, destino = './batallas.html') {
  let salio = false;
  return (aviso) => {
    if (salio) {
      return false;
    }
    salio = true;
    dejarAvisoParaElListado(storage, aviso);
    navegar(destino);
    return true;
  };
}

/**
 * Monta la sala de espera sobre `[data-zona="espera"]`.
 *
 * @param {ParentNode} raiz
 * @param {object} opciones
 * @param {{id: string, idAnfitrion: string, ocupacion: number, maximoParticipantes: number,
 *          estado?: string}} opciones.sala tal como la devolvio `GET /salas/{id}`
 * @param {string|null} opciones.yo identificador (uid) de quien mira
 * @param {(idSala: string) => Promise<void>} opciones.abandonar
 * @param {(idSala: string) => Promise<void>} opciones.cancelar
 * @param {(texto: string) => boolean} [opciones.confirmar] dialogo de confirmacion (CA-05)
 * @param {(salida: {motivo: 'abandono'|'cancelada'}) => void} [opciones.alSalir]
 * @returns {{actualizar: (estado: {ocupacion: {actual: number, maximo: number}}) => void,
 *            ocultar: () => void, esAnfitrion: boolean}}
 */
export function montarSalaDeEspera(
  raiz,
  { sala, yo, abandonar, cancelar, confirmar = () => true, alSalir = () => {} },
) {
  const zona = raiz.querySelector('[data-zona="espera"]');
  const ocupacion = raiz.querySelector('[data-zona="ocupacion"]');
  const aviso = raiz.querySelector('[data-zona="aviso-espera"]');
  const botonSalir = raiz.querySelector('[data-accion="salir-de-sala"]');
  const botonCancelar = raiz.querySelector('[data-accion="cancelar-sala"]');

  const esAnfitrion = Boolean(yo) && sala?.idAnfitrion === yo;
  let ocupacionActual = {
    actual: Number(sala?.ocupacion ?? 1),
    maximo: Number(sala?.maximoParticipantes ?? 0),
  };

  const pintarOcupacion = () => {
    if (ocupacion) {
      ocupacion.textContent = textoDeOcupacion(ocupacionActual);
    }
  };

  const decir = (texto) => {
    if (aviso) {
      aviso.textContent = texto ?? '';
    }
  };

  const ocultar = () => {
    if (zona) {
      zona.hidden = true;
    }
  };

  // CA-03: en curso o cerrada no se ofrece nada.
  const admiteSalir = !sala?.estado || ['ABIERTA', 'PRIVADA', 'LLENA'].includes(sala.estado);

  if (botonSalir) {
    botonSalir.hidden = esAnfitrion || !admiteSalir;
  }
  if (botonCancelar) {
    botonCancelar.hidden = !esAnfitrion || !admiteSalir;
  }
  if (zona) {
    zona.hidden = !admiteSalir;
  }
  pintarOcupacion();

  const ejecutar = async (boton, accion, motivo) => {
    boton.disabled = true;
    decir('');
    try {
      await accion(sala.id);
      alSalir({ motivo });
    } catch (error) {
      // 409 ya empezo · 403 no eres el anfitrion · 404 no existe. El texto lo
      // redacta el servicio, que es quien sabe el motivo.
      decir(error?.detalle ?? error?.message ?? 'No se pudo completar la operacion.');
      boton.disabled = false;
    }
  };

  botonSalir?.addEventListener('click', () => ejecutar(botonSalir, abandonar, 'abandono'));

  botonCancelar?.addEventListener('click', () => {
    // CA-05: cancelar expulsa a los demas, asi que se pregunta. Salir no.
    if (!confirmar(textoDeConfirmacion({ ocupacion: ocupacionActual.actual }))) {
      return;
    }
    ejecutar(botonCancelar, cancelar, 'cancelada');
  });

  return {
    actualizar(estado) {
      if (estado?.ocupacion) {
        ocupacionActual = { ...estado.ocupacion };
        pintarOcupacion();
      }
    },
    ocultar,
    esAnfitrion,
  };
}
