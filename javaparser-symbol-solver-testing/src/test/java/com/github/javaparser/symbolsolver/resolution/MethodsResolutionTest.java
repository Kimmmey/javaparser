/*
 * Copyright (C) 2015-2016 Federico Tomassetti
 * Copyright (C) 2017-2024 The JavaParser Team.
 *
 * This file is part of JavaParser.
 *
 * JavaParser can be used either under the terms of
 * a) the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * b) the terms of the Apache License
 *
 * You should have received a copy of both licenses in LICENCE.LGPL and
 * LICENCE.APACHE. Please refer to those files for details.
 *
 * JavaParser is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 */

package com.github.javaparser.symbolsolver.resolution;

import static com.github.javaparser.StaticJavaParser.parse;
import static org.junit.jupiter.api.Assertions.*;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.resolution.MethodUsage;
import com.github.javaparser.resolution.Navigator;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.model.SymbolReference;
import com.github.javaparser.resolution.model.typesystem.ReferenceTypeImpl;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserAnonymousClassDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserClassDeclaration;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.utils.Log;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MethodsResolutionTest extends AbstractResolutionTest {

    @AfterEach
    void resetConfiguration() {
        StaticJavaParser.setConfiguration(new ParserConfiguration());
        Log.setAdapter(new Log.SilentAdapter());
    }

    @Test
    void testConsistentMethodResultion() {
        Log.setAdapter(new Log.StandardOutStandardErrorAdapter());
        StaticJavaParser.getParserConfiguration().setSymbolResolver(new JavaSymbolSolver(new ReflectionTypeSolver()));

        CompilationUnit cu = parseSample("PlatformTestUtil");
        ClassOrInterfaceDeclaration classDeclaration = Navigator.demandClass(cu, "PlatformTestUtil");
        MethodDeclaration methodDeclaration =
                Navigator.demandMethod(classDeclaration, "assertComparisonContractNotViolated");

        ForStmt outerFor = (ForStmt) methodDeclaration.getBody().get().getStatement(0);
        ForStmt innerFor = (ForStmt) ((BlockStmt) outerFor.getBody()).getStatement(0);
        IfStmt ifStmt = (IfStmt) ((BlockStmt) innerFor.getBody()).getStatement(4);
        MethodCallExpr assertCall =
                (MethodCallExpr) ((ExpressionStmt) ((BlockStmt) ifStmt.getThenStmt()).getStatement(0)).getExpression();
        MethodCallExpr formatCall = (MethodCallExpr) assertCall.getArguments().get(0);

        boolean exception1, exception2, exception3, exception4;
        try {
            formatCall.resolve();
            exception1 = false;
        } catch (Exception e) {
            exception1 = true;
        }

        try {
            formatCall.resolve();
            exception2 = false;
        } catch (Exception e) {
            exception2 = true;
        }

        try {
            formatCall.resolve();
            exception3 = false;
        } catch (Exception e) {
            exception3 = true;
        }

        try {
            formatCall.resolve();
            exception4 = false;
        } catch (Exception e) {
            exception4 = true;
        }

        assertEquals(exception1, exception2);
        assertEquals(exception1, exception3);
        assertEquals(exception1, exception4);
    }

    @Test
    void solveMethodAccessThroughSuper() {
        CompilationUnit cu = parseSample("AccessThroughSuper");
        com.github.javaparser.ast.body.ClassOrInterfaceDeclaration clazz =
                Navigator.demandClass(cu, "AccessThroughSuper.SubClass");
        MethodDeclaration method = Navigator.demandMethod(clazz, "methodTest");
        ReturnStmt returnStmt =
                (ReturnStmt) method.getBody().get().getStatements().get(0);
        Expression expression = returnStmt.getExpression().get();

        ResolvedType ref = JavaParserFacade.get(new ReflectionTypeSolver()).getType(expression);
        assertEquals("java.lang.String", ref.describe());
    }

    @Test
    void testSuperMethodCallAnonymousClass() {
        JavaParser parser = new JavaParser();
        parser.getParserConfiguration().setSymbolResolver(new JavaSymbolSolver(new ReflectionTypeSolver()));
        CompilationUnit cu = parser.parse("" + "public class X { \n"
                        + "    java.util.List x() { \n"
                        + "        return new java.util.ArrayList() { \n"
                        + "            public int size() { \n"
                        + "                return super.size(); \n"
                        + "            } \n"
                        + "        }; \n"
                        + "    } \n"
                        + "}"
                        + "")
                .getResult()
                .get();

        MethodCallExpr expression = Navigator.findMethodCall(cu, "size").get();
        MethodUsage methodUsage =
                JavaParserFacade.get(new ReflectionTypeSolver()).solveMethodAsUsage(expression);
        assertEquals("size", methodUsage.getName());
    }

    @Test
    void testSuperMethodCallDefaultMethod() {
        JavaParser parser = new JavaParser();
        parser.getParserConfiguration().setSymbolResolver(new JavaSymbolSolver(new ReflectionTypeSolver()));
        CompilationUnit cu = parser.parse("" + "public class X { \n"
                        + "    public interface Y { \n"
                        + "        default void foo() {} \n"
                        + "    } \n"
                        + "    public class Z implements Y { \n"
                        + "        public void foo() { \n"
                        + "            Y.super.foo(); \n"
                        + "        } \n"
                        + "    } \n"
                        + "}"
                        + "")
                .getResult()
                .get();

        MethodCallExpr expression = Navigator.findMethodCall(cu, "foo").get();
        MethodUsage methodUsage =
                JavaParserFacade.get(new ReflectionTypeSolver()).solveMethodAsUsage(expression);
        assertEquals("foo", methodUsage.getName());
    }

    @Test
    void solveMethodWithClassExpressionAsParameter() {
        CompilationUnit cu = parseSample("ClassExpression");
        com.github.javaparser.ast.body.ClassOrInterfaceDeclaration clazz = Navigator.demandClass(cu, "ClassExpression");
        MethodDeclaration method = Navigator.demandMethod(clazz, "foo");
        MethodCallExpr expression = Navigator.findMethodCall(method, "noneOf").get();

        MethodUsage methodUsage =
                JavaParserFacade.get(new ReflectionTypeSolver()).solveMethodAsUsage(expression);
        assertEquals("noneOf", methodUsage.getName());
    }

    @Test
    void solveMethodInInterfaceParent() {
        CompilationUnit cu = parseSample("MethodCalls");
        ClassOrInterfaceDeclaration clazz = Navigator.demandClass(cu, "MethodCalls");

        MethodDeclaration method = Navigator.demandMethod(clazz, "inheritedInterfaceMethod");
        MethodCallExpr expression = Navigator.findMethodCall(method, "toString").get();

        TypeSolver typeSolver = new ReflectionTypeSolver();

        JavaParserFacade javaParserFacade = JavaParserFacade.get(typeSolver);
        MethodUsage call1 = javaParserFacade.solveMethodAsUsage(expression);
        assertEquals("java.lang.Object.toString()", call1.getQualifiedSignature());
    }

    @Test
    void solveMethodWithTypePromotionsToLong() {
        CompilationUnit cu = parseSample("Issue338");
        ClassOrInterfaceDeclaration clazz = Navigator.demandClass(cu, "TypePromotions");

        MethodDeclaration method = Navigator.demandMethod(clazz, "callingLong");

        {
            MethodCallExpr expression = method.getBody()
                    .get()
                    .getStatements()
                    .get(0)
                    .asExpressionStmt()
                    .getExpression()
                    .asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference =
                    JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(true, reference.isSolved());
            assertEquals("longParam", reference.getCorrespondingDeclaration().getName());
        }
        {
            MethodCallExpr expression = method.getBody()
                    .get()
                    .getStatements()
                    .get(1)
                    .asExpressionStmt()
                    .getExpression()
                    .asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference =
                    JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(true, reference.isSolved());
            assertEquals("longParam", reference.getCorrespondingDeclaration().getName());
        }
        {
            MethodCallExpr expression = method.getBody()
                    .get()
                    .getStatements()
                    .get(2)
                    .asExpressionStmt()
                    .getExpression()
                    .asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference =
                    JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(true, reference.isSolved());
            assertEquals("longParam", reference.getCorrespondingDeclaration().getName());
        }
        {
            MethodCallExpr expression = method.getBody()
                    .get()
                    .getStatements()
                    .get(3)
                    .asExpressionStmt()
                    .getExpression()
                    .asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference =
                    JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(true, reference.isSolved());
            assertEquals("longParam", reference.getCorrespondingDeclaration().getName());
        }
    }

    @Test
    void solveMethodWithTypePromotionsToInt() {
        CompilationUnit cu = parseSample("Issue338");
        ClassOrInterfaceDeclaration clazz = Navigator.demandClass(cu, "TypePromotions");

        MethodDeclaration method = Navigator.demandMethod(clazz, "callingInt");

        {
            MethodCallExpr expression = method.getBody()
                    .get()
                    .getStatements()
                    .get(0)
                    .asExpressionStmt()
                    .getExpression()
                    .asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference =
                    JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(true, reference.isSolved());
            assertEquals("intParam", reference.getCorrespondingDeclaration().getName());
        }
        {
            MethodCallExpr expression = method.getBody()
                    .get()
                    .getStatements()
                    .get(1)
                    .asExpressionStmt()
                    .getExpression()
                    .asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference =
                    JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(true, reference.isSolved());
            assertEquals("intParam", reference.getCorrespondingDeclaration().getName());
        }
        {
            MethodCallExpr expression = method.getBody()
                    .get()
                    .getStatements()
                    .get(2)
                    .asExpressionStmt()
                    .getExpression()
                    .asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference =
                    JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(true, reference.isSolved());
            assertEquals("intParam", reference.getCorrespondingDeclaration().getName());
        }
        {
            MethodCallExpr expression = method.getBody()
                    .get()
                    .getStatements()
                    .get(3)
                    .asExpressionStmt()
                    .getExpression()
                    .asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference =
                    JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(false, reference.isSolved());
        }
    }

    @Test
    void solveMethodWithTypePromotionsToShort() {
        CompilationUnit cu = parseSample("Issue338");
        ClassOrInterfaceDeclaration clazz = Navigator.demandClass(cu, "TypePromotions");

        MethodDeclaration method = Navigator.demandMethod(clazz, "callingShort");

        {
            MethodCallExpr expression = method.getBody()
                    .get()
                    .getStatements()
                    .get(0)
                    .asExpressionStmt()
                    .getExpression()
                    .asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference =
                    JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(true, reference.isSolved());
            assertEquals("shortParam", reference.getCorrespondingDeclaration().getName());
        }
        {
            MethodCallExpr expression = method.getBody()
                    .get()
                    .getStatements()
                    .get(1)
                    .asExpressionStmt()
                    .getExpression()
                    .asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference =
                    JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(true, reference.isSolved());
            assertEquals("shortParam", reference.getCorrespondingDeclaration().getName());
        }
        {
            MethodCallExpr expression = method.getBody()
                    .get()
                    .getStatements()
                    .get(2)
                    .asExpressionStmt()
                    .getExpression()
                    .asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference =
                    JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(false, reference.isSolved());
        }
        {
            MethodCallExpr expression = method.getBody()
                    .get()
                    .getStatements()
                    .get(3)
                    .asExpressionStmt()
                    .getExpression()
                    .asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference =
                    JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(false, reference.isSolved());
        }
    }

    @Test
    void solveMethodWithTypePromotionsToByte() {
        CompilationUnit cu = parseSample("Issue338");
        ClassOrInterfaceDeclaration clazz = Navigator.demandClass(cu, "TypePromotions");

        MethodDeclaration method = Navigator.demandMethod(clazz, "callingByte");

        {
            MethodCallExpr expression = method.getBody()
                    .get()
                    .getStatements()
                    .get(0)
                    .asExpressionStmt()
                    .getExpression()
                    .asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference =
                    JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(true, reference.isSolved());
            assertEquals("byteParam", reference.getCorrespondingDeclaration().getName());
        }
        {
            MethodCallExpr expression = method.getBody()
                    .get()
                    .getStatements()
                    .get(1)
                    .asExpressionStmt()
                    .getExpression()
                    .asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference =
                    JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(false, reference.isSolved());
        }
        {
            MethodCallExpr expression = method.getBody()
                    .get()
                    .getStatements()
                    .get(2)
                    .asExpressionStmt()
                    .getExpression()
                    .asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference =
                    JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(false, reference.isSolved());
        }
        {
            MethodCallExpr expression = method.getBody()
                    .get()
                    .getStatements()
                    .get(3)
                    .asExpressionStmt()
                    .getExpression()
                    .asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference =
                    JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(false, reference.isSolved());
        }
    }

    @Test
    void solveMethodWithTypePromotionsToLongWithExtraParam() {
        CompilationUnit cu = parseSample("Issue338");
        ClassOrInterfaceDeclaration clazz = Navigator.demandClass(cu, "TypePromotionsWithExtraParam");

        MethodDeclaration method = Navigator.demandMethod(clazz, "callingLong");

        {
            MethodCallExpr expression = method.getBody()
                    .get()
                    .getStatements()
                    .get(0)
                    .asExpressionStmt()
                    .getExpression()
                    .asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference =
                    JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(true, reference.isSolved());
            assertEquals("longParam", reference.getCorrespondingDeclaration().getName());
        }
        {
            MethodCallExpr expression = method.getBody()
                    .get()
                    .getStatements()
                    .get(1)
                    .asExpressionStmt()
                    .getExpression()
                    .asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference =
                    JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(true, reference.isSolved());
            assertEquals("longParam", reference.getCorrespondingDeclaration().getName());
        }
        {
            MethodCallExpr expression = method.getBody()
                    .get()
                    .getStatements()
                    .get(2)
                    .asExpressionStmt()
                    .getExpression()
                    .asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference =
                    JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(true, reference.isSolved());
            assertEquals("longParam", reference.getCorrespondingDeclaration().getName());
        }
        {
            MethodCallExpr expression = method.getBody()
                    .get()
                    .getStatements()
                    .get(3)
                    .asExpressionStmt()
                    .getExpression()
                    .asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference =
                    JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(true, reference.isSolved());
            assertEquals("longParam", reference.getCorrespondingDeclaration().getName());
        }
    }

    @Test
    void solveMethodWithTypePromotionsToIntWithExtraParam() {
        CompilationUnit cu = parseSample("Issue338");
        ClassOrInterfaceDeclaration clazz = Navigator.demandClass(cu, "TypePromotionsWithExtraParam");

        MethodDeclaration method = Navigator.demandMethod(clazz, "callingInt");

        {
            MethodCallExpr expression = method.getBody()
                    .get()
                    .getStatements()
                    .get(0)
                    .asExpressionStmt()
                    .getExpression()
                    .asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference =
                    JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(true, reference.isSolved());
            assertEquals("intParam", reference.getCorrespondingDeclaration().getName());
        }
        {
            MethodCallExpr expression = method.getBody()
                    .get()
                    .getStatements()
                    .get(1)
                    .asExpressionStmt()
                    .getExpression()
                    .asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference =
                    JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(true, reference.isSolved());
            assertEquals("intParam", reference.getCorrespondingDeclaration().getName());
        }
        {
            MethodCallExpr expression = method.getBody()
                    .get()
                    .getStatements()
                    .get(2)
                    .asExpressionStmt()
                    .getExpression()
                    .asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference =
                    JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(true, reference.isSolved());
            assertEquals("intParam", reference.getCorrespondingDeclaration().getName());
        }
        {
            MethodCallExpr expression = method.getBody()
                    .get()
                    .getStatements()
                    .get(3)
                    .asExpressionStmt()
                    .getExpression()
                    .asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference =
                    JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(false, reference.isSolved());
        }
    }

    @Test
    void solveMethodWithTypePromotionsToShortWithExtraParam() {
        CompilationUnit cu = parseSample("Issue338");
        ClassOrInterfaceDeclaration clazz = Navigator.demandClass(cu, "TypePromotionsWithExtraParam");

        MethodDeclaration method = Navigator.demandMethod(clazz, "callingShort");

        {
            MethodCallExpr expression = method.getBody()
                    .get()
                    .getStatements()
                    .get(0)
                    .asExpressionStmt()
                    .getExpression()
                    .asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference =
                    JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(true, reference.isSolved());
            assertEquals("shortParam", reference.getCorrespondingDeclaration().getName());
        }
        {
            MethodCallExpr expression = method.getBody()
                    .get()
                    .getStatements()
                    .get(1)
                    .asExpressionStmt()
                    .getExpression()
                    .asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference =
                    JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(true, reference.isSolved());
            assertEquals("shortParam", reference.getCorrespondingDeclaration().getName());
        }
        {
            MethodCallExpr expression = method.getBody()
                    .get()
                    .getStatements()
                    .get(2)
                    .asExpressionStmt()
                    .getExpression()
                    .asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference =
                    JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(false, reference.isSolved());
        }
        {
            MethodCallExpr expression = method.getBody()
                    .get()
                    .getStatements()
                    .get(3)
                    .asExpressionStmt()
                    .getExpression()
                    .asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference =
                    JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(false, reference.isSolved());
        }
    }

    @Test
    void solveMethodWithTypePromotionsToByteWithExtraParam() {
        CompilationUnit cu = parseSample("Issue338");
        ClassOrInterfaceDeclaration clazz = Navigator.demandClass(cu, "TypePromotionsWithExtraParam");

        MethodDeclaration method = Navigator.demandMethod(clazz, "callingByte");

        {
            MethodCallExpr expression = method.getBody()
                    .get()
                    .getStatements()
                    .get(0)
                    .asExpressionStmt()
                    .getExpression()
                    .asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference =
                    JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(true, reference.isSolved());
            assertEquals("byteParam", reference.getCorrespondingDeclaration().getName());
        }
        {
            MethodCallExpr expression = method.getBody()
                    .get()
                    .getStatements()
                    .get(1)
                    .asExpressionStmt()
                    .getExpression()
                    .asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference =
                    JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(false, reference.isSolved());
        }
        {
            MethodCallExpr expression = method.getBody()
                    .get()
                    .getStatements()
                    .get(2)
                    .asExpressionStmt()
                    .getExpression()
                    .asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference =
                    JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(false, reference.isSolved());
        }
        {
            MethodCallExpr expression = method.getBody()
                    .get()
                    .getStatements()
                    .get(3)
                    .asExpressionStmt()
                    .getExpression()
                    .asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference =
                    JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(false, reference.isSolved());
        }
    }

    @Test
    void callOnThisInAnonymousClass() {
        CompilationUnit cu = parseSample("ThisInAnonymousClass");
        ClassOrInterfaceDeclaration clazz = Navigator.demandClass(cu, "Bar");

        MethodCallExpr fooCall = Navigator.findMethodCall(clazz, "foo").get();

        SymbolReference<ResolvedMethodDeclaration> reference =
                JavaParserFacade.get(new ReflectionTypeSolver()).solve(fooCall);
        assertEquals(true, reference.isSolved());
    }

    @Test
    void thisInAnonymousClass() {
        CompilationUnit cu = parseSample("ThisInAnonymousClass");
        ClassOrInterfaceDeclaration clazz = Navigator.demandClass(cu, "Bar");

        ThisExpr thisExpression = Navigator.demandNodeOfGivenClass(clazz, ThisExpr.class);

        ResolvedType type = JavaParserFacade.get(new ReflectionTypeSolver()).getType(thisExpression);
        assertEquals(true, type.isReferenceType());
        assertEquals(
                true, type.asReferenceType().getTypeDeclaration().get() instanceof JavaParserAnonymousClassDeclaration);
    }

    @Test
    void resolveMethodCallWithScopeDeclarationInSwitchEntryStmt() {
        CompilationUnit cu = parseSample("TryInSwitch");
        ClassOrInterfaceDeclaration clazz = Navigator.demandClass(cu, "TryInSwitch");

        MethodDeclaration method = Navigator.demandMethod(clazz, "foo");

        MethodCallExpr callExpr = method.getBody()
                .get()
                .getStatement(1)
                .asSwitchStmt()
                .getEntry(0)
                .getStatement(1)
                .asTryStmt()
                .getTryBlock()
                .getStatement(1)
                .asExpressionStmt()
                .getExpression()
                .asMethodCallExpr();

        SymbolReference<ResolvedMethodDeclaration> reference =
                JavaParserFacade.get(new ReflectionTypeSolver()).solve(callExpr);

        assertTrue(reference.isSolved());
        assertEquals(
                "java.io.File.delete()", reference.getCorrespondingDeclaration().getQualifiedSignature());
    }

    @Test
    void complexTypeSolving() {
        CompilationUnit cu = parseSample("ComplexTypeResolving");
        ClassOrInterfaceDeclaration mainClass = Navigator.demandClass(cu, "Main");

        ClassOrInterfaceDeclaration childDec = (ClassOrInterfaceDeclaration) mainClass.getMember(1);
        ExpressionStmt stmt = (ExpressionStmt)
                Navigator.demandMethod(childDec, "foo").getBody().get().getStatement(0);
        ReferenceTypeImpl resolvedType = (ReferenceTypeImpl)
                JavaParserFacade.get(new ReflectionTypeSolver()).getType(stmt.getExpression());
        ClassOrInterfaceDeclaration resolvedTypeDeclaration =
                ((JavaParserClassDeclaration) resolvedType.getTypeDeclaration().get()).getWrappedNode();

        assertEquals(mainClass, resolvedTypeDeclaration.getParentNode().get());
    }

    @Test
    void resolveMethodCallOfMethodInMemberClassOfAnotherClass() {
        CompilationUnit cu = parseSample("NestedClasses");
        ClassOrInterfaceDeclaration classA = Navigator.demandClass(cu, "A");

        MethodDeclaration method = Navigator.demandMethod(classA, "foo");

        MethodCallExpr callExpr = method.getBody()
                .get()
                .getStatement(1)
                .asExpressionStmt()
                .getExpression()
                .asMethodCallExpr();

        SymbolReference<ResolvedMethodDeclaration> reference =
                JavaParserFacade.get(new ReflectionTypeSolver()).solve(callExpr);

        assertTrue(reference.isSolved());
        assertEquals("X.Y.bar()", reference.getCorrespondingDeclaration().getQualifiedSignature());
    }

    @Test
    void resolveMethodCallOfMethodInMemberInterfaceOfAnotherInterface() {
        CompilationUnit cu = parseSample("NestedInterfaces");
        ClassOrInterfaceDeclaration classA = Navigator.demandInterface(cu, "A");

        MethodDeclaration method = Navigator.demandMethod(classA, "foo");

        MethodCallExpr callExpr = method.getBody()
                .get()
                .getStatement(1)
                .asExpressionStmt()
                .getExpression()
                .asMethodCallExpr();

        SymbolReference<ResolvedMethodDeclaration> reference =
                JavaParserFacade.get(new ReflectionTypeSolver()).solve(callExpr);

        assertTrue(reference.isSolved());
        assertEquals("X.Y.bar()", reference.getCorrespondingDeclaration().getQualifiedSignature());
    }

    @Test
    void resolveMethodCallOfMethodInMemberInterfaceWithIdenticalNameOfAnotherInterface() {
        CompilationUnit cu = parseSample("NestedInterfacesWithIdenticalNames");
        ClassOrInterfaceDeclaration classA = Navigator.demandInterface(cu, "A");

        MethodDeclaration method = Navigator.demandMethod(classA, "foo");

        MethodCallExpr callExpr = method.getBody()
                .get()
                .getStatement(1)
                .asExpressionStmt()
                .getExpression()
                .asMethodCallExpr();

        SymbolReference<ResolvedMethodDeclaration> reference =
                JavaParserFacade.get(new ReflectionTypeSolver()).solve(callExpr);

        assertTrue(reference.isSolved());
        assertEquals("X.A.bar()", reference.getCorrespondingDeclaration().getQualifiedSignature());
    }

    @Test
    void resolveLocalMethodInClassExtendingUnknownClass() {
        // configure symbol solver before parsing
        StaticJavaParser.getConfiguration().setSymbolResolver(new JavaSymbolSolver(new ReflectionTypeSolver()));

        // parse compilation unit and get field access expression
        CompilationUnit cu = parseSample("ClassExtendingUnknownClass");
        ClassOrInterfaceDeclaration clazz = Navigator.demandClass(cu, "ClassExtendingUnknownClass");
        MethodDeclaration method = Navigator.demandMethod(clazz, "foo");
        MethodCallExpr methodCallExpr = method.getBody()
                .get()
                .getStatements()
                .get(0)
                .asExpressionStmt()
                .getExpression()
                .asMethodCallExpr();

        // resolve field access expression
        ResolvedMethodDeclaration resolvedMethodDeclaration = methodCallExpr.resolve();

        // check that the expected method declaration equals the resolved method declaration
        assertEquals(
                "ClassExtendingUnknownClass.bar(java.lang.String)", resolvedMethodDeclaration.getQualifiedSignature());
    }

    @Test
    void resolveCorrectMethodWithComplexOverloading1() {
        // configure symbol solver before parsing
        StaticJavaParser.getConfiguration().setSymbolResolver(new JavaSymbolSolver(new ReflectionTypeSolver()));

        // parse compilation unit and get method call expression
        CompilationUnit cu = parseSample("OverloadedMethods");
        ClassOrInterfaceDeclaration clazz = Navigator.demandClass(cu, "OverloadedMethods");
        MethodDeclaration testingMethod = Navigator.demandMethod(clazz, "testComplex1");
        MethodCallExpr methodCallExpr = testingMethod
                .getBody()
                .get()
                .getStatements()
                .get(0)
                .asExpressionStmt()
                .getExpression()
                .asMethodCallExpr();

        // resolve method call expression
        ResolvedMethodDeclaration resolvedMethodDeclaration = methodCallExpr.resolve();

        assertEquals(
                "OverloadedMethods.complexOverloading1(java.lang.String, java.lang.String)",
                resolvedMethodDeclaration.getQualifiedSignature());
    }

    @Test
    void resolveCorrectMethodWithComplexOverloading2() {
        // configure symbol solver before parsing
        StaticJavaParser.getConfiguration().setSymbolResolver(new JavaSymbolSolver(new ReflectionTypeSolver()));

        // parse compilation unit and get method call expression
        CompilationUnit cu = parseSample("OverloadedMethods");
        ClassOrInterfaceDeclaration clazz = Navigator.demandClass(cu, "OverloadedMethods");
        MethodDeclaration testingMethod = Navigator.demandMethod(clazz, "testComplex2");
        MethodCallExpr methodCallExpr = testingMethod
                .getBody()
                .get()
                .getStatements()
                .get(0)
                .asExpressionStmt()
                .getExpression()
                .asMethodCallExpr();

        // resolve method call expression
        ResolvedMethodDeclaration resolvedMethodDeclaration = methodCallExpr.resolve();

        assertEquals(
                "OverloadedMethods.complexOverloading2(java.lang.String...)",
                resolvedMethodDeclaration.getQualifiedSignature());
    }

    @Test
    void resolveCorrectMethodWithComplexOverloading3() {
        // configure symbol solver before parsing
        StaticJavaParser.getConfiguration().setSymbolResolver(new JavaSymbolSolver(new ReflectionTypeSolver()));

        // parse compilation unit and get method call expression
        CompilationUnit cu = parseSample("OverloadedMethods");
        ClassOrInterfaceDeclaration clazz = Navigator.demandClass(cu, "OverloadedMethods");
        MethodDeclaration testingMethod = Navigator.demandMethod(clazz, "testComplex3");
        MethodCallExpr methodCallExpr = testingMethod
                .getBody()
                .get()
                .getStatements()
                .get(0)
                .asExpressionStmt()
                .getExpression()
                .asMethodCallExpr();

        // resolve method call expression
        ResolvedMethodDeclaration resolvedMethodDeclaration = methodCallExpr.resolve();

        assertEquals("OverloadedMethods.complexOverloading3(long)", resolvedMethodDeclaration.getQualifiedSignature());
    }

    @Test
    void resolveCorrectMethodWithComplexOverloading4() {
        // configure symbol solver before parsing
        StaticJavaParser.getConfiguration().setSymbolResolver(new JavaSymbolSolver(new ReflectionTypeSolver()));

        // parse compilation unit and get method call expression
        CompilationUnit cu = parseSample("OverloadedMethods");
        ClassOrInterfaceDeclaration clazz = Navigator.demandClass(cu, "OverloadedMethods");
        MethodDeclaration testingMethod = Navigator.demandMethod(clazz, "testComplex4");
        MethodCallExpr methodCallExpr = testingMethod
                .getBody()
                .get()
                .getStatements()
                .get(0)
                .asExpressionStmt()
                .getExpression()
                .asMethodCallExpr();

        // resolve method call expression
        ResolvedMethodDeclaration resolvedMethodDeclaration = methodCallExpr.resolve();

        assertEquals(
                "OverloadedMethods.complexOverloading4(long, int)", resolvedMethodDeclaration.getQualifiedSignature());
    }

    @Test
    void methodRefWithMultipleLambdasInScope() {
        StaticJavaParser.getParserConfiguration().setSymbolResolver(new JavaSymbolSolver(new ReflectionTypeSolver()));
        CompilationUnit compilationUnit = parse("import java.util.function.Function;\n" + "@FunctionalInterface\n"
                + "interface ReturnStringFunction<T> extends Function<T, String> {\n"
                + "  String apply(T t);\n"
                + "}\n"
                + "class Foo {\n"
                + "  static <T> String foo(T t) { return null; }\n"
                + "}\n"
                + "public class Test {\n"
                + "  <T> String acceptsFunction(ReturnStringFunction<T> consumer) { return null; }\n"
                + "  void test() {\n"
                + "    acceptsFunction(Foo::foo);\n"
                + "  }\n"
                + "}");

        MethodCallExpr callExpr =
                compilationUnit.findFirst(MethodCallExpr.class).get();

        assertDoesNotThrow(callExpr::calculateResolvedType);
        assertEquals("java.lang.String", callExpr.calculateResolvedType().describe());
    }
}
