"""Exporta la red a ONNX para que misiones la ejecute en Java (ONNX Runtime).

Contrato con Java (`PuntuadorOnnx`): una entrada `caracteristicas` de forma
[n, DIMENSION] (float32) y una salida `puntajes` de forma [n, 1]; n es dinamico.
Opset 17 (estable, soportado por todas las versiones recientes de ONNX Runtime).
"""
import hashlib
import io
import warnings
from pathlib import Path

import numpy as np
import torch

from . import caracteristicas as c

OPSET = 17
ENTRADA = "caracteristicas"
SALIDA = "puntajes"
TOLERANCIA = 1e-5


def escribir(red, destino):
    """Escribe el ONNX en `destino` (ruta o archivo en memoria)."""
    red.eval()
    ejemplo = torch.zeros((2, c.DIMENSION), dtype=torch.float32)
    with warnings.catch_warnings():
        warnings.simplefilter("ignore")
        torch.onnx.export(red, (ejemplo,), destino, input_names=[ENTRADA], output_names=[SALIDA],
                          dynamic_axes={ENTRADA: {0: "n"}, SALIDA: {0: "n"}}, opset_version=OPSET, dynamo=False)


def verificar(red, onnx_bytes, lotes=(1, 4, 26), semilla=0):
    """Compara ONNX Runtime con PyTorch; devuelve la mayor diferencia o lanza AssertionError."""
    import onnxruntime as ort

    sesion = ort.InferenceSession(onnx_bytes, providers=["CPUExecutionProvider"])
    generador = np.random.default_rng(semilla)
    mayor = 0.0
    red.eval()
    for n in lotes:
        x = generador.random((n, c.DIMENSION), dtype=np.float32)
        esperado = red(torch.from_numpy(x)).detach().numpy()
        obtenido = sesion.run(None, {ENTRADA: x})[0]
        mayor = max(mayor, float(np.abs(obtenido - esperado).max()))
    assert mayor < TOLERANCIA, f"El ONNX no coincide con la red de PyTorch (diferencia {mayor})."
    return mayor


def exportar(red, ruta):
    """Escribe `ruta`, comprueba que reproduce a la red y devuelve sus datos (opset, bytes, sha256)."""
    ruta = Path(ruta)
    ruta.parent.mkdir(parents=True, exist_ok=True)
    memoria = io.BytesIO()
    escribir(red, memoria)
    datos = memoria.getvalue()
    verificar(red, datos)
    ruta.write_bytes(datos)
    return {"archivo": ruta.name, "opset": OPSET, "bytes": len(datos), "sha256": hashlib.sha256(datos).hexdigest()}
