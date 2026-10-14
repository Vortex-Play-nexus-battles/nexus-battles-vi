# language: es
Característica: Vista previa antes de publicar
  Como administrador del catálogo
  Quiero ver cómo quedará el producto antes de publicar el cambio y poder descartarlo
  Para no exponer a los jugadores un diseño a medias

  # Automatizado en: frontend/app-web/src/contenido/productos/
  # catalogo-admin.test.js (HU-PRD-010), productos.test.js y
  # vista-previa-producto.test.js.

  Antecedentes:
    Dado que el administrador abrió la ficha de un producto

  Escenario: Previsualizar el aspecto tal como lo verá el jugador
    Cuando cambia la imagen, el nombre o la descripción
    Entonces la vista previa muestra la tarjeta de la tienda con esos cambios
    Y dice qué cambios están sin publicar
    Y el producto publicado no cambia

  Escenario: Descartar los cambios previsualizados
    Dado que hay cambios sin publicar
    Cuando el administrador los descarta
    Entonces la ficha y la vista previa vuelven a lo publicado
    Y no se envía nada al servicio

  Escenario: Confirmar los cambios previsualizados
    Dado que hay cambios sin publicar
    Cuando el administrador publica y confirma
    Entonces el nuevo aspecto sustituye al publicado
    Y la versión anterior queda como respaldo y en las versiones de diseño
