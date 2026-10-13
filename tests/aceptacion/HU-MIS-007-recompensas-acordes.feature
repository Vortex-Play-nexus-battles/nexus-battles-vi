# language: es
Característica: HU-MIS-007 Recompensas acordes con la misión
  Como jugador
  Quiero recibir recompensas proporcionales a la misión completada
  Para que el esfuerzo y los objetivos cumplidos se reflejen en mi progreso

  Escenario: Calcular las recompensas base
    Dada una misión completada
    Cuando el sistema liquida su resultado
    Entonces calcula los créditos, la experiencia y el botín configurados

  Escenario: Añadir únicamente bonificaciones cumplidas
    Dada una misión con objetivos opcionales bonificados
    Cuando el sistema liquida su resultado
    Entonces suma las bonificaciones de los objetivos cumplidos
    Y no suma las bonificaciones de los objetivos incumplidos

  Escenario: Entregar la épica de un Máster
    Dada una misión completada en la que se derrotó un enemigo Máster
    Cuando el sistema calcula las recompensas
    Entonces incluye la habilidad épica asociada al Máster

  Escenario: Aplicar la configuración de dificultad
    Dada una misión completada en un escalón superior
    Cuando el sistema calcula las recompensas
    Entonces aplica el multiplicador configurado para ese escalón

  Escenario: Separar el detalle de las recompensas
    Dada una misión con créditos, experiencia, botín, bonificaciones y una épica
    Cuando el jugador consulta el reporte final
    Entonces cada categoría de recompensa aparece por separado
