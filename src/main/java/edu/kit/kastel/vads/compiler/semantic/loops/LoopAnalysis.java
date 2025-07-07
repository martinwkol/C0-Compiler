package edu.kit.kastel.vads.compiler.semantic.loops;

import edu.kit.kastel.vads.compiler.parser.ast.ProgramTree;
import edu.kit.kastel.vads.compiler.parser.visitor.RecursivePostorderVisitor;
import edu.kit.kastel.vads.compiler.parser.visitor.RecursiveVisitor;
import edu.kit.kastel.vads.compiler.semantic.util.Namespace;

public class LoopAnalysis {
    private final ProgramTree program;

    public static void analyse(ProgramTree program) {
        LoopAnalysis la = new LoopAnalysis(program);
        la.checkBreakContinue();
        la.checkForLoopStep();
    }

    private LoopAnalysis(ProgramTree program) {
        this.program = program;
    }

    private void checkBreakContinue() {
        this.program.accept(
            new RecursiveVisitor<>(
                    new BreakContinueAnalysis.PreorderVisitor(),
                    new BreakContinueAnalysis.PostorderVisitor()
            ),
            new BreakContinueAnalysis.Counter()
        );
    }

    private void checkForLoopStep() {
        this.program.accept(new RecursivePostorderVisitor<>(
                new ForLoopStepAnalysis()),
                new Namespace<>()
        );
    }

}
