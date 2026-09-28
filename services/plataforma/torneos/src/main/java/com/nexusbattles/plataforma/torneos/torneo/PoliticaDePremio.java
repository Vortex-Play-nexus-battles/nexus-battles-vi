package com.nexusbattles.plataforma.torneos.torneo;

/**
 * El premio del equipo campeon (RF-TOR-007): «el grupo ganador recibira
 * creditos y una recompensa epica unica para su heroe» (7.9 del documento).
 *
 * <p>El documento no dice cuantos creditos ni que epica. Mientras el PO no lo
 * fije (D-24), los dos son configuracion del servicio con un valor
 * PROVISIONAL documentado en {@code application.yml} y en
 * docs/gobierno/DECISIONES-PENDIENTES-DEL-PO.md. Cada torneo copia el premio
 * vigente al crearse: cambiar la configuracion no altera lo que se anuncio a
 * quien ya se inscribio.
 */
public class PoliticaDePremio {

    /**
     * @param creditosPorIntegrante creditos que recibe cada integrante humano del equipo campeon (0 = sin creditos)
     * @param epicaProductoId       producto EPICA del catalogo que recibe cada uno (nulo = sin epica)
     */
    public record Premio(int creditosPorIntegrante, String epicaProductoId) {

        public Premio {
            if (creditosPorIntegrante < 0) {
                throw new IllegalArgumentException("el premio en creditos no puede ser negativo");
            }
            epicaProductoId = epicaProductoId == null || epicaProductoId.isBlank() ? null : epicaProductoId.strip();
        }

        /** Nada que entregar: ni creditos ni epica. */
        public boolean vacio() {
            return creditosPorIntegrante == 0 && epicaProductoId == null;
        }
    }

    private final Premio premio;

    public PoliticaDePremio(int creditosPorIntegrante, String epicaProductoId) {
        this.premio = new Premio(creditosPorIntegrante, epicaProductoId);
    }

    public Premio premio() {
        return premio;
    }
}
