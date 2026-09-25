package com.nexusbattles.ms_chatbot.chat.limite;

// 7.4.8 del documento: «limite de tasa de consultas para prevenir abuso».
// Se responde 429 con Retry-After (ms-chatbot.yaml 2.0.0).
public class LimiteDeFrecuenciaExcedido extends RuntimeException {

    private final long segundosParaReintentar;

    public LimiteDeFrecuenciaExcedido(String detalle, long segundosParaReintentar) {
        super(detalle);
        this.segundosParaReintentar = Math.max(1, segundosParaReintentar);
    }

    public long segundosParaReintentar() {
        return segundosParaReintentar;
    }
}
