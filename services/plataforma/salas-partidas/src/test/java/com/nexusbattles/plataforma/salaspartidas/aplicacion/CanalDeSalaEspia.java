package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.CanalDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.MotivoDeCancelacion;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Doble del canal que anota lo anunciado, para probar el caso de uso sin
 * levantar un servidor WebSocket.
 *
 * <p>No es un mock: guarda lo que recibe y deja consultarlo. Lo que importa en
 * {@code IngresarASalaTest} no es que se llame al canal, sino <b>cuando</b> —
 * despues de guardar, y nunca tras un rechazo.
 */
class CanalDeSalaEspia implements CanalDeSala {

    /**
     * Que se anuncio. El {@code tipo} distingue ingreso de salida y de
     * cancelacion: los tres van al mismo destino y una prueba que solo contara
     * anuncios no notaria que se publico el hecho equivocado.
     */
    record Anuncio(String tipo, UUID idSala, UUID idJugador, int ocupacion) {
    }

    static final String INGRESO = "ingreso";
    static final String SALIDA = "salida";
    static final String CANCELACION = "cancelacion";

    private final List<Anuncio> anuncios = new ArrayList<>();
    private final List<MotivoDeCancelacion> motivos = new ArrayList<>();
    private final List<Integer> creditosDevueltos = new ArrayList<>();

    @Override
    public void anunciarIngreso(Sala sala, UUID idJugador) {
        anuncios.add(new Anuncio(INGRESO, sala.id(), idJugador, sala.ocupacion()));
    }

    @Override
    public void anunciarSalida(Sala sala, UUID idJugador) {
        anuncios.add(new Anuncio(SALIDA, sala.id(), idJugador, sala.ocupacion()));
    }

    @Override
    public void anunciarCancelacion(Sala sala, MotivoDeCancelacion motivo, int devueltos) {
        // Una cancelacion no es de nadie en particular, por eso idJugador va nulo.
        anuncios.add(new Anuncio(CANCELACION, sala.id(), null, sala.ocupacion()));
        motivos.add(motivo);
        creditosDevueltos.add(devueltos);
    }

    List<Anuncio> anuncios() {
        return List.copyOf(anuncios);
    }

    List<MotivoDeCancelacion> motivos() {
        return List.copyOf(motivos);
    }

    List<Integer> creditosDevueltos() {
        return List.copyOf(creditosDevueltos);
    }

    boolean noAnuncioNada() {
        return anuncios.isEmpty();
    }
}
