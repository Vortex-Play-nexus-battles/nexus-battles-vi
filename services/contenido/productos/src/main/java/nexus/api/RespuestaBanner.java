package nexus.api;

import java.time.Instant;

import nexus.dominio.Banner;

/**
 * Un banner como lo publica el contrato (esquema {@code Banner}). Desde SA-18
 * (1.12.0) lleva {@code creadoPor} y {@code modificadoPor}, el {@code uid} de
 * quien lo publicó y de quien lo tocó por última vez; nulos en los anteriores.
 */
public record RespuestaBanner(
        String id,
        String contenido,
        Instant publicarDesde,
        Instant vigenteHasta,
        boolean retirado,
        Instant creadoEn,
        Instant modificadoEn,
        String creadoPor,
        String modificadoPor) {

        public static RespuestaBanner desde(Banner banner) {
                return new RespuestaBanner(
                        banner.id(),
                        banner.contenido(),
                        banner.publicarDesde(),
                        banner.vigenteHasta(),
                        banner.retirado(),
                        banner.creadoEn(),
                        banner.modificadoEn(),
                        banner.creadoPor(),
                        banner.modificadoPor());
        }
}
