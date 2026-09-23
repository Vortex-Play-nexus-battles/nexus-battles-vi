#!/usr/bin/env python3
"""
Guardian: ninguna linea de bitacora imprime un secreto — R11.2.

## Por que hace falta

La regla 6 manda la bitacora a stdout en JSON, y desde R11 sale en los veinte
servicios. Eso es bueno para diagnosticar y es exactamente lo que convierte un
`log.info("token={}", token)` descuidado en un incidente: lo que antes se
perdia en una consola ahora va a un agregador, queda indexado y lo lee
cualquiera con acceso al tablero.

Cuando se escribio este guardian, la auditoria encontro **cero fugas**: los
dos unicos sitios que tocan material secreto de verdad
(`ClientesDeServicio` y `TokenController` de ms-identidad) estan escritos a
proposito para registrar identificadores y no valores. Este archivo no arregla
nada: **fija ese estado** para que la primera fuga sea un fallo de CI y no un
hallazgo de auditoria seis semanas despues.

## Que busca

Una llamada de bitacora cuyo TEXTO nombre material sensible y que ademas pase
argumentos. El texto solo —`log.warn("no hay credencial de servicio")`— es
inofensivo y no se marca; lo que se marca es la combinacion de un texto que
habla de un secreto con un `{}` al lado.

Se mira la LLAMADA ENTERA, no la linea. Dos avisos legitimos de ms-identidad
—el de correo y el de auditoria del cambio de contrasena— parten el texto y
los argumentos en lineas distintas: leyendo linea a linea, el `{}` se ve y el
`getEmail()` que lo rellena no, y los dos salian marcados sin serlo.

## Como se silencia un falso positivo

No hay lista de exclusiones por archivo. Si una linea es legitima, se reescribe
para que se entienda por si sola: `clientId` en vez de `credencial`,
`usuario.getEmail()` en vez de `datos`. Una excepcion que se pueda pedir es una
excepcion que se acaba pidiendo.

Uso:
    python3 tests/contratos/sin-secretos-en-bitacora.py
Salida distinta de cero si aparece una linea sospechosa.
"""

import pathlib
import re
import sys

RAICES = ["services", "shared"]

# Llamada de bitacora: log/logger/BITACORA/bitacora . nivel ( ... )
LLAMADA = re.compile(
    r"\b(?:log|logger|LOG|LOGGER|BITACORA|bitacora)\s*\.\s*"
    r"(?:trace|debug|info|warn|error)\s*\(",
    re.IGNORECASE,
)

# Palabras que, DENTRO del texto de la llamada, indican material sensible.
# `token` no entra: aparece en prosa legitima ("sus tokens saldran sin uid") y
# el patron de argumento de abajo es el que decide.
SENSIBLE = re.compile(
    r"(password|contrase|secret|secreto|authorization|bearer|"
    r"clave[-_ ]?privada|client[-_ ]?secret|jwt\b|credencial)",
    re.IGNORECASE,
)

# Un argumento de verdad: el `{}` de SLF4J, o una concatenacion con algo que
# NO es otro literal. `"texto " + "mas texto"` es una sola frase partida en dos
# lineas para que quepa, y no imprime nada: dos avisos legitimos —el de
# ClientesDeServicio y el de FinanzasPublicacionClientHttp— salian marcados
# por eso.
INTERPOLA = re.compile(r"\{\}|\+\s*[A-Za-z_]")

# Nombres que son identificadores, no secretos: si el argumento es uno de
# estos, la linea esta bien aunque el texto hable de credenciales.
IDENTIFICADORES = re.compile(
    r"(clientId|client_id|keySet\(\)|getEmail\(\)|apodo|usuarioId|\buid\b|"
    r"getMessage\(\)|,\s*ex\)|,\s*e\)|\bclave\b|\bversion\b)",
)


def llamadas(texto: str):
    """Cada llamada de bitacora completa, con su linea de inicio.

    Una llamada puede ocupar varias lineas; se acumula desde `log.x(` hasta
    que los parentesis se equilibran. Sin esto, un aviso partido en dos lineas
    se juzga por su mitad de arriba y se marca aunque su argumento sea un
    identificador inofensivo.
    """
    lineas = texto.splitlines()
    i = 0
    while i < len(lineas):
        if not LLAMADA.search(lineas[i]):
            i += 1
            continue
        inicio = i
        acumulado = []
        profundidad = 0
        abierta = False
        while i < len(lineas):
            acumulado.append(lineas[i])
            for caracter in lineas[i]:
                if caracter == "(":
                    profundidad += 1
                    abierta = True
                elif caracter == ")":
                    profundidad -= 1
            i += 1
            if abierta and profundidad <= 0:
                break
        yield inicio + 1, " ".join(trozo.strip() for trozo in acumulado)


def lineas_sospechosas(ruta: pathlib.Path) -> list[tuple[int, str]]:
    hallazgos = []
    try:
        texto = ruta.read_text(encoding="utf-8")
    except (UnicodeDecodeError, OSError):
        return hallazgos

    for numero, llamada in llamadas(texto):
        if not SENSIBLE.search(llamada):
            continue
        if not INTERPOLA.search(llamada):
            # Texto fijo sobre credenciales, sin argumentos: inofensivo.
            continue
        if IDENTIFICADORES.search(llamada):
            # Habla de credenciales pero imprime un identificador.
            continue
        hallazgos.append((numero, llamada))
    return hallazgos


def main() -> int:
    fallos = []
    revisados = 0

    for raiz in RAICES:
        base = pathlib.Path(raiz)
        if not base.exists():
            continue
        for ruta in base.rglob("*.java"):
            partes = ruta.parts
            if "build" in partes or "build.nosync" in partes or "target" in partes:
                continue
            # Las pruebas pueden imprimir lo que quieran: no se despliegan.
            if "test" in partes:
                continue
            revisados += 1
            for numero, linea in lineas_sospechosas(ruta):
                recorte = linea if len(linea) <= 160 else linea[:160] + "…"
                print(f"::error file={ruta},line={numero}::posible secreto en la bitacora: {recorte}")
                fallos.append(f"{ruta}:{numero}")

    if fallos:
        print(f"\n{len(fallos)} linea(s) de bitacora sospechosa(s).")
        print("Si es legitima, reescribela para que se entienda sola:")
        print("  - imprime el identificador, no el valor (clientId, uid, apodo)")
        print("  - o quita el argumento y deja el texto fijo")
        return 1

    print(f"Ninguna linea de bitacora imprime secretos ({revisados} archivos de produccion).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
