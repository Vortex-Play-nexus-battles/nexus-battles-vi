package nexus.inventario.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexusbattles.comun.seguridad.pruebas.DecodificadorDePrueba;
import jakarta.validation.Valid;
import nexus.inventario.configuracion.SeguridadConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = ControladorValidacionPrueba.class)
@Import({SeguridadConfig.class, DecodificadorDePrueba.class})
class RespuestaValidacionWebTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void validacionUsaElProblemaLegibleDelInventario() throws Exception {
        // Con la cadena cerrada (anyRequest().authenticated()) la validacion
        // del cuerpo solo se ve autenticado: aqui basta un jugador con un
        // token real.
        mvc.perform(post("/prueba-validacion")
                        .header(HttpHeaders.AUTHORIZATION, ComoLlamador.portadorDeJugador("lyra_roja"))
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
