package nexus.dominio;

/**
 * Resultado de intentar ejecutar una accion especial. Si ejecutada es false,
 * ataqueEnValorBase indica que el ataque del turno cae a su valor base
 * (seccion 6.1.1, p. 26).
 */
public record ResultadoDeAccion(boolean ejecutada, EstadoDePoder estado, boolean ataqueEnValorBase) {
}
