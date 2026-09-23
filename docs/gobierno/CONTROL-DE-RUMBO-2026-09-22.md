# Control de rumbo técnico — 22 de septiembre de 2026

**Alcance:** estado real de `develop` (`3dfed6e`), GitHub, CI/CD, AWS y los servicios vivos, tras el
bloque de rediseño UX. Diagnóstico de solo lectura: no se modificó código, ni AWS, ni issues, ni PRs.

**Método:** sondeo HTTP en vivo contra los dos hosts (solo GET), lectura de `develop`, `git log`,
Actuator sin autenticación, y siete auditorías paralelas. Se distingue en todo el documento la
**evidencia medida hoy** de la **información declarada en el repo**.

---

## 1 · Estado ejecutivo

1. **No estamos mal, pero estábamos mirando el mapa equivocado.** El modelo mental de "un solo
   t3.small" era falso desde el 8 de septiembre: hay **dos EC2**.
2. El rediseño UX no rompió nada. **96 de los 116 commits del periodo son míos**; de los 20 ajenos,
   ninguno introdujo una regresión funcional.
3. **#614 tiene causa raíz única y es nuestra, no del Grupo 2.** El borde del banco E2E enruta a AWS
   desde `43f5127`. El seed escribe bien y `ms-productos` lee bien.
4. **Dos servicios de contenido no tienen seguridad en absoluto** — `heroes` y `motor-combate`. Es
   el hallazgo más grave del informe y es deuda preexistente, no daño nuevo.
5. El defecto de nginx en `/admin/auditoria` es real y **hay un segundo peor**: `/api/v1/cofres` no
   tiene `location` ninguno.
6. **El host de contenido no tiene swap.** Margen declarado: −114 MB. Sin red de seguridad: OOM kill.
7. **El segundo EC2 está fuera de gobierno**: estado Terraform local, sin OIDC, sin apagado nocturno,
   sin presupuesto que lo vigile, y una sola persona puede gestionarlo.
8. **La moderación de comentarios del Sprint 3 está en cero absoluto** y COM-007 ya deja comentarios
   en `EN_REVISION` que nadie puede resolver.
9. Hay **dos PR listos y verdes parados sin motivo técnico** (#315 y #247).
10. De las 26 decisiones del PO, **solo 2 lo son de verdad** (D-24, D-26). Ocho son técnicas mal
    clasificadas que podemos cerrar nosotros hoy.
11. Pact existe (11 interacciones) y **ningún proveedor lo verifica**. k6 no existe.
12. Sí puede estar todo conectado a la vez — **excepto la economía**. Ver §12.

---

## 2 · Punto donde suspendimos el plan

El plan técnico se suspendió al cerrar **R7.1** (HU-TOR-004 CA-04, PR #585, 21-sep) y arrancar el
bloque UX. El siguiente corte anunciado era **R7.2 — consumidores de parámetros por API**.

| Pendiente antes de UX | Estado actual | Quién lo cambió | Evidencia | ¿Sigue pendiente? |
|---|---|---|---|---|
| R7.2 — parámetros dinámicos por API | **PARCIAL** — 3 de 22 claves consumidas | nadie | `V1__catalogo_de_parametros.sql`; solo `moderacion-sanciones` lee por API | **SÍ** |
| CD no detecta ms-subastas / ms-finanzas | **NO INICIADO** | nadie | `cd.yml:156-178` `puerto_de()` sin ambos | **SÍ** |
| Capacidad del host de dev | **REEMPLAZADO** — ahora hay dos hosts | cesarluna298-code (#289) | `infrastructure/entornos/contenido/main.tf` | Cambia de forma |
| Smoke tras CD de R7.1 | **COMPLETO** | — | `smoke-dev.yml`: 3 últimas corridas en verde | No |
| #435 sala no demostrable (inventario 503) | **RESUELTO DE FACTO** | cesarluna298-code (#611) | `GET /api/v1/inventario` por el borde → 401, ya no 503 | Cerrar el issue |
| E2E rojo | **AGRAVADO** — dos causas ahora | 43f5127 añadió la segunda | §13 | **SÍ, CRÍTICO** |

---

## 3 · Qué cambió durante UX

116 commits en `develop` entre el 15 y el 22 de septiembre.

| Autor | Commits | Área |
|---|---:|---|
| Simon Perez Gomez | 96 | plataforma, frontend, CI/CD, UX |
| cesarluna298-code | 6 | contenido: inventario, productos, borde, host |
| andresnv22 | 5 | subastas / pujas |
| Santiagosanabriauribe | 3 | ms-identidad |
| Nicolayyy | 3 | revisiones |
| MrWewin | 3 | subastas HU-SUB-001 |
| bugJ1 | 2 | ms-finanzas |
| CristianCamiloChaparro | 1 | subastas filtros |

**Lo que cambió el terreno bajo el plan, en orden de impacto:**

1. **`43f5127` (#611, cesarluna298-code, 22-sep)** — el borde deja de apuntar a nombres de
   contenedor y apunta a `34.193.90.11`. Correcto para AWS; **rompió el banco E2E** porque
   `tests/e2e/compose.yml:540` monta ese mismo archivo. Es la causa de #614.
2. **`0a21632` (#289, cesarluna298-code, 8-sep)** — nace el segundo EC2. No se anunció como cambio
   de arquitectura y la documentación del repo sigue negando que exista.
3. **`601abaaf` + `b963f2cd` + `cc5645a8`** — inventario pasa a identificar por `uid` (contrato
   1.1.1) y usa el JWKS de desarrollo. Correcto y coordinado; dejó a productos e inventario
   apuntando a un JWKS que no es el emisor real (§15, hallazgo 9).

Ningún commit ajeno introdujo una regresión funcional comprobable.

---

## 4 · Estado Grupo 2 — contenido

Servicios reales: `heroes`, `inventario`, `productos`, `motor-combate` (+ `misiones` y
`progresion-jugador`, que son solo README). 85 issues abiertos con etiqueta `grupo-2`.

| Servicio | Puerto | Vivo | Seguridad | Contrato | Observabilidad |
|---|---|---|---|---|---|
| heroes | 8101 | **UP** | **NINGUNA** | heroes 1.0.0 | health + metrics + log ECS |
| inventario | 8102 | **UP** | JWT (JWKS de dev) | inventario 1.1.1 | health, **sin metrics** |
| productos | 8103 | **UP** | JWT + rol en POST | productos 1.1.0 (**el código sirve 3 de 6 rutas**) | health + prometheus + log JSON |
| motor-combate | 8104 | **UP** | **NINGUNA** | motor-combate 1.0.0 | health + metrics |

**Veredicto:** el Grupo 2 es hoy el más **disponible** (4/4 arriba, 39,8 h de uptime) y el que tiene
la peor postura de seguridad. Trabaja bien y rápido; el problema es que su host y sus servicios
entraron en el sistema sin pasar por el gobierno del bloque.

---

## 5 · Estado Grupo 4 — cuentas

Servicios: `ms-identidad`, `ms-finanzas`, `ms-subastas`, `ms-ecommerce`, `ms-cumplimiento`
(+ `ms-chatbot`, solo README). 72 issues abiertos con `grupo-4`.

| Servicio | Puerto | Desplegado en dev | Estado |
|---|---|---|---|
| ms-identidad | 8089 | **SÍ, UP** | Emisor real del sistema. Tres agujeros de RBAC (§15) |
| ms-finanzas | 8093 | **NO** | 502 por el borde. `cd.yml` **no lo conoce** |
| ms-subastas | 8092 | **NO** | 502 por el borde. `cd.yml` **no lo conoce**. WebSocket sin auth |
| ms-ecommerce | 8090 | **NO** | Excluido por capacidad (#459). Faltan 5 secretos (#430) |
| ms-cumplimiento | 8091 | **NO** | Excluido por capacidad. Además inalcanzable por el defecto de nginx |

**Veredicto:** el Grupo 4 tiene **la mitad de su bloque sin desplegar**, y dos de sus servicios el CD
ni siquiera sabe que existen. Cinco de los nueve PR abiertos son suyos. Es el grupo con más trabajo
en vuelo y menos terreno firme.

---

## 6 · Estado Grupo 6 — plataforma

8 servicios, **los 8 UP** (8081-8088), más el borde y el frontend. 59 issues abiertos con `grupo-6`.

| Servicio | Puerto | Estado |
|---|---|---|
| comentarios | 8081 | UP. Moderación (COM-005..009) en cero |
| ~~(libre)~~ | 8082 | correo — UP |
| torneos | 8083 | UP. TOR-006/007 bloqueados por D-24 |
| salas-partidas | 8084 | UP. El más completo; único con trace id |
| notificaciones | 8085 | UP |
| moderacion-sanciones | 8086 | UP. No se comunica con ms-identidad (§17) |
| metricas-plataforma | 8087 | UP. REN-001 CA-03 en 409 |
| admin-parametros | 8088 | UP. 3 de 22 claves consumidas |

**Veredicto:** es el bloque más avanzado y el único desplegado por completo. Sus dos deudas propias
son la moderación de comentarios (cero) y que el catálogo de parámetros es decorativo.

---

## 7 · Grafo real de integraciones

**Todas las integraciones son HTTP síncronas. No hay bus de mensajes. RabbitMQ no existe.**

```
                        ┌─────────────── ms-identidad (8089) ── emisor JWT + JWKS
                        │                      │
   frontend ── borde ───┤                      ├──> lista-negra, correo, notificaciones, auditoría
   (nginx, EC2 A)       │
                        ├── salas-partidas (8084) ─┬──> inventario   (8102, EC2 B)
                        │                          ├──> heroes       (8101, EC2 B)
                        │                          ├──> productos    (8103, EC2 B)
                        │                          ├──> motor-combate(8104, EC2 B)
                        │                          ├──> creditos     (8093)  ✗ NO DESPLEGADO
                        │                          ├──> torneos      (8083)
                        │                          └──> lista-negra, sanciones (8086)
                        │
                        ├── comentarios (8081) ──> lista-negra, sanciones
                        ├── torneos (8083) ──> creditos ✗, lista-negra, sanciones
                        ├── moderacion-sanciones (8086) ──> notificaciones, admin-parametros, auditoría ✗
                        ├── metricas-plataforma (8087) ──> Actuator de todos + moderación
                        └── admin-parametros (8088) ──> auditoría ✗ (ms-cumplimiento no desplegado)

   ms-subastas (8092) ✗ ──> finanzas ✗, inventario, catálogo, notificaciones, sanciones
   inventario ──> heroes, productos          motor-combate ──> heroes
```

**Lecturas del grafo:**

- **`salas-partidas` es el nodo crítico: depende de 8 servicios**, cuatro de ellos en el otro EC2.
  Cualquier corte de red entre hosts lo degrada. HU-DIS-003 lo cubre con corte de circuito.
- **Tres aristas apuntan a servicios que no existen en dev** (creditos ×2, auditoría ×2): la apuesta
  de créditos, el premio del torneo y la bitácora de parámetros están cubiertos por degradación,
  no por función.
- **Cruce entre EC2: 4 aristas**, y ninguna propaga el trace id (§20).

---

## 8 · AWS actual — dos EC2

| | `nexus-plataforma-dev` | `nexus-contenido-dev` |
|---|---|---|
| IP | 35.168.124.119 (**EIP**) | 34.193.90.11 (**EIP**) |
| Tipo / disco | t3.small / 20 GB gp3 **cifrado** | t3.small / 16 GB **sin cifrar, tipo por defecto** |
| Origen | PR #385 | **PR #289, commit `0a21632`, 8-sep, cesarluna298-code, autofusionado el 9-sep** |
| Estado Terraform | **S3 versionado + lock** | **LOCAL** |
| Aplicación | `infra-dev.yml` por **OIDC** | **`terraform apply` a mano** |
| IMDSv2 | requerido | **ausente** |
| Perfil IAM / SSM | sí | **no** |
| `cpu_credits` | `standard` | **sin fijar → `unlimited`, puede facturar extra** |
| `default_tags` | Proyecto/Equipo/Entorno | **solo `Name`** → invisible en Cost Explorer |
| Despliegue por CD | job `desplegar-dev` | job `desplegar-contenido-dev` (`cd.yml:708`) |
| **Enciende el host si está apagado** | **SÍ** (`cd.yml:451-495`) | **NO** — el paso filtra `tag:Name = nexus-plataforma-dev` |
| **Apagado nocturno programado** | **SÍ** (`infra-dev.yml`, crons lun-vie) | **NO — corre 24×7** |
| Presupuesto que lo vigile | `nexus-credito-free-plan-2026` (100 USD/año) + `nexus-gasto-mensual-dev` (30 USD/mes) | **NINGUNO** |

**Coste.** t3.small on-demand us-east-1 = 0,0208 USD/h → **15,2 USD/mes en 24×7**; con EIP ≈ 19 USD.
**t3.small NO entra en la capa gratuita** (esa cubre t2.micro/t3.micro, 750 h). El README de
contenido lo redondea a "tipo elegible del plan gratuito", frase que se lee como si no costara.
Hasta el cierre del 6 de noviembre son **~28 USD adicionales sin vigilancia**.

**Riesgo de gobernanza, textual contra el Riesgo #5 del Charter:** el `tfstate` y la llave `.pem` de
contenido existen solo en el disco de quien hizo el `apply`. Nadie más puede planificar, aplicar,
destruir ni regenerar la llave. Si esa persona se ausenta, el host queda huérfano: sigue facturando y
el borde ya lo necesita.

---

## 9 · IPs, Security Groups y conectividad

Las dos IP son **elásticas**, así que los valores escritos en el repo son fiables.

| Aparición | Clasificación |
|---|---|
| `borde-dev.conf` → `34.193.90.11:8101/8102/8103` (×4) | **Correcta** para AWS, **peligrosa** en el banco E2E (§13) |
| `playwright.smoke.config.js:25` → `35.168.124.119` de respaldo | Debe ser variable; CI pasa `E2E_AWS` |
| `postman/LEEME.md` de productos e inventario | Documentación; aceptable |
| Secretos `DEPLOY_HOST_DEV` / `DEPLOY_HOST_CONTENIDO_DEV` | Correcto |
| `infrastructure/entornos/main.tf` (3 × t3.micro dev/test/prod) | **Código muerto** — retirar |

**Security Groups:** plataforma abre 22, 80, 8081-8089. Contenido abre 22 y 8101-8104. **Los dos
abren todo a `0.0.0.0/0`**, incluido SSH y los puertos de servicio. Plataforma llega a contenido
porque los puertos de contenido son públicos — **no hay red privada ni VPC peering; el tráfico entre
microservicios sale a Internet**. Contenido no llama a plataforma. El borde alcanza contenido por IP
pública.

**Defectos de `.env.example`:** `LISTA_NEGRA_VERIFICAR_URL` duplicada (líneas 58 y 67), y las líneas
70-71 contienen `DB_USER=usuario_nexus` / `DB_PASS=password_secreta_123` en un archivo cuya cabecera
dice que nunca lleva valores reales. Esa misma contraseña es el valor por defecto de siete
`application.yml`.

---

## 10 · Capacidad de cada host

**Medido en vivo** (Actuator sin auth, solo GET) · **Derivado** = suma de `mem_limit` del repo.

| Host | RAM | Swap | Disco (vivo) | Contenedores | Σ `mem_limit` | Margen | Riesgo |
|---|---|---|---|---|---:|---:|---|
| plataforma | 1,9 GiB | **2048 MB** | 19,20 GB / **9,61 libres** | 14 | **4144 MB** | **−2498 MB** (−450 con swap) | **Alto — ya vive en swap** |
| contenido | 1,9 GiB | **0 — NO HAY** | 15,33 GB / **8,48 libres** | 6 | **1760 MB** | **−114 MB** | **Alto — sin red de seguridad** |

**Medido en vivo:** CPU ociosa en ambos (`system.cpu.count`=2; carga 1 min **0,006** en plataforma y
**0,118** en contenido). `jvm.memory.committed` de los 8 de plataforma suma **1646 MiB**. Uptime:
plataforma 5,7 h, contenido 39,8 h.

**El hallazgo más barato de todo el informe:** `infrastructure/entornos/contenido/main.tf:109` **no
crea swapfile**; el de plataforma sí (`plataforma/main.tf:173-186`). Contenido opera con 114 MB de
déficit declarado y **cero swap**: cualquier pico no degrada, **mata el contenedor**. Copiar ese
bloque de `user_data` cuesta minutos y coste cero — el disco gp3 ya está pagado.

**La economía no cabe en ninguna parte.** Los cuatro servicios (ecommerce, cumplimiento, subastas,
finanzas) necesitan ~2048-2304 MB declarados (~1,0-1,1 GB reales). Contenido tiene −114 MB; plataforma
−2498 MB. **Corrección al supuesto anterior:** `FUERA_DEL_HOST_DEV` excluye solo dos
(`ms-cumplimiento ms-ecommerce`); `ms-subastas` y `ms-finanzas` se excluyen **por omisión** — no están
en `puerto_de()` ni en el mapa de `workflow_dispatch`, así que **el CD nunca los ve, ni a demanda**.

**Subconjunto mínimo para el flujo MVP** (login → sala → partida → créditos): solo falta
**ms-finanzas**. 512 MB declarados / ~250 MB reales. Cabe en contenido **después** de añadirle swap, y
en ningún otro sitio.

---

## 11 · Arquitectura de despliegue recomendada, coste adicional 0

| | RAM | Estabilidad | CD | Arranque frío | Riesgo en la demo |
|---|---|---|---|---|---|
| **A — plataforma: G6+identidad · contenido: G2 · economía solo en CI/E2E** | 4144 / 1760 decl. | plataforma en swap; contenido justo | **es `cd.yml` tal cual** | 25 s con reintentos | Bajo-medio |
| **B — contenido acoge además economía de G4** | 3808-4064 sobre 1946 **sin swap** | **OOM inmediato** | Incompatible: `desplegar-contenido-dev` filtra `^services/contenido/`; faltan 20 secretos y las BD | — | **Alto: tumba heroes y motor → mata el combate** |
| **C — perfiles bajo demanda** | Cabe por construcción | — | **`desplegar.sh` no tiene perfiles** | 2 ciclos × 25 s = 1-3 min de pantalla caída; pelea con `restart: unless-stopped` | **El más alto: el fallo cae dentro de la demo**; choca con HU-CICD-003 |

**Recomendación: A, con dos ajustes de coste cero.**

1. **Añadir 2 GB de swap a contenido**, copiando el bloque de `user_data` de plataforma.
2. **Solo si el PO exige créditos en vivo:** ms-finanzas + su Postgres en contenido, *después* del
   swap. Exige abrir bloque de detección y `puerto_de()` en `cd.yml` y 5 secretos.

B queda descartada por cifras. C traslada el fallo al momento de la demostración.

---

## 12 · ¿Puede estar todo conectado simultáneamente?

**Sí, salvo la economía. Respuesta precisa:**

- **Lo que ya está conectado a la vez, hoy, comprobado:** frontend + borde + los 8 de plataforma +
  ms-identidad + los 4 de contenido = **14 servicios**. El flujo login → listar salas → crear sala →
  verificar héroe → partida → combate contra la IA → resultado funciona entero.
- **Lo que no puede estar conectado a la vez:** los 4 de economía. No por diseño, sino por RAM: faltan
  2,1-2,4 GB y no hay dónde ponerlos sin crear infraestructura.
- **Lo que sí se puede añadir:** **ms-finanzas, y solo ms-finanzas**, tras darle swap a contenido.
  Con eso el MVP completo —incluida la apuesta de créditos y el premio del torneo— está conectado a
  la vez. Serían **15 servicios**.
- **Subastas, carrito y bitácora** seguirían viviendo solo en el banco E2E. Hoy degradan con honestidad
  (502 por el borde, 503 desde salas), así que la demo no miente; simplemente no los enseña.

---

## 13 · Estado #614 / E2E — CRÍTICO

**Causa raíz, en una frase:** el seed escribe en la Mongo del banco E2E, pero desde `43f5127` el borde
—**el mismo archivo `borde-dev.conf` que el banco monta en `tests/e2e/compose.yml:540`**— enruta
`/api/v1/productos` a `34.193.90.11:8103`, el `ms-productos` de AWS, cuya Mongo nunca vio los
documentos sembrados.

**Las tres sospechas del issue quedan descartadas por lectura directa:**

| Capa | Esperado | Real | Veredicto |
|---|---|---|---|
| Seed → Mongo | base `productos`, col. `productos`, `_id` String, `_class` | `sembrar.sh:78-107` lo hace exactamente así | **CORRECTO** |
| Entidad | `@Document("productos")`, `@Id String` | `nexus/dominio/Producto.java:11-15` | **COINCIDE** |
| Repositorio | `findById` sin filtro de estado | `ConsultarProductoServicio.java:18-19` | **SIN FILTRO** |
| Controlador | `/api/v1/productos/{id}` | `ProductosController.java:20,58` | **CORRECTO** |
| Seguridad | GET público | `SeguridadConfig.java:57-58` `permitAll` | **No es 401** |
| **Borde** | `srv-productos:8080` | **`34.193.90.11:8103`** (`borde-dev.conf:250-253`) | **AQUÍ SE ROMPE** |

**Prueba en vivo:** `GET http://34.193.90.11:8103/api/v1/productos/p-heroe-e2e` devuelve el 404 con el
cuerpo **byte a byte idéntico** al del log de CI del run rojo. Lo emitió el productos de AWS.

**Alcance mayor que el título del issue:** el mismo commit sacó del compose **tres** prefijos.
`sembrar.sh:134,151,160,176,180,187` llama a `$BORDE/api/v1/inventario/**`, que ahora va al inventario
de AWS y valida contra `jwks-dev`, no contra el ms-identidad del banco → **401**. El arreglo debe
cubrir `heroes`, `inventario` y `productos` a la vez.

**Por qué no lo atrapó nadie:** `infrastructure/red-balanceo/pruebas/comprobar-rutas.sh:41` comprueba
exactamente este caso — y **no corre en CI**. El guardián existe y nadie lo ejecuta.

**Segundo rojo, distinto y anterior:** desde `da6c6b4` (21-sep), `torneos.e2e.spec.js:318` falla en un
`toContainText`. Arreglar el borde **no** lo cierra. #614 mezcla dos fallos.

**Fix mínimo (descrito, no aplicado):** no tocar `borde-dev.conf` —#611 es correcto para AWS—. Generar
en `tests/e2e/levantar.sh` un `default.conf` derivado, con `sed` sobre el real sustituyendo las cuatro
apariciones de `34.193.90.11:810X` por los nombres de contenedor, y añadir `srv-productos`,
`srv-heroes`, `srv-inventario` al `depends_on` de `srv-borde`. Es exactamente la capa de adaptación
que prescribe el Riesgo #3 del Charter. **Segundo cambio obligatorio:** ejecutar `comprobar-rutas.sh`
en CI.

**Responsable:** Grupo 6 / M16A-M16B. **El fix no toca código del Grupo 2.** El issue debe reetiquetarse
y retirar la sospecha sobre ellos.

---

## 14 · Defecto de nginx `/admin/auditoria` — y los que no habíamos visto

`borde-dev.conf` tiene 28 `location` y **ningún `^~` en todo el archivo**. Cinco regex, evaluadas en
orden de aparición. Predicción de la matriz: **26 de 26 aciertos** contra el sondeo en vivo.

| Ruta | Gana | Upstream real | Debería | Estado |
|---|---|---|---|---|
| **/api/v1/admin/auditoria** | regex L186 | **ms-identidad** | ms-cumplimiento:8091 | **COLISIÓN CRÍTICA — 404** |
| **/api/v1/cofres** | ninguno | **404 del borde** | ms-finanzas | **SIN `location` — vista rota** |
| /api/v1/partidas | prefijo L130 | salas-partidas | salas + finanzas | Colisión menor |
| /api/v1/productos (GET exacto) | `=` L245 | ms-ecommerce:8090 | = | 502, upstream ausente |
| /api/v1/creditos·transacciones | regex L192 | ms-finanzas:8093 | = | 502, upstream ausente |
| /api/v1/subastas·mis-pujas | regex L198 | ms-subastas:8092 | = | 502, upstream ausente |
| heroes · inventario · productos/{id} | L230 / L234 / L251 | `34.193.90.11` | = | **OK, 200/401** |
| salas · torneos · parámetros · lista-negra · sanciones · tecnicas | prefijos | correcto | = | OK |

**1. `/api/v1/admin/auditoria` — CRÍTICA.** El comentario de las líneas 174-178 afirma que se arregló
el 17-sep poniendo la `location` *antes* de la regex. **Eso no funciona en nginx:** un prefijo simple
nunca gana a una regex que también case. Devuelve el 404 de Spring de ms-identidad
(`"No static resource api/v1/admin/auditoria"`). `frontend/.../cuentas/auditoria.js:6` consume esa
ruta: **la vista de auditoría lleva rota desde entonces, dándose por resuelta.**

**Fix mínimo:** `borde-dev.conf` **línea 179**, `location /api/v1/admin/auditoria` →
`location ^~ /api/v1/admin/auditoria`. **Es suficiente:** ms-cumplimiento tiene un único controlador
y sus dos rutas cuelgan del mismo prefijo. Corregir también el comentario, que documenta una
precedencia falsa.

**2. `/api/v1/cofres` — ALTA, no estaba en ningún issue.** `frontend/.../cuentas/mis-cofres.js:7` llama
`/api/v1/cofres/mios`; ms-finanzas lo publica; **el borde no tiene `location` para ese prefijo**. Cae
en el 404 genérico. Vista de usuario rota por enrutado, no por upstream.

**3. `/api/v1/partidas` — MEDIA.** Lo declaran salas-partidas y ms-finanzas
(`/partidas/resultado`). El borde manda todo a salas: el endpoint de finanzas es inalcanzable desde
fuera. Hoy solo se usa S2S. Misma clase que #421 y sin issue.

---

## 15 · Seguridad transversal

| Servicio | Grupo | JWT | uid correcto | S2S con token | RBAC admin | Riesgo |
|---|---|---|---|---|---|---|
| **heroes** | 2 | **NO EXISTE** | — | no | **no** | **CRÍTICO** |
| **motor-combate** | 2 | **NO EXISTE** | — | no | **no** | **CRÍTICO** |
| ms-subastas | 4 | Sí (HTTP) / **No (WS)** | sí | sí | — | **ALTO** |
| ms-identidad | 4 | propio | sí | sí | **parcial** | **ALTO** |
| salas-partidas | 6 | sí | sí | sí | — | MEDIO |
| moderacion-sanciones | 6 | sí | sí | parcial | sí | MEDIO |
| admin-parametros | 6 | sí | sí | sí | escritura sí, **lectura abierta** | MEDIO |
| metricas-plataforma | 6 | sí | — | **no (sin client-id)** | sí | MEDIO |
| inventario / productos | 2 | sí (**JWKS de dev**) | sí | sí | parcial | MEDIO |
| comentarios · correo · notificaciones · torneos | 6 | sí | sí | parcial | — | BAJO |
| ms-finanzas · ms-ecommerce · ms-cumplimiento | 4 | sí | sí | sí | sí | BAJO |

**CRÍTICO**

1. `services/contenido/heroes/build.gradle:1-3` aplica `nexus.spring-conventions`, **no**
   `nexus.seguridad-conventions`: **cero Spring Security**. `POST /experiencia`
   (`ProgresionController.java:32`) y `POST /decision` (`EstrategiasController.java:66`) son escrituras
   **anónimas**, y **el borde las publica** (`borde-dev.conf:230`). Cualquiera en Internet puede subir
   experiencia a cualquier héroe.
2. `motor-combate/.../CombateController.java:34,50` — igual. `POST /api/v1/combate/ataques` resuelve
   daño sin credencial. Mitigado solo por que el borde no lo enruta.

**ALTO**

3. `ms-subastas/.../realtime/WebSocketConfig.java:37-43` — sin `ChannelInterceptor`, CONNECT sin
   autenticar y `setAllowedOriginPatterns("*")`. El propio código dice *"PENDIENTE: restringir antes de
   desplegar"*.
4. `ms-identidad/.../RbacController.java:19,29,38,46` — sin `@RequirePermission`:
   `/api/v1/rbac/{authorize,roles,matrix}` queda **público por el borde**. Comprobado en vivo: 200.
5. `ms-identidad/.../SecurityInterceptor.java:48-62` — tres constructores de conveniencia fijan
   `permitirHeaderRol = true`; con `application-dev.properties:39` y el perfil que inyecta el CD, un
   host en `dev` **acepta `X-User-Role: SUPER_ADMINISTRADOR`**.

**MEDIO:** destinos `/tema/partidas/{id}` sin filtrar por participante; tres `permitAll` en
moderación (incluido uno que revela si un uid está sancionado); CORS `"*"` en el emisor;
**inventario y productos siguen apuntando a `KEYCLOAK_JWK_SET_URI` / `jwks-dev`** en vez del emisor
real de ADR-002/005 — quien tenga esa clave de dev firma tokens de administrador;
`GET /api/v1/parametros/**` en `permitAll` publica la configuración operativa; fail-open del S2S en
torneos y comentarios (sin client-id la llamada sale sin `Authorization`, contra ADR-005 §9).

**ADR-002 se cumple.** Ningún servicio hace `UUID.fromString(jwt.getSubject())`; todos leen `uid` con
`sub` de respaldo.

**No hay ninguna regresión de seguridad en los últimos 30 días.** Los ~22 commits del periodo sobre
archivos de seguridad son de cierre. Los dos sospechosos se verificaron uno a uno: `7588cc7d` **añade**
autorización, `b40828c6` **corrige** la lectura de identidad. **Todo lo listado es deuda preexistente
nunca cerrada**, no daño del bloque UX.

---

## 16 · Sprint 3

| HU | Estado real | Qué falta |
|---|---|---|
| **COM-005** #519 cola de moderación | **NO INICIADO** | Sin contrato, tabla ni endpoint |
| **COM-006** #520 reportar | **NO INICIADO** | Cero referencias a reporte/denuncia |
| **COM-007** #521 filtros | PARCIAL | Lista negra sí; falta anti-evasión (l33t) y la revisión manual |
| **COM-008** #522 acciones del moderador | **NO INICIADO** | `Estado` no tiene `OCULTO` |
| **COM-009** #523 orden + voto útil | **NO INICIADO** | `consultarHilo` ni siquiera pagina |
| USR-004 #477 | COMPLETO EN CÓDIGO | CA-05 escalado (D-20) |
| **USR-005** #478 suspensión | **PARCIAL — DEFECTO** | **No cambia el estado en ms-identidad ni cierra sesiones** |
| USR-006 #479 baneo | PARCIAL | Falta congelar inventario y cancelar subastas |
| USR-007 #480 apelación | PARCIAL | Sin SLA, sin acceso del baneado sin sesión |
| USR-008 #561 / USR-009 #562 / USR-010 #563 | PARCIAL 25 % / **NO INICIADO** / PARCIAL 30 % | Panel, búsqueda y ficha |
| TOR-001 #486 / TOR-008 #493 | **COMPLETO Y DESPLEGADO** | — |
| TOR-002 #487 | PARCIAL | ms-finanzas fuera del host → 503 si el costo > 0 |
| TOR-003 #488 | COMPLETO EN CÓDIGO | Defecto #593 (pide UUID, no apodo) |
| TOR-004 #489 | PARCIAL | Bracket reset (#589) y rama IA (**D-26**) |
| TOR-005 #490 | PARCIAL | Sin "nivel acorde" (CA-01) |
| **TOR-006** #491 / **TOR-007** #492 | **BLOQUEADOS — D-24** | Ausencia total comprobada |
| MET-001 #527 | PARCIAL | Umbral D-25; gráfico de registro |
| MET-004 #530 | PARCIAL | `infrastructure/observabilidad/README.md` = **0 bytes** |
| MET-002 #528 / MET-003 #529 | **NO INICIADO** | Son de otro grupo |
| **REN-001** #68 | **BLOQUEADO + PARCIAL** | CA-03 → 409 por `LATENCIA_PERCENTIL`; es latencia por servicio, **no traza distribuida** |
| REN-003 #69 | PARCIAL | Falta `EXPLAIN` y prueba de carga; **sin k6** |
| ADM-001 #481 | PARCIAL | 3 de 22 claves conectadas |
| ADM-002 #26 | COMPLETO EN CÓDIGO (cerrada) | Sin E2E de CA-02 |
| ADM-005 #497 | PARCIAL | Ajuste del árbol ante incidencia |

**Los tres mayores riesgos para el 9 de octubre:**

1. **La moderación de comentarios no existe y el Charter la comprometió.** Cuatro HU en cero absoluto.
   Peor: **COM-007 ya marca comentarios como `EN_REVISION` y ningún moderador puede verlos ni
   resolverlos** — es un callejón sin salida que hoy retiene contenido de usuarios de forma
   indefinida. Son 4 HU desde cero en 2,5 semanas, **y no dependen de ninguna decisión del PO**.
2. **Suspender o banear no bloquea el acceso.** `moderacion-sanciones` y `ms-identidad` mantienen
   implementaciones paralelas que no se hablan. USR-005/006 se dieron por hechas en #576; un usuario
   suspendido sigue entrando.
3. **D-24 sin respuesta + cero evidencia de rendimiento.** El Sprint 3 compromete informes de latencia,
   carga y recuperación, y no existe ninguna prueba de carga.

---

## 17 · Parámetros dinámicos — R7.2

Catálogo real: `V1__catalogo_de_parametros.sql` — **22 claves**, 10 marcadas `inalterable` (Charter).

| Parámetro | Consumidor | Fuente hoy | ¿Dinámico? |
|---|---|---|---|
| `sanciones.suspension.*`, `sanciones.apelacion.plazo-dias` | moderacion-sanciones | **API del catálogo** | **Hecho (3/22)** |
| «dentro de los 30 días» del aviso | moderacion-sanciones | **literal en `SancionesService.cuerpoDe()`** | **Sí — DEFECTO: el correo miente si el PO baja el plazo** |
| `chat.historial.tamano`, `chat.mensajes-por-minuto` | salas-partidas | env / **no implementado** | Sí |
| `salas.apuestas.si-gana-la-maquina` | salas-partidas | `@Value` | Sí |
| `torneos.cupos` (8), `dias-entre-torneos` (91), `integrantes-por-equipo` (2), `costo-inscripcion` | torneos | **quemados** / no se lee | Sí — **torneos no tiene ni una referencia a parámetros** |
| `metricas.umbral-sanciones-por-dia`, `plataforma.cpu-autoescalado`, `latencia-objetivo-ms` | metricas-plataforma | env vacío / quemado | Sí |
| `partidas.creditos` 2/4/1 | ms-finanzas | quemado | Sí |
| Nº de reportes que ocultan un comentario · longitud máxima | comentarios | **no existe la clave** | Sí — falta |
| `RESILIENCIA_*`, `*_URL`, puertos, Hikari, `EntregadorDeAvisos.LOTE` | varios | env | **NO — configuración técnica (regla 10)** |

**Veredicto: R7.2 sigue haciendo falta.** Se consumen **3 de 22 claves (13 %)**, y solo desde un
servicio. Las otras 19 están sembradas y auditadas pero **decorativas**. HU-ADM-001 CA-04 ("cambiar sin
desplegar") se demuestra con un caso y falla en los demás. **Pero no es lo más urgente:** 19 claves
decorativas cuestan menos que cuatro HU de moderación en cero.

---

## 18 · Pact, contratos y eventos

**Contratos.** 20 OpenAPI, todos con servicio real, ninguno huérfano. Tres defectos:

- **`ms-cumplimiento` no tiene contrato** y lo consumen **cuatro** clientes. **`/api/v1/auth` tampoco**
  (solo un `.md` no ejecutable). Regla 1 rota en la integración más transversal del producto.
- **`productos.yaml` declara 6 rutas y el código sirve 3.** Sin controlador: `/{id}/adquisiciones`,
  `/{id}/suspender`, `/{id}/reactivar` — el dominio existe, falta la capa HTTP.
- **`creditos` expone 6 operaciones no contratadas.** API por delante del contrato.
- `contracts/eventos/` y `contracts/esquemas/` contienen **solo un README de 0 bytes**.
- **El Riesgo #3 no se cumple:** hay `version:` en los 20, pero nada los congela — sin tags por
  sprint, sin lint OpenAPI en CI, sin prueba contrato↔código.

**Pact — corrijo mi afirmación anterior: sí existe.** Dos pactos (pact-jvm 4.6.17), 11 interacciones:
finanzas (reservar 201/422, saldo, consumir, liberar) e inventario (bloqueo, elemento 200/404,
transferencias idempotentes). **Defecto crítico: ningún proveedor los verifica.**
`ms-subastas/build.gradle:72` lo dice explícitamente, ningún proveedor declara `pact-provider:junit5`,
y `grep -i pact .github/workflows/*` da **0**. Hoy son documentación ejecutable de un solo lado: coste
hundido a una tarea de distancia del valor.

| Integración | ¿Pact aporta valor? | Por qué |
|---|---|---|
| **salas ↔ inventario** | **SÍ — prioridad 1** | Es lo que bloquea #435; estados "héroe equipado / sin equipar / 404" prueban HU-SAL-003 sin desplegar inventario |
| **salas ↔ finanzas** | **SÍ** | Reserva/liberación/consumo y el 422→503 que destapó el E2E; **reutiliza** estados del pacto de subastas |
| **notificaciones ↔ emisores** | **SÍ (uno por emisor)** | Tres servicios la llaman con forma distinta; un cambio de campo rompe tres avisos a la vez |
| subastas ↔ finanzas | Ya está, y basta | Solo falta que finanzas lo verifique |
| subastas ↔ inventario | Ya está, y **no basta** | Dos de sus seis estados describen un endpoint inexistente |
| salas ↔ motor-combate | **NO** | Un POST sin estado, ya cubierto por prueba contra el enum del YAML |
| salas ↔ torneos | **NO** | Un endpoint entre dos servicios del mismo dueño, ya ejercitado en el E2E |
| comentarios ↔ lista-negra | **NO** | Un booleano sobre un string; `@RestClientTest` cuesta la décima parte |

**Eventos.** Los destinos STOMP de plataforma están contratados
(`websocket/salas-partidas.yaml` 10 canales, `notificaciones.yaml` 1.1.0). **Excepción:**
`/topic/subastas/listado` (`SubastaRealtimePublisher.java:37`) no tiene AsyncAPI **y usa prefijos
`/topic`+`/app` contra el `/tema`+`/cola` del resto** — dos convenciones de canal en el mismo producto.

**RabbitMQ no existe** —cero `@RabbitListener`, `amqp` o dependencia— y **ningún RF/HU vigente lo
exige**. El "bus de notificaciones" del Charter está cubierto por STOMP + `POST /internal/notifications`.
**No hay que introducirlo.** Sí conviene limpiar `COLA_MENSAJES_URL`: se inyecta como secreto en
`cd.yml:548,808,914` y **ningún servicio la lee** — variable muerta que sugiere infraestructura que no
está.

---

## 19 · Rendimiento y k6

**`tests/rendimiento/` contiene únicamente un `README.md` de 0 bytes. Cero scripts k6 en todo el
repo.** Ningún workflow lo ejecuta. k6 está en la pila de CLAUDE.md y no existe.

La instrumentación **sí es real y compartida:** `plataforma-observabilidad/FiltroDeLatencia.java:31`
mide extremo a extremo y se reparte a los 14 servicios Gradle por
`nexus.spring-conventions.gradle:42`. El percentil (`RegistroDeLatencia.java:136-142`) **no tiene valor
por defecto a propósito**, y el 409 es literal: `LatenciaController.java:155-159` devuelve
`CONFLICT` con `problema.setProperty("variable","LATENCIA_PERCENTIL")`.

**Escenarios justificados por un RF/RNF concreto:** listar salas y crear sala (RNF-REN-001, 500 ms),
acciones de partida, búsqueda indexada de inventario (HU-REN-003 CA-01), login (camino obligado).
**Serían invención:** chat y torneos — no hay RNF de carga que los nombre.

**`LATENCIA_PERCENTIL` no es una decisión del PO.** Es una convención de medición de ingeniería. Lo
podemos fijar nosotros hoy y desbloquear REN-001 CA-03.

---

## 20 · Observabilidad

| | health | metrics | prometheus | log JSON | trace id | circuit breaker |
|---|---|---|---|---|---|---|
| salas-partidas | ✔ | ✔ | ✖ | ✖ | **✔ (el único)** | ✔ propio |
| comentarios, correo, notificaciones, torneos, moderación, parámetros, métricas, motor-combate | ✔ | ✔ | ✖ | ✖ | ✖ | ✖ |
| heroes | ✔ | ✔ | ✖ | ✔ ecs | ✖ | ✖ |
| productos | ✔ | ✖ | **✔** | ✔ logstash | ✖ | ✖ |
| inventario, ms-identidad, ms-ecommerce | ✔ | ✖ | ✖ | ✖ | ✖ | ✔ r4j (los dos últimos) |
| ms-finanzas, ms-subastas, ms-cumplimiento | ✔ | ✔ | ✔ (2 de 3) | ✖ | ✖ | ✔ (finanzas) |

**Regla 6 (bitácora JSON a stdout): rota en 15 de 17.** **Regla 5 (trace id): rota en 16 de 17.**
**Regla 3:** 8089, 8102 y 8103 responden salud pero **no exponen `/actuator/metrics`**.

**Con dos EC2, correlacionar una traza es hoy imposible.** `FiltroDeTraza` solo se registra en
salas-partidas, y **ningún cliente HTTP reenvía `traceparent`**: `ClienteInventarioHeroes.java:206,246,272`
manda `Accept` y `X-User-Name`, nada más. Una traza que sale de plataforma hacia contenido **nace de
cero al otro lado**. Es el gap que más duele con la arquitectura actual.

**Se arregla sin infraestructura nueva:** (a) el filtro W3C ya existe en `plataforma-comun` — moverlo a
la autoconfiguración de `plataforma-observabilidad` lo enciende en los 14 sin tocar servicios;
(b) `.header("traceparent", MDC.get("trazaId"))` en los clientes; (c)
`logging.structured.format.console=ecs` es una línea por `application.yml`; (d) `metricas-plataforma`
ya recolecta el Actuator de todos — **es el agregador; no hace falta Prometheus ni Grafana**.

---

## 21 · Product Backlog

221 abiertos / 27 cerrados. Etiquetas en abiertos: `historia-usuario` 191, `grupo-2` 85, `grupo-4` 72,
`grupo-6` 59, `migrado-jira` 56, `tarea` 24, `bug` 5, `criterio-pendiente` 4, `duplicada-solapada` 3.

**El desajuste mayor: 16 HU siguen abiertas con evidencia de implementación fusionada** — #476, #477,
#478, #479, #480, #481, #486, #487, #488, #489, #490, #493, #497, #527, #530, #494. Auditadas criterio
por criterio en §16: **solo 3 están de verdad completas y desplegadas** (TOR-001, TOR-008, y USR-004 a
falta de CA-05). Las demás son parciales. **El backlog no sobre-declara pendientes: declara pendiente
lo que es parcial, y eso está bien. Lo que falta es cerrar las 3 que sí están.**

Defectos abiertos: #581, #571, #569, #567, #226. Con forma de defecto pero etiquetados `tarea`: #614,
#429. Otros: #593 (uid vs apodo), #435 (**ya resuelto de facto por #611 — cerrar**), #430 (5 secretos
de ms-ecommerce), #429 (SonarCloud cancelado en todos los PR), #426, #421.

---

## 22 · PR abiertos

| PR | Autor | Edad | Objetivo | Base | CI | Conflictos | Clasificación |
|---|---|---:|---|---|---|---|---|
| **#315** | Tamadiaz04 | 11 d | Botín HU-JUE-010 en motor-combate (27 arch.) | develop | **7/7 verdes** | 186 detrás | **LISTO PARA MERGE** |
| **#247** | MbappeRonaldo7 | **20 d** | `PATCH /productos/{id}` + reversión; **toca el contrato** | develop | 7/7 verdes (2-sep) | **dirty**, 236 detrás | **LISTO PERO CONFLICTIVO** |
| #282 | Melipete12 | 19 d | Panel de catálogo HU-PRD-008 | develop | **2 en rojo hoy** | 0 detrás | ACTIVO — bloqueado por CI |
| #432 | Julianht5-1 | 3 d | Checkout en ms-ecommerce | develop | **2 en rojo** | 28 detrás | ACTIVO — bloqueado por CI |
| #390 | CristianCamiloChaparro | 6 d | Filtros de subastas + 418 líneas de prueba | develop | Sonar cancelado | 136 detrás | ACTIVO — rebase pendiente |
| #382 | bugJ1 | 6 d | Pasarela simulada | develop | verde (16-sep) | **dirty** | **SUPERADO POR DEVELOP** |
| #381 | SCC405 | 6 d | `--borde-interactivo` en vitrina | develop | solo estructura | **dirty** | **SUPERADO POR DEVELOP** |
| **#270** | Santiagosanabriauribe | 19 d | JWT v2 en ms-identidad | develop | **0 checks jamás** | **dirty**, 238 detrás | SUPERADO + CONFLICTIVO |
| **#269** | Santiagosanabriauribe | 19 d | Idéntico a #270 | **`main`** ⚠ | **0 checks** | **dirty** | **DUPLICADO, base equivocada** |

**#269 / #270.** Ambos apuntan al **mismo commit de cabeza** (`9f9f187`) desde la misma rama. No son
dos trabajos: es **uno abierto dos veces con base distinta**. #269 va contra `main`, que está 238
commits atrás, así que GitHub calcula 483 archivos y +39.357 líneas — casi todo eso es `develop`, no el
trabajo del autor. **#270 tiene la historia correcta**: 26 archivos, +676/−752, 5 commits. Recomendación:
cerrar #269; y #270 tampoco es fusionable tal cual porque `perfil.js`, `gestion-usuarios.js` y
`ms-identidad/perfiles` se reescribieron después — lo sano es recortar la rama desde `develop` actual.

**Aprobados y parados:** **#247 lleva 20 días aprobado el mismo día y sin un solo evento desde
entonces**; está parado porque nadie lo rebasó, y además toca `contracts/openapi/productos.yaml`, lo que
por la regla 1 exige acuerdo de los tres equipos que nunca se pidió. **#315 lleva 11 días aprobado con
la CI entera en verde y nadie lo fusionó: no hay ningún motivo técnico.**

**Cero PR abiertos del Grupo 6.**

---

## 23 · Definition of Done — técnico vs producto

| HU | DONE técnico | DONE producto | Qué falta para producto |
|---|---|---|---|
| TOR-001, TOR-008 | ✔ | ✔ | Nada — cerrar los issues |
| USR-004 | ✔ | ✖ | CA-05 (D-20) + evidencia |
| HU-JUE-017 #494 | ✔ 6/6 | ✖ | Evidencia en acta + validación del PO |
| TOR-004 | ✔ parcial | ✖ | Bracket reset, rama IA (D-26) |
| ADM-001 | ✔ | ✖ | CA-04 solo demostrado en 1 de 22 claves |
| REN-001 | ✔ salvo CA-03 | ✖ | `LATENCIA_PERCENTIL` + carga real |
| USR-005/006 | ✖ **(defecto)** | ✖ | La sanción no bloquea el acceso |
| COM-005..009 | ✖ | ✖ | Todo |

**La distinción importa ahora mismo:** cuatro HU tienen DONE técnico y no se han cerrado por falta de
evidencia y validación, que es papeleo de media hora. Y dos (USR-005/006) se dieron por hechas sin
tener DONE técnico. Son errores opuestos y hay que corregir los dos.

---

## 24 · Decisiones del PO

**26 decisiones. 19 resueltas de facto. 5 realmente abiertas. Solo 2 exigen al PO.**

| # | Estado | Bloquea | ¿Bloqueo real? |
|---|---|---|---|
| **D-24** premiación + transmisión | **ABIERTA** | TOR-006 #491, TOR-007 #492 | **SÍ — bloqueo duro** |
| **D-26** encuentro ganado por la máquina | **ABIERTA** | TOR-004, TOR-005 | **SÍ (rama IA)** |
| D-20 escalado de advertencias | PARCIAL | USR-004 CA-05 | Sí para CA-05 |
| D-06 push además de STOMP | ABIERTA | HU-NOT-006 | No |
| D-17 retención del registro **(T)** | ABIERTA, sin mitigación | HU-DIS-001 | No |
| D-25 umbral de alta frecuencia **(T)** | ABIERTA | MET-001 CA-01 | Parcial |
| `LATENCIA_PERCENTIL` **(T)** | ABIERTA | REN-001 CA-03 | **Sí, y es autoinfligido** |
| D-01..D-05, D-07, D-10, D-12..D-14, D-16, D-18, D-19, D-21..D-23 | Resueltas por implementación | — | No |
| D-08, D-09, D-11, D-15 **(T)** | Resueltas | — | No |

**(T) = decisión técnica mal clasificada como del PO.** Son ocho: D-08, D-09, D-11, D-15 (umbrales),
D-16 (tamaños), D-17, D-25 y `LATENCIA_PERCENTIL`. **Ninguna es de producto: un timeout, un tamaño de
página, una política de retención o una convención de percentil las decide el equipo.** Podemos
cerrarlas hoy sin reunión.

**Al PO solo van D-24 y D-26.** Si D-24 no llega antes del 30 de septiembre, TOR-006 y TOR-007 no
entran en el Sprint 3.

---

## 25 · Riesgos, CRÍTICO → BAJO

**CRÍTICO**

1. **`heroes` y `motor-combate` sin ninguna seguridad**, y heroes publicado por el borde. Escritura
   anónima desde Internet sobre datos de juego.
2. **E2E en rojo por dos causas**, una de ellas el flujo MVP. Sin E2E verde no hay compuerta de calidad
   real (regla 11) ni DoD (e).
3. **El host de contenido no tiene swap** y opera con −114 MB de margen. El siguiente pico no degrada:
   mata contenedores, y ahí viven los cuatro servicios que el combate necesita.

**ALTO**

4. **Moderación de comentarios en cero** con COM-007 reteniendo contenido de usuarios sin salida, a 17
   días de la liberación del Sprint 3.
5. **Suspender no bloquea el acceso** — defecto funcional en HU declaradas completas.
6. **Segundo EC2 fuera de gobierno**: estado local, una sola persona, sin apagado nocturno, sin
   presupuesto. Viola el Riesgo #5 del Charter de forma textual.
7. **Tres agujeros de RBAC en ms-identidad**, incluido el que acepta el rol por cabecera en perfil `dev`.
8. **`/api/v1/cofres` y `/api/v1/admin/auditoria` rotos por enrutado**, este último dándose por resuelto.

**MEDIO**

9. Trace id y bitácora JSON ausentes en casi todos: con dos hosts, un 503 no se puede seguir.
10. Pact escrito y nunca verificado; contratos sin congelar ni validar en CI.
11. `productos.yaml` promete 6 rutas y el código sirve 3.
12. `ms-cumplimiento` y `/api/v1/auth` sin contrato, con 5 consumidores.
13. Dos PR listos parados (#315, #247) y un duplicado con base equivocada (#269).
14. k6 inexistente: el Sprint 3 compromete informes de carga.
15. Parámetros dinámicos al 13 %.

**BAJO**

16. `.env.example` con credencial literal duplicada; `COLA_MENSAJES_URL` muerta; `main.tf` legado con
    tres t3.micro; `playwright.smoke.config.js` con IP de respaldo; documentación del repo que niega la
    existencia del segundo host.

---

## 26 · Omisiones reales y elementos obsoletos

**Lo que nos saltamos de verdad**

- Ejecutar `comprobar-rutas.sh` en CI — existía y habría evitado #614.
- Verificar los pactos en el proveedor.
- Conectar 19 de las 22 claves del catálogo.
- Cerrar las HU que ya tienen DONE técnico.
- Auditar la seguridad de los servicios del Grupo 2 al integrarlos.
- Revisar el PR de otro grupo que introdujo un EC2 nuevo.

**Lo que ya no aplica**

- **"Un solo t3.small"** — falso desde el 8 de septiembre.
- **"Perfiles de despliegue sobre un único host"** — la restricción cambió de forma.
- **`infrastructure/entornos/main.tf`** con tres t3.micro — código muerto.
- **#435** — resuelto de facto por #611.
- **`plataforma/README.md`** afirmando que no hay `mem_limit` y que el host de contenido no existe.
- **Keycloak** — fuera desde ADR-005; solo quedan dos variables con su nombre.
- **RabbitMQ** — ninguna HU vigente lo exige.

---

## 27 · Nueva ruta técnica

Ordenada estrictamente por riesgo, no por comodidad. Los nombres salen de la evidencia.

### R8 — Cerrar la sangría (CRÍTICO)

1. Seguridad a `heroes` y `motor-combate`: `nexus.seguridad-conventions` + `SecurityConfig`. **Es de
   Grupo 2: se coordina antes de tocar.**
2. Desbloquear el E2E: capa de adaptación del borde en el banco, `comprobar-rutas.sh` en CI, y el
   segundo rojo (`torneos.e2e.spec.js:318`) por separado.
3. Swap de 2 GB al host de contenido.
4. `^~` en `borde-dev.conf:179` + `location` para `/api/v1/cofres` + corregir el comentario falso.

### R9 — Gobierno de los dos hosts (ALTO)

5. Migrar el `tfstate` de contenido a S3, darle OIDC, IMDSv2, cifrado, `default_tags` y presupuesto.
6. Extender el encendido automático y los crons de apagado al host de contenido.
7. Cerrar los tres agujeros de RBAC de ms-identidad y el WebSocket de ms-subastas.
8. Apuntar inventario y productos al emisor real; retirar `jwks-dev`.

### R10 — Sprint 3 donde está el hueco (ALTO)

9. **Moderación de comentarios completa** (COM-005, 006, 008, 009) — el bloque más grande y sin
   dependencia del PO.
10. Que la sanción bloquee el acceso: puente moderacion-sanciones ↔ ms-identidad.
11. Escalar **solo D-24 y D-26** al PO. Cerrar nosotros las ocho técnicas, empezando por
    `LATENCIA_PERCENTIL`.

### R11 — Que se pueda diagnosticar (MEDIO)

12. Trace id y bitácora JSON en los 14 por autoconfiguración; `traceparent` en los clientes.
13. Verificar los pactos en finanzas e inventario; añadir el de salas ↔ inventario.
14. Contrato de `ms-cumplimiento` y de `/api/v1/auth`; congelar versiones y validar en CI.

### R12 — Evidencia y cierre (MEDIO)

15. k6 con los cuatro escenarios justificados; `EXPLAIN` de REN-003.
16. Conectar los consumidores de parámetros (R7.2, lo que quedó suspendido).
17. Higiene de PRs: fusionar #315, rebasar #247 con acuerdo de contrato, cerrar #269/#381/#382.
18. Cerrar las HU con DONE técnico y adjuntar evidencia.

### R13 — Limpieza (BAJO)

19. `.env.example`, `COLA_MENSAJES_URL`, `main.tf` legado, READMEs que contradicen la realidad, #435.

---

## Siguiente bloque único

**R8 — Cerrar la sangría.** Cuatro elementos, todos CRÍTICO, todos sin dependencia del PO y sin coste:
seguridad en los dos servicios anónimos, E2E verde con el guardián en CI, swap en contenido, y los dos
defectos de enrutado. Es lo único que bloquea la compuerta de calidad y la Definition of Done del
bloque entero.
