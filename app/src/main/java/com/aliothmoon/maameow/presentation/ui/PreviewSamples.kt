package com.aliothmoon.maameow.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 简单的 Compose Preview 示例
 * @author YML
 */
@Composable
fun SampleGreeting(name: String) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Hello, $name", style = MaterialTheme.typography.titleLarge)
            Text(text = "这是用于验证 Compose Preview 的示例。", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Preview(name = "Light Preview")
@Composable
fun PreviewSampleGreetingLight() {
    MaterialTheme {
        SampleGreeting(name = "Meow")
    }
}

@Preview(name = "Dark Preview", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewSampleGreetingDark() {
    MaterialTheme {
        SampleGreeting(name = "Meow (Dark)")
    }
}

