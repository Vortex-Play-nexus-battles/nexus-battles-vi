package com.nexusbattles.plataforma.correo.cola;

import java.time.Instant;

/**
 * Un envio tal como lo muestra la evidencia de entrega
 * ({@code GET /correos/envios}, esquema {@code EnvioRegistrado} del contrato).
 *
 * <p>RF-COR-001 exige "evidencia de entrega efectiva del correo". Un 202 no lo
 * es: dice que la peticion se guardo. Esto dice que paso despues, con que
 * identificador, cuando y <b>a que servidor</b> -- que es lo que se puede
 * contrastar contra los registros del proveedor.
 *
 * <p><b>Lo que NO lleva:</b> el cuerpo del mensaje, el codigo de recuperacion,
 * el nombre de nadie, ni la direccion completa: el destinatario sale
 * enmascarado ({@code v***a@dominio}).
 *
 * @param instante      ultimo cambio de estado
 * @param destinatario  direccion enmascarada
 * @param plantilla     que correo era
 * @param estado        estado del envio
 * @param destino       PROVEEDOR o BUZON_DE_PRUEBAS; vacio si no se entrego
 * @param identificador Message-ID que puso el servidor, si lo hubo
 * @param motivo        ultimo fallo o motivo de la omision, si lo hubo
 * @param intentos      intentos empezados
 */
public record EnvioRegistrado(
        Instant instante,
        String destinatario,
        String plantilla,
        String estado,
        String destino,
        String identificador,
        String motivo,
        int intentos) {
}
