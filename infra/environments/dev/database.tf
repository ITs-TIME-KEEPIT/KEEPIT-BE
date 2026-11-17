resource "aws_db_subnet_group" "main" {
  name       = local.name
  subnet_ids = aws_subnet.database[*].id
}

resource "aws_db_parameter_group" "mysql" {
  name_prefix = "${local.name}-mysql84-"
  family      = "mysql8.4"

  parameter {
    name  = "require_secure_transport"
    value = "1"
  }
}

resource "aws_db_instance" "main" {
  identifier     = local.name
  engine         = "mysql"
  engine_version = var.mysql_engine_version
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

  lifecycle {
    prevent_destroy = true
  }
}
