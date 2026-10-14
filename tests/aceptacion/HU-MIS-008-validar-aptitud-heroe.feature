# language: es
#
# Evidencia automatizada:
# - C1: MatricularHeroeTest.matricula
# - C2 y C5: MatricularHeroeTest.noApto y ServicioDeMisionesIT
# - C3: MatricularHeroeTest.heroeOcupado
# - C4 (presentacion del rechazo): misiones.test.js
# - C6: MatricularHeroeTest.revalidaDespuesDeCorregirElEquipo
#
# Pendiente de otro modulo:
# - C4 no puede comprobar de extremo a extremo que el heroe participa en un torneo:
#   el contrato actual de torneos no selecciona ni reserva heroes. La pantalla ya
#   conserva y muestra el motivo cuando el servidor lo publica.
Característica: HU-MIS-008 Validar la aptitud de un héroe para una misión
  Como jugador
  Quiero conocer todas las condiciones que incumple el héroe seleccionado
  Para corregirlas antes de confirmar el inicio de una misión

  Escenario: Aceptar un héroe apto
    Dado un héroe propio con equipo, fuera de otras misiones y disponible
    Cuando el jugador confirma el inicio de una misión disponible
    Entonces el sistema informa que el héroe cumple las condiciones previas
    Y permite iniciar la misión

  Escenario: Rechazar un héroe sin el mazo completo
    Dado un héroe propio que no tiene ningún elemento equipado
    Cuando el jugador intenta iniciar una misión con ese héroe
    Entonces el sistema impide continuar
    Y le pide completar el mazo del héroe

  Escenario: Rechazar un héroe asignado a otra misión
    Dado un héroe propio asignado a otra misión activa
    Cuando el jugador intenta iniciar una nueva misión con ese héroe
    Entonces el sistema impide continuar
    Y explica que el héroe ya está ocupado en una misión

  Escenario: Rechazar un héroe que participa en un torneo
    Dado un héroe propio que participa en un torneo
    Cuando el jugador intenta iniciar una misión con ese héroe
    Entonces el sistema impide continuar
    Y explica que el héroe no está disponible por su participación en el torneo

  Escenario: Mostrar todas las condiciones incumplidas
    Dado un héroe propio sin equipo y cuya composición individual no es válida
    Cuando el jugador intenta iniciar una misión con ese héroe
    Entonces el sistema presenta todas las condiciones incumplidas
    Y no bloquea al héroe

  Escenario: Revalidar después de corregir el equipamiento
    Dado un héroe propio que fue rechazado porque no tenía equipo
    Cuando el jugador equipa el héroe y vuelve a iniciar la misión
    Entonces el sistema realiza nuevamente la validación
    Y permite iniciar la misión si ya cumple las condiciones
