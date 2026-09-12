# language: es
# Historia: HU-INV-002 - Busqueda en el inventario | Jira SCRUM-144 | 3 puntos
# Escenarios derivados de los criterios de aceptacion del Issue #21.

Característica: Búsqueda en el inventario
  Como jugador
  quiero buscar productos a partir de su información registrada
  para localizar rápidamente un elemento concreto de mi inventario.

  Antecedentes:
    Dado un jugador autenticado con productos registrados en su inventario

  Esquema del escenario: Un producto se localiza por su información registrada
    Cuando el jugador busca por <campo> usando el criterio <criterio>
    Entonces la vitrina muestra únicamente el producto coincidente
    Y la búsqueda conserva la identidad del jugador

    Ejemplos:
      | campo              | criterio   |
      | nombre propio      | bruma      |
      | identificador      | solar      |
      | tipo               | armadura   |
      | parte de armadura  | guantes    |

  Escenario: Un criterio menor de cuatro caracteres no inicia la búsqueda
    Cuando el jugador escribe un criterio de tres caracteres
    Entonces el campo informa que todavía no alcanza el mínimo
    Y no se envía ninguna búsqueda al servicio

  Escenario: Una búsqueda válida puede no tener coincidencias
    Cuando el jugador busca un criterio que no pertenece a ningún producto
    Entonces la vitrina muestra un estado sin resultados
    Y no muestra un error técnico

  Escenario: El jugador limpia la búsqueda
    Dado que la vitrina muestra un resultado filtrado
    Cuando el jugador limpia el criterio
    Entonces vuelve a ver todos los productos de su inventario
