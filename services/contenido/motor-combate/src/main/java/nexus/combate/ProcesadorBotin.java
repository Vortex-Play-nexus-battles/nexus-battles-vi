package nexus.combate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ProcesadorBotin {

    private final InventarioBotin inventario;
    private final CatalogoBotin catalogo;
    private final EvaluadorCaida evaluador;
    private final Set<String> accionesProcesadas = ConcurrentHashMap.newKeySet();

    public ProcesadorBotin(
            InventarioBotin inventario,
            CatalogoBotin catalogo,
            EvaluadorCaida evaluador) {
        this.inventario = Objects.requireNonNull(inventario, "El inventario es obligatorio");
        this.catalogo = Objects.requireNonNull(catalogo, "El catalogo es obligatorio");
        this.evaluador = Objects.requireNonNull(evaluador, "El evaluador es obligatorio");
    }

    public synchronized ResultadoBotin procesar(DerrotaEnemigo derrota) {
        Objects.requireNonNull(derrota, "La derrota es obligatoria");
        if (accionesProcesadas.contains(derrota.accionId())) {
            return ResultadoBotin.yaProcesado();
        }

        List<ElementoCandidatoBotin> candidatos = inventario.listarCandidatos(
                derrota.propietarioEnemigoId(),
                derrota.heroeEnemigoId());
        List<BotinOtorgado> obtenidos = new ArrayList<>();

        for (ElementoCandidatoBotin candidato : candidatos) {
            ProductoBotin producto = catalogo.consultar(candidato.productoId());
            validarCorrespondencia(candidato, producto);
            if (evaluador.obtiene(producto.tasaDeCaida())) {
                inventario.otorgar(derrota.jugadorGanadorId(), candidato);
                obtenidos.add(new BotinOtorgado(
                        producto.id(),
                        candidato.nombrePropio(),
                        producto.tipo(),
                        candidato.origen(),
                        producto.tasaDeCaida()));
            }
        }

        accionesProcesadas.add(derrota.accionId());
        return obtenidos.isEmpty()
                ? ResultadoBotin.sinBotin()
                : ResultadoBotin.otorgado(obtenidos);
    }

    private void validarCorrespondencia(
            ElementoCandidatoBotin candidato,
            ProductoBotin producto) {
        if (!candidato.productoId().equals(producto.id())) {
            throw new IntegracionBotinException("El catalogo devolvio un producto diferente al solicitado");
        }
        if (candidato.tipo() != producto.tipo()) {
            throw new IntegracionBotinException("El tipo del inventario no coincide con el catalogo");
        }
        if (candidato.tipo() == TipoBotin.ARMADURA
                && candidato.parteArmadura() != producto.parteArmadura()) {
            throw new IntegracionBotinException("La parte de armadura no coincide con el catalogo");
        }
    }
}
