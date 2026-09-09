package nexus.api;

import java.util.List;
import java.util.stream.IntStream;
import nexus.dominio.Heroe;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las reglas de progresion que no dependen del prototipo (HU-HER-003 y
 * HU-HER-004), como servicio sin estado: el inventario guarda nivel y
 * experiencia, las misiones otorgan puntos, y ambos consultan aqui la regla en
 * vez de reimplementarla. Contrato en contracts/openapi/heroes.yaml.
 */
@RestController
@RequestMapping("/api/v1/progresion")
public class ProgresionController {

    /** Tabla de experiencia requerida por nivel: 100 x 1,2^(N-1); el 8 no sube. */
    @GetMapping("/niveles")
    public List<NivelDeProgresion> tablaDeNiveles() {
        return IntStream.rangeClosed(Heroe.NIVEL_MINIMO, Heroe.NIVEL_MAXIMO)
                .mapToObj(n -> new NivelDeProgresion(n, Heroe.experienciaParaSubirDesde(n)))
                .toList();
    }

    /** Aplica puntos a un estado (nivel, experiencia) con sobrante y tope 8. */
    @PostMapping("/experiencia")
    public Progreso progresar(@RequestBody SolicitudDeProgreso solicitud) {
        Heroe.Progreso p = Heroe.progresar(solicitud.nivel(), solicitud.experiencia(), solicitud.puntos());
        return new Progreso(p.nivel(), p.experiencia());
    }

    /**
     * HU-HER-004: 10 x 1,2^(1d8) por enemigo no jugador derrotado. El dado lo
     * lanza quien ejecuta la mision (confirmacion del cliente, 2026-08-27: la
     * experiencia se obtiene en las misiones, no en el combate en linea).
     */
    @GetMapping("/experiencia-por-enemigo/{dado}")
    public ExperienciaOtorgada experienciaPorEnemigo(@PathVariable int dado) {
        return new ExperienciaOtorgada(dado, Heroe.experienciaPorEnemigoDerrotado(dado));
    }

    public record NivelDeProgresion(int nivel, Double experienciaParaSubir) {
    }

    public record SolicitudDeProgreso(int nivel, double experiencia, double puntos) {
    }

    public record Progreso(int nivel, double experiencia) {
    }

    public record ExperienciaOtorgada(int dado, double puntos) {
    }
}
