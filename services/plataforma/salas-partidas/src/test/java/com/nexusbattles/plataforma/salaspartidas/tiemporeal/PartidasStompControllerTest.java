package com.nexusbattles.plataforma.salaspartidas.tiemporeal;

import com.nexusbattles.plataforma.salaspartidas.aplicacion.EjecutarAccion;
import com.nexusbattles.plataforma.salaspartidas.dominio.CanalDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.MotorDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.MotorNoDisponible;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDePartidas;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * La puerta de entrada del jugador al combate — RF-JUE-017.
 *
 * <p><b>Por que existe esta prueba.</b> Doce de las quince lineas de este
 * controlador estaban sin cubrir. Es el unico camino por el que llega una accion
 * de combate desde el navegador, y lo que decide aqui es de seguridad: de donde
 * sale la identidad de quien juega. Si saliera del cuerpo del mensaje en vez del
 * token, cualquiera podria jugar el turno de otro escribiendo su UUID.
 */
@DisplayName("PartidasStompController · quien juega sale del token, nunca del mensaje")
class PartidasStompControllerTest {

    private static final UUID PARTIDA = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID ANA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BRUNO = UUID.fromString("22222222-2222-2222-2222-222222222222");

    /** Lo que llego al caso de uso, para poder mirarlo. */
    private record Llamada(UUID idPartida, UUID idJugador, UUID idObjetivo, String codigo) {
    }

    private final List<Llamada> llamadas = new ArrayList<>();

    /**
     * Caso de uso de verdad, con el metodo sustituido para anotar lo que recibe.
     *
     * <p>Se extiende el real y no se crea una interfaz nueva: lo que se prueba es
     * el controlador, y cambiar la forma del colaborador para poder probarlo
     * seria probar otra cosa.
     */
    private EjecutarAccion espia() {
        RepositorioDePartidas sinUso = new RepositorioDePartidas() {
            @Override
            public Partida guardar(Partida partida) {
                return partida;
            }

            @Override
            public java.util.Optional<Partida> buscarPorId(UUID id) {
                return java.util.Optional.empty();
            }

            @Override
            public java.util.Optional<Partida> buscarPorSala(UUID idSala) {
                return java.util.Optional.empty();
            }
        };
        CanalDePartida canalMudo = new CanalDePartida() {
            @Override
            public void anunciarAccionResuelta(
                    com.nexusbattles.plataforma.salaspartidas.dominio.AccionResuelta accion) {
            }

            @Override
            public void anunciarInicio(
                    com.nexusbattles.plataforma.salaspartidas.dominio.Sala sala, Partida partida) {
            }

            @Override
            public void anunciarTurno(Partida partida) {
            }

            @Override
            public void anunciarFin(Partida partida) {
            }
        };
        MotorDeCombate motorMudo = (atacante, objetivo) -> {
            throw new IllegalStateException("no deberia llamarse");
        };

        return new EjecutarAccion(sinUso, canalMudo, motorMudo) {
            @Override
            public Partida ejecutar(UUID idPartida, UUID idJugador, UUID idObjetivo,
                                    String codigo) {
                llamadas.add(new Llamada(idPartida, idJugador, idObjetivo, codigo));
                return null;
            }
        };
    }

    private static Jwt token(Map<String, Object> claims) {
        return Jwt.withTokenValue("da-igual")
                .header("alg", "none")
                .claims(c -> c.putAll(claims))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    private static Principal jugador(UUID uid) {
        return new JwtAuthenticationToken(
                token(Map.of("sub", "demo_grupo6", "uid", uid.toString())),
                AuthorityUtils.NO_AUTHORITIES);
    }

    // =====================================================================
    // La identidad
    // =====================================================================

    @Test
    @DisplayName("el jugador sale del claim uid, no del apodo del sub")
    void laIdentidadSaleDelToken() {
        // Tras ADR-002 el `sub` de ms-identidad es el APODO, no un UUID.
        // Leerlo como identificador es el defecto que provoco el 500 del PR #404.
        new PartidasStompController(espia())
                .ejecutarAccion(PARTIDA, new EjecutarAccionRequest("ATAQUE", BRUNO),
                        jugador(ANA));

        assertEquals(ANA, llamadas.get(0).idJugador());
    }

    @Test
    @DisplayName("sin token no se juega: no se cae con NullPointer, se deniega")
    void sinTokenNoSeJuega() {
        PartidasStompController controlador = new PartidasStompController(espia());
        Principal cualquiera = new UsernamePasswordAuthenticationToken(
                "alguien", "clave", AuthorityUtils.NO_AUTHORITIES);

        assertAll(
                () -> assertThrows(AccessDeniedException.class, () -> controlador.ejecutarAccion(
                        PARTIDA, new EjecutarAccionRequest("ATAQUE", BRUNO), cualquiera)),
                () -> assertThrows(AccessDeniedException.class, () -> controlador.ejecutarAccion(
                        PARTIDA, new EjecutarAccionRequest("ATAQUE", BRUNO), null)),
                () -> org.junit.jupiter.api.Assertions.assertTrue(llamadas.isEmpty(),
                        "no debe haber llegado ninguna accion al caso de uso"));
    }

    // =====================================================================
    // El cuerpo
    // =====================================================================

    @Test
    @DisplayName("la partida sale del destino y el objetivo y la accion del cuerpo")
    void elCuerpoLlegaEntero() {
        new PartidasStompController(espia())
                .ejecutarAccion(PARTIDA, new EjecutarAccionRequest("GOLPE_FUERTE", BRUNO),
                        jugador(ANA));

        Llamada llamada = llamadas.get(0);
        assertAll(
                () -> assertEquals(PARTIDA, llamada.idPartida()),
                () -> assertEquals(BRUNO, llamada.idObjetivo()),
                () -> assertEquals("GOLPE_FUERTE", llamada.codigo()));
    }

    @Test
    @DisplayName("un mensaje sin cuerpo no revienta: pasa nulos y el caso de uso decide")
    void sinCuerpoNoRevienta() {
        // El cuerpo es `@Payload(required = false)`: un cliente puede mandar el
        // ataque basico sin decir nada mas. Si esto lanzara NullPointer, el
        // jugador veria el canal caerse en vez de un turno jugado.
        new PartidasStompController(espia()).ejecutarAccion(PARTIDA, null, jugador(ANA));

        Llamada llamada = llamadas.get(0);
        assertAll(
                () -> assertEquals(ANA, llamada.idJugador()),
                () -> assertNull(llamada.idObjetivo()),
                () -> assertNull(llamada.codigo()));
    }

    // =====================================================================
    // El rechazo
    // =====================================================================

    @Test
    @DisplayName("un error de negocio vuelve con el formato de problem details de la regla 4")
    void elErrorMantieneElFormatoDeLaPlataforma() {
        // Mismo formato que la API HTTP: quien programe el cliente no tiene que
        // aprender dos maneras de leer un error segun venga por REST o por STOMP.
        MotorNoDisponible caido = new MotorNoDisponible("apagado");

        ProblemDetail problema = new PartidasStompController(espia()).errorDeNegocio(caido);

        assertAll(
                () -> assertEquals(503, problema.getStatus()),
                () -> assertEquals(MotorNoDisponible.TIPO, problema.getType()),
                () -> assertEquals(caido.titulo(), problema.getTitle()),
                () -> assertEquals(caido.detalle(), problema.getDetail()));
    }
}
