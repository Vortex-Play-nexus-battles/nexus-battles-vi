package com.nexusbattles.plataforma.salaspartidas.tiemporeal;

import com.nexusbattles.plataforma.salaspartidas.dominio.AccionResuelta;
import com.nexusbattles.plataforma.salaspartidas.dominio.AccionResuelta.Accion;
import com.nexusbattles.plataforma.salaspartidas.dominio.AccionResuelta.Afectado;
import com.nexusbattles.plataforma.salaspartidas.dominio.CanalDePartida;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Canal de partida extremo a extremo — HU-SAL-005, tercer criterio del issue #31.
 *
 * <p>Prueba el tramo que SI es de este servicio: un cliente conectado por STOMP
 * con su JWT se suscribe a {@code /tema/partidas/{idPartida}}, el dominio
 * anuncia una {@link AccionResuelta} por su puerto, y el cliente recibe
 * {@code partida.accion.resuelta} con la vida de cada afectado. Servidor con
 * puerto, socket, broker y PostgreSQL 17 por Testcontainers: nada simulado.
 *
 * <p>Lo que NO se prueba aqui, porque no existe: quien produce el resultado. El
 * calculo del dano es del motor de combate, fuera de este bloque. El anuncio se
 * dispara desde la prueba por el mismo puerto que usara el caso de uso cuando
 * ese resultado llegue. Asi, cuando exista, lo unico que faltara sera llamar a
 * {@code anunciarAccionResuelta}.
 *
 * <p>Misma seguridad y misma sonda de suscripcion que {@link CanalDeSalaIT};
 * se reutiliza su decodificador de prueba.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(CanalDeSalaIT.SeguridadDePrueba.class)
@DisplayName("Canal de partida extremo a extremo (HU-SAL-005)")
class CanalDePartidaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    /** Token literal que SeguridadDePrueba traduce al jugador 2222... */
    private static final String VISITANTE = "visitante";

    private static final UUID ANA = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BRUNO = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID MAQUINA = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private static final String SONDA = "sonda-de-suscripcion";

    @Value("${local.server.port}")
    private int puerto;

    /** El puerto del dominio, resuelto al adaptador real: es lo que se prueba. */
    @Autowired
    private CanalDePartida canalDePartida;

    /** Solo para la sonda con la que se confirma que la suscripcion esta viva. */
    @Autowired
    private SimpMessagingTemplate mensajeria;

    private StompSession conectar(String token) throws Exception {
        StompHeaders connect = new StompHeaders();
        connect.add("Authorization", "Bearer " + token);
        return new WebSocketStompClient(new StandardWebSocketClient())
                .connectAsync("ws://localhost:" + puerto + "/ws", new WebSocketHttpHeaders(),
                        connect, new StompSessionHandlerAdapter() { })
                .get(10, TimeUnit.SECONDS);
    }

    /** Se suscribe a la partida y no vuelve hasta que la suscripcion esta activa (ver CanalDeSalaIT). */
    private BlockingQueue<String> suscribirseA(StompSession sesion, UUID idPartida) throws Exception {
        BlockingQueue<String> avisos = new LinkedBlockingQueue<>();
        BlockingQueue<String> sondas = new LinkedBlockingQueue<>();
        String destino = CanalDePartidaStomp.destinoDe(idPartida);

        sesion.subscribe(destino, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders cabeceras) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders cabeceras, Object cuerpo) {
                String texto = new String((byte[]) cuerpo, StandardCharsets.UTF_8);
                (texto.contains(SONDA) ? sondas : avisos).add(texto);
            }
        });

        boolean viva = false;
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!viva && System.nanoTime() < limite) {
            mensajeria.convertAndSend(destino, SONDA);
            viva = sondas.poll(200, TimeUnit.MILLISECONDS) != null;
        }
        assertTrue(viva, "la suscripcion al canal de la partida nunca quedo activa");
        while (sondas.poll(200, TimeUnit.MILLISECONDS) != null) {
            // vaciando sondas en vuelo
        }
        return avisos;
    }

    private static AccionResuelta flechaDoble(UUID idPartida) {
        return new AccionResuelta(idPartida, ANA,
                new Accion("FLECHA_DOBLE", "Flecha doble", null),
                List.of(new Afectado(BRUNO, 55, 100, -25),
                        new Afectado(MAQUINA, 30, 100, -70)));
    }

    @Test
    @DisplayName("quien sigue la partida recibe la accion resuelta con el payload del contrato")
    void laAccionResueltaLlegaAQuienSigueLaPartida() throws Exception {
        UUID idPartida = UUID.randomUUID();
        BlockingQueue<String> recibidos = suscribirseA(conectar(VISITANTE), idPartida);

        canalDePartida.anunciarAccionResuelta(flechaDoble(idPartida));

        String aviso = recibidos.poll(10, TimeUnit.SECONDS);
        assertNotNull(aviso, "el mensaje no llego por el canal de la partida");
        assertAll(
                () -> assertTrue(aviso.contains("\"tipo\":\"partida.accion.resuelta\""), aviso),
                () -> assertTrue(aviso.contains("\"idPartida\":\"" + idPartida + "\""), aviso),
                () -> assertTrue(aviso.contains("\"idEjecutor\":\"" + ANA + "\""), aviso),
                () -> assertTrue(aviso.contains("\"codigo\":\"FLECHA_DOBLE\""), aviso),
                () -> assertTrue(aviso.contains("\"nombre\":\"Flecha doble\""), aviso),
                // Los dos afectados, cada uno con vidaActual y vidaMaxima (RF-JUE-009).
                () -> assertTrue(aviso.contains(
                        "{\"idJugador\":\"" + BRUNO + "\",\"vidaActual\":55,\"vidaMaxima\":100,\"diferencia\":-25}"),
                        aviso),
                () -> assertTrue(aviso.contains(
                        "{\"idJugador\":\"" + MAQUINA + "\",\"vidaActual\":30,\"vidaMaxima\":100,\"diferencia\":-70}"),
                        aviso),
                // Nunca un color: el umbral del 60/40 lo aplica el cliente.
                () -> assertTrue(!aviso.contains("color") && !aviso.contains("porcentaje"), aviso));
    }

    @Test
    @DisplayName("una accion de otra partida no llega a quien sigue esta")
    void otraPartidaNoSeMezcla() throws Exception {
        UUID propia = UUID.randomUUID();
        UUID ajena = UUID.randomUUID();
        BlockingQueue<String> recibidos = suscribirseA(conectar(VISITANTE), propia);

        canalDePartida.anunciarAccionResuelta(flechaDoble(ajena));

        assertNull(recibidos.poll(2, TimeUnit.SECONDS),
                "cada partida tiene su destino: lo de otra no debe llegar");
    }
}
