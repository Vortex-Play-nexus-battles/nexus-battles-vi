# language: es
Característica: Topes de tiempo de partida y turno
  Como jugador
  Quiero límites de tiempo para la partida y cada turno
  Para que nadie pueda prolongar el combate indefinidamente

  Escenario: Terminar la partida a los seis minutos
    Dada una partida en curso con dos equipos activos
    Cuando se cumplen seis minutos desde su inicio
    Entonces la partida finaliza
    Y gana el equipo que conserva más vida

  Escenario: Perder por inactividad
    Dado un jugador que tiene el turno activo
    Cuando permanece inactivo durante un minuto
    Entonces deja de participar todo su equipo

  Escenario: Reiniciar el plazo por actividad
    Dado un jugador que tiene el turno activo
    Cuando registra actividad antes de un minuto
    Entonces el plazo de inactividad vuelve a comenzar

  Escenario: Resolver un empate exacto
    Dada una partida empatada en vida a los seis minutos
    Cuando se cierra por tiempo
    Entonces se aplica el criterio de desempate configurado
    Y el resultado usa el mismo flujo de cierre y recompensas
