"""Tabla 7 del documento (6.1.2): los ocho prototipos y sus tres acciones.

Los nombres son los de heroes (`PrototiposIniciales.java`). El costo es en
puntos de poder; `None` es «todos los puntos de poder» (Reanimacion). El orden
de las acciones es el del desbloqueo (RC-01: niveles 1, 4 y 8).
"""

NIVELES_DE_DESBLOQUEO = (1, 4, 8)

ATAQUE_BASICO = "Ataque básico"
SANACION_BASICA = "Sanación básica"

ACCIONES_POR_PROTOTIPO = {
    "Guerrero Tanque": (("Golpe con escudo", 2), ("Mano de piedra", 4), ("Defensa feroz", 6)),
    "Guerrero Armas": (("Embate sangriento", 4), ("Lanza de los dioses", 4), ("Golpe de tormenta", 6)),
    "Mago Fuego": (("Misiles de magma", 2), ("Vulcano", 6), ("Pare de fuego", 4)),
    "Mago Hielo": (("Lluvia de hielo", 2), ("Cono de hielo", 6), ("Bola de hielo", 4)),
    "Pícaro Veneno": (("Flor de loto", 2), ("Agonía", 4), ("Piquete", 4)),
    "Pícaro Machete": (("Cortada", 2), ("Machetazo", 4), ("Planazo", 4)),
    "Chamán": (("Toque de la Vida", 2), ("Vínculo Natural", 4), ("Canto del Bosque", 6)),
    "Médico": (("Curación Directa", 2), ("Neutralización de Efectos", 4), ("Reanimación", None)),
}

PROTOTIPOS = tuple(ACCIONES_POR_PROTOTIPO)

# Las 24 acciones en el orden de la tabla.
ACCIONES = tuple(nombre for acciones in ACCIONES_POR_PROTOTIPO.values() for nombre, _ in acciones)

SANADORES = ("Chamán", "Médico")


def acciones_desbloqueadas(prototipo, nivel):
    """Las acciones de Tabla 7 que el prototipo tiene en ese nivel (RC-01)."""
    todas = ACCIONES_POR_PROTOTIPO.get(prototipo, ())
    cuantas = sum(1 for umbral in NIVELES_DE_DESBLOQUEO if nivel >= umbral)
    return todas[:cuantas]


def costo_de(prototipo, accion, poder_actual):
    """Costo en poder de la accion; Reanimacion cuesta todo el poder que se tenga."""
    for nombre, costo in ACCIONES_POR_PROTOTIPO.get(prototipo, ()):
        if nombre == accion:
            return poder_actual if costo is None else costo
    return 0
