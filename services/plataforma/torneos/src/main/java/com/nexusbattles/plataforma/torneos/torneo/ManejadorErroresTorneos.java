package com.nexusbattles.plataforma.torneos.torneo;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/** Regla 4: problem details con `motivo`, iguales en toda la plataforma. */
@RestControllerAdvice(basePackages = "com.nexusbattles.plataforma.torneos")
public class ManejadorErroresTorneos {

    static final String BASE = "https://nexusbattles.local/errores/";

    @ExceptionHandler(TorneoRechazado.class)
    public ProblemDetail manejar(TorneoRechazado ex) {
        HttpStatus estado = switch (ex.motivo()) {
            case PERMISO_INSUFICIENTE -> HttpStatus.FORBIDDEN;
            case NO_ENCONTRADO -> HttpStatus.NOT_FOUND;
            case SOLICITUD_INVALIDA -> HttpStatus.BAD_REQUEST;
            case VENTANA_DE_91_DIAS, ESTADO_NO_PERMITE, JUGADOR_YA_EN_EQUIPO, CUPO_AGOTADO, YA_INSCRITO,
                 ENCUENTRO_NO_LISTO -> HttpStatus.CONFLICT;
            case NOMBRE_RECHAZADO, CREDITOS_INSUFICIENTES, INTEGRANTE_SANCIONADO, SIN_EQUIPOS,
                 GANADOR_NO_PARTICIPA -> HttpStatus.UNPROCESSABLE_ENTITY;
            case LISTA_NEGRA_NO_DISPONIBLE, LIBRO_NO_DISPONIBLE, SANCIONES_NO_DISPONIBLES -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, ex.getMessage());
        problema.setType(URI.create(BASE + ex.motivo().name().toLowerCase().replace('_', '-')));
        problema.setTitle(tituloDe(ex.motivo()));
        problema.setProperty("motivo", ex.motivo().name());
        if (ex.proximaFechaPosible() != null) {
            problema.setProperty("proximaFechaPosible", ex.proximaFechaPosible());
        }
        return problema;
    }

    static String tituloDe(TorneoRechazado.Motivo motivo) {
        return switch (motivo) {
            case PERMISO_INSUFICIENTE -> "No tienes permiso para esto";
            case SOLICITUD_INVALIDA -> "Solicitud invalida";
            case NO_ENCONTRADO -> "No encontrado";
            case VENTANA_DE_91_DIAS -> "Solo hay un torneo cada 91 dias";
            case ESTADO_NO_PERMITE -> "El estado del torneo no lo permite";
            case JUGADOR_YA_EN_EQUIPO -> "El jugador ya esta en un equipo";
            case NOMBRE_RECHAZADO -> "Nombre o avatar rechazados";
            case LISTA_NEGRA_NO_DISPONIBLE -> "No se pudo verificar el nombre";
            case CUPO_AGOTADO -> "Cupo agotado";
            case YA_INSCRITO -> "El equipo ya esta inscrito";
            case CREDITOS_INSUFICIENTES -> "Creditos insuficientes";
            case INTEGRANTE_SANCIONADO -> "Un integrante esta sancionado";
            case LIBRO_NO_DISPONIBLE -> "El libro de creditos no responde";
            case SANCIONES_NO_DISPONIBLES -> "No se pudo consultar las sanciones";
            case SIN_EQUIPOS -> "Sin equipos inscritos";
            case ENCUENTRO_NO_LISTO -> "El encuentro no esta listo";
            case GANADOR_NO_PARTICIPA -> "Ese equipo no juega este encuentro";
        };
    }
}
