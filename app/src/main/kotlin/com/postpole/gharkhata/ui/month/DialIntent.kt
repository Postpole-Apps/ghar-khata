package com.postpole.gharkhata.ui.month

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.postpole.gharkhata.R

private const val MIN_DIGITS = 3

/**
 * Builds a `tel:` Uri from a hand-typed phone number. Keeps a leading `+` and the digits, drops everything
 * else (spaces, dashes, dots, brackets, inner pluses). Null when fewer than 3 digits are left.
 */
internal fun dialUri(raw: String): Uri? {
    val trimmed = raw.trim()
    val digits = trimmed.filter { it in '0'..'9' }
    if (digits.length < MIN_DIGITS) return null
    val prefix = if (trimmed.startsWith("+")) "+" else ""
    return Uri.parse("tel:$prefix$digits")
}

/**
 * Opens the dialer with [uri] filled in (ACTION_DIAL: no call is placed, no permission needed).
 * A missing dialer is an expected device limitation: Toast only. Anything else is also reported to [onError].
 */
internal fun launchDial(context: Context, uri: Uri, onError: (Exception) -> Unit) {
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.month_call_no_app, Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, R.string.month_call_no_app, Toast.LENGTH_SHORT).show()
        onError(e)
    }
}
