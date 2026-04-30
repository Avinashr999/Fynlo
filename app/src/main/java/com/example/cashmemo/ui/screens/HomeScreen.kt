package com.example.cashmemo.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cashmemo.FinanceViewModel
import com.example.cashmemo.ui.components.DataPoint
import com.example.cashmemo.ui.components.PolishedSummaryCard
import com.example.cashmemo.ui.components.SpendingAnalyticsCard
import com.example.cashmemo.ui.components.WealthDistributionBar
import java.util.Locale

@Composable
fun HomeScreen(viewModel: FinanceViewModel, onNavigateToScreen: (String) -> Unit = {}) {
    val summary by viewModel.financialSummary.collectAsState()
    val analytics by viewModel.expenseAnalytics.collectAsState()
    val haptic = LocalHapticFeedback.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Financial Snapshot",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // 1. Net Worth (Total Equity)
        AnimatedContent(
            targetState = summary.netWorth,
            transitionSpec = {
                slideInVertically { height -> height } + fadeIn() togetherWith
                        slideOutVertically { height -> -height } + fadeOut()
            }, label = "NetWorthAnimation"
        ) { targetNetWorth ->
            Box(modifier = Modifier.clickable { onNavigateToScreen("history") }) {
                PolishedSummaryCard(
                    title = "Total Net Worth",
                    amount = targetNetWorth,
                    subtitle = "Total Assets - Total Liabilities",
                    icon = Icons.Default.AccountBalanceWallet,
                    gradientColors = if (targetNetWorth >= 0) 
                        listOf(Color(0xFF1E88E5), Color(0xFF1565C0)) 
                    else 
                        listOf(Color(0xFFE53935), Color(0xFFC62828))
                )
            }
        }

        WealthDistributionBar(
            cash = summary.totalCash,
            investments = summary.totalInvestments,
            receivables = summary.totalReceivables
        )

        // NEW: SPENDING ANALYTICS CARD
        SpendingAnalyticsCard(data = analytics)

        Spacer(modifier = Modifier.height(16.dp))

        // 2. TOTAL ASSETS CARD
        Text(text = "Wealth & Assets", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Card(
            modifier = Modifier.padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                DataPoint(
                    "TOTAL ASSETS (Sum)", 
                    "₹ ${String.format(Locale.getDefault(), "%,.0f", summary.totalAssets)}",
                    valueColor = MaterialTheme.colorScheme.primary
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                // Account passbooks
                summary.accountBreakdown.forEach { (name, balance) ->
                    Box(Modifier.clickable { onNavigateToScreen("statement/$name") }) {
                        DataPoint(name, "₹ ${String.format(Locale.getDefault(), "%,.0f", balance)}")
                    }
                }
                
                Box(Modifier.clickable { onNavigateToScreen("invest") }) {
                    DataPoint("Market Value of Invest.", "₹ ${String.format(Locale.getDefault(), "%,.0f", summary.totalInvestments)}")
                }
                Box(Modifier.clickable { onNavigateToScreen("lending") }) {
                    DataPoint("Lending Receivables", "₹ ${String.format(Locale.getDefault(), "%,.0f", summary.totalReceivables)}")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 3. TOTAL LIABILITIES CARD
        Text(text = "Debts & Liabilities", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Card(
            modifier = Modifier.padding(vertical = 8.dp).clickable { onNavigateToScreen("debts") },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE).copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                val totalLiab = summary.totalDebtPrincipal + summary.totalDebtInterest
                DataPoint(
                    "TOTAL LIABILITIES", 
                    "₹ ${String.format(Locale.getDefault(), "%,.0f", totalLiab)}",
                    valueColor = Color(0xFFD32F2F)
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                DataPoint("Debt Principal Amount", "₹ ${String.format(Locale.getDefault(), "%,.0f", summary.totalDebtPrincipal)}")
                DataPoint("Debt Accrued Interest", "₹ ${String.format(Locale.getDefault(), "%,.0f", summary.totalDebtInterest)}", valueColor = Color(0xFFD32F2F))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Performance Metrics (Growth & Yield)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PerformanceCard(
                label = "Invest. Growth",
                value = "₹ ${String.format(Locale.getDefault(), "%,.0f", summary.investmentGrowth)}",
                color = Color(0xFF2E7D32),
                modifier = Modifier.weight(1f).clickable { onNavigateToScreen("invest") }
            )
            PerformanceCard(
                label = "Avg. Lending Rate",
                value = "${String.format(Locale.getDefault(), "%.1f", summary.lendingYield)}%",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f).clickable { onNavigateToScreen("lending") }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { 
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.populateDummyData() 
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Reset & Load Sample Data", fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(140.dp))
    }
}

@Composable
fun PerformanceCard(label: String, value: String, color: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
            Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = color)
        }
    }
}