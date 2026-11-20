# AWS Provider의 리전을 지정하고, 인증된 계정이 KEEPIT 개발 계정인지 확인합니다.
provider "aws" {
  region              = local.aws_region
  allowed_account_ids = ["781953768324"]

  # Provider가 지원하는 리소스에 프로젝트·환경·관리 주체 태그를 공통 적용합니다.
  default_tags {
    tags = {
      Project     = "KEEPIT"
      Environment = "dev"
      ManagedBy   = "Terraform"
    }
  }
}

# 현재 AWS 자격 증명의 계정 ID를 조회합니다. 버킷 이름과 계정 출력에 사용합니다.
data "aws_caller_identity" "current" {}

# 서울 리전에서 사용 가능한 일반 가용 영역을 조회합니다.
data "aws_availability_zones" "available" {
  state = "available"

  # Local Zone 등 별도 유형의 영역을 조회 결과에서 제외합니다.
  filter {
    name   = "zone-type"
    values = ["availability-zone"]
  }
}

# Canonical 공개 SSM 파라미터에서 Ubuntu 24.04 amd64 AMI ID를 읽습니다.
data "aws_ssm_parameter" "ubuntu_ami" {
  name = "/aws/service/canonical/ubuntu/server/24.04/stable/current/amd64/hvm/ebs-gp3/ami-id"
}

# 조회한 ID에 해당하는 Canonical 소유의 EC2 이미지 정보를 가져옵니다.
data "aws_ami" "ubuntu" {
  owners = ["099720109477"] # Canonical

  # SSM 파라미터가 가리키는 이미지로 조회 범위를 좁힙니다.
  filter {
    name   = "image-id"
    values = [data.aws_ssm_parameter.ubuntu_ami.value]
  }

  # 개발 서버와 Docker 이미지에 맞춰 x86_64 AMI를 선택합니다.
  filter {
    name   = "architecture"
    values = ["x86_64"]
  }
}

# 리전·리소스 이름·기본 가용 영역·버킷 이름처럼 여러 파일에서 쓰는 값을 모읍니다.
locals {
  name              = "keepit-dev"
  aws_region        = "ap-northeast-2"
  availability_zone = data.aws_availability_zones.available.names[0]
  bucket_name       = "${local.name}-${data.aws_caller_identity.current.account_id}-${local.aws_region}-files"
}
