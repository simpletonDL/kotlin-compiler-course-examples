package ru.itmo.kotlin.plugin.fir

import org.jetbrains.kotlin.fir.FirFunctionTarget
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.expressions.buildResolvedArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildElvisExpression
import org.jetbrains.kotlin.fir.expressions.builder.buildFunctionCall
import org.jetbrains.kotlin.fir.expressions.builder.buildReturnExpression
import org.jetbrains.kotlin.fir.expressions.builder.buildThrowExpression
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.fir.extensions.predicate.has
import org.jetbrains.kotlin.fir.references.builder.buildResolvedNamedReference
import org.jetbrains.kotlin.fir.symbols.impl.ConeClassLikeLookupTagImpl
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.types.ConeNullability
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.impl.ConeClassLikeTypeImpl
import org.jetbrains.kotlin.fir.types.typeContext
import org.jetbrains.kotlin.fir.types.withNullability
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class GenerateOrThrowExtension(session: FirSession): FunctionGenerationExtension(session) {
    companion object {
        private const val GEN_SUFFIX = "OrThrow"
    }

    override val declPredicate: DeclarationPredicate = has("org.itmo.my.pretty.plugin.GenerateOrThrow".fqn())

    override fun generateNewFunctionName(oldName: String): String {
        return oldName + GEN_SUFFIX
    }

    override fun generateBody(returnFunTarget: FirFunctionTarget, baseFunSymbol: FirNamedFunctionSymbol): FirBlock {
        val elvis =
            buildElvisExpression {
                lhs = buildFunctionCallWithSameArguments(baseFunSymbol)
                rhs = buildThrowExpression {
                    exception = buildFunctionCall {
                        val exceptionType = ConeClassLikeTypeImpl(
                            ConeClassLikeLookupTagImpl(
                                ClassId(
                                    FqName("java.lang"),
                                    FqName("IllegalStateException"),
                                    false
                                )
                            ),
                            arrayOf(),
                            false
                        )

                        typeRef = buildResolvedTypeRef {
                            type = exceptionType
                        }
                        argumentList = buildResolvedArgumentList(linkedMapOf())
                        calleeReference = buildResolvedNamedReference {
                            name = Name.identifier("IllegalStateException")
                            resolvedSymbol = getEmptyParamsConstructor(exceptionType, session) ?: throw IllegalStateException()
                        }
                    }
                }
            }
        elvis.replaceTypeRef(
            buildResolvedTypeRef {
                type = baseFunSymbol.resolvedReturnType.withNullability(ConeNullability.NOT_NULL, session.typeContext)
            }
        )
        return buildSimpleBlock(session) {
            statements.add(
                buildReturnExpression {
                    target = returnFunTarget
                    result = elvis
                }
            )
        }
    }

    override fun makeReturnType(baseFunSymbol: FirNamedFunctionSymbol): FirResolvedTypeRef {
        return buildResolvedTypeRef {
            type = baseFunSymbol.resolvedReturnType.withNullability(ConeNullability.NOT_NULL, session.typeContext)
        }
    }
}