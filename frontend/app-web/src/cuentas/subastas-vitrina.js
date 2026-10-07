/**
 * HU-SUB-011 - Cuadricula del listado de subastas activas.
 *
 * Mismo patron que vitrina.js (inventario): construye DOM a partir de la
 * respuesta del backend, sin logica de fetch aqui (eso es
 * cliente-subastas.js). Bloque BEM propio ("subastas", no "vitrina") para
 * no chocar con las clases del inventario si ambos modulos coinciden en
 * una misma pagina.
 */

import { icono } from '../comun/ui/icono.js';
import { ICONO_DEL_TIPO } from '../contenido/inventario/vitrina.js';
import { nombreLegible } from './nombre-de-producto.js';

/** Etiqueta legible de cada tipo, igual que el inventario (mismo catalogo). */
const NOMBRE_DEL_TIPO = {
  HEROE: 'Héroe',
  HABILIDAD: 'Habilidad',
  ARMA: 'Arma',
  ARMADURA: 'Armadura',
  ITEM: 'Ítem',
  EPICA: 'Épica',
};

/**
 * Clase modificadora BEM para cada rareza oficial (shared/ui-kit/tokens.css).
 * Solo estas 4 existen en el sistema de diseno -- una rareza que no calce
 * aqui se muestra sin insignia de color, en vez de adivinar un color.
 */
const CLASE_POR_RAREZA = {
  Común: 'comun',
  Rara: 'rara',
  Épica: 'epica',
  Legendaria: 'legendaria',
};

const FORMATEADOR_CREDITOS = new Intl.NumberFormat('es-CO');

/**
 * UXC-9 — pone al día una tarjeta ya pintada con lo que llega por el canal
 * del listado (`SubastaActualizada`, contracts/websocket/subastas.yaml): la
 * oferta y el número de pujas; si la subasta terminó, lo dice y retira los
 * botones (pujar o comprar ya no se puede).
 *
 * @param {HTMLElement} tarjeta `.subastas__producto`
 * @param {object} resumen `SubastaResumen` con su `estado`
 */
export function actualizarTarjeta(tarjeta, resumen) {
  if (!tarjeta || !resumen) {
    return;
  }
  if (resumen.estado && resumen.estado !== 'ACTIVA') {
    tarjeta.dataset.estado = resumen.estado;
    const acciones = tarjeta.querySelector('.subastas__acciones');
    const aviso = document.createElement('p');
    aviso.className = 'subastas__terminada';
    aviso.textContent =
      resumen.estado === 'CANCELADA' ? 'Quien la publicó la canceló.' : 'Esta subasta ya terminó.';
    if (acciones) {
      acciones.replaceWith(aviso);
    } else if (!tarjeta.querySelector('.subastas__terminada')) {
      tarjeta.appendChild(aviso);
    }
    return;
  }
  const oferta = Number(resumen.ofertaVigente);
  const precio = tarjeta.querySelector('.subastas__precio');
  if (precio && Number.isFinite(oferta)) {
    precio.textContent = `${FORMATEADOR_CREDITOS.format(oferta)} créditos`;
  }
  const pujas = tarjeta.querySelector('.subastas__pujas');
  if (pujas && Number.isInteger(resumen.cantidadPujas)) {
    pujas.textContent = `${resumen.cantidadPujas} ${resumen.cantidadPujas === 1 ? 'puja' : 'pujas'}`;
  }
}

/**
 * Construye la rejilla de subastas de una pagina.
 *
 * @param {{contenido: Array<object>}} pagina PaginaDeSubastasResponse.
 * @param {{alAbrirDetalle?: Function, alComprarAhora?: Function}} [opciones]
 * @returns {HTMLUListElement}
 */
export function construirVitrinaSubastas(pagina, { alAbrirDetalle, alComprarAhora } = {}) {
  if (!pagina || !Array.isArray(pagina.contenido)) {
    throw new TypeError('La página de subastas debe traer un arreglo "contenido"');
  }

  const vitrina = document.createElement('ul');
  vitrina.className = 'subastas';
  for (const subasta of pagina.contenido) {
    vitrina.appendChild(construirTarjeta(subasta, alAbrirDetalle, alComprarAhora));
  }
  return vitrina;
}

function construirTarjeta(subasta, alAbrirDetalle, alComprarAhora) {
  const tarjeta = document.createElement('li');
  tarjeta.className = 'subastas__producto';
  tarjeta.dataset.subastaId = subasta.id;

  tarjeta.appendChild(construirMiniatura(subasta));

  // PLAYER-07b — sin nombre la tarjeta salía con el título vacío y los botones
  // se anunciaban «Ver la subasta de null»; un nombre que fuera un
  // identificador se pintaba tal cual. Ahora, el mismo respaldo que ya usa la
  // sala de pujas: «Objeto sin nombre» (criterio en `nombre-de-producto.js`).
  const nombreVisible = nombreLegible(subasta.nombreProducto, {
    identificadores: [subasta.id, subasta.productoId],
  });

  const nombre = document.createElement('p');
  nombre.className = 'subastas__nombre';
  nombre.textContent = nombreVisible;

  const meta = document.createElement('p');
  meta.className = 'subastas__meta';
  meta.textContent = NOMBRE_DEL_TIPO[subasta.tipoProducto] ?? subasta.tipoProducto ?? '';

  tarjeta.append(nombre, meta);

  if (subasta.rareza) {
    const claseRareza = CLASE_POR_RAREZA[subasta.rareza];
    if (claseRareza) {
      const insigniaRareza = document.createElement('span');
      insigniaRareza.className = `subastas__rareza subastas__rareza--${claseRareza}`;
      insigniaRareza.textContent = subasta.rareza;
      tarjeta.appendChild(insigniaRareza);
    }
  }

  const precio = document.createElement('p');
  precio.className = 'subastas__precio';
  precio.textContent = `${FORMATEADOR_CREDITOS.format(subasta.ofertaVigente)} créditos`;
  tarjeta.appendChild(precio);

  const info = document.createElement('div');
  info.className = 'subastas__info';

  const contador = document.createElement('span');
  contador.className = 'subastas__contador';
  contador.textContent = formatearTiempoRestante(subasta.fechaFin);
  // El contador se pinta con el valor del momento del renderizado -- no se
  // actualiza solo. Ponerlo al dia en vivo (segundo a segundo, o cuando
  // llegue un SubastaActualizadaEvent por WebSocket) es responsabilidad de
  // quien vuelva a llamar construirVitrinaSubastas, no de esta funcion.
  info.appendChild(contador);

  const pujas = document.createElement('span');
  pujas.className = 'subastas__pujas';
  pujas.textContent = `${subasta.cantidadPujas} ${subasta.cantidadPujas === 1 ? 'puja' : 'pujas'}`;
  info.appendChild(pujas);

  tarjeta.appendChild(info);

  const acciones = document.createElement('div');
  acciones.className = 'subastas__acciones';

  if (typeof alAbrirDetalle === 'function') {
    const botonDetalle = document.createElement('button');
    botonDetalle.className = 'subastas__ver-detalle';
    botonDetalle.type = 'button';
    botonDetalle.textContent = 'Ver subasta';
    botonDetalle.setAttribute('aria-label', `Ver la subasta de ${nombreVisible}`);
    botonDetalle.addEventListener('click', () => alAbrirDetalle(subasta));
    acciones.appendChild(botonDetalle);
  }

  if (
    subasta.precioCompraInmediata !== null &&
    subasta.precioCompraInmediata !== undefined &&
    typeof alComprarAhora === 'function'
  ) {
    const botonComprar = document.createElement('button');
    botonComprar.className = 'subastas__comprar-ahora';
    botonComprar.type = 'button';
    botonComprar.textContent = `Comprar ahora · ${FORMATEADOR_CREDITOS.format(subasta.precioCompraInmediata)}`;
    botonComprar.setAttribute('aria-label', `Comprar ${nombreVisible} ahora`);
    botonComprar.addEventListener('click', () => alComprarAhora(subasta));
    acciones.appendChild(botonComprar);
  }

  if (acciones.childElementCount > 0) {
    tarjeta.appendChild(acciones);
  }

  return tarjeta;
}

/**
 * Miniatura + insignia de Maestro de Juego. La insignia va DENTRO de este
 * contenedor (posicionada como cinta superior por CSS), separada a
 * proposito de subastas__rareza: los colores del sistema de diseno ya
 * estan asignados uno a uno a cada rareza, asi que un badge de MdJ del
 * mismo color competiria visualmente con la insignia de rareza en vez de
 * distinguirse de ella. Se diferencian por forma y posicion, no por color.
 */
function construirMiniatura(subasta) {
  const miniatura = document.createElement('div');
  miniatura.className = 'subastas__miniatura';
  miniatura.dataset.tipo = subasta.tipoProducto ?? '';

  // PLAYER-07b — «no se ven bien las imágenes». La miniatura es la `imagen`
  // del catálogo tal cual, y en DEV hay productos con «espada.png», una ruta
  // que no sirve nadie: el navegador pintaba su icono de imagen rota con el
  // nombre encima, sobre una caja gris. Sin imagen, la caja quedaba vacía.
  // Mismo patrón que la tienda (`tienda-producto.js`, imagen rota): la ranura
  // oscura con el símbolo del tipo, y `data-imagen="rota"` cuando la URL falla.
  const simbolo = () =>
    icono(ICONO_DEL_TIPO[subasta.tipoProducto] ?? 'estrella', {
      clase: 'icono subastas__simbolo',
      etiqueta: null,
    });
  if (subasta.miniaturaUrl) {
    miniatura.dataset.imagen = 'si';
    const imagen = document.createElement('img');
    // El nombre ya se lee debajo, en la tarjeta: repetirlo como texto
    // alternativo lo hacía sonar dos veces. La imagen acompaña, no informa.
    imagen.alt = '';
    imagen.loading = 'lazy';
    imagen.decoding = 'async';
    imagen.addEventListener(
      'error',
      () => {
        miniatura.dataset.imagen = 'rota';
        imagen.replaceWith(simbolo());
      },
      { once: true },
    );
    imagen.src = subasta.miniaturaUrl;
    miniatura.appendChild(imagen);
  } else {
    miniatura.dataset.imagen = 'no';
    miniatura.appendChild(simbolo());
  }

  if (subasta.esMaestroDeJuego) {
    const insignia = document.createElement('span');
    insignia.className = 'subastas__insignia-mdj';
    // Termino completo a proposito: "Master" ya designa otra entidad del
    // juego (enemigos de mision, seccion 7.8.4 del SRS) -- nunca abreviar.
    insignia.textContent = 'Maestro de Juego';
    miniatura.appendChild(insignia);
  }

  return miniatura;
}

/**
 * "42m", "18h", "2d". Subastas ya vencidas (el job de cierre todavia no
 * las proceso) muestran "Finalizada" en vez de un numero negativo.
 */
function formatearTiempoRestante(fechaFinIso) {
  const restanteMs = new Date(fechaFinIso).getTime() - Date.now();
  if (restanteMs <= 0) {
    return 'Finalizada';
  }

  const minutos = Math.floor(restanteMs / 60_000);
  if (minutos < 60) {
    return `${minutos}m`;
  }
  const horas = Math.floor(minutos / 60);
  if (horas < 24) {
    return `${horas}h`;
  }
  const dias = Math.floor(horas / 24);
  return `${dias}d`;
}
