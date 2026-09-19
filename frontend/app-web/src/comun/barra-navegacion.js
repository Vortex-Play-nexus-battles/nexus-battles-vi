/**
 * HU-INV-004 - Barra superior de navegacion.
 *
 * Fuente: *Proyecto Integrador II*, secciones 7.1-7.1.1, pp. 34-35.
 *
 * Vive en `src/comun/` porque la seccion 10.2 de la guia de la empresa nombra
 * la cabecera y el menu como componentes compartidos: "lo que se repite en dos
 * vistas sube a src/comun/... no se copian y pegan".
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

/**
 * A que vista lleva cada seccion, relativo a ESTE modulo (`src/comun/`).
 *
 * ## Por que existe
 *
 * Las rutas de arriba (`/jugar`, `/inventario`, ...) son identificadores
 * logicos, no URLs: ninguna corresponde a un archivo del repo ni a una
 * `location` del borde. Hasta ahora la navegacion por omision las mandaba tal
 * cual a `location.href`, asi que los cinco accesos daban 404 en las nueve
 * vistas que montan la barra sin pasar su propio `navegar`. La unica que
 * funcionaba era `cuentas/pujas.html`, que tenia el mapeo copiado dentro.
 *
 * Aqui se mantiene ese mismo mapeo, que ya estaba acordado en esa vista, y se
 * sube al componente compartido, que es donde la guia de la empresa dice que
 * vive lo que se repite.
 *
 * ## Por que relativo a este modulo y no absoluto
 *
 * La barra se monta desde tres carpetas distintas (`cuentas/`,
 * `plataforma/salas-partidas/`, `contenido/inventario/`), asi que una ruta
 * relativa al documento acertaria en unas y fallaria en otras. Y una absoluta
 * tampoco sirve: el borde sirve la aplicacion bajo `/frontend/app-web/src/`
 * mientras que `npm run dev` la sirve desde `src/`. Resolver contra
 * `import.meta.url` acierta en los dos, porque este archivo siempre esta en
 * `src/comun/`.
 *
 * `Misiones` y `Torneo` todavia no tienen vista; apuntan al menu y a batallas
 * respectivamente, que es lo que ya hacia `pujas.html`. Cuando existan, se
 * cambia aqui y las nueve vistas se enteran a la vez.
 */
const DESTINOS = Object.freeze({
  '/jugar': '../plataforma/salas-partidas/batallas.html',
  '/misiones': '../cuentas/index.html',
  '/torneo': '../plataforma/salas-partidas/batallas.html',
  '/inventario': '../contenido/inventario/inventario.html',
  '/subasta': '../cuentas/subastas.html',
  '/cuenta': '../cuentas/perfil.html',
});

/**
 * URL real de una seccion.
 *
 * Una ruta que no este en el mapa se devuelve tal cual: quien pase la suya
 * propia por `navegar` sigue mandando.
 *
 * @param {string} ruta la `ruta` de una seccion
 * @param {string} [base] inyectable para las pruebas
 * @returns {string}
 */
export function destinoDe(ruta, base = import.meta.url) {
  const relativo = DESTINOS[ruta];
  return relativo ? new URL(relativo, base).href : ruta;
}

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
  navegar = (ruta) => {
    globalThis.location.href = destinoDe(ruta);
  },
} = {}) {
  const barra = document.createElement('nav');
  barra.className = 'barra';
  barra.setAttribute('aria-label', 'Navegacion principal');

  barra.appendChild(construirBusqueda());

  const lista = document.createElement('ul');
  lista.className = 'barra__accesos';

  const panelCuenta = construirPanelCuenta(sesion);

  for (const seccion of SECCIONES) {
    lista.appendChild(construirAcceso(seccion, seccionActiva, navegar, panelCuenta));
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
