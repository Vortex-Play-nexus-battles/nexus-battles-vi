/** HU-PRD-008 - Panel de estado del catálogo. */
import { consultarEstadisticasCatalogo } from './cliente-productos.js';

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
  return ESTADOS.map(
    ([clave, etiqueta]) => `
      <article class="panel-tarjeta panel-tarjeta--estado">
        <strong data-estado-catalogo="${clave}">0</strong>
        <span>${etiqueta}</span>
      </article>`,
  ).join('');
}

function tarjetasTipo() {
  return TIPOS.map(
    ([clave, etiqueta]) => `
      <article class="panel-tarjeta panel-tarjeta--tipo">
        <span>${etiqueta}</span>
        <strong data-tipo-catalogo="${clave}">0</strong>
      </article>`,
  ).join('');
}

function plantilla() {
  return `
    <header class="productos-cabecera">
      <div>
        <p class="productos-cabecera__marca">NEXUS BATTLES VI</p>
        <h1>Estado del catálogo</h1>
        <p>Consulta las cantidades actuales de productos por tipo y estado.</p>
      </div>
      <a class="panel-enlace-crear" href="./productos.html">Crear producto</a>
    </header>

    <section class="panel-catalogo" aria-labelledby="panel-resumen-titulo">
      <div class="panel-catalogo__encabezado">
        <div>
          <h2 id="panel-resumen-titulo">Resumen del catálogo</h2>
          <p>Las cifras se obtienen directamente del servicio de Productos.</p>
        </div>
        <button type="button" class="boton-secundario" data-actualizar-panel>
          Actualizar cifras
        </button>
      </div>

      <div
        class="panel-mensaje panel-mensaje--carga"
        data-panel-mensaje
        role="status"
        aria-live="polite"
      >
        Consultando el catálogo…
      </div>

      <div class="panel-resumen">
        <article class="panel-tarjeta panel-tarjeta--total">
          <strong data-total-catalogo>0</strong>
          <span>Total de productos</span>
        </article>
        ${tarjetasEstado()}
      </div>

      <section class="panel-distribucion" aria-labelledby="panel-tipos-titulo">
        <h2 id="panel-tipos-titulo">Distribución por tipo</h2>
        <div class="panel-tipos">
          ${tarjetasTipo()}
        </div>
      </section>
    </section>`;
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

  return fallo?.message || 'No se pudieron cargar las cifras del catálogo.';
}

/**
 * Monta el panel y devuelve una función para actualizarlo.
 *
 * @param {HTMLElement} raiz contenedor de la vista.
 * @param {{consultar?: Function}} dependencias inyectables para pruebas.
 */
export function montarPanelCatalogo(raiz, { consultar = consultarEstadisticasCatalogo } = {}) {
  raiz.innerHTML = plantilla();
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

  return { actualizar, cargaInicial };
}
