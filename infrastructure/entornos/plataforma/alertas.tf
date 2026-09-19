# Vigilancia del credito. AWS Budgets es gratuito para los dos primeros
# presupuestos y 62 budget-dias/mes con acciones; aqui no hay acciones,
# solo avisos. Los correos de Budgets llegan sin confirmacion previa.
#
# Los presupuestos miden el gasto BRUTO (include_credit = false): lo que la
# cuenta habria facturado sin credito. Es exactamente lo que se descuenta del
# credito, asi que "90 % de 100" = quedan 10 USD. El saldo exacto sigue
# estando en Billing > Credits y en el widget Cost and usage de la consola.

locals {
  umbrales_credito = [10, 25, 50, 75, 90]
}

# Credito total del Free Plan (anual: cubre sep-dic 2026, todo el proyecto).
# Un presupuesto anual se reinicia el 1 de enero; para 2027 el recordatorio
# de salida ya habra sonado (ver aws_scheduler_schedule.recordatorio).
resource "aws_budgets_budget" "credito" {
  name              = "nexus-credito-free-plan-2026"
  budget_type       = "COST"
  limit_amount      = tostring(var.credito_total_usd)
  limit_unit        = "USD"
  time_unit         = "ANNUALLY"
  time_period_start = "2026-01-01_00:00" # los anuales van por ano calendario; antes de sep no hubo gasto

  cost_types {
    include_credit             = false
    include_refund             = false
    include_discount           = true
    include_other_subscription = true
    include_recurring          = true
    include_subscription       = true
    include_support            = true
    include_tax                = true
    include_upfront            = true
    use_amortized              = false
    use_blended                = false
  }

  dynamic "notification" {
    for_each = local.umbrales_credito
    content {
      comparison_operator        = "GREATER_THAN"
      threshold                  = notification.value
      threshold_type             = "PERCENTAGE"
      notification_type          = "ACTUAL"
      subscriber_email_addresses = [var.correo_alertas]
    }
  }
}

# Tope mensual: detecta un recurso olvidado (una segunda instancia, un
# volumen huerfano) antes de que se coma el credito. Avisa por gasto real al
# 50/80/100 % y por PRONOSTICO al 100 %, que es el aviso que llega primero.
resource "aws_budgets_budget" "mensual" {
  name              = "nexus-gasto-mensual-dev"
  budget_type       = "COST"
  limit_amount      = tostring(var.tope_mensual_usd)
  limit_unit        = "USD"
  time_unit         = "MONTHLY"
  time_period_start = "2026-09-01_00:00"

  cost_types {
    include_credit             = false
    include_refund             = false
    include_discount           = true
    include_other_subscription = true
    include_recurring          = true
    include_subscription       = true
    include_support            = true
    include_tax                = true
    include_upfront            = true
    use_amortized              = false
    use_blended                = false
  }

  dynamic "notification" {
    for_each = [50, 80, 100]
    content {
      comparison_operator        = "GREATER_THAN"
      threshold                  = notification.value
      threshold_type             = "PERCENTAGE"
      notification_type          = "ACTUAL"
      subscriber_email_addresses = [var.correo_alertas]
    }
  }

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "FORECASTED"
    subscriber_email_addresses = [var.correo_alertas]
  }
}

# ---------------------------------------------------------------------------
# Recordatorios de salida: EventBridge Scheduler (14 M invocaciones gratis al
# mes) publica en un tema SNS (1.000 correos gratis al mes) en fechas fijas.
# La suscripcion por correo exige un clic de confirmacion en el primer
# correo que envia AWS; sin ese clic los recordatorios no llegan.
# ---------------------------------------------------------------------------
resource "aws_sns_topic" "avisos" {
  name = "nexus-plataforma-avisos"
}

resource "aws_sns_topic_subscription" "correo" {
  topic_arn = aws_sns_topic.avisos.arn
  protocol  = "email"
  endpoint  = var.correo_alertas
}

data "aws_iam_policy_document" "confianza_scheduler" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["scheduler.amazonaws.com"]
    }
  }
}

data "aws_iam_policy_document" "publicar_avisos" {
  statement {
    actions   = ["sns:Publish"]
    resources = [aws_sns_topic.avisos.arn]
  }
}

resource "aws_iam_role" "scheduler" {
  name               = "nexus-plataforma-recordatorios"
  assume_role_policy = data.aws_iam_policy_document.confianza_scheduler.json
}

resource "aws_iam_role_policy" "scheduler" {
  name   = "publicar-avisos"
  role   = aws_iam_role.scheduler.id
  policy = data.aws_iam_policy_document.publicar_avisos.json
}

resource "aws_scheduler_schedule" "recordatorio" {
  for_each = var.recordatorios

  name                         = "nexus-recordatorio-${replace(substr(each.key, 0, 10), "-", "")}"
  description                  = each.value
  schedule_expression          = "at(${each.key})"
  schedule_expression_timezone = "UTC"
  state                        = "ENABLED"

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = aws_sns_topic.avisos.arn
    role_arn = aws_iam_role.scheduler.arn
    input    = each.value # texto plano: es el cuerpo del correo
  }
}
