# Red y balanceo (M16A)

## Borde del host de desarrollo — `borde-dev.conf`

Un contenedor nginx (`srv-borde`, `docker-compose.deploy.yml`) es el único punto
de entrada público del host de plataforma en AWS: `http://<ip-del-host>/`.

| Ruta | Destino |
|---|---|
| `/` | redirige a `/frontend/app-web/src/cuentas/login.html` |
| `/frontend/app-web/src/…`, `/shared/ui-kit/…` | archivos estáticos del repo, misma jerarquía (las vistas usan `../../../../../shared/ui-kit`) |
| `/api/v1/salas`, `/api/v1/partidas` | `srv-salas-partidas:8084` |
| `/api/v1/users` | `srv-notificaciones:8085` |
| `/api/v1/products` | `srv-comentarios:8081` |
| `/api/v1/correos` | **no se expone**: correo es entre servicios (ADR-005); desde fuera, 404 |
| `/api/v1/lista-negra`, `/api/v1/sanciones`, `/api/v1/apelaciones` | `srv-moderacion-sanciones:8086` |
| `/api/v1/torneos` | `srv-torneos:8083` |
| `/api/v1/parametros` | `srv-admin-parametros:8088` |
| `/api/v1/{latencia,disponibilidad,consultas,degradacion,tecnicas,moderacion}` | `srv-metricas-plataforma:8087` |
| **`/api/v1/admin/auditoria…`** | **`srv-ms-cumplimiento:8091`** — con `^~`, ver abajo |
| `/api/v1/{auth,perfiles,rbac,admin}` | `srv-ms-identidad:8089` |
| `/api/v1/{creditos,transacciones}` | `srv-ms-finanzas:8093` |
| **`/api/v1/cofres`** | **`srv-ms-finanzas:8093`** — añadido en R8.4, no existía |
| `/api/v1/{subastas,mis-pujas}` | `srv-ms-subastas:8092` |
| `/api/v1/carrito…` | `srv-ms-ecommerce:8090`, reescrito a `/ecommerce/api/v1/carrito…` |
| **`/api/v1/vitrina…`** | **`srv-ms-ecommerce:8090`**, reescrito a `/ecommerce/api/v1/vitrina…` (consulta intacta) — R16 |
| `/api/v1/productos…` (todos los métodos) | **`34.193.90.11:8103`** (host de contenido, desde el PR #611) — un solo dueño desde R16 (#421) |
| `/api/v1/{heroes,equipos,estrategias,progresion}` | **`34.193.90.11:8101`** (host de contenido) |
| `/api/v1/inventario` | **`34.193.90.11:8102`** (host de contenido) |
| `/ws/notificaciones` | `srv-notificaciones:8085` |
| `/ws` | `srv-salas-partidas:8084` |
| `/mailpit/` | bandeja del SMTP de pruebas |
| `/salud-borde` | `UP` (lo comprueba `desplegar.sh`) |
| otro `/api/…` | 404 problem details "ruta sin servicio en el borde" |

Con un solo origen las vistas trabajan en modo integrado (sin
`<meta name="nexus-api-base">`) y no hay CORS entre navegador y servicios. Los
destinos se resuelven por nombre en la red del compose en cada petición: el
borde arranca aunque un servicio no esté desplegado y devuelve 502 solo para
ese prefijo.

**Registrar un prefijo nuevo:** añadir la `location` aquí, el puerto en
`puerto_de()` de `cd.yml` y el servicio en `docker-compose.deploy.yml`.

### Cómo se comprueba el reparto — `pruebas/`

Leer el archivo no basta. `proxy_pass` con **variable y URI a la vez** no añade
el resto de la ruta: manda la URI escrita, tal cual. Por eso
`POST /api/v1/carrito/items` llegaba al servicio como `/ecommerce/api/v1/carrito`
y añadir al carrito nunca funcionó a través del borde.

`pruebas/` levanta este mismo `borde-dev.conf` contra servicios de mentira que
responden con la ruta exacta que reciben. Un solo destino se cambia al
levantarlo: el del catálogo (`34.193.90.11:8103`), que pasa a ser el eco
`srv-productos` para que ninguna petición del banco salga hacia el host de
contenido real (lo hace el contenedor `borde-conf`, que falla cerrado si la
sustitución no aplica):

```bash
cd infrastructure/red-balanceo/pruebas
docker compose up -d --wait
./comprobar-rutas.sh      # devuelve 0 si cada ruta va donde debe
docker compose down -v
```

No cuesta nada, no toca AWS y no necesita ningún servicio real.

### Precedencia de `location`: por qué `/admin/auditoria` lleva `^~`

nginx **no** elige la `location` por orden de aparición. El orden real es:

1. `=` — coincidencia exacta, gana sobre todo;
2. `^~` — prefijo más largo; si gana, **no se evalúan las expresiones regulares**;
3. `~` / `~*` — expresiones regulares, en orden de aparición, la primera que case;
4. prefijo simple — el más largo memorizado, **solo si ninguna regex casó**.

Un prefijo simple se resuelve en el paso 4, o sea **después** de todas las
regex. Por eso `location /api/v1/admin/auditoria` perdía siempre contra
`location ~ ^/api/v1/(auth|perfiles|rbac|admin)`, estuviera declarado donde
estuviera: la petición terminaba en ms-identidad, que no publica esa ruta, y la
vista de auditoría recibía su 404 de Spring. Estuvo así del 17 al 22 de
septiembre, dándose por arreglada.

`^~` la resuelve en el paso 2 y nginx ni mira las regex.

Esto lo fija `pruebas/comprobar-rutas.sh`, que desde R8.4 corre en integración
continua en cada cambio de esta carpeta.

### Colisión resuelta: `/api/v1/productos` (#421)

Hasta R16 `contenido/productos` y `cuentas/ms-ecommerce` declaraban los dos ese
prefijo, y el borde los separaba por método en la ruta exacta (dos
`map $request_method` y una `location =`): el `GET` sin sufijo era la vitrina de
la tienda y el resto, el catálogo. Era una **capa de adaptación**, no una
decisión de contrato, y nginx decidía la semántica de una ruta por su método.

La decisión la tomaron los dueños del prefijo, en sus contratos:

- `productos.yaml` 1.2.0 (#687) publica `GET /api/v1/productos`, el listado del
  catálogo maestro: todo el prefijo es de `contenido/productos`.
- `ecommerce-carrito.yaml` 1.2.0 muda la vitrina a `GET /api/v1/vitrina`, que
  proyecta ese catálogo. El antiguo `GET /productos` de ms-ecommerce queda
  deprecado dentro del servicio y el borde ya no lo publica.

`comprobar-rutas.sh` lo fija con tráfico (`GET` y `POST` exactos y `/{id}` al
catálogo, la vitrina a ms-ecommerce con su consulta) y falla si vuelve a
aparecer un reparto por `$request_method`.

**Cómo llega al host:** `cd.yml` (job *Desplegar en Dev*) copia
`frontend/app-web/src`, `shared/ui-kit` y este archivo a `/opt/nexus/web/`;
`desplegar.sh` levanta `srv-borde`, valida la configuración (`nginx -t`) y la
recarga sin cortar conexiones. Un push que solo toque frontend, ui-kit o esta
carpeta también despliega.

**Siguiente paso (cuando haga falta HTTPS o dominio):** CloudFront plan Free
(USD 0/mes, TLS y DNS incluidos) delante de este mismo borde, sin cambiar rutas.
