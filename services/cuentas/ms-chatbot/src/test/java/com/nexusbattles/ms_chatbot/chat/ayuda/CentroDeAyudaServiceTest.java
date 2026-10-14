package com.nexusbattles.ms_chatbot.chat.ayuda;

import com.nexusbattles.ms_chatbot.chat.ayuda.CentroDeAyudaResponse.CategoriaDeAyuda;
import com.nexusbattles.ms_chatbot.chat.ayuda.CentroDeAyudaResponse.TemaDeAyuda;
import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.motor.model.EstadoVersion;
import com.nexusbattles.ms_chatbot.chat.motor.model.TemaConocimiento;
import com.nexusbattles.ms_chatbot.chat.motor.model.TipoRespuesta;
import com.nexusbattles.ms_chatbot.chat.motor.repository.TemaConocimientoRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// ms-chatbot.yaml 1.8.0: GET /chat/ayuda, lo que el asistente sabe responder.
class CentroDeAyudaServiceTest {

    private final TemaConocimientoRepository repositorio = mock(TemaConocimientoRepository.class);
    private final CentroDeAyudaService servicio = new CentroDeAyudaService(repositorio);

    private static TemaConocimiento tema(Categoria categoria, TipoRespuesta tipo, String titulo, String respuestaEs,
                                         String respuestaEn, int prioridad) {
        return new TemaConocimiento(null, "k-" + titulo, categoria, tipo, titulo, "variante secreta", "secret",
            respuestaEs, respuestaEn, prioridad, true);
    }

    @Test
    void agrupaPorCategoriaEnElOrdenDelContratoYOrdenaPorPrioridadYTitulo() {
        when(repositorio.findByVersionEstadoAndActivoTrue(EstadoVersion.PRODUCCION)).thenReturn(List.of(
            tema(Categoria.SUBASTA_Y_COMERCIO, TipoRespuesta.DIRECTA, "Pujar", "Puja.", "Bid.", 0),
            tema(Categoria.PRODUCTO, TipoRespuesta.DIRECTA, "Héroes", "Hay 8.", "There are 8.", 0),
            tema(Categoria.SUBASTA_Y_COMERCIO, TipoRespuesta.DIRECTA, "Cancelar", "Cancela.", null, 0),
            tema(Categoria.SUBASTA_Y_COMERCIO, TipoRespuesta.DIRECTA, "Publicar", "Publica.", null, 5)));

        List<CategoriaDeAyuda> categorias = servicio.centro().categorias();

        assertThat(categorias).extracting(CategoriaDeAyuda::categoria)
            .containsExactly(Categoria.PRODUCTO, Categoria.SUBASTA_Y_COMERCIO);
        assertThat(categorias).extracting(CategoriaDeAyuda::ancla)
            .containsExactly("categoria-producto", "categoria-subasta-y-comercio");
        assertThat(categorias.get(1).temas()).extracting(TemaDeAyuda::titulo)
            .containsExactly("Publicar", "Cancelar", "Pujar");
        verify(repositorio).findByVersionEstadoAndActivoTrue(EstadoVersion.PRODUCCION);
    }

    @Test
    void sinTemasDeCortesiaYLosPasosSoloDeLosPasoAPaso() {
        when(repositorio.findByVersionEstadoAndActivoTrue(EstadoVersion.PRODUCCION)).thenReturn(List.of(
            tema(Categoria.FAQ_GENERAL, TipoRespuesta.DIRECTA, "Saludo", "Hola.", "Hi.", 9),
            tema(Categoria.CUENTA_Y_REGISTRO, TipoRespuesta.PASO_A_PASO, "Crear cuenta",
                "Para crearla: 1) Entra. 2) Llena el formulario.", "To create it: 1) Go in. 2) Fill the form.", 0),
            tema(Categoria.CUENTA_Y_REGISTRO, TipoRespuesta.DIRECTA, "Apodo", "1) no 2) son pasos", "  ", 0)));

        List<CategoriaDeAyuda> categorias = servicio.centro().categorias();

        assertThat(categorias).extracting(CategoriaDeAyuda::categoria).containsExactly(Categoria.CUENTA_Y_REGISTRO);
        TemaDeAyuda apodo = categorias.get(0).temas().get(0);
        TemaDeAyuda crear = categorias.get(0).temas().get(1);
        assertThat(apodo).isEqualTo(new TemaDeAyuda("Apodo", "1) no 2) son pasos", null, List.of(), List.of()));
        assertThat(crear.pasosEs()).containsExactly("Entra.", "Llena el formulario.");
        assertThat(crear.pasosEn()).containsExactly("Go in.", "Fill the form.");
        assertThat(crear.respuestaEn()).isEqualTo("To create it: 1) Go in. 2) Fill the form.");
    }

    @Test
    void sinVersionEnProduccionNoHayCategorias() {
        when(repositorio.findByVersionEstadoAndActivoTrue(EstadoVersion.PRODUCCION)).thenReturn(List.of());

        assertThat(servicio.centro().categorias()).isEmpty();
    }
}
