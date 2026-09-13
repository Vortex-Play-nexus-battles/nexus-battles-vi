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

    /** HU-REN-003: medicion de las consultas a la base de datos. */
    private Consultas consultas = new Consultas();

    /** Configuracion de la instrumentacion de consultas (HU-REN-003). */
    public static class Consultas {

        /** Valvula de escape por servicio, igual que {@code latencia.activa}. */
        private boolean activa = true;

        private int capacidad = RegistroDeConsultas.CAPACIDAD_POR_OMISION;

        private int capacidadLentas = RegistroDeConsultas.CAPACIDAD_LENTAS_POR_OMISION;

        /**
         * A partir de cuantos ms una consulta se marca como lenta (CA-03).
         *
         * <p>Vacio significa «usa el objetivo de RNF-REN-001», que son 500 ms.
         * <b>No se inventa aqui un presupuesto de base de datos</b>: los 500 ms
         * son extremo a extremo —red, validacion, negocio, consulta y
         * serializacion—, asi que una consulta que sola se los come ya es un
         * problema demostrable. Un presupuesto propio y mas estricto para la
         * base de datos no esta acordado en ningun requisito, y ponerlo aqui
         * seria inventarlo. Queda como decision pendiente del equipo.
         */
        private Long umbralLentaMs;

        private int sentenciasEnInforme = 10;

        public boolean isActiva() {
            return activa;
        }

        public void setActiva(boolean activa) {
            this.activa = activa;
        }

        public int getCapacidad() {
            return capacidad;
        }

        public void setCapacidad(int capacidad) {
            this.capacidad = capacidad;
        }

        public int getCapacidadLentas() {
            return capacidadLentas;
        }

        public void setCapacidadLentas(int capacidadLentas) {
            this.capacidadLentas = capacidadLentas;
        }

        public Long getUmbralLentaMs() {
            return umbralLentaMs;
        }

        public void setUmbralLentaMs(Long umbralLentaMs) {
            this.umbralLentaMs = umbralLentaMs;
        }

        public int getSentenciasEnInforme() {
            return sentenciasEnInforme;
        }

        public void setSentenciasEnInforme(int sentenciasEnInforme) {
            this.sentenciasEnInforme = sentenciasEnInforme;
        }
    }

    public Consultas getConsultas() {
        return consultas;
    }

    public void setConsultas(Consultas consultas) {
        this.consultas = consultas;
    }

    /** El umbral de consulta lenta vigente: el configurado, o el objetivo. */
    public long umbralDeConsultaLentaMs() {
        Long propio = consultas.getUmbralLentaMs();
        return propio != null ? propio : objetivoMs;
    }

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
