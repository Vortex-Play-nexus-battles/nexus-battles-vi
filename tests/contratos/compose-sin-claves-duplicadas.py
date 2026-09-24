#!/usr/bin/env python3
"""Ningun archivo de Compose tiene una clave repetida dentro del mismo mapa.

## Por que existe este guardian

El 23-sep, `#668` quiso darle a `srv-notificaciones` su credencial de servicio
y pego las dos lineas en el bloque `environment:` de `srv-moderacion-sanciones`,
que ya tenia las suyas. El resultado fue un `DIRECTORIO_ACTIVO_CLIENT_ID`
repetido dentro del mismo mapa.

YAML permite claves repetidas segun con que biblioteca se lea -- el `SafeLoader`
de PyYAML se queda con la ultima y no dice nada-- pero **Docker Compose no**:

    failed to parse /opt/nexus/docker-compose.deploy.yml:
    line 209: mapping key "DIRECTORIO_ACTIVO_CLIENT_ID" already defined at line 198

Y como Compose se niega a leer el archivo ENTERO, no fallo el servicio tocado:
fallaron **todos los despliegues de los dos hosts**. La corrida 35931028022, la
del commit que traia `POST /inventario/elementos/{id}/transferencias` (#670),
murio aqui. El codigo estaba fusionado y en verde; simplemente nunca llego a
AWS, y desde fuera se veia igual que si no existiera.

Ningun gate lo vio venir: el CI no lee los compose con un analizador estricto, y
el error solo aparece dentro de una sesion SSH, en el log de un `err:` de una
accion de terceros. Este guardian lo mueve al pull request, que es donde cuesta
un minuto arreglarlo.

## Que comprueba

Recorre todos los `docker-compose*.yml` del repositorio con un cargador que
RECHAZA claves duplicadas, en vez de quedarse con la ultima en silencio. No
valida el esquema de Compose: solo esta clase de error, que es la que rompe el
despliegue entero sin que nadie lo note hasta que ya esta en develop.
"""

import pathlib
import re
import sys

import yaml


class CargadorEstricto(yaml.SafeLoader):
    """SafeLoader que trata una clave repetida como error, no como sustitucion."""


# `<<: *ancla` es la clave de fusion de YAML. Su nodo no lleva el tag de una
# cadena normal, asi que construirlo como clave revienta; ademas puede aparecer
# legitimamente y no es lo que se persigue aqui. Se salta.
TAG_FUSION = "tag:yaml.org,2002:merge"


def _sin_duplicados(cargador, nodo, deep=False):
    vistas = {}
    for nodo_clave, nodo_valor in nodo.value:
        if nodo_clave.tag == TAG_FUSION:
            continue
        clave = cargador.construct_object(nodo_clave, deep=deep)
        if clave in vistas:
            raise yaml.constructor.ConstructorError(
                "mientras se construia un mapa",
                nodo.start_mark,
                f"clave duplicada {clave!r}: ya estaba definida en la linea "
                f"{vistas[clave] + 1}",
                nodo_clave.start_mark,
            )
        vistas[clave] = nodo_clave.start_mark.line
    return yaml.SafeLoader.construct_mapping(cargador, nodo, deep)


CargadorEstricto.add_constructor(
    yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, _sin_duplicados
)

# Compose interpola ${VAR} y ${VAR:-valor} antes de leer el YAML; para el
# analizador son texto plano, salvo cuando el valor por omision lleva `:` o `#`
# y confunde al lexer. Se sustituyen por un marcador inocuo: lo que se comprueba
# aqui es la forma del mapa, no los valores.
INTERPOLACION = re.compile(r"\$\{[^}]*\}")


def main() -> int:
    raiz = pathlib.Path(__file__).resolve().parents[2]
    archivos = sorted(raiz.glob("docker-compose*.yml")) + sorted(
        raiz.glob("tests/**/compose*.yml")
    )
    if not archivos:
        print("::error::No se encontro ningun archivo de Compose que comprobar.")
        return 1

    fallos = 0
    for archivo in archivos:
        texto = INTERPOLACION.sub("INTERPOLADO", archivo.read_text(encoding="utf-8"))
        relativa = archivo.relative_to(raiz)
        try:
            yaml.load(texto, Loader=CargadorEstricto)
        except yaml.YAMLError as error:
            fallos += 1
            detalle = str(error).replace("\n", " ")
            print(f"::error file={relativa}::{detalle}")
            print(f"  {relativa}: {detalle}")
        else:
            print(f"  {relativa}: sin claves duplicadas")

    if fallos:
        print()
        print(
            "::error::Docker Compose se niega a leer un archivo con claves "
            "repetidas, y al negarse no despliega NADA de ese archivo. "
            "Ver la cabecera de este guardian."
        )
        return 1

    print(f"\n{len(archivos)} archivos de Compose, ninguna clave duplicada.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
