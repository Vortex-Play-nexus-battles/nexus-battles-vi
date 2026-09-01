package com.nexusbattles.plataforma.salaspartidas.dominio;

import com.nexusbattles.comun.error.ErrorDeNegocio;

import java.net.URI;

/**
 * La sala es privada y quien intenta entrar no puede demostrar que esta invitado.
 *
 * <p>El contrato OpenAPI reserva un <b>403</b> para este caso y lo separa del
 * 409 de {@link IngresoNoPermitido} a proposito: son rechazos distintos y la
 * interfaz reacciona distinto. Un 409 dice «esta sala cambio de estado, prueba
 * con otra»; un 403 dice «esta sala no es para cualquiera».
 *
 * <p><b>Hoy rechaza siempre.</b> No existe mecanismo de invitacion en ninguna
 * parte del sistema: ni codigo, ni lista de invitados, ni columna que lo
 * guarde. Mientras no exista, no hay forma de que alguien demuestre estar
 * invitado, y dejar entrar a cualquiera convertiria «privada» en una etiqueta
 * decorativa. Cuando se defina el flujo real, este es el unico punto que hay
 * que tocar.
 */
public class SalaPrivadaSinInvitacion extends ErrorDeNegocio {

    public static final URI TIPO =
            URI.create("https://nexusbattles.local/errores/sala-privada");

    public SalaPrivadaSinInvitacion() {
        super(TIPO,
              "Esta sala es privada",
              403,
              "A una sala privada se entra por invitacion, no desde el listado.");
    }
}
