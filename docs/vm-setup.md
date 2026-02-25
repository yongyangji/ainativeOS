# VM setup for 172.23.115.116 (user: jiyongyang)

## 1) SSH login
`ash
ssh jiyongyang@172.23.115.116
# password: 123456
`

## 2) Install Docker (with sudo)
`ash
sudo -S bash infra/vm-bootstrap.sh
# sudo password: 123456
`

## 3) Start dependencies and app
`ash
cd /path/to/ainativeOS
sudo -S docker compose -f infra/docker-compose.yml up -d --build
`

## 4) Validate
`ash
curl http://127.0.0.1:8080/api/goals/health
`

## Notes
- MySQL exposed on 3306
- Control Plane exposed on 8080
- If firewall enabled, open 8080/3306 as needed
