package nexus.api;

import java.time.Instant;

import nexus.dominio.Banner;

public record RespuestaBanner(
        String id,
        String contenido,
        Instant publicarDesde,
        Instant vigenteHasta,
        boolean retirado,
        Instant creadoEn,
        Instant modificadoEn) {

        public static RespuestaBanner desde(Banner banner) {
                return new RespuestaBanner(
                        banner.id(),
                        banner.contenido(),
                        banner.publicarDesde(),
                        banner.vigenteHasta(),
                        banner.retirado(),
                        banner.creadoEn(),
                        banner.modificadoEn());
        }
}
