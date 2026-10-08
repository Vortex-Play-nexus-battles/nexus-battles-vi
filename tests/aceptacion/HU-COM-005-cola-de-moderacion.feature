# language: es
# Historia: HU-COM-005 - Cola de moderacion de comentarios | GitHub #519
# Fuente: ficha RF-COM-005 y RF-COM-008; issue #519; contrato comentarios.yaml 1.10.1
#
# Cada escenario dice, en «# Prueba:», la prueba automatizada que YA lo demuestra
# (archivo › describe › test). Rutas abreviadas:
#   FlujoDeModeracionTest  = services/plataforma/comentarios/src/test/java/com/nexusbattles/plataforma/comentarios/moderacion/FlujoDeModeracionTest.java
#   ModeracionHttpTest     = services/plataforma/comentarios/src/test/java/com/nexusbattles/plataforma/comentarios/moderacion/ModeracionHttpTest.java
#   DecisionEnLoteIT       = services/plataforma/comentarios/src/test/java/com/nexusbattles/plataforma/comentarios/DecisionEnLoteIT.java
#   moderar-comentarios.test.js = frontend/app-web/src/plataforma/comentarios/moderar-comentarios.test.js
#   consola.test.js        = frontend/app-web/src/plataforma/consola/consola.test.js
#   moderacion-comentarios.e2e.spec.js = tests/e2e/moderacion-comentarios.e2e.spec.js
# Pendiente declarado: el recorrido del lote contra los servicios reales (E2E) no existe todavia.

Caracteristica: Cola de moderacion de comentarios reportados
  Como moderador
  quiero revisar los comentarios pendientes y resolverlos uno a uno o en lote
  para mantener la comunidad sana dejando constancia de cada decision.

  # --- CA-01: flujo principal ---

  Escenario: La cola presenta primero los comentarios mas reportados
    Dado varios comentarios reportados, uno con mas reportes que otro
    Cuando el moderador abre la cola
    Entonces el comentario con mas reportes aparece primero
    Y cada entrada indica cuantos reportes tiene y de que categorias
    # Prueba: FlujoDeModeracionTest › La cola (RF-COM-005) › priorizada: el mas reportado primero
    # Prueba: moderacion-comentarios.e2e.spec.js › Moderacion de comentarios: el comentario en revision tiene salida (RF-COM-005/006/008) › 5: la moderadora lo encuentra en la cola con su reporte y su categoria

  Escenario: La cola se filtra por categoria de reporte y los filtros se combinan
    Dado comentarios reportados con categorias distintas, alguno marcado para seguimiento y de productos distintos
    Cuando el moderador filtra la cola por una categoria, y despues la combina con producto y con marcado
    Entonces solo ve los comentarios con al menos un reporte de esa categoria que cumplen tambien los otros filtros
    # Prueba: FlujoDeModeracionTest › La cola (RF-COM-005) › categoria deja solo los comentarios con al menos un reporte de esa categoria (1.10.0)
    # Prueba: FlujoDeModeracionTest › La cola (RF-COM-005) › los filtros nuevos se combinan por Y con productoId y con marcado
    # Prueba: moderar-comentarios.test.js › HU-COM-005 (#519): filtros de categoría y de prioridad en la cola › elegir una categoría recarga de inmediato con ella; «Todas» la quita

  Escenario: El moderador revisa un comentario con su contexto
    Dado un comentario reportado que ya tuvo decisiones de moderacion
    Cuando el moderador abre su detalle
    Entonces ve el comentario, sus reportes con categoria y el historial de decisiones en orden
    # Prueba: FlujoDeModeracionTest › Decidir (RF-COM-008) › el detalle trae el comentario, sus reportes y su historial
    # Prueba: moderacion-comentarios.e2e.spec.js › Moderacion de comentarios: el comentario en revision tiene salida (RF-COM-005/006/008) › 9-10: repetir la decision es 409, y restaurar lo devuelve al hilo

  Escenario: El moderador resuelve un comentario con un motivo obligatorio
    Dado un comentario reportado en la cola
    Cuando el moderador aplica una accion sobre el
    Entonces la accion exige un motivo, tambien al aprobar
    Y sin motivo no se aplica nada
    # Prueba: FlujoDeModeracionTest › Decidir (RF-COM-008) › el motivo es obligatorio, incluso para aprobar, y va de 3 a 500 caracteres
    # Prueba: moderar-comentarios.test.js › el detalle y la decision › el boton esta apagado hasta que hay motivo, y manda accion y motivo

  Escenario: El moderador selecciona varios comentarios y aplica la misma accion en lote
    Dado varios comentarios de la cola seleccionados
    Cuando el moderador aplica una accion con un motivo unico
    Entonces todos los comentarios seleccionados quedan resueltos con esa accion
    Y cada uno recibe su propio asiento, en el orden en que se pidieron
    # Prueba: FlujoDeModeracionTest › Decision en lote (HU-COM-005, contrato 1.10.0) › 9: un lote resuelve todos, con un asiento por comentario y los resultados en el orden de entrada
    # Prueba: DecisionEnLoteIT › IT-a: un lote exitoso persiste los estados y un asiento por comentario, en el orden recibido
    # Prueba: moderar-comentarios.test.js › HU-COM-005 (#519): decisión en lote › aplicar el lote › P9: manda los ids en el orden de la cola, con el motivo limpio y sin confirmación

  Escenario: Eliminar en lote exige escribir la confirmacion
    Dado varios comentarios seleccionados y la accion eliminar
    Cuando el moderador intenta aplicarla sin la confirmacion exacta
    Entonces el sistema la rechaza y no cambia ningun comentario
    Y con la confirmacion exacta el lote se aplica
    # Prueba: FlujoDeModeracionTest › Decision en lote (HU-COM-005, contrato 1.10.0) › 6: ELIMINAR sin la confirmacion exacta ELIMINAR es 400 CONFIRMACION_REQUERIDA y no cambia nada
    # Prueba: FlujoDeModeracionTest › Decision en lote (HU-COM-005, contrato 1.10.0) › 6c: ELIMINAR con la confirmacion ELIMINAR resuelve el lote
    # Prueba: moderar-comentarios.test.js › HU-COM-005 (#519): decisión en lote › eliminar en lote › P14: pide escribir ELIMINAR tal cual y no llama al servicio antes

  # --- CA-02: flujo alternativo ---

  Escenario: El moderador llega a la cola desde la consola de moderacion
    Dado un moderador en la consola de moderacion
    Cuando busca sus herramientas
    Entonces encuentra la cola de comentarios y su enlace lleva a la pantalla de la cola
    # Prueba: consola.test.js › el moderador encuentra la cola de comentarios entre sus herramientas
    # Prueba: moderacion-comentarios.e2e.spec.js › Moderacion de comentarios: el comentario en revision tiene salida (RF-COM-005/006/008) › 13: desde la pantalla, la consola lleva a la cola; se marca, se filtra y se edita (B3)

  Escenario: Tras aplicar un lote el moderador ve el resultado y la cola se actualiza
    Dado varios comentarios seleccionados y un motivo
    Cuando el moderador aplica el lote
    Entonces la pantalla dice que se hizo y si los autores fueron avisados
    Y la seleccion y el motivo se vacian y la cola se vuelve a pedir
    # Prueba: moderar-comentarios.test.js › HU-COM-005 (#519): decisión en lote › aplicar el lote › P9: manda los ids en el orden de la cola, con el motivo limpio y sin confirmación
    # Prueba: moderar-comentarios.test.js › HU-COM-005 (#519): decisión en lote › aplicar el lote › P10: si el aviso a algún autor no salió, se dice, sin ids

  Escenario: Un lote que falla no deja ningun comentario a medias
    Dado un lote en el que algo impide resolver alguno de sus comentarios
    Cuando el moderador lo aplica
    Entonces ningun comentario cambia, ni se escribe ningun asiento, ni se avisa a ningun autor
    Y si la escritura falla a mitad se revierte todo
    # Prueba: FlujoDeModeracionTest › Decision en lote (HU-COM-005, contrato 1.10.0) › 10: si uno falla no cambia ninguno: ni estados, ni asientos, ni avisos, ni auditoria
    # Prueba: DecisionEnLoteIT › IT-c: si el asiento del segundo comentario falla, se revierte TODO: estados, asientos, avisos y auditoria

  # --- CA-03: excepciones ---

  Escenario: Con la cola sin pendientes se muestra el estado vacio, no un error
    Dado que no hay comentarios pendientes de revision
    Cuando el moderador abre la cola
    Entonces ve que no hay trabajo pendiente
    Y no recibe un error
    # Prueba: ModeracionHttpTest › la cola y la decision (RF-COM-005 y RF-COM-008) › la cola vacia es 200 con lista vacia, no 404: no tener trabajo es una respuesta
    # Prueba: moderar-comentarios.test.js › la cola › vacia es un estado vacio, no un error: no tener trabajo es una respuesta

  Escenario: Si otro moderador ya resolvio el comentario, la decision se rechaza y nada cambia
    Dado un comentario que otro moderador ya resolvio mientras este lo miraba
    Cuando el segundo moderador intenta resolverlo
    Entonces se rechaza con un error estandar que explica el motivo
    Y no queda ningun asiento nuevo
    Y la pantalla lo explica y recarga la cola
    # Prueba: FlujoDeModeracionTest › Decidir (RF-COM-008) › si otro moderador ya lo resolvio, el segundo recibe 409 y nada cambia (CA-03)
    # Prueba: ModeracionHttpTest › la cola y la decision (RF-COM-005 y RF-COM-008) › si otro moderador se adelanto, es 409 con motivo: la pantalla estaba vieja
    # Prueba: moderacion-comentarios.e2e.spec.js › Moderacion de comentarios: el comentario en revision tiene salida (RF-COM-005/006/008) › 14: aprobar uno reportado que sigue a la vista cierra sus reportes; aprobar otra vez es 409
    # Prueba: moderar-comentarios.test.js › resolver desde la vista › si otro moderador se adelanto (409), se explica y se recarga la cola

  Escenario: Un lote con un comentario ya resuelto o inexistente se rechaza completo
    Dado un lote que incluye un comentario que otro moderador ya resolvio, o uno que no existe
    Cuando el moderador lo aplica
    Entonces se rechaza completo con un error estandar que lista todos los comentarios afectados
    Y no cambia ninguno de los demas
    # Prueba: FlujoDeModeracionTest › Decision en lote (HU-COM-005, contrato 1.10.0) › 8: es 409 con TODOS los afectados, no solo el primero, en el orden de entrada; el valido no cambia
    # Prueba: FlujoDeModeracionTest › Decision en lote (HU-COM-005, contrato 1.10.0) › 7: ids que no existen son 404 con TODOS los que faltan, en el orden de entrada; los demas no cambian
    # Prueba: ModeracionHttpTest › la decision en lote (HU-COM-005, contrato 1.10.0) › H10: comentarios que no admiten la accion son 409 TRANSICION_INVALIDA con todos los ids
    # Prueba: DecisionEnLoteIT › IT-b: un lote con un comentario invalido es 409 con sus ids y deja la base intacta
    # Prueba: moderar-comentarios.test.js › HU-COM-005 (#519): decisión en lote › los rechazos del servicio › P18: 409 explica que otro moderador se adelantó, lista a quién y no pierde el trabajo

  Escenario: Un lote mal formado se rechaza antes de tocar nada
    Dado un lote vacio, con ids repetidos, por encima del tope del contrato, sin motivo o con la accion editar
    Cuando el moderador lo envia
    Entonces se rechaza con un error estandar y no se toca ningun comentario
    # Prueba: FlujoDeModeracionTest › Decision en lote (HU-COM-005, contrato 1.10.0) › 1: una lista vacia (o nula) es 400 y no toca la base
    # Prueba: FlujoDeModeracionTest › Decision en lote (HU-COM-005, contrato 1.10.0) › 2: ids repetidos es 400, nada cambia
    # Prueba: FlujoDeModeracionTest › Decision en lote (HU-COM-005, contrato 1.10.0) › 3a: mas de 100 ids (el tope del contrato) es 400
    # Prueba: FlujoDeModeracionTest › Decision en lote (HU-COM-005, contrato 1.10.0) › 4a: EDITAR no vale en lote (necesita un texto por comentario): 400, no 404 ni 409
    # Prueba: FlujoDeModeracionTest › Decision en lote (HU-COM-005, contrato 1.10.0) › 5: el motivo es obligatorio, de 3 a 500 caracteres

  Escenario: Quien no es moderador no ve la cola ni decide
    Dado un jugador, o una peticion sin sesion
    Cuando intenta abrir la cola o resolver comentarios, uno a uno o en lote
    Entonces se le rechaza y el servicio no hace nada
    # Prueba: ModeracionHttpTest › la cola y la decision (RF-COM-005 y RF-COM-008) › una jugadora NO ve la cola ni resuelve: 403 en las dos puertas
    # Prueba: ModeracionHttpTest › la decision en lote (HU-COM-005, contrato 1.10.0) › H1: sin token es 401 y el servicio ni se entera
    # Prueba: ModeracionHttpTest › la decision en lote (HU-COM-005, contrato 1.10.0) › H2: una jugadora o un token de servicio no deciden en lote: 403
    # Prueba: moderacion-comentarios.e2e.spec.js › Moderacion de comentarios: el comentario en revision tiene salida (RF-COM-005/006/008) › 3-4: reportar dos veces es 409, y quien reporta no puede ver la cola

  # --- CA-04: resultado observable ---

  Escenario: Cada comentario resuelto queda con su asiento y sale de la cola
    Dado un comentario reportado en la cola
    Cuando un moderador lo resuelve
    Entonces queda un asiento con quien decidio segun su sesion, el motivo y los estados anterior y nuevo
    Y el comentario resuelto ya no esta en la cola de pendientes
    # Prueba: moderacion-comentarios.e2e.spec.js › Moderacion de comentarios: el comentario en revision tiene salida (RF-COM-005/006/008) › 6-7: la moderadora oculta, el asiento lleva SU uid y queda en el historial
    # Prueba: FlujoDeModeracionTest › el camino entero: se reporta, entra en la cola, se resuelve y sale
    # Prueba: DecisionEnLoteIT › IT-a: un lote exitoso persiste los estados y un asiento por comentario, en el orden recibido

  Escenario: El autor recibe el aviso con el motivo de la decision
    Dado un comentario resuelto por un moderador
    Cuando se consulta la bandeja del autor
    Entonces el aviso esta alli e incluye el motivo
    # Prueba: moderacion-comentarios.e2e.spec.js › Moderacion de comentarios: el comentario en revision tiene salida (RF-COM-005/006/008) › 8: el autor se entera — el aviso esta en su bandeja
    # Prueba: FlujoDeModeracionTest › Decision en lote (HU-COM-005, contrato 1.10.0) › 9: un lote resuelve todos, con un asiento por comentario y los resultados en el orden de entrada
