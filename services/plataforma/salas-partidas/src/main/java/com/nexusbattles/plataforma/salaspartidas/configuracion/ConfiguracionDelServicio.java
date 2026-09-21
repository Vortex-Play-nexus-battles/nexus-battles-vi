package com.nexusbattles.plataforma.salaspartidas.configuracion;

import com.nexusbattles.comun.observabilidad.FiltroDeTraza;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.AbandonarSala;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.CancelarSala;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.CreditosDelJugador;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.CrearSala;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.IngresarASala;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.ListarSalas;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.HeroeDelJugador;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.IniciarPartida;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.ObtenerPartida;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.ObtenerSala;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.VerificarHeroe;
import com.nexusbattles.plataforma.salaspartidas.dominio.CanalDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.CanalDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDePartidas;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.client.RestClient;

import java.time.Clock;

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
    public CrearSala crearSala(RepositorioDeSalas repositorio, CreditosDelJugador creditos,
                               HeroeDelJugador heroes) {
        return new CrearSala(repositorio, creditos, heroes);
    }

    @Bean
    public ListarSalas listarSalas(RepositorioDeSalas repositorio) {
        return new ListarSalas(repositorio);
    }

    @Bean
    public IngresarASala ingresarASala(RepositorioDeSalas repositorio, CanalDeSala canal,
                                       HeroeDelJugador heroes, CreditosDelJugador creditos) {
        return new IngresarASala(repositorio, canal, heroes, creditos);
    }

    @Bean
    public ObtenerSala obtenerSala(RepositorioDeSalas repositorio) {
        return new ObtenerSala(repositorio);
    }

    @Bean
    public AbandonarSala abandonarSala(RepositorioDeSalas repositorio, CanalDeSala canal,
                                       CreditosDelJugador creditos) {
        return new AbandonarSala(repositorio, canal, creditos);
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

    /** HU-SAL-004 · RF-JUE-017: arranque del combate. */
    @Bean
    public IniciarPartida iniciarPartida(RepositorioDeSalas salas, RepositorioDePartidas partidas,
                                         CanalDePartida canal, HeroeDelJugador heroes) {
        return new IniciarPartida(salas, partidas, canal, heroes, Clock.systemUTC());
    }

    /** RF-JUE-017: el turno pasa de manos cuando el jugador juega. */
    @Bean
    public com.nexusbattles.plataforma.salaspartidas.aplicacion.AvanzarTurno avanzarTurno(
            RepositorioDePartidas partidas, CanalDePartida canal) {
        return new com.nexusbattles.plataforma.salaspartidas.aplicacion.AvanzarTurno(partidas, canal);
    }

    /** RF-JUE-006 · RF-JUE-017: la accion se resuelve en el motor y mueve la vida. */
    @Bean
    public com.nexusbattles.plataforma.salaspartidas.aplicacion.EjecutarAccion ejecutarAccion(
            RepositorioDePartidas partidas, CanalDePartida canal,
            com.nexusbattles.plataforma.salaspartidas.dominio.MotorDeCombate motor,
            com.nexusbattles.plataforma.salaspartidas.aplicacion.LiquidarApuesta apuesta) {
        return new com.nexusbattles.plataforma.salaspartidas.aplicacion.EjecutarAccion(
                partidas, canal, motor, apuesta);
    }

    /**
     * HU-JUE-014: liquidacion de la apuesta al terminar.
     *
     * <p>{@code salas.apuestas.si-gana-la-maquina} es una decision funcional
     * que ninguna HU toma (la IA no tiene bolsa a la que pagar); por defecto
     * se devuelve lo apostado. Ver {@code LiquidarApuesta}.
     */
    @Bean
    public com.nexusbattles.plataforma.salaspartidas.aplicacion.LiquidarApuesta liquidarApuesta(
            RepositorioDeSalas salas,
            com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeLiquidaciones liquidaciones,
            CreditosDelJugador creditos,
            @org.springframework.beans.factory.annotation.Value("${salas.apuestas.si-gana-la-maquina:LIBERAR}")
            com.nexusbattles.plataforma.salaspartidas.aplicacion.LiquidarApuesta.SiGanaLaMaquina siGanaLaMaquina) {
        return new com.nexusbattles.plataforma.salaspartidas.aplicacion.LiquidarApuesta(
                salas, liquidaciones, creditos, Clock.systemUTC(), siGanaLaMaquina);
    }

    /** HU-JUE-014, CA-06: las liquidaciones que el libro dejo pendientes se reintentan. */
    @Bean
    public com.nexusbattles.plataforma.salaspartidas.aplicacion.ReintentarLiquidaciones reintentarLiquidaciones(
            com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeLiquidaciones liquidaciones,
            RepositorioDePartidas partidas,
            com.nexusbattles.plataforma.salaspartidas.aplicacion.LiquidarApuesta liquidar,
            CanalDePartida canal) {
        return new com.nexusbattles.plataforma.salaspartidas.aplicacion.ReintentarLiquidaciones(
                liquidaciones, partidas, liquidar, canal);
    }

    /**
     * Cliente hacia el libro de creditos (ms-finanzas, contrato
     * {@code contracts/openapi/creditos.yaml}) — HU-JUE-014.
     *
     * <p>Propio, como los demas. Lleva la credencial de servicio (ADR-005)
     * cuando esta configurada, igual que el de inventario: ms-finanzas tiene
     * hoy {@code /creditos/**} abierto, pero el dia que lo cierre a
     * {@code ROLE_SERVICIO} no habra que tocar nada aqui.
     */
    @Bean
    public CreditosDelJugador creditosDelJugador(
            @org.springframework.beans.factory.annotation.Value("${salas.creditos.url}") String urlDelLibro,
            org.springframework.beans.factory.ObjectProvider<
                    com.nexusbattles.comun.seguridad.servicio.InterceptorDePortadorDeServicio> credencial) {
        RestClient.Builder constructor = RestClient.builder();
        credencial.ifAvailable(constructor::requestInterceptor);
        return new com.nexusbattles.plataforma.salaspartidas.integracion.ClienteCreditos(
                constructor.build(), urlDelLibro);
    }

    /**
     * Cliente hacia el motor de combate.
     *
     * <p>Propio y no compartido con el de inventario: son dos integraciones
     * distintas y el dia que una necesite su propio tiempo de espera no debe
     * arrastrar a la otra.
     */
    @Bean
    public com.nexusbattles.plataforma.salaspartidas.dominio.MotorDeCombate motorDeCombate(
            @org.springframework.beans.factory.annotation.Value("${motor.combate.url:http://localhost:8104}")
            String urlDelMotor) {
        return new com.nexusbattles.plataforma.salaspartidas.integracion.ClienteMotorCombate(
                RestClient.builder().build(), urlDelMotor);
    }

    /** RF-JUE-017: estado de la partida, para pintar y para reconectar. */
    @Bean
    public ObtenerPartida obtenerPartida(RepositorioDePartidas partidas) {
        return new ObtenerPartida(partidas);
    }

    /**
     * Cliente HTTP hacia inventario.
     *
     * <p>Propio y no compartido con el del chat: son dos integraciones
     * distintas, con proveedores distintos, y el dia que una necesite un tiempo
     * de espera o un interceptor suyo no debe arrastrar a la otra.
     *
     * <p>Lleva la credencial de servicio de salas-partidas (ADR-001 via el
     * emisor transitorio de ADR-005) cuando esta configurada
     * ({@code DIRECTORIO_ACTIVO_*}): inventario ya no cree en
     * {@code X-User-Name} a secas, solo cuando se la manda un servicio
     * autenticado. Sin credencial configurada el cliente sale sin
     * {@code Authorization} y la puerta de heroe respondera 503, que es lo que
     * corresponde: mejor un fallo visible que verificar el heroe de nadie.
     */
    @Bean
    public RestClient restClientInventario(
            org.springframework.beans.factory.ObjectProvider<
                    com.nexusbattles.comun.seguridad.servicio.InterceptorDePortadorDeServicio> credencial) {
        RestClient.Builder constructor = RestClient.builder();
        credencial.ifAvailable(constructor::requestInterceptor);
        return constructor.build();
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
