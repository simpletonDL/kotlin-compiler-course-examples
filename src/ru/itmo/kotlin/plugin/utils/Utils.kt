package ru.itmo.kotlin.plugin.utils

import org.jetbrains.kotlin.name.FqName

fun String.fqn(): FqName {
    return FqName(this)
}