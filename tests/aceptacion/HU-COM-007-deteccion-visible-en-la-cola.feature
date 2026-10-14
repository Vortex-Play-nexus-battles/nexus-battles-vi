# language: es
# Historia: HU-COM-007 (RF-COM-007) | GitHub #521
# Fuente: criterio CA-01 de #521, «registra la detección», tal como lo cita la nota 1.10.0 de
#         contracts/openapi/comentarios.yaml; RF-COM-007, retener antes que publicar sin filtrar
# Alcance: solo CA-01, la detección visible en la cola y en el detalle de moderación
#          (contrato comentarios 1.10.0 y moderacion-lista-negra 2.1.0)

Característica: La cola de moderación dice qué regla retuvo cada comentario
  Como moderador
  quiero ver por qué el filtro automático retuvo un comentario
  para decidir sabiendo qué regla de la lista negra lo señaló.

  Escenario: El comentario retenido por la lista negra muestra las reglas que coincidieron
    Dado un jugador que publica un comentario con un término de la lista negra
    Cuando el filtro automático lo retiene en revisión
    Entonces la cola y el detalle de moderación muestran su detección automática
    Y la detección dice qué reglas coincidieron, con su categoría y su motivo
    Y no repite el texto del comentario ni los términos que coincidieron

  Escenario: Si la lista negra no responde, el comentario se retiene y la detección lo explica
    Dado que la lista negra no responde
    Cuando un jugador publica un comentario
    Entonces el comentario queda retenido en revisión
    Y la detección dice que la lista negra no respondió y que se retuvo por precaución

  Escenario: El comentario que llegó a la cola por reportes no trae detección
    Dado un comentario publicado que otro jugador reporta
    Cuando el moderador lo ve en la cola
    Entonces no aparece ninguna detección automática
