package com.nexusbattles.plataforma.salaspartidas.configuracion;

import com.nexusbattles.plataforma.observabilidad.InterceptorDeTraza;
import com.nexusbattles.plataforma.resiliencia.CortaCircuitos;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
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
                               HeroeDelJugador heroes,
                               com.nexusbattles.plataforma.salaspartidas.sanciones.SancionesDelJugador sanciones) {
        return new CrearSala(repositorio, creditos, heroes, sanciones);
    }

    @Bean
    public ListarSalas listarSalas(RepositorioDeSalas repositorio) {
        return new ListarSalas(repositorio);
    }

    @Bean
    public IngresarASala ingresarASala(RepositorioDeSalas repositorio, CanalDeSala canal,
                                       HeroeDelJugador heroes, CreditosDelJugador creditos,
                                       com.nexusbattles.plataforma.salaspartidas.sanciones.SancionesDelJugador sanciones) {
        return new IngresarASala(repositorio, canal, heroes, creditos, sanciones);
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
            com.nexusbattles.plataforma.salaspartidas.aplicacion.LiquidarApuesta apuesta,
            com.nexusbattles.plataforma.salaspartidas.aplicacion.AcreditarRecompensa recompensa,
            com.nexusbattles.plataforma.salaspartidas.aplicacion.InformarEncuentroDeTorneo torneo) {
        return new com.nexusbattles.plataforma.salaspartidas.aplicacion.EjecutarAccion(
                partidas, canal, motor, apuesta, recompensa, torneo);
    }

    /**
     * HU-TOR-004 (CA-04): la sala que es un encuentro de torneo informa el
     * ganador a torneos al terminar, con la credencial de servicio de este
     * modulo. Sin URL, el arbitro anota el fallo y lo resuelve el administrador.
     */
    @Bean
    public com.nexusbattles.plataforma.salaspartidas.aplicacion.InformarEncuentroDeTorneo informarEncuentroDeTorneo(
            com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeVinculosDeTorneo vinculos,
            @org.springframework.beans.factory.annotation.Value("${salas.torneos.url:}") String urlDeTorneos,
            org.springframework.beans.factory.ObjectProvider<
                    com.nexusbattles.comun.seguridad.servicio.InterceptorDePortadorDeServicio> credencial,
            ClientHttpRequestFactory fabricaConTiempos) {
        com.nexusbattles.plataforma.salaspartidas.aplicacion.ArbitroDeTorneo arbitro;
        if (urlDeTorneos == null || urlDeTorneos.isBlank()) {
            arbitro = (idTorneo, numero, ganador, idPartida) -> {
                throw new com.nexusbattles.plataforma.salaspartidas.aplicacion.ArbitroDeTorneo.TorneoNoDisponible(
                        "SALAS_TORNEOS_URL no esta configurada en este entorno");
            };
        } else {
            RestClient.Builder constructor = constructorConTraza(fabricaConTiempos);
            credencial.ifAvailable(constructor::requestInterceptor);
            arbitro = new com.nexusbattles.plataforma.salaspartidas.integracion.ClienteTorneos(
                    constructor.build(), urlDeTorneos);
        }
        return new com.nexusbattles.plataforma.salaspartidas.aplicacion.InformarEncuentroDeTorneo(
                vinculos, arbitro, Clock.systemUTC());
    }

    /**
     * HU-JUE-012: al terminar, el resultado se informa al libro para que
     * acredite la recompensa por jugar. La sancion activa de cada humano se
     * consulta al mismo servicio que silencia el chat (D-14).
     */
    @Bean
    public com.nexusbattles.plataforma.salaspartidas.aplicacion.AcreditarRecompensa acreditarRecompensa(
            RepositorioDeSalas salas,
            com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeRecompensas recompensas,
            com.nexusbattles.plataforma.salaspartidas.aplicacion.AcreditadorDePartidas libro,
            com.nexusbattles.plataforma.salaspartidas.sanciones.SancionesDelJugador sanciones) {
        return new com.nexusbattles.plataforma.salaspartidas.aplicacion.AcreditarRecompensa(
                salas, recompensas, libro, sanciones, Clock.systemUTC());
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
            com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeRecompensas recompensas,
            RepositorioDePartidas partidas,
            com.nexusbattles.plataforma.salaspartidas.aplicacion.LiquidarApuesta liquidar,
            com.nexusbattles.plataforma.salaspartidas.aplicacion.AcreditarRecompensa acreditar,
            CanalDePartida canal) {
        return new com.nexusbattles.plataforma.salaspartidas.aplicacion.ReintentarLiquidaciones(
                liquidaciones, recompensas, partidas, liquidar, acreditar, canal);
    }

    /**
     * Cliente hacia el libro de creditos (ms-finanzas, contrato
     * {@code contracts/openapi/creditos.yaml}) — HU-JUE-014.
     *
     * <p>Propio, como los demas. Lleva la credencial de servicio (ADR-005)
     * cuando esta configurada, igual que el de inventario: ms-finanzas cierra
     * {@code /creditos/**} y {@code /partidas/**} a {@code ROLE_SERVICIO}
     * (#455), asi que sin credencial el libro responde 401.
     */
    @Bean
    public CreditosDelJugador creditosDelJugador(
            @org.springframework.beans.factory.annotation.Value("${salas.creditos.url}") String urlDelLibro,
            org.springframework.beans.factory.ObjectProvider<
                    com.nexusbattles.comun.seguridad.servicio.InterceptorDePortadorDeServicio> credencial,
            @Qualifier("cortaCreditos") CortaCircuitos corta,
            ClientHttpRequestFactory fabricaConTiempos) {
        RestClient.Builder constructor = constructorConTraza(fabricaConTiempos);
        credencial.ifAvailable(constructor::requestInterceptor);
        return new com.nexusbattles.plataforma.salaspartidas.integracion.ClienteCreditos(
                constructor.build(), urlDelLibro, corta);
    }

    /**
     * Cliente hacia el mismo libro para informar el resultado de la partida
     * (HU-JUE-012). Comparte URL, credencial y corta circuitos con
     * {@link #creditosDelJugador}: es el mismo servicio, y si esta caido lo
     * esta para las dos cosas.
     */
    @Bean
    public com.nexusbattles.plataforma.salaspartidas.aplicacion.AcreditadorDePartidas acreditadorDePartidas(
            @org.springframework.beans.factory.annotation.Value("${salas.creditos.url}") String urlDelLibro,
            org.springframework.beans.factory.ObjectProvider<
                    com.nexusbattles.comun.seguridad.servicio.InterceptorDePortadorDeServicio> credencial,
            @Qualifier("cortaCreditos") CortaCircuitos corta,
            ClientHttpRequestFactory fabricaConTiempos) {
        RestClient.Builder constructor = constructorConTraza(fabricaConTiempos);
        credencial.ifAvailable(constructor::requestInterceptor);
        return new com.nexusbattles.plataforma.salaspartidas.integracion.ClienteAcreditacionDePartidas(
                constructor.build(), urlDelLibro, corta);
    }

    /**
     * Cliente hacia el motor de combate.
     *
     * <p>Propio y no compartido con el de inventario: son dos integraciones
     * distintas y el dia que una necesite su propio tiempo de espera no debe
     * arrastrar a la otra. Por eso mismo cada uno lleva su corta circuitos
     * (HU-DIS-003, ver {@link ConfiguracionDeResiliencia}).
     *
     * <p><b>R8.1 — la credencial de servicio.</b> Hasta aqui este era el
     * <b>unico</b> cliente HTTP del servicio que se construia con un
     * {@code RestClient.builder()} pelado, sin el interceptor de portador: no
     * hacia falta, porque {@code motor-combate} no tenia seguridad ninguna y
     * aceptaba peticiones anonimas. Ahora {@code POST /api/v1/combate/ataques}
     * exige {@code ROLE_SERVICIO}, asi que la credencial de
     * {@code salas-partidas} (ADR-001 via el emisor de ADR-005) viaja tambien
     * aqui. Sin credencial configurada el cliente sale sin {@code Authorization}
     * y el motor responde 401, que el corta circuitos traduce a la degradacion
     * de HU-DIS-003: la partida avisa de que el combate no esta disponible en
     * vez de resolver un ataque que nadie autorizo.
     */
    @Bean
    public com.nexusbattles.plataforma.salaspartidas.dominio.MotorDeCombate motorDeCombate(
            @org.springframework.beans.factory.annotation.Value("${motor.combate.url:http://localhost:8104}")
            String urlDelMotor,
            @Qualifier("cortaMotorCombate") CortaCircuitos corta,
            org.springframework.beans.factory.ObjectProvider<
                    com.nexusbattles.comun.seguridad.servicio.InterceptorDePortadorDeServicio> credencial,
            ClientHttpRequestFactory fabricaConTiempos) {
        RestClient.Builder constructor = constructorConTraza(fabricaConTiempos);
        credencial.ifAvailable(constructor::requestInterceptor);
        return new com.nexusbattles.plataforma.salaspartidas.integracion.ClienteMotorCombate(
                constructor.build(), urlDelMotor, corta);
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
                    com.nexusbattles.comun.seguridad.servicio.InterceptorDePortadorDeServicio> credencial,
            ClientHttpRequestFactory fabricaConTiempos) {
        // Con tiempos de espera acotados (HU-DIS-003): ver
        // ConfiguracionDeResiliencia.fabricaDePeticionesConTiempos.
        RestClient.Builder constructor = constructorConTraza(fabricaConTiempos);
        credencial.ifAvailable(constructor::requestInterceptor);
        return constructor.build();
    }

    /**
     * Constructor de clientes HTTP con la traza puesta — regla 5, R11.
     *
     * <p>Existe para que <b>no se pueda olvidar</b>. Este archivo arma seis
     * clientes hacia seis servicios distintos, y la regla 5 dice que todos
     * propagan el trace id. Repetir `.requestInterceptor(...)` seis veces
     * garantiza que el septimo salga sin el: lo que se repite se olvida.
     *
     * <p>El interceptor no tiene estado —lee el MDC y pone una cabecera—, asi
     * que se instancia aqui en vez de inyectarse: una dependencia menos que
     * declarar en seis firmas de metodo.
     *
     * <p>El {@code traceparent} de ENTRADA lo pone
     * {@code TrazaAutoConfiguration}, que llega por las convenciones de
     * Gradle y no hay que declarar en ningun sitio.
     */
    private static RestClient.Builder constructorConTraza(ClientHttpRequestFactory fabricaConTiempos) {
        return RestClient.builder()
                .requestFactory(fabricaConTiempos)
                .requestInterceptor(new InterceptorDeTraza());
    }
}
