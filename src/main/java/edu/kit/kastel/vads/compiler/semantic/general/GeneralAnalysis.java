package edu.kit.kastel.vads.compiler.semantic.general;

import edu.kit.kastel.vads.compiler.parser.ast.FunctionTree;
import edu.kit.kastel.vads.compiler.parser.ast.ProgramTree;
import edu.kit.kastel.vads.compiler.parser.type.FunctionType;
import edu.kit.kastel.vads.compiler.parser.visitor.RecursivePostorderVisitor;
import edu.kit.kastel.vads.compiler.semantic.util.Namespace;
import edu.kit.kastel.vads.compiler.semantic.util.VariableStatus;

public class GeneralAnalysis {
    private final FunctionTree function;
    private final Namespace<FunctionType> functionNamespace;

    public static void analyze(ProgramTree program, Namespace<FunctionType> functionNamespace) {
        for (FunctionTree function : program.topLevelTrees()) {
            GeneralAnalysis ga = new GeneralAnalysis(function, functionNamespace);
            ga.checkIntegerLiteralRange();
            ga.checkVariableStatus();
            ga.checkTypes();
        }
    }

    private GeneralAnalysis(FunctionTree function, Namespace<FunctionType> functionNamespace) {
        this.function = function;
        this.functionNamespace = functionNamespace;
    }

    private void checkIntegerLiteralRange() {
        this.function.accept(
                new RecursivePostorderVisitor<>(new IntegerLiteralRangeAnalysis()),
                new Namespace<>()
        );
    }
    
    private void checkVariableStatus() {
        this.function.accept(
                new VariableStatusAnalysisVisitor(), 
                new VariableStatus()
        );
    }


    private void checkTypes() {
        this.function.accept(
                new RecursivePostorderVisitor<>(new TypeAnalysis(function.name(), functionNamespace)),
                new TypeAnalysis.TypeMapping()
        );
    }
}
