package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Conversacion y MensajeDirecto · la clave canonica y quien ve que (B6)")
class ConversacionTest {

    // "0..." va antes que "f..." en el orden de la forma canonica, pero como
    // long con signo 0xf... es NEGATIVO: UUID.compareTo los ordenaria al reves.
    private static final UUID PRIMERO = UUID.fromString("0aaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SEGUNDO = UUID.fromString("faaaaaaa-0000-0000-0000-000000000002");
    private static final UUID TERCERO = UUID.fromString("5aaaaaaa-0000-0000-0000-000000000003");

    @Test
    @DisplayName("una sola conversacion por pareja, escriba quien escriba")
    void unaPorPareja() {
        Conversacion ida = Conversacion.entre(PRIMERO, SEGUNDO);
        Conversacion vuelta = Conversacion.entre(SEGUNDO, PRIMERO);

        assertAll(
                () -> assertEquals(ida, vuelta),
                () -> assertEquals("dm:" + PRIMERO + ":" + SEGUNDO, ida.clave()),
                () -> assertEquals(ida.clave(), vuelta.clave()));
    }

    @Test
    @DisplayName("el orden es el del texto del uid, no el de UUID.compareTo")
    void ordenLexicografico() {
        assertTrue(SEGUNDO.compareTo(PRIMERO) < 0, "premisa: compareTo los ordena al reves");
        assertEquals(PRIMERO, Conversacion.entre(SEGUNDO, PRIMERO).menor());
    }

    @Test
    @DisplayName("la clave cabe en la columna y vuelve a ser la misma conversacion")
    void idaYVueltaPorLaClave() {
        Conversacion conversacion = Conversacion.entre(TERCERO, PRIMERO);
        String clave = conversacion.clave();

        assertAll(
                () -> assertEquals(76, clave.length()),
                () -> assertEquals(conversacion, Conversacion.desdeClave(clave)));
    }

    @Test
    @DisplayName("sabe quien es el otro y quien no esta en ella")
    void elOtro() {
        Conversacion conversacion = Conversacion.entre(PRIMERO, SEGUNDO);

        assertAll(
                () -> assertEquals(SEGUNDO, conversacion.otroDe(PRIMERO)),
                () -> assertEquals(PRIMERO, conversacion.otroDe(SEGUNDO)),
                () -> assertTrue(conversacion.incluye(PRIMERO)),
                () -> assertFalse(conversacion.incluye(TERCERO)),
                () -> assertThrows(IllegalArgumentException.class, () -> conversacion.otroDe(TERCERO)));
    }

    @Test
    @DisplayName("nadie conversa consigo mismo, ni con nulos, ni fuera de orden")
    void invariantes() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> Conversacion.entre(PRIMERO, PRIMERO)),
                () -> assertThrows(NullPointerException.class, () -> Conversacion.entre(null, PRIMERO)),
                () -> assertThrows(NullPointerException.class, () -> Conversacion.entre(PRIMERO, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new Conversacion(SEGUNDO, PRIMERO)));
    }

    @Test
    @DisplayName("una clave que no es del contrato no se acepta")
    void clavesMalas() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> Conversacion.desdeClave(null)),
                () -> assertThrows(IllegalArgumentException.class, () -> Conversacion.desdeClave("general")),
                () -> assertThrows(IllegalArgumentException.class, () -> Conversacion.desdeClave("dm:" + PRIMERO)),
                () -> assertThrows(IllegalArgumentException.class, () -> Conversacion.desdeClave("dm:x:y")));
    }

    @Test
    @DisplayName("lo propio cuenta como leido y el idCliente solo lo ve su autor")
    void quienVeQue() {
        MensajeDirecto sinLeer = new MensajeDirecto(UUID.randomUUID(), Conversacion.entre(PRIMERO, SEGUNDO),
                PRIMERO, "ana", SEGUNDO, "bruno", "hola", Instant.now(), null, "cli-1");
        MensajeDirecto leido = new MensajeDirecto(sinLeer.id(), sinLeer.conversacion(), PRIMERO, "ana",
                SEGUNDO, "bruno", "hola", sinLeer.enviadoEn(), Instant.now(), "cli-1");

        assertAll(
                () -> assertTrue(sinLeer.leidoPara(PRIMERO), "lo escrito por uno no es un pendiente suyo"),
                () -> assertFalse(sinLeer.leidoPara(SEGUNDO)),
                () -> assertTrue(leido.leidoPara(SEGUNDO)),
                () -> assertEquals("cli-1", sinLeer.idClientePara(PRIMERO)),
                () -> assertNull(sinLeer.idClientePara(SEGUNDO)),
                () -> assertEquals("bruno", sinLeer.apodoDelOtro(PRIMERO)),
                () -> assertEquals("ana", sinLeer.apodoDelOtro(SEGUNDO)));
    }

    @Test
    @DisplayName("un mensaje es entre los dos de su conversacion")
    void mensajeFueraDeSuConversacion() {
        assertThrows(IllegalArgumentException.class, () -> new MensajeDirecto(UUID.randomUUID(),
                Conversacion.entre(PRIMERO, SEGUNDO), PRIMERO, "ana", TERCERO, "carla", "hola",
                Instant.now(), null, null));
    }

    @Test
    @DisplayName("un remitente sin apodo se pinta como «Jugador», nunca vacio")
    void remitenteSinApodo() {
        assertAll(
                () -> assertEquals("Jugador", new Remitente(PRIMERO, null).apodo()),
                () -> assertEquals("Jugador", new Remitente(PRIMERO, " ").apodo()),
                () -> assertEquals("ana", new Remitente(PRIMERO, "ana").apodo()),
                () -> assertThrows(NullPointerException.class, () -> new Remitente(null, "ana")));
    }
}
