provider "aws" {
  region              = var.aws_region
  allowed_account_ids = [var.aws_account_id]

  # Credentials come from AWS_PROFILE; never put access keys in Terraform.
  default_tags {
    tags = {
      Project     = "KEEPIT"
      Environment = "dev"
      ManagedBy   = "Terraform"
    }
  }
}

data "aws_caller_identity" "current" {}

data "aws_availability_zones" "available" {
  state = "available"

  filter {
    name   = "zone-type"
    values = ["availability-zone"]
  }
}

data "aws_ssm_parameter" "ubuntu_ami" {
  name = "/aws/service/canonical/ubuntu/server/24.04/stable/current/amd64/hvm/ebs-gp3/ami-id"
}

data "aws_ami" "ubuntu" {
  owners = ["099720109477"] # Canonical

  filter {
    name   = "image-id"
    values = [data.aws_ssm_parameter.ubuntu_ami.value]
  }

  filter {
    name   = "architecture"
    values = ["x86_64"]
  }
}

locals {
  name              = "keepit-dev"
  availability_zone = data.aws_availability_zones.available.names[0]
  bucket_name       = "${local.name}-${data.aws_caller_identity.current.account_id}-${var.aws_region}-files"
}
