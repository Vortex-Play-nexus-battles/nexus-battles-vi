package nexus.misiones.aplicacion;

import java.util.List;

/** Una pagina del tablon: dieciseis misiones como mucho (RNF-USA-001). */
public record PaginaDeMisiones(List<MisionParaJugador> misiones, int total, int pagina, int totalPaginas) {

    public static final int TAMANIO = 16;
}
