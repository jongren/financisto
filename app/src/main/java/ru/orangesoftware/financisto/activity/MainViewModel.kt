package ru.orangesoftware.financisto.activity

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.orangesoftware.financisto.R

enum class Tab(
    val tag: String,
    val titleRes: Int,
    val selectedIconRes: Int,
    val unselectedIconRes: Int
) {
    ACCOUNTS("accounts", R.string.accounts, R.drawable.ic_tab_accounts_selected, R.drawable.ic_tab_accounts_unselected),
    BLOTTER("blotter", R.string.blotter, R.drawable.ic_tab_blotter_selected, R.drawable.ic_tab_blotter_unselected),
    BUDGETS("budgets", R.string.budgets, R.drawable.ic_tab_budgets_selected, R.drawable.ic_tab_budgets_unselected),
    REPORTS("reports", R.string.reports, R.drawable.ic_tab_reports_selected, R.drawable.ic_tab_reports_unselected),
    MENU("menu", R.string.menu, R.drawable.ic_tab_menu_selected, R.drawable.ic_tab_menu_unselected)
}

class MainViewModel : ViewModel() {
    private val _currentTab = MutableStateFlow(Tab.ACCOUNTS)
    val currentTab: StateFlow<Tab> = _currentTab.asStateFlow()

    fun selectTab(tab: Tab) {
        _currentTab.value = tab
    }
}
