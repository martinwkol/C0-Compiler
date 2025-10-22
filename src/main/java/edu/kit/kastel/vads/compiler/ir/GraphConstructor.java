package edu.kit.kastel.vads.compiler.ir;

import edu.kit.kastel.vads.compiler.ir.node.*;
import edu.kit.kastel.vads.compiler.ir.optimize.Optimizer;
import edu.kit.kastel.vads.compiler.parser.symbol.Name;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class GraphConstructor {

    private final Optimizer optimizer;
    private final IrGraph graph;
    private final Map<Name, Map<Block, Node>> currentDef = new HashMap<>();
    private final Map<Block, Map<Name, Phi>> incompletePhis = new HashMap<>();
    private final Map<Block, Node> currentSideEffect = new HashMap<>();
    private final Map<Block, Phi> incompleteSideEffectPhis = new HashMap<>();
    private final Set<Block> sealedBlocks = new HashSet<>();
    private Block currentBlock;

    public GraphConstructor(Optimizer optimizer, String name) {
        this.optimizer = optimizer;
        this.graph = new IrGraph(name);
        this.currentBlock = this.graph.startBlock();
        // the start block never gets any more predecessors
        sealBlock(this.currentBlock);
    }

    public Node newStart() {
        assert currentBlock() == this.graph.startBlock() : "start must be in start block";
        return new StartNode(currentBlock());
    }

    public Node newAssign(Node node) {
        return this.optimizer.transform(new AssignNode(currentBlock(), node));
    }

    public Node newAdd(Node left, Node right) {
        return this.optimizer.transform(new AddNode(currentBlock(), left, right));
    }
    public Node newSub(Node left, Node right) {
        return this.optimizer.transform(new SubNode(currentBlock(), left, right));
    }

    public Node newMul(Node left, Node right) {
        return this.optimizer.transform(new MulNode(currentBlock(), left, right));
    }

    public Node newDiv(Node left, Node right) {
        return this.optimizer.transform(new DivNode(currentBlock(), left, right, readCurrentSideEffect()));
    }

    public Node newMod(Node left, Node right) {
        return this.optimizer.transform(new ModNode(currentBlock(), left, right, readCurrentSideEffect()));
    }

    public Node newBitAnd(Node left, Node right) {
        return this.optimizer.transform(new BitAndNode(currentBlock(), left, right));
    }

    public Node newBitOr(Node left, Node right) {
        return this.optimizer.transform(new BitOrNode(currentBlock(), left, right));
    }

    public Node newBitXor(Node left, Node right) {
        return this.optimizer.transform(new BitXorNode(currentBlock(), left, right));
    }

    public Node newBitNegation(Node node) {
        return this.optimizer.transform(new BitNegationNode(currentBlock(), node));
    }

    public Node newShiftLeft(Node left, Node right) {
        return this.optimizer.transform(new ShiftLeftNode(currentBlock(), left, right));
    }

    public Node newShiftRight(Node left, Node right) {
        return this.optimizer.transform(new ShiftRightNode(currentBlock(), left, right));
    }

    public Node newEquals(Node left, Node right) {
        return this.optimizer.transform(new CEqualsNode(currentBlock(), left, right));
    }

    public Node newUnequals(Node left, Node right) {
        return this.optimizer.transform(new CUnequalsNode(currentBlock(), left, right));
    }

    public Node newSmaller(Node left, Node right) {
        return this.optimizer.transform(new CSmallerNode(currentBlock(), left, right));
    }

    public Node newSmallerEq(Node left, Node right) {
        return this.optimizer.transform(new CSmallerEqNode(currentBlock(), left, right));
    }

    public Node newBigger(Node left, Node right) {
        return this.optimizer.transform(new CBiggerNode(currentBlock(), left, right));
    }

    public Node newBiggerEq(Node left, Node right) {
        return this.optimizer.transform(new CBiggerEqNode(currentBlock(), left, right));
    }

    public Node newLogNegation(Node node) {
        return this.optimizer.transform(new LogNegationNode(currentBlock(), node));
    }

    public Node newCall(String functionName, List<Node> parameters) {
        return this.optimizer.transform(new CallNode(currentBlock(), functionName, readCurrentSideEffect(), parameters));
    }

    public Node newConstInt(int value) {
        // TODO: :(
        // always move const into start block, this allows better deduplication
        // and resultingly in better value numbering
        return this.optimizer.transform(new ConstIntNode(currentBlock(), value));
    }

    public Node newConstBool(boolean value) {
        return this.optimizer.transform(new ConstBoolNode(currentBlock(), value));
    }

    public Node newSideEffectProj(Node node) {
        return new ProjNode(currentBlock(), node, ProjNode.SimpleProjectionInfo.SIDE_EFFECT);
    }

    public Node newResultProj(Node node) {
        return new ProjNode(currentBlock(), node, ProjNode.SimpleProjectionInfo.RESULT);
    }

    public Block currentBlock() {
        return this.currentBlock;
    }

    public void setCurrentBlock(Block block) {
        this.currentBlock = block;
    }

    public Block newBlock() {
        return new Block(this.graph());
    }

    public void setJumpExitNode(Block block, Block targetBlock) {
        block.setExitNode(new JumpNode(block, readSideEffect(block), targetBlock));
    }

    public void setIfExitNode(Block block, Node condition, Block caseTrue, Block caseFalse) {
        block.setExitNode(new IfNode(block, readSideEffect(block), condition, caseTrue, caseFalse));
    }

    public void setReturnExitNode(Block block, Node result) {
        block.setExitNode(new ReturnNode(block, readSideEffect(block), result));
    }

    public Phi newPhi(Block block) {
        // don't transform phi directly, it is not ready yet
        return new Phi(block);
    }

    public Node newPhiWithOperands(Block block, Node... operands) {
        Phi phi = newPhi(block);
        for (Node operand : operands) {
            phi.appendOperand(operand);
        }
        return tryRemoveTrivialPhi(phi);
    }

    public IrGraph graph() {
        return this.graph;
    }

    void writeVariable(Name variable, Block block, Node value) {
        this.currentDef.computeIfAbsent(variable, _ -> new HashMap<>()).put(block, value);
    }

    Node readVariable(Name variable, Block block) {
        return readVariable(variable, block, currentBlock());
    }

    private Node readVariable(Name variable, Block block, Block origin) {
        Node node = this.currentDef.getOrDefault(variable, Map.of()).get(block);
        if (node != null) {
            return node;
        }
        return readVariableRecursive(variable, block, origin);
    }

    private Node readVariableRecursive(Name variable, Block block, Block origin) {
        Node val;
        if (!this.sealedBlocks.contains(block)) {
            val = newPhi(origin);
            this.incompletePhis.computeIfAbsent(block, _ -> new HashMap<>()).put(variable, (Phi) val);
        } else if (block.predecessors().size() == 1) {
            val = readVariable(variable, block.predecessors().getFirst().block(), origin);
        } else {
            val = newPhi(origin);
            writeVariable(variable, block, val);
            val = addPhiOperands(variable, (Phi) val, block);
        }
        writeVariable(variable, block, val);
        return val;
    }

    Node addPhiOperands(Name variable, Phi phi, Block block) {
        for (Node pred : block.predecessors()) {
            phi.appendOperand(readVariable(variable, pred.block(), pred.block()));
        }
        return tryRemoveTrivialPhi(phi);
    }

    Node tryRemoveTrivialPhi(Phi phi) {
        Node same = null;
        for (Node operand : phi.operands()) {
            if (operand.equals(same) || operand.equals(phi)) continue;
            if (same != null) return phi;
            same = operand;
        }
        if (same == null) {
            // Phi unreachable or in start block -> use dummy node
            return new InvalidNode(phi.block());
        }
        Set<Node> users = this.graph.successors(phi);
        for (Node user : users) {
            if (user == phi) continue;
            user.replacePredecessor(phi, same);
        }

        for (Node user : users) {
            if (user instanceof Phi && user != phi) {
                tryRemoveTrivialPhi((Phi) user);
            }
        }
        return same;
    }

    void sealBlock(Block block) {
        for (Map.Entry<Name, Phi> entry : this.incompletePhis.getOrDefault(block, Map.of()).entrySet()) {
            addPhiOperands(entry.getKey(), entry.getValue(), block);
        }
        this.sealedBlocks.add(block);
    }

    public void writeCurrentSideEffect(Node node) {
        writeSideEffect(currentBlock(), node);
    }

    private void writeSideEffect(Block block, Node node) {
        this.currentSideEffect.put(block, node);
    }

    public Node readCurrentSideEffect() {
        return readSideEffect(currentBlock());
    }

    private Node readSideEffect(Block block) {
        return readSideEffect(block, currentBlock());
    }

    private Node readSideEffect(Block block, Block origin) {
        Node node = this.currentSideEffect.get(block);
        if (node != null) {
            return node;
        }
        return readSideEffectRecursive(block, origin);
    }

    private Node readSideEffectRecursive(Block block, Block origin) {
        Node val;
        if (!this.sealedBlocks.contains(block)) {
            val = newPhi(origin);
            Phi old = this.incompleteSideEffectPhis.put(block, (Phi) val);
            assert old == null : "double readSideEffectRecursive for " + block;
        } else if (block.predecessors().size() == 1) {
            val = readSideEffect(block.predecessors().getFirst().block(), origin);
        } else {
            val = newPhi(origin);
            writeSideEffect(block, val);
            val = addPhiOperands((Phi) val, block);
        }
        writeSideEffect(block, val);
        return val;
    }

    Node addPhiOperands(Phi phi, Block block) {
        for (Node pred : block.predecessors()) {
            phi.appendOperand(readSideEffect(pred.block(), pred.block()));
        }
        return tryRemoveTrivialPhi(phi);
    }

}
