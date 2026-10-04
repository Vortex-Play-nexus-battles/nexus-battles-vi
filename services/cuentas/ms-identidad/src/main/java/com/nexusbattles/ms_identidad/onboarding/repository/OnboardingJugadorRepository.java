package com.nexusbattles.ms_identidad.onboarding.repository;

import com.nexusbattles.ms_identidad.onboarding.model.EstadoOnboarding;
import com.nexusbattles.ms_identidad.onboarding.model.OnboardingJugador;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OnboardingJugadorRepository extends JpaRepository<OnboardingJugador, UUID> {

    /**
     * Toma el turno del alta de un jugador, de forma atomica.
     *
     * <p>Es un UPDATE condicional y no un SELECT seguido de UPDATE: dos
     * trabajadores que lleguen a la vez (el registro y el reintento programado,
     * o dos pestanas pulsando «Reintentar») no pueden ganar los dos. Solo uno ve
     * {@code 1}; el otro ve {@code 0} y se retira sin tocar nada. Un turno cuyo
     * {@code en_proceso_hasta} ya paso (el proceso murio a mitad) se puede volver
     * a tomar.
     *
     * @return 1 si este trabajador tiene el turno, 0 si no
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OnboardingJugador o SET o.estado = :enProceso, o.enProcesoHasta = :hasta,"
            + " o.intentos = o.intentos + 1, o.actualizadoEn = :ahora"
            + " WHERE o.usuarioUid = :uid"
            + " AND (o.estado IN :reclamables"
            + "      OR (o.estado = :enProceso AND o.enProcesoHasta < :ahora))")
    int reclamar(@Param("uid") UUID uid,
                 @Param("enProceso") EstadoOnboarding enProceso,
                 @Param("reclamables") Collection<EstadoOnboarding> reclamables,
                 @Param("ahora") LocalDateTime ahora,
                 @Param("hasta") LocalDateTime hasta);

    /**
     * Altas que toca reintentar ahora: las aplazadas cuyo siguiente intento ya
     * llego, las pendientes que nadie tomo (se perdio el aviso tras el registro,
     * por ejemplo por un reinicio) y las que se quedaron en proceso con el turno
     * caducado.
     */
    @Query("SELECT o.usuarioUid FROM OnboardingJugador o"
            + " WHERE (o.estado = :aplazado AND o.siguienteIntento <= :ahora)"
            + "    OR (o.estado = :pendiente AND o.creadoEn <= :pendienteDesde)"
            + "    OR (o.estado = :enProceso AND o.enProcesoHasta < :ahora)"
            + " ORDER BY o.actualizadoEn ASC")
    List<UUID> porReintentar(@Param("aplazado") EstadoOnboarding aplazado,
                             @Param("pendiente") EstadoOnboarding pendiente,
                             @Param("enProceso") EstadoOnboarding enProceso,
                             @Param("ahora") LocalDateTime ahora,
                             @Param("pendienteDesde") LocalDateTime pendienteDesde,
                             Pageable limite);
}
