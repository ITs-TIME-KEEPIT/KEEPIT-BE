# 애플리케이션 파일을 저장할 S3 버킷을 만듭니다.
resource "aws_s3_bucket" "files" {
  bucket        = local.bucket_name
  force_destroy = false

  # Terraform이 실수로 파일 버킷을 삭제하거나 교체하는 것을 막습니다.
  lifecycle {
    prevent_destroy = true
  }
}

# 공개 ACL과 공개 버킷 정책을 통한 접근을 차단합니다.
resource "aws_s3_bucket_public_access_block" "files" {
  bucket                  = aws_s3_bucket.files.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# 객체 소유권을 버킷 소유자에게 통일합니다.
resource "aws_s3_bucket_ownership_controls" "files" {
  bucket = aws_s3_bucket.files.id

  # 객체 ACL을 비활성화하고 정책으로 접근을 관리합니다.
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

# 버킷에 새로 저장하는 객체의 기본 암호화를 설정합니다.
resource "aws_s3_bucket_server_side_encryption_configuration" "files" {
  bucket = aws_s3_bucket.files.id

  # SSE-S3를 기본 암호화 규칙으로 적용합니다.
  rule {
    # S3가 관리하는 키로 저장 데이터를 암호화합니다.
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# 객체를 덮어쓸 때 이전 버전을 보관할 수 있도록 버전 관리를 설정합니다.
resource "aws_s3_bucket_versioning" "files" {
  bucket = aws_s3_bucket.files.id

  # 버전 관리 상태를 활성화로 지정합니다.
  versioning_configuration {
    status = "Enabled"
  }
}

# 이전 객체 버전과 끝나지 않은 멀티파트 업로드를 주기적으로 정리합니다.
resource "aws_s3_bucket_lifecycle_configuration" "files" {
  bucket = aws_s3_bucket.files.id

  depends_on = [aws_s3_bucket_versioning.files]

  # 한 규칙에서 이전 버전 만료와 미완료 업로드 취소를 관리합니다.
  rule {
    id     = "limit-old-versions-and-incomplete-uploads"
    status = "Enabled"
    # 접두사나 태그 조건 없이 버킷 전체에 적용합니다.
    filter {}

    # 객체가 이전 버전이 된 뒤 7일이 지나면 만료 대상으로 지정합니다.
    noncurrent_version_expiration {
      noncurrent_days = 7
    }

    # 시작한 지 1일이 지난 미완료 멀티파트 업로드를 취소합니다.
    abort_incomplete_multipart_upload {
      days_after_initiation = 1
    }
  }
}

# 암호화되지 않은 요청을 차단하는 버킷 정책을 생성합니다.
data "aws_iam_policy_document" "files_tls" {
  # 조건과 일치하는 요청에 대해 버킷 및 객체 작업을 명시적으로 거부합니다.
  statement {
    sid       = "DenyInsecureTransport"
    effect    = "Deny"
    actions   = ["s3:*"]
    resources = [aws_s3_bucket.files.arn, "${aws_s3_bucket.files.arn}/*"]

    # 거부 규칙은 요청 주체 전체에 적용됩니다.
    principals {
      type        = "*"
      identifiers = ["*"]
    }

    # 전송 구간이 암호화되지 않은 요청에만 이 거부 규칙을 적용합니다.
    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}

# HTTPS 강제 정책을 파일 버킷에 적용합니다.
resource "aws_s3_bucket_policy" "files" {
  bucket = aws_s3_bucket.files.id
  policy = data.aws_iam_policy_document.files_tls.json
}
