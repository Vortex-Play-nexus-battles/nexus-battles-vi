# Simulacro de reversión automática — HU-CICD-002, CA-02

**Fecha:** 21 de septiembre de 2026 · **Entorno:** dev (el único host, `t3.small`) ·
**Corrida:** [cd.yml #35623088299](https://github.com/Vortex-Play-nexus-battles/nexus-battles-vi/actions/runs/35623088299) ·
**Servicio ejercitado:** `comentarios` · **Resultado:** superado.

## Por qué un simulacro

El criterio CA-02 de HU-CICD-002 pide reversión automática ante fallo de salud.
El mecanismo existía (`scripts/cd/revertir.sh`, step «Reversion automatica por
fallo de salud») pero solo en el job de producción, y producción no existe
(D-08). #441 lo dejó como «reversión nunca ejercitada; simulacro sin
documento». Un mecanismo de recuperación que nunca se ha ejecutado no cuenta
como existente.

## Cómo se inyecta el fallo

Entrada `simulacro_reversion=<servicio>` de `cd.yml` (solo a demanda; un push
nunca la activa). `desplegar.sh` despliega ese servicio con la **imagen buena**
de la corrida y `SERVER_PORT=8999` en un override de Compose efímero
(`docker-compose.simulacro.yml`, no versionado). El contenedor arranca sano en
un puerto que nadie publica; el healthcheck del paso 4 consulta el puerto real
y no lo encuentra. Es un fallo de configuración, reproducible, que no toca ni la
imagen ni la base de datos. `revertir.sh` no incluye el override, así que el tag
estable vuelve con su entorno normal.

## Lo que ocurrió (extracto literal del log)

```
== 2) Guardando el tag estable actual de cada servicio, antes de tocarlo ==
  comentarios: tag estable previo = 5eb7788
== SIMULACRO DE REVERSION: comentarios se despliega con SERVER_PORT=8999 (nadie lo publica); debe fallar la salud y revertirse al tag 5eb7788 ==
== 3) Desplegando TAG=9269f29 para: comentarios:8081 ==
== 4) Verificando /actuator/health de cada servicio desplegado (con reintentos) ==
  comentarios (puerto 8081): intento 1/12 sin exito, reintentando en 5s
  ...
  comentarios (puerto 8081): intento 12/12 sin exito, reintentando en 5s
  comentarios: NO paso la verificacion de salud tras 12 intentos
  | Tomcat started on port 8999 (http) with context path '/'
  | Started ComentariosApplication in 14.375 seconds
Uno o mas servicios no pasaron /actuator/health. Detalle en /opt/nexus/ultimo-fallo.txt

[step: Reversion automatica por fallo de salud]
Login Succeeded
Reversion automatica por fallo de salud -> servicio: comentarios | tag fallido: 9269f29 | revertido a: 5eb7788
  la imagen ghcr.io/vortex-play-nexus-battles/nexus-battles-vi-comentarios:5eb7788 sigue en el host: se reutiliza sin pull

[step: Comprobar que el servicio volvio al tag estable y esta sano]
tag de la corrida (fallido): 9269f29 | tag estable esperado: 5eb7788 | tag que corre ahora: 5eb7788
srv-comentarios (puerto 8081) volvio a estar UP en el intento 5 con el tag 5eb7788
ultimo-fallo.txt registro el fallo: comentarios:9269f29:5eb7788

[step: Veredicto del simulacro de reversion]
Simulacro superado: fallo de salud detectado, reversion ejecutada y comprobada
```

Después, el smoke del entorno dev corrió solo y quedó en verde
([smoke-dev #35623432485](https://github.com/Vortex-Play-nexus-battles/nexus-battles-vi/actions/runs/35623432485)).

## Qué demuestra

| Paso de CA-02 | Evidencia |
|---|---|
| El despliegue detecta que el servicio no está sano | «NO paso la verificacion de salud tras 12 intentos», con el log del contenedor (Tomcat en 8999) adjunto |
| Se registra qué revertir y a qué | `ultimo-fallo.txt` = `comentarios:9269f29:5eb7788` |
| La reversión deja el tag estable anterior | «tag que corre ahora: 5eb7788» (≠ 9269f29) |
| El servicio queda operativo | `/actuator/health` UP en el intento 5, puerto 8081; smoke verde |
| Sin intervención manual | Todo dentro de la misma corrida, tres steps con nombre explícito |

## Lo que el simulacro destapó y se corrigió antes de correrlo

`revertir.sh` hacía `docker compose pull` de la imagen estable **sin sesión en
ghcr.io** (las imágenes son privadas): habría fallado justo cuando más falta
hacía. Ahora reutiliza la imagen si sigue en el host (lo normal: `desplegar.sh`
conserva 72 h) y, si no, hace `pull` con el token de la corrida. Los dos steps
de reversión (dev y producción) reciben ese token.

## Lo que NO cambia

En un push normal a `develop` no hay reversión automática en dev: un fallo de
salud deja el job en rojo y el servicio como quedó, para investigarlo, tal como
se acordó con Santiago González (M16B). Activarla siempre es quitar la condición
`inputs.simulacro_reversion != ''` de los tres steps del job `desplegar-dev`.

## Cómo repetirlo

```
gh workflow run cd.yml --ref develop -f servicios=<servicio> -f simulacro_reversion=<servicio>
```

Elegir un servicio fuera del camino crítico del MVP (no `salas-partidas` ni
`ms-identidad`): el servicio queda no disponible ~2 minutos (60 s de ventana de
salud + reversión). `metricas-plataforma` registra esa caída, que sirve de
evidencia para HU-DIS-001.
