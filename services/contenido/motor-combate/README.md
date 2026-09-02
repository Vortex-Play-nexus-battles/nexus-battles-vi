# Motor de combate

Nucleo de las reglas de combate de THE NEXUS BATTLES VI. El modulo usa Java
21, dominio puro y TDD para que las reglas puedan integrarse despues con la
capa de aplicacion sin acoplarlas a transporte o persistencia.

## Alcance implementado

| Historia | Que cubre |
|---|---|
| HU-JUE-001 | Sorteo uniforme del orden inicial, cada participante exactamente una vez, secuencia inmutable durante el combate y avance circular entre rondas |

El sorteo usa Fisher-Yates y `SecureRandom` en produccion. Las pruebas inyectan
un generador con semilla para que la validacion estadistica sea reproducible.

## Como correr

```powershell
.\gradlew.bat check
```

La tarea `check` ejecuta JUnit y falla si la cobertura de lineas es inferior al
80 %. El reporte HTML queda en `build/reports/jacoco/test/html/index.html`.

## Pendiente

- Integrar `ColaTurnos` con la entidad `Partida` cuando esta se implemente.
- Exponer el inicio de partida desde la capa de aplicacion; HU-JUE-001 no exige
  por si sola un endpoint.
