# language: es
# Historia: HU-INV-001 - Vitrina del inventario | Jira SCRUM-140 | 5 puntos
# Fuente: Proyecto Integrador II, seccion 7.1, p. 34
# Escenarios derivados de los criterios de aceptacion del Product Backlog v3.5.

Característica: Vitrina del inventario
  Como jugador
  quiero ver mi inventario de personajes e ítems en una vitrina web que se adapte a mi pantalla
  para revisar lo que tengo sin perder información ni tener que desplazarme de lado.

  Antecedentes:
    Dado un jugador autenticado con sesión iniciada

  # Criterio 1 - caso nominal
  Escenario: La vitrina muestra dieciséis productos en la resolución de referencia
    Dado que el jugador tiene 40 productos en su inventario
    Y una ventana de 1360 x 768 píxeles
    Cuando abre la vitrina de su inventario
    Entonces la primera página muestra exactamente 16 productos
    Y la vitrina se ve completa sin desplazamiento horizontal

  # Criterio 1 - frontera: menos productos que el tamaño de página
  Escenario: La vitrina no rellena huecos cuando hay menos de dieciséis productos
    Dado que el jugador tiene 7 productos en su inventario
    Y una ventana de 1360 x 768 píxeles
    Cuando abre la vitrina de su inventario
    Entonces la primera página muestra exactamente 7 productos
    Y la vitrina se ve completa sin desplazamiento horizontal

  # Criterio 2 - adaptación a resoluciones inferiores
  Esquema del escenario: El contenido se reorganiza en resoluciones menores sin perder legibilidad
    Dado que el jugador tiene 40 productos en su inventario
    Y una ventana de <ancho> x <alto> píxeles
    Cuando abre la vitrina de su inventario
    Entonces el contenido se reorganiza para caber en el ancho disponible
    Y no se produce desplazamiento horizontal
    Y todo texto e icono de cada tarjeta permanece legible

    Ejemplos:
      | ancho | alto |
      |  1024 |  768 |
      |   768 | 1024 |
      |   375 |  812 |

  # Criterio 3 - estado vacío, nunca un error
  Escenario: Un inventario vacío muestra un estado explicativo
    Dado que el jugador no tiene ningún producto en su inventario
    Cuando abre la vitrina de su inventario
    Entonces se muestra un estado vacío que explica que aún no tiene productos
    Y no se muestra ningún mensaje de error
    Y no se muestra ningún código de estado del protocolo
