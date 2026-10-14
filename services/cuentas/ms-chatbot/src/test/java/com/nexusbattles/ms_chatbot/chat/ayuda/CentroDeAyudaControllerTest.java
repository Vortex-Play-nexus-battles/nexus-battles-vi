package com.nexusbattles.ms_chatbot.chat.ayuda;

import com.nexusbattles.ms_chatbot.chat.ayuda.CentroDeAyudaResponse.CategoriaDeAyuda;
import com.nexusbattles.ms_chatbot.chat.ayuda.CentroDeAyudaResponse.TemaDeAyuda;
import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// ms-chatbot.yaml 1.8.0: GET /chat/ayuda, publica y en cache.
@WebMvcTest(CentroDeAyudaController.class)
@Import(SecurityConfig.class)
class CentroDeAyudaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CentroDeAyudaService servicio;

    private static CentroDeAyudaResponse centro() {
        return new CentroDeAyudaResponse(List.of(new CategoriaDeAyuda(Categoria.CUENTA_Y_REGISTRO,
            "categoria-cuenta-y-registro", List.of(new TemaDeAyuda("Crear cuenta", "Para crearla: 1) a 2) b",
                null, List.of("a", "b"), List.of())))));
    }

    @Test
    void sinTokenDevuelveElCentroConCachePublicaDeCincoMinutos() throws Exception {
        when(servicio.centro()).thenReturn(centro());

        mockMvc.perform(get("/chat/ayuda"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=300, public"))
            .andExpect(jsonPath("$.categorias[0].categoria").value("CUENTA_Y_REGISTRO"))
            .andExpect(jsonPath("$.categorias[0].ancla").value("categoria-cuenta-y-registro"))
            .andExpect(jsonPath("$.categorias[0].temas[0].titulo").value("Crear cuenta"))
            .andExpect(jsonPath("$.categorias[0].temas[0].pasosEs[1]").value("b"))
            .andExpect(jsonPath("$.categorias[0].temas[0].respuestaEn").isEmpty())
            // Nada interno del tema.
            .andExpect(jsonPath("$.categorias[0].temas[0].clave").doesNotExist())
            .andExpect(jsonPath("$.categorias[0].temas[0].prioridad").doesNotExist())
            .andExpect(jsonPath("$.categorias[0].temas[0].variantesEs").doesNotExist());
    }

    @Test
    void unTokenInvalidoNoLaCierra() throws Exception {
        when(servicio.centro()).thenReturn(centro());

        mockMvc.perform(get("/chat/ayuda").header(HttpHeaders.AUTHORIZATION, "Bearer no-es-un-jwt"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=300, public"));
    }
}
