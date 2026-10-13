"""El generador de eventos sinteticos: solo sirve de fixture de pruebas (y para producir un modelo de
prueba). Sus documentos deben tener EXACTAMENTE el formato que guarda misiones; la prueba de Java
(`EventosSinteticosTest`) lo comprueba del otro lado, deserializandolos con el documento real."""
from nexus_ia import caracteristicas as c
from nexus_ia import eventos, sintetico

CAMPOS = {"_id", "ejecucionId", "misionId", "secuencia", "encuentro", "enemigo", "turno", "actor", "oponente",
          "antes", "alIniciar", "jugada", "despues", "registradoEn"}
CAMPOS_ACTOR = {"lado", "nombre", "prototipo", "nivel"}
CAMPOS_ESTADO = {"vida", "vidaMaxima", "poder", "poderMaximo", "recargas", "efectos"}
CAMPOS_JUGADA = {"decidida", "ejecutada", "enValorBase", "costoDecidido", "costoDePoder", "rechazadas", "resultado",
                 "decididaPor", "versionDelModelo", "candidatas"}
CAMPOS_RESULTADO = {"categoria", "acierta", "ataqueResuelto", "defensaObjetivo", "porcentajeDano", "danoBase",
                    "danoAplicado", "critico", "sucesos"}
CAMPOS_CANDIDATA = {"accion", "costoDePoder", "rotacion", "puntaje"}


def test_la_misma_semilla_da_los_mismos_eventos_y_otra_semilla_otros():
    assert sintetico.generar(ejecuciones=6, semilla=1) == sintetico.generar(ejecuciones=6, semilla=1)
    assert sintetico.generar(ejecuciones=6, semilla=1) != sintetico.generar(ejecuciones=6, semilla=2)


def test_cada_ejecucion_tiene_secuencia_sin_saltos_desde_uno_e_id_ejecucion_secuencia():
    lista = sintetico.generar(ejecuciones=8, semilla=3)
    por_ejecucion = {}
    for e in lista:
        por_ejecucion.setdefault(e["ejecucionId"], []).append(e)
    assert len(por_ejecucion) == 8
    for ejecucion, turnos in por_ejecucion.items():
        assert [t["secuencia"] for t in turnos] == list(range(1, len(turnos) + 1))
        assert all(t["_id"] == f"{ejecucion}:{t['secuencia']}" for t in turnos)


def test_los_documentos_tienen_solo_los_campos_del_formato_de_misiones():
    for e in sintetico.generar(ejecuciones=10, semilla=4):
        assert set(e) <= CAMPOS and {"_id", "ejecucionId", "secuencia", "actor", "antes", "despues"} <= set(e)
        assert set(e["actor"]) == CAMPOS_ACTOR and set(e["oponente"]) == CAMPOS_ACTOR
        for lado in ("actor", "oponente"):
            assert set(e["antes"][lado]) == CAMPOS_ESTADO
            assert set(e["despues"][lado]) == CAMPOS_ESTADO
        if "jugada" in e:
            j = e["jugada"]
            assert set(j) <= CAMPOS_JUGADA
            assert set(j["resultado"]) <= CAMPOS_RESULTADO
            for cand in j.get("candidatas", []):
                assert set(cand) <= CAMPOS_CANDIDATA and {"accion", "costoDePoder"} <= set(cand)


def test_las_cifras_son_coherentes():
    for e in sintetico.generar(ejecuciones=10, semilla=5):
        for momento in ("antes", "despues"):
            for lado in ("actor", "oponente"):
                s = e[momento][lado]
                assert 0 <= s["vida"] <= s["vidaMaxima"]
                assert 0 <= s["poder"] <= s["poderMaximo"]
        if "jugada" in e:
            assert e["jugada"]["ejecutada"] in c.ACCIONES or e["jugada"]["ejecutada"] == "Sanación básica"
            assert e["jugada"]["decididaPor"] in ("REGLA", "MODELO")


def test_hay_combates_ganados_y_perdidos_y_turnos_con_y_sin_candidatas_registradas():
    lista = sintetico.generar(ejecuciones=30, semilla=6)
    ganadores = set(eventos.ganadores_por_encuentro(lista).values())
    assert {"HEROE", "ENEMIGO"} <= ganadores
    con = [e for e in lista if e.get("jugada", {}).get("candidatas")]
    sin = [e for e in lista if "jugada" in e and not e["jugada"].get("candidatas")]
    assert con and sin
    assert any(e["jugada"]["decididaPor"] == "MODELO" for e in con)


def test_casi_todo_sirve_para_entrenar():
    lista = sintetico.generar(ejecuciones=30, semilla=7)
    m, descartes = eventos.muestras(lista)
    assert len(m) > 400
    assert sum(descartes.values()) < 0.1 * len(lista)
    assert {x.origen_candidatas for x in m} == {"derivadas", "registradas"}
