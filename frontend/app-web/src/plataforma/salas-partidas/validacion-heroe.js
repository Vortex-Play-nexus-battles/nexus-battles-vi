/**
 * HU-SAL-003 · RF-JUE-003 — Dialogo de verificacion de heroe.
 *
 * Vista: Pantalla 4 del sistema de diseno (nodo `24:105`), con el conjunto
 * `Dialogo de validacion de heroe` (`22:50`) y sus tres variantes:
 * `Sin heroe`, `Ocupado` y `Disponible`.
 *
 * La estructura es la misma en las tres, tal como esta en Figma: titulo fijo,
 * fila de retrato mas mensaje, un `Aviso` y dos botones. Lo que cambia entre
 * ellas es el texto, el tono del aviso y la accion principal.
 *
 * NO hay selector de heroe. El diseno no lo tiene: el jugador no elige aqui,
 * el sistema le dice el veredicto sobre el heroe que ya trae. Si falta, se va
 * al inventario y vuelve.
 *
 * NO inventa datos. El backend de esta historia esta bloqueado porque el modulo
 * de contenido todavia no publica cual es el heroe activo del jugador, asi que
 * `verificar` se inyecta y cada pieza que dependa de un dato ausente
 * simplemente no se pinta, en vez de rellenarse con un valor de ejemplo.
 */

/** Resultados del esquema `VerificacionHeroe` del contrato OpenAPI. */
export const RESULTADOS = {
  DISPONIBLE: 'DISPONIBLE',
  SIN_HEROE: 'SIN_HEROE_EQUIPADO',
  OCUPADO: 'HEROE_OCUPADO',
};

/**
 * Textos y tono de cada variante, calcados de Figma.
 *
 * Las funciones reciben la verificacion y devuelven el texto; devolver `null`
 * significa «este dato no vino, no pintes esta parte».
 */
const VARIANTES = {
  [RESULTADOS.SIN_HEROE]: {
    titulo: () => 'No tienes un heroe equipado',
    detalle: () => 'Equipa un heroe desde tu inventario antes de entrar a la sala.',
    avisoTono: 'error',
    avisoTitulo: () => 'Tu inventario',
    avisoCuerpo: (v) =>
      typeof v.heroesSinEquipar === 'number'
        ? `Tienes ${v.heroesSinEquipar} heroes sin equipar. Equipa uno y vuelve.`
        : null,
    accion: 'IR AL INVENTARIO',
  },

  [RESULTADOS.OCUPADO]: {
    titulo: () => 'Tu heroe esta en otra partida',
    detalle: (v) =>
      v.heroe?.nombre && v.salaQueLoOcupa
        ? `«${v.heroe.nombre}» esta en la sala «${v.salaQueLoOcupa}». ` +
          'Espera a que termine o elige otro heroe.'
        : null,
    avisoTono: 'advertencia',
    avisoTitulo: () => 'Cuanto falta',
    avisoCuerpo: (v) =>
      typeof v.minutosRestantes === 'number'
        ? `La partida en curso termina en unos ${v.minutosRestantes} minutos.`
        : null,
    accion: 'ELEGIR OTRO HEROE',
  },

  [RESULTADOS.DISPONIBLE]: {
    titulo: (v) => (v.heroe?.nombre ? `${v.heroe.nombre}, listo para combatir` : null),
    detalle: (v) => estadisticasDe(v.heroe),
    avisoTono: 'exito',
    avisoTitulo: () => 'Antes de entrar',
    avisoCuerpo: (v) =>
      typeof v.creditosRequeridos === 'number'
        ? `Se descontaran ${v.creditosRequeridos} creditos de tu saldo al confirmar.`
        : null,
    accion: 'ENTRAR A LA SALA',
  },
};

/**
 * Linea de estadisticas del heroe, con el separador del diseno.
 *
 * Solo escribe las que vengan. Figma muestra las cuatro, pero `Vida` es la
 * unica que el contrato garantiza hoy: `HeroeEnPartida` no tiene `ataque` ni
 * `defensa`. Inventarlas seria mentir sobre el heroe del jugador.
 *
 * @param {object} [heroe]
 * @returns {string|null}
 */
export function estadisticasDe(heroe) {
  if (!heroe) {
    return null;
  }
  const partes = [];
  if (typeof heroe.vidaMaxima === 'number') {
    partes.push(`Vida ${heroe.vidaMaxima}`);
  }
  if (typeof heroe.ataque === 'number') {
    partes.push(`Ataque ${heroe.ataque}`);
  }
  if (typeof heroe.defensa === 'number') {
    partes.push(`Defensa ${heroe.defensa}`);
  }
  if (typeof heroe.nivel === 'number') {
    partes.push(`Nivel ${heroe.nivel}`);
  }
  return partes.length > 0 ? partes.join(' · ') : null;
}

/**
 * Pinta el dialogo dentro de `raiz` para una verificacion dada.
 *
 * @param {HTMLElement} raiz contenedor del dialogo
 * @param {object} verificacion segun el esquema VerificacionHeroe
 * @param {{alCancelar?: Function, alConfirmar?: Function}} [acciones]
 */
export function pintarValidacion(raiz, verificacion, acciones = {}) {
  const doc = raiz.ownerDocument;
  const variante = VARIANTES[verificacion?.resultado];

  raiz.innerHTML = '';
  // `--ancho`: el conjunto «Dialogo de validacion de heroe» mide 520 px con
  // 20 px entre bloques, frente a los 480/16 del `Dialogo` base. Es un
  // modificador porque el dialogo base lo consumen otras vistas.
  raiz.className = 'dialogo dialogo--ancho pila';
  raiz.setAttribute('role', 'dialog');
  raiz.setAttribute('aria-modal', 'true');
  raiz.setAttribute('aria-labelledby', 'titulo-validacion-heroe');

  if (!variante) {
    // Un resultado que el diseno no contempla —CREDITOS_INSUFICIENTES lo esta
    // en el contrato pero no tiene variante— no se inventa: se dice.
    raiz.dataset.resultado = 'DESCONOCIDO';
    raiz.append(
      texto(doc, 'h2', 'dialogo__titulo', 'Verificacion de heroe', 'titulo-validacion-heroe'),
      texto(doc, 'p', 't-cuerpo', 'No se pudo interpretar la respuesta de la verificacion.'),
    );
    return;
  }

  raiz.dataset.resultado = verificacion.resultado;

  // 1 · Titulo del dialogo. Fijo en las tres variantes.
  raiz.append(
    texto(doc, 'h2', 'dialogo__titulo', 'Verificacion de heroe', 'titulo-validacion-heroe'),
  );

  // 2 · Retrato mas mensaje, en fila. El retrato es decorativo: lo que
  //     comunica es el texto, no el circulo.
  const fila = doc.createElement('div');
  fila.className = 'dialogo__encabezado';

  const retrato = doc.createElement('span');
  retrato.className = 'dialogo__icono dialogo__icono--grande';
  retrato.setAttribute('aria-hidden', 'true');

  const mensaje = doc.createElement('div');
  mensaje.className = 'pila pila--ajustada';

  const titulo = variante.titulo(verificacion);
  if (titulo) {
    mensaje.append(texto(doc, 'p', 't-subtitulo', titulo));
  }
  const detalle = variante.detalle(verificacion);
  if (detalle) {
    // `.t-meta` y no `.t-cuerpo`: en Figma la linea de detalle mide 19 px de
    // alto, que son 13 px de fuente, no los 16 del cuerpo. Ademas va en color
    // atenuado, como en el diseno.
    mensaje.append(texto(doc, 'p', 't-meta', detalle));
  }

  fila.append(retrato, mensaje);
  raiz.append(fila);

  // 3 · Aviso. Solo si trae cuerpo: un aviso sin contenido seria un adorno.
  const cuerpoAviso = variante.avisoCuerpo(verificacion);
  if (cuerpoAviso) {
    const aviso = doc.createElement('div');
    aviso.className = `aviso aviso--${variante.avisoTono}`;
    aviso.setAttribute('role', variante.avisoTono === 'error' ? 'alert' : 'status');

    const dentro = doc.createElement('div');
    dentro.append(
      texto(doc, 'p', 'aviso__titulo', variante.avisoTitulo(verificacion)),
      texto(doc, 'p', '', cuerpoAviso),
    );
    aviso.append(dentro);
    raiz.append(aviso);
  }

  // 4 · Acciones. Cancelar nunca es el boton prominente.
  const zonaAcciones = doc.createElement('div');
  zonaAcciones.className = 'dialogo__acciones';

  const cancelar = boton(doc, 'boton boton--secundario', 'Cancelar');
  cancelar.dataset.accion = 'cancelar';
  cancelar.addEventListener('click', () => acciones.alCancelar?.(verificacion));

  const confirmar = boton(doc, 'boton boton--primario boton--grande', variante.accion);
  confirmar.dataset.accion = 'confirmar';
  confirmar.addEventListener('click', () => acciones.alConfirmar?.(verificacion));

  zonaAcciones.append(cancelar, confirmar);
  raiz.append(zonaAcciones);
}

/**
 * Monta el dialogo: pide la verificacion y la pinta.
 *
 * @param {HTMLElement} raiz
 * @param {object} opciones
 * @param {string} opciones.idSala
 * @param {(idSala: string) => Promise<object>} opciones.verificar puerto inyectable
 * @param {Function} [opciones.alCancelar]
 * @param {Function} [opciones.alConfirmar]
 * @returns {Promise<void>}
 */
export async function montarValidacionDeHeroe(
  raiz,
  { idSala, verificar, alCancelar, alConfirmar },
) {
  const doc = raiz.ownerDocument;

  raiz.className = 'dialogo dialogo--ancho pila';
  raiz.dataset.resultado = 'CARGANDO';
  raiz.innerHTML = '';
  raiz.append(
    texto(doc, 'h2', 'dialogo__titulo', 'Verificacion de heroe'),
    texto(doc, 'p', 't-cuerpo', 'Comprobando tu heroe.'),
  );

  try {
    pintarValidacion(raiz, await verificar(idSala), { alCancelar, alConfirmar });
  } catch (error) {
    pintarFalloDeVerificacion(raiz, error, alCancelar);
  }
}

/**
 * Estado de error de la verificacion — RNF-USA-003.
 *
 * Mientras el servicio no implemente la ruta, este es el estado que se ve en
 * la vista real: un 404 explicado, no un dialogo en blanco ni un veredicto
 * inventado.
 */
function pintarFalloDeVerificacion(raiz, error, alCancelar) {
  const doc = raiz.ownerDocument;
  raiz.dataset.resultado = 'ERROR';
  raiz.innerHTML = '';
  raiz.append(
    texto(doc, 'h2', 'dialogo__titulo', 'Verificacion de heroe'),
    avisoDeError(doc, error),
    accionesSoloCancelar(doc, alCancelar),
  );
}

/* -- Ayudas de construccion. Ninguna decide nada. ------------------------- */

function texto(doc, etiqueta, clase, contenido, id) {
  const n = doc.createElement(etiqueta);
  if (clase) {
    n.className = clase;
  }
  if (id) {
    n.id = id;
  }
  n.textContent = contenido;
  return n;
}

function boton(doc, clase, etiqueta) {
  const b = doc.createElement('button');
  b.type = 'button';
  b.className = clase;
  b.textContent = etiqueta;
  return b;
}

function avisoDeError(doc, error) {
  const aviso = doc.createElement('div');
  aviso.className = 'aviso aviso--error';
  aviso.setAttribute('role', 'alert');
  const dentro = doc.createElement('div');
  dentro.append(
    texto(doc, 'p', 'aviso__titulo', error?.titulo ?? 'No se pudo verificar tu heroe'),
    texto(doc, 'p', '', error?.detalle ?? error?.message ?? 'Intentalo de nuevo en un momento.'),
  );
  aviso.append(dentro);
  return aviso;
}

function accionesSoloCancelar(doc, alCancelar) {
  const zona = doc.createElement('div');
  zona.className = 'dialogo__acciones';
  const cancelar = boton(doc, 'boton boton--secundario', 'Cancelar');
  cancelar.dataset.accion = 'cancelar';
  cancelar.addEventListener('click', () => alCancelar?.());
  zona.append(cancelar);
  return zona;
}
