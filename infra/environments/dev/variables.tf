variable "aws_region" {
  description = "Reviewed deployment region. Seoul is the default for Korean users."
  type        = string
  default     = "ap-northeast-2"

  validation {
    condition     = contains(["ap-northeast-2", "us-east-1"], var.aws_region)
    error_message = "Choose ap-northeast-2 or us-east-1 and review regional pricing before applying."
  }
}

variable "aws_account_id" {
  description = "Expected AWS account; prevents accidentally applying to a different account."
  type        = string
  default     = "781953768324"

  validation {
    condition     = can(regex("^[0-9]{12}$", var.aws_account_id))
    error_message = "AWS account ID must contain 12 digits."
  }
}

variable "instance_type" {
  description = "t3.micro keeps current amd64 images compatible; do not use nano for the JVM."
  type        = string
  default     = "t3.micro"

  validation {
    condition     = contains(["t3.micro", "t3.small"], var.instance_type)
    error_message = "Use the reviewed x86 types t3.micro or t3.small. ARM needs an AMI and image change."
  }
}

variable "web_ingress_cidrs" {
  description = "IPv4 ranges allowed to reach Nginx. Public HTTP is for development only."
  type        = set(string)
  default     = ["0.0.0.0/0"]

  validation {
    condition     = alltrue([for cidr in var.web_ingress_cidrs : can(cidrnetmask(cidr))])
    error_message = "Each entry must be a valid IPv4 CIDR. Use [] for SSM-only access."
  }
}

variable "mysql_engine_version" {
  description = "MySQL 8.4 major version; AWS selects a supported minor release at creation."
  type        = string
  default     = "8.4"

  validation {
    condition     = can(regex("^8\\.4(\\.[0-9]+)?$", var.mysql_engine_version))
    error_message = "Use MySQL 8.4, matching the application's local database major version."
  }
}
