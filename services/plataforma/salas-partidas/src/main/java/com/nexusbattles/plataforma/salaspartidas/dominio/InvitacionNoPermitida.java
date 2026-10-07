package com.nexusbattles.plataforma.salaspartidas.dominio;

import com.nexusbattles.comun.error.ErrorDeNegocio;

import java.net.URI;

/**
 * Una invitacion que no se puede mandar — salas-partidas.yaml 1.10.0,
 * {@code POST /salas/{id}/invitaciones} (revision del modo jugador del 6-oct:
 * «invitar amigos o buscar por nombre»).
 *
 * <p>Un solo tipo de problema con el motivo en {@code detail}: la vista lo
 * pinta tal cual, junto al buscador, y no decide nada por el texto.
 */
public class InvitacionNoPermitida extends ErrorDeNegocio {

    public static final URI TIPO = URI.create("https://nexusbattles.local/errores/invitacion-no-permitida");

    private InvitacionNoPermitida(int estado, String detalle) {
        super(TIPO, "No se pudo invitar", estado, detalle);
    }

    public static InvitacionNoPermitida soloElAnfitrion() {
        return new InvitacionNoPermitida(403, "Solo quien creó la sala puede invitar.");
    }

    public static InvitacionNoPermitida aTiMismo() {
        return new InvitacionNoPermitida(422, "No te puedes invitar a ti mismo.");
    }

    public static InvitacionNoPermitida yaEstaDentro() {
        return new InvitacionNoPermitida(409, "Ese jugador ya está en la sala.");
    }

    public static InvitacionNoPermitida salaQueNoEspera() {
        return new InvitacionNoPermitida(409, "La sala ya no espera jugadores.");
    }

    public static InvitacionNoPermitida sinCupo() {
        return new InvitacionNoPermitida(409, "La sala está completa.");
    }

    public static InvitacionNoPermitida jugadorNoEncontrado() {
        return new InvitacionNoPermitida(404, "No encontramos a ese jugador, o su cuenta no está activa.");
    }

    public static InvitacionNoPermitida sinServicio() {
        return new InvitacionNoPermitida(503, "No pudimos enviar la invitación ahora. Inténtalo en un momento.");
    }
}
