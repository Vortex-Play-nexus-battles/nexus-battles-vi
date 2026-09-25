/**
 * Mi Cuenta — resumen, perfil, seguridad e historial (#567, #569).
 *
 * **Por qué se reescribió.** `perfil.js` buscaba trece elementos por id
 * (`campo-apodo`, `estado-carga`, `dialogo-apodo`, `btn-reintentar`…) que
 * `perfil.html` no tenía: eran dos versiones distintas de la misma pantalla
 * conviviendo en el repo. El resultado es el defecto #567: la vista quedaba en
 * blanco porque el módulo reventaba en la primera línea al leer `.value` de
 * `null`. Parchear los ids habría dejado el mismo formulario gigante de una
 * sola página; esto lo reorganiza en las cuatro cosas que de verdad hace.
 *
 * **Qué usa de backend, todo existente:**
 *
 * | Zona      | Endpoint                                    | Servicio      |
 * |-----------|---------------------------------------------|---------------|
 * | Perfil    | `GET/PUT /api/v1/perfiles/{uid}`            | ms-identidad  |
 * | Seguridad | `PUT /api/v1/auth/password` (cambiar-password.js) | ms-identidad |
 * | Historial | `GET /api/v1/creditos/{uid}/movimientos`    | ms-finanzas   |
 * | Historial | `GET /api/v1/transacciones/mi-historial`    | ms-finanzas   |
 * | Resumen   | `GET /api/v1/creditos/{uid}/saldo`          | ms-finanzas   |
 * | Resumen   | `GET /api/v1/sanciones/usuarios/{uid}`      | moderación    |
 *
 * Nada de eso se inventa: si el servicio no está en el entorno, la zona lo
 * dice con su nombre y ofrece reintentar.
 */

import { fetchWithHttpErrorInterceptor } from '../comun/interceptors/http-error.interceptor.js';
import { baseDeApi } from '../comun/base-api.js';
import { h, vaciar } from '../comun/ui/dom.js';
import { creditos as formatoCreditos, fechaHora } from '../comun/ui/formato.js';
import { boton, conCarga } from '../comun/ui/boton.js';
import { distintivo } from '../comun/ui/distintivo.js';
import { tarjetaDeCifra } from '../comun/ui/tarjeta.js';
import { limpiarAviso, pintarAviso, tonoPorEstado } from '../comun/ui/aviso.js';
import { marcarErrorDe } from '../comun/ui/campo.js';
import { confirmar } from '../comun/ui/dialogo.js';
import { estadoDeCuenta } from '../comun/ui/sancion.js';
import { vigilarCuentasAtras } from '../comun/ui/cuenta-atras.js';
import { RUTAS, resolver } from '../comun/sesion.js';
import {
  estadoDeCarga,
  estadoDeError,
  estadoVacio,
  pintarEstado,
} from '../comun/ui/estado-vista.js';

const PERFILES = '/api/v1/perfiles';

/**
 * Una lectura que puede no estar disponible en este entorno.
 *
 * @returns {Promise<{ok: true, datos: unknown}|{ok: false, estado: number}>}
 */
async function leer(ruta, fetchImpl) {
  try {
    const respuesta = await fetchImpl(`${baseDeApi()}${ruta}`, {
      method: 'GET',
      headers: { Accept: 'application/json' },
    });
    if (!respuesta.ok) {
      return { ok: false, estado: respuesta.status };
    }
    return { ok: true, datos: await respuesta.json() };
  } catch {
    return { ok: false, estado: 0 };
  }
}

/**
 * Texto del movimiento según su signo. La regla la decide ms-finanzas
 * (`signo` del contrato 1.3.0); aquí solo se traduce a algo legible.
 *
 * @param {{signo: string, monto: number|string}} movimiento
 * @returns {{texto: string, tono: string}}
 */
export function comoImporte({ signo, monto }) {
  const cantidad = formatoCreditos(monto);
  switch (signo) {
    case 'SUMA':
      return { texto: `+${cantidad}`, tono: 'suma' };
    case 'RESTA':
      return { texto: `−${cantidad}`, tono: 'resta' };
    case 'APARTA':
      return { texto: `${cantidad} apartados`, tono: 'aparta' };
    default:
      return { texto: `${cantidad} devueltos`, tono: 'neutro' };
  }
}

/** Nombre de persona para cada concepto que escribe el backend. */
export function nombreDelConcepto(concepto) {
  const conocidos = {
    'apuesta-sala': 'Apuesta de una batalla',
    'recompensa-victoria': 'Recompensa por ganar',
    'recompensa-participacion': 'Recompensa por jugar',
    'inscripcion-torneo': 'Inscripción a un torneo',
    // R17 — el abono con el que empieza toda cuenta nueva (ms-identidad lo
    // pide a finanzas con este concepto al completar el alta).
    'bono-registro': 'Créditos de bienvenida',
    'DEBITO-DIRECTO': 'Cobro',
    'CREDITO-PARTIDA': 'Créditos de una partida',
  };
  return conocidos[concepto] ?? concepto ?? 'Movimiento de créditos';
}

// ---------------------------------------------------------------- resumen

async function pintarResumen(zona, { sesion, fetchImpl, perfil }) {
  vaciar(zona);

  const identidad = h('article', { clase: 'tarjeta pila', datos: { zona: 'identidad' } });
  const cabeza = h('div', { clase: 'tarjeta__cabecera' });
  const textos = h('div', { clase: 'pila pila--ajustada' });
  textos.append(
    h('h3', { clase: 'tarjeta__titulo', texto: perfil?.apodo ?? sesion.apodo ?? 'Sin apodo' }),
    h('p', { clase: 't-meta', texto: perfil?.email ?? '' }),
  );
  cabeza.append(textos);
  if (sesion.rol) {
    cabeza.append(
      h('div', {
        clase: 'tarjeta__distintivos',
        hijos: [distintivo(sesion.rol, sesion.rol.toLowerCase())],
      }),
    );
  }
  identidad.append(cabeza);
  if (perfil?.avatar) {
    identidad.append(
      h('img', {
        clase: 'avatar-vista-previa',
        atributos: { src: perfil.avatar, alt: `Avatar de ${perfil.apodo ?? ''}` },
      }),
    );
  }
  zona.append(identidad);

  // UXC-7 — el estado de la cuenta, con la cuenta atrás si una sanción la
  // restringe. Va antes que el saldo: si no puedes jugar, es lo primero.
  const zonaEstado = h('div', { datos: { zona: 'resumen-estado' } });
  zona.append(zonaEstado);
  pintarEstadoDeLaCuenta(zonaEstado, { sesion, fetchImpl });

  const zonaSaldo = h('div', { datos: { zona: 'resumen-saldo' } });
  zona.append(zonaSaldo);
  pintarEstado(zonaSaldo, estadoDeCarga({ filas: 2 }));

  const saldo = await leer(`/api/v1/creditos/${encodeURIComponent(sesion.uid)}/saldo`, fetchImpl);
  if (!saldo.ok) {
    vaciar(zonaSaldo).append(
      estadoDeError({
        titulo: 'Tus créditos no están disponibles',
        detalle: 'El libro de créditos no responde en este entorno. Vuelve a intentarlo.',
        alReintentar: () => pintarResumen(zona, { sesion, fetchImpl, perfil }),
      }),
    );
    return;
  }
  const rejilla = h('div', { clase: 'home__rejilla' });
  rejilla.append(
    tarjetaDeCifra({
      etiqueta: 'Créditos disponibles',
      valor: formatoCreditos(saldo.datos.saldoDisponible),
    }),
    tarjetaDeCifra({
      etiqueta: 'Apartado en apuestas',
      valor: formatoCreditos(saldo.datos.saldoReservado),
    }),
  );
  vaciar(zonaSaldo).append(rejilla);
}

/**
 * «Estado de tu cuenta» en el resumen (SanctionCountdown).
 *
 * @param {HTMLElement} zona
 * @param {{sesion: object, fetchImpl: Function}} opciones
 */
async function pintarEstadoDeLaCuenta(zona, { sesion, fetchImpl }) {
  pintarEstado(zona, estadoDeCarga({ filas: 1, etiqueta: 'Consultando el estado de tu cuenta…' }));
  const historial = await leer(
    `/api/v1/sanciones/usuarios/${encodeURIComponent(sesion.uid)}`,
    fetchImpl,
  );
  if (!historial.ok) {
    vaciar(zona).append(
      estadoDeError({
        titulo: 'No pudimos consultar el estado de tu cuenta',
        detalle: 'No significa que tengas una sanción: significa que no lo sabemos ahora mismo.',
        alReintentar: () => pintarEstadoDeLaCuenta(zona, { sesion, fetchImpl }),
      }),
    );
    return;
  }
  vaciar(zona).append(
    estadoDeCuenta(Array.isArray(historial.datos) ? historial.datos : [], {
      hrefSanciones: resolver(RUTAS.misSanciones),
      titulo: 'Estado de tu cuenta',
    }),
  );
  vigilarCuentasAtras(zona);
}

// ---------------------------------------------------------------- historial

/**
 * Historial de créditos (#569).
 *
 * Hasta ahora esta pantalla solo listaba pagos en moneda real, así que una
 * partida con apuesta —que sí movió el saldo— no aparecía en ninguna parte.
 */
export async function pintarHistorial(zona, { sesion, fetchImpl }) {
  pintarEstado(zona, estadoDeCarga({ filas: 4 }));

  const respuesta = await leer(
    `/api/v1/creditos/${encodeURIComponent(sesion.uid)}/movimientos?page=0&size=20`,
    fetchImpl,
  );
  if (!respuesta.ok) {
    vaciar(zona).append(
      estadoDeError({
        titulo: 'Tu historial no está disponible',
        detalle: 'El libro de créditos no responde en este entorno. Vuelve a intentarlo.',
        alReintentar: () => pintarHistorial(zona, { sesion, fetchImpl }),
      }),
    );
    return;
  }

  const movimientos = Array.isArray(respuesta.datos?.content) ? respuesta.datos.content : [];
  if (movimientos.length === 0) {
    vaciar(zona).append(
      estadoVacio({
        titulo: 'Todavía no has movido créditos',
        detalle:
          'Aquí aparecen tus apuestas, lo que ganas por jugar y las inscripciones a torneos.',
        accion: {
          texto: 'Jugar una batalla',
          href: '../plataforma/salas-partidas/crear-sala.html',
        },
      }),
    );
    return;
  }

  const tabla = h('table', { clase: 'tabla', datos: { zona: 'movimientos' } });
  const cabecera = h('thead');
  cabecera.append(
    h('tr', {
      hijos: [
        h('th', { texto: 'Concepto' }),
        h('th', { texto: 'Importe' }),
        h('th', { texto: 'Estado' }),
        h('th', { texto: 'Cuándo' }),
      ],
    }),
  );
  const cuerpo = h('tbody');
  for (const movimiento of movimientos) {
    const importe = comoImporte(movimiento);
    cuerpo.append(
      h('tr', {
        datos: { movimiento: movimiento.id, signo: movimiento.signo },
        hijos: [
          h('td', { texto: nombreDelConcepto(movimiento.concepto) }),
          h('td', {
            clase: 'movimiento__importe',
            texto: importe.texto,
            datos: { tono: importe.tono },
          }),
          h('td', { hijos: [distintivo(movimiento.estado, null)] }),
          h('td', { clase: 't-meta', texto: fechaHora(movimiento.creado) }),
        ],
      }),
    );
  }
  tabla.append(cabecera, cuerpo);
  vaciar(zona).append(tabla);
}

// ---------------------------------------------------------------- perfil

/**
 * Rellena el formulario del perfil.
 *
 * @param {HTMLFormElement} formulario
 * @param {object} perfil
 */
export function llenarFormulario(formulario, perfil) {
  for (const [campo, valor] of Object.entries({
    apodo: perfil?.apodo ?? '',
    nombres: perfil?.nombres ?? '',
    apellidos: perfil?.apellidos ?? '',
    preferencias: perfil?.preferencias ?? '',
  })) {
    const control = formulario.elements.namedItem(campo);
    if (control) {
      control.value = valor;
    }
  }
}

/**
 * Cuerpo de la actualización. El apodo **solo viaja si cambió**: la lista
 * negra y la unicidad las valida el backend, y mandarlo igual haría que cada
 * guardado pidiera confirmación sin motivo.
 *
 * @returns {FormData}
 */
export function cuerpoDeActualizacion(formulario, apodoOriginal) {
  const datos = new FormData();
  datos.append('nombres', formulario.elements.nombres.value.trim());
  datos.append('apellidos', formulario.elements.apellidos.value.trim());
  datos.append('preferencias', formulario.elements.preferencias.value.trim());

  const archivo = formulario.elements.avatar?.files?.[0];
  if (archivo) {
    datos.append('avatar', archivo);
  }
  const apodo = formulario.elements.apodo.value.trim();
  if (apodo && apodo !== (apodoOriginal ?? '')) {
    datos.append('apodo', apodo);
  }
  return datos;
}

/**
 * Monta Mi Cuenta sobre una vista ya renderizada.
 *
 * @param {ParentNode} raiz
 * @param {{sesion: object, fetchImpl?: Function, alGuardarApodo?: Function}} opciones
 */
export function montarCuenta(raiz, { sesion, fetchImpl = fetchWithHttpErrorInterceptor }) {
  const zonaAviso = raiz.querySelector('[data-zona="aviso"]');
  const zonaResumen = raiz.querySelector('[data-zona="panel-resumen"] [data-zona="contenido"]');
  const zonaHistorial = raiz.querySelector('[data-zona="panel-historial"] [data-zona="contenido"]');
  const formulario = raiz.querySelector('#formulario-perfil');
  const botonGuardar = raiz.querySelector('[data-accion="guardar-perfil"]');
  const botonDescartar = raiz.querySelector('[data-accion="descartar-perfil"]');
  const vistaAvatar = raiz.querySelector('[data-zona="avatar-previo"]');

  // `cargarPerfil` es el UNICO sitio que escribe aquí. `guardar()` tenía su
  // propia copia de esa publicación (guardar en sesión, rellenar el
  // formulario) y ESLint lo señaló con `require-atomic-updates`: leía `perfil`
  // antes de su `await` y lo reasignaba después, así que dos respuestas
  // podían pisarse. Además la copia estaba incompleta —no refrescaba la
  // vista previa del avatar, de modo que subir una foto no se veía hasta
  // recargar—. Ahora guardar termina releyendo por el camino de siempre.
  let perfil = null;

  async function cargarPerfil() {
    const respuesta = await leer(`${PERFILES}/${encodeURIComponent(sesion.uid)}`, fetchImpl);
    if (!respuesta.ok) {
      pintarAviso(zonaAviso, {
        tono: tonoPorEstado(respuesta.estado),
        titulo: 'No pudimos cargar tu perfil',
        detalle:
          respuesta.estado === 0 || respuesta.estado >= 500
            ? 'El servicio de cuentas no responde ahora mismo.'
            : 'Vuelve a entrar y prueba otra vez.',
        accion: { texto: 'Reintentar', nombre: 'reintentar', alPulsar: () => iniciar() },
      });
      return null;
    }
    perfil = respuesta.datos;
    if (perfil?.apodo) {
      sessionStorage.setItem('nexus.apodoActual', perfil.apodo);
    }
    if (formulario) {
      llenarFormulario(formulario, perfil);
    }
    if (vistaAvatar && perfil?.avatar) {
      vistaAvatar.src = perfil.avatar;
      vistaAvatar.hidden = false;
    }
    return perfil;
  }

  async function guardar() {
    limpiarAviso(zonaAviso);
    marcarErrorDe(formulario.elements.nombres, null);
    marcarErrorDe(formulario.elements.apellidos, null);

    const nombres = formulario.elements.nombres.value.trim();
    const apellidos = formulario.elements.apellidos.value.trim();
    if (!nombres || !apellidos) {
      // El motivo va en el campo, no en un aviso general: así se sabe cuál.
      marcarErrorDe(formulario.elements.nombres, nombres ? null : 'Escribe tus nombres.');
      marcarErrorDe(formulario.elements.apellidos, apellidos ? null : 'Escribe tus apellidos.');
      (nombres ? formulario.elements.apellidos : formulario.elements.nombres).focus();
      return;
    }

    const apodoOriginal = perfil?.apodo ?? '';
    const cuerpo = cuerpoDeActualizacion(formulario, apodoOriginal);

    // El apodo es el nombre con el que te ven en salas, chat y comentarios:
    // cambiarlo no puede pasar por accidente al guardar otra cosa.
    if (cuerpo.has('apodo')) {
      const confirmado = await confirmar({
        titulo: 'Cambiar tu apodo',
        mensaje:
          `Tu apodo pasará de «${apodoOriginal}» a «${cuerpo.get('apodo')}». ` +
          'Es el nombre con el que te ven en las salas, el chat y los comentarios.',
        textoConfirmar: 'Cambiar apodo',
        peligro: false,
      });
      if (!confirmado) {
        return;
      }
    }

    conCarga(botonGuardar, true, 'Guardando…');
    try {
      const respuesta = await fetchImpl(
        `${baseDeApi()}${PERFILES}/${encodeURIComponent(sesion.uid)}`,
        // Sin Content-Type a mano: el navegador arma el multipart con su
        // frontera solo cuando el cuerpo es un FormData.
        { method: 'PUT', body: cuerpo },
      );
      if (!respuesta.ok) {
        let problema = null;
        try {
          problema = await respuesta.json();
        } catch {
          problema = null;
        }
        pintarAviso(zonaAviso, {
          tono: tonoPorEstado(respuesta.status),
          titulo: problema?.title ?? 'No pudimos guardar los cambios',
          detalle: problema?.detail ?? 'Revisa los datos e inténtalo otra vez.',
        });
        return;
      }
      await cargarPerfil();
      pintarAviso(zonaAviso, {
        tono: 'exito',
        titulo: 'Perfil actualizado',
        detalle: 'Tus datos quedaron guardados.',
      });
    } catch {
      pintarAviso(zonaAviso, {
        tono: 'error',
        titulo: 'No pudimos contactar con el servicio',
        detalle: 'Revisa tu conexión e inténtalo de nuevo.',
      });
    } finally {
      conCarga(botonGuardar, false);
    }
  }

  if (formulario) {
    formulario.addEventListener('submit', (evento) => {
      evento.preventDefault();
      guardar();
    });
  }
  if (botonDescartar) {
    botonDescartar.addEventListener('click', () => {
      llenarFormulario(formulario, perfil);
      limpiarAviso(zonaAviso);
    });
  }

  async function iniciar() {
    limpiarAviso(zonaAviso);
    if (zonaResumen) {
      pintarEstado(zonaResumen, estadoDeCarga({ filas: 3 }));
    }
    await cargarPerfil();
    if (zonaResumen) {
      await pintarResumen(zonaResumen, { sesion, fetchImpl, perfil });
    }
    if (zonaHistorial) {
      await pintarHistorial(zonaHistorial, { sesion, fetchImpl });
    }
  }

  iniciar();
  return { recargar: iniciar, perfilActual: () => perfil };
}

/** Botones de la zona de seguridad que no son el formulario de contraseña. */
export function montarAccionesDeSesion(raiz, { sesion, alCerrarSesion }) {
  const zona = raiz.querySelector('[data-zona="sesion"]');
  if (!zona) {
    return;
  }
  vaciar(zona);
  const lista = h('dl', { clase: 'tarjeta__datos' });
  lista.append(
    h('div', {
      clase: 'tarjeta__dato',
      hijos: [
        h('dt', { clase: 't-meta', texto: 'Identificador' }),
        h('dd', { texto: sesion.uid ?? '—' }),
      ],
    }),
    h('div', {
      clase: 'tarjeta__dato',
      hijos: [h('dt', { clase: 't-meta', texto: 'Rol' }), h('dd', { texto: sesion.rol ?? '—' })],
    }),
  );
  zona.append(
    lista,
    boton({
      texto: 'Cerrar sesión en este navegador',
      variante: 'secundario',
      nombre: 'cerrar-sesion',
      alPulsar: alCerrarSesion,
    }),
  );
}
