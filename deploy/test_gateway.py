"""Verifica configuración antes de ejecutar mutaciones reales en AWS."""
import contextlib
import io
import unittest
from unittest.mock import patch
import gateway


class GatewayTests(unittest.TestCase):
    def test_cli_boolean_flags_have_no_value(self):
        with patch("subprocess.check_output", return_value="{}") as execute:
            gateway.aws("update-stage", api_id="test", auto_deploy=True)
        command = execute.call_args.args[0]
        self.assertIn("--auto-deploy", command)
        self.assertNotIn("true", command)

    def run_config(self, existing):
        calls = []
        def fake(operation, **kwargs):
            calls.append((operation, kwargs))
            return {
                "get-api": {"ProtocolType": "HTTP"},
                "get-vpc-link": {"VpcLinkStatus": "AVAILABLE"},
                "get-routes": {"Items": existing},
                "get-authorizers": {"Items": []},
                "create-authorizer": {"AuthorizerId": "auth"},
                "get-integrations": {"Items": []},
                "create-integration": {"IntegrationId": "bff"},
                "get-stages": {"Items": [{"StageName": "$default"}]},
            }.get(operation, {})
        with patch.object(gateway, "aws", side_effect=fake), patch("sys.argv", [
            "gateway.py", "--listener-arn", "arn:listener", "--vpc-link-id", "link"
        ]), contextlib.redirect_stdout(io.StringIO()):
            gateway.main()
        return calls

    def test_five_protected_routes_preflight_and_private_target(self):
        calls = self.run_config([])
        routes = [args for op, args in calls if op == "create-route"]
        protected = [r for r in routes if r["authorization_type"] == "JWT"]
        self.assertEqual(set(gateway.ROUTES), {r["route_key"] for r in protected})
        self.assertTrue(all(r["authorization_scopes"] == ["access_as_user"] for r in protected))
        options = next(r for r in routes if r["route_key"].startswith("OPTIONS"))
        self.assertEqual("NONE", options["authorization_type"])
        self.assertNotIn("authorizer_id", options)
        self.assertNotIn("authorization_scopes", options)
        self.assertTrue(all(r["target"] == "integrations/bff" for r in routes))
        integration = next(a for op, a in calls if op == "create-integration")
        self.assertEqual("VPC_LINK", integration["connection_type"])
        self.assertEqual({"overwrite:path": "$request.path"}, integration["request_parameters"])
        cors = next(a["cors_configuration"] for op, a in calls if op == "update-api")
        self.assertEqual(["http://localhost"], cors["AllowOrigins"])

    def test_updates_existing_routes(self):
        calls = self.run_config([{"RouteKey": "GET /api/perfil", "RouteId": "existing"}])
        updates = [a for op, a in calls if op == "update-route"]
        self.assertEqual("existing", updates[0]["route_id"])
        self.assertFalse(any(op.startswith("delete") for op, _ in calls))

    def test_refuses_unreviewed_routes(self):
        with self.assertRaisesRegex(SystemExit, "Revisar"):
            self.run_config([{"RouteKey": "$default", "RouteId": "old"}])


if __name__ == "__main__":
    unittest.main()
