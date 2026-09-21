package com.nexusbattles.plataforma.adminparametros.parametros;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Un parametro operativo del sistema (RF-ADM-001) con su regla de validacion
 * (tipo, rango u opciones), su valor vigente, un cambio programado y su
 * version. Los inalterables (Charter) se consultan pero no se editan.
 */
@Entity
@Table(name = "parametros")
public class Parametro {

    public enum Tipo { ENTERO, DECIMAL, BOOLEANO, TEXTO }

    @Id
    @Column(length = 80)
    private String clave;

    @Column(nullable = false, length = 300)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Tipo tipo;

    @Column(length = 200)
    private String valor;

    @Column(length = 30)
    private String unidad;

    private BigDecimal minimo;

    private BigDecimal maximo;

    @Column(length = 300)
    private String opciones;

    @Column(nullable = false)
    private boolean inalterable;

    @Column(nullable = false, length = 120)
    private String origen;

    @Column(nullable = false)
    private int version;

    @Column(name = "actualizado_por")
    private UUID actualizadoPor;

    @Column(name = "actualizado_en")
    private OffsetDateTime actualizadoEn;

    @Column(name = "valor_programado", length = 200)
    private String valorProgramado;

    @Column(name = "vigente_desde")
    private OffsetDateTime vigenteDesde;

    @Column(nullable = false)
    private int orden;

    protected Parametro() {
    }

    /** Solo para pruebas: un parametro editable con rango. */
    public static Parametro editable(String clave, Tipo tipo, String valor, BigDecimal minimo, BigDecimal maximo,
                                     String opciones, String origen) {
        Parametro p = new Parametro();
        p.clave = clave;
        p.descripcion = clave;
        p.tipo = tipo;
        p.valor = valor;
        p.minimo = minimo;
        p.maximo = maximo;
        p.opciones = opciones;
        p.origen = origen;
        p.version = 1;
        return p;
    }

    /** Solo para pruebas: un parametro fijado por el Charter. */
    public static Parametro inalterable(String clave, Tipo tipo, String valor, String origen) {
        Parametro p = editable(clave, tipo, valor, null, null, null, origen);
        p.inalterable = true;
        return p;
    }

    /**
     * Valida un candidato contra tipo, rango y opciones.
     *
     * @return el valor normalizado (como texto) o null si el candidato es null
     * @throws ParametroRechazado VALOR_INVALIDO
     */
    public String validar(String candidato) {
        if (candidato == null || candidato.isBlank()) {
            return null;
        }
        String texto = candidato.strip();
        switch (tipo) {
            case ENTERO -> {
                long numero;
                try {
                    numero = Long.parseLong(texto);
                } catch (NumberFormatException noEntero) {
                    throw new ParametroRechazado(ParametroRechazado.Motivo.VALOR_INVALIDO,
                            clave + " es un entero y llego «" + texto + "»");
                }
                exigirRango(BigDecimal.valueOf(numero));
                return Long.toString(numero);
            }
            case DECIMAL -> {
                BigDecimal numero;
                try {
                    numero = new BigDecimal(texto);
                } catch (NumberFormatException noDecimal) {
                    throw new ParametroRechazado(ParametroRechazado.Motivo.VALOR_INVALIDO,
                            clave + " es un decimal y llego «" + texto + "»");
                }
                exigirRango(numero);
                return numero.stripTrailingZeros().toPlainString();
            }
            case BOOLEANO -> {
                String bajo = texto.toLowerCase(Locale.ROOT);
                if (!bajo.equals("true") && !bajo.equals("false")) {
                    throw new ParametroRechazado(ParametroRechazado.Motivo.VALOR_INVALIDO,
                            clave + " es booleano (true/false) y llego «" + texto + "»");
                }
                return bajo;
            }
            case TEXTO -> {
                if (opciones != null && !opcionesComoLista().contains(texto)) {
                    throw new ParametroRechazado(ParametroRechazado.Motivo.VALOR_INVALIDO,
                            clave + " admite " + opciones.replace('|', '/') + " y llego «" + texto + "»");
                }
                if (texto.length() > 200) {
                    throw new ParametroRechazado(ParametroRechazado.Motivo.VALOR_INVALIDO, clave + " admite hasta 200 caracteres");
                }
                return texto;
            }
            default -> throw new IllegalStateException("tipo desconocido " + tipo);
        }
    }

    private void exigirRango(BigDecimal numero) {
        if (minimo != null && numero.compareTo(minimo) < 0) {
            throw new ParametroRechazado(ParametroRechazado.Motivo.VALOR_INVALIDO,
                    clave + " no puede ser menor que " + minimo.stripTrailingZeros().toPlainString());
        }
        if (maximo != null && numero.compareTo(maximo) > 0) {
            throw new ParametroRechazado(ParametroRechazado.Motivo.VALOR_INVALIDO,
                    clave + " no puede ser mayor que " + maximo.stripTrailingZeros().toPlainString());
        }
    }

    /**
     * Valor vigente en {@code ahora}: el programado si su fecha ya llego, si
     * no el actual. No muta: la promocion la hace {@link #consolidar}.
     */
    public String valorVigenteEn(OffsetDateTime ahora) {
        if (valorProgramado != null || vigenteDesde != null) {
            if (vigenteDesde != null && !vigenteDesde.isAfter(ahora)) {
                return valorProgramado;
            }
        }
        return valor;
    }

    /** Si el cambio programado ya entro en vigor, lo vuelve el valor actual. */
    public boolean consolidar(OffsetDateTime ahora) {
        if (vigenteDesde != null && !vigenteDesde.isAfter(ahora)) {
            valor = valorProgramado;
            valorProgramado = null;
            vigenteDesde = null;
            return true;
        }
        return false;
    }

    /**
     * Aplica (o programa) un cambio ya validado.
     *
     * @return la version nueva
     */
    public Version cambiar(String nuevoValor, String motivo, UUID quien, OffsetDateTime ahora, OffsetDateTime desde) {
        if (inalterable) {
            throw new ParametroRechazado(ParametroRechazado.Motivo.INALTERABLE,
                    clave + " lo fija el Project Charter (" + origen + ") y no se edita");
        }
        consolidar(ahora);
        String anterior = valor;
        OffsetDateTime vigencia = desde == null || !desde.isAfter(ahora) ? ahora : desde;
        version++;
        actualizadoPor = quien;
        actualizadoEn = ahora;
        if (vigencia.isAfter(ahora)) {
            valorProgramado = nuevoValor;
            vigenteDesde = vigencia;
        } else {
            valor = nuevoValor;
            valorProgramado = null;
            vigenteDesde = null;
        }
        return new Version(null, clave, version, anterior, nuevoValor, motivo, quien, ahora, vigencia);
    }

    public List<String> opcionesComoLista() {
        return opciones == null ? List.of() : Arrays.asList(opciones.split("\\|"));
    }

    public String clave() { return clave; }
    public String descripcion() { return descripcion; }
    public Tipo tipo() { return tipo; }
    public String valor() { return valor; }
    public String unidad() { return unidad; }
    public BigDecimal minimo() { return minimo; }
    public BigDecimal maximo() { return maximo; }
    public String opciones() { return opciones; }
    public boolean inalterable() { return inalterable; }
    public String origen() { return origen; }
    public int version() { return version; }
    public UUID actualizadoPor() { return actualizadoPor; }
    public OffsetDateTime actualizadoEn() { return actualizadoEn; }
    public String valorProgramado() { return valorProgramado; }
    public OffsetDateTime vigenteDesde() { return vigenteDesde; }
    public int orden() { return orden; }
}
