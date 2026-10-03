import json

import pytest

from nexus_ia import eventos
from fabrica import estado, evento, EJECUCION


# ---------------------------------------------------------------- lectura

def test_leer_jsonl_devuelve_un_documento_por_linea_y_salta_las_vacias(tmp_path):
    ruta = tmp_path / "eventos.jsonl"
    ruta.write_text(json.dumps(evento(1)) + "\n\n" + json.dumps(evento(2)) + "\n", encoding="utf-8")
    assert [e["secuencia"] for e in eventos.leer_jsonl(ruta)] == [1, 2]


def test_leer_jsonl_con_una_linea_rota_dice_cual(tmp_path):
    ruta = tmp_path / "eventos.jsonl"
    ruta.write_text(json.dumps(evento(1)) + "\n{no es json\n", encoding="utf-8")
    with pytest.raises(ValueError, match="linea 2"):
        list(eventos.leer_jsonl(ruta))


class ColeccionFalsa:
    """Lo unico que se le pide a una coleccion de MongoDB: find(filtro, proyeccion) -> iterable."""

    def __init__(self, documentos):
        self.documentos = documentos
        self.llamadas = []

    def find(self, filtro=None, proyeccion=None):
        self.llamadas.append((filtro, proyeccion))
        return iter(self.documentos)


def test_leer_coleccion_ordena_por_ejecucion_y_secuencia():
    desordenados = [evento(2), evento(1), evento(1, ejecucion="aaaaaaaa")]
    coleccion = ColeccionFalsa(desordenados)
    leidos = list(eventos.leer_coleccion(coleccion))
    assert [(e["ejecucionId"], e["secuencia"]) for e in leidos] == [
        ("0c7a2d9e-4f8b-4a55-9b65-6f1d3b2f0a11", 1), ("0c7a2d9e-4f8b-4a55-9b65-6f1d3b2f0a11", 2),
        ("aaaaaaaa", 1)]
    assert coleccion.llamadas[0][0] == {}


def test_leer_coleccion_puede_filtrar_por_mision():
    coleccion = ColeccionFalsa([evento(1)])
    list(eventos.leer_coleccion(coleccion, mision="templo-olvidado"))
    assert coleccion.llamadas[0][0] == {"misionId": "templo-olvidado"}


# ---------------------------------------------------------------- validacion

def test_un_evento_sin_los_campos_minimos_no_es_valido():
    assert eventos.es_valido(evento(1))
    sin_actor = evento(1)
    del sin_actor["actor"]
    assert not eventos.es_valido(sin_actor)
    sin_antes = evento(1)
    del sin_antes["antes"]
    assert not eventos.es_valido(sin_antes)


# ---------------------------------------------------------------- ganador del duelo

def test_gana_el_heroe_si_al_final_del_encuentro_el_rival_cayo():
    final = {"actor": estado(vida=20), "oponente": estado(vida=0, vida_maxima=40)}
    lista = [evento(1, despues={"actor": estado(vida=30), "oponente": estado(vida=10, vida_maxima=40)}),
             evento(2, despues=final)]
    assert eventos.ganadores_por_encuentro(lista)[(EJECUCION, 1)] == "HEROE"


def test_el_enemigo_gana_si_el_heroe_cae():
    final = {"actor": estado(vida=15, vida_maxima=40), "oponente": estado(vida=0)}
    lista = [evento(1, lado="ENEMIGO", prototipo="Mago Fuego", oponente_lado="HEROE", despues=final)]
    assert eventos.ganadores_por_encuentro(lista)[(EJECUCION, 1)] == "ENEMIGO"


def test_si_los_dos_siguen_en_pie_no_gana_nadie():
    assert eventos.ganadores_por_encuentro([evento(1)])[(EJECUCION, 1)] is None


def test_se_decide_con_el_ultimo_turno_del_encuentro_aunque_lleguen_desordenados():
    cae = evento(3, encuentro=2, despues={"actor": estado(), "oponente": estado(vida=0)})
    sigue = evento(2, encuentro=2)
    assert eventos.ganadores_por_encuentro([cae, sigue])[(EJECUCION, 2)] == "HEROE"


# ---------------------------------------------------------------- muestras

def una(lista, **kw):
    muestras, descartes = eventos.muestras(lista, **kw)
    assert len(muestras) == 1, descartes
    return muestras[0]


def test_la_situacion_sale_del_estado_antes_de_decidir():
    m = una([evento(1, turno=3, nivel=4, oponente_nivel=6)])
    s = m.situacion
    assert (s.prototipo, s.nivel, s.turno) == ("Guerrero Armas", 4, 3)
    assert (s.vida, s.vida_maxima, s.poder, s.poder_maximo) == (44, 44, 8, 12)
    assert (s.oponente_prototipo, s.oponente_nivel, s.oponente_vida, s.oponente_vida_maxima) == (
        "Mago Fuego", 6, 40, 40)


def test_los_efectos_se_cuentan():
    antes = {"actor": estado(efectos=("Sangrado", "Veneno")), "oponente": estado(efectos=("Protegido",))}
    m = una([evento(1, antes=antes)])
    assert m.situacion.efectos == 2 and m.situacion.oponente_efectos == 1


def test_sin_candidatas_registradas_se_derivan_de_la_tabla_7_las_que_se_podian_jugar():
    # Guerrero Armas nivel 4: Embate (4) y Lanza (4); con poder 5 alcanza para cualquiera de las dos.
    antes = {"actor": estado(poder=5), "oponente": estado()}
    m = una([evento(1, antes=antes, ejecutada="Lanza de los dioses")])
    assert [a for a, _ in m.candidatas] == ["Ataque básico", "Embate sangriento", "Lanza de los dioses"]
    assert m.candidatas[m.elegida][0] == "Lanza de los dioses"
    assert m.origen_candidatas == "derivadas"


def test_las_derivadas_dejan_fuera_lo_que_no_alcanza_o_esta_en_recarga():
    antes = {"actor": estado(poder=4, recargas=(("Embate sangriento", 1),)), "oponente": estado()}
    m = una([evento(1, antes=antes, ejecutada="Lanza de los dioses")])
    assert [a for a, _ in m.candidatas] == ["Ataque básico", "Lanza de los dioses"]


def test_las_derivadas_no_incluyen_lo_que_el_nivel_no_desbloquea():
    antes = {"actor": estado(poder=12), "oponente": estado()}
    m = una([evento(1, nivel=1, antes=antes, ejecutada="Embate sangriento")])
    assert [a for a, _ in m.candidatas] == ["Ataque básico", "Embate sangriento"]


def test_reanimacion_cuesta_todo_el_poder_que_se_tenga():
    antes = {"actor": estado(poder=9), "oponente": estado()}
    m = una([evento(1, prototipo="Médico", nivel=8, antes=antes, ejecutada="Reanimación", costo=9)])
    assert ("Reanimación", 9) in m.candidatas


def test_la_sanacion_basica_es_el_ataque_basico_para_el_modelo():
    m = una([evento(1, prototipo="Chamán", ejecutada="Sanación básica", costo=0)])
    assert m.candidatas[m.elegida] == ("Ataque básico", 0)


def test_si_el_evento_trae_candidatas_se_usan_esas():
    m = una([evento(1, ejecutada="Lanza de los dioses",
                    candidatas=[("Embate sangriento", 4), ("Lanza de los dioses", 4), ("Ataque básico", 0)])])
    assert [a for a, _ in m.candidatas] == ["Embate sangriento", "Lanza de los dioses", "Ataque básico"]
    assert m.candidatas[m.elegida][0] == "Lanza de los dioses"
    assert m.origen_candidatas == "registradas"


def test_se_descartan_con_motivo_los_turnos_que_no_sirven_para_aprender():
    lista = [
        evento(1, sin_jugada=True),
        evento(2, en_valor_base=True),
        evento(3, ejecutada="Epica rara"),
        evento(4, ejecutada="Lanza de los dioses", candidatas=[("Embate sangriento", 4), ("Ataque básico", 0)]),
        evento(5),
    ]
    muestras, descartes = eventos.muestras(lista)
    assert [m.secuencia for m in muestras] == [5]
    assert descartes == {"sin_jugada": 1, "en_valor_base": 1, "accion_fuera_de_la_tabla_7": 1,
                         "elegida_fuera_de_las_candidatas": 1}


def test_un_evento_sin_oponente_deja_en_ceros_su_bloque():
    sin = evento(1)
    del sin["oponente"]
    assert una([sin]).situacion.oponente_prototipo is None


# ---------------------------------------------------------------- pesos

def test_pesa_mas_la_jugada_de_un_combate_ganado_y_la_que_mas_dano_aplico():
    ganado_final = {"actor": estado(), "oponente": estado(vida=0, vida_maxima=40)}
    perdido_final = {"actor": estado(vida=0), "oponente": estado(vida=10, vida_maxima=40)}
    gana = evento(1, encuentro=1, dano=20, despues=ganado_final)
    gana_poco_dano = evento(2, encuentro=2, dano=2, despues=ganado_final)
    pierde = evento(3, encuentro=3, dano=20, despues=perdido_final)
    ms, _ = eventos.muestras([gana, gana_poco_dano, pierde], peso_derrota=0.25)
    pesos = {m.secuencia: m.peso for m in ms}
    assert pesos[1] > pesos[2] > pesos[3]
    assert pesos[3] == pytest.approx(0.25 * (1 + 20 / 40))
    assert pesos[1] == pytest.approx(1.0 * (1 + 20 / 40))


def test_el_peso_de_un_enemigo_cuenta_cuando_el_enemigo_gana():
    cae_el_heroe = {"actor": estado(vida=30, vida_maxima=40), "oponente": estado(vida=0)}
    e = evento(1, lado="ENEMIGO", prototipo="Mago Fuego", oponente_lado="HEROE", dano=0, despues=cae_el_heroe,
               ejecutada="Misiles de magma", costo=2)
    assert una([e]).peso == pytest.approx(1.0)

def test_la_traza_de_estrategia_de_un_enemigo_no_cambia_lo_que_aprende_el_modelo():
    """HU-SIM-004 agrega `estrategia` y `estrategiaId` a la jugada de un enemigo. Son para auditar, no para aprender:
    la muestra (situacion, candidatas, elegida, peso) es la misma con y sin ellas."""
    sin = evento(1, lado="ENEMIGO", prototipo="Mago Fuego", oponente_lado="HEROE", oponente_prototipo="Guerrero Armas",
                 ejecutada="Misiles de magma", costo=2)
    con = evento(1, lado="ENEMIGO", prototipo="Mago Fuego", oponente_lado="HEROE", oponente_prototipo="Guerrero Armas",
                 ejecutada="Misiles de magma", costo=2, estrategia="PREDEFINIDA", estrategia_id="mago-fuego-n4")
    assert con["jugada"]["estrategia"] == "PREDEFINIDA" and con["jugada"]["estrategiaId"] == "mago-fuego-n4"
    assert "estrategia" not in sin["jugada"]
    assert eventos.muestras([con]) == eventos.muestras([sin])
    assert una([con]).elegida == una([sin]).elegida
