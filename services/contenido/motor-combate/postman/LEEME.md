# Validacion Postman de HU-JUE-004

Esta carpeta comprueba la superficie HTTP disponible de `HU-JUE-004` -
Influencia del equipamiento en los efectos. Actualmente Motor de Combate solo
expone los endpoints de Actuator, por lo que Postman valida que el
microservicio arranque y responda como saludable.

El recalculo de probabilidades y la conservacion de las 8.000 filas todavia
no se pueden invocar por HTTP. No se agrega un endpoint artificial para las
pruebas: esos criterios se comprueban con las pruebas de dominio existentes.

## Ejecutar el servicio

Desde la raiz del repositorio:

```powershell
$env:SERVER_PORT = "8093"
.\gradlew.bat :services:contenido:motor-combate:bootRun
```

## Ejecutar con Postman

1. Importar `hu-jue-004.postman_collection.json` y
   `local.postman_environment.json`.
2. Seleccionar el entorno `motor-combate - local`.
3. Ejecutar la coleccion completa y comprobar que las dos aserciones pasen.

Tambien se puede ejecutar con Newman:

```powershell
npx --yes newman run services/contenido/motor-combate/postman/hu-jue-004.postman_collection.json -e services/contenido/motor-combate/postman/local.postman_environment.json
```

## Validar la funcionalidad de HU-JUE-004

Hasta que exista un contrato y un endpoint de negocio, ejecutar:

```powershell
.\gradlew.bat :services:contenido:motor-combate:test --tests "nexus.combate.DistribucionEfectosEquipamientoTest" --tests "nexus.combate.TablaEfectosTest" --tests "nexus.combate.SelectorEfectoTest"
```

Estas pruebas verifican que el efecto del equipamiento cambie la distribucion,
que se mantengan exactamente 8.000 filas y que el selector respete la tabla.
