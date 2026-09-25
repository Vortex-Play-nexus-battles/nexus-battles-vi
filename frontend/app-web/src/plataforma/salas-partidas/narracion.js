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

/** Nombre legible de un código de acción (`ATAQUE_BASICO` → «Ataque básico»). */
export function nombreDeAccion(codigo) {
  const texto = String(codigo ?? '').trim();
  if (!texto) {
    return 'una acción';
  }
  if (texto.toUpperCase() === 'ATAQUE_BASICO') {
    return 'Ataque básico';
  }
  return texto;
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
  const accion = categoria ? null : nombreDeAccion(aviso?.accion?.nombre ?? aviso?.accion?.codigo);
  const afectados = Array.isArray(aviso?.afectados) ? aviso.afectados : [];

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

  for (const afectado of afectados) {
    const objetivo = nombreDe(afectado.idJugador, participantes, yo);
    const diferencia = Number.isFinite(afectado.diferencia) ? afectado.diferencia : null;
    const vida =
      Number.isFinite(afectado.vidaActual) && Number.isFinite(afectado.vidaMaxima)
        ? ` (${afectado.vidaActual}/${afectado.vidaMaxima})`
        : '';

    if (diferencia !== null && diferencia > 0) {
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
