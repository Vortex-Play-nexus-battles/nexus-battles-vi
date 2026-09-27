# R16 — Recuperación de la plataforma: cierre del 24-sep-2026

Estado medido en AWS dev entre las 03:50 y las 05:30 UTC del 24-sep, sobre
`develop` en `b4d942d`. Cada número sale de una corrida de GitHub Actions
citada al lado. Las tablas de HU y del estado inicial son una **auditoría**
de lo que hay en `develop` y en los issues: no definen requisitos nuevos.

## 1. Las cinco pantallas del jugador

| Pantalla | Antes de R16 | Ahora |
|---|---|---|
| Mi cuenta (perfil + créditos) | perfil 400, créditos 502 | 200 (canarios 6/6) |
| Historial | 502 | 200 |
| Cofres | 502 | 200 |
| Tienda / carrito | vitrina vacía; «Añadir» 403 por CORS | 56 productos del catálogo maestro en COP, ninguno a 0; carrito 200 |
| Inventario | 200 | 200 |

Canarios en AWS: 35956414864 (04:38 UTC) y 35959724531 (05:24 UTC, después de
un stop/start real del host), **6/6** las dos. Smoke 35957745786 y 35959177634
en verde. E2E sobre `develop` (35955506386, `b647b2b`): 112 correctas, 1 inestable.

## 2. Capacidad y arranque (detalle en `infrastructure/despliegue/CAPACIDAD.md`)

- Host de plataforma: 12 JVM en régimen ocupan 3052 MiB entre RAM y swap
  (medición 35959726825). Libres: ~140 MiB de RAM y ~225 MiB de swap. **No cabe
  una JVM más**: `ms-subastas` como decimotercera colgó el host a las 03:53.
- Arranque escalonado (#709): tras un stop/start, las 12 JVM sanas a los
  **251 s**. Sin escalonar, a los 9 minutos no respondía ninguna (swap
  2040/2047). Evidencia en el comentario de #709.
- Host de contenido (cuenta del grupo 2): ~650-680 MiB disponibles, swap
  128/2047 (35957716128). Su grupo de seguridad solo deja pasar 22 y
  8101-8104, y esos cinco puertos contestan desde fuera de AWS (comprobado con
  conexiones TCP; 8092, 8093, 8094, 8105 y 5432 quedan filtrados).

## 3. Bloqueos con la acción exacta que los levanta

| Bloqueo | Por qué no se resolvió aquí | Acción que lo levanta | Quién |
|---|---|---|---|
| `ms-subastas` sin desplegar (502 en `/api/v1/subastas`) | En plataforma no cabe (medido); en contenido el grupo de seguridad no deja pasar su puerto, y esa cuenta no tiene OIDC | En la cuenta 551262695144, SG de `nexus-contenido-dev`: abrir TCP 8092 **solo** desde 35.168.124.119/32. Después, en este repo: `claseHost: contenido` para ms-subastas, su compose en el host de contenido, ruta del borde a 34.193.90.11:8092, y abrir 8093 (ms-finanzas) en el SG de plataforma solo desde 34.193.90.11/32 (OpenTofu, `infra-dev.yml` apply) | Grupo 2 (AWS) + Equipo 6 |
| `ms-chatbot` sin desplegar (502 en `/api/v1/chat/*`) | Mismo motivo de capacidad; además la vista no llama al servicio (PR #708 abierto) | La misma vía que subastas (puerto 8094), después de #708 | Grupo 4 + Grupo 2 + Equipo 6 |
| 8101-8104 del host de contenido abiertos a internet | La cuenta es del grupo 2 | Restringirlos a 35.168.124.119/32 en ese SG | Grupo 2 |
| Estado inicial del jugador | Ninguna HU ni D-xx fija saldo, héroes ni misiones iniciales (tabla 5) | Decisión del PO | PO |
| Misiones | `services/contenido/misiones` no tiene código | Implementar HU-MIS-* | Grupo 2 |
| Pago (tienda) | PAG-001 y CAR-010 sin código en develop (PR #382 y #432 abiertos) | Terminar y fusionar esos PR | Grupo 4 |

## 4. Auditoría de HU de las verticales del jugador

`OPERATIVA` = código, CI, AWS y vista conectada, sin criterio incumplido
detectado. Grupo/sprint de los metadatos Jira del issue; `S2*`/`S3*` = sprint
citado en el cuerpo de un issue sin metadatos.

| HU (#) | Servicio | Código (develop) | CI | AWS | Persistencia | Frontend | E2E | Estado |
|---|---|---|---|---|---|---|---|---|
| AUT-001 (#49) Registro | ms-identidad | `POST /auth/registro` | sí | sí | JPA `ddl-auto` (sin Flyway) | cuentas/registro.js | todas (API) | PARCIAL |
| AUT-004 (#50) Login | ms-identidad | `POST /auth/login` | sí | sí | JPA | cuentas/login.js | todas | OPERATIVA |
| AUT-005 (#501) Recuperar cuenta | ms-identidad | `POST /auth/restablecer/*` | sí | sí | JPA | restablecer-*.js | no | PARCIAL (sin preguntas de seguridad) |
| USR-001 (#51) Editar perfil | ms-identidad | `GET/PUT /perfiles/{usuario}` | sí | sí | JPA | cuentas/cuenta.js | canario | PARCIAL (cambio sin auditar) |
| USR-012 (#564) Estadísticas/logros | progresion-jugador | no | no | no | no | no | no | NO IMPLEMENTADA |
| JUE-012 (#470) Créditos por partida | ms-finanzas + salas | `POST /partidas/resultado`, `GET /creditos/{uid}/saldo\|movimientos` | sí | sí | V2, V4; salas V11 | resultado.js, cuenta.js | recompensa-por-partida | OPERATIVA |
| JUE-013 (#482) Cofres | ms-finanzas | `GET /cofres/mios` | sí | sí | V4 | cuentas/mis-cofres.js | canario | PARCIAL (contenido provisional; entrega al inventario sin código) |
| JUE-014 (#442) Apuesta | salas + ms-finanzas | `/creditos/reservar`, `/reservas/{id}/*` | sí | sí | reserva_credito; salas V9 | crear-sala.js | apuesta-de-creditos | OPERATIVA |
| PAG-002 (#536) Transacciones | ms-finanzas | `POST /transacciones`, `GET …/mi-historial` | sí | sí | V1 | historial-transacciones.js | registro-de-transacciones | OPERATIVA (nada la alimenta: no hay pago) |
| PAG-001 (#475) Pasarela simulada | ms-finanzas | no (PR #382) | — | — | no | «Pagar» deshabilitado | no | NO IMPLEMENTADA |
| CAR-001 (#58) Vitrina | ms-ecommerce | `GET /vitrina` | sí | sí | V1, V2 | cuentas/tienda.js | tienda, canario | PARCIAL (sin promociones, deseados ni búsqueda) |
| CAR-004 (#59) Carrito | ms-ecommerce | `GET /carrito`, `POST/DELETE /carrito/items` | sí | sí | V1, V2 | tienda.js | tienda | PARCIAL (sin quitar ni cambiar cantidad en la vista) |
| CAR-010 (#504) Resumen y pago | ms-ecommerce | no (PR #432) | — | — | no | no | no | NO IMPLEMENTADA |
| PRD-001 (#23) Alta de productos | productos | `POST /productos` | sí | sí | Mongo | contenido/productos | tienda | OPERATIVA |
| INV-001 (#20) Consultar inventario | inventario | `GET /inventario/elementos` | sí | sí | Mongo | inventario.js | subastas, canario | OPERATIVA |
| INV-003 (#22) Crear/editar elementos | inventario | `POST/PATCH/DELETE …/elementos` | sí | sí | Mongo | inventario.js | subastas | OPERATIVA |
| INV-005 (#85) Equipamiento | inventario | `…/heroes/{id}/equipamiento` | sí | sí | Mongo | inventario.js | verificacion-heroe | OPERATIVA |
| INV-010 (#115) Bloqueo por subasta | inventario + ms-subastas | `…/{id}/bloqueo-subasta` | sí | parcial | Mongo | publicar-subasta.js | subastas | BLOQUEADA-CAPACIDAD |
| HER-001 (#9) Selección de héroe | heroes | `GET /heroes` | sí | sí | en memoria | detalle-heroe.js | degradacion | PARCIAL (sin endpoint de selección) |
| HER-002 (#10) Estadísticas base | heroes + inventario | `GET /heroes/{nombre}`, `…/estadisticas` | sí | sí | no | detalle-heroe.js | sembrar | OPERATIVA |
| HER-003 (#11) Subida de nivel | heroes | `POST /progresion/experiencia` (solo cálculo) | sí | sí | **nadie guarda nivel/XP** | no | no | SOLO CÓDIGO |
| HER-004 (#12) XP por enemigo | heroes | `GET …/experiencia-por-enemigo/{dado}` | sí | sí | no | no | no | SOLO CÓDIGO |
| SAL-003 (#27) Héroe equipado | salas-partidas | `GET /salas/{id}/verificacion-heroe` | sí | sí | salas V7, V8 | validacion-heroe.js | verificacion-heroe | OPERATIVA |
| JUE-003 (#92) Ataque/defensa | motor-combate | `POST /combate/ataques` | sí | sí | no | combate.js | modalidades | OPERATIVA |
| JUE-005 (#94) Fin de partida | salas-partidas | `GET /partidas/{id}` | sí | sí | salas V6 | sala-batalla.js | modalidades | OPERATIVA |
| JUE-010 (#97) Botín | motor/inventario | no (PR #315) | — | — | no | no | no | NO IMPLEMENTADA |
| SUB-011 (#474) Listado | ms-subastas | `GET /subastas` | sí | **no** | V1, V3 | subastas.js | subastas | BLOQUEADA-CAPACIDAD |
| SUB-001 (#471) Publicar | ms-subastas | `POST /subastas` | sí | **no** | V1, V4 | publicar-subasta.js | subastas | BLOQUEADA-CAPACIDAD |
| SUB-004 (#472) Pujas | ms-subastas | `POST /subastas/{id}/pujas` | sí | **no** | V1, V5 | pujas.js | subastas | BLOQUEADA-CAPACIDAD |
| SUB-006 (#473) Reserva de créditos | ms-subastas + ms-finanzas | `/creditos/reservar` | sí | **no** | reserva_credito | pujas.js | subastas | BLOQUEADA-CAPACIDAD |
| AUD-001 (#60) Auditoría | ms-cumplimiento | `GET /admin/auditoria`, `POST …/eventos` | sí | sí | V1 | cuentas/auditoria.js | no (fuera del banco) | PARCIAL |
| AUD-006 (#500) Auditoría económica | ms-cumplimiento | no | sí | sí | no | no | no | NO IMPLEMENTADA |
| PRV-003 (#543) Consentimiento | ms-cumplimiento | no | sí | sí | no | no | no | NO IMPLEMENTADA (Sprint 3 del grupo dueño) |
| MIS-001…020 (#104…) Misiones | misiones | no | no | no | no | «pendiente» en la barra | no | NO IMPLEMENTADA |
| CHA-001 (#505) Chatbot | ms-chatbot | `POST /chat/mensajes` | sí | **no** | V1-V4 | asistente.js no lo llama (PR #708) | no | BLOQUEADA-CAPACIDAD |
| CHA-002 (#506) Ventana de conversación | ms-chatbot | `/chat/mensajes`, `/chat/historial` | sí | **no** | V1 | no | no | BLOQUEADA-CAPACIDAD |

## 5. Quién es dueño de cada parte del estado inicial del jugador

Solo propiedad y puntos de entrada existentes. **No** se diseña el bootstrap ni
se proponen cantidades.

| Elemento | Servicio · grupo | HU | Punto de entrada que existe hoy | Valor inicial escrito en una HU | ¿Lo toca el registro hoy? |
|---|---|---|---|---|---|
| Perfil | ms-identidad · G4 | #49, #51 | `POST /auth/registro` (crea usuario y perfil) | #49 CA-05: rol Jugador, activo, inventario vacío | Sí (interno) |
| Héroes | heroes (catálogo) + inventario (elemento `HEROE`) · G2 | #9, #10, #22 | `POST /inventario/elementos` con `tipo: HEROE` (jugador con su token o servicio) | #10 CA-01: un héroe nuevo empieza en nivel 1 de su prototipo; cuántos y cuáles, no definido | No |
| Créditos | ms-finanzas · G4 | ninguna (relacionadas #470, #473, #536) | `POST /creditos/acreditar` (solo servicio, idempotente por `refId`) | No definido | No |
| Misiones | misiones · G2 | #104, #101, #130 | no existe | #130: dificultad Normal disponible sin requisito previo | No |
| Inventario | inventario · G2 | #49, #20 | `POST /inventario/elementos` | #49: vacío | No |
| Progresión (nivel/XP) | heroes + progresion-jugador · G2 | #11, #12, #564 | no existe (el cálculo no se guarda) | héroe en nivel 1 (#10); nivel del jugador, no definido | No |

`docs/gobierno/DECISIONES-PENDIENTES-DEL-PO.md` (D-01…D-27) no tiene ninguna
decisión sobre el estado inicial.

## 6. Hallazgos que siguen abiertos

- `ms-identidad` sin Flyway (`ddl-auto`); incumple la regla 8.
- `srv-motor-combate` en el host de contenido corre con `restart: no` y no lo
  creó el CD (el compose declara `unless-stopped`): si ese host se reinicia, el
  combate no vuelve solo. Arreglo: redesplegarlo por el CD.
- `GET /api/v1/perfiles/{usuario}` sin token responde 403 en vez de 401.
- Los crones de `infra-dev.yml` llegan con 4,5-5 h de retraso; un CD a demanda
  pendiente se pierde si llega un push (#703 solo cubre los push).
