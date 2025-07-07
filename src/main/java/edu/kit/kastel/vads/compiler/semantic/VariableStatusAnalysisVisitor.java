package edu.kit.kastel.vads.compiler.semantic;

import edu.kit.kastel.vads.compiler.lexer.Operator;
import edu.kit.kastel.vads.compiler.parser.ast.*;
import edu.kit.kastel.vads.compiler.parser.visitor.Visitor;
import edu.kit.kastel.vads.compiler.semantic.util.VariableStatus;

/// Checks that variables are
/// - declared before assignment
/// - not declared twice
/// - not initialized twice
/// - assigned before referenced
class VariableStatusAnalysisVisitor implements Visitor<VariableStatus, VariableStatus> {

    @Override
    public VariableStatus visit(AssignmentTree assignmentTree, VariableStatus data) {
        data = assignmentTree.lValue().accept(this, data);
        data = assignmentTree.expression().accept(this, data);
        switch (assignmentTree.lValue()) {
            case LValueIdentTree(var name) -> {
                if (assignmentTree.operator().type() == Operator.OperatorType.ASSIGN) {
                    checkDeclared(name, data);
                } else {
                    checkInitialized(name, data);
                }
                data.initialized.add(name.name());
            }
        }
        return data;
    }

    @Override
    public VariableStatus visit(BinaryOperationTree binaryOperationTree, VariableStatus data) {
        data = binaryOperationTree.lhs().accept(this, data);
        data = binaryOperationTree.rhs().accept(this, data);
        return data;
    }

    @Override
    public VariableStatus visit(BlockTree blockTree, VariableStatus data) {
        VariableStatus cloned = VariableStatus.clonedFrom(data);
        for (StatementTree statement : blockTree.statements()) {
            cloned = statement.accept(this, cloned);
        }
        cloned.declared = data.declared;
        cloned.initialized.retainAll(data.declared);
        return cloned;
    }

    @Override
    public VariableStatus visit(DeclarationTree declarationTree, VariableStatus data) {
        checkUndeclared(declarationTree.name(), data);
        if (declarationTree.initializer() != null) {
            data = declarationTree.initializer().accept(this, data);
            data.initialized.add(declarationTree.name().name());
        }
        data.declared.add(declarationTree.name().name());
        return data;
    }

    @Override
    public VariableStatus visit(FunctionTree functionTree, VariableStatus data) {
        VariableStatus cloned = VariableStatus.clonedFrom(data);
        for (ParameterTree parameter : functionTree.parameters()) {
            cloned = parameter.accept(this, cloned);
        }
        functionTree.body().accept(this, cloned);
        return data;
    }

    @Override
    public VariableStatus visit(CallTree callTree, VariableStatus data) {
        for (ExpressionTree parameter : callTree.parameters()) {
            data = parameter.accept(this, data);
        }
        return data;
    }

    @Override
    public VariableStatus visit(IdentExpressionTree identExpressionTree, VariableStatus data) {
        checkInitialized(identExpressionTree.name(), data);
        return data;
    }

    @Override
    public VariableStatus visit(IfTree ifTree, VariableStatus data) {
        data = ifTree.condition().accept(this, data);
        if (ifTree.caseFalse() == null) {
            ifTree.caseTrue().accept(this, VariableStatus.clonedFrom(data));
            return data;
        }
        VariableStatus clonedTrue = ifTree.caseTrue().accept(this, VariableStatus.clonedFrom(data));
        VariableStatus clonedFalse = ifTree.caseFalse().accept(this, VariableStatus.clonedFrom(data));
        clonedTrue.declared = data.declared;
        clonedTrue.initialized.retainAll(data.declared);
        // variables only initialized, if initialized in both blocks
        clonedTrue.initialized.retainAll(clonedFalse.initialized);
        return clonedTrue;
    }

    @Override
    public VariableStatus visit(WhileTree whileTree, VariableStatus data) {
        data = whileTree.condition().accept(this, data);
        whileTree.body().accept(this, VariableStatus.clonedFrom(data));
        return data;
    }

    @Override
    public VariableStatus visit(ForTree forTree, VariableStatus data) {
        VariableStatus clonedHeader = VariableStatus.clonedFrom(data);
        if (forTree.initializer() != null) {
            clonedHeader = forTree.initializer().accept(this, clonedHeader);
        }
        clonedHeader = forTree.condition().accept(this, clonedHeader);
        VariableStatus clonedBody = forTree.body().accept(this, VariableStatus.clonedFrom(clonedHeader));
        if (forTree.step() != null) {
            forTree.step().accept(this, clonedBody);
        }
        clonedHeader.declared = data.declared;
        clonedHeader.initialized.retainAll(data.declared);
        return clonedHeader;
    }

    @Override
    public VariableStatus visit(BreakTree breakTree, VariableStatus data) {
        return data;
    }

    @Override
    public VariableStatus visit(ContinueTree continueTree, VariableStatus data) {
        return data;
    }

    @Override
    public VariableStatus visit(TernaryConditionTree ternaryConditionTree, VariableStatus data) {
        data = ternaryConditionTree.condition().accept(this, data);
        data = ternaryConditionTree.caseTrue().accept(this, data);
        data = ternaryConditionTree.caseFalse().accept(this, data);
        return data;
    }

    @Override
    public VariableStatus visit(IntLiteralTree intLiteralTree, VariableStatus data) {
        return data;
    }

    @Override
    public VariableStatus visit(BoolLiteralTree boolLiteralTree, VariableStatus data) {
        return data;
    }

    @Override
    public VariableStatus visit(LValueIdentTree lValueIdentTree, VariableStatus data) {
        return data;
    }

    @Override
    public VariableStatus visit(NameTree nameTree, VariableStatus data) {
        return data;
    }

    @Override
    public VariableStatus visit(UnaryOperatorTree unaryOperatorTree, VariableStatus data) {
        data = unaryOperatorTree.expression().accept(this, data);
        return data;
    }

    @Override
    public VariableStatus visit(ProgramTree programTree, VariableStatus data) {
        for (FunctionTree functionTree : programTree.topLevelTrees()) {
            data = functionTree.accept(this, data);
        }
        return data;
    }

    @Override
    public VariableStatus visit(ReturnTree returnTree, VariableStatus data) {
        data = returnTree.expression().accept(this, data);
        return data;
    }

    @Override
    public VariableStatus visit(ParameterTree parameterTree, VariableStatus data) {
        checkUndeclared(parameterTree.name(), data);
        data.declared.add(parameterTree.name().name());
        data.initialized.add(parameterTree.name().name());
        return data;
    }

    @Override
    public VariableStatus visit(TypeTree typeTree, VariableStatus data) {
        return data;
    }

    private static void checkDeclared(NameTree name, VariableStatus data) {
        if (!data.declared.contains(name.name())) {
            throw new SemanticException("Variable " + name + " must be declared before assignment");
        }
    }

    private static void checkInitialized(NameTree name, VariableStatus data) {
        if (!data.initialized.contains(name.name())) {
            throw new SemanticException("Variable " + name + " must be initialized before use");
        }
    }

    private static void checkUndeclared(NameTree name, VariableStatus data) {
        if (data.declared.contains(name.name())) {
            throw new SemanticException("Variable " + name + " is already declared");
        }
    }
}
