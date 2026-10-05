# language: es
Característica: Alertas de cambios del catálogo
  Como jugador
  Quiero conocer los cambios relevantes del catálogo al iniciar sesión
  Para entender qué productos y reglas de balance cambiaron desde mi último ingreso

  Escenario: Avisar la creación de un producto
    Dado que se creó un producto después del último ingreso del jugador
    Cuando el jugador inicia sesión
    Entonces recibe una alerta de nuevo producto
    Y la alerta muestra una descripción clara y la fecha de implementación

  Escenario: Avisar una modificación general
    Dado que se modificó la información general de un producto
    Cuando el jugador inicia sesión
    Entonces recibe una alerta de producto modificado

  Escenario: Distinguir un cambio de balance
    Dado que se modificó un atributo de balance de un producto
    Cuando el jugador inicia sesión
    Entonces recibe una alerta de cambio de balance

  Escenario: Avisar una suspensión y una reactivación
    Dado que un producto fue suspendido y posteriormente reactivado
    Cuando el jugador inicia sesión
    Entonces recibe una alerta por cada cambio efectivo de estado

  Escenario: No repetir cambios ya entregados
    Dado que el jugador ya consultó las alertas de un cambio del catálogo
    Cuando vuelve a iniciar sesión sin cambios nuevos
    Entonces no recibe nuevamente esa alerta
