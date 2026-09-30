package com.example.netije.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.netije.R

sealed class BottomNavItem(
    val route: String,
    @StringRes val labelRes: Int,
    val iconOutlined: ImageVector,
    val iconFilled: ImageVector
) {
    data object Home : BottomNavItem(
        route = "home",
        labelRes = R.string.nav_home,
        iconOutlined = Icons.Outlined.Home,
        iconFilled = Icons.Filled.Home
    )
    data object Calendar : BottomNavItem(
        route = "calendar",
        labelRes = R.string.nav_calendar,
        iconOutlined = Icons.Outlined.CalendarMonth,
        iconFilled = Icons.Filled.CalendarMonth
    )
    data object Reports : BottomNavItem(
        route = "reports",
        labelRes = R.string.nav_reports,
        iconOutlined = Icons.Outlined.BarChart,
        iconFilled = Icons.Filled.BarChart
    )
    data object Settings : BottomNavItem(
        route = "settings",
        labelRes = R.string.nav_settings,
        iconOutlined = Icons.Outlined.Settings,
        iconFilled = Icons.Filled.Settings
    )

    companion object {
        val entries: List<BottomNavItem> = listOf(Home, Calendar, Reports, Settings)
    }
}

object Routes {
    const val HOME = "home"
    const val CALENDAR = "calendar"
    const val REPORTS = "reports"
    const val SETTINGS = "settings"
    const val NEW_PERSON = "new_person?personId={personId}"
    const val NEW_TRANSACTION = "new_transaction?preselectedPersonId={preselectedPersonId}&date={date}&transactionId={transactionId}"
    const val SELECT_CONTACT = "select_contact"
    const val PERSON_DETAIL = "person_detail/{personId}"
    const val TRANSACTION_DETAIL = "transaction_detail/{transactionId}"
    const val REPORT_USER = "report_user?personId={personId}"
    const val REPORT_SUMMARY = "report_summary"
    const val REPORT_TRANSACTIONS = "report_transactions"

    fun newTransaction(
        personId: String? = null,
        date: Long? = null,
        transactionId: String? = null
    ): String {
        val pid = personId.orEmpty()
        val d = date ?: 0L
        val tid = transactionId.orEmpty()
        return "new_transaction?preselectedPersonId=$pid&date=$d&transactionId=$tid"
    }

    fun newPerson(personId: String? = null): String = "new_person?personId=${personId.orEmpty()}"
    fun personDetail(personId: String): String = "person_detail/$personId"
    fun transactionDetail(transactionId: String): String = "transaction_detail/$transactionId"
    fun reportUser(personId: String? = null): String = "report_user?personId=${personId.orEmpty()}"
}
