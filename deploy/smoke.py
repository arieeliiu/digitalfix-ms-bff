"""Demostración real. Tokens solo en memoria; crea una orden de prueba persistente."""
import getpass
import json
import urllib.error
import urllib.request

BASE = "https://9ijsvq2s6j.execute-api.us-east-1.amazonaws.com"


def call(method, path, expected, token=None, body=None, headers=None):
    request_headers = {"Origin": "http://localhost", **(headers or {})}
    if token:
        request_headers["Authorization"] = "Bearer " + token
    data = None
    if body is not None:
        data = json.dumps(body).encode()
        request_headers["Content-Type"] = "application/json"
    request = urllib.request.Request(BASE + path, method=method, headers=request_headers, data=data)
    try:
        response = urllib.request.urlopen(request, timeout=30)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        status, raw, returned_headers = response.status, response.read(), response.headers
    if status != expected:
        raise SystemExit(f"FALLO {method} {path}: {status}, esperado {expected}")
    print(f"OK {method} {path}: {status}")
    return json.loads(raw) if raw else None, returned_headers


def main():
    call("GET", "/api/perfil", 401)
    # AWS puede responder 200 o 204 al preflight; se acepta cualquier 2xx.
    request = urllib.request.Request(BASE + "/api/workorders", method="OPTIONS", headers={
        "Origin": "http://localhost", "Access-Control-Request-Method": "POST",
        "Access-Control-Request-Headers": "authorization,content-type"})
    with urllib.request.urlopen(request, timeout=30) as response:
        assert 200 <= response.status < 300
        assert response.headers.get("Access-Control-Allow-Origin") == "http://localhost"
        assert {"get", "post", "options"} <= set(response.headers.get("Access-Control-Allow-Methods", "").lower().replace(" ", "").split(","))
        assert {"authorization", "content-type"} <= set(response.headers.get("Access-Control-Allow-Headers", "").lower().replace(" ", "").split(","))
    print("OK preflight sin JWT y CORS")
    token = getpass.getpass("Access token de Cliente A (sin Bearer): ")
    call("GET", "/api/perfil", 200, token)
    services, _ = call("GET", "/api/catalog/services", 200, token)
    if not services:
        raise SystemExit("Preparar al menos un servicio en Catalog.")
    body = {"servicioId": services[0]["id"], "descripcion": "Prueba integración DigitalFix", "direccion": "Dirección de prueba"}
    order, _ = call("POST", "/api/workorders", 201, token, body)
    oid = input("oid de Cliente A verificado en Entra: ").strip()
    assert order["solicitanteId"] == oid
    order_id = order["id"]
    assert order["estado"] == "CREADA" and order["fechaCreacion"]
    call("GET", f"/api/workorders/{order_id}", 200, token)
    orders, _ = call("GET", "/api/workorders", 200, token)
    assert any(o["id"] == order_id for o in orders)
    assert all(o["solicitanteId"] == oid for o in orders)
    missing = max(s["id"] for s in services) + 1000000
    call("POST", "/api/workorders", 400, token, {**body, "servicioId": missing})
    other = getpass.getpass("Access token de Cliente B (otro usuario): ")
    call("GET", f"/api/workorders/{order_id}", 404, other)
    other_orders, _ = call("GET", "/api/workorders", 200, other)
    assert all(o["id"] != order_id for o in other_orders)
    no_role = getpass.getpass("Access token válido con scope pero sin rol DigitalFix: ")
    call("GET", "/api/catalog/services", 403, no_role)
    input("En EC2 ejecuta docker compose restart workorders; espera a que arranque y pulsa Enter: ")
    recovered, _ = call("GET", f"/api/workorders/{order_id}", 200, token)
    assert recovered == order
    print(f"OK: orden {order_id} conservada tras reiniciar Workorders. No se borró la evidencia.")


if __name__ == "__main__":
    main()
