#!/bin/bash
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive

# Small emergency swap on encrypted EBS; not a substitute for adequate RAM.
if [ ! -f /swapfile ]; then
  fallocate -l 1G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
fi
if ! swapon --show=NAME --noheadings | grep -qx /swapfile; then
  swapon /swapfile
fi
if ! grep -q '^/swapfile ' /etc/fstab; then
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

# Never include application secrets in user data or cloud-init logs.
apt-get update
apt-get -o DPkg::Lock::Timeout=120 install -y --no-install-recommends docker.io docker-compose-v2 curl unzip
apt-get clean
systemctl enable --now docker

curl --fail --silent --show-error --location https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip --output /tmp/awscliv2.zip
unzip -q /tmp/awscliv2.zip -d /tmp
/tmp/aws/install
rm -rf /tmp/aws /tmp/awscliv2.zip

if snap list amazon-ssm-agent >/dev/null 2>&1; then
  snap start --enable amazon-ssm-agent
else
  snap install amazon-ssm-agent --classic
  snap start --enable amazon-ssm-agent
fi

install -d -m 0750 -o ubuntu -g ubuntu /opt/keepit
install -d -m 0755 /etc/docker
cat > /etc/docker/daemon.json <<'JSON'
{
  "log-driver": "json-file",
  "log-opts": { "max-size": "10m", "max-file": "3" }
}
JSON
systemctl restart docker
docker compose version
touch /opt/keepit/bootstrap-complete

# Image build, application secrets, TLS and deployment are a later reviewed step.
