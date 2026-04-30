package com.example.cashmemo

sealed interface UiState {
    data object Initial : UiState
    data object Loading : UiState
    data class Success(val message: String) : UiState
    data class Error(val message: String) : UiState
}