package nexus.inventario.api;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexusbattles.comun.seguridad.pruebas.DecodificadorDePrueba;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import java.util.List;
import java.util.UUID;
import nexus.inventario.aplicacion.BuscarElementosInventario;
import nexus.inventario.aplicacion.ConsultarElementoInventario;
import nexus.inventario.aplicacion.ConsultarInventarioPaginado;
import nexus.inventario.aplicacion.GestionarEquipamiento;
import nexus.inventario.aplicacion.GestionarInventario;
import nexus.inventario.configuracion.IdentidadDelLlamador;
import nexus.inventario.configuracion.SeguridadConfig;
import nexus.inventario.aplicacion.PaginaInventario;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.EquipamientoHeroe;
import nexus.inventario.dominio.TipoElementoInventario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Quien es el propietario del inventario, con la cadena de seguridad real y
 * tokens firmados de verdad.
 *
 * <p>Antes {@code X-User-Name} era la identidad sin verificar: con el apodo
 * de otro se leia, creaba, equipaba o borraba en su inventario. Ahora la
 * cabecera solo cuenta cuando la manda un servicio con credencial
 * (salas-partidas al verificar el heroe de cada participante); un jugador es
 * quien dice su token, diga lo que diga la cabecera.
 */
@WebMvcTest(controllers = {InventarioController.class, EquipamientoController.class})
@Import({SeguridadConfig.class, IdentidadDelLlamador.class, DecodificadorDePrueba.class, ManejadorDeErrores.class})
class SeguridadDelInventarioTest {

    private static final String VITRINA = "/api/v1/inventario/elementos";
    private static final EmisorDeTokensDePrueba EMISOR = EmisorDeTokensDePrueba.emisor();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private GestionarInventario gestion;
    @MockitoBean
    private ConsultarInventarioPaginado consulta;
    @MockitoBean
    private BuscarElementosInventario busqueda;
    @MockitoBean
    private ConsultarElementoInventario consultaElemento;
    @MockitoBean
    private GestionarEquipamiento equipamiento;

    private static PaginaInventario paginaVacia() {
        return new PaginaInventario(List.of(), 0, 16, 0, 0, true);
    }

    @Test
    @DisplayName("sin token, 401: la cabecera sola ya no identifica a nadie")
    void sinTokenEs401() throws Exception {
        mvc.perform(get(VITRINA).header("X-User-Name", "jugador-A"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(VITRINA).header("X-User-Name", "jugador-A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productoId\":\"p\",\"tipo\":\"ITEM\",\"nombrePropio\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(VITRINA + "/busqueda").param("criterio", "espada").header("X-User-Name", "jugador-A"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(consulta, gestion, busqueda);
    }

    @Test
    @DisplayName("un jugador es quien dice su token: la vitrina que ve es la suya aunque la cabecera diga otra")
    void elJugadorEsElDelToken() throws Exception {
        when(consulta.consultar(eq("lyra_roja"), anyInt())).thenReturn(paginaVacia());

        mvc.perform(get(VITRINA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + EMISOR.tokenDeJugador("lyra_roja", UUID.randomUUID()))
                        .header("X-User-Name", "jugador-suplantado"))
                .andExpect(status().isOk());

        verify(consulta).consultar("lyra_roja", 0);
    }

    @Test
    @DisplayName("un jugador crea y equipa en su propio inventario, sin cabecera")
    void elJugadorOperaSobreLoSuyo() throws Exception {
        when(gestion.crear(eq("lyra_roja"), anyString(), org.mockito.ArgumentMatchers.any(), anyString(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ElementoInventario("e-1", "p", TipoElementoInventario.ITEM, "x"));
        when(equipamiento.equipar("lyra_roja", "heroe-1", "arma-1"))
                .thenReturn(EquipamientoHeroe.vacio("heroe-1"));

        String token = "Bearer " + EMISOR.tokenDeJugador("lyra_roja", UUID.randomUUID());
        mvc.perform(post(VITRINA).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productoId\":\"p\",\"tipo\":\"ITEM\",\"nombrePropio\":\"x\"}"))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/v1/inventario/heroes/heroe-1/equipamiento/arma-1")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());

        verify(gestion).crear(eq("lyra_roja"), eq("p"), org.mockito.ArgumentMatchers.any(), eq("x"),
                org.mockito.ArgumentMatchers.any());
        verify(equipamiento).equipar("lyra_roja", "heroe-1", "arma-1");
    }

    @Test
    @DisplayName("un servicio con credencial actua por el jugador que declara en X-User-Name (ADR-001)")
    void elServicioDeclaraAlJugador() throws Exception {
        when(consulta.consultar(eq("anfitriona_e2e"), anyInt())).thenReturn(paginaVacia());

        mvc.perform(get(VITRINA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + EMISOR.tokenDeServicio("salas-partidas"))
                        .header("X-User-Name", "anfitriona_e2e"))
                .andExpect(status().isOk());

        verify(consulta).consultar("anfitriona_e2e", 0);
    }

    @Test
    @DisplayName("un servicio sin decir a quien afecta recibe 400, no un inventario de nadie")
    void elServicioSinCabeceraEs400() throws Exception {
        mvc.perform(get(VITRINA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + EMISOR.tokenDeServicio("salas-partidas")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(consulta);
    }

    @Test
    @DisplayName("un token caducado o firmado por otro es 401")
    void tokenInvalidoEs401() throws Exception {
        UUID uid = UUID.randomUUID();
        mvc.perform(get(VITRINA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + EMISOR.tokenCaducado("lyra_roja", uid)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(VITRINA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + EMISOR.tokenFirmadoPorOtro("lyra_roja", uid)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(consulta);
    }

    @Test
    @DisplayName("la consulta por id sigue siendo solo de subastas: ni un jugador ni otro servicio la ven")
    void laConsultaPorIdSigueSiendoDeSubastas() throws Exception {
        mvc.perform(get(VITRINA + "/e-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + EMISOR.tokenDeJugador("lyra_roja", UUID.randomUUID())))
                .andExpect(status().isForbidden());
        mvc.perform(get(VITRINA + "/e-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + EMISOR.tokenDeServicio("salas-partidas")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(consultaElemento);
    }
}
