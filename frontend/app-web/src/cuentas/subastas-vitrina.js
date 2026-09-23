/**
 * HU-SUB-011 - Cuadricula del listado de subastas activas.
 *
 * Mismo patron que vitrina.js (inventario): construye DOM a partir de la
 * respuesta del backend, sin logica de fetch aqui (eso es
 * cliente-subastas.js). Bloque BEM propio ("subastas", no "vitrina") para
 * no chocar con las clases del inventario si ambos modulos coinciden en
 * una misma pagina.
 */

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

  const nombre = document.createElement('p');
  nombre.className = 'subastas__nombre';
  nombre.textContent = subasta.nombreProducto ?? '';

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
    botonDetalle.setAttribute('aria-label', `Ver la subasta de ${subasta.nombreProducto}`);
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
    botonComprar.setAttribute('aria-label', `Comprar ${subasta.nombreProducto} ahora`);
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

  if (subasta.miniaturaUrl) {
    const imagen = document.createElement('img');
    imagen.src = subasta.miniaturaUrl;
    imagen.alt = subasta.nombreProducto ?? '';
    imagen.loading = 'lazy';
    miniatura.appendChild(imagen);
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
