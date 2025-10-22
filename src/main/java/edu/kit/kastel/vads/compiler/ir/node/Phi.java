package edu.kit.kastel.vads.compiler.ir.node;

import org.jspecify.annotations.Nullable;

import java.util.List;

public final class Phi extends Node {
    @Nullable
    private Node simplified = null;

    public Phi(Block block) {
        super(block);
    }

    public void appendOperand(Node node) {
        addPredecessor(node);
    }

    public List<? extends Node> operands() {
        return this.predecessors();
    }

    public void setSimplifiedVersion(Node node) {
        simplified = node;
    }

    public boolean hasSimplifiedVersion() {
        return simplified != null;
    }
    
    public Node simplified() {
        assert this.simplified != null;
        while (this.simplified instanceof Phi phi && phi.hasSimplifiedVersion()) {
            this.simplified = phi;
        }
        return this.simplified;
    }
}
