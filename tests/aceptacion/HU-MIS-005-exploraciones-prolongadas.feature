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
    Cuando vence el intervalo correspondiente a un encuentro
    Entonces procesa los encuentros pendientes en el orden configurado
    Y registra los eventos de combate con su secuencia
    Y si el héroe cae la misión termina como Fallida

  Escenario: Consultar el progreso temporal
    Dada una exploración en curso
    Cuando el jugador consulta sus misiones activas
    Entonces observa el porcentaje transcurrido y el tiempo estimado de finalización

  Escenario: Acumular sin entregar antes de terminar
    Dada una exploración en curso que ya superó encuentros
    Cuando el jugador consulta sus misiones activas
    Entonces observa encuentros completados y totales
    Y observa créditos, experiencia y objetos acumulados
    Pero las recompensas todavía no fueron entregadas

  Escenario: Liquidar una sola vez al terminar
    Dada una exploración que resolvió todos sus encuentros
    Cuando finaliza con éxito
    Entonces entrega todas las recompensas acumuladas en una sola liquidación

  Escenario: Fallar conserva lo ganado
    Dada una exploración con recompensas acumuladas
    Cuando el héroe cae en el siguiente encuentro
    Entonces la misión termina como Fallida
    Y conserva y liquida lo acumulado hasta ese encuentro

  Escenario: Cancelar aplica la penalización de abandono
    Dada una exploración con recompensas acumuladas
    Cuando el jugador la cancela voluntariamente
    Entonces pierde las recompensas acumuladas

  Escenario: Cancelar por error del sistema no penaliza
    Dada una exploración cuya simulación viene fallando por un error del sistema
    Cuando el jugador la cancela
    Entonces conserva las recompensas acumuladas

  Escenario: Resolver la aparición de un Máster
    Dada una exploración que configura enemigos Máster
    Cuando se simulan sus encuentros
    Entonces se aplica la probabilidad configurada para cada periodo de 24 horas
