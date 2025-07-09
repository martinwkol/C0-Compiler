package edu.kit.kastel.vads.compiler.ir;

import edu.kit.kastel.vads.compiler.ir.node.*;
import edu.kit.kastel.vads.compiler.ir.optimize.Optimizer;
import edu.kit.kastel.vads.compiler.ir.util.DebugInfo;
import edu.kit.kastel.vads.compiler.ir.util.DebugInfoHelper;
import edu.kit.kastel.vads.compiler.ir.util.LoopInfo;
import edu.kit.kastel.vads.compiler.lexer.Operator;
import edu.kit.kastel.vads.compiler.parser.ast.*;
import edu.kit.kastel.vads.compiler.parser.symbol.Name;
import edu.kit.kastel.vads.compiler.parser.visitor.Visitor;
import edu.kit.kastel.vads.compiler.util.Union;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.BinaryOperator;

/// SSA translation as described in
/// [`Simple and Efficient Construction of Static Single Assignment Form`](https://compilers.cs.uni-saarland.de/papers/bbhlmz13cc.pdf).
///
/// This implementation also tracks side effect edges that can be used to avoid reordering of operations that cannot be
/// reordered.
///
/// We recommend to read the paper to better understand the mechanics implemented here.
public class SsaTranslation {
    private final FunctionTree function;
    private final GraphConstructor constructor;

    public SsaTranslation(FunctionTree function, Optimizer optimizer) {
        this.function = function;
        this.constructor = new GraphConstructor(optimizer, function.name().name().asString());
    }

    public IrGraph translate() {
        var visitor = new SsaTranslationVisitor();
        this.function.accept(visitor, this);
        return this.constructor.graph();
    }

    private void writeVariable(Name variable, Block block, Node value) {
        this.constructor.writeVariable(variable, block, value);
    }

    private Node readVariable(Name variable, Block block) {
        return this.constructor.readVariable(variable, block);
    }

    private Block currentBlock() {
        return this.constructor.currentBlock();
    }

    private static class SsaTranslationVisitor implements Visitor<SsaTranslation, Optional<Node>> {

        @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
        private static final Optional<Node> NOT_AN_EXPRESSION = Optional.empty();

        private final Deque<DebugInfo> debugStack = new ArrayDeque<>();
        private final Deque<LoopInfo> loopStack = new ArrayDeque<>();

        private void pushSpan(Tree tree) {
            this.debugStack.push(DebugInfoHelper.getDebugInfo());
            DebugInfoHelper.setDebugInfo(new DebugInfo.SourceInfo(tree.span()));
        }

        private void popSpan() {
            DebugInfoHelper.setDebugInfo(this.debugStack.pop());
        }

        @Override
        public Optional<Node> visit(AssignmentTree assignmentTree, SsaTranslation data) {
            pushSpan(assignmentTree);
            BinaryOperator<Node> desugar = switch (assignmentTree.operator().type()) {
                case ASSIGN_MINUS -> data.constructor::newSub;
                case ASSIGN_PLUS -> data.constructor::newAdd;
                case ASSIGN_MUL -> data.constructor::newMul;
                case ASSIGN_DIV -> (lhs, rhs) -> projResultDivMod(data, data.constructor.newDiv(lhs, rhs));
                case ASSIGN_MOD -> (lhs, rhs) -> projResultDivMod(data, data.constructor.newMod(lhs, rhs));
                case ASSIGN_BITWISE_AND -> data.constructor::newBitAnd;
                case ASSIGN_BITWISE_OR -> data.constructor::newBitOr;
                case ASSIGN_BITWISE_XOR -> data.constructor::newBitXor;
                case ASSIGN_SHIFT_LEFT -> data.constructor::newShiftLeft;
                case ASSIGN_SHIFT_RIGHT -> data.constructor::newShiftRight;
                case ASSIGN -> null;
                default ->
                    throw new IllegalArgumentException("not an assignment operator " + assignmentTree.operator());
            };

            switch (assignmentTree.lValue()) {
                case LValueIdentTree(var name) -> {
                    Node rhs = assignmentTree.expression().accept(this, data).orElseThrow();
                    if (desugar != null) {
                        rhs = desugar.apply(data.readVariable(name.name(), data.currentBlock()), rhs);
                    } else { // normal assign
                        rhs = data.constructor.newAssign(rhs);
                    }
                    data.writeVariable(name.name(), data.currentBlock(), rhs);
                }
            }
            popSpan();
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(BinaryOperationTree binaryOperationTree, SsaTranslation data) {
            pushSpan(binaryOperationTree);
            Operator.OperatorType operatorType = binaryOperationTree.operatorType();
            if (operatorType == Operator.OperatorType.LOGICAL_OR || operatorType == Operator.OperatorType.LOGICAL_AND) {
                if (operatorType == Operator.OperatorType.LOGICAL_OR) {
                    return Optional.of(ternaryCondition(
                            binaryOperationTree.lhs(),
                            Union.fromSecond(true),
                            Union.fromFirst(binaryOperationTree.rhs()),
                            data
                    ));
                } else {
                    return Optional.of(ternaryCondition(
                            binaryOperationTree.lhs(),
                            Union.fromFirst(binaryOperationTree.rhs()),
                            Union.fromSecond(false),
                            data
                    ));
                }
            }
            Node lhs = binaryOperationTree.lhs().accept(this, data).orElseThrow();
            Node rhs = binaryOperationTree.rhs().accept(this, data).orElseThrow();
            Node res = switch (binaryOperationTree.operatorType()) {
                case MINUS -> data.constructor.newSub(lhs, rhs);
                case PLUS -> data.constructor.newAdd(lhs, rhs);
                case MUL -> data.constructor.newMul(lhs, rhs);
                case DIV -> projResultDivMod(data, data.constructor.newDiv(lhs, rhs));
                case MOD -> projResultDivMod(data, data.constructor.newMod(lhs, rhs));
                case BITWISE_AND -> data.constructor.newBitAnd(lhs, rhs);
                case BITWISE_OR -> data.constructor.newBitOr(lhs, rhs);
                case BITWISE_XOR -> data.constructor.newBitXor(lhs, rhs);
                case SHIFT_LEFT -> data.constructor.newShiftLeft(lhs, rhs);
                case SHIFT_RIGHT -> data.constructor.newShiftRight(lhs, rhs);
                case EQUALITY -> data.constructor.newEquals(lhs, rhs);
                case DISEQUALITY -> data.constructor.newUnequals(lhs, rhs);
                case SMALLER -> data.constructor.newSmaller(lhs, rhs);
                case SMALLER_EQUAL -> data.constructor.newSmallerEq(lhs, rhs);
                case BIGGER -> data.constructor.newBigger(lhs, rhs);
                case BIGGER_EQUAL -> data.constructor.newBiggerEq(lhs, rhs);
                default ->
                    throw new IllegalArgumentException("not a binary expression operator " + binaryOperationTree.operatorType());
            };
            popSpan();
            return Optional.of(res);
        }

        @Override
        public Optional<Node> visit(UnaryOperatorTree unaryOperatorTree, SsaTranslation data) {
            pushSpan(unaryOperatorTree);
            Node node = unaryOperatorTree.expression().accept(this, data).orElseThrow();
            Node res = switch (unaryOperatorTree.operator()) {
                case MINUS -> data.constructor.newSub(data.constructor.newConstInt(0), node);
                case LOGICAL_NOT -> data.constructor.newLogNegation(node);
                case BITWISE_NOT -> data.constructor.newBitNegation(node);
                default ->
                        throw new IllegalArgumentException("not a unary expression operator " + unaryOperatorTree.operator());
            };
            popSpan();
            return Optional.of(res);
        }

        @Override
        public Optional<Node> visit(CallTree callTree, SsaTranslation data) {
            pushSpan(callTree);
            List<Node> parameters = new ArrayList<>(callTree.parameters().size());
            for (ExpressionTree parameter : callTree.parameters()) {
                parameters.add(parameter.accept(this, data).orElseThrow());
            }
            String functionName = callTree.functionName().name().asString();
            Node ret = projResultCall(data, data.constructor.newCall(functionName, parameters));
            popSpan();
            return Optional.of(ret);
        }

        @Override
        public Optional<Node> visit(IfTree ifTree, SsaTranslation data) {
            pushSpan(ifTree);

            Node condition = ifTree.condition().accept(this, data).orElseThrow();

            Block beforeIf = data.constructor.currentBlock();
            Block trueEntry = data.constructor.newBlock();
            Block falseEntry = data.constructor.newBlock();

            data.constructor.setIfExitNode(beforeIf, condition, trueEntry, falseEntry);
            data.constructor.sealBlock(trueEntry);
            data.constructor.sealBlock(falseEntry);

            data.constructor.setCurrentBlock(trueEntry);
            ifTree.caseTrue().accept(this, data);
            Block trueExit = data.constructor.currentBlock();

            Block falseExit = falseEntry;
            if (ifTree.caseFalse() != null) {
                data.constructor.setCurrentBlock(falseEntry);
                ifTree.caseFalse().accept(this, data);
                falseExit = data.constructor.currentBlock();
            }

            Block ifExit = data.constructor.newBlock();
            if (trueExit.exitNode() == null) data.constructor.setJumpExitNode(trueExit, ifExit);
            if (falseExit.exitNode() == null) data.constructor.setJumpExitNode(falseExit, ifExit);
            data.constructor.sealBlock(ifExit);
            data.constructor.setCurrentBlock(ifExit);

            popSpan();
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(TernaryConditionTree ternaryConditionTree, SsaTranslation data) {
            pushSpan(ternaryConditionTree);

            Optional<Node> result = Optional.of(ternaryCondition(
                ternaryConditionTree.condition(),
                Union.fromFirst(ternaryConditionTree.caseTrue()),
                Union.fromFirst(ternaryConditionTree.caseFalse()),
                data
            ));

            popSpan();
            return result;
        }

        @Override
        public Optional<Node> visit(WhileTree whileTree, SsaTranslation data) {
            pushSpan(whileTree);

            Block beforeWhile = data.constructor.currentBlock();
            Block whileHeader = data.constructor.newBlock();
            Block bodyEntry = data.constructor.newBlock();
            Block loopExit = data.constructor.newBlock();

            // cannot seal while header yet
            data.constructor.setJumpExitNode(beforeWhile, whileHeader);

            loopStack.push(new LoopInfo(whileHeader, loopExit));
            data.constructor.setCurrentBlock(whileHeader);
            Node condition = whileTree.condition().accept(this, data).orElseThrow();
            data.constructor.setIfExitNode(whileHeader, condition, bodyEntry, loopExit);
            data.constructor.sealBlock(bodyEntry);

            data.constructor.setCurrentBlock(bodyEntry);
            whileTree.body().accept(this, data);
            Block bodyExit = data.constructor.currentBlock();
            // might already be set if block ends with break or continue
            if (bodyExit.exitNode() == null) {
                data.constructor.setJumpExitNode(bodyExit, whileHeader);
            }
            loopStack.pop();

            data.constructor.sealBlock(whileHeader);
            data.constructor.sealBlock(loopExit);
            data.constructor.setCurrentBlock(loopExit);

            popSpan();
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(ForTree forTree, SsaTranslation data) {
            pushSpan(forTree);

            if (forTree.initializer() != null) {
                forTree.initializer().accept(this, data);
            }

            Block beforeLoop = data.constructor.currentBlock();
            Block loopHeader = data.constructor.newBlock();
            Block bodyEntry = data.constructor.newBlock();
            Block bodyExit = data.constructor.newBlock();
            Block loopExit = data.constructor.newBlock();

            data.constructor.setJumpExitNode(beforeLoop, loopHeader);

            loopStack.push(new LoopInfo(bodyExit, loopExit));
            data.constructor.setCurrentBlock(loopHeader);
            Node condition = forTree.condition().accept(this, data).orElseThrow();
            data.constructor.setIfExitNode(loopHeader, condition, bodyEntry, loopExit);
            data.constructor.sealBlock(bodyEntry);

            data.constructor.setCurrentBlock(bodyEntry);
            forTree.body().accept(this, data);
            Block preExitBody = data.constructor.currentBlock();
            // might already be set if block ends with break or continue
            if (preExitBody.exitNode() == null) {
                data.constructor.setJumpExitNode(preExitBody, bodyExit);
            }

            data.constructor.setCurrentBlock(bodyExit);
            if (forTree.step() != null) {
                forTree.step().accept(this, data);
            }
            data.constructor.setJumpExitNode(bodyExit, loopHeader);
            loopStack.pop();

            data.constructor.sealBlock(bodyExit);
            data.constructor.sealBlock(loopHeader);
            data.constructor.sealBlock(loopExit);
            data.constructor.setCurrentBlock(loopExit);

            popSpan();
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(BreakTree breakTree, SsaTranslation data) {
            pushSpan(breakTree);

            if (loopStack.isEmpty()) {
                throw new RuntimeException("break statement outside of loop");
            }
            data.constructor.setJumpExitNode(data.constructor.currentBlock(), loopStack.peek().loopExit());

            popSpan();
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(ContinueTree continueTree, SsaTranslation data) {
            pushSpan(continueTree);

            if (loopStack.isEmpty()) {
                throw new RuntimeException("continue statement outside of loop");
            }
            data.constructor.setJumpExitNode(data.constructor.currentBlock(), loopStack.peek().bodyExit());

            popSpan();
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(BlockTree blockTree, SsaTranslation data) {
            pushSpan(blockTree);
            for (StatementTree statement : blockTree.statements()) {
                statement.accept(this, data);
                // skip everything after a return in a block
                if (statement instanceof ReturnTree) {
                    break;
                }
            }
            popSpan();
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(DeclarationTree declarationTree, SsaTranslation data) {
            pushSpan(declarationTree);
            if (declarationTree.initializer() != null) {
                Node rhs = declarationTree.initializer().accept(this, data).orElseThrow();
                data.writeVariable(declarationTree.name().name(), data.currentBlock(), rhs);
            }
            popSpan();
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(FunctionTree functionTree, SsaTranslation data) {
            pushSpan(functionTree);
            Node start = data.constructor.newStart();
            data.constructor.writeCurrentSideEffect(data.constructor.newSideEffectProj(start));
            for (int i = 0; i < functionTree.parameters().size(); i++) {
                ParameterTree parameter = functionTree.parameters().get(i);
                FParameterNode node = new FParameterNode(data.currentBlock(), i);
                data.writeVariable(parameter.name().name(), data.currentBlock(), node);
            }
            functionTree.body().accept(this, data);
            popSpan();
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(IdentExpressionTree identExpressionTree, SsaTranslation data) {
            pushSpan(identExpressionTree);
            Node value = data.readVariable(identExpressionTree.name().name(), data.currentBlock());
            popSpan();
            return Optional.of(value);
        }

        @Override
        public Optional<Node> visit(BoolLiteralTree boolLiteralTree, SsaTranslation data) {
            pushSpan(boolLiteralTree);
            Node node = data.constructor.newConstBool(boolLiteralTree.value());
            popSpan();
            return Optional.of(node);
        }

        @Override
        public Optional<Node> visit(IntLiteralTree intLiteralTree, SsaTranslation data) {
            pushSpan(intLiteralTree);
            Node node = data.constructor.newConstInt((int) intLiteralTree.parseValue().orElseThrow());
            popSpan();
            return Optional.of(node);
        }

        @Override
        public Optional<Node> visit(LValueIdentTree lValueIdentTree, SsaTranslation data) {
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(NameTree nameTree, SsaTranslation data) {
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(ProgramTree programTree, SsaTranslation data) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Node> visit(ParameterTree parameterTree, SsaTranslation data) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Node> visit(ReturnTree returnTree, SsaTranslation data) {
            pushSpan(returnTree);
            Node node = returnTree.expression().accept(this, data).orElseThrow();
            data.constructor.setReturnExitNode(data.constructor.currentBlock(), node);
            popSpan();
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(TypeTree typeTree, SsaTranslation data) {
            throw new UnsupportedOperationException();
        }

        private Node ternaryCondition(
                ExpressionTree condition,
                Union<ExpressionTree, Boolean> caseTrue,
                Union<ExpressionTree, Boolean> caseFalse,
                SsaTranslation data
        ) {
            Node conditionNode = condition.accept(this, data).orElseThrow();

            Block beforeIf = data.constructor.currentBlock();
            Block trueEntry = data.constructor.newBlock();
            Block falseEntry = data.constructor.newBlock();

            data.constructor.setIfExitNode(beforeIf, conditionNode, trueEntry, falseEntry);
            data.constructor.sealBlock(trueEntry);
            data.constructor.sealBlock(falseEntry);

            data.constructor.setCurrentBlock(trueEntry);
            Node resultTrue;
            if (caseTrue.isFirst()) {
                resultTrue = data.constructor.newAssign(
                        caseTrue.first().accept(this, data).orElseThrow()
                );
            } else {
                resultTrue = data.constructor.newConstBool(caseTrue.second());
            }
            Block trueExit = data.constructor.currentBlock();

            data.constructor.setCurrentBlock(falseEntry);
            Node resultFalse;
            if (caseFalse.isFirst()) {
                resultFalse = data.constructor.newAssign(
                        caseFalse.first().accept(this, data).orElseThrow()
                );
            } else {
                resultFalse = data.constructor.newConstBool(caseFalse.second());
            }
            Block falseExit = data.constructor.currentBlock();

            Block ifExit = data.constructor.newBlock();
            data.constructor.setJumpExitNode(trueExit, ifExit);
            data.constructor.setJumpExitNode(falseExit, ifExit);
            data.constructor.sealBlock(ifExit);
            data.constructor.setCurrentBlock(ifExit);

            return data.constructor.newPhiWithOperands(ifExit, resultTrue, resultFalse);
        }

        private Node projResultDivMod(SsaTranslation data, Node divMod) {
            // make sure we actually have a div or a mod, as optimizations could
            // have changed it to something else already
            if (!(divMod instanceof DivNode || divMod instanceof ModNode)) {
                return divMod;
            }
            Node projSideEffect = data.constructor.newSideEffectProj(divMod);
            data.constructor.writeCurrentSideEffect(projSideEffect);
            return data.constructor.newResultProj(divMod);
        }

        private Node projResultCall(SsaTranslation data, Node call) {
            if (!(call instanceof CallNode)) return call;
            Node projSideEffect = data.constructor.newSideEffectProj(call);
            data.constructor.writeCurrentSideEffect(projSideEffect);
            return data.constructor.newResultProj(projSideEffect);
        }
    }


}
