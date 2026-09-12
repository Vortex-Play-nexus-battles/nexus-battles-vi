# metricas-plataforma (M16A)

Observabilidad del bloque: **disponibilidad** (HU-DIS-001, RNF-DIS-001, issue #71)
y **latencia extremo a extremo** (HU-REN-001, RNF-REN-001, issue #68).

## Disponibilidad — qué hace

Consulta cada 30 s el endpoint de salud de Actuator de cada servicio del bloque,
registra los tramos en que alguno estuvo caído y con eso arma el informe de
disponibilidad que se anexa al informe de avance.

No hay que instrumentar nada en el servicio medido: la regla 3 de plataforma ya
obliga a exponer Actuator y las convenciones de Gradle lo incluyen en los veinte
módulos.

## API

Contrato: [`contracts/openapi/metricas-plataforma.yaml`](../../../contracts/openapi/metricas-plataforma.yaml)

| Método | Ruta | Para qué |
|---|---|---|
| `GET` | `/api/v1/disponibilidad` | Estado en vivo de cada servicio (CP-01) |
| `GET` | `/api/v1/disponibilidad/informe?desde=&hasta=` | Tiempo disponible e interrupciones del periodo (CP-02). Sin fechas, últimos 30 días |
| `GET` | `/api/v1/latencia/informe` | Informe de latencia en JSON (HU-REN-001 CA-02) |
| `GET` | `/api/v1/latencia/informe/texto` | El mismo informe redactado, para pegarlo como evidencia |
| `GET` | `/api/v1/consultas/informe` | Latencia de las consultas a la base de datos (HU-REN-003 CA-01) |
| `GET` | `/api/v1/consultas/lentas` | Registro de consultas lentas (HU-REN-003 CA-03) |
| `GET` | `/api/v1/degradacion` | Qué secciones están limitadas ahora mismo (HU-DIS-003) |

## Cómo se mide (DEC-01)

Umbral del **99,95 % mensual**, adoptado provisionalmente por el Product Owner y
sujeto a confirmación del Cliente. Es una **meta de diseño verificable, no un
acuerdo de nivel de servicio**: la infraestructura opera sobre cuotas gratuitas y
el proveedor no ofrece SLA.

Tres exclusiones, que están implementadas y probadas:

1. **Solo los servicios del bloque.** Los que se miden son los que declara
   `disponibilidad.servicios`; los de los equipos socios no entran en la cifra.
2. **El mantenimiento programado no cuenta.** `VentanaDeMantenimiento` descuenta
   ese tiempo de la indisponibilidad.
3. **La disponibilidad del bloque es la del peor servicio, no el promedio.** Si
   `salas-partidas` está caído el jugador no puede jugar, aunque `correo` responda.

## Configuración

Regla 10 de plataforma: ningún valor real se versiona. Todo sale de variables de
entorno y lo que hay en `application.yml` son los valores de desarrollo local.

| Variable | Por omisión | Para qué |
|---|---|---|
| `DISPONIBILIDAD_UMBRAL` | `99.95` | Umbral mensual de DEC-01 |
| `DISPONIBILIDAD_INTERVALO_MS` | `30000` | Cada cuánto se comprueba la salud |
| `SALUD_<SERVICIO>` | `http://localhost:<puerto>/actuator/health` | Endpoint de salud de cada servicio del bloque |

Añadir un servicio al monitoreo es añadir una entrada en `disponibilidad.servicios`;
no hay que recompilar nada.

## Alertas

`Alertas` es un puerto. Hoy la implementación (`AlertasEnBitacora`) escribe a
nivel `ERROR` en la bitácora estructurada a stdout, que es lo que la regla 6 ya
exige, con campos fijos (`alerta=servicio_caido`, `alerta=disponibilidad_bajo_umbral`)
para poder filtrarlas sin leer el texto.

El destino definitivo —correo, canal de notificaciones o herramienta externa—
está pendiente de acuerdo del equipo (subtarea SCRUM-1143). Cuando se decida, se
cambia el adaptador y no la lógica.

Se alerta **en el flanco**: la primera vez que un servicio sano deja de responder,
no en cada ronda mientras siga caído. Alertar siempre convertiría la alerta en
ruido y nadie la miraría.

## Latencia extremo a extremo (HU-REN-001)

Este módulo **no mide** la latencia: la miden todos los módulos a la vez. El
filtro vive en [`shared/libs/plataforma-observabilidad`](../../../shared/libs/plataforma-observabilidad/)
y llega a los veinte servicios desde `nexus.spring-conventions.gradle`, igual que
JaCoCo o Actuator. Lo que este módulo aporta es el **informe**: percentil,
máximo, y las operaciones más lentas, que es donde hay que mirar cuando el
objetivo no se cumple.

| Variable | Por omisión | Para qué |
|---|---|---|
| `LATENCIA_OBJETIVO_MS` | `500` | Objetivo de RNF-REN-001 |
| `LATENCIA_PERCENTIL` | **ninguno** | Percentil de evaluación — ver abajo |
| `LATENCIA_CAPACIDAD` | `10000` | Tamaño de la ventana de muestras |
| `LATENCIA_OPERACIONES_EN_INFORME` | `5` | Cuántas operaciones lentas lista el informe |

### Por qué `LATENCIA_PERCENTIL` no tiene valor por omisión

**CA-03 exige que el Product Owner apruebe por escrito si RNF-REN-001 se evalúa
en p95 o en p99.** Poner un `95` en el `application.yml` tomaría esa decisión en
su lugar y nadie volvería a mirarla.

Mientras falte esa aprobación:

- la **medición sigue activa** en los veinte módulos y las muestras se acumulan;
- el informe responde **409** con el nombre de la variable, el criterio
  (`HU-REN-001 CA-03`) y cuántas muestras lleva acumuladas.

Fallar así —explícito, localizado en un endpoint— y no al arrancar es
deliberado: arrancar en rojo por una decisión de negocio pendiente tumbaría
servicios de los tres equipos por algo que no es un defecto.

El día que el PO decida, es cambiar la variable. No hay que recompilar, y hay
una prueba que lo demuestra (`cambiarElPercentilCambiaElInformeSinTocarCodigo`).

### Deuda de latencia

El registro vive **en memoria y por proceso**: cada servicio reporta lo suyo y
al reiniciar pierde sus muestras. Todavía no hay agregación entre servicios ni
entre réplicas — es la misma decisión abierta que la de disponibilidad
(SCRUM-1141, SCRUM-1144), y no se inventa aquí.

## Búsquedas indexadas (HU-REN-003)

Las consultas a la base de datos se miden envolviendo el `DataSource`, no pidiéndole a cada
equipo que instrumente sus repositorios. Así la medida cubre lo que genera Hibernate, lo de
Spring Data y lo escrito a mano, y un repositorio nuevo queda medido sin que nadie se
acuerde de añadir una línea.

La sentencia se guarda **con sus marcadores `?`, nunca con los valores**: agrupa todas las
ejecuciones de la misma consulta —sin eso, cada búsqueda de un jugador distinto sería una
consulta distinta y no habría percentil— y evita que datos de jugadores acaben en el
informe.

| Variable | Por omisión | Para qué |
|---|---|---|
| `LATENCIA_CONSULTAS_ACTIVA` | `true` | Válvula de escape por servicio |
| `LATENCIA_CONSULTAS_UMBRAL_MS` | *(el objetivo, 500)* | A partir de aquí una consulta se marca como lenta |
| `LATENCIA_CONSULTAS_CAPACIDAD` | `10000` | Ventana general |
| `LATENCIA_CONSULTAS_CAPACIDAD_LENTAS` | `200` | Ventana de lentas, separada a propósito |

Dos ventanas y no una: con una sola, una racha de consultas rápidas expulsaría justo las
lentas, que son las únicas que hay que optimizar.

El umbral no trae un presupuesto propio de base de datos porque **ningún requisito lo fija**.
Cae en los 500 ms de RNF-REN-001, que son extremo a extremo: una consulta que sola se los
come ya es un problema demostrable. Acordar un presupuesto más estricto es tarea del equipo.

**Las tres búsquedas críticas elegidas, el estado de sus índices y cómo obtener la evidencia
de CA-02 están en [`docs/CONSULTAS-CRITICAS.md`](docs/CONSULTAS-CRITICAS.md)** — incluida una
sospecha de escaneo secuencial en la consulta más frecuente del bloque.

## Degradación controlada (HU-DIS-003)

`GET /api/v1/degradacion` dice qué secciones están limitadas por la caída de otro
servicio, y desde cuándo. Que esa ruta responda **200 mientras hay una sección caída** es,
en sí mismo, la evidencia de CA-01: el servicio sigue en pie y lo está contando.

Cuidado al leer `operativoPorCompleto` al revés: que haya una sección limitada no
significa que el servicio esté caído, sino justo lo contrario.

Los corta circuitos viven en
[`shared/libs/plataforma-resiliencia`](../../../shared/libs/plataforma-resiliencia/).
Esa biblioteca **no** se reparte desde `buildSrc`, a diferencia de la de observabilidad:
solo hace falta donde hay una llamada saliente a otro servicio, y este módulo la declara
en su `build.gradle`.

## Pruebas

```bash
./gradlew :shared:libs:plataforma-observabilidad:test
./gradlew :shared:libs:plataforma-resiliencia:test
./gradlew :services:plataforma:metricas-plataforma:test
```

El dominio (registro, informe, umbral, exclusiones) no conoce Spring y se prueba
sin contexto. La ronda de comprobaciones se prueba con sonda y reloj falsos, sin
red ni esperas.

## Lo que todavía no hace

- **Herramienta externa de monitoreo** (SCRUM-1141) y **panel visual** (SCRUM-1142):
  el equipo aún no ha acordado cuál. Lo que hay aquí es la medición propia, que es
  lo que los criterios de aceptación piden y lo que permite tener evidencia
  acumulada desde el Sprint 1.
- **Persistencia del registro** (SCRUM-1144/1145: recolección centralizada y
  política de retención). Hoy el registro vive en memoria: al reiniciar el
  servicio se pierde el histórico. Antes de la demostración final hay que
  decidir dónde se guarda y con qué retención, porque el informe mensual necesita
  sobrevivir a un reinicio.
