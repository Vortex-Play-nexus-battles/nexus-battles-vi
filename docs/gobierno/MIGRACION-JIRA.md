# Migracion del backlog de Jira a GitHub

**Empresa A - The Nexus Battles VI: Return of the Warriors**
Proyecto Integrador II - UPB Bucaramanga

Este documento explica como el backlog que el equipo escribio en Jira se
traslada a Issues de GitHub y al Project de la organizacion, como volver a
ejecutar la migracion y que decisiones se tomaron por el camino.

---

## 1. Por que existe esta migracion

El backlog nacio en Jira, pero el trabajo del dia a dia ocurre en GitHub: las
ramas, los commits, los pull requests y las revisiones estan aqui. Tener el
backlog en un sitio y el codigo en otro obliga a actualizar dos herramientas
para el mismo avance, y una de las dos siempre se queda atras.

La migracion resuelve eso llevando cada historia y cada tarea de Jira a un
Issue de GitHub, y dando de alta ese Issue en el Project de la organizacion con
todos sus campos. A partir de ahi, un pull request puede cerrar una historia con
`Closes #42` y el tablero se mueve solo.

**Jira sigue siendo la fuente original de la redaccion.** Esta migracion es de
una sola direccion: de Jira hacia GitHub. Nada de lo que se cambie en GitHub
vuelve a Jira.

---

## 2. Que se migro

Migracion del **Sprint 1**, ejecutada sobre el proyecto `SCRUM` de Jira.

| Concepto | Cantidad |
| --- | --- |
| Historias de usuario | 53 |
| Tareas | 16 |
| **Total de incidencias migradas** | **69** |

Reparto por equipo, comprobado contra Jira una por una:

| Grupo en Jira | Responsable | Incidencias |
| --- | --- | --- |
| Grupo-6 | Simon | 27 |
| Grupo-2 | Thomas | 29 |
| Grupo-4 | Santiago | 13 |
| | **Total** | **69** |

Los Issues creados ocupan los numeros **#9 a #61** (historias) y **#63 a #78**
(tareas).

---

## 3. Las dos piezas

| Archivo | Que hace |
| --- | --- |
| `.github/workflows/migrar-backlog-jira.yml` | El boton. Se lanza a mano desde la pestana **Actions**, pide tres parametros y comprueba que existan los secretos antes de tocar nada. |
| `scripts/migrar-backlog.mjs` | El motor. Consulta Jira, traduce cada incidencia, crea el Issue y rellena los diez campos del Project. |

El script **no lleva una copia del backlog dentro del repositorio**. Lee Jira en
vivo cada vez que se ejecuta. Esto se decidio a proposito: una copia
versionada de 172 KB queda obsoleta el mismo dia que alguien edita una historia
en Jira, y nadie se entera.

---

## 4. Como se ejecuta

1. Entrar a la pestana **Actions** del repositorio.
2. Elegir **Migrar backlog de Jira** en la lista de la izquierda.
3. Pulsar **Run workflow**. Aparecen tres campos:

| Parametro | Valor por defecto | Para que sirve |
| --- | --- | --- |
| `simulacion` | `true` | Con `true` no crea nada: solo imprime la consulta, los campos que encontro en el Project y la lista completa de lo que crearia. |
| `jql` | `project = SCRUM AND issuetype in (Historia, Tarea) AND sprint IS NOT EMPTY ORDER BY key ASC` | La consulta de Jira. Cambiala para migrar otro sprint u otro tipo de incidencia. |
| `sprint` | `Sprint 1` | La iteracion del Project a la que se asignan las incidencias. |

> **Ejecutar siempre primero en simulacion.** Leer el registro, comprobar que el
> numero de incidencias cuadra con lo que muestra Jira, y solo entonces repetir
> con `simulacion` en `false`.

### Para el Sprint 2 y siguientes

Cambiar `sprint` a `Sprint 2` y ajustar el `jql` para que apunte a ese sprint,
por ejemplo:

```
project = SCRUM AND issuetype in (Historia, Tarea) AND sprint = "Sprint 2" ORDER BY key ASC
```

---

## 5. Secretos necesarios

Se configuran en **Settings > Secrets and variables > Actions**. Ninguno se
imprime en el registro de ejecucion.

| Secreto | Contenido |
| --- | --- |
| `JIRA_EMAIL` | Correo de la cuenta de Atlassian con acceso al proyecto `SCRUM`. |
| `JIRA_TOKEN` | Token de API de Atlassian de esa misma cuenta. |
| `TOKEN_MIGRACION` | Token de GitHub con permiso de escritura sobre los Issues del repositorio y sobre los proyectos de la organizacion. |

El sitio de Jira (`proyectonexusbattles.atlassian.net`) va escrito en el
workflow, no es un secreto.

> **Al terminar una migracion, revocar `TOKEN_MIGRACION`.** Es un token con
> permisos amplios que solo hace falta durante la ejecucion. Se vuelve a crear
> cuando toque migrar el siguiente sprint.

---

## 6. Que campo de Jira acaba donde

| Origen en Jira | Destino en GitHub |
| --- | --- |
| Clave (`SCRUM-123`) | Aparece en el cuerpo del Issue. Es la marca que hace idempotente la migracion. |
| Resumen | Titulo del Issue |
| Descripcion (formato ADF) | Cuerpo del Issue, aplanado a texto |
| Criterios de aceptacion | Cuerpo del Issue |
| Tipo de incidencia | Campo `Tipo` del Project y etiqueta del Issue |
| Prioridad | Campo `Prioridad` del Project |
| Estado | Campo `Status` del Project |
| Etiqueta de grupo | Campo `Equipo` del Project |
| Prefijo del codigo de historia | Campo `Modulo` del Project |
| Dependencias entre incidencias | Cuerpo del Issue, traducidas al espanol |
| (fijado por el parametro `sprint`) | Campo `Sprint` del Project |

La descripcion de Jira llega en **ADF** (Atlassian Document Format), que es un
arbol JSON y no texto plano. El script lo recorre y lo aplana, conservando
parrafos y listas.

---

## 7. Diccionarios de traduccion

El script traduce con tablas fijas, no adivinando. Las tres principales:

**Equipo** - de la etiqueta de grupo de Jira al responsable:

| Etiqueta en Jira | Equipo en el Project |
| --- | --- |
| `Grupo-2` | Thomas |
| `Grupo-4` | Santiago |
| `Grupo-6` | Simon |

**Modulo** - del prefijo del codigo de historia al modulo del sistema. Hay 25
prefijos registrados. Algunos ejemplos:

| Prefijo | Modulo |
| --- | --- |
| `HU-CICD` | CI/CD |
| `HU-POR`, `HU-REN`, `HU-DIS` | Infraestructura |

**Relacion** - los tipos de enlace de Jira llegan en ingles (`blocks`,
`is blocked by`) y se traducen al espanol antes de escribirlos en el cuerpo del
Issue.

Ademas hay diccionarios para `Tipo`, `Etiqueta`, `Prioridad`, `Estado` y la
carpeta del monorepo a la que pertenece cada modulo.

**Si aparece un prefijo o una etiqueta que no esta en el diccionario**, el
script lo avisa en el registro y deja el campo vacio. No inventa un valor.

---

## 8. Idempotencia: se puede volver a ejecutar sin duplicar

Antes de crear nada, el script lee los Issues que ya existen en el repositorio y
busca la clave de Jira (`SCRUM-123`) dentro del cuerpo de cada uno. Si la
encuentra, salta esa incidencia.

Consecuencias practicas:

- Volver a lanzar el workflow **no duplica** nada.
- Si la migracion se corta a la mitad, basta con relanzarla: continua donde se
  quedo.
- **Pero tampoco actualiza lo ya creado.** Si alguien reescribe una historia en
  Jira despues de la migracion, el Issue de GitHub se queda con el texto viejo.
  Hay que editarlo a mano.

---

## 9. Limitaciones conocidas

Se dejan escritas para que nadie las descubra por sorpresa.

1. **El campo `Requisito` quedo vacio en 40 de las 53 historias.** Esas
   historias no tienen un codigo `RF-XXX-000` en Jira. No se invento ninguno: es
   preferible un campo vacio a un dato falso. Se rellenan a mano cuando el
   equipo defina la trazabilidad con los requisitos funcionales.

2. **Las dependencias de los Issues creados antes de la correccion siguen en
   ingles.** La traduccion al espanol se anadio despues de la primera tanda y no
   se reescribieron los cuerpos ya publicados.

3. **La migracion no vuelve hacia Jira.** Cerrar un Issue en GitHub no cierra la
   incidencia en Jira.

4. **Los comentarios y los adjuntos de Jira no se migran.** Solo la descripcion y
   los criterios de aceptacion.

---

## 10. Historial

| Fecha | Cambio |
| --- | --- |
| Sprint 1 | Migracion inicial: 53 historias (#9-#61). |
| Sprint 1 | Correccion del `jql`: se anadio `Tarea` al filtro de tipos, que antes solo traia `Historia`. Se migraron las 16 tareas restantes (#63-#78). |
| Sprint 1 | Traduccion al espanol de los tipos de relacion entre incidencias. |
| Sprint 1 | El workflow y el script se llevan a `develop`, que es la rama donde trabaja el equipo. |
