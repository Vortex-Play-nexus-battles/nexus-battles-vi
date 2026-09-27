package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos;

import com.nexusbattles.comun.error.ErrorDeCampo;
import com.nexusbattles.comun.error.ErrorDeNegocio;

import java.net.URI;
import java.util.List;

/**
 * Un parametro de consulta de los mensajes privados fuera de lo que admite el
 * contrato ({@code limite} de 1 a 100, {@code antesDe} como fecha ISO-8601,
 * {@code uidOtro} como UUID) — 400.
 *
 * <p>Mismo {@code type} que {@code ParametrosInvalidos} de las salas: para la
 * interfaz es el mismo caso («corrige este campo»), con el campo en
 * {@code errores}.
 */
public class ConsultaInvalida extends ErrorDeNegocio {

    public static final URI TIPO = URI.create("https://nexusbattles.local/errores/parametros-invalidos");

    public ConsultaInvalida(String campo, String mensaje) {
        super(TIPO, "Revisa la consulta", 400, mensaje, List.of(new ErrorDeCampo(campo, mensaje)));
    }
}
