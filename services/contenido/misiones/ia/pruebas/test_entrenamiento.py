import numpy as np
import pytest
import torch

from nexus_ia import caracteristicas as c
from nexus_ia import entrenamiento, eventos, sintetico
from fabrica import estado, evento


def muestras_sinteticas(ejecuciones=30, semilla=7):
    m, _ = eventos.muestras(sintetico.generar(ejecuciones=ejecuciones, semilla=semilla))
    return m


# ---------------------------------------------------------------- lotes

def test_los_lotes_tienen_la_forma_y_la_mascara_correctas():
    m = [eventos.muestras([evento(1, ejecutada="Lanza de los dioses",
                                  antes={"actor": estado(poder=8), "oponente": estado()})])[0][0],
         eventos.muestras([evento(2, prototipo="Médico", nivel=8, ejecutada="Curación Directa", costo=2)])[0][0]]
    lote = entrenamiento.armar_lotes(m)
    k = max(len(x.candidatas) for x in m)
    assert lote.x.shape == (2, k, c.DIMENSION)
    assert lote.x.dtype == torch.float32
    assert lote.mascara.sum(dim=1).tolist() == [len(x.candidatas) for x in m]
    assert lote.objetivo.tolist() == [x.elegida for x in m]
    assert lote.peso.tolist() == pytest.approx([x.peso for x in m])


def test_las_filas_de_relleno_valen_cero_y_las_reales_son_el_vector_de_caracteristicas():
    m = muestras_sinteticas(ejecuciones=4)
    lote = entrenamiento.armar_lotes(m)
    primera = m[0]
    esperado = c.vector(primera.situacion, *primera.candidatas[0])
    assert lote.x[0, 0].tolist() == pytest.approx(esperado, abs=1e-6)
    n = len(primera.candidatas)
    assert float(lote.x[0, n:].abs().sum()) == 0.0 if n < lote.x.shape[1] else True


# ---------------------------------------------------------------- perdida

def test_la_perdida_es_la_entropia_cruzada_ponderada_sobre_las_candidatas_reales():
    puntajes = torch.tensor([[2.0, 0.0, 99.0],           # la tercera es relleno: no cuenta
                             [0.0, 1.0, 5.0]])
    mascara = torch.tensor([[True, True, False], [True, True, True]])
    objetivo = torch.tensor([0, 1])
    peso = torch.tensor([2.0, 1.0])
    uno = -np.log(np.exp(2.0) / (np.exp(2.0) + np.exp(0.0)))
    dos = -np.log(np.exp(1.0) / (np.exp(0.0) + np.exp(1.0) + np.exp(5.0)))
    esperado = (2.0 * uno + 1.0 * dos) / 3.0              # promedio sobre el peso total
    assert float(entrenamiento.perdida(puntajes, mascara, objetivo, peso)) == pytest.approx(esperado, rel=1e-5)


# ---------------------------------------------------------------- aprender

def _datos_de_juguete(n=200):
    """Con poder 8 conviene la Lanza; con poder 2 no alcanza y se juega el basico. Mismas dos candidatas siempre."""
    lista = []
    for i in range(n):
        fuerte = i % 2 == 0
        antes = {"actor": estado(poder=8 if fuerte else 2), "oponente": estado()}
        lista.append(evento(i + 1, ejecucion=f"ej-{i % 10}", antes=antes,
                            ejecutada="Lanza de los dioses" if fuerte else "Ataque básico",
                            costo=4 if fuerte else 0,
                            candidatas=[("Ataque básico", 0), ("Lanza de los dioses", 4)],
                            despues={"actor": estado(), "oponente": estado(vida=0)}))
    return eventos.muestras(lista)[0]


def test_entrenar_baja_la_perdida_y_aprende_a_leer_el_poder():
    resultado = entrenamiento.entrenar(_datos_de_juguete(), epocas=80, semilla=1, validacion=0.0)
    assert resultado.metricas["perdida_entrenamiento"] < resultado.metricas["perdida_inicial"] * 0.5
    assert resultado.metricas["aciertos_entrenamiento"] == pytest.approx(1.0)


def test_entrenar_con_datos_sinteticos_supera_al_azar_en_validacion():
    resultado = entrenamiento.entrenar(muestras_sinteticas(40), epocas=30, semilla=3)
    metricas = resultado.metricas
    assert metricas["aciertos_validacion"] > metricas["base_azar_validacion"] + 0.1
    assert metricas["perdida_entrenamiento"] < metricas["perdida_inicial"]


def test_el_mismo_conjunto_y_la_misma_semilla_dan_los_mismos_pesos():
    m = muestras_sinteticas(10)
    a = entrenamiento.entrenar(m, epocas=5, semilla=11)
    b = entrenamiento.entrenar(m, epocas=5, semilla=11)
    for pa, pb in zip(a.red.parameters(), b.red.parameters()):
        assert torch.equal(pa, pb)


def test_la_validacion_se_separa_por_ejecucion_no_por_turno():
    m = muestras_sinteticas(20)
    entrenamiento_, validacion = entrenamiento.separar(m, fraccion=0.25, semilla=1)
    ej_ent = {x.ejecucion for x in entrenamiento_}
    ej_val = {x.ejecucion for x in validacion}
    assert ej_ent and ej_val and not (ej_ent & ej_val)
    assert len(entrenamiento_) + len(validacion) == len(m)


def test_el_peso_inclina_el_modelo_hacia_lo_que_gano():
    """Misma situacion, dos jugadas distintas: la del combate ganado pesa mas y debe puntuar mas."""
    gana = {"actor": estado(), "oponente": estado(vida=0)}
    pierde = {"actor": estado(vida=0), "oponente": estado(vida=20)}
    lista = []
    for i in range(60):
        cand = [("Ataque básico", 0), ("Embate sangriento", 4), ("Lanza de los dioses", 4)]
        if i % 2 == 0:
            lista.append(evento(i + 1, encuentro=i + 1, ejecutada="Embate sangriento", candidatas=cand, despues=gana,
                                ejecucion=f"e{i}"))
        else:
            lista.append(evento(i + 1, encuentro=i + 1, ejecutada="Lanza de los dioses", candidatas=cand,
                                despues=pierde, ejecucion=f"e{i}"))
    m = eventos.muestras(lista, peso_derrota=0.1)[0]
    resultado = entrenamiento.entrenar(m, epocas=80, semilla=2, validacion=0.0)
    vectores = torch.tensor([c.vector(m[0].situacion, a, k) for a, k in m[0].candidatas], dtype=torch.float32)
    with torch.no_grad():
        puntajes = resultado.red(vectores).squeeze(1).tolist()
    nombres = [a for a, _ in m[0].candidatas]
    assert puntajes[nombres.index("Embate sangriento")] > puntajes[nombres.index("Lanza de los dioses")]


def test_sin_muestras_suficientes_se_niega_a_entrenar():
    with pytest.raises(ValueError, match="muestras"):
        entrenamiento.entrenar([], epocas=1)
