# ADR-003 · Código de invitación, salida y cancelación de sala

- **Estado:** aceptada
- **Fecha:** 2026-09-17
- **Contexto de decisión:** Equipo 6 (M09 — Juego en línea), operaciones `ingresarASala`,
  `abandonarSala` y `cancelarSala` de `contracts/openapi/salas-partidas.yaml`
- **Sustituye a:** nada. **Relacionada con:** ADR-002 (identidad de usuario)

## Contexto

El contrato OpenAPI de salas declara ocho operaciones. Tres estaban implementadas. De las cinco
que faltaban, tres no dependen de ningún otro módulo y son las que resuelve esta decisión:
`obtenerSala`, `cancelarSala` y `abandonarSala`. Al implementarlas aparecieron tres huecos que
el contrato no cerraba y que había que resolver antes de escribir una línea.

### Hueco 1 — el código de invitación no lo emitía nadie

`IngresoRequest.codigoInvitacion` existe en el contrato desde el Sprint 1 y está descrito como
«obligatorio únicamente si la sala es privada». `CrearSalaRequest` dice que a una sala privada
«solo se entra con el código de invitación». Pero **ninguna operación del contrato entregaba ese
código**, y ninguna columna lo guardaba.

El código del dominio lo reconocía por escrito. `Sala.unirse` traía este comentario:

> Una sala PRIVADA rechaza siempre, de momento. […] Sin forma de demostrar que alguien está
> invitado, dejar entrar a cualquiera sería peor que rechazar: convertiría «privada» en una
> etiqueta decorativa.

La decisión de entonces fue correcta —rechazar es más honesto que fingir— pero el efecto era
que **una sala privada no la podía abrir nadie, ni siquiera quien la creó**. Un flujo completo
del contrato estaba muerto.

### Hueco 2 — cancelar tenía que devolver créditos que nadie sabía identificar

`EstadoSala.CANCELADA` dice «el anfitrión la canceló antes de empezar; los créditos se
devuelven». `CreditosDelJugador.liberar` dice que se llama «cuando la sala no llega a existir o
cuando se cancela». Pero `CrearSala` descartaba el `ReservaDeCreditos` en cuanto guardaba la
sala: al cancelar no había forma de saber qué reserva liberar.

### Hueco 3 — qué pasa cuando el anfitrión se va

El contrato tiene `abandonarSala` (204) y `cancelarSala` (204, solo el anfitrión), y no dice qué
ocurre si el anfitrión llama a la primera.

## Decisión

### 1. El servidor genera el código; solo el anfitrión lo ve

Ocho caracteres de un alfabeto de 32 (`SecureRandom`), agrupados `XXXX-XXXX`. El alfabeto excluye
`I`, `O`, `0` y `1`, que se confunden al dictarlos o al copiarlos de una captura.

Se añade `codigoInvitacion` al esquema `Sala` como **campo opcional**, presente solo en las
respuestas dirigidas al anfitrión (`crearSala`, `obtenerSala`, `ingresarASala` cuando quien
pregunta es el dueño). A cualquier otro jugador el campo se omite del JSON — no viaja como
`null`, se omite. Quien ya entró no necesita la llave, y dársela convertiría a cada invitado en
repartidor de invitaciones.

La comparación normaliza mayúsculas, espacios y guiones: el código se reparte por chat y llega
pegado con el formato cambiado. Rechazar por eso sería rechazar a alguien que sí está invitado.

En el modelo, `SalaResponse` tiene **dos fábricas** (`desde` y `paraElAnfitrion`) en vez de una
con un `boolean`. Quien llama tiene que pedir el secreto por su nombre; es más difícil filtrar
algo que hay que nombrar que algo que viene puesto y hay que acordarse de quitar.

### 2. La sala guarda su reserva de créditos

Columna `id_reserva_creditos` (nullable, índice único parcial). `CrearSala` la anota en cuanto
el módulo de créditos responde; `CancelarSala` la libera.

El orden es: **guardar la cancelación → liberar los créditos → avisar**. Al revés, un fallo al
guardar dejaría al anfitrión con sus créditos de vuelta y la sala en pie y jugable. Y un fallo al
liberar **no tumba la cancelación**: la sala ya está cancelada en la base, así que propagar el
error haría que quien canceló viera un 500 y creyera que su sala sigue abierta. Se registra en la
bitácora con todo lo necesario para reclamarla a mano y se avisa de `creditosDevueltos: 0`, en
vez de prometer un número que no se cumplió.

### 3. El anfitrión no abandona: cancela

`abandonar` rechaza al anfitrión con 409 y el detalle le dice cuál es el camino correcto.

## Alternativas descartadas

**Que el anfitrión elija el código.** Un código escogido a mano es corto, memorable y adivinable
—que es justo lo que no queremos de una llave— y además obligaría a manejar colisiones.

**Guardar el código resumido (hash) en vez de en claro.** Un resumen sirve para comprobar, no
para mostrar, y hay que poder devolvérselo al anfitrión cuando vuelva a consultar su sala. Es un
código de acceso a una sala de juego, no una credencial de cuenta: la amenaza es colarse en una
partida, no suplantar a una persona.

**Dejar el campo `codigoInvitacion` fuera del contrato y entregarlo por otro canal** (correo,
notificación). Añade una dependencia de M15 a un flujo que no la necesita, y deja al anfitrión
sin forma de volver a consultarlo.

**Que el anfitrión al irse ceda la sala al siguiente participante.** Abre preguntas que ningún
requisito responde: quién hereda, quién recupera los créditos comprometidos, quién puede
cancelarla después. Responderlas por nuestra cuenta sería inventar requisitos.

**Borrar la sala al cancelarla en vez de marcarla.** Dejaría sin explicación a quienes estaban
dentro. Como el listado filtra por estado, una sala `CANCELADA` desaparece igual, pero conserva
su rastro.

## Consecuencias

- Las salas privadas pasan a ser utilizables de punta a punta: crear → repartir el código →
  entrar. El 403 del contrato deja de ser un rechazo universal y pasa a significar lo que dice.
- `Sala.unirse(UUID)` sigue existiendo como atajo sin código. En una sala privada rechaza, que es
  lo correcto, así que las 27 pruebas anteriores siguen siendo válidas sin tocarlas.
- La migración `V5` añade una restricción: una sala privada **debe** tener código y una pública
  **no puede** tenerlo. Las salas privadas que ya existían reciben uno generado en la propia
  migración; hoy no se pueden abrir de ninguna forma, así que cualquier código las mejora.
- Queda pendiente, y anotado: entre reservar y guardar no hay transacción distribuida
  (`CrearSala` ya lo documenta). Esta decisión no lo empeora ni lo resuelve.
- `MotivoDeCancelacion` declara los tres valores del AsyncAPI, pero solo
  `CANCELADA_POR_ANFITRION` tiene productor. `INACTIVIDAD` necesita un plazo que ningún requisito
  fija; elegirlo por nuestra cuenta sería inventar un umbral.

## Cómo se verificó

- `SalaTest`: 20 pruebas nuevas en tres bloques (invitación, salida, cancelación), incluida la de
  que 50 salas privadas seguidas no comparten código.
- `AbandonarSalaTest`, `CancelarSalaTest`, `ObtenerSalaTest`: la coordinación de cada caso de uso,
  con dobles del repositorio, del canal y del módulo de créditos.
- `SalasControllerTest`: los tres endpoints nuevos y, por cada camino que devuelve una sala, una
  prueba de que el código **no** se filtra a quien no le toca.
- `CanalDeSalaIT`: los avisos `sala.participante.salio` y `sala.cancelada` llegan por STOMP con el
  payload del contrato.
