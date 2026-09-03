# language: es
Característica: Evitar daño a compañeros de equipo
  Como jugador en modo cooperativo
  Quiero que mis acciones no dañen a mis compañeros
  Para jugar en grupo sin arruinar la partida

  Escenario: Un ataque normal no daña a un compañero
    Dado un combate cooperativo
    Y una acción que solo puede afectar oponentes
    Cuando el objetivo pertenece al mismo equipo del atacante
    Entonces la resolución no aplica daño al objetivo

  Escenario: Una acción puede incluir aliados por definición
    Dado un combate cooperativo
    Y una acción cuya definición permite expresamente afectar aliados
    Cuando el objetivo pertenece al mismo equipo del atacante
    Entonces la acción se resuelve normalmente
