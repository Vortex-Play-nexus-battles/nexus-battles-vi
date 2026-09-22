# Auditoría UX de las vistas — navegación, sesión y consistencia (R3, 21-sep-2026)

Origen: capturas de `batallas.html`, `subastas.html` y la barra con «Mi
Cuenta / Registrarse» tomadas en el host de dev el 21-sep. No se asumió la
causa: se comprobó en el código de las 25 vistas de `frontend/app-web/src`.

## Diagnóstico (comprobado en código, no supuesto)

| Síntoma en la captura | Causa real | Dónde |
|---|---|---|
| «Registrarse» dentro de Mi Cuenta con sesión iniciada (`demo_grupo6`) | `construirBarra()` recibía la sesión **por parámetro** y por omisión era visitante. Cuatro vistas la montaban **sin pasarla**: `subastas.js:38`, `inventario.html:35`, `auditoria.html:111`, `tienda.html:25`. No era una variante de página: era un defecto. | `src/comun/barra-navegacion.js` (eliminado) |
| Dos encabezados distintos (orden, buscador, estado activo, avatar) | Dos implementaciones: `construirBarra` (`.barra`, CSS propio) en 12 vistas de cuentas/inventario/subastas, y un `<header class="cabecera">` **copiado a mano** en 7 vistas de plataforma; 3 vistas (productos, lista negra, panel de métricas) sin barra; 4 públicas (login, registro, restablecer) sin cabecera. | 25 vistas |
| Sesión confusa | «Cerrar sesión» solo existía en `index.js` y en `pujas.html` (código copiado). Las opciones del panel de la barra compartida **no tenían manejador**: pulsar «Mi perfil» o «Cerrar sesión» no hacía nada. Ninguna vista comprobaba la caducidad del token. Solo 4 vistas privadas redirigían al login sin sesión (perfil, historial, cofres, pujas); las de plataforma leían el token y seguían. | `index.js`, `pujas.html`, `*.js` |
| Buscador «Buscar productos» en todas partes | La barra lo pintaba siempre, aunque solo inventario (HU-INV-002) y subastas tienen búsqueda; en las demás vistas no hacía nada. | `construirBarra` |
| Densidades y jerarquías distintas | Las vistas de cuentas no cargaban el ui-kit compartido (`tokens.css`, `componentes.css`) y usaban su `tema-cuentas.css`; las de plataforma sí. Dos sistemas visuales en la misma aplicación. | `<link>` de cada vista |

## Matriz de vistas (estado ANTES de R3 → DESPUÉS)

Leyenda: privada = exige sesión; guard = redirige al login sin sesión.
«Figma»: el archivo del proyecto (`PXanKCqsAYLemJhyTyTTHk`) solo tiene la
página «00 · Resumen + Fundamentos» (paleta y tipografía); no hay pantallas
de navegación ni de cuentas. La cabecera es el componente `.cabecera` del
ui-kit derivado de esos fundamentos.

| Vista | Ruta (`src/`) | Sesión | Barra antes → después | Activo | Acción primaria | Volver | Estados (carga/vacío/error) | Figma |
|---|---|---|---|---|---|---|---|---|
| Login | `cuentas/login.html` | pública | ninguna → shell sin sesión | — | Iniciar sesión | — | error | fundamentos |
| Registro | `cuentas/registro.html` | pública | ninguna → shell | — | Crear cuenta | ← login | error | fundamentos |
| Restablecer (solicitar/confirmar) | `cuentas/restablecer-*.html` | pública | ninguna → shell | — | Enviar / Confirmar | ← login | error | fundamentos |
| Inicio | `cuentas/index.html` | privada, sin guard → **guard en HU-UX-001 CA-04** (pendiente: la vista es de G4) | `construirBarra` con sesión → shell | cuenta | tarjetas por rol | — | vacío | — |
| Mi perfil | `cuentas/perfil.html` | privada, guard | construirBarra → shell | cuenta | Guardar | sí | carga/vacío/error | — |
| Gestión de usuarios | `cuentas/gestion-usuarios.html` | admin, sin guard | construirBarra → shell | cuenta | buscar/editar | sí | vacío/error | — |
| Crear cuenta admin | `cuentas/crear-cuenta-admin.html` | admin, sin guard | construirBarra → shell | cuenta | Crear | sí | carga/error | — |
| Auditoría | `cuentas/auditoria.html` | super admin | **sin sesión → Registrarse** → shell + guard | cuenta | filtrar | no | carga/vacío/error | — |
| Historial de transacciones | `cuentas/historial-transacciones.html` | privada, guard | construirBarra → shell + guard | cuenta | paginar | sí | carga/vacío/error | — |
| Mis cofres | `cuentas/mis-cofres.html` | privada, guard | construirBarra → shell + guard | cuenta | paginar | sí | carga/vacío/error | — |
| Tienda | `cuentas/tienda.html` | privada | **sin sesión → Registrarse** → shell + guard | cuenta | Comprar | no | carga/vacío/error | — |
| Subastas | `cuentas/subastas.html` | pública (pujar exige sesión) | **sin sesión → Registrarse** → shell | subasta | pujar | no | carga/vacío/error (502 en dev: ms-subastas no desplegado) | — |
| Pujas / detalle | `cuentas/pujas.html` | privada, guard | construirBarra + copia de rutas → shell | subasta | Pujar | no | carga/vacío/error | — |
| Inventario | `contenido/inventario/inventario.html` | privada | **sin sesión → Registrarse** → shell + guard | inventario | equipar/buscar | no | carga/vacío/error | — |
| Productos (admin catálogo) | `contenido/productos/productos.html` | admin | ninguna → shell | — | crear/editar | no | vacío/error | — |
| Batallas | `plataforma/salas-partidas/batallas.html` | privada | cabecera copiada → shell + guard | jugar | Crear sala / Entrar | sí | carga/vacío/error/degradación | — |
| Crear sala | `…/crear-sala.html` | privada | copiada → shell + guard | jugar | Crear sala | sí | carga/error/degradación | — |
| Validación de héroe | `…/validacion-heroe.html` | privada | copiada → shell + guard | jugar | Verificar | sí | carga/error/degradación | — |
| Sala de batalla | `…/sala-batalla.html` | privada | copiada → shell + guard | jugar | Atacar / Iniciar | sí | carga/error/degradación | RF-JUE-017 (#494) |
| Chat | `…/chat.html` | privada | copiada → shell + guard | jugar | Enviar | sí | vacío/error | — |
| Comentar producto | `plataforma/comentarios/publicar-comentario.html` | privada | copiada → shell + guard | subasta | Publicar | no | carga/vacío/error | — |
| Notificaciones | `plataforma/notificaciones/notificaciones.html` | privada | copiada → shell + guard (campana abre el panel) | — | marcar leída | sí | carga/vacío/error | — |
| Lista negra (admin) | `plataforma/moderacion-sanciones/lista-negra-admin.html` | admin | ninguna → shell + guard | cuenta | añadir/quitar | no | carga/vacío/error | — |
| Panel de métricas | `plataforma/metricas-plataforma/panel-metricas.html` | admin | ninguna → shell + guard | cuenta | consultar | no | carga/vacío/error | — |

Todas las vistas tienen `<meta name="viewport">`. Ninguna tenía la
cabecera responsiva por debajo de 860 px; ahora la navegación baja a una
segunda fila sin perder destinos (RNF-USA-001).

## Arquitectura de información

Se mantiene el orden de las **seis secciones que fija RF-INV-004 (§7.1 del
documento fuente)**: Jugar online · Misiones · Torneo · Mi inventario ·
Subasta · Mi Cuenta. No se inventaron destinos: Misiones y Torneo siguen
visibles pero marcadas «todavía no publicada» (HU-MIS de G2, HU-TOR-008 #493)
hasta que exista su vista. Tienda, historial, cofres y las vistas
administrativas cuelgan de «Mi Cuenta» según el rol.

## Qué hace el shell (`src/comun/cabecera-app.js`)

- Marca, navegación con estado activo y `aria-current`, destinos pendientes
  sin `href`.
- Sesión leída del login y del `exp` del token: sin sesión → «Iniciar sesión»
  y «Registrarse»; con sesión → avatar con inicial, apodo, rol si no es
  jugador, campana de notificaciones y menú de cuenta con **cerrar sesión**
  (borra las cuatro claves y vuelve al login). Un token caducado se trata
  como sin sesión y se dice.
- `exigirSesion()` para vistas privadas: al login con `?volver=` (solo rutas
  del propio origen; `login.js` vuelve allí después).
- Buscador solo cuando la vista lo pide (`buscador: {alBuscar}`).
- Marcado del ui-kit (`.cabecera`, `.menu`, `.boton`, iconos del sprite);
  las vistas de cuentas cargan ahora `tokens.css` y `componentes.css`.

## Lo que queda (fuera de R3, con dueño)

- Guard en `cuentas/index.html`, `gestion-usuarios`, `crear-cuenta-admin`,
  `productos` (vistas de G4/G2): criterio CA-04 de HU-UX-001, coordinar.
- Contador de notificaciones no leídas en la campana de todas las vistas
  (hoy solo en `notificaciones.html`): HU-NOT-006 #32 + shell.
- Saldo de créditos en la cabecera (`data-zona="creditos"` oculto): HU-JUE-012 #470.
- Interfaz de combate como juego (>80 % campo, vistas de inicio/fin): HU-JUE-017 #494.
- Migrar `tema-cuentas.css` y `login.css` a los tokens del ui-kit (mismo
  color de fondo y tipografía en toda la aplicación): RNF-USA-002, siguiente
  incremento de HU-UX-001.
