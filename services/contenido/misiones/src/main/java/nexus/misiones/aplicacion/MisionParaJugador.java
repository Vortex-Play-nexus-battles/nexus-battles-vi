package nexus.misiones.aplicacion;

import nexus.misiones.dominio.Mision;
import nexus.misiones.dominio.SituacionDelJugador;

/** Una mision tal como la ve un jugador: la definicion, su situacion y si es favorita. */
public record MisionParaJugador(Mision mision, SituacionDelJugador situacion, boolean favorita) {
}
