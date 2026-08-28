# language: es
Característica: Creación y edición de elementos propios
  Como jugador autenticado
  quiero crear y modificar elementos de mi inventario
  para ver en la vitrina el estado que quedó guardado

  Escenario: Un elemento creado aparece en la vitrina
    Dado que el jugador autenticado tiene el inventario vacío
    Cuando crea un ítem con producto, tipo y nombre
    Entonces el elemento aparece en su vitrina

  Escenario: El nuevo nombre aparece en la vitrina
    Dado que el jugador autenticado tiene un elemento propio
    Cuando cambia el nombre del elemento
    Entonces la misma tarjeta muestra el nuevo nombre

  Escenario: No se reemplaza la vista con una escritura rechazada
    Dado que el jugador autenticado está viendo su inventario
    Cuando el servicio rechaza una modificación
    Entonces la vitrina conserva el estado anterior
    Y se informa el problema sin mostrar códigos técnicos
