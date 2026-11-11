package io.github.autobridge.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.autobridge.R
import io.github.autobridge.app
import io.github.autobridge.car.CarStateMonitor
import io.github.autobridge.relay.TestMessage
import io.github.autobridge.source.RelayDiagnostics
import kotlinx.coroutines.launch

/** Starts a settings change from the UI. */
private typealias SettingsEdit = (suspend SettingsRepository.() -> Unit) -> Unit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: SettingsRepository, carState: CarStateMonitor) {
    val scope = rememberCoroutineScope()
    // Null until loaded, so edits never start from defaults instead of what's saved.
    val loaded by settings.relaySettings.collectAsStateWithLifecycle(initialValue = null)
    val projecting by carState.projecting.collectAsStateWithLifecycle(initialValue = false)
    val edit: SettingsEdit = { change -> scope.launch { settings.change() } }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SetupSection()
            loaded?.let { current ->
                RelaySection(current, projecting, edit)
                FilterSection(current, edit)
                WorkspaceSection(current, edit)
                QuickReplySection(current, edit)
            }
            DiagnosticsSection()
            Text(stringResource(R.string.settings_disclaimer), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SetupSection() {
    val context = LocalContext.current
    var status by remember { mutableStateOf(context.setupStatus()) }

    // Statuses change in system settings; re-check whenever we come back.
    LifecycleResumeEffect(Unit) {
        status = context.setupStatus()
        onPauseOrDispose { }
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) context.startFirst(listOf(Permissions.appNotificationSettingsIntent(context)))
        status = context.setupStatus()
    }

    SectionTitle(R.string.settings_permissions)
    StatusRow(R.string.perm_listener, R.string.perm_listener_desc, status.listener) {
        context.startFirst(Permissions.listenerSettingsIntents(context))
    }
    StatusRow(R.string.perm_post, R.string.perm_post_desc, status.post) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startFirst(listOf(Permissions.appNotificationSettingsIntent(context)))
        }
    }
    StatusRow(R.string.perm_battery, R.string.perm_battery_desc, status.battery) {
        context.startFirst(listOf(Permissions.batteryOptimizationIntent(context)))
    }
    StatusRow(R.string.perm_slack, R.string.perm_slack_desc, status.slack, onGrant = null)
    Text(stringResource(R.string.settings_auto_hint), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun RelaySection(current: RelaySettings, projecting: Boolean, edit: SettingsEdit) {
    SectionTitle(R.string.settings_relay_section)
    SwitchRow(
        stringResource(R.string.settings_relay),
        stringResource(R.string.settings_relay_desc),
        current.enabled,
    ) { edit { setRelayEnabled(it) } }
    SwitchRow(
        stringResource(R.string.settings_only_in_car),
        stringResource(R.string.settings_only_in_car_desc),
        current.onlyInCar,
        status = stringResource(if (projecting) R.string.car_connected else R.string.car_not_connected),
    ) { edit { setOnlyInCar(it) } }
}

@Composable
private fun FilterSection(current: RelaySettings, edit: SettingsEdit) {
    val filters = current.filters
    SectionTitle(R.string.settings_filters)
    SwitchRow(
        stringResource(R.string.filter_dms_only),
        stringResource(R.string.filter_dms_only_desc),
        filters.dmsOnly,
    ) { on -> edit { updateFilters { copy(dmsOnly = on) } } }
    SwitchRow(
        stringResource(R.string.filter_mentions_only),
        stringResource(R.string.filter_mentions_only_desc),
        filters.mentionsOnly,
    ) { on -> edit { updateFilters { copy(mentionsOnly = on) } } }
    EditableList(
        R.string.filter_allowed,
        R.string.filter_allowed_desc,
        R.string.hint_channel,
        filters.allowedChannels.sorted(),
        onAdd = { entry -> edit { updateFilters { copy(allowedChannels = allowedChannels + entry) } } },
        onRemove = { entry -> edit { updateFilters { copy(allowedChannels = allowedChannels - entry) } } },
    )
    EditableList(
        R.string.filter_blocked,
        R.string.filter_blocked_desc,
        R.string.hint_channel,
        filters.blockedChannels.sorted(),
        onAdd = { entry -> edit { updateFilters { copy(blockedChannels = blockedChannels + entry) } } },
        onRemove = { entry -> edit { updateFilters { copy(blockedChannels = blockedChannels - entry) } } },
    )
    EditableList(
        R.string.filter_keywords,
        R.string.filter_keywords_desc,
        R.string.hint_keyword,
        filters.mutedKeywords.sorted(),
        onAdd = { entry -> edit { updateFilters { copy(mutedKeywords = mutedKeywords + entry) } } },
        onRemove = { entry -> edit { updateFilters { copy(mutedKeywords = mutedKeywords - entry) } } },
    )
}

@Composable
private fun WorkspaceSection(current: RelaySettings, edit: SettingsEdit) {
    SectionTitle(R.string.settings_workspaces)
    Text(stringResource(R.string.workspaces_desc), style = MaterialTheme.typography.bodySmall)
    if (current.knownWorkspaces.isEmpty()) {
        Text(stringResource(R.string.workspaces_empty), style = MaterialTheme.typography.bodySmall)
        return
    }
    current.knownWorkspaces.sorted().forEach { workspace ->
        SwitchRow(workspace, null, workspace !in current.filters.blockedWorkspaces) { relay ->
            edit {
                updateFilters {
                    copy(blockedWorkspaces = if (relay) blockedWorkspaces - workspace else blockedWorkspaces + workspace)
                }
            }
        }
    }
    TextButton(onClick = { edit { forgetKnownWorkspaces() } }) { Text(stringResource(R.string.workspaces_forget)) }
}

@Composable
private fun QuickReplySection(current: RelaySettings, edit: SettingsEdit) {
    SectionTitle(R.string.settings_quick_replies)
    EditableList(
        R.string.quick_replies_title,
        R.string.quick_replies_desc,
        R.string.hint_quick_reply,
        current.quickReplies,
        onAdd = { entry -> edit { updateQuickReplies { this + entry } } },
        onRemove = { entry -> edit { updateQuickReplies { this - entry } } },
    )
}

@Composable
private fun DiagnosticsSection() {
    val context = LocalContext.current
    val latestShape by RelayDiagnostics.latest.collectAsStateWithLifecycle()
    SectionTitle(R.string.settings_diagnostics)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.test_desc), style = MaterialTheme.typography.bodySmall)
            Button(onClick = { TestMessage.post(context.app) }) { Text(stringResource(R.string.test_send)) }
            Text(stringResource(R.string.diagnostics_latest), style = MaterialTheme.typography.titleSmall)
            SelectionContainer {
                Text(latestShape ?: stringResource(R.string.diagnostics_empty), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: Int) {
    Text(
        stringResource(title),
        Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun SwitchRow(
    title: String,
    description: String?,
    checked: Boolean,
    status: String? = null,
    onChange: (Boolean) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        // The whole row toggles, and screen readers announce the text as the switch's label.
        Row(
            Modifier
                .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                status?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

@Composable
private fun EditableList(
    title: Int,
    description: Int,
    hint: Int,
    items: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    val addDraft = {
        val entry = draft.trim()
        if (entry.isNotEmpty() && items.none { it.equals(entry, ignoreCase = true) }) onAdd(entry)
        draft = ""
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(description), style = MaterialTheme.typography.bodySmall)
            items.forEach { item ->
                val removeLabel = stringResource(R.string.action_remove_item, item)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(
                        onClick = { onRemove(item) },
                        modifier = Modifier.semantics { contentDescription = removeLabel },
                    ) { Text(stringResource(R.string.action_remove)) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(stringResource(hint)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addDraft() }),
                )
                Spacer(Modifier.width(8.dp))
                TextButton(enabled = draft.isNotBlank(), onClick = addDraft) { Text(stringResource(R.string.action_add)) }
            }
        }
    }
}

@Composable
private fun StatusRow(title: Int, description: Int, ok: Boolean, onGrant: (() -> Unit)?) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(description), style = MaterialTheme.typography.bodySmall)
                Text(
                    stringResource(if (ok) R.string.status_ok else R.string.status_missing),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
            if (!ok && onGrant != null) {
                Spacer(Modifier.width(12.dp))
                Button(onClick = onGrant) { Text(stringResource(R.string.action_grant)) }
            }
        }
    }
}

private data class SetupStatus(
    val listener: Boolean,
    val post: Boolean,
    val battery: Boolean,
    val slack: Boolean,
)

private fun Context.setupStatus() = SetupStatus(
    listener = Permissions.hasListenerAccess(this),
    post = Permissions.canPostNotifications(this),
    battery = Permissions.isIgnoringBatteryOptimizations(this),
    slack = Permissions.isSlackInstalled(this),
)

/** Some OEMs lack the specific settings screens; fall through to the next intent. */
private fun Context.startFirst(intents: List<Intent>) {
    for (intent in intents) {
        try {
            startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) {
            continue
        }
    }
}
