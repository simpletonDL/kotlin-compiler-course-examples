package ru.itmo.kotlin.plugin.fir

import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.declarations.FirPluginKey
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.builder.buildSimpleFunction
import org.jetbrains.kotlin.fir.declarations.builder.buildValueParameter
import org.jetbrains.kotlin.fir.declarations.builder.buildValueParameterCopy
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.buildResolvedArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.*
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.predicate.has
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.references.builder.buildExplicitThisReference
import org.jetbrains.kotlin.fir.references.builder.buildImplicitThisReference
import org.jetbrains.kotlin.fir.references.builder.buildResolvedNamedReference
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.withNullability
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.ConstantValueKind
import ru.itmo.kotlin.plugin.utils.fqn

class ThrowsFunctionExtension(session: FirSession): FirDeclarationGenerationExtension(session) {
    companion object {
        private const val GENERATED_SUFFIX = "OrNull"
        private val THROWS_ANNOTATION_PREDICATE = has("org.itmo.my.pretty.plugin.Throws".fqn())
    }

    object ThrowsFunctionKey : FirPluginKey() {
        override fun toString(): String = "ThrowsFunctionKey"
    }

    private val predicateBaseProvider = session.predicateBasedProvider

    private val matchedFunction: List<FirNamedFunctionSymbol> by lazy {
        predicateBaseProvider.getSymbolsByPredicate(THROWS_ANNOTATION_PREDICATE)
            .filterIsInstance<FirNamedFunctionSymbol>()
    }

    private val baseFunSymbolsTotal: Map<CallableId, FirNamedFunctionSymbol> by lazy {
        matchedFunction.associateBy { symbol ->
            val genName = Name.identifier(symbol.callableId.callableName.asString() + GENERATED_SUFFIX)
            symbol.callableId.copy(callableName = genName)
        }
    }

    private val baseFunSymbolsTopLevel: Map<CallableId, FirNamedFunctionSymbol> by lazy {
        baseFunSymbolsTotal.filter { (_, symbol) ->
            symbol.callableId.className == null
        }
    }

    private val baseFunSymbolsClassLevel: Map<CallableId, FirNamedFunctionSymbol> by lazy {
        baseFunSymbolsTotal.filter { (_, symbol) ->
            symbol.callableId.classId != null
        }
    }

    private val callableNamesForClass: Map<ClassId, MutableSet<Name>> by lazy {
        return@lazy buildMap {
            for (genId in baseFunSymbolsClassLevel.keys) {
                val interestedSet = getOrPut(genId.classId!!) { mutableSetOf() }
                interestedSet.add(genId.callableName)
            }
        }
    }

    @OptIn(SymbolInternals::class)
    override fun generateFunctions(callableId: CallableId, owner: FirClassSymbol<*>?): List<FirNamedFunctionSymbol> {
        if (callableId !in baseFunSymbolsTotal) {
            return emptyList()
        }

        val baseFunSymbol = baseFunSymbolsTotal[callableId]!!
        val returnFunTarget = FirFunctionTarget(callableId.callableName.asString(), false)


        // { return baseFun(...args...) }
        val returnOriginalBlock = buildBlock {
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

                        if (owner != null) {
                            dispatchReceiver = buildThisReceiverExpression {
                                typeRef = buildResolvedTypeRef {
                                    type = owner.defaultType()
                                }
                                calleeReference = buildImplicitThisReference {
                                    boundSymbol = owner
                                }
                            }
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
        returnOriginalBlock.replaceTypeRef(
            buildResolvedTypeRef {
                type = session.builtinTypes.nothingType.type
            }
        )

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

                    val catchBlock = buildBlock {
                        statements.add(
                            buildReturnExpression {
                                target = returnFunTarget
                                result = buildConstExpression(null, ConstantValueKind.Null, null, setType = true)
                            }
                        )
                    }
                    catchBlock.replaceTypeRef(
                        buildResolvedTypeRef {
                            type = session.builtinTypes.nothingType.type
                        }
                    )
                    block = catchBlock
                }
            )
        }

        val genFun = buildSimpleFunction {
            baseFunSymbol.fir.valueParameters.forEach { original ->
                valueParameters.add(
                    buildValueParameterCopy(original) {}
                )
            }

            resolvePhase = FirResolvePhase.BODY_RESOLVE
            moduleData = session.moduleData
            origin = ThrowsFunctionKey.origin
            status = baseFunSymbol.resolvedStatus
            returnTypeRef = buildResolvedTypeRef {
                type = baseFunSymbol.resolvedReturnType.withNullability(ConeNullability.NULLABLE, session.typeContext)
            }
            name = callableId.callableName
            symbol = FirNamedFunctionSymbol(callableId)

            val bodyBlock = buildBlock {
                statements.add(tryCatch)
            }
            bodyBlock.replaceTypeRef(
                buildResolvedTypeRef {
                    type = session.builtinTypes.nothingType.type
                }
            )
            body = bodyBlock
        }

        returnFunTarget.bind(genFun)

        return listOf(genFun.symbol)
    }

    override fun getTopLevelCallableIds(): Set<CallableId> {
        return baseFunSymbolsTopLevel.keys
    }

    override fun getCallableNamesForClass(classSymbol: FirClassSymbol<*>): Set<Name> {
        return callableNamesForClass[classSymbol.classId] ?: setOf()
    }

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(THROWS_ANNOTATION_PREDICATE)
    }
}
