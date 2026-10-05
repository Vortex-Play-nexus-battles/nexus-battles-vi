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

import { textoDeError } from '../../comun/ui/texto-de-fallo.js';

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
    return '¿Cancelar la sala? Todavía no ha entrado nadie más.';
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

/** Estados en los que la sala todavía espera gente (contrato de salas-partidas). */
export const ESTADOS_DE_ESPERA = Object.freeze(['ABIERTA', 'PRIVADA', 'LLENA']);

/**
 * ¿La sala sigue esperando gente? — RFINAL-04.
 *
 * Solo entonces tienen sentido «Iniciar combate», el código de invitación,
 * «Salir» y «Cancelar sala». La revisión de AWS DEV del 4-oct vio los tres
 * en una sala ya terminada: la sala de espera se montaba sin mirar el estado.
 * Sin estado (una ficha vieja) se supone que espera, como hasta ahora.
 *
 * @param {{estado?: string}|null|undefined} sala
 * @returns {boolean}
 */
export function salaEnEspera(sala) {
  return !sala?.estado || ESTADOS_DE_ESPERA.includes(sala.estado);
}

/**
 * Lo que se ofrece en una sala que ya no espera a nadie — RFINAL-04.
 *
 * Solo acciones pertinentes y rutas que existen: volver a Jugar online y, si
 * la batalla se jugó, «Ver mi cuenta» (allí está «Tus batallas», con el
 * resultado). Nunca arrancar, invitar ni cancelar.
 *
 * @param {{estado?: string}|null|undefined} sala
 * @param {{batallas: string, cuenta: string}} rutas
 * @returns {{titulo: string, detalle: string,
 *   acciones: Array<{texto: string, href: string, principal: boolean}>}|null}
 *   null si la sala sigue esperando
 */
export function salaCerrada(sala, { batallas, cuenta }) {
  if (salaEnEspera(sala)) {
    return null;
  }
  const volver = { texto: 'Volver a Jugar online', href: batallas, principal: true };
  if (sala.estado === 'CANCELADA') {
    return {
      titulo: 'Esta sala se canceló',
      detalle: 'Aquí ya no se juega. Busca otra sala abierta o crea la tuya.',
      acciones: [volver],
    };
  }
  const miCuenta = { texto: 'Ver mi cuenta', href: cuenta, principal: false };
  if (sala.estado === 'EN_JUEGO') {
    return {
      titulo: 'Esta batalla está en curso',
      detalle: 'Ya no admite a nadie más. Si es tuya, vuelve a ella desde «Tus batallas».',
      acciones: [volver, miCuenta],
    };
  }
  return {
    titulo: 'Esta batalla ya terminó',
    detalle: 'El resultado queda en «Tus batallas», en tu cuenta.',
    acciones: [volver, miCuenta],
  };
}

/**
 * Pinta {@link salaCerrada} en el estado vacío de la vista
 * (`[data-zona="sin-partida"]`): título, detalle y sus acciones, en lugar de
 * «Ver salas abiertas / Crear sala».
 *
 * @param {ParentNode} raiz
 * @param {ReturnType<typeof salaCerrada>} cerrada
 * @returns {boolean} true si pintó algo
 */
export function pintarSalaCerrada(raiz, cerrada) {
  const zona = raiz.querySelector('[data-zona="sin-partida"]');
  if (!zona || !cerrada) {
    return false;
  }
  const doc = zona.ownerDocument;
  const titulo = zona.querySelector('.estado-vista__titulo');
  const detalle = zona.querySelector('.estado-vista__detalle');
  if (titulo) {
    titulo.textContent = cerrada.titulo;
  }
  if (detalle) {
    detalle.textContent = cerrada.detalle;
  }
  let fila = zona.querySelector('.fila');
  if (!fila) {
    fila = doc.createElement('div');
    fila.className = 'fila';
    zona.append(fila);
  }
  fila.replaceChildren(
    ...cerrada.acciones.map((accion) => {
      const enlace = doc.createElement('a');
      enlace.className = `boton ${accion.principal ? 'boton--primario' : 'boton--secundario'}`;
      enlace.href = accion.href;
      enlace.textContent = accion.texto;
      return enlace;
    }),
  );
  zona.dataset.sala = 'cerrada';
  return true;
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
  // RFINAL-04: invitar a una sala que ya no admite gente es invitar a nada.
  if (!codigo || !sala?.id || !salaEnEspera(sala)) {
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

/** Lo que exige el servidor para empezar (`Sala.iniciarPartida`): dos, contando a la IA. */
export const MINIMO_PARA_EMPEZAR = 2;

/** Por que todavia no se puede empezar, dicho como lo dice el servidor. */
export const MOTIVO_SIN_RIVAL =
  'Hace falta al menos un rival para empezar: invita a alguien o crea la sala con héroe de la IA.';

/**
 * Por que el anfitrion no puede empezar todavia, o null si puede.
 *
 * Auditoria de DEV del 30-sep: con 1 de 2 el boton «Iniciar combate» se podia
 * pulsar y solo entonces llegaba el rechazo. La regla es la del servidor (la
 * ocupacion cuenta a la IA) y el servidor sigue siendo quien decide: esto solo
 * evita ofrecer un boton que se sabe que va a rechazar.
 *
 * @param {{actual: number}} ocupacion
 * @returns {string|null}
 */
export function motivoParaNoEmpezar(ocupacion) {
  const actual = Number(ocupacion?.actual ?? 0);
  return actual < MINIMO_PARA_EMPEZAR ? MOTIVO_SIN_RIVAL : null;
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
 * @param {(texto: string) => boolean|Promise<boolean>} [opciones.confirmar] dialogo de
 *   confirmacion (CA-05); puede ser el del kit, que devuelve una promesa
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
  const botonIniciar = raiz.querySelector('[data-accion="iniciar-partida"]');
  const avisoArranque = raiz.querySelector('[data-zona="aviso-arranque"]');

  const esAnfitrion = Boolean(yo) && sala?.idAnfitrion === yo;
  let ocupacionActual = {
    actual: Number(sala?.ocupacion ?? 1),
    maximo: Number(sala?.maximoParticipantes ?? 0),
  };

  const pintarOcupacion = () => {
    if (ocupacion) {
      ocupacion.textContent = textoDeOcupacion(ocupacionActual);
    }
    // El arranque es del anfitrion: se cierra con su motivo mientras falte
    // rival y se abre en cuanto entra alguien (el canal avisa).
    if (botonIniciar && esAnfitrion) {
      const motivo = motivoParaNoEmpezar(ocupacionActual);
      botonIniciar.disabled = motivo !== null;
      if (motivo) {
        botonIniciar.title = motivo;
        botonIniciar.setAttribute('aria-describedby', 'motivo-arranque');
      } else {
        botonIniciar.removeAttribute('title');
        botonIniciar.removeAttribute('aria-describedby');
      }
      if (avisoArranque && (motivo || avisoArranque.dataset.motivo === 'ocupacion')) {
        avisoArranque.id = 'motivo-arranque';
        avisoArranque.textContent = motivo ?? '';
        avisoArranque.dataset.motivo = motivo ? 'ocupacion' : '';
      }
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
  const admiteSalir = salaEnEspera(sala);

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
      decir(textoDeError(error, 'No se pudo completar la operación.'));
      boton.disabled = false;
    }
  };

  botonSalir?.addEventListener('click', () => ejecutar(botonSalir, abandonar, 'abandono'));

  botonCancelar?.addEventListener('click', async () => {
    // CA-05: cancelar expulsa a los demas, asi que se pregunta. Salir no.
    // UXC-9 — con el diálogo del kit (una promesa), no con confirm().
    if (!(await confirmar(textoDeConfirmacion({ ocupacion: ocupacionActual.actual })))) {
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
