# 이 개발 환경에 사용할 Terraform 버전, Provider, 상태 저장소를 지정합니다.
terraform {
  required_version = ">= 1.11.0, < 2.0.0"

  # AWS 리소스에 사용할 Provider의 배포처와 허용 버전 범위를 지정합니다.
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }

  # 단독 검토 시 상태 파일을 로컬에 저장합니다. 팀에서 공유할 때는 잠금이 있는 원격 상태 저장소로 이전합니다.
  backend "local" {
    path = "terraform.tfstate"
  }
}
