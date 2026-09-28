package nexus.misiones.integracion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexusbattles.plataforma.resiliencia.CortaCircuitos;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import nexus.misiones.dominio.simulacion.Golpe;
import nexus.misiones.dominio.simulacion.ResolutorDeGolpes;
import nexus.misiones.dominio.simulacion.SinCapacidadDeAtaque;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * {@link ResolutorDeGolpes} contra el motor de combate
 * ({@code POST /api/v1/combate/ataques}, motor-combate.yaml 1.1.0). «Motor de
 * combate identico al de batallas en linea» (7.8.12): el mismo que usa
 * salas-partidas, con la misma credencial de servicio.
 *
 * <p>El motor pide la distribucion de efectos por uno de sus seis prototipos
 * (GUERRERO_TANQUE...). Se deriva del nombre del catalogo sin inventar nada:
 * «Pícaro Veneno» es PICARO_VENENO. Los sanadores no tienen distribucion ni
 * formula de ataque —el motor los rechaza con 422—, asi que su golpe no se
 * pide: hace cero (6.1.1, «le esta vedado infligir dano»).
 */
public class ClienteMotor implements ResolutorDeGolpes {

    static final String DEPENDENCIA = "motor-combate";

    /** Los seis de {@code NombreDePrototipo} en motor-combate.yaml. */
    static final Set<String> PROTOTIPOS_DEL_MOTOR = Set.of("GUERRERO_TANQUE", "GUERRERO_ARMAS", "MAGO_FUEGO",
            "MAGO_HIELO", "PICARO_VENENO", "PICARO_MACHETE");

    private final RestClient http;
    private final String base;
    private final CortaCircuitos corta;

    public ClienteMotor(RestClient http, String base, CortaCircuitos corta) {
        this.http = http;
        this.base = ClienteInventario.sinBarraFinal(base);
        this.corta = corta;
    }

    @Override
    public Golpe resolver(String prototipoAtacante, int defensaObjetivo, Long semilla) {
        String distribucion = distribucionDe(prototipoAtacante)
                .orElseThrow(() -> new SinCapacidadDeAtaque(prototipoAtacante));
        PeticionDeAtaque peticion = new PeticionDeAtaque(prototipoAtacante, Math.max(0, defensaObjetivo),
                new Distribucion(distribucion), semilla);
        Contestacion<Resolucion> c = Contestacion.protegida(corta, () -> http.post()
                .uri(base + "/api/v1/combate/ataques")
                .contentType(MediaType.APPLICATION_JSON)
                .body(peticion)
                .retrieve()
                .body(Resolucion.class));
        if (c.rechazada()) {
            if (c.estado() == 422) {
                throw new SinCapacidadDeAtaque(prototipoAtacante);
            }
            throw c.comoRechazo(DEPENDENCIA);
        }
        return new Golpe(Math.max(0, c.cuerpo().danoAplicado()), "CAUSAR_DANO_CRITICO".equals(c.cuerpo().categoria()));
    }

    /** «Pícaro Veneno» → PICARO_VENENO; vacio si no es uno de los seis del motor. */
    static Optional<String> distribucionDe(String prototipo) {
        if (prototipo == null) {
            return Optional.empty();
        }
        String sinTildes = Normalizer.normalize(prototipo, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String nombre = sinTildes.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "_");
        return PROTOTIPOS_DEL_MOTOR.contains(nombre) ? Optional.of(nombre) : Optional.empty();
    }

    record PeticionDeAtaque(String heroeAtacante, int defensaObjetivo, Distribucion distribucion, Long semilla) {
    }

    record Distribucion(String prototipo) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Resolucion(String categoria, int danoAplicado, int ataqueResuelto, int defensaObjetivo) {
    }
}
