"""Definicion de las caracteristicas del modelo (VERSION 1).

Es la MISMA que repite Java en `nexus.misiones.ia.Caracteristicas`; la prueba
`features-dorados.json` (generada aqui, verificada alla) garantiza que
coinciden. Si cambias algo de este archivo: sube VERSION, regenera los dorados
(`python -m nexus_ia.dorados`), actualiza la clase de Java y reentrena.

El modelo puntua UNA candidata: recibe el vector [situacion ++ candidata] y
devuelve un numero; la mejor candidata es la de mayor puntaje.

Posiciones del vector (DIMENSION = 54):

    0  vida propia / vida maxima                       [0, 1]
    1  poder propio / poder maximo                     [0, 1]
    2  min(poder propio, 12) / 12                      [0, 1]
    3  min(nivel, 8) / 8                               [0, 1]
    4  min(turno, 20) / 20                             [0, 1]
    5  min(efectos propios, 4) / 4                     [0, 1]
    6  vida del oponente / su vida maxima              [0, 1]
    7  poder del oponente / su poder maximo            [0, 1]
    8  min(nivel del oponente, 8) / 8                  [0, 1]
    9  min(efectos del oponente, 4) / 4                [0, 1]
    10 posicion 0 menos posicion 6 (ventaja de vida)   [-1, 1]
    11-18 one-hot del prototipo propio (orden de Tabla 7)
    19-26 one-hot del prototipo del oponente
    27-51 one-hot de la accion (0 = Ataque basico, 1-24 = Tabla 7)
    52 min(costo, 12) / 12                             [0, 1]
    53 costo / max(poder propio, 1), acotado           [0, 1]

Un prototipo o una accion desconocidos dejan su bloque one-hot en ceros.
"""
from dataclasses import dataclass

from . import tabla7

VERSION = 1

PROTOTIPOS = tabla7.PROTOTIPOS
ACCIONES = (tabla7.ATAQUE_BASICO,) + tabla7.ACCIONES

TOPE_PODER = 12
TOPE_NIVEL = 8
TOPE_TURNO = 20
TOPE_EFECTOS = 4

DIMENSION = 11 + 2 * len(PROTOTIPOS) + len(ACCIONES) + 2


@dataclass(frozen=True)
class Situacion:
    """Lo que se sabe al decidir: el estado propio y el del oponente."""
    prototipo: str
    nivel: int
    turno: int
    vida: int
    vida_maxima: int
    poder: int
    poder_maximo: int
    efectos: int
    oponente_prototipo: str
    oponente_nivel: int
    oponente_vida: int
    oponente_vida_maxima: int
    oponente_poder: int
    oponente_poder_maximo: int
    oponente_efectos: int


def _acotar(x, bajo=0.0, alto=1.0):
    return max(bajo, min(alto, x))


def _fraccion(parte, total):
    return 0.0 if total <= 0 else _acotar(parte / total)


def _one_hot(valor, vocabulario):
    return [1.0 if valor == v else 0.0 for v in vocabulario]


def vector(s, accion, costo):
    """Vector de DIMENSION numeros para la candidata (accion, costo) en la situacion s."""
    vida_propia = _fraccion(s.vida, s.vida_maxima)
    vida_oponente = _fraccion(s.oponente_vida, s.oponente_vida_maxima)
    v = [
        vida_propia,
        _fraccion(s.poder, s.poder_maximo),
        _acotar(s.poder / TOPE_PODER),
        _acotar(s.nivel / TOPE_NIVEL),
        _acotar(s.turno / TOPE_TURNO),
        _acotar(s.efectos / TOPE_EFECTOS),
        vida_oponente,
        _fraccion(s.oponente_poder, s.oponente_poder_maximo),
        _acotar(s.oponente_nivel / TOPE_NIVEL),
        _acotar(s.oponente_efectos / TOPE_EFECTOS),
        vida_propia - vida_oponente,
    ]
    v += _one_hot(s.prototipo, PROTOTIPOS)
    v += _one_hot(s.oponente_prototipo, PROTOTIPOS)
    v += _one_hot(accion, ACCIONES)
    v.append(_acotar(costo / TOPE_PODER))
    v.append(_acotar(costo / max(s.poder, 1)))
    return v
