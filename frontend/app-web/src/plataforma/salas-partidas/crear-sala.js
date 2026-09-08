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
 * Lee el formulario y arma el cuerpo del contrato.
 *
 * Las tres entradas de RF-JUE-001 mas las dos opciones de sus flujos
 * alternativos. Las salas no tienen nombre: ningun requisito lo pide.
 *
 * `tamanoEquipo` solo viaja en la modalidad que admite equipos: mandarlo en un
 * duelo seria un parametro que el servicio va a rechazar (RF-JUE-004).
 */
export function leerFormulario(formulario) {
  const datos = new FormData(formulario);
  const modalidad = datos.get('modalidad');
  const tamano = datos.get('tamanoEquipo');

  return {
    maximoParticipantes: Number(datos.get('maximoParticipantes')),
    modalidad,
    recompensaCreditos: Number(datos.get('recompensaCreditos') || 0),
    incluirHeroeIA: datos.get('incluirHeroeIA') === 'on',
    privada: datos.get('privada') === 'on',
    tamanoEquipo: modalidad === 'HASTA_SEIS' && tamano ? Number(tamano) : null,
  };
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

function pintarAviso(zona, { tono, titulo, detalle }) {
  zona.innerHTML = '';
  const aviso = document.createElement('div');
  aviso.className = `aviso aviso--${tono}`;
  aviso.setAttribute('role', tono === 'error' || tono === 'advertencia' ? 'alert' : 'status');

  const encabezado = document.createElement('p');
  encabezado.className = 'aviso__titulo';
  encabezado.textContent = titulo;
  aviso.appendChild(encabezado);

  if (detalle) {
    const cuerpo = document.createElement('p');
    cuerpo.textContent = detalle;
    aviso.appendChild(cuerpo);
  }

  zona.appendChild(aviso);
  zona.hidden = false;
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
 * @param {{crearSalaImpl?: Function, alCrear?: Function}} [opciones]
 */
export function montarCrearSala(formulario, { crearSalaImpl = crearSala, alCrear } = {}) {
  const zonaAviso = formulario.querySelector('[data-zona="aviso"]');
  const boton = formulario.querySelector('[type="submit"]');

  formulario.addEventListener('submit', async (evento) => {
    evento.preventDefault();

    limpiarErroresDeCampo(formulario);
    zonaAviso.hidden = true;
    zonaAviso.innerHTML = '';
    cargando(boton, true);

    try {
      const sala = await crearSalaImpl(leerFormulario(formulario));

      pintarAviso(zonaAviso, {
        tono: 'exito',
        titulo: 'Sala creada',
        detalle:
          `Tu sala esta abierta y esperando jugadores: ${sala.maximoParticipantes} ` +
          `participantes${sala.recompensaCreditos ? `, ${sala.recompensaCreditos} creditos en juego` : ''}.`,
      });
      formulario.reset();
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
          detalle: 'Revisa tu conexion e intentalo de nuevo.',
        });
      }
    } finally {
      cargando(boton, false);
    }
  });
}
