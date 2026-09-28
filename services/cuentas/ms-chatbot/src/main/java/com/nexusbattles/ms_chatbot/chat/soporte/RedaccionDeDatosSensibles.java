package com.nexusbattles.ms_chatbot.chat.soporte;

// 7.4.8 «no almacenamiento de informacion sensible». Lo que el ticket guarda
// (asunto, mensaje y contexto) pasa por aqui ANTES de guardarse: contrasenas,
// tarjetas y tokens salen tapados.
//
// La redaccion de verdad la trae el equipo de plataforma (Simon) en su PR de
// 7.4.8. Esta interfaz es el unico punto donde se conecta: cuando su
// componente este en develop, RedaccionProvisional se reemplaza por un
// adaptador a el. El PR de los tickets NO se abre antes de eso.
public interface RedaccionDeDatosSensibles {

    String redactar(String texto);
}
