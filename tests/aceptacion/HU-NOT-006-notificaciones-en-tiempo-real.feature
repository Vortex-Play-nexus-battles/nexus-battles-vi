# language: es
# Historia: HU-NOT-006 - Notificaciones en tiempo real | GitHub #32 | 5 puntos
# Fuente: Proyecto Integrador II, seccion 7.7.13, p. 57 y seccion 7.8.12, p. 65

Caracteristica: Notificaciones en tiempo real sincronizadas entre sesiones
  Como jugador con varias sesiones abiertas
  quiero recibir las notificaciones en todas ellas y con el mismo estado de lectura
  para no perder ningun evento ni tener que revisar dos veces lo mismo.

  Escenario: El aviso llega a todas las sesiones abiertas del jugador
    Dado un jugador con tres sesiones activas al mismo tiempo
    Cuando se produce un evento notificable dirigido a el
    Entonces la notificacion se entrega a las tres sesiones
    Y el estado de lectura queda igual en todas

  Escenario: El aviso no se pierde cuando el jugador no esta conectado
    Dado un jugador que no tiene ninguna sesion abierta
    Cuando se produce un evento notificable dirigido a el
    Entonces la notificacion queda guardada como pendiente
    Y se le entrega en su siguiente ingreso

  Escenario: La sesion que perdio la conexion recupera lo que se perdio
    Dado un jugador con dos sesiones activas del que una pierde la conexion
    Cuando llegan avisos nuevos mientras esa sesion esta caida
    Entonces al reconectar recibe unicamente los avisos que se perdio
    Y su cuenta de no leidos vuelve a coincidir con la de la otra sesion

  Escenario: No se admiten avisos ni sesiones invalidos
    Dado un identificador de usuario, de sesion o de aviso vacio o repetido
    Cuando se intenta registrarlo en la bandeja
    Entonces la operacion se rechaza antes de guardar nada
