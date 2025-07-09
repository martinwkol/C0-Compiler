package edu.kit.kastel.vads.compiler.semantic.functions;

import edu.kit.kastel.vads.compiler.parser.ast.CallTree;
import edu.kit.kastel.vads.compiler.parser.type.FunctionType;
import edu.kit.kastel.vads.compiler.parser.visitor.NoOpVisitor;
import edu.kit.kastel.vads.compiler.parser.visitor.Unit;
import edu.kit.kastel.vads.compiler.semantic.util.Namespace;
import edu.kit.kastel.vads.compiler.semantic.util.SemanticException;

class CallAnalysis implements NoOpVisitor<Namespace<Void>> {
    private final Namespace<FunctionType> functionTypeNamespace;

    public CallAnalysis(Namespace<FunctionType> functionTypeNamespace) {
        this.functionTypeNamespace = functionTypeNamespace;
    }

    @Override
    public Unit visit(CallTree callTree, Namespace<Void> data) {
        if (!functionTypeNamespace.contains(callTree.functionName())) {
            throw new SemanticException("No function named " + callTree.functionName().name());
        }
        FunctionType functionType = functionTypeNamespace.get(callTree.functionName());
        if (callTree.parameters().size() != functionType.parameterTypes().size()) {
            throw new SemanticException(String.format(
                "Function %s expectes %d arguments but got %d", 
                callTree.functionName().name(),
                functionType.parameterTypes().size(),
                callTree.parameters().size()
            ));
        }
        return NoOpVisitor.super.visit(callTree, data);
    }
}
