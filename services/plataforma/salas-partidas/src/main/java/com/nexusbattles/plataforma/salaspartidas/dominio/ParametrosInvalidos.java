package com.nexusbattles.plataforma.salaspartidas.dominio;

import com.nexusbattles.comun.error.ErrorDeCampo;
import com.nexusbattles.comun.error.ErrorDeNegocio;

import java.net.URI;
import java.util.List;

/**
 * Uno o mas parametros de la sala estan fuera de rango.
 *
 * <p>Lleva siempre el detalle por campo para que la interfaz marque el campo
 * concreto en variante Invalido, en vez de mostrar un aviso general: el requisito
 * exige indicar <b>el motivo</b> del rechazo, no solo que fallo.
 */
public class ParametrosInvalidos extends ErrorDeNegocio {

    public static final URI TIPO =
            URI.create("https://nexusbattles.local/errores/parametros-invalidos");

    public ParametrosInvalidos(List<ErrorDeCampo> errores) {
        super(TIPO,
              "Revisa los datos de la sala",
              400,
              resumir(errores),
              errores);
    }

    public ParametrosInvalidos(String campo, String mensaje) {
        this(List.of(new ErrorDeCampo(campo, mensaje)));
    }

    private static String resumir(List<ErrorDeCampo> errores) {
        if (errores == null || errores.isEmpty()) {
            throw new IllegalArgumentException(
                    "ParametrosInvalidos sin errores de campo no explica el motivo del rechazo.");
        }
        if (errores.size() == 1) {
            return errores.get(0).mensaje();
        }
        return "Hay " + errores.size() + " campos que corregir.";
    }
}
