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
y se apunta a este directorio con `@PactFolder("contracts/pactos")`. Cada
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

## Por qué esto y no un documento

Riesgo #3 del acta: *contratos que cambian después de ser consumidos*. La
mitigación acordada es congelar y versionar el contrato al inicio de cada
sprint. Un `.md` no se puede congelar: se puede dejar de leer. Esto falla la
compilación.

Caso real, 15/09/2026: durante días `InventarioClientHttp` mandó el UUID del
jugador en la cabecera `X-User-Name`, que inventario compara contra el apodo.
Todo bloqueo real habría salido 403 y ninguna prueba lo notaba, porque las dos
partes se probaban por separado contra su propia idea del contrato.
