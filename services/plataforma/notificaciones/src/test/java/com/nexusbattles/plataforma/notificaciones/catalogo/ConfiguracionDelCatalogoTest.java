package com.nexusbattles.plataforma.notificaciones.catalogo;

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

import com.nexusbattles.comun.seguridad.servicio.InterceptorDePortadorDeServicio;
import com.nexusbattles.plataforma.notificaciones.bandeja.AvisosPorIncorporar;
import com.nexusbattles.plataforma.notificaciones.bandeja.ServicioDeNotificaciones;
import com.nexusbattles.plataforma.observabilidad.InterceptorDeTraza;

/**
 * HU-NOT-001 — cuando se enciende la importacion de avisos del catalogo.
 *
 * <p>Solo con credencial de servicio (productos la exige) y con una
 * configuracion que productos acepte. En cualquier otro caso queda apagada y
 * la bandeja funciona como siempre: una variable mal puesta no puede dejar
 * a notificaciones sin arrancar.
 */
@DisplayName("Notificaciones · configuracion de los avisos del catalogo")
class ConfiguracionDelCatalogoTest {

    private final CursorCatalogoRepository cursores = mock(CursorCatalogoRepository.class);
    private final ServicioDeNotificaciones servicio = mock(ServicioDeNotificaciones.class);

    private static <T> ObjectProvider<T> ninguno(Class<T> tipo) {
        return new StaticListableBeanFactory().getBeanProvider(tipo);
    }

    private static <T> ObjectProvider<T> uno(Class<T> tipo, T valor) {
        StaticListableBeanFactory fabrica = new StaticListableBeanFactory();
        fabrica.addBean("unico", valor);
        return fabrica.getBeanProvider(tipo);
    }

    private AvisosPorIncorporar armar(boolean activo, int limite, long intervaloS, String zona,
                                      ObjectProvider<InterceptorDePortadorDeServicio> credencial) {
        return new ConfiguracionDelCatalogo().avisosDelCatalogo(
                activo, "http://127.0.0.1:9", 1_000, 2_000, limite, intervaloS, zona,
                ninguno(InterceptorDeTraza.class), credencial, ninguno(Clock.class), cursores, servicio);
    }

    @Test
    @DisplayName("con credencial de servicio y la configuracion por omision, importa")
    void encendida() {
        AvisosPorIncorporar avisos = armar(true, 50, 60, "America/Bogota",
                uno(InterceptorDePortadorDeServicio.class, new InterceptorDePortadorDeServicio(() -> "t")));

        assertInstanceOf(ImportadorDeAvisosDelCatalogo.class, avisos);
    }

    @Test
    @DisplayName("apagada por variable: no hace nada")
    void apagadaPorVariable() {
        AvisosPorIncorporar avisos = armar(false, 50, 60, "America/Bogota",
                uno(InterceptorDePortadorDeServicio.class, new InterceptorDePortadorDeServicio(() -> "t")));

        assertFalse(avisos instanceof ImportadorDeAvisosDelCatalogo);
        avisos.incorporar("jugador-1");
        verifyNoInteractions(cursores, servicio);
    }

    @Test
    @DisplayName("sin credencial de servicio queda apagada: productos rechazaria cada consulta")
    void sinCredencial() {
        AvisosPorIncorporar avisos = armar(true, 50, 60, "America/Bogota",
                ninguno(InterceptorDePortadorDeServicio.class));

        assertFalse(avisos instanceof ImportadorDeAvisosDelCatalogo);
        avisos.incorporar("jugador-1");
        verifyNoInteractions(cursores, servicio);
    }

    @ParameterizedTest(name = "limite={0}, intervalo={1}s, zona={2}")
    @CsvSource({
            "0,60,America/Bogota",
            "201,60,America/Bogota",
            "50,-1,America/Bogota",
            "50,60,Marte/Olimpo"})
    @DisplayName("una configuracion que productos no aceptaria la deja apagada en vez de tumbar el servicio")
    void configuracionInvalida(int limite, long intervaloS, String zona) {
        AvisosPorIncorporar avisos = armar(true, limite, intervaloS, zona,
                uno(InterceptorDePortadorDeServicio.class, new InterceptorDePortadorDeServicio(() -> "t")));

        assertFalse(avisos instanceof ImportadorDeAvisosDelCatalogo);
    }
}
