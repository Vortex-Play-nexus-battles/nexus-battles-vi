# Red y balanceo (M16A)

## Borde del host de desarrollo — `borde-dev.conf`

Un contenedor nginx (`srv-borde`, `docker-compose.deploy.yml`) es el único punto
de entrada público del host de plataforma en AWS: `http://<ip-del-host>/`.

| Ruta | Destino |
|---|---|
| `/` | **F6:** la portada pública (`cuentas/portada.html`, con `<base>` y la marca de rutas limpias): qué es el juego, cómo empezar y la tienda con productos reales. Hasta el 4-oct redirigía a `/login`; entrar sigue en `/login` y crear la cuenta en `/registro` |
| `/frontend/app-web/src/…`, `/shared/ui-kit/…` | archivos estáticos del repo, misma jerarquía (las vistas usan `../../../../../shared/ui-kit`) |
| `/api/v1/salas`, `/api/v1/partidas`, `/api/v1/mensajes-directos` (B6) | `srv-salas-partidas:8084` |
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
| **`/api/v1/banners…`** (todos los métodos) | **`34.193.90.11:8103`** — el mismo servicio de productos (HU-PRD-013); `GET /vigentes` es público y alimenta el banner rotativo de la home (RF-NOT-002). Hasta #854 no tenía `location` y caía en el 404 genérico |
| `/api/v1/{heroes,equipos,estrategias,progresion}` | **`34.193.90.11:8101`** (host de contenido) |
| `/api/v1/inventario` | **`34.193.90.11:8102`** (host de contenido) |
| `/ws/notificaciones` | `srv-notificaciones:8085` |
| `/ws` | `srv-salas-partidas:8084` |
| `/mailpit/` | bandeja del SMTP de pruebas — **solo orígenes internos** desde B12; desde internet, 403 (ver abajo) |
| `/salud-borde` | `UP` (lo comprueba `desplegar.sh`) |
| otro `/api/…` | 404 problem details "ruta sin servicio en el borde" |

Con un solo origen las vistas trabajan en modo integrado (sin
`<meta name="nexus-api-base">`) y no hay CORS entre navegador y servicios. Los
destinos se resuelven por nombre en la red del compose en cada petición: el
borde arranca aunque un servicio no esté desplegado y devuelve 502 solo para
ese prefijo.

**Registrar un prefijo nuevo:** añadir la `location` aquí, el puerto en
`puerto_de()` de `cd.yml` y el servicio en `docker-compose.deploy.yml`.

### Mailpit no es público (B12)

La bandeja de pruebas guarda los códigos de verificación y de recuperación de
todas las cuentas `@nexus.test`. Hasta B12, `http://35.168.124.119/mailpit/`
respondía 200 a cualquiera: con eso bastaba para activar una cuenta ajena o
quedarse con ella. Ahora `location /mailpit/` solo admite orígenes internos y
responde **403** al resto:

| Origen admitido | Quién llega así |
|---|---|
| `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16` | la pasarela de Docker: todo lo que entra por el `localhost:80` del propio host (un túnel SSH o de Session Manager) y los bancos, que entran por el puerto publicado de su anfitrión |
| `127.0.0.0/8` | el propio contenedor del borde |

`$remote_addr` es el par real porque el borde no usa `real_ip`. Si algún día se
pone un proxy o CloudFront delante, todo llegaría desde la dirección del proxy y
esta regla (y el límite de frecuencia) habría que revisarla **antes**.

**Las pruebas contra DEV** (`smoke-dev.yml`, `canarios-jugador.yml`,
`prueba-del-profesor.yml`) abren un túnel con la llave de despliegue
(`.github/actions/tunel-mailpit`) y leen el buzón en
`MAILPIT_URL=http://localhost:18025/mailpit`; el resto de su tráfico sigue
yendo al borde público, como el de un jugador. El smoke afirma además que
`/mailpit/` responde 403 desde internet.

**Una persona que necesite la bandeja de DEV** (una demo con cuentas
`@nexus.test`, depurar un correo):

```bash
# con la llave de despliegue
ssh -N -L 18025:localhost:80 ubuntu@35.168.124.119
# o sin ella, con Session Manager (usuario IAM con permiso)
aws ssm start-session --target <id-de-la-instancia> \
  --document-name AWS-StartPortForwardingSession \
  --parameters portNumber=80,localPortNumber=18025
# y en el navegador: http://localhost:18025/mailpit/
```

Solo las direcciones reservadas para pruebas (`.test`, `.example`,
`.invalid`, `.localhost`, `.local`) van a Mailpit; una dirección real va al
servidor SMTP que tenga configurado el entorno (`SMTP_HOST`, ver
`services/plataforma/correo/src/main/resources/application.yml`), no a esta
bandeja.

### Límite de frecuencia (B12)

Para lo que un bot haría en bucle desde una sola dirección. **No añade ninguna
`location`**: el reparto de rutas es el mismo; la ruta solo decide con qué
clave se cuenta la petición, y una clave vacía no se cuenta.

| Zona | Qué cuenta (solo `POST`) | Límite por dirección | `Retry-After` |
|---|---|---|---|
| `acceso` | `/api/v1/auth/login`, `/api/v1/auth/registro`, `/api/v1/auth/verificacion/*`, `/api/v1/auth/restablecer/*` | 30/min, ráfaga de 20 sin espera | 2 s |
| `escritura` | `/api/v1/products/{id}/comments` (y `…/comments/{id}/reportes`), `/api/v1/products/{id}/rating`, `/api/v1/comentarios/imagenes`, `/api/v1/mensajes-directos/…`, `/api/v1/subastas/{id}/pujas` | 120/min, ráfaga de 60 sin espera | 1 s |

- **Los orígenes internos no se limitan** (`geo $origen_interno`, los mismos
  rangos que `/mailpit/`): el banco E2E hace cientos de logins por minuto desde
  la pasarela de Docker, y un túnel al host entra por ella.
- **La lectura no se limita**, ni siquiera en una ruta cuya escritura sí.
- **La respuesta** es `429` con `application/problem+json`
  (`type` `https://nexusbattles.upb.edu.co/errors/demasiadas-peticiones`,
  `title`, `status`, `detail` en español) y `Retry-After`, lo que tarda en
  liberarse el siguiente hueco. Sale de la location con nombre
  `@demasiadas_peticiones` (`error_page 429`), que hereda las cabeceras de
  seguridad del server. Un 429 que venga de un servicio pasa tal cual: el borde
  solo pone `Retry-After` cuando el rechazo es suyo (`$limit_req_status`).
- **Todo va a nivel de `server`** (`limit_req`, `limit_req_status`,
  `error_page`): como con `add_header`, una `location` que declarara los suyos
  dejaría de heredar estos. `comprobar-rutas.sh` falla si alguna lo hace.
- **El método elige la clave, nunca el destino** (#421). El guardián lo
  comprueba siguiendo la dependencia de `$request_method` a través de los `map`
  hasta cualquier `set`, `proxy_pass`, `return`, `rewrite` o `if`.
- `/api/v1/mensajes-directos` está en la lista pero aún no tiene `location`
  (B6): hoy cae en el 404 de «prefijo sin servicio», que se contesta antes de
  contar.

**Los números son de protección, no requisitos del documento del curso.**
Registrarse, confirmar el correo y entrar son tres peticiones de `acceso`;
equivocarse de contraseña un par de veces, cinco o seis. Un aula entera detrás
del mismo NAT entra de golpe hasta 20 y después una cada 2 s; a quien le toque
un 429 la interfaz le dice que espere un momento (`comun/codigo-de-correo.js`,
`cuentas/login.js`). Un bot que prueba contraseñas se queda en 30/min, y
ms-identidad además bloquea la cuenta tras sus intentos fallidos. En escritura,
dos por segundo sostenidas es más de lo que escribe nadie a mano.

**Consecuencias conocidas:**

- La medición k6 **a demanda contra DEV** desde un runner es una sola dirección
  pública: el escenario `login` mediría 429 del borde, no el login. Contra DEV
  se deja fuera con `ESCENARIOS` (campo `escenarios` del workflow, ver
  `tests/rendimiento/README.md`) y se mide en el banco E2E (origen privado).
- Si algún día hay un proxy o CloudFront delante, todos los clientes llegarían
  desde la dirección del proxy: compartirían cupo (o, si es privada, nadie
  tendría límite). Hay que configurar `real_ip` **antes**.

### El banco también entra desde internet (B12)

`pruebas/` levanta, además de los ecos, un `cliente-publico` que solo vive en la
red `publico` (`203.0.113.0/24`, TEST-NET-3, interna). `comprobar-rutas.sh`
comprueba en cada corrida que el anfitrión llega al borde como origen privado y
ese cliente como `203.0.113.x`, y desde él: `/mailpit/` → 403, las ráfagas de
cada ruta limitada → 429 con su forma, la lectura y un `POST /api/v1/salas`
sin límite. Desde el anfitrión, Mailpit sigue en 200 y 40 logins seguidos
llegan los 40.

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

## HTTPS con Let's Encrypt (28-sep; revisado el 6-oct) — listo, apagado hasta que haya dominio

El borde publica HTTPS **en el mismo nginx**, sin coste y sin cambiar rutas.
Mientras no haya dominio todo sigue como antes: solo el 80.

| Pieza | Dónde | Qué hace |
|---|---|---|
| `include /etc/nginx/nexus-tls/*.conf;` | `borde-dev.conf`, dentro del `server` | Sin fragmento no incluye nada (un comodín sin coincidencias no es un error) |
| `location ^~ /.well-known/acme-challenge/` | `borde-dev.conf` | Sirve el reto HTTP-01 desde `/opt/nexus/acme` |
| Plantilla del fragmento | `tls/borde-tls.conf.plantilla` | `listen 443 ssl`, TLS 1.2/1.3 con suites AEAD, 301 del 80 a `https://DOMINIO` salvo `/salud-borde`, el reto y `/mailpit/`; HSTS opcional |
| `scripts/cd/certificado.sh` | host de plataforma | Emite, renueva, amplía, activa o quita el fragmento; solo publica un certificado **de confianza**; nunca deja nginx sin recargar |
| `abrir_origenes_al_dominio` | `scripts/cd/desplegar.sh` | Suma `https://DOMINIO` a las ocho listas de orígenes (CORS y WebSocket) en los dos hosts |
| 443 en el grupo de seguridad | `entornos/plataforma/main.tf` | Abierto antes del certificado: sin él, nadie escucha en el 443 (aplicado: `sg-005c60a57c33baac1`) |
| `certificado-dev.yml` | Actions | Renovación diaria (lun-vie) sin encender el host, DNS público, comprobación desde fuera y aviso a 14 días de caducar; a demanda `estado` y `probar-renovacion` |
| `pruebas/comprobar-tls.sh` | CI (banco del borde) | El mismo `borde-dev.conf` con el fragmento, un certificado autofirmado y un cliente «de internet» |
| `tests/e2e/https-del-borde.smoke.spec.js` | smoke de DEV | Con `PUBLIC_BASE_URL` en https: 301, rutas limpias, contenido mixto 0, enlaces de correo y `wss://` |

**Lo que cambió el 6-oct al revisarlo para encenderlo** (nada de esto se había ejercitado con un dominio real):

- certbot deja `live/` y `archive/` en `0700` de root y el despliegue corre como `ubuntu`: el `[ -f ]` desde el
  host no veía nunca el certificado recién emitido y el borde volvía a HTTP. Ahora el estado se lee dentro del
  contenedor de certbot.
- Un certificado del entorno de pruebas ya no se publica; al quitar `ACME_PRUEBAS` se pide el real con
  `--force-renewal` (sin él certbot se quedaba con el de pruebas: «no toca renovar»).
- Desde `https://` el `Origin` deja de coincidir con lo que cada servicio cree ser (`http://host:80`) y Spring lo
  trata como CORS: sin `https://DOMINIO` en su lista, login, carrito, chatbot, pujas y los canales STOMP dan 403.
  `IDENTIDAD_CORS_ORIGENES` no llegaba al host y ms-chatbot no tenía de dónde leer la suya.
- `/mailpit/` queda fuera de la redirección: los túneles SSH de smoke, canarios y la prueba del profesor la leen
  por `http://localhost`, y redirigida habría salido a internet, donde el borde la niega.
- `ACME_CORREO` es opcional: Let's Encrypt dejó de mandar avisos de caducidad el 4-jun-2025. El aviso es
  `certificado-dev.yml` en rojo a menos de 14 días.

**Para activarlo** (lo hace Grupo 6; una persona solo pone el dominio y su consentimiento):

1. Registro **A** del dominio → `35.168.124.119` (TTL 300), sin AAAA, sin proxy de CDN delante.
2. Variables del entorno `dev`: `DOMINIO_PUBLICO` (sin `https://`) y `ACME_ACEPTA_TERMINOS=true` — esta última
   **solo tras el sí explícito de una persona** al Subscriber Agreement de Let's Encrypt. `ACME_CORREO` opcional.
3. Ensayo: `ACME_PRUEBAS=1` y `certificado-dev.yml` (asegurar). Emite en el entorno de pruebas de Let's Encrypt,
   no lo publica y dice si DNS y reto funcionan.
4. Real: quitar `ACME_PRUEBAS` y volver a lanzarlo. Publica HTTPS y lo comprueba desde fuera.
5. Desplegar los servicios que leen orígenes (`cd.yml` a demanda): salas-partidas, notificaciones, ms-identidad,
   ms-ecommerce y ms-chatbot en plataforma; ms-subastas en contenido. `desplegar.sh` les suma `https://DOMINIO`.
6. `PUBLIC_BASE_URL=https://DOMINIO` y desplegar correo: los enlaces de los correos dejan de llevar la IP, y smoke,
   canarios y la prueba del profesor pasan a entrar por el dominio.
7. `certificado-dev.yml` con `probar-renovacion` (certbot renew --dry-run) y un apagado/encendido del host.

**Para volver atrás:** vaciar `DOMINIO_PUBLICO` (y `PUBLIC_BASE_URL` a la IP) y desplegar. El script quita el
fragmento y el borde vuelve a solo HTTP. **HSTS**: `HSTS_SEGUNDOS` vacío hasta que HTTPS lleve una semana estable;
después 300 → 86400 → 31536000, sin `includeSubDomains` ni `preload`.

Contingencia sin dominio: CloudFront con el plan Free (USD 0/mes, TLS incluido) delante de este
mismo borde, con un nombre `*.cloudfront.net`.
