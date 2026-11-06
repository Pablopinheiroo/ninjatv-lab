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
    } catch {
      return null;
    }
  }

  function record(type, detail = {}) {
    if (events.length >= maxEvents) events.shift();
    events.push({ time: new Date().toISOString(), type, ...detail });
  }

  function testStorage(name) {
    try {
      const storage = window[name];
      const key = "__ninja_lab_probe__";
      storage.setItem(key, "1");
      storage.removeItem(key);
      return "disponível";
    } catch (error) {
      return "indisponível: " + String(error.name || "erro");
    }
  }

  function testIndexedDB() {
    try {
      return typeof indexedDB !== "undefined" ? "API disponível" : "API ausente";
    } catch (error) {
      return "erro: " + String(error.name || "erro");
    }
  }

  window.addEventListener("error", event => {
    if (event.target && event.target !== window) {
      const resource = safeUrl(event.target.src || event.target.href || "");
      record("resource-error", {
        resource,
        tag: event.target.tagName || "unknown"
      });
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
      directive: event.violatedDirective || null,
      blocked: safeUrl(event.blockedURI)
    });
  });

  window.addEventListener("message", event => {
    if (event.origin && event.origin !== location.origin) {
      record("cross-origin-message-metadata", {
        origin: event.origin,
        dataType: typeof event.data
      });
    }
  });

  function snapshot() {
    const frames = [...document.querySelectorAll("iframe")].map(frame => ({
      origin: safeUrl(frame.src)?.origin || "about:blank",
      title: (frame.title || "").slice(0, 80),
      visible: Boolean(frame.getClientRects().length)
    }));

    const resources = performance.getEntriesByType("resource").slice(-80).map(item => ({
      resource: safeUrl(item.name),
      type: item.initiatorType,
      duration_ms: Math.round(item.duration)
    }));

    const text = (document.body?.innerText || "").toLowerCase();
    const ua = navigator.userAgent || "";
    const features = {
      javascript: "ativo",
      localStorage: testStorage("localStorage"),
      sessionStorage: testStorage("sessionStorage"),
      indexedDB: testIndexedDB(),
      cookiesEnabled: navigator.cookieEnabled === true,
      cryptoSubtle: Boolean(window.crypto && window.crypto.subtle),
      messageChannel: typeof MessageChannel === "function",
      broadcastChannel: typeof BroadcastChannel === "function",
      postMessage: typeof window.postMessage === "function",
      fetch: typeof window.fetch === "function",
      promise: typeof Promise === "function",
      requestAnimationFrame: typeof requestAnimationFrame === "function"
    };

    return {
      capturedAt: new Date().toISOString(),
      page: { origin: location.origin, path: location.pathname },
      browser: {
        userAgent: ua,
        language: navigator.language || null,
        platform: navigator.platform || null,
        online: navigator.onLine,
        cookieEnabled: navigator.cookieEnabled,
        viewport: { width: innerWidth, height: innerHeight },
        webViewHint: ua.includes("; wv)")
      },
      features,
      hints: {
        turnstileScriptRequested: Boolean(document.querySelector('script[src*="challenges.cloudflare.com/turnstile"]')),
        turnstileApiAvailable: Boolean(window.turnstile && typeof window.turnstile.render === "function"),
        turnstileFramePresent: frames.some(frame => frame.origin.includes("challenges.cloudflare.com")),
        cloudflareChallengeText: /checking your browser|verify you are human|just a moment|attention required/.test(text),
        iframeCount: frames.length
      },
      frames,
      recentResources: resources,
      recentErrors: events.slice()
    };
  }

  document.getElementById("runDiagnostics")?.addEventListener("click", async () => {
    status.textContent = "Coletando dados locais…";
    const started = performance.now();
    let health;

    try {
      const response = await fetch("/api/health", { cache: "no-store" });
      health = {
        httpStatus: response.status,
        ok: response.ok,
        latency_ms: Math.round(performance.now() - started)
      };
    } catch (error) {
      health = {
        ok: false,
        error: String(error.message || error).slice(0, 160)
      };
    }

    const data = snapshot();
    data.workerHealth = health;
    output.textContent = JSON.stringify(data, null, 2);
    status.textContent = "Diagnóstico pronto. Dados mantidos somente nesta página.";
  });

  document.getElementById("clearDiagnostics")?.addEventListener("click", () => {
    events.length = 0;
    output.textContent = "Registros locais apagados.";
    status.textContent = "Registros limpos.";
  });

  record("diagnostics-ready", { page: location.pathname });
})();