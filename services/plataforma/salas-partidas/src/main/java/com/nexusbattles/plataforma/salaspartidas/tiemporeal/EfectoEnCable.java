package com.nexusbattles.plataforma.salaspartidas.tiemporeal;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoDeCombate;

import java.util.List;

/**
 * Esquema {@code Efecto} del canal (1.5.0): un efecto activo tal como viaja.
 *
 * @param turnosRestantes turnos propios que le quedan; 1 en una proteccion,
 *                        que termina al empezar el siguiente turno de quien
 *                        la lleva
 * @param tipo            el {@code TipoDeEfecto} del motor
 */
public record EfectoEnCable(String codigo, String nombre, String icono, Integer turnosRestantes, String tipo,
                            Integer valor) {

    static EfectoEnCable de(EstadoDeCombate.Efecto efecto) {
        return new EfectoEnCable(efecto.codigo(), efecto.nombre(), null,
                efecto.hastaSuTurno() ? 1 : efecto.turnos(), efecto.tipo(), efecto.valor());
    }

    static List<EfectoEnCable> de(List<EstadoDeCombate.Efecto> efectos) {
        return efectos == null ? List.of() : efectos.stream().map(EfectoEnCable::de).toList();
    }
}
