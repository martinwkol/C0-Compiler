package edu.kit.kastel.vads.compiler.parser.ast;

import java.util.List;

import edu.kit.kastel.vads.compiler.Position;
import edu.kit.kastel.vads.compiler.Span;
import edu.kit.kastel.vads.compiler.parser.visitor.Visitor;

public record CallTree(IdentExpressionTree functionName, List<ExpressionTree> parameters, Position closingBlacketPos) implements StatementTree, ExpressionTree {
    @Override
    public Span span() {
        return new Span.SimpleSpan(functionName.span().start(), closingBlacketPos);
    }
    
    @Override
    public <T, R> R accept(Visitor<T, R> visitor, T data) {
        return visitor.visit(this, data);
    }
}
