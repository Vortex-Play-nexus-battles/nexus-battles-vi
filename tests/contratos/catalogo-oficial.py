#!/usr/bin/env python3
"""
Guardian: el catalogo que siembra el codigo es EXACTAMENTE el del documento —
BACKEND-06 (B4).

## Por que hace falta

El catalogo del juego vive repartido en tres sitios que nadie comparaba:

  * la semilla de productos (`catalogo-inicial.json`): heroes, armas,
    armaduras, items y epicas como productos del catalogo;
  * `PrototiposIniciales.java` (heroes): los ocho prototipos con sus
    estadisticas de la Tabla 6 y sus tres acciones de la Tabla 7;
  * `EpicasIniciales.java` (heroes): las ocho epicas de la Tabla 20.

Un nombre con una tilde de menos ("Escudo de dragon"), una estadistica
cambiada o un producto inventado ("Espada corta") pasaban todas las pruebas:
cada servicio se comparaba consigo mismo. Este guardian compara los tres contra
la lista canonica `contracts/esquemas/catalogo-oficial.yaml`, que es copia del
documento del cliente, y falla si algo **sobra, falta, se llama distinto o
tiene otra estadistica**.

Tambien mira `CatalogoEfectosEquipamiento.java` (inventario), que busca el
efecto de cada objeto por NOMBRE: un nombre que no esta en el catalogo oficial
es un efecto que nunca se aplica.

## Que compara

  * heroes: nombres; sanador; poder, vida, defensa y las formulas de ataque,
    dano y sanar (Tabla 6) — en la semilla y en PrototiposIniciales.
  * acciones: nombre, heroe al que pertenecen y costo (Tabla 7) — en
    PrototiposIniciales. No son productos: la semilla no debe traerlas.
  * armas, armaduras, items: nombre, heroe y probabilidad de caida; en las
    armaduras, ademas, la parte del cuerpo (Tablas 8-19) — en la semilla.
  * epicas: nombre, heroe afin y probabilidad de Master (Tabla 20) — en la
    semilla y en EpicasIniciales.
  * epicasdemision: las epicas que el documento define dentro de una mision
    (7.8.14, «Velo de Sombras»): la semilla de productos las exige junto a las
    de la Tabla 20; EpicasIniciales (heroes) NO las admite, porque son solo de
    la Tabla 20.

Sin Docker ni compilacion: lee el Java como texto con un analizador minimo de
llamadas `new X(...)`, en decimas de segundo.

Uso:
    python3 tests/contratos/catalogo-oficial.py              # compara el repo
    python3 tests/contratos/catalogo-oficial.py --autoprueba # demuestra que se pone rojo
"""

from __future__ import annotations

import copy
import json
import pathlib
import re
import sys
import tempfile
import unicodedata

import yaml

CANONICO = pathlib.Path("contracts/esquemas/catalogo-oficial.yaml")
SEMILLA = pathlib.Path("services/contenido/productos/src/main/resources/semilla/catalogo-inicial.json")
PROTOTIPOS = pathlib.Path("services/contenido/heroes/src/main/java/nexus/dominio/PrototiposIniciales.java")
EPICAS = pathlib.Path("services/contenido/heroes/src/main/java/nexus/dominio/EpicasIniciales.java")
EFECTOS = pathlib.Path(
    "services/contenido/inventario/src/main/java/nexus/inventario/aplicacion/CatalogoEfectosEquipamiento.java")

# Listas de la semilla que son productos, y como se llama cada una en el canonico.
LISTAS_DE_LA_SEMILLA = ("heroes", "armas", "armaduras", "items", "epicas")


# ---------------------------------------------------------------- utilidades

def sin_tildes(texto: str) -> str:
    """Mayusculas sin marcas diacriticas: 'Pantalón' -> 'PANTALON'."""
    return "".join(c for c in unicodedata.normalize("NFD", texto) if unicodedata.category(c) != "Mn").upper()


def porcentaje(texto: object) -> float | None:
    """'3%' -> 3.0; '0.04%' -> 0.04; None si no es un porcentaje."""
    if texto is None:
        return None
    coincidencia = re.fullmatch(r"\s*(\d+(?:[.,]\d+)?)\s*%\s*", str(texto))
    return float(coincidencia.group(1).replace(",", ".")) if coincidencia else None


def numero(valor: object) -> float | None:
    return None if valor is None else float(valor)


def formula(base: int, dados: int, caras: int) -> str:
    """El texto de la Tabla 6, igual que Formula.texto() en heroes."""
    if dados == 0:
        return str(base)
    tirada = f"{dados}d{caras}"
    return tirada if base == 0 else f"{base} + {tirada}"


def estadistica(valor: object) -> object:
    """Normaliza un valor de estadistica de la semilla: '-' es 'no aplica'."""
    if valor is None:
        return None
    texto = str(valor).strip()
    if texto in ("", "-"):
        return None
    return int(texto) if texto.isdigit() else texto


# ------------------------------------------------ analizador minimo de Java

TOKEN = re.compile(
    r'"(?:\\.|[^"\\])*"'          # literal de texto
    r"|\d+(?:\.\d+)?[dDfFlL]?"      # numero
    r"|[A-Za-z_][A-Za-z0-9_]*"      # identificador
    r"|[()\[\]{},.;=<>+\-*/]"       # puntuacion
)


def quitar_comentarios(texto: str) -> str:
    """Quita // y /* */ sin tocar lo que va dentro de literales de texto."""
    salida = []
    i = 0
    en_texto = False
    while i < len(texto):
        c = texto[i]
        if en_texto:
            salida.append(c)
            if c == "\\" and i + 1 < len(texto):
                salida.append(texto[i + 1])
                i += 2
                continue
            if c == '"':
                en_texto = False
            i += 1
            continue
        if c == '"':
            en_texto = True
            salida.append(c)
            i += 1
        elif texto.startswith("//", i):
            fin = texto.find("\n", i)
            i = len(texto) if fin == -1 else fin
        elif texto.startswith("/*", i):
            fin = texto.find("*/", i + 2)
            i = len(texto) if fin == -1 else fin + 2
        else:
            salida.append(c)
            i += 1
    return "".join(salida)


def tokens(texto: str) -> list[str]:
    return TOKEN.findall(quitar_comentarios(texto))


class Llamada:
    """Una llamada `new X(...)`, `A.b(...)` o `f(...)` con sus argumentos ya analizados."""

    def __init__(self, nombre: str, argumentos: list):
        self.nombre = nombre
        self.argumentos = argumentos

    def __repr__(self) -> str:  # pragma: no cover - solo para depurar
        return f"{self.nombre}{self.argumentos}"


def analizar_expresion(fichas: list[str], i: int) -> tuple[object, int]:
    """Analiza una expresion desde fichas[i]; devuelve (valor, siguiente indice)."""
    ficha = fichas[i]
    if ficha.startswith('"'):
        # Varias literales seguidas con '+' se concatenan ("a" + "b").
        valor = json.loads(ficha)
        i += 1
        while i + 1 < len(fichas) and fichas[i] == "+" and fichas[i + 1].startswith('"'):
            valor += json.loads(fichas[i + 1])
            i += 2
        return valor, i
    if ficha == "-" and i + 1 < len(fichas) and re.match(r"\d", fichas[i + 1]):
        valor, i = analizar_expresion(fichas, i + 1)
        return -valor, i
    if re.match(r"\d", ficha):
        limpio = ficha.rstrip("dDfFlL")
        return (float(limpio) if "." in limpio else int(limpio)), i + 1
    if ficha in ("null", "true", "false"):
        return {"null": None, "true": True, "false": False}[ficha], i + 1
    if ficha == "new":
        nombre = fichas[i + 1]
        return analizar_argumentos(fichas, i + 2, nombre)
    if re.match(r"[A-Za-z_]", ficha):
        # Nombre calificado (List.of, Map.entry) o llamada simple (mod(...)).
        nombre = ficha
        i += 1
        while i + 1 < len(fichas) and fichas[i] == "." and re.match(r"[A-Za-z_]", fichas[i + 1]):
            nombre += "." + fichas[i + 1]
            i += 2
        if i < len(fichas) and fichas[i] == "(":
            return analizar_argumentos(fichas, i, nombre)
        return nombre, i
    raise ValueError(f"expresion inesperada en «{ficha}»")


def analizar_argumentos(fichas: list[str], i: int, nombre: str) -> tuple[Llamada, int]:
    if fichas[i] != "(":
        raise ValueError(f"se esperaba '(' despues de {nombre}")
    i += 1
    argumentos = []
    while fichas[i] != ")":
        valor, i = analizar_expresion(fichas, i)
        argumentos.append(valor)
        if fichas[i] == ",":
            i += 1
    return Llamada(nombre, argumentos), i + 1


def llamadas(texto: str, nombre: str) -> list[Llamada]:
    """Todas las llamadas `nombre(...)` del texto, analizadas."""
    fichas = tokens(texto)
    encontradas = []
    for i, ficha in enumerate(fichas):
        cabeza = ficha
        j = i + 1
        while j + 1 < len(fichas) and fichas[j] == "." and re.match(r"[A-Za-z_]", fichas[j + 1]):
            cabeza += "." + fichas[j + 1]
            j += 2
        if cabeza == nombre and j < len(fichas) and fichas[j] == "(" and (i == 0 or fichas[i - 1] != "."):
            llamada, _ = analizar_argumentos(fichas, j, nombre)
            encontradas.append(llamada)
    return encontradas


# ------------------------------------------------------ lectura de fuentes

def leer_prototipos(ruta: pathlib.Path) -> tuple[dict[str, dict], list[dict]]:
    """({nombre: {sanador, estadisticas}}, [acciones]) de PrototiposIniciales.java."""
    heroes: dict[str, dict] = {}
    acciones: list[dict] = []
    for prototipo in llamadas(ruta.read_text(encoding="utf-8"), "Prototipo"):
        nombre, _clase, _descripcion, sanador, stats, lista = prototipo.argumentos
        poder, vida, defensa, ataque, dano, sanar = stats.argumentos

        def texto(f):
            return None if f is None else formula(*f.argumentos)

        heroes.setdefault(nombre, {"repeticiones": 0})
        heroes[nombre].update({
            "sanador": sanador,
            "estadisticas": {"poder": poder, "vida": vida, "defensa": defensa,
                             "ataque": texto(ataque), "dano": texto(dano), "sanar": texto(sanar)},
        })
        heroes[nombre]["repeticiones"] += 1
        for accion in lista.argumentos:
            acciones.append({"nombre": accion.argumentos[0], "heroe": nombre, "costo": accion.argumentos[1]})
    return heroes, acciones


def leer_epicas_java(ruta: pathlib.Path) -> list[dict]:
    return [{"nombre": e.argumentos[0], "heroe": e.argumentos[1], "probabilidadMaster": numero(e.argumentos[4])}
            for e in llamadas(ruta.read_text(encoding="utf-8"), "Epica")]


def leer_nombres_de_efectos(ruta: pathlib.Path) -> list[str]:
    return [e.argumentos[0] for e in llamadas(ruta.read_text(encoding="utf-8"), "Map.entry")]


# ------------------------------------------------------------ comparacion

class Informe:
    def __init__(self):
        self.errores: list[tuple[str, str]] = []
        self.resumen: list[str] = []

    def error(self, archivo: pathlib.Path, mensaje: str) -> None:
        self.errores.append((str(archivo).replace("\\", "/"), mensaje))


def comparar_nombres(informe: Informe, archivo: pathlib.Path, tipo: str,
                     oficiales: list[str], encontrados: list[str]) -> set[str]:
    """Sobra, falta y repetidos; devuelve los nombres que estan en los dos lados."""
    for nombre in sorted({n for n in encontrados if encontrados.count(n) > 1}):
        informe.error(archivo, f"{tipo}: «{nombre}» esta repetido")
    faltan = [n for n in oficiales if n not in encontrados]
    sobran = sorted(set(encontrados) - set(oficiales))
    for nombre in faltan:
        parecido = [s for s in sobran if sin_tildes(s) == sin_tildes(nombre)]
        pista = f" (hay «{parecido[0]}»: el nombre oficial es «{nombre}»)" if parecido else ""
        informe.error(archivo, f"{tipo}: falta «{nombre}»{pista}")
    for nombre in sobran:
        informe.error(archivo, f"{tipo}: sobra «{nombre}», que no esta en el documento")
    return set(oficiales) & set(encontrados)


def comparar_campo(informe: Informe, archivo: pathlib.Path, tipo: str, nombre: str,
                   campo: str, oficial: object, encontrado: object) -> None:
    if oficial != encontrado:
        informe.error(archivo, f"{tipo} «{nombre}»: {campo} es {encontrado!r} y el documento dice {oficial!r}")


def comparar_semilla(informe: Informe, canonico: dict, ruta: pathlib.Path) -> None:
    semilla = json.loads(ruta.read_text(encoding="utf-8"))
    for clave, valor in semilla.items():
        if isinstance(valor, list) and clave not in LISTAS_DE_LA_SEMILLA:
            informe.error(ruta, f"la semilla trae la lista «{clave}», que no es un tipo de producto del catalogo oficial")

    por_nombre = {tipo: {e.get("nombre"): e for e in semilla.get(tipo) or []} for tipo in LISTAS_DE_LA_SEMILLA}

    # Heroes: nombre, prototipo y estadisticas de nivel 1.
    oficiales = {h["nombre"]: h for h in canonico["heroes"]}
    comunes = comparar_nombres(informe, ruta, "heroe", list(oficiales),
                               [e.get("nombre") for e in semilla.get("heroes") or []])
    for nombre in sorted(comunes):
        entrada = por_nombre["heroes"][nombre]
        comparar_campo(informe, ruta, "heroe", nombre, "prototipo", nombre, entrada.get("prototipo"))
        leidas = entrada.get("estadisticasNivel1") or {}
        for campo, oficial in oficiales[nombre]["estadisticas"].items():
            clave = "daño" if campo == "dano" else campo
            comparar_campo(informe, ruta, "heroe", nombre, campo, oficial, estadistica(leidas.get(clave)))

    # Armas, armaduras e items: nombre, heroe, caida (y parte en armaduras).
    for tipo, singular in (("armas", "arma"), ("armaduras", "armadura"), ("items", "item")):
        oficiales = {o["nombre"]: o for o in canonico[tipo]}
        comunes = comparar_nombres(informe, ruta, singular, list(oficiales),
                                   [e.get("nombre") for e in semilla.get(tipo) or []])
        for nombre in sorted(comunes):
            entrada, oficial = por_nombre[tipo][nombre], oficiales[nombre]
            comparar_campo(informe, ruta, singular, nombre, "heroe", oficial["heroe"], entrada.get("heroe"))
            comparar_campo(informe, ruta, singular, nombre, "probabilidadCaida",
                           numero(oficial["probabilidadCaida"]), porcentaje(entrada.get("probabilidadCaida")))
            if tipo == "armaduras":
                comparar_campo(informe, ruta, singular, nombre, "parte",
                               sin_tildes(oficial["parte"]), sin_tildes(str(entrada.get("parte"))))

    # Epicas: nombre, heroe afin y probabilidad de Master. Las de la Tabla 20 y
    # las que el documento define dentro de una mision (7.8.14) son productos EPICA.
    oficiales = {e["nombre"]: e for e in canonico["epicas"] + (canonico.get("epicasdemision") or [])}
    comunes = comparar_nombres(informe, ruta, "epica", list(oficiales),
                               [e.get("nombre") for e in semilla.get("epicas") or []])
    for nombre in sorted(comunes):
        entrada, oficial = por_nombre["epicas"][nombre], oficiales[nombre]
        comparar_campo(informe, ruta, "epica", nombre, "heroe", oficial["heroe"], entrada.get("heroe"))
        comparar_campo(informe, ruta, "epica", nombre, "probabilidadMaster",
                       numero(oficial["probabilidadMaster"]), porcentaje(entrada.get("probabilidadMaster")))

    informe.resumen.append(
        f"semilla de productos: {sum(len(semilla.get(t) or []) for t in LISTAS_DE_LA_SEMILLA)} productos "
        f"({', '.join(f'{len(semilla.get(t) or [])} {t}' for t in LISTAS_DE_LA_SEMILLA)})")


def comparar_prototipos(informe: Informe, canonico: dict, ruta: pathlib.Path) -> None:
    heroes, acciones = leer_prototipos(ruta)
    oficiales = {h["nombre"]: h for h in canonico["heroes"]}
    nombres = [n for n, h in heroes.items() for _ in range(h["repeticiones"])]
    comunes = comparar_nombres(informe, ruta, "prototipo", list(oficiales), nombres)
    for nombre in sorted(comunes):
        comparar_campo(informe, ruta, "prototipo", nombre, "sanador",
                       oficiales[nombre]["sanador"], heroes[nombre]["sanador"])
        for campo, oficial in oficiales[nombre]["estadisticas"].items():
            comparar_campo(informe, ruta, "prototipo", nombre, campo, oficial, heroes[nombre]["estadisticas"][campo])

    oficiales_acc = {a["nombre"]: a for a in canonico["acciones"]}
    comunes = comparar_nombres(informe, ruta, "accion", list(oficiales_acc), [a["nombre"] for a in acciones])
    leidas = {a["nombre"]: a for a in acciones}
    for nombre in sorted(comunes):
        comparar_campo(informe, ruta, "accion", nombre, "heroe", oficiales_acc[nombre]["heroe"], leidas[nombre]["heroe"])
        comparar_campo(informe, ruta, "accion", nombre, "costo", oficiales_acc[nombre]["costo"], leidas[nombre]["costo"])
    informe.resumen.append(f"PrototiposIniciales: {len(heroes)} prototipos, {len(acciones)} acciones")


def comparar_epicas(informe: Informe, canonico: dict, ruta: pathlib.Path) -> None:
    epicas = leer_epicas_java(ruta)
    oficiales = {e["nombre"]: e for e in canonico["epicas"]}
    comunes = comparar_nombres(informe, ruta, "epica", list(oficiales), [e["nombre"] for e in epicas])
    leidas = {e["nombre"]: e for e in epicas}
    for nombre in sorted(comunes):
        comparar_campo(informe, ruta, "epica", nombre, "heroe", oficiales[nombre]["heroe"], leidas[nombre]["heroe"])
        comparar_campo(informe, ruta, "epica", nombre, "probabilidadMaster",
                       numero(oficiales[nombre]["probabilidadMaster"]), leidas[nombre]["probabilidadMaster"])
    informe.resumen.append(f"EpicasIniciales: {len(epicas)} epicas")


def comparar_efectos(informe: Informe, canonico: dict, ruta: pathlib.Path) -> None:
    """Cada nombre con efecto tiene que ser un objeto oficial; no todos necesitan efecto."""
    oficiales = {o["nombre"] for tipo in ("armas", "armaduras", "items") for o in canonico[tipo]}
    nombres = leer_nombres_de_efectos(ruta)
    for nombre in nombres:
        if nombre not in oficiales:
            informe.error(ruta, f"efecto de «{nombre}», que no es un arma, armadura ni item del documento: "
                                f"con ese nombre el efecto nunca se aplica")
    informe.resumen.append(f"CatalogoEfectosEquipamiento: {len(nombres)} objetos con efecto")


def comprobar(canonico_ruta: pathlib.Path, semilla: pathlib.Path, prototipos: pathlib.Path,
              epicas: pathlib.Path, efectos: pathlib.Path | None) -> Informe:
    informe = Informe()
    canonico = yaml.safe_load(canonico_ruta.read_text(encoding="utf-8"))
    # tests/e2e/contrato-del-profesor.smoke.spec.js cuenta los nombres de cada seccion leyendo
    # este archivo linea a linea con /^([a-z]+):/. Una clave con mayusculas o guiones no cierra
    # la seccion anterior y sus entradas se sumarian a ella (la epica de mision, a las de la Tabla 20).
    for clave in canonico:
        if not re.fullmatch(r"[a-z]+", str(clave)):
            informe.error(canonico_ruta, f"la clave «{clave}» no es solo minusculas: el spec E2E del profesor "
                                         f"la leeria como parte de la seccion anterior")
    for fuente, comparador in ((semilla, comparar_semilla), (prototipos, comparar_prototipos),
                               (epicas, comparar_epicas), (efectos, comparar_efectos)):
        if fuente is None:
            continue
        if not fuente.exists():
            informe.error(fuente, "no existe")
            continue
        try:
            comparador(informe, canonico, fuente)
        except (ValueError, KeyError, IndexError, TypeError, json.JSONDecodeError) as fallo:
            informe.error(fuente, f"no se pudo leer: {fallo}")
    return informe


# ------------------------------------------------------------- autoprueba

def autoprueba() -> int:
    """Rompe a proposito copias del catalogo y exige que el guardian lo vea.

    Es la prueba roja del guardian: si alguno de estos casos pasara en verde,
    el guardian no estaria mirando lo que dice mirar.
    """
    real = comprobar(CANONICO, SEMILLA, PROTOTIPOS, EPICAS, EFECTOS)
    fallos = 0
    if real.errores:
        print("::error::la autoprueba necesita el catalogo real en verde, y no lo esta:")
        for archivo, mensaje in real.errores:
            print(f"::error file={archivo}::{mensaje}")
        return 1
    print("ok    catalogo real: en verde")

    semilla = json.loads(SEMILLA.read_text(encoding="utf-8"))
    prototipos = PROTOTIPOS.read_text(encoding="utf-8")
    epicas = EPICAS.read_text(encoding="utf-8")
    canonico = CANONICO.read_text(encoding="utf-8")

    def semilla_con(cambio):
        copia = copy.deepcopy(semilla)
        cambio(copia)
        return json.dumps(copia, ensure_ascii=False)

    casos = [
        ("falta un item (Benditas)", "semilla",
         semilla_con(lambda s: s.__setitem__("items", [i for i in s["items"] if i["nombre"] != "Benditas"])),
         "falta «Benditas»"),
        ("sobra un arma inventada (Espada corta)", "semilla",
         semilla_con(lambda s: s["armas"].append(dict(s["armas"][0], nombre="Espada corta"))),
         "sobra «Espada corta»"),
        ("nombre sin tilde (Escudo de dragon)", "semilla",
         semilla_con(lambda s: [a.__setitem__("nombre", "Escudo de dragon")
                                for a in s["armas"] if a["nombre"] == "Escudo de dragón"]),
         "falta «Escudo de dragón» (hay «Escudo de dragon»"),
        ("estadistica distinta en la semilla (vida del Guerrero Tanque)", "semilla",
         semilla_con(lambda s: s["heroes"][0]["estadisticasNivel1"].__setitem__("vida", "45")),
         "vida es 45 y el documento dice 44"),
        ("parte de armadura cambiada (Ventisca en Casco)", "semilla",
         semilla_con(lambda s: [a.__setitem__("parte", "Casco")
                                for a in s["armaduras"] if a["nombre"] == "Ventisca"]),
         "«Ventisca»: parte"),
        ("las acciones coladas como productos", "semilla",
         semilla_con(lambda s: s.__setitem__("habilidades", [{"nombre": "Vulcano"}])),
         "la lista «habilidades»"),
        ("estadistica distinta en PrototiposIniciales (poder del Mago Fuego)", "prototipos",
         prototipos.replace("new Estadisticas(8, 40, 10, new Formula(10, 1, 8), new Formula(0, 1, 8), null)",
                            "new Estadisticas(9, 40, 10, new Formula(10, 1, 8), new Formula(0, 1, 8), null)"),
         "«Mago Fuego»: poder es 9 y el documento dice 8"),
        # «Flor de loto» (Picaro Veneno) y «Cortada» (Picaro Machete) cuestan lo
        # mismo: intercambiarlas solo se ve si se mira a que heroe pertenecen.
        ("accion en el heroe equivocado (Cortada con el Picaro Veneno)", "prototipos",
         prototipos.replace('new Accion("Flor de loto"', 'new Accion("__intercambio__"')
         .replace('new Accion("Cortada"', 'new Accion("Flor de loto"')
         .replace('new Accion("__intercambio__"', 'new Accion("Cortada"'),
         "accion «Cortada»: heroe es 'Pícaro Veneno'"),
        ("epica renombrada (Frío concentrado)", "epicas",
         epicas.replace('"Frio concentrado"', '"Frío concentrado"'),
         "falta «Frio concentrado»"),
        ("epica con otra probabilidad", "epicas",
         epicas.replace('"Té changua", "Chamán", null, "Sana a todos +(4d8)", 0.1',
                        '"Té changua", "Chamán", null, "Sana a todos +(4d8)", 0.2'),
         "probabilidadMaster es 0.2"),
        # 7.8.14: la epica de la mision es producto del catalogo, no de la Tabla 20.
        ("falta la epica de la mision (Velo de Sombras) en la semilla", "semilla",
         semilla_con(lambda s: s.__setitem__("epicas", [e for e in s["epicas"] if e["nombre"] != "Velo de Sombras"])),
         "falta «Velo de Sombras»"),
        ("epica de la mision con otro heroe (Velo de Sombras del Mago Fuego)", "semilla",
         semilla_con(lambda s: [e.__setitem__("heroe", "Mago Fuego")
                                for e in s["epicas"] if e["nombre"] == "Velo de Sombras"]),
         "«Velo de Sombras»: heroe es 'Mago Fuego'"),
        ("epica de la mision con otra probabilidad (Velo de Sombras al 0.15 %)", "semilla",
         semilla_con(lambda s: [e.__setitem__("probabilidadMaster", "0.15%")
                                for e in s["epicas"] if e["nombre"] == "Velo de Sombras"]),
         "«Velo de Sombras»: probabilidadMaster"),
        ("la epica de la mision colada entre las de la Tabla 20 (heroes)", "epicas",
         epicas.replace('new Epica("Reanimador 3000"',
                        'new Epica("Velo de Sombras", "Pícaro Veneno", "+2 a la defensa", "Intangible", 15),\n'
                        '            new Epica("Reanimador 3000"'),
         "sobra «Velo de Sombras»"),
        # tests/e2e/contrato-del-profesor.smoke.spec.js lee este archivo con /^([a-z]+):/: una
        # clave con mayusculas no cierra la seccion anterior y sus entradas se cuentan en ella.
        ("una clave del canonico con mayusculas (epicasDeMision)", "canonico",
         canonico.replace("\nepicasdemision:\n", "\nepicasDeMision:\n"),
         "la clave «epicasDeMision»"),
    ]

    with tempfile.TemporaryDirectory() as carpeta:
        base = pathlib.Path(carpeta)
        for titulo, fuente, contenido, esperado in casos:
            rutas = {"semilla": SEMILLA, "prototipos": PROTOTIPOS, "epicas": EPICAS, "canonico": CANONICO}
            rota = base / f"{fuente}{pathlib.Path(rutas[fuente]).suffix}"
            rota.write_text(contenido, encoding="utf-8")
            rutas[fuente] = rota
            informe = comprobar(rutas["canonico"], rutas["semilla"], rutas["prototipos"], rutas["epicas"], None)
            mensajes = [m for _, m in informe.errores]
            if informe.errores and any(esperado in m for m in mensajes):
                print(f"ok    rojo como debe: {titulo}")
            else:
                print(f"::error::el guardian NO detecto: {titulo} (esperaba «{esperado}», obtuvo {mensajes})")
                fallos += 1
            if contenido == {"semilla": json.dumps(semilla, ensure_ascii=False),
                             "prototipos": prototipos, "epicas": epicas, "canonico": canonico}[fuente]:
                print(f"::error::el caso «{titulo}» no cambio nada: la autoprueba esta mal escrita")
                fallos += 1

    if fallos:
        print(f"\n{fallos} caso(s) rojo(s) que el guardian dejo pasar.")
        return 1
    print(f"\nAutoprueba en verde: {len(casos)} desviaciones detectadas y el catalogo real limpio.")
    return 0


def main(argumentos: list[str]) -> int:
    if "--autoprueba" in argumentos:
        return autoprueba()

    informe = comprobar(CANONICO, SEMILLA, PROTOTIPOS, EPICAS, EFECTOS)
    for linea in informe.resumen:
        print(f"leido {linea}")
    if informe.errores:
        for archivo, mensaje in informe.errores:
            print(f"::error file={archivo}::{mensaje}")
        print(f"\n{len(informe.errores)} diferencia(s) con el catalogo oficial ({CANONICO}).")
        print("Corrige el codigo, no el canonico: el canonico es copia del documento del cliente.")
        return 1

    canonico = yaml.safe_load(CANONICO.read_text(encoding="utf-8"))
    print(f"\nEl catalogo del codigo coincide con el documento (catalogo oficial v{canonico['version']}): "
          f"{len(canonico['heroes'])} heroes, {len(canonico['acciones'])} acciones, "
          f"{len(canonico['armas'])} armas, {len(canonico['armaduras'])} armaduras, "
          f"{len(canonico['items'])} items, {len(canonico['epicas'])} epicas de la Tabla 20 y "
          f"{len(canonico.get('epicasdemision') or [])} de mision.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
