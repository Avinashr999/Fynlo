package com.example.cashmemo.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    object Projects : Screen("projects", "Projects", Icons.Default.Business)
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
    var isLoggedIn by remember { mutableStateOf(false) }
    val syncStatus by viewModel.syncStatus.collectAsState()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    val showFab = when (currentRoute) {
        Screen.Settings.route, Screen.About.route, Screen.People.route, Screen.Profile.route -> false
        else -> drawerState.isClosed
    }

    if (!isLoggedIn) {
        LoginScreen(onLoginSuccess = { isLoggedIn = true })
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
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.padding(innerPadding),
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
        }
    }
}

@Composable
fun QuickActionMenu(onActionClick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
