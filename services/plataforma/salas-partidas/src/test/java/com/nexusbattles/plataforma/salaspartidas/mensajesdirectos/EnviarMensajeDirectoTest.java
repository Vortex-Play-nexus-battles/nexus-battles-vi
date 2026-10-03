package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos;

import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.DirectorioDeJugadores.CuentaDeJugador;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.DirectorioDeJugadores.DirectorioNoDisponible;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.FiltroDeMensajesPrivados.Veredicto;
import com.nexusbattles.plataforma.salaspartidas.sanciones.SancionesDelJugador;
import com.nexusbattles.plataforma.salaspartidas.sanciones.SancionesNoDisponibles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Las reglas de un mensaje privado — B6, con todos los puertos en memoria.
 *
 * <p>Es la misma clase para STOMP y para REST: si una regla esta aqui, esta en
 * las dos vias.
 */
@DisplayName("EnviarMensajeDirecto · las reglas de las dos vias (B6)")
class EnviarMensajeDirectoTest {

    private static final UUID ANA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BRUNO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Remitente REMITENTE = new Remitente(ANA, "ana");
    private static final Instant AHORA = Instant.parse("2026-09-25T18:00:00.123456789Z");

    private final RepositorioDeMensajesDirectosEnMemoria repositorio = new RepositorioDeMensajesDirectosEnMemoria();
    private final Sanciones sanciones = new Sanciones();
    private final Directorio directorio = new Directorio();
    private final Filtro filtro = new Filtro();
    private final Limite limite = new Limite();
    private final Entrega entrega = new Entrega();
    private final Aviso aviso = new Aviso();

    private final EnviarMensajeDirecto enviar = new EnviarMensajeDirecto(repositorio, sanciones, directorio, filtro,
            limite, entrega, aviso, Clock.fixed(AHORA, ZoneOffset.UTC));

    @BeforeEach
    void brunoExisteYEstaActivo() {
        directorio.cuentas.put(BRUNO, new CuentaDeJugador(BRUNO, "bruno", "ACTIVO"));
    }

    private MensajeDirectoRechazado rechazo(Runnable envio) {
        return assertThrows(MensajeDirectoRechazado.class, envio::run);
    }

    @Nested
    @DisplayName("cuando todo pasa")
    class CuandoTodoPasa {

        @Test
        @DisplayName("se guarda, se entrega a los dos y el remitente es el del token")
        void seGuardaYSeEntrega() {
            MensajeDirecto mensaje = enviar.enviar(REMITENTE, BRUNO, "  hola bruno  ", "cli-1");

            assertAll(
                    () -> assertEquals(List.of(mensaje), repositorio.guardados),
                    () -> assertEquals(ANA, mensaje.remitente()),
                    () -> assertEquals("ana", mensaje.apodoRemitente()),
                    () -> assertEquals(BRUNO, mensaje.destinatario()),
                    () -> assertEquals("bruno", mensaje.apodoDestinatario(), "el apodo sale de identidad"),
                    () -> assertEquals("hola bruno", mensaje.texto(), "recortado como en el chat"),
                    () -> assertEquals("dm:" + ANA + ":" + BRUNO, mensaje.conversacion().clave()),
                    () -> assertEquals(AHORA.truncatedTo(java.time.temporal.ChronoUnit.MICROS), mensaje.enviadoEn()),
                    () -> assertNull(mensaje.leidoEn()),
                    () -> assertEquals("cli-1", mensaje.idCliente()),
                    () -> assertEquals(List.of(mensaje), entrega.entregados),
                    () -> assertEquals(List.of("hola bruno"), filtro.revisados, "se revisa lo que se guarda"));
        }

        @Test
        @DisplayName("un texto de 500 caracteres justos pasa; el limite es el del chat")
        void quinientosPasan() {
            // Texto de verdad: 500 «a» seguidas ya no es un mensaje (PoliticaDeTexto).
            MensajeDirecto mensaje = enviar.enviar(REMITENTE, BRUNO, "hola amigo".repeat(50), null);
            assertAll(
                    () -> assertEquals(500, mensaje.texto().length()),
                    () -> assertEquals(500, EnviarMensajeDirecto.LARGO_MAXIMO),
                    () -> assertNull(mensaje.idCliente(), "sin idCliente no se inventa uno"));
        }

        @Test
        @DisplayName("un idCliente en blanco es como no mandarlo")
        void idClienteEnBlanco() {
            assertNull(enviar.enviar(REMITENTE, BRUNO, "hola", "  ").idCliente());
        }
    }

    @Nested
    @DisplayName("idempotencia por idCliente")
    class Idempotencia {

        @Test
        @DisplayName("un reintento devuelve el mismo mensaje, no guarda otro y solo repite el eco")
        void reintento() {
            MensajeDirecto primero = enviar.enviar(REMITENTE, BRUNO, "hola", "cli-1");
            sanciones.consultas = 0;

            MensajeDirecto segundo = enviar.enviar(REMITENTE, BRUNO, "hola", "cli-1");

            assertAll(
                    () -> assertSame(primero, segundo),
                    () -> assertEquals(1, repositorio.guardados.size()),
                    () -> assertEquals(List.of(primero), entrega.entregados, "al destinatario solo una vez"),
                    () -> assertEquals(List.of(primero), entrega.ecos),
                    () -> assertEquals(1, limite.intentos, "el reintento no cuenta como otro envio"),
                    () -> assertEquals(0, sanciones.consultas, "ni vuelve a pasar por moderacion"));
        }

        @Test
        @DisplayName("si otro envio con el mismo idCliente gano la carrera, se devuelve el suyo")
        void carrera() {
            MensajeDirecto ganador = new MensajeDirecto(UUID.randomUUID(), Conversacion.entre(ANA, BRUNO), ANA,
                    "ana", BRUNO, "bruno", "hola", AHORA, null, "cli-1");
            repositorio.ganadorDeLaCarrera = ganador;

            MensajeDirecto devuelto = enviar.enviar(REMITENTE, BRUNO, "hola", "cli-1");

            assertAll(
                    () -> assertSame(ganador, devuelto),
                    () -> assertTrue(entrega.entregados.isEmpty(), "el ganador ya se entrego"),
                    () -> assertEquals(List.of(ganador), entrega.ecos));
        }
    }

    @Nested
    @DisplayName("rechazos, con el motivo del contrato y el idCliente de vuelta")
    class Rechazos {

        @Test
        @DisplayName("auditoria del 30-sep: un dibujo de simbolos o una racha del mismo caracter es TEXTO_INVALIDO y no se revisa ni se guarda")
        void unDibujoNoEsUnMensaje() {
            String dibujo = String.join("\n", " /\\_/\\ ", "( o.o )", " > ^ < ", "/|   |\\", "(_| |_)", " || || ",
                    " '' '' ");
            assertAll(
                    () -> assertEquals(MotivoDeRechazo.TEXTO_INVALIDO,
                            rechazo(() -> enviar.enviar(REMITENTE, BRUNO, dibujo, null)).motivo()),
                    () -> assertEquals(MotivoDeRechazo.TEXTO_INVALIDO,
                            rechazo(() -> enviar.enviar(REMITENTE, BRUNO, "a".repeat(40), null)).motivo()),
                    () -> assertTrue(filtro.revisados.isEmpty(), "rechazar lo evidente no cuesta una llamada"),
                    () -> assertTrue(entrega.entregados.isEmpty()));
        }

        @Test
        @DisplayName("texto vacio, en blanco o de mas de 500: TEXTO_INVALIDO")
        void textoInvalido() {
            assertAll(
                    () -> assertEquals(MotivoDeRechazo.TEXTO_INVALIDO,
                            rechazo(() -> enviar.enviar(REMITENTE, BRUNO, null, "c1")).motivo()),
                    () -> assertEquals(MotivoDeRechazo.TEXTO_INVALIDO,
                            rechazo(() -> enviar.enviar(REMITENTE, BRUNO, "   ", "c1")).motivo()),
                    () -> assertEquals("c1",
                            rechazo(() -> enviar.enviar(REMITENTE, BRUNO, "a".repeat(501), "c1")).idCliente()),
                    () -> assertTrue(repositorio.guardados.isEmpty()));
        }

        @Test
        @DisplayName("un idCliente de mas de 64 caracteres es un envio invalido, y no se devuelve")
        void idClienteLargo() {
            MensajeDirectoRechazado r = rechazo(() -> enviar.enviar(REMITENTE, BRUNO, "hola", "x".repeat(65)));
            assertAll(
                    () -> assertEquals(MotivoDeRechazo.TEXTO_INVALIDO, r.motivo()),
                    () -> assertNull(r.idCliente()));
        }

        @Test
        @DisplayName("escribirse a uno mismo: DESTINATARIO_PROPIO")
        void aSiMismo() {
            assertEquals(MotivoDeRechazo.DESTINATARIO_PROPIO,
                    rechazo(() -> enviar.enviar(REMITENTE, ANA, "hola", null)).motivo());
        }

        @Test
        @DisplayName("sin destinatario, o uno que no existe o no esta activo: DESTINATARIO_INEXISTENTE")
        void destinatarioQueNoRecibe() {
            UUID nadie = UUID.randomUUID();
            UUID suspendido = UUID.randomUUID();
            directorio.cuentas.put(suspendido, new CuentaDeJugador(suspendido, "susp", "SUSPENDIDO"));

            assertAll(
                    () -> assertEquals(MotivoDeRechazo.DESTINATARIO_INEXISTENTE,
                            rechazo(() -> enviar.enviar(REMITENTE, null, "hola", null)).motivo()),
                    () -> assertEquals(MotivoDeRechazo.DESTINATARIO_INEXISTENTE,
                            rechazo(() -> enviar.enviar(REMITENTE, nadie, "hola", null)).motivo()),
                    () -> assertEquals(MotivoDeRechazo.DESTINATARIO_INEXISTENTE,
                            rechazo(() -> enviar.enviar(REMITENTE, suspendido, "hola", null)).motivo()),
                    () -> assertTrue(repositorio.guardados.isEmpty()));
        }

        @Test
        @DisplayName("el remitente sancionado no escribe: SANCIONADO")
        void sancionado() {
            sanciones.sancionados.add(ANA);
            assertEquals(MotivoDeRechazo.SANCIONADO,
                    rechazo(() -> enviar.enviar(REMITENTE, BRUNO, "hola", null)).motivo());
            assertTrue(entrega.entregados.isEmpty());
        }

        @Test
        @DisplayName("la lista negra bloquea: TEXTO_NO_PERMITIDO y no se entrega")
        void listaNegra() {
            filtro.veredicto = Veredicto.BLOQUEADO;
            MensajeDirectoRechazado r = rechazo(() -> enviar.enviar(REMITENTE, BRUNO, "algo feo", "c9"));
            assertAll(
                    () -> assertEquals(MotivoDeRechazo.TEXTO_NO_PERMITIDO, r.motivo()),
                    () -> assertEquals("c9", r.idCliente()),
                    () -> assertEquals(422, r.estado()),
                    () -> assertTrue(repositorio.guardados.isEmpty()),
                    () -> assertTrue(entrega.entregados.isEmpty()));
        }

        @Test
        @DisplayName("demasiado rapido: DEMASIADO_RAPIDO con la espera, y antes de preguntar a nadie")
        void demasiadoRapido() {
            limite.espera = Duration.ofMillis(2_300);
            MensajeDirectoRechazado r = rechazo(() -> enviar.enviar(REMITENTE, BRUNO, "hola", null));
            assertAll(
                    () -> assertEquals(MotivoDeRechazo.DEMASIADO_RAPIDO, r.motivo()),
                    () -> assertEquals(Optional.of(3L), r.reintentarEnSegundos()),
                    () -> assertEquals(429, r.estado()),
                    () -> assertEquals(0, sanciones.consultas, "insistir no martillea a moderacion"),
                    () -> assertEquals(0, directorio.consultas),
                    () -> assertTrue(filtro.revisados.isEmpty()));
        }
    }

    @Nested
    @DisplayName("fallo cerrado (D-14): lo que no se pudo revisar no sale")
    class FalloCerrado {

        @Test
        @DisplayName("sanciones no responde")
        void sancionesCaidas() {
            sanciones.caidas = true;
            assertEquals(MotivoDeRechazo.MODERACION_NO_DISPONIBLE,
                    rechazo(() -> enviar.enviar(REMITENTE, BRUNO, "hola", null)).motivo());
        }

        @Test
        @DisplayName("identidad no responde")
        void directorioCaido() {
            directorio.caido = true;
            assertEquals(MotivoDeRechazo.MODERACION_NO_DISPONIBLE,
                    rechazo(() -> enviar.enviar(REMITENTE, BRUNO, "hola", null)).motivo());
        }

        @Test
        @DisplayName("la lista negra no responde")
        void listaNegraCaida() {
            filtro.veredicto = Veredicto.SIN_VERIFICAR;
            MensajeDirectoRechazado r = rechazo(() -> enviar.enviar(REMITENTE, BRUNO, "hola", null));
            assertAll(
                    () -> assertEquals(MotivoDeRechazo.MODERACION_NO_DISPONIBLE, r.motivo()),
                    () -> assertEquals(503, r.estado()),
                    () -> assertTrue(r.reintentarEnSegundos().isEmpty()),
                    () -> assertTrue(repositorio.guardados.isEmpty()));
        }
    }

    @Nested
    @DisplayName("aviso en la bandeja de quien no esta escuchando")
    class AvisoEnLaBandeja {

        @Test
        @DisplayName("cada mensaje sin escuchar avisa con el id de la racha: el primer no leido de ese remitente")
        void unaVezPorRacha() {
            MensajeDirecto primero = enviar.enviar(REMITENTE, BRUNO, "uno", null);
            MensajeDirecto segundo = enviar.enviar(REMITENTE, BRUNO, "dos", null);

            // Los dos avisos llevan el mismo id: notificaciones descarta el
            // repetido (409) y a la bandeja llega uno por racha.
            assertAll(
                    () -> assertEquals(List.of(primero, segundo), aviso.avisados),
                    () -> assertEquals(List.of(primero.id(), primero.id()), aviso.rachas));
        }

        @Test
        @DisplayName("tras leerlos, empieza otra racha y el aviso lleva otro id")
        void trasLeer() {
            enviar.enviar(REMITENTE, BRUNO, "uno", null);
            repositorio.marcarLeidos(BRUNO, ANA, AHORA);

            MensajeDirecto otro = enviar.enviar(REMITENTE, BRUNO, "otra vez", null);

            assertEquals(otro.id(), aviso.rachas.get(1));
        }

        @Test
        @DisplayName("si el primer no leido llego mientras escuchaba y se fue sin leerlo, el siguiente avisa igual")
        void rachaEmpezadaEscuchando() {
            // Lo vio llegar (contador, anuncio) pero cerro sin abrir la
            // conversacion: con «solo el primero avisa», nada de lo que siguiera
            // llegaria nunca a su bandeja.
            entrega.escuchando = true;
            MensajeDirecto visto = enviar.enviar(REMITENTE, BRUNO, "uno", null);
            entrega.escuchando = false;

            MensajeDirecto sinEscuchar = enviar.enviar(REMITENTE, BRUNO, "dos", null);

            assertAll(
                    () -> assertEquals(List.of(sinEscuchar), aviso.avisados),
                    () -> assertEquals(List.of(visto.id()), aviso.rachas));
        }

        @Test
        @DisplayName("si esta escuchando, no hace falta aviso")
        void escuchando() {
            entrega.escuchando = true;
            enviar.enviar(REMITENTE, BRUNO, "uno", null);
            assertTrue(aviso.avisados.isEmpty());
        }
    }

    @Nested
    @DisplayName("lo guardado no se pierde por un fallo de entrega")
    class FallosDeEntrega {

        @Test
        @DisplayName("si el broker falla, el mensaje queda guardado y se devuelve")
        void brokerFalla() {
            entrega.falla = true;
            MensajeDirecto mensaje = enviar.enviar(REMITENTE, BRUNO, "hola", "c1");
            assertEquals(List.of(mensaje), repositorio.guardados);
        }

        @Test
        @DisplayName("si el eco de un reintento falla, se devuelve igual")
        void ecoFalla() {
            MensajeDirecto primero = enviar.enviar(REMITENTE, BRUNO, "hola", "c1");
            entrega.falla = true;
            assertSame(primero, enviar.enviar(REMITENTE, BRUNO, "hola", "c1"));
        }
    }

    // ------------------------------------------------------------- dobles

    static final class Sanciones implements SancionesDelJugador {
        final List<UUID> sancionados = new ArrayList<>();
        boolean caidas;
        int consultas;

        @Override
        public boolean tieneSancionActiva(UUID idJugador) {
            consultas++;
            if (caidas) {
                throw new SancionesNoDisponibles();
            }
            return sancionados.contains(idJugador);
        }
    }

    static final class Directorio implements DirectorioDeJugadores {
        final Map<UUID, CuentaDeJugador> cuentas = new HashMap<>();
        boolean caido;
        int consultas;

        @Override
        public Optional<CuentaDeJugador> buscar(UUID uid) {
            consultas++;
            if (caido) {
                throw new DirectorioNoDisponible("caido");
            }
            return Optional.ofNullable(cuentas.get(uid));
        }
    }

    static final class Filtro implements FiltroDeMensajesPrivados {
        Veredicto veredicto = Veredicto.ENTREGABLE;
        final List<String> revisados = new ArrayList<>();

        @Override
        public Veredicto verificar(String texto) {
            revisados.add(texto);
            return veredicto;
        }
    }

    static final class Limite implements LimiteDeFrecuencia {
        Duration espera;
        int intentos;

        @Override
        public Optional<Duration> registrar(UUID remitente) {
            intentos++;
            return Optional.ofNullable(espera);
        }
    }

    static final class Entrega implements EntregaDeMensajesDirectos {
        final List<MensajeDirecto> entregados = new ArrayList<>();
        final List<MensajeDirecto> ecos = new ArrayList<>();
        boolean escuchando;
        boolean falla;

        @Override
        public void entregar(MensajeDirecto mensaje) {
            if (falla) {
                throw new IllegalStateException("broker caido");
            }
            entregados.add(mensaje);
        }

        @Override
        public void reenviarEco(MensajeDirecto mensaje) {
            if (falla) {
                throw new IllegalStateException("broker caido");
            }
            ecos.add(mensaje);
        }

        @Override
        public boolean estaEscuchando(UUID jugador) {
            if (falla) {
                throw new IllegalStateException("registro caido");
            }
            return escuchando;
        }
    }

    static final class Aviso implements AvisoDeMensajeDirecto {
        final List<MensajeDirecto> avisados = new ArrayList<>();
        final List<UUID> rachas = new ArrayList<>();

        @Override
        public void avisar(MensajeDirecto mensaje, UUID primerNoLeido) {
            avisados.add(mensaje);
            rachas.add(primerNoLeido);
        }
    }
}
