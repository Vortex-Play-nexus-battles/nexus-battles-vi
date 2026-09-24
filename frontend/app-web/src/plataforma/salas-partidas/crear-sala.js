/**
 * HU-SAL-001 — Vista de creacion de sala de batalla.
 *
 * Corresponde a «Pantalla 1 · Crear sala de batalla» del sistema de diseno.
 * Usa las clases del ui-kit compartido; no define estilos propios.
 *
 * El manejo de errores sigue `shared/ui-kit/MAPEO-ERRORES.md` al pie de la letra:
 *
 *   - `errores[]`  -> se marca cada campo y el mensaje va debajo, NO en un aviso
 *   - resto        -> un aviso, con el tono que decide el codigo HTTP
 *   - se decide por `type` y `status`, nunca comparando textos
 */

import { crearSala, ErrorDeApi } from './cliente-salas.js';
import { vaciar } from '../../comun/ui/dom.js';
import { pintarAviso } from '../../comun/ui/aviso.js';
import {
  esSeccionDegradada,
  pintarSeccionDegradada,
  limpiarSeccionDegradada,
} from '../../comun/degradacion/aviso-degradacion.js';

/** Codigo HTTP -> variante del componente Aviso (tabla 4 del mapeo). */
export function tonoPara(estado) {
  if (estado >= 500) {
    return 'error';
  }
  if (estado === 404) {
    return 'info';
  }
  return 'advertencia';
}

/**
 * Limites por modalidad — RF-JUE-004, HU-SAL-004 (SCRUM-1080).
 *
 * Copia de la tabla de `Modalidad` en `contracts/openapi/salas-partidas.yaml`
 * (1.2.0). El servicio es quien manda: aqui solo sirven para que el
 * formulario no ofrezca lo que el servicio va a rechazar (un 1 contra 1 con
 * cuatro cupos, una maquina en un duelo, equipos fuera de hasta seis).
 *
 * @param {string} modalidad
 * @returns {{participantes: {min: number, max: number}, heroesIA: {min: number, max: number} | null, equipos: boolean}}
 */
export function limitesDe(modalidad) {
  switch (modalidad) {
    case 'UNO_CONTRA_UNO':
      return { participantes: { min: 2, max: 2 }, heroesIA: null, equipos: false };
    case 'CONTRA_IA':
      return { participantes: { min: 2, max: 2 }, heroesIA: { min: 1, max: 1 }, equipos: false };
    default:
      return { participantes: { min: 2, max: 6 }, heroesIA: { min: 0, max: 5 }, equipos: true };
  }
}

/** Cuantas maquinas caben con ese aforo: el anfitrion siempre juega. */
export function maximoDeMaquinas(modalidad, maximoParticipantes) {
  const limites = limitesDe(modalidad).heroesIA;
  if (!limites) {
    return 0;
  }
  return Math.max(limites.min, Math.min(limites.max, maximoParticipantes - 1));
}

/**
 * Lee el formulario y arma el cuerpo del contrato.
 *
 * Las tres entradas de RF-JUE-001 mas las opciones de sus flujos alternativos
 * y de RF-JUE-004. Las salas no tienen nombre: ningun requisito lo pide.
 *
 * `tamanoEquipo` y `heroesIA` solo viajan en la modalidad que los admite:
 * mandarlos en un duelo seria un parametro que el servicio va a rechazar.
 * Contra la IA la maquina va siempre (`heroesIA: 1`); `incluirHeroeIA` se
 * sigue mandando por el contrato anterior y vale «al menos una».
 */
export function leerFormulario(formulario) {
  const datos = new FormData(formulario);
  const modalidad = datos.get('modalidad');
  const tamano = datos.get('tamanoEquipo');
  const limites = limitesDe(modalidad);

  let heroesIA = 0;
  if (modalidad === 'CONTRA_IA') {
    heroesIA = 1;
  } else if (limites.heroesIA) {
    // Numero nuevo, o la casilla de RF-JUE-001 si la vista todavia la usa.
    const pedidas = datos.get('heroesIA');
    if (pedidas !== null) {
      heroesIA = Number(pedidas || 0);
    } else if (datos.get('incluirHeroeIA') === 'on') {
      heroesIA = 1;
    }
  }

  const cuerpo = {
    maximoParticipantes: Number(datos.get('maximoParticipantes')),
    modalidad,
    recompensaCreditos: Number(datos.get('recompensaCreditos') || 0),
    incluirHeroeIA: heroesIA > 0,
    heroesIA,
    privada: datos.get('privada') === 'on',
    tamanoEquipo: limites.equipos && tamano ? Number(tamano) : null,
  };
  // HU-TOR-004 (contrato 1.4.0): la sala es un encuentro de torneo solo si la
  // vista lo trae prefijado; el jugador no lo escribe a mano.
  const torneoId = datos.get('torneoId');
  const numeroEncuentro = datos.get('numeroEncuentro');
  if (torneoId && numeroEncuentro) {
    cuerpo.torneo = { torneoId, numeroEncuentro: Number(numeroEncuentro) };
  }
  return cuerpo;
}

/**
 * El encuentro de torneo que viene en la URL (`?torneo=<id>&encuentro=<n>`),
 * puesto por la vista de torneos — HU-TOR-004 CA-04.
 *
 * @param {string} busqueda `location.search`
 * @returns {{torneoId: string, numeroEncuentro: number}|null}
 */
export function encuentroDesde(busqueda) {
  const parametros = new URLSearchParams(busqueda);
  const torneoId = parametros.get('torneo');
  const numero = Number(parametros.get('encuentro'));
  if (!torneoId || !Number.isInteger(numero) || numero < 1 || numero > 14) {
    return null;
  }
  return { torneoId, numeroEncuentro: numero };
}

/**
 * Deja el formulario listo para jugar un encuentro de torneo: campos ocultos
 * con el vinculo, nota visible y, como los encuentros son de equipos de dos
 * (D-22), sugiere hasta seis con cuatro jugadores en equipos de 2. Todo queda
 * editable: el servicio es quien valida.
 */
export function prefijarEncuentro(formulario, encuentro) {
  if (!encuentro) {
    return;
  }
  for (const [nombre, valor] of [
    ['torneoId', encuentro.torneoId],
    ['numeroEncuentro', String(encuentro.numeroEncuentro)],
  ]) {
    let oculto = formulario.querySelector(`input[name="${nombre}"]`);
    if (!oculto) {
      oculto = document.createElement('input');
      oculto.type = 'hidden';
      oculto.name = nombre;
      formulario.prepend(oculto);
    }
    oculto.value = valor;
  }
  const nota = formulario.querySelector('[data-zona="nota-torneo"]');
  if (nota) {
    nota.hidden = false;
    nota.textContent =
      `Esta sala es el encuentro ${encuentro.numeroEncuentro} del torneo. ` +
      'Al terminar la partida, el resultado se informa al torneo automaticamente.';
  }
  const hastaSeis = formulario.querySelector('[name="modalidad"][value="HASTA_SEIS"]');
  if (hastaSeis) {
    hastaSeis.checked = true;
  }
  const participantes = formulario.querySelector('[name="maximoParticipantes"]');
  if (participantes) {
    participantes.value = '4';
  }
  const tamano = formulario.querySelector('[name="tamanoEquipo"]');
  if (tamano) {
    tamano.value = '2';
  }
  ajustarPorModalidad(formulario);
}

/**
 * Acomoda el formulario a la modalidad elegida — SCRUM-1080.
 *
 * Cambia los limites (y recorta el valor) del numero de participantes, muestra
 * u oculta las opciones que solo tienen sentido en hasta seis (maquinas y
 * equipos) y la nota de contra la IA. No decide nada que el servicio no
 * decida ya: solo evita ofrecer combinaciones que van a volver con un 400.
 *
 * @param {HTMLFormElement} formulario
 * @returns {{modalidad: string, participantes: {min: number, max: number}}}
 */
export function ajustarPorModalidad(formulario) {
  const modalidad = new FormData(formulario).get('modalidad') ?? 'UNO_CONTRA_UNO';
  const limites = limitesDe(modalidad);
  const participantes = formulario.querySelector('[name="maximoParticipantes"]');
  const maquinas = formulario.querySelector('[name="heroesIA"]');
  const zonaSeis = formulario.querySelector('[data-zona="opciones-hasta-seis"]');
  const notaIa = formulario.querySelector('[data-zona="nota-contra-ia"]');
  const pista = formulario.querySelector('[data-zona="pista-participantes"]');

  if (participantes) {
    participantes.min = String(limites.participantes.min);
    participantes.max = String(limites.participantes.max);
    const actual = Number(participantes.value) || limites.participantes.min;
    participantes.value = String(
      Math.max(limites.participantes.min, Math.min(limites.participantes.max, actual)),
    );
    // Con un unico valor posible no hay nada que elegir.
    participantes.readOnly = limites.participantes.min === limites.participantes.max;
  }
  if (pista) {
    pista.textContent =
      limites.participantes.min === limites.participantes.max
        ? `Exactamente ${limites.participantes.min} jugadores.`
        : `Entre ${limites.participantes.min} y ${limites.participantes.max} jugadores.`;
  }
  if (maquinas && participantes) {
    const tope = maximoDeMaquinas(modalidad, Number(participantes.value));
    maquinas.max = String(tope);
    if (Number(maquinas.value) > tope) {
      maquinas.value = String(tope);
    }
  }
  if (zonaSeis) {
    zonaSeis.hidden = !limites.equipos;
  }
  if (notaIa) {
    notaIa.hidden = modalidad !== 'CONTRA_IA';
  }

  return { modalidad, participantes: limites.participantes };
}

function limpiarErroresDeCampo(formulario) {
  formulario.querySelectorAll('.campo--invalido').forEach((campo) => {
    campo.classList.remove('campo--invalido');
    const control = campo.querySelector('.campo__control');
    if (control) {
      control.removeAttribute('aria-invalid');
      control.removeAttribute('aria-describedby');
    }
  });
  formulario.querySelectorAll('.campo__error').forEach((mensaje) => mensaje.remove());
}

/**
 * Marca los campos que el servicio rechazo y devuelve el primero, para llevarle
 * el foco. Sin foco, en un formulario largo la persona no ve donde fallo.
 */
function marcarCampos(formulario, errores) {
  let primero = null;

  errores.forEach(({ campo, mensaje }) => {
    const control = formulario.querySelector(`[name="${campo}"]`);
    if (!control) {
      return;
    }

    const contenedor = control.closest('.campo') ?? control.parentElement;
    contenedor.classList.add('campo--invalido');

    const idMensaje = `error-${campo}`;
    const aviso = document.createElement('p');
    aviso.className = 'campo__error';
    aviso.id = idMensaje;
    aviso.textContent = mensaje;
    contenedor.appendChild(aviso);

    control.setAttribute('aria-invalid', 'true');
    control.setAttribute('aria-describedby', idMensaje);

    if (!primero) {
      primero = control;
    }
  });

  return primero;
}

/**
 * Bloquea el boton mientras se espera y lo restaura tal cual estaba.
 *
 * El literal en reposo lo pone la vista («CREAR SALA», como en Figma), no
 * este modulo: se guarda la primera vez y se devuelve intacto, en vez de
 * imponer un texto que puede no coincidir con el HTML.
 */
function cargando(boton, activo) {
  if (boton.dataset.textoReposo === undefined) {
    boton.dataset.textoReposo = boton.textContent;
  }
  boton.disabled = activo;
  boton.setAttribute('aria-busy', String(activo));
  boton.textContent = activo ? 'Creando la sala…' : boton.dataset.textoReposo;
}

/**
 * Conecta el formulario con el servicio.
 *
 * @param {HTMLFormElement} formulario
 * @param {{crearSalaImpl?: Function, alCrear?: Function, encuentro?: {torneoId: string, numeroEncuentro: number}|null}} [opciones]
 *   `encuentro`: HU-TOR-004, la sala juega ese encuentro de torneo (ver `encuentroDesde`)
 */
export function montarCrearSala(
  formulario,
  { crearSalaImpl = crearSala, alCrear, encuentro = null } = {},
) {
  const zonaAviso = formulario.querySelector('[data-zona="aviso"]');
  // HU-DIS-003: el hueco donde se pinta `Seccion degradada` cuando el
  // inventario (o el libro de creditos) no responde. Distinto del aviso:
  // MAPEO-ERRORES §5.5 dice que no es un fallo de la accion sino una
  // seccion limitada, con reintentar y sin tapar el resto del formulario.
  const zonaDegradacion = formulario.querySelector('[data-zona="degradacion"]');
  const boton = formulario.querySelector('[type="submit"]');

  // RF-JUE-004: la modalidad manda sobre el resto del formulario, desde el
  // primer pintado y cada vez que cambia ella o el aforo.
  ajustarPorModalidad(formulario);
  prefijarEncuentro(formulario, encuentro);
  formulario.addEventListener('change', (evento) => {
    const nombre = evento.target?.name;
    if (nombre === 'modalidad' || nombre === 'maximoParticipantes') {
      ajustarPorModalidad(formulario);
    }
  });

  async function enviar() {
    limpiarErroresDeCampo(formulario);
    zonaAviso.hidden = true;
    vaciar(zonaAviso);
    limpiarSeccionDegradada(zonaDegradacion);
    cargando(boton, true);

    try {
      const sala = await crearSalaImpl(leerFormulario(formulario));

      pintarAviso(zonaAviso, {
        tono: 'exito',
        titulo: 'Sala creada',
        detalle:
          `Tu sala está abierta y esperando jugadores: ${sala.maximoParticipantes} ` +
          `participantes${sala.recompensaCreditos ? `, ${sala.recompensaCreditos} creditos en juego` : ''}.`,
      });
      formulario.reset();
      prefijarEncuentro(formulario, encuentro);
      if (alCrear) {
        alCrear(sala);
      }
    } catch (error) {
      if (error instanceof ErrorDeApi && error.esDeFormulario) {
        // El requisito exige senalar el motivo: se marca cada campo, no un
        // aviso general que obligue a adivinar cual esta mal.
        const primero = marcarCampos(formulario, error.errores);
        if (primero) {
          primero.focus();
        }
      } else if (
        error instanceof ErrorDeApi &&
        zonaDegradacion &&
        esSeccionDegradada(error.problema)
      ) {
        // Un servicio del que depende crear la sala no responde. Se dice
        // cual, que el resto sigue, y se deja reintentar sin volver a
        // rellenar nada: el formulario queda tal cual.
        pintarSeccionDegradada(zonaDegradacion, error.problema, { alReintentar: enviar });
      } else if (error instanceof ErrorDeApi) {
        pintarAviso(zonaAviso, {
          tono: tonoPara(error.estado),
          titulo: error.titulo,
          detalle: error.detalle,
        });
      } else {
        pintarAviso(zonaAviso, {
          tono: 'error',
          titulo: 'No pudimos contactar con el servicio',
          detalle: 'Revisa tu conexión e inténtalo de nuevo.',
        });
      }
    } finally {
      cargando(boton, false);
    }
  }

  formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    enviar();
  });
}
