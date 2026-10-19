package dev.ohs.demo.anc.net

import dev.ohs.fhir.engine.ServerConfiguration
import dev.ohs.fhir.engine.sync.HttpAuthenticator

/** Base URL of the FHIR server, per platform (the Android emulator reaches the host via 10.0.2.2). */
expect val defaultServerUrl: String

/**
 * The one place the sync server is configured. The hosted demo server needs a Keycloak bearer
 * token: pass an [authenticator] then; never commit credentials.
 */
fun serverConfiguration(
  baseUrl: String = defaultServerUrl,
  authenticator: HttpAuthenticator? = null,
) = ServerConfiguration(baseUrl = baseUrl, authenticator = authenticator)
