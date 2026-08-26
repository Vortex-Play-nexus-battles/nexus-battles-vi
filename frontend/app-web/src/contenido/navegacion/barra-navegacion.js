/**
 * HU-INV-004 - Barra superior de navegacion.
 *
 * Fuente: *Proyecto Integrador II*, secciones 7.1-7.1.1, pp. 34-35.
 *
 * El criterio 1 la pide en "cualquier pantalla del alcance del grupo", asi
 * que vive en `contenido/`. Si el equipo levanta `shared/ui-kit` y la barra
 * pasa a servir a los tres grupos, se muda sin cambiar su interfaz.
 *
 * La sesion entra por parametro y **por omision es visitante**: el contrato
 * de identidad es `HU-INF-009` y todavia no existe. Asumir visitante es la
 * opcion segura — un jugador mal reconocido veria opciones que no le tocan.
 */

/** Las seis secciones que enumera el criterio 1, en su orden. */
export const SECCIONES = Object.freeze([
  { id: 'jugar', etiqueta: 'Jugar online', ruta: '/jugar' },
  { id: 'misiones', etiqueta: 'Misiones', ruta: '/misiones' },
  { id: 'torneo', etiqueta: 'Torneo', ruta: '/torneo' },
  { id: 'inventario', etiqueta: 'Mi inventario', ruta: '/inventario' },
  { id: 'subasta', etiqueta: 'Subasta', ruta: '/subasta' },
  { id: 'cuenta', etiqueta: 'Mi Cuenta', ruta: '/cuenta' },
]);

/** Lo unico que se le ofrece a quien no ha iniciado sesion. */
const OPCIONES_VISITANTE = ['Registrarse'];

/** Opciones de un jugador con sesion. */
const OPCIONES_JUGADOR = ['Mi perfil', 'Cerrar sesion'];

/**
 * Construye la barra superior.
 *
 * @param {object} opciones
 * @param {string} [opciones.seccionActiva] id de la seccion en curso.
 * @param {{autenticado: boolean}} [opciones.sesion] sesion del visitante.
 * @param {(ruta: string) => void} [opciones.navegar] como se navega.
 * @returns {HTMLElement} la barra lista para insertar.
 */
export function construirBarra({
  seccionActiva = null,
  sesion = { autenticado: false },
  navegar = (ruta) => { globalThis.location.href = ruta; },
} = {}) {

  const barra = document.createElement('nav');
  barra.className = 'barra';
  barra.setAttribute('aria-label', 'Navegacion principal');

  barra.appendChild(construirBusqueda());

  const lista = document.createElement('ul');
  lista.className = 'barra__accesos';

  const panelCuenta = construirPanelCuenta(sesion);

  for (const seccion of SECCIONES) {
    lista.appendChild(construirAcceso(
      seccion, seccionActiva, navegar, panelCuenta));
  }

  barra.append(lista, panelCuenta);
  return barra;
}

function construirBusqueda() {
  const caja = document.createElement('div');
  caja.className = 'barra__busqueda';

  const campo = document.createElement('input');
  campo.type = 'search';
  campo.placeholder = 'Buscar productos';
  // El comportamiento de la busqueda es HU-INV-002; aqui solo esta el campo.
  campo.setAttribute('aria-label', 'Buscar productos');

  caja.appendChild(campo);
  return caja;
}

function construirAcceso(seccion, seccionActiva, navegar, panelCuenta) {
  const celda = document.createElement('li');

  const acceso = document.createElement('button');
  acceso.type = 'button';
  acceso.className = 'barra__acceso';
  acceso.dataset.seccion = seccion.id;
  acceso.textContent = seccion.etiqueta;

  if (seccion.id === seccionActiva) {
    acceso.classList.add('barra__acceso--activo');
    acceso.setAttribute('aria-current', 'page');
  }

  acceso.addEventListener('click', () => {
    if (seccion.id === 'cuenta') {
      // Mi Cuenta despliega sus opciones en sitio; no saca al visitante
      // de la pantalla en la que esta.
      alternarPanel(panelCuenta);
      return;
    }
    cerrarPanel(panelCuenta);
    navegar(seccion.ruta);
  });

  celda.appendChild(acceso);
  return celda;
}

function construirPanelCuenta(sesion) {
  const panel = document.createElement('div');
  panel.className = 'barra__panel-cuenta';
  panel.hidden = true;

  const opciones = sesion?.autenticado ? OPCIONES_JUGADOR : OPCIONES_VISITANTE;
  for (const etiqueta of opciones) {
    const opcion = document.createElement('button');
    opcion.type = 'button';
    opcion.className = 'barra__opcion-cuenta';
    opcion.textContent = etiqueta;
    panel.appendChild(opcion);
  }
  return panel;
}

function alternarPanel(panel) {
  panel.hidden = !panel.hidden;
}

function cerrarPanel(panel) {
  panel.hidden = true;
}
