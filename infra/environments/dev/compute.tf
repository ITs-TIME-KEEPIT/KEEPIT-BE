data "aws_iam_policy_document" "ec2_trust" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "app" {
  name               = "${local.name}-ec2"
  assume_role_policy = data.aws_iam_policy_document.ec2_trust.json
}

resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.app.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# Defined from this infrastructure's explicit bucket operations, not inferred from app code.
data "aws_iam_policy_document" "files_access" {
  statement {
    sid       = "ReadWriteApplicationObjects"
    actions   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
    resources = ["${aws_s3_bucket.files.arn}/*"]
  }
}

resource "aws_iam_role_policy" "files_access" {
  name   = "application-files"
  role   = aws_iam_role.app.id
  policy = data.aws_iam_policy_document.files_access.json
}

resource "aws_iam_instance_profile" "app" {
  name = "${local.name}-ec2"
  role = aws_iam_role.app.name
}

resource "aws_instance" "app" {
  ami                         = data.aws_ami.ubuntu.id
  instance_type               = var.instance_type
  subnet_id                   = aws_subnet.public.id
  vpc_security_group_ids      = [aws_security_group.app.id]
  associate_public_ip_address = true
  iam_instance_profile        = aws_iam_instance_profile.app.name
  monitoring                  = false
  user_data                   = file("${path.module}/templates/bootstrap.sh")
  user_data_replace_on_change = true

  credit_specification {
    cpu_credits = "standard"
  }

  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
    instance_metadata_tags      = "disabled"
  }

  root_block_device {
    volume_type           = "gp3"
    volume_size           = 8
    encrypted             = true
    delete_on_termination = true
    tags                  = { Name = "${local.name}-root" }
  }

  tags = { Name = local.name }

  depends_on = [
    aws_iam_role_policy_attachment.ssm,
    aws_route.internet,
    aws_route_table_association.public,
    aws_vpc_security_group_egress_rule.app,
  ]

  lifecycle {
    # Refreshing Canonical's latest AMI must not silently replace a working dev host.
    ignore_changes = [ami]
    precondition {
      condition     = data.aws_ami.ubuntu.root_device_type == "ebs"
      error_message = "The selected AMI must have an EBS root disk."
    }
  }
}
