package nexus.inventario.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = ControladorValidacionPrueba.class)
class RespuestaValidacionWebTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void validacionUsaElProblemaLegibleDelInventario() throws Exception {
        mvc.perform(post("/prueba-validacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productoId":"espada-corta","tipo":"ARMA"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Solicitud invalida"))
                .andExpect(jsonPath("$.detail").value("Revisa los datos del elemento."));
    }
}

@RestController
class ControladorValidacionPrueba {

    @PostMapping("/prueba-validacion")
    void validar(@Valid @RequestBody CrearElementoRequest solicitud) {
    }
}
