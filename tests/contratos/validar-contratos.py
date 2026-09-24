#!/usr/bin/env python3
"""
Guardian: los contratos son OpenAPI valido — regla 1 de plataforma (R11).

La regla 1 dice «contrato primero: OpenAPI/AsyncAPI se publica y se acuerda
antes de implementar». Habia 21 documentos en `contracts/openapi/` y **nadie
los abria nunca**: la unica mencion de `contracts/` en toda la integracion
continua era un filtro de rutas que dispara una compilacion de Java. Tocar un
contrato compilaba el monorepo entero, pero ningun proceso parseaba el YAML.

La primera vez que se ejecuto esto, sobre los contratos tal como estaban en
develop, fallaron **cinco de veintiuno**:

  * `ecommerce-carrito.yaml` y `torneos.yaml` no eran ni siquiera YAML bien
    formado. Los dos por lo mismo: un `: ` sin comillas dentro de un texto
    (`Tienda (ms-ecommerce): carrito`, `La lista negra no responde: no se
    registra`). Cualquier generador de clientes se habria atragantado, y
    nadie lo sabia.
  * `admin-parametros.yaml`, `metricas-plataforma.yaml` y
    `moderacion-sanciones-admin.yaml` tenian respuestas `200` sin
    `description`, que OpenAPI exige.

Ninguno de los cinco rompia nada en ejecucion, y por eso llevaban semanas
ahi: un contrato invalido no falla hasta que alguien intenta usarlo.

Desde R11.6 comprueba tres cosas mas, todas con el documento ya cargado —no
cuestan una sola llamada de red ni dependen de ningun servicio externo:

  * **operationId duplicado** entre documentos distintos. Un generador de
    clientes produce un metodo por operationId; dos iguales en dos contratos
    se pisan y el cliente resultante pierde una de las dos operaciones sin
    avisar.
  * **la misma ruta declarada en dos contratos**. Significa que dos servicios
    creen ser el dueno del mismo prefijo, que es exactamente como nacio la
    colision de `/api/v1/moderacion` entre comentarios y metricas-plataforma
    (R10.1). Se detecta leyendo YAML en vez de leyendo la configuracion de
    nginx cuando ya es tarde.
  * **`info.version` presente y con forma de version semantica**. La regla 2
    dice que un cambio incompatible abre version nueva; sin version no hay
    nada que abrir.

Lo que NO comprueba, a proposito: que las rutas del contrato existan en el
codigo. Eso exige arrancar cada servicio o parsear anotaciones de Spring, y
seria la clase de compuerta que se cae sola cada vez que alguien renombra un
paquete. La correspondencia contrato-codigo la vigilan las pruebas de
contrato (Pact) y el banco E2E, que hablan HTTP de verdad.

Uso:
    python3 tests/contratos/validar-contratos.py
Salida distinta de cero si algun documento no valida.
"""

import collections
import glob
import re
import sys

import yaml
from openapi_spec_validator import validate
from openapi_spec_validator.readers import read_from_filename

OPENAPI = "contracts/openapi/*.yaml"

# Los de WebSocket no son OpenAPI (describen destinos STOMP), asi que el
# validador de OpenAPI no aplica. Se comprueba al menos que sean YAML bien
# formado, que es exactamente donde se rompieron los otros dos.
WEBSOCKET = "contracts/websocket/*.yaml"

# Version semantica: mayor.menor.parche, con sufijo opcional.
SEMVER = re.compile(r"^\d+\.\d+\.\d+(?:[-+].+)?$")

# Claves de `paths` que no son metodos HTTP y no cuentan como operacion.
NO_SON_METODOS = {"parameters", "servers", "summary", "description", "$ref"}


def validar_openapi(ruta: str) -> str | None:
    try:
        documento, base = read_from_filename(ruta)
        validate(documento, base_uri=base)
        return None
    except Exception as fallo:  # noqa: BLE001 - se quiere el mensaje, sea cual sea
        return str(fallo).strip().splitlines()[0]


def validar_yaml(ruta: str) -> str | None:
    try:
        with open(ruta, encoding="utf-8") as archivo:
            yaml.safe_load(archivo)
        return None
    except Exception as fallo:  # noqa: BLE001
        return str(fallo).strip().splitlines()[0]


def catalogo(ruta: str) -> tuple[dict, str | None]:
    """El documento cargado, o el motivo por el que no se pudo leer."""
    try:
        with open(ruta, encoding="utf-8") as archivo:
            return yaml.safe_load(archivo) or {}, None
    except Exception as fallo:  # noqa: BLE001
        return {}, str(fallo).strip().splitlines()[0]


def revisar_coherencia(rutas: list[str]) -> list[str]:
    """operationId duplicados, rutas compartidas y versiones ausentes."""
    problemas: list[str] = []
    ids: dict[str, list[str]] = collections.defaultdict(list)
    caminos: dict[str, list[str]] = collections.defaultdict(list)

    for ruta in rutas:
        documento, motivo = catalogo(ruta)
        if motivo:
            # El validador de OpenAPI ya lo habra reportado con mas detalle.
            continue

        version = (documento.get("info") or {}).get("version")
        if not version:
            print(f"::error file={ruta}::info.version ausente: la regla 2 exige versionar el contrato")
            problemas.append(f"{ruta}: sin info.version")
        elif not SEMVER.match(str(version)):
            print(f"::error file={ruta}::info.version «{version}» no tiene forma mayor.menor.parche")
            problemas.append(f"{ruta}: version «{version}» no es semantica")

        # El prefijo del server cuenta: dos contratos pueden declarar
        # `/creditos` y no chocar si uno cuelga de `/api/v1` y el otro no.
        servidores = documento.get("servers") or [{}]
        prefijo = str((servidores[0] or {}).get("url", "")).rstrip("/")
        # Solo importa la parte de camino de la URL del server, no el host.
        prefijo = re.sub(r"^[a-z]+://[^/]+", "", prefijo)

        for camino, operaciones in (documento.get("paths") or {}).items():
            if not isinstance(operaciones, dict):
                continue
            for metodo, operacion in operaciones.items():
                if metodo in NO_SON_METODOS or not isinstance(operacion, dict):
                    continue
                identificador = operacion.get("operationId")
                if identificador:
                    ids[identificador].append(ruta)
                caminos[f"{metodo.upper()} {prefijo}{camino}"].append(ruta)

    for identificador, donde in sorted(ids.items()):
        if len(donde) > 1:
            lista = ", ".join(sorted(set(donde)))
            print(f"::error::operationId duplicado «{identificador}» en: {lista}")
            problemas.append(f"operationId duplicado: {identificador}")

    for camino, donde in sorted(caminos.items()):
        distintos = sorted(set(donde))
        if len(distintos) > 1:
            lista = ", ".join(distintos)
            print(f"::error::la ruta «{camino}» la declaran dos contratos: {lista}")
            problemas.append(f"ruta compartida: {camino}")

    return problemas


def main() -> int:
    fallos = []

    rutas_openapi = sorted(glob.glob(OPENAPI))
    if not rutas_openapi:
        print("::error::no se encontro ningun contrato en " + OPENAPI)
        return 1

    for ruta in rutas_openapi:
        motivo = validar_openapi(ruta)
        if motivo:
            print(f"::error file={ruta}::{motivo}")
            fallos.append(ruta)
        else:
            print(f"ok    {ruta}")

    for ruta in sorted(glob.glob(WEBSOCKET)):
        motivo = validar_yaml(ruta)
        if motivo:
            print(f"::error file={ruta}::YAML mal formado: {motivo}")
            fallos.append(ruta)
        else:
            print(f"ok    {ruta} (solo YAML: no es OpenAPI)")

    incoherencias = revisar_coherencia(rutas_openapi)

    if fallos or incoherencias:
        if fallos:
            print(f"\n{len(fallos)} contrato(s) no validan:")
            for ruta in fallos:
                print(f"  - {ruta}")
        if incoherencias:
            print(f"\n{len(incoherencias)} problema(s) de coherencia entre contratos:")
            for problema in incoherencias:
                print(f"  - {problema}")
        return 1

    print(f"\nTodos los contratos validan ({len(rutas_openapi)} OpenAPI),")
    print("sin operationId duplicados, sin rutas compartidas y todos versionados.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
