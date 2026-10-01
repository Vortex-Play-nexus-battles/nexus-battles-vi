import hashlib
import json

import numpy as np
import onnx
import onnxruntime as ort
import pytest
import torch

from nexus_ia import caracteristicas as c
from nexus_ia import entrenamiento, eventos, exportacion, sintetico
from nexus_ia import entrenar as cli


@pytest.fixture(scope="module")
def entrenado():
    m, _ = eventos.muestras(sintetico.generar(ejecuciones=20, semilla=9))
    return entrenamiento.entrenar(m, epocas=8, semilla=1)


def test_el_onnx_es_valido_pesa_kilobytes_y_usa_un_opset_estable(entrenado, tmp_path):
    ruta = tmp_path / "modelo.onnx"
    info = exportacion.exportar(entrenado.red, ruta)
    onnx.checker.check_model(onnx.load(str(ruta)))
    assert ruta.stat().st_size < 100 * 1024
    assert info["opset"] == 17
    assert info["bytes"] == ruta.stat().st_size
    assert info["sha256"] == hashlib.sha256(ruta.read_bytes()).hexdigest()


def test_las_entradas_y_salidas_tienen_el_contrato_que_espera_java(entrenado, tmp_path):
    ruta = tmp_path / "modelo.onnx"
    exportacion.exportar(entrenado.red, ruta)
    sesion = ort.InferenceSession(str(ruta))
    entrada, salida = sesion.get_inputs()[0], sesion.get_outputs()[0]
    assert (entrada.name, entrada.shape) == ("caracteristicas", ["n", c.DIMENSION])
    assert (salida.name, salida.shape) == ("puntajes", ["n", 1])


@pytest.mark.parametrize("n", [1, 2, 7, 26])
def test_onnx_runtime_da_las_mismas_salidas_que_pytorch(entrenado, tmp_path, n):
    ruta = tmp_path / "modelo.onnx"
    exportacion.exportar(entrenado.red, ruta)
    sesion = ort.InferenceSession(str(ruta))
    x = np.random.default_rng(n).random((n, c.DIMENSION), dtype=np.float32)
    esperado = entrenado.red(torch.from_numpy(x)).detach().numpy()
    obtenido = sesion.run(None, {"caracteristicas": x})[0]
    assert obtenido.shape == (n, 1)
    assert np.abs(obtenido - esperado).max() < 1e-5


def test_exportar_verifica_por_si_mismo_que_coincide(entrenado, tmp_path):
    """`exportar` lanza si el ONNX no reproduce al modelo: nunca se deja un archivo que mienta."""
    class Mentiroso(torch.nn.Module):
        def forward(self, x):
            return x[:, :1] * 0

    sana = exportacion.verificar(entrenado.red, _exportar_a_bytes(entrenado.red))
    assert sana < 1e-5
    with pytest.raises(AssertionError, match="no coincide"):
        exportacion.verificar(Mentiroso(), _exportar_a_bytes(entrenado.red))


def _exportar_a_bytes(red):
    import io
    b = io.BytesIO()
    exportacion.escribir(red, b)
    return b.getvalue()


# ---------------------------------------------------------------- la linea de comandos

def test_la_cli_entrena_con_un_jsonl_y_deja_el_onnx_y_el_modelo_json(tmp_path):
    entrada = tmp_path / "eventos.jsonl"
    sintetico.escribir_jsonl(sintetico.generar(ejecuciones=16, semilla=5), entrada)
    salida = tmp_path / "salida"
    codigo = cli.main(["--jsonl", str(entrada), "--salida", str(salida), "--epocas", "5", "--semilla", "1",
                       "--sintetico"])
    assert codigo == 0
    meta = json.loads((salida / "modelo.json").read_text(encoding="utf-8"))
    assert (salida / "modelo.onnx").exists()
    assert meta["version"].startswith("v1-")
    assert meta["sintetico"] is True
    assert meta["caracteristicas"] == {"version": c.VERSION, "dimension": c.DIMENSION}
    assert meta["entrada"] == "caracteristicas" and meta["salida"] == "puntajes"
    assert meta["eventos"] > 0 and meta["muestras"] > 0 and meta["ejecuciones"] == 16
    assert meta["fecha"][:4].isdigit()
    assert meta["onnx"]["sha256"] == hashlib.sha256((salida / "modelo.onnx").read_bytes()).hexdigest()
    assert {"perdida_entrenamiento", "aciertos_validacion", "base_azar_validacion"} <= set(meta["metricas"])
    assert set(meta["candidatas"]) == {"derivadas", "registradas"}
    assert "descartes" in meta and "peso_derrota" in meta["hiperparametros"]


def test_sin_la_bandera_sintetico_el_modelo_json_no_lo_dice(tmp_path):
    entrada = tmp_path / "e.jsonl"
    sintetico.escribir_jsonl(sintetico.generar(ejecuciones=12, semilla=5), entrada)
    cli.main(["--jsonl", str(entrada), "--salida", str(tmp_path / "s"), "--epocas", "2"])
    meta = json.loads((tmp_path / "s" / "modelo.json").read_text(encoding="utf-8"))
    assert meta["sintetico"] is False


def test_la_cli_sin_eventos_falla_con_un_mensaje_claro(tmp_path, capsys):
    vacio = tmp_path / "vacio.jsonl"
    vacio.write_text("", encoding="utf-8")
    codigo = cli.main(["--jsonl", str(vacio), "--salida", str(tmp_path / "s")])
    assert codigo == 2
    assert "no hay eventos" in capsys.readouterr().err.lower()
    assert not (tmp_path / "s" / "modelo.onnx").exists()


def test_la_cli_exige_una_fuente_de_datos(tmp_path):
    with pytest.raises(SystemExit):
        cli.main(["--salida", str(tmp_path)])


def test_el_modelo_de_prueba_versionado_corresponde_a_la_definicion_actual():
    """Si cambian las caracteristicas hay que reentrenar el modelo de prueba que usa Java."""
    ruta = cli.RAIZ_DE_RECURSOS_DE_PRUEBA / "modelo.json"
    meta = json.loads(ruta.read_text(encoding="utf-8"))
    assert meta["caracteristicas"] == {"version": c.VERSION, "dimension": c.DIMENSION}
    assert meta["sintetico"] is True
    onnx_ = ruta.with_name("modelo.onnx")
    assert hashlib.sha256(onnx_.read_bytes()).hexdigest() == meta["onnx"]["sha256"]


def test_el_modelo_de_prueba_se_regenera_con_un_solo_comando(tmp_path):
    from nexus_ia import modelo_de_prueba
    assert modelo_de_prueba.main(["--salida", str(tmp_path), "--ejecuciones", "14", "--epocas", "2"]) == 0
    meta = json.loads((tmp_path / "modelo.json").read_text(encoding="utf-8"))
    assert meta["sintetico"] is True
    assert meta["version"] == "sintetico-v1"
    assert (tmp_path / "modelo.onnx").stat().st_size < 100 * 1024


def test_el_modelo_json_trae_ejemplos_para_que_java_compruebe_su_inferencia(tmp_path):
    entrada = tmp_path / "e.jsonl"
    sintetico.escribir_jsonl(sintetico.generar(ejecuciones=12, semilla=5), entrada)
    cli.main(["--jsonl", str(entrada), "--salida", str(tmp_path / "s"), "--epocas", "2"])
    meta = json.loads((tmp_path / "s" / "modelo.json").read_text(encoding="utf-8"))
    sesion = ort.InferenceSession(str(tmp_path / "s" / "modelo.onnx"))
    assert len(meta["ejemplos"]) == 3
    for ejemplo in meta["ejemplos"]:
        assert len(ejemplo["entrada"]) == c.DIMENSION
        x = np.array([ejemplo["entrada"]], dtype=np.float32)
        assert sesion.run(None, {"caracteristicas": x})[0][0][0] == pytest.approx(ejemplo["salida"], abs=1e-5)
