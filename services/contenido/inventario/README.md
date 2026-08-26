# Servicio de inventario

Base de persistencia de `HU-INV-003`, subtarea `SCRUM-326`. El servicio usa
Java 21, Spring Boot 4.1 y Spring Data MongoDB.

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
```

El inventario conserva referencias al catalogo y no copia imagenes,
estadisticas, habilidades, efectos ni precios. Asi, las modificaciones globales
de productos pueden propagarse a todas las instancias, como exige `RF-ADM-10`.

El agregado es inmutable y se guarda como un documento por propietario. Esta
decision permite que los cambios de una instancia se persistan atomicamente y
sirve como base para la prueba de escritura fallida de `SCRUM-328`.

## Alcance actual

Incluido en `SCRUM-326`:

- modelo de inventario y elementos;
- referencia al propietario y al producto del catalogo;
- repositorio de dominio y adaptador Spring Data MongoDB;
- indice unico por propietario;
- pruebas unitarias y de integracion con MongoDB 8 mediante Testcontainers;
- compuerta JaCoCo de cobertura minima del 80 %;
- imagen Docker multietapa.

La API REST, la identidad autenticada, la autorizacion por propietario y los
casos de creacion y modificacion pertenecen a `SCRUM-327` y no se implementan
en esta subtarea.

Incluido en `SCRUM-318`:

- consulta del inventario por propietario en paginas de 16 elementos;
- metadatos `numero`, `tamanio`, `totalElementos`, `totalPaginas` y `ultima`;
- respuesta vacia con HTTP 200 cuando el jugador aun no tiene productos;
- rechazo con HTTP 400 de numeros de pagina negativos.

### Consulta para la vitrina

```http
GET /api/v1/inventarios/{propietarioId}/elementos?pagina=0
```

El tamano de pagina es fijo porque HU-INV-001 exige 16 productos en la vista de
referencia. El `propietarioId` es por ahora una entrada del caso de uso; la
validacion contra la identidad autenticada se conectara cuando el grupo de
plataforma publique el contrato de identidad (`HU-INF-009`). El endpoint no
debe exponerse fuera del entorno interno sin esa integracion.

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
