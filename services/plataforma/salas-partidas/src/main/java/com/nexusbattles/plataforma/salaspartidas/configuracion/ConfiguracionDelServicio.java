package com.nexusbattles.plataforma.salaspartidas.configuracion;

import com.nexusbattles.comun.observabilidad.FiltroDeTraza;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.CreditosDelJugador;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.CrearSala;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.IngresarASala;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.ListarSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Cableado del servicio.
 *
 * <p>Los casos de uso son clases de Java corrientes, sin anotaciones de Spring:
 * asi se prueban sin levantar un contexto y no quedan atados al framework. El
 * precio es declararlos aqui, y es barato.
 */
@Configuration
public class ConfiguracionDelServicio {

    @Bean
    public CrearSala crearSala(RepositorioDeSalas repositorio, CreditosDelJugador creditos) {
        return new CrearSala(repositorio, creditos);
    }

    @Bean
    public ListarSalas listarSalas(RepositorioDeSalas repositorio) {
        return new ListarSalas(repositorio);
    }

    @Bean
    public IngresarASala ingresarASala(RepositorioDeSalas repositorio) {
        return new IngresarASala(repositorio);
    }

    /**
     * Regla 5: propagacion del trace id. Se registra el primero de todos para
     * que cualquier error posterior, incluso los de seguridad, salga en la
     * bitacora con su traza.
     */
    @Bean
    public FilterRegistrationBean<FiltroDeTraza> filtroDeTraza() {
        FilterRegistrationBean<FiltroDeTraza> registro = new FilterRegistrationBean<>(new FiltroDeTraza());
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registro.addUrlPatterns("/*");
        return registro;
    }
}
