package ru.itmo.kotlin.plugin.fir

import org.jetbrains.kotlin.fir.FirFunctionTarget
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.builder.buildSimpleFunction
import org.jetbrains.kotlin.fir.declarations.builder.buildValueParameterCopy
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.buildResolvedArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildFunctionCall
import org.jetbrains.kotlin.fir.expressions.builder.buildPropertyAccessExpression
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.references.builder.buildResolvedNamedReference
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name

abstract class FunctionGenerationExtension(session: FirSession): FirDeclarationGenerationExtension(session) {
    abstract val declPredicate: DeclarationPredicate

    abstract fun generateNewFunctionName(oldName: String): String

    abstract fun generateBody(returnFunTarget: FirFunctionTarget, baseFunSymbol: FirNamedFunctionSymbol): FirBlock

    abstract fun makeReturnType(baseFunSymbol: FirNamedFunctionSymbol): FirResolvedTypeRef

    @OptIn(SymbolInternals::class)
    override fun generateFunctions(callableId: CallableId, owner: FirClassSymbol<*>?): List<FirNamedFunctionSymbol> {
        if (callableId !in generatedCallableIds) {
            return emptyList()
        }
        val baseFunSymbol = generatedCallableIds[callableId]!!
        val returnFunTarget = FirFunctionTarget(callableId.callableName.asString(), false)

        val funBody = generateBody(returnFunTarget, baseFunSymbol)

        val genFun = buildSimpleFunction {
            baseFunSymbol.fir.valueParameters.forEach { original ->
                valueParameters.add(
                    buildValueParameterCopy(original) {}
                )
            }

            resolvePhase = FirResolvePhase.BODY_RESOLVE
            moduleData = session.moduleData
            origin = GenerateOrNullFunctionExtension.ThrowsFunctionKey.origin
            status = baseFunSymbol.resolvedStatus
            returnTypeRef = makeReturnType(baseFunSymbol)
            name = callableId.callableName
            symbol = FirNamedFunctionSymbol(callableId)

            body = funBody
        }

        returnFunTarget.bind(genFun)


        return listOf(genFun.symbol)
    }

    private val predicateBaseProvider = session.predicateBasedProvider

    private val matchedFunction: List<FirNamedFunctionSymbol> by lazy {
        predicateBaseProvider.getSymbolsByPredicate(declPredicate).filterIsInstance<FirNamedFunctionSymbol>()
    }

    private val generatedCallableIds: Map<CallableId, FirNamedFunctionSymbol> by lazy {
        matchedFunction.associateBy { symbol ->
            val genName = generateNewFunctionName(symbol.callableId.callableName.asString())
            symbol.callableId.copy(callableName = Name.identifier(genName))
        }
    }

    override fun getTopLevelCallableIds(): Set<CallableId> {
        return generatedCallableIds.keys
    }

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(declPredicate)
    }

    @OptIn(SymbolInternals::class)
    protected fun buildFunctionCallWithSameArguments(baseFunSymbol: FirNamedFunctionSymbol): FirFunctionCall {
        return buildFunctionCall {
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
}