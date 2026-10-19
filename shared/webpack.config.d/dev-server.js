// SQLite-WASM with OPFS (fhir-engine's web storage) needs a cross-origin-isolated page, so the dev
// server sends COOP/COEP headers. Any production host must send the same two headers.
// Port 8086 to stay clear of HAPI (8080) and other local services.
(function (config) {
  config.devServer = config.devServer || {};
  config.devServer.port = 8086;
  config.devServer.headers = [
    { key: "Cross-Origin-Opener-Policy", value: "same-origin" },
    { key: "Cross-Origin-Embedder-Policy", value: "require-corp" },
  ];
})(config);
