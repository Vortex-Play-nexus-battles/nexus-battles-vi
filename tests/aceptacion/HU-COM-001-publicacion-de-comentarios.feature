# language: es
# Historia: HU-COM-001 - Publicacion de comentarios | GitHub #34 | 5 puntos
# Fuente: Proyecto Integrador II, seccion 7.1, p. 34 y seccion 7.7.9, p. 55; RN-CMT-001

Caracteristica: Publicacion de comentarios con texto e imagenes sobre productos
  Como jugador
  quiero opinar sobre un producto con texto, imagenes y mi calificacion
  para compartir mi experiencia con la comunidad.

  Escenario: El comentario queda registrado con apodo, estrellas y fecha
    Dado un jugador sin sancion activa y un producto que existe
    Cuando publica un comentario con texto, una imagen valida y su calificacion
    Entonces el comentario queda guardado con el apodo, la calificacion y la fecha
    Y aparece en el hilo del producto
    Y su calificacion entra en el promedio

  Escenario: El segundo comentario del mismo jugador va sin calificacion
    Dado un jugador que ya califico ese producto
    Cuando publica otro comentario con estrellas
    Entonces el sistema lo acepta sin calificacion asociada
    Y el promedio del producto no se mueve

  Escenario: Un jugador puede comentar cuantas veces quiera
    Dado un jugador que ya tiene comentarios en el hilo
    Cuando publica varios comentarios mas
    Entonces todos quedan en el hilo sin ningun limite de cantidad

  Escenario: El silencio por sancion y la imagen invalida impiden publicar
    Dado un jugador con sancion activa de silencio o una imagen con formato no admitido
    Cuando intenta publicar
    Entonces el comentario no se guarda
    Y se le explica al autor cual de los dos motivos lo impidio

  Escenario: El contenido senalado por el filtro se retiene, no se rechaza
    Dado un comentario cuyo texto el filtro automatico senala
    Cuando el jugador lo publica
    Entonces el comentario queda guardado en revision a la espera de un moderador
    Y no aparece en el hilo ni mueve el promedio hasta que se apruebe
