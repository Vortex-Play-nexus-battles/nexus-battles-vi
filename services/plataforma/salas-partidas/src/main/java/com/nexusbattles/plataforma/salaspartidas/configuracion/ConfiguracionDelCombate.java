package com.nexusbattles.plataforma.salaspartidas.configuracion;

import com.nexusbattles.plataforma.observabilidad.InterceptorDeTraza;
import com.nexusbattles.plataforma.resiliencia.parametros.LectorDeParametros;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.EjecutarAccion;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.HeroesDeLaMaquina;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.MisPartidas;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.PartidaModificadaConcurrentemente;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDePartidas;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
import com.nexusbattles.plataforma.salaspartidas.integracion.ClienteCatalogoDeHeroes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestClient;

import java.time.Clock;

/**
 * Cableado del combate contractual (B7).
 *
 * <p>Va aparte de {@link ConfiguracionDelServicio} a proposito: son las piezas
 * nuevas del combate —el heroe de la maquina, el historial, el tiempo por
 * turno— y juntas se leen mejor que repartidas entre las de salas.
 */
@Configuration
@EnableScheduling
public class ConfiguracionDelCombate {

    private static final Logger BITACORA = LoggerFactory.getLogger(ConfiguracionDelCombate.class);

    /**
     * D-B7-11: la maquina juega con un heroe aleatorio del catalogo. Lectura
     * publica, sin credencial, con traza y tiempos acotados como todo cliente
     * saliente (regla 5, HU-DIS-003).
     */
    @Bean
    public HeroesDeLaMaquina heroesDeLaMaquina(@Value("${salas.heroes.url}") String urlHeroes,
                                               ClientHttpRequestFactory fabricaConTiempos) {
        RestClient http = RestClient.builder()
                .requestFactory(fabricaConTiempos)
                .requestInterceptor(new InterceptorDeTraza())
                .build();
        return new ClienteCatalogoDeHeroes(http, urlHeroes);
    }

    /** {@code GET /partidas/mias} (1.7.0). */
    @Bean
    public MisPartidas misPartidas(RepositorioDePartidas partidas, RepositorioDeSalas salas) {
        return new MisPartidas(partidas, salas);
    }

    /**
     * Tiempo por turno — D-B7-14.
     *
     * <p>El documento no fija un tiempo por turno, asi que es un parametro del
     * catalogo de admin-parametros ({@code salas.partidas.segundos-por-turno})
     * que nace SIN VALOR: sin valor no hay limite. {@code
     * SALAS_PARTIDAS_SEGUNDOS_POR_TURNO} es el respaldo, 0 por omision (sin
     * limite). Se consulta en cada turno para que fijarlo no exija reiniciar.
     */
    @Bean
    public TiempoPorTurno tiempoPorTurno(LectorDeParametros parametros,
                                         @Value("${salas.partidas.segundos-por-turno:0}") long respaldo) {
        return new TiempoPorTurno(() -> parametros.entero(EjecutarAccion.CLAVE_SEGUNDOS_POR_TURNO, respaldo));
    }

    /**
     * Pasa los turnos agotados (D-B7-14). Sin tiempo por turno ninguna partida
     * tiene vencimiento, y la consulta no encuentra nada.
     */
    @Bean
    public VencimientoDeTurnos vencimientoDeTurnos(RepositorioDePartidas partidas, EjecutarAccion ejecutarAccion) {
        return new VencimientoDeTurnos(partidas, ejecutarAccion, Clock.systemUTC());
    }

    /** El tiempo por turno vigente: nulo sin limite. */
    public static final class TiempoPorTurno implements java.util.function.Supplier<Integer> {

        private final java.util.function.LongSupplier segundos;

        TiempoPorTurno(java.util.function.LongSupplier segundos) {
            this.segundos = segundos;
        }

        @Override
        public Integer get() {
            long valor = segundos.getAsLong();
            return valor > 0 ? (int) Math.min(valor, Integer.MAX_VALUE) : null;
        }
    }

    /** Busca los turnos agotados y los pasa, cada {@code salas.partidas.vencimiento-ms}. */
    public static final class VencimientoDeTurnos {

        private final RepositorioDePartidas partidas;
        private final EjecutarAccion ejecutarAccion;
        private final Clock reloj;

        VencimientoDeTurnos(RepositorioDePartidas partidas, EjecutarAccion ejecutarAccion, Clock reloj) {
            this.partidas = partidas;
            this.ejecutarAccion = ejecutarAccion;
            this.reloj = reloj;
        }

        @Scheduled(fixedDelayString = "${salas.partidas.vencimiento-ms:5000}",
                   initialDelayString = "${salas.partidas.vencimiento-ms:5000}")
        public void pasarTurnosAgotados() {
            for (Partida partida : partidas.conTurnoVencido(reloj.instant())) {
                try {
                    ejecutarAccion.agotarTurno(partida.id());
                } catch (PartidaModificadaConcurrentemente otroSeAdelanto) {
                    BITACORA.debug("La partida {} cambio mientras se agotaba su turno", partida.id());
                } catch (RuntimeException fallo) {
                    BITACORA.warn("No se pudo pasar el turno agotado de la partida {}: {}", partida.id(),
                            fallo.getMessage());
                }
            }
        }
    }
}
