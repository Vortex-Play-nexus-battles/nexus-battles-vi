# language: es
#
# Evidencia automatizada:
# - C1 y C4: equipamiento-inventario.test.js
# - C2 y C3: PuertaDeHeroeTest.HeroeEnMision
# - C4 y C8: HeroeEnMisionTest
# - C5, C6 y C7: CicloDeUnaMisionTest
Característica: HU-MIS-011 Restringir el uso del héroe durante la misión
  Como jugador
  Quiero que el héroe asignado a una misión permanezca reservado hasta que esta termine
  Para no utilizarlo simultáneamente ni modificar su equipamiento durante la ejecución

  Escenario: Mostrar que el héroe está en misión
    Dado un héroe asignado a una misión En progreso
    Cuando el jugador consulta el héroe en su inventario
    Entonces el sistema muestra el indicador "En misión"

  Escenario: Rechazar el héroe en una batalla Jugador contra Jugador
    Dado un héroe asignado a una misión En progreso
    Cuando el jugador intenta utilizarlo en una batalla Jugador contra Jugador
    Entonces el sistema rechaza la acción
    Y explica que el héroe está ocupado en una misión

  Escenario: Rechazar el héroe en un torneo
    Dado un héroe asignado a una misión En progreso
    Cuando un encuentro de torneo intenta iniciar una partida con ese héroe
    Entonces el sistema rechaza la acción
    Y explica que el héroe está ocupado en una misión

  Escenario: Conservar el equipamiento durante la misión
    Dado un héroe asignado a una misión En progreso
    Cuando el jugador intenta equipar o desequipar un elemento
    Entonces el sistema rechaza la modificación
    Y conserva el equipamiento con el que inició la misión

  Esquema del escenario: Liberar al héroe cuando termina la misión
    Dado un héroe asignado a una misión En progreso
    Cuando la misión cambia al estado <estado>
    Entonces el sistema libera al héroe asignado
    Y vuelve a mostrarlo como disponible

    Ejemplos:
      | estado     |
      | Completada |
      | Abandonada |
      | Fallida    |

  Escenario: Volver a utilizar al héroe liberado
    Dado un héroe liberado después de finalizar una misión
    Cuando el jugador modifica su equipamiento o lo utiliza en otro modo de juego
    Entonces el sistema permite continuar si no existe otra restricción
