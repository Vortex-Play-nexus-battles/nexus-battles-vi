/**
 * HU-COM-001 — Vista de publicacion de comentarios sobre un producto.
 *
 * Formulario de publicacion con texto, imagenes (con previsualizacion,
 * subtarea SCRUM-1100) y calificacion opcional en estrellas, mas el hilo del
 * producto donde aparece lo que se publica con apodo, estrellas y fecha
 * (RN-CMT-001).
 *
 * Usa solo clases del ui-kit compartido (`campo`, `zona-carga`, `estrellas`,
 * `aviso`, `tarjeta`, `estado-vista`); no define estilos propios.
 *
 * El manejo de errores sigue `shared/ui-kit/MAPEO-ERRORES.md`:
 *
 *   - `errores[]`                       -> cada campo marcado, mensaje debajo
 *   - 422 FORMATO_DE_IMAGEN_NO_ADMITIDO -> la zona de carga en error, con el motivo
 *   - 403 AUTOR_SILENCIADO              -> aviso de advertencia (no es fallo del sistema)
 *   - 409                               -> aviso con salida: reintentar sin calificar
 *   - 202                               -> aviso de informacion: esta en revision, no en el hilo
 *   - 201                               -> aviso de exito y el comentario entra al hilo
 *
 * Se decide por `estado` y `motivo`, nunca comparando textos.
 *
 * Lo que NO hace, porque el contrato no lo define: leer el hilo existente del
 * producto. No hay `GET` en `comentarios.yaml`; el hilo muestra lo publicado
 * en esta sesion y lo dice.
 */

import { publicarComentario, ErrorDeApi, MOTIVO, ESTADO } from './cliente-comentarios.js';

/** Claves de sesion que ya usan las vistas de cuentas (`perfil.js`). */
const CLAVE_USUARIO_ID = 'nexus.usuarioId';
const CLAVE_APODO = 'nexus.apodoActual';

const MAXIMO_ESTRELLAS = 5;

/**
 * Lee la identidad del jugador que las vistas de cuentas dejan en la sesion.
 *
 * @param {Storage} [almacen=sessionStorage]
 * @returns {{usuarioId: string|null, apodo: string|null}}
 */
export function leerSesion(almacen = globalThis.sessionStorage) {
  return {
    usuarioId: almacen?.getItem?.(CLAVE_USUARIO_ID) ?? null,
    apodo: almacen?.getItem?.(CLAVE_APODO) ?? null,
  };
}

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
 * Nombres de los archivos elegidos, leidos de las miniaturas que pinta la
 * zona de carga. Se leen del DOM y no de un `FileList` porque este es
 * inmutable y la persona puede quitar archivos uno a uno.
 *
 * @param {HTMLFormElement} formulario
 * @returns {string[]}
 */
export function nombresDeImagenes(formulario) {
  return Array.from(formulario.querySelectorAll('[data-zona="miniaturas"] [data-nombre]')).map(
    (nodo) => nodo.dataset.nombre,
  );
}

/**
 * Lee el formulario y arma `PublicacionComentarioRequest` del contrato.
 *
 * `estrellas` solo viaja si la persona califico: el contrato dice «omitir si
 * no se quiere calificar», y mandar `null` no es omitir.
 *
 * @param {HTMLFormElement} formulario
 * @param {{usuarioId: string, apodo: string}} sesion
 */
export function leerFormulario(formulario, sesion) {
  const datos = new FormData(formulario);
  const cuerpo = {
    autorId: sesion.usuarioId,
    apodoAutor: sesion.apodo,
    texto: String(datos.get('texto') ?? '').trim(),
    imagenes: nombresDeImagenes(formulario),
  };

  const estrellas = Number(datos.get('estrellas'));
  if (estrellas >= 1 && estrellas <= MAXIMO_ESTRELLAS) {
    cuerpo.estrellas = estrellas;
  }

  return cuerpo;
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

  const zonaCarga = formulario.querySelector('[data-zona="carga"]');
  if (zonaCarga) {
    zonaCarga.classList.remove('zona-carga--error');
    const ayuda = zonaCarga.querySelector('.zona-carga__ayuda');
    if (ayuda && ayuda.dataset.textoReposo !== undefined) {
      ayuda.textContent = ayuda.dataset.textoReposo;
    }
  }
}

/**
 * Marca un campo como invalido y escribe el motivo debajo (mapeo §6).
 *
 * @returns {HTMLElement|null} el control marcado, para llevarle el foco
 */
function marcarCampo(formulario, campo, mensaje) {
  const control = formulario.querySelector(`[name="${campo}"]`);
  if (!control) {
    return null;
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
  return control;
}

function marcarCampos(formulario, errores) {
  let primero = null;
  errores.forEach(({ campo, mensaje }) => {
    const control = marcarCampo(formulario, campo, mensaje);
    if (control && !primero) {
      primero = control;
    }
  });
  return primero;
}

/** La zona de carga dice el motivo escrito, no solo el borde en rojo. */
function marcarZonaDeCarga(formulario, motivo) {
  const zonaCarga = formulario.querySelector('[data-zona="carga"]');
  if (!zonaCarga) {
    return;
  }
  zonaCarga.classList.add('zona-carga--error');
  const ayuda = zonaCarga.querySelector('.zona-carga__ayuda');
  if (ayuda) {
    if (ayuda.dataset.textoReposo === undefined) {
      ayuda.dataset.textoReposo = ayuda.textContent;
    }
    ayuda.textContent = motivo;
  }
}

function pintarAviso(zona, { tono, titulo, detalle, accion }) {
  zona.innerHTML = '';
  const aviso = document.createElement('div');
  aviso.className = `aviso aviso--${tono}`;
  aviso.setAttribute('role', tono === 'error' || tono === 'advertencia' ? 'alert' : 'status');

  const cuerpo = document.createElement('div');
  const encabezado = document.createElement('p');
  encabezado.className = 'aviso__titulo';
  encabezado.textContent = titulo;
  cuerpo.appendChild(encabezado);
  if (detalle) {
    const texto = document.createElement('p');
    texto.textContent = detalle;
    cuerpo.appendChild(texto);
  }
  if (accion) {
    const boton = document.createElement('button');
    boton.type = 'button';
    boton.className = 'boton boton--secundario boton--pequeno';
    boton.dataset.accion = accion.nombre;
    boton.textContent = accion.texto;
    boton.addEventListener('click', accion.alPulsar);
    cuerpo.appendChild(boton);
  }
  aviso.appendChild(cuerpo);
  zona.appendChild(aviso);
  zona.hidden = false;
}

function ocultarAviso(zona) {
  zona.hidden = true;
  zona.innerHTML = '';
}

function cargando(boton, activo) {
  if (boton.dataset.textoReposo === undefined) {
    boton.dataset.textoReposo = boton.textContent;
  }
  boton.disabled = activo;
  boton.setAttribute('aria-busy', String(activo));
  boton.textContent = activo ? 'Publicando…' : boton.dataset.textoReposo;
}

/* ---------------------------------------------------------------------------
   Calificacion en estrellas (componente `estrellas` del ui-kit, editable)
   --------------------------------------------------------------------------- */

/**
 * Pinta las estrellas llenas hasta `valor` y el detalle textual que el ui-kit
 * exige («las estrellas redondean; la precision la da el numero de al lado»).
 *
 * @param {HTMLElement} contenedor elemento `.estrellas`
 * @param {number|null} valor 1..5, o null cuando no hay calificacion
 */
export function pintarEstrellas(contenedor, valor) {
  const estrellas = contenedor.querySelectorAll('.estrellas__estrella');
  estrellas.forEach((estrella, indice) => {
    estrella.classList.toggle('estrellas__estrella--llena', valor !== null && indice < valor);
  });
  const detalle = contenedor.querySelector('.estrellas__detalle');
  if (detalle) {
    detalle.textContent = valor === null ? 'Sin calificar' : `${valor} de ${MAXIMO_ESTRELLAS}`;
  }
}

function calificacionElegida(formulario) {
  const marcada = formulario.querySelector('[name="estrellas"]:checked');
  return marcada ? Number(marcada.value) : null;
}

function quitarCalificacion(formulario) {
  formulario.querySelectorAll('[name="estrellas"]').forEach((entrada) => {
    entrada.checked = false;
  });
  const contenedor = formulario.querySelector('[data-zona="calificacion"]');
  if (contenedor) {
    pintarEstrellas(contenedor, null);
  }
}

function montarCalificacion(formulario) {
  const contenedor = formulario.querySelector('[data-zona="calificacion"]');
  if (!contenedor) {
    return;
  }
  pintarEstrellas(contenedor, calificacionElegida(formulario));
  contenedor.addEventListener('change', () => {
    pintarEstrellas(contenedor, calificacionElegida(formulario));
  });
  const sinCalificar = formulario.querySelector('[data-accion="sin-calificar"]');
  if (sinCalificar) {
    sinCalificar.addEventListener('click', () => quitarCalificacion(formulario));
  }
}

/* ---------------------------------------------------------------------------
   Zona de carga de imagenes con previsualizacion (SCRUM-1100)
   --------------------------------------------------------------------------- */

function pintarMiniatura(lista, archivo, alQuitar) {
  const item = document.createElement('li');
  item.className = 'zona-carga__miniatura';
  item.dataset.nombre = archivo.name;
  item.title = archivo.name;

  // La previsualizacion real solo existe en el navegador; en las pruebas
  // (jsdom) no hay createObjectURL y la miniatura queda como caja con nombre.
  if (typeof URL.createObjectURL === 'function') {
    const imagen = document.createElement('img');
    imagen.src = URL.createObjectURL(archivo);
    imagen.alt = archivo.name;
    imagen.width = 56;
    imagen.height = 56;
    imagen.addEventListener('load', () => URL.revokeObjectURL(imagen.src));
    item.appendChild(imagen);
  }

  const quitar = document.createElement('button');
  quitar.type = 'button';
  quitar.className = 'zona-carga__quitar';
  quitar.setAttribute('aria-label', `Quitar ${archivo.name}`);
  quitar.textContent = '×';
  quitar.addEventListener('click', (evento) => {
    evento.stopPropagation();
    item.remove();
    alQuitar();
  });
  item.appendChild(quitar);
  lista.appendChild(item);
}

function montarZonaDeCarga(formulario) {
  const zonaCarga = formulario.querySelector('[data-zona="carga"]');
  const entrada = formulario.querySelector('[name="imagenes"]');
  const lista = formulario.querySelector('[data-zona="miniaturas"]');
  if (!zonaCarga || !entrada || !lista) {
    return;
  }

  const actualizarEstado = () => {
    zonaCarga.classList.toggle('zona-carga--con-archivos', lista.children.length > 0);
  };

  // La zona es tambien un boton que abre el selector: arrastrar nunca es la unica via.
  zonaCarga.addEventListener('click', (evento) => {
    if (evento.target === entrada || evento.target.closest('.zona-carga__quitar')) {
      return;
    }
    entrada.click();
  });
  zonaCarga.addEventListener('keydown', (evento) => {
    if (evento.key === 'Enter' || evento.key === ' ') {
      evento.preventDefault();
      entrada.click();
    }
  });

  const agregar = (archivos) => {
    const existentes = new Set(nombresDeImagenes(formulario));
    Array.from(archivos).forEach((archivo) => {
      if (!existentes.has(archivo.name)) {
        pintarMiniatura(lista, archivo, actualizarEstado);
        existentes.add(archivo.name);
      }
    });
    actualizarEstado();
  };

  entrada.addEventListener('change', () => {
    agregar(entrada.files ?? []);
    // El mismo archivo debe poder elegirse otra vez despues de quitarlo.
    entrada.value = '';
  });

  zonaCarga.addEventListener('dragover', (evento) => {
    evento.preventDefault();
    zonaCarga.classList.add('zona-carga--arrastrando');
  });
  zonaCarga.addEventListener('dragleave', () => {
    zonaCarga.classList.remove('zona-carga--arrastrando');
  });
  zonaCarga.addEventListener('drop', (evento) => {
    evento.preventDefault();
    zonaCarga.classList.remove('zona-carga--arrastrando');
    agregar(evento.dataTransfer?.files ?? []);
  });
}

function vaciarMiniaturas(formulario) {
  const lista = formulario.querySelector('[data-zona="miniaturas"]');
  if (lista) {
    lista.innerHTML = '';
  }
  const zonaCarga = formulario.querySelector('[data-zona="carga"]');
  if (zonaCarga) {
    zonaCarga.classList.remove('zona-carga--con-archivos');
  }
}

/* ---------------------------------------------------------------------------
   Hilo del producto
   --------------------------------------------------------------------------- */

const formatoDeFecha = new Intl.DateTimeFormat('es-CO', {
  dateStyle: 'medium',
  timeStyle: 'short',
});

/**
 * @param {string} iso fecha ISO 8601 (`fechaPublicacion` del contrato)
 * @returns {string} fecha legible, o el valor original si no se puede leer
 */
export function fechaLegible(iso) {
  const fecha = new Date(iso);
  return Number.isNaN(fecha.getTime()) ? String(iso) : formatoDeFecha.format(fecha);
}

function nodoDeEstrellas(valor) {
  const contenedor = document.createElement('span');
  contenedor.className = 'estrellas';
  contenedor.setAttribute('aria-label', `Calificacion: ${valor} de ${MAXIMO_ESTRELLAS}`);

  const lista = document.createElement('span');
  lista.className = 'estrellas__lista';
  lista.setAttribute('aria-hidden', 'true');
  for (let indice = 0; indice < MAXIMO_ESTRELLAS; indice += 1) {
    const estrella = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    estrella.setAttribute('class', 'estrellas__estrella');
    const uso = document.createElementNS('http://www.w3.org/2000/svg', 'use');
    uso.setAttribute('href', '../../../../../shared/ui-kit/iconos/sprite.svg#estrella');
    estrella.appendChild(uso);
    lista.appendChild(estrella);
  }
  contenedor.appendChild(lista);

  const detalle = document.createElement('span');
  detalle.className = 'estrellas__detalle';
  contenedor.appendChild(detalle);

  pintarEstrellas(contenedor, valor);
  return contenedor;
}

/**
 * Anade un comentario publicado al hilo, con apodo, calificacion y fecha
 * (RN-CMT-001). Un comentario sin `estrellas` es normal, no un error: es el
 * segundo comentario del mismo jugador sobre el producto (CA-02).
 *
 * @param {HTMLElement} zonaHilo elemento con `[data-zona="hilo"]`
 * @param {object} comentario `ComentarioResponse` del contrato
 * @returns {HTMLElement} el articulo pintado
 */
export function agregarAlHilo(zonaHilo, comentario) {
  const vacio = zonaHilo.querySelector('[data-zona="hilo-vacio"]');
  if (vacio) {
    vacio.hidden = true;
  }

  const articulo = document.createElement('article');
  articulo.className = 'tarjeta pila pila--compacta';
  articulo.dataset.comentarioId = comentario.id ?? '';

  const cabecera = document.createElement('div');
  cabecera.className = 'fila';

  const apodo = document.createElement('span');
  apodo.className = 'tarjeta__titulo';
  apodo.dataset.campo = 'apodo';
  apodo.textContent = comentario.apodoAutor ?? '';
  cabecera.appendChild(apodo);

  if (Number.isInteger(comentario.estrellas)) {
    cabecera.appendChild(nodoDeEstrellas(comentario.estrellas));
  }

  const fecha = document.createElement('time');
  fecha.className = 'tarjeta__meta';
  fecha.dataset.campo = 'fecha';
  fecha.dateTime = comentario.fechaPublicacion ?? '';
  fecha.textContent = fechaLegible(comentario.fechaPublicacion);
  cabecera.appendChild(fecha);

  articulo.appendChild(cabecera);

  const texto = document.createElement('p');
  texto.className = 't-cuerpo';
  texto.dataset.campo = 'texto';
  texto.textContent = comentario.texto ?? '';
  articulo.appendChild(texto);

  if (Array.isArray(comentario.imagenes) && comentario.imagenes.length > 0) {
    const imagenes = document.createElement('ul');
    imagenes.className = 'zona-carga__miniaturas';
    comentario.imagenes.forEach((nombre) => {
      const item = document.createElement('li');
      item.className = 'zona-carga__miniatura';
      item.dataset.nombre = nombre;
      item.title = nombre;
      imagenes.appendChild(item);
    });
    articulo.appendChild(imagenes);
  }

  const lista = zonaHilo.querySelector('[data-zona="hilo-lista"]') ?? zonaHilo;
  lista.prepend(articulo);
  return articulo;
}

/* ---------------------------------------------------------------------------
   Montaje
   --------------------------------------------------------------------------- */

/**
 * Conecta el formulario con el servicio.
 *
 * @param {HTMLFormElement} formulario
 * @param {object} [opciones]
 * @param {string} [opciones.productoId] si falta, se lee de `data-producto-id`
 * @param {{usuarioId: string|null, apodo: string|null}} [opciones.sesion]
 * @param {Function} [opciones.publicarImpl] inyeccion para las pruebas
 * @param {HTMLElement} [opciones.hilo] zona del hilo; si falta, se busca en el documento
 * @param {Function} [opciones.alPublicar] callback con el `ComentarioResponse` publicado
 */
export function montarPublicarComentario(
  formulario,
  { productoId, sesion = leerSesion(), publicarImpl = publicarComentario, hilo, alPublicar } = {},
) {
  const zonaAviso = formulario.querySelector('[data-zona="aviso"]');
  const boton = formulario.querySelector('[type="submit"]');
  const zonaHilo = hilo ?? formulario.ownerDocument.querySelector('[data-zona="hilo"]');
  const idProducto = productoId ?? formulario.dataset.productoId;

  montarCalificacion(formulario);
  montarZonaDeCarga(formulario);

  // Sin sesion no hay autor ni apodo que mandar: el contrato los exige.
  if (!sesion?.usuarioId || !sesion?.apodo) {
    formulario.querySelectorAll('input, textarea, button').forEach((control) => {
      control.disabled = true;
    });
    pintarAviso(zonaAviso, {
      tono: 'advertencia',
      titulo: 'Inicia sesion para comentar',
      detalle: 'Tu comentario se publica con tu apodo, y para eso hace falta tu sesion.',
    });
    return;
  }

  formulario.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    limpiarErroresDeCampo(formulario);
    ocultarAviso(zonaAviso);

    const cuerpo = leerFormulario(formulario, sesion);
    if (!cuerpo.texto) {
      const control = marcarCampo(
        formulario,
        'texto',
        'Escribe el comentario antes de publicarlo.',
      );
      control?.focus();
      return;
    }

    cargando(boton, true);
    try {
      const { comentario, estado } = await publicarImpl(idProducto, cuerpo);

      if (estado === ESTADO.EN_REVISION) {
        // Retenido, no rechazado: se guardo y lo vera un moderador. No entra
        // al hilo hasta que se apruebe (CA-03, caso adicional de #34).
        pintarAviso(zonaAviso, {
          tono: 'info',
          titulo: 'Tu comentario esta en revision',
          detalle:
            'El filtro automatico lo senalo. Quedo guardado y un moderador lo revisara antes de publicarlo.',
        });
      } else {
        pintarAviso(zonaAviso, {
          tono: 'exito',
          titulo: 'Comentario publicado',
          detalle: Number.isInteger(comentario.estrellas)
            ? 'Ya aparece en el hilo del producto con tu calificacion.'
            : 'Ya aparece en el hilo del producto. Como ya habias calificado este producto, va sin estrellas.',
        });
        if (zonaHilo) {
          agregarAlHilo(zonaHilo, comentario);
        }
      }

      formulario.reset();
      vaciarMiniaturas(formulario);
      quitarCalificacion(formulario);
      if (alPublicar) {
        alPublicar(comentario, estado);
      }
    } catch (error) {
      if (error instanceof ErrorDeApi && error.esDeFormulario) {
        const primero = marcarCampos(formulario, error.errores);
        primero?.focus();
      } else if (
        error instanceof ErrorDeApi &&
        error.motivo === MOTIVO.FORMATO_DE_IMAGEN_NO_ADMITIDO
      ) {
        // El motivo va escrito en la propia zona de carga (mapeo §5.3 y ui-kit).
        marcarZonaDeCarga(formulario, error.detalle);
        const control = marcarCampo(formulario, 'imagenes', error.detalle);
        control?.focus();
      } else if (error instanceof ErrorDeApi && error.estado === 409) {
        // Conflicto de calificacion simultanea: el contrato dice que reintentar
        // sin estrellas publica el comentario. El aviso lleva esa salida.
        pintarAviso(zonaAviso, {
          tono: tonoPara(error.estado),
          titulo: error.titulo,
          detalle: error.detalle,
          accion: {
            nombre: 'reintentar-sin-calificar',
            texto: 'Publicar sin calificacion',
            alPulsar: () => {
              quitarCalificacion(formulario);
              formulario.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
            },
          },
        });
      } else if (error instanceof ErrorDeApi) {
        // Incluye 403 AUTOR_SILENCIADO: es una advertencia, no un fallo del sistema.
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
