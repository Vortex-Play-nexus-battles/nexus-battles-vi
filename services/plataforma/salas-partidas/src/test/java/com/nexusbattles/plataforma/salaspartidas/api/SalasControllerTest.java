package com.nexusbattles.plataforma.salaspartidas.api;

import com.nexusbattles.plataforma.salaspartidas.aplicacion.AbandonarSala;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.CancelarSala;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.CrearSala;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.IngresarASala;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.JugadorAutenticado;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.ListarSalas;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.ObtenerSala;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.VerificacionDeIngreso;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.VerificarHeroe;
import com.nexusbattles.plataforma.salaspartidas.dominio.CreditosInsuficientes;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoDelHeroe;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.InventarioNoDisponible;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.NoEsElAnfitrion;
import com.nexusbattles.plataforma.salaspartidas.dominio.PaginaDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosInvalidos;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import com.nexusbattles.plataforma.salaspartidas.dominio.SalaNoEncontrada;
import com.nexusbattles.plataforma.salaspartidas.dominio.SalidaNoPermitida;
import com.nexusbattles.plataforma.salaspartidas.dominio.SalaPrivadaSinInvitacion;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeNoDisponible;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoDelHeroe;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

    @MockitoBean
    private ObtenerSala obtenerSala;

    @MockitoBean
    private AbandonarSala abandonarSala;

    @MockitoBean
    private CancelarSala cancelarSala;

    @MockitoBean
    private VerificarHeroe verificarHeroe;

    @MockitoBean
    private com.nexusbattles.plataforma.salaspartidas.aplicacion.IniciarPartida iniciarPartida;

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
    @DisplayName("con un token de ms-identidad el anfitrion sale de uid, no del apodo del sujeto")
    void elAnfitrionSaleDeUid() throws Exception {
        // Forma real del token de ms-identidad tras ADR-002: el sujeto es el
        // apodo y el identificador estable viaja en uid. Leer el sujeto a secas
        // reventaba UUID.fromString y devolvia 500 al crear una sala.
        Sala sala = salaDeEjemplo();
        when(crearSala.ejecutar(any(), any())).thenReturn(sala);

        mockMvc.perform(post("/api/v1/salas")
                        .with(jwt().jwt(token -> token
                                        .subject("demo_grupo6")
                                        .claim("uid", JUGADOR.toString())
                                        .claim("preferred_username", "demo_grupo6"))
                                .authorities(new org.springframework.security.core.authority
                                        .SimpleGrantedAuthority("ROLE_JUGADOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isCreated());

        // Las dos mitades del token, cada una de su sitio: el identificador de
        // `uid` -no del sujeto, que aqui es el apodo- y el apodo de
        // `preferred_username`, que es lo que el inventario reconoce.
        verify(crearSala).ejecutar(any(),
                org.mockito.ArgumentMatchers.eq(new JugadorAutenticado(JUGADOR, "demo_grupo6")));
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
    @DisplayName("HU-JUE-014 CA-06: si el libro de creditos no responde, una recompensa sale como 503 con su tipo estable, no como 500")
    void creditosNoDisponibles() throws Exception {
        when(crearSala.ejecutar(any(), any()))
                .thenThrow(new com.nexusbattles.plataforma.salaspartidas.dominio
                        .CreditosNoDisponibles("connection refused"));

        mockMvc.perform(post("/api/v1/salas")
                        .with(jugador())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO.replace("\"recompensaCreditos\": 0", "\"recompensaCreditos\": 320")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/creditos-no-disponibles"))
                .andExpect(jsonPath("$.title").value("El libro de creditos no esta disponible ahora mismo"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Nada quedo reservado")))
                .andExpect(jsonPath("$.errores").doesNotExist());
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
        when(ingresarASala.ejecutar(any(), any(), any())).thenReturn(salaDeEjemplo());

        mockMvc.perform(post("/api/v1/salas/{id}/participantes", ID_SALA).with(jugador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ABIERTA"));
    }

    @Test
    @DisplayName("el jugador que entra sale del token, aunque el cuerpo diga otra cosa")
    void elJugadorSaleDelToken() throws Exception {
        when(ingresarASala.ejecutar(any(), any(), any())).thenReturn(salaDeEjemplo());

        mockMvc.perform(post("/api/v1/salas/{id}/participantes", ID_SALA)
                        .with(jugador())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idJugador\": \"99999999-9999-9999-9999-999999999999\"}"))
                .andExpect(status().isOk());

        // El cuerpo solo aporta el codigo de invitacion; `idJugador` no existe
        // en IngresoRequest y Jackson lo descarta. La identidad es la del token.
        verify(ingresarASala).ejecutar(ID_SALA, new JugadorAutenticado(JUGADOR, "Simon_P"), null);
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
        when(ingresarASala.ejecutar(any(), any(), any())).thenThrow(new SalaNoEncontrada(ID_SALA));

        mockMvc.perform(post("/api/v1/salas/{id}/participantes", ID_SALA).with(jugador()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type")
                        .value("https://nexusbattles.local/errores/sala-no-encontrada"));
    }

    @Test
    @DisplayName("una sala privada responde 403, no 409")
    void salaPrivada() throws Exception {
        when(ingresarASala.ejecutar(any(), any(), any())).thenThrow(new SalaPrivadaSinInvitacion());

        mockMvc.perform(post("/api/v1/salas/{id}/participantes", ID_SALA).with(jugador()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type")
                        .value("https://nexusbattles.local/errores/sala-privada"));
    }

    @Test
    @DisplayName("una sala llena responde 409 diciendo el motivo")
    void salaLlena() throws Exception {
        when(ingresarASala.ejecutar(any(), any(), any()))
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

    // ---------------------------------------------------------------------
    // Codigo de invitacion: quien lo ve y quien no.
    //
    // Es la parte con mas riesgo de todo el controlador. El codigo abre una
    // sala privada, asi que cada camino que devuelve una sala tiene su prueba
    // de que NO lo filtra a quien no le toca.
    // ---------------------------------------------------------------------

    /** Sala privada cuyo anfitrion es {@code JUGADOR}, el del token de {@link #jugador()}. */
    private static Sala unaSalaPrivadaDelJugador() {
        return Sala.crear(
                new ParametrosDeSala(2, Modalidad.UNO_CONTRA_UNO, 0, false, true, null), JUGADOR);
    }

    @Test
    @DisplayName("al crear una sala privada, el anfitrion recibe su codigo de invitacion")
    void elAnfitrionRecibeElCodigo() throws Exception {
        Sala sala = unaSalaPrivadaDelJugador();
        when(crearSala.ejecutar(any(), any())).thenReturn(sala);

        mockMvc.perform(post("/api/v1/salas")
                        .with(jugador())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigoInvitacion").value(sala.codigoInvitacion()));
    }

    @Test
    @DisplayName("al entrar a una sala privada, el invitado NO recibe el codigo")
    void elInvitadoNoRecibeElCodigo() throws Exception {
        // La sala es de OTRO anfitrion: quien entra no es su dueno.
        Sala sala = Sala.crear(
                new ParametrosDeSala(2, Modalidad.UNO_CONTRA_UNO, 0, false, true, null),
                UUID.fromString("88888888-8888-8888-8888-888888888888"));
        when(ingresarASala.ejecutar(any(), any(), any())).thenReturn(sala);

        mockMvc.perform(post("/api/v1/salas/{id}/participantes", ID_SALA)
                        .with(jugador())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoInvitacion\": \"ABCD-2345\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoInvitacion").doesNotExist());
    }

    @Test
    @DisplayName("el codigo del cuerpo llega al caso de uso tal cual")
    void elCodigoViajaAlCasoDeUso() throws Exception {
        when(ingresarASala.ejecutar(any(), any(), any())).thenReturn(salaDeEjemplo());

        mockMvc.perform(post("/api/v1/salas/{id}/participantes", ID_SALA)
                        .with(jugador())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoInvitacion\": \"ABCD-2345\"}"))
                .andExpect(status().isOk());

        verify(ingresarASala).ejecutar(ID_SALA, new JugadorAutenticado(JUGADOR, "Simon_P"), "ABCD-2345");
    }

    // ---------------------------------------------------------------------
    // GET /salas/{id} — obtenerSala
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("GET de una sala devuelve 200 con sus datos")
    void obtieneLaSala() throws Exception {
        Sala sala = salaDeEjemplo();
        when(obtenerSala.ejecutar(ID_SALA)).thenReturn(sala);

        mockMvc.perform(get("/api/v1/salas/{id}", ID_SALA).with(jugador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sala.id().toString()))
                .andExpect(jsonPath("$.estado").value("ABIERTA"));
    }

    @Test
    @DisplayName("el anfitrion ve el codigo de su sala al consultarla; otro jugador no")
    void elCodigoSoloParaElAnfitrion() throws Exception {
        when(obtenerSala.ejecutar(ID_SALA)).thenReturn(unaSalaPrivadaDelJugador());

        mockMvc.perform(get("/api/v1/salas/{id}", ID_SALA).with(jugador()))
                .andExpect(jsonPath("$.codigoInvitacion").isNotEmpty());

        mockMvc.perform(get("/api/v1/salas/{id}", ID_SALA).with(otroJugador()))
                .andExpect(jsonPath("$.codigoInvitacion").doesNotExist());
    }

    @Test
    @DisplayName("GET de una sala inexistente responde 404 con su tipo")
    void obtieneSalaInexistente() throws Exception {
        when(obtenerSala.ejecutar(ID_SALA)).thenThrow(new SalaNoEncontrada(ID_SALA));

        mockMvc.perform(get("/api/v1/salas/{id}", ID_SALA).with(jugador()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type")
                        .value("https://nexusbattles.local/errores/sala-no-encontrada"));
    }

    @Test
    @DisplayName("GET de una sala sin token responde 401")
    void obtieneSinToken() throws Exception {
        mockMvc.perform(get("/api/v1/salas/{id}", ID_SALA))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------
    // DELETE /salas/{id} — cancelarSala
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("el anfitrion cancela su sala y recibe 204 sin cuerpo")
    void cancela() throws Exception {
        mockMvc.perform(delete("/api/v1/salas/{id}", ID_SALA).with(jugador()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(cancelarSala).ejecutar(ID_SALA, JUGADOR);
    }

    @Test
    @DisplayName("quien no es el anfitrion recibe 403 con su tipo")
    void cancelaSinSerAnfitrion() throws Exception {
        doThrow(new NoEsElAnfitrion()).when(cancelarSala).ejecutar(any(), any());

        mockMvc.perform(delete("/api/v1/salas/{id}", ID_SALA).with(jugador()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type")
                        .value("https://nexusbattles.local/errores/no-es-el-anfitrion"));
    }

    @Test
    @DisplayName("cancelar una partida ya empezada responde 409")
    void cancelaPartidaEmpezada() throws Exception {
        doThrow(new SalidaNoPermitida("La partida ya comenzo: la sala no se puede cancelar."))
                .when(cancelarSala).ejecutar(any(), any());

        mockMvc.perform(delete("/api/v1/salas/{id}", ID_SALA).with(jugador()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type")
                        .value("https://nexusbattles.local/errores/salida-no-permitida"));
    }

    @Test
    @DisplayName("cancelar sin token responde 401")
    void cancelaSinToken() throws Exception {
        mockMvc.perform(delete("/api/v1/salas/{id}", ID_SALA))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------
    // DELETE /salas/{id}/participantes — abandonarSala
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("abandonar la sala responde 204 y el jugador sale del token")
    void abandona() throws Exception {
        mockMvc.perform(delete("/api/v1/salas/{id}/participantes", ID_SALA).with(jugador()))
                .andExpect(status().isNoContent());

        verify(abandonarSala).ejecutar(ID_SALA, JUGADOR);
    }

    @Test
    @DisplayName("el anfitrion que intenta abandonar recibe 409 diciendo que cancele")
    void elAnfitrionNoAbandona() throws Exception {
        doThrow(new SalidaNoPermitida("El anfitrion no abandona su sala: la cancela."))
                .when(abandonarSala).ejecutar(any(), any());

        mockMvc.perform(delete("/api/v1/salas/{id}/participantes", ID_SALA).with(jugador()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El anfitrion no abandona su sala: la cancela."));
    }

    @Test
    @DisplayName("abandonar sin rol de jugador responde 403")
    void abandonaSinRol() throws Exception {
        mockMvc.perform(delete("/api/v1/salas/{id}/participantes", ID_SALA)
                        .with(jwt().jwt(t -> t.subject(JUGADOR.toString()))))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------------
    // GET /salas/{id}/verificacion-heroe — HU-SAL-003
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("la verificacion sale con la forma del contrato y el apodo del token")
    void verificaElHeroe() throws Exception {
        when(verificarHeroe.ejecutar(any(), any())).thenReturn(VerificacionDeIngreso.de(
                EstadoDelHeroe.disponible(HeroeDeCombate.aPleno("h-1", "Sombra de Vael", 140)), 320));

        mockMvc.perform(get("/api/v1/salas/{id}/verificacion-heroe", ID_SALA).with(jugador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("DISPONIBLE"))
                .andExpect(jsonPath("$.puedeIngresar").value(true))
                .andExpect(jsonPath("$.heroe.id").value("h-1"))
                .andExpect(jsonPath("$.heroe.nombre").value("Sombra de Vael"))
                .andExpect(jsonPath("$.heroe.vidaActual").value(140))
                .andExpect(jsonPath("$.heroe.vidaMaxima").value(140))
                .andExpect(jsonPath("$.creditosRequeridos").value(320));

        // El apodo con el que se pregunta al inventario sale del token, igual
        // que el identificador: ninguno de los dos viaja en la peticion.
        verify(verificarHeroe).ejecutar(ID_SALA,
                new com.nexusbattles.plataforma.salaspartidas.aplicacion.JugadorAutenticado(
                        JUGADOR, "Simon_P"));
    }

    @Test
    @DisplayName("un heroe sin equipar no habilita el ingreso y no manda heroe")
    void verificacionSinHeroeEquipado() throws Exception {
        when(verificarHeroe.ejecutar(any(), any()))
                .thenReturn(VerificacionDeIngreso.de(EstadoDelHeroe.sinHeroeEquipado(), 0));

        mockMvc.perform(get("/api/v1/salas/{id}/verificacion-heroe", ID_SALA).with(jugador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("SIN_HEROE_EQUIPADO"))
                .andExpect(jsonPath("$.puedeIngresar").value(false))
                .andExpect(jsonPath("$.heroe").doesNotExist());
    }

    @Test
    @DisplayName("si el inventario no responde sale 503, no un veredicto inventado")
    void verificacionSinInventario() throws Exception {
        when(verificarHeroe.ejecutar(any(), any()))
                .thenThrow(new InventarioNoDisponible("apagado"));

        mockMvc.perform(get("/api/v1/salas/{id}/verificacion-heroe", ID_SALA).with(jugador()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type")
                        .value("https://nexusbattles.local/errores/inventario-no-disponible"));
    }

    @Test
    @DisplayName("una sala que no existe responde 404 tambien en la verificacion")
    void verificacionDeSalaInexistente() throws Exception {
        when(verificarHeroe.ejecutar(any(), any())).thenThrow(new SalaNoEncontrada(ID_SALA));

        mockMvc.perform(get("/api/v1/salas/{id}/verificacion-heroe", ID_SALA).with(jugador()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("sin token no se verifica nada")
    void verificacionSinToken() throws Exception {
        mockMvc.perform(get("/api/v1/salas/{id}/verificacion-heroe", ID_SALA))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------
    // POST /salas/{id}/partida — arranque del combate (HU-SAL-004)
    // ---------------------------------------------------------------------

    private static com.nexusbattles.plataforma.salaspartidas.dominio.Partida partidaDeEjemplo() {
        Sala sala = Sala.crear(
                new ParametrosDeSala(2, Modalidad.CONTRA_IA, 320, true, false, null), JUGADOR);
        return com.nexusbattles.plataforma.salaspartidas.dominio.Partida.iniciar(
                sala, java.time.Instant.parse("2026-09-17T20:00:00Z"));
    }

    @Test
    @DisplayName("el anfitrion arranca el combate y recibe 201 con el estado inicial")
    void iniciaLaPartida() throws Exception {
        var partida = partidaDeEjemplo();
        when(iniciarPartida.ejecutar(any(), any())).thenReturn(partida);

        mockMvc.perform(post("/api/v1/salas/{id}/partida", ID_SALA).with(jugador()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/partidas/" + partida.id()))
                .andExpect(jsonPath("$.id").value(partida.id().toString()))
                .andExpect(jsonPath("$.estado").value("EN_CURSO"))
                .andExpect(jsonPath("$.turnoActual.idJugador").value(JUGADOR.toString()));

        // Quien arranca sale del token, nunca del cuerpo: la peticion no lleva ninguno.
        verify(iniciarPartida).ejecutar(ID_SALA, new JugadorAutenticado(JUGADOR, "Simon_P"));
    }

    @Test
    @DisplayName("quien no es el anfitrion recibe 403, no arranca la partida de otro")
    void soloElAnfitrionArranca() throws Exception {
        when(iniciarPartida.ejecutar(any(), any())).thenThrow(new NoEsElAnfitrion());

        mockMvc.perform(post("/api/v1/salas/{id}/partida", ID_SALA).with(otroJugador()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type")
                        .value("https://nexusbattles.local/errores/no-es-el-anfitrion"));
    }

    @Test
    @DisplayName("una sala que ya no admite empezar responde 409 con su tipo")
    void salaQueNoPuedeEmpezar() throws Exception {
        when(iniciarPartida.ejecutar(any(), any()))
                .thenThrow(new IngresoNoPermitido("La sala necesita al menos un rival."));

        mockMvc.perform(post("/api/v1/salas/{id}/partida", ID_SALA).with(jugador()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type")
                        .value("https://nexusbattles.local/errores/ingreso-no-permitido"));
    }

    @Test
    @DisplayName("una sala que no existe responde 404 tambien al arrancar")
    void arrancarSalaInexistente() throws Exception {
        when(iniciarPartida.ejecutar(any(), any())).thenThrow(new SalaNoEncontrada(ID_SALA));

        mockMvc.perform(post("/api/v1/salas/{id}/partida", ID_SALA).with(jugador()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("sin token no se arranca ningun combate")
    void arrancarSinToken() throws Exception {
        mockMvc.perform(post("/api/v1/salas/{id}/partida", ID_SALA))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("la puerta de heroe cerrada sale como 422 con su tipo y su motivo (SCRUM-1074)")
    void puertaDeHeroeCerrada() throws Exception {
        when(ingresarASala.ejecutar(any(), any(), any()))
                .thenThrow(new HeroeNoDisponible(EstadoDelHeroe.sinHeroeEquipado()));

        mockMvc.perform(post("/api/v1/salas/{id}/participantes", ID_SALA).with(jugador()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type")
                        .value("https://nexusbattles.local/errores/heroe-no-equipado"))
                // El motivo concreto: la vista distingue «equipa un heroe» de
                // «tu heroe esta en otra batalla» sin leer el texto.
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("Equipa un heroe")));
    }

    @Test
    @DisplayName("tampoco se arranca el combate con la puerta cerrada")
    void puertaCerradaTampocoArranca() throws Exception {
        when(iniciarPartida.ejecutar(any(), any()))
                .thenThrow(new HeroeNoDisponible(
                        EstadoDelHeroe.ocupado(
                                new com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate(
                                        "h-1", "Sombra", null, 3, 100, 100),
                                "Torre del Alba")));

        mockMvc.perform(post("/api/v1/salas/{id}/partida", ID_SALA).with(jugador()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type")
                        .value("https://nexusbattles.local/errores/heroe-ocupado"))
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("Torre del Alba")));
    }

    /** Un jugador distinto del de {@link #jugador()}, para probar quien ve que. */
    private static org.springframework.test.web.servlet.request.RequestPostProcessor otroJugador() {
        return jwt().jwt(token -> token
                        .subject("88888888-8888-8888-8888-888888888888")
                        .claim("preferred_username", "Otro"))
                .authorities(new org.springframework.security.core.authority
                        .SimpleGrantedAuthority("ROLE_JUGADOR"));
    }
}
