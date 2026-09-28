/** HU-PRD-001 - Formulario accesible de creación de productos. */
import { crearProducto } from './cliente-productos.js';
import {
  construirSolicitudProducto,
  PARTES_ARMADURA,
  PROTOTIPOS,
  TIPOS_PRODUCTO,
} from './solicitud-producto.js';

import { h, vaciar } from '../../comun/ui/dom.js';
import { textoDeError } from '../../comun/ui/texto-de-fallo.js';

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

function crearVista() {
  const cabecera = h('header', {
    clase: 'productos-cabecera',
    hijos: [
      h('div', {
        hijos: [
          h('p', {
            clase: 'productos-cabecera__marca',
            texto: 'NEXUS BATTLES VI',
          }),
          h('h1', { texto: 'Crear producto del catálogo' }),
          h('p', {
            texto: 'Registra un producto con los atributos definidos para su tipo.',
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
            texto: 'Estado del catálogo',
            atributos: { href: './panel-catalogo.html' },
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
          campo(
            'Referencia de imagen ',
            control('input', 'imagen', {
              placeholder: 'Ruta o referencia de la imagen',
              required: true,
            }),
            { clase: 'producto-campo--ancho' },
          ),
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
    campo('UUID del héroe ', control('input', 'heroe', { required: true, disabled: true })),
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
    campo('UUID del héroe ', control('input', 'heroe', { required: true, disabled: true })),
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
    hijos: [datosGenerales, atributosTipo, estadoProhibido, estado, acciones],
  });

  const precioReal = formulario.querySelector('[data-precio="real"]');
  if (precioReal instanceof HTMLElement) {
    precioReal.hidden = true;
  }
  return [cabecera, formulario];
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
export function montarFormularioProductos(raiz, { crear = crearProducto } = {}) {
  vaciar(raiz).append(...crearVista());
  const formulario = raiz.querySelector('form');
  const tipo = formulario.elements.namedItem('tipo');
  const premium = formulario.elements.namedItem('premium');
  const boton = formulario.querySelector('[type="submit"]');

  mostrarTipo(raiz, tipo.value);
  mostrarPrecio(raiz, premium.checked);

  tipo.addEventListener('change', () => mostrarTipo(raiz, tipo.value));
  premium.addEventListener('change', () => mostrarPrecio(raiz, premium.checked));
  formulario.addEventListener('reset', () => {
    queueMicrotask(() => {
      mostrarTipo(raiz, tipo.value);
      mostrarPrecio(raiz, premium.checked);
      mostrarEstado(raiz, 'Completa los campos para registrar el producto.');
    });
  });

  formulario.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    if (!formulario.checkValidity()) {
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
        `Producto ${creado.nombre} creado correctamente con estado ${creado.estado}.`,
        'exito',
      );
    } catch (fallo) {
      console.error('No se pudo crear el producto', fallo);
      mostrarEstado(raiz, mensajeFallo(fallo), 'error');
    } finally {
      boton.disabled = false;
    }
  });

  return { formulario, mostrarTipo: (nuevoTipo) => mostrarTipo(raiz, nuevoTipo) };
}
