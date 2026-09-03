# language: es
# Historia: HU-JUE-001 - Sorteo del orden de turnos | GitHub #84 | 3 puntos
# Fuente: Proyecto Integrador II, seccion 6.1.3, p. 31; RG-031

Caracteristica: Sorteo del orden de turnos
  Como jugador
  quiero que el orden de turnos se sortee al empezar y no cambie durante la partida
  para que la batalla sea justa y previsible.

  Escenario: Todos los participantes aparecen una sola vez en el orden inicial
    Dado un combate con cuatro participantes distintos
    Cuando se sortea el orden de turnos
    Entonces el orden contiene exactamente a los cuatro participantes
    Y ningun participante aparece mas de una vez

  Escenario: El orden sorteado permanece durante todo el combate
    Dado que ya se sorteo el orden de turnos de un combate
    Cuando transcurren varios turnos y rondas
    Entonces la secuencia conserva el mismo orden inicial

  Escenario: El sorteo no favorece participantes ni posiciones
    Dado un conjunto suficiente de ejecuciones reproducibles del sorteo
    Cuando se cuenta cuantas veces aparece cada participante en cada posicion
    Entonces las frecuencias se mantienen dentro de la tolerancia estadistica definida para la prueba

  Escenario: No se inicia un sorteo con participantes invalidos
    Dado un conjunto con menos de dos participantes o identificadores repetidos
    Cuando se intenta sortear el orden
    Entonces la operacion se rechaza antes de iniciar el combate
