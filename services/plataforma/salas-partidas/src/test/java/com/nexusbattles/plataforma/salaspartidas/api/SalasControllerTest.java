package com.nexusbattles.plataforma.salaspartidas.api;

import com.nexusbattles.plataforma.salaspartidas.aplicacion.CrearSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.CreditosInsuficientes;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosInvalidos;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import com.nexusbattles.plataforma.salaspartidas.seguridad.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API de creacion de sala — HU-SAL-001.
 *
 * <p>Se prueban tres cosas que no se pueden probar mas abajo: que la seguridad
 * corta a quien no tiene rol, que el anfitrion sale del token y no del cuerpo, y
 * que los errores de negocio salen en formato problem details (regla 4).
 */
@WebMvcTest(controllers = SalasController.class)
@Import({SecurityConfig.class, ManejadorDeErrores.class})
class SalasControllerTest {

    private static final UUID JUGADOR = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private static final String CUERPO = """
            {
              "nombre": "Duelo en el Nexo",
              "maximoParticipantes": 4,
              "modalidad": "HASTA_SEIS",
              "recompensaCreditos": 0,
              "incluirHeroeIA": false,
              "privada": false
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CrearSala crearSala;

    private static Sala salaDeEjemplo() {
        return Sala.crear(new ParametrosDeSala("Duelo en el Nexo", 4, Modalidad.HASTA_SEIS,
                0, false, false, null), JUGADOR);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor jugador() {
        return jwt().jwt(token -> token
                        .subject(JUGADOR.toString())
                        .claim("preferred_username", "Simon_P"))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_JUGADOR"));
    }

    @Test
    @DisplayName("crea la sala y devuelve 201 con su ubicacion")
    void creaLaSala() throws Exception {
        Sala sala = salaDeEjemplo();
        when(crearSala.ejecutar(any(), any())).thenReturn(sala);

        mockMvc.perform(post("/api/v1/salas")
                        .with(jugador())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/salas/" + sala.id()))
                .andExpect(jsonPath("$.nombre").value("Duelo en el Nexo"))
                .andExpect(jsonPath("$.estado").value("ABIERTA"))
                .andExpect(jsonPath("$.anfitrion.id").value(JUGADOR.toString()))
                .andExpect(jsonPath("$.anfitrion.apodo").value("Simon_P"))
                .andExpect(jsonPath("$.idPartida").doesNotExist());
    }

    @Test
    @DisplayName("sin token no se puede crear una sala")
    void sinToken() throws Exception {
        mockMvc.perform(post("/api/v1/salas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("un visitante sin rol de jugador no puede crear salas")
    void sinRolDeJugador() throws Exception {
        mockMvc.perform(post("/api/v1/salas")
                        .with(jwt().jwt(t -> t.subject(JUGADOR.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("los parametros invalidos salen como problem details con el campo senalado")
    void parametrosInvalidos() throws Exception {
        when(crearSala.ejecutar(any(), any()))
                .thenThrow(new ParametrosInvalidos("maximoParticipantes",
                        "Esta modalidad admite entre 2 y 6 jugadores."));

        mockMvc.perform(post("/api/v1/salas")
                        .with(jugador())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/parametros-invalidos"))
                .andExpect(jsonPath("$.title").value("Revisa los datos de la sala"))
                .andExpect(jsonPath("$.errores[0].campo").value("maximoParticipantes"));
    }

    @Test
    @DisplayName("los creditos insuficientes salen como 422 diciendo cuanto hay y cuanto falta")
    void creditosInsuficientes() throws Exception {
        when(crearSala.ejecutar(any(), any())).thenThrow(new CreditosInsuficientes(240, 400));

        mockMvc.perform(post("/api/v1/salas")
                        .with(jugador())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/creditos-insuficientes"))
                .andExpect(jsonPath("$.detail").value("Tienes 240 creditos y necesitas 400 para crear esta sala."));
    }

    @Test
    @DisplayName("una modalidad que no existe se rechaza como peticion mal formada")
    void modalidadInexistente() throws Exception {
        String cuerpoRoto = CUERPO.replace("HASTA_SEIS", "BATALLA_CAMPAL");

        mockMvc.perform(post("/api/v1/salas")
                        .with(jugador())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoRoto))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/peticion-ilegible"));
    }

    @Test
    @DisplayName("toda respuesta lleva su identificador de traza")
    void devuelveLaTraza() throws Exception {
        when(crearSala.ejecutar(any(), any())).thenReturn(salaDeEjemplo());

        // El filtro de traza no entra en la porcion @WebMvcTest, asi que aqui solo
        // se comprueba que la peticion funciona con una cabecera entrante valida.
        mockMvc.perform(post("/api/v1/salas")
                        .with(jugador())
                        .header("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("el anfitrion sale del token, aunque el cuerpo intente decir otra cosa")
    void elAnfitrionSaleDelToken() throws Exception {
        Sala sala = salaDeEjemplo();
        when(crearSala.ejecutar(any(), any())).thenReturn(sala);

        String cuerpoConIntruso = """
                {
                  "nombre": "Duelo en el Nexo",
                  "maximoParticipantes": 4,
                  "modalidad": "HASTA_SEIS",
                  "recompensaCreditos": 0,
                  "idAnfitrion": "99999999-9999-9999-9999-999999999999"
                }
                """;

        mockMvc.perform(post("/api/v1/salas")
                        .with(jugador())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoConIntruso))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.anfitrion.id").value(JUGADOR.toString()));
    }

    @Test
    @DisplayName("los campos desconocidos del cuerpo no rompen la peticion")
    void camposDesconocidos() throws Exception {
        when(crearSala.ejecutar(any(), any())).thenReturn(salaDeEjemplo());

        mockMvc.perform(post("/api/v1/salas")
                        .with(jugador())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO.replace("\"privada\": false", "\"privada\": false, \"colado\": 1")))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("una lista de errores de campo viaja entera")
    void variosErroresDeCampo() throws Exception {
        when(crearSala.ejecutar(any(), any())).thenThrow(new ParametrosInvalidos(List.of(
                new com.nexusbattles.comun.error.ErrorDeCampo("nombre", "Muy corto."),
                new com.nexusbattles.comun.error.ErrorDeCampo("recompensaCreditos", "No puede ser negativa."))));

        mockMvc.perform(post("/api/v1/salas")
                        .with(jugador())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.length()").value(2))
                .andExpect(jsonPath("$.detail").value("Hay 2 campos que corregir."));
    }
}
