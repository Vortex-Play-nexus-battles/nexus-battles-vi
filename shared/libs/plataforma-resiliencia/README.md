# plataforma-resiliencia

Degradación controlada ante la caída de un microservicio — **HU-DIS-003**
(RNF-DIS-003, issue #72).

**No es un servicio**: no se despliega ni tiene `main`.

## Por qué esta biblioteca NO se reparte desde `buildSrc`

`plataforma-observabilidad` sí se reparte a los veinte módulos, porque su criterio de
aceptación exige que la instrumentación esté en todos. Un corta circuitos es distinto:
solo hace falta **donde hay una llamada saliente a otro servicio**. Repartirlo a todos
metería un jar que la mayoría no usaría y ampliaría el radio de impacto sin ganar nada.

El servicio que lo necesita lo declara en su `build.gradle`, una línea:

```groovy
implementation project(':shared:libs:plataforma-resiliencia')
```

## Qué contiene

| Clase | Para qué |
|---|---|
| `CortaCircuitos` | la máquina de tres estados que corta la cascada (SCRUM-1146) |
| `EstadoDelCorta` | `CERRADO` · `ABIERTO` · `SEMIABIERTO` |
| `DependenciaDegradada` | la excepción que nombra **qué sección** queda limitada |
| `RegistroDeDegradacion` | qué secciones están limitadas ahora y desde cuándo |
| `ErroresDeDegradacion` | el problem detail estándar de sección limitada (regla 4) |
| `ManejadorDeDegradacion` | lo traduce a la respuesta HTTP, idéntico en los 20 módulos |

## Cómo se usa

```java
// Un corta circuitos por dependencia. Los umbrales los decide el servicio que
// llama, porque solo él sabe qué tan crítica es esa llamada.
var inventario = new CortaCircuitos(
        "inventario", "Inventario", 3, Duration.ofSeconds(30), Clock.systemUTC(), registro);

// Con respuesta alternativa: el jugador no ve ningún error.
var equipo = inventario.ejecutar(
        () -> clienteInventario.consultar(jugador),
        () -> Equipamiento.desconocido());

// Sin respuesta alternativa razonable: el frontend pinta la sección limitada.
var equipo = inventario.ejecutarOFallar(() -> clienteInventario.consultar(jugador));
```

## Decisiones que conviene no deshacer

- **Protege al que llama, no al que está caído.** Seguir llamando a un servicio que no
  responde consume un hilo y un tiempo de espera por intento; con suficiente tráfico el
  que se queda sin hilos es el llamante. Ahí es donde la caída de un microservicio se
  convierte en la caída de la aplicación.
- **Un fallo aislado no abre nada.** La red pierde un paquete de vez en cuando; abrir por
  eso dejaría sin servicio una sección que funciona.
- **El semiabierto deja pasar UNA llamada.** Si pasaran todas, una avalancha caería sobre
  un servicio que quizá aún se está levantando.
- **La llamada se hace fuera del bloque sincronizado.** Bloquear durante una llamada
  remota convertiría el corta circuitos en el cuello de botella que pretende evitar.
- **503 y no 500.** 500 significa «este servicio se rompió»; aquí el servicio está
  perfectamente y es una dependencia la que no responde.
- **La excepción lleva `seccion`.** Sin ese dato el frontend solo podría decir «algo
  falló», y el criterio pide que el jugador vea **qué función** está limitada.
- **Sin dependencias externas.** Un corta circuitos es una máquina de tres estados y un
  contador. Traer una biblioteca de resiliencia entera añadiría configuración, métricas y
  modos de fallo que nadie del equipo ha acordado.

## Configuración

| Variable | Por omisión | Para qué |
|---|---|---|
| `RESILIENCIA_REINTENTAR_EN_SEGUNDOS` | `30` | Lo que se anuncia al cliente en `Retry-After` |

Los umbrales de cada corta circuitos **no** son configuración global: los pasa el servicio
al construirlo, porque dependen de qué tan crítica sea esa llamada concreta.

## Estado de HU-DIS-003

| CA | Estado | Dónde |
|---|---|---|
| CA-01 · inyección de fallos sin caída total | ✅ (unitario) | `CortaCircuitosTest` — la dependencia lanza y el llamante sigue sirviendo |
| CA-02 · el usuario ve el aviso de función limitada | ✅ | `ErroresDeDegradacion` + `comun/degradacion/aviso-degradacion.js` + `MAPEO-ERRORES.md` §5.5 y §7 |
| CA-03 · el resto de servicios sigue operando | ✅ (unitario) | `laCaidaDeUnaDependenciaNoAfectaALasDemas` + `GET /api/v1/degradacion` |

**La HU no está terminada.** Falta:

- **SCRUM-1148 — inyección de fallos apagando contenedores.** La prueba automatizada
  inyecta el fallo haciendo que la dependencia lance; apagar un contenedor de verdad es
  otra prueba y es de entorno. CP-01 pide esa evidencia.
- **SCRUM-1150 — cola de reintentos para procesos asíncronos.** No entra aquí: el bus de
  mensajería todavía no está montado en `develop`, y una cola de reintentos sin bus sería
  inventar la mitad del diseño.
- **Cablear los corta circuitos en las llamadas salientes reales.** Hoy `develop` no tiene
  ninguna: `salas-partidas` y `comentarios` aún no han integrado sus clientes.
