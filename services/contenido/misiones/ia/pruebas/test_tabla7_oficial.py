"""La Tabla 7 que usa el entrenamiento debe ser la del catalogo oficial del repositorio.

`contracts/esquemas/catalogo-oficial.yaml` es lo que el guardian `tests/contratos/catalogo-oficial.py` compara con
el codigo de heroes. Si `tabla7.py` se desvia de ese catalogo, el vocabulario de la red (y el de Java, que se
comprueba contra este con los casos dorados) dejaria de ser el del juego.
"""
from pathlib import Path

import pytest
import yaml

from nexus_ia import caracteristicas as c
from nexus_ia import tabla7

CATALOGO = Path(__file__).resolve().parents[5] / "contracts" / "esquemas" / "catalogo-oficial.yaml"


@pytest.fixture(scope="module")
def oficial():
    if not CATALOGO.exists():
        pytest.skip("no se corre dentro del monorepo: falta contracts/esquemas/catalogo-oficial.yaml")
    return yaml.safe_load(CATALOGO.read_text(encoding="utf-8"))


def test_los_prototipos_y_su_orden_son_los_del_catalogo(oficial):
    assert list(tabla7.PROTOTIPOS) == [h["nombre"] for h in oficial["heroes"]]


def test_los_sanadores_son_los_del_catalogo(oficial):
    assert set(tabla7.SANADORES) == {h["nombre"] for h in oficial["heroes"] if h["sanador"]}


def test_las_acciones_sus_costos_y_su_orden_son_los_de_la_tabla_7_del_catalogo(oficial):
    esperado = [(a["nombre"], a["heroe"], a["costo"]) for a in oficial["acciones"]]
    obtenido = [(nombre, prototipo, costo) for prototipo, acciones in tabla7.ACCIONES_POR_PROTOTIPO.items()
                for nombre, costo in acciones]
    assert obtenido == esperado


def test_el_vocabulario_de_la_red_es_el_ataque_basico_mas_esas_acciones(oficial):
    assert list(c.ACCIONES) == ["Ataque básico"] + [a["nombre"] for a in oficial["acciones"]]
