#!/usr/bin/env python3
"""
Guardian: un cambio incompatible abre version nueva — regla 2 de plataforma.

La regla 2 dice «todo bajo /api/v1; un cambio incompatible abre version nueva,
no modifica la existente». Hasta R11.6 eso era una frase: nada comprobaba que
al quitar una operacion de un contrato alguien subiera la version, ni siquiera
que la tocara.

Esto compara cada contrato contra **su propia version en la base de la rama**.
No consulta un broker, ni un registro, ni una API: `git show BASE:archivo` y
un `yaml.safe_load`. Por eso no puede caerse por culpa de un servicio externo,
que es el fallo tipico de las compuertas de contrato.

## Que considera incompatible

Solo cosas que rompen a un consumidor que ya funcionaba, sin falsos positivos
de mantenimiento:

  * **desaparece una operacion** (metodo + ruta). El cliente que la llamaba
    empieza a recibir 404.
  * **desaparece un `operationId`**. Aunque la ruta siga ahi, el metodo
    generado cambia de nombre y el codigo del consumidor no compila.
  * **desaparece un codigo de respuesta 2xx** de una operacion que sigue
    existiendo. El consumidor que sabia leer esa respuesta deja de poder
    hacerlo.

Un **codigo de error (4xx/5xx) que desaparece NO es incompatible**, y esa
regla se afino porque lo primero que esta compuerta marco fue el cambio de
quien la escribio: ADR-006 quito los dos `409` de `metricas-plataforma.yaml`
porque el servicio ya no puede producirlos. Pensandolo bien, un consumidor no
puede *depender* de recibir un error: si deja de llegar, su rama de manejo
queda muerta, no rota. Quitar un error que el servidor ya no emite es corregir
la documentacion, no romper el contrato. Lo contrario —dar el visto bueno a la
excepcion porque era nuestra— habria convertido la compuerta en un adorno.

Se sigue exigiendo que la version se mueva, de modo que el cambio no pasa en
silencio; solo deja de exigirse que sea MAYOR.
  * **un parametro que era opcional pasa a obligatorio**, o **aparece un
    parametro obligatorio nuevo**. Las peticiones que ya se enviaban empiezan
    a fallar con 400.

Añadir operaciones, respuestas o parametros opcionales NO es incompatible y no
se marca: es exactamente lo que hace crecer un contrato sin romper a nadie.

## Que exige cuando encuentra uno

Que `info.version` suba de **mayor**. Un cambio incompatible con un parche o
un menor es la situacion que la regla 2 prohibe: el consumidor actualiza sin
enterarse.

Y hay una comprobacion mas floja pero util: si el contrato cambio de forma
compatible y `info.version` **no se movio**, tambien falla. Un contrato que
cambia sin cambiar de version no se puede referenciar en un acta ni en un
issue: «la 1.3.0» deja de significar algo.

Uso:
    python3 tests/contratos/cambios-de-contrato.py <ref-base>
"""

import subprocess
import sys

import yaml

CARPETA = "contracts/openapi/"

NO_SON_METODOS = {"parameters", "servers", "summary", "description", "$ref"}


def version_en(ref: str, ruta: str) -> dict | None:
    """El contrato tal como estaba en `ref`, o None si aun no existia."""
    resultado = subprocess.run(
        ["git", "show", f"{ref}:{ruta}"],
        capture_output=True,
        text=True,
        check=False,
    )
    if resultado.returncode != 0:
        return None
    try:
        return yaml.safe_load(resultado.stdout) or {}
    except yaml.YAMLError:
        # Si la version anterior no era YAML valido no hay nada que comparar;
        # el validador de estructura ya se ocupa de la version actual.
        return None


def leer(ruta: str) -> dict:
    with open(ruta, encoding="utf-8") as archivo:
        return yaml.safe_load(archivo) or {}


def operaciones(documento: dict) -> dict[str, dict]:
    """{"POST /salas": {...}} para toda operacion del documento."""
    encontradas: dict[str, dict] = {}
    for camino, metodos in (documento.get("paths") or {}).items():
        if not isinstance(metodos, dict):
            continue
        for metodo, operacion in metodos.items():
            if metodo in NO_SON_METODOS or not isinstance(operacion, dict):
                continue
            encontradas[f"{metodo.upper()} {camino}"] = operacion
    return encontradas


def obligatorios(operacion: dict) -> set[str]:
    """Nombres de los parametros marcados `required: true`."""
    return {
        str(p.get("name"))
        for p in (operacion.get("parameters") or [])
        if isinstance(p, dict) and p.get("required") is True
    }


def opcionales(operacion: dict) -> set[str]:
    return {
        str(p.get("name"))
        for p in (operacion.get("parameters") or [])
        if isinstance(p, dict) and p.get("required") is not True
    }


def incompatibilidades(antes: dict, ahora: dict) -> tuple[list[str], list[str]]:
    """(incompatibles, compatibles-dignos-de-mencion)."""
    rotos: list[str] = []
    notas: list[str] = []

    ops_antes = operaciones(antes)
    ops_ahora = operaciones(ahora)

    for clave in sorted(set(ops_antes) - set(ops_ahora)):
        rotos.append(f"desaparecio la operacion «{clave}»")

    ids_antes = {o.get("operationId") for o in ops_antes.values() if o.get("operationId")}
    ids_ahora = {o.get("operationId") for o in ops_ahora.values() if o.get("operationId")}
    for identificador in sorted(ids_antes - ids_ahora):
        rotos.append(f"desaparecio el operationId «{identificador}»")

    for clave in sorted(set(ops_antes) & set(ops_ahora)):
        antigua, nueva = ops_antes[clave], ops_ahora[clave]

        codigos_antes = set((antigua.get("responses") or {}).keys())
        codigos_ahora = set((nueva.get("responses") or {}).keys())
        for codigo in sorted(codigos_antes - codigos_ahora):
            if str(codigo).startswith("2"):
                rotos.append(f"«{clave}» ya no declara la respuesta {codigo}")
            else:
                # Ver el docstring: un error que desaparece deja al consumidor
                # con una rama muerta, no con una peticion rota.
                notas.append(f"«{clave}» ya no declara la respuesta {codigo} (error: compatible)")

        nuevos_obligatorios = obligatorios(nueva) - obligatorios(antigua)
        for nombre in sorted(nuevos_obligatorios):
            if nombre in opcionales(antigua):
                rotos.append(f"«{clave}»: el parametro «{nombre}» paso de opcional a obligatorio")
            else:
                rotos.append(f"«{clave}»: parametro obligatorio nuevo «{nombre}»")

    return rotos, notas


def mayor(version: object) -> int | None:
    try:
        return int(str(version).split(".")[0])
    except (ValueError, IndexError):
        return None


def main() -> int:
    if len(sys.argv) != 2:
        print("uso: cambios-de-contrato.py <ref-base>")
        return 2
    base = sys.argv[1]

    diferencia = subprocess.run(
        ["git", "diff", "--name-only", base, "--", CARPETA],
        capture_output=True,
        text=True,
        check=False,
    )
    if diferencia.returncode != 0:
        # Sin esto, un `git` que falla —base inexistente, historial poco
        # profundo, worktree mal montado— devolveria una lista vacia y el
        # guardian diria «nada que comparar» en verde. Es exactamente el modo
        # de fallo que R11.6 vino a quitar: un verde que significa «no se
        # ejecuto».
        print(f"::error::no se pudo comparar contra «{base}»: {diferencia.stderr.strip()}")
        print("Comprueba que la base existe y que el checkout trae historial (fetch-depth: 0).")
        return 1

    contratos = sorted(r for r in diferencia.stdout.split() if r.endswith(".yaml"))

    if not contratos:
        print("Ningun contrato OpenAPI cambio respecto a la base; nada que comparar.")
        return 0

    fallos = 0
    for ruta in contratos:
        antes = version_en(base, ruta)
        if antes is None:
            print(f"nuevo   {ruta} (no existia en la base: no hay compatibilidad que romper)")
            continue

        ahora = leer(ruta)
        version_antes = (antes.get("info") or {}).get("version")
        version_ahora = (ahora.get("info") or {}).get("version")
        rotos, notas = incompatibilidades(antes, ahora)

        if rotos:
            subio_mayor = (
                mayor(version_ahora) is not None
                and mayor(version_antes) is not None
                and mayor(version_ahora) > mayor(version_antes)
            )
            if subio_mayor:
                print(f"ok      {ruta}: cambio incompatible, pero {version_antes} -> {version_ahora} abre version mayor")
                for motivo in rotos:
                    print(f"          · {motivo}")
                continue

            print(f"::error file={ruta}::cambio incompatible sin abrir version mayor ({version_antes} -> {version_ahora})")
            for motivo in rotos:
                print(f"::error file={ruta}::  · {motivo}")
            fallos += 1
            continue

        if version_antes == version_ahora:
            print(f"::error file={ruta}::el contrato cambio y info.version sigue en {version_ahora}")
            fallos += 1
            continue

        print(f"ok      {ruta}: cambio compatible, {version_antes} -> {version_ahora}")
        for nota in notas:
            print(f"          · {nota}")

    if fallos:
        print(f"\n{fallos} contrato(s) incumplen la regla 2.")
        print("Un cambio incompatible abre version MAYOR; uno compatible sube menor o parche.")
        return 1

    print(f"\n{len(contratos)} contrato(s) cambiados, todos conformes con la regla 2.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
