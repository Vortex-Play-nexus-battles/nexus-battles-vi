provider "aws" {
  region = "us-east-1"
}

# Buscar la imagen más reciente de Ubuntu 22.04
data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"] # Canonical
  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-amd64-server-*"]
  }
}

# SCRUM-1117: Firewall (Security Group)
resource "aws_security_group" "plataforma_sg" {
  name        = "bloqueo-produccion-sg"
  description = "Permitir trafico web y restringir SSH"

  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
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
resource "tls_private_key" "dev_key" {
  algorithm = "RSA"
  rsa_bits  = 4096
}
resource "aws_key_pair" "dev_deployer" {
  key_name   = "dev-key"
  public_key = tls_private_key.dev_key.public_key_openssh
}
resource "aws_instance" "dev" {
  ami                    = data.aws_ami.ubuntu.id
  instance_type = "t3.micro"
  key_name               = aws_key_pair.dev_deployer.key_name
  vpc_security_group_ids = [aws_security_group.plataforma_sg.id]
  user_data              = "#!/bin/bash\napt-get update -y\napt-get install -y docker.io docker-compose-v2\nusermod -aG docker ubuntu"
  tags = { Name = "nexus-dev" }
}

# ==========================================
# ENTORNO: PRUEBAS (TEST)
# ==========================================
resource "tls_private_key" "test_key" {
  algorithm = "RSA"
  rsa_bits  = 4096
}
resource "aws_key_pair" "test_deployer" {
  key_name   = "test-key"
  public_key = tls_private_key.test_key.public_key_openssh
}
resource "aws_instance" "test" {
  ami                    = data.aws_ami.ubuntu.id
  instance_type = "t3.micro"
  key_name               = aws_key_pair.test_deployer.key_name
  vpc_security_group_ids = [aws_security_group.plataforma_sg.id]
  user_data              = "#!/bin/bash\napt-get update -y\napt-get install -y docker.io docker-compose-v2\nusermod -aG docker ubuntu"
  tags = { Name = "nexus-test" }
}

# ==========================================
# ENTORNO: PRODUCCIÓN (PROD)
# ==========================================
resource "tls_private_key" "prod_key" {
  algorithm = "RSA"
  rsa_bits  = 4096
}
resource "aws_key_pair" "prod_deployer" {
  key_name   = "prod-key"
  public_key = tls_private_key.prod_key.public_key_openssh
}
resource "aws_instance" "prod" {
  ami                    = data.aws_ami.ubuntu.id
  instance_type = "t3.micro"
  key_name               = aws_key_pair.prod_deployer.key_name
  vpc_security_group_ids = [aws_security_group.plataforma_sg.id]
  user_data              = "#!/bin/bash\napt-get update -y\napt-get install -y docker.io docker-compose-v2\nusermod -aG docker ubuntu"
  tags = { Name = "nexus-prod" }
}

# ==========================================
# OUTPUTS
# ==========================================
output "ip_dev" {
  value = aws_instance.dev.public_ip
}
output "llave_dev" {
  value     = tls_private_key.dev_key.private_key_pem
  sensitive = true
}

output "ip_test" {
  value = aws_instance.test.public_ip
}
output "llave_test" {
  value     = tls_private_key.test_key.private_key_pem
  sensitive = true
}

output "ip_prod" {
  value = aws_instance.prod.public_ip
}
output "llave_prod" {
  value     = tls_private_key.prod_key.private_key_pem
  sensitive = true
}
