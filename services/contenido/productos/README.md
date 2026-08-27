# Productos

Núcleo de disponibilidad del catálogo de THE NEXUS BATTLES VI. El módulo usa
Java 21, dominio puro y TDD para integrarse con la aplicación de HU-PRD-001 sin
duplicar su modelo completo ni publicar endpoints que todavía no estén en el
contrato aprobado.

## Alcance implementado

| Historia | Qué cubre |
|---|---|
| HU-PRD-002 | Tiraje positivo exacto, `-1` ilimitado, rechazo al agotarse y reserva atómica de la última unidad |
| HU-PRD-004 | Suspensión sin borrado, bloqueo de adquisiciones y reactivación al estado previo |

`RepositorioDisponibilidadProductos.adquirirUnaUnidad` declara como parte de
su contrato que la reserva debe ser atómica. El adaptador en memoria usa
`ConcurrentHashMap.compute`, por lo que dos solicitudes simultáneas sobre la
última unidad producen un solo resultado exitoso.

Los estados disponibles son `ACTIVO`, `UNICO` y `SUSPENDIDO`, de acuerdo con la
decisión registrada por el Product Owner en el Issue de HU-PRD-001.

## Integración pendiente de las dependencias

- Conectar el puerto de persistencia a la tabla y transacción que entregue
  HU-PRD-001. La estructura relacional todavía no existe en `main`.
- Exponer las operaciones desde el contrato/API de Productos cuando el equipo
  apruebe sus rutas. Estas HU no inventan endpoints.
- Hacer que la adquisición del módulo proveedor llame a la reserva atómica y
  traduzca `AGOTADO` o `SUSPENDIDO` a la respuesta correspondiente.

## Cómo correr

```powershell
gradle --no-daemon check
```

`check` ejecuta JUnit, genera el reporte JaCoCo y falla si la cobertura de
líneas es inferior al 80 %.
