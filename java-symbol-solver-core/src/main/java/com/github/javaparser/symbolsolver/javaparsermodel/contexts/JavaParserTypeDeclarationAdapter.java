package com.github.javaparser.symbolsolver.javaparsermodel.contexts;

import java.util.List;
import java.util.stream.Collectors;

import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithTypeParameters;
import com.github.javaparser.ast.type.TypeParameter;
import com.github.javaparser.symbolsolver.core.resolution.Context;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFactory;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserTypeParameter;
import com.github.javaparser.symbolsolver.model.declarations.ClassDeclaration;
import com.github.javaparser.symbolsolver.model.declarations.ConstructorDeclaration;
import com.github.javaparser.symbolsolver.model.declarations.MethodDeclaration;
import com.github.javaparser.symbolsolver.model.declarations.ReferenceTypeDeclaration;
import com.github.javaparser.symbolsolver.model.declarations.TypeDeclaration;
import com.github.javaparser.symbolsolver.model.resolution.SymbolReference;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.model.typesystem.ReferenceType;
import com.github.javaparser.symbolsolver.model.typesystem.Type;
import com.github.javaparser.symbolsolver.reflectionmodel.ReflectionClassDeclaration;
import com.github.javaparser.symbolsolver.resolution.ConstructorResolutionLogic;
import com.github.javaparser.symbolsolver.resolution.MethodResolutionLogic;

/**
 * @author Federico Tomassetti
 */
public class JavaParserTypeDeclarationAdapter {

    private com.github.javaparser.ast.body.TypeDeclaration<?> wrappedNode;
    private TypeSolver typeSolver;
    private Context context;
    private ReferenceTypeDeclaration typeDeclaration;

    public JavaParserTypeDeclarationAdapter(com.github.javaparser.ast.body.TypeDeclaration<?> wrappedNode, TypeSolver typeSolver,
                                            ReferenceTypeDeclaration typeDeclaration,
                                            Context context) {
        this.wrappedNode = wrappedNode;
        this.typeSolver = typeSolver;
        this.typeDeclaration = typeDeclaration;
        this.context = context;
    }

    public SymbolReference<TypeDeclaration> solveType(String name, TypeSolver typeSolver) {
        if (this.wrappedNode.getName().getId().equals(name)) {
            return SymbolReference.solved(JavaParserFacade.get(typeSolver).getTypeDeclaration(wrappedNode));
        }

        // Internal classes
        for (BodyDeclaration<?> member : this.wrappedNode.getMembers()) {
            if (member instanceof com.github.javaparser.ast.body.TypeDeclaration) {
                com.github.javaparser.ast.body.TypeDeclaration<?> internalType = (com.github.javaparser.ast.body.TypeDeclaration<?>) member;
                if (internalType.getName().getId().equals(name)) {
                    return SymbolReference.solved(JavaParserFacade.get(typeSolver).getTypeDeclaration(internalType));
                } else if (name.startsWith(String.format("%s.%s", wrappedNode.getName(), internalType.getName()))) {
                    return JavaParserFactory.getContext(internalType, typeSolver).solveType(name.substring(wrappedNode.getName().getId().length() + 1), typeSolver);
                } else if (name.startsWith(String.format("%s.", internalType.getName()))) {
                    return JavaParserFactory.getContext(internalType, typeSolver).solveType(name.substring(internalType.getName().getId().length() + 1), typeSolver);
                }
            }
        }

        if (wrappedNode instanceof NodeWithTypeParameters) {
            NodeWithTypeParameters<?> nodeWithTypeParameters = (NodeWithTypeParameters<?>) wrappedNode;
            for (TypeParameter astTpRaw : (Iterable<TypeParameter>)nodeWithTypeParameters.getTypeParameters()) {
                TypeParameter astTp = (TypeParameter) astTpRaw;
                if (astTp.getName().getId().equals(name)) {
                    return SymbolReference.solved(new JavaParserTypeParameter(astTp, typeSolver));
                }
            }
        }

        return context.getParent().solveType(name, typeSolver);
    }

    public SymbolReference<MethodDeclaration> solveMethod(String name, List<Type> argumentsTypes, TypeSolver typeSolver) {
        List<MethodDeclaration> candidateMethods = typeDeclaration.getDeclaredMethods().stream()
                .filter(m -> m.getName().equals(name))
                .collect(Collectors.toList());

        for (ReferenceType ancestor : typeDeclaration.getAncestors()) {
            SymbolReference<MethodDeclaration> res = MethodResolutionLogic.solveMethodInType(ancestor.getTypeDeclaration(), name, argumentsTypes, typeSolver);
            // consider methods from superclasses and only default methods from interfaces : not true, we should keep abstract as a valid candidate
            // abstract are removed in MethodResolutionLogic.isApplicable is necessary
            if (res.isSolved() /*&& (!ancestor.getTypeDeclaration().isInterface() || res.getCorrespondingDeclaration().isDefaultMethod())*/) {
                candidateMethods.add(res.getCorrespondingDeclaration());
            }
        }

        // We want to avoid infinite recursion when a class is using its own method
        // see issue #75
        if (candidateMethods.isEmpty()) {
            SymbolReference<MethodDeclaration> parentSolution = context.getParent().solveMethod(name, argumentsTypes, typeSolver);
            if (parentSolution.isSolved()) {
                candidateMethods.add(parentSolution.getCorrespondingDeclaration());
            }
        }

        // if is interface and candidate method list is empty, we should check the Object Methods
        if (candidateMethods.isEmpty() && typeDeclaration.isInterface()) {
          SymbolReference<MethodDeclaration> res = MethodResolutionLogic.solveMethodInType(new ReflectionClassDeclaration(Object.class, typeSolver), name, argumentsTypes, typeSolver);
          if (res.isSolved()) {
              candidateMethods.add(res.getCorrespondingDeclaration());
          }
        }
        
        return MethodResolutionLogic.findMostApplicable(candidateMethods, name, argumentsTypes, typeSolver);
    }

    public SymbolReference<ConstructorDeclaration> solveConstructor(List<Type> argumentsTypes, TypeSolver typeSolver) {
      if (typeDeclaration instanceof ClassDeclaration) {
        return ConstructorResolutionLogic.findMostApplicable(((ClassDeclaration) typeDeclaration).getConstructors(), argumentsTypes, typeSolver);
      }
      return SymbolReference.unsolved(ConstructorDeclaration.class);
    }
}
