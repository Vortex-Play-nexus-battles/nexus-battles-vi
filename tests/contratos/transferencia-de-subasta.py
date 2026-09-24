#!/usr/bin/env python3
"""
Guardian: la transferencia de propiedad por subasta sigue siendo real —
FI-TRANSFER-1 (HU-SUB-004).

## Por que hace falta

Durante meses `InventarioClientHttp` llamaba a
`POST /api/v1/inventario/elementos/{id}/transferencias` y esa ruta **no existia
en ningun controlador ni en ningun contrato**. El efecto no era un error
visible: la subasta se adjudicaba, el ganador pagaba, y el objeto no cambiaba
de dueno. El cliente atrapaba el 404 y seguia. Todos los verdes del repositorio
—unitarios, contrato, E2E— eran compatibles con eso.

Y habia un segundo modo de llegar al mismo sitio sin tocar una linea de Java:
`app.inventario.modo` cae en `fake` por omision, asi que un despliegue que no
declare `INVENTARIO_MODO` habla con un doble en memoria aunque la ruta exista y
la URL este puesta. Fue literalmente el caso de `tests/e2e/compose.yml`, que
declaraba `INVENTARIO_BASE_URL` y nunca la usaba.

Las pruebas guardan el comportamiento; esto guarda que el cableado siga
conectado, que es lo que se rompio. Corre en menos de un segundo y sin Docker.

## Que comprueba

  * Que el contrato de inventario declare la ruta, con su `operationId`.
  * Que algun controlador de inventario la exponga de verdad (`@RequestMapping`
    o `@PostMapping` con esa ruta).
  * Que `SeguridadConfig` la restrinja explicitamente: una ruta que mueve
    propiedad no puede quedarse solo con «autenticado».
  * Que el cliente de ms-subastas llame exactamente a esa ruta, y no a una
    parecida.
  * Que todo compose que le de a ms-subastas una `INVENTARIO_BASE_URL` declare
    tambien `INVENTARIO_MODO` con algo distinto de `fake`. Poner la URL y
    dejarse el modo es la forma silenciosa de no integrar nada.

Uso:
    python3 tests/contratos/transferencia-de-subasta.py
Salida distinta de cero si cualquiera de esas piezas falta.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]

RUTA = "/api/v1/inventario/elementos/{elementoId}/transferencias"
SEGMENTO = "transferencias"
OPERACION = "transferirElementoPorSubasta"

CONTRATO = RAIZ / "contracts/openapi/inventario.yaml"
API_INVENTARIO = RAIZ / "services/contenido/inventario/src/main/java/nexus/inventario/api"
SEGURIDAD = (RAIZ / "services/contenido/inventario/src/main/java/nexus/inventario"
             / "configuracion/SeguridadConfig.java")
CLIENTE = (RAIZ / "services/cuentas/ms-subastas/src/main/java/com/nexusbattles/ms_subastas"
           / "subastas/port/InventarioClientHttp.java")


def leer(ruta: Path) -> str | None:
    try:
        return ruta.read_text(encoding="utf-8")
    except OSError:
        return None


def revisar_contrato(fallos: list[str]) -> None:
    texto = leer(CONTRATO)
    if texto is None:
        fallos.append(f"no se pudo leer {CONTRATO.relative_to(RAIZ)}")
        return
    # Como clave de YAML y no como subcadena: con `in` a secas, renombrar la
    # ruta a «.../transferencias-x» seguia pasando el guardian, porque la vieja
    # es prefijo de la nueva. Una ruta parecida no es la misma ruta.
    declarada = re.search(r"^ {2}" + re.escape(RUTA) + r":\s*$", texto, re.MULTILINE)
    if not declarada:
        fallos.append(
            f"{CONTRATO.relative_to(RAIZ)} no declara «{RUTA}» como ruta. Una que "
            "el cliente llama y el contrato no declara es una API acordada de palabra.")
    elif OPERACION not in texto:
        fallos.append(
            f"{CONTRATO.relative_to(RAIZ)} declara la ruta sin operationId "
            f"«{OPERACION}»: los generadores y el validador de duplicados lo necesitan.")
    else:
        print(f"ok    el contrato declara la ruta, con operationId {OPERACION}")


def revisar_controlador(fallos: list[str]) -> None:
    if not API_INVENTARIO.is_dir():
        fallos.append(f"no existe {API_INVENTARIO.relative_to(RAIZ)}")
        return
    patron = re.compile(
        r'@(?:RequestMapping|PostMapping)\s*\(\s*(?:value\s*=\s*)?"[^"]*'
        + SEGMENTO + r'"')
    for fuente in sorted(API_INVENTARIO.glob("*.java")):
        texto = leer(fuente) or ""
        if patron.search(texto):
            print(f"ok    {fuente.name} expone la ruta")
            return
    fallos.append(
        "ningun controlador de inventario mapea «.../transferencias». Si se "
        "borro, ms-subastas cobra y no entrega: el cliente se traga el 404.")


def revisar_seguridad(fallos: list[str]) -> None:
    texto = leer(SEGURIDAD)
    if texto is None:
        fallos.append(f"no se pudo leer {SEGURIDAD.relative_to(RAIZ)}")
        return
    linea = next((l for l in texto.splitlines() if SEGMENTO in l), None)
    if linea is None:
        fallos.append(
            "SeguridadConfig no menciona «transferencias». Sin una regla propia "
            "la ruta cae en el comodin de elementos, y cualquier token serviria "
            "para cambiar de dueno un objeto ajeno.")
        return
    indice = texto.splitlines().index(linea)
    siguientes = "\n".join(texto.splitlines()[indice:indice + 4])
    if "permitAll" in siguientes or "authenticated()" in siguientes:
        fallos.append(
            "la regla de «transferencias» en SeguridadConfig se conforma con "
            "permitAll/authenticated. Mueve propiedad: exige la credencial del "
            "servicio de subastas.")
    else:
        print("ok    SeguridadConfig restringe la ruta con una regla propia")


def revisar_cliente(fallos: list[str]) -> None:
    texto = leer(CLIENTE)
    if texto is None:
        fallos.append(f"no se pudo leer {CLIENTE.relative_to(RAIZ)}")
        return
    if '"/api/v1/inventario/elementos/" + elementoInventarioId + "/transferencias"' in texto:
        print("ok    InventarioClientHttp llama a la misma ruta del contrato")
    elif SEGMENTO in texto:
        fallos.append(
            "InventarioClientHttp menciona «transferencias» pero no con la ruta "
            f"del contrato ({RUTA}). Una ruta parecida no es la misma ruta.")
    else:
        fallos.append("InventarioClientHttp ya no llama a la ruta de transferencias")


def revisar_modo(fallos: list[str]) -> None:
    revisados = 0
    for compose in sorted(set(candidatos())):
        texto = leer(compose)
        if texto is None or "INVENTARIO_BASE_URL" not in texto:
            continue
        relativo = compose.relative_to(RAIZ)
        for servicio, bloque in servicios_con_url(texto):
            # Solo los bloques de ms-subastas. Otros servicios tienen su propia
            # INVENTARIO_BASE_URL con todo derecho —el chatbot reenvia el token
            # del jugador— y no pasan por app.inventario.modo.
            if "subastas" not in servicio:
                continue
            revisados += 1
            declarado = modo_declarado(bloque)
            if declarado is None:
                fallos.append(
                    f"{relativo} ({servicio}) declara INVENTARIO_BASE_URL y no "
                    "INVENTARIO_MODO: por omision es «fake», asi que la URL no se "
                    "usa y el ganador no recibe nada.")
            elif declarado == "fake":
                fallos.append(
                    f"{relativo} ({servicio}) pone INVENTARIO_MODO en «fake». "
                    "Un doble en memoria no entrega objetos.")
            else:
                print(f"ok    {relativo}: {servicio} habla con inventario de verdad "
                      f"(modo {declarado})")
    if revisados == 0:
        fallos.append(
            "ningun compose le da a ms-subastas una INVENTARIO_BASE_URL. O se "
            "borro la integracion, o este guardian dejo de encontrarla.")


def modo_declarado(bloque: str) -> str | None:
    """El valor efectivo de INVENTARIO_MODO en un bloque de servicio.

    Se ignoran los comentarios antes de buscar: el propio compose explica el
    problema citando `${INVENTARIO_MODO:fake}`, y una version anterior de este
    guardian leia esa cita como si fuese la configuracion. Tambien se resuelve
    la forma `${VAR:-valor}`, donde lo que manda es el valor por omision.
    """
    for linea in bloque.splitlines():
        sin_comentario = linea.split("#", 1)[0]
        hallado = re.match(r"\s*INVENTARIO_MODO:\s*(\S+)", sin_comentario)
        if not hallado:
            continue
        valor = hallado.group(1).strip("\"'")
        interpolado = re.match(r"\$\{[A-Z_]+:-?([^}]*)\}", valor)
        if interpolado:
            valor = interpolado.group(1)
        return valor or None
    return None


def candidatos() -> list[Path]:
    """Los composes del repositorio, buscados en sitio y no recursivamente.

    Un `glob("**/*.yml")` desde la raiz tarda minutos: el repositorio arrastra
    directorios de build y arboles de trabajo sueltos. Estas tres ubicaciones
    son donde viven los composes de verdad; si alguien anade una cuarta, este
    guardian avisa por el camino contrario —dira que ningun compose declara la
    integracion— en vez de callarse.
    """
    sitios = [
        RAIZ.glob("docker-compose*.yml"),
        (RAIZ / "tests/e2e").glob("*.yml"),
        (RAIZ / "infrastructure").glob("*/*/*.yml"),
    ]
    return [ruta for sitio in sitios for ruta in sitio if ruta.is_file()]


def servicios_con_url(texto: str) -> list[tuple[str, str]]:
    """Los servicios que declaran INVENTARIO_BASE_URL, con su nombre.

    Se corta por definicion de servicio (dos espacios de sangria y dos puntos)
    en vez de con un parser de YAML, para no anadirle una dependencia a un
    guardian que tiene que poder correr en cualquier sitio.
    """
    encontrados: list[tuple[str, str]] = []
    nombre: str | None = None
    actual: list[str] = []

    def cerrar() -> None:
        if nombre and "INVENTARIO_BASE_URL" in "\n".join(actual):
            encontrados.append((nombre, "\n".join(actual)))

    for linea in texto.splitlines():
        cabecera = re.match(r"^  ([\w.-]+):\s*$", linea)
        if cabecera:
            cerrar()
            nombre = cabecera.group(1)
            actual = []
        elif nombre is not None:
            actual.append(linea)
    cerrar()
    return encontrados


def main() -> int:
    fallos: list[str] = []
    revisar_contrato(fallos)
    revisar_controlador(fallos)
    revisar_seguridad(fallos)
    revisar_cliente(fallos)
    revisar_modo(fallos)

    if fallos:
        print()
        for fallo in fallos:
            print(f"::error::{fallo}")
        print(f"\n{len(fallos)} pieza(s) de la transferencia de subasta sin conectar.")
        return 1

    print("\nLa transferencia de propiedad por subasta esta conectada de punta a punta.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
