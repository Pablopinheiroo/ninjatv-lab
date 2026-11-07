(() => {
  "use strict";

  const output = document.getElementById("diagnosticOutput");
  const status = document.getElementById("diagnosticStatus");
  const events = [];
  const maxEvents = 100;

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

  function recentResources() {
    return performance.getEntriesByType("resource").slice(-100).map(item => ({
      resource: safeUrl(item.name),
      type: item.initiatorType,
      duration_ms: Math.round(item.duration),
      transfer_bytes: Number.isFinite(item.transferSize) ? item.transferSize : null
    }));
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

  window.addEventListener("ninja-turnstile-success", () => {
    record("turnstile-callback-success", { tokenValueCaptured: false });
  });

  window.addEventListener("ninja-turnstile-error", event => {
    record("turnstile-callback-error", {
      code: String(event.detail?.code || "sem código").slice(0, 80)
    });
  });

  window.addEventListener("ninja-turnstile-expired", () => {
    record("turnstile-callback-expired", {});
  });

  window.addEventListener("online", () => record("network-online", {}));
  window.addEventListener("offline", () => record("network-offline", {}));
  document.addEventListener("visibilitychange", () => {
    record("visibility-change", { state: document.visibilityState });
  });

  function snapshot() {
    const frames = [...document.querySelectorAll("iframe")].map(frame => ({
      origin: safeUrl(frame.src)?.origin || "about:blank",
      title: (frame.title || "").slice(0, 80),
      visible: Boolean(frame.getClientRects().length)
    }));

    const resources = recentResources();
    const cloudflareResources = resources.filter(item =>
      item.resource?.origin?.includes("cloudflare.com")
    );

    const text = (document.body?.innerText || "").toLowerCase();
    const ua = navigator.userAgent || "";
    const chromeMatch = ua.match(/Chrome\/([\d.]+)/i);
    const androidMatch = ua.match(/Android\s+([\d.]+)/i);
    const webViewHint = /;\s*wv\)/i.test(ua) ||
      (/Android/i.test(ua) && /Version\/[\d.]+.*Chrome\/[\d.]+.*Mobile Safari/i.test(ua));

    const turnstileScript = [...document.querySelectorAll("script[src]")]
      .map(script => safeUrl(script.src))
      .find(resource => resource?.origin?.includes("challenges.cloudflare.com") &&
        resource.path.includes("/turnstile/"));

    const features = {
      javascript: "ativo",
      localStorage: testStorage("localStorage"),
      sessionStorage: testStorage("sessionStorage"),
      indexedDB: testIndexedDB(),
      cookiesEnabled: navigator.cookieEnabled === true,
      cryptoSubtle: Boolean(window.crypto && window.crypto.subtle),
      secureContext: window.isSecureContext === true,
      messageChannel: typeof MessageChannel === "function",
      broadcastChannel: typeof BroadcastChannel === "function",
      postMessage: typeof window.postMessage === "function",
      fetch: typeof window.fetch === "function",
      promise: typeof Promise === "function",
      requestAnimationFrame: typeof requestAnimationFrame === "function"
    };

    return {
      capturedAt: new Date().toISOString(),
      page: {
        origin: location.origin,
        path: location.pathname,
        visibility: document.visibilityState
      },
      browser: {
        userAgent: ua,
        chromeVersion: chromeMatch?.[1] || null,
        androidVersion: androidMatch?.[1] || null,
        language: navigator.language || null,
        platform: navigator.platform || null,
        online: navigator.onLine,
        cookieEnabled: navigator.cookieEnabled,
        viewport: { width: innerWidth, height: innerHeight },
        androidWebViewHint: webViewHint
      },
      nativeWebViewSettings: {
        webViewPackageVersion: "não acessível pelo JavaScript da página",
        thirdPartyCookieSetting: "não acessível pelo JavaScript da página",
        note: "Esses dados exigem diagnóstico nativo no aplicativo Android."
      },
      features,
      turnstile: {
        apiAvailable: Boolean(window.turnstile &&
          typeof window.turnstile.render === "function"),
        scriptElementPresent: Boolean(turnstileScript),
        script: turnstileScript || null,
        framePresent: frames.some(frame =>
          frame.origin.includes("challenges.cloudflare.com")
        ),
        cloudflareResourceCount: cloudflareResources.length,
        cloudflareResources,
        challengeTextDetected:
          /checking your browser|verify you are human|just a moment|attention required/.test(text)
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
    status.textContent =
      "Diagnóstico pronto. Dados locais; nenhum cookie ou token foi coletado.";
  });

  document.getElementById("clearDiagnostics")?.addEventListener("click", () => {
    events.length = 0;
    output.textContent = "Registros locais apagados.";
    status.textContent = "Registros limpos.";
  });

  record("diagnostics-ready", { page: location.pathname });
})();