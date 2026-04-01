package com.example.widget_android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.example.widget_android.MainActivity
import com.example.widget_android.R
import com.example.widget_android.ui.QuickActionDestination

internal object QuickActionsWidgetBinder {

    private const val PI_NEW_INVOICE = 401
    private const val PI_ADD_EXPENSE = 402
    private const val PI_ADD_PRODUCT = 403
    private const val PI_NEW_CUSTOMER = 404
    private const val MIN_LABEL_WIDTH_DP = 150
    private const val MIN_LABEL_HEIGHT_DP = 150

    fun build(context: Context, appWidgetOptions: Bundle? = null): RemoteViews {
        val app = context.applicationContext
        val views = RemoteViews(app.packageName, R.layout.widget_quick_actions)
        val showLabels = shouldShowLabels(appWidgetOptions)

        setLabelVisibility(views, showLabels)
        bindClicks(app, views)
        return views
    }

    private fun setLabelVisibility(
        views: RemoteViews,
        visible: Boolean
    ) {
        val visibility = if (visible) View.VISIBLE else View.GONE
        views.setViewVisibility(R.id.quick_action_label_invoice, visibility)
        views.setViewVisibility(R.id.quick_action_label_expense, visibility)
        views.setViewVisibility(R.id.quick_action_label_product, visibility)
        views.setViewVisibility(R.id.quick_action_label_customer, visibility)
    }

    private fun shouldShowLabels(options: Bundle?): Boolean {
        if (options == null) return true

        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        if (minWidth == 0 || minHeight == 0) return true

        return minWidth >= MIN_LABEL_WIDTH_DP && minHeight >= MIN_LABEL_HEIGHT_DP
    }

    private fun bindClicks(
        context: Context,
        views: RemoteViews
    ) {
        val app = context.applicationContext
        var pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pendingIntentFlags = pendingIntentFlags or PendingIntent.FLAG_IMMUTABLE
        }

        views.setOnClickPendingIntent(
            R.id.quick_action_tile_invoice,
            createPendingIntent(
                context = app,
                destination = QuickActionDestination.NEW_INVOICE,
                requestCode = PI_NEW_INVOICE,
                pendingIntentFlags = pendingIntentFlags
            )
        )
        views.setOnClickPendingIntent(
            R.id.quick_action_tile_expense,
            createPendingIntent(
                context = app,
                destination = QuickActionDestination.ADD_EXPENSE,
                requestCode = PI_ADD_EXPENSE,
                pendingIntentFlags = pendingIntentFlags
            )
        )
        views.setOnClickPendingIntent(
            R.id.quick_action_tile_product,
            createPendingIntent(
                context = app,
                destination = QuickActionDestination.ADD_PRODUCT,
                requestCode = PI_ADD_PRODUCT,
                pendingIntentFlags = pendingIntentFlags
            )
        )
        views.setOnClickPendingIntent(
            R.id.quick_action_tile_customer,
            createPendingIntent(
                context = app,
                destination = QuickActionDestination.NEW_CUSTOMER,
                requestCode = PI_NEW_CUSTOMER,
                pendingIntentFlags = pendingIntentFlags
            )
        )
    }

    private fun createPendingIntent(
        context: Context,
        destination: QuickActionDestination,
        requestCode: Int,
        pendingIntentFlags: Int
    ): PendingIntent {
        val intent =
            Intent(context, MainActivity::class.java).apply {
                setPackage(context.packageName)
                action = "com.example.widget_android.quick_action.${destination.value}"
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(
                    QuickActionDestination.EXTRA_QUICK_ACTION_DESTINATION,
                    destination.value
                )
            }
        return PendingIntent.getActivity(context, requestCode, intent, pendingIntentFlags)
    }
}
