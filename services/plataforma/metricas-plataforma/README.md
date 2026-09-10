# metricas-plataforma (M16A)

Monitoreo y registro de disponibilidad de los servicios del bloque — **HU-DIS-001**
(RNF-DIS-001, issue #71).

## Qué hace

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

## Pruebas

```bash
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
