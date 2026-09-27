package com.nexusbattles.ms_identidad.auth.codigos;

/**
 * Resultado de comprobar un codigo contra el ultimo de su familia.
 *
 * @param codigoId el codigo que coincidio (solo en {@link Resultado#VALIDO})
 * @param tipo     su tipo real: en la familia de restablecimiento puede ser
 *                 ACTIVACION o RESTABLECIMIENTO
 */
public record Comprobacion(Resultado resultado, Long codigoId, TipoCodigo tipo) {

    public enum Resultado {
        VALIDO,
        /** Incorrecto, caducado, usado, anulado o inexistente: la persona no ve la diferencia. */
        INVALIDO,
        /** Este intento agoto los permitidos (o ya estaban agotados): el codigo quedo anulado. */
        DEMASIADOS_INTENTOS
    }

    static Comprobacion valido(Long codigoId, TipoCodigo tipo) {
        return new Comprobacion(Resultado.VALIDO, codigoId, tipo);
    }

    static Comprobacion invalido() {
        return new Comprobacion(Resultado.INVALIDO, null, null);
    }

    static Comprobacion demasiadosIntentos() {
        return new Comprobacion(Resultado.DEMASIADOS_INTENTOS, null, null);
    }

    public boolean esValida() {
        return resultado == Resultado.VALIDO;
    }

    /**
     * El codigo si es valido; si no, la excepcion que la ruta publica traduce
     * a 400 {@code codigo-invalido} o 429 {@code demasiados-intentos}.
     */
    public Comprobacion exigirValida() {
        return switch (resultado) {
            case VALIDO -> this;
            case INVALIDO -> throw new CodigoInvalidoException();
            case DEMASIADOS_INTENTOS -> throw new DemasiadosIntentosException();
        };
    }
}
