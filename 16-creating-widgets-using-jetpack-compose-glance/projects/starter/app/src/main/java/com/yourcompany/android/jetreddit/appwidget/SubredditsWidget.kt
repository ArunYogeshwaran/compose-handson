package com.yourcompany.android.jetreddit.appwidget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.ToggleableStateKey
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.FixedColorProvider
import com.yourcompany.android.jetreddit.R
import com.yourcompany.android.jetreddit.dependencyinjection.dataStore
import com.yourcompany.android.jetreddit.screens.communities
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.map

private val toggledSubredditIdKey = ActionParameters.Key<String>("ToggledSubredditIdKey")

class SubredditsWidget : GlanceAppWidget() {

  @Composable
  override fun Content() {
    Column(
      modifier = GlanceModifier
        .fillMaxSize()
        .padding(16.dp)
        .appWidgetBackground()
        .background(Color.White)
        .cornerRadius(16.dp)
    ) {
      WidgetTitle()
      ScrollableSubredditsList()
    }
  }
}

@Composable
fun WidgetTitle() {
  Text(
    text = "Subreddits",
    modifier = GlanceModifier.fillMaxWidth(),
    style = TextStyle(
      fontWeight = FontWeight.Bold,
      fontSize = 18.sp,
      color = FixedColorProvider(Color.Black)
    ),
  )
}

@Composable
fun ScrollableSubredditsList() {
  LazyColumn {
    items(communities) { communityId ->
      Subreddit(id = communityId)
    }
  }
}

@Composable
fun Subreddit(@StringRes id: Int) {
  val preferences: Preferences = currentState()
  val checked: Boolean = preferences[booleanPreferencesKey(id.toString())] ?: false

  Row(
    modifier = GlanceModifier
      .padding(top = 16.dp)
      .fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
  ) {

    Image(
      provider = ImageProvider(R.drawable.subreddit_placeholder),
      contentDescription = null,
      modifier = GlanceModifier.size(24.dp)
    )

    Text(
      text = LocalContext.current.getString(id),
      modifier = GlanceModifier.padding(start = 16.dp).defaultWeight(),
      style = TextStyle(
        color = FixedColorProvider(color = MaterialTheme.colors.primaryVariant),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold
      )
    )

    Switch(
      checked = checked,
      onCheckedChange = actionRunCallback<SwitchToggleAction>(
        actionParametersOf(
          toggledSubredditIdKey to id.toString()
        )
      )
    )
  }
}

class SwitchToggleAction : ActionCallback {

  override suspend fun onAction(
    context: Context,
    glanceId: GlanceId,
    parameters: ActionParameters
  ) {
    val toggledSubredditId: String = requireNotNull(parameters[toggledSubredditIdKey])
    val checked: Boolean = requireNotNull(parameters[ToggleableStateKey])

    updateAppWidgetState(context, glanceId) { glancePreferences ->
      glancePreferences[booleanPreferencesKey(toggledSubredditId)] = checked
    }

    context.dataStore.edit { appPreferences ->
      appPreferences[booleanPreferencesKey(toggledSubredditId)] = checked
    }

    SubredditsWidget().update(context, glanceId)
  }
}

class SubredditsWidgetReceiver : GlanceAppWidgetReceiver() {

  override val glanceAppWidget: GlanceAppWidget = SubredditsWidget()

  private val coroutineScope = MainScope()

  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray
  ) {
    super.onUpdate(context, appWidgetManager, appWidgetIds)

    coroutineScope.launch {
      val glanceId: GlanceId? = GlanceAppWidgetManager(context)
        .getGlanceIds(SubredditsWidget::class.java)
        .firstOrNull()

      if (glanceId != null) {

        withContext(Dispatchers.IO) {
          context.dataStore.data
            .map { preferences -> preferences.toSubredditIdToCheckedMap() }
            .collect { subredditIdToCheckedMap ->
              updateAppWidgetPreferences(subredditIdToCheckedMap, context, glanceId)
              glanceAppWidget.update(context, glanceId)
            }
        }
      }
    }
  }

  private suspend fun updateAppWidgetPreferences(
    subredditIdToCheckedMap: Map<Int, Boolean>,
    context: Context,
    glanceId: GlanceId
  ) {
    subredditIdToCheckedMap.forEach { (subredditId, checked) ->
      updateAppWidgetState(context, glanceId) { state ->
        state[booleanPreferencesKey(subredditId.toString())] = checked
      }
    }
  }

  private fun Preferences.toSubredditIdToCheckedMap(): Map<Int, Boolean> {
    return communities.associateWith { communityId ->
      this[booleanPreferencesKey(communityId.toString())] ?: false
    }
  }

  override fun onDeleted(context: Context, appWidgetIds: IntArray) {
    super.onDeleted(context, appWidgetIds)
    coroutineScope.cancel()
  }
}