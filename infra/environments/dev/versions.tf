terraform {
  required_version = ">= 1.11.0, < 2.0.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }

  # Review draft: single-operator local state. Migrate to locked S3 state before team use.
  backend "local" {
    path = "terraform.tfstate"
  }
}
