package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("BandejaDeMensajesDirectos · cada uno lee lo suyo (B6)")
class BandejaDeMensajesDirectosTest {

    private static final UUID ANA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BRUNO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CARLA = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant T0 = Instant.parse("2026-09-25T18:00:00Z");

    private final RepositorioDeMensajesDirectosEnMemoria repositorio = new RepositorioDeMensajesDirectosEnMemoria();
    private final BandejaDeMensajesDirectos bandeja =
            new BandejaDeMensajesDirectos(repositorio, Clock.fixed(T0.plusSeconds(3600), ZoneOffset.UTC));

    private MensajeDirecto escribe(UUID de, String apodoDe, UUID a, String apodoA, String texto, long segundo) {
        MensajeDirecto m = new MensajeDirecto(UUID.randomUUID(), Conversacion.entre(de, a), de, apodoDe, a, apodoA,
                texto, T0.plusSeconds(segundo), null, null);
        repositorio.guardar(m);
        return m;
    }

    @Test
    @DisplayName("las conversaciones van de la mas reciente a la mas antigua, con el apodo del otro y sus no leidos")
    void conversaciones() {
        escribe(ANA, "ana", BRUNO, "bruno", "hola bruno", 1);
        escribe(BRUNO, "bruno", ANA, "ana", "hola ana", 2);
        escribe(BRUNO, "bruno", ANA, "ana", "sigues?", 3);
        escribe(CARLA, "carla", ANA, "ana", "hola, soy carla", 10);
        escribe(BRUNO, "bruno", CARLA, "carla", "esto no es de ana", 20);

        List<ResumenDeConversacion> deAna = bandeja.conversacionesDe(ANA);

        assertAll(
                () -> assertEquals(2, deAna.size(), "la de bruno con carla no es suya"),
                () -> assertEquals(CARLA, deAna.get(0).uidOtro()),
                () -> assertEquals("carla", deAna.get(0).apodoOtro()),
                () -> assertEquals(1, deAna.get(0).noLeidos()),
                () -> assertEquals(BRUNO, deAna.get(1).uidOtro()),
                () -> assertEquals("bruno", deAna.get(1).apodoOtro()),
                () -> assertEquals("sigues?", deAna.get(1).ultimoMensaje().texto()),
                () -> assertEquals(2, deAna.get(1).noLeidos()));
    }

    @Test
    @DisplayName("D-40: cada conversacion dice si se puede escribir, y bloquear no borra el historial")
    void estadoDeCadaConversacion() {
        RepositorioDeBloqueosEnMemoria bloqueados = new RepositorioDeBloqueosEnMemoria();
        BloqueosDeMensajes bloqueos = new BloqueosDeMensajes(bloqueados, Clock.fixed(T0, ZoneOffset.UTC));
        BandejaDeMensajesDirectos conBloqueos = new BandejaDeMensajesDirectos(repositorio,
                Clock.fixed(T0.plusSeconds(3600), ZoneOffset.UTC), bloqueos);
        escribe(ANA, "ana", BRUNO, "bruno", "hola bruno", 1);
        escribe(CARLA, "carla", ANA, "ana", "hola ana", 2);
        UUID dario = UUID.randomUUID();
        escribe(dario, "dario", ANA, "ana", "soy dario", 3);
        bloqueos.bloquear(ANA, BRUNO);
        bloqueos.bloquear(CARLA, ANA);

        List<ResumenDeConversacion> deAna = conBloqueos.conversacionesDe(ANA);
        List<ResumenDeConversacion> deBruno = conBloqueos.conversacionesDe(BRUNO);

        assertAll(
                () -> assertEquals(List.of(dario, CARLA, BRUNO),
                        deAna.stream().map(ResumenDeConversacion::uidOtro).toList()),
                () -> assertEquals(List.of(EstadoDeConversacion.ACTIVA, EstadoDeConversacion.NO_ADMITE,
                        EstadoDeConversacion.BLOQUEADA),
                        deAna.stream().map(ResumenDeConversacion::estado).toList()),
                () -> assertEquals(EstadoDeConversacion.NO_ADMITE, deBruno.get(0).estado(),
                        "Bruno ve que no puede escribirle, no que lo bloqueo"),
                () -> assertEquals(List.of("hola bruno"), conBloqueos.historial(BRUNO, ANA, null, null).stream()
                        .map(MensajeDirecto::texto).toList(), "el historial se queda para los dos"),
                () -> assertTrue(bandeja.conversacionesDe(ANA).stream()
                        .allMatch(c -> c.estado() == EstadoDeConversacion.ACTIVA),
                        "sin almacen de bloqueos todas estan activas"));
    }

    @Test
    @DisplayName("el apodo del otro sale del ultimo mensaje, lo escribiera quien lo escribiera")
    void apodoDelOtroEnAmbosSentidos() {
        escribe(ANA, "ana", BRUNO, "bruno", "hola", 1);
        List<ResumenDeConversacion> deAna = bandeja.conversacionesDe(ANA);
        List<ResumenDeConversacion> deBruno = bandeja.conversacionesDe(BRUNO);

        assertAll(
                () -> assertEquals("bruno", deAna.get(0).apodoOtro()),
                () -> assertEquals(0, deAna.get(0).noLeidos(), "lo propio no queda pendiente"),
                () -> assertEquals("ana", deBruno.get(0).apodoOtro()),
                () -> assertEquals(1, deBruno.get(0).noLeidos()));
    }

    @Test
    @DisplayName("el historial es el de la pareja pedida y nada mas, en orden de lectura")
    void historial() {
        escribe(ANA, "ana", BRUNO, "bruno", "uno", 1);
        escribe(BRUNO, "bruno", ANA, "ana", "dos", 2);
        escribe(CARLA, "carla", ANA, "ana", "de carla", 3);

        List<String> textos = bandeja.historial(ANA, BRUNO, null, null).stream()
                .map(MensajeDirecto::texto).toList();

        assertEquals(List.of("uno", "dos"), textos);
    }

    @Test
    @DisplayName("se pagina hacia atras con antesDe y limite")
    void paginacion() {
        for (int i = 1; i <= 5; i++) {
            escribe(ANA, "ana", BRUNO, "bruno", "m" + i, i);
        }

        List<String> ultimos = bandeja.historial(BRUNO, ANA, null, 2).stream().map(MensajeDirecto::texto).toList();
        List<String> anteriores = bandeja.historial(BRUNO, ANA, T0.plusSeconds(4), 2).stream()
                .map(MensajeDirecto::texto).toList();

        assertAll(
                () -> assertEquals(List.of("m4", "m5"), ultimos),
                () -> assertEquals(List.of("m2", "m3"), anteriores));
    }

    @Test
    @DisplayName("la conversacion con uno mismo no existe, y el limite va de 1 a 100")
    void validaciones() {
        assertAll(
                () -> assertEquals(MotivoDeRechazo.DESTINATARIO_PROPIO, assertThrows(MensajeDirectoRechazado.class,
                        () -> bandeja.historial(ANA, ANA, null, null)).motivo()),
                () -> assertEquals(400, assertThrows(ConsultaInvalida.class,
                        () -> bandeja.historial(ANA, BRUNO, null, 0)).estado()),
                () -> assertEquals("limite", assertThrows(ConsultaInvalida.class,
                        () -> bandeja.historial(ANA, BRUNO, null, 101)).errores().get(0).campo()),
                () -> assertTrue(bandeja.historial(ANA, BRUNO, null, 100).isEmpty()),
                () -> assertTrue(bandeja.historial(ANA, BRUNO, null, 1).isEmpty()));
    }

    @Test
    @DisplayName("marcar leido baja los no leidos de ese remitente, y repetirlo no cambia nada")
    void marcarLeida() {
        escribe(BRUNO, "bruno", ANA, "ana", "uno", 1);
        escribe(BRUNO, "bruno", ANA, "ana", "dos", 2);
        escribe(CARLA, "carla", ANA, "ana", "de carla", 3);

        bandeja.marcarLeida(ANA, BRUNO);
        bandeja.marcarLeida(ANA, BRUNO);
        bandeja.marcarLeida(ANA, ANA);

        assertAll(
                () -> assertEquals(0, repositorio.noLeidos(ANA, BRUNO)),
                () -> assertEquals(1, repositorio.noLeidos(ANA, CARLA), "lo de carla sigue pendiente"),
                () -> assertEquals(T0.plusSeconds(3600), repositorio.guardados.get(0).leidoEn()));
    }

    @Test
    @DisplayName("sin nadie de quien sean, no hay consulta")
    void nulos() {
        assertAll(
                () -> assertThrows(NullPointerException.class, () -> bandeja.conversacionesDe(null)),
                () -> assertThrows(NullPointerException.class, () -> bandeja.historial(null, ANA, null, null)),
                () -> assertThrows(NullPointerException.class, () -> bandeja.historial(ANA, null, null, null)),
                () -> assertThrows(NullPointerException.class, () -> bandeja.marcarLeida(null, ANA)),
                () -> assertThrows(NullPointerException.class, () -> bandeja.marcarLeida(ANA, null)));
    }
}
