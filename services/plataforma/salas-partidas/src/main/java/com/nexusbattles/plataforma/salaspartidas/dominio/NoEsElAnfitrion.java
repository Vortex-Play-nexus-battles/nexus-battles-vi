package com.nexusbattles.plataforma.salaspartidas.dominio;

import com.nexusbattles.comun.error.ErrorDeNegocio;

import java.net.URI;

/**
 * Quien pide la operacion no es el anfitrion de la sala.
 *
 * <p>El contrato OpenAPI reserva un <b>403</b> para {@code cancelarSala}: «Solo
 * el anfitrion puede cancelar la sala». Es 403 y no 404 a proposito: la sala
 * existe y quien pregunta puede verla en el listado; lo que no tiene es
 * permiso. Devolver 404 escondería un recurso que el propio listado ya muestra,
 * y confundiría «no existe» con «no es tuya».
 *
 * <p>El mensaje no nombra al anfitrion. Quien no es dueno de la sala no tiene
 * por que enterarse de quien lo es a traves de un error.
 */
public class NoEsElAnfitrion extends ErrorDeNegocio {

    public static final URI TIPO =
            URI.create("https://nexusbattles.local/errores/no-es-el-anfitrion");

    public NoEsElAnfitrion() {
        super(TIPO,
              "No eres el anfitrion de esta sala",
              403,
              "Solo quien creo la sala puede cancelarla.");
    }
}
