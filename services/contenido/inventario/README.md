# Servicio de inventario

Servicio de inventario de `HU-INV-003` y equipamiento con limites de
`HU-INV-005`. Usa Java 21, Spring Boot 4.1 y Spring Data MongoDB.

## Modelo

Cada jugador tiene un unico documento `inventarios`:

```text
Inventario
|- id                 identificador MongoDB
|- propietarioId      identificador unico del jugador
`- elementos
   |- id              identificador de la instancia poseida
   |- productoId      referencia al catalogo de productos
   |- tipo            HEROE, HABILIDAD, ARMA, ARMADURA, ITEM o EPICA
   `- nombrePropio     dato editable de la instancia
`- equipamientos
   |- heroeId           instancia HEROE del mismo inventario
   |- armas             maximo dos identificadores de elemento
   |- armaduras         una por CASCO, PECHO, GUANTES, BRAZALETES,
   |                    PANTALON y ZAPATOS
   `- items             maximo dos identificadores de elemento
```

El inventario conserva referencias al catalogo y no copia imagenes,
estadisticas, habilidades, efectos ni precios. Asi, las modificaciones globales
de productos pueden propagarse a todas las instancias, como exige `RF-ADM-10`.

El agregado es inmutable y se guarda como un documento por propietario. Esta
decision permite que los cambios de una instancia se persistan atomicamente y
sirve como base para la prueba de escritura fallida de `SCRUM-328`.

## API de elementos propios

La API deriva el propietario de `X-User-Name`, la convencion temporal de
`ms-identidad`. El cliente nunca envia ni puede elegir `propietarioId`. Cuando
`HU-AUT-004` publique JWT, este encabezado se reemplazara por el `subject` del
token sin cambiar las reglas de propiedad de la aplicacion.

```text
POST  /api/v1/inventario/elementos
PATCH /api/v1/inventario/elementos/{elementoId}
```

`PATCH` solo permite modificar elementos del inventario autenticado. Intentar
modificar el de otro jugador responde `403` y no altera los datos. El contrato
completo esta en `contracts/openapi/inventario.yaml`.

## Equipamiento con limites

`HU-INV-005` guarda el equipamiento en el mismo documento del inventario para
que ocupar o liberar una ranura sea una escritura atomica. El destino debe ser
una instancia `HEROE` propia y el elemento debe existir en el mismo inventario.
Una instancia no puede estar equipada simultaneamente en dos heroes.

```text
GET    /api/v1/inventario/heroes/{heroeId}/equipamiento
PUT    /api/v1/inventario/heroes/{heroeId}/equipamiento/{elementoId}
DELETE /api/v1/inventario/heroes/{heroeId}/equipamiento/{elementoId}
```

### Estado de las dependencias

- `HU-HER-001` esta disponible. Su contrato publica prototipos de heroe por
  nombre (`GET /api/v1/heroes`), no instancias poseidas por un jugador. Por ese
  limite de contrato, inventario no importa sus clases: `heroeId` identifica la
  instancia propia almacenada en inventario y `productoId` conserva la
  referencia al catalogo.
- `HU-PRD-001` aun no esta disponible en `develop` y no se inventa un endpoint
  que no existe. Temporalmente se conserva en el elemento el tipo y, para
  armaduras, la parte que define la ranura. Cuando productos publique su
  consulta por identificador, la entrada debera obtener esos metadatos del
  catalogo y dejar de aceptar `tipo` y `parteArmadura` como datos declarados por
  el cliente. Las reglas de dos armas, seis partes y dos items no dependen de
  ese cambio.

## Alcance actual

Incluido en `SCRUM-326`:

- modelo de inventario y elementos;
- referencia al propietario y al producto del catalogo;
- repositorio de dominio y adaptador Spring Data MongoDB;
- indice unico por propietario;
- pruebas unitarias y de integracion con MongoDB 8 mediante Testcontainers;
- compuerta JaCoCo de cobertura minima del 80 %;
- imagen Docker multietapa.

Incluido en `SCRUM-327`:

- API REST para crear y renombrar elementos;
- propietario derivado exclusivamente de la identidad autenticada;
- autorizacion de modificaciones por propietario;
- errores RFC 9457 para solicitudes invalidas y acceso denegado;
- contrato OpenAPI y pruebas de aplicacion, API y persistencia.

La visualizacion completa en vitrina sigue dependiendo de `HU-INV-001`. La
integracion con esa vitrina no forma parte de estas subtareas.

## Atomicidad

`SCRUM-328` conserva todos los elementos de un propietario dentro de un unico
documento MongoDB. Crear o renombrar produce una nueva version inmutable del
agregado y el adaptador la guarda mediante una sola operacion `save`; MongoDB
garantiza que una escritura sobre un documento es atomica.

Si Spring Data informa un fallo, la API responde `503` sin exponer detalles de
MongoDB. Como no existe una escritura previa sobre otra coleccion ni se modifica
el agregado persistido en memoria, el inventario conserva su estado completo
anterior y no quedan elementos parcialmente actualizados.

## Prueba de aceptacion de propiedad

`SCRUM-329` ejecuta la API Spring completa contra MongoDB 8 con Testcontainers.
El escenario crea inventarios separados para los jugadores A y B, comprueba que
cada alta se asocia exclusivamente a la identidad autenticada y hace que A
intente modificar un elemento de B. La respuesta debe ser `403` y el documento
de B, consultado nuevamente desde MongoDB, debe ser exactamente igual al estado
anterior al intento.

Con esto quedan cubiertos en backend la creacion, modificacion, propiedad y
atomicidad de `HU-INV-003`. La comprobacion visual final sigue pendiente hasta
que `HU-INV-001` publique la vitrina del inventario.

## Pruebas

En Windows, con `JAVA_HOME` apuntando a un JDK 21:

```powershell
.\gradlew.bat clean test jacocoTestCoverageVerification
```

Las pruebas de integracion se omiten automaticamente si Docker no esta
disponible. En GitHub Actions se ejecutan contra un contenedor efimero de
MongoDB 8.

Para ejecutar el servicio, MongoDB se configura con la variable estandar de
Spring Boot `SPRING_DATA_MONGODB_URI`.
