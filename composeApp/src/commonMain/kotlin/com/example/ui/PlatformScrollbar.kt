package com.example.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun PlatformLazyColumnScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier
)

@Composable
expect fun PlatformLazyGridScrollbar(
    state: LazyGridState,
    modifier: Modifier = Modifier
)

@Composable
expect fun PlatformColumnScrollbar(
    state: ScrollState,
    modifier: Modifier = Modifier
)
