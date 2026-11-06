const VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

const SCENARIOS = {
  pass_visible: {
    label: "Aprovação previsível (visível)",
    sitekey: "1x00000000000000000000AA",
    secretName: "TURNSTILE_SECRET_PASS",
    expected: true,
    size: "normal"
  },
  fail_visible: {
    label: "Reprovação previsível (visível)",
    sitekey: "2x00000000000000000000AB",
    secretName: "TURNSTILE_SECRET_FAIL",
    expected: false,
    size: "normal"
  },
  pass_invisible: {
    label: "Aprovação previsível (invisível)",
    sitekey: "1x00000000000000000000BB",
    secretName: "TURNSTILE_SECRET_PASS",
    expected: true,
    size: "invisible"
  },
  fail_invisible: {
    label: "Reprovação previsível (invisível)",
    sitekey: "2x00000000000000000000BB",
    secretName: "TURNSTILE_SECRET_FAIL",
    expected: false,
    size: "invisible"
  },
  interactive: {
    label: "Desafio interativo de teste",
    sitekey: "3x00000000000000000000FF",
    secretName: "TURNSTILE_SECRET_PASS",
    expected: true,
    size: "normal"
  },
  duplicate: {
    label: "Token duplicado/consumido",
    sitekey: "1x00000000000000000000AA",
    secretName: "TURNSTILE_SECRET_DUPLICATE",
    expected: false,
    size: "normal"
  }
};

const json = (data, status = 200) => new Response(JSON.stringify(data), {
  status,
  headers: {
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store"
  }
});

function publicScenarios() {
  return Object.fromEntries(Object.entries(SCENARIOS).map(([id, item]) => [
    id,
    { label: item.label, sitekey: item.sitekey, size: item.size }
  ]));
}

async function verify(request, env) {
  let body;
  try {
    body = await request.json();
  } catch {
    return json({ success: false, error: "O corpo da solicitação não é JSON válido." }, 400);
  }

  const scenario = SCENARIOS[body?.scenario];
  const token = body?.token;

  if (!scenario) {
    return json({ success: false, error: "Cenário desconhecido." }, 400);
  }
  if (typeof token !== "string" || token.length === 0 || token.length > 4096) {
    return json({ success: false, error: "Token ausente ou inválido." }, 400);
  }

  const secret = env[scenario.secretName];
  if (!secret) {
    return json({
      success: false,
      error: "Configuração pendente: segredo de teste não cadastrado no Worker.",
      required_binding: scenario.secretName
    }, 503);
  }

  const form = new URLSearchParams();
  form.set("secret", secret);
  form.set("response", token);

  try {
    const upstream = await fetch(VERIFY_URL, {
      method: "POST",
      headers: { "content-type": "application/x-www-form-urlencoded" },
      body: form.toString()
    });

    if (!upstream.ok) {
      return json({ success: false, error: "O serviço Siteverify respondeu com erro HTTP." }, 502);
    }

    const result = await upstream.json();
    // Never log or return the submitted token or the secret to the browser.
    return json({
      success: Boolean(result.success),
      expected: scenario.expected,
      label: scenario.label,
      siteverify: {
        success: Boolean(result.success),
        challenge_ts: result.challenge_ts ?? null,
        hostname: result.hostname ?? null,
        action: result.action ?? null,
        cdata: result.cdata ?? null,
        "error-codes": Array.isArray(result["error-codes"]) ? result["error-codes"] : []
      }
    });
  } catch {
    return json({ success: false, error: "Não foi possível consultar o Siteverify." }, 502);
  }
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (url.pathname === "/api/health" && request.method === "GET") {
      return json({ ok: true, app: "NinjaSolver Lab", mode: "official-test-keys-only" });
    }

    if (url.pathname === "/api/scenarios" && request.method === "GET") {
      return json(publicScenarios());
    }

    if (url.pathname === "/api/verify" && request.method === "POST") {
      if (!request.headers.get("content-type")?.includes("application/json")) {
        return json({ success: false, error: "Use Content-Type: application/json." }, 415);
      }
      return verify(request, env);
    }

    if (url.pathname === "/api/simulate-auxiliary-failure" && request.method === "POST") {
      return json({
        simulated: true,
        resource: "telemetria-local-de-teste",
        error: "ERR_CONNECTION_REFUSED (simulado)",
        note: "Falha auxiliar simulada; nenhuma chamada do Turnstile foi bloqueada."
      }, 503);
    }

    if (url.pathname.startsWith("/api/")) {
      return json({ error: "Rota de API não encontrada." }, 404);
    }

    return env.ASSETS.fetch(request);
  }
};
