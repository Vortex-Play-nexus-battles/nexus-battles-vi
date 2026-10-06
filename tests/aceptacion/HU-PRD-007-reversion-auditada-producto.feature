# language: es
@HU-PRD-007 @productos @administracion
Característica: Reversión de cambios con auditoría
  Como administrador del catálogo
  Quiero restaurar un producto desde su respaldo
  Para corregir un cambio sin perder la responsabilidad sobre lo ocurrido

  # Evidencia automática:
  # - RevertirProductoServicioTest.revierteAlEstadoAnterior
  # - RevertirProductoServicioTest.noPisaCambiosPosteriores
  # - ReversionProductoApiTest.consultaHistorialAdministrativo
  # - catalogo-admin.test.js: consulta el historial y revierte el último cambio

  Escenario: ofrecer la reversión después de detectar un cambio incorrecto
    Dado un producto modificado con un respaldo previo
    Cuando el administrador consulta su historial
    Entonces el último cambio se muestra como revertible

  Escenario: restaurar el estado anterior del producto y sus unidades
    Dado un producto cuya versión actual corresponde al respaldo elegido
    Cuando el administrador confirma la reversión
    Entonces el producto recupera los datos del estado respaldado
    Y las unidades del inventario vuelven a proyectar esos datos mediante su productoId

  Escenario: conservar una auditoría inmutable de la reversión
    Dado un cambio registrado con producto, autor y fecha
    Cuando el administrador ejecuta la reversión
    Entonces se agrega una nueva entrada que referencia el respaldo restaurado
    Y la API no ofrece operaciones para modificar o eliminar el historial

  Esquema del escenario: auditar cualquier cambio administrativo
    Dado un producto administrado
    Cuando se ejecuta una <accion>
    Entonces el historial agrega producto, autor, fecha y campos modificados

    Ejemplos:
      | accion       |
      | creación     |
      | modificación |
      | suspensión   |
      | reactivación |
      | reversión    |

  Escenario: no sobrescribir cambios posteriores
    Dado un respaldo que ya no produjo la versión actual del producto
    Cuando el administrador intenta revertirlo
    Entonces el servicio rechaza la operación con conflicto
