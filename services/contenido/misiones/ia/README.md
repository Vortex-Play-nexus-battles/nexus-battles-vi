# IA de combate: entrenamiento de la red propia (HU-SIM-008)

Decisión del PO (`decisiones.md`, 2026-10-01): una **red neuronal pequeña, propia, entrenada por el equipo** con PyTorch sobre los eventos de combate que guarda HU-SIM-003, exportada a ONNX y ejecutada en Java (ONNX Runtime) dentro del servicio de misiones. **El modelo propone y la regla acota**: el modelo solo elige entre jugadas que la regla de heroes ya admite (rotación, poder, recarga); si falla o no está seguro, decide la regla de siempre.

Esta carpeta es el lado de Python. El lado de Java vive en `src/main/java/nexus/misiones/ia/` y se explica en el README del servicio.

## Qué hay

| Ruta | Para qué |
|---|---|
| `nexus_ia/tabla7.py` | Los 8 prototipos y sus 24 acciones (Tabla 7), con costo y nivel de desbloqueo (RC-01) |
| `nexus_ia/caracteristicas.py` | **La definición de características (versión 1)**: el vector de 54 números que ve la red. Java repite la misma definición en `nexus.misiones.ia.Caracteristicas` |
| `nexus_ia/eventos.py` | Lee `eventos_de_combate` (MongoDB o JSONL) y arma las muestras: situación, candidatas, elegida, peso |
| `nexus_ia/entrenamiento.py` | La red (MLP 54 → 48 → 32 → 1, ~4.500 parámetros), la pérdida y el entrenamiento |
| `nexus_ia/exportacion.py` | Exporta a ONNX (opset 17) y comprueba que reproduce a PyTorch |
| `nexus_ia/entrenar.py` | La línea de comandos |
| `nexus_ia/sintetico.py`, `modelo_de_prueba.py` | Eventos **sintéticos** y el modelo de **prueba** que usa Java. No son producción |
| `nexus_ia/dorados.py` | Genera `pruebas/features-dorados.json`, que Java verifica |
| `modelos/<versión>/` | **Los modelos entrenados que viajan en la imagen** (`modelo.onnx` + `modelo.json`). Hoy `1.0.0` |
| `pruebas/` | pytest (69 pruebas) |

## Cómo se entrena

```bash
python -m venv D:/ML/venvs/sim008        # ruta corta: las largas de Windows rompen los venv
D:/ML/venvs/sim008/Scripts/pip install -r requirements.txt \
    --extra-index-url https://download.pytorch.org/whl/cpu   # torch solo CPU

cd services/contenido/misiones/ia

# con los eventos reales (solo lectura sobre la coleccion)
python -m nexus_ia.entrenar --mongo-uri mongodb://HOST:27017/misiones --salida salida/

# con un export: mongoexport --collection eventos_de_combate --db misiones --out eventos.jsonl
python -m nexus_ia.entrenar --jsonl eventos.jsonl --salida salida/
```

Deja `salida/modelo.onnx` (≈18 KB) y `salida/modelo.json` (versión, fecha, número de eventos, métricas, hash del ONNX, versión y dimensión de las características). Opciones útiles: `--mision`, `--epocas`, `--semilla`, `--validacion`, `--peso-derrota`, `--version`.

### Qué aprende y cómo

**Imitación ponderada por resultado** (*reward-weighted imitation*). Cada turno registrado es una muestra: la situación al decidir (`antes`), las candidatas y la acción que se jugó. La red puntúa cada candidata y se entrena para que la jugada hecha tenga la mayor probabilidad (softmax sobre las candidatas), con la pérdida multiplicada por un peso:

```
peso = (1 si el bando del actor ganó el encuentro, 0,25 si no) × (1 + daño aplicado / vida máxima del rival)
```

Gana el bando que sigue en pie en el último turno del encuentro. Con esto, las jugadas de combates ganados y las que más vida le quitaron al rival pesan más. La validación se separa **por ejecución**, no por turno.

**Candidatas.** Si el evento trae `jugada.candidatas` (lo registra la IA con modelo encendido) se usan esas. Si no (eventos de la regla sola, que es lo único que habrá hasta encender el modelo), se **derivan**: ataque básico más lo que el actor tenía desbloqueado en su nivel (RC-01), con poder para pagarlo y fuera de recarga. `modelo.json` dice cuántas muestras fueron de cada tipo.

**Lo que se descarta** (y se cuenta en `descartes`): turnos sin jugada, jugadas en valor base (faltó poder: el motor y heroes discreparon), acciones fuera de la Tabla 7 (épicas) y jugadas fuera de sus candidatas.

**Lo que se ignora.** La jugada de un enemigo trae además `estrategia` (`MISION`, `PREDEFINIDA` o `HEURISTICA`) y `estrategiaId` (HU-SIM-004: de dónde salieron las rotaciones con que jugó). Sirven para auditar cada estrategia, no para aprender: `eventos.py` no las lee y `test_eventos.py` comprueba que las muestras son las mismas con y sin ellas.

### Límite honesto

Mientras solo haya eventos de la regla, el modelo aprende, sobre todo, a imitar la regla. Lo que aporta de nuevo viene de dos fuentes: el peso por resultado y, sobre todo, los eventos que genere la IA con modelo encendido (con sus candidatas y su versión), que traen jugadas que la regla no habría elegido. Por eso el ciclo es: encender, dejar que se acumulen partidas, reentrenar.

## El modelo 1.0.0 (el que viaja en la imagen)

`modelos/1.0.0/modelo.onnx` y `modelo.json`. Es el primer modelo entrenado con partidas reales y el que la imagen de misiones lleva a `/app/ia/`.

**Procedencia.**

- 689 eventos de combate de 33 ejecuciones de misión, en AWS DEV, exportados el 5-oct-2026 con `mongoexport` en solo lectura (el `modelo.json` guarda la fecha de entrenamiento en UTC: `2026-10-06T02:50:06Z`, las 21:50 del 5-oct en Colombia).
- Los eventos **no están en el repositorio**: son datos de jugadores de DEV. Se guardan fuera (`D:/ML/datos/sim008/eventos-dev-2026-10-05.jsonl` en la máquina de quien entrenó).
- Se entrenó con `python -m nexus_ia.entrenar --jsonl eventos-dev-2026-10-05.jsonl --semilla 7 --version 1.0.0`, con el venv `D:/ML/venvs/sim008`.
- Ningún evento traía candidatas registradas (los decidió la regla sola): las 689 muestras usan candidatas **derivadas** (ataque básico más lo desbloqueado, con poder y fuera de recarga). No se descartó ninguna.

**Métricas** (`modelo.json`, 30 épocas, semilla 7, validación 20 % separada por ejecución):

| | Entrenamiento | Validación |
|---|--:|--:|
| Muestras | 486 | 203 |
| Pérdida | 0,0049 | 0,00058 |
| Aciertos | 0,998 | 1,000 |
| Base al azar (elegir una candidata cualquiera) | 0,722 | 0,736 |

**Qué significa que imite la regla.** Los 689 eventos los decidió la regla de rotaciones de heroes, así que el modelo aprendió a prever **lo que ella habría jugado**: el 1,0 de validación mide eso y no que el modelo juegue mejor que la regla (ni peor). Con él encendido, el servicio se comporta casi igual que con la regla sola, pero ya decide una red entrenada con partidas reales: cada jugada que el modelo decide queda con `decididaPor: MODELO`, `versionDelModelo: "1.0.0"` y las **candidatas con su puntaje**. Las versiones siguientes aprenden de esas partidas, que sí registran las candidatas, **ponderadas por resultado** (se refuerzan las jugadas de los combates ganados y las que más vida le quitaron al rival): ahí puede aparecer una jugada que la regla no habría elegido. Con solo 33 ejecuciones de partida, es el primer paso del ciclo, no el último.

## Reentrenar y publicar una versión nueva

1. **Exportar** los eventos de DEV, solo lectura (nunca se escribe en la base):

   ```bash
   mongoexport --uri mongodb://HOST:27017/misiones --collection eventos_de_combate --out eventos-<fecha>.jsonl
   ```

   Los `.jsonl` no se suben al repositorio.
2. **Entrenar** con una versión nueva (semver; la `1.0.0` no se reutiliza):

   ```bash
   cd services/contenido/misiones/ia
   python -m nexus_ia.entrenar --jsonl eventos-<fecha>.jsonl --semilla 7 --version 1.1.0 --salida salida/
   ```

   Revisar en `salida/modelo.json` las métricas, los `descartes` y cuántas muestras traen candidatas **registradas**.
3. **Versionar** `salida/modelo.onnx` y `salida/modelo.json` en `ia/modelos/<versión>/`. Si cambió la definición de las características, ver "Si cambias las características" (Java se niega a cargar un modelo de otra versión).
4. **Cambiar la carpeta que copia el `Dockerfile`** (`COPY --from=construccion /repo/services/contenido/misiones/ia/modelos/<versión>/ /app/ia/`) y la constante `VERSION` de `ModeloVersionado` (pruebas) junto con el hash y la procedencia que comprueba `ModeloVersionadoTest`. Esas pruebas fallan hasta que carpeta, `Dockerfile` y constante coinciden.
5. **Pasar por PR**: la rama corta, `gradlew :services:contenido:misiones:test` y `python -m pytest pruebas -q`. El despliegue lo hace el pipeline al fusionar; nadie copia un modelo a mano a una instancia.

Para volver atrás basta apuntar la carpeta del `Dockerfile` a la versión anterior (o apagar el modelo con `MISIONES_IA_MODELO_HABILITADO=false` mientras tanto).

## Pruebas

```bash
cd services/contenido/misiones/ia
python -m pytest pruebas -q
```

- `test_caracteristicas.py`: el vector, posición por posición.
- `test_dorados.py`: `pruebas/features-dorados.json` coincide con lo que genera el código; Java lo lee (`DoradosDeCaracteristicasTest`) y comprueba que su `Caracteristicas` da lo mismo.
- `test_eventos.py`, `test_sintetico.py`: lectura, candidatas, pesos y el formato del documento.
- `test_entrenamiento.py`, `test_exportacion.py`: aprende, es determinista, y el ONNX da lo mismo que PyTorch.

### Si cambias las características

Súbele `VERSION` a `caracteristicas.py`, actualiza `Caracteristicas.java`, y corre:

```bash
python -m nexus_ia.dorados            # regenera pruebas/features-dorados.json
python -m nexus_ia.modelo_de_prueba   # regenera src/test/resources/ia (el modelo de prueba de Java)
```

Java se niega a cargar un modelo cuya versión o dimensión de características no coincida con la suya.

## El modelo de prueba no es el de producción

`src/test/resources/ia/modelo.onnx` se entrenó con **eventos sintéticos** (`sintetico: true` en su `modelo.json`) y solo existe para probar la carga, la inferencia y el desempate en Java. El de producción es el de `modelos/1.0.0/` (`sintetico: false`), que entra a la imagen por el `Dockerfile` y se carga desde `/app/ia/modelo.onnx` (`MISIONES_IA_MODELO_RUTA`). No hay ningún modelo en `src/main/resources`.
