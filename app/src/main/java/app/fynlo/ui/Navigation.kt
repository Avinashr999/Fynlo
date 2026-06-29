@file:Suppress(
    "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE",
    "UNUSED_VARIABLE",
    "LocalVariableName",
    "NAME_SHADOWING"
)
package app.fynlo.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.fynlo.billing.BillingManager
import app.fynlo.data.AuthManager
import app.fynlo.logic.toRecurringTemplate
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.fynlo.AppLockState
import app.fynlo.FinanceViewModel
import app.fynlo.data.UserPreferences
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
    object Loans : Screen("loans_hub", "Loans", Icons.Default.Handshake)
    object Invest : Screen("invest", "Invest", Icons.AutoMirrored.Filled.TrendingUp)
    object Spend : Screen("spend", "Expenses", Icons.AutoMirrored.Filled.ReceiptLong)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object UpgradePro : Screen("upgrade_pro", "Fynlo Ledger Pro", Icons.Default.Star)
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
    object LoanCalc   : Screen("loan_calc",      "EMI Calculator",   Icons.Default.Calculate)
    object Reports     : Screen("reports_hub",   "Reports",          Icons.Default.Assessment)
    object GlobalSearch: Screen("global_search",  "Search",           Icons.Default.Search)
    object Calendar    : Screen("collection_calendar", "Collection Calendar", Icons.Default.CalendarMonth)
    object InterestIncome : Screen("interest_income", "Interest Income", Icons.AutoMirrored.Filled.TrendingUp)
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Loans,
    Screen.Invest,
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
    var addTxnIsIncome by remember { mutableStateOf(false) }
    var showLendingDialog by remember { mutableStateOf(false) }
    var showDebtDialog by remember { mutableStateOf(false) }
    var showInvestmentDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val app = context.applicationContext as app.fynlo.FynloApplication
    var isLoggedIn by remember { mutableStateOf(app.authManager.isSignedInWithGoogle) }
    val pinManager = remember { PinManager(context) }

    LaunchedEffect(Unit) {
        viewModel.feedbackEvents.collect { message ->
            snackbarHostState.showSnackbar(
                message = message,
                withDismissAction = false,
                duration = SnackbarDuration.Short,
            )
        }
    }

    // Start locked if PIN is set
    var isPinUnlocked by remember { mutableStateOf(!pinManager.isPinSet) }

    // Re-lock when app comes back from background (AppLockState set by MainActivity.onStop)
    if (AppLockState.isLocked && pinManager.isPinSet) {
        isPinUnlocked = false
        AppLockState.unlock()
    }

    // Onboarding + Setup — show only on first launch (DataStore-backed)
    var showOnboarding by remember { mutableStateOf(!UserPreferences.getOnboardingDoneSync(context)) }
    var showSetup by remember { mutableStateOf(!UserPreferences.getSetupDoneSync(context)) }

    if (showOnboarding) {
        OnboardingScreen(onComplete = {
            kotlinx.coroutines.MainScope().launch {
                UserPreferences.setOnboardingDone(context, true)
            }
            showOnboarding = false
        })
        return
    }

    if (showSetup) {
        FirstLaunchSetupScreen(onComplete = {
            kotlinx.coroutines.MainScope().launch {
                UserPreferences.setSetupDone(context, true)
            }
            showSetup = false
        })
        return
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
    // Strip any query args (e.g. "loans_hub?tab=1") so route comparisons still match.
    val baseRoute = currentRoute?.substringBefore("?")

    // Pro gate: go to [dest] if the user has Pro, otherwise to the upgrade screen.
    // While billing is disabled, isPro is always true → this just navigates normally.
    fun navGated(dest: String) {
        if (BillingManager.isPro.value) navController.navigate(dest)
        else navController.navigate(Screen.UpgradePro.route)
    }

    // C07 fix (UX_AUDIT §C07): the Scaffold FAB opens the QuickActionMenu
    // (all transaction types). Hide it on screens that have their own
    // contextual add-affordance — otherwise the user sees two FABs (or a
    // FAB + an `+` icon in the header) competing for the same intent.
    // Added in 3.2.12: Budgets, Goals, Recurring — each owns its own
    // "Add Budget" / "Add Goal" / "Add Recurring" entry point.
    val showFab = false

    // Routes that provide their own full-screen chrome (own top bar) — the outer
    // app bar/bottom bar must hide to avoid duplicate back arrows.
    val isFullScreenRoute = currentRoute == Screen.GlobalSearch.route ||
        currentRoute?.startsWith("customer/") == true ||
        currentRoute?.startsWith("debt/") == true ||
        currentRoute?.startsWith("statement/") == true

    val syncStatus by viewModel.syncStatus.collectAsState()
    val transactionsForReview by viewModel.transactions.collectAsState()
    val hasRatedApp by UserPreferences.reviewPromptRated(context).collectAsState(initial = false)
    val reviewLastShownAt by UserPreferences.reviewPromptLastShownAt(context).collectAsState(initial = 0L)
    var showReviewPrompt by remember { mutableStateOf(false) }

    LaunchedEffect(transactionsForReview.size, hasRatedApp, reviewLastShownAt, isLoggedIn, isPinUnlocked) {
        val now = System.currentTimeMillis()
        val cooldownMs = 7L * 24L * 60L * 60L * 1000L
        if (
            isLoggedIn &&
            isPinUnlocked &&
            !hasRatedApp &&
            transactionsForReview.size >= 8 &&
            now - reviewLastShownAt > cooldownMs
        ) {
            showReviewPrompt = true
        }
    }

    if (showReviewPrompt) {
        FynloReviewDialog(
            onDismiss = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                showReviewPrompt = false
                scope.launch { UserPreferences.remindReviewPromptLater(context) }
            },
            onRateNow = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                showReviewPrompt = false
                scope.launch { UserPreferences.markReviewPromptRated(context) }
                openPlayStore(context)
            },
        )
    }

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

    val navAccounts by viewModel.accounts.collectAsState()
    val navDebts    by viewModel.debts.collectAsState()
    val navProject  by viewModel.currentProject.collectAsState()
    val navCurrencyCode = navProject?.currency ?: "INR"

    // Dialog Triggering
    if (showExpenseDialog) {
        // 3.2.59 — wire real account / investment / debt names so the
        // source picker is a dropdown of existing entities (orphan fix).
        val allInvestments by viewModel.investments.collectAsState()
        AddTransactionDialog(
            onDismiss = { showExpenseDialog = false },
            onConfirm = { txn ->
                viewModel.addTransaction(txn)
                viewModel.showFeedback(if (txn.type.equals("income", ignoreCase = true)) "Income added" else "Expense added")
                showExpenseDialog = false
            },
            initialIsIncome = addTxnIsIncome,
            rememberLastCategory = { isIncome -> viewModel.rememberLastTransactionCategory(isIncome) },
            onRecordCategory = { isIncome, cat -> viewModel.recordTransactionCategory(isIncome, cat) },
            // 3.2.81 (C13 #5) — "Repeat monthly?" → also create a recurring template.
            // `_root_ide_package_.app.fynlo.logic.…` not needed; use the import alias via toRecurringTemplate.
            onRepeatMonthly = { txn -> viewModel.addRecurringTransaction(toRecurringTemplate(txn)) },
            currencyCode    = navCurrencyCode,
            bankAccounts    = navAccounts.map { it.name },
            investmentNames = allInvestments.map { it.name },
            debtNames       = navDebts.map { it.name },
            existingTransactions = transactionsForReview,
        )
    }
    
    if (showLendingDialog) {
        AddLendingDialog(
            viewModel = viewModel,
            onDismiss = { showLendingDialog = false },
            onConfirm = { borrower, source ->
                viewModel.addBorrowerWithSource(borrower, source)
                viewModel.showFeedback("Loan added")
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
                viewModel.showFeedback("Debt added")
                showDebtDialog = false
            }
        )
    }

    // Auto-log recurring transactions once on each app session start
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.triggerDueRecurring()
        viewModel.saveSnapshotNow()
    }

    if (showInvestmentDialog) {
        AddInvestmentDialog(
            accounts  = navAccounts,
            debts     = navDebts,
            currencyCode = navCurrencyCode,
            onDismiss = { showInvestmentDialog = false },
            onConfirm = { req: InvestmentSaveRequest ->
                when (req.sourceType) {
                    "account"       -> viewModel.addInvestmentFundedByAccount(req.investment, req.sourceAccountName, req.sourceAccountId)
                    "existing_debt" -> req.sourceDebt?.let { viewModel.addInvestmentFundedByExistingDebt(req.investment, it) }
                    "new_loan"      -> req.newLoan?.let { viewModel.addInvestmentFundedByNewLoan(req.investment, it) }
                    else            -> viewModel.addInvestmentWithSource(req.investment, req.sourceAccountName)
                }
                viewModel.showFeedback("Investment added")
                showInvestmentDialog = false
            }
        )
    }

    val canNavigateBack = currentRoute != Screen.Home.route &&
        !bottomNavItems.any { it.route == currentRoute }

    ModalNavigationDrawer(
        drawerState     = drawerState,
        gesturesEnabled = !canNavigateBack,
        drawerContent   = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.background,
                modifier = Modifier.width(300.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxHeight().verticalScroll(rememberScrollState())
                ) {

                    // ── C20 (3.2.44) — Compact header per audit §C20 ──────────────
                    // Pre-C20 the header was 52dp icon + "Fynlo Ledger" headline + a
                    // "Personal Finance Manager" tagline = ~120dp tall (~25%
                    // of drawer vertical). Now it's a single row: 40dp avatar
                    // + user name + email. Drops the tagline (it's still on
                    // the About screen) and surfaces the signed-in identity
                    // (audit: "Avinash's name/email exists per Profile but
                    // isn't surfaced here").
                    val authForDrawer = remember { AuthManager() }
                    val hasSignedInProfile = authForDrawer.userName.isNotBlank() || authForDrawer.userEmail.isNotBlank()
                    val drawerUserName = authForDrawer.userName.ifBlank {
                        if (hasSignedInProfile) "Signed in" else "Local mode"
                    }
                    val drawerUserEmail = authForDrawer.userEmail.ifBlank {
                        if (hasSignedInProfile) "Google sync available" else "Sign in from Profile to sync"
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, top = 28.dp, bottom = 12.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        tonalElevation = 0.dp,
                        border = androidx.compose.foundation.BorderStroke(
                            0.8.dp,
                            TemplateBorder,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FynloBrandMark(size = 42.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    drawerUserName,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Text(
                                    drawerUserEmail,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = Emerald100.copy(alpha = 0.75f),
                                ) {
                                    Text(
                                        "Local ledger",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Emerald700,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                    )

                    Spacer(Modifier.height(8.dp))

                    // ── C20 (3.2.44) — Items ordered by usage frequency per audit
                    // §C20 fix #3 ("Order items by usage frequency, not semantic
                    // group. Top 3-5: most-tapped destinations"). The top group
                    // gets primary tint via accent=true; secondary items below
                    // stay grey. Bottom-nav destinations (Home/Loans/Invest/
                    // Reports/Expenses) are NOT in the drawer — they have
                    // primary entry points already; the drawer is the
                    // *secondary* navigation surface.
                    //
                    // Removed "Investments" drawer entry — it was a duplicate
                    // of the bottom-nav "Invest" tab; reduces noise + matches
                    // the audit's icon-consistency point (drawer ShowChart vs
                    // bottom-nav TrendingUp).
                    DrawerItem(Icons.Default.Settings, "Settings",
                        currentRoute == Screen.Settings.route,
                        accent = true) {
                        navController.navigate(Screen.Settings.route)
                        scope.launch { drawerState.close() }
                    }
                    DrawerItem(Icons.Default.Person, "Profile & Security",
                        currentRoute == Screen.Profile.route,
                        accent = true) {
                        navController.navigate(Screen.Profile.route)
                        scope.launch { drawerState.close() }
                    }
                    DrawerItem(Icons.Default.AccountBalanceWallet, "Budgeting",
                        currentRoute == Screen.Budgets.route,
                        accent = true) {
                        navController.navigate(Screen.Budgets.route)
                        scope.launch { drawerState.close() }
                    }
                    DrawerItem(Icons.Default.Star, "Savings Goals",
                        currentRoute == Screen.Goals.route,
                        accent = true) {
                        navController.navigate(Screen.Goals.route)
                        scope.launch { drawerState.close() }
                    }
                    DrawerItem(Icons.Default.Group, "Contact Book",
                        currentRoute == Screen.People.route,
                        accent = true) {
                        navController.navigate(Screen.People.route)
                        scope.launch { drawerState.close() }
                    }

                    DrawerDivider()

                    // Secondary destinations (grey tint).
                    DrawerItem(Icons.Default.Repeat, "Recurring Transactions",
                        currentRoute == Screen.Recurring.route) {
                        navGated(Screen.Recurring.route)
                        scope.launch { drawerState.close() }
                    }
                    DrawerItem(Icons.Default.Business, "Manage Projects",
                        currentRoute == Screen.Projects.route) {
                        navController.navigate(Screen.Projects.route)
                        scope.launch { drawerState.close() }
                    }
                    // 3.2.17 — EMI Calculator was registered as a route since 3.0
                    // but had ZERO entry points in the UI. Adding here keeps it
                    // discoverable; also exposed as a tile on the Reports hub.
                    DrawerItem(Icons.Default.Calculate, "EMI Calculator",
                        currentRoute == Screen.LoanCalc.route) {
                        navController.navigate(Screen.LoanCalc.route)
                        scope.launch { drawerState.close() }
                    }
                    DrawerItem(Icons.Default.Info, "About & Disclaimer",
                        currentRoute == Screen.About.route) {
                        navController.navigate(Screen.About.route)
                        scope.launch { drawerState.close() }
                    }

                    DrawerDivider()

                    // ── Logout ────────────────────────────────────────────────
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
                if (!isFullScreenRoute) {
                    val isPrivacy by viewModel.isPrivacyMode.collectAsState()
                    LedgerAppTopBar(
                        canNavigateBack = canNavigateBack,
                        isPrivacy = isPrivacy,
                        syncStatus = syncStatus,
                        onBack = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            navController.navigateUp()
                        },
                        onMenu = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch { drawerState.open() }
                        },
                        onHome = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Home.route) { saveState = true; inclusive = false }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onTogglePrivacy = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.togglePrivacyMode()
                        },
                        onSearch = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            navController.navigate(Screen.GlobalSearch.route)
                        },
                        onSyncClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val msg = when (syncStatus) {
                                is app.fynlo.data.SyncStatus.Synced       -> "All changes synced to cloud"
                                is app.fynlo.data.SyncStatus.Syncing      -> "Syncing..."
                                is app.fynlo.data.SyncStatus.Offline      -> "Offline - changes sync when reconnected"
                                is app.fynlo.data.SyncStatus.Initialising -> "Connecting to cloud..."
                                is app.fynlo.data.SyncStatus.Error        -> "Sync error - sign in again to retry"
                            }
                            viewModel.showFeedback(msg)
                        },
                    )
                }
            },
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        shape = RoundedCornerShape(16.dp),
                        containerColor = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                }
            },
            bottomBar = {
                if (!isFullScreenRoute)
                LedgerBottomNav(
                    items = bottomNavItems,
                    selectedRoute = baseRoute,
                    onSelect = { screen ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (baseRoute != screen.route) {
                                    val isHome = screen.route == Screen.Home.route
                                    navController.navigate(screen.route) {
                                        // Pop everything up to Home (clears Settings, History,
                                        // detail screens etc. from the back stack).
                                        // Don't save/restore state when the target IS Home —
                                        // otherwise the popped child gets saved under Home's key
                                        // and immediately restored, bouncing back to it.
                                        popUpTo(Screen.Home.route) {
                                            inclusive = false
                                            saveState = !isHome
                                        }
                                        launchSingleTop = true
                                        restoreState    = !isHome
                                    }
                                }
                    }
                )
            },
            floatingActionButton = {
                if (showFab) {
                    FloatingActionButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showSheet = true
                        },
                        modifier = Modifier.size(58.dp),
                        shape = RoundedCornerShape(16.dp),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Quick Add", modifier = Modifier.size(30.dp))
                    }
                }
            },
            floatingActionButtonPosition = FabPosition.Center
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding)) {
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
                            Text("No internet \u2014 changes will sync when reconnected",
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
                    val isBottomNav = bottomNavItems.any {
                        initialState.destination.route == it.route ||
                        targetState.destination.route == it.route
                    }
                    if (isBottomNav)
                        fadeIn(animationSpec = androidx.compose.animation.core.tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                    else
                        slideInHorizontally(
                            animationSpec = androidx.compose.animation.core.tween(
                                240,
                                easing = androidx.compose.animation.core.FastOutSlowInEasing,
                            )
                        ) { (it * 0.25f).toInt() } + fadeIn(androidx.compose.animation.core.tween(250))
                },
                exitTransition = {
                    val isBottomNav = bottomNavItems.any {
                        initialState.destination.route == it.route ||
                        targetState.destination.route == it.route
                    }
                    if (isBottomNav)
                        fadeOut(animationSpec = androidx.compose.animation.core.tween(160))
                    else
                        slideOutHorizontally(
                            animationSpec = androidx.compose.animation.core.tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                        ) { -(it * 0.25f).toInt() } + fadeOut(androidx.compose.animation.core.tween(180))
                },
                popEnterTransition = {
                    slideInHorizontally(
                        animationSpec = androidx.compose.animation.core.tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                    ) { -(it * 0.25f).toInt() } + fadeIn(androidx.compose.animation.core.tween(220))
                },
                popExitTransition = {
                    slideOutHorizontally(
                        animationSpec = androidx.compose.animation.core.tween(
                            200,
                            easing = androidx.compose.animation.core.FastOutSlowInEasing,
                        )
                    ) { (it * 0.25f).toInt() } + fadeOut(androidx.compose.animation.core.tween(180))
                }
            ) {
                composable(Screen.Home.route) {
                    HomeScreenModern(
                        viewModel = viewModel,
                        onNavigateToScreen = { route -> navController.navigate(route) }
                    )
                }
                composable(Screen.History.route) { TransactionHistoryScreen(viewModel) }
                composable(
                    route = "loans_hub?tab={tab}",
                    arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 })
                ) { backStackEntry ->
                    LoansHubScreen(
                        viewModel = viewModel,
                        onNavigateToDetail     = { id -> navController.navigate("customer/$id") },
                        onNavigateToDebtDetail = { id -> navController.navigate("debt/$id") },
                        onNavigateToCalendar   = { navController.navigate(Screen.Calendar.route) },
                        // 3.2.63 — plumbed through so the Payoff plan tile inside
                        // the Owed-tab DebtScreen actually navigates instead of
                        // hitting the default no-op lambda.
                        onNavigateToPayoffPlan = { navController.navigate(Screen.DebtPayoff.route) },
                        initialTab = backStackEntry.arguments?.getInt("tab") ?: 0,
                    )
                }
                composable(Screen.Lending.route) {
                    LendingScreen(
                        viewModel = viewModel,
                        onNavigateToDetail = { id -> navController.navigate("customer/$id") },
                        onNavigateToCalendar = { navController.navigate(Screen.Calendar.route) }
                    )
                }
                composable(Screen.Debts.route) {
                    DebtScreen(
                        viewModel = viewModel,
                        onNavigateToDetail     = { id -> navController.navigate("debt/$id") },
                        // 3.2.62 — was navGated() which silently redirected non-Pro users
                        // to the upgrade screen, making the new payoff-plan tile feel
                        // "broken/untappable". Payoff Planner is pure math on existing
                        // data — no premium-vendor cost — so it should be free.
                        onNavigateToPayoffPlan = { navController.navigate(Screen.DebtPayoff.route) },
                    )
                }
                composable(Screen.Invest.route) { InvestmentScreen(viewModel) }
                composable(Screen.Spend.route) { SpendScreen(viewModel) }
                composable(Screen.Settings.route) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onNavigateToAbout = { navController.navigate(Screen.About.route) },
                        onNavigateToUpgrade = { navController.navigate(Screen.UpgradePro.route) }
                    )
                }
                composable(Screen.UpgradePro.route) {
                    UpgradeProScreen(onNavigateBack = { navController.navigateUp() })
                }
                composable(Screen.About.route) { AboutScreen() }
                composable(Screen.People.route) { PeopleScreen(viewModel) }
                composable(Screen.Budgets.route) { BudgetScreen(viewModel) }
                composable(Screen.Goals.route) { GoalScreen(viewModel) }
                composable(Screen.Profile.route) { ProfileScreen(onLogout = { isLoggedIn = false }, onNavigateToUpgrade = { navController.navigate(Screen.UpgradePro.route) }, viewModel = viewModel) }
                composable(Screen.Projects.route) { ProjectsScreen(viewModel, onNavigateToUpgrade = { navController.navigate(Screen.UpgradePro.route) }) }
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
                        onNavigateToBorrower = { id -> navController.navigate("customer/$id") },
                        onNavigateToDebt = { id -> navController.navigate("debt/$id") },
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
                        onNavigateToPL        = { navGated(Screen.ProfitLoss.route) },
                        onNavigateToNetWorth  = { navGated(Screen.NetWorthH.route) },
                        onNavigateToMoneyFlow = { navGated(Screen.MoneyFlow.route) },
                        onNavigateToInterest  = { navGated(Screen.InterestIncome.route) },
                        onNavigateToMonthly   = { navGated(Screen.Monthly.route) },
                        onNavigateToDebtPayoff = { navGated(Screen.DebtPayoff.route) },
                        onNavigateToLoanCalc  = { navGated(Screen.LoanCalc.route) },
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

                // C12 Stage 3 (3.2.28) — Owed-side detail screen (audit §C12 #5–#7).
                // Mirrors customer/{borrowerId}: tap a row in DebtScreen / Loans Hub
                // (Owed tab) → DebtDetailScreen hosts Pay / Edit / Delete + payment
                // history. The row itself is now action-free per audit #6.
                composable("debt/{debtId}") { backStackEntry ->
                    val debtId = backStackEntry.arguments?.getString("debtId") ?: ""
                    DebtDetailScreen(
                        debtId = debtId,
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
                                "expense" -> { addTxnIsIncome = false; showExpenseDialog = true }
                                "income"  -> { addTxnIsIncome = true; showExpenseDialog = true }
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
            .padding(horizontal = 16.dp)
            .padding(bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Quick Log",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ActionItem(Icons.Default.Remove, "Expense", SemanticRed) { onActionClick("expense") }
            ActionItem(Icons.Default.Add, "Income", Emerald500) { onActionClick("income") }
            ActionItem(Icons.Default.Handshake, "Lend", SemanticBlue) { onActionClick("lending") }
            ActionItem(Icons.Default.CreditCard, "Debt", SemanticAmber) { onActionClick("debt") }
            ActionItem(Icons.AutoMirrored.Filled.ShowChart, "Invest", Carbon500) { onActionClick("invest") }
        }

        Spacer(Modifier.height(20.dp))
        TextButton(onClick = { navController.navigate(Screen.FlowWizard.route) }) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = Emerald500)
            Spacer(Modifier.width(6.dp))
            Text("Use Smart Flow Wizard", color = Emerald500, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun ActionItem(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
            modifier = Modifier.size(54.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = color.copy(alpha = 0.12f),
                contentColor = color
            )
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

// ── Drawer helper composables ────────────────────────────────────────

@Composable
private fun LedgerAppTopBar(
    canNavigateBack: Boolean,
    isPrivacy: Boolean,
    syncStatus: SyncStatus,
    onBack: () -> Unit,
    onMenu: () -> Unit,
    onHome: () -> Unit,
    onTogglePrivacy: () -> Unit,
    onSearch: () -> Unit,
    onSyncClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LedgerShellButton(
                icon = if (canNavigateBack) Icons.AutoMirrored.Filled.ArrowBack else Icons.AutoMirrored.Filled.Segment,
                contentDescription = if (canNavigateBack) "Back" else "Menu",
                onClick = if (canNavigateBack) onBack else onMenu,
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onHome)
                    .padding(horizontal = 2.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FynloBrandMark(size = 30.dp)
                Text(
                    "Fynlo Ledger",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Emerald700,
                    ),
                )
            }
            LedgerShellButton(
                icon = if (isPrivacy) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = "Toggle Privacy",
                onClick = onTogglePrivacy,
            )
            LedgerShellButton(
                icon = Icons.Default.Search,
                contentDescription = "Search",
                onClick = onSearch,
            )
            Surface(
                onClick = onSyncClick,
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 2.dp,
                border = androidx.compose.foundation.BorderStroke(
                    0.5.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                ),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    SyncStatusBadge(status = syncStatus)
                }
            }
        }
    }
}

@Composable
private fun LedgerShellButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LedgerBottomNav(
    items: List<Screen>,
    selectedRoute: String?,
    onSelect: (Screen) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(68.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(
                0.5.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                items.forEach { screen ->
                    val selected = selectedRoute == screen.route
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onSelect(screen) }
                            .padding(top = 6.dp, bottom = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .height(30.dp)
                                .width(if (selected) 58.dp else 38.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    if (selected) Emerald100.copy(alpha = 0.92f)
                                    else Color.Transparent
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.label,
                                modifier = Modifier.size(if (selected) 22.dp else 20.dp),
                                tint = if (selected) Emerald700 else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            screen.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            ),
                            color = if (selected) Emerald700 else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

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

/**
 * C20 (3.2.44) — `accent: Boolean` gives frequently-used items a primary
 * (emerald) tint on the icon even when not selected, per audit §C20 fix #5
 * ("most-used items get fynlo_green_primary tint; rest stay grey"). The
 * selected state still overrides everything with the full emerald treatment
 * + fill background.
 */
@Composable
fun DrawerItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val iconTint = when {
        selected -> Emerald700
        accent   -> Emerald700
        else     -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val labelColor = when {
        selected -> Emerald700
        else     -> MaterialTheme.colorScheme.onSurface
    }
    NavigationDrawerItem(
        icon     = {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                modifier           = Modifier.size(20.dp),
                tint               = iconTint
            )
        },
        label    = {
            Text(
                text       = label,
                style      = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold
                ),
                color      = labelColor
            )
        },
        selected = selected,
        onClick  = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        colors   = NavigationDrawerItemDefaults.colors(
            selectedContainerColor   = Emerald100.copy(alpha = 0.85f),
            unselectedContainerColor = Color.Transparent
        ),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 1.dp).height(46.dp)
    )
}

@Composable
fun DrawerDivider() {
    HorizontalDivider(
        modifier  = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        thickness = 0.5.dp,
        color     = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
    )
}

@Composable
private fun FynloReviewDialog(
    onDismiss: () -> Unit,
    onRateNow: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Emerald500.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Star, contentDescription = null, tint = Emerald500)
            }
        },
        title = {
            Text(
                "Enjoying Fynlo Ledger?",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            )
        },
        text = {
            Text(
                "A quick Play Store review helps other people find Fynlo Ledger and tells us what to polish next.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            Button(
                onClick = onRateNow,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
            ) {
                Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Rate now")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Remind me later")
            }
        },
        shape = RoundedCornerShape(24.dp),
    )
}

private fun openPlayStore(context: android.content.Context) {
    try {
        context.startActivity(
            android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                "market://details?id=${context.packageName}".toUri(),
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
        context.startActivity(
            android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                "https://play.google.com/store/apps/details?id=${context.packageName}".toUri(),
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
