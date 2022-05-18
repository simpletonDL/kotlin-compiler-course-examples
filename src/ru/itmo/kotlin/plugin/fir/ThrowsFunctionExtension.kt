package ru.itmo.kotlin.plugin.fir

import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.declarations.FirPluginKey
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.builder.buildValueParameter
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.buildResolvedArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.*
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.fir.extensions.predicate.has
import org.jetbrains.kotlin.fir.references.builder.buildResolvedNamedReference
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.withNullability
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.ConstantValueKind
import ru.itmo.kotlin.plugin.utils.fqn

class ThrowsFunctionExtension(session: FirSession): FunctionTransformationExtension(session) {
    companion object {
        private const val GENERATED_SUFFIX = "OrNull"
    }

    object ThrowsFunctionKey : FirPluginKey()

    override val declPredicate: DeclarationPredicate = has("org.itmo.my.pretty.plugin.Throws".fqn())

    override fun generateNewFunctionName(oldName: String) =
        oldName + GENERATED_SUFFIX

    @OptIn(SymbolInternals::class)
    override fun generateBody(returnFunTarget: FirFunctionTarget, baseFunSymbol: FirNamedFunctionSymbol): FirBlock {
        // { return baseFun(...args...) }
        val returnOriginalBlock = buildSimpleBlock(session) {
            statements.add(
                buildReturnExpression {
                    target = returnFunTarget

                    result = buildFunctionCall {
                        typeRef = buildResolvedTypeRef {
                            type = baseFunSymbol.resolvedReturnType
                        }
                        calleeReference = buildResolvedNamedReference {
                            name = baseFunSymbol.name
                            resolvedSymbol = baseFunSymbol
                        }

                        val argumentMap = baseFunSymbol.fir.valueParameters.associateBy { param ->
                            buildPropertyAccessExpression {
                                typeRef = param.returnTypeRef
                                calleeReference = buildResolvedNamedReference {
                                    name = param.name
                                    resolvedSymbol = param.symbol
                                }
                            }
                        }.let {
                            val result = linkedMapOf<FirExpression, FirValueParameter>()
                            result.putAll(it)
                            result
                        }

                        argumentList = buildResolvedArgumentList(argumentMap)
                    }
                }
            )
        }

        // try { returnOriginalBlock } catch { return null }
        val tryCatch = buildTryExpression {
            tryBlock = returnOriginalBlock
            typeRef = buildResolvedTypeRef {
                type = session.builtinTypes.nothingType.type
            }
            catches.add(
                buildCatch {
                    parameter = buildValueParameter {
                        moduleData = session.moduleData
                        origin = ThrowsFunctionKey.origin
                        returnTypeRef = buildResolvedTypeRef {
                            type = session.builtinTypes.throwableType.type
                        }
                        name = Name.identifier("e")
                        symbol = FirValueParameterSymbol(Name.identifier("e"))
                        isCrossinline = false
                        isNoinline = false
                        isVararg = false
                    }

                    val catchBlock = buildSimpleBlock(session) {
                        statements.add(
                            buildReturnExpression {
                                target = returnFunTarget
                                result = buildConstExpression(null, ConstantValueKind.Null, null, setType = true)
                            }
                        )
                    }
                    block = catchBlock
                }
            )
        }

        return buildSimpleBlock(session) {
            statements.add(tryCatch)
        }
    }

    override fun makeReturnType(baseFunSymbol: FirNamedFunctionSymbol): FirResolvedTypeRef {
        return buildResolvedTypeRef {
            type = baseFunSymbol.resolvedReturnType.withNullability(ConeNullability.NULLABLE, session.typeContext)
        }
    }
}
