package nexus.misiones.dominio;

import java.util.Optional;

/** Estrategias guardadas, una por jugador y heroe (coleccion {@code estrategias}). */
public interface RepositorioDeEstrategias {

    Optional<EstrategiaGuardada> buscar(String jugadorUid, String heroeId);

    EstrategiaGuardada guardar(EstrategiaGuardada estrategia);
}
