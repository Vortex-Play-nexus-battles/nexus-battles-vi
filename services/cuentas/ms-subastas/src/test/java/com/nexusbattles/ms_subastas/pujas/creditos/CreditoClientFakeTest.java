package com.nexusbattles.ms_subastas.pujas.creditos;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * El doble de creditos existe para poder trabajar sin ms-finanzas, pero tiene
 * dos modos y la diferencia importa: en pruebas es estricto, y levantado en
 * local tiene que sobrevivir a que lo reinicien.
 */
class CreditoClientFakeTest {

    @Test
    void unJugadorDesconocidoEmpiezaSinSaldoPorDefecto() {
        CreditoClientFake fake = new CreditoClientFake();

        assertEquals(0, BigDecimal.ZERO.compareTo(fake.saldoDisponible(UUID.randomUUID())));
    }

    /**
     * Sin ms-finanzas no hay ningun sitio desde donde acreditar a nadie, asi
     * que levantar el servicio con saldo cero significa que ninguna puja pasa
     * y la historia no se puede ni ensenar.
     */
    @Test
    void conSaldoInicialCualquierJugadorPuedePujarEnLocal() {
        CreditoClientFake fake = new CreditoClientFake(new BigDecimal("5000"));

        assertEquals(0, new BigDecimal("5000").compareTo(fake.saldoDisponible(UUID.randomUUID())));
    }

    @Test
    void reservarDescuentaDelDisponibleSinDebitarlo() {
        CreditoClientFake fake = new CreditoClientFake(new BigDecimal("1000"));
        UUID jugador = UUID.randomUUID();

        fake.reservar(jugador, new BigDecimal("300"), UUID.randomUUID(), "clave-1");

        assertEquals(0, new BigDecimal("700").compareTo(fake.saldoDisponible(jugador)));
    }

    @Test
    void laMismaClaveDeIdempotenciaNoReservaDosVeces() {
        CreditoClientFake fake = new CreditoClientFake(new BigDecimal("1000"));
        UUID jugador = UUID.randomUUID();
        UUID subasta = UUID.randomUUID();

        ReservaCredito primera = fake.reservar(jugador, new BigDecimal("300"), subasta, "misma-clave");
        ReservaCredito segunda = fake.reservar(jugador, new BigDecimal("300"), subasta, "misma-clave");

        assertEquals(primera.id(), segunda.id());
        assertEquals(0, new BigDecimal("700").compareTo(fake.saldoDisponible(jugador)));
    }

    /**
     * En pruebas el doble nace y muere con cada caso: no hay estado heredado,
     * asi que una reserva desconocida es un error de verdad y taparlo
     * esconderia fallos reales del motor.
     */
    @Test
    void enModoEstrictoUnaReservaDesconocidaEsUnError() {
        CreditoClientFake fake = new CreditoClientFake(new BigDecimal("1000"));

        CreditoClientException error = assertThrows(CreditoClientException.class,
                () -> fake.liberar(UUID.randomUUID()));

        assertEquals(CreditoClientException.Motivo.RESERVA_INEXISTENTE, error.getMotivo());
    }

    /**
     * El caso que rompia la aplicacion al reiniciarla: las pujas viven en
     * PostgreSQL y las reservas solo en memoria, asi que tras un reinicio hay
     * pujas apuntando a reservas que ya no existen. La siguiente puja sobre esa
     * subasta intenta liberar la del postor anterior y devolvia un 500.
     */
    @Test
    void tolerandoReservasDeOtraEjecucionLiberarUnaDesconocidaNoRompe() {
        CreditoClientFake fake = new CreditoClientFake(new BigDecimal("1000"), true);

        assertDoesNotThrow(() -> fake.liberar(UUID.randomUUID()));
    }

    /**
     * Tolerar lo heredado no puede convertirse en tolerar cualquier cosa: una
     * reserva que este doble si conoce sigue con sus reglas.
     */
    @Test
    void tolerarLoHeredadoNoRelajaLasReservasQueSiConoce() {
        CreditoClientFake fake = new CreditoClientFake(new BigDecimal("1000"), true);
        ReservaCredito reserva = fake.reservar(UUID.randomUUID(), new BigDecimal("300"),
                UUID.randomUUID(), "clave-1");
        fake.liberar(reserva.id());

        CreditoClientException error = assertThrows(CreditoClientException.class,
                () -> fake.liberar(reserva.id()));

        assertEquals(CreditoClientException.Motivo.RESERVA_YA_LIBERADA, error.getMotivo());
    }

    @Test
    void consumirConvierteLaReservaEnDebitoReal() {
        CreditoClientFake fake = new CreditoClientFake(new BigDecimal("1000"));
        UUID jugador = UUID.randomUUID();
        ReservaCredito reserva = fake.reservar(jugador, new BigDecimal("300"), UUID.randomUUID(), "clave-1");

        UUID vendedor = UUID.randomUUID();
        fake.consumir(reserva.id(), vendedor);

        assertEquals(0, new BigDecimal("700").compareTo(fake.saldoDisponible(jugador)));
    }

    /**
     * Consumir no es solo cobrarle al comprador: el vendedor tiene que recibir
     * ese dinero. El doble lo replica porque, si no, las pruebas darian por
     * bueno un flujo donde el comprador paga y nadie cobra.
     */
    @Test
    void consumirAbonaAlVendedorLoQueSeLeCobraAlComprador() {
        CreditoClientFake fake = new CreditoClientFake(new BigDecimal("1000"));
        UUID comprador = UUID.randomUUID();
        UUID vendedor = UUID.randomUUID();
        ReservaCredito reserva = fake.reservar(comprador, new BigDecimal("300"), UUID.randomUUID(), "clave-2");

        fake.consumir(reserva.id(), vendedor);

        assertEquals(0, new BigDecimal("700").compareTo(fake.saldoDisponible(comprador)));
        assertEquals(0, new BigDecimal("1300").compareTo(fake.saldoDisponible(vendedor)),
                "el vendedor cobra los 300 ademas de los 1000 con los que aparece");
    }
}
