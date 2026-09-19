package com.nexusbattles.plataforma.salaspartidas.integracion;

import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.MotorDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.MotorNoDisponible;
import com.nexusbattles.plataforma.salaspartidas.dominio.ResolucionDelMotor;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Adaptador hacia el motor de combate — espejo de
 * {@code contracts/openapi/motor-combate.yaml}.
 *
 * <p>Habla con {@code POST /api/v1/combate/ataques}. El motor no guarda nada
 * entre llamadas: cada peticion se resuelve entera, asi que este cliente no
 * tiene que mantener sesion ni identificador de partida.
 *
 * <p><b>Que manda y que no.</b> Manda el nombre del heroe atacante —el motor
 * pide sus estadisticas al catalogo por su cuenta— y la vida del objetivo como
 * defensa. Este servicio no conoce la defensa real de un heroe: no la guarda ni
 * la debe guardar. Ver la nota de abajo.
 *
 * <p><b>Cualquier fallo es {@link MotorNoDisponible}</b>, incluido un cuerpo que
 * no se entiende. No se traduce a un dano de cero: un cero se confunde con un
 * ataque fallido y decidiria el combate con un numero que nadie calculo.
 */
public class ClienteMotorCombate implements MotorDeCombate {

    /**
     * Prototipo de distribucion con el que se pide la resolucion.
     *
     * <p><b>Decision funcional pendiente, documentada y no inventada.</b> El
     * motor define seis prototipos y el catalogo de heroes tiene mas tipos; en
     * ningun sitio del repositorio existe el mapeo heroe → prototipo, y el
     * Product Owner no lo ha fijado. Se usa uno equilibrado para todos y queda
     * anotado en el contrato del motor: el dia que exista el mapeo, se cambia
     * aqui una linea.
     */
    static final String PROTOTIPO_POR_DEFECTO = "GUERRERO_ARMAS";

    private final RestClient http;
    private final String base;

    public ClienteMotorCombate(RestClient http, String base) {
        this.http = http;
        this.base = base;
    }

    @Override
    public ResolucionDelMotor resolver(HeroeDeCombate atacante, HeroeDeCombate objetivo) {
        try {
            Respuesta respuesta = http.post()
                    .uri(base + "/api/v1/combate/ataques")
                    .body(new Peticion(atacante.nombre(), defensaDe(objetivo),
                            new Peticion.Distribucion(PROTOTIPO_POR_DEFECTO)))
                    .retrieve()
                    .body(Respuesta.class);

            if (respuesta == null || respuesta.categoria() == null) {
                throw new MotorNoDisponible("el motor respondio un cuerpo que no se entiende");
            }
            return new ResolucionDelMotor(respuesta.categoria(),
                    respuesta.danoAplicado(), respuesta.ataqueResuelto());

        } catch (RestClientException noResponde) {
            throw new MotorNoDisponible(noResponde.getMessage());
        }
    }

    /**
     * Defensa del objetivo.
     *
     * <p><b>Aqui hay una simplificacion consciente.</b> La defensa real de un
     * heroe la publica el catalogo (`GET /api/v1/heroes/{nombre}`), y este
     * servicio no la guarda: la ficha que conserva de cada participante lleva
     * vida, no defensa. Mandar su vida actual como defensa es lo mas cercano
     * que se puede hacer sin inventar un numero ni anadir aqui una integracion
     * con el catalogo que pertenece al motor.
     *
     * <p>Consecuencia observable: un heroe herido se defiende peor. No es una
     * regla acordada en ninguna HU —queda anotada como pendiente—, pero es
     * determinable y coherente, y no falsea ninguna estadistica. Cuando el
     * motor acepte el nombre del objetivo y consulte su defensa al catalogo
     * -como ya hace con el atacante-, esta linea desaparece.
     */
    private static int defensaDe(HeroeDeCombate objetivo) {
        return objetivo.vidaActual();
    }

    /** Espejo de {@code PeticionDeAtaque} del contrato del motor. */
    private record Peticion(String heroeAtacante, int defensaObjetivo, Distribucion distribucion) {

        private record Distribucion(String prototipo) {
        }
    }

    /** Espejo de {@code ResolucionDeAtaque}. Se ignora lo que no se usa. */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private record Respuesta(String categoria, int danoAplicado, int ataqueResuelto) {
    }
}
