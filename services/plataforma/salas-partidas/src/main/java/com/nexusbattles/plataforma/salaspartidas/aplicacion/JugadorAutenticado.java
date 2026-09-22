package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import java.util.Objects;
import java.util.UUID;

/**
 * Las dos caras de la identidad de un jugador.
 *
 * <p>Este servicio guarda a la gente por {@code id} —un UUID estable— porque el
 * apodo es mutable y una sala que recordara a su anfitrion por el apodo
 * convertiria a otra persona en anfitrion el dia que alguien se cambie el
 * nombre.
 *
 * <p>El inventario tambien identifica ya por el identificador estable
 * (contrato 1.1.1, #575): la cabecera {@code X-User-Name} que manda
 * {@code ClienteInventarioHeroes} lleva el {@code id}, no el apodo. Con eso
 * desaparecio la ultima razon tecnica para arrastrar el apodo hasta el
 * adaptador.
 *
 * <p>El apodo se conserva porque el canal de la partida y el chat muestran un
 * nombre de persona, no un UUID: quien mira la sala lee «vael», no
 * «11111111-…». Es un dato de presentacion, y por eso el dominio de salas
 * sigue tratando solo con {@code id}.
 *
 * @param id    identificador estable (ADR-002); con el se persiste y se pregunta
 * @param apodo nombre visible, para lo que se pinta en pantalla
 */
public record JugadorAutenticado(UUID id, String apodo) {

    public JugadorAutenticado {
        Objects.requireNonNull(id, "Un jugador sin identificador no puede entrar a ninguna sala.");
        Objects.requireNonNull(apodo, "Sin apodo no se puede preguntar al inventario por su heroe.");
    }
}
