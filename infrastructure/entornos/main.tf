provider "aws" {
  region = "us-east-1"
}

# SCRUM-1118: Generación de llave SSH asimétrica para el pipeline
resource "tls_private_key" "pipeline_key" {
  algorithm = "RSA"
  rsa_bits  = 4096
}

resource "aws_key_pair" "deployer" {
  key_name   = "github-actions-key"
  public_key = tls_private_key.pipeline_key.public_key_openssh
}

# SCRUM-1117: Firewall (Security Group) bloqueando acceso manual
resource "aws_security_group" "plataforma_sg" {
  name        = "bloqueo-produccion-sg"
  description = "Permitir trafico web y restringir SSH"

  # Tráfico web abierto para los clientes del juego
  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # SSH bloqueado al público general (Restringido al pipeline)
  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    # cidr_blocks = ["IP_DE_GITHUB_ACTIONS/32"] # TODO: Reemplazar con IP estática del runner
    cidr_blocks = ["0.0.0.0/0"] # Abierto solo temporalmente para validación
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# Salida de la llave privada (marcada como sensible)
output "llave_privada_pipeline" {
  value     = tls_private_key.pipeline_key.private_key_pem
  sensitive = true
}
