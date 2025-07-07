package edu.kit.kastel.vads.compiler.semantic.general;

import edu.kit.kastel.vads.compiler.parser.ast.ProgramTree;
import edu.kit.kastel.vads.compiler.parser.type.FunctionType;
import edu.kit.kastel.vads.compiler.parser.visitor.RecursivePostorderVisitor;
import edu.kit.kastel.vads.compiler.semantic.util.Namespace;
import edu.kit.kastel.vads.compiler.semantic.util.VariableStatus;

public class GeneralAnalysis {
    private final ProgramTree program;
    private final Namespace<FunctionType> functionNamespace;

    public static void analyze(ProgramTree program, Namespace<FunctionType> functionNamespace) {
        GeneralAnalysis ga = new GeneralAnalysis(program, functionNamespace);
        ga.checkIntegerLiteralRange();
        ga.checkVariableStatus();
        ga.checkTypes();
    }

    private GeneralAnalysis(ProgramTree program, Namespace<FunctionType> functionNamespace) {
        this.program = program;
        this.functionNamespace = functionNamespace;
    }

    private void checkIntegerLiteralRange() {
        this.program.accept(
                new RecursivePostorderVisitor<>(new IntegerLiteralRangeAnalysis()),
                new Namespace<>()
        );
    }
    
    private void checkVariableStatus() {
        this.program.accept(
                new VariableStatusAnalysisVisitor(), 
                new VariableStatus()
        );
    }


    private void checkTypes() {
        this.program.accept(
                new RecursivePostorderVisitor<>(new TypeAnalysis(functionNamespace)),
                new TypeAnalysis.TypeMapping()
        );
    }
}
