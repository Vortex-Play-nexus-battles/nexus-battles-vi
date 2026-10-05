# language: es
# Historia: HU-NOT-001 (RF-NOT-001, alertas al iniciar sesión) | GitHub #532 | 3 puntos
# Fuente: criterio CA-02 de #532, «el usuario marca todas las alertas como leídas; se resuelve
#         sin estado a medias y ve el resultado»; ficha RF-NOT-001, SRS Rev. 1.1, pp. 26-27
# Alcance: solo CA-02, sobre la bandeja de notificaciones (contrato HTTP 1.3.0)

Característica: Marcar todas las notificaciones como leídas
  Como jugador con notificaciones sin leer
  quiero marcarlas todas como leídas de una vez
  para dejar mi bandeja al día sin revisarlas una por una.

  Escenario: El jugador marca todas como leídas y la bandeja no queda a medias
    Dado un jugador con tres notificaciones sin leer y dos sesiones abiertas
    Cuando marca todas como leídas desde una de sus sesiones
    Entonces las tres quedan leídas en una sola operación, sin ninguna a medias
    Y ve cuántas se marcaron y que ya no le queda ninguna sin leer
    Y el contador de no leídas baja a cero en sus dos sesiones
