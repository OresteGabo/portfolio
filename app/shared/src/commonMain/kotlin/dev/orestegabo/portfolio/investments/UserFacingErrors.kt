package dev.orestegabo.portfolio.investments

internal const val GENERAL_ERROR_MESSAGE = "Something went wrong. Please try again."
internal const val CONNECTION_ERROR_MESSAGE = "Could not connect. Check your connection and try again."

/** Exact, app-authored messages only. Never trust free-form exception or server response text. */
private val approvedErrorMessages = setOf(
    "Bids are unavailable for this form or the subscription window has closed.",
    "This subscription window is closed or its issuance month has not started.",
    "The application form details could not be verified. Open the form or contact the owner.",
    "The bid could not be verified. Refresh your portfolio before retrying.",
    "This bid identity is already in use.",
    "You cannot sell more shares than remain.",
    "Sale charges cannot exceed proceeds.",
    "Remove linked transactions before editing this purchase.",
    "Remove linked transactions before deleting this purchase.",
    "Invalid transaction identity.",
    "This transaction identity is already in use.",
    "Transaction not found.",
    "Use the purchase currency and a date on or after purchase.",
    PDF_READ_ERROR,
    PUBLIC_DATA_PERMISSION_ERROR,
    "Only the owner can upload bond application forms.",
    "The application form could not be saved. Try again.",
    "Choose a published calendar issuance for this application form.",
    "A different application form is already saved for this issuance, or this PDF belongs to another issuance.",
    "This PDF could not be displayed.",
    "Statement PDF saved and records checked. Could not refresh the portfolio. Pull down on Home to update; do not submit it again.",
    "Finish or cancel the current import first.",
    "The saved purchase differs from this PDF. Review your portfolio before importing again.",
    "This import is already complete.",
    "This is a different PDF. Start a new import for this file.",
    "This import may already be saved. Check your portfolio before trying again.",
    "Statement results could not be verified. Try again.",
    "Finish the waiting PDF before sharing another one.",
    "The selected file is not a PDF.",
    "A share sheet is already open.",
    "This pending purchase is no longer available. Refresh your portfolio.",
    "The purchase changed. Review its latest details before confirming.",
    "This PDF does not match the pending bond or applied amount.",
    "Use your personal PDF to confirm the final allocation and price.",
    "PDF saved and purchase confirmed. Could not refresh the portfolio. Pull down on Home to update; do not submit it again.",
    "Choose the PDF again before saving.",
    "Some issuance details could not be verified. Choose a clearer BNR market results PDF.",
    "The issuance report could not be saved. Try again.",
    "This bond issuance is already saved with different figures. Review the existing report.",
    "Bond issuance and PDF saved. Could not refresh the portfolio. Pull down on Home to update; do not submit it again.",
    "This issuance is already saved. No extra copy was stored. Could not refresh the portfolio. Pull down on Home to update; do not submit it again.",
    "A document picker is already open.",
    "A file picker is already open.",
    "A save dialog is already open.",
    "Account deletion failed.",
    "Account deletion was not confirmed. Please try again.",
    "Authentication required.",
    "Cash injections must fit the horizon.",
    "The saved confirmation PDF could not be linked. Refresh Documents and try again.",
    "The saved purchase differs from this PDF. Review it before attaching evidence.",
    "This purchase already has another confirmation PDF.",
    "Check the details and try again.",
    "Check the purchase details.",
    "Check the share details.",
    "Choose 1, 2, 3, 4, 6, or 12 coupon payments per year.",
    "Choose a PDF of up to 10 MB.",
    "Choose a bond that has not matured.",
    "Choose a published bond.",
    "Choose the PDF again before confirming.",
    "Contribution periods must fit the horizon.",
    "Could not connect. Check your connection and try again.",
    "Could not open the selected PDF.",
    "Could not read the selected PDF.",
    "Could not read this PDF.",
    "Could not read this purchase PDF. Try a clearer copy.",
    "Could not recognize this PDF. Use a BNR bond result, share purchase contract or BK Capital statement.",
    "Could not remove saved sign-in.",
    "Could not securely save your sign-in.",
    "Coupon assumptions must be between 10.65% and 13.5%, as in the web model.",
    "Device authentication is already open.",
    "Device authentication is unavailable.",
    "Email or password is incorrect.",
    "Enter a positive whole RWF amount.",
    "Enter a positive whole number of shares.",
    "Enter an invitation and matching passwords of at least 15 characters.",
    "Enter the bond name.",
    "Enter valid planning assumptions.",
    "Face value must be positive.",
    "Invalid contribution amount.",
    "Invalid document URL.",
    "Invalid end month.",
    "Invalid injection amount.",
    "Invalid injection month.",
    "Invalid start month.",
    "No purchase details were found.",
    "No purchases were found in this statement.",
    "Only invited accounts can be deleted here.",
    "Password did not match. Try again.",
    "Purchase and PDF saved. Could not refresh the portfolio. Pull down on Home to update; do not submit it again.",
    "Purchase deleted. Could not refresh the portfolio. Pull down on Home to update; do not submit it again.",
    "Purchase minimum must be positive.",
    "Purchase saved to your portfolio. Could not refresh the portfolio. Pull down on Home to update; do not submit it again.",
    "Reinvestment and fill rates must be 0–100%.",
    "Saved sign-in belongs to another server.",
    "Saved sign-in is invalid.",
    "Saved sign-in is invalid. Use your password to sign in again.",
    "Saved sign-in is too large.",
    "Saved sign-in is unavailable. Use your password to sign in again.",
    "Select at least one tenor.",
    "Set up Face ID, Touch ID, or a device passcode first.",
    "Set up device authentication first.",
    "Set up device biometrics or a screen lock first.",
    "Share purchase saved. Could not refresh the portfolio. Pull down on Home to update; do not submit it again.",
    "Sign-in account did not match.",
    "Some details could not be read. Try a clearer PDF.",
    "Something went wrong. Please try again.",
    "Start month must be 1–12.",
    "The PDF could not be saved. Try again.",
    "The investment operation could not be completed. Check the input and try again.",
    "The projection could not be calculated.",
    "The selected PDF is empty.",
    "The server did not return a PDF.",
    "The service is temporarily unavailable. Please try again later.",
    "This PDF does not match the pending purchase.",
    "This PDF does not match the statement purchase. Choose its confirmation document.",
    "This bond has no maturity date yet.",
    "This bond has no settlement date yet.",
    "Check the order dates.",
    "Check the order details.",
    "Enter a valid ticker.",
    "Enter a positive whole number of shares and a valid price.",
    "The order amount is too large.",
    "This order changed on another device. Refresh before editing.",
    "This order changed. Refresh before continuing.",
    "This order is already closed.",
    "Finish an existing pending order first.",
    "Order not found.",
    "Record the full requested quantity for this security. Edit the pending order first if the quantity changed.",
    "The recorded purchase is no longer available.",
    "Sign in to save a pending share order.",
    "This item already exists. Refresh and try again.",
    "Too many attempts. Please wait and try again.",
    "Unable to prepare the document.",
    "Unable to save the document.",
    "Unsupported purchase document.",
    "Use a BNR bond confirmation PDF.",
    "Use a whole horizon between 1 and 100 years.",
    "Use a whole start year between 2000 and 2199.",
    "Use an amount in RWF 100,000 increments.",
    "You do not have permission to do that.",
    "Your saved sign-in no longer works. Sign in with your password.",
)

internal fun safeErrorMessage(message: String?, fallback: String = GENERAL_ERROR_MESSAGE): String =
    message?.takeIf { it in approvedErrorMessages }
        ?: fallback.takeIf { it in approvedErrorMessages } ?: GENERAL_ERROR_MESSAGE

internal fun userErrorMessage(failure: Throwable?, fallback: String = GENERAL_ERROR_MESSAGE): String {
    if (failure is ApiException && failure.status == 400 && failure.message == "Confirmation PDF not found.")
        return "The saved confirmation PDF could not be linked. Refresh Documents and try again."
    failure?.message?.takeIf { it in approvedErrorMessages }?.let { return it }
    return if (failure is ApiException) when (failure.status) {
        401 -> if (failure.message in setOf("Invalid credentials", "Invalid credentials.", "Invalid email or password."))
            "Email or password is incorrect." else "Authentication required."
        403 -> "You do not have permission to do that."
        400, 422 -> "Check the details and try again."
        409 -> "This item already exists. Refresh and try again."
        413 -> "Choose a PDF of up to 10 MB."
        429 -> "Too many attempts. Please wait and try again."
        in 500..599 -> "The service is temporarily unavailable. Please try again later."
        else -> safeErrorMessage(null, fallback)
    } else safeErrorMessage(null, fallback)
}

/** Parser progress is presentation data too; discard arbitrary bridge/native diagnostic text. */
internal fun safePdfProgressDetail(detail: String): String {
    if (detail in setOf("Checking PDF structure…", "Loading text recognition on your device…")) return detail
    return detail.takeIf { Regex(
        "(?:Reading page \\d{1,3} of \\d{1,3}…|Recognizing page \\d{1,3} on this device…|" +
            "Recognizing page \\d{1,3} of \\d{1,3}: \\d{1,3}%|Checking coupon table on page \\d{1,3}…|\\d{1,4} / \\d{1,4})"
    ).matches(it) }.orEmpty()
}
