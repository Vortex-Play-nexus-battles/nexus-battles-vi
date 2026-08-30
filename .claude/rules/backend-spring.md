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
  importe de un servicio a otro. Si dos servicios necesitan el mismo dato, se pide por REST o se
  escucha por evento (RabbitMQ) — nunca se comparte la clase ni se hace join entre esquemas.
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
- Mensajería: Spring AMQP sobre RabbitMQ 4
- Tiempo real: Spring WebSocket + STOMP (ver nota de Socket.IO en `frontend-web.md` — el cliente
  debe ser STOMP, son protocolos incompatibles)
- Seguridad: Spring Security 7 como resource server OAuth2 contra Keycloak
- Resiliencia: Resilience4j (cortacircuitos, reintentos, respaldos)
- Mapeo entre capas: MapStruct (nada de conversión manual repetitiva)
- Instrumentación: Micrometer + Spring Boot Actuator

## Cómo se comunican los servicios
- **Síncrono:** REST sobre HTTPS/JSON — contratos OpenAPI 3.1 en `contracts/openapi/`
- **Tiempo real:** WebSocket con STOMP — contratos en `contracts/websocket/` (sala de batalla,
  chat, subastas, notificaciones, chatbot)
- **Asíncrono:** RabbitMQ 4 — contratos AsyncAPI en `contracts/eventos/` (catálogo de eventos es
  entregable de Sprint 2, lo consumen los tres equipos)
- **Entrada única:** Spring Cloud Gateway (TLS, enrutamiento, límite de tasa, propagación de trace id)

## Datos
- **PostgreSQL 17** — relacional: usuarios, roles, comentarios, moderación, sanciones, pagos,
  subastas, torneos, **correo, notificaciones**, auditoría, métricas. Un esquema por servicio.
- **MongoDB 8** — documental: héroes, ítems, armas, armaduras, inventario
- **Redis 8** — estado de partidas en curso, sesiones, caché (objetivo de latencia <500ms)
- Esquema analítico append-only en PostgreSQL, exportado a Parquet en almacenamiento de objetos

**Regla dura: ningún servicio accede a la base de datos de otro servicio.** Integración siempre por
interfaz (REST) o por evento (RabbitMQ), nunca por consulta directa a un esquema ajeno.

## Identidad
Keycloak 26 autoalojado. 5 roles del catálogo de actores (Jugador, Moderador, Administrador, Super
Administrador, cuentas institucionales) modelados como roles de Keycloak. Segundo factor obligatorio
para cuentas administrativas.

## Infraestructura
- Docker multietapa con imagen base recortada vía jlink, por servicio
- Docker Compose con perfiles por dominio (levantar solo mi dominio + infra común en local)
- k3s para orquestación (escalado automático >75% CPU) · OpenTofu para infra como código
- GitHub Container Registry
- **Azure for Students** — 100 USD/integrante = 1.800 USD de crédito de empresa; consumo estimado
  420–630 USD para el semestre. Créditos individuales, no se agrupan — vigilar consumo semanal.

## Calidad y pruebas de backend
- JUnit 5 + Mockito (unitarias) · JaCoCo con **umbral de 80% que rompe el build**
- Testcontainers para integración (Postgres/Mongo/Redis/RabbitMQ/Keycloak efímeros reales)
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
