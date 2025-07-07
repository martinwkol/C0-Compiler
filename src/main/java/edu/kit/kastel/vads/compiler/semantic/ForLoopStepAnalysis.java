package edu.kit.kastel.vads.compiler.semantic;

import edu.kit.kastel.vads.compiler.parser.ast.DeclarationTree;
import edu.kit.kastel.vads.compiler.parser.ast.ForTree;
import edu.kit.kastel.vads.compiler.parser.visitor.NoOpVisitor;
import edu.kit.kastel.vads.compiler.parser.visitor.Unit;
import edu.kit.kastel.vads.compiler.semantic.util.Namespace;

public class ForLoopStepAnalysis implements NoOpVisitor<Namespace<Void>> {
    @Override
    public Unit visit(ForTree forTree, Namespace<Void> data) {
        if (forTree.step() instanceof DeclarationTree) {
            throw new SemanticException("Declaration in 3rd for argument");
        }
        return NoOpVisitor.super.visit(forTree, data);
    }
}
