/**
 * HU-SUB-011 - Panel de filtros del listado de subastas.
 *
 * Un <form> real, no un <div> -- FormData lee todos los valores de una vez
 * (incluidos los checkboxes multiples de tipoProducto), y "Limpiar filtros"
 * es un boton type="reset" nativo, sin logica manual para vaciar cada campo.
 */

const TIPOS_PRODUCTO = [
  { valor: 'HEROE', etiqueta: 'Héroe' },
  { valor: 'HABILIDAD', etiqueta: 'Habilidad' },
  { valor: 'ARMA', etiqueta: 'Arma' },
  { valor: 'ARMADURA', etiqueta: 'Armadura' },
  { valor: 'ITEM', etiqueta: 'Ítem' },
  { valor: 'EPICA', etiqueta: 'Épica' },
];

/** Mismas 4 cadenas exactas que CLASE_POR_RAREZA en subastas-vitrina.js. */
const RAREZAS = ['Común', 'Rara', 'Épica', 'Legendaria'].map((r) => ({ valor: r, etiqueta: r }));

const OPCIONES_TIEMPO_RESTANTE = [
  { valor: 'FINALIZA_PRONTO', etiqueta: 'Finaliza pronto' },
  { valor: 'MENOS_1H', etiqueta: 'Menos de 1 hora' },
  { valor: 'MENOS_6H', etiqueta: 'Menos de 6 horas' },
  { valor: 'MENOS_24H', etiqueta: 'Menos de 24 horas' },
];

const OPCIONES_TIPO_VENTA = [
  { valor: 'SOLO_PUJAS', etiqueta: 'Solo pujas' },
  { valor: 'COMPRA_INMEDIATA_DISPONIBLE', etiqueta: 'Compra inmediata disponible' },
];

const OPCIONES_METODO_PAGO = [
  { valor: 'CREDITOS', etiqueta: 'Créditos' },
  { valor: 'DINERO_REAL', etiqueta: 'Dinero real' },
];

const OPCIONES_VENDEDOR = [
  { valor: 'JUGADORES', etiqueta: 'Jugadores' },
  { valor: 'MAESTRO_DE_JUEGO', etiqueta: 'Maestro de Juego' },
];

/**
 * Construye el panel de filtros.
 *
 * @param {{alCambiar?: (filtros: object) => void}} [opciones]
 * @returns {HTMLFormElement}
 */
export function construirFiltros({ alCambiar } = {}) {
  const formulario = document.createElement('form');
  formulario.className = 'subastas-filtros';
  formulario.setAttribute('aria-label', 'Filtros de subastas');

  formulario.append(
    construirGrupoCheckbox('tipoProducto', 'Tipo de producto', TIPOS_PRODUCTO),
    construirGrupoRadio('rareza', 'Rareza', RAREZAS),
    construirGrupoPrecio(),
    construirGrupoRadio('tiempoRestante', 'Termina en', OPCIONES_TIEMPO_RESTANTE),
    construirGrupoRadio('tipoVenta', 'Tipo de venta', OPCIONES_TIPO_VENTA),
    construirGrupoRadio('metodoPago', 'Método de pago', OPCIONES_METODO_PAGO),
    construirGrupoRadio('vendedor', 'Vendedor', OPCIONES_VENDEDOR),
    construirBotonLimpiar(),
  );

  const notificar = () => {
    if (typeof alCambiar === 'function') {
      alCambiar(leerFiltros(formulario));
    }
  };

  formulario.addEventListener('change', notificar);
  // El evento "reset" se dispara cuando el formulario YA quedo en sus
  // valores por defecto (todas las opciones "Todas" / checkboxes sin
  // marcar), asi que leer de inmediato ya refleja el estado limpio.
  formulario.addEventListener('reset', notificar);
  // Sin boton submit, pero Enter dentro de un campo numerico podria
  // intentar enviar el formulario en algunos navegadores -- se evita
  // cualquier navegacion accidental.
  formulario.addEventListener('submit', (evento) => evento.preventDefault());

  return formulario;
}

/**
 * Lee el estado actual del panel, en la forma que espera
 * cliente-subastas.js / FiltrosSubasta del backend.
 *
 * @param {HTMLFormElement} formulario
 * @returns {object}
 */
export function leerFiltros(formulario) {
  const datos = new FormData(formulario);
  const filtros = {};

  const tipos = datos.getAll('tipoProducto');
  if (tipos.length > 0) {
    filtros.tipoProducto = tipos;
  }

  for (const campo of ['rareza', 'tiempoRestante', 'tipoVenta', 'metodoPago', 'vendedor']) {
    const valor = datos.get(campo);
    if (valor) {
      filtros[campo] = valor;
    }
  }

  const precioMin = datos.get('precioMin');
  if (precioMin) {
    filtros.precioMin = Number(precioMin);
  }
  const precioMax = datos.get('precioMax');
  if (precioMax) {
    filtros.precioMax = Number(precioMax);
  }

  return filtros;
}

function construirGrupoCheckbox(nombre, etiquetaGrupo, opciones) {
  const grupo = document.createElement('fieldset');
  grupo.className = 'subastas-filtros__grupo';

  const leyenda = document.createElement('legend');
  leyenda.className = 'subastas-filtros__leyenda';
  leyenda.textContent = etiquetaGrupo;
  grupo.appendChild(leyenda);

  for (const opcion of opciones) {
    const etiqueta = document.createElement('label');
    etiqueta.className = 'subastas-filtros__opcion';

    const casilla = document.createElement('input');
    casilla.type = 'checkbox';
    casilla.name = nombre;
    casilla.value = opcion.valor;

    etiqueta.append(casilla, document.createTextNode(opcion.etiqueta));
    grupo.appendChild(etiqueta);
  }

  return grupo;
}

/** Siempre agrega una opcion "Todas" (valor "") al inicio, marcada por defecto. */
function construirGrupoRadio(nombre, etiquetaGrupo, opciones) {
  const grupo = document.createElement('fieldset');
  grupo.className = 'subastas-filtros__grupo';

  const leyenda = document.createElement('legend');
  leyenda.className = 'subastas-filtros__leyenda';
  leyenda.textContent = etiquetaGrupo;
  grupo.appendChild(leyenda);

  const todasMasOpciones = [{ valor: '', etiqueta: 'Todas' }, ...opciones];
  for (const opcion of todasMasOpciones) {
    const etiqueta = document.createElement('label');
    etiqueta.className = 'subastas-filtros__opcion';

    const boton = document.createElement('input');
    boton.type = 'radio';
    boton.name = nombre;
    boton.value = opcion.valor;
    if (opcion.valor === '') {
      boton.checked = true;
    }

    etiqueta.append(boton, document.createTextNode(opcion.etiqueta));
    grupo.appendChild(etiqueta);
  }

  return grupo;
}

function construirGrupoPrecio() {
  const grupo = document.createElement('fieldset');
  grupo.className = 'subastas-filtros__grupo';

  const leyenda = document.createElement('legend');
  leyenda.className = 'subastas-filtros__leyenda';
  leyenda.textContent = 'Precio (créditos)';
  grupo.appendChild(leyenda);

  const fila = document.createElement('div');
  fila.className = 'subastas-filtros__precio';
  fila.append(construirCampoPrecio('precioMin', 'Mínimo'), construirCampoPrecio('precioMax', 'Máximo'));

  grupo.appendChild(fila);
  return grupo;
}

function construirCampoPrecio(nombre, etiquetaTexto) {
  const contenedor = document.createElement('label');
  contenedor.className = 'subastas-filtros__campo-precio';

  const etiqueta = document.createElement('span');
  etiqueta.className = 'subastas-filtros__etiqueta-precio';
  etiqueta.textContent = etiquetaTexto;

  const campo = document.createElement('input');
  campo.type = 'number';
  campo.name = nombre;
  campo.min = '0';
  campo.step = '1';
  campo.inputMode = 'numeric';

  contenedor.append(etiqueta, campo);
  return contenedor;
}

function construirBotonLimpiar() {
  const boton = document.createElement('button');
  boton.type = 'reset';
  boton.className = 'subastas-filtros__limpiar';
  boton.textContent = 'Limpiar filtros';
  return boton;
}
