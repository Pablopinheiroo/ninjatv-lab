from flask import Flask, jsonify, render_template, request
import requests

app = Flask(__name__)
app.config["MAX_CONTENT_LENGTH"] = 16 * 1024

VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify"

# Official Cloudflare test keys only. These keys do not validate challenges
# issued by third-party production websites.
SCENARIOS = {
    "pass_visible": {
        "label": "Aprovação previsível (visível)",
        "sitekey": "1x00000000000000000000AA",
        "secret": "1x0000000000000000000000000000000AA",
        "expected": True,
    },
    "fail_visible": {
        "label": "Reprovação previsível (visível)",
        "sitekey": "2x00000000000000000000AB",
        "secret": "2x0000000000000000000000000000000AA",
        "expected": False,
    },
    "pass_invisible": {
        "label": "Aprovação previsível (invisível)",
        "sitekey": "1x00000000000000000000BB",
        "secret": "1x0000000000000000000000000000000AA",
        "expected": True,
    },
    "fail_invisible": {
        "label": "Reprovação previsível (invisível)",
        "sitekey": "2x00000000000000000000BB",
        "secret": "2x0000000000000000000000000000000AA",
        "expected": False,
    },
    "interactive": {
        "label": "Desafio interativo de teste",
        "sitekey": "3x00000000000000000000FF",
        "secret": "1x0000000000000000000000000000000AA",
        "expected": True,
    },
    "duplicate": {
        "label": "Token duplicado/consumido",
        "sitekey": "1x00000000000000000000AA",
        "secret": "3x0000000000000000000000000000000AA",
        "expected": False,
    },
}


@app.get("/")
def index():
    public_scenarios = {
        key: {"label": value["label"], "sitekey": value["sitekey"]}
        for key, value in SCENARIOS.items()
    }
    return render_template("index.html", scenarios=public_scenarios)


@app.post("/verify")
def verify():
    data = request.get_json(silent=True) or {}
    scenario_id = data.get("scenario")
    token = data.get("token")

    if scenario_id not in SCENARIOS:
        return jsonify({"success": False, "error": "Cenário desconhecido."}), 400
    if not isinstance(token, str) or not token or len(token) > 4096:
        return jsonify({"success": False, "error": "Token ausente ou inválido."}), 400

    scenario = SCENARIOS[scenario_id]

    try:
        response = requests.post(
            VERIFY_URL,
            data={"secret": scenario["secret"], "response": token},
            timeout=12,
        )
        response.raise_for_status()
        result = response.json()
        # Never return the secret key or submitted token to the browser.
        return jsonify({
            "success": bool(result.get("success")),
            "expected": scenario["expected"],
            "label": scenario["label"],
            "siteverify": result,
        })
    except requests.RequestException as exc:
        return jsonify({
            "success": False,
            "error": "Não foi possível consultar o Siteverify.",
            "detail": str(exc),
        }), 502
    except ValueError:
        return jsonify({
            "success": False,
            "error": "O Siteverify retornou uma resposta que não é JSON.",
        }), 502


@app.post("/simulate-auxiliary-failure")
def simulate_auxiliary_failure():
    # This endpoint is deliberately local to this lab. It does NOT block,
    # intercept, or alter Cloudflare Insights or Turnstile on other websites.
    return jsonify({
        "simulated": True,
        "resource": "telemetria-local-de-teste",
        "error": "ERR_CONNECTION_REFUSED (simulado)",
        "note": "Falha auxiliar simulada; nenhuma chamada do Turnstile foi bloqueada.",
    }), 503


if __name__ == "__main__":
    # Intended for a trusted local network test only, not public deployment.
    app.run(host="0.0.0.0", port=5000, debug=False)
