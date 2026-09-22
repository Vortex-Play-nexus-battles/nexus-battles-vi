# Motor de combate

Nucleo de las reglas de combate de THE NEXUS BATTLES VI. El modulo usa Java
21, dominio puro y TDD para que las reglas puedan integrarse despues con la
capa de aplicacion sin acoplarlas a transporte o persistencia.

## Alcance implementado

| Historia | Que cubre |
|---|---|
| HU-JUE-001 | Sorteo uniforme del orden inicial, cada participante exactamente una vez, secuencia inmutable durante el combate y avance circular entre rondas |
| HU-JUE-002 | Una sola accion por turno, avance al resolver o expirar y la misma duracion configurable para todos los participantes |
| HU-JUE-005 | Fallecimiento al llegar a cero vida, cierre individual o por equipos y rechazo de acciones posteriores |
| HU-JUE-007 | Tope de 6 minutos, derrota por 1 minuto de inactividad, vida conservada y estrategia de desempate |
| HU-JUE-008 | Proteccion contra dano a companeros en combate cooperativo, salvo que la accion permita afectar aliados expresamente
| HU-JUE-010 | Botin por derrota con evaluacion independiente de la tasa de caida de cada arma, armadura o item equipado o almacenado por el enemigo |

El sorteo usa Fisher-Yates y `SecureRandom` en produccion. Las pruebas inyectan
un generador con semilla para que la validacion estadistica sea reproducible.

Todos los cierres producen un único `ResultadoPartida` y pasan por el puerto
`AlCerrarPartida`, que permite conectar recompensas e historial sin tratar de
forma distinta los cierres por supervivencia, tiempo o inactividad.

El botin se procesa unicamente cuando un ataque cambia al objetivo de activo a
derrotado con cero puntos de vida. El motor consulta la tasa configurada en
productos, identifica en inventario si cada candidato estaba equipado o
almacenado y registra en el inventario del ganador todos los objetos cuyo sorteo
resulte exitoso. Las integraciones usan `PRODUCTOS_URL` e `INVENTARIO_URL` sin
modificar los servicios que implementan esas historias.

El criterio concreto para un empate exacto no está documentado todavía en el
repositorio. `CriterioDesempate` lo recibe por inyección para que el equipo
pueda conectar la decisión aprobada sin inventar una regla provisional.

## Como correr

```powershell
.\gradlew.bat check
```

La tarea `check` ejecuta JUnit y falla si la cobertura de lineas es inferior al
80 %. El reporte HTML queda en `build/reports/jacoco/test/html/index.html`.

## Pendiente

- Configurar el `CriterioDesempate` cuando el cliente documente la regla.
- Exponer el inicio de partida desde la capa de aplicacion; HU-JUE-001 y
  HU-JUE-002 no exigen por si solas un endpoint.

## Despliegue

Este servicio se despliega en el host propio del dominio de contenido (`infrastructure/entornos/contenido/`), por el flujo `cd.yml` (job `desplegar-contenido-dev`) en cada push a `develop` que toque `services/contenido/motor-combate`. Queda publicado en el puerto **8104** del host (8080 dentro del contenedor), consumiendo héroes, productos e inventario mediante `HEROES_URL`, `PRODUCTOS_URL` e `INVENTARIO_URL`. Salud: `http://<ip-del-host>:8104/actuator/health`.

La imagen lleva la etiqueta propia de este servicio (`TAG_MOTOR_COMBATE`, el sha corto del push que lo cambió); las dependencias que no cambiaron conservan la etiqueta que ya tienen desplegada. Lo resuelve `resolver_etiquetas_contenido` en `scripts/cd/desplegar.sh`.
