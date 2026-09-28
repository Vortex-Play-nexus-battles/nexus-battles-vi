package com.nexusbattles.ms_subastas.notificaciones;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbattles.comun.observabilidad.FiltroDeTraza;
import com.nexusbattles.comun.seguridad.servicio.TokenDeServicio;
import com.nexusbattles.ms_subastas.seguridad.Traza;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.net.URI;
import java.net.http.HttpRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * La salida por correo solo existe con {@code CORREO_URL}: sin ella —local,
 * banco E2E— los avisos van solo a la bandeja. Y la traza de los trabajos
 * programados (regla 5).
 */
@DisplayName("Configuracion de la salida por correo y traza")
class CorreoConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CorreoConfig.class)
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @AfterEach
    void limpiarTraza() {
        MDC.remove(FiltroDeTraza.CLAVE_MDC);
    }

    @Test
    @DisplayName("sin CORREO_URL no hay clientes de correo ni de contacto")
    void sinUrlNoHayCorreo() {
        runner.run(contexto -> assertThat(contexto).hasNotFailed()
                .doesNotHaveBean(CorreoSubastaClient.class)
                .doesNotHaveBean(ContactoClient.class));
        runner.withPropertyValues("app.correo.url=  ")
                .run(contexto -> assertThat(contexto).doesNotHaveBean(CorreoSubastaClient.class));
    }

    @Test
    @DisplayName("con CORREO_URL hay los dos clientes, con o sin credencial de servicio")
    void conUrlHayCorreo() {
        runner.withPropertyValues("app.correo.url=http://correo:8086", "app.identidad.url=http://identidad:8089")
                .run(contexto -> assertThat(contexto).hasNotFailed()
                        .hasSingleBean(CorreoSubastaClient.class)
                        .hasSingleBean(ContactoClient.class));
        runner.withPropertyValues("app.correo.url=http://correo:8086")
                .withBean(TokenDeServicio.class, () -> () -> "token")
                .run(contexto -> assertThat(contexto).hasNotFailed()
                        .getBean(CorreoSubastaClient.class).isInstanceOf(CorreoSubastaClientHttp.class));
    }

    @Test
    @DisplayName("una pasada sin traza abre una propia y la cierra; si ya habia una, la respeta")
    void trazaDeLosTrabajos() {
        assertNull(MDC.get(FiltroDeTraza.CLAVE_MDC));
        boolean propia = Traza.abrir();
        assertTrue(propia);
        String traza = MDC.get(FiltroDeTraza.CLAVE_MDC);
        assertEquals(32, traza.length(), "traza W3C de 16 bytes en hexadecimal");

        HttpRequest peticion = Traza.propagar(HttpRequest.newBuilder(URI.create("http://x/"))).build();
        String cabecera = peticion.headers().firstValue(FiltroDeTraza.CABECERA).orElseThrow();
        assertTrue(cabecera.matches("00-" + traza + "-[0-9a-f]{16}-01"), cabecera);

        assertFalse(Traza.abrir(), "no se abre otra encima de la que ya hay");
        Traza.cerrar(false);
        assertEquals(traza, MDC.get(FiltroDeTraza.CLAVE_MDC));
        Traza.cerrar(propia);
        assertNull(MDC.get(FiltroDeTraza.CLAVE_MDC));

        HttpRequest sinTraza = Traza.propagar(HttpRequest.newBuilder(URI.create("http://x/"))).build();
        assertTrue(sinTraza.headers().firstValue(FiltroDeTraza.CABECERA).isEmpty());
    }
}
