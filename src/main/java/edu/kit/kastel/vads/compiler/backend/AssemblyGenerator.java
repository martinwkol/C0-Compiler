package edu.kit.kastel.vads.compiler.backend;

import edu.kit.kastel.vads.compiler.backend.instruction.*;
import edu.kit.kastel.vads.compiler.backend.register.*;
import edu.kit.kastel.vads.compiler.ir.node.Block;
import org.jspecify.annotations.Nullable;

public class AssemblyGenerator {
    private final StringBuilder builder = new StringBuilder();
    private int comparisonLabelCounter = 0;

    // Temps for current function
    private FunctionInstructionSet instructionSet;
    private RegisterMapping registerMapping;
    private @Nullable VirtualRegister storedInTemp;
    private int maxStackVariables;
    private int stackOffset;

    public AssemblyGenerator() {
        addStarterCode();
    }

    public void addFunction(FunctionInstructionSet instructionSet, RegisterMapping registerMapping, int maxStackVariables) {
        this.instructionSet = instructionSet;
        this.registerMapping = registerMapping;
        this.storedInTemp = null;
        this.maxStackVariables = maxStackVariables;
        this.stackOffset = 0;
        if (instructionSet.name().equals("main")) {
            builder.append(".main:\n");
        } else {
            builder.append(String.format("%s:\n", instructionSet.name()));
        }
        allocateStack(maxStackVariables);
        for (Block block : instructionSet.getBlocks()) {
            addLabel(block);
            for (Instruction instruction : instructionSet.getInstructions(block)) {
                generateForInstruction(instruction);
            }
            builder.append('\n');
        }
    }

    public String getAssembly() {
        return builder.toString();
    }

    private void addStarterCode() {
        builder.append(".global main\n" +
                ".global .main\n" +
                ".text\n" +
                "main:\n" +
                "call .main\n" +
                "movq %rax, %rdi\n" +
                "movq $0x3C, %rax\n" +
                "syscall\n");
    }

    private void addLabel(Block block) {
        if (!(instructionSet.getInstruction(block, 0) instanceof LabelInstruction label)) {
            throw new RuntimeException("First instruction of block was not a label");
        }
        builder.append(String.format("%s:\n", label.label(instructionSet.name())));
    }

    private void generateForInstruction(Instruction instruction) {
        switch (instruction) {
            case MoveInstruction moveInstruction        -> addMove(moveInstruction);
            case ParameterInstruction parameter         -> addParameter(parameter);

            case AddInstruction add                     -> addBinary(add, "addl", true);
            case SubInstruction sub                     -> addBinary(sub, "subl", false);
            case MulInstruction mul                     -> addBinary(mul, "imull", true);
            case CtldInstruction _                      -> addCtld();
            case DivModInstruction dm                   -> addDivMod(dm);

            case BitAndInstruction bitAnd               -> addBinary(bitAnd,"and", true);
            case BitOrInstruction bitOr                 -> addBinary(bitOr,"or", true);
            case BitXorInstruction bitXor               -> addBinary(bitXor,"xor", true);
            case BitNegationInstruction bitNegation     -> addBitNegation(bitNegation);

            case ShiftLeftInstruction shiftLeft         -> addShift(shiftLeft, "sal");
            case ShiftRightInstruction shiftRight       -> addShift(shiftRight, "sar");

            case EqualsInstruction equals               -> addComparison(equals, "je");
            case UnequalsInstruction unequals           -> addComparison(unequals, "jne");
            case SmallerInstruction smaller             -> addComparison(smaller, "jl");
            case SmallerEqInstruction smallerEq         -> addComparison(smallerEq, "jle");
            case BiggerInstruction bigger               -> addComparison(bigger, "jg");
            case BiggerEqInstruction biggerEq           -> addComparison(biggerEq, "jge");
            case LogNegationInstruction logNegation     -> addLogNegation(logNegation);

            case CallInstruction call                   -> addCall(call);

            case ConstIntInstruction constInt           -> addConstInt(constInt);
            case ConstBoolInstruction constBool         -> addConstBool(constBool);

            case JumpInstruction jump                   -> addJump(jump);
            case JumpZeroInstruction jumpZero           -> addJumpZero(jumpZero);
            case JumpNonZeroInstruction jumpNonZero     -> addJumpNonZero(jumpNonZero);


            case ReturnInstruction ret                  -> addReturnInstruction(ret);
            case LabelInstruction _                     -> {}
        }
    }

    private void addMove(MoveInstruction moveInstruction) {
        Register destination = moveInstruction.getDestination(registerMapping);
        Register source = moveInstruction.getSource(registerMapping);
        vmove(source, destination);
    }

    private void addParameter(ParameterInstruction parameter) {
        Register destination = parameter.getDestination(registerMapping);
        Register source = new VirtualRegister(this.maxStackVariables + 1 + parameter.index()); // + 1 for return address
        vmove(source, destination);
    }

    private void addBinary(BinaryOperationInstruction binOp, String assemblyInstructionName, boolean commutative) {
        Register destination = binOp.getDestination(registerMapping);
        Register left = binOp.getLeft(registerMapping);
        Register right = binOp.getRight(registerMapping);

        if (right instanceof PhysicalRegister && right.equals(destination)) { // -> destination physical -> temp free
            if (commutative) {
                Register temp = left;
                left = right;
                right = temp;
            } else {
                move(right, PhysicalRegister.Temp);
                right = PhysicalRegister.Temp;
            }
        }
        assignTempIfVirtual(destination);
        move(left, physical(destination));
        builder.append(
            String.format(
                "%s %s, %s\n",
                assemblyInstructionName,
                addrOf(right),
                addrOf(physical(destination))
            )
        );
        moveToStackIfVirtual(destination);
    }

    private void addComparison(BinaryOperationInstruction binOp, String jumpInstruction) {
        Register destination = binOp.getDestination(registerMapping);
        Register left = binOp.getLeft(registerMapping);
        Register right = binOp.getRight(registerMapping);
        if (left instanceof VirtualRegister && right instanceof VirtualRegister) {
            // cmp first second computes second - first => first = right, second = left
            move(right, PhysicalRegister.Temp);
            builder.append(String.format("cmp %s, %s\n", addrOf(PhysicalRegister.Temp), addrOf(left)));
        }
        else {
            // cmp first second computes second - first => first = right, second = left
            builder.append(String.format("cmp %s, %s\n", addrOf(right), addrOf(left)));
        }
        String labelTrue = String.format(".C%dT", comparisonLabelCounter);
        String labelEnd = String.format(".C%dE", comparisonLabelCounter);
        comparisonLabelCounter++;

        builder.append(String.format("%s %s\n", jumpInstruction, labelTrue));
        builder.append(String.format("movl $%d, %s\n", 0, addrOf(destination)));
        builder.append(String.format("jmp %s\n", labelEnd));
        builder.append(String.format("%s:\n", labelTrue));
        builder.append(String.format("movl $%d, %s\n", 1, addrOf(destination)));
        builder.append(String.format("%s:\n", labelEnd));
    }

    public void addShift(ShiftInstruction shift, String shiftAsmInstruction) {
        Register destination = shift.getDestination(registerMapping);
        builder.append(String.format(
                "%s %s, %s\n",
                shiftAsmInstruction,
                addrOf(PhysicalRegister.ShiftRegister),
                addrOf(destination)
        ));
    }

    private void addBitNegation(BitNegationInstruction bitNegation) {
        Register destination = bitNegation.getDestination(registerMapping);
        Register source = bitNegation.getSource(registerMapping);

        if (destination == source) {
            builder.append(String.format("not %s\n", addrOf(destination)));
            return;
        }
        if (destination instanceof VirtualRegister) {
            assignTempIfVirtual(destination);
        }
        move(source, physical(destination));
        builder.append(String.format("not %s\n", addrOf(physical(destination))));
        moveToStackIfVirtual(destination);
    }

    private void addLogNegation(LogNegationInstruction logNegation) {
        Register destination = logNegation.getDestination(registerMapping);
        builder.append(String.format("subl $%d, %s\n", 1, addrOf(destination)));
    }

    private void addJump(JumpInstruction jump) {
        builder.append(String.format("jmp %s\n", jump.target().label(instructionSet.name())));
    }

    private void addJumpZero(JumpZeroInstruction jump) {
        Register destination = jump.register(registerMapping);
        builder.append(String.format("cmp $%d, %s\n", 0, addrOf(destination)));
        builder.append(String.format("jz %s\n", jump.target().label(instructionSet.name())));
    }

    private void addJumpNonZero(JumpNonZeroInstruction jump) {
        Register destination = jump.register(registerMapping);
        builder.append(String.format("cmp $%d, %s\n", 0, addrOf(destination)));
        builder.append(String.format("jnz %s\n", jump.target().label(instructionSet.name())));
    }

    private void addCtld() {
        builder.append("cltd\n");
    }

    private void addDivMod(DivModInstruction dm) {
        Register divisor = dm.getDivisor(registerMapping);
        moveToTempIfVirtual(divisor);
        builder.append(String.format("idivl %s\n", addrOf(physical(divisor))));
        discardTemp();
    }




    private void addCall(CallInstruction call) {
        Register destination = call.getReturnRegister(registerMapping);

        // save registers
        for (int i = PhysicalRegister.FreelyUsable.length - 1; i >= 0; i--) {
            PhysicalRegister register = PhysicalRegister.FreelyUsable[i];
            if (call.isLive(register) && !register.equals(destination)) {
                push(register);
            }
        }

        // push parameters
        for (int i = call.numParameters() - 1; i >= 0; i--) {
            Register parameter = call.getParameter(registerMapping, i);
            push(parameter);
        }

        // call
        builder.append(String.format("call %s\n", call.functionName()));

        // pop parameters
        deallocateStack(call.numParameters());

        // move return value to destination
        move(PhysicalRegister.Return, destination);

        // Restore registers
        for (int i = 0; i < PhysicalRegister.FreelyUsable.length; i++) {
            PhysicalRegister register = PhysicalRegister.FreelyUsable[i];
            if (call.isLive(register) && !register.equals(destination)) {
                pop(register);
            }
        }
    }




    private void addConstInt(ConstIntInstruction constIntInstruction) {
        Register destination = constIntInstruction.getDestination(registerMapping);
        assignTempIfVirtual(destination);
        builder.append(String.format("movl $%d, %s\n", constIntInstruction.getValue(), addrOf(physical(destination))));
        moveToStackIfVirtual(destination);
    }

    private void addConstBool(ConstBoolInstruction constBool) {
        Register destination = constBool.getDestination(registerMapping);
        assignTempIfVirtual(destination);
        int num = constBool.getValue() ? 1 : 0;
        builder.append(String.format("movl $%d, %s\n", num, addrOf(physical(destination))));
        moveToStackIfVirtual(destination);
    }




    private void addReturnInstruction(ReturnInstruction returnInstruction) {
        Register returnRegister = returnInstruction.getReturnRegister(registerMapping);
        if (returnRegister != PhysicalRegister.Return) move(returnRegister, PhysicalRegister.Return);
        deallocateStack(maxStackVariables);
        builder.append("ret\n");
    }



    private void allocateStack(int numVars) {
        if (numVars == 0) return;
        builder.append(String.format("subq $%d, %%rsp\n", numVars * 8));
        stackOffset += numVars;
    }

    private void deallocateStack(int numVars) {
        if (numVars == 0) return;
        builder.append(String.format("addq $%d, %%rsp\n", numVars * 8));
        stackOffset -= numVars;
    }



    private void push(Register register) {
        if (register instanceof PhysicalRegister physicalRegister) {
            push(physicalRegister);
        } else if (register instanceof VirtualRegister virtualRegister) {
            push(virtualRegister);
        } else {
            new IllegalArgumentException("Register must be physical register or virtual register");
        }
    }

    private void push(PhysicalRegister physicalRegister) {
        builder.append(String.format("pushq %s\n", addrOf(physicalRegister)));
        stackOffset++;
    }

    private void push(VirtualRegister virtualRegister) {
        move(virtualRegister, PhysicalRegister.Temp);
        push(PhysicalRegister.Temp);
    }



    private void pop(Register register) {
        if (register instanceof PhysicalRegister physicalRegister) {
            pop(physicalRegister);
        } else if (register instanceof VirtualRegister virtualRegister) {
            pop(virtualRegister);
        } else {
            new IllegalArgumentException("Register must be physical register or virtual register");
        }
    }

    private void pop(PhysicalRegister physicalRegister) {
        builder.append(String.format("popq %s\n", addrOf(physicalRegister)));
        stackOffset--;
    }

    private void pop(VirtualRegister virtualRegister) {
        pop(PhysicalRegister.Temp);
        move(PhysicalRegister.Temp, virtualRegister);
    }



    private PhysicalRegister physical(Register register) {
        if (register instanceof PhysicalRegister physicalRegister) return physicalRegister;
        if (storedInTemp == register) return PhysicalRegister.Temp;
        throw new IllegalStateException("Non physical register not in temp");
    }

    private boolean assignTempIfVirtual(Register register) {
        if (!(register instanceof VirtualRegister virtualRegister)) return false;
        if (storedInTemp != null) throw new IllegalStateException("temp register already occupied");
        storedInTemp = virtualRegister;
        return true;
    }

    private boolean moveToTempIfVirtual(Register register) {
        if (!assignTempIfVirtual(register)) return false;
        move(register, PhysicalRegister.Temp);
        return true;
    }

    private void discardTemp() {
        storedInTemp = null;
    }

    private boolean moveToStackIfVirtual(Register register) {
        if (!(register instanceof VirtualRegister)) return false;
        if (register != storedInTemp) throw new IllegalStateException("attempted to store temp in wrong register");
        move(PhysicalRegister.Temp, register);
        storedInTemp = null;
        return true;
    }

    

    private void vmove(Register from, Register to) {
        if (from instanceof VirtualRegister && to instanceof VirtualRegister) {
            move(from, PhysicalRegister.Temp);
            move(PhysicalRegister.Temp, to);
        } else {
            move(from, to);
        }
    }

    private void move(Register from, Register to) {
        if (!(from instanceof PhysicalRegister) && !(to instanceof PhysicalRegister)) {
            throw new IllegalArgumentException("At least on register must be physical");
        }
        if (from.equals(to)) return;
        builder.append(
            String.format(
                "movl %s, %s\n",
                addrOf(from),
                addrOf(to)
            )
        );
    }

    private String addrOf(Register register) {
        return addrOf(register, 4);
    }

    private String addrOf(PhysicalRegister register) {
        return addrOf(register, 4);
    }

    private String addrOf(VirtualRegister register) {
        return addrOf(register, 4);
    }

    private String addrOf(Register register, int bytes) {
        if (register instanceof PhysicalRegister physicalRegister) {
            return addrOf(physicalRegister, bytes);
        } else if (register instanceof VirtualRegister virtualRegister) {
            return addrOf(virtualRegister, bytes);
        } else {
            throw new IllegalArgumentException("Register must be physical register or virtual register");
        }
    }

    private String addrOf(PhysicalRegister physicalRegister, int bytes) {
        return switch (bytes) {
            case 1 -> "%" + physicalRegister.name1byte;
            case 2 -> "%" + physicalRegister.name2bytes;
            case 4 -> "%" + physicalRegister.name4bytes;
            case 8 -> "%" + physicalRegister.name8bytes;
            default -> throw new IllegalArgumentException(String.format(
                "%d is an invalid size", 
                bytes
            ));
        };
    }

    private String addrOf(VirtualRegister virtualRegister, int bytes) {
        return String.format(
            "%d(%%rsp)", 
            (virtualRegister.id() + stackOffset - maxStackVariables) * 8
        );
    }
}
