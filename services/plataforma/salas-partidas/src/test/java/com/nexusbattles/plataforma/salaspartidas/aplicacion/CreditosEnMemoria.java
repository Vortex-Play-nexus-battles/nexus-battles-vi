package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.CreditosInsuficientes;
import com.nexusbattles.plataforma.salaspartidas.dominio.CreditosNoDisponibles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Doble del libro de creditos con las mismas reglas que ms-finanzas
 * ({@code contracts/openapi/creditos.yaml}): reservar aparta de verdad y de
 * forma atomica, liberar y consumir son idempotentes, y la clave de
 * idempotencia de reservar devuelve la misma reserva.
 *
 * <p>Lleva saldo por jugador, para que las pruebas afirmen <b>estado</b> —a
 * quien le queda cuanto— y no solo que «se llamo a algo». Puede dejarse caido
 * ({@link #caido}) para probar el fallo cerrado.
 */
public class CreditosEnMemoria implements CreditosDelJugador {

    /** Saldo por defecto de quien no se ha configurado: de sobra para no estorbar. */
    static final int SALDO_POR_DEFECTO = 10_000;

    enum Estado { ACTIVA, LIBERADA, CONSUMIDA }

    record Reserva(UUID id, UUID jugador, int creditos, Estado estado) {
        Reserva con(Estado nuevo) {
            return new Reserva(id, jugador, creditos, nuevo);
        }
    }

    final Map<UUID, Integer> saldos = new HashMap<>();
    final Map<UUID, Reserva> reservas = new LinkedHashMap<>();
    private final Map<String, UUID> porClave = new HashMap<>();

    /** Historial de llamadas, en orden. */
    final List<String> llamadas = new ArrayList<>();

    /** Reservas liberadas, en orden (las repeticiones se cuentan). */
    final List<UUID> liberadas = new ArrayList<>();

    /** Cuando esta caido, toda operacion falla como fallaria el libro real. */
    boolean caido;

    /** Si falla solo al liberar (para los caminos de compensacion). */
    boolean fallaAlLiberar;

    public CreditosEnMemoria conSaldo(UUID jugador, int saldo) {
        saldos.put(jugador, saldo);
        return this;
    }

    public int saldoDe(UUID jugador) {
        return saldos.getOrDefault(jugador, SALDO_POR_DEFECTO);
    }

    public int reservadoDe(UUID jugador) {
        return reservas.values().stream()
                .filter(r -> r.jugador().equals(jugador) && r.estado() == Estado.ACTIVA)
                .mapToInt(Reserva::creditos).sum();
    }

    public int disponibleDe(UUID jugador) {
        return saldoDe(jugador) - reservadoDe(jugador);
    }

    List<Reserva> activasDe(UUID jugador) {
        return reservas.values().stream()
                .filter(r -> r.jugador().equals(jugador) && r.estado() == Estado.ACTIVA).toList();
    }

    @Override
    public ReservaDeCreditos reservar(UUID idJugador, int creditos, UUID idSala, long ingreso) {
        llamadas.add("reservar " + idJugador + " " + creditos);
        if (caido) {
            throw new CreditosNoDisponibles("el libro esta caido");
        }
        String clave = idSala + "/" + idJugador + "/" + ingreso;
        UUID existente = porClave.get(clave);
        if (existente != null) {
            Reserva r = reservas.get(existente);
            return new ReservaDeCreditos(r.id(), r.creditos());
        }
        if (disponibleDe(idJugador) < creditos) {
            throw new CreditosInsuficientes(disponibleDe(idJugador), creditos);
        }
        Reserva reserva = new Reserva(UUID.randomUUID(), idJugador, creditos, Estado.ACTIVA);
        reservas.put(reserva.id(), reserva);
        porClave.put(clave, reserva.id());
        return new ReservaDeCreditos(reserva.id(), reserva.creditos());
    }

    @Override
    public void liberar(UUID idReserva) {
        llamadas.add("liberar " + idReserva);
        if (caido || fallaAlLiberar) {
            throw new CreditosNoDisponibles("el libro esta caido");
        }
        liberadas.add(idReserva);
        Reserva reserva = reservas.get(idReserva);
        if (reserva != null && reserva.estado() == Estado.ACTIVA) {
            reservas.put(idReserva, reserva.con(Estado.LIBERADA));
        }
    }

    @Override
    public void consumir(UUID idReserva, UUID idBeneficiario) {
        llamadas.add("consumir " + idReserva + " -> " + idBeneficiario);
        if (caido) {
            throw new CreditosNoDisponibles("el libro esta caido");
        }
        Reserva reserva = reservas.get(idReserva);
        if (reserva == null) {
            throw new CreditosNoDisponibles("reserva desconocida " + idReserva);
        }
        if (reserva.estado() == Estado.CONSUMIDA) {
            return; // idempotente: no se cobra dos veces
        }
        if (reserva.estado() == Estado.LIBERADA) {
            throw new CreditosNoDisponibles("la reserva ya fue liberada");
        }
        saldos.put(reserva.jugador(), saldoDe(reserva.jugador()) - reserva.creditos());
        if (idBeneficiario != null) {
            saldos.put(idBeneficiario, saldoDe(idBeneficiario) + reserva.creditos());
        }
        reservas.put(idReserva, reserva.con(Estado.CONSUMIDA));
    }
}
