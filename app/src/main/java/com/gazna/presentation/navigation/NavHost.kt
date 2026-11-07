package com.gazna.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.gazna.presentation.screen.calendar.CalendarScreen
import com.gazna.presentation.screen.home.HomeScreen
import com.gazna.presentation.screen.person.NewPersonScreen
import com.gazna.presentation.screen.person.PersonDetailScreen
import com.gazna.presentation.screen.person.SelectContactScreen
import com.gazna.presentation.screen.reports.ReportSummaryScreen
import com.gazna.presentation.screen.reports.ReportTransactionsScreen
import com.gazna.presentation.screen.reports.ReportUserScreen
import com.gazna.presentation.screen.reports.ReportsScreen
import com.gazna.presentation.screen.settings.SettingsScreen
import com.gazna.presentation.screen.transaction.NewTransactionScreen
import com.gazna.presentation.screen.transaction.TransactionDetailScreen
import com.gazna.presentation.viewmodel.SettingsViewModel

@Composable
fun NetijeNavHost(
    navController: NavHostController,
    settingsVm: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier
    ) {
        composable(Routes.HOME) { HomeScreen(nav = navController) }
        composable(Routes.CALENDAR) { CalendarScreen(nav = navController) }
        composable(Routes.REPORTS) { ReportsScreen(nav = navController) }
        composable(Routes.SETTINGS) { SettingsScreen(nav = navController, vm = settingsVm) }
        composable(
            Routes.NEW_PERSON,
            arguments = listOf(
                navArgument("personId") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { NewPersonScreen(nav = navController) }
        composable(
            Routes.NEW_TRANSACTION,
            arguments = listOf(
                navArgument("preselectedPersonId") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("date") {
                    type = NavType.LongType
                    defaultValue = 0L
                },
                navArgument("transactionId") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) {
            val pid = it.arguments?.getString("preselectedPersonId")?.ifBlank { null }
            NewTransactionScreen(
                nav = navController,
                preselectedPersonId = pid,
                pickerState = it.savedStateHandle
            )
        }
        composable(Routes.SELECT_CONTACT) {
            SelectContactScreen(
                nav = navController,
                onPersonSelected = { id, name ->
                    // Return the selection through the transaction entry's saved state.
                    navController.previousBackStackEntry?.savedStateHandle?.set("picked_person", arrayListOf(id, name))
                }
            )
        }
        composable(
            Routes.PERSON_DETAIL,
            arguments = listOf(navArgument("personId") { type = NavType.StringType })
        ) { PersonDetailScreen(nav = navController) }
        composable(
            Routes.TRANSACTION_DETAIL,
            arguments = listOf(navArgument("transactionId") { type = NavType.StringType })
        ) { TransactionDetailScreen(nav = navController) }
        composable(
            Routes.REPORT_USER,
            arguments = listOf(
                navArgument("personId") { type = NavType.StringType; defaultValue = "" }
            )
        ) { ReportUserScreen(nav = navController) }
        composable(Routes.REPORT_SUMMARY) { ReportSummaryScreen(nav = navController) }
        composable(
            Routes.REPORT_TRANSACTIONS_SEARCH,
            arguments = listOf(navArgument("search") { type = NavType.StringType; defaultValue = "" })
        ) { ReportTransactionsScreen(nav = navController) }
    }
}
