package edu.kit.kastel.vads.compiler.semantic.general;

import edu.kit.kastel.vads.compiler.parser.ast.IntLiteralTree;
import edu.kit.kastel.vads.compiler.parser.visitor.NoOpVisitor;
import edu.kit.kastel.vads.compiler.parser.visitor.Unit;
import edu.kit.kastel.vads.compiler.semantic.util.Namespace;
import edu.kit.kastel.vads.compiler.semantic.util.SemanticException;

class IntegerLiteralRangeAnalysis implements NoOpVisitor<Namespace<Void>> {

    @Override
    public Unit visit(IntLiteralTree intLiteralTree, Namespace<Void> data) {
      intLiteralTree.parseValue()
          .orElseThrow(
              () -> new SemanticException("invalid integer literal " + intLiteralTree.value())
          );
        return NoOpVisitor.super.visit(intLiteralTree, data);
    }
}
