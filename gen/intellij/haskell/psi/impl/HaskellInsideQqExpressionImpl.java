// This is a generated file. Not intended for manual editing.
package intellij.haskell.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static intellij.haskell.psi.HaskellTypes.*;
import intellij.haskell.psi.*;

public class HaskellInsideQqExpressionImpl extends HaskellCompositeElementImpl implements HaskellInsideQqExpression {

  public HaskellInsideQqExpressionImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull HaskellVisitor visitor) {
    visitor.visitInsideQqExpression(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof HaskellVisitor) accept((HaskellVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<HaskellQName> getQNameList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, HaskellQName.class);
  }

  @Override
  @NotNull
  public List<HaskellQuasiQuote> getQuasiQuoteList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, HaskellQuasiQuote.class);
  }

  @Override
  @NotNull
  public List<HaskellSccPragma> getSccPragmaList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, HaskellSccPragma.class);
  }

}
