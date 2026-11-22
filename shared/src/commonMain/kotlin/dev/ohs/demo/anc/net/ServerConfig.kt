package dev.ohs.demo.anc.net

import dev.ohs.fhir.engine.NetworkConfiguration
import dev.ohs.fhir.engine.ServerConfiguration
import dev.ohs.fhir.engine.sync.HttpAuthenticator

/**
 * HAPI's public test server: no login, and anyone can read what is uploaded, so only fictional
 * data belongs here. For the local HAPI server (docker-compose.yml) use `http://localhost:8080/fhir/`,
 * or `http://10.0.2.2:8080/fhir/` from the Android emulator.
 */
const val defaultServerUrl = "https://hapi.fhir.org/baseR4/"

/**
 * The one place the sync server is configured. The hosted demo server needs a Keycloak bearer
 * token: pass an [authenticator] then; never commit credentials.
 */
fun serverConfiguration(
  baseUrl: String = defaultServerUrl,
  authenticator: HttpAuthenticator? = null,
) =
  ServerConfiguration(
    baseUrl = baseUrl,
    // The public server often takes several seconds per request.
    networkConfiguration = NetworkConfiguration(connectionTimeOut = 30, readTimeOut = 30, writeTimeOut = 30),
    authenticator = authenticator,
  )
