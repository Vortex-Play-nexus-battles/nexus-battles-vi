# language: es
Característica: HU-JUE-010 Botín por tasa de caída
  Como jugador
  Quiero tener la posibilidad de obtener un objeto al derrotar a un enemigo según su tasa de caída
  Para progresar mediante el botín definido por el juego

  Escenario: Obtener un objeto cuando se cumple su tasa de caída
    Dado un enemigo derrotado con un objeto cuya tasa de caída es 12.5 por ciento
    Cuando el valor aleatorio generado es menor que 12.5 por ciento
    Entonces el objeto se registra en el inventario del jugador ganador

  Escenario: No obtener un objeto cuando no se cumple su tasa de caída
    Dado un enemigo derrotado con un objeto cuya tasa de caída es 12.5 por ciento
    Cuando el valor aleatorio generado es igual o mayor que 12.5 por ciento
    Entonces el inventario del jugador ganador no recibe ese objeto

  Esquema del escenario: Reconocer el origen del objeto susceptible de caída
    Dado que el enemigo tenía el objeto <origen>
    Y la tasa de caída del objeto se cumple
    Cuando se procesa la derrota
    Entonces el objeto puede entregarse como botín al jugador ganador

    Ejemplos:
      | origen     |
      | equipado   |
      | almacenado |

  Escenario: Aplicar la tasa configurada a armas, armaduras e ítems
    Dado que Productos publica las tasas establecidas para los objetos de las Tablas 8 a 19
    Cuando el motor evalúa un arma, una armadura o un ítem susceptible de caída
    Entonces utiliza la tasa publicada para cada producto

  Escenario: Evitar un premio duplicado por la misma derrota
    Dado que una acción de combate ya produjo un botín
    Cuando la misma acción se procesa nuevamente
    Entonces no se registra otro objeto en el inventario del jugador ganador
