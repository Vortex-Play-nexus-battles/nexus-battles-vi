package com.nexusbattles.ms_ecommerce.compra.pago;

import java.util.Objects;

/**
 * Una tarjeta que paso la validacion, lista para la pasarela simulada.
 *
 * <p>Lo unico que la orden guarda es {@link #marca()} y {@link #ultimos4()}.
 * El numero completo esta aqui solo porque la pasarela simulada lo necesita
 * para decidir (sus tarjetas de prueba se reconocen por los ultimos digitos);
 * no sale de la peticion y {@link #toString()} no lo ensena.
 */
public final class TarjetaValidada {

    private final String marca;
    private final String numero;

    TarjetaValidada(String marca, String numero) {
        this.marca = Objects.requireNonNull(marca, "marca");
        this.numero = Objects.requireNonNull(numero, "numero");
    }

    /** VISA, MASTERCARD, AMEX, DINERS, DISCOVER o TARJETA si no se reconoce. */
    public String marca() {
        return marca;
    }

    public String ultimos4() {
        return numero.substring(numero.length() - 4);
    }

    /** Los digitos del numero. Solo para la pasarela: nunca se guardan ni se registran. */
    String numero() {
        return numero;
    }

    @Override
    public String toString() {
        return "TarjetaValidada[" + marca + " terminada en " + ultimos4() + "]";
    }
}
