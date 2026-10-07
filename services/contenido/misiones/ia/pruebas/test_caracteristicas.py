"""La definicion de caracteristicas (version 1): la misma que repite Java en
`nexus.misiones.ia.Caracteristicas`. Estas pruebas fijan el contrato numerico."""
import pytest

from nexus_ia import caracteristicas as c
from nexus_ia import tabla7


def situacion(**cambios):
    base = dict(
        prototipo="Guerrero Armas", nivel=4, turno=3,
        vida=22, vida_maxima=44, poder=6, poder_maximo=12, efectos=1,
        oponente_prototipo="Mago Fuego", oponente_nivel=8,
        oponente_vida=30, oponente_vida_maxima=60, oponente_poder=4,
        oponente_poder_maximo=8, oponente_efectos=2,
    )
    base.update(cambios)
    return c.Situacion(**base)


def test_la_tabla_7_tiene_ocho_prototipos_y_veinticuatro_acciones():
    assert len(tabla7.PROTOTIPOS) == 8
    assert len(tabla7.ACCIONES) == 24
    assert all(len(a) == 3 for a in tabla7.ACCIONES_POR_PROTOTIPO.values())


def test_el_vocabulario_de_acciones_empieza_por_el_ataque_basico():
    assert c.ACCIONES[0] == "Ataque básico"
    assert len(c.ACCIONES) == 25
    assert c.ACCIONES[1:] == tabla7.ACCIONES


def test_la_dimension_es_54_y_el_vector_la_respeta():
    assert c.DIMENSION == 54
    assert len(c.vector(situacion(), "Lanza de los dioses", 4)) == 54


def test_la_version_de_las_caracteristicas_es_1():
    assert c.VERSION == 1


def test_numeros_del_estado_propio_y_del_oponente():
    v = c.vector(situacion(), "Lanza de los dioses", 4)
    assert v[0] == pytest.approx(0.5)      # vida propia 22/44
    assert v[1] == pytest.approx(0.5)      # poder propio 6/12
    assert v[2] == pytest.approx(0.5)      # poder absoluto min(6,12)/12
    assert v[3] == pytest.approx(0.5)      # nivel 4/8
    assert v[4] == pytest.approx(0.15)     # turno 3/20
    assert v[5] == pytest.approx(0.25)     # un efecto de cuatro
    assert v[6] == pytest.approx(0.5)      # vida oponente 30/60
    assert v[7] == pytest.approx(0.5)      # poder oponente 4/8
    assert v[8] == pytest.approx(1.0)      # nivel oponente 8/8
    assert v[9] == pytest.approx(0.5)      # dos efectos de cuatro
    assert v[10] == pytest.approx(0.0)     # ventaja de vida 0.5 - 0.5


def test_one_hot_de_los_prototipos_propio_y_del_oponente():
    v = c.vector(situacion(), "Lanza de los dioses", 4)
    propio = v[11:19]
    oponente = v[19:27]
    assert propio == [0, 1, 0, 0, 0, 0, 0, 0]       # Guerrero Armas es el segundo
    assert oponente == [0, 0, 1, 0, 0, 0, 0, 0]     # Mago Fuego es el tercero


def test_one_hot_de_la_accion_y_costos():
    v = c.vector(situacion(), "Lanza de los dioses", 4)
    accion = v[27:52]
    assert sum(accion) == 1
    assert accion[c.ACCIONES.index("Lanza de los dioses")] == 1
    assert v[52] == pytest.approx(4 / 12)           # costo absoluto
    assert v[53] == pytest.approx(4 / 6)            # costo sobre el poder actual


def test_el_ataque_basico_es_la_posicion_cero_y_no_cuesta():
    v = c.vector(situacion(), "Ataque básico", 0)
    assert v[27] == 1
    assert v[52] == 0 and v[53] == 0


def test_valores_fuera_de_rango_se_acotan():
    v = c.vector(situacion(vida=500, poder=99, turno=400, efectos=9, nivel=30), "Embate sangriento", 99)
    assert v[0] == 1 and v[1] == 1 and v[2] == 1 and v[3] == 1
    assert v[4] == 1 and v[5] == 1
    assert v[52] == 1 and v[53] == 1


def test_denominadores_en_cero_dan_cero_y_no_explotan():
    v = c.vector(situacion(vida_maxima=0, poder_maximo=0, oponente_vida_maxima=0, oponente_poder_maximo=0, poder=0),
                 "Embate sangriento", 4)
    assert v[0] == 0 and v[1] == 0 and v[6] == 0 and v[7] == 0
    assert v[53] == 1                                # costo 4 sobre poder 0 (se toma 1): acotado a 1


def test_un_prototipo_o_accion_desconocidos_quedan_en_cero():
    v = c.vector(situacion(prototipo="Dragon", oponente_prototipo=None), "Rugido", 3)
    assert sum(v[11:27]) == 0
    assert sum(v[27:52]) == 0


def test_la_ventaja_de_vida_es_propia_menos_la_del_oponente():
    v = c.vector(situacion(vida=44, oponente_vida=15), "Embate sangriento", 4)
    assert v[10] == pytest.approx(1.0 - 0.25)
