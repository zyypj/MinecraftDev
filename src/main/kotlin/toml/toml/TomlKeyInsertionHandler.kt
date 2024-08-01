package com.demonwav.mcdev.toml.toml

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiDocumentManager
import org.toml.lang.psi.TomlElementTypes
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.ext.elementType

/** Inserts `=` after the completed key if missing and invokes the completion popup for the value automatically */
class TomlKeyInsertionHandler(private val keyValue: TomlKeyValue) : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val hasEq = keyValue.children.any { it.elementType == TomlElementTypes.EQ }
        if (!hasEq) {
            context.document.insertString(context.tailOffset, " = ")
            PsiDocumentManager.getInstance(context.project).commitDocument(context.document)
            context.editor.caretModel.moveToOffset(context.tailOffset) // The tail offset is tracked automatically
            AutoPopupController.getInstance(context.project).scheduleAutoPopup(context.editor);
        }
    }
}
