# =============================================================================
# SUPERSEDED — NO APLICABLE. Se conserva solo como referencia histórica.
# =============================================================================
#
# Este archivo declara tres instancias `t3.micro` (`nexus-dev`, `nexus-test`,
# `nexus-prod`) con estado LOCAL y sin backend remoto. Corresponde al diseño
# inicial de tres entornos, anterior a la cuenta AWS con Free Plan.
#
# **Ningún workflow lo aplica.** `infra-dev.yml` solo opera sobre las dos
# carpetas de abajo, y ninguna de las tres instancias de aquí existe en la
# cuenta. No hacer `tofu/terraform apply` sobre esta carpeta: crearía
# infraestructura duplicada, con estado en el disco de quien la aplique.
#
# Lo que SÍ gobierna la infraestructura real (dos hosts, ambos `t3.small`,
# con el estado en S3 versionado y bloqueo):
#
#   infrastructure/entornos/plataforma/  -> nexus-plataforma-dev (35.168.124.119)
#   infrastructure/entornos/contenido/   -> nexus-contenido-dev  (34.193.90.11)
#
# El mapa completo de qué corre en cada host está en
# docs/arquitectura/README.md.
#
# Retirarlo del repositorio es una decisión pendiente de coordinación con los
# dueños de HU-CICD-002 / HU-POR-003 (Néstor y Santiago G.); hasta entonces se
# deja como está, marcado, para no perder el registro del diseño original.
# =============================================================================

provider "aws" {
  region = "us-east-1"
}

# Buscar dinámicamente la última imagen oficial de Ubuntu 22.04
data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"] # ID oficial de Canonical (creadores de Ubuntu)

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-amd64-server-*"]
  }
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

  # SSH restringido al pipeline (temporalmente abierto durante validación)
  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    # cidr_blocks = ["IP_DE_GITHUB_ACTIONS/32"] # TODO: Reemplazar con IP estática del runner
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# ==========================================
# ENTORNO: DESARROLLO (DEV)
# ==========================================
resource "aws_instance" "dev" {
  ami                    = data.aws_ami.ubuntu.id
  instance_type          = "t3.micro"
  key_name               = aws_key_pair.deployer.key_name
  vpc_security_group_ids = [aws_security_group.plataforma_sg.id]
  user_data              = "#!/bin/bash\napt-get update -y\napt-get install -y docker.io docker-compose-v2\nusermod -aG docker ubuntu"
  tags                   = { Name = "nexus-dev" }
}

# ==========================================
# ENTORNO: PRUEBAS (TEST)
# ==========================================
resource "aws_instance" "test" {
  ami                    = data.aws_ami.ubuntu.id
  instance_type          = "t3.micro"
  key_name               = aws_key_pair.deployer.key_name
  vpc_security_group_ids = [aws_security_group.plataforma_sg.id]
  user_data              = "#!/bin/bash\napt-get update -y\napt-get install -y docker.io docker-compose-v2\nusermod -aG docker ubuntu"
  tags                   = { Name = "nexus-test" }
}

# ==========================================
# ENTORNO: PRODUCCIÓN (PROD)
# ==========================================
resource "aws_instance" "prod" {
  ami                    = data.aws_ami.ubuntu.id
  instance_type          = "t3.micro"
  key_name               = aws_key_pair.deployer.key_name
  vpc_security_group_ids = [aws_security_group.plataforma_sg.id]
  user_data              = "#!/bin/bash\napt-get update -y\napt-get install -y docker.io docker-compose-v2\nusermod -aG docker ubuntu"
  tags                   = { Name = "nexus-prod" }
}

# ==========================================
# OUTPUTS
# ==========================================
output "ip_dev" {
  value = aws_instance.dev.public_ip
}

output "ip_test" {
  value = aws_instance.test.public_ip
}

output "ip_prod" {
  value = aws_instance.prod.public_ip
}

# Salida de la llave privada (marcada como sensible)
output "llave_privada_pipeline" {
  value     = tls_private_key.pipeline_key.private_key_pem
  sensitive = true
}
