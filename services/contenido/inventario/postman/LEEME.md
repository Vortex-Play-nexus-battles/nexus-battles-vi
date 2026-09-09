# Validacion con Postman del servicio de inventario

Coleccion con **aserciones** para HU-INV-003 (creacion y edicion de elementos
propios) y HU-INV-005 (equipamiento con limites). Cada peticion verifica el
criterio de aceptacion que le corresponde y las peticiones estan encadenadas:
cada una guarda los identificadores que usan las siguientes, asi que se corre
la coleccion **completa y en orden**.

## Archivos

| Archivo | Que es |
|---|---|
| `inventario.postman_collection.json` | 32 peticiones agrupadas por historia |
| `local.postman_environment.json` | Entorno local: `baseUrl` |

## Requisitos

- El servicio en ejecucion con un MongoDB alcanzable. Desde la raiz del monorepo:

```bash
SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/inventario ./gradlew :services:contenido:inventario:bootRun
```

- La identidad viaja en la cabecera `X-User-Name` (convencion temporal hasta
  HU-AUT-004). La coleccion crea dos jugadores con sufijo de fecha para que
  cada corrida use inventarios nuevos y no dependa de datos previos.

## Con la app de Postman

1. **Import** y arrastrar los dos archivos.
2. Elegir el entorno `inventario - local` (ajustar `baseUrl` si el servicio
   corre en otro puerto).
3. Clic derecho sobre la coleccion → **Run collection** → **Run**. Todo debe
   quedar en verde; cada asercion dice que criterio verifica.

## Desde consola (Newman)

```bash
npx --yes newman run inventario.postman_collection.json -e local.postman_environment.json
```

Contra otro puerto: `--env-var baseUrl=http://localhost:8082`.

## Que cubre

| Historia | Criterio | Peticiones |
|---|---|---|
| HU-INV-003 | Crear y modificar quedan persistidos y se ven en la vitrina | Crear heroe (201 + Location), renombrar (200), vitrina en pagina de 16 |
| HU-INV-003 | Operar sobre el inventario de otro no se permite | A renombra el elemento de B → 403 "Inventario ajeno"; B sigue intacto |
| HU-INV-003 | Errores legibles | 401 sin identidad, 400 sin nombre, 404 inexistente |
| HU-INV-005 | Maximo dos armas | 2 equipan, la tercera → 409 "dos armas"; la misma dos veces → 409 |
| HU-INV-005 | Seis partes de armadura, una por ranura | CASCO ocupa, segundo CASCO → 409; PECHO cabe |
| HU-INV-005 | Maximo dos items | 2 equipan, el tercero → 409 |
| HU-INV-005 | Solo se equipan armas, armaduras e items | Equipar el heroe → 400 "Elemento no equipable" |
| HU-INV-005 | Liberar ranura | Desequipar el arma 1 → cabe el arma 3 |
| HU-INV-005 | Propiedad | B consulta el heroe de A → 403; sin identidad → 401 |

Cuando cambie el contrato (`contracts/openapi/inventario.yaml`), actualizar
aqui la peticion afectada en el mismo cambio.
