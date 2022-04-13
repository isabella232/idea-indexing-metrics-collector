package co.elastic.idea.plugin.imc

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.json.jackson.JacksonJsonpMapper
import co.elastic.clients.transport.rest_client.RestClientTransport
import co.elastic.idea.plugin.imc.settings.ImcSettingsState
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.apache.http.Header
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.CredentialsProvider
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.message.BasicHeader
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestClientBuilder
import java.nio.charset.StandardCharsets
import java.util.*


class ElasticsearchClientFactory(private val settingsState: ImcSettingsState) {

    private val jacksonJsonpMapper = JacksonJsonpMapper()

    init {
        jacksonJsonpMapper.objectMapper().registerKotlinModule()
        jacksonJsonpMapper.objectMapper().registerModule(JavaTimeModule())
    }

    fun newElasticsearchClient(): ElasticsearchClient {
        return ElasticsearchClient(newRestClientTransport())
    }

    private fun newRestClientTransport(): RestClientTransport {
        val restClientBuilder = RestClient.builder(
            HttpHost(settingsState.elasticsearchHost, settingsState.elasticsearchPort, "https")
        )
        return RestClientTransport(withAuthentication(restClientBuilder).build(), jacksonJsonpMapper)
    }

    private fun withAuthentication(restClientBuilder: RestClientBuilder) : RestClientBuilder{
        when (settingsState.authType) {
            ImcSettingsState.AuthType.BASIC_AUTH -> {
                val credentialsProvider: CredentialsProvider = BasicCredentialsProvider()
                credentialsProvider.setCredentials(
                    AuthScope.ANY,
                    UsernamePasswordCredentials(settingsState.elasticsearchUsername, settingsState.elasticsearchPassword)
                )
                restClientBuilder.setHttpClientConfigCallback { httpClientBuilder ->
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider)
                }
            }
            ImcSettingsState.AuthType.ACCESS_TOKEN_AUTH -> {
                val defaultHeaders: Array<Header> = arrayOf(
                    BasicHeader(
                        "Authorization",
                        "Bearer ${settingsState.elasticsearchAccessToken}"
                    )
                )
                restClientBuilder.setDefaultHeaders(defaultHeaders)
            }
            ImcSettingsState.AuthType.API_KEYS_AUTH -> {
                val apiKeyAuth: String = Base64.getEncoder().encodeToString(
                    (settingsState.elasticsearchApiKey + ":" + settingsState.elasticsearchApiSecret).toByteArray(StandardCharsets.UTF_8)
                )
                val defaultHeaders = arrayOf<Header>(
                    BasicHeader(
                        "Authorization",
                        "ApiKey $apiKeyAuth"
                    )
                )
                restClientBuilder.setDefaultHeaders(defaultHeaders)
            }
        }
        return restClientBuilder
    }


}