from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Dict, List, Optional

import requests


@dataclass
class ClientConfig:
    base_url: str = "http://127.0.0.1:8080"
    timeout_seconds: int = 20
    max_retries: int = 1


class AiNativeOsClient:
    def __init__(self, config: Optional[ClientConfig] = None):
        self.config = config or ClientConfig()
        self._session = requests.Session()

    def plan(self, goal_spec: Dict[str, Any]) -> Dict[str, Any]:
        return self._post("/api/goals/plan", goal_spec)

    def execute(self, goal_spec: Dict[str, Any]) -> Dict[str, Any]:
        return self._post("/api/goals/execute", goal_spec)

    def trace(self, goal_id: str) -> List[Dict[str, Any]]:
        return self._get(f"/api/goals/{goal_id}/trace")

    def executions(self, goal_id: Optional[str] = None) -> List[Dict[str, Any]]:
        path = "/api/goals/executions"
        if goal_id:
            path += f"?goalId={goal_id}"
        return self._get(path)

    def capabilities(self) -> List[Dict[str, Any]]:
        return self._get("/api/goals/capabilities")

    def plugins(self) -> List[Dict[str, Any]]:
        return self._get("/api/goals/plugins")

    def templates(self) -> List[Dict[str, Any]]:
        return self._get("/api/goals/templates")

    def execute_template(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        return self._post("/api/goals/templates/execute", payload)

    def health(self) -> Dict[str, Any]:
        return self._get("/api/goals/health")

    def _post(self, path: str, payload: Dict[str, Any]) -> Dict[str, Any]:
        return self._request("POST", path, json=payload)

    def _get(self, path: str):
        return self._request("GET", path)

    def _request(self, method: str, path: str, **kwargs):
        url = f"{self.config.base_url.rstrip('/')}{path}"
        last_error = None
        for _ in range(self.config.max_retries + 1):
            try:
                response = self._session.request(method, url, timeout=self.config.timeout_seconds, **kwargs)
                if 200 <= response.status_code < 300:
                    return response.json()
                raise RuntimeError(f"HTTP {response.status_code}: {response.text}")
            except Exception as exc:  # noqa: BLE001
                last_error = exc
        raise RuntimeError("SDK request failed after retries") from last_error
