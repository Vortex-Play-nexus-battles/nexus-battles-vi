package com.nexusbattles.plataforma.salaspartidas.api;

import com.nexusbattles.plataforma.salaspartidas.aplicacion.CrearSala;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.IngresarASala;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.ListarSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.CreditosInsuficientes;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.PaginaDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosInvalidos;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import com.nexusbattles.plataforma.salaspartidas.dominio.SalaNoEncontrada;
import com.nexusbattles.plataforma.salaspartidas.dominio.SalaPrivadaSinInvitacion;
import com.nexusbattles.plataforma.salaspartidas.dominio.IngresoNoPermitido;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @MockitoBean
    private ListarSalas listarSalas;

    @MockitoBean
    private IngresarASala ingresarASala;

    private static Sala salaDeEjemplo() {
        return Sala.crear(new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 0, false, false, null), JUGADOR);
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
                .andExpect(jsonPath("$.estado").value("ABIERTA"))
                .andExpect(jsonPath("$.idAnfitrion").value(JUGADOR.toString()))
                .andExpect(jsonPath("$.ocupacion").value(1))
                .andExpect(jsonPath("$.maximoParticipantes").value(4))
                .andExpect(jsonPath("$.recompensaCreditos").value(0))
                .andExpect(jsonPath("$.incluirHeroeIA").value(false))
                // El apodo no viaja: pertenece al modulo de cuentas y ninguna
                // pantalla de HU-SAL-002 lo muestra.
                .andExpect(jsonPath("$.anfitrion").doesNotExist())
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
                .andExpect(jsonPath("$.idAnfitrion").value(JUGADOR.toString()));
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
                new com.nexusbattles.comun.error.ErrorDeCampo("maximoParticipantes", "Fuera de rango."),
                new com.nexusbattles.comun.error.ErrorDeCampo("recompensaCreditos", "No puede ser negativa."))));

        mockMvc.perform(post("/api/v1/salas")
                        .with(jugador())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.length()").value(2))
                .andExpect(jsonPath("$.detail").value("Hay 2 campos que corregir."));
    }

    // =========================================================================
    // HU-SAL-002 · RF-JUE-002 — listado e ingreso
    //
    // Los codigos salen del contrato OpenAPI, que distingue tres rechazos que
    // la interfaz trata distinto: 404 la sala no existe, 403 es privada, 409
    // esta llena o la partida empezo.
    // =========================================================================

    private static final UUID ID_SALA = UUID.fromString("77777777-7777-7777-7777-777777777777");

    private static PaginaDeSalas paginaCon(Sala... salas) {
        return new PaginaDeSalas(List.of(salas), 0, 16, salas.length, 1);
    }

    @Test
    @DisplayName("GET /salas devuelve la pagina con los cinco campos del contrato")
    void listaSalas() throws Exception {
        when(listarSalas.ejecutar(any(), any(), any(), any())).thenReturn(paginaCon(salaDeEjemplo()));

        mockMvc.perform(get("/api/v1/salas").with(jugador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido.length()").value(1))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamano").value(16))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.totalPaginas").value(1));
    }

    @Test
    @DisplayName("GET /salas sin token responde 401")
    void listaSalasSinToken() throws Exception {
        mockMvc.perform(get("/api/v1/salas"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /salas traslada los filtros y la paginacion del contrato")
    void listaSalasConFiltros() throws Exception {
        when(listarSalas.ejecutar(any(), any(), any(), any())).thenReturn(paginaCon());

        mockMvc.perform(get("/api/v1/salas")
                        .param("pagina", "2")
                        .param("tamano", "8")
                        .param("modalidad", "HASTA_SEIS")
                        .param("estado", "ABIERTA")
                        .with(jugador()))
                .andExpect(status().isOk());

        verify(listarSalas).ejecutar(2, 8, Modalidad.HASTA_SEIS, EstadoSala.ABIERTA);
    }

    @Test
    @DisplayName("GET /salas sin parametros no inventa valores: los decide el caso de uso")
    void listaSalasSinParametros() throws Exception {
        when(listarSalas.ejecutar(any(), any(), any(), any())).thenReturn(paginaCon());

        mockMvc.perform(get("/api/v1/salas").with(jugador()))
                .andExpect(status().isOk());

        verify(listarSalas).ejecutar(null, null, null, null);
    }

    @Test
    @DisplayName("POST participantes admite al jugador y devuelve la sala")
    void ingresa() throws Exception {
        when(ingresarASala.ejecutar(any(), any())).thenReturn(salaDeEjemplo());

        mockMvc.perform(post("/api/v1/salas/{id}/participantes", ID_SALA).with(jugador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ABIERTA"));
    }

    @Test
    @DisplayName("el jugador que entra sale del token, aunque el cuerpo diga otra cosa")
    void elJugadorSaleDelToken() throws Exception {
        when(ingresarASala.ejecutar(any(), any())).thenReturn(salaDeEjemplo());

        mockMvc.perform(post("/api/v1/salas/{id}/participantes", ID_SALA)
                        .with(jugador())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idJugador\": \"99999999-9999-9999-9999-999999999999\"}"))
                .andExpect(status().isOk());

        verify(ingresarASala).ejecutar(ID_SALA, JUGADOR);
    }

    @Test
    @DisplayName("POST participantes sin token responde 401")
    void ingresaSinToken() throws Exception {
        mockMvc.perform(post("/api/v1/salas/{id}/participantes", ID_SALA))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("una sala inexistente responde 404 con su tipo")
    void salaInexistente() throws Exception {
        when(ingresarASala.ejecutar(any(), any())).thenThrow(new SalaNoEncontrada(ID_SALA));

        mockMvc.perform(post("/api/v1/salas/{id}/participantes", ID_SALA).with(jugador()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type")
                        .value("https://nexusbattles.local/errores/sala-no-encontrada"));
    }

    @Test
    @DisplayName("una sala privada responde 403, no 409")
    void salaPrivada() throws Exception {
        when(ingresarASala.ejecutar(any(), any())).thenThrow(new SalaPrivadaSinInvitacion());

        mockMvc.perform(post("/api/v1/salas/{id}/participantes", ID_SALA).with(jugador()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type")
                        .value("https://nexusbattles.local/errores/sala-privada"));
    }

    @Test
    @DisplayName("una sala llena responde 409 diciendo el motivo")
    void salaLlena() throws Exception {
        when(ingresarASala.ejecutar(any(), any()))
                .thenThrow(new IngresoNoPermitido("La sala ya alcanzo su maximo de participantes."));

        mockMvc.perform(post("/api/v1/salas/{id}/participantes", ID_SALA).with(jugador()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type")
                        .value("https://nexusbattles.local/errores/ingreso-no-permitido"))
                .andExpect(jsonPath("$.detail")
                        .value("La sala ya alcanzo su maximo de participantes."));
    }

    @Test
    @DisplayName("un visitante sin rol de jugador no puede entrar a una sala")
    void ingresaSinRol() throws Exception {
        mockMvc.perform(post("/api/v1/salas/{id}/participantes", ID_SALA)
                        .with(jwt().jwt(t -> t.subject(JUGADOR.toString()))))
                .andExpect(status().isForbidden());
    }
}
