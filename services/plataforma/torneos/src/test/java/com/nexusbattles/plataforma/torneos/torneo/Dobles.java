package com.nexusbattles.plataforma.torneos.torneo;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dobles CON ESTADO de los proveedores, con la misma semantica que declaran
 * sus contratos (creditos.yaml, inventario.yaml 1.4.0, notificaciones.yaml):
 * reservar es idempotente por clave, consumir y liberar por reserva, acreditar
 * por refId, entregar por Idempotency-Key, avisar por id. Asi una prueba puede
 * afirmar lo que de verdad importa —que el saldo del jugador bajo UNA vez—, no
 * solo cuantas veces se llamo a un metodo.
 */
final class Dobles {

    private Dobles() {
    }

    /** Libro de creditos en memoria (creditos.yaml 1.4.0). */
    static final class LibroEnMemoria implements LibroDeCreditos {

        record ReservaFalsa(UUID id, UUID jugador, int monto, String estado) { }

        final Map<UUID, ReservaFalsa> reservas = new HashMap<>();
        final Map<String, UUID> porClave = new HashMap<>();
        final Map<UUID, Integer> bruto = new HashMap<>();
        final Map<UUID, Integer> reservado = new HashMap<>();
        final Set<String> acreditados = new HashSet<>();
        /** Cuantos consumos que movieron saldo hubo (no las repeticiones idempotentes). */
        final AtomicInteger consumosEfectivos = new AtomicInteger();
        /** Las siguientes N llamadas a consumir fallan como si el libro no respondiera. */
        final AtomicInteger consumosQueFallan = new AtomicInteger();
        volatile boolean caido;

        synchronized void reiniciar() {
            reservas.clear();
            porClave.clear();
            bruto.clear();
            reservado.clear();
            acreditados.clear();
            consumosEfectivos.set(0);
            consumosQueFallan.set(0);
            caido = false;
        }

        synchronized void darSaldo(UUID jugador, int creditos) {
            bruto.merge(jugador, creditos, Integer::sum);
        }

        synchronized int bruto(UUID jugador) {
            return bruto.getOrDefault(jugador, 0);
        }

        synchronized int reservado(UUID jugador) {
            return reservado.getOrDefault(jugador, 0);
        }

        synchronized String estado(UUID reserva) {
            return reservas.get(reserva).estado();
        }

        /** Lo que haria el libro si alguien cobrara la reserva por su cuenta (una caida a mitad). */
        synchronized void consumirPorFuera(UUID reserva) {
            aplicarConsumo(reserva);
        }

        @Override
        public synchronized Reserva reservar(UUID jugador, int creditos, String clave, String referencia) {
            if (caido) {
                throw new TorneoRechazado(TorneoRechazado.Motivo.LIBRO_NO_DISPONIBLE, "libro caido");
            }
            UUID existente = porClave.get(clave);
            if (existente != null) {
                ReservaFalsa r = reservas.get(existente);
                return new Reserva(r.id(), r.estado());
            }
            int disponible = bruto(jugador) - reservado(jugador);
            if (disponible < creditos) {
                throw new TorneoRechazado(TorneoRechazado.Motivo.CREDITOS_INSUFICIENTES, "no alcanza");
            }
            UUID id = UUID.randomUUID();
            reservas.put(id, new ReservaFalsa(id, jugador, creditos, "ACTIVA"));
            porClave.put(clave, id);
            reservado.merge(jugador, creditos, Integer::sum);
            return new Reserva(id, "ACTIVA");
        }

        @Override
        public synchronized String liberar(UUID reservaId) {
            if (caido) {
                throw FalloDeIntegracion.pasajero("libro caido");
            }
            ReservaFalsa r = reservas.get(reservaId);
            if (r == null) {
                throw FalloDeIntegracion.definitivo("reserva-no-encontrada");
            }
            if (!"ACTIVA".equals(r.estado())) {
                return r.estado();
            }
            reservado.merge(r.jugador(), -r.monto(), Integer::sum);
            reservas.put(reservaId, new ReservaFalsa(r.id(), r.jugador(), r.monto(), "LIBERADA"));
            return "LIBERADA";
        }

        @Override
        public synchronized void consumir(UUID reservaId) {
            if (caido) {
                throw FalloDeIntegracion.pasajero("libro caido");
            }
            if (consumosQueFallan.get() > 0) {
                consumosQueFallan.decrementAndGet();
                throw FalloDeIntegracion.pasajero("el libro no respondio a tiempo");
            }
            aplicarConsumo(reservaId);
        }

        private void aplicarConsumo(UUID reservaId) {
            ReservaFalsa r = reservas.get(reservaId);
            if (r == null) {
                throw FalloDeIntegracion.definitivo("reserva-no-encontrada");
            }
            if ("CONSUMIDA".equals(r.estado())) {
                return; // 200 TX-EXISTENTE: no vuelve a cobrar
            }
            if ("LIBERADA".equals(r.estado())) {
                throw FalloDeIntegracion.definitivo("reserva-ya-liberada");
            }
            reservado.merge(r.jugador(), -r.monto(), Integer::sum);
            bruto.merge(r.jugador(), -r.monto(), Integer::sum);
            reservas.put(reservaId, new ReservaFalsa(r.id(), r.jugador(), r.monto(), "CONSUMIDA"));
            consumosEfectivos.incrementAndGet();
        }

        @Override
        public synchronized void acreditar(UUID jugador, int creditos, String refId, String concepto) {
            if (caido) {
                throw FalloDeIntegracion.pasajero("libro caido");
            }
            if (acreditados.add(refId)) {
                bruto.merge(jugador, creditos, Integer::sum);
            }
        }
    }

    /** Inventario en memoria: entregas idempotentes por Idempotency-Key. */
    static final class InventarioEnMemoria implements EntregaDeInventario {

        record EntregaFalsa(UUID jugador, String productoId, String referencia, String clave) { }

        final Map<String, EntregaFalsa> porClave = new ConcurrentHashMap<>();
        final AtomicInteger entregasQueFallan = new AtomicInteger();
        volatile boolean productoInexistente;

        void reiniciar() {
            porClave.clear();
            entregasQueFallan.set(0);
            productoInexistente = false;
        }

        @Override
        public void entregarEpica(UUID jugador, String productoId, String referencia, String clave) {
            if (productoInexistente) {
                throw FalloDeIntegracion.definitivo("422 producto inexistente");
            }
            if (entregasQueFallan.get() > 0) {
                entregasQueFallan.decrementAndGet();
                throw FalloDeIntegracion.pasajero("inventario no responde");
            }
            porClave.putIfAbsent(clave, new EntregaFalsa(jugador, productoId, referencia, clave));
        }

        long delJugador(UUID jugador) {
            return porClave.values().stream().filter(e -> e.jugador().equals(jugador)).count();
        }
    }

    /** Bandeja de notificaciones en memoria: idempotente por id. */
    static final class AvisosEnMemoria implements AvisosAlJugador {

        record AvisoFalso(UUID destinatario, String id, String titulo, String cuerpo) { }

        final Map<String, AvisoFalso> bandeja = new ConcurrentHashMap<>();
        final List<String> correos = Collections.synchronizedList(new ArrayList<>());
        volatile boolean conCorreo;

        void reiniciar() {
            bandeja.clear();
            correos.clear();
            conCorreo = false;
        }

        @Override
        public void notificar(UUID destinatario, String id, String titulo, String cuerpo, OffsetDateTime creadaEn) {
            bandeja.putIfAbsent(id, new AvisoFalso(destinatario, id, titulo, cuerpo));
        }

        @Override
        public boolean correoConfigurado() {
            return conCorreo;
        }

        @Override
        public Correo enviarCorreo(UUID destinatario, UUID torneoId, String asunto, String mensaje, String clave) {
            correos.add(clave);
            return Correo.ENVIADO;
        }

        List<AvisoFalso> de(UUID destinatario) {
            return bandeja.values().stream().filter(a -> a.destinatario().equals(destinatario)).toList();
        }
    }

    /** Sanciones: nadie sancionado salvo los que la prueba marque. */
    static final class SancionesEnMemoria implements ConsultaDeSanciones {

        final Set<UUID> sancionados = ConcurrentHashMap.newKeySet();
        volatile boolean caido;

        void reiniciar() {
            sancionados.clear();
            caido = false;
        }

        @Override
        public boolean sancionado(UUID jugador) {
            if (caido) {
                throw new TorneoRechazado(TorneoRechazado.Motivo.SANCIONES_NO_DISPONIBLES, "sanciones caido");
            }
            return sancionados.contains(jugador);
        }
    }

    @TestConfiguration
    static class Configuracion {

        @Bean
        @Primary
        LibroEnMemoria libroEnMemoria() {
            return new LibroEnMemoria();
        }

        @Bean
        @Primary
        InventarioEnMemoria inventarioEnMemoria() {
            return new InventarioEnMemoria();
        }

        @Bean
        @Primary
        AvisosEnMemoria avisosEnMemoria() {
            return new AvisosEnMemoria();
        }

        @Bean
        @Primary
        SancionesEnMemoria sancionesEnMemoria() {
            return new SancionesEnMemoria();
        }

        @Bean
        @Primary
        FiltroDeNombres filtroQueApruebaTodo() {
            return texto -> true;
        }
    }
}
