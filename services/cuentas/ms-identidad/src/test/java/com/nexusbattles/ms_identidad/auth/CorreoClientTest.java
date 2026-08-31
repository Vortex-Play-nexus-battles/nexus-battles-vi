package com.nexusbattles.ms_identidad.auth;

import com.nexusbattles.ms_identidad.auth.correo.CorreoClient;
import com.nexusbattles.ms_identidad.auth.correo.dto.CorreoAvisoAccesoRequest;
import com.nexusbattles.ms_identidad.auth.correo.dto.CorreoBienvenidaRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorreoClientTest {

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RestClient.RequestBodySpec requestBodySpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private CorreoClient correoClient;

    private static final String URL_BIENVENIDA = "http://localhost:8082/api/v1/correos/bienvenida";
    private static final String URL_AVISO_ACCESO = "http://localhost:8082/api/v1/correos/aviso-acceso";

    @BeforeEach
    void setUp() {
        correoClient = new CorreoClient(restClient);
        ReflectionTestUtils.setField(correoClient, "urlBienvenida", URL_BIENVENIDA);
        ReflectionTestUtils.setField(correoClient, "urlAvisoAcceso", URL_AVISO_ACCESO);
    }

    @Test
    void debeEnviarCorreoDeBienvenidaConLosDatosCorrectos() {

        CorreoBienvenidaRequest datos = new CorreoBienvenidaRequest(
            "cristian@test.com", "cristianc", "Cristian", "Chaparro"
        );
        ArgumentCaptor<CorreoBienvenidaRequest> captor = ArgumentCaptor.forClass(CorreoBienvenidaRequest.class);

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(URL_BIENVENIDA)).thenReturn(requestBodySpec);
        when(requestBodySpec.body(captor.capture())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.accepted().build());

        correoClient.enviarBienvenida(datos);

        assertEquals("cristian@test.com", captor.getValue().getEmail());
        assertEquals("cristianc", captor.getValue().getApodo());
    }

    @Test
    void debeEnviarAvisoDeAccesoConLosDatosCorrectos() {

        CorreoAvisoAccesoRequest datos = new CorreoAvisoAccesoRequest(
            "cristian@test.com", "cristianc", "127.0.0.1", "2026-08-30T14:23:11-05:00"
        );
        ArgumentCaptor<CorreoAvisoAccesoRequest> captor = ArgumentCaptor.forClass(CorreoAvisoAccesoRequest.class);

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(URL_AVISO_ACCESO)).thenReturn(requestBodySpec);
        when(requestBodySpec.body(captor.capture())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.accepted().build());

        correoClient.enviarAvisoAcceso(datos);

        assertEquals("127.0.0.1", captor.getValue().getIp());
    }

    @Test
    void elRespaldoDeBienvenidaNoDebeLanzarExcepcion() {

        CorreoBienvenidaRequest datos = new CorreoBienvenidaRequest(
            "cristian@test.com", "cristianc", "Cristian", "Chaparro"
        );

        assertDoesNotThrow(() ->
            ReflectionTestUtils.invokeMethod(
                correoClient, "enviarBienvenidaConFallback", datos, new RuntimeException("Servicio caído")
            )
        );
    }

    @Test
    void elRespaldoDeAvisoAccesoNoDebeLanzarExcepcion() {

        CorreoAvisoAccesoRequest datos = new CorreoAvisoAccesoRequest(
            "cristian@test.com", "cristianc", "127.0.0.1", "2026-08-30T14:23:11-05:00"
        );

        assertDoesNotThrow(() ->
            ReflectionTestUtils.invokeMethod(
                correoClient, "enviarAvisoAccesoConFallback", datos, new RuntimeException("Servicio caído")
            )
        );
    }
}
