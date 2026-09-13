package com.nexusbattles.plataforma.observabilidad;

import java.util.Optional;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracion de la instrumentacion de latencia (HU-REN-001).
 *
 * <p>Regla 10 de plataforma: todo se configura por variable de entorno, no hay
 * ningun valor real versionado. El mapeo es el de Spring Boot:
 *
 * <pre>
 *   latencia.objetivo-ms  ->  LATENCIA_OBJETIVO_MS
 *   latencia.percentil    ->  LATENCIA_PERCENTIL
 *   latencia.capacidad    ->  LATENCIA_CAPACIDAD
 *   latencia.servicio     ->  LATENCIA_SERVICIO
 * </pre>
 *
 * <p><b>El percentil se deja deliberadamente sin valor por omision.</b> Ver
 * {@link #objetivo()}.
 */
@ConfigurationProperties(prefix = "latencia")
public class PropiedadesDeLatencia {

    /**
     * Objetivo de RNF-REN-001. Este si tiene valor por omision porque el numero
     * no lo decide el equipo: viene fijado por el requisito y por el Project
     * Charter, y ponerlo aqui no suplanta ninguna decision pendiente.
     */
    private long objetivoMs = ObjetivoDeLatencia.OBJETIVO_POR_OMISION_MS;

    /**
     * Percentil de evaluacion. Nulo mientras el Product Owner no lo apruebe.
     *
     * <p>No es un descuido: CA-03 exige aprobacion <i>por escrito</i> de p95 o
     * p99. Escribir aqui un 95 tomaria esa decision en su lugar y nadie
     * volveria a mirarla.
     */
    private Double percentil;

    /** Cuantas muestras se conservan en la ventana. */
    private int capacidad = RegistroDeLatencia.CAPACIDAD_POR_OMISION;

    /**
     * Nombre del servicio en las muestras. Si esta vacio se usa
     * {@code spring.application.name}, que ya identifica al modulo.
     */
    private String servicio;

    /** Cuantas de las operaciones mas lentas incluye el informe. */
    private int operacionesEnInforme = 5;

    /**
     * El objetivo vigente, o vacio si el Product Owner aun no aprobo el percentil.
     *
     * <p>Devolver {@code Optional} y no lanzar al arrancar es intencional: la
     * <b>medicion</b> no depende de ninguna decision pendiente y debe estar
     * activa desde el Sprint 1 en los veinte modulos. Lo que si depende del
     * Product Owner es la <b>evaluacion</b> —decir «cumple» o «no cumple»—, y eso
     * falla de forma explicita y localizada en el endpoint del informe, en vez de
     * impedir que arranquen servicios de los tres equipos.
     */
    public Optional<ObjetivoDeLatencia> objetivo() {
        return percentil == null
                ? Optional.empty()
                : Optional.of(new ObjetivoDeLatencia(objetivoMs, percentil));
    }

    public long getObjetivoMs() {
        return objetivoMs;
    }

    public void setObjetivoMs(long objetivoMs) {
        this.objetivoMs = objetivoMs;
    }

    public Double getPercentil() {
        return percentil;
    }

    public void setPercentil(Double percentil) {
        this.percentil = percentil;
    }

    public int getCapacidad() {
        return capacidad;
    }

    public void setCapacidad(int capacidad) {
        this.capacidad = capacidad;
    }

    public String getServicio() {
        return servicio;
    }

    public void setServicio(String servicio) {
        this.servicio = servicio;
    }

    public int getOperacionesEnInforme() {
        return operacionesEnInforme;
    }

    public void setOperacionesEnInforme(int operacionesEnInforme) {
        this.operacionesEnInforme = operacionesEnInforme;
    }
}
