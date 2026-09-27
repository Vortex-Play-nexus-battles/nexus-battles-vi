# Matriz de acceso a las vistas — UX-R3.0

**Fuente de verdad ejecutable:** `frontend/app-web/src/comun/acceso.js` (`MATRIZ`).
Este documento la explica; no la duplica. `acceso.test.js` comprueba contra el disco
que **toda** vista del repositorio está en la matriz y que **toda** entrada de la
matriz existe, así que una vista nueva sin política de acceso rompe la compuerta.

## Por qué existe

Hasta UX-R2 la política de acceso vivía repartida:

- Las ocho vistas de administración llamaban a `exigirSesion()` **y nada más**. Un
  JUGADOR que escribiera `cuentas/gestion-usuarios.html` en la barra del navegador
  veía la pantalla administrativa entera. La API le negaba los datos, pero la
  interfaz ya le había enseñado qué columnas tiene y qué acciones ofrece. No
  aparecer en la navegación no es una comprobación: es un escondite.
- `ROLES_DE_ADMINISTRACION` estaba escrito cuatro veces (`cabecera-app.js`,
  `parametros.js`, `sanciones.js`, `torneos.js`) con **dos significados distintos**:
  en unos sitios incluía `MODERADOR` y en otros no.

## Roles — RF-RBAC-001

Los cuatro del documento fuente (§7.3.1, p. 38), ni uno más:

| Rol | Alcance (Tabla 24, p. 38) |
|---|---|
| `JUGADOR` | Juega, gestiona su inventario, comenta y puja. |
| `MODERADOR` | Además: modera comentarios, advierte y suspende temporalmente. |
| `ADMINISTRADOR` | Además: gestiona usuarios y productos, banea, ajusta parámetros. |
| `SUPER_ADMINISTRADOR` | Además: crea administradores (RF-RBAC-003) y lee la auditoría. |

Conjuntos en `acceso.js`, con nombres que dicen a quién abarcan:

- `ROLES_DE_MODERACION` = MODERADOR + ADMINISTRADOR + SUPER_ADMINISTRADOR
- `ROLES_DE_ADMINISTRACION` = ADMINISTRADOR + SUPER_ADMINISTRADOR
- `ROLES_DE_SUPERADMINISTRACION` = SUPER_ADMINISTRADOR

## Veredictos

| Veredicto | Qué pasa | Cuándo |
|---|---|---|
| `VISIBLE` | La vista se carga. | Nivel satisfecho. |
| `REDIRIGE` | Al login, con `?volver=` y `?motivo=`. | Falta sesión o caducó. |
| `DENEGADA` | Se pinta «No tienes acceso a esta sección» y una salida. | Hay sesión, el rol no alcanza. |

La diferencia entre `REDIRIGE` y `DENEGADA` es deliberada (§17): rebotar al login a
quien ya tiene una sesión válida le sugiere que su sesión está rota cuando no lo está.

## La matriz

`ANON` = visitante sin sesión · `JUG` = jugador · `MOD` = moderador ·
`ADM` = administrador · `SUP` = super administrador.
`V` = visible · `→` = redirige al login · `✗` = denegada con explicación.

### Portal de entrada — armazón `publico`

| Vista | Ruta | ANON | JUG | MOD | ADM | SUP |
|---|---|:-:|:-:|:-:|:-:|:-:|
| login | `cuentas/login.html` | V | V | V | V | V |
| registro | `cuentas/registro.html` | V | V | V | V | V |
| restablecer-solicitar | `cuentas/restablecer-solicitar.html` | V | V | V | V | V |
| restablecer-confirmar | `cuentas/restablecer-confirmar.html` | V | V | V | V | V |

### Vitrinas públicas — armazón `jugador`

RF-INV-008 da como actor principal «Jugador; **Visitante**»: las subastas y el
cuadro de torneos se miran sin cuenta. *Operar* sobre ellos —pujar, inscribirse—
sí exige sesión, y eso lo comprueba cada acción, no la pantalla.

| Vista | Ruta | ANON | JUG | MOD | ADM | SUP |
|---|---|:-:|:-:|:-:|:-:|:-:|
| subastas | `cuentas/subastas.html` | V | V | V | V | V |
| pujas | `cuentas/pujas.html` | V | V | V | V | V |
| torneos | `plataforma/torneos/torneos.html` | V | V | V | V | V |

### Jugador — armazón `jugador`

| Vista | Ruta | ANON | JUG | MOD | ADM | SUP |
|---|---|:-:|:-:|:-:|:-:|:-:|
| home | `cuentas/index.html` | → | V | V | V | V |
| perfil | `cuentas/perfil.html` | → | V | V | V | V |
| historial-transacciones | `cuentas/historial-transacciones.html` | → | V | V | V | V |
| mis-cofres | `cuentas/mis-cofres.html` | → | V | V | V | V |
| inventario | `contenido/inventario/inventario.html` | → | V | V | V | V |
| tienda | `cuentas/tienda.html` | → | V | V | V | V |
| publicar-subasta | `cuentas/publicar-subasta.html` | → | V | V | V | V |
| batallas | `plataforma/salas-partidas/batallas.html` | → | V | V | V | V |
| crear-sala | `plataforma/salas-partidas/crear-sala.html` | → | V | V | V | V |
| sala-batalla | `plataforma/salas-partidas/sala-batalla.html` | → | V | V | V | V |
| validacion-heroe | `plataforma/salas-partidas/validacion-heroe.html` | → | V | V | V | V |
| chat | `plataforma/salas-partidas/chat.html` | → | V | V | V | V |
| publicar-comentario | `plataforma/comentarios/publicar-comentario.html` | → | V | V | V | V |
| notificaciones | `plataforma/notificaciones/notificaciones.html` | → | V | V | V | V |
| mis-sanciones | `plataforma/moderacion-sanciones/mis-sanciones.html` | → | V | V | V | V |

### Trastienda — armazón `admin`

| Vista | Ruta | Nivel | ANON | JUG | MOD | ADM | SUP |
|---|---|---|:-:|:-:|:-:|:-:|:-:|
| consola | `plataforma/consola/consola.html` | moderación | → | ✗ | V | V | V |
| sanciones-admin | `plataforma/moderacion-sanciones/sanciones-admin.html` | moderación | → | ✗ | V | V | V |
| lista-negra-admin | `plataforma/moderacion-sanciones/lista-negra-admin.html` | moderación | → | ✗ | V | V | V |
| gestion-usuarios | `cuentas/gestion-usuarios.html` | administración | → | ✗ | ✗ | V | V |
| productos | `contenido/productos/productos.html` | administración | → | ✗ | ✗ | V | V |
| parametros-admin | `plataforma/admin-parametros/parametros-admin.html` | administración | → | ✗ | ✗ | V | V |
| panel-metricas | `plataforma/metricas-plataforma/panel-metricas.html` | administración | → | ✗ | ✗ | V | V |
| tablero-tecnico | `plataforma/metricas-plataforma/tablero-tecnico.html` | administración | → | ✗ | ✗ | V | V |
| crear-cuenta-admin | `cuentas/crear-cuenta-admin.html` | super | → | ✗ | ✗ | ✗ | V |
| auditoria | `cuentas/auditoria.html` | super | → | ✗ | ✗ | ✗ | V |

**Por qué `productos` no es una vitrina (UX-R3.5):**

`contenido/productos/productos.html` se llama «productos» y lo parecía. No lo es:
es el **formulario de alta del catálogo**. El servidor ya lo sabía —
`services/contenido/productos/.../SeguridadConfig.java` declara
`POST /api/v1/productos` como `hasAnyRole("ADMINISTRADOR", "SUPER_ADMINISTRADOR")`—
y la Tabla 24 lo dice igual: «Gestionar productos — No / No / Sí / Sí». La pantalla
estaba clasificada como pública **y sin ninguna guarda**: cualquiera que escribiera
su URL veía el formulario entero, con su botón de «Administración» al lado.

**De dónde salen los dos niveles `super`:**

- `crear-cuenta-admin` — RF-RBAC-003: «el sistema deberá permitir **exclusivamente
  al Super Administrador** asignar o modificar el rol y los permisos de cualquier
  cuenta».
- `auditoria` — `ms-cumplimiento/seguridad/SeguridadConfig.java` ya declara
  `/api/v1/admin/auditoria` como `hasRole("SUPER_ADMINISTRADOR")`. La interfaz se
  alinea con lo que el servidor ya hacía; antes ofrecía la pantalla a cualquier
  administrador y la API le respondía 403.

## Esto no es seguridad

RF-RBAC-004 es explícito: «el sistema deberá verificar la autorización del
solicitante **en el servidor** antes de ejecutar cualquier operación, con
independencia de los controles aplicados en la interfaz de usuario».

Todo lo de esta matriz es **usabilidad**: no enseñar puertas que no se pueden abrir,
y explicarlo con palabras cuando se intenta. Quien salte estos guardas con las
herramientas del navegador se encuentra exactamente lo mismo que antes: un 401 o un
403 del microservicio.

## Cómo se usa desde una vista

```js
import { exigirAcceso } from '../comun/acceso.js';
import { montarCabecera } from '../comun/cabecera-app.js';

const sesion = exigirAcceso('gestion-usuarios');
if (sesion) {
  montarCabecera(raiz, { vista: 'gestion-usuarios', seccionActiva: 'usuarios' });
  // …
}
```

Una línea. La vista no escribe `if (rol === ...)` nunca más.
