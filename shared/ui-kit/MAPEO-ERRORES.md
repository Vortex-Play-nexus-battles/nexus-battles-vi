# Mapeo de errores: problem details (RFC 7807) → interfaz

**Regla 4 de plataforma:** formato de error estándar, idéntico en los 20 módulos.
Este documento cierra la otra mitad de esa regla: **cómo se pinta ese error**, para que
los tres equipos no rendericen el mismo fallo de veinte formas distintas.

Es de obligado cumplimiento para todo módulo que muestre un error al usuario.

Dueño: `shared/ui-kit` — cambios con revisión de los tres Scrum Masters.

---

## 1. El contrato de entrada

Todo servicio devuelve los errores con `Content-Type: application/problem+json` y este
cuerpo. Está definido en `contracts/openapi/rbac.yaml` y en
`contracts/openapi/salas-partidas.yaml`, y es idéntico en ambos.

```json
{
  "type":     "https://nexusbattles.local/errores/creditos-insuficientes",
  "title":    "Créditos insuficientes",
  "status":   422,
  "detail":   "Tienes 240 créditos y necesitas 400 para crear esta sala.",
  "instance": "/api/v1/salas",
  "errores":  [ { "campo": "recompensaCreditos", "mensaje": "Supera tu saldo." } ]
}
```

`errores` es opcional y aparece solo en validaciones de formulario.

---

## 2. Regla de oro

> **La interfaz decide por `type` y por `status`. Nunca por el texto de `title` o `detail`.**

`title` y `detail` son texto para el usuario y pueden cambiar de redacción sin previo
aviso. `type` es un identificador estable: es lo único sobre lo que se puede programar.

Nunca comparar cadenas de texto para decidir qué pintar.

---

## 3. Dónde va cada campo

| Campo del error | Dónde se pinta | Notas |
|---|---|---|
| `title` | Título del componente `Aviso` | Corto. Sin punto final. |
| `detail` | Cuerpo del componente `Aviso` | Frase completa dirigida al jugador. |
| `status` | Decide la variante `Tipo` del `Aviso` | Ver tabla 4. |
| `type` | No se muestra | Decide qué componente se usa y qué acción se ofrece. |
| `instance` | No se muestra | Solo para la bitácora. |
| `errores[]` | `Campo` en variante `Invalido`, mensaje debajo | Uno por campo. Ver §6. |

**`instance` y `type` nunca se muestran al usuario.** Son URIs internas.

---

## 4. `status` → variante del `Aviso`

El componente `Aviso` tiene exactamente cuatro variantes: `Error`, `Advertencia`,
`Exito`, `Informacion`.

| `status` | Variante | Por qué |
|---|---|---|
| `400` `422` | `Advertencia` | El jugador puede corregirlo y reintentar. No es un fallo del sistema. |
| `401` `403` | `Advertencia` | Falta permiso o sesión. Se resuelve entrando de nuevo. |
| `404` | `Informacion` | Lo buscado ya no está. No hay nada que corregir. |
| `409` | `Advertencia` | Conflicto de estado: la sala se llenó, la partida empezó. |
| `429` | `Advertencia` | Demasiadas peticiones. Reintentar más tarde. |
| `5xx` | `Error` | Fallo del sistema. El jugador no puede hacer nada. |

`Exito` no procede de un error: se reserva para confirmaciones.

---

## 5. Dónde aparece el error: tres sitios, no uno

Elegir según **de dónde vino** el error, no según su gravedad.

### 5.1 El error impide pintar la vista entera → `Estado de vista`

La carga de una vista completa falla. Variante `Error`. La vista no se pinta.
Lleva siempre un botón de reintentar.

Ejemplo: el listado de batallas no responde.

### 5.2 El error afecta a una acción concreta → `Aviso`

El jugador pulsó algo y falló. La vista sigue en pie; el aviso aparece junto a la acción
o en la parte superior del formulario.

Ejemplo: crear sala sin créditos suficientes.

### 5.3 El error es de un campo → `Campo` en variante `Invalido`

Ver §6.

### 5.4 Caso aparte: la conexión en tiempo real

La caída del canal WebSocket **no** es un `Aviso`. Se pinta con `Estado de conexion`,
que tiene sus propias variantes: `Estable`, `Latencia alta`, `Reconectando`,
`Sin conexion`. Un `Aviso` de error aquí sería ruido, porque el sistema se está
recuperando solo.

---

## 6. Errores de formulario

Cuando llega `errores[]`, **no** se muestra un `Aviso` con la lista dentro.

1. Cada entrada marca su `Campo` en variante `Invalido` y escribe `mensaje` debajo.
2. El foco va al primer campo inválido.
3. Solo si además hay un motivo general se añade un `Aviso` encima del formulario.

Esto cumple el requisito de indicar **el motivo** del rechazo, no solo que falló.

---

## 7. Tipos de error con tratamiento propio

Estos `type` no se pintan con el `Aviso` genérico porque el requisito exige un diálogo
concreto. La lista crece con cada módulo; añadir aquí al definir el `type`.

| `type` (sufijo) | Origen | Tratamiento | Requisito |
|---|---|---|---|
| `heroe-no-equipado` | `salas-partidas` | `Dialogo de validacion de heroe`, variante sin héroe. Lleva al inventario. | RF-JUE-003 |
| `heroe-ocupado` | `salas-partidas` | `Dialogo de validacion de heroe`, variante ocupado. Nombra la sala. | RF-JUE-003 |
| `creditos-insuficientes` | `salas-partidas` | `Aviso` que dice cuántos créditos hay y cuántos faltan. | RF-JUE-001, RF-JUE-014 |
| `sesion-caducada` | cualquiera | No es `Aviso`: lleva a iniciar sesión conservando a dónde iba. | — |

---

## 8. Sesión caducada

Un `401` con `type` de sesión caducada **nunca** se pinta como un aviso más.

1. Se conserva la ruta a la que iba el jugador.
2. Se le lleva a iniciar sesión.
3. Al volver, se le devuelve a donde estaba.

Si ocurre **en mitad de un combate**, primero se muestra `Estado de conexion` en
`Sin conexion`, porque perder la partida por un salto de pantalla es peor que esperar.

---

## 9. Qué no hacer

- No mostrar `type` ni `instance` al usuario. Son URIs internas.
- No mostrar la traza ni el `status` como número.
- No usar `Error` para un fallo que el jugador puede corregir: eso es `Advertencia`.
- No decidir nada comparando el texto de `title` o `detail`.
- No dejar un `Aviso` de error sin salida: o hay reintento, o hay una acción alternativa.
- No apilar avisos. Uno a la vez por contexto; el nuevo reemplaza al anterior.

---

## 10. Referencia rápida

```
¿falló la vista entera?      → Estado de vista · Error       + reintentar
¿falló una acción?           → Aviso · según status          + salida
¿falló un campo?             → Campo · Invalido              + mensaje debajo
¿se cayó el canal?           → Estado de conexion            (no es un Aviso)
¿caducó la sesión?           → iniciar sesión, guardando destino
¿el type tiene diálogo?      → tabla §7, manda sobre lo anterior
```
