# language: es
# Historia: HU-COM-008 - Moderacion de comentarios | GitHub #522
# Fuente: ficha RF-COM-008; issue #522. Decision D-35 (lote: todo o nada, maximo 50,
# sin EDITAR, una accion y un motivo para todo el lote).

Caracteristica: Acciones de moderacion sobre comentarios, incluida la aplicacion en lote
  Como moderador
  quiero aprobar, ocultar, eliminar, editar y marcar comentarios, uno a uno o en lote,
  para mantener trazabilidad completa de cada decision.

  # CA-01 - flujo principal
  Escenario: Una accion sobre un comentario deja estado, asiento y aviso al autor
    Dado un comentario existente y un moderador con permisos
    Cuando aplica una accion aportando el identificador y el motivo
    Entonces el comentario queda con su nuevo estado
    Y queda un asiento con quien, cuando, el motivo y los estados anterior y nuevo
    Y se avisa al autor
    Y un comentario oculto conserva su registro

  # CA-02 - flujo alternativo: el lote
  Escenario: Un lote valido se aplica entero y el moderador ve el resultado
    Dado varios comentarios sobre los que la accion elegida es valida
    Cuando el moderador aplica esa accion con un mismo motivo a todos a la vez
    Entonces cada comentario queda con su nuevo estado
    Y cada uno tiene su propio asiento firmado por el moderador del token
    Y la respuesta trae un resultado por comentario, en el orden pedido, con el total
    Y se avisa a cada autor, salvo con MARCAR y DESMARCAR

  Escenario: Un solo comentario invalido rechaza el lote entero sin dejar estados a medias
    Dado un lote donde uno de los comentarios no admite la accion
    Cuando el moderador lo aplica
    Entonces se rechaza con un error estandar de motivo LOTE_RECHAZADO
    Y ningun comentario cambia de estado, no se crea ningun asiento y no se avisa a nadie
    Y el error lista cada comentario que fallo con su motivo

  Escenario: Un comentario inexistente dentro del lote tambien lo rechaza
    Dado un lote que incluye un identificador que no existe
    Cuando el moderador lo aplica
    Entonces se rechaza con LOTE_RECHAZADO y ese identificador figura como COMENTARIO_NO_ENCONTRADO
    Y ningun otro comentario del lote cambia

  Escenario: Un aviso que no sale no deshace el lote
    Dado un lote valido y el servicio de avisos caido
    Cuando el moderador lo aplica
    Entonces todos los comentarios quedan resueltos
    Y cada resultado indica que el autor no fue notificado

  Escenario: Con el servicio de avisos colgado el lote deja de avisar tras 3 fallos seguidos
    Dado un lote valido de varios comentarios y el servicio de avisos sin responder
    Cuando el moderador lo aplica
    Entonces todos los comentarios quedan resueltos con su asiento
    Y solo se intenta avisar a los tres primeros autores
    Y el resto de resultados indica que su autor no fue notificado

  Escenario: Un lote mal formado se rechaza antes de tocar nada
    Dado un lote vacio, con mas de 50 comentarios, con identificadores repetidos o en blanco, con la accion EDITAR o con un motivo de menos de 3 caracteres
    Cuando el moderador lo envia
    Entonces se rechaza con un error estandar 400
    Y no cambia ningun comentario

  # CA-03 - excepciones
  Escenario: Sin permisos la operacion se rechaza sin cambios
    Dado un jugador sin rol de moderacion
    Cuando intenta resolver un comentario o un lote
    Entonces recibe un 403 con error estandar
    Y no cambia nada

  Escenario: Un comentario ya resuelto por otro moderador se rechaza sin cambios
    Dado un comentario que otro moderador ya resolvio
    Cuando se intenta aplicar una accion que ya no es valida desde su estado, sola o dentro de un lote
    Entonces se rechaza con un 409 y nada cambia

  # CA-04 - resultado observable
  Escenario: El historial conserva la trazabilidad de las decisiones en lote
    Dado un lote ya aplicado
    Cuando el moderador abre el detalle de cualquiera de sus comentarios
    Entonces el historial muestra el asiento del lote con el mismo motivo y el moderador que lo aplico
    Y el autor recibio su notificacion
