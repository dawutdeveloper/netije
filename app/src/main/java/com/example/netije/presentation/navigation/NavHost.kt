package com.example.netije.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.netije.di.PickedPersonForTxHolder
import com.example.netije.presentation.screen.calendar.CalendarScreen
import com.example.netije.presentation.screen.home.HomeScreen
import com.example.netije.presentation.screen.person.NewPersonScreen
import com.example.netije.presentation.screen.person.PersonDetailScreen
import com.example.netije.presentation.screen.person.SelectContactScreen
import com.example.netije.presentation.screen.reports.ReportSummaryScreen
import com.example.netije.presentation.screen.reports.ReportTransactionsScreen
import com.example.netije.presentation.screen.reports.ReportUserScreen
import com.example.netije.presentation.screen.reports.ReportsScreen
import com.example.netije.presentation.screen.settings.SettingsScreen
import com.example.netije.presentation.screen.transaction.NewTransactionScreen
import com.example.netije.presentation.screen.transaction.TransactionDetailScreen
import com.example.netije.presentation.viewmodel.SettingsViewModel

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
            val date = it.arguments?.getLong("date")?.takeIf { it > 0L }
            NewTransactionScreen(
                nav = navController,
                preselectedPersonId = pid,
                presetDate = date
            )
        }
        composable(Routes.SELECT_CONTACT) {
            SelectContactScreen(
                nav = navController,
                onPersonSelected = { id, name ->
                    // Store id+name in holder; the NewTransactionScreen composable under
                    // the previous NavBackStack entry will consume() it when it recomposes
                    // after user taps back / popBackStack().
                    PickedPersonForTxHolder.set(id, name)
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
        composable(Routes.REPORT_TRANSACTIONS) { ReportTransactionsScreen(nav = navController) }
    }
}
