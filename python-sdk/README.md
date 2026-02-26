# Python SDK (ainativeos-sdk)

## Install

```bash
pip install -e .
```

## Usage

```python
from ainativeos_sdk import AiNativeOsClient

client = AiNativeOsClient()
plan = client.plan({
    "goalId": "goal-py-001",
    "naturalLanguageIntent": "run command",
    "successCriteria": ["ok"],
    "constraints": {"runtimeCommand": "echo hello"},
    "maxRetries": 2,
    "policyProfile": "default"
})
print(plan)
```
