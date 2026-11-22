# 개발 앱의 Docker 이미지를 저장할 ECR 저장소를 만듭니다.
resource "aws_ecr_repository" "app" {
  name                 = "${local.name}-app"
  image_tag_mutability = "IMMUTABLE"
  force_delete         = false

  # ECR에 저장된 이미지 데이터를 AES-256으로 암호화합니다.
  encryption_configuration {
    encryption_type = "AES256"
  }
}

# 최근 이미지 10개를 유지하고 오래된 이미지를 만료 대상으로 지정합니다.
resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep the most recent 10 deployment images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })
}

# AWS가 GitHub Actions의 OIDC 토큰을 확인할 수 있도록 발급자를 등록합니다.
resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
}

# GitHub Actions가 개발 배포 역할을 인수할 수 있는 조건을 정의합니다.
data "aws_iam_policy_document" "github_deploy_trust" {
  # OIDC 토큰을 제출해 STS 임시 자격 증명을 얻을 수 있도록 허용합니다.
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    # 위에 등록한 GitHub 토큰 발급자를 신뢰 주체로 지정합니다.
    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }
    # AWS STS를 대상으로 발급된 토큰만 받습니다.
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }
    # GitHub의 ID 기반 OIDC 주체로 KEEPIT-BE 저장소의 develop 브랜치를 확인합니다.
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:ITs-TIME-KEEPIT@342240231/KEEPIT-BE@1418129186:ref:refs/heads/develop"]
    }
  }
}

# GitHub Actions가 위 신뢰 정책에 따라 인수하는 개발 배포 역할을 만듭니다.
resource "aws_iam_role" "github_deploy" {
  name               = "${local.name}-github-deploy"
  assume_role_policy = data.aws_iam_policy_document.github_deploy_trust.json
}

# GitHub Secret을 SSM에 전송하기 전에 암호화할 개발 전용 대칭 키를 만듭니다.
resource "aws_kms_key" "deploy_env" {
  description         = "Encrypt KEEPIT dev dotenv during GitHub Actions deployments"
  enable_key_rotation = true
}

# 배포 스크립트가 키 ID를 하드코딩하지 않고 동일한 키를 찾게 합니다.
resource "aws_kms_alias" "deploy_env" {
  name          = "alias/${local.name}-deploy-env"
  target_key_id = aws_kms_key.deploy_env.key_id
}

# 개발 배포 역할에 이미지 업로드와 SSM 배포에 필요한 권한을 정의합니다.
data "aws_iam_policy_document" "github_deploy" {
  # GitHub Actions가 ECR 로그인 토큰을 발급받을 수 있게 합니다.
  statement {
    sid       = "EcrLogin"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }
  # 개발 ECR 저장소에서 이미지 태그를 조회하고 이미지 레이어를 업로드할 수 있게 합니다.
  statement {
    sid = "PushImage"
    actions = [
      "ecr:BatchCheckLayerAvailability", "ecr:BatchGetImage", "ecr:CompleteLayerUpload",
      "ecr:DescribeImages", "ecr:InitiateLayerUpload", "ecr:PutImage", "ecr:UploadLayerPart",
    ]
    resources = [aws_ecr_repository.app.arn]
  }
  # 배포 스크립트가 대상 EC2를 찾을 수 있도록 인스턴스 조회를 허용합니다.
  statement {
    sid       = "FindDeploymentInstance"
    actions   = ["ec2:DescribeInstances"]
    resources = ["*"]
  }
  # 개발 EC2에 지정된 SSM 문서로 배포 명령을 보낼 수 있게 합니다.
  statement {
    sid       = "SendDeploymentCommand"
    actions   = ["ssm:SendCommand"]
    resources = [aws_instance.app.arn, "arn:aws:ssm:${local.aws_region}::document/AWS-RunShellScript"]
  }
  # GitHub Actions가 SSM 명령의 종료 상태와 출력을 확인할 수 있게 합니다.
  statement {
    sid       = "ReadDeploymentResult"
    actions   = ["ssm:GetCommandInvocation"]
    resources = ["*"]
  }
  # GitHub는 개발 환경 파일을 암호화할 권한으로 암호문을 생성합니다.
  statement {
    sid       = "EncryptDeploymentEnvironment"
    actions   = ["kms:Encrypt"]
    resources = [aws_kms_key.deploy_env.arn]

    condition {
      test     = "StringEquals"
      variable = "kms:EncryptionContext:Application"
      values   = [local.name]
    }
  }
}

# 배포 권한 정책을 GitHub Actions용 IAM 역할에 연결합니다.
resource "aws_iam_role_policy" "github_deploy" {
  name   = "deploy-to-ecr-and-ssm"
  role   = aws_iam_role.github_deploy.id
  policy = data.aws_iam_policy_document.github_deploy.json
}

# 개발 EC2가 ECR에서 앱 이미지와 레이어를 읽는 권한을 정의합니다.
data "aws_iam_policy_document" "ecr_pull" {
  # EC2가 ECR에 로그인하는 데 필요한 인증 토큰 발급을 허용합니다.
  statement {
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }
  # 이미지 읽기 권한을 개발 앱의 ECR 저장소에 한정합니다.
  statement {
    actions = [
      "ecr:BatchCheckLayerAvailability", "ecr:BatchGetImage", "ecr:GetDownloadUrlForLayer",
    ]
    resources = [aws_ecr_repository.app.arn]
  }
}

# ECR 이미지 다운로드 정책을 개발 EC2의 IAM 역할에 연결합니다.
resource "aws_iam_role_policy" "ecr_pull" {
  name   = "pull-application-image"
  role   = aws_iam_role.app.id
  policy = data.aws_iam_policy_document.ecr_pull.json
}

# EC2는 개발 배포 키와 컨텍스트로 전달된 환경 파일을 복호화합니다.
data "aws_iam_policy_document" "deploy_env_decrypt" {
  statement {
    actions   = ["kms:Decrypt"]
    resources = [aws_kms_key.deploy_env.arn]

    condition {
      test     = "StringEquals"
      variable = "kms:EncryptionContext:Application"
      values   = [local.name]
    }
  }
}

# SSM 배포 명령이 EC2 인스턴스 역할을 사용해 환경 파일을 복호화하게 합니다.
resource "aws_iam_role_policy" "deploy_env_decrypt" {
  name   = "decrypt-deployment-environment"
  role   = aws_iam_role.app.id
  policy = data.aws_iam_policy_document.deploy_env_decrypt.json
}
