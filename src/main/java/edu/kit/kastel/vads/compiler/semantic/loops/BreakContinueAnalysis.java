package edu.kit.kastel.vads.compiler.semantic.loops;

import edu.kit.kastel.vads.compiler.parser.ast.*;
import edu.kit.kastel.vads.compiler.parser.visitor.NoOpVisitor;
import edu.kit.kastel.vads.compiler.parser.visitor.Unit;
import edu.kit.kastel.vads.compiler.semantic.util.SemanticException;

class BreakContinueAnalysis {
    static class Counter {
        public int count = 0;
    }

    static class PreorderVisitor implements NoOpVisitor<Counter> {
        @Override
        public Unit visit(WhileTree whileTree, Counter data) {
            data.count += 1;
            return NoOpVisitor.super.visit(whileTree, data);
        }

        @Override
        public Unit visit(ForTree forTree, Counter data) {
            data.count += 1;
            return NoOpVisitor.super.visit(forTree, data);
        }

        @Override
        public Unit visit(ContinueTree continueTree, Counter data) {
            if (data.count <= 0) {
                throw new SemanticException("continue statement outside of loop");
            }
            return NoOpVisitor.super.visit(continueTree, data);
        }

        @Override
        public Unit visit(BreakTree breakTree, Counter data) {
            if (data.count <= 0) {
                throw new SemanticException("break statement outside of loop");
            }
            return NoOpVisitor.super.visit(breakTree, data);
        }
    }

    static class PostorderVisitor implements NoOpVisitor<Counter> {
        @Override
        public Unit visit(WhileTree whileTree, Counter data) {
            data.count -= 1;
            return NoOpVisitor.super.visit(whileTree, data);
        }

        @Override
        public Unit visit(ForTree forTree, Counter data) {
            data.count -= 1;
            return NoOpVisitor.super.visit(forTree, data);
        }
    }
}
