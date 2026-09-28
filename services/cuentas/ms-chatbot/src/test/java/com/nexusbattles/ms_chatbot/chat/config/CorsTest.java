package com.nexusbattles.ms_chatbot.config;

import com.nexusbattles.ms_chatbot.chat.api.ChatController;
import com.nexusbattles.ms_chatbot.chat.identidad.ResolutorDeIdentidad;
import com.nexusbattles.ms_chatbot.chat.identidad.SesionesAnonimas;
import com.nexusbattles.ms_chatbot.chat.service.CalificacionService;
import com.nexusbattles.ms_chatbot.chat.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// El filtro de CORS responde la consulta previa del navegador antes de llegar
// a ningun controlador, asi que con ChatController basta para probar tambien
// las rutas del panel (/chatbot/admin/**), que van por la otra cadena.
// B11: ChatController resuelve la identidad con ResolutorDeIdentidad (real
// aqui) sobre SesionesAnonimas (simulada, sin base).
@WebMvcTest(ChatController.class)
@Import({SecurityConfig.class, ResolutorDeIdentidad.class})
class CorsTest {

    private static final String LIVE_SERVER = "http://localhost:5500";
    private static final String NPM_DEV = "http://127.0.0.1:8080";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

    @MockitoBean
    private CalificacionService calificacionService;

    @MockitoBean
    private SesionesAnonimas sesiones;

    @Test
    void consultaPreviaDelChat_desdeLiveServer_seAutoriza() throws Exception {
        mockMvc.perform(options("/chat/mensajes")
                .header(HttpHeaders.ORIGIN, LIVE_SERVER)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,x-id-sesion-anonima"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, LIVE_SERVER));
    }

    // Sin .cors() en la cadena estricta, esta consulta (que el navegador manda
    // SIN token) recibiria 401 y el panel no podria hacer ninguna llamada.
    @Test
    void consultaPreviaDelPanel_sinToken_seAutorizaIgual() throws Exception {
        mockMvc.perform(options("/chatbot/admin/analiticas")
                .header(HttpHeaders.ORIGIN, NPM_DEV)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, NPM_DEV));
    }

    // 1.3.0: atender un ticket desde el panel es un PATCH.
    @Test
    void consultaPreviaDelPanel_conPatch_seAutoriza() throws Exception {
        mockMvc.perform(options("/chatbot/admin/tickets/00000000-0000-0000-0000-000000000001")
                .header(HttpHeaders.ORIGIN, LIVE_SERVER)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, LIVE_SERVER))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("PATCH")));
    }

    @Test
    void consultaPrevia_desdeUnOrigenNoPermitido_seRechaza() throws Exception {
        mockMvc.perform(options("/chat/mensajes")
                .header(HttpHeaders.ORIGIN, "http://sitio-ajeno.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
            .andExpect(status().isForbidden());
    }

    // El panel lee el nombre del archivo exportado de Content-Disposition.
    @Test
    void peticionReal_exponeContentDisposition() throws Exception {
        mockMvc.perform(get("/chat/historial")
                .header("X-Id-Sesion-Anonima", "visitante-cors")
                .header(HttpHeaders.ORIGIN, LIVE_SERVER))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, LIVE_SERVER))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, containsString("Content-Disposition")))
            // B11: la sesion emitida y el Retry-After del 429 se leen desde el navegador.
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, containsString("X-Id-Sesion-Anonima")))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, containsString("Retry-After")));
    }
}
