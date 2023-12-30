package org.jetbrains.plugins.d2.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.elementType
import com.intellij.psi.util.siblings
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.d2.*
import org.jetbrains.plugins.d2.lang.D2ElementTypes
import org.jetbrains.plugins.d2.lang.psi.D2BlockDefinition
import org.jetbrains.plugins.d2.lang.psi.D2Connector
import org.jetbrains.plugins.d2.lang.psi.D2PropertyKey
import org.jetbrains.plugins.d2.lang.psi.ShapeId

private val keywords = (SIMPLE_RESERVED_KEYWORDS + KEYWORD_HOLDERS).map {
  val builder = LookupElementBuilder.create(it)
    .withIcon(D2Icons.ATTRIBUTE)
  if (it != "style") {
    builder.withInsertHandler(ColonLookupElementInsertHandler)
  }
  builder
}

private object ColonLookupElementInsertHandler : InsertHandler<LookupElement> {
  override fun handleInsert(context: InsertionContext, item: LookupElement) {
    val caretOffset = context.editor.caretModel.offset
    val colon = ": "
    context.document.insertString(context.selectionEndOffset, colon)
    context.editor.caretModel.moveToOffset(caretOffset + colon.length)
  }
}

@VisibleForTesting
internal val varsAndClasses: List<LookupElementBuilder> = sequenceOf("vars", "classes")
  .map {
    LookupElementBuilder.create(it)
      .withIcon(D2Icons.ATTRIBUTE)
      .withInsertHandler(ColonLookupElementInsertHandler)
  }
  .toList()

private val connections = sequenceOf("--", "->", "<-", "<->")
  .map {
    LookupElementBuilder.create(it)
      .withIcon(D2Icons.CONNECTION)
  }
  .toList()

private val STYLE_VALIDATOR_KEY: Key<StyleValidator> = Key("styleValidator")

private val styleSubProperties by lazy {
  ShapeStyles.values().map { styleDescriptor ->
    LookupElementBuilder.create(styleDescriptor.keyword)
      .withIcon(AllIcons.Actions.Show)
      .withInsertHandler { context, _ ->
        val caretOffset = context.editor.caretModel.offset

        val colon = ": "
        val document = context.document
        if (document.charsSequence.get(context.selectionEndOffset) == ':') {
          return@withInsertHandler
        }

        document.insertString(context.selectionEndOffset, colon)
        context.editor.caretModel.moveToOffset(caretOffset + colon.length)

        if (styleDescriptor.completionElements != null) {
          // invoke contributor again
          AutoPopupController.getInstance(context.project).autoPopupMemberLookup(context.editor, null)
        }
      }
      .also {
        it.putUserData(STYLE_VALIDATOR_KEY, styleDescriptor.validator)
      }
  }
}

private const val spaceAndDummy = " " + CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED

private class D2BasicCompletionContributor : CompletionContributor() {
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    if (parameters.completionType != CompletionType.BASIC) {
      return
    }

    val position = parameters.position
    var parent = position.context
    if (parent !is ShapeId) {
      styleCompletion(position, parent, result)
      return
    }

    if (parent.firstChild == parent.lastChild && position.textMatches(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED)) {
      // for `test.<caret> ->` a new "shape" is created, so, we must resolve parent for it,
      // otherwise we cannot check siblings (to forbid reserved keyword completion in edges)
      val newParent = parent.parent
      if (newParent is ShapeId) {
        parent = newParent
      }
    }

    // doesn't work after the introduction of ShapeDeclaration
    // see D2CompletionTest.connection
    if (position.elementType == D2ElementTypes.ID && position.textContains(' ') && position.text.endsWith(spaceAndDummy)) {
      result.addAllElements(connections)
    } else {
      // reserved keywords are prohibited in edges
      if (parent.siblings(withSelf = false).none(::notConnectorOrArrow) &&
        parent.siblings(withSelf = false, forward = false).none(::notConnectorOrArrow)
      ) {
        val inMap = parent.context is D2BlockDefinition
        result.addAllElements(keywords)
        if (!inMap) {
          // classes and vars only in a file context
          result.addAllElements(varsAndClasses)
        }
      }
    }
  }
}

private fun styleCompletion(position: PsiElement, parent: PsiElement?, result: CompletionResultSet) {
  var startElement = position
  if (parent is PsiErrorElement) {
    // logs.style.<caret>: case (we expect STYLE_KEYWORDS, that's why PsiErrorElement)
    startElement = parent
  }

  // logs.style.<caret> case
  val dot = startElement.siblings(forward = false, withSelf = false).firstOrNull { it.elementType == D2ElementTypes.DOT }
  @Suppress("GrazieInspection")
  if (dot != null && dot.siblings(forward = false, withSelf = false).any { it.elementType == D2ElementTypes.STYLE_KEYWORD }) {
    val variants = styleSubProperties
    // filter by existing value doesn't work - logs.style.animated: "#694024" (here animated accepts only boolean value)
    // because for this case (logs.style.<caret>:) after `:` lexer goes to LABEL_STATE, where int/float/boolean is parsed as UNQUOTED_STRING,
    // it can be somehow solved, but for complexity is not worth it
    //if (parent is D2PropertyKey) {
    //  val value = parent.siblings(withSelf = false).firstOrNull { it is D2PropertyValue }
    //  if (value != null) {
    //    val valueType = value.firstChild?.elementType
    //    if (valueType != null) {
    //      // filter out variants
    //      variants = variants.filter { it.getUserData(STYLE_VALIDATOR_KEY)!!.isAcceptableValueType(valueType) }
    //    }
    //  }
    //}
    result.addAllElements(variants)
  } else if (parent != null) {
    val key = parent.siblings(forward = false).firstOrNull { it is D2PropertyKey }?.lastChild ?: return
    if (key.elementType == D2ElementTypes.STYLE_KEYWORDS) {
      result.addAllElements(ShapeStyles.fromKeyword(key.text)?.completionElements ?: return)
    }
  }
}

private fun notConnectorOrArrow(it: PsiElement) = it is D2Connector || it.elementType == D2ElementTypes.ARROW