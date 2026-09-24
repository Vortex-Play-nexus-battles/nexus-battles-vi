#!/usr/bin/env python3
"""
Guardian: cada pacto tiene quien lo verifique, y sus estados existen — R11.6.

## Por que hace falta

En R11.4 se monto la primera verificacion de proveedor
(`VerificacionDelPactoDeSubastasTest` en ms-finanzas). Corre con
Testcontainers, y como la clase esta anotada
`@Testcontainers(disabledWithoutDocker = true)`, **en una maquina sin Docker
se salta entera y la corrida sale verde**. Ese es un verde que no significa
nada, igual que el `ci-contratos` condicional que R11.6 vino a quitar.

Esto no sustituye a la verificacion —no habla HTTP con nadie—, la vigila: lee
los JSON de `contracts/pactos/` y el codigo de las clases `@Provider`, y
comprueba **sin Docker y en menos de un segundo** que el cableado sigue en su
sitio. Si alguien anade una interaccion con un `given(...)` nuevo y se olvida
del `@State`, esto se pone rojo aunque la corrida con Docker no llegue a
ejecutarse.

## Que comprueba

  * Que cada pacto JSON se pueda leer y declare consumidor y proveedor.
  * Que exista una clase de prueba anotada `@Provider("<proveedor>")`.
  * Que **cada `providerState` del pacto** tenga su `@State("...")` con la
    cadena exacta en esa clase. La comparacion es literal a proposito: pact
    tambien casa por cadena exacta, y un estado que no casa se salta en
    silencio dejando que la interaccion se verifique contra un mock vacio.

Un pacto **sin ninguna clase verificadora** no es un fallo: se reporta como
brecha conocida y se listan sus estados; montar una compuerta roja a proposito
termina con alguien desactivandola. Ese era el caso de
`ms-subastas-ms-inventario.json` mientras su endpoint de transferencias no
existia. Desde FI-TRANSFER-1 existe, esta en el contrato y lo verifica
`VerificacionDelPactoDeSubastasTest` con sus seis estados, asi que hoy no queda
ninguna brecha de estas.

Uso:
    python3 tests/contratos/pactos-verificados.py
Salida distinta de cero si un pacto verificado tiene estados sin cubrir.
"""

import json
import pathlib
import re
import sys

PACTOS = pathlib.Path("contracts/pactos")

# Una clase @Provider es una prueba, y las pruebas de los servicios viven
# siempre en `<equipo>/<servicio>/src/test/java`. Acotar el recorrido a esos
# arboles, en vez de recorrer `services/` entera, evita atravesar los
# directorios `build/` —que en un monorepo de veinte modulos son decenas de
# miles de archivos— y deja el guardian en decimas de segundo.
ARBOLES = "services/*/*/src/test/java/**/*.java"

PROVEEDOR = re.compile(r'@Provider\s*\(\s*"([^"]+)"\s*\)')
ESTADO = re.compile(r'@State\s*\(\s*"([^"]+)"\s*\)')


def estados_del_pacto(documento: dict) -> set[str]:
    """Los `given(...)` del pacto, en cualquiera de las dos formas del formato."""
    encontrados: set[str] = set()
    for interaccion in documento.get("interactions", []):
        # Pact v3: lista de objetos con `name`. Pact v2: una sola cadena.
        for estado in interaccion.get("providerStates", []):
            if isinstance(estado, dict) and estado.get("name"):
                encontrados.add(str(estado["name"]))
        suelto = interaccion.get("providerState")
        if suelto:
            encontrados.add(str(suelto))
    return encontrados


def verificadores() -> dict[str, tuple[pathlib.Path, set[str]]]:
    """{proveedor: (archivo, estados cubiertos)} de las clases @Provider."""
    encontrados: dict[str, tuple[pathlib.Path, set[str]]] = {}
    for ruta in pathlib.Path(".").glob(ARBOLES):
        try:
            texto = ruta.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        if "@Provider" not in texto:
            continue
        nombre = PROVEEDOR.search(texto)
        if not nombre:
            continue
        encontrados[nombre.group(1)] = (ruta, set(ESTADO.findall(texto)))
    return encontrados


def main() -> int:
    if not PACTOS.exists():
        print(f"::error::no existe el directorio {PACTOS}")
        return 1

    archivos = sorted(PACTOS.glob("*.json"))
    if not archivos:
        print(f"::error::no hay ningun pacto en {PACTOS}")
        return 1

    clases = verificadores()
    fallos = 0
    sin_verificar = []

    for archivo in archivos:
        try:
            documento = json.loads(archivo.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, OSError) as fallo:
            print(f"::error file={archivo}::no se pudo leer el pacto: {fallo}")
            fallos += 1
            continue

        consumidor = (documento.get("consumer") or {}).get("name", "?")
        proveedor = (documento.get("provider") or {}).get("name", "?")
        estados = estados_del_pacto(documento)

        if proveedor not in clases:
            sin_verificar.append((archivo, consumidor, proveedor, estados))
            continue

        ruta_clase, cubiertos = clases[proveedor]
        huerfanos = sorted(estados - cubiertos)
        if huerfanos:
            print(f"::error file={ruta_clase}::el pacto {archivo.name} declara estados sin @State:")
            for estado in huerfanos:
                print(f'::error file={ruta_clase}::  · «{estado}»')
            fallos += 1
            continue

        print(
            f"ok    {archivo.name}: {consumidor} -> {proveedor}, "
            f"{len(estados)} estado(s) cubiertos por {ruta_clase.name}"
        )

    for archivo, consumidor, proveedor, estados in sin_verificar:
        print(f"\nBRECHA  {archivo.name}: {consumidor} -> {proveedor} no tiene clase @Provider.")
        print("        El pacto existe y nadie lo verifica. Estados que habria que saber montar:")
        for estado in sorted(estados):
            print(f"          · {estado}")

    if fallos:
        print(f"\n{fallos} pacto(s) con estados sin cubrir.")
        print("Anade el @State que falta, o quita la interaccion del consumidor si ya no aplica.")
        return 1

    verificados = len(archivos) - len(sin_verificar)
    print(f"\n{verificados} de {len(archivos)} pacto(s) verificados, con todos sus estados cubiertos.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
