package nexus.inventario.aplicacion;

import java.util.Objects;
import java.util.UUID;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.ElementoNoEncontradoException;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.RepositorioDeInventarios;
import nexus.inventario.dominio.TablaDeNiveles;
import org.springframework.stereotype.Service;

/**
 * El heroe en mision (inventario.yaml 1.6.0, B9; secciones 7.8.6 y 7.8.10).
 * Lo usa solo el servicio de misiones, con su credencial.
 *
 * <p>Mismo patron que {@link GestionarBloqueoSubasta}: el propietario viaja en el
 * cuerpo como dato de negocio y se contrasta con el dueno del documento; la
 * {@code Idempotency-Key} se exige, pero la verdad es el estado del elemento.
 *
 * <p>La liberacion quita el bloqueo y suma la experiencia en UNA escritura del
 * documento del jugador: nunca queda un heroe libre sin su experiencia ni con
 * ella sumada dos veces. Si ya no esta bloqueado por esa ejecucion, es que la
 * liberacion ya se aplico, y se devuelve el heroe tal cual.
 */
@Service
public class GestionarBloqueoMision {

    private final RepositorioDeInventarios repositorio;
    private final FuenteDeTablaDeNiveles niveles;

    public GestionarBloqueoMision(RepositorioDeInventarios repositorio, FuenteDeTablaDeNiveles niveles) {
        this.repositorio = Objects.requireNonNull(repositorio, "repositorio es obligatorio");
        this.niveles = Objects.requireNonNull(niveles, "niveles es obligatorio");
    }

    public ElementoInventario bloquear(UUID propietarioUid, String heroeId, UUID ejecucionId,
                                       String claveIdempotencia) {
        Objects.requireNonNull(propietarioUid, "propietarioUid es obligatorio");
        Objects.requireNonNull(ejecucionId, "ejecucionId es obligatorio");
        exigirTexto(claveIdempotencia, "claveIdempotencia");
        String heroe = exigirTexto(heroeId, "heroeId");
        Inventario inventario = inventarioDe(propietarioUid, heroe);
        Inventario guardado = repositorio.guardar(inventario.bloquearEnMision(heroe, ejecucionId.toString()));
        return guardado.elemento(heroe);
    }

    public ElementoInventario liberar(UUID propietarioUid, String heroeId, UUID ejecucionId, double experiencia,
                                      String claveIdempotencia) {
        Objects.requireNonNull(propietarioUid, "propietarioUid es obligatorio");
        Objects.requireNonNull(ejecucionId, "ejecucionId es obligatorio");
        exigirTexto(claveIdempotencia, "claveIdempotencia");
        if (experiencia < 0 || Double.isNaN(experiencia) || Double.isInfinite(experiencia)) {
            throw new IllegalArgumentException("experiencia no puede ser negativa");
        }
        String heroe = exigirTexto(heroeId, "heroeId");
        Inventario inventario = inventarioDe(propietarioUid, heroe);
        ElementoInventario actual = inventario.elemento(heroe);
        if (!actual.enMision()) {
            // Idempotente por estado: la liberacion (y su experiencia) ya se aplico.
            return actual;
        }
        int nivel = actual.nivelActual();
        double acumulada = actual.experienciaActual();
        if (experiencia > 0) {
            TablaDeNiveles.Progresion progresion = niveles.tabla().sumar(nivel, acumulada, experiencia);
            nivel = progresion.nivel();
            acumulada = progresion.experiencia();
        }
        Inventario guardado = repositorio.guardar(
                inventario.liberarDeMision(heroe, ejecucionId.toString(), nivel, acumulada));
        return guardado.elemento(heroe);
    }

    private Inventario inventarioDe(UUID propietarioUid, String heroeId) {
        Inventario inventario = repositorio.buscarPorElementoId(heroeId)
                .orElseThrow(ElementoNoEncontradoException::new);
        if (!inventario.propietarioId().equals(propietarioUid.toString())) {
            throw new InventarioAjenoException();
        }
        return inventario;
    }

    private static String exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " no puede estar vacio");
        }
        return valor.trim();
    }
}
