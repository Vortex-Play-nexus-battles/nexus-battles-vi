output "ip_publica" {
  description = "Valor del secreto DEPLOY_HOST_DEV (entorno dev de GitHub). IP elastica: no cambia al apagar y encender."
  value       = aws_eip.plataforma.public_ip
}

output "usuario_ssh" {
  description = "Valor del secreto SSH_USER_DEV."
  value       = "ubuntu"
}

output "parametro_llave_privada" {
  description = "Nombre del parametro SecureString con la llave privada (valor del secreto SSH_KEY_DEV). Se lee desde Systems Manager > Parameter Store con un usuario administrador; nunca se imprime aqui."
  value       = aws_ssm_parameter.llave_privada.name
}

output "instancia_id" {
  description = "Id de la instancia; lo usan las acciones start/stop de infra-dev.yml y Session Manager."
  value       = aws_instance.plataforma.id
}

output "tipo_instancia" {
  value = var.instance_type
}

output "costo_estimado_usd_mes_24x7" {
  description = "Estimacion con precios oficiales on-demand us-east-1 del 2026-09-15 (instancia + IPv4 + disco), 720 h/mes. Solo orientativo; el saldo real esta en Billing > Credits."
  value       = format("%.2f", (
    lookup({
      "t3.micro"       = 0.0104
      "t3.small"       = 0.0208
      "t4g.micro"      = 0.0084
      "t4g.small"      = 0.0168
      "c7i-flex.large" = 0.08479
      "m7i-flex.large" = 0.09576
    }, var.instance_type) * 720
    + 0.005 * 720
    + 0.08 * var.tamano_disco_gb
  ))
}
