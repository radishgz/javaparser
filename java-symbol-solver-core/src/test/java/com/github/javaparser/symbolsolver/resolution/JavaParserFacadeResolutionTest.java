/*
 * Copyright 2016 Federico Tomassetti
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

package com.github.javaparser.symbolsolver.resolution;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.symbolsolver.javaparser.Navigator;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.model.declarations.TypeDeclaration;
import com.github.javaparser.symbolsolver.model.methods.MethodUsage;
import com.github.javaparser.symbolsolver.model.typesystem.ReferenceType;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import org.junit.Test;

import static org.junit.Assert.assertEquals;


public class JavaParserFacadeResolutionTest extends AbstractResolutionTest {

    @Test
    public void typeDeclarationSuperClassImplicitlyIncludeObject() throws ParseException {
        CompilationUnit cu = parseSample("Generics");
        ClassOrInterfaceDeclaration clazz = Navigator.demandClass(cu, "Generics");
        TypeDeclaration typeDeclaration = JavaParserFacade.get(new ReflectionTypeSolver()).getTypeDeclaration(clazz);
        ReferenceType superclass = typeDeclaration.asClass().getSuperClass();
        assertEquals(Object.class.getCanonicalName(), superclass.getQualifiedName());
    }

    // See issue 42
    @Test
    public void solvingReferenceToUnsupportedOperationException() {
        String code = "public class Bla {\n" +
                "    public void main()\n" +
                "    {\n" +
                "        try\n" +
                "        {\n" +
                "            int i = 0;\n" +
                "        }\n" +
                "        catch (UnsupportedOperationException e)\n" +
                "        {\n" +
                "            String s;\n" +
                "            e.getMessage();\n" +
                "        }\n" +
                "    }\n" +
                "}";
        MethodCallExpr methodCallExpr = Navigator.findNodeOfGivenClass(JavaParser.parse(code), MethodCallExpr.class);
        MethodUsage methodUsage = JavaParserFacade.get(new ReflectionTypeSolver()).solveMethodAsUsage(methodCallExpr);
        assertEquals("java.lang.Throwable.getMessage()", methodUsage.getQualifiedSignature());
    }
}
