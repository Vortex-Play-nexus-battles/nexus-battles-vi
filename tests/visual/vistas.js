/**
 * Catálogo de las vistas del producto — UX-R2.1.
 *
 * La lista se comprueba contra el disco en `auditoria-visual.spec.js`: si
 * alguien añade una vista y no la apunta aquí, la prueba lo dice. Una lista de
 * cobertura que se queda corta en silencio no sirve de nada.
 *
 * `acceso` dice quién puede verla de verdad, no quién debería:
 *   - `publica`  — se ve sin sesión
 *   - `jugador`  — exige sesión
 *   - `admin`    — exige sesión y además un rol administrativo
 */

/**
 * Prefijo de la parte web dentro de la raíz servida.
 *
 * Las vistas enlazan el kit con `../../../../shared/ui-kit/...`, que sale de
 * `src/` y llega a la raíz del monorepo. Por eso el servidor tiene que servir
 * **la raíz**, no `src/`: es lo que hace el borde real (`root /srv/nexus` con
 * `location /frontend/` y `location /shared/` en `borde-dev.conf`). Servir
 * `src/` deja los tres CSS del kit en 404 y la página se pinta sin estilos.
 */
export const PREFIJO_WEB = 'frontend/app-web/src';

/** Resoluciones de la matriz. Las cinco del plan UX-R2. */
export const PANTALLAS = Object.freeze([
  { nombre: 'desktop', ancho: 1440, alto: 900 },
  { nombre: 'laptop', ancho: 1280, alto: 800 },
  { nombre: 'laptop-min', ancho: 1024, alto: 768 },
  { nombre: 'tablet', ancho: 768, alto: 1024 },
  { nombre: 'movil', ancho: 375, alto: 812 },
]);

/** Objetivo táctil mínimo en móvil (WCAG 2.5.5 nivel AAA es 44×44). */
export const OBJETIVO_TACTIL = 44;

export const VISTAS = Object.freeze([
  // --- Entrada -----------------------------------------------------------
  { id: 'login', ruta: 'cuentas/login.html', acceso: 'publica', grupo: 'entrada' },
  { id: 'registro', ruta: 'cuentas/registro.html', acceso: 'publica', grupo: 'entrada' },
  {
    id: 'restablecer-solicitar',
    ruta: 'cuentas/restablecer-solicitar.html',
    acceso: 'publica',
    grupo: 'entrada',
  },
  {
    id: 'restablecer-confirmar',
    ruta: 'cuentas/restablecer-confirmar.html',
    acceso: 'publica',
    grupo: 'entrada',
  },

  // --- Home y cuenta ------------------------------------------------------
  { id: 'home', ruta: 'cuentas/index.html', acceso: 'jugador', grupo: 'cuenta' },
  { id: 'perfil', ruta: 'cuentas/perfil.html', acceso: 'jugador', grupo: 'cuenta' },
  {
    id: 'historial-transacciones',
    ruta: 'cuentas/historial-transacciones.html',
    acceso: 'jugador',
    grupo: 'cuenta',
  },
  { id: 'mis-cofres', ruta: 'cuentas/mis-cofres.html', acceso: 'jugador', grupo: 'cuenta' },

  // --- Batallas -----------------------------------------------------------
  {
    id: 'batallas',
    ruta: 'plataforma/salas-partidas/batallas.html',
    acceso: 'jugador',
    grupo: 'batalla',
  },
  {
    id: 'crear-sala',
    ruta: 'plataforma/salas-partidas/crear-sala.html',
    acceso: 'jugador',
    grupo: 'batalla',
  },
  {
    id: 'sala-batalla',
    ruta: 'plataforma/salas-partidas/sala-batalla.html',
    acceso: 'jugador',
    grupo: 'batalla',
  },
  {
    id: 'validacion-heroe',
    ruta: 'plataforma/salas-partidas/validacion-heroe.html',
    acceso: 'jugador',
    grupo: 'batalla',
  },
  { id: 'chat', ruta: 'plataforma/salas-partidas/chat.html', acceso: 'jugador', grupo: 'batalla' },

  // --- Colección ----------------------------------------------------------
  {
    id: 'inventario',
    ruta: 'contenido/inventario/inventario.html',
    acceso: 'jugador',
    grupo: 'coleccion',
  },
  {
    id: 'productos',
    ruta: 'contenido/productos/productos.html',
    acceso: 'publica',
    grupo: 'coleccion',
  },

  // --- Mercado ------------------------------------------------------------
  { id: 'subastas', ruta: 'cuentas/subastas.html', acceso: 'publica', grupo: 'mercado' },
  { id: 'pujas', ruta: 'cuentas/pujas.html', acceso: 'publica', grupo: 'mercado' },
  {
    id: 'publicar-subasta',
    ruta: 'cuentas/publicar-subasta.html',
    acceso: 'jugador',
    grupo: 'mercado',
  },
  { id: 'tienda', ruta: 'cuentas/tienda.html', acceso: 'jugador', grupo: 'mercado' },

  // --- Torneos ------------------------------------------------------------
  { id: 'torneos', ruta: 'plataforma/torneos/torneos.html', acceso: 'publica', grupo: 'torneo' },

  // --- Comunidad ----------------------------------------------------------
  {
    id: 'publicar-comentario',
    ruta: 'plataforma/comentarios/publicar-comentario.html',
    acceso: 'jugador',
    grupo: 'comunidad',
  },
  {
    id: 'notificaciones',
    ruta: 'plataforma/notificaciones/notificaciones.html',
    acceso: 'jugador',
    grupo: 'comunidad',
  },
  {
    id: 'mis-sanciones',
    ruta: 'plataforma/moderacion-sanciones/mis-sanciones.html',
    acceso: 'jugador',
    grupo: 'comunidad',
  },

  // --- Administración -----------------------------------------------------
  {
    id: 'gestion-usuarios',
    ruta: 'cuentas/gestion-usuarios.html',
    acceso: 'admin',
    grupo: 'admin',
  },
  {
    id: 'crear-cuenta-admin',
    ruta: 'cuentas/crear-cuenta-admin.html',
    acceso: 'admin',
    grupo: 'admin',
  },
  { id: 'auditoria', ruta: 'cuentas/auditoria.html', acceso: 'admin', grupo: 'admin' },
  {
    id: 'lista-negra-admin',
    ruta: 'plataforma/moderacion-sanciones/lista-negra-admin.html',
    acceso: 'admin',
    grupo: 'admin',
  },
  {
    id: 'sanciones-admin',
    ruta: 'plataforma/moderacion-sanciones/sanciones-admin.html',
    acceso: 'admin',
    grupo: 'admin',
  },
  {
    id: 'parametros-admin',
    ruta: 'plataforma/admin-parametros/parametros-admin.html',
    acceso: 'admin',
    grupo: 'admin',
  },
  {
    id: 'panel-metricas',
    ruta: 'plataforma/metricas-plataforma/panel-metricas.html',
    acceso: 'admin',
    grupo: 'admin',
  },
  {
    id: 'tablero-tecnico',
    ruta: 'plataforma/metricas-plataforma/tablero-tecnico.html',
    acceso: 'admin',
    grupo: 'admin',
  },
]);

/** Las que se pueden fotografiar sin sesión. */
export const PUBLICAS = VISTAS.filter((v) => v.acceso === 'publica');

/** Las que necesitan que el arnés consiga identidad. */
export const PRIVADAS = VISTAS.filter((v) => v.acceso !== 'publica');
