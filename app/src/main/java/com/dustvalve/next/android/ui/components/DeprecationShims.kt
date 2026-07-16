// slack-lints' DeprecatedCall (slackhq/slack-lints#268) matches call sites by
// function NAME: when any overload in an overload set carries @Deprecated,
// every caller is flagged - including calls that resolve to non-deprecated
// overloads (kotlinc emits no deprecation warning for them). FlowRow and
// ButtonGroup both have one deprecated overload, so direct use forced
// file-wide DeprecatedCall suppressions across feature screens.
//
// These thin pass-through shims confine the suppression to this file so the
// feature files keep full DeprecatedCall coverage. Delete the shims and call
// FlowRow/ButtonGroup directly once the upstream false positive is fixed.
@file:Suppress("DeprecatedCall")

package com.dustvalve.next.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupMenuState
import androidx.compose.material3.ButtonGroupScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Pass-through for the non-deprecated [FlowRow] overload. */
@Composable
fun AppFlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable FlowRowScope.() -> Unit,
) = FlowRow(
    modifier = modifier,
    horizontalArrangement = horizontalArrangement,
    verticalArrangement = verticalArrangement,
    content = content,
)

/** Pass-through for the non-deprecated [ButtonGroup] overload. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppButtonGroup(
    overflowIndicator: @Composable (ButtonGroupMenuState) -> Unit,
    modifier: Modifier = Modifier,
    content: ButtonGroupScope.() -> Unit,
) = ButtonGroup(
    overflowIndicator = overflowIndicator,
    modifier = modifier,
    content = content,
)
