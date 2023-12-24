package org.jetbrains.plugins.d2.editor

import com.dvd.intellij.d2.ide.utils.ColorStyleValidator
import com.intellij.openapi.editor.ElementColorProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiFileFactoryImpl
import com.intellij.psi.util.elementType
import org.jetbrains.plugins.d2.lang.D2ElementTypes
import org.jetbrains.plugins.d2.lang.D2Language
import java.awt.Color

private fun getHex(color: Color): String = String.format("#%02x%02x%02x", color.red, color.green, color.blue)

class D2ColorProvider : ElementColorProvider {
  override fun getColorFrom(element: PsiElement): Color? {
    if (element.elementType !in listOf(D2ElementTypes.ID, D2ElementTypes.STRING)) {
      return null
    }
    if (element.parent.elementType != D2ElementTypes.PROPERTY_VALUE) {
      return null
    }

    val text = element.text.removeSurrounding("\"")
    return when {
      ColorStyleValidator.COLOR_REGEX.matches(text) -> Color.decode(text)
      text in ColorStyleValidator.NAMED_COLORS -> ColorStyleValidator.NAMED_COLORS[text]
      else -> null
    }
  }

  // todo set not correctly working due to wrong psi
  override fun setColorTo(element: PsiElement, color: Color) {
    val psiManager = PsiManager.getInstance(element.project)
    val factory = PsiFileFactoryImpl(psiManager)
    val newPsi = factory.createElementFromText(
      "\"${getHex(color)}\"",
      D2Language,
      D2ElementTypes.PROPERTY_VALUE,
      element.context
    ) ?: return

    // write action?
    element.parent.replace(newPsi)
  }
}