package com.nexusbattles.ms_subastas.seguridad;

import com.nexusbattles.comun.seguridad.pruebas.DecodificadorDePrueba;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import com.nexusbattles.ms_subastas.pujas.api.PujaController;
import com.nexusbattles.ms_subastas.pujas.service.ConsultaDeParticipacionService;
import com.nexusbattles.ms_subastas.pujas.service.PujaApplicationService;
import com.nexusbattles.ms_subastas.subastas.api.SubastaListadoController;
import com.nexusbattles.ms_subastas.subastas.dto.PaginaDeSubastasResponse;
import com.nexusbattles.ms_subastas.subastas.port.IdentidadClient;
import com.nexusbattles.ms_subastas.subastas.service.SubastaListadoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La cadena de seguridad contra tokens con la forma real de ms-identidad
 * (RS256 + JWKS): lo publico es publico, lo demas exige un usuario, y un
 * token caducado o firmado por otro no entra. Es lo que faltaba: hasta el
 * 21-sep este servicio validaba con una clave HS256 que ms-identidad nunca
 * uso, y todo inicio de sesion real acababa en 401.
 */
@WebMvcTest(controllers = {SubastaListadoController.class, PujaController.class})
@Import({SeguridadWebConfig.class, DecodificadorDePrueba.class, IdentidadDesdeToken.class})
@DisplayName("SeguridadWebConfig · tokens de ms-identidad (ADR-002)")
class SeguridadWebConfigTest {

    private static final UUID SUBASTA = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID LYRA = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SubastaListadoService listado;

    @MockitoBean
    private PujaApplicationService pujas;

    @MockitoBean
    private ConsultaDeParticipacionService consultas;

    private final EmisorDeTokensDePrueba emisor = EmisorDeTokensDePrueba.emisor();

    @Test
    @DisplayName("el listado es publico: sin token responde 200")
    void listadoPublico() throws Exception {
        when(listado.listar(any(), anyInt(), anyInt()))
                .thenReturn(new PaginaDeSubastasResponse(List.of(), 0, 16, 0, 0));
        mvc.perform(get("/subastas")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("pujar sin token es 401")
    void pujarSinToken() throws Exception {
        mvc.perform(post("/subastas/{id}/pujas", SUBASTA).contentType("application/json").content("{\"monto\":10}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("un token caducado o firmado por otro emisor es 401, aunque tenga la forma correcta")
    void tokenCaducadoOAjeno() throws Exception {
        mvc.perform(get("/subastas/{id}/mi-participacion", SUBASTA)
                        .header("Authorization", "Bearer " + emisor.tokenCaducado("lyra", LYRA)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/subastas/{id}/mi-participacion", SUBASTA)
                        .header("Authorization", "Bearer " + emisor.tokenFirmadoPorOtro("lyra", LYRA)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("un token de servicio (rol SERVICIO) no es un jugador: 403")
    void tokenDeServicioNoPuja() throws Exception {
        mvc.perform(get("/subastas/{id}/mi-participacion", SUBASTA)
                        .header("Authorization", "Bearer " + emisor.tokenDeServicio("salas-partidas")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("con el token de un jugador de ms-identidad la peticion llega al controlador y el uid es el del token")
    void jugadorAutenticado() throws Exception {
        when(consultas.miParticipacion(SUBASTA, LYRA)).thenReturn(null);
        mvc.perform(get("/subastas/{id}/mi-participacion", SUBASTA)
                        .header("Authorization", "Bearer " + emisor.tokenDeJugador("lyra", LYRA)))
                .andExpect(status().isOk());
        org.mockito.Mockito.verify(consultas).miParticipacion(SUBASTA, LYRA);
    }
}
