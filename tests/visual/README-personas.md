# Identidades de prueba del laboratorio UX

Cómo consigue el arnés una sesión de cada rol, y qué hace falta para que las
pantallas de administración se puedan revisar de verdad.

## El problema que resuelve

Nueve de las 32 vistas del producto solo existen para roles de trastienda, y
esos roles **no se pueden crear registrándose** — está bien que no se pueda:
RF-RBAC-003 reserva la asignación de roles al super administrador.

Hasta UX-R3.9 el laboratorio lo resolvía así, con backend real:

```js
sesionAdmin = sesionJugador;  // ← el arnés de UX-R2
```

Las nueve pantallas se fotografiaban **con un jugador**, salía el estado de
permiso denegado, y esa captura se archivaba como «la vista de auditoría».

## Cómo se consiguen ahora

Por los mecanismos reales, en cadena, igual que los conseguiría una persona:

| Persona | Cómo | Endpoint |
|---|---|---|
| Visitante | no hace falta nada | — |
| Jugador | se registra | `POST /api/v1/auth/registro` |
| Moderador | lo crea el super administrador | `POST /api/v1/admin/cuentas` |
| Administrador | lo crea el super administrador | `POST /api/v1/admin/cuentas` |
| Super administrador | **ya existe en el entorno** | `POST /api/v1/auth/login` |

El super administrador es el único que el arnés no puede fabricar, y no se
salta esa regla.

## Qué hay que poner en el entorno

```sh
UX_SUPERADMIN_EMAIL=...
UX_SUPERADMIN_PASSWORD=...
```

Nada de esto se versiona. El jugador y las cuentas administrativas que el
arnés crea usan apodo y contraseña generados al azar en cada corrida
(`crypto.randomBytes`), así que no se repiten ni entre ejecuciones.

Si las variables no están, el arnés **lo dice y sigue**: captura lo que puede
y avisa de que las vistas de trastienda se están viendo con el jugador — es
decir, su pantalla de acceso denegado, que también hay que mirar. Lo que no
hace es inventar un rol ni falsificar un token para colarse: una captura de
una vista administrativa hecha con un rol falso no demuestra nada.

## Cómo crear el super administrador de arranque

`ms-identidad` no trae hoy ningún seeder de cuentas (solo `RolSeeder`, que
crea los cuatro roles del catálogo). Mientras no lo haya, la cuenta se crea a
mano una vez por entorno, contra la base de datos del servicio.

**Propuesta pendiente de coordinar con el dueño de `ms-identidad`** (M01 —
Identidad, cuentas y RBAC): un `CommandLineRunner` activo solo bajo un perfil
de Spring (`ux-seed`, nunca en producción) que cree esa cuenta a partir de dos
variables de entorno. Sería el mismo patrón que ya usa `RolSeeder` y quitaría
el paso manual. No se implementa desde aquí: es el servicio de otro equipo y
el Charter pide coordinar antes de tocarlo.

## Sin backend

El modo por omisión del laboratorio sirve la raíz del monorepo con un servidor
estático y sin API. Ahí cada persona recibe un JWT **sin firma válida**, solo
para que el guard del navegador deje pintar el armazón de la vista.

Ese token no abre nada: cualquier servicio real lo rechaza porque no está
firmado por el emisor. Sirve para fotografiar, no para entrar. Las capturas de
ese modo se etiquetan `sin-servicios`.

Y es justo el modo correcto para `permisos.spec.js`, porque lo que esa prueba
comprueba es el **guard del navegador** — que no se enseñen puertas que no se
pueden abrir. El guard del servidor es otra prueba y es la que de verdad
protege (RF-RBAC-004).

## Cómo se corre

```sh
# sin backend: estados degradados, rápido, hermético
npx playwright test --config=playwright.visual.config.js

# solo los permisos
npx playwright test --config=playwright.visual.config.js permisos

# contra un backend, con identidades reales
VISUAL_BASE=http://localhost:8099 \
UX_SUPERADMIN_EMAIL=... UX_SUPERADMIN_PASSWORD=... \
npx playwright test --config=playwright.visual.config.js
```
