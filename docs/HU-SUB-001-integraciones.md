# HU-SUB-001: revisión de integraciones

Base inspeccionada: `origin/develop` en `103f4a1`, después de `git pull --rebase origin develop`.
Rama: `feat/hu-sub-001-integraciones`, ya existente, limpia y sin commits propios al empezar.
No se hicieron commits ni push.

## Contratos y cambios recientes

| Área | Evidencia actual | Resultado |
| --- | --- | --- |
| Publicación | `contracts/openapi/ms-subastas-publicar.yaml` | POST `/api/v1/subastas`, `Idempotency-Key`, 201 y errores 400/401/403/404/409/422/503. Duraciones JSON `24H`/`48H`; no admite propiedades adicionales. |
| Sanciones | `contracts/openapi/moderacion-sanciones-consulta.yaml`, PR #336 | Nuevo consumidor HTTP de GET `/api/v1/sanciones/usuarios/{uid}/activa`. El booleano es obligatorio; errores HTTP, JSON inválido, timeout e interrupción nunca se convierten en `false`. |
| Identidad | PR #347, `ValidadorDeToken`, `IdentidadDesdeToken`, `SeguridadConfig` | Reutilizados. `exigirJugadorId()` ya existe y exige `uid`. Se corrige únicamente el caso de `uid` con tipo JSON distinto de string, para devolver 401. No se consulta tokenVersion ni se duplica el validador. |
| Catálogo | `contracts/openapi/productos.yaml`, cliente existente | Conservado GET `/api/v1/productos/{id}`, sus pruebas y `JacksonConfig`. |
| Inventario | `contracts/openapi/inventario.yaml`, modelo/controllers actuales; PR #347 y commits de inventario #309/#310 | El cliente de subastas agregado en #347 contiene rutas que todavía no ofrece el proveedor. No se modificó ni se consideró una integración oficial de publicación. |
| Finanzas | PR #345, `services/cuentas/ms-finanzas` | **Caduco al momento de leerlo:** en la fecha de esta revisión era solo esqueleto, pero el libro de créditos ya está implementado — `contracts/openapi/creditos.yaml` existe y va por 1.4.0, con HU-JUE-012 y HU-JUE-014 probadas en E2E. Lo que sigue sin existir es el contrato/controller de comisión y compensación de subastas. Estado vigente en `services/cuentas/ms-finanzas/README.md`. |
| Listado/modelo | PR #343 y versión actual de `SubastaListadoService` | Se conserva prioridad MdJ solo en orden por defecto. `Subasta.java`, V4, pujas y cierre permanecen sin cambios. |

## Qué queda implementado

- Controller REST y errores de publicación; valida identidad mediante el adapter de Andrés. Con solicitud válida y sin dependencias disponibles responde 503; un JWT ausente, inválido, expirado o sin uid válido responde 401.
- Servicio existente reutilizado. La idempotencia se adquiere atomicamente despues de autenticar y se separa por uid. Un titular procesa; duplicados en curso reciben 409 sin repetir efectos, y duplicados confirmados reutilizan la respuesta.
- La respuesta idempotente se guarda después del commit. Un rollback posterior al flush compensa reserva/débito; si una compensación falla, se registra el identificador de subasta para conciliación y se intenta la otra compensación.
- `ConditionalOnBean` se conserva en `PublicacionAutoConfiguration`, evaluada después del escaneo de componentes. Sin InventarioPublicacionClient o FinanzasPublicacionClient no se crea el servicio; el controller sí está disponible para informar 503. Las pruebas de contexto verifican ausencia/presencia del servicio según sus puertos.
- Sanciones usa el `ObjectMapper` compartido y timeout de conexión/petición. Configuración: `SANCIONES_BASE_URL` (local: `http://localhost:8086`) y `SANCIONES_TIMEOUT_MS` (5000).
- Se mantiene `app.subastas.incremento-minimo` sin valor de negocio por defecto. Sin configuración positiva se rechaza publicar con 503.
- La lógica de 24h/48h, comisión, snapshot, propietario, producto, equipamiento, sanción, unidad activa y compensación se verifica con dobles de puertos. La consulta HTTP de sanciones se prueba además contra un servidor HTTP local.

## Bloqueos para publicación real de extremo a extremo

1. **Inventario:** falta contrato/proveedor de consulta de unidad concreta con `productoId`, propietario estable `uid` y `enUso`; reserva atómica que compruebe propiedad, equipamiento y reserva previa; liberación idempotente asociada a subasta. La ruta `/elementos/{elementoId}` del contrato actual solo tiene PATCH. El modelo sigue usando propietario textual y `X-User-Name`. No hay reserva atómica oficial. No cambiar a `app.inventario.modo=http` suponiendo que las rutas del cliente ya existen.
2. **Finanzas:** faltan rutas, DTO, estados de saldo insuficiente, débito y compensación idempotentes de comisión por uid/subasta. Al no existir `FinanzasPublicacionClient`, no se activa el servicio de publicación en la configuración actual.
3. **Maestro de Juego:** el token firmado entrega `rol`, pero `Role` de identidad solo declara JUGADOR, MODERADOR, ADMINISTRADOR y SUPER_ADMINISTRADOR. El adapter de Andrés sigue entregando `esMaestroDeJuego=false` y documenta esta limitación. Falta acordar la representación oficial del MdJ; no se equipara a administrador ni se inventa un claim/rol. La exención se verifica en la capa de aplicación con identidad de prueba.
4. **Incremento mínimo:** requiere definición/configuración externa según SRS; no se inventó un importe.

No se ha probado publicación end-to-end entre microservicios reales ni se declara la HU terminada en producción.
La idempotencia continua en memoria: coordina solicitudes simultaneas dentro del proceso, pero no persiste reinicios ni coordina replicas. El índice parcial de V4 protege la misma unidad ACTIVA. Fallos de red con resultado remoto ambiguo, caída del proceso o commit desconocido requieren conciliación; no hay todavía contratos externos para resolver esos resultados ni reintentos durables de compensaciones.

## Validación

Comando de suite completa:

```powershell
.\gradlew.bat :services:cuentas:ms-subastas:test :services:cuentas:ms-subastas:check :services:cuentas:ms-subastas:jacocoTestCoverageVerification :services:cuentas:ms-subastas:jacocoTestReport
```

Resultado final con Docker: BUILD SUCCESSFUL en 1m 57s. 335 tests, 0 fallos, 0 errores, 0 omitidos. test, check y jacocoTestCoverageVerification aprobados. Lineas: 1139/1195 (95,31 %); ramas: 432/522 (82,76 %). Las pruebas especificas se ejecutaron antes de la suite completa. PublicacionConcurrenteTest: 7 casos aprobados (5 con dos threads y 2 de limpieza de errores); PostgreSQL: 2 casos aprobados; contexto Spring: 4 casos aprobados; controller: 24 casos aprobados, incluidos 201 completos de 24H y 48H.

## Archivos de esta entrega

Creados:

- `docs/HU-SUB-001-integraciones.md`
- `services/cuentas/ms-subastas/src/main/java/com/nexusbattles/ms_subastas/config/PublicacionAutoConfiguration.java`
- `services/cuentas/ms-subastas/src/main/java/com/nexusbattles/ms_subastas/subastas/api/ManejadorDeErroresPublicacion.java`
- `services/cuentas/ms-subastas/src/main/java/com/nexusbattles/ms_subastas/subastas/api/PublicacionSubastaController.java`
- `services/cuentas/ms-subastas/src/main/java/com/nexusbattles/ms_subastas/subastas/port/InventarioPublicacionClient.java`
- `services/cuentas/ms-subastas/src/main/java/com/nexusbattles/ms_subastas/subastas/port/SancionesClientException.java`
- `services/cuentas/ms-subastas/src/main/java/com/nexusbattles/ms_subastas/subastas/port/SancionesClientHttp.java`
- `services/cuentas/ms-subastas/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `services/cuentas/ms-subastas/src/test/java/com/nexusbattles/ms_subastas/config/PublicacionAutoConfigurationTest.java`
- `services/cuentas/ms-subastas/src/test/java/com/nexusbattles/ms_subastas/subastas/api/PublicacionSubastaControllerTest.java`
- `services/cuentas/ms-subastas/src/test/java/com/nexusbattles/ms_subastas/subastas/port/SancionesClientHttpTest.java`
- `services/cuentas/ms-subastas/src/test/java/com/nexusbattles/ms_subastas/subastas/service/IdempotenciaPublicacionEnMemoriaTest.java`
- `services/cuentas/ms-subastas/src/test/java/com/nexusbattles/ms_subastas/subastas/service/PublicacionConcurrenteTest.java`
- `services/cuentas/ms-subastas/src/test/java/com/nexusbattles/ms_subastas/subastas/service/PublicacionRestriccionPostgresTest.java`

Modificados:

- `services/cuentas/ms-subastas/src/main/java/com/nexusbattles/ms_subastas/seguridad/ValidadorDeToken.java`
- `services/cuentas/ms-subastas/src/main/java/com/nexusbattles/ms_subastas/subastas/dto/PublicarSubastaRequest.java`
- `services/cuentas/ms-subastas/src/main/java/com/nexusbattles/ms_subastas/subastas/model/DuracionSubasta.java`
- `services/cuentas/ms-subastas/src/main/java/com/nexusbattles/ms_subastas/subastas/port/IdempotenciaPublicacion.java`
- `services/cuentas/ms-subastas/src/main/java/com/nexusbattles/ms_subastas/subastas/service/IdempotenciaPublicacionEnMemoria.java`
- `services/cuentas/ms-subastas/src/main/java/com/nexusbattles/ms_subastas/subastas/service/PublicacionSubastaException.java`
- `services/cuentas/ms-subastas/src/main/java/com/nexusbattles/ms_subastas/subastas/service/PublicarSubastaApplicationService.java`
- `services/cuentas/ms-subastas/src/main/resources/application.properties`
- `services/cuentas/ms-subastas/src/test/java/com/nexusbattles/ms_subastas/subastas/service/PublicarSubastaApplicationServiceTest.java`

## Correcciones de la auditoria previa al commit

- Activacion: InventarioPublicacionClient extiende el puerto existente como capacidad especifica de publicacion. La autoconfiguracion exige e inyecta ese tipo. Ni InventarioClientFake ni InventarioClientHttp de pujas lo implementan; no se modifican. No hay implementacion de produccion hasta disponer del contrato oficial. Los tests comprueban fake + finanzas desactivado, HTTP generico desactivado y capacidad dedicada activada con dobles exclusivos de prueba, incluso coexistiendo con el fake.
- Idempotencia: putIfAbsent adquiere uid:key antes de consultas/efectos externos; conserva huella y token de titularidad. Distinta huella devuelve 409 inmediatamente. Misma huella en curso tambien devuelve 409 controlado: esperar dentro de una transaccion podria retener conexiones o bloquear llamadas anidadas. Tras commit devuelve el resultado original. El rollback compensa antes de liberar la clave; un titular antiguo no puede liberar ni confirmar una adquisicion nueva. STATUS_UNKNOWN pasa a INCIERTA (503, conciliacion), nunca a exito ni a un reintento automatico.
- Los callbacks se registran inmediatamente tras adquirir, cubriendo tambien fallos de validacion externos previos a reserva. No hay futures ni esperas en el codigo de produccion. La implementacion sigue siendo local al proceso y no durable.
- Integridad: solo ConstraintViolationException.getConstraintName() igual a uq_subastas_elemento_inventario_activa produce el 409 funcional. Se recorre la cadena de causas con proteccion frente a ciclos; no se analizan mensajes. Cualquier otra DataIntegrityViolationException se relanza intacta y el manejador devuelve 500 tecnico. PostgreSQL real verifica V4 y otra restriccion; la misma excepcion obtenida del proveedor JPA se utiliza para verificar la traduccion del servicio.
- REST 201: el mock devuelve un DTO completo; se compara estrictamente todo el JSON (17 propiedades), tipos, importes y fechas para 24H y 48H.
- Concurrencia: dos threads y latches verifican misma key/solicitud, diferente solicitud, usuarios independientes, fallo durante persistencia y fallo de commit con rollback. TransactionTemplate ejecuta los callbacks de Spring; no se invocan manualmente en estas pruebas. Se verifican efectos y reintentos posteriores.

Archivos modificados respecto de la entrega anterior: PublicacionAutoConfiguration.java, IdempotenciaPublicacion.java, IdempotenciaPublicacionEnMemoria.java, PublicarSubastaApplicationService.java, ManejadorDeErroresPublicacion.java, PublicacionAutoConfigurationTest.java, PublicacionSubastaControllerTest.java, PublicarSubastaApplicationServiceTest.java y este informe.

Archivos nuevos respecto de la entrega anterior: InventarioPublicacionClient.java, PublicacionConcurrenteTest.java, IdempotenciaPublicacionEnMemoriaTest.java y PublicacionRestriccionPostgresTest.java.

## Estado Git final

Rama feat/hu-sub-001-integraciones. Sin staging, commits ni push. git diff --check sin errores. 9 archivos modificados y 14 nuevos sin seguimiento.

git status --short:

```text
 M services/cuentas/ms-subastas/src/main/java/com/nexusbattles/ms_subastas/seguridad/ValidadorDeToken.java
 M services/cuentas/ms-subastas/src/main/java/com/nexusbattles/ms_subastas/subastas/dto/PublicarSubastaRequest.java
 M services/cuentas/ms-subastas/src/main/java/com/nexusbattles/ms_subastas/subastas/model/DuracionSubasta.java
 M services/cuentas/ms-subastas/src/main/java/com/nexusbattles/ms_subastas/subastas/port/IdempotenciaPublicacion.java
 M services/cuentas/ms-subastas/src/main/java/com/nexusbattles/ms_subastas/subastas/service/IdempotenciaPublicacionEnMemoria.java
 M services/cuentas/ms-subastas/src/main/java/com/nexusbattles/ms_subastas/subastas/service/PublicacionSubastaException.java
 M services/cuentas/ms-subastas/src/main/java/com/nexusbattles/ms_subastas/subastas/service/PublicarSubastaApplicationService.java
 M services/cuentas/ms-subastas/src/main/resources/application.properties
 M services/cuentas/ms-subastas/src/test/java/com/nexusbattles/ms_subastas/subastas/service/PublicarSubastaApplicationServiceTest.java
?? docs/HU-SUB-001-integraciones.md
?? services/cuentas/ms-subastas/src/main/java/com/nexusbattles/ms_subastas/config/PublicacionAutoConfiguration.java
?? services/cuentas/ms-subastas/src/main/java/com/nexusbattles/ms_subastas/subastas/api/ManejadorDeErroresPublicacion.java
?? services/cuentas/ms-subastas/src/main/java/com/nexusbattles/ms_subastas/subastas/api/PublicacionSubastaController.java
?? services/cuentas/ms-subastas/src/main/java/com/nexusbattles/ms_subastas/subastas/port/InventarioPublicacionClient.java
?? services/cuentas/ms-subastas/src/main/java/com/nexusbattles/ms_subastas/subastas/port/SancionesClientException.java
?? services/cuentas/ms-subastas/src/main/java/com/nexusbattles/ms_subastas/subastas/port/SancionesClientHttp.java
?? services/cuentas/ms-subastas/src/main/resources/META-INF/
?? services/cuentas/ms-subastas/src/test/java/com/nexusbattles/ms_subastas/config/PublicacionAutoConfigurationTest.java
?? services/cuentas/ms-subastas/src/test/java/com/nexusbattles/ms_subastas/subastas/api/PublicacionSubastaControllerTest.java
?? services/cuentas/ms-subastas/src/test/java/com/nexusbattles/ms_subastas/subastas/port/SancionesClientHttpTest.java
?? services/cuentas/ms-subastas/src/test/java/com/nexusbattles/ms_subastas/subastas/service/IdempotenciaPublicacionEnMemoriaTest.java
?? services/cuentas/ms-subastas/src/test/java/com/nexusbattles/ms_subastas/subastas/service/PublicacionConcurrenteTest.java
?? services/cuentas/ms-subastas/src/test/java/com/nexusbattles/ms_subastas/subastas/service/PublicacionRestriccionPostgresTest.java
```

git diff --stat (archivos seguidos; los 14 nuevos se enumeran arriba):

```text
 .../ms_subastas/seguridad/ValidadorDeToken.java    |   7 +-
 .../subastas/dto/PublicarSubastaRequest.java       |   4 +
 .../subastas/model/DuracionSubasta.java            |  12 ++
 .../subastas/port/IdempotenciaPublicacion.java     |   7 +-
 .../service/IdempotenciaPublicacionEnMemoria.java  |  46 +++++-
 .../service/PublicacionSubastaException.java       |  12 +-
 .../service/PublicarSubastaApplicationService.java | 123 ++++++++++++----
 .../src/main/resources/application.properties      |   3 +
 .../PublicarSubastaApplicationServiceTest.java     | 157 +++++++++++++++++++++
 9 files changed, 336 insertions(+), 35 deletions(-)
```

Auditoria final de las cuatro correcciones: A) SEGURO PARA COMMIT. No implica publicacion end-to-end disponible: siguen faltando contratos/adapters oficiales de Inventario y Finanzas, la representacion MdJ y la configuracion del incremento. Ninguno se invento. Las areas protegidas y el cambio previo de ValidadorDeToken se conservaron.
