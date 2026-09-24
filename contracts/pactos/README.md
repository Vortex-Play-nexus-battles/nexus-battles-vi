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

## Regenerarlos

```bash
./gradlew :services:cuentas:ms-subastas:test --tests '*PactoTest'
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

Los dos pactos se verifican:

| Pacto | Verificación | Cómo |
|---|---|---|
| ms-finanzas | `ms-finanzas/.../contratos/VerificacionDelPactoDeSubastasTest` | servicio arrancado, `CreditoService` simulado, PostgreSQL de Testcontainers |
| ms-inventario | `inventario/.../contratos/VerificacionDelPactoDeSubastasTest` | servicio arrancado, **casos de uso reales** sobre un repositorio en memoria, sin Mongo |

`tests/contratos/pactos-verificados.py` vigila en CI que cada `given(...)`
tenga su `@State`.

La verificación de inventario usa los casos de uso de verdad a propósito, y eso
destapó dos defectos **del pacto**, no del servicio:

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

## Por qué esto y no un documento

Riesgo #3 del acta: *contratos que cambian después de ser consumidos*. La
mitigación acordada es congelar y versionar el contrato al inicio de cada
sprint. Un `.md` no se puede congelar: se puede dejar de leer. Esto falla la
compilación.

Caso real, 15/09/2026: durante días `InventarioClientHttp` mandó el UUID del
jugador en la cabecera `X-User-Name`, que inventario compara contra el apodo.
Todo bloqueo real habría salido 403 y ninguna prueba lo notaba, porque las dos
partes se probaban por separado contra su propia idea del contrato.
