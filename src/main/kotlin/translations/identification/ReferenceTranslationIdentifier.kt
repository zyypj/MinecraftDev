/*
 * Minecraft Development for IntelliJ
 *
 * https://mcdev.io/
 *
 * Copyright (C) 2024 minecraft-dev
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, version 3.0 only.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.demonwav.mcdev.translations.identification

import com.intellij.codeInsight.completion.CompletionUtilCore
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.uast.UField
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.resolveToUElement

class ReferenceTranslationIdentifier : TranslationIdentifier<UReferenceExpression>() {
    override fun identify(element: UReferenceExpression): TranslationInstance? {
        val reference = element.resolveToUElement() ?: return null
        val statement = element.uastParent ?: return null
        val project = element.sourcePsi?.project ?: return null

        if (reference is UField) {
            val scope = GlobalSearchScope.allScope(project)
            val stringClass =
                JavaPsiFacade.getInstance(project).findClass("java.lang.String", scope) ?: return null
            val isConstant = reference.isStatic && reference.isFinal
            val type = reference.type as? PsiClassReferenceType ?: return null
            val resolved = type.resolve() ?: return null
            if (isConstant && (resolved.isEquivalentTo(stringClass) || resolved.isInheritor(stringClass, true))) {
                val referenceElement = reference.uastInitializer as? ULiteralExpression ?: return null
                val result = identify(project, element, statement, referenceElement)

                return result?.copy(
                    key = result.key.copy(
                        infix = result.key.infix.replace(
                            CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED,
                            "",
                        ),
                    ),
                )
            }
        }

        return null
    }

    override fun elementClass(): Class<UReferenceExpression> = UReferenceExpression::class.java
}
