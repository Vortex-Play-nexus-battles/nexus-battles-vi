package nexus.dominio;

import java.util.List;

/**
 * Una rotacion que el heroe no puede seguir (ERS CU-61, excepcion E1: "el
 * sistema rechaza la rotacion e indica las habilidades validas"). El mensaje
 * es apto para el jugador; habilidadesValidas es lo que si puede usar.
 */
public class RotacionInvalidaException extends IllegalArgumentException {

    private final List<String> habilidadesValidas;

    public RotacionInvalidaException(String mensaje, List<String> habilidadesValidas) {
        super(mensaje);
        this.habilidadesValidas = List.copyOf(habilidadesValidas);
    }

    public List<String> habilidadesValidas() {
        return habilidadesValidas;
    }
}
