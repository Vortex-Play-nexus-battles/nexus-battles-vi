# language: es
Característica: HU-MIS-005 Exploraciones prolongadas
  Como jugador
  Quiero enviar un héroe a una exploración de larga duración
  Para acumular progreso y recompensas durante varios encuentros

  Escenario: Consultar una exploración
    Dada una misión de exploración disponible
    Cuando el jugador consulta su detalle
    Entonces conoce su duración, requisitos, encuentros y recompensas

  Esquema del escenario: Validar la duración permitida
    Dada una exploración configurada con <horas> horas
    Cuando se valida el catálogo de misiones
    Entonces la configuración es <resultado>

    Ejemplos:
      | horas | resultado |
      | 24    | válida    |
      | 72    | válida    |
      | 23    | rechazada |
      | 73    | rechazada |

  Escenario: Procesar los encuentros definidos
    Dada una exploración en curso con varios encuentros
    Cuando el trabajo de misiones ejecuta la simulación
    Entonces procesa los encuentros en el orden configurado
    Y registra los eventos de combate con su secuencia

  Escenario: Consultar el progreso temporal
    Dada una exploración en curso
    Cuando el jugador consulta sus misiones activas
    Entonces observa el porcentaje transcurrido y el tiempo estimado de finalización

  Escenario: Resolver la aparición de un Máster
    Dada una exploración que configura enemigos Máster
    Cuando se simulan sus encuentros
    Entonces se aplica la probabilidad configurada para cada periodo de 24 horas
