# language: es
Característica: Rareza y habilidades en el listado de productos
  Como servicio consumidor del catálogo
  Quiero recibir la rareza y las habilidades de cada producto
  Para mostrar, buscar y filtrar productos sin duplicar reglas del catálogo

  Escenario: Publicar los efectos de un arma común
    Dado un arma con el efecto "+3 al ataque"
    Cuando consulto el listado público de productos
    Entonces el arma tiene rareza "COMUN"
    Y sus habilidades contienen "+3 al ataque"

  Escenario: Publicar los efectos de una habilidad épica
    Dado una habilidad épica con efecto general "Daño en área"
    Y efecto potenciado "Duplica el daño"
    Cuando consulto el listado público de productos
    Entonces la habilidad tiene rareza "EPICA"
    Y sus habilidades contienen "Daño en área" y "Duplica el daño"

  Escenario: Mantener vacías las habilidades no definidas
    Dado un héroe sin habilidades definidas en el catálogo de productos
    Cuando consulto el listado público de productos
    Entonces su rareza es "COMUN"
    Y sus habilidades están vacías
