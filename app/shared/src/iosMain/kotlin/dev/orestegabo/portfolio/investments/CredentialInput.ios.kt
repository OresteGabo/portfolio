@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package dev.orestegabo.portfolio.investments

import androidx.compose.ui.text.input.PlatformImeOptions
import platform.UIKit.*

actual fun credentialImeOptions(input: CredentialInput): PlatformImeOptions? = PlatformImeOptions {
    textContentType(when (input) {
        CredentialInput.USERNAME -> UITextContentTypeUsername
        CredentialInput.PASSWORD -> UITextContentTypePassword
        CredentialInput.NEW_PASSWORD -> UITextContentTypeNewPassword
    })
    autocapitalizationType(UITextAutocapitalizationType.UITextAutocapitalizationTypeNone)
    autocorrectionType(UITextAutocorrectionType.UITextAutocorrectionTypeNo)
}
