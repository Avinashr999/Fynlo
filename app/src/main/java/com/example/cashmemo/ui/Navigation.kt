package com.example.cashmemo.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.cashmemo.FinanceViewModel
import com.example.cashmemo.ui.screens.*
import com.example.cashmemo.ui.components.*
import com.example.cashmemo.ui.components.SyncStatusBadge
import com.example.cashmemo.data.SyncStatus
import com.example.cashmemo.data.PinManager
import com.example.cashmemo.ui.screens.PinMode
import com.example.cashmemo.ui.screens.PinScreen
import kotlinx.coroutines.launch

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
    object Login : Screen("login", "Login", Icons.Default.Lock)
    object Profile  : Screen("profile",  "Profile",  Icons.Default.Person)
    object FlowWizard : Screen("flow_wizard", "Flow Wizard", Icons.Default.AutoAwesome)
    object Projects  : Screen("projects",       "Projects",         Icons.Default.Business)
    object Recurring  : Screen("recurring",      "Recurring",        Icons.Default.Repeat)
    object Monthly    : Screen("monthly",        "Monthly Summary",  Icons.Default.DateRange)
    object ProfitLoss : Screen("profit_loss",    "Profit & Loss",    Icons.Default.List)
    object DebtPayoff : Screen("debt_payoff",    "Debt Payoff",      Icons.Default.Schedule)
    object NetWorthH  : Screen("net_worth_hist", "Net Worth History",Icons.AutoMirrored.Filled.TrendingUp)
    object MoneyFlow  : Screen("money_flow",     "Money Flow",       Icons.Default.SwapHoriz)
    object LoanCalc   : Screen("loan_calc",      "Loan Calculator",  Icons.Default.Calculate)
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.History,
    Screen.Lending,
    Screen.Debts,
    Screen.Invest,
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
    var showExpenseDialog by remember { mutableStateOf(false) }
    var showLendingDialog by remember { mutableStateOf(false) }
    var showDebtDialog by remember { mutableStateOf(false) }
    var showInvestmentDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val app = context.applicationContext as com.example.cashmemo.CashMemoApplication
    var isLoggedIn by remember { mutableStateOf(app.authManager.isSignedInWithGoogle) }
    val pinManager = remember { PinManager(context) }
    // Start locked if PIN is set — user must enter PIN on every fresh app launch
    var isPinUnlocked by remember { mutableStateOf(!pinManager.isPinSet) }

    // Onboarding — show only on first launch
    val prefs = remember { context.getSharedPreferences("cashmemo_prefs", android.content.Context.MODE_PRIVATE) }
    var showOnboarding by remember { mutableStateOf(!prefs.getBoolean("onboarding_done", false)) }


    if (showOnboarding) {
        OnboardingScreen(onComplete = {
            prefs.edit().putBoolean("onboarding_done", true).apply()
            showOnboarding = false
        })
        return@MainNavigation
    }

    // Offline banner — use real network state, not Firestore status
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
        Screen.Settings.route, Screen.About.route, Screen.People.route, Screen.Profile.route -> false
        else -> drawerState.isClosed
    }

    val syncStatus by viewModel.syncStatus.collectAsState()

    if (!isLoggedIn) {
        LoginScreen(onSignedIn = { isLoggedIn = true })
        return@MainNavigation
    }

    if (!isPinUnlocked) {
        PinScreen(
            mode      = PinMode.ENTER,
            onSuccess = { isPinUnlocked = true },
            onSkip    = null
        )
        return@MainNavigation
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

    if (showInvestmentDialog) {
        AddInvestmentDialog(
            onDismiss = { showInvestmentDialog = false },
            onConfirm = { invest, source ->
                viewModel.addInvestmentWithSource(invest, source)
                showInvestmentDialog = false
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Cash Memo Pro", 
                    modifier = Modifier.padding(16.dp), 
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                )
                HorizontalDivider()
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    label = { Text("Profile & Security") },
                    selected = currentRoute == Screen.Profile.route,
                    onClick = { 
                        navController.navigate(Screen.Profile.route)
                        scope.launch { drawerState.close() } 
                    }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Group, contentDescription = null) },
                    label = { Text("Contact Book") },
                    selected = currentRoute == Screen.People.route,
                    onClick = { 
                        navController.navigate(Screen.People.route)
                        scope.launch { drawerState.close() } 
                    }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Business, contentDescription = null) },
                    label = { Text("Manage Projects") },
                    selected = currentRoute == Screen.Projects.route,
                    onClick = {
                        navController.navigate(Screen.Projects.route)
                        scope.launch { drawerState.close() }
                    }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
                    label = { Text("Budgeting") },
                    selected = currentRoute == Screen.Budgets.route,
                    onClick = { 
                        navController.navigate(Screen.Budgets.route)
                        scope.launch { drawerState.close() } 
                    }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Star, contentDescription = null) },
                    label = { Text("Savings Goals") },
                    selected = currentRoute == Screen.Goals.route,
                    onClick = { 
                        navController.navigate(Screen.Goals.route)
                        scope.launch { drawerState.close() } 
                    }
                )
                NavigationDrawerItem(
                    icon     = { Icon(Icons.Default.Repeat, null) },
                    label    = { Text("Recurring Transactions") },
                    selected = currentRoute == Screen.Recurring.route,
                    onClick  = { navController.navigate(Screen.Recurring.route); scope.launch { drawerState.close() } }
                )
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("Reports", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 2.dp))
                NavigationDrawerItem(
                    icon     = { Icon(Icons.Default.DateRange, null) },
                    label    = { Text("Monthly Summary") },
                    selected = currentRoute == Screen.Monthly.route,
                    onClick  = { navController.navigate(Screen.Monthly.route); scope.launch { drawerState.close() } }
                )
                NavigationDrawerItem(
                    icon     = { Icon(Icons.Default.List, null) },
                    label    = { Text("Profit & Loss") },
                    selected = currentRoute == Screen.ProfitLoss.route,
                    onClick  = { navController.navigate(Screen.ProfitLoss.route); scope.launch { drawerState.close() } }
                )
                NavigationDrawerItem(
                    icon     = { Icon(Icons.Default.Schedule, null) },
                    label    = { Text("Debt Payoff Tracker") },
                    selected = currentRoute == Screen.DebtPayoff.route,
                    onClick  = { navController.navigate(Screen.DebtPayoff.route); scope.launch { drawerState.close() } }
                )
                NavigationDrawerItem(
                    icon     = { Icon(Icons.AutoMirrored.Filled.TrendingUp, null) },
                    label    = { Text("Net Worth History") },
                    selected = currentRoute == Screen.NetWorthH.route,
                    onClick  = { navController.navigate(Screen.NetWorthH.route); scope.launch { drawerState.close() } }
                )
                NavigationDrawerItem(
                    icon     = { Icon(Icons.Default.SwapHoriz, null) },
                    label    = { Text("Money Flow") },
                    selected = currentRoute == Screen.MoneyFlow.route,
                    onClick  = { navController.navigate(Screen.MoneyFlow.route); scope.launch { drawerState.close() } }
                )
                NavigationDrawerItem(
                    icon     = { Icon(Icons.Default.Calculate, null) },
                    label    = { Text("Loan Calculator") },
                    selected = currentRoute == Screen.LoanCalc.route,
                    onClick  = { navController.navigate(Screen.LoanCalc.route); scope.launch { drawerState.close() } }
                )
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("App Settings") },
                    selected = currentRoute == Screen.Settings.route,
                    onClick = {
                        navController.navigate(Screen.Settings.route)
                        scope.launch { drawerState.close() }
                    }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Info, contentDescription = null) },
                    label = { Text("About & Disclaimer") },
                    selected = currentRoute == Screen.About.route,
                    onClick = { 
                        navController.navigate(Screen.About.route)
                        scope.launch { drawerState.close() } 
                    }
                )
                Spacer(Modifier.weight(1f))
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    label = { Text("Logout & Lock") },
                    selected = false,
                    onClick = { 
                        isLoggedIn = false
                        scope.launch { drawerState.close() } 
                    },
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                val canNavigateBack = currentRoute != Screen.Home.route && 
                                   !bottomNavItems.any { it.route == currentRoute }
                
                CenterAlignedTopAppBar(
                    title = { Text("Cash Memo") },
                    actions = {
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
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
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
            Column(modifier = Modifier.padding(innerPadding)) {
                // Offline banner
                androidx.compose.animation.AnimatedVisibility(
                    visible = isOffline,
                    enter   = androidx.compose.animation.expandVertically(),
                    exit    = androidx.compose.animation.shrinkVertically()
                ) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer) {
                        Row(
                            modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CloudOff, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                            Text("No internet — changes will sync when reconnected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
                NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.weight(1f),
                enterTransition = { slideInHorizontally { it } + fadeIn() },
                exitTransition = { slideOutHorizontally { -it } + fadeOut() },
                popEnterTransition = { slideInHorizontally { -it } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it } + fadeOut() }
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
                        onNavigateToDetail = { id -> navController.navigate("customer/$id") }
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
                composable(Screen.FlowWizard.route) {
                    SmartFlowWizardScreen(
                        viewModel = viewModel,
                        onDone    = { navController.navigateUp() }
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
