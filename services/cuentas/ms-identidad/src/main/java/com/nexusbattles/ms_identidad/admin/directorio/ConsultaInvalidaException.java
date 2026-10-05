package com.nexusbattles.ms_identidad.admin.directorio;

/**
 * Un filtro del directorio o de los indicadores que no se puede aplicar — HU-USR-008.
 *
 * <p>Rol o estado fuera de la lista, una fecha mal escrita, un desde posterior
 * al hasta, un rango mas largo que el tope. Se responde 400
 * {@code datos-invalidos} (ms-identidad-admin.yaml 1.3.0, {@code ConsultaInvalida})
 * y el mensaje es para la persona que hizo la consulta. Lo contrario —ignorar
 * el filtro o devolver una lista vacia— pareceria un resultado.
 */
public class ConsultaInvalidaException extends RuntimeException {

    public ConsultaInvalidaException(String mensaje) {
        super(mensaje);
    }
}
