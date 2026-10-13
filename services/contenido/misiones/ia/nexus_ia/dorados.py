"""Genera `pruebas/features-dorados.json`: casos con su vector esperado.

Java los lee y comprueba que `Caracteristicas` calcula lo mismo. Uso:
    python -m nexus_ia.dorados          # reescribe el archivo
"""
import json
from dataclasses import asdict
from pathlib import Path

from . import caracteristicas as c
from . import tabla7

ARCHIVO = Path(__file__).resolve().parent.parent / "pruebas" / "features-dorados.json"


def _situacion(prototipo, oponente, **cambios):
    base = dict(
        prototipo=prototipo, nivel=4, turno=3,
        vida=22, vida_maxima=44, poder=6, poder_maximo=12, efectos=1,
        oponente_prototipo=oponente, oponente_nivel=8,
        oponente_vida=30, oponente_vida_maxima=60, oponente_poder=4,
        oponente_poder_maximo=8, oponente_efectos=2,
    )
    base.update(cambios)
    return c.Situacion(**base)


def _caso(situacion, accion, costo):
    return {"situacion": asdict(situacion), "accion": accion, "costo": costo,
            "vector": c.vector(situacion, accion, costo)}


def generar():
    casos = []
    # Una situacion por cada prototipo (propio) contra el siguiente de la lista (oponente).
    for i, prototipo in enumerate(tabla7.PROTOTIPOS):
        oponente = tabla7.PROTOTIPOS[(i + 3) % len(tabla7.PROTOTIPOS)]
        s = _situacion(prototipo, oponente, nivel=1 + i, turno=1 + 2 * i, vida=44 - 5 * i,
                       poder=2 * i, oponente_vida=10 + 7 * i, oponente_efectos=i % 5)
        casos.append(_caso(s, c.ACCIONES[0], 0))
        for nombre, costo in tabla7.ACCIONES_POR_PROTOTIPO[prototipo]:
            casos.append(_caso(s, nombre, s.poder if costo is None else costo))
    # Bordes.
    base = _situacion("Guerrero Armas", "Mago Fuego")
    casos.append(_caso(_situacion("Guerrero Armas", "Mago Fuego", vida_maxima=0, poder_maximo=0,
                                  oponente_vida_maxima=0, oponente_poder_maximo=0, poder=0), "Embate sangriento", 4))
    casos.append(_caso(_situacion("Guerrero Armas", "Mago Fuego", vida=500, poder=99, turno=400, efectos=9, nivel=30,
                                  oponente_nivel=30, oponente_efectos=40), "Golpe de tormenta", 99))
    casos.append(_caso(_situacion("Guerrero Armas", "Mago Fuego", vida=0, oponente_vida=0), c.ACCIONES[0], 0))
    casos.append(_caso(base, "Rugido", 3))
    casos.append(_caso(_situacion("Dragon", None), "Embate sangriento", 4))
    casos.append(_caso(_situacion("Médico", "Chamán", poder=12, poder_maximo=12), "Reanimación", 12))
    return {"version": c.VERSION, "dimension": c.DIMENSION,
            "prototipos": list(c.PROTOTIPOS), "acciones": list(c.ACCIONES), "casos": casos}


def main():
    ARCHIVO.write_text(json.dumps(generar(), ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"Escrito {ARCHIVO}")


if __name__ == "__main__":
    main()
