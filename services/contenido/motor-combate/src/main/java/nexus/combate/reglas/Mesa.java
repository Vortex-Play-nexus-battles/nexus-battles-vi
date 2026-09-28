package nexus.combate.reglas;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * La mesa de juego durante UNA resolucion: el estado de todos, que va
 * cambiando, y lo que paso. Mutable y de vida corta: nace con la peticion y
 * muere con la respuesta. Los combatientes que guarda son inmutables; lo que
 * cambia es cual esta sobre la mesa.
 *
 * <p>Aqui vive lo que tiene que ocurrir IGUAL venga de donde venga el dano o la
 * sanacion: la vida no baja de cero ni pasa del maximo, un golpe queda anotado
 * para «Pare de fuego», y quien cae con un vinculo de «Reanimador 3000» se
 * levanta con el 20 % de su vida.
 */
final class Mesa {

    /** Tabla 20, Reanimador 3000: «se reanima con el 20% de su salud». */
    static final int PORCENTAJE_DE_REANIMACION_DEL_VINCULO = 20;

    private final Map<String, Contendiente> porId = new LinkedHashMap<>();
    private final Map<String, FichaDeCombate> fichas = new LinkedHashMap<>();
    private final Map<String, Integer> vidaInicial = new LinkedHashMap<>();
    private final List<Evento> eventos = new ArrayList<>();

    void sentar(Contendiente contendiente, FichaDeCombate ficha) {
        if (porId.putIfAbsent(contendiente.id(), contendiente) != null) {
            throw new IllegalArgumentException("El combatiente " + contendiente.id() + " esta repetido.");
        }
        fichas.put(contendiente.id(), ficha);
        vidaInicial.put(contendiente.id(), contendiente.vida());
    }

    Optional<Contendiente> buscar(String id) {
        return Optional.ofNullable(porId.get(id));
    }

    Contendiente de(String id) {
        Contendiente c = porId.get(id);
        if (c == null) {
            throw new IllegalArgumentException("El combatiente " + id + " no esta en la partida.");
        }
        return c;
    }

    FichaDeCombate ficha(String id) {
        return fichas.get(id);
    }

    void poner(Contendiente contendiente) {
        porId.put(contendiente.id(), contendiente);
    }

    List<Contendiente> todos() {
        return List.copyOf(porId.values());
    }

    void evento(TipoDeEvento tipo, String combatiente, String origen, String efecto, Integer cantidad) {
        eventos.add(new Evento(tipo, combatiente, origen, efecto, cantidad));
    }

    List<Evento> eventos() {
        return List.copyOf(eventos);
    }

    /** Quienes acabaron con otra vida que la que traian. */
    List<Afectado> afectados() {
        List<Afectado> lista = new ArrayList<>();
        porId.forEach((id, c) -> {
            int antes = vidaInicial.get(id);
            if (antes != c.vida()) {
                lista.add(new Afectado(id, antes, c.vida()));
            }
        });
        return lista;
    }

    /**
     * Quita vida. Un golpe directo ({@link TipoDeEvento#DANO}) queda anotado
     * como el ultimo recibido; un sangrado o un reflejo no. Si la vida llega a
     * cero y el combatiente lleva un vinculo de reanimacion, se levanta.
     *
     * @return la vida que de verdad perdio
     */
    int danar(String id, int cantidad, String origen, String efecto, TipoDeEvento tipo) {
        Contendiente c = de(id);
        if (cantidad <= 0 || !c.enPie()) {
            return 0;
        }
        int nueva = Math.max(0, c.vida() - cantidad);
        int perdida = c.vida() - nueva;
        c = c.conVida(nueva);
        if (tipo == TipoDeEvento.DANO && origen != null) {
            c = c.conUltimoDanoRecibido(new DanoRecibido(origen, perdida));
        }
        evento(tipo, id, origen, efecto, perdida);
        poner(c);
        if (nueva == 0) {
            alCaer(id);
        }
        return perdida;
    }

    /** Suma vida sin pasar del maximo; a quien ya cayo no le hace nada. */
    int sanar(String id, int cantidad, String origen, String efecto, TipoDeEvento tipo) {
        Contendiente c = de(id);
        if (cantidad <= 0 || !c.enPie()) {
            return 0;
        }
        int nueva = Math.min(c.vidaMaxima(), c.vida() + cantidad);
        int ganada = nueva - c.vida();
        poner(c.conVida(nueva));
        evento(tipo, id, origen, efecto, ganada);
        return ganada;
    }

    /** Devuelve toda la vida, tambien a quien cayo (Reanimacion). */
    void restaurarPorCompleto(String id, String origen, String efecto) {
        Contendiente c = de(id);
        boolean estabaCaido = !c.enPie();
        int ganada = c.vidaMaxima() - c.vida();
        poner(c.conVida(c.vidaMaxima()));
        evento(estabaCaido ? TipoDeEvento.REANIMACION : TipoDeEvento.SANACION, id, origen, efecto, ganada);
    }

    void quitarPoder(String id, int puntos, String origen, String efecto) {
        Contendiente c = de(id);
        int nuevo = Math.max(0, c.poder() - puntos);
        if (nuevo != c.poder()) {
            poner(c.conPoder(nuevo));
            evento(TipoDeEvento.PODER_PERDIDO, id, origen, efecto, c.poder() - nuevo);
        }
    }

    /** Pone un efecto; a quien ya cayo no se le ponen efectos. */
    void aplicarEfecto(String id, EfectoActivo efecto) {
        Contendiente c = de(id);
        if (!c.enPie()) {
            return;
        }
        poner(c.conEfecto(efecto));
        evento(TipoDeEvento.EFECTO_APLICADO, id, efecto.origen(), efecto.nombre(), efecto.valor());
    }

    private void alCaer(String id) {
        Contendiente c = de(id);
        Optional<EfectoActivo> vinculo = c.efectos().stream()
                .filter(e -> e.tipo() == TipoDeEfecto.VINCULO_REANIMACION)
                .findFirst();
        if (vinculo.isEmpty()) {
            evento(TipoDeEvento.CAIDO, id, null, null, null);
            return;
        }
        int vida = Math.max(1, c.vidaMaxima() * PORCENTAJE_DE_REANIMACION_DEL_VINCULO / 100);
        List<EfectoActivo> sinVinculo = c.efectos().stream().filter(e -> e != vinculo.get()).toList();
        poner(c.conEfectos(sinVinculo).conVida(vida));
        evento(TipoDeEvento.REANIMACION, id, vinculo.get().origen(), vinculo.get().nombre(), vida);
    }
}
