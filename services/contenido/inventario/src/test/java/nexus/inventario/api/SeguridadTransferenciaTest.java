package nexus.inventario.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import nexus.inventario.aplicacion.TransferirElementoPorSubasta;
import nexus.inventario.configuracion.SeguridadConfig;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.TipoElementoInventario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Quien puede cambiar de dueno un objeto — T7 de FI-TRANSFER-1.
 *
 * <p>Esta es la operacion mas sensible del inventario: mueve propiedad. La
 * autorizacion se comprueba por {@code azp} del token de servicio, no por un
 * rol que el llamador se atribuya en una cabecera, porque una cabecera la pone
 * cualquiera que alcance el puerto. Habia pruebas para el bloqueo
 * ({@code SeguridadBloqueoSubastaTest}) y ninguna para la transferencia, que es
 * la que de verdad importa.
 */
@WebMvcTest(controllers = TransferenciaSubastaController.class)
@Import(SeguridadConfig.class)
class SeguridadTransferenciaTest {

    private static final String ELEMENTO = "elemento-1";
    private static final UUID COMPRADOR =
            UUID.fromString("7c1f0b2e-5d43-4a91-9f0c-1b2e3d4a5b6c");
    private static final UUID SUBASTA =
            UUID.fromString("89d9040d-52e0-44ae-8d8c-8ec033978afb");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TransferirElementoPorSubasta transferencias;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void sinTokenNoSeTransfiere() throws Exception {
        mvc.perform(peticion())
                .andExpect(status().isUnauthorized());

        verify(transferencias, never()).transferir(any(), any(), any(), any());
    }

    /**
     * Un jugador con sesion valida no puede moverse un objeto a su propio
     * inventario. Sin esto, cualquiera con token podria vaciar el inventario de
     * otro inventandose un identificador de subasta.
     */
    @Test
    void unJugadorAutenticadoNoPuedeTransferir() throws Exception {
        mvc.perform(peticion()
                        .with(jwt().jwt(token -> token
                                .claim("uid", COMPRADOR.toString())
                                .claim("azp", "frontend-web"))))
                .andExpect(status().isForbidden());

        verify(transferencias, never()).transferir(any(), any(), any(), any());
    }

    /** Otro servicio interno tampoco: la credencial no es un permiso general. */
    @Test
    void otroServicioConCredencialTampocoPuedeTransferir() throws Exception {
        mvc.perform(peticion()
                        .with(jwt().jwt(token -> token.claim("azp", "ms-finanzas"))))
                .andExpect(status().isForbidden());

        verify(transferencias, never()).transferir(any(), any(), any(), any());
    }

    /**
     * Y una cabecera de rol no sustituye al token: si esto diera 200, la
     * autorizacion seria decorativa.
     */
    @Test
    void unaCabeceraDeRolNoAutoriza() throws Exception {
        mvc.perform(peticion()
                        .header("X-User-Role", "SERVICIO")
                        .header("X-User-Name", "ms-subastas"))
                .andExpect(status().isUnauthorized());

        verify(transferencias, never()).transferir(any(), any(), any(), any());
    }

    @Test
    void elServicioDeSubastasSiPuedeTransferir() throws Exception {
        when(transferencias.transferir(eq(ELEMENTO), eq(COMPRADOR), eq(SUBASTA), any()))
                .thenReturn(new ElementoInventario(
                        ELEMENTO, UUID.randomUUID().toString(),
                        TipoElementoInventario.ITEM, "Amuleto", null, SUBASTA.toString()));

        mvc.perform(peticion()
                        .with(jwt().jwt(token -> token.claim("azp", "ms-subastas"))))
                .andExpect(status().isOk());

        verify(transferencias).transferir(eq(ELEMENTO), eq(COMPRADOR), eq(SUBASTA), any());
    }

    /**
     * La clave de idempotencia es obligatoria en el contrato, y desde
     * FI-TRANSFER-1 tambien en el controlador: es la unica traza de por que se
     * movio un objeto, y sin ella un reintento no se distingue de una segunda
     * transferencia.
     */
    @Test
    void sinClaveDeIdempotenciaNoSeTransfiere() throws Exception {
        mvc.perform(post("/api/v1/inventario/elementos/{elementoId}/transferencias", ELEMENTO)
                        .with(jwt().jwt(token -> token.claim("azp", "ms-subastas")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo()))
                .andExpect(status().isBadRequest());

        verify(transferencias, never()).transferir(any(), any(), any(), any());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion() {
        return post("/api/v1/inventario/elementos/{elementoId}/transferencias", ELEMENTO)
                .header("Idempotency-Key", "cierre-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo());
    }

    private String cuerpo() {
        return """
                {"nuevoPropietarioUid":"%s","subastaId":"%s"}
                """.formatted(COMPRADOR, SUBASTA);
    }
}
