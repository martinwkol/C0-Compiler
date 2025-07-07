package edu.kit.kastel.vads.compiler.semantic.functions;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import edu.kit.kastel.vads.compiler.parser.ast.ProgramTree;
import edu.kit.kastel.vads.compiler.parser.type.BasicType;
import edu.kit.kastel.vads.compiler.parser.type.FunctionType;
import edu.kit.kastel.vads.compiler.parser.visitor.RecursivePostorderVisitor;
import edu.kit.kastel.vads.compiler.semantic.SemanticException;
import edu.kit.kastel.vads.compiler.semantic.util.Namespace;

import static java.util.Map.entry;  

public class FunctionAnalysis {
    private final ProgramTree program;
    private final Namespace<FunctionType> namespace = new Namespace<>();

    public static Namespace<FunctionType> analyze(ProgramTree program) {
        FunctionAnalysis fa = new FunctionAnalysis(program);
        fa.initBuiltin();
        fa.checkDefinitions();
        fa.checkReturns();
        fa.checkCalls();
        fa.checkMainFunction();
        return fa.namespace;
    }

    private FunctionAnalysis(ProgramTree program) {
        this.program = program;
    }

    private void initBuiltin() {
        Map<String, FunctionType> buintinFunctions = Map.ofEntries(
                entry("print", new FunctionType(BasicType.INT, List.of(BasicType.INT))),
                entry("read", new FunctionType(BasicType.INT, List.of())),
                entry("flush", new FunctionType(BasicType.INT, List.of()))
        );

        // add builtin functions to namespace
        for (Entry<String, FunctionType> entry : buintinFunctions.entrySet()) {
                namespace.put(entry.getKey(), entry.getValue());
        }
    }

    private void checkDefinitions() {
        this.program.accept(
                new FunctionDefinitionAnalysis(), 
                namespace
        );
    }

    private void checkReturns() {
        this.program.accept(
                new RecursivePostorderVisitor<>(new ReturnAnalysis()),
                new ReturnAnalysis.ReturnState()
        );
    }

    private void checkCalls() {
        this.program.accept(new CallAnalysis(namespace), new Namespace<>());
    }

    private void checkMainFunction() {
        FunctionType mainFunctionType = namespace.get("main");
        if (mainFunctionType == null) {
                throw new SemanticException("No main function");
        }
        if (!mainFunctionType.returnType().equals(BasicType.INT)) {
                throw new SemanticException("Main function must return int");
        }
        if (!mainFunctionType.parameterTypes().isEmpty()) {
                throw new SemanticException("Main must not take parameters");
        }
    }
}
