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

Uso:
    python3 tests/contratos/validar-contratos.py
Salida distinta de cero si algun documento no valida.
"""

import glob
import sys

import yaml
from openapi_spec_validator import validate
from openapi_spec_validator.readers import read_from_filename

OPENAPI = "contracts/openapi/*.yaml"

# Los de WebSocket no son OpenAPI (describen destinos STOMP), asi que el
# validador de OpenAPI no aplica. Se comprueba al menos que sean YAML bien
# formado, que es exactamente donde se rompieron los otros dos.
WEBSOCKET = "contracts/websocket/*.yaml"


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

    if fallos:
        print(f"\n{len(fallos)} contrato(s) no validan:")
        for ruta in fallos:
            print(f"  - {ruta}")
        return 1

    print(f"\nTodos los contratos validan ({len(rutas_openapi)} OpenAPI).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
