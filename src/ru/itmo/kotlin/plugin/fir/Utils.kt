package ru.itmo.kotlin.plugin.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.expressions.builder.FirBlockBuilder
import org.jetbrains.kotlin.fir.expressions.builder.buildBlock
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
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