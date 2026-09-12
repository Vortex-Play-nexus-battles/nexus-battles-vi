# plataforma-observabilidad

Biblioteca compartida de instrumentación de rendimiento — **HU-REN-001** (RNF-REN-001,
issue #68) y **HU-REN-003** (RNF-REN-003, issue #69).

**No es un servicio**: no se despliega ni tiene `main`. La usan los servicios del
bloque de tiempo real para medir el tiempo extremo a extremo de sus peticiones
sin copiar el mismo filtro ocho veces.

## Qué contiene

| Clase | Para qué |
|---|---|
| `FiltroDeLatencia` | `OncePerRequestFilter` que mide cada petición: servicio, método, ruta, código, duración e instante |
| `MuestraDeLatencia` | una petición medida |
| `RegistroDeLatencia` | ventana acotada en memoria + cálculo de percentiles |
| `ObjetivoDeLatencia` | objetivo en ms + percentil de evaluación |
| `InformeDeLatencia` | resultado exportable como evidencia (CA-02) |
| `PropiedadesDeLatencia` | la configuración, solo por variable de entorno (regla 10) |
| `ObservabilidadDeLatenciaAutoConfiguration` | enciende todo lo anterior sola en cualquier servicio |
| `DataSourceInstrumentado` | envuelve el `DataSource` y mide **cada consulta a la base de datos** (HU-REN-003) |
| `RegistroDeConsultas` | ventana de consultas + ventana separada de las lentas |
| `MuestraDeConsulta` / `InformeDeConsultas` | una consulta medida y el informe agregado |

## Cómo llega a los veinte módulos

No se instala: llega. La dependencia está declarada una sola vez, en
`buildSrc/src/main/groovy/nexus.spring-conventions.gradle`, que ya aplican
todos los servicios del monorepo. La autoconfiguración hace el resto.

Un servicio nuevo queda instrumentado **por existir**: no declara la
dependencia, no registra el filtro, no escribe una línea. Eso es CA-01, y es la
razón de que esto viva en `shared/libs/` y no copiado en cada módulo — con
veinte copias, en dos sprints habría veinte variantes midiendo cosas
ligeramente distintas y el informe de RNF-REN-001 no sería comparable entre
servicios.

Cada bean es `@ConditionalOnMissingBean`, así que un equipo que necesite otra
cosa declara el suyo y lo reemplaza. Y `latencia.activa=false` la apaga entera:
mejor una propiedad documentada que alguien excluyendo la dependencia a mano en
su `build.gradle`.

## Decisiones que conviene no deshacer

- **Se mide en un filtro, no en los controladores.** Así la medida abarca el viaje
  completo que atiende el servicio —validación, negocio, base de datos y
  serialización— y no solo el método del controlador.
- **La duración se toma con `System.nanoTime()`**, no con el reloj de pared: un
  ajuste de NTP puede mover el reloj hacia atrás y producir duraciones negativas.
  El reloj inyectado solo pone la marca de tiempo de la muestra.
- **Se mide en `finally`**, así que una petición que termina en excepción también
  cuenta. Si solo se midiera el camino feliz, los fallos lentos —los que más
  molestan al jugador— no aparecerían en ningún percentil.
- **Se agrupa por plantilla de ruta** (`/api/v1/salas/{id}`) y no por URI concreta.
  Con la URI, cada sala sería una operación distinta y no habría percentil que
  calcular.
- **El percentil es por rango más cercano, sin interpolar.** Decir «el p95 fue de
  412 ms» y que esos 412 ms correspondan a una petición que de verdad ocurrió es
  más defendible ante el cliente que un valor interpolado que nunca se observó.
- **La ventana es acotada** (10 000 muestras por omisión). Guardar todo acabaría
  tumbando el servicio que se pretende medir, que es justo lo que la historia
  advierte al pedir instrumentar «sin afectar el rendimiento de la aplicación».
- **Un informe sin muestras no cumple.** No medir no es lo mismo que cumplir.

## Configuración

| Variable | Por omisión | Para qué |
|---|---|---|
| `LATENCIA_OBJETIVO_MS` | `500` | Objetivo de RNF-REN-001. Este sí tiene valor por omisión: no lo decide el equipo |
| `LATENCIA_PERCENTIL` | **ninguno** | Percentil de evaluación. **Obligatorio y a propósito sin valor por omisión** |
| `LATENCIA_CAPACIDAD` | `10000` | Tamaño de la ventana de muestras |
| `LATENCIA_SERVICIO` | `spring.application.name` | Nombre del módulo en las muestras |
| `LATENCIA_OPERACIONES_EN_INFORME` | `5` | Cuántas de las operaciones más lentas lista el informe |
| `LATENCIA_ACTIVA` | `true` | Válvula de escape para apagar la instrumentación en un servicio |

### Por qué el percentil no tiene valor por omisión

CA-03 de #68 dice que el Product Owner debe aprobar **por escrito** si el
requisito se evalúa en p95 o en p99. Poner uno por omisión sería tomar esa
decisión en su lugar y que nadie volviera a mirarla. `ObjetivoDeLatencia` falla
al construirse si el percentil no es válido, y el mensaje de error apunta a
`LATENCIA_PERCENTIL` para que quede claro dónde se arregla.

Cambiar de p95 a p99 el día que el PO decida es cambiar esa variable: **no exige
recompilar ni tocar código**, y hay una prueba que lo demuestra.

Que falte el percentil **no impide medir**. La distinción importa: la
*recolección* no depende de ninguna decisión pendiente y tiene que estar activa
en los veinte módulos desde el Sprint 1. Lo que espera al Product Owner es la
*evaluación* — decir «cumple» o «no cumple» —, y eso falla de forma explícita y
localizada en `GET /api/v1/latencia/informe` (409, con el nombre de la variable
y el criterio), en vez de impedir que arranquen servicios de los tres equipos.

## Medición de consultas (HU-REN-003)

Mismo principio que el filtro, una capa más abajo: en vez de pedirle a cada equipo que
instrumente sus repositorios, se envuelve el `DataSource` y se mide todo lo que pasa por él.
Cubre Hibernate, Spring Data y el SQL escrito a mano, y un repositorio nuevo queda medido
sin que nadie tenga que acordarse de nada.

- **La sentencia se guarda con marcadores `?`, nunca con los valores.** Agrupa las
  ejecuciones de la misma consulta y evita que datos de jugadores acaben en el informe.
- **Se mide en `finally`.** Un tiempo de espera agotado es la consulta lenta por
  excelencia, y es justo la que interesa ver.
- **Medir nunca puede tumbar una consulta.** Si el registro fallara, el error se traga: el
  jugador no va a perder una operación real por culpa de la observabilidad.
- **Delegación fiel, incluidos `unwrap` e `isWrapperFor`.** Son los que usan Hibernate y el
  pool para llegar al objeto concreto; un envoltorio que no los delegue rompe el arranque.
- **Dos ventanas, no una.** Con una sola, una racha de consultas rápidas expulsaría justo
  las lentas, que son las únicas que hay que optimizar.

| Variable | Por omisión | Para qué |
|---|---|---|
| `LATENCIA_CONSULTAS_ACTIVA` | `true` | Válvula de escape por servicio |
| `LATENCIA_CONSULTAS_UMBRAL_MS` | *(el objetivo, 500)* | Cuándo una consulta se marca como lenta |
| `LATENCIA_CONSULTAS_CAPACIDAD` | `10000` | Ventana general |
| `LATENCIA_CONSULTAS_CAPACIDAD_LENTAS` | `200` | Ventana de lentas |

## Estado de HU-REN-001

| CA | Estado | Dónde |
|---|---|---|
| CA-01 · instrumentación activa capturando el tiempo extremo a extremo | ✅ | esta biblioteca + la línea de `nexus.spring-conventions.gradle` |
| CA-02 · exportación de las métricas para el informe | ✅ | `InformeDeLatencia` + `/api/v1/latencia/informe` en `metricas-plataforma` |
| CA-03 · objetivo de 500 ms cumplido en el percentil acordado | ⛔ | **BLOQUEADA — el PO debe aprobar por escrito p95 o p99** |

**La HU no está terminada.** CA-03 no es una tarea pendiente de programar: es una
decisión de negocio sin tomar, y no se puede cerrar la historia eligiendo por el
Product Owner. Mientras tanto la instrumentación acumula evidencia, que es lo
que permitirá responder el día que la decisión llegue.

### Deuda declarada

- **El registro vive en memoria y por proceso.** Al reiniciar un servicio se
  pierden sus muestras, y cada instancia mide solo lo suyo: todavía no hay
  agregación entre servicios ni entre réplicas. Qué se usa para agregar y
  persistir es la misma decisión de equipo que quedó abierta en HU-DIS-001
  (SCRUM-1141, SCRUM-1144), y no se inventa aquí.

## Estado de HU-REN-003

| CA | Estado | Dónde |
|---|---|---|
| CA-01 · medición de las consultas contra el objetivo | ✅ | `DataSourceInstrumentado` + `/api/v1/consultas/informe` |
| CA-02 · el plan de ejecución demuestra uso de índices | ⛔ | **Necesita PostgreSQL con volumen de datos.** Procedimiento y hallazgos en `metricas-plataforma/docs/CONSULTAS-CRITICAS.md` |
| CA-03 · las consultas lentas quedan marcadas | ✅ | `RegistroDeConsultas` + `/api/v1/consultas/lentas` |

**La HU tampoco está terminada.** CA-02 es evidencia de entorno, no de código: hace falta
una base de datos poblada para que el `EXPLAIN` signifique algo. Sobre una tabla vacía el
planificador elige `Seq Scan` aunque el índice sea perfecto.
