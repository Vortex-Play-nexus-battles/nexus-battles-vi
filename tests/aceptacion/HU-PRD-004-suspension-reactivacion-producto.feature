# language: es
Característica: Suspensión y reactivación de un producto
  Como administrador
  Quiero retirar temporalmente un producto sin borrarlo
  Para impedir nuevas compras sin afectar a quienes ya lo poseen

  Escenario: Suspender un producto sin eliminarlo
    Dado un producto activo con unidades disponibles
    Cuando el administrador lo suspende
    Entonces su registro y sus unidades se conservan
    Y una nueva adquisición es rechazada por suspensión

  Escenario: Reactivar un producto suspendido
    Dado un producto suspendido que antes estaba disponible
    Cuando el administrador lo reactiva
    Entonces recupera su estado anterior
    Y vuelve a aceptar adquisiciones
