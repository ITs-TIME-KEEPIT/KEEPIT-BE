# 서로 다른 가용 영역에 있는 격리 서브넷 두 개를 RDS 배치에 사용할 그룹으로 묶습니다.
resource "aws_db_subnet_group" "main" {
  name       = local.name
  subnet_ids = aws_subnet.database[*].id
}

# MySQL 8.4에 적용할 데이터베이스 설정을 정의합니다.
resource "aws_db_parameter_group" "mysql" {
  name_prefix = "${local.name}-mysql84-"
  family      = "mysql8.4"

  # 인증 정보와 쿼리가 암호화된 연결을 통해서만 오가도록 TLS를 요구합니다.
  parameter {
    name  = "require_secure_transport"
    value = "1"
  }
}

# EC2와 같은 가용 영역에 비공개 MySQL RDS 한 대를 생성합니다.
resource "aws_db_instance" "main" {
  identifier     = local.name
  engine         = "mysql"
  engine_version = "8.4"
  instance_class = "db.t4g.micro"

  allocated_storage     = 20
  max_allocated_storage = 0 # No unreviewed automatic storage growth; monitor free space.
  storage_type          = "gp3"
  storage_encrypted     = true

  db_name                     = "keepit"
  username                    = "keepit_admin"
  manage_master_user_password = true
  db_subnet_group_name        = aws_db_subnet_group.main.name
  parameter_group_name        = aws_db_parameter_group.mysql.name
  vpc_security_group_ids      = [aws_security_group.database.id]
  availability_zone           = local.availability_zone
  multi_az                    = false
  publicly_accessible         = false
  port                        = 3306
  ca_cert_identifier          = "rds-ca-rsa2048-g1"

  backup_retention_period      = 1
  backup_window                = "18:00-18:30"
  maintenance_window           = "sun:19:00-sun:19:30"
  auto_minor_version_upgrade   = true
  allow_major_version_upgrade  = false
  engine_lifecycle_support     = "open-source-rds-extended-support-disabled"
  performance_insights_enabled = false
  monitoring_interval          = 0
  copy_tags_to_snapshot        = true
  deletion_protection          = true
  skip_final_snapshot          = false
  final_snapshot_identifier    = "${local.name}-final"
  delete_automated_backups     = false
  apply_immediately            = false

  # Terraform이 실수로 DB를 삭제하거나 교체하는 것을 막습니다.
  lifecycle {
    prevent_destroy = true
  }
}
