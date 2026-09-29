# language: es
Característica: HU-JUE-011 Pérdida de equipo al ser derrotado
  Como jugador
  Quiero que un héroe derrotado pierda un objeto de su equipamiento
  Para que la victoria distribuya botín sin afectar el inventario almacenado

  Escenario: Perder un único objeto equipado
    Dado un héroe derrotado con varios objetos equipados
    Cuando se cierra la partida
    Entonces pierde únicamente el objeto con mayor porcentaje de caída

  Escenario: Conservar el inventario almacenado y los objetos épicos
    Dado un héroe derrotado con objetos almacenados y un objeto épico
    Cuando se calcula la pérdida de equipo
    Entonces los objetos almacenados permanecen con su propietario
    Y el objeto épico permanece con su propietario

  Escenario: Repartir el equipo perdido entre los ganadores
    Dada una partida por equipos con varios héroes derrotados y varios ganadores
    Cuando se cierra la partida
    Entonces cada objeto perdido se asigna aleatoriamente a un ganador
    Y el inventario procesa todas las asignaciones en una sola operación
    Y cada objeto existe una sola vez al finalizar
