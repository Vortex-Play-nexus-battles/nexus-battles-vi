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
    return '¿Cancelar la sala? Todavía no ha entrado nadie mas.';
  }
  const gente = otros === 1 ? '1 participante' : `${otros} participantes`;
  return `¿Cancelar la sala? Se expulsará a ${gente}.`;
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
 * La invitacion que el anfitrion puede repartir — FI-R4.
 *
 * `GET /salas/{id}` devuelve `codigoInvitacion` **solo al anfitrion**
 * (`SalaResponse.segunQuienPregunta`), y hasta ahora el frontend no lo miraba
 * en ningun sitio: el codigo se generaba, se guardaba en la columna
 * `codigo_invitacion` y moria ahi. Una sala privada era, por construccion,
 * una sala a la que no podia entrar nadie.
 *
 * El enlace lleva el codigo en la URL para poder pegarlo en un chat de una
 * sola pieza. No es un secreto que haya que proteger del historial del
 * navegador: es una llave de un solo uso practico, para una partida que dura
 * minutos, y pedirle a alguien que copie ocho caracteres a mano desde una
 * captura es como se pierde una sala.
 *
 * @param {{codigoInvitacion?: string|null, id?: string}} sala
 * @param {string} [origen] normalmente `location.origin + location.pathname`
 * @returns {{codigo: string, enlace: string}|null} null si no hay codigo que dar
 */
export function invitacionDe(sala, origen = '') {
  const codigo = typeof sala?.codigoInvitacion === 'string' ? sala.codigoInvitacion.trim() : '';
  if (!codigo || !sala?.id) {
    return null;
  }
  const base = origen ? origen.replace(/\/[^/]*$/, '') : '';
  const enlace = `${base}/batallas.html?sala=${encodeURIComponent(sala.id)}&codigo=${encodeURIComponent(codigo)}`;
  return { codigo, enlace };
}

/**
 * Monta el bloque de invitacion sobre `[data-zona="invitacion"]`.
 *
 * Solo hace algo cuando la sala trae codigo, o sea cuando quien mira es el
 * anfitrion de una sala privada. Al resto ni les aparece la zona: no es que
 * se les oculte un dato, es que el servidor no se lo manda.
 *
 * @param {ParentNode} raiz
 * @param {object} opciones
 * @param {object} opciones.sala tal como la devolvio `GET /salas/{id}`
 * @param {string} [opciones.origen]
 * @param {(texto: string) => Promise<void>} [opciones.copiar] normalmente
 *   `navigator.clipboard.writeText`
 * @returns {boolean} true si se pinto la invitacion
 */
export function montarInvitacion(raiz, { sala, origen = '', copiar } = {}) {
  const zona = raiz.querySelector('[data-zona="invitacion"]');
  if (!zona) {
    return false;
  }

  const invitacion = invitacionDe(sala, origen);
  if (!invitacion) {
    zona.hidden = true;
    return false;
  }

  zona.hidden = false;
  const salidaCodigo = zona.querySelector('[data-zona="codigo-invitacion"]');
  const acuse = zona.querySelector('[data-zona="acuse-copia"]');
  if (salidaCodigo) {
    salidaCodigo.textContent = invitacion.codigo;
  }

  const decir = (texto) => {
    if (acuse) {
      acuse.textContent = texto;
    }
  };

  const copiarTexto = async (texto, etiqueta) => {
    // Sin portapapeles —navegador viejo, contexto no seguro, permiso
    // denegado— el codigo sigue en pantalla y se puede seleccionar a mano. Lo
    // que no puede pasar es que el boton diga «copiado» sin haber copiado.
    if (typeof copiar !== 'function') {
      decir('Tu navegador no deja copiar solo. Selecciona el código y cópialo a mano.');
      return;
    }
    try {
      await copiar(texto);
      decir(`${etiqueta} copiado.`);
    } catch {
      decir('No se pudo copiar. Selecciona el código y cópialo a mano.');
    }
  };

  zona
    .querySelector('[data-accion="copiar-codigo"]')
    ?.addEventListener('click', () => copiarTexto(invitacion.codigo, 'Código'));
  zona
    .querySelector('[data-accion="copiar-enlace"]')
    ?.addEventListener('click', () => copiarTexto(invitacion.enlace, 'Enlace'));

  return true;
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
      decir(error?.detalle ?? error?.message ?? 'No se pudo completar la operación.');
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
