package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Set;

/**
 * Hasta cuando tiene el panel para decidir una apelacion — §7.3.2 «Proceso de
 * apelacion»: «notificacion de la decision final en un plazo de 15 dias
 * habiles» (SA-4).
 *
 * <p>El numero no es un parametro: lo fija el documento oficial y el Project
 * Charter declara inalterables los plazos. Se cuenta desde el dia siguiente a
 * presentar la apelacion, en la hora de Colombia, y el plazo acaba al final
 * del decimoquinto dia habil.
 *
 * <p><b>Que es un dia habil aqui (decision tecnica).</b> De lunes a viernes,
 * sin los festivos que se configuren ({@code sanciones.apelacion.festivos}).
 * Sin festivos configurados el plazo sale, como mucho, unos dias mas corto
 * que el legal: el panel tiene menos tiempo, nunca mas, y el jugador recibe
 * su respuesta antes de lo prometido.
 */
public final class PlazoDeDecision {

    /** §7.3.2: quince dias habiles. */
    public static final int DIAS_HABILES = 15;

    /** La hora del jugador y del panel (UPB Bucaramanga). */
    public static final ZoneId ZONA = ZoneId.of("America/Bogota");

    private final Set<LocalDate> festivos;

    public PlazoDeDecision(Set<LocalDate> festivos) {
        this.festivos = Set.copyOf(Objects.requireNonNull(festivos));
    }

    /** Sin festivos: de lunes a viernes. */
    public static PlazoDeDecision deLunesAViernes() {
        return new PlazoDeDecision(Set.of());
    }

    /**
     * El ultimo instante para notificar la decision de una apelacion
     * presentada en {@code presentada}: el final (23:59:59.999, hora de
     * Colombia) del decimoquinto dia habil siguiente.
     */
    public OffsetDateTime limitePara(OffsetDateTime presentada) {
        LocalDate dia = Objects.requireNonNull(presentada).atZoneSameInstant(ZONA).toLocalDate();
        int contados = 0;
        while (contados < DIAS_HABILES) {
            dia = dia.plusDays(1);
            if (esHabil(dia)) {
                contados++;
            }
        }
        return dia.plusDays(1).atStartOfDay(ZONA).minusNanos(1_000_000).toOffsetDateTime()
                .withOffsetSameInstant(ZoneOffset.UTC);
    }

    /** Si la decision de una apelacion pendiente ya va fuera de plazo. */
    public boolean vencida(OffsetDateTime presentada, OffsetDateTime ahora) {
        return Objects.requireNonNull(ahora).isAfter(limitePara(presentada));
    }

    private boolean esHabil(LocalDate dia) {
        DayOfWeek semana = dia.getDayOfWeek();
        return semana != DayOfWeek.SATURDAY && semana != DayOfWeek.SUNDAY && !festivos.contains(dia);
    }
}
