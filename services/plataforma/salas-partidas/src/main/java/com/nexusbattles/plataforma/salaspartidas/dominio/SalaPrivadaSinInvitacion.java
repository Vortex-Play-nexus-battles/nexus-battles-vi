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
 * <p>Se lanza cuando el codigo falta o no coincide. El mecanismo existe y esta
 * completo: {@code Sala.generarCodigoDeInvitacion} lo crea con
 * {@link java.security.SecureRandom} sobre un alfabeto sin O, 0, I ni 1,
 * {@code codigo_invitacion} lo guarda (migracion V5) y
 * {@code Sala.codigoCoincide} lo compara tolerando mayusculas, espacios y
 * guiones puestos de otra forma.
 *
 * <p>Este javadoc decia hasta FI-R4 que «no existe mecanismo de invitacion en
 * ninguna parte del sistema: ni codigo, ni lista de invitados, ni columna que
 * lo guarde». Era cierto cuando se escribio y dejo de serlo con la migracion
 * V5, y nadie volvio a mirarlo. Queda anotado porque el frontend leyo ese
 * comentario en vez del codigo: mandaba el {@code POST} sin cuerpo, el
 * agregado recibia {@code null} y toda sala privada era inaccesible por
 * construccion.
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
