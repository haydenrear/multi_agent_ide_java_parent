package com.hayden.utilitymodule.nullable

fun <T, R> T?.mapNullable(transform: (T) -> R): R? {
    return this?.let(transform)
}

fun <T> T?.orElseGet(transform: () -> T): T {
    return this ?: transform()
}

fun <T> T?.or(transform: () -> T?): T? {
    return this ?: transform()
}

fun <T> T?.orElse(transform: T): T {
    return this ?: transform
}
