/**
 * Qué pasó en cada acción, dicho con palabras — UXC-2 (CombatLog y
 * DamageCallout).
 *
 * `partida.accion.resuelta` (contracts/websocket/salas-partidas.yaml) trae
 * quién actuó, qué acción fue y, por cada afectado, su vida resultante y la
 * `diferencia` (negativa si fue daño, positiva si fue curación). Hasta aquí la
 * vista solo movía las barras: el jugador veía bajar una barra sin saber quién
 * golpeó, cuánto ni con qué resultado.
 *
 * ## La categoría del golpe
 *
 * El motor de combate sortea para cada ataque una categoría de la tabla de
 * 8.000 filas (§6.1.4; `CategoriaEfecto` de motor-combate.yaml): daño,
 * crítico, evade, resiste, escapa o sin efecto. Hoy salas-partidas la reenvía
 * en `accion.nombre` (`EjecutarAccion`: `new Accion(codigo,
 * resolucion.categoria(), null)`), así que se reconoce por su valor: si
 * `nombre` es una de las seis, es la categoría; si no, es el nombre de una
 * acción y se dice tal cual. No se deduce nada de la cifra: una evasión no se
 * adivina por que el daño sea pequeño.
 *
 * Los porcentajes que se muestran son los del documento (§6.1.4, Tablas 21 a 23) y
 * del contrato del motor; no se calcula nada con ellos.
 *
 * @module narracion
 */

/** Las seis categorías del motor, con cómo se dicen. */
export const CATEGORIAS = Object.freeze({
  CAUSAR_DANO: { etiqueta: 'Golpe', tono: 'dano', icono: 'espada', porcentaje: '100 %' },
  CAUSAR_DANO_CRITICO: {
    etiqueta: 'Crítico',
    tono: 'critico',
    icono: 'rayo',
    porcentaje: 'entre 120 % y 180 %',
  },
  EVADIR_EL_GOLPE: { etiqueta: 'Evade', tono: 'mitigado', icono: 'escudo', porcentaje: '80 %' },
  RESISTIR_EL_GOLPE: { etiqueta: 'Resiste', tono: 'mitigado', icono: 'escudo', porcentaje: '60 %' },
  ESCAPAR_AL_GOLPE: { etiqueta: 'Escapa', tono: 'mitigado', icono: 'escudo', porcentaje: '20 %' },
  SIN_EFECTO: { etiqueta: 'Sin efecto', tono: 'fallo', icono: 'cerrar', porcentaje: '0 %' },
});

/**
 * La categoría del golpe, si la acción la trae.
 *
 * @param {{codigo?: string, nombre?: string}|null|undefined} accion
 * @returns {{clave: string, etiqueta: string, tono: string, icono: string, porcentaje: string}|null}
 */
export function categoriaDe(accion) {
  for (const valor of [accion?.nombre, accion?.codigo]) {
    const clave = String(valor ?? '')
      .trim()
      .toUpperCase();
    if (CATEGORIAS[clave]) {
      return { clave, ...CATEGORIAS[clave] };
    }
  }
  return null;
}

/** `codigo` de un efecto que actuó al empezar un turno (canal 1.5.0). */
export const EFECTO_POR_TURNO = 'EFECTO_POR_TURNO';

/**
 * Nombre legible de un código de acción (`ATAQUE_BASICO` → «Ataque básico»).
 *
 * Un código del motor sin nombre conocido (`MANO_DE_PIEDRA`) se dice en
 * minúsculas y con espacios («Mano de piedra»): la auditoría de DEV del 30-sep
 * vio el registro con códigos en mayúsculas. Un nombre ya legible se deja tal
 * cual.
 */
export function nombreDeAccion(codigo) {
  const texto = String(codigo ?? '').trim();
  if (!texto) {
    return 'una acción';
  }
  if (texto.toUpperCase() === 'ATAQUE_BASICO') {
    return 'Ataque básico';
  }
  if (/^[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+$/.test(texto)) {
    const frase = texto.toLowerCase().replaceAll('_', ' ');
    return frase.charAt(0).toUpperCase() + frase.slice(1);
  }
  return texto;
}

/**
 * El nombre de una acción como lo da el estado de combate del héroe que la
 * juega (`heroe.acciones[].nombre`, REST 1.7.0 y canal 1.5.0, calculado por el
 * motor); si no se conoce, el código legible.
 *
 * @param {string} codigo
 * @param {string|null} idEjecutor
 * @param {Array<object>} participantes
 * @returns {string}
 */
export function nombreDeLaAccion(codigo, idEjecutor, participantes) {
  const heroe = (participantes ?? []).find((p) => p.jugador?.id === idEjecutor)?.heroe;
  const acciones = Array.isArray(heroe?.acciones) ? heroe.acciones : [];
  const conocida = acciones.find((a) => a?.codigo === codigo);
  const nombre = typeof conocida?.nombre === 'string' ? conocida.nombre.trim() : '';
  return nombre || nombreDeAccion(codigo);
}

/**
 * Lo que se jugó, con nombre. Una defensa o una sanación llegan con el código
 * también en `nombre`; un efecto por turno, con el nombre del efecto.
 *
 * @param {object} aviso
 * @param {Array<object>} participantes
 * @returns {string}
 */
function nombreDeLoJugado(aviso, participantes) {
  const codigo = aviso?.accion?.codigo;
  const nombre = aviso?.accion?.nombre;
  if (codigo && codigo !== EFECTO_POR_TURNO && (!nombre || nombre === codigo)) {
    return nombreDeLaAccion(codigo, aviso?.idEjecutor, participantes);
  }
  return nombreDeAccion(nombre ?? codigo);
}

/**
 * Nombre con el que se cita a un participante: el de su héroe, y «tú» para
 * quien mira.
 *
 * @param {string} idJugador
 * @param {Array<object>} participantes esquema del panel (`{jugador:{id}, heroe:{nombre}}`)
 * @param {string|null} yo
 * @returns {string}
 */
export function nombreDe(idJugador, participantes, yo) {
  const participante = (participantes ?? []).find((p) => p.jugador?.id === idJugador);
  const nombre = participante?.heroe?.nombre ?? (participante?.esIA ? 'la máquina' : 'un rival');
  return idJugador && idJugador === yo ? `${nombre} (tú)` : nombre;
}

/**
 * Las líneas del registro y los impactos del campo para una acción resuelta.
 *
 * @param {object} aviso mensaje `AccionResuelta`
 * @param {Array<object>} participantes
 * @param {string|null} yo
 * @returns {{lineas: Array<{texto: string, tono: string, icono: string}>,
 *   impactos: Array<{idJugador: string, cifra: string, etiqueta: string|null, tono: string}>}}
 */
export function narrarAccion(aviso, participantes, yo) {
  const lineas = [];
  const impactos = [];
  const ejecutor = nombreDe(aviso?.idEjecutor, participantes, yo);
  const categoria = categoriaDe(aviso?.accion);
  const accion = categoria ? null : nombreDeLoJugado(aviso, participantes);
  const todos = Array.isArray(aviso?.afectados) ? aviso.afectados : [];
  // Desde B7 (canal 1.5.0) el aviso trae tambien al ejecutor aunque su vida no
  // cambie: viaja para llevar su poder, sus cargas y sus efectos. Eso no se
  // narra como si hubiera recibido su propia accion.
  const afectados = todos.filter(
    (a) =>
      a?.idJugador !== aviso?.idEjecutor || (Number.isFinite(a.diferencia) && a.diferencia !== 0),
  );

  // UXC-9 — 1.5.0: faltó poder para la acción pedida y el turno se jugó con
  // el valor base (§6.1.1). Sin decirlo, parecía que el servidor había
  // ignorado la acción elegida.
  if (aviso?.accion?.enValorBase === true) {
    const pedida = aviso.accion.accionPedida;
    lineas.push({
      texto: pedida
        ? `A ${ejecutor} no le alcanza el poder para ${nombreDeLaAccion(pedida, aviso.idEjecutor, participantes)}: ataca con su valor base.`
        : `A ${ejecutor} no le alcanza el poder: ataca con su valor base.`,
      tono: 'sistema',
      icono: 'rayo',
    });
  }

  if (afectados.length === 0 && todos.length > 0 && !categoria) {
    // Una accion que no mueve ninguna vida (una defensa, un apoyo): se dice
    // que se uso; lo que cambia (poder, efectos) ya se ve en su sitio.
    lineas.push({ texto: `${ejecutor} usa ${accion}.`, tono: 'sistema', icono: 'rayo' });
    return { lineas, impactos };
  }

  // Un ataque especial (B7) llega con la categoria del golpe en `nombre` y la
  // accion jugada en `codigo`: se dice cual fue antes de contar el golpe.
  const codigo = aviso?.accion?.codigo;
  if (categoria && codigo && codigo !== 'ATAQUE_BASICO' && !categoriaDe({ codigo })) {
    lineas.push({
      texto: `${ejecutor} usa ${nombreDeLaAccion(codigo, aviso?.idEjecutor, participantes)}.`,
      tono: 'sistema',
      icono: 'rayo',
    });
  }

  if (afectados.length === 0) {
    lineas.push({
      texto: categoria
        ? `${ejecutor}: ${categoria.etiqueta.toLowerCase()}, no alcanza a nadie.`
        : `${ejecutor} usa ${accion}, sin efecto sobre nadie.`,
      tono: 'fallo',
      icono: categoria?.icono ?? 'cerrar',
    });
    return { lineas, impactos };
  }

  // Una sanación con nombre propio se dice una vez antes de contar a quién
  // curó: antes el registro solo decía «se cura: +5» y no qué acción fue
  // (auditoría de DEV del 30-sep: «los especiales no se narran»).
  let curacionNombrada = false;
  for (const afectado of afectados) {
    const objetivo = nombreDe(afectado.idJugador, participantes, yo);
    const diferencia = Number.isFinite(afectado.diferencia) ? afectado.diferencia : null;
    const vida =
      Number.isFinite(afectado.vidaActual) && Number.isFinite(afectado.vidaMaxima)
        ? ` (${afectado.vidaActual}/${afectado.vidaMaxima})`
        : '';

    if (diferencia !== null && diferencia > 0) {
      if (accion && !curacionNombrada) {
        curacionNombrada = true;
        lineas.push({ texto: `${ejecutor} usa ${accion}.`, tono: 'sistema', icono: 'rayo' });
      }
      const aSiMismo = afectado.idJugador === aviso?.idEjecutor;
      lineas.push({
        texto: aSiMismo
          ? `${ejecutor} se cura: +${diferencia} de vida${vida}.`
          : `${ejecutor} cura a ${objetivo}: +${diferencia} de vida${vida}.`,
        tono: 'curacion',
        icono: 'cruz',
      });
      impactos.push({
        idJugador: afectado.idJugador,
        cifra: `+${diferencia}`,
        etiqueta: 'Curación',
        tono: 'curacion',
      });
    } else if (categoria?.clave === 'SIN_EFECTO' || diferencia === 0) {
      lineas.push({
        texto: `El golpe de ${ejecutor} no hace efecto en ${objetivo}${vida}.`,
        tono: 'fallo',
        icono: 'cerrar',
      });
      impactos.push({
        idJugador: afectado.idJugador,
        cifra: '0',
        etiqueta: 'Sin efecto',
        tono: 'fallo',
      });
    } else {
      const cifra = diferencia === null ? null : `${diferencia}`;
      const detalle = cifra === null ? '' : `: ${cifra.replace('-', '−')} de vida${vida}`;
      let texto;
      if (!categoria) {
        texto = `${ejecutor} usa ${accion} sobre ${objetivo}${detalle}.`;
      } else if (categoria.clave === 'CAUSAR_DANO_CRITICO') {
        texto = `¡Crítico! ${ejecutor} golpea a ${objetivo}${detalle}.`;
      } else if (categoria.tono === 'mitigado') {
        texto = `${objetivo} ${categoria.etiqueta.toLowerCase()} el golpe de ${ejecutor} y recibe el ${categoria.porcentaje} del daño${detalle}.`;
      } else {
        texto = `${ejecutor} golpea a ${objetivo}${detalle}.`;
      }
      lineas.push({
        texto,
        tono: categoria?.tono ?? 'dano',
        icono: categoria?.icono ?? 'espada',
      });
      if (cifra !== null) {
        impactos.push({
          idJugador: afectado.idJugador,
          cifra: cifra.replace('-', '−'),
          etiqueta: categoria && categoria.clave !== 'CAUSAR_DANO' ? categoria.etiqueta : null,
          tono: categoria?.tono ?? 'dano',
        });
      }
    }

    if (afectado.vidaActual === 0) {
      lineas.push({ texto: `${objetivo} cae.`, tono: 'caida', icono: 'alerta' });
    }
  }
  return { lineas, impactos };
}

/**
 * La línea del registro para un cambio de turno.
 *
 * @param {{idJugador: string, numeroTurno?: number}} aviso
 * @param {Array<object>} participantes
 * @param {string|null} yo
 * @returns {{texto: string, tono: string, icono: string}}
 */
export function narrarTurno(aviso, participantes, yo) {
  const numero = Number.isInteger(aviso?.numeroTurno) ? `Turno ${aviso.numeroTurno}: ` : '';
  const quien =
    aviso?.idJugador === yo
      ? 'te toca a ti.'
      : `juega ${nombreDe(aviso?.idJugador, participantes, yo)}.`;
  const texto = numero ? `${numero}${quien}` : quien.charAt(0).toUpperCase() + quien.slice(1);
  return { texto, tono: 'turno', icono: 'reloj' };
}
