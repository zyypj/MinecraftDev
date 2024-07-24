package com.demonwav.mcdev.platform.mixin.framework

import com.demonwav.mcdev.asset.PlatformAssets
import com.demonwav.mcdev.platform.mixin.util.isMixin
import com.intellij.ide.IconProvider
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement

class MixinIconProvider : IconProvider() {
    override fun getIcon(element: PsiElement, flags: Int) =
        PlatformAssets.MIXIN_ICON.takeIf { element is PsiClass && element.isMixin }
}
