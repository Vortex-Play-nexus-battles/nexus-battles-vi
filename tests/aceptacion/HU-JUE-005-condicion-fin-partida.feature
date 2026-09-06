# language: es
Característica: Condición de fin de partida
  Como jugador
  Quiero que el combate termine cuando queda un único superviviente o equipo
  Para que el resultado sea definitivo

  Escenario: Un héroe fallece al llegar a cero vida
    Dado un héroe activo en una partida
    Cuando sus puntos de vida se reducen a cero
    Entonces queda declarado fallecido
    Y deja de participar

  Escenario: Terminar un combate individual
    Dado un combate individual con varios héroes activos
    Cuando queda un único héroe con vida
    Entonces la partida finaliza y ese héroe gana

  Escenario: Terminar un combate cooperativo
    Dado un combate con varios equipos activos
    Cuando queda un único equipo con héroes activos
    Entonces la partida finaliza y ese equipo gana

  Escenario: Rechazar acciones después del cierre
    Dada una partida finalizada
    Cuando se intenta ejecutar una nueva acción de daño
    Entonces la acción es rechazada
