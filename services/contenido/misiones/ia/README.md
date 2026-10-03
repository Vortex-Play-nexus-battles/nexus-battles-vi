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

`src/test/resources/ia/modelo.onnx` se entrenó con **eventos sintéticos** (`sintetico: true` en su `modelo.json`) y solo existe para probar la carga, la inferencia y el desempate en Java. No hay ningún modelo en `src/main/resources`: el de producción se entrena con los eventos reales de DEV y se entrega por `MISIONES_IA_MODELO_RUTA`.
