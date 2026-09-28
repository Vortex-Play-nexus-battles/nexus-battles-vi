/** HU-PRD-008 - Panel de estado del catálogo. */
import { consultarEstadisticasCatalogo } from './cliente-productos.js';
import { montarCatalogoAdmin } from './catalogo-admin.js';
import { h, vaciar } from '../../comun/ui/dom.js';
import { textoDeError } from '../../comun/ui/texto-de-fallo.js';

const TIPOS = [
  ['HEROE', 'Héroes'],
  ['HABILIDAD', 'Habilidades'],
  ['ARMA', 'Armas'],
  ['ARMADURA', 'Armaduras'],
  ['ITEM', 'Ítems'],
  ['EPICA', 'Épicas'],
];

const ESTADOS = [
  ['ACTIVO', 'Activos'],
  ['UNICO', 'Únicos'],
  ['SUSPENDIDO', 'Suspendidos'],
];

function cantidad(valor) {
  const numero = Number(valor);
  return Number.isFinite(numero) && numero >= 0 ? numero : 0;
}

function tarjetasEstado() {
  return ESTADOS.map(([clave, etiqueta]) =>
    h('article', {
      clase: 'panel-tarjeta panel-tarjeta--estado',
      hijos: [
        h('strong', {
          texto: '0',
          datos: { estadoCatalogo: clave },
        }),
        h('span', { texto: etiqueta }),
      ],
    }),
  );
}

function tarjetasTipo() {
  return TIPOS.map(([clave, etiqueta]) =>
    h('article', {
      clase: 'panel-tarjeta panel-tarjeta--tipo',
      hijos: [
        h('span', { texto: etiqueta }),
        h('strong', {
          texto: '0',
          datos: { tipoCatalogo: clave },
        }),
      ],
    }),
  );
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
          h('h1', { texto: 'Estado del catálogo' }),
          h('p', {
            texto: 'Consulta las cantidades actuales de productos por tipo y estado.',
          }),
        ],
      }),
      h('a', {
        clase: 'panel-enlace-crear',
        texto: 'Crear producto',
        atributos: { href: './productos.html' },
      }),
    ],
  });

  const encabezado = h('div', {
    clase: 'panel-catalogo__encabezado',
    hijos: [
      h('div', {
        hijos: [
          h('h2', {
            texto: 'Resumen del catálogo',
            atributos: { id: 'panel-resumen-titulo' },
          }),
          h('p', {
            texto: 'Las cifras se obtienen directamente del servicio de Productos.',
          }),
        ],
      }),
      h('button', {
        clase: 'boton-secundario',
        texto: 'Actualizar cifras',
        datos: { actualizarPanel: '' },
        atributos: { type: 'button' },
      }),
    ],
  });

  const mensaje = h('div', {
    clase: 'panel-mensaje panel-mensaje--carga',
    texto: 'Consultando el catálogo…',
    datos: { panelMensaje: '' },
    atributos: {
      role: 'status',
      'aria-live': 'polite',
    },
  });

  const resumen = h('div', {
    clase: 'panel-resumen',
    hijos: [
      h('article', {
        clase: 'panel-tarjeta panel-tarjeta--total',
        hijos: [
          h('strong', {
            texto: '0',
            datos: { totalCatalogo: '' },
          }),
          h('span', { texto: 'Total de productos' }),
        ],
      }),
      ...tarjetasEstado(),
    ],
  });

  const distribucion = h('section', {
    clase: 'panel-distribucion',
    atributos: { 'aria-labelledby': 'panel-tipos-titulo' },
    hijos: [
      h('h2', {
        texto: 'Distribución por tipo',
        atributos: { id: 'panel-tipos-titulo' },
      }),
      h('div', {
        clase: 'panel-tipos',
        hijos: tarjetasTipo(),
      }),
    ],
  });

  const panel = h('section', {
    clase: 'panel-catalogo',
    atributos: { 'aria-labelledby': 'panel-resumen-titulo' },
    hijos: [encabezado, mensaje, resumen, distribucion],
  });

  // UXC-7 — debajo de las cifras, los productos: verlos, modificarlos,
  // suspenderlos y reactivarlos (la ficha de gestión de cada uno).
  const catalogo = h('section', {
    clase: 'panel-catalogo catalogo-admin',
    datos: { zona: 'catalogo-admin' },
  });

  return [cabecera, panel, catalogo];
}

function mostrarMensaje(raiz, texto, tipo) {
  const mensaje = raiz.querySelector('[data-panel-mensaje]');
  mensaje.textContent = texto;
  mensaje.className = `panel-mensaje panel-mensaje--${tipo}`;
  mensaje.setAttribute('role', tipo === 'error' ? 'alert' : 'status');
}

function pintarResumen(raiz, resumen) {
  raiz.querySelector('[data-total-catalogo]').textContent = String(cantidad(resumen?.total));

  for (const [clave] of ESTADOS) {
    raiz.querySelector(`[data-estado-catalogo="${clave}"]`).textContent = String(
      cantidad(resumen?.porEstado?.[clave]),
    );
  }

  for (const [clave] of TIPOS) {
    raiz.querySelector(`[data-tipo-catalogo="${clave}"]`).textContent = String(
      cantidad(resumen?.porTipo?.[clave]),
    );
  }
}

function mensajeDeError(fallo) {
  if (fallo?.codigo === 'SESION_REQUERIDA' || fallo?.status === 401) {
    return 'Tu sesión no está disponible. Inicia sesión para consultar el catálogo.';
  }

  if (fallo?.status === 403) {
    return 'No tienes permiso para consultar el estado del catálogo.';
  }

  return textoDeError(fallo, 'No se pudieron cargar las cifras del catálogo.');
}

/**
 * Monta el panel y devuelve una función para actualizarlo.
 *
 * @param {HTMLElement} raiz contenedor de la vista.
 * @param {{consultar?: Function}} dependencias inyectables para pruebas.
 */
export function montarPanelCatalogo(
  raiz,
  { consultar = consultarEstadisticasCatalogo, fetchImpl, conCatalogo = true } = {},
) {
  vaciar(raiz).append(...crearVista());
  const botonActualizar = raiz.querySelector('[data-actualizar-panel]');

  const actualizar = async () => {
    botonActualizar.disabled = true;
    raiz.setAttribute('aria-busy', 'true');
    mostrarMensaje(raiz, 'Consultando el catálogo…', 'carga');

    try {
      const resumen = await consultar();
      pintarResumen(raiz, resumen);
      mostrarMensaje(raiz, 'Cifras actualizadas correctamente.', 'exito');
    } catch (fallo) {
      console.error('No se pudo consultar el estado del catálogo', fallo);
      mostrarMensaje(raiz, mensajeDeError(fallo), 'error');
    } finally {
      botonActualizar.disabled = false;
      raiz.setAttribute('aria-busy', 'false');
    }
  };

  botonActualizar.addEventListener('click', () => {
    void actualizar();
  });

  const cargaInicial = actualizar();

  const zonaCatalogo = raiz.querySelector('[data-zona="catalogo-admin"]');
  const catalogo =
    conCatalogo && zonaCatalogo
      ? montarCatalogoAdmin(zonaCatalogo, {
          fetchImpl,
          // Suspender o modificar cambia las cifras de arriba.
          alCambiarCatalogo: () => void actualizar(),
        })
      : null;

  return { actualizar, cargaInicial, catalogo };
}
