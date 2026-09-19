# Red y balanceo (M16A)

## Borde del host de desarrollo — `borde-dev.conf`

Un contenedor nginx (`srv-borde`, `docker-compose.deploy.yml`) es el único punto
de entrada público del host de plataforma en AWS: `http://<ip-del-host>/`.

| Ruta | Destino |
|---|---|
| `/` | redirige a `/frontend/app-web/src/cuentas/login.html` |
| `/frontend/app-web/src/…`, `/shared/ui-kit/…` | archivos estáticos del repo, misma jerarquía (las vistas usan `../../../../../shared/ui-kit`) |
| `/api/v1/salas` | `srv-salas-partidas:8084` |
| `/api/v1/users` | `srv-notificaciones:8085` |
| `/api/v1/products` | `srv-comentarios:8081` |
| `/api/v1/correos` | `srv-correo:8082` |
| `/api/v1/lista-negra`, `/api/v1/sanciones` | `srv-moderacion-sanciones:8086` |
| `/api/v1/{latencia,disponibilidad,consultas,degradacion}` | `srv-metricas-plataforma:8087` |
| `/api/v1/{auth,perfiles,rbac,admin}` | `srv-ms-identidad:8089` |
| `/api/v1/carrito…` | `srv-ms-ecommerce:8090`, reescrito a `/ecommerce/api/v1/carrito…` |
| `GET /api/v1/productos` (exacto) | `srv-ms-ecommerce:8090` → `/ecommerce/api/v1/productos` (vitrina) |
| `/api/v1/productos…` (resto) | `srv-productos:8080` (catálogo de contenido) |
| `/ws` | `srv-salas-partidas:8084`; con `?usuario=…` → `srv-notificaciones:8085` |
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
responden con la ruta exacta que reciben:

```bash
cd infrastructure/red-balanceo/pruebas
docker compose up -d && sleep 5
./comprobar-rutas.sh      # devuelve 0 si cada ruta va donde debe
docker compose down -v
```

No cuesta nada, no toca AWS y no necesita ningún servicio real.

### Colisión conocida: `/api/v1/productos`

`contenido/productos` y `cuentas/ms-ecommerce` declaran los dos ese prefijo.
No se pisan de hecho —contenido no publica un `GET` sin sufijo—, así que el
borde reparte por método y solo en la ruta exacta. Es una **capa de adaptación**
mientras sus dueños deciden de quién es el prefijo (#421), no una decisión de
contrato tomada aquí. `comprobar-rutas.sh` falla en cuanto ese reparto cambie.

**Cómo llega al host:** `cd.yml` (job *Desplegar en Dev*) copia
`frontend/app-web/src`, `shared/ui-kit` y este archivo a `/opt/nexus/web/`;
`desplegar.sh` levanta `srv-borde`, valida la configuración (`nginx -t`) y la
recarga sin cortar conexiones. Un push que solo toque frontend, ui-kit o esta
carpeta también despliega.

**Siguiente paso (cuando haga falta HTTPS o dominio):** CloudFront plan Free
(USD 0/mes, TLS y DNS incluidos) delante de este mismo borde, sin cambiar rutas.
