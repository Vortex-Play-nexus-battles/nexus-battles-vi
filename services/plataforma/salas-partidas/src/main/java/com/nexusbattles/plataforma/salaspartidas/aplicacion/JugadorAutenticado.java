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
 * <p>Pero el inventario, hoy, identifica al jugador por la cabecera
 * {@code X-User-Name} (asi lo declara {@code contracts/openapi/inventario.yaml}:
 * «las rutas del jugador conservan temporalmente X-User-Name»). Mientras siga
 * siendo asi, cualquier consulta a su vitrina necesita el apodo, no el UUID.
 *
 * <p>Este record lleva las dos y evita que el apodo se cuele en el dominio: las
 * salas siguen tratando con {@code id}, y solo el adaptador de inventario mira
 * {@code apodo}. El dia que el inventario acepte el token (ADR-001), se borra el
 * segundo campo y no se toca nada mas.
 *
 * @param id    identificador estable, el que persisten las salas
 * @param apodo nombre visible, el que reconoce el inventario
 */
public record JugadorAutenticado(UUID id, String apodo) {

    public JugadorAutenticado {
        Objects.requireNonNull(id, "Un jugador sin identificador no puede entrar a ninguna sala.");
        Objects.requireNonNull(apodo, "Sin apodo no se puede preguntar al inventario por su heroe.");
    }
}
