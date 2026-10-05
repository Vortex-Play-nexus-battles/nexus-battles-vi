# language: es
Característica: Productos premium adquiribles en moneda real
  Como administrador
  Quiero gestionar productos premium adquiribles en moneda real
  Para ofrecer contenido exclusivo sin permitir su reventa entre jugadores

  Escenario: Identificación de un producto premium en la tienda
    Dado un producto premium con precio en moneda real
    Cuando el jugador consulta la vitrina
    Entonces la tarjeta del producto muestra el distintivo "Premium"
    Y no ofrece un precio en créditos

  Escenario: Restricción de publicación en subasta
    Dado un producto marcado como premium
    Cuando un jugador intenta publicarlo en una subasta
    Entonces el sistema rechaza la operación por ser un producto no subastable

  Escenario: Adquisición segura de un producto premium
    Dado un producto premium disponible en la tienda
    Cuando el jugador completa el pago mediante la pasarela simulada
    Entonces la orden queda registrada con su detalle
    Y el medio de pago conserva únicamente la marca y los últimos cuatro dígitos
    Y el producto se entrega al inventario del jugador
