package com.nexusbattles.ms_subastas.pujas.api;

import com.nexusbattles.ms_subastas.pujas.creditos.CreditoClientException;
import com.nexusbattles.ms_subastas.pujas.service.PujaRechazadaException;
import com.nexusbattles.ms_subastas.pujas.service.SubastaNoEncontradaException;
import com.nexusbattles.ms_subastas.seguridad.TokenInvalidoException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.ProblemDetail;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * La tabla de traduccion motivo -> codigo HTTP es contrato publico: la interfaz
 * decide con ella si ofrecer "reintentar" o "corrige y vuelve a enviar". Se
 * prueba entera, y con @EnumSource para que anadir un motivo nuevo sin decidir
 * su codigo rompa el build en vez de salir como un 500 el dia de la demo.
 */
class ManejadorDeErroresPujasTest {

    /** Motivos que son una carrera perdida: reintentar con datos frescos tiene sentido. */
    private static final Set<PujaRechazadaException.Motivo> ESPERADOS_409 = EnumSet.of(
            PujaRechazadaException.Motivo.SUBASTA_NO_ACTIVA,
            PujaRechazadaException.Motivo.OFERTA_INSUFICIENTE);

    private final ManejadorDeErroresPujas manejador = new ManejadorDeErroresPujas();
    private final HttpServletRequest peticion = peticionA("/api/v1/subastas/" + UUID.randomUUID() + "/pujas");

    private static HttpServletRequest peticionA(String uri) {
        HttpServletRequest peticion = mock(HttpServletRequest.class);
        when(peticion.getRequestURI()).thenReturn(uri);
        return peticion;
    }

    @ParameterizedTest
    @EnumSource(PujaRechazadaException.Motivo.class)
    void cadaMotivoTieneCodigoYViajaComoCampoEstable(PujaRechazadaException.Motivo motivo) {
        ProblemDetail problema = manejador.manejarPujaRechazada(
                new PujaRechazadaException(motivo, "detalle de prueba"), peticion);

        int esperado = ESPERADOS_409.contains(motivo) ? 409 : 422;
        assertEquals(esperado, problema.getStatus(), "motivo " + motivo);

        assertNotNull(problema.getProperties());
        assertEquals(motivo.name(), problema.getProperties().get("motivo"),
                "la interfaz elige el mensaje por este campo, no por el texto libre");
    }

    @Test
    void elTokenInvalidoDaUn401QueNoCuentaQueFalloDelToken() {
        ProblemDetail problema = manejador.manejarTokenInvalido(
                new TokenInvalidoException("la firma del token no valida"), peticion);

        assertEquals(401, problema.getStatus());
        assertFalse(problema.getDetail().toLowerCase().contains("firma"),
                "decirle a quien prueba tokens si fallo la firma o la expiracion es regalarle informacion");
    }

    @Test
    void unaSubastaInexistenteDaUn404() {
        UUID inexistente = UUID.randomUUID();

        ProblemDetail problema = manejador.manejarSubastaNoEncontrada(
                new SubastaNoEncontradaException(inexistente), peticion);

        assertEquals(404, problema.getStatus());
        assertTrue(problema.getDetail().contains(inexistente.toString()));
    }

    /** Saldo insuficiente es una respuesta valida de ms-finanzas, no una averia. */
    @Test
    void elSaldoInsuficienteEsUnRechazoDeNegocioNoUnFalloDelServidor() {
        ProblemDetail problema = manejador.manejarFalloDeCreditos(
                new CreditoClientException(CreditoClientException.Motivo.SALDO_INSUFICIENTE, "sin saldo"), peticion);

        assertEquals(422, problema.getStatus());
        assertEquals("SALDO_INSUFICIENTE", problema.getProperties().get("motivo"));
    }

    /**
     * Una reserva que no existe o que ya se libero es una inconsistencia entre
     * ms-subastas y ms-finanzas: eso si es culpa del servidor, y no se le
     * cuelga al jugador como si hubiera hecho algo mal.
     */
    @Test
    void unaInconsistenciaDeReservasDaUn500SinDetallesInternos() {
        ProblemDetail problema = manejador.manejarFalloDeCreditos(
                new CreditoClientException(CreditoClientException.Motivo.RESERVA_YA_LIBERADA,
                        "La reserva abc ya estaba liberada"), peticion);

        assertEquals(500, problema.getStatus());
        assertFalse(problema.getDetail().contains("abc"));
        assertNull(problema.getProperties() == null ? null : problema.getProperties().get("motivo"),
                "un fallo interno no lleva motivo de negocio: la interfaz no puede hacer nada con el");
    }

    @Test
    void unFalloDeInventarioDaUn500ConFormatoProblemDetail() {
        ProblemDetail problema = manejador.manejarFalloDeInventario(
                new com.nexusbattles.ms_subastas.subastas.port.InventarioClientException("Fallo al contactar inventario"), peticion);

        assertEquals(500, problema.getStatus());
        assertEquals("https://nexusbattles.upb.edu.co/errors/error-de-inventario", problema.getType().toString());
        assertEquals("Error en el inventario", problema.getTitle());
    }
}
