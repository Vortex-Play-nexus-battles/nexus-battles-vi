/** HU-PRD-001 - Formulario accesible de creación de productos. */
import { crearProducto } from './cliente-productos.js';
import {
  construirSolicitudProducto,
  PARTES_ARMADURA,
  PROTOTIPOS,
  TIPOS_PRODUCTO,
} from './solicitud-producto.js';

const ETIQUETAS_TIPO = {
  HEROE: 'Héroe',
  HABILIDAD: 'Habilidad',
  ARMA: 'Arma',
  ARMADURA: 'Armadura',
  ITEM: 'Ítem',
  EPICA: 'Épica',
};

function opciones(valores, etiquetas = {}) {
  return valores
    .map((valor) => `<option value="${valor}">${etiquetas[valor] || valor}</option>`)
    .join('');
}

function plantilla() {
  return `
    <header class="productos-cabecera">
      <div>
        <p class="productos-cabecera__marca">NEXUS BATTLES VI</p>
        <h1>Crear producto del catálogo</h1>
        <p>Registra un producto con los atributos definidos para su tipo.</p>
      </div>
      <span class="productos-cabecera__insignia">Administración</span>
    </header>

    <form class="producto-formulario" novalidate>
      <section class="producto-seccion" aria-labelledby="datos-generales">
        <h2 id="datos-generales">Datos generales</h2>
        <div class="producto-rejilla">
          <label>Nombre <input name="nombre" maxlength="120" required /></label>
          <label>Tipo
            <select name="tipo" required>${opciones(TIPOS_PRODUCTO, ETIQUETAS_TIPO)}</select>
          </label>
          <label class="producto-campo--ancho">Referencia de imagen
            <input name="imagen" placeholder="Ruta o referencia de la imagen" required />
          </label>
          <label class="producto-campo--ancho">Descripción
            <textarea name="descripcion" rows="4" required></textarea>
          </label>
          <label>Tiraje
            <input name="tiraje" type="number" step="1" value="-1" required />
            <small>-1 significa ilimitado; los demás valores deben ser mayores que cero.</small>
          </label>
          <label class="producto-premium">
            <input name="premium" type="checkbox" /> Producto premium
          </label>
          <label data-precio="creditos">Precio en créditos
            <input name="precioCreditos" type="number" min="0" step="1" value="0" required />
          </label>
          <label data-precio="real" hidden>Precio en moneda real
            <input name="precioMonedaReal" type="number" min="0" step="0.01" value="0" disabled />
          </label>
        </div>
      </section>

      <section class="producto-seccion" aria-labelledby="datos-tipo">
        <h2 id="datos-tipo">Atributos del tipo</h2>
        <div class="producto-tipo" data-tipo="HEROE">
          <label>Prototipo
            <select name="prototipo" required>${opciones(PROTOTIPOS)}</select>
          </label>
        </div>
        <div class="producto-tipo" data-tipo="HABILIDAD" hidden>
          <label>UUID del héroe <input name="heroe" required disabled /></label>
          <label>Costo de poder <input name="costoPoder" type="number" min="1" step="1" required disabled /></label>
          <label>Multiplicador de nivel <input name="multiplicadorNivel" type="number" min="0.000001" step="any" required disabled /></label>
          <label>Turnos de carga <input name="turnosCarga" type="number" min="0" step="1" required disabled /></label>
        </div>
        <div class="producto-tipo" data-tipo="ARMA" hidden>
          <label>Poder de ataque <input name="poderDeAtaque" type="number" min="1" step="1" required disabled /></label>
          <label>Tasa de caída (%) <input name="tasaDeCaida" type="number" min="0" max="100" step="any" required disabled /></label>
        </div>
        <div class="producto-tipo" data-tipo="ARMADURA" hidden>
          <label>Defensa <input name="defensa" type="number" min="1" step="1" required disabled /></label>
          <label>Parte
            <select name="parte" required disabled>${opciones(PARTES_ARMADURA)}</select>
          </label>
          <label>Tasa de caída (%) <input name="tasaDeCaida" type="number" min="0" max="100" step="any" required disabled /></label>
        </div>
        <div class="producto-tipo" data-tipo="ITEM" hidden>
          <label>Efecto <textarea name="efecto" rows="3" required disabled></textarea></label>
          <label>Tasa de caída (%) <input name="tasaDeCaida" type="number" min="0" max="100" step="any" required disabled /></label>
        </div>
        <div class="producto-tipo" data-tipo="EPICA" hidden>
          <label>UUID del héroe <input name="heroe" required disabled /></label>
          <label>Turnos de recarga <input name="turnosRecarga" type="number" min="0" step="1" required disabled /></label>
          <label>Efecto general <textarea name="efectoGeneral" rows="3" required disabled></textarea></label>
          <label>Efecto potenciado <textarea name="efectoPotenciado" rows="3" required disabled></textarea></label>
        </div>
      </section>

      <div id="nexus-rbac-forbidden" class="producto-estado producto-estado--error" role="alert" hidden></div>
      <div class="producto-estado" data-estado="vacio" role="status" aria-live="polite">
        Completa los campos para registrar el producto.
      </div>
      <div class="producto-acciones">
        <button type="reset" class="boton-secundario">Limpiar</button>
        <button type="submit" class="boton-primario">Crear producto</button>
      </div>
    </form>`;
}

function gruposPorTipo(raiz) {
  return [...raiz.querySelectorAll('[data-tipo]')];
}

function mostrarTipo(raiz, tipo) {
  for (const grupo of gruposPorTipo(raiz)) {
    const activo = grupo.dataset.tipo === tipo;
    grupo.hidden = !activo;
    for (const control of grupo.querySelectorAll('input, select, textarea')) {
      control.disabled = !activo;
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
    return fallo.message || 'Revisa los datos ingresados.';
  }
  return fallo?.message || 'No pudimos crear el producto. Inténtalo nuevamente.';
}

/** Monta la vista de creación y delega la autenticación al interceptor común. */
export function montarFormularioProductos(raiz, { crear = crearProducto } = {}) {
  raiz.innerHTML = plantilla();
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
