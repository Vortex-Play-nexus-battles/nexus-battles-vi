/**
 * HU-INV-001 - Orquesta la vitrina y sus cuatro estados.
 *
 * Separa la decision de que mostrar (este archivo) de como se dibuja la
 * rejilla (`vitrina.js`) y de como se pide el dato (`cliente-inventario.js`).
 */

import {
  consultarPagina,
  crearElemento,
  modificarElemento,
  consultarEquipamiento,
  equiparElemento,
  desequiparElemento,
} from './cliente-inventario.js';
import { construirVitrina, PRODUCTOS_POR_PAGINA } from './vitrina.js';
import { construirCarga, construirVacio, construirError } from './estados-vista.js';

const TIPOS = [
  ['HEROE', 'Héroe'],
  ['HABILIDAD', 'Habilidad'],
  ['ARMA', 'Arma'],
  ['ARMADURA', 'Armadura'],
  ['ITEM', 'Ítem'],
  ['EPICA', 'Épica'],
];

const PARTES_ARMADURA = [
  ['CASCO', 'Casco'],
  ['PECHO', 'Pecho'],
  ['GUANTES', 'Guantes'],
  ['BRAZALETES', 'Brazaletes'],
  ['PANTALON', 'Pantalón'],
  ['ZAPATOS', 'Zapatos'],
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
  { consultar = consultarPagina, alEditar, alEquipar } = {},
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

  contenedor.replaceChildren(construirVitrina(pagina, { alEditar, alEquipar }));
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
  const parte = campoFormulario('Parte de armadura', 'parteArmadura', 'select');
  for (const [valor, etiqueta] of PARTES_ARMADURA) {
    const opcion = document.createElement('option');
    opcion.value = valor;
    opcion.textContent = etiqueta;
    parte.control.appendChild(opcion);
  }
  parte.etiqueta.hidden = true;
  const acciones = elementoHtml('div', 'inventario-editor__acciones');
  const botonGuardar = elementoHtml('button', 'inventario-editor__guardar', 'Guardar');
  botonGuardar.type = 'submit';
  const botonCancelar = elementoHtml('button', 'inventario-editor__cancelar', 'Cancelar');
  botonCancelar.type = 'button';
  acciones.append(botonGuardar, botonCancelar);
  formulario.append(producto.etiqueta, tipo.etiqueta, nombre.etiqueta, parte.etiqueta, acciones);
  editor.append(tituloEditor, formulario);

  const equipo = elementoHtml('section', 'inventario-equipo');
  equipo.hidden = true;
  const equipoCabecera = elementoHtml('header', 'inventario-equipo__cabecera');
  const equipoTitulo = elementoHtml('h2', 'inventario-equipo__titulo', 'Equipamiento');
  const equipoCerrar = elementoHtml('button', 'inventario-equipo__cerrar', 'Cerrar');
  equipoCerrar.type = 'button';
  equipoCabecera.append(equipoTitulo, equipoCerrar);
  const equipoResumen = elementoHtml('p', 'inventario-equipo__resumen');
  const equipoLista = elementoHtml('ul', 'inventario-equipo__lista');
  equipo.append(equipoCabecera, equipoResumen, equipoLista);

  const mensaje = elementoHtml('p', 'inventario__mensaje');
  mensaje.id = 'nexus-rbac-forbidden';
  mensaje.hidden = true;
  mensaje.setAttribute('role', 'status');
  mensaje.setAttribute('aria-live', 'polite');
  const contenido = elementoHtml('div', 'inventario__contenido');

  return {
    elementos: [cabecera, editor, equipo, mensaje, contenido],
    cabecera,
    botonNuevo,
    editor,
    tituloEditor,
    formulario,
    producto,
    tipo,
    nombre,
    parte,
    botonGuardar,
    botonCancelar,
    equipo,
    equipoTitulo,
    equipoCerrar,
    equipoResumen,
    equipoLista,
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
  {
    consultar = consultarPagina,
    crear = crearElemento,
    modificar = modificarElemento,
    consultarEquipo = consultarEquipamiento,
    equipar = equiparElemento,
    desequipar = desequiparElemento,
  } = {},
) {
  const vista = construirGestion();
  raiz.replaceChildren(...vista.elementos);

  let paginaActual = numeroPagina;
  let paginaMostrada = null;
  let elementoSeleccionado = null;
  let heroeSeleccionado = null;
  let equipoActual = null;

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
    vista.parte.etiqueta.hidden = true;
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
    vista.parte.etiqueta.hidden = true;
    vista.nombre.control.value = elemento.nombrePropio;
    vista.editor.hidden = false;
    mostrarMensaje('');
    vista.nombre.control.focus();
  }

  function idsEquipados(equipo) {
    return new Set([...equipo.armas, ...Object.values(equipo.armaduras), ...equipo.items]);
  }

  function pintarEquipo() {
    const equipados = idsEquipados(equipoActual);
    vista.equipoResumen.textContent =
      `Armas ${equipoActual.armas.length}/2 · ` +
      `Armadura ${Object.keys(equipoActual.armaduras).length}/6 · ` +
      `Ítems ${equipoActual.items.length}/2`;
    vista.equipoLista.replaceChildren();

    const disponibles = (paginaMostrada?.elementos ?? []).filter((elemento) =>
      ['ARMA', 'ARMADURA', 'ITEM'].includes(elemento.tipo),
    );
    for (const elemento of disponibles) {
      const fila = elementoHtml('li', 'inventario-equipo__elemento');
      const detalle = elementoHtml(
        'span',
        'inventario-equipo__nombre',
        elemento.parteArmadura
          ? `${elemento.nombrePropio} · ${elemento.parteArmadura}`
          : elemento.nombrePropio,
      );
      const estaEquipado = equipados.has(elemento.id);
      const boton = elementoHtml(
        'button',
        estaEquipado ? 'inventario-equipo__desequipar' : 'inventario-equipo__equipar',
        estaEquipado ? 'Desequipar' : 'Equipar',
      );
      boton.type = 'button';
      boton.addEventListener('click', async () => {
        cambiarDisponibilidad(boton, false);
        try {
          equipoActual = estaEquipado
            ? await desequipar(identidad, heroeSeleccionado.id, elemento.id)
            : await equipar(identidad, heroeSeleccionado.id, elemento.id);
          pintarEquipo();
          mostrarMensaje(estaEquipado ? 'Elemento desequipado.' : 'Elemento equipado.');
        } catch (fallo) {
          console.error('No se pudo cambiar el equipamiento', fallo);
          let texto = 'No pudimos cambiar el equipamiento. Inténtalo de nuevo.';
          if (fallo?.status === 409) {
            texto = 'Ese cambio supera los límites de equipamiento.';
          } else if (fallo?.status === 403) {
            texto = 'No tienes permiso para modificar ese inventario.';
          }
          mostrarMensaje(texto, true);
          cambiarDisponibilidad(boton, true);
        }
      });
      fila.append(detalle, boton);
      vista.equipoLista.appendChild(fila);
    }
  }

  async function abrirEquipamiento(heroe) {
    heroeSeleccionado = heroe;
    vista.equipoTitulo.textContent = `Equipamiento de ${heroe.nombrePropio}`;
    mostrarMensaje('Cargando equipamiento...');
    try {
      equipoActual = await consultarEquipo(identidad, heroe.id);
      vista.equipo.hidden = false;
      pintarEquipo();
      mostrarMensaje('');
    } catch (fallo) {
      console.error('No se pudo consultar el equipamiento', fallo);
      mostrarMensaje('No pudimos cargar el equipamiento. Inténtalo de nuevo.', true);
    }
  }

  async function actualizar(numero = paginaActual) {
    paginaActual = numero;
    const consultada = await montarVitrina(vista.contenido, identidad, paginaActual, {
      consultar,
      alEditar: abrirEdicion,
      alEquipar: abrirEquipamiento,
    });
    if (consultada) {
      paginaMostrada = consultada;
    }
    return consultada;
  }

  vista.botonNuevo.addEventListener('click', abrirCreacion);
  vista.botonCancelar.addEventListener('click', cerrarEditor);
  vista.equipoCerrar.addEventListener('click', () => {
    vista.equipo.hidden = true;
    heroeSeleccionado = null;
    equipoActual = null;
  });
  vista.tipo.control.addEventListener('change', () => {
    vista.parte.etiqueta.hidden = vista.tipo.control.value !== 'ARMADURA';
    vista.parte.control.required = vista.tipo.control.value === 'ARMADURA';
  });
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
          parteArmadura:
            vista.tipo.control.value === 'ARMADURA' ? vista.parte.control.value : undefined,
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
