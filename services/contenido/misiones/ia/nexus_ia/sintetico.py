"""Eventos de combate SINTETICOS, en el formato exacto de `eventos_de_combate`.

SOLO sirven de fixture de pruebas y para producir un modelo de PRUEBA. No son
partidas reales ni se parecen al balance del juego: un duelo muy simplificado
(dados y danos inventados) que respeta lo que el entrenamiento necesita —los
campos del documento, la Tabla 7 con sus costos, la recarga de un turno, el
poder que se recupera a razon de 2 por turno—. Un modelo entrenado con esto NO
es el modelo de produccion: ese se entrena con los eventos reales de DEV.

    python -m nexus_ia.sintetico --ejecuciones 40 --semilla 7 --salida eventos.jsonl
"""
import argparse
import json
import random
import uuid
from pathlib import Path

from . import tabla7

MISION = "sintetico-de-pruebas"
VERSION_DEL_MODELO_SINTETICO = "sintetico-0"
EXPLORACION = 0.35
RONDAS_MAXIMAS = 40
RECUPERACION_POR_TURNO = 2
ENEMIGOS_POR_EJECUCION = (2, 4)


class _Combatiente:
    def __init__(self, lado, nombre, prototipo, nivel, azar):
        self.lado, self.nombre, self.prototipo, self.nivel = lado, nombre, prototipo, nivel
        self.vida_maxima = 36 + 6 * nivel + azar.randint(0, 8)
        self.vida = self.vida_maxima
        self.poder_maximo = azar.choice((8, 10, 12))
        self.poder = self.poder_maximo
        self.ultimo_uso = {}            # accion -> ronda en que se uso
        self.efectos = []               # [(nombre, turnos restantes)]
        self.es_sanador = prototipo in tabla7.SANADORES

    def estado(self, ronda):
        recargas = [{"accion": a, "turnosRestantes": 1} for a, r in sorted(self.ultimo_uso.items()) if r == ronda - 1]
        efectos = [{"nombre": n, "tipo": "DANO_POR_TURNO", "valor": 2, "turnos": t} for n, t in self.efectos]
        return {"vida": self.vida, "vidaMaxima": self.vida_maxima, "poder": self.poder,
                "poderMaximo": self.poder_maximo, "recargas": recargas, "efectos": efectos}

    def actor(self):
        return {"lado": self.lado, "nombre": self.nombre, "prototipo": self.prototipo, "nivel": self.nivel}


def _viables(c, ronda):
    """Lo que la regla admite: desbloqueado, con poder y fuera de recarga (un turno)."""
    viables = []
    for nombre, _ in tabla7.acciones_desbloqueadas(c.prototipo, c.nivel):
        costo = tabla7.costo_de(c.prototipo, nombre, c.poder)
        if costo <= c.poder and c.ultimo_uso.get(nombre) != ronda - 1:
            viables.append((nombre, costo))
    return viables


def _sin_nulos(valor):
    """Spring Data no escribe los campos nulos: tampoco aqui."""
    if isinstance(valor, dict):
        return {k: _sin_nulos(v) for k, v in valor.items() if v is not None}
    if isinstance(valor, list):
        return [_sin_nulos(v) for v in valor]
    return valor


def _jugar(actor, otro, ronda, azar, con_candidatas):
    """Devuelve (jugada, sucesos). La regla elige la accion mas avanzada viable; a veces se explora."""
    basica = tabla7.SANACION_BASICA if actor.es_sanador else tabla7.ATAQUE_BASICO
    por_prioridad = list(reversed(_viables(actor, ronda)))      # la mas avanzada primero (como los enemigos)
    legales = por_prioridad + [(tabla7.ATAQUE_BASICO, 0)]
    regla = legales[0]
    explorando = con_candidatas and azar.random() < EXPLORACION
    elegida = azar.choice(legales) if explorando else regla
    nombre, costo = elegida
    decididor = "MODELO" if explorando else "REGLA"

    dano = 0
    sanacion = 0
    if nombre == tabla7.ATAQUE_BASICO:
        ejecutada = basica
        if actor.es_sanador:
            sanacion = azar.randint(1, 4)
        else:
            dano = azar.randint(2, 6)
    else:
        ejecutada = nombre
        actor.poder -= costo
        actor.ultimo_uso[nombre] = ronda
        if actor.es_sanador:
            sanacion = azar.randint(2, 6) + costo
        else:
            dano = azar.randint(3, 8) + 2 * costo
    sucesos = []
    if dano:
        otro.vida = max(0, otro.vida - dano)
        sucesos.append({"tipo": "DANO", "combatiente": otro.lado,
                        "origen": actor.lado, "efecto": ejecutada, "cantidad": dano})
    if sanacion:
        actor.vida = min(actor.vida_maxima, actor.vida + sanacion)
        sucesos.append({"tipo": "SANACION", "combatiente": actor.lado, "origen": actor.lado,
                        "efecto": ejecutada, "cantidad": sanacion})

    resultado = {"sucesos": sucesos, "critico": False}
    if dano:
        resultado.update({"categoria": "CAUSAR_DANO", "acierta": True, "ataqueResuelto": 10 + dano,
                          "defensaObjetivo": 11, "porcentajeDano": 100, "danoBase": dano, "danoAplicado": dano})
    jugada = {"decidida": tabla7.ATAQUE_BASICO if nombre == tabla7.ATAQUE_BASICO else nombre,
              "ejecutada": ejecutada, "enValorBase": False, "costoDecidido": costo, "costoDePoder": costo,
              "rechazadas": [], "resultado": resultado, "decididaPor": decididor}
    if con_candidatas:
        jugada["versionDelModelo"] = VERSION_DEL_MODELO_SINTETICO
        jugada["candidatas"] = [
            {"accion": a, "costoDePoder": k, "rotacion": None if a == tabla7.ATAQUE_BASICO else i + 1,
             "puntaje": round(1.0 / (1 + i), 4)}
            for i, (a, k) in enumerate(legales)]
    return jugada, sucesos


def _ejecucion(indice, semilla, mision=MISION):
    azar = random.Random(f"{semilla}:{indice}")
    ejecucion = str(uuid.UUID(int=azar.getrandbits(128), version=4))
    con_candidatas = indice % 2 == 1
    nivel = azar.randint(1, 8)
    heroe = _Combatiente("HEROE", "Heroe", azar.choice(tabla7.PROTOTIPOS), nivel, azar)
    eventos, secuencia = [], 0
    cantidad = azar.randint(*ENEMIGOS_POR_EJECUCION)
    for encuentro in range(1, cantidad + 1):
        lado = "JEFE" if encuentro == cantidad else "ENEMIGO"
        prototipo = azar.choice([p for p in tabla7.PROTOTIPOS if p not in tabla7.SANADORES])
        enemigo = _Combatiente(lado, f"Enemigo {encuentro}", prototipo, nivel, azar)
        heroe.poder, heroe.ultimo_uso, heroe.efectos = heroe.poder_maximo, {}, []
        orden = [heroe, enemigo] if azar.random() < 0.5 else [enemigo, heroe]
        for ronda in range(1, RONDAS_MAXIMAS + 1):
            if heroe.vida <= 0 or enemigo.vida <= 0:
                break
            for actor in orden:
                otro = enemigo if actor is heroe else heroe
                if heroe.vida <= 0 or enemigo.vida <= 0:
                    break
                recuperado = min(RECUPERACION_POR_TURNO, actor.poder_maximo - actor.poder)
                actor.poder += recuperado
                al_iniciar = ([{"tipo": "PODER_RECUPERADO", "combatiente": actor.lado, "cantidad": recuperado}]
                              if recuperado else [])
                antes = {"actor": actor.estado(ronda), "oponente": otro.estado(ronda)}
                jugada, _ = _jugar(actor, otro, ronda, azar, con_candidatas)
                despues = {"actor": actor.estado(ronda), "oponente": otro.estado(ronda)}
                secuencia += 1
                eventos.append(_sin_nulos({
                    "_id": f"{ejecucion}:{secuencia}", "ejecucionId": ejecucion, "misionId": mision,
                    "secuencia": secuencia, "encuentro": encuentro, "enemigo": enemigo.nombre, "turno": ronda,
                    "actor": actor.actor(), "oponente": otro.actor(), "antes": antes, "alIniciar": al_iniciar,
                    "jugada": jugada, "despues": despues, "registradoEn": "2026-10-01T10:00:00Z"}))
        if heroe.vida <= 0:
            break
    return eventos


def generar(ejecuciones=40, semilla=7, mision=MISION):
    """Los eventos de `ejecuciones` misiones simuladas, en orden de ejecucion y secuencia.

    Las ejecuciones de indice impar simulan una IA con modelo: registran las candidatas, la
    version del modelo y a veces exploran una opcion que la regla no habria elegido.
    """
    lista = []
    for i in range(ejecuciones):
        lista.extend(_ejecucion(i, semilla, mision))
    return lista


def escribir_jsonl(lista, ruta):
    with open(ruta, "w", encoding="utf-8", newline="\n") as archivo:
        for e in lista:
            archivo.write(json.dumps(e, ensure_ascii=False, sort_keys=True) + "\n")


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    p.add_argument("--ejecuciones", type=int, default=40)
    p.add_argument("--semilla", type=int, default=7)
    p.add_argument("--salida", type=Path, required=True)
    args = p.parse_args(argv)
    lista = generar(args.ejecuciones, args.semilla)
    escribir_jsonl(lista, args.salida)
    print(f"{len(lista)} eventos sinteticos de {args.ejecuciones} ejecuciones en {args.salida}")


if __name__ == "__main__":
    main()
