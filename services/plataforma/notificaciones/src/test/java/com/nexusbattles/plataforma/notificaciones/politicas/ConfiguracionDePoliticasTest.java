package com.nexusbattles.plataforma.notificaciones.politicas;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import com.nexusbattles.plataforma.notificaciones.bandeja.AvisosPorIncorporar;
import com.nexusbattles.plataforma.notificaciones.bandeja.ServicioDeNotificaciones;
import com.nexusbattles.plataforma.observabilidad.InterceptorDeTraza;

/** SA-11 — cuando se encienden los avisos de cambios en las politicas de uso. */
@DisplayName("Notificaciones · configuracion de los avisos de cambios en las politicas")
class ConfiguracionDePoliticasTest {

    private final CursorPoliticasRepository cursores = mock(CursorPoliticasRepository.class);
    private final ServicioDeNotificaciones servicio = mock(ServicioDeNotificaciones.class);

    private static <T> ObjectProvider<T> ninguno(Class<T> tipo) {
        return new StaticListableBeanFactory().getBeanProvider(tipo);
    }

    private AvisosPorIncorporar armar(boolean activo, String url, int limite, long intervaloS, long ventanaDias,
                                      String zona) {
        return new ConfiguracionDePoliticas().avisosDePoliticas(activo, url, 1_000, 2_000, limite, intervaloS,
                ventanaDias, zona, ninguno(InterceptorDeTraza.class), ninguno(Clock.class), cursores, servicio);
    }

    @Test
    @DisplayName("con la configuracion por omision, importa (no necesita credencial: la lectura es publica)")
    void encendida() {
        assertInstanceOf(ImportadorDeCambiosDePolitica.class,
                armar(true, "http://127.0.0.1:9/api/v1", 50, 60, 30, "America/Bogota"));
    }

    @Test
    @DisplayName("apagada por variable: no hace nada")
    void apagada() {
        AvisosPorIncorporar avisos = armar(false, "http://127.0.0.1:9/api/v1", 50, 60, 30, "America/Bogota");

        assertFalse(avisos instanceof ImportadorDeCambiosDePolitica);
        avisos.incorporar("jugador-1");
        verifyNoInteractions(cursores, servicio);
    }

    @ParameterizedTest(name = "url={0}, limite={1}, intervalo={2}s, ventana={3}d, zona={4}")
    @CsvSource({
            "http://x/api/v1,0,60,30,America/Bogota",
            "http://x/api/v1,101,60,30,America/Bogota",
            "http://x/api/v1,50,-1,30,America/Bogota",
            "http://x/api/v1,50,60,0,America/Bogota",
            "http://x/api/v1,50,60,30,Marte/Olimpo",
            "' ',50,60,30,America/Bogota"})
    @DisplayName("una configuracion invalida la deja apagada en vez de tumbar el servicio")
    void invalida(String url, int limite, long intervaloS, long ventanaDias, String zona) {
        assertFalse(armar(true, url, limite, intervaloS, ventanaDias, zona) instanceof ImportadorDeCambiosDePolitica);
    }
}
