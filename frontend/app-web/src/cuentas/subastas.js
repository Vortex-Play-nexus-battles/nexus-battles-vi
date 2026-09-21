/**
 * HU-SUB-011 - Controlador de la pagina de subastas.
 *
 * Ensambla la barra de navegacion compartida, el buscador con
 * autocompletado, el orden, los filtros y la vitrina -- coordinando los
 * modulos que ya existen por separado (cliente-subastas, subastas-vitrina,
 * subastas-filtros). Mismo patron que login.js junto a login.html.
 *
 * PENDIENTE, a proposito fuera de este archivo: el contador en vivo real
 * (suscripcion STOMP al canal /topic/subastas/listado) todavia no esta
 * conectado aqui. Este archivo carga y refresca solo por accion del
 * usuario (filtrar, ordenar, paginar, buscar) -- la actualizacion push en
 * tiempo real queda como el siguiente incremento, no silenciada.
 */

import { montarCabecera } from '../comun/cabecera-app.js';
import { listarSubastas, sugerirSubastas } from './cliente-subastas.js';
import { construirVitrinaSubastas } from './subastas-vitrina.js';
import { construirFiltros } from './subastas-filtros.js';

const TAMANO_PAGINA = 16;
const ESPERA_DEBOUNCE_MS = 300;

const estado = {
  pagina: 0,
  filtros: {},
  ordenarPor: 'FECHA_PUBLICACION',
};

document.addEventListener('DOMContentLoaded', inicializar);

function inicializar() {
  const raiz = document.getElementById('raiz-subastas');
  if (!raiz) {
    throw new Error('subastas.html debe traer un elemento con id="raiz-subastas"');
  }

  // Cabecera unica de la aplicacion (HU-UX-001). Subasta es publica: un
  // visitante ve el listado; pujar exige sesion.
  // Va fuera de la raiz (a todo el ancho, como en las demas vistas); si la
  // pagina no trae el contenedor, se crea dentro para no quedarse sin barra.
  let cabecera = document.querySelector('[data-cabecera-app]');
  if (!cabecera) {
    cabecera = document.createElement('div');
    cabecera.dataset.cabeceraApp = '';
    raiz.appendChild(cabecera);
  }
  montarCabecera(cabecera, { seccionActiva: 'subasta' });

  const titulo = document.createElement('h1');
  titulo.className = 'subastas-titulo';
  titulo.textContent = 'Subastas activas';
  raiz.appendChild(titulo);

  raiz.appendChild(construirBarraBusqueda());
  raiz.appendChild(construirBarraOrden());

  const contenido = document.createElement('div');
  contenido.className = 'subastas-contenido';

  const filtros = construirFiltros({
    alCambiar: (nuevosFiltros) => {
      // El texto de busqueda vive aparte (construirBarraBusqueda), asi
      // que se preserva aqui en vez de dejar que el panel de filtros lo
      // borre al no conocerlo.
      estado.filtros = { ...nuevosFiltros, q: estado.filtros.q };
      estado.pagina = 0;
      cargarYRenderizar();
    },
  });

  const zonaResultados = document.createElement('div');
  zonaResultados.id = 'subastas-resultados';

  contenido.append(filtros, zonaResultados);
  raiz.appendChild(contenido);

  cargarYRenderizar();
}

/** Buscador con autocompletado, independiente del de la barra compartida. */
function construirBarraBusqueda() {
  const contenedor = document.createElement('div');
  contenedor.className = 'subastas-busqueda';

  const campo = document.createElement('input');
  campo.type = 'search';
  campo.className = 'subastas-busqueda__campo';
  campo.placeholder = 'Buscar por nombre, tipo o habilidad...';
  campo.setAttribute('aria-label', 'Buscar subastas');
  campo.setAttribute('aria-autocomplete', 'list');

  const listaSugerencias = document.createElement('ul');
  listaSugerencias.className = 'subastas-busqueda__sugerencias';
  listaSugerencias.hidden = true;

  let idPeticionVigente = 0;

  const buscar = debounce(async (texto) => {
    if (texto.trim() === '') {
      ocultarSugerencias();
      estado.filtros = { ...estado.filtros, q: undefined };
      estado.pagina = 0;
      cargarYRenderizar();
      return;
    }

    const idPeticion = ++idPeticionVigente;
    let sugerencias;
    try {
      sugerencias = await sugerirSubastas(texto);
    } catch {
      // Un fallo en las sugerencias no debe romper la barra de busqueda:
      // el usuario igual puede presionar Enter para buscar el texto tal
      // cual, solo se queda sin lista de autocompletado.
      sugerencias = [];
    }
    if (idPeticion !== idPeticionVigente) {
      return; // Llego una respuesta vieja despues de una tecla mas reciente.
    }
    mostrarSugerencias(sugerencias);
  }, ESPERA_DEBOUNCE_MS);

  campo.addEventListener('input', () => buscar(campo.value));

  campo.addEventListener('keydown', (evento) => {
    if (evento.key === 'Enter') {
      evento.preventDefault();
      confirmarBusqueda(campo.value);
    }
    if (evento.key === 'Escape') {
      ocultarSugerencias();
    }
  });

  function mostrarSugerencias(sugerencias) {
    listaSugerencias.replaceChildren();
    if (sugerencias.length === 0) {
      ocultarSugerencias();
      return;
    }
    for (const sugerencia of sugerencias) {
      const item = document.createElement('li');
      const boton = document.createElement('button');
      boton.type = 'button';
      boton.className = 'subastas-busqueda__sugerencia';
      boton.textContent = sugerencia;
      boton.addEventListener('click', () => {
        campo.value = sugerencia;
        confirmarBusqueda(sugerencia);
      });
      item.appendChild(boton);
      listaSugerencias.appendChild(item);
    }
    listaSugerencias.hidden = false;
  }

  function ocultarSugerencias() {
    listaSugerencias.hidden = true;
    listaSugerencias.replaceChildren();
  }

  function confirmarBusqueda(texto) {
    ocultarSugerencias();
    estado.filtros = { ...estado.filtros, q: texto.trim() || undefined };
    estado.pagina = 0;
    cargarYRenderizar();
  }

  contenedor.append(campo, listaSugerencias);
  return contenedor;
}

function construirBarraOrden() {
  const contenedor = document.createElement('div');
  contenedor.className = 'subastas-orden';

  const control = document.createElement('select');
  control.className = 'subastas-orden__control';
  control.setAttribute('aria-label', 'Ordenar subastas por');

  const opciones = [
    { valor: 'FECHA_PUBLICACION', etiqueta: 'Más recientes' },
    { valor: 'PRECIO_ASC', etiqueta: 'Precio: menor a mayor' },
    { valor: 'PRECIO_DESC', etiqueta: 'Precio: mayor a menor' },
    { valor: 'TIEMPO_RESTANTE', etiqueta: 'Termina pronto' },
    { valor: 'PUJAS', etiqueta: 'Más pujas' },
    { valor: 'POPULARIDAD', etiqueta: 'Más popular' },
  ];
  for (const opcion of opciones) {
    const elemento = document.createElement('option');
    elemento.value = opcion.valor;
    elemento.textContent = opcion.etiqueta;
    control.appendChild(elemento);
  }

  control.addEventListener('change', () => {
    estado.ordenarPor = control.value;
    estado.pagina = 0;
    cargarYRenderizar();
  });

  contenedor.appendChild(control);
  return contenedor;
}

async function cargarYRenderizar() {
  const zona = document.getElementById('subastas-resultados');
  if (!zona) {
    return;
  }

  zona.replaceChildren(construirEstado('carga', 'Cargando subastas...'));

  let pagina;
  try {
    pagina = await listarSubastas(
      { ...estado.filtros, ordenarPor: estado.ordenarPor },
      estado.pagina,
      TAMANO_PAGINA,
    );
  } catch (error) {
    zona.replaceChildren(
      construirEstado('error', 'No se pudieron cargar las subastas', error.message),
    );
    return;
  }

  if (pagina.contenido.length === 0) {
    zona.replaceChildren(construirEstado('vacio', 'No hay subastas que coincidan con tu búsqueda'));
    return;
  }

  const vitrina = construirVitrinaSubastas(pagina, {
    alAbrirDetalle: (subasta) => {
      // La vista de detalle todavia no existe (ver pendientes de HU-SUB-011).
      globalThis.location.href = `./pujas.html?id=${subasta.id}`;
    },
  });

  zona.replaceChildren(vitrina, construirPaginacion(pagina));
}

function construirEstado(tipo, mensaje, detalle) {
  const contenedor = document.createElement('div');
  contenedor.className = `estado estado-${tipo}`;

  const titulo = document.createElement('p');
  titulo.className = 'estado__mensaje';
  titulo.textContent = mensaje;
  contenedor.appendChild(titulo);

  if (detalle) {
    const parrafoDetalle = document.createElement('p');
    parrafoDetalle.className = 'estado__detalle';
    parrafoDetalle.textContent = detalle;
    contenedor.appendChild(parrafoDetalle);
  }

  return contenedor;
}

function construirPaginacion(pagina) {
  const nav = document.createElement('nav');
  nav.className = 'subastas-paginacion';
  nav.setAttribute('aria-label', 'Paginación de subastas');

  const irA = (numeroPagina) => {
    estado.pagina = numeroPagina;
    cargarYRenderizar();
  };

  nav.appendChild(construirBotonPagina('Anterior', pagina.pagina - 1, pagina.pagina > 0, irA));

  for (const numero of paginasVisibles(pagina.pagina, pagina.totalPaginas)) {
    if (numero === '...') {
      const puntos = document.createElement('span');
      puntos.textContent = '...';
      nav.appendChild(puntos);
      continue;
    }
    const boton = document.createElement('button');
    boton.type = 'button';
    boton.className = 'subastas-paginacion__pagina';
    if (numero === pagina.pagina) {
      boton.classList.add('subastas-paginacion__pagina--activa');
      boton.setAttribute('aria-current', 'page');
    }
    boton.textContent = String(numero + 1); // Vista 1-indexada; el backend es 0-indexado.
    boton.addEventListener('click', () => irA(numero));
    nav.appendChild(boton);
  }

  nav.appendChild(
    construirBotonPagina(
      'Siguiente',
      pagina.pagina + 1,
      pagina.pagina + 1 < pagina.totalPaginas,
      irA,
    ),
  );

  return nav;
}

function construirBotonPagina(etiqueta, numeroDestino, habilitado, irA) {
  const boton = document.createElement('button');
  boton.type = 'button';
  boton.className = 'subastas-paginacion__pagina';
  boton.textContent = etiqueta;
  boton.disabled = !habilitado;
  if (habilitado) {
    boton.addEventListener('click', () => irA(numeroDestino));
  }
  return boton;
}

/** Ventana de paginas a mostrar: primera, ultima, y vecinas de la actual. */
function paginasVisibles(actual, total) {
  const paginas = new Set([0, total - 1, actual - 1, actual, actual + 1]);
  const ordenadas = [...paginas].filter((p) => p >= 0 && p < total).sort((a, b) => a - b);

  const resultado = [];
  for (let i = 0; i < ordenadas.length; i++) {
    if (i > 0 && ordenadas[i] - ordenadas[i - 1] > 1) {
      resultado.push('...');
    }
    resultado.push(ordenadas[i]);
  }
  return resultado;
}

function debounce(funcion, esperaMs) {
  let temporizador;
  return (...argumentos) => {
    clearTimeout(temporizador);
    temporizador = setTimeout(() => funcion(...argumentos), esperaMs);
  };
}
