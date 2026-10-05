# Resultado de combate: que dato es real y que dato no existe — R10

Clasificacion de **todo** lo que la pantalla de final de partida podria mostrar,
en las cuatro categorias que pidio R10. La regla que la ordena es una: **no se
muestra recompensa ni experiencia ficticia**. Un numero que el servidor no
conoce no se pinta, ni como cero, ni como guion, ni como «pendiente».

Fecha del barrido: 2026-09-24. Superficie unica:
`[data-zona="resultado"]` en `sala-batalla.html`, que rellena
`panelDeResultado` desde `combate.js`. No hay otra vista de resultado.

## Tabla

| Dato | Categoria | De donde sale, exactamente |
|---|---|---|
| **Ganador** | Autoritativo del servidor | `ganadores[]` y `equipoGanador` de `AvisoDePartidaFinalizada`. El servidor deja `ganadores` vacio cuando nadie quedo en pie, y el contrato lo dice: «Vacio si nadie quedo en pie (empate; el desempate es del PO)» |
| VICTORIA / DERROTA / EMPATE, icono, frases | Derivado en el frontend | `desenlaceDe` y `textoDelResultado`, con la MISMA regla (`gano`/`empate`), no adivinando de un texto traducible |
| **Creditos de la apuesta** | Autoritativo del servidor | `reparto[].creditos`, que calcula `LiquidarApuesta` y liquida `ms-finanzas` de verdad: la reserva del ganador se libera y las de los perdedores se consumen a su favor. Ausente mientras la liquidacion este `PENDIENTE`, y entonces **no se pinta nada** en vez de un cero |
| **Recompensa por jugar** | Autoritativo del servidor | `recompensa[].creditos/.ganador/.cofre`. Los importes son de `ms-finanzas` (`AcreditacionPartidaService`), no de salas-partidas, que a proposito no tiene constantes |
| Cifra grande del panel | Derivado en el frontend | `netoDeCreditos` = apuesta + recompensa. Sumar dos cifras del servidor no inventa ninguna; el resultado es lo que de verdad le paso al saldo |
| Cofre | Fuera de alcance de esta pantalla | Llega como UUID en `recompensa[].cofre` y solo se dice «Ademas te llevas un cofre». Nombre y rareza no viajan en este aviso |
| **Torneo** | Fuera de alcance de esta pantalla | El resultado **si** se informa al servicio de torneos (`InformarEncuentroDeTorneo`), pero la pantalla no lo menciona. No hay campo que falsear; tampoco confirmacion al jugador |
| **Experiencia** | **No implementado** | Ver abajo |
| `reparto[].experiencia` | **No implementado** (contrato que promete de mas) | Declarado en `contracts/websocket/salas-partidas.yaml` y **el record de Java no tiene ese componente**, asi que la clave no viaja ni como nula |
| `reparto[].cofre` (objeto) | **No implementado** (contrato que promete de mas) | Igual: declarado en el AsyncAPI, sin campo en `Reparto` |

## Experiencia: por que no se muestra

`services/contenido/heroes` publica `POST /api/v1/progresion/experiencia` y
`GET /api/v1/progresion/experiencia-por-enemigo/{dado}`, con sus reglas
probadas. **Nadie las llama.** Un barrido del repositorio entero solo encuentra
referencias dentro de heroes: su controlador, su configuracion de seguridad, sus
pruebas y su contrato.

Y no hay donde guardar el resultado. El contrato de heroes dice que «el estado
del heroe (nivel y experiencia) lo guarda el inventario; aqui no se persiste
nada», pero el documento del inventario (`ElementoDocumento`) no tiene columna
de nivel ni de experiencia. El contrato delega en un servicio que no lo
implementa.

Para que la experiencia funcionara harian falta, en este orden:

1. Persistencia de nivel y experiencia por elemento de inventario de tipo
   HEROE, con su RF y su contrato.
2. Un puerto de salida en salas-partidas, al lado de `apuesta` y `recompensa`,
   que llame a la progresion al terminar con la credencial de servicio.
3. El campo `experiencia` en el aviso — ya reservado en el AsyncAPI.
4. Pintarlo.

Nada de eso existe. **Consecuencia aceptada y visible:** la pantalla no dice
nada de experiencia. Es preferible al cero, que se leeria como «no ganaste
nada» cuando la verdad es «esto todavia no se cuenta».

## Defectos que R10 encontro y arreglo

**1. La cifra grande mentia por omision.** Salia de `recompensa`, que **nunca
resta** (`CreditoPorPartida` rechaza negativos y el contrato la declara
`minimum: 0`). Quien perdia una apuesta de 350 creditos veia un `+2` enorme y la
perdida solo en la frase de abajo; la rama `data-signo="negativo"` del panel era
inalcanzable desde produccion. Ahora es la suma de las dos.

**2. El empate se pintaba como derrota.** `panelDeResultado` tenia dos estados,
asi que una partida sin vencedor mostraba el escudo y la palabra DERROTA
mientras el texto decia «Combate terminado en empate». El kit ya tenia un
`.resultado--empate` sin usar, en un bloque de CSS muerto. Ahora hay tres
estados.

**3. El final se anuncia por partes y el segundo aviso borraba el primero.**
Cuando la liquidacion de la apuesta llega tarde, el servidor manda un segundo
`partida.finalizada` con `recompensa` vacia. El panel se repintaba solo con ese,
asi que la recompensa ya anunciada desaparecia y la cifra grande cambiaba de
significado a mitad. Ahora se acumulan por jugador.

## Deuda registrada, no arreglada aqui

- **El AsyncAPI promete dos campos que ningun codigo emite**
  (`reparto[].experiencia`, `reparto[].cofre`). Un contrato que declara mas de
  lo que existe es tan enganoso como una ruta sin declarar, del reves: el
  consumidor los lee y programa contra ellos. Quitarlos es un cambio de contrato
  con su version, y toca a un consumidor que no es solo esta vista.
- **El torneo no se confirma al jugador**, y hay dos casos en que el resultado
  no llega a registrarse: cuando gana la maquina o hay empate (queda para el
  administrador, por decision explicita) y cuando el entorno no tiene
  `salas.torneos.url` configurada. Las dos se registran como enlace fallido, no
  en silencio.
- **CSS muerto** en `componentes.css` con un comentario que promete «muestra la
  experiencia ganada y ofrece revancha». Ni una cosa ni la otra existen. El
  bloque `.resultado*` no lo usa nadie.
- **El boton de revancha** nunca se pinta: `panelDeResultado` acepta `acciones`
  y ninguna llamada las pasa.
