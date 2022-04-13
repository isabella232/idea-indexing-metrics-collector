package co.elastic.idea.plugin.imc

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.ElasticsearchException
import co.elastic.clients.elasticsearch.core.IndexRequest
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest
import co.elastic.clients.elasticsearch.indices.ExistsRequest
import co.elastic.idea.plugin.imc.model.SimpleProjectIndexingEvent
import co.elastic.idea.plugin.imc.modelbuilder.SimpleProjectIndexingEventModelBuilder
import co.elastic.idea.plugin.imc.settings.ImcSettingsState
import com.intellij.ide.plugins.PluginManager
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.Project
import com.intellij.util.indexing.diagnostic.ProjectIndexingHistory
import com.intellij.util.indexing.diagnostic.ProjectIndexingHistoryListener
import com.intellij.util.indexing.diagnostic.dto.toMillis
import java.io.InputStream

@org.jetbrains.annotations.ApiStatus.Internal
class IndexHistoryListener : ProjectIndexingHistoryListener {

    private val settingsState = ImcSettingsState.instance
    private val elasticsearchClientFactory = ElasticsearchClientFactory(settingsState)
    private val version = PluginManager.getInstance().findEnabledPlugin(
        PluginId.getId("co.elastic.idea.indexingmetricscollector")
    )!!.version

    private var initializedIndex = ""

    override fun onFinishedIndexing(projectIndexingHistory: ProjectIndexingHistory) {
        val project = projectIndexingHistory.project
        runBackgroundableTask("Uploading indexing metrics", project) {
            if (validBasicConfiguration(project)) {
                val index = settingsState.elasticsearchIndex
                withWarningNotifications("Error publishing indexing metrics", project) {
                    val client = elasticsearchClientFactory.newElasticsearchClient()
                    maybeInitializeIndex(project, client, index)
                    client.index { builder: IndexRequest.Builder<SimpleProjectIndexingEvent> ->
                        builder.index(index)
                            .document(
                                SimpleProjectIndexingEventModelBuilder.builder()
                                    .withEnvironment(version)
                                    .withAnonymizedData(settingsState.anonymize)
                                    .withProjectName(project.name)
                                    .withIndexingReason(projectIndexingHistory.indexingReason)
                                    .withWasFullIndex(projectIndexingHistory.times.wasFullIndexing)
                                    .withWasInterrupted(projectIndexingHistory.times.wasInterrupted)
                                    .withTotalUpdatingTime(projectIndexingHistory.times.totalUpdatingTime.toMillis())
                                    .withScanFilesDuration(projectIndexingHistory.times.scanFilesDuration.toMillis())
                                    .withIndexingDuration(projectIndexingHistory.times.indexingDuration.toMillis())
                                    .withTimestamps(
                                        projectIndexingHistory.times.updatingStart.toInstant().toEpochMilli(),
                                        projectIndexingHistory.times.updatingEnd.toInstant().toEpochMilli()
                                    ).build()
                            )
                    }
                }
            }
        }
    }

    private fun maybeInitializeIndex(
        project: Project,
        client: ElasticsearchClient,
        index: String
    ) {
        if (initializedIndex.isNullOrBlank() || initializedIndex != index) {
            maybeCreateIndex(project, client, index)
            initializedIndex = index
        }
    }

    private fun validBasicConfiguration(project: Project): Boolean {
        return if (!settingsState.elasticsearchHost.isNullOrBlank() && settingsState.elasticsearchPort > 1) {
            true
        } else {
            sentNotification(project, "Indexing metrics collector endpoint not configured")
            false
        }
    }

    private fun maybeCreateIndex(project: Project, client: ElasticsearchClient, index: String) {
        if (!elasticsearchIndexExists(client, index)) {
            informAboutIndexCreating(project, index)
            createIndexWithPredefinedMapping(client, index)
        }
    }

    private fun createIndexWithPredefinedMapping(client: ElasticsearchClient, index: String) {
        val input: InputStream = this.javaClass.getResourceAsStream("/idea-indexing-mapping.json")
        client.indices().create(CreateIndexRequest.of { b: CreateIndexRequest.Builder ->
            b.index(index).withJson(input)
        }).acknowledged()
    }

    private fun elasticsearchIndexExists(client: ElasticsearchClient, index: String) =
        client.indices().exists(
            ExistsRequest.of { b: ExistsRequest.Builder -> b.index(index) }
        ).value()

    private fun informAboutIndexCreating(project: Project, index: String) {
        sentNotification(project) {
            it.createNotification(
                "Creating indexing metrics elasticsearch index $index",
                NotificationType.INFORMATION
            )
        }
    }

    private fun withWarningNotifications(content: String, project: Project, action: () -> Unit) {
        try {
            action.invoke()
        } catch (e: ElasticsearchException) {
            sentNotification(project, content)
        }
    }

    private fun sentNotification(project: Project, content: String) {
        sentNotification(project) {
            it.createNotification(content, NotificationType.WARNING).addAction(ShowSettingsAction("Configure..."))
        }
    }

    private fun sentNotification(project: Project, notficationAction: (group: NotificationGroup) -> Notification) {
        notficationAction.invoke(NotificationGroupManager.getInstance().getNotificationGroup("Indexing Metrics Collector Group"))
            .notify(project)
    }

}