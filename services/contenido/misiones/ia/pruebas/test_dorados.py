"""Los casos dorados: Python los genera, Java los verifica (DoradosDeCaracteristicasTest).

Si esta prueba falla, la definicion de caracteristicas cambio sin regenerar el
archivo: `python -m nexus_ia.dorados` y vuelve a correr las pruebas de Java.
"""
import json
from pathlib import Path

from nexus_ia import caracteristicas as c
from nexus_ia import dorados

ARCHIVO = Path(__file__).resolve().parent / "features-dorados.json"


def test_el_archivo_versionado_es_el_que_genera_el_codigo_actual():
    versionado = json.loads(ARCHIVO.read_text(encoding="utf-8"))
    assert versionado == dorados.generar()


def test_los_dorados_declaran_version_dimension_y_vocabularios():
    d = dorados.generar()
    assert d["version"] == c.VERSION
    assert d["dimension"] == c.DIMENSION
    assert d["prototipos"] == list(c.PROTOTIPOS)
    assert d["acciones"] == list(c.ACCIONES)


def test_hay_casos_con_todas_las_acciones_y_los_bordes():
    casos = dorados.generar()["casos"]
    assert len(casos) >= 38
    acciones = {caso["accion"] for caso in casos}
    assert set(c.ACCIONES) <= acciones
    assert all(len(caso["vector"]) == c.DIMENSION for caso in casos)
    # bordes: denominadores en cero, valores por encima de los topes y desconocidos
    assert any(caso["situacion"]["vida_maxima"] == 0 for caso in casos)
    assert any(caso["situacion"]["poder"] > c.TOPE_PODER for caso in casos)
    assert any(caso["accion"] not in c.ACCIONES for caso in casos)
