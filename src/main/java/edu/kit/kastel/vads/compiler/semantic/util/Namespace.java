package edu.kit.kastel.vads.compiler.semantic.util;

import edu.kit.kastel.vads.compiler.parser.ast.NameTree;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BinaryOperator;

public class Namespace<T> {

    private final Map<String, T> content;

    public Namespace() {
        this.content = new HashMap<>();
    }

    public void put(NameTree name, T value, BinaryOperator<T> merger) {
        this.content.merge(name.name().asString(), value, merger);
    }

    public void put(String name, T value, BinaryOperator<T> merger) {
        this.content.merge(name, value, merger);
    }

    public void put(NameTree name, T value) {
        this.content.put(name.name().asString(), value);
    }

    public void put(String name, T value) {
        this.content.put(name, value);
    }

    public @Nullable T get(NameTree name) {
        return this.content.get(name.name().asString());
    }

    public @Nullable T get(String name) {
        return this.content.get(name);
    }

    public boolean contains(NameTree name) {
        return this.content.containsKey(name.name().asString());
    }

    public boolean contains(String name) {
        return this.content.containsKey(name);
    }

}
