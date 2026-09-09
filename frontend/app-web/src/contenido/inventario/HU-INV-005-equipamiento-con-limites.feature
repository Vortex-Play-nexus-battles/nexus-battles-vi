# language: es
Característica: Equipamiento del héroe con límites
  Como jugador autenticado
  quiero equipar a mi héroe respetando las ranuras del juego
  para prepararlo sin superar los límites permitidos

  Escenario: El héroe equipa hasta dos armas
    Dado un héroe propio con dos armas equipadas
    Cuando intenta equipar una tercera arma
    Entonces el cambio se rechaza y conserva las dos armas anteriores

  Escenario: Cada parte de armadura ocupa una ranura distinta
    Dado un héroe propio con una pieza en cada una de las seis ranuras
    Cuando intenta equipar otra pieza en una ranura ocupada
    Entonces el cambio se rechaza y conserva la armadura anterior

  Escenario: El héroe equipa hasta dos ítems
    Dado un héroe propio con dos ítems equipados
    Cuando intenta equipar un tercer ítem
    Entonces el cambio se rechaza y conserva los dos ítems anteriores

  Escenario: Desequipar libera una ranura
    Dado un héroe propio con un elemento equipado
    Cuando el jugador desequipa el elemento
    Entonces puede equipar otro elemento en la ranura liberada
