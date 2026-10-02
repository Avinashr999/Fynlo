package app.fynlo.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.fynlo.ui.components.FormDialog
import app.fynlo.ui.components.FormPrimaryButton
import app.fynlo.ui.components.FynloConfirmDialog
import app.fynlo.ui.theme.FynloTheme
import app.fynlo.ui.theme.ThemeController
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LedgerFormDesignTest {
    @get:Rule val compose = createComposeRule()

    @After fun resetTheme() { ThemeController.darkModeOverride = null }

    @Test fun lightFormKeepsCloseVisibleAndSaveDisabledUntilInput() {
        ThemeController.darkModeOverride = false
        compose.setContent {
            FynloTheme {
                var amount by remember { mutableStateOf("") }
                FormDialog("New entry", onDismiss = {}) {
                    OutlinedTextField(amount, { amount = it }, label = { Text("Amount") })
                    FormPrimaryButton("Save entry", {}, enabled = amount.isNotBlank())
                }
            }
        }
        compose.onNodeWithContentDescription("Close").assertIsDisplayed()
        compose.onNodeWithText("Save entry").assertIsNotEnabled()
        compose.onNodeWithText("Amount").performTextInput("1234567.89")
        compose.onNodeWithText("Save entry").performScrollTo().assertIsEnabled()
    }

    @Test fun darkLongFormScrollsWithoutLosingHeaderAtLargeText() {
        ThemeController.darkModeOverride = true
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.6f)) {
                FynloTheme {
                    FormDialog("Edit recurring transaction", onDismiss = {}) {
                        repeat(16) {
                            Text("Field $it")
                            Spacer(Modifier.height(32.dp))
                        }
                        FormPrimaryButton("Save changes", {})
                    }
                }
            }
        }
        compose.onNodeWithText("Save changes").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Close").assertIsDisplayed()
        compose.onNodeWithText("Edit recurring transaction").assertIsDisplayed()
    }

    @Test fun longConfirmationActionsRemainReachable() {
        var confirmed = false
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.6f)) {
                FynloTheme {
                    FynloConfirmDialog(
                        title = "Confirm this correction",
                        message = "The selected record will be updated. Review the account and amount before continuing.",
                        confirmText = "Confirm and save correction",
                        dismissText = "Keep current record",
                        onConfirm = { confirmed = true },
                        onDismiss = {},
                    )
                }
            }
        }
        compose.onNodeWithText("Keep current record").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Confirm and save correction").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(confirmed) }
    }
}
