---
paths:
  - "services/**/*"
  - "shared/libs/**/*"
  - "contracts/**/*"
  - "infrastructure/**/*"
---

# Backend — Spring Boot (`PILA_T_1.PDF`)

## Arquitectura: microservicios reales, no monolito

Los 20 módulos de la especificación son 20 microservicios independientes, no paquetes dentro de una
sola aplicación. Cada carpeta bajo `services/<dominio>/<módulo>/` es su propio despliegue: su propia
aplicación Spring Boot, su propio proceso/JVM, su propio `build.gradle`, su propio Dockerfile, su
propio esquema de base de datos.

- **Nunca** crear un contexto de Spring compartido entre dos módulos, ni una entidad JPA que se
  importe de un servicio a otro. Si dos servicios necesitan el mismo dato, se pide **por REST**
  (hoy no hay bus de mensajes desplegado; ver «Cómo se comunican los servicios») — nunca se
  comparte la clase ni se hace join entre esquemas.
- `shared/libs/` es la ÚNICA excepción, y solo para utilidades genéricas (formato de error estándar,
  propagación de trazas, tipos comunes, utilidades de prueba) — nunca lógica de negocio ni entidades
  de dominio de un módulo específico.
- **Ejemplo concreto:** `services/plataforma/correo/` y `services/plataforma/notificaciones/` son
  dos microservicios separados entre sí, cada uno con su propio esquema en PostgreSQL — no un solo
  servicio con dos paquetes ni una base de datos compartida entre los dos, aunque ambos formen parte
  del mismo módulo funcional (M15) en la documentación de requisitos.
- ArchUnit (ver más abajo) hace esto verificable: falla el build si un servicio importa clases
  internas de otro dominio, así que ni siquiera es negociable "por practicidad" en un momento de apuro.

## Stack por servicio
- Java 21 LTS (Eclipse Temurin) + **Spring Boot 4.1** — nunca 3.5, fin de vida el 30/jun/2026, sin
  parches de seguridad desde entonces
- Build: **Gradle** con complementos de convención compartidos en `shared/config/` — no crear
  configuración de build propia por servicio
- Spring Web MVC con hilos virtuales
- springdoc-openapi → el contrato se publica en `contracts/openapi/`
- Datos relacionales: Spring Data JPA + Hibernate; **migraciones solo por Flyway**, nunca a mano
- Datos documentales: Spring Data MongoDB (héroes, ítems, inventario)
- Caché / estado en memoria: Spring Data Redis
- Mensajería: **ninguna desplegada**. La pila contemplaba Spring AMQP sobre RabbitMQ 4, pero no hay
  broker en ningún entorno ni dependencia AMQP en ningún `build.gradle`. No añadir una sin decidirlo
  con el equipo: hoy toda integración entre servicios es REST síncrono, y el tiempo real, STOMP
- Tiempo real: Spring WebSocket + STOMP (ver nota de Socket.IO en `frontend-web.md` — el cliente
  debe ser STOMP, son protocolos incompatibles)
- Seguridad: Spring Security 7 como resource server OAuth2 contra **`ms-identidad`**, que es el
  emisor real (RS256, JWKS en `/api/v1/auth/jwks`, variable `IDENTIDAD_JWKS_URL`). Keycloak está
  en la pila pero **no desplegado**: ADR-005 lo sustituyó. Ver ADR-002 y ADR-005
- Resiliencia: Resilience4j (cortacircuitos, reintentos, respaldos)
- Mapeo entre capas: MapStruct (nada de conversión manual repetitiva)
- Instrumentación: Micrometer + Spring Boot Actuator

## Cómo se comunican los servicios
- **Síncrono:** REST sobre HTTPS/JSON — contratos OpenAPI 3.1 en `contracts/openapi/`
- **Tiempo real:** WebSocket con STOMP — contratos en `contracts/websocket/` (sala de batalla,
  chat, subastas, notificaciones, chatbot)
- **Asíncrono: no existe hoy.** La pila preveía RabbitMQ 4 con contratos AsyncAPI en
  `contracts/eventos/`, pero no hay broker desplegado y esa carpeta solo tiene su README. Lo que en
  la documentación se llama «evento» (`partida.finalizada`, avisos de moderación) viaja **por REST
  o por STOMP**, no por cola. Antes de escribir un consumidor AMQP, preguntar al equipo
- **Entrada única:** Spring Cloud Gateway (TLS, enrutamiento, límite de tasa, propagación de trace id)

## Datos
- **PostgreSQL 17** — relacional: usuarios, roles, comentarios, moderación, sanciones, pagos,
  subastas, torneos, **correo, notificaciones**, auditoría, métricas. Un esquema por servicio.
- **MongoDB 8** — documental: héroes, ítems, armas, armaduras, inventario
- **Redis 8** — estado de partidas en curso, sesiones, caché (objetivo de latencia <500ms)
- Esquema analítico append-only en PostgreSQL, exportado a Parquet en almacenamiento de objetos

**Regla dura: ningún servicio accede a la base de datos de otro servicio.** Integración siempre por
interfaz (REST, que es lo que hay desplegado) o, el día que exista un bus, por evento — nunca por
consulta directa a un esquema ajeno.

## Identidad
**El emisor es `services/cuentas/ms-identidad`, no Keycloak.** La pila preveía Keycloak 26
autoalojado y ADR-001 decidió `client_credentials` contra él, pero **nunca se aprovisionó** y no cabe
en el host de desarrollo. ADR-005 lo sustituyó: `ms-identidad` firma RS256 y emite tanto los tokens
de usuario (ADR-002) como las credenciales de servicio (`POST /api/v1/auth/token` con
`grant_type=client_credentials`), publicando su clave en `GET /api/v1/auth/jwks`. Todo resource
server lo verifica por `IDENTIDAD_JWKS_URL`. **Si encuentras una referencia a un realm o a un
`KEYCLOAK_*`, no busques el servidor: no existe.**

Los 5 roles del catálogo de actores (Jugador, Moderador, Administrador, Super Administrador, cuentas
institucionales) se traducen desde el claim del token con `ConversorRolesJwt`, en
`shared/libs/plataforma-seguridad`. Segundo factor obligatorio para cuentas administrativas.

## Infraestructura
- Docker multietapa con imagen base recortada vía jlink, por servicio
- Docker Compose con perfiles por dominio (levantar solo mi dominio + infra común en local)
- k3s para orquestación (escalado automático >75% CPU) · OpenTofu para infra como código
- GitHub Container Registry
- **AWS — cuenta normal con el plan gratuito.** Aprobado por el cliente en agosto de 2026, sustituye
  a Azure for Students. Se descartó **AWS Academy** pese a ser gratuito: sus instancias se detienen al
  cerrar la sesión del laboratorio, y eso hace imposible HU-DIS-001, que exige monitorizar la
  disponibilidad de forma continua.
- **Crédito disponible:** desde el 15 de julio de 2025 AWS entrega 100 USD al abrir cuenta, ampliables
  a 200 completando cinco tareas de iniciación. El plan gratuito dura 6 meses, plazo que cubre el
  semestre. Las cuentas creadas antes de esa fecha conservan el modelo antiguo de 12 meses.
- **Dimensionado planeado:** una instancia `t4g.large` (ARM Graviton, 2 vCPU, 8 GB) con k3s, pensada
  para que cupieran el servidor de identidad y las bases de datos. Orden de magnitud: 45–50 USD/mes.
- **Dimensionado real (vigente):** **dos instancias `t3.small`** (2 vCPU / 2 GiB cada una) con Docker
  Compose, sin k3s — `nexus-plataforma-dev` y `nexus-contenido-dev`. Es lo que cabe bajo la política
  de gasto de bolsillo cero del Free Plan, y es la razón de que no haya Keycloak ni broker: no cabían.
  Qué corre en cada host, en `docs/arquitectura/README.md`.
- **Las bases de datos van como contenedores dentro del clúster, nunca gestionadas.** RDS, DocumentDB
  y ElastiCache agotarían el crédito en semanas. El `docker-compose` local ya las levanta así, con lo
  que el despliegue en nube es un calco del entorno de desarrollo.
- **Desplegar por perfil de dominio, no los 20 servicios a la vez.**
- **Graviton es ARM:** las imágenes con `jlink` deben construirse para `arm64` o ser
  multiarquitectura. Si el equipo de CI/CD prefiere evitarlo, `t3.large` es x86 y cuesta ~20% más.
  Es decisión de HU-CICD-001 y HU-CICD-002, no del resto del equipo.
- **Sobre el objetivo de 99,95% del SRS:** ningún entorno con crédito gratuito lo alcanza. HU-DIS-001
  pide *monitorizar y registrar* la disponibilidad, no garantizarla: se instrumenta, se mide y se
  documenta la diferencia con el objetivo. No prometer en la sustentación una cifra que no se tiene.

## Calidad y pruebas de backend
- JUnit 5 + Mockito (unitarias) · JaCoCo con **umbral de 80% que rompe el build**
- Testcontainers para integración (Postgres/Mongo/Redis efímeros reales). No hay contenedor de
  RabbitMQ ni de Keycloak porque no hay ni broker ni realm: la identidad en las IT se cubre con
  tokens firmados de verdad por el emisor de ADR-005 (fixtures en `shared/libs/plataforma-seguridad`)
- RestAssured para verificar el contrato REST desde fuera del servicio
- **Pact JVM** para contrato entre los tres equipos — mitiga el riesgo #3 del Project Charter
  (contratos que cambian tras ser consumidos). Si el proveedor no existe, desarrollar contra un
  doble generado desde el mismo contrato, nunca uno escrito a mano.
- ArchUnit — falla si un servicio importa clases internas de otro dominio

## Servicios externos — módulo Correo y Notificaciones
- **Correo transaccional: Brevo en producción** (300/día gratis) **· Mailpit en desarrollo**
  (captura los mensajes en local sin enviarlos)
- **Plantilla corporativa: MJML** — resuelve RF-COR-001; el problema real no es el contenido sino
  la compatibilidad del HTML entre clientes de correo
