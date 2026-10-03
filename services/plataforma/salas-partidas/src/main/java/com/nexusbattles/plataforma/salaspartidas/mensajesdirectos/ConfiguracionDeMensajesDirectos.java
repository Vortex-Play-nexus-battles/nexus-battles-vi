package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos;

import com.nexusbattles.plataforma.salaspartidas.chat.PoliticaDeTexto;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.canal.EntregaStomp;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.integracion.ClienteDirectorioDeIdentidad;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.integracion.ClienteListaNegraMensajesPrivados;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.integracion.ClienteNotificacionesDeMensajes;
import com.nexusbattles.plataforma.salaspartidas.sanciones.SancionesDelJugador;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executors;

/**
 * Cableado de los mensajes privados — B6 (FEEDBACK DEL PROFESOR, no requisito
 * del documento).
 *
 * <p>Mismo criterio que {@code ConfiguracionDelServicio}: los casos de uso son
 * clases corrientes y se declaran aqui. Todo cliente saliente usa
 * {@code restClientServicios} (traza, tiempos acotados y credencial de
 * servicio): identidad, lista negra y notificaciones exigen token de servicio.
 *
 * <p>Variables de entorno (valores por omision de desarrollo local):
 * <ul>
 *   <li>{@code IDENTIDAD_URL} — ms-identidad sin {@code /api/v1}; la misma
 *       que usa moderacion-sanciones desde B2.</li>
 *   <li>{@code NOTIFICACIONES_URL} — notificaciones con su {@code /api/v1}.</li>
 *   <li>{@code MENSAJES_DIRECTOS_LIMITE_MENSAJES} y
 *       {@code MENSAJES_DIRECTOS_LIMITE_VENTANA_SEGUNDOS} — el limite de
 *       frecuencia, 5 cada 10 s: <b>provisional</b>, decision del PO
 *       pendiente.</li>
 *   <li>{@code MENSAJES_DIRECTOS_IDENTIDAD_CACHE_SEGUNDOS} — cuanto se recuerda
 *       que un destinatario existe (30 s).</li>
 * </ul>
 */
@Configuration
public class ConfiguracionDeMensajesDirectos {

    @Bean
    public LimiteDeFrecuencia limiteDeMensajesDirectos(
            @Value("${mensajes-directos.limite.mensajes:5}") int mensajes,
            @Value("${mensajes-directos.limite.ventana-segundos:10}") long ventanaSegundos) {
        return new LimiteDeFrecuenciaEnMemoria(mensajes, Duration.ofSeconds(ventanaSegundos), Clock.systemUTC());
    }

    @Bean
    public DirectorioDeJugadores directorioDeJugadores(
            @Qualifier("restClientServicios") RestClient http,
            @Value("${mensajes-directos.identidad.url:http://localhost:8089}") String urlDeIdentidad,
            @Value("${mensajes-directos.identidad.cache-segundos:30}") long cacheSegundos) {
        return new ClienteDirectorioDeIdentidad(http, urlDeIdentidad, Duration.ofSeconds(cacheSegundos),
                Clock.systemUTC());
    }

    @Bean
    public FiltroDeMensajesPrivados filtroDeMensajesPrivados(
            @Qualifier("restClientServicios") RestClient http,
            @Value("${chat.lista-negra.url}") String urlDeVerificacion) {
        return new ClienteListaNegraMensajesPrivados(http, urlDeVerificacion);
    }

    /** Un hilo virtual por aviso: quien escribe no espera a notificaciones. */
    @Bean
    public AvisoDeMensajeDirecto avisoDeMensajeDirecto(
            @Qualifier("restClientServicios") RestClient http,
            @Value("${mensajes-directos.notificaciones.url:}") String urlDeNotificaciones) {
        return new ClienteNotificacionesDeMensajes(http, urlDeNotificaciones,
                Executors.newVirtualThreadPerTaskExecutor());
    }

    @Bean
    public EntregaDeMensajesDirectos entregaDeMensajesDirectos(SimpMessagingTemplate plantilla,
                                                               SimpUserRegistry registro) {
        return new EntregaStomp(plantilla, registro);
    }

    @Bean
    public EnviarMensajeDirecto enviarMensajeDirecto(RepositorioDeMensajesDirectos repositorio,
                                                     SancionesDelJugador sanciones,
                                                     DirectorioDeJugadores directorio,
                                                     FiltroDeMensajesPrivados filtro,
                                                     LimiteDeFrecuencia limite,
                                                     EntregaDeMensajesDirectos entrega,
                                                     AvisoDeMensajeDirecto aviso,
                                                     PoliticaDeTexto.Limites limitesDeTexto) {
        return new EnviarMensajeDirecto(repositorio, sanciones, directorio, filtro, limite, entrega, aviso,
                Clock.systemUTC(), limitesDeTexto);
    }

    @Bean
    public BandejaDeMensajesDirectos bandejaDeMensajesDirectos(RepositorioDeMensajesDirectos repositorio) {
        return new BandejaDeMensajesDirectos(repositorio, Clock.systemUTC());
    }
}
