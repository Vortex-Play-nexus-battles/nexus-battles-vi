/**
 * HU-PRD-001 — formulario accesible de creación de productos.
 *
 * SA-6: la imagen se sube o se elige de la biblioteca (HU-PRD-009; antes era
 * un campo de texto «Referencia de imagen» donde no se podía subir nada,
 * informe de revisión punto 17), el héroe de una habilidad o de una épica se
 * elige por su nombre (antes había que escribir su UUID) y el producto se
 * previsualiza tal como lo verá el jugador mientras se completa (HU-PRD-010).
 */
import { urlDeVista } from '../../comun/acceso.js';
import { crearProducto } from './cliente-productos.js';
import { rellenarHeroes } from './heroes-del-catalogo.js';
import { selectorDeRecurso } from './recursos-graficos.js';
import { vistaPreviaDeProducto } from './vista-previa-producto.js';
import {
  CAMPOS_DE_PROMOCION,
  LIMITES_DE_PROMOCION,
  construirSolicitudProducto,
  ESTADOS_INICIALES,
  PARTES_ARMADURA,
  PROTOTIPOS,
  TIPOS_PRODUCTO,
} from './solicitud-producto.js';

import { h, vaciar } from '../../comun/ui/dom.js';
import { textoDeError } from '../../comun/ui/texto-de-fallo.js';

/** SA-13: la disponibilidad con la que nace un producto (§7.2.1). */
const ETIQUETAS_DISPONIBILIDAD = {
  ACTIVO: 'Activo',
  UNICO: 'Único',
};

const ETIQUETAS_TIPO = {
  HEROE: 'Héroe',
  HABILIDAD: 'Habilidad',
  ARMA: 'Arma',
  ARMADURA: 'Armadura',
  ITEM: 'Ítem',
  EPICA: 'Épica',
};

function opciones(valores, etiquetas = {}) {
  return valores.map((valor) =>
    h('option', {
      texto: etiquetas[valor] || valor,
      atributos: { value: valor },
    }),
  );
}

function control(etiqueta, nombre, atributos = {}) {
  return h(etiqueta, {
    atributos: {
      name: nombre,
      ...atributos,
    },
  });
}

function campo(texto, elemento, { clase, datos, adicionales = [] } = {}) {
  return h('label', {
    clase,
    datos,
    hijos: [texto, elemento, ...adicionales],
  });
}

function grupoTipo(tipo, hijos, oculto = true) {
  return h('div', {
    clase: 'producto-tipo',
    datos: { tipo },
    atributos: { hidden: oculto ? true : null },
    hijos,
  });
}

function selectorDeHeroe() {
  return h('select', {
    atributos: { name: 'heroe', required: true, disabled: true },
    hijos: [h('option', { texto: 'Cargando héroes…', atributos: { value: '' } })],
  });
}

function crearVista(selectorImagen) {
  const cabecera = h('header', {
    clase: 'productos-cabecera',
    hijos: [
      h('div', {
        hijos: [
          h('p', {
            clase: 'productos-cabecera__marca',
            texto: 'NEXUS BATTLES VI',
          }),
          h('h1', { texto: 'Nuevo producto' }),
          h('p', {
            texto: 'Aparece en la tienda en cuanto lo creas.',
          }),
        ],
      }),
      h('div', {
        clase: 'productos-cabecera__acciones',
        hijos: [
          h('span', {
            clase: 'productos-cabecera__insignia',
            texto: 'Administración',
          }),
          h('a', {
            clase: 'productos-cabecera__enlace',
            texto: 'Catálogo',
            atributos: { href: urlDeVista('panel-catalogo') ?? './panel-catalogo.html' },
          }),
          h('a', {
            clase: 'productos-cabecera__enlace',
            texto: 'Banners y anuncios',
            atributos: { href: urlDeVista('banners') ?? './banners.html' },
          }),
        ],
      }),
    ],
  });

  const datosGenerales = h('section', {
    clase: 'producto-seccion',
    atributos: { 'aria-labelledby': 'datos-generales' },
    hijos: [
      h('h2', {
        texto: 'Datos generales',
        atributos: { id: 'datos-generales' },
      }),
      h('div', {
        clase: 'producto-rejilla',
        hijos: [
          campo(
            'Nombre ',
            control('input', 'nombre', {
              maxlength: 120,
              required: true,
            }),
          ),
          campo(
            'Tipo ',
            h('select', {
              atributos: { name: 'tipo', required: true },
              hijos: opciones(TIPOS_PRODUCTO, ETIQUETAS_TIPO),
            }),
          ),
          h('div', { clase: 'producto-campo--ancho', hijos: [selectorImagen.elemento] }),
          campo(
            'Descripción ',
            control('textarea', 'descripcion', {
              rows: 4,
              required: true,
            }),
            { clase: 'producto-campo--ancho' },
          ),
          campo(
            'Tiraje ',
            control('input', 'tiraje', {
              type: 'number',
              step: 1,
              value: -1,
              required: true,
            }),
            {
              adicionales: [
                h('small', {
                  texto: '-1 significa ilimitado; los demás valores deben ser mayores que cero.',
                }),
              ],
            },
          ),
          // SA-13 (§7.2.1): la disponibilidad con la que nace. Suspender no
          // es una opción del alta: tiene su acción en el catálogo.
          campo(
            'Disponibilidad ',
            h('select', {
              atributos: { name: 'estado' },
              hijos: opciones(ESTADOS_INICIALES, ETIQUETAS_DISPONIBILIDAD),
            }),
            {
              adicionales: [
                h('small', {
                  texto:
                    'Único: se vende igual que uno activo y se distingue como pieza única en la tienda y en el catálogo.',
                }),
              ],
            },
          ),
          h('label', {
            clase: 'producto-premium',
            hijos: [control('input', 'premium', { type: 'checkbox' }), ' Producto premium'],
          }),
          campo(
            'Precio en créditos ',
            control('input', 'precioCreditos', {
              type: 'number',
              min: 0,
              step: 1,
              value: 0,
              required: true,
            }),
            { datos: { precio: 'creditos' } },
          ),
          campo(
            'Precio en moneda real ',
            control('input', 'precioMonedaReal', {
              type: 'number',
              min: 0,
              step: 0.01,
              value: 0,
              disabled: true,
            }),
            { datos: { precio: 'real' } },
          ),
        ],
      }),
    ],
  });

  const tipoHeroe = grupoTipo(
    'HEROE',
    [
      campo(
        'Prototipo ',
        h('select', {
          atributos: { name: 'prototipo', required: true },
          hijos: opciones(PROTOTIPOS),
        }),
      ),
    ],
    false,
  );

  const tipoHabilidad = grupoTipo('HABILIDAD', [
    campo('Héroe ', selectorDeHeroe()),
    campo(
      'Costo de poder ',
      control('input', 'costoPoder', {
        type: 'number',
        min: 1,
        step: 1,
        required: true,
        disabled: true,
      }),
    ),
    campo(
      'Multiplicador de nivel ',
      control('input', 'multiplicadorNivel', {
        type: 'number',
        min: 0.000001,
        step: 'any',
        required: true,
        disabled: true,
      }),
    ),
    campo(
      'Turnos de carga ',
      control('input', 'turnosCarga', {
        type: 'number',
        min: 0,
        step: 1,
        required: true,
        disabled: true,
      }),
    ),
  ]);

  const tipoArma = grupoTipo('ARMA', [
    campo(
      'Poder de ataque ',
      control('input', 'poderDeAtaque', {
        type: 'number',
        min: 1,
        step: 1,
        required: true,
        disabled: true,
      }),
    ),
    campo(
      'Tasa de caída (%) ',
      control('input', 'tasaDeCaida', {
        type: 'number',
        min: 0,
        max: 100,
        step: 'any',
        required: true,
        disabled: true,
      }),
    ),
  ]);

  const tipoArmadura = grupoTipo('ARMADURA', [
    campo(
      'Defensa ',
      control('input', 'defensa', {
        type: 'number',
        min: 1,
        step: 1,
        required: true,
        disabled: true,
      }),
    ),
    campo(
      'Parte ',
      h('select', {
        atributos: { name: 'parte', required: true, disabled: true },
        hijos: opciones(PARTES_ARMADURA),
      }),
    ),
    campo(
      'Tasa de caída (%) ',
      control('input', 'tasaDeCaida', {
        type: 'number',
        min: 0,
        max: 100,
        step: 'any',
        required: true,
        disabled: true,
      }),
    ),
  ]);

  const tipoItem = grupoTipo('ITEM', [
    campo('Efecto ', control('textarea', 'efecto', { rows: 3, required: true, disabled: true })),
    campo(
      'Tasa de caída (%) ',
      control('input', 'tasaDeCaida', {
        type: 'number',
        min: 0,
        max: 100,
        step: 'any',
        required: true,
        disabled: true,
      }),
    ),
  ]);

  const tipoEpica = grupoTipo('EPICA', [
    campo('Héroe ', selectorDeHeroe()),
    campo(
      'Turnos de recarga ',
      control('input', 'turnosRecarga', {
        type: 'number',
        min: 0,
        step: 1,
        required: true,
        disabled: true,
      }),
    ),
    campo(
      'Efecto general ',
      control('textarea', 'efectoGeneral', { rows: 3, required: true, disabled: true }),
    ),
    campo(
      'Efecto potenciado ',
      control('textarea', 'efectoPotenciado', {
        rows: 3,
        required: true,
        disabled: true,
      }),
    ),
  ]);

  const atributosTipo = h('section', {
    clase: 'producto-seccion',
    atributos: { 'aria-labelledby': 'datos-tipo' },
    hijos: [
      h('h2', { texto: 'Atributos del tipo', atributos: { id: 'datos-tipo' } }),
      tipoHeroe,
      tipoHabilidad,
      tipoArma,
      tipoArmadura,
      tipoItem,
      tipoEpica,
    ],
  });

  // UXC-9 — §7.2.4 y §7.5: la promoción del producto (productos.yaml 1.4.0),
  // opcional. O los tres campos o ninguno; el servidor dice si está vigente.
  const promocion = h('section', {
    clase: 'producto-seccion',
    atributos: { 'aria-labelledby': 'datos-promocion' },
    hijos: [
      h('h2', { texto: 'Promoción (opcional)', atributos: { id: 'datos-promocion' } }),
      h('p', {
        texto: 'Un descuento con fecha de inicio y de fin. Déjalo vacío si el producto no tiene.',
      }),
      h('div', {
        clase: 'producto-rejilla',
        hijos: [
          campo(
            'Descuento (%) ',
            control('input', CAMPOS_DE_PROMOCION.porcentaje, {
              type: 'number',
              min: LIMITES_DE_PROMOCION.minimo,
              max: LIMITES_DE_PROMOCION.maximo,
              step: 1,
            }),
            {
              adicionales: [
                h('small', {
                  texto: `Entre ${LIMITES_DE_PROMOCION.minimo} y ${LIMITES_DE_PROMOCION.maximo}.`,
                }),
              ],
            },
          ),
          campo(
            'Empieza ',
            control('input', CAMPOS_DE_PROMOCION.desde, { type: 'datetime-local' }),
          ),
          campo(
            'Termina ',
            control('input', CAMPOS_DE_PROMOCION.hasta, { type: 'datetime-local' }),
          ),
        ],
      }),
    ],
  });

  const estadoProhibido = h('div', {
    clase: 'producto-estado producto-estado--error',
    atributos: { id: 'nexus-rbac-forbidden', role: 'alert', hidden: true },
  });

  const estado = h('div', {
    clase: 'producto-estado',
    texto: 'Completa los campos para registrar el producto.',
    datos: { estado: 'vacio' },
    atributos: { role: 'status', 'aria-live': 'polite' },
  });

  const acciones = h('div', {
    clase: 'producto-acciones',
    hijos: [
      h('button', {
        clase: 'boton-secundario',
        texto: 'Limpiar',
        atributos: { type: 'reset' },
      }),
      h('button', {
        clase: 'boton-primario',
        texto: 'Crear producto',
        atributos: { type: 'submit' },
      }),
    ],
  });

  const formulario = h('form', {
    clase: 'producto-formulario',
    atributos: { novalidate: true },
    hijos: [datosGenerales, atributosTipo, promocion, estadoProhibido, estado, acciones],
  });

  const precioReal = formulario.querySelector('[data-precio="real"]');
  if (precioReal instanceof HTMLElement) {
    precioReal.hidden = true;
  }

  // HU-PRD-010 — la tarjeta de la tienda con lo que se lleva escrito.
  const vistaPrevia = h('aside', {
    clase: 'producto-vista-previa',
    atributos: { 'aria-labelledby': 'vista-previa-alta' },
    hijos: [
      h('h2', { texto: 'Vista previa', atributos: { id: 'vista-previa-alta' } }),
      h('div', { datos: { zona: 'vista-previa-alta' } }),
    ],
  });
  const cuerpo = h('div', { clase: 'producto-alta', hijos: [formulario, vistaPrevia] });
  return [cabecera, cuerpo];
}

/**
 * Lo escrito hasta ahora, sin validar: lo justo para la vista previa.
 *
 * @param {HTMLFormElement} formulario
 * @returns {object}
 */
export function borradorDelFormulario(formulario) {
  const leer = (nombre) => {
    const candidatos = [...formulario.querySelectorAll(`[name="${nombre}"]`)];
    const activo = candidatos.find((c) => !c.disabled) ?? candidatos[0];
    if (!activo) {
      return '';
    }
    return activo.type === 'checkbox' ? activo.checked : String(activo.value ?? '').trim();
  };
  const premium = Boolean(leer('premium'));
  return {
    nombre: leer('nombre') || 'Nombre del producto',
    descripcion: leer('descripcion'),
    tipo: leer('tipo'),
    imagen: leer('imagen') || null,
    premium,
    precioCreditos: premium ? null : Number(leer('precioCreditos')) || null,
    precioMonedaReal: premium ? Number(leer('precioMonedaReal')) || null : null,
  };
}

function gruposPorTipo(raiz) {
  return [...raiz.querySelectorAll('[data-tipo]')];
}

function mostrarTipo(raiz, tipo) {
  for (const grupo of gruposPorTipo(raiz)) {
    const activo = grupo.dataset.tipo === tipo;
    grupo.hidden = !activo;
    for (const elementoControl of grupo.querySelectorAll('input, select, textarea')) {
      elementoControl.disabled = !activo;
    }
  }
}

function mostrarPrecio(raiz, premium) {
  const creditos = raiz.querySelector('[data-precio="creditos"]');
  const real = raiz.querySelector('[data-precio="real"]');
  creditos.hidden = premium;
  real.hidden = !premium;
  creditos.querySelector('input').disabled = premium;
  real.querySelector('input').disabled = !premium;
}

function mostrarEstado(raiz, texto, tipo = 'vacio') {
  const estado = raiz.querySelector('[data-estado]');
  estado.textContent = texto;
  estado.dataset.estado = tipo;
  estado.className = `producto-estado producto-estado--${tipo}`;
  estado.setAttribute('role', tipo === 'error' ? 'alert' : 'status');
}

function mensajeFallo(fallo) {
  if (fallo?.codigo === 'SESION_REQUERIDA' || fallo?.status === 401) {
    return 'Tu sesión no está disponible. Inicia sesión nuevamente.';
  }
  if (fallo?.status === 403) {
    return 'No tienes permiso para crear productos.';
  }
  if (fallo?.status === 400) {
    return textoDeError(fallo, 'Revisa los datos ingresados.');
  }
  return textoDeError(fallo, 'No pudimos crear el producto. Inténtalo nuevamente.');
}

/** Monta la vista de creación y delega la autenticación al interceptor común. */
export function montarFormularioProductos(
  raiz,
  { crear = crearProducto, listarHeroes, subir, listar, fetchImpl } = {},
) {
  let repintarVistaPrevia = () => {};
  const selectorImagen = selectorDeRecurso({
    subir,
    listar,
    fetchImpl,
    alCambiar: () => repintarVistaPrevia(),
  });
  vaciar(raiz).append(...crearVista(selectorImagen));
  const formulario = raiz.querySelector('form');
  const tipo = formulario.elements.namedItem('tipo');
  const premium = formulario.elements.namedItem('premium');
  const boton = formulario.querySelector('[type="submit"]');
  const zonaVistaPrevia = raiz.querySelector('[data-zona="vista-previa-alta"]');

  repintarVistaPrevia = () => {
    zonaVistaPrevia?.replaceChildren(vistaPreviaDeProducto(borradorDelFormulario(formulario)));
  };

  for (const selector of formulario.querySelectorAll('select[name="heroe"]')) {
    void rellenarHeroes(selector, { listar: listarHeroes, fetchImpl });
  }

  mostrarTipo(raiz, tipo.value);
  mostrarPrecio(raiz, premium.checked);
  repintarVistaPrevia();

  tipo.addEventListener('change', () => mostrarTipo(raiz, tipo.value));
  premium.addEventListener('change', () => mostrarPrecio(raiz, premium.checked));
  formulario.addEventListener('input', () => repintarVistaPrevia());
  formulario.addEventListener('change', () => repintarVistaPrevia());
  formulario.addEventListener('reset', () => {
    queueMicrotask(() => {
      selectorImagen.establecer('');
      mostrarTipo(raiz, tipo.value);
      mostrarPrecio(raiz, premium.checked);
      mostrarEstado(raiz, 'Completa los campos para registrar el producto.');
      repintarVistaPrevia();
    });
  });

  formulario.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    // El campo de la imagen es un `input` oculto: el navegador no lo valida.
    const sinImagen = !selectorImagen.valor();
    selectorImagen.marcarError(
      sinImagen ? 'Sube la imagen del producto o elige una de la biblioteca.' : null,
    );
    if (!formulario.checkValidity() || sinImagen) {
      formulario.reportValidity();
      mostrarEstado(raiz, 'Revisa los campos obligatorios y sus valores.', 'error');
      return;
    }

    boton.disabled = true;
    mostrarEstado(raiz, 'Creando producto…', 'carga');
    try {
      const solicitud = construirSolicitudProducto(formulario);
      const creado = await crear(solicitud);
      mostrarEstado(
        raiz,
        `«${creado.nombre}» ya está en el catálogo${creado.estado === 'UNICO' ? ' como producto único' : ''}.`,
        'exito',
      );
    } catch (fallo) {
      console.error('No se pudo crear el producto', fallo);
      mostrarEstado(raiz, mensajeFallo(fallo), 'error');
    } finally {
      boton.disabled = false;
    }
  });

  return {
    formulario,
    selectorImagen,
    mostrarTipo: (nuevoTipo) => mostrarTipo(raiz, nuevoTipo),
  };
}
