package com.example.widget_android.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.widget_android.R

enum class QuickActionDestination(
    val value: String,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    @StringRes val widgetLabelRes: Int,
    @StringRes val contentDescriptionRes: Int,
    @DrawableRes val iconRes: Int
) {
    NEW_INVOICE(
        value = "new_invoice",
        titleRes = R.string.quick_action_title_new_invoice,
        subtitleRes = R.string.quick_action_subtitle_new_invoice,
        widgetLabelRes = R.string.quick_action_label_invoice,
        contentDescriptionRes = R.string.quick_action_cd_new_invoice,
        iconRes = R.drawable.ic_quick_action_invoice
    ),
    ADD_EXPENSE(
        value = "add_expense",
        titleRes = R.string.quick_action_title_add_expense,
        subtitleRes = R.string.quick_action_subtitle_add_expense,
        widgetLabelRes = R.string.quick_action_label_expense,
        contentDescriptionRes = R.string.quick_action_cd_add_expense,
        iconRes = R.drawable.ic_quick_action_expense
    ),
    ADD_PRODUCT(
        value = "add_product",
        titleRes = R.string.quick_action_title_add_product,
        subtitleRes = R.string.quick_action_subtitle_add_product,
        widgetLabelRes = R.string.quick_action_label_product,
        contentDescriptionRes = R.string.quick_action_cd_add_product,
        iconRes = R.drawable.ic_quick_action_product
    ),
    NEW_CUSTOMER(
        value = "new_customer",
        titleRes = R.string.quick_action_title_new_customer,
        subtitleRes = R.string.quick_action_subtitle_new_customer,
        widgetLabelRes = R.string.quick_action_label_customer,
        contentDescriptionRes = R.string.quick_action_cd_new_customer,
        iconRes = R.drawable.ic_quick_action_customer
    );

    companion object {
        const val EXTRA_QUICK_ACTION_DESTINATION = "quick_action_destination"

        fun fromValue(value: String?): QuickActionDestination? =
            entries.firstOrNull { it.value == value }
    }
}
