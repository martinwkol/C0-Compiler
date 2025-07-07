package edu.kit.kastel.vads.compiler.backend.instruction;

import java.util.List;

import edu.kit.kastel.vads.compiler.backend.register.PhysicalRegister;
import edu.kit.kastel.vads.compiler.backend.register.Register;
import edu.kit.kastel.vads.compiler.backend.register.RegisterMapping;

public final class CallInstruction extends Instruction {
    private final String functionName;
    private final List<Register> parameters;
    private final Register returnRegister;

    public CallInstruction(String functionName, List<Register> parameters, Register returnRegister) {
        super(true);
        addDefines(PhysicalRegister.Return);
        for (Register parameter : parameters) {
            addUses(parameter);
        }
        this.functionName = functionName;
        this.parameters = parameters;
        this.returnRegister = returnRegister;
    }

    public String functionName() {
        return this.functionName;
    }

    public Register getParameter(RegisterMapping registerMapping, int index) {
        return registerMapping.get(parameters.get(index));
    }

    public int numParameters() {
        return this.parameters.size();
    }

    public Register getReturnRegister(RegisterMapping registerMapping) {
        return registerMapping.get(returnRegister);
    }
}
