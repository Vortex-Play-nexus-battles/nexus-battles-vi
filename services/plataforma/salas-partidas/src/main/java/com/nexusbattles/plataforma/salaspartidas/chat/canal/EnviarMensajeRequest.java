package com.nexusbattles.plataforma.salaspartidas.chat.canal;

import com.nexusbattles.plataforma.salaspartidas.chat.MensajeDeChat.LogroCompartido;

/** Cuerpo del mensaje enviarMensaje del contrato AsyncAPI. */
public record EnviarMensajeRequest(String texto, LogroCompartido logro) { }
