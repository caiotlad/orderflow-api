#!/usr/bin/env python3
"""Demonstra o fluxo da API usando apenas a biblioteca padrão do Python."""
import argparse
import concurrent.futures
import getpass
import json
import os
import time
import urllib.error
import urllib.request
import uuid


class Api:
    def __init__(self, base_url):
        self.base_url = base_url.rstrip("/")
        self.token = None

    def request(self, method, path, body=None, key=None):
        headers = {"Accept": "application/json"}
        if self.token:
            headers["Authorization"] = "Bearer " + self.token
        if key:
            headers["Idempotency-Key"] = str(key)
        data = None
        if body is not None:
            data = json.dumps(body).encode("utf-8")
            headers["Content-Type"] = "application/json"
        request = urllib.request.Request(self.base_url + path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=20) as response:
                return response.status, json.load(response), response.headers
        except urllib.error.HTTPError as error:
            return error.code, json.load(error), error.headers

    def expect(self, method, path, body=None, key=None, status=200):
        actual, payload, headers = self.request(method, path, body, key)
        if actual != status:
            raise RuntimeError(f"{method} {path}: esperado HTTP {status}, recebido {actual}: {payload}")
        return payload, headers


def check(condition, message):
    if not condition:
        raise RuntimeError(message)


def wait_for_health(api, seconds=60):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        try:
            status, payload, _ = api.request("GET", "/actuator/health")
            if status == 200 and payload.get("status") == "UP":
                return
        except (urllib.error.URLError, TimeoutError, OSError):
            pass
        time.sleep(1)
    raise RuntimeError("A aplicação não ficou saudável dentro do prazo.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--username", default=os.getenv("DEMO_USERNAME", "admin"))
    parser.add_argument("--wait", action="store_true", help="Aguardar o health check antes da demonstração")
    args = parser.parse_args()
    api = Api(args.base_url)
    if args.wait:
        wait_for_health(api)
    password = os.getenv("DEMO_PASSWORD") or getpass.getpass("Senha do administrador: ")
    login, _ = api.expect("POST", "/api/v1/auth/token", {"username": args.username, "password": password})
    api.token = login["accessToken"]

    def product(stock):
        created, _ = api.expect("POST", "/api/v1/products",
                                {"sku": "DEMO-" + uuid.uuid4().hex[:16], "name": "Café de demonstração", "price": 25.90},
                                status=201)
        api.expect("POST", f"/api/v1/products/{created['id']}/stock-movements",
                   {"type": "INBOUND", "quantity": stock, "reason": "Saldo da demonstração"}, status=201)
        return created["id"]

    product_id = product(10)
    body = {"customerName": "Cliente de demonstração", "items": [{"productId": product_id, "quantity": 2}]}
    key = uuid.uuid4()
    order, _ = api.expect("POST", "/api/v1/orders", body, key, 201)
    replay, headers = api.expect("POST", "/api/v1/orders", body, key, 201)
    check(order == replay and headers.get("Idempotency-Replayed") == "true", "O reenvio não preservou a resposta.")
    api.expect("POST", f"/api/v1/orders/{order['id']}/confirm")
    api.expect("POST", f"/api/v1/orders/{order['id']}/confirm")
    balance, _ = api.expect("GET", f"/api/v1/products/{product_id}")
    check(balance["onHand"] == 8 and balance["reserved"] == 0, "Saldo incorreto após confirmar.")
    print("Reserva, reenvio e confirmação: OK")

    cancel_body = {"customerName": "Pedido para cancelar", "items": [{"productId": product_id, "quantity": 3}]}
    cancel_order, _ = api.expect("POST", "/api/v1/orders", cancel_body, uuid.uuid4(), 201)
    api.expect("POST", f"/api/v1/orders/{cancel_order['id']}/cancel")
    api.expect("POST", f"/api/v1/orders/{cancel_order['id']}/cancel")
    balance, _ = api.expect("GET", f"/api/v1/products/{product_id}")
    check(balance["available"] == 8 and balance["reserved"] == 0, "Cancelamento não liberou a reserva.")
    print("Cancelamento e repetição do cancelamento: OK")

    last_unit = product(1)
    race_body = {"customerName": "Disputa pela última unidade", "items": [{"productId": last_unit, "quantity": 1}]}
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
        futures = [executor.submit(api.request, "POST", "/api/v1/orders", race_body, uuid.uuid4()) for _ in range(2)]
        results = [future.result(timeout=30) for future in futures]
    check(sorted(result[0] for result in results) == [201, 409], "A disputa não terminou com uma compra e uma recusa.")
    balance, _ = api.expect("GET", f"/api/v1/products/{last_unit}")
    check(balance["onHand"] == 1 and balance["reserved"] == 1 and balance["available"] == 0,
          "O saldo da última unidade ficou inconsistente.")
    winning_order = next(payload for status, payload, _ in results if status == 201)
    api.expect("POST", f"/api/v1/orders/{winning_order['id']}/cancel")
    print("Duas compras pela última unidade: HTTP 201 + HTTP 409. Estoque consistente.")
    print("Demonstração concluída. Os produtos e pedidos ficam registrados para consulta.")


if __name__ == "__main__":
    main()
