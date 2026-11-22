# EC2 서비스에만 이 서버의 IAM 역할 사용을 허용하는 신뢰 정책을 정의합니다.
data "aws_iam_policy_document" "ec2_trust" {
  # 허용할 동작은 STS를 통한 역할 인수입니다.
  statement {
    actions = ["sts:AssumeRole"]

    # 역할을 인수할 AWS 서비스 주체를 EC2로 제한합니다.
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

# 개발 EC2에서 AWS API를 호출하는 데 사용할 역할을 만듭니다.
resource "aws_iam_role" "app" {
  name               = "${local.name}-ec2"
  assume_role_policy = data.aws_iam_policy_document.ec2_trust.json
}

# SSM Agent가 개발 EC2를 등록하고 SSM 명령을 수신하는 데 필요한 관리형 정책을 붙입니다.
resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.app.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# 개발 파일 버킷에 대한 EC2 역할의 객체 접근 정책을 정의합니다.
data "aws_iam_policy_document" "files_access" {
  # 해당 버킷의 객체 읽기·쓰기·삭제만 허용합니다.
  statement {
    sid       = "ReadWriteApplicationObjects"
    actions   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
    resources = ["${aws_s3_bucket.files.arn}/*"]
  }
}

# 객체 접근 정책을 EC2 IAM 역할에 연결합니다.
resource "aws_iam_role_policy" "files_access" {
  name   = "application-files"
  role   = aws_iam_role.app.id
  policy = data.aws_iam_policy_document.files_access.json
}

# IAM 역할을 EC2에 연결할 수 있도록 인스턴스 프로파일에 넣습니다.
resource "aws_iam_instance_profile" "app" {
  name = "${local.name}-ec2"
  role = aws_iam_role.app.name
}

# 퍼블릭 서브넷에 개발 EC2를 만들고 첫 부팅 때 서버 준비 스크립트를 실행합니다.
resource "aws_instance" "app" {
  ami                         = data.aws_ami.ubuntu.id
  instance_type               = "t3.micro"
  subnet_id                   = aws_subnet.public.id
  vpc_security_group_ids      = [aws_security_group.app.id]
  associate_public_ip_address = true
  iam_instance_profile        = aws_iam_instance_profile.app.name
  monitoring                  = false
  user_data                   = file("${path.module}/templates/bootstrap.sh")
  user_data_replace_on_change = true

  # CPU 크레딧이 소진되면 기본 성능으로 제한하는 표준 모드를 사용합니다.
  credit_specification {
    cpu_credits = "standard"
  }

  # IMDSv2 토큰을 요구하고 컨테이너에서 메타데이터에 접근할 수 있도록 응답 홉 한도를 설정합니다.
  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
    instance_metadata_tags      = "disabled"
  }

  # 운영체제와 Docker 이미지가 배포 중 공간 부족으로 실패하지 않도록 암호화된 20GiB gp3 루트 디스크를 설정합니다.
  root_block_device {
    volume_type           = "gp3"
    volume_size           = 20
    encrypted             = true
    delete_on_termination = true
    tags                  = { Name = "${local.name}-root" }
  }

  tags = { Name = local.name }

  # SSM 권한과 인터넷 연결에 필요한 네트워크 구성이 준비된 뒤 EC2를 생성합니다.
  depends_on = [
    aws_iam_role_policy_attachment.ssm,
    aws_route.internet,
    aws_route_table_association.public,
    aws_vpc_security_group_egress_rule.app,
  ]

  # AMI 조회 결과가 갱신돼도 기존 인스턴스를 자동으로 교체하지 않습니다.
  lifecycle {
    ignore_changes = [ami]
    # 선택한 이미지가 EBS 루트 디스크를 사용하는지 확인합니다.
    precondition {
      condition     = data.aws_ami.ubuntu.root_device_type == "ebs"
      error_message = "The selected AMI must have an EBS root disk."
    }
  }
}
