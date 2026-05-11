@file:Suppress(
    "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE",
    "UNUSED_VARIABLE",
    "LocalVariableName",
    "NAME_SHADOWING"
)
package app.fynlo.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.fynlo.AppLockState
import app.fynlo.FinanceViewModel
import app.fynlo.ui.screens.*
import app.fynlo.ui.components.*
import app.fynlo.data.SyncStatus
import app.fynlo.data.PinManager
import kotlinx.coroutines.launch
import app.fynlo.ui.theme.*

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "Dashboard", Icons.Default.Home)
    object History : Screen("history", "History", Icons.AutoMirrored.Filled.ReceiptLong)
    object Lending : Screen("lending", "Lending", Icons.Default.Group)
    object Debts : Screen("debts", "Debts", Icons.Default.CreditCard)
    object Invest : Screen("invest", "Invest", Icons.AutoMirrored.Filled.TrendingUp)
    object Spend : Screen("spend", "Expenses", Icons.AutoMirrored.Filled.ReceiptLong)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object About : Screen("about", "About", Icons.Default.Info)
    object People : Screen("people", "Contact Book", Icons.Default.Group)
    object Budgets : Screen("budgets", "Budgeting", Icons.Default.AccountBalanceWallet)
    object Goals : Screen("goals", "Savings Goals", Icons.Default.Star)
    object Profile  : Screen("profile",  "Profile",  Icons.Default.Person)
    object FlowWizard : Screen("flow_wizard", "Flow Wizard", Icons.Default.AutoAwesome)
    object Projects  : Screen("projects",       "Projects",         Icons.Default.Business)
    object Recurring  : Screen("recurring",      "Recurring",        Icons.Default.Repeat)
    object Monthly    : Screen("monthly",        "Monthly Summary",  Icons.Default.DateRange)
    object ProfitLoss : Screen("profit_loss",    "Profit & Loss",    Icons.AutoMirrored.Filled.List)
    object DebtPayoff : Screen("debt_payoff",    "Debt Payoff",      Icons.Default.Schedule)
    object NetWorthH  : Screen("net_worth_hist", "Net Worth History",Icons.AutoMirrored.Filled.TrendingUp)
    object MoneyFlow  : Screen("money_flow",     "Money Flow",       Icons.Default.SwapHoriz)
    object LoanCalc   : Screen("loan_calc",      "Loan Calculator",  Icons.Default.Calculate)
    object Reports     : Screen("reports_hub",   "Reports",          Icons.Default.Assessment)
    object GlobalSearch: Screen("global_search",  "Search",           Icons.Default.Search)
    object Calendar    : Screen("collection_calendar", "Collection Calendar", Icons.Default.CalendarMonth)
    object InterestIncome : Screen("interest_income", "Interest Income", Icons.AutoMirrored.Filled.TrendingUp)
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Lending,
    Screen.Debts,
    Screen.Reports,
    Screen.Spend
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(viewModel: FinanceViewModel) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()
    
    var showSheet by remember { mutableStateOf(false) }
    @Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")     var showExpenseDialog by remember { mutableStateOf(false) }
    var showLendingDialog by remember { mutableStateOf(false) }
    var showDebtDialog by remember { mutableStateOf(false) }
    var showInvestmentDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val app = context.applicationContext as app.fynlo.FynloApplication
    var isLoggedIn by remember { mutableStateOf(app.authManager.isSignedInWithGoogle) }
    val pinManager = remember { PinManager(context) }
    // Start locked if PIN is set
    var isPinUnlocked by remember { mutableStateOf(!pinManager.isPinSet) }

    // Re-lock when app comes back from background (AppLockState set by MainActivity.onStop)
    if (AppLockState.isLocked && pinManager.isPinSet) {
        isPinUnlocked = false
        AppLockState.unlock()
    }

    // Onboarding â€” show only on first launch
    val prefs = remember { context.getSharedPreferences("fynlo_prefs", android.content.Context.MODE_PRIVATE) }
    var showOnboarding by remember { mutableStateOf(!prefs.getBoolean("onboarding_done", false)) }


    if (showOnboarding) {
        OnboardingScreen(onComplete = {
            prefs.edit { putBoolean("onboarding_done", true) }
            showOnboarding = false
        })
        return
    }

    // Offline banner â€” use real network state, not Firestore status
    val isOffline = remember {
        val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(network)
        caps == null || !caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.let { initial ->
        var offline by remember { mutableStateOf(initial) }
        DisposableEffect(Unit) {
            val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val callback = object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) { offline = false }
                override fun onLost(network: android.net.Network) { offline = true }
            }
            cm.registerDefaultNetworkCallback(callback)
            onDispose { cm.unregisterNetworkCallback(callback) }
        }
        offline
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    val showFab = when (currentRoute) {
        Screen.Settings.route, Screen.About.route, Screen.People.route,
        Screen.Profile.route, Screen.Lending.route, Screen.Debts.route,
        Screen.Reports.route -> false
        else -> drawerState.isClosed
    }

    val syncStatus by viewModel.syncStatus.collectAsState()

    if (!isLoggedIn) {
        LoginScreen(onSignedIn = { isLoggedIn = true })
        return
    }

    if (!isPinUnlocked) {
        PinScreen(
            mode      = PinMode.ENTER,
            onSuccess = { isPinUnlocked = true },
            onSkip    = null
        )
        return
    }

    // Dialog Triggering
    if (showExpenseDialog) {
        AddTransactionDialog(
            onDismiss = { showExpenseDialog = false },
            onConfirm = { txn ->
                viewModel.addTransaction(txn)
                showExpenseDialog = false
            }
        )
    }
    
    if (showLendingDialog) {
        AddLendingDialog(
            viewModel = viewModel,
            onDismiss = { showLendingDialog = false },
            onConfirm = { borrower, source ->
                viewModel.addBorrowerWithSource(borrower, source)
                showLendingDialog = false
            }
        )
    }
    
    if (showDebtDialog) {
        AddDebtDialog(
            viewModel = viewModel,
            onDismiss = { showDebtDialog = false },
            onConfirm = { debt, destination ->
                viewModel.addDebtWithDestination(debt, destination)
                showDebtDialog = false
            }
        )
    }

    val navAccounts by viewModel.accounts.collectAsState()
    val navDebts    by viewModel.debts.collectAsState()

    if (showInvestmentDialog) {
        AddInvestmentDialog(
            accounts  = navAccounts,
            debts     = navDebts,
            onDismiss = { showInvestmentDialog = false },
            onConfirm = { req: InvestmentSaveRequest ->
                when (req.sourceType) {
                    "account"       -> viewModel.addInvestmentFundedByAccount(req.investment, req.sourceAccountName)
                    "existing_debt" -> req.sourceDebt?.let { viewModel.addInvestmentFundedByExistingDebt(req.investment, it) }
                    "new_loan"      -> req.newLoan?.let { viewModel.addInvestmentFundedByNewLoan(req.investment, it) }
                    else            -> viewModel.addInvestmentWithSource(req.investment, req.sourceAccountName)
                }
                showInvestmentDialog = false
            }
        )
    }

    ModalNavigationDrawer(
        drawerState   = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.width(300.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxHeight().verticalScroll(rememberScrollState())
                ) {

                    // â”€â”€ Brand Header â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Emerald500, Emerald600)
                                )
                            )
                            .padding(start = 20.dp, end = 20.dp, top = 48.dp, bottom = 24.dp)
                    ) {
                        Column {
                            // App icon circle
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.AccountBalanceWallet, null,
                                    Modifier.size(28.dp), tint = Color.White
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Fynlo",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold, color = Color.White
                                )
                            )
                            Text(
                                "Personal Finance Manager",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.75f)
                                )
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // â”€â”€ Account â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    DrawerSectionLabel("Account")
                    DrawerItem(Icons.Default.Person, "Profile & Security",
                        currentRoute == Screen.Profile.route) {
                        navController.navigate(Screen.Profile.route)
                        scope.launch { drawerState.close() }
                    }
                    DrawerItem(Icons.Default.Group, "Contact Book",
                        currentRoute == Screen.People.route) {
                        navController.navigate(Screen.People.route)
                        scope.launch { drawerState.close() }
                    }
                    DrawerItem(Icons.Default.Business, "Manage Projects",
                        currentRoute == Screen.Projects.route) {
                        navController.navigate(Screen.Projects.route)
                        scope.launch { drawerState.close() }
                    }

                    DrawerDivider()

                    // â”€â”€ Finance Tools â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    DrawerSectionLabel("Finance Tools")
                    DrawerItem(Icons.Default.PieChart, "Budgeting",
                        currentRoute == Screen.Budgets.route) {
                        navController.navigate(Screen.Budgets.route)
                        scope.launch { drawerState.close() }
                    }
                    DrawerItem(Icons.Default.Star, "Savings Goals",
                        currentRoute == Screen.Goals.route) {
                        navController.navigate(Screen.Goals.route)
                        scope.launch { drawerState.close() }
                    }
                    DrawerItem(Icons.Default.Repeat, "Recurring Transactions",
                        currentRoute == Screen.Recurring.route) {
                        navController.navigate(Screen.Recurring.route)
                        scope.launch { drawerState.close() }
                    }
                    DrawerItem(Icons.AutoMirrored.Filled.ShowChart, "Investments",
                        currentRoute == Screen.Invest.route) {
                        navController.navigate(Screen.Invest.route)
                        scope.launch { drawerState.close() }
                    }
                    DrawerItem(Icons.Default.Calculate, "Loan Calculator",
                        currentRoute == Screen.LoanCalc.route) {
                        navController.navigate(Screen.LoanCalc.route)
                        scope.launch { drawerState.close() }
                    }
                    DrawerItem(Icons.Default.CalendarMonth, "Collection Calendar",
                        currentRoute == Screen.Calendar.route) {
                        navController.navigate(Screen.Calendar.route)
                        scope.launch { drawerState.close() }
                    }

                    DrawerDivider()

                    // â”€â”€ Reports â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    DrawerSectionLabel("Reports")
                    DrawerItem(Icons.Default.DateRange, "Monthly Summary",
                        currentRoute == Screen.Monthly.route) {
                        navController.navigate(Screen.Monthly.route)
                        scope.launch { drawerState.close() }
                    }
                    DrawerItem(Icons.AutoMirrored.Filled.List, "Profit & Loss",
                        currentRoute == Screen.ProfitLoss.route) {
                        navController.navigate(Screen.ProfitLoss.route)
                        scope.launch { drawerState.close() }
                    }
                    DrawerItem(Icons.Default.Schedule, "Debt Payoff Tracker",
                        currentRoute == Screen.DebtPayoff.route) {
                        navController.navigate(Screen.DebtPayoff.route)
                        scope.launch { drawerState.close() }
                    }
                    DrawerItem(Icons.AutoMirrored.Filled.TrendingUp, "Net Worth History",
                        currentRoute == Screen.NetWorthH.route) {
                        navController.navigate(Screen.NetWorthH.route)
                        scope.launch { drawerState.close() }
                    }
                    DrawerItem(Icons.Default.SwapHoriz, "Money Flow",
                        currentRoute == Screen.MoneyFlow.route) {
                        navController.navigate(Screen.MoneyFlow.route)
                        scope.launch { drawerState.close() }
                    }

                    DrawerDivider()

                    // â”€â”€ App â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    DrawerSectionLabel("App")
                    DrawerItem(Icons.Default.Settings, "Settings",
                        currentRoute == Screen.Settings.route) {
                        navController.navigate(Screen.Settings.route)
                        scope.launch { drawerState.close() }
                    }
                    DrawerItem(Icons.Default.Info, "About & Disclaimer",
                        currentRoute == Screen.About.route) {
                        navController.navigate(Screen.About.route)
                        scope.launch { drawerState.close() }
                    }

                    DrawerDivider()

                    // â”€â”€ Logout â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    NavigationDrawerItem(
                        icon     = { Icon(Icons.AutoMirrored.Filled.Logout, null,
                            tint = SemanticRed) },
                        label    = { Text("Logout & Lock",
                            color = SemanticRed,
                            fontWeight = FontWeight.SemiBold) },
                        selected = false,
                        onClick  = { isLoggedIn = false; scope.launch { drawerState.close() } },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )

                    Spacer(Modifier.height(24.dp))
                } // close Column
            } // close ModalDrawerSheet
        }  // close drawerContent
    ) {
        Scaffold(
            topBar = {
                val canNavigateBack = currentRoute != Screen.Home.route && 
                                   !bottomNavItems.any { it.route == currentRoute }
                
                CenterAlignedTopAppBar(
                    title = { Text("Fynlo") },
                    actions = {
                        IconButton(onClick = { navController.navigate(Screen.GlobalSearch.route) }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        SyncStatusBadge(status = syncStatus)
                    },
                    navigationIcon = {
                        if (canNavigateBack) {
                            IconButton(onClick = { navController.navigateUp() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        } else {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon     = { Icon(screen.icon, contentDescription = screen.label) },
                            label    = { Text(screen.label) },
                            selected = currentRoute == screen.route,
                            onClick  = {
                                navController.navigate(screen.route) {
                                    // Pop everything up to the start destination (Home)
                                    // This clears Settings, About, Profile etc. from back stack
                                    popUpTo(Screen.Home.route) {
                                        saveState    = true
                                        inclusive    = false
                                    }
                                    launchSingleTop = true
                                    restoreState    = true
                                }
                            }
                        )
                    }
                }
            },
            floatingActionButton = {
                if (showFab) {
                    FloatingActionButton(
                        onClick = { showSheet = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Quick Add")
                    }
                }
            },
            floatingActionButtonPosition = FabPosition.Center
        ) { innerPadding ->
            Column(modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())) {
                // Offline banner
                AnimatedVisibility(
                    visible = isOffline,
                    enter   = expandVertically(),
                    exit    = shrinkVertically()
                ) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer) {
                        Row(
                            modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CloudOff, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                            Text("No internet â€” changes will sync when reconnected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
                NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.weight(1f),
                enterTransition = {
                    // Fade for bottom nav tabs, slide for drill-down
                    val isBottomNav = bottomNavItems.any {
                        initialState.destination.route == it.route ||
                        targetState.destination.route == it.route
                    }
                    if (isBottomNav) fadeIn(animationSpec = androidx.compose.animation.core.tween(200))
                    else slideInHorizontally(animationSpec = androidx.compose.animation.core.tween(280)) { it / 3 } + fadeIn(androidx.compose.animation.core.tween(280))
                },
                exitTransition = {
                    val isBottomNav = bottomNavItems.any {
                        initialState.destination.route == it.route ||
                        targetState.destination.route == it.route
                    }
                    if (isBottomNav) fadeOut(animationSpec = androidx.compose.animation.core.tween(150))
                    else slideOutHorizontally(animationSpec = androidx.compose.animation.core.tween(280)) { -it / 3 } + fadeOut(androidx.compose.animation.core.tween(280))
                },
                popEnterTransition = { slideInHorizontally(animationSpec = androidx.compose.animation.core.tween(280)) { -it / 3 } + fadeIn(androidx.compose.animation.core.tween(280)) },
                popExitTransition = { slideOutHorizontally(animationSpec = androidx.compose.animation.core.tween(280)) { it / 3 } + fadeOut(androidx.compose.animation.core.tween(280)) }
            ) {
                composable(Screen.Home.route) { 
                    HomeScreen(
                        viewModel = viewModel,
                        onNavigateToScreen = { route -> navController.navigate(route) }
                    ) 
                }
                composable(Screen.History.route) { TransactionHistoryScreen(viewModel) }
                composable(Screen.Lending.route) { 
                    LendingScreen(
                        viewModel = viewModel,
                        onNavigateToDetail = { id -> navController.navigate("customer/$id") },
                        onNavigateToCalendar = { navController.navigate(Screen.Calendar.route) }
                    ) 
                }
                composable(Screen.Debts.route) { DebtScreen(viewModel) }
                composable(Screen.Invest.route) { InvestmentScreen(viewModel) }
                composable(Screen.Spend.route) { SpendScreen(viewModel) }
                composable(Screen.Settings.route) { 
                    SettingsScreen(
                        viewModel = viewModel,
                        onNavigateToAbout = { navController.navigate(Screen.About.route) }
                    ) 
                }
                composable(Screen.About.route) { AboutScreen() }
                composable(Screen.People.route) { PeopleScreen(viewModel) }
                composable(Screen.Budgets.route) { BudgetScreen(viewModel) }
                composable(Screen.Goals.route) { GoalScreen(viewModel) }
                composable(Screen.Profile.route) { ProfileScreen(onLogout = { isLoggedIn = false }) }
                composable(Screen.Projects.route) { ProjectsScreen(viewModel) }
                composable(Screen.Recurring.route)  { RecurringScreen(viewModel) }
                composable(Screen.Monthly.route)    { MonthlySummaryScreen(viewModel) }
                composable(Screen.ProfitLoss.route) { ProfitLossScreen(viewModel) }
                composable(Screen.DebtPayoff.route) { DebtPayoffScreen(viewModel) }
                composable(Screen.NetWorthH.route)  { NetWorthHistoryScreen(viewModel) }
                composable(Screen.MoneyFlow.route)   { MoneyFlowScreen(viewModel) }
                composable(Screen.LoanCalc.route)    { LoanCalculatorScreen() }
                composable(Screen.Calendar.route) {
                    CollectionCalendarScreen(
                        viewModel = viewModel,
                        onNavigateBack = { navController.navigateUp() },
                        onNavigateToBorrower = { id -> navController.navigate("customer/$id") }
                    )
                }
                composable(Screen.GlobalSearch.route) {
                    GlobalSearchScreen(
                        viewModel                = viewModel,
                        onNavigateBack           = { navController.popBackStack() },
                        onNavigateToCustomerDetail = { id -> navController.navigate("customer/$id") }
                    )
                }
                composable(Screen.Reports.route) {
                    ReportsHubScreen(
                        viewModel             = viewModel,
                        onNavigateToPL        = { navController.navigate(Screen.ProfitLoss.route) },
                        onNavigateToNetWorth  = { navController.navigate(Screen.NetWorthH.route) },
                        onNavigateToMoneyFlow = { navController.navigate(Screen.MoneyFlow.route) },
                        onNavigateToInterest  = { navController.navigate(Screen.InterestIncome.route) }
                    )
                }
                composable(Screen.InterestIncome.route) {
                    InterestIncomeScreen(
                        viewModel      = viewModel,
                        onNavigateBack = { navController.navigateUp() }
                    )
                }
                composable(Screen.FlowWizard.route) {
                    SmartFlowWizardScreen(
                        viewModel = viewModel,
                        onNavigateBack = { navController.navigateUp() }
                    )
                }
                
                composable("customer/{borrowerId}") { backStackEntry ->
                    val borrowerId = backStackEntry.arguments?.getString("borrowerId") ?: ""
                    CustomerDetailScreen(
                        borrowerId = borrowerId,
                        viewModel = viewModel,
                        onNavigateBack = { navController.navigateUp() }
                    )
                }

                composable("statement/{accountName}") { backStackEntry ->
                    val accountName = backStackEntry.arguments?.getString("accountName") ?: ""
                    AccountStatementScreen(
                        accountName = accountName,
                        viewModel = viewModel,
                        onNavigateBack = { navController.navigateUp() }
                    )
                }
            }

            if (showSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showSheet = false },
                    sheetState = sheetState
                ) {
                    QuickActionMenu(
                        navController = navController,
                        onActionClick = { actionType ->
                            showSheet = false
                            when (actionType) {
                                "expense" -> showExpenseDialog = true
                                "lending" -> showLendingDialog = true
                                "debt" -> showDebtDialog = true
                                "invest" -> showInvestmentDialog = true
                            }
                        }
                    )
                }
            }
            } // close Column
        }
    }
}

@Composable
fun QuickActionMenu(navController: NavController, onActionClick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FilledTonalButton(onClick = { navController.navigate(Screen.FlowWizard.route) }, modifier = Modifier.fillMaxWidth().height(48.dp).padding(bottom = 8.dp), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Use Smart Flow Wizard", fontWeight = FontWeight.SemiBold) }
        Text(
            "Quick Log", 
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ActionItem(Icons.AutoMirrored.Filled.ReceiptLong, "Expense") { onActionClick("expense") }
            ActionItem(Icons.Default.Handshake, "Lending") { onActionClick("lending") }
            ActionItem(Icons.Default.CreditCard, "Debt") { onActionClick("debt") }
            ActionItem(Icons.AutoMirrored.Filled.ShowChart, "Invest") { onActionClick("invest") }
        }
    }
}

@Composable
fun ActionItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(64.dp),
            shape = MaterialTheme.shapes.large,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

// â”€â”€ Drawer helper composables â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
fun DrawerSectionLabel(title: String) {
    Text(
        text     = title.uppercase(),
        style    = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = androidx.compose.ui.unit.TextUnit(1.5f,
                androidx.compose.ui.unit.TextUnitType.Sp)
        ),
        color    = Emerald500,
        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
fun DrawerItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        icon     = {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                modifier           = Modifier.size(20.dp),
                tint               = if (selected) Emerald500
                                     else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        label    = {
            Text(
                text       = label,
                style      = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                ),
                color      = if (selected) Emerald500
                             else MaterialTheme.colorScheme.onSurface
            )
        },
        selected = selected,
        onClick  = onClick,
        colors   = NavigationDrawerItemDefaults.colors(
            selectedContainerColor   = Emerald500.copy(alpha = 0.1f),
            unselectedContainerColor = Color.Transparent
        ),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 1.dp).height(48.dp)
    )
}

@Composable
fun DrawerDivider() {
    HorizontalDivider(
        modifier  = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        thickness = 0.5.dp,
        color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}



