package nexus.combate.api;

import nexus.combate.ClienteHeroes;
import nexus.combate.ContextoAccion;
import nexus.combate.DetalleAtaque;
import nexus.combate.DistribucionEfectos;
import nexus.combate.EstadisticasHeroeRespuesta;
import nexus.combate.ResolucionAtaque;
import nexus.combate.ResolutorCombate;
import nexus.combate.TiradorDados;

import java.util.Objects;
import java.util.Random;
import java.util.random.RandomGenerator;

/**
 * Resuelve un ataque de principio a fin — RF-JUE-003, RF-JUE-006.
 *
 * <p>Compone tres piezas del dominio que hasta ahora nadie encadenaba desde
 * fuera: pedir las estadisticas del heroe, tirar sus dados, y resolver el
 * ataque contra la defensa del objetivo.
 *
 * <p><b>No decide ninguna regla.</b> El porcentaje de cada categoria, la forma
 * de la tabla de 8.000 filas y el reparto del critico ya viven en
 * {@link ResolutorCombate}; esta clase solo los llama en orden. Si apareciera
 * aqui un numero de juego, estaria en el sitio equivocado.
 *
 * <p>Es Java puro, sin anotaciones de Spring, igual que el resto de
 * {@code nexus.combate}: el arranque la instancia a mano. Asi se prueba sin
 * levantar contexto y la prueba que vigila que el dominio no se llene de beans
 * sigue valiendo.
 */
public class ResolverAtaque {

    private final ClienteHeroes heroes;

    public ResolverAtaque(ClienteHeroes heroes) {
        this.heroes = Objects.requireNonNull(heroes, "Sin catalogo de heroes no hay ataque.");
    }

    /**
     * @param peticion ataque a resolver, ya validado en su forma
     * @return el resultado, con el dano a aplicar
     * @throws PeticionInvalida  si faltan datos o estan fuera de rango
     * @throws HeroeSinAtaque    si el heroe no tiene formula de ataque
     */
    public RespuestaDeAtaque ejecutar(PeticionDeAtaque peticion) {
        exigirDatosMinimos(peticion);

        DistribucionEfectos distribucion = peticion.distribucion().aDominio();
        int defensa = peticion.defensaObjetivo();

        // El catalogo manda: ni la defensa del atacante ni su formula se
        // inventan aqui. Si no responde, la excepcion sube y sale un 503.
        EstadisticasHeroeRespuesta estadisticas =
                heroes.obtenerEstadisticas(peticion.heroeAtacante());

        DetalleAtaque formula = estadisticas.ataqueDetalle();
        if (formula == null) {
            throw new HeroeSinAtaque(peticion.heroeAtacante());
        }

        // Tres generadores, no uno: el dominio los pide separados para que el
        // sorteo de la fila y el del critico no se contaminen entre si.
        RandomGenerator paraDados = generador(peticion.semilla());
        RandomGenerator paraIndice = generador(peticion.semilla());
        RandomGenerator paraCritico = generador(peticion.semilla());

        int ataqueResuelto = TiradorDados.resolver(formula, paraDados);

        ResolucionAtaque resolucion = resolver(peticion, distribucion, defensa,
                ataqueResuelto, paraIndice, paraCritico);

        return RespuestaDeAtaque.de(resolucion, ataqueResuelto, defensa);
    }

    private static ResolucionAtaque resolver(PeticionDeAtaque peticion,
                                             DistribucionEfectos distribucion,
                                             int defensa, int ataqueResuelto,
                                             RandomGenerator paraIndice,
                                             RandomGenerator paraCritico) {
        // El dano base de la categoria es la propia tirada: es lo que el
        // dominio usa como `danoResuelto` en sus pruebas de integracion.
        if (peticion.contexto() == null) {
            return ResolutorCombate.resolverCompleto(ataqueResuelto, defensa, distribucion,
                    ataqueResuelto, paraIndice, paraCritico);
        }
        ContextoAccion contexto = peticion.contexto().aDominio();
        return ResolutorCombate.resolverCompleto(contexto, ataqueResuelto, defensa, distribucion,
                ataqueResuelto, paraIndice, paraCritico);
    }

    /**
     * {@code SecureRandom} salvo que la peticion traiga semilla.
     *
     * <p>La semilla existe para poder reproducir un combate en una prueba. En
     * partida real no se usa: un generador predecible convierte el critico en
     * un tramite.
     */
    private static RandomGenerator generador(Long semilla) {
        return semilla == null ? TiradorDados.generadorProduccion() : new Random(semilla);
    }

    private static void exigirDatosMinimos(PeticionDeAtaque peticion) {
        if (peticion == null) {
            throw new PeticionInvalida("Hace falta un cuerpo de peticion.");
        }
        if (peticion.heroeAtacante() == null || peticion.heroeAtacante().isBlank()) {
            throw new PeticionInvalida("Hace falta el nombre del heroe atacante.");
        }
        if (peticion.defensaObjetivo() == null) {
            throw new PeticionInvalida("Hace falta la defensa del objetivo.");
        }
        if (peticion.defensaObjetivo() < 0) {
            throw new PeticionInvalida("La defensa del objetivo no puede ser negativa.");
        }
        if (peticion.distribucion() == null) {
            throw new PeticionInvalida(
                    "Hace falta la distribucion de efectos: un prototipo o los seis porcentajes.");
        }
    }
}
