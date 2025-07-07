package edu.kit.kastel.vads.compiler.semantic.util;

import edu.kit.kastel.vads.compiler.parser.symbol.Name;

import java.util.HashSet;
import java.util.Set;

public class VariableStatus {
    public Set<Name> declared = new HashSet<>();
    public Set<Name> initialized = new HashSet<>();

    public static VariableStatus clonedFrom(VariableStatus variableStatus) {
        return  new VariableStatus(
                new HashSet<>(variableStatus.declared),
                new HashSet<>(variableStatus.initialized));
    }

    public VariableStatus() {
    }

    private VariableStatus(Set<Name> declared, Set<Name> initialized) {
        this.declared = declared;
        this.initialized = initialized;
    }
}
