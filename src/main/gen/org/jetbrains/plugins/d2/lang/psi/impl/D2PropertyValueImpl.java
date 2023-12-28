// This is a generated file. Not intended for manual editing.
package org.jetbrains.plugins.d2.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.d2.lang.D2CompositeElementImpl;
import org.jetbrains.plugins.d2.lang.psi.D2BlockString;
import org.jetbrains.plugins.d2.lang.psi.D2PropertyValue;
import org.jetbrains.plugins.d2.lang.psi.D2Visitor;

public class D2PropertyValueImpl extends D2CompositeElementImpl implements D2PropertyValue {

  public D2PropertyValueImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull D2Visitor visitor) {
    visitor.visitPropertyValue(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof D2Visitor) accept((D2Visitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public D2BlockString getBlockString() {
    return findChildByClass(D2BlockString.class);
  }

}
