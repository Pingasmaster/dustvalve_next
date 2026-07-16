package com.dustvalve.next.android.data.local.scanner

/** Outcome summary of a local-folder music scan. */
data class ScanResult(val added: Int, val removed: Int, val total: Int)
