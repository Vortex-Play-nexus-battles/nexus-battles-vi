package com.nexusbattles.ms_subastas.subastas.port;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InventarioClientFakeTest {

    private InventarioClientFake fake;

    @BeforeEach
    void setUp() {
        fake = new InventarioClientFake();
    }

    @Test
    void buscarDevuelveVacioSiNoExisteYElementoSiEstaRegistrado() {
        String id = "elem-1";
        assertEquals(Optional.empty(), fake.buscar(id));

        UUID prod = UUID.randomUUID();
        UUID prop = UUID.randomUUID();
        fake.registrarElemento(new InventarioClient.ElementoInventario(id, prod, prop, false));

        Optional<InventarioClient.ElementoInventario> hallado = fake.buscar(id);
        assertTrue(hallado.isPresent());
        assertEquals(id, hallado.get().id());
        assertEquals(prod, hallado.get().productoId());
        assertEquals(prop, hallado.get().propietarioId());
        assertFalse(hallado.get().enUso());
    }

    @Test
    void reservarMarcaElementoEnUsoYGuardaRegistro() {
        String id = "elem-2";
        UUID prop = UUID.randomUUID();
        UUID subasta = UUID.randomUUID();
        fake.registrarElemento(new InventarioClient.ElementoInventario(id, UUID.randomUUID(), prop, false));

        fake.reservar(id, prop, subasta, "idem-1");

        assertTrue(fake.buscar(id).orElseThrow().enUso());
        assertTrue(fake.getReservas().containsKey(subasta.toString()));
        assertEquals(id, fake.getReservas().get(subasta.toString()).elementoInventarioId());
    }

    @Test
    void reservarRechazaSiYaEstaEnUso() {
        String id = "elem-3";
        UUID prop = UUID.randomUUID();
        fake.registrarElemento(new InventarioClient.ElementoInventario(id, UUID.randomUUID(), prop, true));

        assertThrows(InventarioClientException.class, () ->
                fake.reservar(id, prop, UUID.randomUUID(), "idem-2"));
    }

    @Test
    void liberarReservaDesmarcaEnUsoYRemueveRegistro() {
        String id = "elem-4";
        UUID prop = UUID.randomUUID();
        UUID subasta = UUID.randomUUID();
        fake.registrarElemento(new InventarioClient.ElementoInventario(id, UUID.randomUUID(), prop, false));

        fake.reservar(id, prop, subasta, "idem-3");
        assertTrue(fake.buscar(id).orElseThrow().enUso());

        fake.liberarReserva(id, subasta, "idem-3");
        assertFalse(fake.buscar(id).orElseThrow().enUso());
        assertFalse(fake.getReservas().containsKey(subasta.toString()));
    }

    @Test
    void transferirProductoActualizaNuevoPropietarioYDesmarcaEnUso() {
        String id = "elem-5";
        UUID vendedor = UUID.randomUUID();
        UUID comprador = UUID.randomUUID();
        UUID subasta = UUID.randomUUID();
        fake.registrarElemento(new InventarioClient.ElementoInventario(id, UUID.randomUUID(), vendedor, false));
        fake.reservar(id, vendedor, subasta, "idem-pub");

        fake.transferirProducto(id, comprador, subasta, "idem-buy");

        InventarioClient.ElementoInventario transferido = fake.buscar(id).orElseThrow();
        assertEquals(comprador, transferido.propietarioId());
        assertFalse(transferido.enUso());
        assertFalse(fake.getReservas().containsKey(subasta.toString()));

        assertEquals(1, fake.getTransferencias().size());
        assertEquals(comprador, fake.getTransferencias().get(0).nuevoPropietarioId());
        assertEquals("idem-buy", fake.getTransferencias().get(0).idempotencyKey());
    }

    @Test
    void simularFalloProvocaExcepcionEnTodasLasOperaciones() {
        fake.simularFallo(true, "Inventario inaccesible");

        assertThrows(InventarioClientException.class, () -> fake.buscar("elem-x"));
        assertThrows(InventarioClientException.class, () -> fake.reservar("elem-x", UUID.randomUUID(), UUID.randomUUID(), "k"));
        assertThrows(InventarioClientException.class, () -> fake.liberarReserva("elem-x", UUID.randomUUID(), "k"));
        assertThrows(InventarioClientException.class, () -> fake.transferirProducto("elem-x", UUID.randomUUID(), UUID.randomUUID(), "k"));
    }

    @Test
    void transferirProductoEsIdempotenteConMismaClave() {
        String id = "elem-idem";
        UUID vendedor = UUID.randomUUID();
        UUID comprador = UUID.randomUUID();
        UUID subasta = UUID.randomUUID();
        fake.registrarElemento(new InventarioClient.ElementoInventario(id, UUID.randomUUID(), vendedor, false));
        fake.reservar(id, vendedor, subasta, "idem-1");

        fake.transferirProducto(id, comprador, subasta, "idem-repetida");
        fake.transferirProducto(id, comprador, subasta, "idem-repetida");

        assertEquals(1, fake.getTransferencias().size());
        assertEquals(comprador, fake.buscar(id).orElseThrow().propietarioId());
    }

    @Test
    void reservarEsIdempotenteParaLaMismaSubasta() {
        String id = "elem-res-idem";
        UUID prop = UUID.randomUUID();
        UUID subasta = UUID.randomUUID();
        fake.registrarElemento(new InventarioClient.ElementoInventario(id, UUID.randomUUID(), prop, false));

        fake.reservar(id, prop, subasta, "clave-1");
        assertDoesNotThrow(() -> fake.reservar(id, prop, subasta, "clave-1"));

        assertTrue(fake.buscar(id).orElseThrow().enUso());
    }

    @Test
    void validaParametrosObligatoriosEnTodasLasOperaciones() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();

        assertThrows(InventarioClientException.class, () -> fake.buscar(null));
        assertThrows(InventarioClientException.class, () -> fake.buscar("   "));

        assertThrows(InventarioClientException.class, () -> fake.reservar(null, u1, u2, "k"));
        assertThrows(InventarioClientException.class, () -> fake.reservar("  ", u1, u2, "k"));
        assertThrows(InventarioClientException.class, () -> fake.reservar("elem", null, u2, "k"));
        assertThrows(InventarioClientException.class, () -> fake.reservar("elem", u1, null, "k"));

        assertThrows(InventarioClientException.class, () -> fake.liberarReserva(null, u2, "k"));
        assertThrows(InventarioClientException.class, () -> fake.liberarReserva("  ", u2, "k"));
        assertThrows(InventarioClientException.class, () -> fake.liberarReserva("elem", null, "k"));

        assertThrows(InventarioClientException.class, () -> fake.transferirProducto(null, u1, u2, "k"));
        assertThrows(InventarioClientException.class, () -> fake.transferirProducto("  ", u1, u2, "k"));
        assertThrows(InventarioClientException.class, () -> fake.transferirProducto("elem", null, u2, "k"));
        assertThrows(InventarioClientException.class, () -> fake.transferirProducto("elem", u1, null, "k"));
    }

    @Test
    void limpiarVaciaTodoElEstado() {
        fake.registrarElemento(new InventarioClient.ElementoInventario("elem-6", UUID.randomUUID(), UUID.randomUUID(), false));
        fake.reservar("elem-6", UUID.randomUUID(), UUID.randomUUID(), "k");
        fake.transferirProducto("elem-6", UUID.randomUUID(), UUID.randomUUID(), "k");

        fake.limpiar();

        assertEquals(Optional.empty(), fake.buscar("elem-6"));
        assertTrue(fake.getReservas().isEmpty());
        assertTrue(fake.getTransferencias().isEmpty());
    }
}
