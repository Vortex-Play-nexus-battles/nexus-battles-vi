package nexus.misiones.aplicacion;

import java.time.Clock;
import java.util.Objects;
import nexus.misiones.dominio.CatalogoDeMisiones;
import nexus.misiones.dominio.MisionNoEncontrada;
import nexus.misiones.dominio.RepositorioDeFavoritas;

/** «Agregar a favoritos» (7.8.9, HU-MIS-017): una marca por jugador y mision. */
public class GestionarFavoritas {

    private final CatalogoDeMisiones catalogo;
    private final RepositorioDeFavoritas favoritas;
    private final Clock reloj;

    public GestionarFavoritas(CatalogoDeMisiones catalogo, RepositorioDeFavoritas favoritas, Clock reloj) {
        this.catalogo = Objects.requireNonNull(catalogo);
        this.favoritas = Objects.requireNonNull(favoritas);
        this.reloj = Objects.requireNonNull(reloj);
    }

    public void marcar(String jugadorUid, String misionId) {
        exigirMision(misionId);
        favoritas.marcar(jugadorUid, misionId, reloj.instant());
    }

    public void desmarcar(String jugadorUid, String misionId) {
        exigirMision(misionId);
        favoritas.desmarcar(jugadorUid, misionId);
    }

    private void exigirMision(String misionId) {
        if (catalogo.buscar(misionId).isEmpty()) {
            throw new MisionNoEncontrada(misionId);
        }
    }
}
