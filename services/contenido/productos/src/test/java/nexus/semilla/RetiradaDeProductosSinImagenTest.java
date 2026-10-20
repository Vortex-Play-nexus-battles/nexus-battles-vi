package nexus.semilla;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import nexus.persistencia.ProductoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class RetiradaDeProductosSinImagenTest {

    private final ProductoRepository repositorio = mock(ProductoRepository.class);
    private final RetiradaDeProductosSinImagen retirada = new RetiradaDeProductosSinImagen(repositorio);

    @Test
    @DisplayName("devuelve los nombres de lo que el repositorio suspendio")
    void devuelveLosRetirados() {
        when(repositorio.suspenderConImagenSuelta()).thenReturn(List.of("Espada corta", "Casco de edicion limitada"));

        assertEquals(List.of("Espada corta", "Casco de edicion limitada"), retirada.retirar());
    }

    @Test
    @DisplayName("sin productos de imagen suelta no hay nada que retirar")
    void nadaQueRetirar() {
        when(repositorio.suspenderConImagenSuelta()).thenReturn(List.of());

        assertTrue(retirada.retirar().isEmpty());
    }

    @Test
    @DisplayName("el arranque (run) llama al repositorio una vez")
    void runRetira() {
        retirada.run(null);

        verify(repositorio).suspenderConImagenSuelta();
    }

    @Test
    @DisplayName("si la base falla, run() lo registra y el servicio arranca igual")
    void unaBaseCaidaNoImpideArrancar() {
        when(repositorio.suspenderConImagenSuelta()).thenThrow(new DataAccessResourceFailureException("mongo caido"));

        assertDoesNotThrow(() -> retirada.run(null));
    }
}
