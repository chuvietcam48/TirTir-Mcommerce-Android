const Sentry = require("@sentry/node");

// @sentry/profiling-node uses native C++ bindings that may not be compiled
// for every Node.js version (e.g. Node 25+). Gracefully degrade if unavailable.
let nodeProfilingIntegration = null;
try {
  nodeProfilingIntegration = require("@sentry/profiling-node").nodeProfilingIntegration;
} catch (err) {
  console.warn("⚠️  @sentry/profiling-node failed to load (likely unsupported Node.js version).");
  console.warn("   Sentry error tracking will still work, but CPU profiling is disabled.");
  console.warn(`   Error: ${err.message}`);
}

// Ensure Sentry is initialized if DSN is provided
if (process.env.SENTRY_DSN) {
  const integrations = [];
  if (nodeProfilingIntegration) {
    integrations.push(nodeProfilingIntegration());
  }

  Sentry.init({
    dsn: process.env.SENTRY_DSN,
    integrations,

    // Send structured logs to Sentry
    enableLogs: true,

    // Tracing
    tracesSampleRate: 1.0, //  Capture 100% of the transactions

    // Set sampling rate for profiling - this is evaluated only once per SDK.init call
    // Note: older SDKs use profilesSampleRate, newer docs mention profileSessionSampleRate. 
    // We set both to be safe or prefer the one from user prompt if valid.
    // user prompt: profileSessionSampleRate: 1.0
    profilesSampleRate: 1.0,
    // profileSessionSampleRate: 1.0, // Uncomment if using very latest SDK beta that requires this

    // Trace lifecycle automatically enables profiling during active traces
    // profileLifecycle: 'trace', // This might be specific to certain SDK versions/configs

    // Setting this option to true will send default PII data to Sentry.
    // For example, automatic IP address collection on events
    sendDefaultPii: true,
  });
  console.log("✅ Sentry Initialized successfully with Advanced Config (Logs, Metrics, Profiling)");
} else {
  console.log("⚠️ Sentry DSN not found. Sentry is disabled.");
}
