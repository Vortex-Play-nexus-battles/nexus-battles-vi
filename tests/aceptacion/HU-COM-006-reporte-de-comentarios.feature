# language: es
# Historia: HU-COM-006 - Reporte de comentarios por los usuarios | GitHub #520
# Fuente: ficha RF-COM-006; issue #520

Caracteristica: Reporte de comentarios inapropiados por los jugadores
  Como jugador
  quiero reportar un comentario inapropiado eligiendo una categoria de violacion
  para que el comentario quede sujeto a revision por moderacion.

  Escenario: El reporte con categoria y descripcion opcional queda registrado
    Dado un jugador autenticado y un comentario publicado
    Cuando reporta el comentario eligiendo una categoria de violacion, con o sin descripcion
    Entonces el reporte queda registrado a nombre de quien lo envia
    Y el jugador recibe la confirmacion del reporte

  Escenario: Los reportes de varios jugadores sobre un mismo comentario se agrupan
    Dado un comentario que ya fue reportado por otro jugador
    Cuando un segundo jugador lo reporta
    Entonces la cola de moderacion sigue mostrando una sola entrada para ese comentario
    Y la entrada acumula los dos reportes, agrupados por categoria

  # CA-01 y CA-04: «incorpora el comentario a la cola de moderacion» y queda
  # «sujeto a revision». Encolar no es ocultar: en la auditoria de DEV del
  # 30-sep un solo reporte lo sacaba del hilo para todos (comentarios 1.8.0).
  Escenario: El comentario reportado queda encolado para revision sin dejar de verse
    Dado un comentario publicado que nadie ha reportado
    Cuando un jugador lo reporta
    Entonces el comentario aparece en la cola de moderacion a la espera de un moderador
    Y sigue publicado en el hilo del producto hasta que un moderador decida

  # El ocultamiento automatico por numero de reportes es una decision abierta
  # con el Product Owner (D-36): aqui no se escribe ninguna cifra. Sin umbral
  # configurado (valor 0) el escenario no se activa.
  Escenario: Un comentario que alcanza el umbral de ocultamiento sale del hilo mientras se revisa
    Dado un umbral de ocultamiento configurado y un comentario al que le falta un reporte para alcanzarlo
    Cuando un jugador envia el reporte que lo alcanza
    Entonces el comentario deja de mostrarse en el hilo hasta que un moderador decida
    Y sigue en la cola de moderacion

  Escenario: Aprobar un comentario reportado que sigue publicado cierra sus reportes
    Dado un comentario publicado con reportes pendientes en la cola
    Cuando un moderador lo aprueba con su motivo
    Entonces el comentario sigue publicado y sale de la cola
    Y un segundo moderador que intente aprobarlo recibe un error estandar que explica que ya se resolvio

  # CA-02 depende del umbral de denuncias, cuyo valor es una decision abierta
  # con el Product Owner: aqui no se escribe ninguna cifra. Sin umbral
  # configurado (valor 0) el escenario no se activa.
  Escenario: Un reporte que alcanza el umbral eleva la prioridad en la cola
    Dado un umbral de denuncias configurado y un comentario al que le falta un reporte para alcanzarlo
    Cuando un jugador envia el reporte que lo alcanza
    Entonces el comentario sube de prioridad en la cola de moderacion
    Y el reporte y el cambio de prioridad quedan aplicados juntos, sin estado a medias
    Y el jugador ve el resultado de su reporte

  Escenario: Sin umbral configurado la cola solo se ordena por numero de reportes
    Dado que no hay umbral de denuncias configurado
    Cuando un comentario acumula reportes de varios jugadores
    Entonces ninguna entrada de la cola se marca con prioridad elevada
    Y la cola sigue ordenada por numero de reportes y, a igualdad, por antiguedad

  Escenario: El jugador que agoto su limite de reportes no puede enviar otro
    Dado un jugador que ya alcanzo el limite de reportes permitido
    Cuando intenta reportar otro comentario
    Entonces el reporte se rechaza con un error estandar que explica el motivo
    Y no se registra nada ni cambia el estado del comentario

  Escenario: Un jugador no puede reportar dos veces el mismo comentario
    Dado un jugador que ya reporto un comentario
    Cuando lo reporta de nuevo
    Entonces el reporte se rechaza con un error estandar que explica el motivo
    Y el numero de reportes y la prioridad del comentario no cambian
