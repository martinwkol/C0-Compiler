package edu.kit.kastel.vads.compiler.semantic;

import edu.kit.kastel.vads.compiler.parser.ast.ProgramTree;
import edu.kit.kastel.vads.compiler.parser.type.FunctionType;
import edu.kit.kastel.vads.compiler.semantic.functions.FunctionAnalysis;
import edu.kit.kastel.vads.compiler.semantic.general.GeneralAnalysis;
import edu.kit.kastel.vads.compiler.semantic.loops.LoopAnalysis;
import edu.kit.kastel.vads.compiler.semantic.util.Namespace;

public class SemanticAnalysis {

    public static void analyze(ProgramTree program) {
        Namespace<FunctionType> functionTypeNamespace = FunctionAnalysis.analyze(program);
        GeneralAnalysis.analyze(program, functionTypeNamespace);
        LoopAnalysis.analyze(program);
    }

}
