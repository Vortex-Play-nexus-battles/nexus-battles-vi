"""Regenera el modelo de PRUEBA que usan las pruebas de Java (src/test/resources/ia).

Se entrena con eventos SINTETICOS (ver `sintetico.py`): sirve para probar la carga, la
inferencia y el desempate en Java; NO es el modelo de produccion.

    python -m nexus_ia.modelo_de_prueba                  # escribe en src/test/resources/ia
"""
import argparse
import sys
from pathlib import Path

from . import entrenar, sintetico

VERSION = "sintetico-v1"


def main(argv=None):
    p = argparse.ArgumentParser(prog="python -m nexus_ia.modelo_de_prueba", description=__doc__.split("\n")[0])
    p.add_argument("--salida", type=Path, default=entrenar.RAIZ_DE_RECURSOS_DE_PRUEBA)
    p.add_argument("--ejecuciones", type=int, default=120)
    p.add_argument("--semilla", type=int, default=11)
    p.add_argument("--epocas", type=int, default=30)
    args = p.parse_args(argv)
    lista = sintetico.generar(ejecuciones=args.ejecuciones, semilla=args.semilla)
    codigo, _ = entrenar.construir_modelo(lista, args.salida, epocas=args.epocas, semilla=args.semilla,
                                          version=VERSION, sintetico=True)
    return codigo


if __name__ == "__main__":
    sys.exit(main())
