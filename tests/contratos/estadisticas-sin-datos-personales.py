#!/usr/bin/env python3
"""
Guardian: las estadisticas no identifican a nadie — §7.3.6 («Anonimizacion de
datos para analisis estadisticos»), RF-PRV-002, ADR-010.

## Por que hace falta

Las cifras que la plataforma publica para analizarla —los indicadores de
cuentas, el tablero de moderacion, las metricas de sanciones, la frecuencia de
reportes, las analiticas del asistente, el estado del catalogo, el tablero
tecnico— se calculan sobre datos personales (quien se registro, a quien se
sanciono, quien reporto, quien converso con el asistente), pero **solo salen
agregadas**: cuantas, por dia, por estado, por categoria. Ninguna lleva el
`uid`, el correo, el apodo, el nombre ni la IP de una persona.

Ese es el modo en que hoy se cumple la anonimizacion para analisis estadistico
(ADR-010): no hay un conjunto de datos aparte que anonimizar, porque ninguna
estadistica sale con identificadores. Bastaria un campo `uid` anadido a una de
esas respuestas para romperlo sin que ninguna prueba se enterara. Este guardian
lo fija.

## Que mira

Para cada operacion de `OPERACIONES`, todas las propiedades de su respuesta
2xx en JSON, recorriendo `$ref`, `allOf`/`oneOf`/`anyOf`, `items` y
`additionalProperties`. Falla si:

  * una propiedad identifica a una persona (`IDENTIFICADORES`, o un `...Id`
    de una persona: `usuarioId`, `jugadorId`, `autorId`, `idJugador`...);
  * una operacion de la lista ya no existe o no responde JSON: la lista se
    actualiza en el mismo cambio, nunca se queda vacia sin avisar.

Las consultas sobre UNA persona (la ficha administrativa, las estadisticas de
un jugador, el resumen de mis pujas) no son analisis estadistico y no estan en
la lista: identifican a esa persona por diseno y las protege su permiso.

Uso:
    python3 tests/contratos/estadisticas-sin-datos-personales.py              # mira los contratos
    python3 tests/contratos/estadisticas-sin-datos-personales.py --autoprueba # demuestra que se pone rojo
"""

from __future__ import annotations

import copy
import pathlib
import re
import sys
import unicodedata

import yaml

CONTRATOS = pathlib.Path("contracts/openapi")

# Operaciones que publican estadisticas para analizar la plataforma, por
# contrato. Una estadistica nueva entra aqui en el mismo cambio que la crea.
OPERACIONES: dict[str, tuple[str, ...]] = {
    "comentarios.yaml": ("frecuenciaDeReportes", "resumenDeCalificaciones"),
    "metricas-plataforma.yaml": ("tableroTecnico", "tableroDeModeracion"),
    "moderacion-sanciones-consulta.yaml": ("metricasDeModeracion",),
    "ms-chatbot.yaml": ("consultarAnaliticasDelChatbot",),
    "ms-identidad-admin.yaml": ("indicadoresDeCuentas",),
    "productos.yaml": ("consultarEstadoCatalogo",),
}

# Nombres de propiedad que identifican a una persona (comparados sin tildes,
# sin mayusculas y sin separadores).
IDENTIFICADORES = {
    "uid", "sub", "usuario", "jugador", "autor", "moderador", "administrador",
    "reportante", "email", "correo", "correoelectronico", "apodo", "nickname",
    "username", "nombres", "apellidos", "nombrecompleto", "telefono", "celular",
    "ip", "iporigen", "direccionip", "ipaddress", "fechadenacimiento",
    "fechanacimiento", "documento",
}

# `...Id` o `id...` de una persona.
PERSONAS = ("usuario", "jugador", "autor", "moderador", "administrador", "reportante",
            "cuenta", "user", "player", "author")
ID_DE_PERSONA = re.compile(r"^(?:(?:%s)id|id(?:%s))$" % ("|".join(PERSONAS), "|".join(PERSONAS)))


def normalizar(nombre: str) -> str:
    """'ipOrigen' -> 'iporigen'; 'Correo_Electrónico' -> 'correoelectronico'."""
    sin_tildes = "".join(c for c in unicodedata.normalize("NFD", nombre) if unicodedata.category(c) != "Mn")
    return re.sub(r"[^a-z0-9]", "", sin_tildes.lower())


def identifica_a_alguien(nombre: str) -> bool:
    clave = normalizar(nombre)
    return clave in IDENTIFICADORES or bool(ID_DE_PERSONA.match(clave))


# ------------------------------------------------------------------ recorrido

def resolver(documento: dict, nodo):
    """Sigue un `$ref` local (#/components/...) hasta su destino."""
    vistos = set()
    while isinstance(nodo, dict) and "$ref" in nodo:
        ref = nodo["$ref"]
        if not ref.startswith("#/") or ref in vistos:
            return nodo
        vistos.add(ref)
        destino = documento
        for parte in ref[2:].split("/"):
            destino = destino[parte]
        nodo = destino
    return nodo


def propiedades(documento: dict, esquema, ruta: str = "", vistos=None):
    """Cada propiedad del esquema como (ruta, nombre), recorriendo todo."""
    vistos = set() if vistos is None else vistos
    esquema = resolver(documento, esquema)
    if not isinstance(esquema, dict) or id(esquema) in vistos:
        return
    vistos.add(id(esquema))
    for combinacion in ("allOf", "oneOf", "anyOf"):
        for parte in esquema.get(combinacion) or []:
            yield from propiedades(documento, parte, ruta, vistos)
    if "items" in esquema:
        yield from propiedades(documento, esquema["items"], ruta + "[]", vistos)
    adicionales = esquema.get("additionalProperties")
    if isinstance(adicionales, dict):
        yield from propiedades(documento, adicionales, ruta + "{}", vistos)
    for nombre, valor in (esquema.get("properties") or {}).items():
        yield ruta + "." + nombre, nombre
        yield from propiedades(documento, valor, ruta + "." + nombre, vistos)


def respuestas_json(documento: dict, operacion: dict):
    for codigo, respuesta in (operacion.get("responses") or {}).items():
        if not str(codigo).startswith("2"):
            continue
        respuesta = resolver(documento, respuesta)
        for tipo, contenido in ((respuesta or {}).get("content") or {}).items():
            if "json" in tipo:
                yield str(codigo), contenido.get("schema", {})


def operaciones_por_id(documento: dict) -> dict[str, dict]:
    encontradas = {}
    for operaciones in (documento.get("paths") or {}).values():
        for metodo, operacion in (operaciones or {}).items():
            if isinstance(operacion, dict) and "operationId" in operacion:
                encontradas[operacion["operationId"]] = operacion
    return encontradas


# ---------------------------------------------------------------- comprobacion

def comprobar(contratos: dict[str, dict], lista: dict[str, tuple[str, ...]]) -> list[str]:
    errores = []
    for archivo, ids in lista.items():
        documento = contratos.get(archivo)
        if documento is None:
            errores.append(f"{archivo}: el contrato no existe; actualiza OPERACIONES")
            continue
        por_id = operaciones_por_id(documento)
        for operation_id in ids:
            operacion = por_id.get(operation_id)
            if operacion is None:
                errores.append(f"{archivo}: «{operation_id}» ya no existe; actualiza OPERACIONES")
                continue
            esquemas = list(respuestas_json(documento, operacion))
            if not esquemas:
                errores.append(f"{archivo}: «{operation_id}» no responde JSON en 2xx; actualiza OPERACIONES")
                continue
            for codigo, esquema in esquemas:
                for ruta, nombre in propiedades(documento, esquema):
                    if identifica_a_alguien(nombre):
                        errores.append(
                            f"{archivo}: «{operation_id}» ({codigo}) publica «{ruta.lstrip('.')}», "
                            "que identifica a una persona: una estadistica sale agregada (ADR-010)")
    return errores


def cargar_contratos(raiz: pathlib.Path) -> dict[str, dict]:
    return {ruta.name: yaml.safe_load(ruta.read_text(encoding="utf-8")) for ruta in sorted(raiz.glob("*.yaml"))}


# ------------------------------------------------------------------ autoprueba

def autoprueba() -> int:
    """Mete a proposito un identificador en copias de los contratos y exige verlo."""
    reales = cargar_contratos(CONTRATOS)
    errores = comprobar(reales, OPERACIONES)
    if errores:
        print("::error::la autoprueba necesita los contratos reales en verde, y no lo estan:")
        for error in errores:
            print(f"::error::{error}")
        return 1
    print("ok    contratos reales: en verde")

    def con(cambio):
        copia = copy.deepcopy(reales)
        cambio(copia)
        return copia

    def esquema_de(contratos, archivo, operation_id):
        documento = contratos[archivo]
        operacion = operaciones_por_id(documento)[operation_id]
        _, esquema = next(respuestas_json(documento, operacion))
        return resolver(documento, esquema)

    def anadir(archivo, operation_id, nombre, en=None):
        def cambio(contratos):
            esquema = esquema_de(contratos, archivo, operation_id)
            destino = resolver(contratos[archivo], en(esquema) if en else esquema)
            destino.setdefault("properties", {})[nombre] = {"type": "string"}
        return cambio

    def primer_arreglo(esquema):
        """El esquema de los elementos del primer arreglo de la respuesta."""
        for valor in (esquema.get("properties") or {}).values():
            if valor.get("type") == "array" and "items" in valor:
                return valor["items"]
        raise AssertionError("la respuesta no tiene ningun arreglo")

    def renombrar(archivo, operation_id):
        def cambio(contratos):
            operaciones_por_id(contratos[archivo])[operation_id]["operationId"] = operation_id + "Renombrada"
        return cambio

    casos = [
        ("un uid en los indicadores de cuentas",
         anadir("ms-identidad-admin.yaml", "indicadoresDeCuentas", "uid"), "publica «uid»"),
        ("el correo de quien reporta, dentro de un arreglo",
         anadir("comentarios.yaml", "frecuenciaDeReportes", "correo", en=primer_arreglo), "[].correo"),
        ("la IP en el tablero tecnico",
         anadir("metricas-plataforma.yaml", "tableroTecnico", "ipOrigen"), "«ipOrigen»"),
        ("el id del jugador en las analiticas del asistente",
         anadir("ms-chatbot.yaml", "consultarAnaliticasDelChatbot", "jugadorId"), "«jugadorId»"),
        ("el apodo de quien sanciona en las metricas de sanciones",
         anadir("moderacion-sanciones-consulta.yaml", "metricasDeModeracion", "apodo"), "«apodo»"),
        ("una estadistica renombrada sin actualizar la lista",
         renombrar("productos.yaml", "consultarEstadoCatalogo"), "ya no existe"),
    ]

    fallos = 0
    for titulo, cambio, esperado in casos:
        errores = comprobar(con(cambio), OPERACIONES)
        if any(esperado in error for error in errores):
            print(f"ok    {titulo}: rojo")
        else:
            print(f"::error::el guardian dejo pasar «{titulo}» (esperaba «{esperado}», vio {errores})")
            fallos += 1

    # Lo agregado no es un identificador: cuantos reportantes no es quien reporto.
    for nombre in ("reportantes", "moderadoresActivos", "cuentas", "total", "porDia", "productoId"):
        if identifica_a_alguien(nombre):
            print(f"::error::«{nombre}» es una cifra o un objeto, no una persona, y el guardian lo marca")
            fallos += 1
    if not fallos:
        print("ok    cifras agregadas (reportantes, moderadoresActivos, cuentas...): no se marcan")

    if fallos:
        print(f"\n{fallos} caso(s) que el guardian no vio como debia.")
        return 1
    print("\nAutoprueba en verde: el guardian se pone rojo cuando debe.")
    return 0


def main(argumentos: list[str]) -> int:
    if "--autoprueba" in argumentos:
        return autoprueba()
    errores = comprobar(cargar_contratos(CONTRATOS), OPERACIONES)
    total = sum(len(ids) for ids in OPERACIONES.values())
    if errores:
        for error in errores:
            print(f"::error::{error}")
        print(f"\n{len(errores)} problema(s) en {total} estadistica(s).")
        return 1
    print(f"{total} estadisticas de {len(OPERACIONES)} contratos: ninguna identifica a una persona.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
