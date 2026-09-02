# language: es
Característica: Tiraje limitado de un producto
  Como administrador
  Quiero fijar cuántas unidades existen de un producto
  Para lanzar ediciones limitadas que se agoten automáticamente

  Escenario: Registrar un tiraje limitado
    Dado un producto nuevo con tiraje de 3 unidades
    Cuando se registra su disponibilidad
    Entonces quedan exactamente 3 unidades disponibles

  Escenario: Registrar un producto ilimitado
    Dado un producto nuevo con tiraje igual a -1
    Cuando se realizan varias adquisiciones
    Entonces todas son aceptadas y el tiraje permanece en -1

  Escenario: Rechazar la adquisición de un producto agotado
    Dado un producto limitado sin unidades restantes
    Cuando se intenta adquirir nuevamente
    Entonces la adquisición se rechaza indicando que está agotado

  Escenario: Resolver dos adquisiciones de la última unidad
    Dado un producto que conserva una sola unidad
    Cuando dos jugadores intentan adquirirla simultáneamente
    Entonces solo una adquisición es aceptada
    Y la otra se rechaza por agotamiento
