# ADR-006 — El percentil con que se evalúa RNF-REN-001 es p95, y lo decide el equipo

- **Estado:** aceptada
- **Fecha:** 23 de septiembre de 2026
- **Ámbito:** los 20 módulos (la medición la reparte `shared/libs/plataforma-observabilidad`)
- **Sustituye a:** la lectura de CA-03 de HU-REN-001 que exigía aprobación escrita del Product Owner
- **Relacionada con:** HU-REN-001, HU-REN-002, HU-REN-003, RNF-REN-001, D-17

## Contexto

RNF-REN-001 fija **500 ms** de latencia extremo a extremo. Ese número no es
negociable: lo fijan el requisito y el Project Charter, y el catálogo de
`admin-parametros` lo guarda como `plataforma.latencia-objetivo-ms` marcado
inalterable.

Lo que ningún documento fija es **en qué percentil se comprueba ese umbral**.
Una latencia no es un número, es una distribución: «bajo 500 ms» puede querer
decir en la mediana, en el p95, en el p99 o en el máximo, y cada lectura da un
veredicto distinto sobre el mismo servicio.

Al implementar HU-REN-001 se leyó el criterio CA-03 como si esa elección
tuviera que aprobarla el Product Owner por escrito. En consecuencia:

- `latencia.percentil` se dejó deliberadamente sin valor por omisión;
- `PropiedadesDeLatencia.objetivo()` devolvía `Optional.empty()`;
- `GET /latencia/informe` y `GET /consultas/informe` respondían **409** con un
  `problem detail` que decía que faltaba la aprobación.

El resultado, cuatro semanas después: la medición corría en los veinte módulos
y **el requisito no se podía evaluar en ninguno**. El informe técnico de
rendimiento que pide CA-02 no se podía emitir, y la evidencia de HU-REN-003
tampoco.

## Qué se revisó

Se buscó en todo el repositorio —Charter, `CLAUDE.md`, `.claude/rules/`,
contratos, SRS disponibles, catálogo de parámetros— una mención que fijara el
percentil. **No existe ninguna.** Todas las apariciones de `p95` en el
repositorio eran ejemplos de documentación o fixtures de prueba; todas las de
«500 ms» aparecen sin percentil.

El propio documento de control de rumbo ya lo había anotado: *«no es una
decisión del PO. Es una convención de medición de ingeniería»*.

## Decisión

**El percentil de evaluación de RNF-REN-001 es p95**, fijado como convención
de medición del equipo.

Vive en un único sitio del código,
`ObjetivoDeLatencia.PERCENTIL_POR_OMISION`, y no se copia en los veinte
`application.yml`. Se sigue pudiendo cambiar con `LATENCIA_PERCENTIL` sin
recompilar; lo que desaparece es el estado «sin decidir».

### Por qué p95 y no p99

Con las ventanas que este registro mantiene —10 000 muestras por proceso, en
memoria— un p99 se calcula sobre las 100 peores muestras. A esa escala, una
sola pausa del recolector de basura o un arranque en frío de un contenedor
mueve la cifra, y el informe pasaría de CUMPLE a NO CUMPLE por ruido y no por
el servicio. p95 se apoya en 500 muestras y describe la experiencia típica sin
quedar a merced de un valor atípico.

Subir a p99 para una campaña de medición concreta es cambiar una variable de
entorno.

### Por qué esto no invade la decisión de nadie

Un umbral de negocio —«la plataforma responde en medio segundo»— es una
promesa al cliente y la fija él. La convención estadística con la que se
comprueba esa promesa es una decisión de método, del mismo tipo que elegir con
qué herramienta se mide o cada cuánto se muestrea. El Product Owner puede
revisarla como cualquier otra decisión técnica; no hace falta que la origine.

Si el cliente llegara a fijar el percentil por escrito y fuera otro, la
adaptación es una variable de entorno y una línea en este documento.

## Consecuencias

- `PropiedadesDeLatencia.objetivo()` devuelve un `ObjetivoDeLatencia`, ya no
  un `Optional`.
- Desaparecen los dos `409` (`percentil-no-acordado`) de `LatenciaController` y
  `ConsultasController`, y con ellos sus respuestas en
  `contracts/openapi/metricas-plataforma.yaml` (1.6.0 → **1.7.0**). No se
  retira ninguna operación: solo una respuesta que el servicio ya no puede
  producir.
- `LATENCIA_PERCENTIL=` (definida pero vacía) degrada a p95 en vez de impedir
  el arranque. Hay prueba para ese caso.
- **HU-REN-001 CA-03 deja de estar bloqueada.** Lo que quedaba pendiente era
  esta decisión, no trabajo de implementación.
- El informe de latencia y el de consultas vuelven a poder anexarse como
  evidencia de Sprint.

## Alternativas descartadas

**Seguir esperando la aprobación.** Es lo que estaba pasando. Nadie la había
pedido formalmente porque el requisito del que salía la exigencia no existe, y
mientras tanto un RNF comprometido no se podía evaluar.

**Escribir `95` en cada `application.yml`.** Reparte la misma decisión por
veinte archivos de tres equipos y garantiza que en unos meses dos módulos
midan distinto sin que nadie lo note.

**Evaluar contra el máximo.** Es el criterio más estricto y el más inútil: un
único pico convierte en incumplidor a un servicio que atiende el 99,99 % de
las peticiones en 80 ms.
