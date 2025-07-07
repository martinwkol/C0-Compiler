package edu.kit.kastel.vads.compiler.parser.visitor;

import edu.kit.kastel.vads.compiler.parser.ast.*;

public class RecursiveVisitor<T, R> implements Visitor<T, R> {
    private final Visitor<T, R> preorderVisitor;
    private final Visitor<T, R> postorderVisitor;

    public RecursiveVisitor(Visitor<T, R> preorderVisitor, Visitor<T, R> postorderVisitor) {
        this.preorderVisitor = preorderVisitor;
        this.postorderVisitor = postorderVisitor;
    }

    @Override
    public R visit(AssignmentTree assignmentTree, T data) {
        R r = this.preorderVisitor.visit(assignmentTree, data);
        r = assignmentTree.lValue().accept(this, accumulate(data, r));
        r = assignmentTree.expression().accept(this, accumulate(data, r));
        r = this.postorderVisitor.visit(assignmentTree, accumulate(data, r));
        return r;
    }

    @Override
    public R visit(BinaryOperationTree binaryOperationTree, T data) {
        R r = this.preorderVisitor.visit(binaryOperationTree, data);
        r = binaryOperationTree.lhs().accept(this, accumulate(data, r));
        r = binaryOperationTree.rhs().accept(this, accumulate(data, r));
        r = this.postorderVisitor.visit(binaryOperationTree, accumulate(data, r));
        return r;
    }

    @Override
    public R visit(BlockTree blockTree, T data) {
        R r = this.preorderVisitor.visit(blockTree, data);
        T d = accumulate(data, r);
        for (StatementTree statement : blockTree.statements()) {
            r = statement.accept(this, d);
            d = accumulate(d, r);
        }
        r = this.postorderVisitor.visit(blockTree, d);
        return r;
    }

    @Override
    public R visit(DeclarationTree declarationTree, T data) {
        R r = this.preorderVisitor.visit(declarationTree, data);
        r = declarationTree.type().accept(this, accumulate(data, r));
        r = declarationTree.name().accept(this, accumulate(data, r));
        if (declarationTree.initializer() != null) {
            r = declarationTree.initializer().accept(this, accumulate(data, r));
        }
        r = this.postorderVisitor.visit(declarationTree, accumulate(data, r));
        return r;
    }

    @Override
    public R visit(FunctionTree functionTree, T data) {
        R r = this.preorderVisitor.visit(functionTree, data);
        r = functionTree.returnType().accept(this, accumulate(data, r));
        r = functionTree.name().accept(this, accumulate(data, r));
        for (ParameterTree parameter : functionTree.parameters()) {
            r = parameter.accept(this, accumulate(data, r));
        }
        r = functionTree.body().accept(this, accumulate(data, r));
        r = this.postorderVisitor.visit(functionTree, accumulate(data, r));
        return r;
    }

    @Override
    public R visit(CallTree callTree, T data) {
        R r = this.preorderVisitor.visit(callTree, data);
        r = callTree.functionName().accept(this, accumulate(data, r));
        for (ExpressionTree expression : callTree.parameters()) {
            r = expression.accept(this, accumulate(data, r));
        }
        r = this.postorderVisitor.visit(callTree, accumulate(data, r));
        return r;
    }

    @Override
    public R visit(IdentExpressionTree identExpressionTree, T data) {
        R r = this.preorderVisitor.visit(identExpressionTree, data);
        r = identExpressionTree.name().accept(this, accumulate(data, r));
        r = this.postorderVisitor.visit(identExpressionTree, accumulate(data, r));
        return r;
    }

    @Override
    public R visit(IfTree ifTree, T data) {
        R r = this.preorderVisitor.visit(ifTree, data);
        r = ifTree.condition().accept(this, accumulate(data, r));
        r = ifTree.caseTrue().accept(this, accumulate(data, r));
        if (ifTree.caseFalse() != null) {
            r = ifTree.caseFalse().accept(this, accumulate(data, r));
        }
        r = this.postorderVisitor.visit(ifTree, accumulate(data, r));
        return r;
    }

    public R visit(WhileTree whileTree, T data) {
        R r = this.preorderVisitor.visit(whileTree, data);
        r = whileTree.condition().accept(this, accumulate(data, r));
        r = whileTree.body().accept(this, accumulate(data, r));
        r = this.postorderVisitor.visit(whileTree, accumulate(data, r));
        return r;
    }

    public R visit(ForTree forTree, T data) {
        R r = this.preorderVisitor.visit(forTree, data);
        if (forTree.initializer() != null) {
            r = forTree.initializer().accept(this, accumulate(data, r));
        }
        r = forTree.condition().accept(this, accumulate(data, r));
        r = forTree.body().accept(this, accumulate(data, r));
        if (forTree.step() != null) {
            r = forTree.step().accept(this, accumulate(data, r));
        }
        r = this.postorderVisitor.visit(forTree, accumulate(data, r));
        return r;
    }

    public R visit(BreakTree breakTree, T data) {
        R r = this.preorderVisitor.visit(breakTree, data);
        return this.postorderVisitor.visit(breakTree, accumulate(data, r));
    }

    public R visit(ContinueTree continueTree, T data) {
        R r = this.preorderVisitor.visit(continueTree, data);
        return this.postorderVisitor.visit(continueTree, accumulate(data, r));
    }

    public R visit(TernaryConditionTree ternaryConditionTree, T data) {
        R r = this.preorderVisitor.visit(ternaryConditionTree, data);
        r = ternaryConditionTree.condition().accept(this, accumulate(data, r));
        r = ternaryConditionTree.caseTrue().accept(this, accumulate(data, r));
        r = ternaryConditionTree.caseFalse().accept(this, accumulate(data, r));
        r = this.postorderVisitor.visit(ternaryConditionTree, accumulate(data, r));
        return r;
    }

    @Override
    public R visit(IntLiteralTree intLiteralTree, T data) {
        R r = this.preorderVisitor.visit(intLiteralTree, data);
        return this.postorderVisitor.visit(intLiteralTree, accumulate(data, r));
    }

    @Override
    public R visit(BoolLiteralTree boolLiteralTree, T data) {
        R r = this.preorderVisitor.visit(boolLiteralTree, data);
        return this.postorderVisitor.visit(boolLiteralTree, accumulate(data, r));
    }

    @Override
    public R visit(LValueIdentTree lValueIdentTree, T data) {
        R r = this.preorderVisitor.visit(lValueIdentTree, data);
        r = lValueIdentTree.name().accept(this, accumulate(data, r));
        r = this.postorderVisitor.visit(lValueIdentTree, accumulate(data, r));
        return r;
    }

    @Override
    public R visit(NameTree nameTree, T data) {
        R r = this.preorderVisitor.visit(nameTree, data);
        return this.postorderVisitor.visit(nameTree, accumulate(data, r));
    }

    @Override
    public R visit(UnaryOperatorTree unaryOperatorTree, T data) {
        R r = this.preorderVisitor.visit(unaryOperatorTree, data);
        r = unaryOperatorTree.expression().accept(this, accumulate(data, r));
        r = this.postorderVisitor.visit(unaryOperatorTree, accumulate(data, r));
        return r;
    }

    @Override
    public R visit(ProgramTree programTree, T data) {
        R r = this.preorderVisitor.visit(programTree, data);
        T d = accumulate(data, r);
        for (FunctionTree tree : programTree.topLevelTrees()) {
            r = tree.accept(this, d);
            d = accumulate(data, r);
        }
        r = this.postorderVisitor.visit(programTree, d);
        return r;
    }

    @Override
    public R visit(ReturnTree returnTree, T data) {
        R r = this.preorderVisitor.visit(returnTree, data);
        r = returnTree.expression().accept(this, accumulate(data, r));
        r = this.postorderVisitor.visit(returnTree, accumulate(data, r));
        return r;
    }

    @Override
    public R visit(ParameterTree parameterTree, T data) {
        R r = this.preorderVisitor.visit(parameterTree, data);
        r = parameterTree.type().accept(this, accumulate(data, r));
        r = parameterTree.name().accept(this, accumulate(data, r));
        return this.postorderVisitor.visit(parameterTree, accumulate(data, r));
    }

    @Override
    public R visit(TypeTree typeTree, T data) {
        R r = this.preorderVisitor.visit(typeTree, data);
        return this.postorderVisitor.visit(typeTree, accumulate(data, r));
    }

    protected T accumulate(T data, R value) {
        return data;
    }
}
