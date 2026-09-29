# Apagado nocturno y encendido antes de la jornada del host de plataforma,
# en hora de Colombia, con EventBridge Scheduler (29-sep).
#
# ## Por que aqui y no en el cron de infra-dev.yml
#
# El horario vivia solo en los `schedule` de infra-dev.yml, y el planificador
# de GitHub llega tarde, a veces horas: el 27 y el 28-sep el apagado de las
# 23:23 corrio entre las 05:00 y las 06:46, y el encendido de las 07:17 a las
# 14:41 (hora de Colombia). Con t3.small eso costaba una manana sin entorno;
# con c7i-flex.large (opcion E, cuatro veces el precio por hora) ademas cuesta
# credito cada noche que se queda encendido.
#
# EventBridge Scheduler ya corre en esta cuenta (los recordatorios de
# alertas.tf): este archivo no estrena servicio ni rol, reutiliza los dos.
# Dispara a la hora exacta (flexible_time_window OFF), entiende la zona IANA
# (America/Bogota) y es gratuito hasta 14 millones de invocaciones al mes.
# Llama directamente a EC2 (StartInstances / StopInstances) con el rol del
# planificador, que solo puede encender y apagar ESTA instancia.
#
# infra-dev.yml conserva sus `schedule`: para el host de contenido y como
# respaldo de este. Cuando estos dos horarios existen y estan activos, su
# paso de encender/apagar de plataforma no hace nada (no hay dos
# planificadores haciendo lo mismo); si faltan o se desactivan, vuelve a
# actuar el cron.
#
# Idempotente: encender una instancia encendida o apagar una apagada no hace
# nada. Si el CD necesita el host fuera de horario, lo enciende el mismo
# (accion asegurar-host-encendido) y el siguiente apagado programado lo apaga.

locals {
  horario_zona = "America/Bogota"
}

data "aws_iam_policy_document" "encender_apagar" {
  statement {
    sid       = "EncenderApagarSoloPlataforma"
    actions   = ["ec2:StartInstances", "ec2:StopInstances"]
    resources = [aws_instance.plataforma.arn]
  }
}

# El rol de alertas.tf ya confia en scheduler.amazonaws.com. Una politica
# aparte, con su nombre, para que se lea que puede hacer y por que.
resource "aws_iam_role_policy" "scheduler_encender_apagar" {
  name   = "encender-apagar-plataforma"
  role   = aws_iam_role.scheduler.id
  policy = data.aws_iam_policy_document.encender_apagar.json
}

resource "aws_scheduler_schedule" "apagar_plataforma" {
  name                         = "nexus-plataforma-apagar"
  description                  = "Apaga nexus-plataforma-dev cada noche (hora de Colombia). Ver horario.tf."
  schedule_expression          = var.horario_apagar
  schedule_expression_timezone = local.horario_zona
  state                        = var.horario_activo ? "ENABLED" : "DISABLED"

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = "arn:aws:scheduler:::aws-sdk:ec2:stopInstances"
    role_arn = aws_iam_role.scheduler.arn
    input    = jsonencode({ InstanceIds = [aws_instance.plataforma.id] })

    # Un fallo pasajero de la API se reintenta; pasada una hora ya no tiene
    # sentido apagar "la noche de ayer".
    retry_policy {
      maximum_event_age_in_seconds = 3600
      maximum_retry_attempts       = 10
    }
  }

  depends_on = [aws_iam_role_policy.scheduler_encender_apagar]
}

resource "aws_scheduler_schedule" "encender_plataforma" {
  name                         = "nexus-plataforma-encender"
  description                  = "Enciende nexus-plataforma-dev antes de la jornada, de lunes a viernes (hora de Colombia). Ver horario.tf."
  schedule_expression          = var.horario_encender
  schedule_expression_timezone = local.horario_zona
  state                        = var.horario_activo ? "ENABLED" : "DISABLED"

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = "arn:aws:scheduler:::aws-sdk:ec2:startInstances"
    role_arn = aws_iam_role.scheduler.arn
    input    = jsonencode({ InstanceIds = [aws_instance.plataforma.id] })

    # Encender si importa aunque llegue tarde (una falta momentanea de
    # capacidad en la zona, por ejemplo): se reintenta durante dos horas.
    retry_policy {
      maximum_event_age_in_seconds = 7200
      maximum_retry_attempts       = 20
    }
  }

  depends_on = [aws_iam_role_policy.scheduler_encender_apagar]
}
