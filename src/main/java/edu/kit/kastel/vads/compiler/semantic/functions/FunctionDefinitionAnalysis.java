package edu.kit.kastel.vads.compiler.semantic.functions;

import java.util.List;
import java.util.stream.Collectors;

import edu.kit.kastel.vads.compiler.parser.ast.FunctionTree;
import edu.kit.kastel.vads.compiler.parser.ast.ProgramTree;
import edu.kit.kastel.vads.compiler.parser.type.FunctionType;
import edu.kit.kastel.vads.compiler.parser.type.Type;
import edu.kit.kastel.vads.compiler.parser.visitor.NoOpVisitor;
import edu.kit.kastel.vads.compiler.parser.visitor.Unit;
import edu.kit.kastel.vads.compiler.semantic.SemanticException;
import edu.kit.kastel.vads.compiler.semantic.util.Namespace;

class FunctionDefinitionAnalysis implements NoOpVisitor<Namespace<FunctionType>> {
    @Override
    public Unit visit(ProgramTree programTree, Namespace<FunctionType> data) {
        for (FunctionTree function : programTree.topLevelTrees()) {
            function.accept(this, data);
        }
        return NoOpVisitor.super.visit(programTree, data);
    }
    
    @Override
    public Unit visit(FunctionTree functionTree, Namespace<FunctionType> data) {
        if (data.get(functionTree.name()) != null) {
            throw new SemanticException("function " + functionTree.name().name() + " already defined");
        }
        List<Type> parameterTypes = functionTree.parameters().stream().map(param -> param.type().type()).collect(Collectors.toList());
        data.put(functionTree.name(), new FunctionType(functionTree.returnType().type(), parameterTypes));
        return NoOpVisitor.super.visit(functionTree, data);
    }
}
