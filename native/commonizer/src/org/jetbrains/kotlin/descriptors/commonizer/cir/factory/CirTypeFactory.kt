/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.descriptors.commonizer.cir.factory

import org.jetbrains.kotlin.descriptors.ClassifierDescriptor
import org.jetbrains.kotlin.descriptors.ClassifierDescriptorWithTypeParameters
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.descriptors.commonizer.cir.*
import org.jetbrains.kotlin.descriptors.commonizer.cir.impl.CirSimpleTypeImpl
import org.jetbrains.kotlin.descriptors.commonizer.utils.Interner
import org.jetbrains.kotlin.descriptors.commonizer.utils.declarationDescriptor
import org.jetbrains.kotlin.types.*

object CirTypeFactory {
    private val interner = Interner<CirSimpleType>()

    fun create(source: KotlinType): CirType = source.unwrap().run {
        when (this) {
            is SimpleType -> create(this)
            is FlexibleType -> CirFlexibleType(create(lowerBound), create(upperBound))
        }
    }

    fun create(source: SimpleType, useAbbreviation: Boolean = true): CirSimpleType {
        @Suppress("NAME_SHADOWING")
        val source = if (useAbbreviation && source is AbbreviatedType) source.abbreviation else source
        val classifierDescriptor: ClassifierDescriptor = source.declarationDescriptor

        return if (classifierDescriptor is ClassifierDescriptorWithTypeParameters) {
            val arguments = source.arguments.map { projection ->
                CirTypeProjection(
                    projectionKind = projection.projectionKind,
                    isStarProjection = projection.isStarProjection,
                    type = create(projection.type)
                )
            }

            createWithAllOuterTypes(
                classifierDescriptor = classifierDescriptor,
                arguments = arguments,
                isMarkedNullable = source.isMarkedNullable
            )
        } else {
            create(
                classifierId = CirClassifierIdFactory.create(classifierDescriptor),
                outerType = null,
                visibility = DescriptorVisibilities.UNKNOWN,
                arguments = emptyList(),
                isMarkedNullable = source.isMarkedNullable
            )
        }
    }

    fun create(
        classifierId: CirClassifierId,
        outerType: CirSimpleType?,
        visibility: DescriptorVisibility,
        arguments: List<CirTypeProjection>,
        isMarkedNullable: Boolean
    ): CirSimpleType {
        return interner.intern(
            CirSimpleTypeImpl(
                classifierId = classifierId,
                outerType = outerType,
                visibility = visibility,
                arguments = arguments,
                isMarkedNullable = isMarkedNullable
            )
        )
    }

    private fun createWithAllOuterTypes(
        classifierDescriptor: ClassifierDescriptorWithTypeParameters,
        arguments: List<CirTypeProjection>,
        isMarkedNullable: Boolean
    ): CirSimpleType {
        val outerType: CirSimpleType?
        val remainingArguments: List<CirTypeProjection>

        if (classifierDescriptor.isInner) {
            val declaredTypeParametersCount = classifierDescriptor.declaredTypeParameters.size
            outerType = createWithAllOuterTypes(
                classifierDescriptor = classifierDescriptor.containingDeclaration as ClassifierDescriptorWithTypeParameters,
                arguments = arguments.subList(0, arguments.size - declaredTypeParametersCount),
                isMarkedNullable = false // don't pass nullable flag to outer types
            )
            remainingArguments = arguments.subList(arguments.size - declaredTypeParametersCount, arguments.size)
        } else {
            outerType = null
            remainingArguments = arguments
        }

        return create(
            classifierId = CirClassifierIdFactory.create(classifierDescriptor),
            outerType = outerType,
            visibility = classifierDescriptor.visibility,
            arguments = remainingArguments,
            isMarkedNullable = isMarkedNullable
        )
    }
}
