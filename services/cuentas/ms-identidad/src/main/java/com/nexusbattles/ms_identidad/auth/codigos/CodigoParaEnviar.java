package com.nexusbattles.ms_identidad.auth.codigos;

/**
 * Un codigo recien emitido que hay que llevar al correo cuando la transaccion
 * que lo emitio se confirme ({@link CorreosDeCuenta}).
 *
 * <p>Es el unico sitio donde el codigo existe en claro, y solo en memoria:
 * nunca se guarda ni se escribe en la bitacora. Por eso {@link #toString()}
 * lo tapa: un {@code log.debug("{}", evento)} descuidado, o el propio
 * framework al informar de un fallo del oyente, no lo pueden imprimir.
 *
 * @param codigoId       la fila del codigo; entra en la clave de idempotencia del correo
 * @param minutosVigencia lo que el correo promete, igual a lo que se guardo
 */
public record CodigoParaEnviar(Long codigoId, TipoCodigo tipo, String email, String apodo,
                               String codigo, int minutosVigencia) {

    /** Clave {@code Idempotency-Key} del correo (correo.yaml 1.4.0): un reintento no encola otro. */
    public String claveDeIdempotencia() {
        return tipo.name().toLowerCase(java.util.Locale.ROOT) + "-" + codigoId;
    }

    @Override
    public String toString() {
        return "CodigoParaEnviar[codigoId=" + codigoId + ", tipo=" + tipo + ", codigo=********]";
    }
}
