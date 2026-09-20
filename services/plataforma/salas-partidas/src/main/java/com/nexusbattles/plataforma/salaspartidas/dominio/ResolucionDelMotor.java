package com.nexusbattles.plataforma.salaspartidas.dominio;

import java.util.Objects;

/**
 * Lo que el motor de combate responde a un ataque — RF-JUE-006.
 *
 * <p>Espejo de {@code RespuestaDeAtaque} en
 * {@code contracts/openapi/motor-combate.yaml}. Se copia a un tipo del dominio
 * en vez de pasear el DTO del adaptador: asi el caso de uso no depende de la
 * forma del JSON de otro servicio.
 *
 * <p>{@code ataqueResuelto} no hace falta para restar vida, pero viaja igual:
 * sin el, un dano de cero no se distingue de un fallo de integracion, y un
 * combate tiene que poder auditarse.
 *
 * @param categoria      efecto que sorteo el motor, tal cual lo nombra
 * @param danoAplicado   lo que hay que restar a la vida del objetivo
 * @param ataqueResuelto tirada del atacante, para poder auditar el combate
 */
public record ResolucionDelMotor(String categoria, int danoAplicado, int ataqueResuelto) {

    public ResolucionDelMotor {
        Objects.requireNonNull(categoria, "El motor siempre nombra la categoria del efecto.");
        if (danoAplicado < 0) {
            throw new IllegalArgumentException("El motor no puede devolver dano negativo.");
        }
    }
}
