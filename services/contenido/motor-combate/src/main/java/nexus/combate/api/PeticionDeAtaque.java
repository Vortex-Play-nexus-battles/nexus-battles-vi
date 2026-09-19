package nexus.combate.api;

import nexus.combate.ContextoAccion;
import nexus.combate.DistribucionEfectos;
import nexus.combate.RelacionObjetivo;

/**
 * Cuerpo de {@code POST /api/v1/combate/ataques} — espejo del contrato.
 *
 * <p>Traduce el JSON al vocabulario del dominio y no hace nada mas: aqui no vive
 * ninguna regla de combate. Si apareciera un calculo de dano en esta clase,
 * estaria en el sitio equivocado.
 */
public record PeticionDeAtaque(String heroeAtacante, Integer defensaObjetivo,
                               Distribucion distribucion, Contexto contexto, Long semilla) {

    /**
     * Reparto de efectos. Se puede pedir por prototipo o campo a campo; si
     * vienen los dos mandan los campos, que es lo que permite aplicar el efecto
     * del equipamiento sobre un prototipo base (RF-JUE-004).
     */
    public record Distribucion(String prototipo, Integer causarDano, Integer causarDanoCritico,
                               Integer evadirElGolpe, Integer resistirElGolpe,
                               Integer escaparAlGolpe, Integer sinEfecto) {

        /** True cuando trae los seis valores y por tanto no hace falta el prototipo. */
        boolean tieneTodosLosCampos() {
            return causarDano != null && causarDanoCritico != null && evadirElGolpe != null
                    && resistirElGolpe != null && escaparAlGolpe != null && sinEfecto != null;
        }

        DistribucionEfectos aDominio() {
            if (tieneTodosLosCampos()) {
                return new DistribucionEfectos(causarDano, causarDanoCritico, evadirElGolpe,
                        resistirElGolpe, escaparAlGolpe, sinEfecto);
            }
            if (prototipo == null || prototipo.isBlank()) {
                throw new PeticionInvalida(
                        "Indica un prototipo de distribucion o los seis porcentajes.");
            }
            return prototipoPorNombre(prototipo);
        }
    }

    /** Propiedades de la accion que pueden anularla antes de tirar nada (HU-JUE-008). */
    public record Contexto(Boolean combateCooperativo, String relacionObjetivo,
                           Boolean permiteAfectarAliados) {

        ContextoAccion aDominio() {
            if (combateCooperativo == null || relacionObjetivo == null
                    || permiteAfectarAliados == null) {
                throw new PeticionInvalida(
                        "El contexto de la accion necesita sus tres campos, o se omite entero.");
            }
            RelacionObjetivo relacion;
            try {
                relacion = RelacionObjetivo.valueOf(relacionObjetivo);
            } catch (IllegalArgumentException noEsUnValor) {
                throw new PeticionInvalida(
                        "relacionObjetivo solo admite MISMO_EQUIPO o EQUIPO_CONTRARIO.");
            }
            return new ContextoAccion(combateCooperativo, relacion, permiteAfectarAliados);
        }
    }

    /**
     * Los seis prototipos del dominio, por nombre.
     *
     * <p>No hay mapeo heroe → prototipo en ningun sitio del repositorio y no se
     * inventa uno: lo elige quien llama. Queda anotado como decision funcional
     * pendiente en el contrato.
     */
    static DistribucionEfectos prototipoPorNombre(String nombre) {
        return switch (nombre) {
            case "GUERRERO_TANQUE" -> DistribucionEfectos.GUERRERO_TANQUE;
            case "GUERRERO_ARMAS" -> DistribucionEfectos.GUERRERO_ARMAS;
            case "MAGO_FUEGO" -> DistribucionEfectos.MAGO_FUEGO;
            case "MAGO_HIELO" -> DistribucionEfectos.MAGO_HIELO;
            case "PICARO_VENENO" -> DistribucionEfectos.PICARO_VENENO;
            case "PICARO_MACHETE" -> DistribucionEfectos.PICARO_MACHETE;
            default -> throw new PeticionInvalida(
                    "Prototipo desconocido: " + nombre + ". Consulta /api/v1/combate/distribuciones.");
        };
    }

    /** Nombres de los seis prototipos, en el orden en que los declara el dominio. */
    static java.util.List<String> nombresDePrototipos() {
        return java.util.List.of("GUERRERO_TANQUE", "GUERRERO_ARMAS", "MAGO_FUEGO",
                "MAGO_HIELO", "PICARO_VENENO", "PICARO_MACHETE");
    }
}
