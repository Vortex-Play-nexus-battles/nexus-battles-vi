# language: es
Característica: Recursos gráficos de un producto
  Como administrador del catálogo
  Quiero cargar y administrar las imágenes, iconos y animaciones de un producto
  Para componer su aspecto sin depender de un diseñador externo

  # Automatizado en: services/contenido/productos/src/test/java/nexus/recursos/
  # (ExaminadorDeRecursosTest, GestionarRecursosServicioTest, RecursosApiTest,
  # RecursosContraMongoIT), frontend/app-web/src/contenido/productos/
  # recursos-graficos.test.js y tests/e2e/recursos-graficos.e2e.spec.js.

  Antecedentes:
    Dado que existe un producto en el catálogo
    Y que el administrador está autenticado

  Escenario: Cargar una imagen, un icono o una animación y asociarla al producto
    Cuando el administrador sube un archivo PNG como imagen desde la ficha del producto
    Entonces el recurso queda en la biblioteca asociado al producto
    Y la ficha lo muestra con su formato, sus medidas y su peso
    Y al publicar el cambio la imagen del producto pasa a ser ese recurso

  Escenario: Reemplazar o retirar un recurso ya asociado
    Dado que un recurso es la imagen actual del producto
    Cuando el administrador intenta retirarlo
    Entonces se rechaza porque dejaría al producto sin imagen
    Cuando el administrador le pone otra imagen y lo retira con un motivo
    Entonces deja de aparecer en la biblioteca
    Y las versiones anteriores del diseño que lo usaban lo siguen mostrando

  Escenario: Rechazar un recurso que no cumple el formato o el tamaño admitidos
    Dado un archivo HTML con nombre de imagen, una imagen fija subida como animación o un icono de más de 512 px
    Cuando el administrador lo sube
    Entonces se rechaza indicando el requisito incumplido
    Y no se guarda nada
