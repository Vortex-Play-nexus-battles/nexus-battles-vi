"""Entrena la red con los eventos de combate y deja `modelo.onnx` + `modelo.json`.

    # con los eventos reales de la coleccion (solo lectura)
    python -m nexus_ia.entrenar --mongo-uri mongodb://localhost:27017/misiones --salida salida/

    # con un export (una linea JSON por evento, p. ej. de mongoexport)
    python -m nexus_ia.entrenar --jsonl eventos.jsonl --salida salida/

`--sintetico` marca el modelo como entrenado con datos de prueba (no de produccion).
"""
import argparse
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

from . import caracteristicas as c
from . import entrenamiento, eventos, exportacion

# Donde Java busca el modelo de PRUEBA (src/test/resources/ia): ver el README de ia/.
RAIZ_DE_RECURSOS_DE_PRUEBA = Path(__file__).resolve().parents[2] / "src" / "test" / "resources" / "ia"


def _argumentos(argv):
    p = argparse.ArgumentParser(prog="python -m nexus_ia.entrenar", description=__doc__.split("\n")[0])
    fuente = p.add_mutually_exclusive_group(required=True)
    fuente.add_argument("--mongo-uri", help="MongoDB de misiones (solo se lee la coleccion de eventos)")
    fuente.add_argument("--jsonl", type=Path, help="un evento por linea, en el formato de la coleccion")
    p.add_argument("--base", help="base de datos (por omision, la de la URI)")
    p.add_argument("--coleccion", default="eventos_de_combate")
    p.add_argument("--mision", help="solo los eventos de esta mision")
    p.add_argument("--salida", type=Path, required=True, help="carpeta donde queda modelo.onnx y modelo.json")
    p.add_argument("--epocas", type=int, default=30)
    p.add_argument("--semilla", type=int, default=0)
    p.add_argument("--validacion", type=float, default=0.2, help="fraccion de ejecuciones para validar")
    p.add_argument("--peso-derrota", type=float, default=eventos.PESO_DE_DERROTA,
                   help="peso de las jugadas de un combate que su bando no gano (las ganadas pesan 1)")
    p.add_argument("--version", help="etiqueta del modelo (por omision v<caracteristicas>-<fecha>-<hash>)")
    p.add_argument("--sintetico", action="store_true", help="el modelo se entreno con datos de prueba")
    return p.parse_args(argv)


def construir_modelo(lista, salida, epocas=30, semilla=0, validacion=0.2, peso_derrota=eventos.PESO_DE_DERROTA,
                     version=None, sintetico=False):
    """Entrena con `lista` (eventos) y deja modelo.onnx y modelo.json en `salida`. Devuelve (codigo, meta)."""
    if not lista:
        print("No hay eventos de combate con los que entrenar.", file=sys.stderr)
        return 2, None
    muestras, descartes = eventos.muestras(lista, peso_derrota)
    if not muestras:
        print(f"No hay muestras utiles en {len(lista)} eventos (descartes: {descartes}).", file=sys.stderr)
        return 2, None

    resultado = entrenamiento.entrenar(muestras, epocas=epocas, semilla=semilla, validacion=validacion)
    salida = Path(salida)
    salida.mkdir(parents=True, exist_ok=True)
    info = exportacion.exportar(resultado.red, salida / "modelo.onnx")

    ahora = datetime.now(timezone.utc)
    version = version or f"v{c.VERSION}-{ahora:%Y%m%d}-{info['sha256'][:8]}"
    meta = {
        "version": version,
        "fecha": ahora.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "sintetico": bool(sintetico),
        "eventos": len(lista),
        "ejecuciones": len({e.get("ejecucionId") for e in lista}),
        "muestras": len(muestras),
        "descartes": descartes,
        "candidatas": {origen: sum(1 for m in muestras if m.origen_candidatas == origen)
                       for origen in ("derivadas", "registradas")},
        "caracteristicas": {"version": c.VERSION, "dimension": c.DIMENSION},
        "entrada": exportacion.ENTRADA,
        "salida": exportacion.SALIDA,
        "hiperparametros": dict(resultado.hiperparametros, peso_derrota=peso_derrota),
        "metricas": resultado.metricas,
        "onnx": info,
        "ejemplos": exportacion.ejemplos(resultado.red),
    }
    (salida / "modelo.json").write_text(json.dumps(meta, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Modelo {version}: {len(muestras)} muestras de {len(lista)} eventos, "
          f"{info['bytes']} bytes de ONNX en {salida}")
    return 0, meta


def main(argv=None):
    args = _argumentos(argv)
    if args.jsonl:
        lista = list(eventos.leer_jsonl(args.jsonl))
    else:
        lista = eventos.leer_mongo(args.mongo_uri, args.base, args.coleccion, args.mision)
    codigo, _ = construir_modelo(lista, args.salida, args.epocas, args.semilla, args.validacion, args.peso_derrota,
                                 args.version, args.sintetico)
    return codigo


if __name__ == "__main__":
    sys.exit(main())
