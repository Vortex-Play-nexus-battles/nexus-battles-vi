"""Eventos de combate armados a mano, en el formato del documento de SIM-003
(`EventoDeCombateDocumento`, mas `oponente` y la trazabilidad de SIM-008)."""

EJECUCION = "0c7a2d9e-4f8b-4a55-9b65-6f1d3b2f0a11"


def estado(vida=44, vida_maxima=44, poder=8, poder_maximo=12, recargas=(), efectos=()):
    return {"vida": vida, "vidaMaxima": vida_maxima, "poder": poder, "poderMaximo": poder_maximo,
            "recargas": [{"accion": a, "turnosRestantes": t} for a, t in recargas],
            "efectos": [{"nombre": n, "tipo": "DANO_POR_TURNO", "valor": 2, "turnos": 1} for n in efectos]}


def evento(secuencia=1, encuentro=1, turno=1, lado="HEROE", prototipo="Guerrero Armas", nivel=4,
           oponente_lado="ENEMIGO", oponente_prototipo="Mago Fuego", oponente_nivel=4,
           antes=None, despues=None, ejecutada="Embate sangriento", decidida=None, costo=4,
           dano=12, en_valor_base=False, candidatas=None, decidida_por="REGLA", ejecucion=EJECUCION,
           sin_jugada=False):
    antes = antes or {"actor": estado(), "oponente": estado(vida=40, vida_maxima=40, poder=10, poder_maximo=10)}
    despues = despues or {"actor": estado(poder=4), "oponente": estado(vida=28, vida_maxima=40, poder=10,
                                                                         poder_maximo=10)}
    doc = {
        "_id": f"{ejecucion}:{secuencia}", "ejecucionId": ejecucion, "misionId": "templo-olvidado",
        "secuencia": secuencia, "encuentro": encuentro, "enemigo": "Guardian", "turno": turno,
        "actor": {"lado": lado, "nombre": "Vorn", "prototipo": prototipo, "nivel": nivel},
        "oponente": {"lado": oponente_lado, "nombre": "Guardian", "prototipo": oponente_prototipo,
                     "nivel": oponente_nivel},
        "antes": antes, "alIniciar": [], "despues": despues, "registradoEn": "2026-10-01T10:00:00Z",
    }
    if not sin_jugada:
        jugada = {"decidida": decidida or ejecutada, "ejecutada": ejecutada, "enValorBase": en_valor_base,
                  "costoDecidido": costo, "costoDePoder": costo, "rechazadas": [],
                  "decididaPor": decidida_por,
                  "resultado": {"categoria": "CAUSAR_DANO", "acierta": True, "ataqueResuelto": 15,
                                "defensaObjetivo": 11, "porcentajeDano": 100, "danoBase": dano,
                                "danoAplicado": dano, "critico": False, "sucesos": []}}
        if candidatas is not None:
            jugada["candidatas"] = [{"accion": a, "costoDePoder": c} for a, c in candidatas]
        doc["jugada"] = jugada
    return doc
