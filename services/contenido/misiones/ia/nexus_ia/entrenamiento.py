"""La red y su entrenamiento: imitacion ponderada por resultado.

La red puntua UNA candidata (vector de `caracteristicas.DIMENSION` numeros -> un
puntaje). En cada muestra se comparan los puntajes de todas las candidatas con
una softmax y se maximiza la probabilidad de la que se jugo, con la perdida
multiplicada por el peso de la muestra (mas peso a las jugadas de combates
ganados y de mas dano; ver `eventos`). Es una red chica a proposito: dos capas
ocultas, ~4.500 parametros, ~18 KB en ONNX.
"""
import math
import random
from dataclasses import dataclass, field

import torch
from torch import nn

from . import caracteristicas as c

OCULTAS = (48, 32)


class RedDePuntaje(nn.Module):
    """MLP: [n, DIMENSION] -> [n, 1]."""

    def __init__(self, dimension=c.DIMENSION, ocultas=OCULTAS):
        super().__init__()
        capas, previa = [], dimension
        for n in ocultas:
            capas += [nn.Linear(previa, n), nn.ReLU()]
            previa = n
        capas.append(nn.Linear(previa, 1))
        self.capas = nn.Sequential(*capas)

    def forward(self, x):
        return self.capas(x)


@dataclass
class Lote:
    x: torch.Tensor          # [B, K, F]; las filas de relleno valen cero
    mascara: torch.Tensor    # [B, K]; verdadero donde hay una candidata real
    objetivo: torch.Tensor   # [B]; indice de la elegida
    peso: torch.Tensor       # [B]


@dataclass
class Resultado:
    red: RedDePuntaje
    metricas: dict
    hiperparametros: dict = field(default_factory=dict)


def armar_lotes(muestras):
    """Todas las muestras en tensores con relleno hasta el mayor numero de candidatas."""
    k = max(len(m.candidatas) for m in muestras)
    x = torch.zeros((len(muestras), k, c.DIMENSION), dtype=torch.float32)
    mascara = torch.zeros((len(muestras), k), dtype=torch.bool)
    for i, m in enumerate(muestras):
        for j, (accion, costo) in enumerate(m.candidatas):
            x[i, j] = torch.tensor(c.vector(m.situacion, accion, costo), dtype=torch.float32)
            mascara[i, j] = True
    objetivo = torch.tensor([m.elegida for m in muestras], dtype=torch.long)
    peso = torch.tensor([m.peso for m in muestras], dtype=torch.float32)
    return Lote(x, mascara, objetivo, peso)


def puntajes(red, lote):
    """[B, K] con los puntajes de la red; las filas de relleno quedan en -inf."""
    b, k, f = lote.x.shape
    bruto = red(lote.x.reshape(b * k, f)).reshape(b, k)
    return bruto.masked_fill(~lote.mascara, float("-inf"))


def perdida(puntajes_, mascara, objetivo, peso):
    """Entropia cruzada ponderada, promediada sobre el peso total de las muestras."""
    enmascarados = puntajes_.masked_fill(~mascara, float("-inf"))
    logaritmos = torch.log_softmax(enmascarados, dim=1)
    por_muestra = -logaritmos.gather(1, objetivo.unsqueeze(1)).squeeze(1)
    return (por_muestra * peso).sum() / peso.sum()


def _aciertos(red, lote):
    with torch.no_grad():
        return float((puntajes(red, lote).argmax(dim=1) == lote.objetivo).float().mean())


def _base_azar(lote):
    return float((1.0 / lote.mascara.sum(dim=1).float()).mean())


def separar(muestras, fraccion=0.2, semilla=0):
    """Separa por EJECUCION (no por turno): los turnos de una misma mision se parecen mucho."""
    ejecuciones = sorted({m.ejecucion for m in muestras})
    if fraccion <= 0 or len(ejecuciones) < 2:
        return list(muestras), []
    random.Random(semilla).shuffle(ejecuciones)
    cuantas = min(len(ejecuciones) - 1, max(1, math.ceil(fraccion * len(ejecuciones))))
    para_validar = set(ejecuciones[:cuantas])
    return ([m for m in muestras if m.ejecucion not in para_validar],
            [m for m in muestras if m.ejecucion in para_validar])


def entrenar(muestras, epocas=30, semilla=0, validacion=0.2, lote=64, tasa=3e-3, decaimiento=1e-4,
             ocultas=OCULTAS):
    """Entrena la red. Determinista en CPU para la misma semilla y los mismos datos."""
    if not muestras:
        raise ValueError("No hay muestras con las que entrenar.")
    torch.manual_seed(semilla)
    entrenamiento_, validar = separar(muestras, validacion, semilla)
    tren = armar_lotes(entrenamiento_)
    val = armar_lotes(validar) if validar else None
    red = RedDePuntaje(ocultas=ocultas)
    optimizador = torch.optim.Adam(red.parameters(), lr=tasa, weight_decay=decaimiento)

    def perdida_de(l):
        with torch.no_grad():
            return float(perdida(puntajes(red, l), l.mascara, l.objetivo, l.peso))

    inicial = perdida_de(tren)
    generador = torch.Generator().manual_seed(semilla)
    n = tren.x.shape[0]
    for _ in range(epocas):
        red.train()
        orden = torch.randperm(n, generator=generador)
        for inicio in range(0, n, lote):
            idx = orden[inicio:inicio + lote]
            sub = Lote(tren.x[idx], tren.mascara[idx], tren.objetivo[idx], tren.peso[idx])
            optimizador.zero_grad()
            perdida(puntajes(red, sub), sub.mascara, sub.objetivo, sub.peso).backward()
            optimizador.step()
    red.eval()

    metricas = {
        "perdida_inicial": inicial,
        "perdida_entrenamiento": perdida_de(tren),
        "aciertos_entrenamiento": _aciertos(red, tren),
        "base_azar_entrenamiento": _base_azar(tren),
        "muestras_entrenamiento": len(entrenamiento_),
        "perdida_validacion": perdida_de(val) if val else None,
        "aciertos_validacion": _aciertos(red, val) if val else None,
        "base_azar_validacion": _base_azar(val) if val else None,
        "muestras_validacion": len(validar),
    }
    hiperparametros = {"epocas": epocas, "semilla": semilla, "validacion": validacion, "lote": lote,
                       "tasa": tasa, "decaimiento": decaimiento, "ocultas": list(ocultas)}
    return Resultado(red, metricas, hiperparametros)
