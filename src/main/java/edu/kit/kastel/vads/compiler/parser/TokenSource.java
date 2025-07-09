package edu.kit.kastel.vads.compiler.parser;

import edu.kit.kastel.vads.compiler.lexer.Identifier;
import edu.kit.kastel.vads.compiler.lexer.Keyword;
import edu.kit.kastel.vads.compiler.lexer.KeywordType;
import edu.kit.kastel.vads.compiler.lexer.Lexer;
import edu.kit.kastel.vads.compiler.lexer.Operator;
import edu.kit.kastel.vads.compiler.lexer.Operator.OperatorType;
import edu.kit.kastel.vads.compiler.lexer.Separator;
import edu.kit.kastel.vads.compiler.lexer.Separator.SeparatorType;
import edu.kit.kastel.vads.compiler.lexer.Token;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class TokenSource {
    private final List<Token> tokens;
    private int idx;

    public TokenSource(Lexer lexer) {
        this.tokens = Stream.generate(lexer::nextToken)
            .takeWhile(Optional::isPresent)
            .map(Optional::orElseThrow)
            .toList();
    }

    TokenSource(List<Token> tokens) {
        this.tokens = List.copyOf(tokens);
    }

    public Token peek() {
        expectHasMore();
        return this.tokens.get(this.idx);
    }

    public Token peek(int n) {
        expectHasMore(n);
        return this.tokens.get(this.idx + n);
    }

    public Keyword expectKeyword(KeywordType type) {
        Token token = peek();
        if (!(token instanceof Keyword kw) || kw.type() != type) {
            throw new ParseException("expected keyword '" + type + "' but got " + token);
        }
        this.idx++;
        return kw;
    }

    public Keyword expectKeyword(KeywordType... type) {
        Token token = peek();
        List<KeywordType> typeList = List.of(type);
        if (!(token instanceof Keyword kw) || !typeList.contains(kw.type())) {
            throw new ParseException("expected keyword '" + Arrays.toString(type) + "' but got " + token);
        }
        this.idx++;
        return kw;
    }

    public Separator expectSeparator(SeparatorType type) {
        Token token = peek();
        if (!(token instanceof Separator sep) || sep.type() != type) {
            throw new ParseException("expected separator '" + type + "' but got " + token);
        }
        this.idx++;
        return sep;
    }

    public Separator expectSeparator(SeparatorType... type) {
        Token token = peek();
        List<SeparatorType> typeList = List.of(type);
        if (!(token instanceof Separator sep) || !typeList.contains(sep.type())) {
            throw new ParseException("expected separator '" + Arrays.toString(type) + "' but got " + token);
        }
        this.idx++;
        return sep;
    }

    public Operator expectOperator(OperatorType type) {
        Token token = peek();
        if (!(token instanceof Operator op) || op.type() != type) {
            throw new ParseException("expected operator '" + type + "' but got " + token);
        }
        this.idx++;
        return op;
    }

    public Operator expectOperator(OperatorType... type) {
        Token token = peek();
        List<OperatorType> typeList = List.of(type);
        if (!(token instanceof Operator op) || !typeList.contains(op.type())) {
            throw new ParseException("expected operator '" + Arrays.toString(type) + "' but got " + token);
        }
        this.idx++;
        return op;
    }

    public Identifier expectIdentifier() {
        Token token = peek();
        if (!(token instanceof Identifier ident)) {
            throw new ParseException("expected identifier but got " + token);
        }
        this.idx++;
        return ident;
    }

    public Token consume() {
        Token token = peek();
        this.idx++;
        return token;
    }

    public boolean hasMore() {
        return this.idx < this.tokens.size();
    }

    public boolean hasMore(int n) {
        return this.idx + n < this.tokens.size();
    }

    private void expectHasMore() {
        if (this.idx >= this.tokens.size()) {
            throw new ParseException("reached end of file");
        }
    }

    private void expectHasMore(int n) {
        if (this.idx + n >= this.tokens.size()) {
            throw new ParseException("reached end of file");
        }
    }
}
