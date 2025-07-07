package edu.kit.kastel.vads.compiler.backend;

import edu.kit.kastel.vads.compiler.backend.instruction.*;
import edu.kit.kastel.vads.compiler.backend.register.PhysicalRegister;
import edu.kit.kastel.vads.compiler.backend.register.Register;
import edu.kit.kastel.vads.compiler.backend.register.VirtualRegister;
import edu.kit.kastel.vads.compiler.backend.register.VirtualRegisterAllocator;
import edu.kit.kastel.vads.compiler.ir.IrGraph;
import edu.kit.kastel.vads.compiler.ir.node.*;

import java.util.*;

import static edu.kit.kastel.vads.compiler.ir.util.NodeSupport.predecessorSkipProj;

public class FunctionInstructionSet {
    private final String name;
    private final List<Block> blocks = new ArrayList<>();
    private final Map<Block, List<Instruction>> instructions = new HashMap<>();
    private final VirtualRegisterAllocator registerAllocator;

    public FunctionInstructionSet(IrGraph graph, VirtualRegisterAllocator registerAllocator) {
        this.name = graph.name();
        this.registerAllocator = registerAllocator;
        allocateRegisters(graph.endBlock());
        scanBlocks(graph.endBlock());
        scanInstructions(graph.endBlock());
        handlePhis(graph.endBlock());
        handleJumps();
    }

    public String name() {
        return name;
    }

    public Block getBlock(int idx) {
        return blocks.get(idx);
    }

    public List<Block> getBlocks() {
        return blocks;
    }

    public Instruction getInstruction(Block block, int idx) {
        return instructions.get(block).get(idx);
    }

    public List<Instruction> getInstructions(Block block) {
        return instructions.get(block);
    }

    public String getLabel(Block block) {
        return ((LabelInstruction) instructions.get(block).getFirst()).label();
    }

    public void deduceLiveness() {
        for (Block block : blocks) {
            for (Instruction instruction : instructions.get(block)) {
                instruction.markUsedAsLive();
            }
        }

        boolean changes = true;
        while (changes) {
            changes = false;
            Instruction next = null;
            for (int i = blocks.size() - 1; i >= 0; --i) {
                List<Instruction> instructionList = instructions.get(blocks.get(i));
                for (int j = instructionList.size() - 1; j >= 0; --j) {
                    Instruction instruction = instructionList.get(j);
                    boolean changesForInstruction = instruction.deduceLiveness(next);
                    changes = changes || changesForInstruction;
                    next = instruction;
                }
            }
        }
    }

    public InterferenceGraph buildInterferenceGraph() {
        InterferenceGraph interferenceGraph = new InterferenceGraph();
        for (VirtualRegister virtualRegister : registerAllocator.usedRegisters()) {
            interferenceGraph.addRegister(virtualRegister);
        }

        Instruction next = null;
        for (int i = blocks.size() - 1; i >= 0; --i) {
            List<Instruction> instructionList = instructions.get(blocks.get(i));
            for (int j = instructionList.size() - 1; j >= 0; --j) {
                Instruction instruction = instructionList.get(j);
                instruction.addEdges(interferenceGraph, next);
                next = instruction;
            }
        }
        return interferenceGraph;
    }

    private void allocateRegisters(Block endBlock) {
        Set<Node> visited = new HashSet<>();
        visited.add(endBlock);
        allocateRegistersRecursive(endBlock, visited);
    }

    private void allocateRegistersRecursive(Node node, Set<Node> visited) {
        for (Node predecessor : node.predecessors()) {
            if (visited.add(predecessor)) {
                allocateRegistersRecursive(predecessor, visited);
            }
        }
        registerAllocator.allocateRegister(node);
        if (node instanceof Block block) {
            if (block.exitNode() != null) allocateRegistersRecursive(block.exitNode(), visited);
        }
    }

    private void scanBlocks(Block endBlock) {
        Set<Node> visited = new HashSet<>();
        visited.add(endBlock);
        scanBlocksRecursive(endBlock, visited);
    }

    private void scanBlocksRecursive(Node node, Set<Node> visited) {
        for (Node predecessor : node.predecessors()) {
            if (visited.add(predecessor)) {
                scanBlocksRecursive(predecessor, visited);
            }
        }
        if (node instanceof Block block) newBlock(block, visited);
    }

    private void scanInstructions(Block endBlock) {
        Set<Node> visited = new HashSet<>();
        visited.add(endBlock);
        scanInstructionsRecursive(endBlock, visited);
    }

    private void scanInstructionsRecursive(Node node, Set<Node> visited) {
        for (Node predecessor : node.predecessors()) {
            if (visited.add(predecessor)) {
                scanInstructionsRecursive(predecessor, visited);
            }
        }

        switch (node) {
            case Block block                    -> visitExitNode(block, visited);

            case FParameterNode param           -> newParam(param);

            case AssignNode assign              -> newAssign(assign);
            case AddNode add                    -> newAdd(add);
            case SubNode sub                    -> newSub(sub);
            case MulNode mul                    -> newMul(mul);
            case DivNode div                    -> newDiv(div);
            case ModNode mod                    -> newMod(mod);

            case BitAndNode bitAnd              -> newBitAnd(bitAnd);
            case BitOrNode bitOr                -> newBitOr(bitOr);
            case BitXorNode bitXor              -> newBitXor(bitXor);
            case BitNegationNode bitNegation    -> newBitNegation(bitNegation);

            case ShiftLeftNode shiftLeft        -> newShiftLeft(shiftLeft);
            case ShiftRightNode shiftRight      -> newShiftRight(shiftRight);

            case CEqualsNode equals             -> newEquals(equals);
            case CUnequalsNode unequals         -> newUnequals(unequals);
            case CSmallerNode smaller           -> newSmaller(smaller);
            case CSmallerEqNode smallerEq       -> newSmallerEq(smallerEq);
            case CBiggerNode bigger             -> newBigger(bigger);
            case CBiggerEqNode biggerEq         -> newBiggerEq(biggerEq);
            case LogNegationNode logNegation    -> newLogNegation(logNegation);

            case CallNode call                  -> newCall(call);

            case ConstBoolNode constBool        -> newConstBool(constBool);
            case ConstIntNode constInt          -> newConstInt(constInt);

            case ReturnNode ret                 -> newReturn(ret);
            case JumpNode _, IfNode _           -> {} // handle jumps later
            case Phi _                          -> {} // ignore phis for now
            case ProjNode _, StartNode _        -> {}

            case InvalidNode _                  -> {} // tolerate it for now
            //case InvalidNode _                  -> throw new UnsupportedOperationException("Invalid node");
        }
    }

    private void visitExitNode(Block block, Set<Node> visited) {
        if (block.exitNode() != null) scanInstructionsRecursive(block.exitNode(), visited);
    }

    private void handlePhis(Block endBlock) {
        Set<Node> visited = new HashSet<>();
        visited.add(endBlock);
        handlePhisRecursive(endBlock, visited);
    }

    private void handlePhisRecursive(Node node, Set<Node> visited) {
        for (Node predecessor : node.predecessors()) {
            if (visited.add(predecessor)) {
                handlePhisRecursive(predecessor, visited);
            }
        }
        if (node instanceof Phi phi) {
            for (Node operand : phi.operands()) {
                if (!registerAllocator.isAllocated(operand)) continue;
                instructions.get(operand.block()).add(new MoveInstruction(
                    registerAllocator.get(operand), registerAllocator.get(phi)
                ));
            }
        } else if (node instanceof Block block) {
            if (block.exitNode() != null) handlePhisRecursive(block.exitNode(), visited);
        }
    }

    private void handleJumps() {
        for (int i = 0; i < blocks.size(); i++) {
            Block block = blocks.get(i);
            ExitNode exitNode = block.exitNode();
            if (exitNode instanceof JumpNode jump) {
                if (i == blocks.size() - 1 || jump.targetBlock() == blocks.get(i + 1)) continue;
                instructions.get(block).add(newJump(jump.targetBlock()));
            }
            else if (exitNode instanceof IfNode ifNode) {
                if (i < blocks.size() - 1) {
                    if (ifNode.trueEntry() == blocks.get(i + 1)) {
                        instructions.get(block).add(newJumpZero(ifNode.falseEntry(), ifNode.condition()));
                        continue;
                    }
                    if (ifNode.falseEntry() == blocks.get(i + 1)) {
                        instructions.get(block).add(newJumpNonZero(ifNode.trueEntry(), ifNode.condition()));
                        continue;
                    }
                }
                instructions.get(block).add(newJumpZero(ifNode.falseEntry(), ifNode.condition()));
                instructions.get(block).add(newJump(ifNode.trueEntry()));
            }
        }
    }

    private void newBlock(Block block, Set<Node> visited) {
        blocks.add(block);
        instructions.put(block, new ArrayList<>());
        instructions.get(block).add(new LabelInstruction(".block" + blocks.size()));
        // maybe unnecessary
        if (block.exitNode() != null) scanBlocksRecursive(block.exitNode(), visited);
    }

    private void newParam(FParameterNode parameterNode) {
        instructions.get(parameterNode.block()).add(new ParameterInstruction(
                registerAllocator.get(parameterNode),
                parameterNode.index()
        ));
    }

    private void newAssign(AssignNode assign) {
        instructions.get(assign.block()).add(new MoveInstruction(
                registerAllocator.get(predecessorSkipProj(assign, AssignNode.NODE)),
                registerAllocator.get(assign)
        ));
    }

    private void newAdd(AddNode add) {
        instructions.get(add.block()).add(new AddInstruction(add, registerAllocator));
    }

    private void newSub(SubNode sub) {
        instructions.get(sub.block()).add(new SubInstruction(sub, registerAllocator));
    }

    private void newMul(MulNode mul) {
        instructions.get(mul.block()).add(new MulInstruction(mul, registerAllocator));
    }

    private void newDiv(DivNode div) {
        addDivMod(div);
    }

    private void newMod(ModNode mod) {
        addDivMod(mod);
    }

    private void newBitAnd(BitAndNode bitAnd) {
        instructions.get(bitAnd.block()).add(new BitAndInstruction(bitAnd, registerAllocator));
    }

    private void newBitOr(BitOrNode bitOr) {
        instructions.get(bitOr.block()).add(new BitOrInstruction(bitOr, registerAllocator));
    }

    private void newBitXor(BitXorNode bitXor) {
        instructions.get(bitXor.block()).add(new BitXorInstruction(bitXor, registerAllocator));
    }

    private void newBitNegation(BitNegationNode bitNegation) {
        instructions.get(bitNegation.block()).add(new BitNegationInstruction(bitNegation, registerAllocator));
    }

    private void newShiftLeft(ShiftLeftNode shiftLeft) {
        addShiftMoveInstructions(shiftLeft.block(), shiftLeft.right(), shiftLeft.left(), shiftLeft);
        instructions.get(shiftLeft.block()).add(new ShiftLeftInstruction(
                registerAllocator.get(shiftLeft)
        ));
    }

    private void newShiftRight(ShiftRightNode shiftRight) {
        addShiftMoveInstructions(shiftRight.block(), shiftRight.right(), shiftRight.left(), shiftRight);
        instructions.get(shiftRight.block()).add(new ShiftRightInstruction(
                registerAllocator.get(shiftRight)
        ));
    }

    private void newEquals(CEqualsNode equals) {
        instructions.get(equals.block()).add(new EqualsInstruction(equals, registerAllocator));
    }

    private void newUnequals(CUnequalsNode unequals) {
        instructions.get(unequals.block()).add(new UnequalsInstruction(unequals, registerAllocator));
    }

    private void newSmaller(CSmallerNode smaller) {
        instructions.get(smaller.block()).add(new SmallerInstruction(smaller, registerAllocator));
    }

    private void newSmallerEq(CSmallerEqNode smallerEq) {
        instructions.get(smallerEq.block()).add(new SmallerEqInstruction(smallerEq, registerAllocator));
    }

    private void newBigger(CBiggerNode bigger) {
        instructions.get(bigger.block()).add(new BiggerInstruction(bigger, registerAllocator));
    }

    private void newBiggerEq(CBiggerEqNode biggerEq) {
        instructions.get(biggerEq.block()).add(new BiggerEqInstruction(biggerEq, registerAllocator));
    }

    private void newLogNegation(LogNegationNode logNegation) {
        instructions.get(logNegation.block()).add(new LogNegationInstruction(logNegation, registerAllocator));
    }

    private void newReturn(ReturnNode ret) {
        instructions.get(ret.block()).add(new ReturnInstruction(ret, registerAllocator));
    }

    private void newCall(CallNode callNode) {
        instructions.get(callNode.block()).add(new CallInstruction(
            callNode.functionName(),
            callNode.parameters().stream().map(node -> (Register) registerAllocator.get(node)).toList(),
            registerAllocator.get(callNode)
        ));
    }

    private void newConstBool(ConstBoolNode constBool) {
        instructions.get(constBool.block()).add(new ConstBoolInstruction(constBool, registerAllocator));
    }

    private void newConstInt(ConstIntNode constInt) {
        instructions.get(constInt.block()).add(new ConstIntInstruction(constInt, registerAllocator));
    }

    private JumpInstruction newJump(Block target) {
        return new JumpInstruction((LabelInstruction) instructions.get(target).getFirst());
    }

    private JumpZeroInstruction newJumpZero(Block target, Node condition) {
        return new JumpZeroInstruction(
                (LabelInstruction) instructions.get(target).getFirst(),
                registerAllocator.get(condition)
        );
    }

    private JumpNonZeroInstruction newJumpNonZero(Block target, Node condition) {
        return new JumpNonZeroInstruction(
                (LabelInstruction) instructions.get(target).getFirst(),
                registerAllocator.get(condition)
        );
    }

    private void addShiftMoveInstructions(Block block, Node shift, Node source, Node destination) {
        instructions.get(block).add(new MoveInstruction(
                registerAllocator.get(shift),
                PhysicalRegister.ShiftRegister
        ));
        instructions.get(block).add(new MoveInstruction(
                registerAllocator.get(source),
                registerAllocator.get(destination)
        ));
    }

    private void addDivMod(Node node) {
        assert node instanceof DivNode || node instanceof ModNode;
        Register destination = registerAllocator.get(node);
        Register left = registerAllocator.get(predecessorSkipProj(node, BinaryOperationNode.LEFT));
        Register right = registerAllocator.get(predecessorSkipProj(node, BinaryOperationNode.RIGHT));

        List<Instruction> instructionList = instructions.get(node.block());
        instructionList.add(new MoveInstruction(left, PhysicalRegister.DividendLS));
        instructionList.add(new CtldInstruction());
        instructionList.add(new DivModInstruction(right));
        if (node instanceof DivNode) {
            instructionList.add(new MoveInstruction(PhysicalRegister.Quotient, destination));
        } else {
            instructionList.add(new MoveInstruction(PhysicalRegister.Remainder, destination));
        }
    }
}
