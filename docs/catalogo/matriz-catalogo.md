# Matriz del catálogo oficial (B4)

El catálogo del juego sale de la sección 6 del documento del curso: Tabla 6
(héroes), Tabla 7 (acciones), Tablas 8 a 19 (armas, armaduras e ítems) y
Tabla 20 (épicas). Su copia canónica, con los nombres exactos, es
[`contracts/esquemas/catalogo-oficial.yaml`](../../contracts/esquemas/catalogo-oficial.yaml)
(versión 1). Nada en el código, la semilla o la base puede tener otro nombre,
otro número ni un elemento de más o de menos: lo vigila
[`tests/contratos/catalogo-oficial.py`](../../tests/contratos/catalogo-oficial.py),
que corre en el job `ci-contratos` de `.github/workflows/ci.yml` junto con su
`--autoprueba` (diez desviaciones sembradas que tiene que detectar).

## Por tipo: dónde vive cada cosa

| Tipo | Cant. | Contrato (canónico) | Código | Semilla | Base de datos | Lo que compara el guardián |
|---|---|---|---|---|---|---|
| Héroes | 8 | `heroes`: nombre, sanador, estadísticas de nivel 1 (poder, vida, defensa, ataque, daño, sanar) | `services/contenido/heroes/src/main/java/nexus/dominio/PrototiposIniciales.java` (`VERSION = 1`) | `services/contenido/productos/src/main/resources/semilla/catalogo-inicial.json` → `heroes` (producto HEROE con su `prototipo`) | heroes: colección `prototipos` (perfil `mongo`). productos: colección `productos`, tipo HEROE | nombres exactos y estadísticas en `PrototiposIniciales` y en la semilla |
| Acciones | 24 | `acciones`: nombre, héroe, costo de poder (Reanimación sin costo fijo) | `PrototiposIniciales.java` (tres por prototipo) | no son productos: no están en la semilla de productos | heroes: embebidas en cada documento de `prototipos` | nombre, héroe y costo |
| Armas | 16 | `armas`: nombre, héroe, probabilidad de caída | inventario: `CatalogoEfectosEquipamiento.java` (efecto por nombre) | `catalogo-inicial.json` → `armas` | productos: `productos`, tipo ARMA | en la semilla, nombre, héroe y probabilidad; en los efectos, que cada nombre sea oficial |
| Armaduras | 16 | `armaduras`: además, la parte del cuerpo | `CatalogoEfectosEquipamiento.java` | `catalogo-inicial.json` → `armaduras` (con `parte`) | productos: `productos`, tipo ARMADURA con `parte`. El inventario usa esa parte como ranura al equipar | lo mismo que en armas, más la parte |
| Ítems | 8 | `items`: nombre, héroe, probabilidad de caída | `CatalogoEfectosEquipamiento.java` | `catalogo-inicial.json` → `items` | productos: `productos`, tipo ITEM | lo mismo que en armas |
| Épicas | 8 | `epicas`: nombre, héroe afín, probabilidad de caer como máster | heroes: `services/contenido/heroes/src/main/java/nexus/dominio/EpicasIniciales.java` | `catalogo-inicial.json` → `epicas` | productos: `productos`, tipo EPICA | nombre, héroe y probabilidad en `EpicasIniciales` y en la semilla |

El guardián también marca como error una clave de lista que no conoce, un
duplicado y un renombre (avisa si la diferencia es solo una tilde).

## Identificadores

Los productos sembrados tienen identificador fijo: un UUID derivado del slug
de la entrada (`MapeadorDelCatalogo.identificador`). Es el mismo en todos los
entornos, así que la tienda, el inventario y las subastas pueden referirse a
«Espada de una mano» por el mismo id en local, en el banco E2E y en AWS.

## Semilla versionada

Cada producto sembrado lleva `origen = SEMILLA` y `semillaVersion` (la
`version` del JSON). Al arrancar, productos pone al día su colección así:

| Situación del producto | Qué hace la semilla |
|---|---|
| no existe | lo inserta con `origen = SEMILLA` y la versión actual |
| sembrado con una versión menor y nadie lo editó | lo actualiza (conserva estado, fecha de alta, promoción y reservas de tiraje) |
| sembrado y ya en la versión actual | no hace nada |
| sembrado pero editado por un administrador (`modificadoPor`) | lo respeta y deja un aviso en la bitácora |
| dado de alta por un administrador (`origen = ADMINISTRACION`) | no lo toca |
| anterior a B4 (sin `origen`) con `version > 1` | lo trata como editado y lo respeta |

La actualización es condicional (solo si nadie lo editó entre la lectura y la
escritura), así que dos réplicas que arrancan a la vez no se pisan. La semilla
está activa por omisión en todos los entornos; se apaga con
`CATALOGO_SEMILLA=false`. Las pruebas con repositorio simulado la apagan en
`src/test/resources/config/application.properties`, y el banco E2E en
`tests/e2e/compose.yml`: tiene su propio catálogo (`sembrar.sh`), y la tienda
solo pinta la primera página de la vitrina.

Los prototipos de héroes siguen el mismo criterio (`PrototiposIniciales.VERSION`,
`origen`, `semillaVersion`, `modificadoPor` en `prototipos`) cuando el
servicio de héroes corre con el perfil `mongo`. Sin ese perfil el catálogo de
héroes vive en memoria y siempre es el oficial.

## Cómo se cambia el catálogo

1. Se cambia primero el contrato (`catalogo-oficial.yaml`), citando la tabla
   del documento que lo justifica.
2. Se cambian la semilla y el código que corresponda, y se sube la `version`
   del JSON (y `PrototiposIniciales.VERSION` si cambian héroes o acciones).
3. `python3 tests/contratos/catalogo-oficial.py` tiene que quedar en verde.
   Al desplegar, la semilla pone al día los productos que nadie editó.

## Lo que el documento no fija

- **Precios.** El documento no los trae (RF-ADM-03: los fija el
  administrador). Los de `preciosDemostracion` en la semilla son una decisión
  del PO para la demo (23-sep-2026), editables después desde la
  administración (HU-PRD-003).
- **Qué objetos puede llevar cada héroe.** Las tablas 8 a 19 asocian cada
  arma, armadura e ítem a un tipo de héroe (campo `heroe` del contrato), pero
  el documento no enuncia una regla que impida equipar los de otro tipo. La
  única afinidad con efecto que define es la de las épicas (Tabla 20). Por
  eso el inventario no la aplica al equipar. Queda como decisión del PO.
