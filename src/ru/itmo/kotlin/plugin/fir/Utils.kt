package ru.itmo.kotlin.plugin.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.expressions.builder.FirBlockBuilder
import org.jetbrains.kotlin.fir.expressions.builder.buildBlock
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.resolve.scope
import org.jetbrains.kotlin.fir.scopes.FakeOverrideTypeCalculator
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.name.FqName
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
inline fun buildSimpleBlock(session: FirSession,init: FirBlockBuilder.() -> Unit = {}): FirBlock {
    contract {
        callsInPlace(init, InvocationKind.EXACTLY_ONCE)
    }

    return buildBlock(init).also {
        it.replaceTypeRef(
            buildResolvedTypeRef {
                type = session.builtinTypes.nothingType.type
            }
        )
    }
}

fun getEmptyParamsConstructor(type: ConeKotlinType, session: FirSession): FirConstructorSymbol? {
    val scope = type.scope(session, ScopeSession(), FakeOverrideTypeCalculator.DoNothing) ?: return null
    var constructor: FirConstructorSymbol? = null

    scope.processDeclaredConstructors {
        if (it.valueParameterSymbols.isEmpty()) {
            constructor = it
        }
    }
    return constructor
}

fun String.fqn(): FqName {
    return FqName(this)
}
