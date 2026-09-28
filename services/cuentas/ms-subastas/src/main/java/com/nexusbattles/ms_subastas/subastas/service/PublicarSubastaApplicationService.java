package com.nexusbattles.ms_subastas.subastas.service;

import com.nexusbattles.ms_subastas.notificaciones.AvisosDeSubasta;
import com.nexusbattles.ms_subastas.reglas.FuenteDeReglas;
import com.nexusbattles.ms_subastas.reglas.ReglasVigentes;
import com.nexusbattles.ms_subastas.subastas.dto.PublicarSubastaRequest;
import com.nexusbattles.ms_subastas.subastas.dto.PublicarSubastaResponse;
import com.nexusbattles.ms_subastas.subastas.model.EstadoSubasta;
import com.nexusbattles.ms_subastas.subastas.port.*;
import com.nexusbattles.ms_subastas.subastas.realtime.SubastaActualizadaEvent;
import com.nexusbattles.ms_subastas.subastas.repository.SubastaRepository;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.HexFormat;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import static com.nexusbattles.ms_subastas.subastas.service.PublicacionSubastaException.Motivo.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;


/**
 * Publicar una subasta (HU-SUB-001, 7.7.5 del documento del curso).
 *
 * <p><b>B8.</b> Aplica las reglas de 7.7 que faltaban: la compra inmediata
 * tiene que superar el precio minimo; el vendedor no puede tener mas de 10
 * subastas activas (con candado por vendedor, para que dos publicaciones
 * simultaneas no pasen las dos con 9); el incremento minimo sale de
 * admin-parametros y, sin valor, no se publica. Cada publicacion avisa al
 * vendedor y llega al listado en vivo.
 */
public class PublicarSubastaApplicationService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PublicarSubastaApplicationService.class);
    private final SubastaRepository subastas;
    private final InventarioClient inventario;
    private final CatalogoProductosClient catalogo;
    private final FinanzasPublicacionClient finanzas;
    private final IdentidadClient identidad;
    private final SancionesClient sanciones;
    private final IdempotenciaPublicacion idempotencia;
    private final CalculadorComisionPublicacion comisiones;
    private final Clock clock;
    private final FuenteDeReglas reglas;
    private final AvisosDeSubasta avisos;
    private final ApplicationEventPublisher eventos;

    public PublicarSubastaApplicationService(SubastaRepository subastas, InventarioClient inventario,
            CatalogoProductosClient catalogo, FinanzasPublicacionClient finanzas, IdentidadClient identidad,
            SancionesClient sanciones, IdempotenciaPublicacion idempotencia,
            CalculadorComisionPublicacion comisiones, Clock clock, FuenteDeReglas reglas,
            AvisosDeSubasta avisos, ApplicationEventPublisher eventos) {
        this.subastas = subastas; this.inventario = inventario; this.catalogo = catalogo; this.finanzas = finanzas;
        this.identidad = identidad; this.sanciones = sanciones; this.idempotencia = idempotencia;
        this.comisiones = comisiones; this.clock = clock;
        this.reglas = Objects.requireNonNull(reglas, "reglas");
        this.avisos = Objects.requireNonNull(avisos, "avisos");
        this.eventos = Objects.requireNonNull(eventos, "eventos");
    }

    @Transactional
    public PublicarSubastaResponse publicar(PublicarSubastaRequest solicitud, String idempotencyKey) {
        var quien = identidad.actual();
        if (quien == null || quien.usuarioId() == null) throw new PublicacionSubastaException(NO_AUTENTICADO, "Usuario no autenticado");
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) throw new PublicacionSubastaException(SOLICITUD_INVALIDA, "Idempotency-Key es obligatorio");
        solicitud.validarPrecios();
        String huella = huella(solicitud);
        String claveUsuario = quien.usuarioId() + ":" + idempotencyKey;
        // Primero la reproduccion: un reintento de una publicacion que YA se
        // hizo devuelve su resultado aunque las reglas hayan cambiado despues
        // (un administrador que vacia el incremento no puede convertir en 503
        // la respuesta perdida de algo que se publico y se cobro).
        var adquisicion = idempotencia.adquirir(claveUsuario, huella);
        if (adquisicion.resultado().isPresent()) return adquisicion.resultado().get().respuesta();
        var ejecucion = new Ejecucion(claveUsuario, adquisicion.titular(), idempotencyKey);
        boolean registrada = false;
        try {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(ejecucion);
                registrada = true;
            }
            // B8 — DECISION DEL PO pendiente (RF-SUB-002): sin incremento en
            // admin-parametros no se publica. Antes de cualquier efecto externo;
            // la clave recien adquirida se libera al revertir (no se hizo nada).
            ReglasVigentes vigentes = reglas.vigentes();
            BigDecimal incrementoMinimo = vigentes.incremento().orElseThrow(() -> new PublicacionSubastaException(
                    DEPENDENCIA_NO_DISPONIBLE, PublicacionSubastaException.INCREMENTO_MINIMO_NO_CONFIGURADO,
                    "El incremento minimo de puja no esta configurado: el parametro subastas.incremento-minimo de "
                            + "admin-parametros no tiene valor (decision pendiente del Product Owner). No se pueden "
                            + "publicar subastas hasta que un administrador lo fije."));
            ejecucion.respuesta = ejecutar(solicitud, idempotencyKey, quien, ejecucion, incrementoMinimo, vigentes);
            if (!registrada) ejecucion.afterCommit();
            return ejecucion.respuesta;
        } catch (RuntimeException | Error fallo) {
            if (!registrada) ejecucion.revertir();
            throw fallo;
        }
    }

    private PublicarSubastaResponse ejecutar(PublicarSubastaRequest solicitud, String idempotencyKey,
            IdentidadClient.Identidad quien, Ejecucion ejecucion, BigDecimal incrementoMinimo, ReglasVigentes vigentes) {
        if (sanciones.tieneSancionActiva(quien.usuarioId())) throw new PublicacionSubastaException(PROHIBIDO, "El usuario tiene una sanción activa");
        var elemento = inventario.buscar(solicitud.elementoInventarioId()).orElseThrow(() -> new PublicacionSubastaException(NO_ENCONTRADO, "Elemento de inventario inexistente"));
        if (!quien.usuarioId().equals(elemento.propietarioId())) throw new PublicacionSubastaException(PROHIBIDO, "El elemento no pertenece al usuario");
        if (!solicitud.productoId().equals(elemento.productoId())) throw new PublicacionSubastaException("El producto no coincide con el elemento");
        if (elemento.enUso()) throw new PublicacionSubastaException("El producto está en uso");
        var producto = catalogo.buscar(solicitud.productoId()).orElseThrow(() -> new PublicacionSubastaException(NO_ENCONTRADO, "Producto inexistente"));
        if (!producto.subastable()) throw new PublicacionSubastaException("El producto no es subastable");
        exigirCupoDePublicacion(quien, vigentes.maxSubastasActivasPorJugador());

        UUID subastaId = UUID.randomUUID();
        BigDecimal comision = comisiones.calcular(solicitud.duracion(), quien.esMaestroDeJuego());
        String concepto = switch (solicitud.duracion()) {
            case H24 -> "comision-publicacion-24h";
            case H48 -> "comision-publicacion-48h";
        };
        ejecucion.elemento = elemento.id();
        ejecucion.subasta = subastaId;
        try {
            inventario.reservar(elemento.id(), quien.usuarioId(), subastaId, idempotencyKey); ejecucion.reservado = true;
            if (comision.signum() > 0) { finanzas.debitarComision(quien.usuarioId(), comision, subastaId, concepto); ejecucion.debitado = true; }
            Instant publicada = Instant.now(clock);
            Subasta subasta = new Subasta();
            subasta.setId(subastaId); subasta.setProductoId(producto.id()); subasta.setElementoInventarioId(elemento.id());
            subasta.setVendedorId(quien.usuarioId()); subasta.setPrecioInicial(solicitud.precioInicial());
            subasta.setOfertaVigente(solicitud.precioInicial()); subasta.setIncrementoMinimo(incrementoMinimo);
            subasta.setPrecioCompraInmediata(solicitud.precioCompraInmediata()); subasta.setEstado(EstadoSubasta.ACTIVA);
            subasta.setFechaPublicacion(publicada); subasta.setFechaFin(publicada.plus(solicitud.duracion().duracion()));
            subasta.setNombreProducto(producto.nombre()); subasta.setTipoProducto(producto.tipo()); subasta.setRareza(producto.rareza());
            subasta.setMiniaturaUrl(producto.miniaturaUrl()); subasta.setDescripcionCorta(producto.descripcionCorta());
            subasta.setHabilidades(producto.habilidades()); subasta.setCantidadPujas(0); subasta.setEsMaestroDeJuego(quien.esMaestroDeJuego()); subasta.setVistas(0);
            // B8: lo cobrado queda guardado (la penalizacion de cancelar es el
            // 50 % de ESTO) y el apodo, para la ficha de 7.7.9.
            subasta.setComisionCobrada(comision);
            subasta.setApodoVendedor(quien.apodo());
            Subasta guardada = subastas.saveAndFlush(subasta);
            PublicarSubastaResponse respuesta = PublicarSubastaResponse.desde(guardada, comision);
            // 7.7.8 «Confirmacion de publicacion exitosa» y el listado en vivo,
            // en la misma transaccion: si la publicacion se deshace, no se avisa.
            avisos.publicada(guardada);
            eventos.publishEvent(new SubastaActualizadaEvent(this, guardada));
            return respuesta;
        } catch (FinanzasPublicacionClientException e) {
            ejecucion.finanzasInciertas = e.resultadoIncierto();
            throw e;
        } catch (DataIntegrityViolationException e) {
            if (esUnidadActivaDuplicada(e)) {
                throw new PublicacionSubastaException(CONFLICTO, "El elemento ya tiene una subasta activa", e);
            }
            throw e;
        }
    }

    /**
     * 7.7.10: «Maximo de 10 subastas activas simultaneas por jugador». El
     * Maestro de Juego esta exento: «publicar productos en cualquier momento
     * sin restricciones» (7.7.4).
     *
     * <p>El candado por vendedor serializa sus publicaciones hasta el final de
     * la transaccion; sin el, dos publicaciones simultaneas con 9 activas
     * contarian 9 las dos. Va antes de cualquier efecto externo: si no hay
     * cupo, no se bloquea nada en inventario ni se cobra nada.
     */
    private void exigirCupoDePublicacion(IdentidadClient.Identidad quien, int maximo) {
        if (quien.esMaestroDeJuego()) {
            return;
        }
        UUID vendedor = quien.usuarioId();
        subastas.bloquearPublicacionesDe(vendedor.getMostSignificantBits() ^ vendedor.getLeastSignificantBits());
        long activas = subastas.countByVendedorIdAndEstado(vendedor, EstadoSubasta.ACTIVA);
        if (activas >= maximo) {
            throw new PublicacionSubastaException(REGLA_NEGOCIO, PublicacionSubastaException.LIMITE_PUBLICACIONES_ACTIVAS,
                    "Ya tienes " + activas + " subastas activas; el maximo es " + maximo
                            + ". Espera a que termine alguna o cancela una sin pujas.");
        }
    }

    private static boolean esUnidadActivaDuplicada(Throwable fallo) {
        var visitados = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
        for (Throwable causa = fallo; causa != null && visitados.add(causa); causa = causa.getCause()) {
            if (causa instanceof org.hibernate.exception.ConstraintViolationException restriccion
                    && "uq_subastas_elemento_inventario_activa".equals(restriccion.getConstraintName())) return true;
        }
        return false;
    }

    private final class Ejecucion implements TransactionSynchronization {
        private final String clave;
        private final UUID titular;
        private final String claveExterna;
        private String elemento;
        private UUID subasta;
        private boolean reservado;
        private boolean debitado;
        private boolean finanzasInciertas;
        private PublicarSubastaResponse respuesta;

        private Ejecucion(String clave, UUID titular, String claveExterna) {
            this.clave = clave;
            this.titular = titular;
            this.claveExterna = claveExterna;
        }

        @Override
        public void afterCommit() {
            idempotencia.confirmar(clave, titular, respuesta);
        }

        @Override
        public void afterCompletion(int status) {
            if (status == STATUS_ROLLED_BACK) revertir();
            else if (status == STATUS_UNKNOWN) {
                idempotencia.marcarIncierta(clave, titular);
                log.error("Resultado de commit desconocido para subasta {}; requiere conciliacion", subasta);
            }
        }

        private void revertir() {
            try {
                if (!compensar(elemento, subasta, claveExterna, reservado, debitado)) finanzasInciertas = true;
            } finally {
                if (finanzasInciertas) {
                    idempotencia.marcarIncierta(clave, titular);
                    log.error("Resultado financiero incierto para subasta {}; requiere conciliacion", subasta);
                } else idempotencia.liberar(clave, titular);
            }
        }
    }

    private boolean compensar(String elemento, UUID subasta, String clave, boolean reservado, boolean debitado) {
        boolean finanzasResueltas = true;
        if (debitado) try { finanzas.compensarDebito(subasta, "publicacion-fallida"); }
        catch (RuntimeException fallo) {
            finanzasResueltas = false;
            log.error("No se pudo compensar comision de subasta {}; requiere conciliacion", subasta, fallo);
        }
        if (reservado) try { inventario.liberarReserva(elemento, subasta, clave); }
        catch (RuntimeException fallo) { log.error("No se pudo liberar reserva de subasta {}; requiere conciliacion", subasta, fallo); }
        return finanzasResueltas;
    }
    private static String huella(PublicarSubastaRequest s) {
        String texto = s.elementoInventarioId()+"|"+s.productoId()+"|"+s.duracion()+"|"+s.precioInicial()+"|"+s.precioCompraInmediata();
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
