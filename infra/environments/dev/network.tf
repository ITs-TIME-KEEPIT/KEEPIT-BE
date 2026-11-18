# EC2와 RDS가 함께 사용하는 개발용 사설 네트워크를 만듭니다.
resource "aws_vpc" "main" {
  cidr_block           = "10.42.0.0/16"
  enable_dns_support   = true
  enable_dns_hostnames = true
  tags                 = { Name = local.name }
}

# 퍼블릭 서브넷의 인터넷 경로가 사용할 게이트웨이를 VPC에 연결합니다.
resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id
  tags   = { Name = local.name }
}

# 개발 EC2를 배치할 서브넷을 만듭니다. 공인 IP는 EC2 설정에서 개별 할당합니다.
resource "aws_subnet" "public" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = "10.42.0.0/24"
  availability_zone       = local.availability_zone
  map_public_ip_on_launch = false
  tags                    = { Name = "${local.name}-public" }
}

# RDS 서브넷 그룹에 넣을 격리 서브넷 두 개를 서로 다른 가용 영역에 만듭니다.
resource "aws_subnet" "database" {
  count = 2

  vpc_id                  = aws_vpc.main.id
  cidr_block              = cidrsubnet(aws_vpc.main.cidr_block, 8, count.index + 10)
  availability_zone       = data.aws_availability_zones.available.names[count.index]
  map_public_ip_on_launch = false
  tags                    = { Name = "${local.name}-db-${count.index + 1}" }
}

# EC2 서브넷에 연결할 라우팅 테이블을 만듭니다.
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id
  tags   = { Name = "${local.name}-public" }
}

# 퍼블릭 서브넷의 기본 IPv4 경로를 인터넷 게이트웨이로 보냅니다.
resource "aws_route" "internet" {
  route_table_id         = aws_route_table.public.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.main.id
}

# 기본 인터넷 경로가 들어 있는 테이블을 EC2 서브넷에 적용합니다.
resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id
}

# DB용 테이블에는 VPC 내부 경로만 남기고 인터넷·NAT 경로를 추가하지 않습니다.
resource "aws_route_table" "database" {
  vpc_id = aws_vpc.main.id
  tags   = { Name = "${local.name}-db-isolated" }
}

# 두 RDS 서브넷에 같은 격리 라우팅 테이블을 적용합니다.
resource "aws_route_table_association" "database" {
  count = 2

  subnet_id      = aws_subnet.database[count.index].id
  route_table_id = aws_route_table.database.id
}

# Spring Boot가 실행되는 EC2에 연결할 보안 그룹을 만듭니다.
resource "aws_security_group" "app" {
  name_prefix = "${local.name}-app-"
  description = "Spring Boot ingress; administration through SSM, without SSH"
  vpc_id      = aws_vpc.main.id
}

# 허용할 IPv4 대역마다 개발 HTTP 8080 인바운드 규칙을 만듭니다.
resource "aws_vpc_security_group_ingress_rule" "http" {
  for_each = toset(["0.0.0.0/0"])

  security_group_id = aws_security_group.app.id
  cidr_ipv4         = each.value
  ip_protocol       = "tcp"
  from_port         = 8080
  to_port           = 8080
  description       = "Development Spring Boot HTTP; configure TLS before real users"
}

# EC2가 SSM·ECR·S3·RDS 등에 연결할 수 있도록 IPv4 아웃바운드를 허용합니다.
resource "aws_vpc_security_group_egress_rule" "app" {
  security_group_id = aws_security_group.app.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
  description       = "Package updates, image pulls, SSM, S3 and private RDS"
}

# RDS에 연결할 별도의 보안 그룹을 만듭니다.
resource "aws_security_group" "database" {
  name_prefix = "${local.name}-db-"
  description = "Only the application security group can connect to MySQL"
  vpc_id      = aws_vpc.main.id
}

# 앱 보안 그룹이 붙은 리소스에서 오는 MySQL 3306 연결만 RDS에 허용합니다.
resource "aws_vpc_security_group_ingress_rule" "mysql" {
  security_group_id            = aws_security_group.database.id
  referenced_security_group_id = aws_security_group.app.id
  ip_protocol                  = "tcp"
  from_port                    = 3306
  to_port                      = 3306
}
