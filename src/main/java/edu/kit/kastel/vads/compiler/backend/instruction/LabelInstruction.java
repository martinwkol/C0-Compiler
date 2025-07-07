package edu.kit.kastel.vads.compiler.backend.instruction;

public final class LabelInstruction extends Instruction {
    private final String label;

    public LabelInstruction(String label) {
        super(true);
        this.label = label;
    }

    public String label(String functionName) {
        return functionName + "." + this.label;
    }
}
