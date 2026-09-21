package com.nexusbattles.ms_subastas.seguridad;

import org.springframework.beans.factory.ObjectProvider;

import com.nexusbattles.comun.seguridad.servicio.TokenDeServicio;

/**
 * Resuelve la credencial con la que este servicio habla con los demás cuando
 * el adaptador es el real ({@code app.*.modo=http}) — ADR-005.
 *
 * <p>Falla el arranque, no la primera puja: desde #455 ms-finanzas responde
 * 401 a todo {@code /creditos/**} sin credencial de servicio, e inventario
 * hace lo mismo con los bloqueos y transferencias sobre el inventario de
 * otro (#451). Arrancar «bien» y descubrirlo cuando un jugador puja sería
 * exactamente el fallo silencioso que las doce reglas prohíben.
 */
public final class CredencialSaliente {

    private CredencialSaliente() {
    }

    /**
     * @param proveedor el {@code TokenDeServicio} que crea la autoconfiguración
     *        de {@code plataforma-seguridad} cuando {@code DIRECTORIO_ACTIVO_CLIENT_ID}
     *        (o {@code seguridad.servicio.client-id}) tiene valor
     * @param destino   nombre del servicio remoto, para el mensaje
     * @throws IllegalStateException si no hay credencial configurada
     */
    public static PortadorDeServicio obligatoria(ObjectProvider<TokenDeServicio> proveedor, String destino) {
        TokenDeServicio token = proveedor.getIfAvailable();
        if (token == null) {
            throw new IllegalStateException("ms-subastas necesita credencial de servicio para hablar con "
                    + destino + " (ADR-005): configura DIRECTORIO_ACTIVO_URL, DIRECTORIO_ACTIVO_CLIENT_ID y "
                    + "DIRECTORIO_ACTIVO_CLIENT_SECRET, o usa el doble (app.*.modo=fake).");
        }
        return PortadorDeServicio.de(token);
    }
}
