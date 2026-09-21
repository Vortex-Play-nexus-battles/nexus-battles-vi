package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoDelHeroe;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.ResultadoVerificacion;

import java.util.Objects;

/**
 * Lo que el jugador ve antes de pulsar Entrar — HU-SAL-003, RF-JUE-003.
 *
 * <p>Junta las dos mitades de la verificacion: el veredicto sobre el heroe, que
 * viene del inventario, y lo que cuesta la sala, que es dato propio. Espejo del
 * esquema {@code VerificacionHeroe} del contrato.
 *
 * <p>{@code creditosDisponibles} va vacio a proposito y seguira asi mientras el
 * puerto de creditos no tenga operacion de consulta; el porque esta en
 * {@link ResultadoVerificacion}. {@code creditosRequeridos} si se informa —es la
 * recompensa de la sala— para que el dialogo pueda decir cuanto hay que poner
 * aunque no pueda decir cuanto se tiene.
 *
 * @param resultado           veredicto que elige la variante del dialogo
 * @param puedeIngresar       atajo para la interfaz: habilita o no el boton
 * @param heroe               heroe implicado, o {@code null}
 * @param salaQueLoOcupa      donde esta comprometido el heroe, si lo esta
 * @param creditosRequeridos  recompensa comprometida de la sala
 * @param creditosDisponibles saldo del jugador; vacio mientras no haya proveedor
 */
public record VerificacionDeIngreso(
        ResultadoVerificacion resultado,
        boolean puedeIngresar,
        HeroeDeCombate heroe,
        String salaQueLoOcupa,
        Integer creditosRequeridos,
        Integer creditosDisponibles) {

    public VerificacionDeIngreso {
        Objects.requireNonNull(resultado, "La verificacion sin resultado no dice nada.");
    }

    /**
     * Compone la respuesta a partir del veredicto del inventario y de la sala.
     *
     * <p>{@code puedeIngresar} no es un campo independiente que alguien pueda
     * poner a {@code true} por su cuenta: se deriva del resultado. Si fueran dos
     * datos sueltos, un dia dirian cosas distintas.
     */
    public static VerificacionDeIngreso de(EstadoDelHeroe estado, int recompensaDeLaSala) {
        Objects.requireNonNull(estado, "Sin veredicto del inventario no hay verificacion.");
        return new VerificacionDeIngreso(
                estado.resultado(),
                estado.puedeCombatir(),
                estado.heroe(),
                estado.ocupadoPor(),
                recompensaDeLaSala,
                null);
    }
}
