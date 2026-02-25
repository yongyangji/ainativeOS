# VM setup for 172.23.115.116

## 1) Install Docker on Ubuntu/Debian VM
```bash
apt-get update
apt-get install -y ca-certificates curl gnupg
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null
apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
systemctl enable docker
systemctl start docker
```

## 2) Start MySQL dependency
```bash
docker compose -f infra/docker-compose.yml up -d
```

## 3) Run application
```bash
export DB_HOST=127.0.0.1
export DB_PORT=3306
export DB_NAME=ainativeos
export DB_USER=ainativeos
export DB_PASSWORD=ainativeos_pwd
mvn spring-boot:run
```

## 4) Quick validation
```bash
curl -X POST http://127.0.0.1:8080/api/goals/health
```
