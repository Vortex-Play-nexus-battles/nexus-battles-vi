package com.nexusbattles.ms_identidad.privacidad;

import com.nexusbattles.ms_identidad.onboarding.ServidorFalso;
import com.nexusbattles.ms_identidad.onboarding.traza.InterceptorDeTraza;
import com.nexusbattles.ms_identidad.onboarding.traza.Traza;
import com.nexusbattles.ms_identidad.privacidad.ConsultaDeSubastas.OperacionesAbiertas;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lo que el cierre de cuenta le pregunta a ms-subastas, contra un servidor HTTP
 * de verdad (ms-subastas-panel.yaml 1.0.0 y ms-subastas-pujas.yaml 0.4.0), con
 * el token de la persona: esas rutas solo responden por quien firma el token.
 */
@DisplayName("Subastas y pujas abiertas de la persona (cierre de cuenta)")
class ConsultaDeSubastasTest {

    private static final String AUTORIZACION = "Bearer token-de-ada";
    private static final String PUBLICADAS = "/api/v1/mis-subastas/publicadas";
    private static final String PUJAS = "/api/v1/mis-pujas";

    private ServidorFalso subastas;
    private ConsultaDeSubastas consulta;

    @BeforeEach
    void preparar() {
        subastas = new ServidorFalso()
                .responder("GET", PUBLICADAS, 200, "[]")
                .responder("GET", PUJAS, 200, "[]");
        consulta = new ConsultaDeSubastas(subastas.url() + "/api/v1", new InterceptorDeTraza());
    }

    @AfterEach
    void cerrar() {
        Traza.cerrar();
        subastas.close();
    }

    @Test
    @DisplayName("sin nada abierto: cero y cero, preguntando con el token de la persona")
    void sinNadaAbierto() {
        OperacionesAbiertas abiertas = consulta.delJugador(AUTORIZACION);

        assertThat(abiertas).isEqualTo(new OperacionesAbiertas(0, 0));
        assertThat(abiertas.hay()).isFalse();
        assertThat(subastas.recibidas("GET", PUBLICADAS)).singleElement().satisfies(peticion -> {
            assertThat(peticion.cabecera("Authorization")).isEqualTo(AUTORIZACION);
            assertThat(peticion.consulta()).isEqualTo("estado=ACTIVA");
        });
        assertThat(subastas.recibidas("GET", PUJAS)).singleElement()
                .satisfies(peticion -> assertThat(peticion.cabecera("Authorization")).isEqualTo(AUTORIZACION));
    }

    @Test
    @DisplayName("cuenta solo las publicaciones ACTIVA, aunque el servidor devuelva otras")
    void publicacionesActivas() {
        subastas.responder("GET", PUBLICADAS, 200, """
                [{"subastaId":"s1","nombreProducto":"Espada","estado":"ACTIVA"},
                 {"subastaId":"s2","nombreProducto":"Escudo","estado":"ADJUDICADA"},
                 {"subastaId":"s3","nombreProducto":"Arco","estado":"ACTIVA","vistas":4}]
                """);

        assertThat(consulta.delJugador(AUTORIZACION)).isEqualTo(new OperacionesAbiertas(2, 0));
    }

    @Test
    @DisplayName("pujas vigentes: GANANDO o AUTOMATICA en una subasta ACTIVA; las demas no cuentan")
    void pujasVigentes() {
        subastas.responder("GET", PUJAS, 200, """
                [{"subastaId":"a","estadoSubasta":"ACTIVA","estado":"GANANDO"},
                 {"subastaId":"b","estadoSubasta":"ACTIVA","estado":"AUTOMATICA"},
                 {"subastaId":"c","estadoSubasta":"ACTIVA","estado":"SUPERADA"},
                 {"subastaId":"d","estadoSubasta":"ADJUDICADA","estado":"GANADA"},
                 {"subastaId":"e","estadoSubasta":"CANCELADA","estado":"CERRADA"}]
                """);

        OperacionesAbiertas abiertas = consulta.delJugador(AUTORIZACION);

        assertThat(abiertas).isEqualTo(new OperacionesAbiertas(0, 2));
        assertThat(abiertas.hay()).isTrue();
    }

    @Test
    @DisplayName("un 5xx de ms-subastas: no disponible, nunca «no tiene nada»")
    void errorDelServidor() {
        subastas.responder("GET", PUJAS, 502, "{\"title\":\"Bad Gateway\"}");

        assertThatThrownBy(() -> consulta.delJugador(AUTORIZACION))
                .isInstanceOf(SubastasNoDisponiblesException.class);
    }

    @Test
    @DisplayName("un 401 de ms-subastas (token que no acepta): tampoco se sabe, no disponible")
    void rechazoDelToken() {
        subastas.responder("GET", PUBLICADAS, 401, "{\"title\":\"No autenticado\"}");

        assertThatThrownBy(() -> consulta.delJugador(AUTORIZACION))
                .isInstanceOf(SubastasNoDisponiblesException.class);
    }

    @Test
    @DisplayName("una respuesta que no es una lista: no disponible")
    void respuestaIlegible() {
        subastas.responder("GET", PUBLICADAS, 200, "{\"no\":\"es una lista\"}");

        assertThatThrownBy(() -> consulta.delJugador(AUTORIZACION))
                .isInstanceOf(SubastasNoDisponiblesException.class);
    }

    @Test
    @DisplayName("servicio apagado (conexion rechazada): no disponible")
    void servicioApagado() {
        String url = subastas.url();
        subastas.close();
        ConsultaDeSubastas sinServicio = new ConsultaDeSubastas(url + "/api/v1", new InterceptorDeTraza());

        assertThatThrownBy(() -> sinServicio.delJugador(AUTORIZACION))
                .isInstanceOf(SubastasNoDisponiblesException.class);
    }

    @Test
    @DisplayName("sin URL configurada: no disponible, sin llamar a nadie")
    void sinConfiguracion() {
        ConsultaDeSubastas sinUrl = new ConsultaDeSubastas("  ", new InterceptorDeTraza());

        assertThatThrownBy(() -> sinUrl.delJugador(AUTORIZACION))
                .isInstanceOf(SubastasNoDisponiblesException.class);
        assertThat(subastas.recibidas()).isEmpty();
    }

    @Test
    @DisplayName("propaga la traza de la peticion (regla 5)")
    void propagaLaTraza() {
        String traceId = Traza.abrir(null);

        consulta.delJugador(AUTORIZACION);

        assertThat(subastas.recibidas("GET", PUJAS)).singleElement()
                .satisfies(peticion -> assertThat(peticion.cabecera("traceparent")).contains(traceId));
    }
}
