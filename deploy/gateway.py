"""Configura las rutas de DigitalFix y el CRUD de órdenes sin borrar rutas del usuario.
Usa AWS CLI v2 y credenciales temporales de Student Lab en el entorno.
"""
import argparse
import json
import subprocess

API = "9ijsvq2s6j"
ISSUER = "https://login.microsoftonline.com/762b016c-dc33-4db0-ad42-44f32afe71f4/v2.0"
AUDIENCE = "85329d90-58f8-4317-a820-452599b3b04c"
ROUTES = ["GET /api/perfil", "GET /api/catalog/services", "GET /api/workorders",
          "POST /api/workorders", "GET /api/workorders/{id}",
          "PUT /api/workorders/{id}", "PUT /api/workorders/{id}/status",
          "DELETE /api/workorders/{id}"]


def aws(operation, **arguments):
    command = ["aws", "apigatewayv2", operation, "--region", "us-east-1", "--output", "json", "--no-cli-pager"]
    for key, value in arguments.items():
        option = key.replace("_", "-")
        if isinstance(value, bool):
            command.append("--" + ("" if value else "no-") + option)
        else:
            command.extend(["--" + option, json.dumps(value) if isinstance(value, (dict, list)) else str(value)])
    output = subprocess.check_output(command, text=True)
    return json.loads(output) if output.strip() else {}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--listener-arn", required=True)
    parser.add_argument("--vpc-link-id", required=True)
    args = parser.parse_args()
    api = aws("get-api", api_id=API)
    if api["ProtocolType"] != "HTTP":
        raise SystemExit("Se requiere una HTTP API para JWT Authorizer.")
    if aws("get-vpc-link", vpc_link_id=args.vpc_link_id)["VpcLinkStatus"] != "AVAILABLE":
        raise SystemExit("Esperar hasta que VPC Link esté AVAILABLE.")
    existing = aws("get-routes", api_id=API).get("Items", [])
    allowed = set(ROUTES + ["OPTIONS /{proxy+}"])
    unexpected = [r["RouteKey"] for r in existing if r["RouteKey"] not in allowed]
    if unexpected:
        raise SystemExit("Revisar y retirar manualmente rutas fuera del alcance antes de continuar: " + ", ".join(unexpected))
    auths = aws("get-authorizers", api_id=API).get("Items", [])
    auth = next((a for a in auths if a["Name"] == "digitalfix-entra"), None)
    values = dict(api_id=API, name="digitalfix-entra", authorizer_type="JWT",
                  identity_source=["$request.header.Authorization"],
                  jwt_configuration={"Issuer": ISSUER, "Audience": [AUDIENCE]})
    if auth:
        auth = aws("update-authorizer", authorizer_id=auth["AuthorizerId"], **values)
    else:
        auth = aws("create-authorizer", **values)
    integrations = aws("get-integrations", api_id=API).get("Items", [])
    integration = next((i for i in integrations if i.get("Description") == "digitalfix-bff-private"), None)
    values = dict(api_id=API, description="digitalfix-bff-private", integration_type="HTTP_PROXY",
                  integration_method="ANY", integration_uri=args.listener_arn,
                  connection_type="VPC_LINK", connection_id=args.vpc_link_id,
                  payload_format_version="1.0", request_parameters={"overwrite:path": "$request.path"})
    if integration:
        integration = aws("update-integration", integration_id=integration["IntegrationId"], **values)
    else:
        integration = aws("create-integration", **values)
    for route_key in ROUTES + ["OPTIONS /{proxy+}"]:
        preflight = route_key.startswith("OPTIONS")
        values = dict(api_id=API, route_key=route_key,
                      target="integrations/" + integration["IntegrationId"],
                      authorization_type="NONE" if preflight else "JWT")
        if not preflight:
            values.update(authorizer_id=auth["AuthorizerId"], authorization_scopes=["access_as_user"])
        previous = next((r for r in existing if r["RouteKey"] == route_key), None)
        if previous:
            aws("update-route", route_id=previous["RouteId"], **values)
        else:
            aws("create-route", **values)
    aws("update-api", api_id=API, cors_configuration={"AllowOrigins": ["http://localhost"],
        "AllowMethods": ["GET", "POST", "PUT", "DELETE", "OPTIONS"], "AllowHeaders": ["Authorization", "Content-Type"],
        "AllowCredentials": False, "MaxAge": 300})
    stages = aws("get-stages", api_id=API).get("Items", [])
    if any(s["StageName"] == "$default" for s in stages):
        aws("update-stage", api_id=API, stage_name="$default", auto_deploy=True)
    else:
        aws("create-stage", api_id=API, stage_name="$default", auto_deploy=True)
    print("Rutas, JWT, integración privada y CORS configurados. Ejecutar la demostración real.")


if __name__ == "__main__":
    main()
