package edu.kit.kastel.vads.compiler.semantic;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static java.util.Map.entry;  

import edu.kit.kastel.vads.compiler.parser.ast.ProgramTree;
import edu.kit.kastel.vads.compiler.parser.type.BasicType;
import edu.kit.kastel.vads.compiler.parser.type.FunctionType;
import edu.kit.kastel.vads.compiler.parser.visitor.RecursivePostorderVisitor;
import edu.kit.kastel.vads.compiler.parser.visitor.RecursiveVisitor;

public class SemanticAnalysis {

    private final ProgramTree program;

    public SemanticAnalysis(ProgramTree program) {
        this.program = program;
    }

    public void analyze() {
        this.program.accept(
                new RecursivePostorderVisitor<>(new IntegerLiteralRangeAnalysis()),
                new Namespace<>()
        );
        Namespace<FunctionType> functionTypeNamespace = analyseFunctions();
        this.program.accept(new VariableStatusAnalysisVisitor(), new VariableStatus());
        this.program.accept(
                new RecursivePostorderVisitor<>(new TypeAnalysis(functionTypeNamespace)),
                new TypeAnalysis.TypeMapping()
        );
        this.program.accept(
                new RecursiveVisitor<>(
                        new BreakContinueAnalysis.PreorderVisitor(),
                        new BreakContinueAnalysis.PostorderVisitor()
                ),
                new BreakContinueAnalysis.Counter()
        );
        this.program.accept(new RecursivePostorderVisitor<>(
                new ForLoopStepAnalysis()),
                new Namespace<>()
        );
    }

    private Namespace<FunctionType> analyseFunctions() {
        Namespace<FunctionType> functionTypeNamespace = new Namespace<>();
        Map<String, FunctionType> buintinFunctions = Map.ofEntries(
                entry("print", new FunctionType(BasicType.INT, List.of(BasicType.INT))),
                entry("read", new FunctionType(BasicType.INT, List.of())),
                entry("flush", new FunctionType(BasicType.INT, List.of()))
        );

        // add builtin functions to namespace
        for (Entry<String, FunctionType> entry : buintinFunctions.entrySet()) {
                functionTypeNamespace.put(entry.getKey(), entry.getValue());
        }

        // check function definitions
        this.program.accept(
                new FunctionDefinitionAnalysis(), 
                functionTypeNamespace
        );

        // check if functions return
        this.program.accept(
                new RecursivePostorderVisitor<>(new ReturnAnalysis()),
                new ReturnAnalysis.ReturnState()
        );

        // Check if called functions exist
        this.program.accept(new CallAnalysis(functionTypeNamespace), new Namespace<>());

        // check main function
        FunctionType mainFunctionType = functionTypeNamespace.get("main");
        if (mainFunctionType == null) {
                throw new SemanticException("No main function");
        }
        if (!mainFunctionType.returnType().equals(BasicType.INT)) {
                throw new SemanticException("Main function must return int");
        }
        if (!mainFunctionType.parameterTypes().isEmpty()) {
                throw new SemanticException("Main must not take parameters");
        }

        return functionTypeNamespace;
    }

}
