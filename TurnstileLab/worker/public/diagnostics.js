(() => {
  "use strict";
  const output = document.getElementById("diagnosticOutput");
  const status = document.getElementById("diagnosticStatus");
  const events = [];
  const maxEvents = 80;

  function safeUrl(value) {
    try {
      const u = new URL(value, location.href);
      return { origin: u.origin, path: u.pathname };
    } catch { return null; }
  }
  function record(type, detail) {
    if (events.length >= maxEvents) events.shift();
    events.push({ time: new Date().toISOString(), type, ...detail });
  }

  window.addEventListener("error", event => {
    if (event.target && event.target !== window) {
      const resource = safeUrl(event.target.src || event.target.href || "");
      record("resource-error", { resource, tag: event.target.tagName || "unknown" });
    } else {
      record("javascript-error", {
        message: String(event.message || "erro").slice(0, 240),
        source: safeUrl(event.filename || "")
      });
    }
  }, true);

  window.addEventListener("unhandledrejection", event => {
    record("unhandled-rejection", {
      message: String(event.reason?.message || event.reason || "rejeição").slice(0, 240)
    });
  });

  window.addEventListener("securitypolicyviolation", event => {
    record("csp-violation", {
      directive: event.violatedDirective,
      blocked: safeUrl(event.blockedURI)
    });
  });

  function snapshot() {
    const frames = [...document.querySelectorAll("iframe")].map(frame => ({
      origin: safeUrl(frame.src)?.origin || "about:blank",
      title: (frame.title || "").slice(0, 80),
      visible: Boolean(frame.getClientRects().length)
    }));
    const resources = performance.getEntriesByType("resource").slice(-60).map(item => ({
      resource: safeUrl(item.name),
      type: item.initiatorType,
      duration_ms: Math.round(item.duration)
    }));
    const text = (document.body?.innerText || "").toLowerCase();
    const hints = {
      turnstileScript: Boolean(document.querySelector('script[src*="challenges.cloudflare.com/turnstile"]')),
      turnstileFrame: frames.some(frame => frame.origin.includes("challenges.cloudflare.com")),
      cloudflareChallengeText: /checking your browser|verify you are human|just a moment|attention required/.test(text),
      iframeCount: frames.length
    };
    const ua = navigator.userAgent || "";
    return {
      capturedAt: new Date().toISOString(),
      page: { origin: location.origin, path: location.pathname },
      browser: {
        userAgent: ua,
        language: navigator.language || null,
        platform: navigator.platform || null,
        online: navigator.onLine,
        viewport: { width: innerWidth, height: innerHeight },
        webViewHint: /; wv\)/i.test(ua)
      },
      hints,
      frames,
      recentResources: resources,
      recentErrors: events.slice()
    };
  }

  document.getElementById("runDiagnostics").addEventListener("click", async () => {
    status.textContent = "Coletando dados locais…";
    let health;
    const started = performance.now();
    try {
      const response = await fetch("/api/health", { cache: "no-store" });
      health = { httpStatus: response.status, ok: response.ok, latency_ms: Math.round(performance.now() - started) };
    } catch (error) {
      health = { ok: false, error: String(error.message || error).slice(0, 160) };
    }
    const data = snapshot();
    data.workerHealth = health;
    output.textContent = JSON.stringify(data, null, 2);
    status.textContent = "Diagnóstico pronto. Os dados ficaram somente nesta página; nada foi enviado para outro serviço.";
  });

  document.getElementById("clearDiagnostics").addEventListener("click", () => {
    events.length = 0;
    output.textContent = "Registros locais apagados.";
    status.textContent = "Registros limpos.";
  });

  record("diagnostics-ready", { page: location.pathname });
})();