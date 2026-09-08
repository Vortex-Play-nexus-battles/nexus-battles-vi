package com.nexusbattles.plataforma.salaspartidas.dominio;

import com.nexusbattles.comun.error.ErrorDeNegocio;

import java.net.URI;
import java.util.UUID;

/**
 * No existe ninguna sala con ese identificador.
 *
 * <p>Se distingue a proposito de {@link IngresoNoPermitido}: una cosa es que la
 * sala exista y no admita a este jugador -un conflicto, 409- y otra que el
 * identificador no corresponda a nada -404-. El contrato OpenAPI las separa, y
 * la interfaz no puede reaccionar igual: ante un 404 no tiene sentido ofrecer
 * reintentar, y ante un 409 si.
 */
public class SalaNoEncontrada extends ErrorDeNegocio {

    public static final URI TIPO =
            URI.create("https://nexusbattles.local/errores/sala-no-encontrada");

    public SalaNoEncontrada(UUID idSala) {
        super(TIPO,
              "Esa sala no existe",
              404,
              "No hay ninguna sala con el identificador " + idSala + ".");
    }
}
