# language: es
Característica: Versiones del diseño de un producto
  Como administrador del catálogo
  Quiero consultar y reutilizar diseños anteriores
  Para corregir o replicar el aspecto de un producto sin alterar sus reglas comerciales

  Antecedentes:
    Dado que existe un producto con una imagen y una descripción
    Y que el administrador está autenticado

  Escenario: Consultar las versiones anteriores con fecha y autor
    Dado que dos administradores cambiaron el diseño del producto
    Cuando el administrador abre las versiones del diseño
    Entonces ve la versión actual y las anteriores de la más reciente a la más antigua
    Y cada versión indica su fecha y su autor

  Escenario: Exportar e importar una plantilla sobre otro producto
    Dado que el administrador exportó la plantilla del producto origen
    Cuando importa esa plantilla sobre un producto destino
    Entonces el destino recibe la imagen y la descripción de la plantilla
    Y conserva su identidad, tipo, tiraje, precios y reglas de combate
    Y la importación queda registrada en el historial

  Escenario: Rechazar una plantilla inválida sin alterar el producto
    Dado un archivo con un formato o una versión de plantilla no compatibles
    Cuando el administrador intenta importarlo
    Entonces el servicio responde 400 explicando que la plantilla es inválida
    Y el producto destino conserva todos sus datos y su versión
