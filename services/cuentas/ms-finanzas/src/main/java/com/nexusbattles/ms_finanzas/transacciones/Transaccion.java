package com.nexusbattles.ms_finanzas.transacciones;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "transacciones")
@Getter
@Setter
@NoArgsConstructor
public class Transaccion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Clave de idempotencia proporcionada por el cliente. Un reintento por
    // timeout debe traer el mismo refId para que ms-finanzas rechace el
    // duplicado en vez de cobrar dos veces.
    @Column(name = "ref_id", nullable = false, unique = true, length = 128)
    private String refId;

    // Identificador público inmutable del usuario, tomado del claim `uid` del
    // JWT — nunca el apodo, que es mutable (HU-PAG-002 y política del proyecto).
    @Column(name = "uid_usuario", nullable = false, length = 64)
    private String uidUsuario;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal monto;

    // Código ISO 4217 en mayúsculas (p. ej. "COP", "USD").
    @Column(nullable = false, length = 3, columnDefinition = "char(3)")
    private String moneda;

    @Column(nullable = false, length = 128)
    private String concepto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ResultadoTransaccion resultado;

    @Column(name = "comprobante_url", length = 512)
    private String comprobanteUrl;

    // Referencia devuelta por la pasarela simulada; se usa para conciliar
    // ante estados indeterminados sin depender del refId del cliente.
    @Column(name = "pasarela_ref_externa", length = 128)
    private String pasarelaRefExterna;

    @Column(nullable = false, updatable = false)
    private Instant creado;

    @Column(nullable = false)
    private Instant actualizado;
}
