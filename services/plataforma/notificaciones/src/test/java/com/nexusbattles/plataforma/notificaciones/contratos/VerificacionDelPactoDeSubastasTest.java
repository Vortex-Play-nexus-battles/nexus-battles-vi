package com.nexusbattles.plataforma.notificaciones.contratos;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import com.nexusbattles.plataforma.notificaciones.Notificacion;
import com.nexusbattles.plataforma.notificaciones.bandeja.AvisoDuplicado;
import com.nexusbattles.plataforma.notificaciones.bandeja.ServicioDeNotificaciones;
import org.apache.hc.core5.http.HttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;

/**
 * Notificaciones cumple lo que ms-subastas cree que promete (B8): la
 * verificacion de proveedor de {@code contracts/pactos/ms-subastas-notificaciones.json}.
 *
 * <p>Mismo patron que las verificaciones de ms-finanzas e inventario: el
 * servicio arrancado de verdad, hablando HTTP por un puerto, con la cadena de
 * seguridad real (JWKS del emisor de prueba) y el manejador de errores real.
 * {@code au.com.dius.pact.provider:junit5} y no {@code junit5spring}, que en
 * Spring Boot 4 revienta por {@code javax.servlet}.
 *
 * <p>La capa de servicio se simula: un pacto describe la <b>puerta</b> —ruta,
 * credencial exigida, forma del cuerpo, codigo de estado, y que un
 * {@link AvisoDuplicado} sale como 409—. Lo que la bandeja hace con el aviso
 * ya lo prueban {@code ServicioDeNotificacionesTest} y
 * {@code ArranqueDeLaAplicacionIT}.
 *
 * <p><b>La credencial.</b> El pacto registra que ms-subastas manda
 * {@code Authorization: Bearer ...} con un valor de ejemplo. Aqui se sustituye
 * por un token de servicio real del mismo emisor que usa produccion, para que
 * lo que se verifique sea la respuesta del servicio y no un 401 por firma. La
 * interaccion que el consumidor graba SIN credencial se deja tal cual: su 401
 * es precisamente lo que fija.
 */
@Provider("notificaciones")
@PactFolder("../../../contracts/pactos")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Pacto: notificaciones cumple lo que ms-subastas espera")
class VerificacionDelPactoDeSubastasTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void identidad(DynamicPropertyRegistry registro) {
        EmisorDeTokensDePrueba.registrarJwks(registro);
    }

    /** Ver el javadoc de la clase: el pacto describe la puerta, no la bandeja. */
    @MockitoBean
    private ServicioDeNotificaciones servicio;

    @LocalServerPort
    private int puerto;

    @BeforeEach
    void apuntarAlServicioArrancado(PactVerificationContext contexto) {
        contexto.setTarget(new HttpTestTarget("localhost", puerto));
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verificarCadaInteraccion(PactVerificationContext contexto, HttpRequest peticion) {
        if (peticion.containsHeader("Authorization")) {
            peticion.setHeader("Authorization",
                    "Bearer " + EmisorDeTokensDePrueba.emisor().tokenDeServicio("ms-subastas"));
        }
        contexto.verifyInteraction();
    }

    // ------------------------------------------------------------- estados

    @State("el destinatario todavia no tiene ese aviso")
    void sinEseAviso() {
        Mockito.when(servicio.emitir(ArgumentMatchers.anyString(), ArgumentMatchers.any(Notificacion.class)))
                .thenReturn(Set.of());
    }

    @State("el destinatario ya tiene un aviso con ese identificador")
    void conEseAviso() {
        Mockito.when(servicio.emitir(ArgumentMatchers.anyString(), ArgumentMatchers.any(Notificacion.class)))
                .thenThrow(new AvisoDuplicado("el jugador ya tiene un aviso con ese identificador"));
    }
}
