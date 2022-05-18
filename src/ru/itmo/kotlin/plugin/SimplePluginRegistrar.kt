package ru.itmo.kotlin.plugin

import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import ru.itmo.kotlin.plugin.fir.GenerateOrThrowExtension
import ru.itmo.kotlin.plugin.fir.GenerateOrNullFunctionExtension

class SimplePluginRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
//        +::SimpleClassGenerator
        +::GenerateOrNullFunctionExtension
        +::GenerateOrThrowExtension
    }
}
