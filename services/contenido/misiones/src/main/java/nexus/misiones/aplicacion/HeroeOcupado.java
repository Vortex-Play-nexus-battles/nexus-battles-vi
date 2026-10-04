package nexus.misiones.aplicacion;

import nexus.misiones.dominio.ReglaDeMisionIncumplida;

/**
 * El heroe ya esta en otra mision o bloqueado por una subasta (409): «no debe
 * estar en otra mision activa» (7.8.6); HU-MIS-008: «ya esta ocupado en una
 * mision».
 */
public class HeroeOcupado extends ReglaDeMisionIncumplida {

    public HeroeOcupado(String detalle) {
        super(detalle);
    }

    public static HeroeOcupado enMision() {
        return new HeroeOcupado("Ese héroe ya está ocupado en una misión: espera a que vuelva o cancélala.");
    }

    public static HeroeOcupado enSubasta() {
        return new HeroeOcupado("Ese héroe está publicado en una subasta y no puede salir de misión.");
    }
}
