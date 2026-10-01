"""Lee los eventos de combate (coleccion `eventos_de_combate`, HU-SIM-003) y los
convierte en muestras de entrenamiento: (situacion, candidatas, elegida, peso).

Un evento es un turno de un combatiente. El formato es el del documento
`EventoDeCombateDocumento` de misiones (ver el README de misiones); aqui se
leen solo los campos que el modelo necesita y se toleran los que falten.

Etiqueta: imitacion ponderada por resultado (reward-weighted imitation). La
muestra imita la accion que se ejecuto, y pesa mas si el bando del actor gano
ese encuentro y si la jugada quito una fraccion grande de la vida del rival:

    peso = (1 si su bando gano el encuentro, `peso_derrota` si no) * (1 + dano / vida maxima del rival)
"""
import json
from dataclasses import dataclass

from . import caracteristicas as c
from . import tabla7

PESO_DE_DERROTA = 0.25

CAMPOS_MINIMOS = ("ejecucionId", "secuencia", "encuentro", "turno", "actor", "antes")


# ---------------------------------------------------------------- lectura

def leer_jsonl(ruta):
    """Un documento por linea (lo que sale de `mongoexport` sin --jsonArray)."""
    with open(ruta, encoding="utf-8") as archivo:
        for numero, linea in enumerate(archivo, start=1):
            if not linea.strip():
                continue
            try:
                yield json.loads(linea)
            except json.JSONDecodeError as error:
                raise ValueError(f"{ruta}: la linea {numero} no es JSON ({error})") from error


def leer_coleccion(coleccion, mision=None):
    """Los documentos de una coleccion de MongoDB, ordenados por ejecucion y secuencia.

    `coleccion` es cualquier objeto con `find(filtro, proyeccion)`.
    """
    filtro = {} if mision is None else {"misionId": mision}
    documentos = list(coleccion.find(filtro, None))
    documentos.sort(key=lambda d: (str(d.get("ejecucionId")), d.get("secuencia", 0)))
    return documentos


def leer_mongo(uri, base=None, coleccion="eventos_de_combate", mision=None):
    """Conecta a MongoDB (solo lectura) y lee la coleccion de eventos."""
    from pymongo import MongoClient  # import tardio: --jsonl no lo necesita

    cliente = MongoClient(uri, serverSelectionTimeoutMS=10000)
    try:
        bd = cliente[base] if base else cliente.get_default_database()
        return leer_coleccion(bd[coleccion], mision)
    finally:
        cliente.close()


def es_valido(evento):
    return all(evento.get(campo) is not None for campo in CAMPOS_MINIMOS)


# ---------------------------------------------------------------- resultado de cada encuentro

def _bando(actor):
    return "HEROE" if actor.get("lado") == "HEROE" else "ENEMIGO"


def ganadores_por_encuentro(lista):
    """Por (ejecucion, encuentro): `HEROE`, `ENEMIGO` o None si nadie cayo (tope de rondas).

    Se decide con el ULTIMO turno del encuentro: gana el bando que sigue en pie.
    """
    ultimos = {}
    for e in lista:
        if not es_valido(e):
            continue
        clave = (e["ejecucionId"], e["encuentro"])
        if clave not in ultimos or e["secuencia"] > ultimos[clave]["secuencia"]:
            ultimos[clave] = e
    ganadores = {}
    for clave, e in ultimos.items():
        despues = e.get("despues") or e["antes"]
        actor_en_pie = despues["actor"]["vida"] > 0
        oponente_en_pie = despues["oponente"]["vida"] > 0
        bando_actor = _bando(e["actor"])
        otro = "ENEMIGO" if bando_actor == "HEROE" else "HEROE"
        if actor_en_pie and not oponente_en_pie:
            ganadores[clave] = bando_actor
        elif oponente_en_pie and not actor_en_pie:
            ganadores[clave] = otro
        else:
            ganadores[clave] = None
    return ganadores


# ---------------------------------------------------------------- muestras

@dataclass(frozen=True)
class Muestra:
    ejecucion: str
    secuencia: int
    situacion: c.Situacion
    candidatas: tuple          # ((accion, costo), ...)
    elegida: int               # indice en candidatas
    peso: float
    origen_candidatas: str     # "registradas" | "derivadas"


def normalizar_accion(nombre):
    """Para el modelo la sanacion basica es el ataque basico (la basica de un sanador)."""
    return tabla7.ATAQUE_BASICO if nombre == tabla7.SANACION_BASICA else nombre


def _situacion(e):
    antes = e["antes"]
    propio, otro = antes["actor"], antes["oponente"]
    oponente = e.get("oponente") or {}
    return c.Situacion(
        prototipo=e["actor"].get("prototipo"), nivel=e["actor"].get("nivel", 1), turno=e["turno"],
        vida=propio["vida"], vida_maxima=propio["vidaMaxima"],
        poder=propio["poder"], poder_maximo=propio["poderMaximo"], efectos=len(propio.get("efectos") or []),
        oponente_prototipo=oponente.get("prototipo"), oponente_nivel=oponente.get("nivel", 1),
        oponente_vida=otro["vida"], oponente_vida_maxima=otro["vidaMaxima"],
        oponente_poder=otro["poder"], oponente_poder_maximo=otro["poderMaximo"],
        oponente_efectos=len(otro.get("efectos") or []))


def _derivadas(e, s):
    """Lo que el actor podia jugar: lo desbloqueado en su nivel, con poder y fuera de recarga."""
    en_carga = {r["accion"] for r in (e["antes"]["actor"].get("recargas") or []) if r.get("turnosRestantes", 0) > 0}
    candidatas = [(tabla7.ATAQUE_BASICO, 0)]
    for nombre, _ in tabla7.acciones_desbloqueadas(s.prototipo, s.nivel):
        costo = tabla7.costo_de(s.prototipo, nombre, s.poder)
        if costo <= s.poder and nombre not in en_carga:
            candidatas.append((nombre, costo))
    return candidatas


def _peso(e, ganador, peso_derrota):
    gano = ganador is not None and ganador == _bando(e["actor"])
    resultado = (e["jugada"].get("resultado") or {})
    dano = resultado.get("danoAplicado") or 0
    vida_maxima = max(1, e["antes"]["oponente"]["vidaMaxima"])
    return (1.0 if gano else peso_derrota) * (1.0 + min(dano, vida_maxima) / vida_maxima)


def muestras(lista, peso_derrota=PESO_DE_DERROTA):
    """(muestras, descartes): descartes cuenta por motivo los turnos que no sirven."""
    lista = list(lista)
    ganadores = ganadores_por_encuentro(lista)
    resultado, descartes = [], {}

    def descartar(motivo):
        descartes[motivo] = descartes.get(motivo, 0) + 1

    for e in lista:
        if not es_valido(e):
            descartar("incompleto")
            continue
        jugada = e.get("jugada")
        if not jugada or not jugada.get("ejecutada"):
            descartar("sin_jugada")
            continue
        if jugada.get("enValorBase"):
            descartar("en_valor_base")
            continue
        elegida = normalizar_accion(jugada["ejecutada"])
        if elegida not in c.ACCIONES:
            descartar("accion_fuera_de_la_tabla_7")
            continue
        s = _situacion(e)
        registradas = jugada.get("candidatas")
        if registradas:
            candidatas = [(normalizar_accion(x["accion"]), x.get("costoDePoder", 0)) for x in registradas]
            origen = "registradas"
        else:
            candidatas = _derivadas(e, s)
            origen = "derivadas"
        nombres = [a for a, _ in candidatas]
        if elegida not in nombres:
            descartar("elegida_fuera_de_las_candidatas")
            continue
        peso = _peso(e, ganadores.get((e["ejecucionId"], e["encuentro"])), peso_derrota)
        resultado.append(Muestra(e["ejecucionId"], e["secuencia"], s, tuple(candidatas), nombres.index(elegida),
                                 peso, origen))
    return resultado, descartes
