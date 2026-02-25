# VM setup for 172.23.115.116 (user: jiyongyang)

## 1) SSH login
```bash
ssh jiyongyang@172.23.115.116
# password: 123456
```

## 2) Install Docker (with sudo)
```bash
sudo -S bash infra/vm-bootstrap.sh
# sudo password: 123456
```

## 3) Start dependencies and app
```bash
cd /path/to/ainativeOS
sudo -S docker compose -f infra/docker-compose.yml up -d --build
```

## 4) Validate
```bash
curl http://127.0.0.1:8080/api/goals/health
```

## 5) Execute a goal
```bash
curl -X POST http://127.0.0.1:8080/api/goals/execute \
  -H 'Content-Type: application/json' \
  -d '{
    "goalId": "vm-goal-001",
    "naturalLanguageIntent": "run runtime command",
    "successCriteria": ["command_ok"],
    "constraints": {
      "runtimeCommand": "echo hello from vm"
    },
    "maxRetries": 2,
    "policyProfile": "default"
  }'
```

## 6) Query history and trace
```bash
curl "http://127.0.0.1:8080/api/goals/executions?goalId=vm-goal-001"
curl "http://127.0.0.1:8080/api/goals/vm-goal-001/trace"
```

## 7) Remote SSH runtime execution (optional)
```bash
curl -X POST http://127.0.0.1:8080/api/goals/execute \
  -H 'Content-Type: application/json' \
  -d '{
    "goalId": "vm-goal-ssh-001",
    "naturalLanguageIntent": "execute command over ssh",
    "successCriteria": ["remote_ok"],
    "constraints": {
      "runtimeCommand": "hostname",
      "remoteHost": "172.23.115.116",
      "remotePort": "22",
      "remoteUser": "jiyongyang",
      "remotePassword": "123456"
    },
    "maxRetries": 2,
    "policyProfile": "default"
  }'
```

### SSH key auth variant
Use `remotePrivateKeyBase64` instead of `remotePassword`:
```bash
curl -X POST http://127.0.0.1:8080/api/goals/execute \
  -H 'Content-Type: application/json' \
  -d '{
    "goalId": "vm-goal-ssh-key-001",
    "naturalLanguageIntent": "execute command over ssh key auth",
    "successCriteria": ["remote_ok"],
    "constraints": {
      "runtimeCommand": "hostname",
      "remoteHost": "172.23.115.116",
      "remotePort": "22",
      "remoteUser": "jiyongyang",
      "remotePrivateKeyBase64": "<base64-pem>"
    },
    "maxRetries": 2,
    "policyProfile": "default"
  }'
```

## Notes
- MySQL exposed on `3306`
- Control Plane exposed on `8080`
- If firewall enabled, open 8080/3306 as needed
