from ainativeos_sdk import AiNativeOsClient


def main() -> None:
    client = AiNativeOsClient()
    health = client.health()
    print("health:", health)

    result = client.execute_template(
        {
            "templateId": "tpl-inspect-runtime",
            "params": {
                "runtimeCommand": "echo hello-from-python-sdk"
            }
        }
    )
    print("template execution:", result)


if __name__ == "__main__":
    main()
