package com.nexusbattles.plataforma.salaspartidas.configuracion;

import com.nexusbattles.comun.observabilidad.FiltroDeTraza;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.AbandonarSala;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.CancelarSala;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.CreditosDelJugador;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.CrearSala;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.IngresarASala;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.ListarSalas;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.HeroeDelJugador;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.ObtenerSala;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.VerificarHeroe;
import com.nexusbattles.plataforma.salaspartidas.dominio.CanalDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.client.RestClient;

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
    public IngresarASala ingresarASala(RepositorioDeSalas repositorio, CanalDeSala canal) {
        return new IngresarASala(repositorio, canal);
    }

    @Bean
    public ObtenerSala obtenerSala(RepositorioDeSalas repositorio) {
        return new ObtenerSala(repositorio);
    }

    @Bean
    public AbandonarSala abandonarSala(RepositorioDeSalas repositorio, CanalDeSala canal) {
        return new AbandonarSala(repositorio, canal);
    }

    @Bean
    public CancelarSala cancelarSala(RepositorioDeSalas repositorio, CreditosDelJugador creditos,
                                     CanalDeSala canal) {
        return new CancelarSala(repositorio, creditos, canal);
    }

    /** HU-SAL-003: verificacion previa de heroe, sin efectos. */
    @Bean
    public VerificarHeroe verificarHeroe(RepositorioDeSalas repositorio, HeroeDelJugador heroes) {
        return new VerificarHeroe(repositorio, heroes);
    }

    /**
     * Cliente HTTP hacia inventario.
     *
     * <p>Propio y no compartido con el del chat: son dos integraciones
     * distintas, con proveedores distintos, y el dia que una necesite un tiempo
     * de espera o un interceptor suyo no debe arrastrar a la otra.
     */
    @Bean
    public RestClient restClientInventario() {
        return RestClient.builder().build();
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
