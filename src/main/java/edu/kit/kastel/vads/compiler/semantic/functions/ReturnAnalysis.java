package edu.kit.kastel.vads.compiler.semantic.functions;

import edu.kit.kastel.vads.compiler.parser.ast.*;
import edu.kit.kastel.vads.compiler.parser.visitor.NoOpVisitor;
import edu.kit.kastel.vads.compiler.parser.visitor.Unit;
import edu.kit.kastel.vads.compiler.semantic.SemanticException;

import java.util.HashSet;
import java.util.Set;

/// Checks that functions return.
class ReturnAnalysis implements NoOpVisitor<ReturnAnalysis.ReturnState> {

    static class ReturnState {
        private final Set<Tree> returns;

        public ReturnState() {
            returns = new HashSet<>();
        }

        public boolean doesReturn(Tree tree) {
            return returns.contains(tree);
        }

        public void setReturns(Tree tree) {
            returns.add(tree);
        }
    }

    @Override
    public Unit visit(ReturnTree returnTree, ReturnState data) {
        data.setReturns(returnTree);
        return NoOpVisitor.super.visit(returnTree, data);
    }

    @Override
    public Unit visit(BlockTree blockTree, ReturnState data) {
        for (StatementTree statement : blockTree.statements()) {
            if (data.doesReturn(statement)) {
                data.setReturns(blockTree);
                break;
            }
        }
        return NoOpVisitor.super.visit(blockTree, data);
    }

    @Override
    public Unit visit(IfTree ifTree, ReturnState data) {
        if (ifTree.caseFalse() != null
                && data.doesReturn(ifTree.caseTrue())
                && data.doesReturn(ifTree.caseFalse())) {
            data.setReturns(ifTree);
        }
        return NoOpVisitor.super.visit(ifTree, data);
    }

    @Override
    public Unit visit(FunctionTree functionTree, ReturnState data) {
        if (!data.doesReturn(functionTree.body())) {
            throw new SemanticException("function " + functionTree.name() + " does not return");
        }
        return NoOpVisitor.super.visit(functionTree, data);
    }
}
