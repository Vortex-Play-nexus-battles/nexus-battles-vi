# language: es
# Historia: HU-JUE-002 - Control de una accion por turno
# Dependencia: HU-JUE-001 - Sorteo del orden de turnos

Caracteristica: Control de una accion por turno
  Como jugador
  quiero ejecutar una sola accion en mi turno y que todos dispongan del mismo tiempo
  para que nadie tenga ventaja por rapidez.

  Escenario: Una accion resuelta entrega el turno al siguiente participante
    Dado un participante en su turno
    Cuando ejecuta una accion
    Entonces no puede ejecutar otra hasta su siguiente turno
    Y el turno pasa al siguiente participante de la secuencia sorteada

  Escenario: Todos disponen de la misma duracion de turno
    Dado que el combate tiene una duracion de turno configurada
    Cuando cada participante recibe su turno
    Entonces todos disponen exactamente de la misma duracion para actuar

  Escenario: Un turno sin accion expira
    Dado un participante que no actua dentro de la duracion del turno
    Cuando se agota el tiempo configurado
    Entonces el turno expira sin registrar una accion
    Y el turno pasa al siguiente participante de la secuencia sorteada
