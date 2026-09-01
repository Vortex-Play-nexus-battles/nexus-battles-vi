/**
 * HU-INV-001 - Orquesta la vitrina y sus cuatro estados.
 *
 * Separa la decision de que mostrar (este archivo) de como se dibuja la
 * rejilla (`vitrina.js`) y de como se pide el dato (`cliente-inventario.js`).
 */

import { consultarPagina, crearElemento, modificarElemento } from './cliente-inventario.js';
import { construirVitrina, PRODUCTOS_POR_PAGINA } from './vitrina.js';
import { construirCarga, construirVacio, construirError } from './estados-vista.js';
import { abrirFicha } from './ficha-producto.js';

const TIPOS = [
  ['HEROE', 'Héroe'],
  ['HABILIDAD', 'Habilidad'],
  ['ARMA', 'Arma'],
  ['ARMADURA', 'Armadura'],
  ['ITEM', 'Ítem'],
  ['EPICA', 'Épica'],
];

/**
 * Pinta el inventario de un jugador dentro de `contenedor`.
 *
 * @param {HTMLElement} contenedor donde se monta la vista.
 * @param {string} identidad jugador autenticado, que viaja en la cabecera.
 * @param {number} numeroPagina pagina pedida, desde cero.
 * @param {{consultar?: Function, alEditar?: Function}} opciones inyeccion para las pruebas.
 * @returns {Promise<object|null>} pagina mostrada o null cuando falla la consulta.
 */
export async function montarVitrina(
  contenedor,
  identidad,
  numeroPagina = 0,
  { consultar = consultarPagina, alEditar } = {},
) {
  contenedor.replaceChildren(construirCarga());

  let pagina;
  try {
    pagina = await consultar(identidad, numeroPagina);
  } catch (fallo) {
    // El detalle tecnico es para el equipo; al jugador se le habla en su idioma.
    console.error('No se pudo cargar la vitrina del inventario', fallo);
    contenedor.replaceChildren(construirError());
    return null;
  }

  if (!pagina || pagina.elementos.length === 0) {
    contenedor.replaceChildren(construirVacio());
    return pagina;
  }

  contenedor.replaceChildren(
    construirVitrina(pagina, {
      alEditar,
      // HU-INV-007: la ficha lee el catalogo por su cuenta; el inventario
      // solo guarda la referencia (RF-ADM-10).
      alAbrirDetalle: (elemento) =>
        abrirFicha(elemento.productoId, { origen: document.activeElement }),
    }),
  );
  return pagina;
}

function elementoHtml(etiqueta, clase, texto) {
  const elemento = document.createElement(etiqueta);
  if (clase) {
    elemento.className = clase;
  }
  if (texto) {
    elemento.textContent = texto;
  }
  return elemento;
}

function campoFormulario(texto, nombre, tipo = 'text') {
  const etiqueta = elementoHtml('label', 'inventario-editor__campo');
  etiqueta.appendChild(elementoHtml('span', 'inventario-editor__etiqueta', texto));
  const control = document.createElement(tipo === 'select' ? 'select' : 'input');
  control.name = nombre;
  control.required = true;
  if (tipo !== 'select') {
    control.type = tipo;
  }
  control.className = 'inventario-editor__control';
  etiqueta.appendChild(control);
  return { etiqueta, control };
}

function construirGestion() {
  const cabecera = elementoHtml('header', 'inventario-cabecera');
  const titulo = elementoHtml('h1', 'vitrina-titulo', 'Mi inventario');
  const botonNuevo = elementoHtml('button', 'inventario__nuevo', 'Agregar elemento');
  botonNuevo.type = 'button';
  cabecera.append(titulo, botonNuevo);

  const editor = elementoHtml('section', 'inventario-editor');
  editor.hidden = true;
  const tituloEditor = elementoHtml('h2', 'inventario-editor__titulo', 'Nuevo elemento');
  const formulario = elementoHtml('form', 'inventario-editor__formulario');
  const producto = campoFormulario('Producto', 'productoId');
  const tipo = campoFormulario('Tipo', 'tipo', 'select');
  for (const [valor, etiqueta] of TIPOS) {
    const opcion = document.createElement('option');
    opcion.value = valor;
    opcion.textContent = etiqueta;
    tipo.control.appendChild(opcion);
  }
  const nombre = campoFormulario('Nombre', 'nombrePropio');
  const acciones = elementoHtml('div', 'inventario-editor__acciones');
  const botonGuardar = elementoHtml('button', 'inventario-editor__guardar', 'Guardar');
  botonGuardar.type = 'submit';
  const botonCancelar = elementoHtml('button', 'inventario-editor__cancelar', 'Cancelar');
  botonCancelar.type = 'button';
  acciones.append(botonGuardar, botonCancelar);
  formulario.append(producto.etiqueta, tipo.etiqueta, nombre.etiqueta, acciones);
  editor.append(tituloEditor, formulario);

  const mensaje = elementoHtml('p', 'inventario__mensaje');
  mensaje.id = 'nexus-rbac-forbidden';
  mensaje.hidden = true;
  mensaje.setAttribute('role', 'status');
  mensaje.setAttribute('aria-live', 'polite');
  const contenido = elementoHtml('div', 'inventario__contenido');

  return {
    elementos: [cabecera, editor, mensaje, contenido],
    cabecera,
    botonNuevo,
    editor,
    tituloEditor,
    formulario,
    producto,
    tipo,
    nombre,
    botonGuardar,
    botonCancelar,
    mensaje,
    contenido,
  };
}

/**
 * Monta la gestion de HU-INV-003 y mantiene la vitrina de HU-INV-001 como
 * fuente visible del estado que quedo persistido.
 */
export async function montarInventario(
  raiz,
  identidad,
  numeroPagina = 0,
  { consultar = consultarPagina, crear = crearElemento, modificar = modificarElemento } = {},
) {
  const vista = construirGestion();
  raiz.replaceChildren(...vista.elementos);

  let paginaActual = numeroPagina;
  let paginaMostrada = null;
  let elementoSeleccionado = null;

  function mostrarMensaje(texto, esError = false) {
    vista.mensaje.textContent = texto;
    vista.mensaje.hidden = texto === '';
    vista.mensaje.setAttribute('role', esError ? 'alert' : 'status');
  }

  function cerrarEditor() {
    vista.editor.hidden = true;
    vista.formulario.reset();
    elementoSeleccionado = null;
  }

  function abrirCreacion() {
    vista.formulario.reset();
    elementoSeleccionado = null;
    vista.tituloEditor.textContent = 'Nuevo elemento';
    vista.producto.etiqueta.hidden = false;
    vista.tipo.etiqueta.hidden = false;
    vista.producto.control.disabled = false;
    vista.tipo.control.disabled = false;
    vista.editor.hidden = false;
    mostrarMensaje('');
    vista.producto.control.focus();
  }

  function abrirEdicion(elemento) {
    elementoSeleccionado = elemento;
    vista.tituloEditor.textContent = 'Editar elemento';
    vista.producto.etiqueta.hidden = true;
    vista.tipo.etiqueta.hidden = true;
    vista.producto.control.disabled = true;
    vista.tipo.control.disabled = true;
    vista.nombre.control.value = elemento.nombrePropio;
    vista.editor.hidden = false;
    mostrarMensaje('');
    vista.nombre.control.focus();
  }

  async function actualizar(numero = paginaActual) {
    paginaActual = numero;
    const consultada = await montarVitrina(vista.contenido, identidad, paginaActual, {
      consultar,
      alEditar: abrirEdicion,
    });
    if (consultada) {
      paginaMostrada = consultada;
    }
    return consultada;
  }

  vista.botonNuevo.addEventListener('click', abrirCreacion);
  vista.botonCancelar.addEventListener('click', cerrarEditor);
  vista.formulario.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    cambiarDisponibilidad(vista.botonGuardar, false);
    mostrarMensaje('Guardando...');

    try {
      if (elementoSeleccionado) {
        await modificar(identidad, elementoSeleccionado.id, {
          nombrePropio: vista.nombre.control.value,
        });
        cerrarEditor();
        await actualizar();
        mostrarMensaje('Elemento actualizado.');
      } else {
        const totalAntes = paginaMostrada?.totalElementos ?? 0;
        await crear(identidad, {
          productoId: vista.producto.control.value,
          tipo: vista.tipo.control.value,
          nombrePropio: vista.nombre.control.value,
        });
        cerrarEditor();
        await actualizar(Math.floor(totalAntes / PRODUCTOS_POR_PAGINA));
        mostrarMensaje('Elemento creado.');
      }
    } catch (fallo) {
      console.error('No se pudo guardar el elemento del inventario', fallo);
      const mensaje =
        fallo?.status === 403
          ? 'No tienes permiso para modificar ese inventario.'
          : 'No pudimos guardar el elemento. Revisa los datos e inténtalo de nuevo.';
      mostrarMensaje(mensaje, true);
    } finally {
      cambiarDisponibilidad(vista.botonGuardar, true);
    }
  });

  await actualizar();
}

function cambiarDisponibilidad(boton, disponible) {
  boton.disabled = !disponible;
}
