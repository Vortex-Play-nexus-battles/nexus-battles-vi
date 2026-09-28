# Pactos de consumidor

Contratos ejecutables generados por las pruebas de los **consumidores**. Cada
archivo dice exactamente qué necesita un servicio de otro: rutas, nombres de
campo, códigos y —lo más importante— los `type` URI que distinguen un rechazo
de negocio de una avería.

No los escribe nadie a mano: salen de correr las pruebas del consumidor.

| Archivo | Consumidor | Proveedor | Lo genera |
|---|---|---|---|
| `ms-subastas-ms-finanzas.json` | ms-subastas (HU-SUB-004) | ms-finanzas | `CreditosPactoTest` |
| `ms-subastas-ms-inventario.json` | ms-subastas (HU-SUB-001/004) | ms-inventario | `InventarioPactoTest` |
| `ms-ecommerce-productos.json` | ms-ecommerce (compra, B5) | productos | `contratos/ProductosPactoTest` |
| `ms-ecommerce-inventario.json` | ms-ecommerce (compra, B5) | inventario | `contratos/InventarioPactoTest` |
| `ms-subastas-notificaciones.json` | ms-subastas (B8, avisos de 7.7.8) | notificaciones | `NotificacionesPactoTest` |
| `comentarios-productos.json` | comentarios (B3: comentar y calificar solo productos que existen) | productos | `CatalogoPactoTest` |

## Regenerarlos

```bash
./gradlew :services:cuentas:ms-subastas:test --tests '*PactoTest'
./gradlew :services:plataforma:comentarios:test --tests '*PactoTest'
# ms-ecommerce es Maven: desde services/cuentas/ms-ecommerce
./mvnw -B test -Dtest='*PactoTest'
```

Si un pacto cambia en un commit que no tocaba el cliente, eso **es** la señal:
algo se movió en lo que esperamos del otro servicio.

## Verificarlos (proveedores)

Un pacto no sirve de nada hasta que su dueño lo verifica contra su
implementación. Del lado del proveedor se añade `au.com.dius.pact.provider:junit5`
(no `junit5spring`, que revienta en Spring Boot 4) y se apunta a este directorio
con `@PactFolder("../../../contracts/pactos")`: la ruta es relativa al módulo
de Gradle, no a la raíz del repositorio. Cada
`given(...)` del pacto es un estado que el proveedor tiene que saber montar
antes de responder — por ejemplo *"el jugador no tiene saldo disponible
suficiente"*.

Los estados que hay que poder montar hoy:

**ms-finanzas** (Juan Diego)
- el jugador tiene saldo disponible suficiente
- el jugador no tiene saldo disponible suficiente
- el jugador tiene una cuenta de créditos
- existe una reserva activa del comprador
- no existe ninguna reserva con ese identificador

**ms-inventario** (Nicolay)
- el elemento existe, está disponible y es del propietario indicado
- el elemento existe
- el elemento no existe
- el elemento está bloqueado por esa subasta
- el elemento está bloqueado por esa subasta y va a adjudicarse
- ese elemento ya se transfirió con esa misma clave de idempotencia

**productos** (pacto de ms-ecommerce, B5)
- el producto existe y le quedan unidades
- el producto existe y esta agotado
- el producto existe y esta suspendido
- el producto no existe

**inventario** (pacto de ms-ecommerce, B5)
- los productos de la entrega existen y no estan suspendidos
- la entrega con esa clave ya se hizo con el mismo cuerpo
- un producto de la entrega esta suspendido
- un producto de la entrega no existe en el catalogo

**notificaciones** (B8)
- el destinatario todavia no tiene ese aviso
- el destinatario ya tiene un aviso con ese identificador

(La tercera interacción, sin credencial de servicio, no tiene estado: fija que
`/internal/notifications` responde 401 sin `Authorization`.)

**productos** (B3; lo consume comentarios)
- el producto existe en el catalogo
- el producto no existe en el catalogo

Los pactos se verifican:

| Pacto | Verificación | Cómo |
|---|---|---|
| ms-finanzas | `ms-finanzas/.../contratos/VerificacionDelPactoDeSubastasTest` | servicio arrancado, `CreditoService` simulado, PostgreSQL de Testcontainers |
| ms-inventario | `inventario/.../contratos/VerificacionDelPactoDeSubastasTest` | servicio arrancado, **casos de uso reales** sobre un repositorio en memoria, sin Mongo |
| productos | `productos/.../contratos/VerificacionDelPactoDeEcommerceTest` | servicio arrancado, `AdquirirProductoServicio` y `CatalogoProductos` **reales** sobre el tiraje y el registro de claves en memoria, sin Mongo |
| inventario | `inventario/.../contratos/VerificacionDelPactoDeEcommerceTest` | servicio arrancado, `EntregarProductos` **real** sobre inventarios, entregas y catálogo en memoria, sin Mongo |
| notificaciones | `notificaciones/.../contratos/VerificacionDelPactoDeSubastasTest` | servicio arrancado con su cadena de seguridad real, `ServicioDeNotificaciones` simulado, PostgreSQL de Testcontainers. La credencial de ejemplo del pacto se sustituye por un token de servicio real del emisor de prueba |
| productos | `productos/.../contratos/VerificacionDelPactoDeComentariosTest` | servicio arrancado, caso de uso real (`ConsultarProductoServicio`) sobre `ProductoRepository` simulado, sin Mongo |

El de notificaciones fija lo que falló en producción hasta B8: el adaptador de
ms-subastas salía **sin** credencial de servicio y cada aviso recibía 401, que el
outbox reintentaba para siempre. Ahora la cabecera está en el pacto y la
interacción sin ella deja escrito el 401.

Lo que fija `comentarios-productos.json`, y por qué es tan poco: comentarios
pregunta al catálogo si un producto existe antes de dejar comentarlo o
calificarlo. Pacta que `GET /api/v1/productos/{id}` sea **público** (no manda
token), que un producto que no existe sea **404** —la única respuesta que
comentarios cree como «no existe»; cualquier otra cosa la trata como «el
catálogo no contestó» y no escribe a ciegas— y que uno que existe responda 200
con su `id`, que el cliente compara con el pedido. Ni nombre, ni tipo, ni
precio: nadie los lee. Se comprobó que muerde cambiando el 404 del pacto por un
400: la verificación se puso roja en esa interacción y en ninguna otra.

`tests/contratos/pactos-verificados.py` vigila en CI que cada `given(...)`
tenga su `@State`.

La verificación del pacto de ms-subastas en inventario usa los casos de uso de
verdad a propósito, y eso destapó dos defectos **del pacto**, no del servicio:

- La transferencia exigía `{"elementoId", "propietarioUid"}` en la respuesta.
  Inventario devuelve `id` y no dice de quién es el elemento, y ms-subastas no
  lee ese cuerpo. Se retiró la expectativa, con el mismo criterio que R11.4
  aplicó al bloqueo y a la liberación.
- La liberación se grababa **sin** `Idempotency-Key`. ms-subastas sí la manda,
  e inventario la exige: sin ella responde 400. Con servicios simulados esto
  habría salido verde.

Los dos se comprobaron reintroduciéndolos en una copia del pacto: la
verificación se puso roja en esas dos interacciones y en ninguna más.

La transferencia (`POST /elementos/{elementoId}/transferencias`) va en esta
rama como **propuesta para Nicolay**, que es el dueño de inventario. Si la
rechaza o la cambia, esta verificación es la que le dice qué tiene que seguir
cumpliendo.

Lo que el pacto fija de ese endpoint, y por qué:

- **El nuevo dueño viaja como `nuevoPropietarioUid` (UUID) en el cuerpo**, igual
  que `propietarioUid` en el bloqueo. No puede salir de una cabecera de
  identidad: de las tres llamadas que hace ms-subastas, dos las dispara un
  `@Scheduled` sin petición ni token —el cierre por vencimiento—, y la tercera
  transfiere **al vendedor** como compensación, que no es quien pidió nada.
- **Tiene que ser idempotente.** El cierre corre dentro de una transacción y el
  job reintenta la misma subasta a los 30 s; sin idempotencia, la segunda pasada
  vuelve a mover el producto. La propuesta de inventario la da **por estado**,
  no guardando la clave: si el elemento ya es del nuevo dueño, termina bien sin
  moverlo.
- Un reintento ya aplicado responde **200**, no un error.

### Pactos de la compra de ms-ecommerce (B5)

La compra reserva tiraje (`POST /api/v1/productos/{id}/adquisiciones`,
productos 1.4.0) y entrega lo comprado (`POST /api/v1/inventario/entregas`,
inventario 1.5.0). B4 implementó las dos operaciones; con B4 ya fusionado, cada
proveedor verifica su pacto en una clase propia,
`VerificacionDelPactoDeEcommerceTest`, acotada con `@Consumer("ms-ecommerce")`.
Las verificaciones del pacto de ms-subastas no se tocan.

Cada estado se monta por las operaciones del dominio, nunca escribiendo el
resultado a mano, y reinicia los dobles: el contexto de Spring es uno solo para
las cuatro interacciones.

- **productos**: con unidades, tiraje limitado y ACTIVO; agotado, tiraje 1 y
  otra compra se lleva la última unidad por `AdquirirProductoServicio`;
  suspendido, con unidades y `CatalogoProductos.suspender` (el 409 sale de la
  suspensión, no del tiraje); inexistente, almacén vacío. Las cuatro
  interacciones usan la misma `Idempotency-Key`, así que cada estado vacía
  también el registro de claves.
- **inventario**: el doble del servicio de productos responde el identificador
  del pacto como «Kit de urgencias», un arma del catálogo oficial
  (`contracts/esquemas/catalogo-oficial.yaml`), ACTIVO o SUSPENDIDO, o no lo
  conoce; la entrega repetida la hace antes el propio `EntregarProductos` con la
  misma clave y el mismo cuerpo, y la petición del pacto es su reintento.

Las dos se comprobaron rompiendo a propósito un estado (el suspendido montado
sin suspender): se puso roja esa interacción, con el código y el cuerpo que
esperaba el pacto, y ninguna más.

El proveedor se llama `inventario` y no `ms-inventario` a propósito: la
verificación del pacto de ms-subastas carga todos los pactos de
`ms-inventario` y se pondría roja con cuatro estados que no sabe montar. Las
claves de idempotencia se fijan por forma (`orden-{uuid}` y
`orden-{uuid}-l{linea}-u{unidad}`); el cuerpo de la respuesta de inventario no
se pide porque la compra solo lee el código.

## Por qué esto y no un documento

Riesgo #3 del acta: *contratos que cambian después de ser consumidos*. La
mitigación acordada es congelar y versionar el contrato al inicio de cada
sprint. Un `.md` no se puede congelar: se puede dejar de leer. Esto falla la
compilación.

Caso real, 15/09/2026: durante días `InventarioClientHttp` mandó el UUID del
jugador en la cabecera `X-User-Name`, que inventario compara contra el apodo.
Todo bloqueo real habría salido 403 y ninguna prueba lo notaba, porque las dos
partes se probaban por separado contra su propia idea del contrato.
