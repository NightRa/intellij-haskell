/*
 * Copyright 2016 Rik van der Kleij
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package intellij.haskell.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.impl.PsiFileFactoryImpl
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiFileFactory, PsiManager, PsiWhiteSpace}
import intellij.haskell.psi.HaskellTypes._
import intellij.haskell.util.OSUtil
import intellij.haskell.{HaskellFile, HaskellFileType, HaskellLanguage}

object HaskellElementFactory {
  def createVarid(project: Project, name: String): Option[HaskellVarid] = {
    createElementFromText(project, name, HS_VARID).map(_.asInstanceOf[HaskellVarid])
  }

  def createConid(project: Project, name: String): Option[HaskellConid] = {
    createElementFromText(project, name, HS_CONID).map(_.asInstanceOf[HaskellConid])
  }

  def createVarsym(project: Project, name: String): Option[HaskellVarsym] = {
    createElementFromText(project, name, HS_VARSYM).map(_.asInstanceOf[HaskellVarsym])
  }

  def createConsym(project: Project, name: String): Option[HaskellConsym] = {
    createElementFromText(project, name, HS_CONSYM).map(_.asInstanceOf[HaskellConsym])
  }

  def createQualifiedNameElement(project: Project, name: String): HaskellQualifiedNameElement = {
    val haskellFile = createFileFromText(project, name)
    PsiTreeUtil.findChildOfType(haskellFile, classOf[HaskellQualifiedNameElement])
  }

  def createBody(project: Project, body: String): HaskellModuleBody = {
    val haskellFile = createFileFromText(project, body)
    PsiTreeUtil.findChildOfType(haskellFile, classOf[HaskellModuleBody])
  }

  def createTopDeclaration(project: Project, declaration: String): Option[HaskellTopDeclaration] = {
    createElementFromText(project, declaration, HS_TOP_DECLARATION).map(_.asInstanceOf[HaskellTopDeclaration])
  }

  def createLanguagePragma(project: Project, languagePragma: String): HaskellLanguagePragma = {
    val haskellFile = createFileFromText(project, languagePragma)
    PsiTreeUtil.findChildOfType(haskellFile, classOf[HaskellLanguagePragma])
  }

  def createWhiteSpace(project: Project, space: String = " ") = {
    val haskellFile = createFileFromText(project, space)
    PsiTreeUtil.findChildOfType(haskellFile, classOf[PsiWhiteSpace])
  }

  def createTab(project: Project) = {
    val tabSize = CodeStyleSettingsManager.getInstance().getCurrentSettings.getTabSize(HaskellFileType.INSTANCE)
    createWhiteSpace(project, " " * tabSize)
  }

  def createNewLine(project: Project) = {
    createFileFromText(project, OSUtil.LineSeparator.toString).getFirstChild
  }

  def createQualifier(project: Project, qualifier: String) = {
    createElementFromText(project, qualifier, HS_QUALIFIER).map(_.asInstanceOf[HaskellQualifier])
  }

  def createQConQualifier(project: Project, qConQualifier: String) = {
    createElementFromText(project, qConQualifier, HS_Q_CON_QUALIFIER).map(_.asInstanceOf[HaskellQConQualifier])
  }

  def createModid(project: Project, moduleName: String): Option[HaskellModid] = {
    val haskellModuleDeclaration = createElementFromText(project, s"module $moduleName where", HS_MODULE_DECLARATION)
    haskellModuleDeclaration.map(_.asInstanceOf[HaskellModuleDeclaration]).map(_.getModid)
  }

  def createImportDeclaration(project: Project, moduleName: String): Option[HaskellImportDeclaration] = {
    val haskellImportDeclaration = createElementFromText(project, "import " + moduleName, HS_IMPORT_DECLARATION)
    haskellImportDeclaration.map(_.asInstanceOf[HaskellImportDeclaration])
  }

  private def createFileFromText(project: Project, text: String): HaskellFile = {
    PsiFileFactory.getInstance(project).createFileFromText("a.hs", HaskellLanguage.Instance, text).asInstanceOf[HaskellFile]
  }

  private def createElementFromText(project: Project, text: String, elementType: IElementType) = {
    Option(new PsiFileFactoryImpl(PsiManager.getInstance(project)).createElementFromText(text, HaskellLanguage.Instance, elementType, null))
  }
}
