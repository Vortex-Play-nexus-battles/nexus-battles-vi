# language: es
Característica: Pujar o comprar de forma inmediata en una subasta
  Como jugador
  Quiero pujar por un producto superando la oferta actual, comprarlo de inmediato si tiene ese precio, o configurar una puja automática hasta un límite
  Para competir por adquirir el producto sin perder tiempo revisando constantemente

  Escenario: Una puja que supera la oferta vigente y el incremento mínimo se acepta
    Dado una subasta activa con oferta vigente de 100 créditos e incremento mínimo de 10
    Cuando un jugador distinto del vendedor puja 110 créditos
    Entonces la puja se acepta y queda como oferta vigente
    Y se reservan 110 créditos del nuevo ofertante

  Escenario: Una puja que no supera el incremento mínimo se rechaza
    Dado una subasta activa con oferta vigente de 100 créditos e incremento mínimo de 10
    Cuando un jugador puja 105 créditos
    Entonces la puja se rechaza por oferta insuficiente

  Escenario: El postor superado recupera sus créditos reservados
    Dado una subasta con una puja vigente de un primer jugador
    Cuando un segundo jugador puja por encima de la oferta vigente
    Entonces los créditos reservados del primer jugador se liberan
    Y los créditos del segundo jugador quedan reservados

  Escenario: Nadie puede pujar en su propia subasta
    Dado una subasta activa publicada por un jugador
    Cuando ese mismo jugador intenta pujar en su subasta
    Entonces la puja se rechaza por ser el vendedor

  Escenario: Se respeta el intervalo mínimo de 5 segundos entre pujas del mismo jugador
    Dado un jugador que pujó hace menos de 5 segundos en la misma subasta
    Cuando ese jugador intenta pujar de nuevo
    Entonces la puja se rechaza por no cumplir el intervalo mínimo

  Escenario: La compra inmediata exige confirmación y cierra la subasta
    Dado una subasta activa con precio de compra inmediata definido
    Cuando un jugador confirma la compra inmediata
    Entonces el producto se transfiere al instante
    Y la subasta queda cerrada y adjudicada
    Y se notifica a quienes hubieran pujado

  Escenario: Los créditos reservados se restituyen si la subasta cierra sin adjudicación
    Dado una subasta con pujas activas que llega a su hora de cierre sin compra inmediata
    Cuando la subasta se cierra sin adjudicación
    Entonces todos los créditos reservados de los postores se restituyen automáticamente

  Escenario: La puja automática emite ofertas hasta su límite y se detiene notificando
    Dado un jugador con una puja automática configurada hasta un límite de 200 créditos
    Cuando la oferta vigente sube y el siguiente incremento superaría su límite
    Entonces la puja automática se detiene
    Y se notifica al jugador que alcanzó su límite

  Esquema del escenario: Se aplican los 4 límites de participación configurables
    Dado un jugador que intenta pujar
    Cuando se supera el límite de "<limite>"
    Entonces la puja se rechaza por ese límite

    Ejemplos:
      | limite                                   |
      | máximo 10 subastas activas simultáneas    |
      | máximo 50 pujas activas simultáneas       |
      | intervalo mínimo de 5 s entre pujas        |
      | prohibición de pujar en subastas propias  |
