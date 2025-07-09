package edu.kit.kastel.vads.compiler.parser;

import edu.kit.kastel.vads.compiler.lexer.Identifier;
import edu.kit.kastel.vads.compiler.lexer.Keyword;
import edu.kit.kastel.vads.compiler.lexer.KeywordType;
import edu.kit.kastel.vads.compiler.lexer.NumberLiteral;
import edu.kit.kastel.vads.compiler.lexer.Operator;
import edu.kit.kastel.vads.compiler.lexer.Operator.OperatorType;
import edu.kit.kastel.vads.compiler.lexer.Separator;
import edu.kit.kastel.vads.compiler.lexer.Separator.SeparatorType;
import edu.kit.kastel.vads.compiler.Span;
import edu.kit.kastel.vads.compiler.lexer.Token;
import edu.kit.kastel.vads.compiler.parser.ast.*;
import edu.kit.kastel.vads.compiler.parser.symbol.Name;
import edu.kit.kastel.vads.compiler.parser.type.BasicType;

import java.util.ArrayList;
import java.util.List;

public class Parser {
    private final TokenSource tokenSource;

    public Parser(TokenSource tokenSource) {
        this.tokenSource = tokenSource;
    }

    public ProgramTree parseProgram() {
        List<FunctionTree> functions = new ArrayList<>();
        while (this.tokenSource.hasMore() && this.tokenSource.peek().isKeyword(KeywordType.INT, KeywordType.BOOL)) {
            functions.add(parseFunction());
        }
        if (this.tokenSource.hasMore()) {
            throw new ParseException("expected end of input but got " + this.tokenSource.peek());
        }
        return new ProgramTree(functions);
    }

    private FunctionTree parseFunction() {
        Keyword returnType = this.tokenSource.expectKeyword(KeywordType.INT, KeywordType.BOOL);
        Identifier identifier = this.tokenSource.expectIdentifier();
        this.tokenSource.expectSeparator(SeparatorType.PAREN_OPEN);
        List<ParameterTree> parameters = new ArrayList<>();
        boolean moreParameters = !this.tokenSource.peek().isSeparator(SeparatorType.PAREN_CLOSE);
        while (moreParameters) {
            parameters.add(parseParameter());
            if (this.tokenSource.peek().isSeparator(SeparatorType.COMMA)) this.tokenSource.consume();
            else moreParameters = false;
        }
        this.tokenSource.expectSeparator(SeparatorType.PAREN_CLOSE);
        BlockTree body = parseBlock();
        return new FunctionTree(
            new TypeTree(basicType(returnType), returnType.span()),
            name(identifier),
            parameters,
            body
        );
    }

    private ParameterTree parseParameter() {
        Keyword keyword = this.tokenSource.expectKeyword(KeywordType.INT, KeywordType.BOOL);
        Identifier ident = this.tokenSource.expectIdentifier();
        BasicType basicType = basicType(keyword);
        return new ParameterTree(new TypeTree(basicType, keyword.span()), name(ident));
    }

    private BlockTree parseBlock() {
        Separator bodyOpen = this.tokenSource.expectSeparator(SeparatorType.BRACE_OPEN);
        List<StatementTree> statements = new ArrayList<>();
        while (!this.tokenSource.peek().isSeparator(SeparatorType.BRACE_CLOSE)) {
            statements.add(parseStatement());
        }
        Separator bodyClose = this.tokenSource.expectSeparator(SeparatorType.BRACE_CLOSE);
        return new BlockTree(statements, bodyOpen.span().merge(bodyClose.span()));
    }

    private StatementTree parseStatement() {
        // Block statement
        if (this.tokenSource.peek() instanceof Separator sep && sep.type() == SeparatorType.BRACE_OPEN) {
            return parseBlock();
        }

        StatementTree statement = null;
        // Control statements
        if (this.tokenSource.peek() instanceof Keyword keyword) {
            statement = switch (keyword.type()) {
                case KeywordType.IF -> parseIf();
                case KeywordType.WHILE -> parseWhile();
                case KeywordType.FOR -> parseFor();
                default -> null;
            };
            if (statement != null) return statement; // No semicolon required
            statement = switch (keyword.type()) {
                case KeywordType.BREAK -> parseBreak();
                case KeywordType.CONTINUE -> parseContinue();
                case KeywordType.RETURN -> parseReturn();
                default -> null;
            };
        }

        // Declaration and simple statements
        if (statement == null) {
            statement = parseSimpleStatement();
        }
        this.tokenSource.expectSeparator(SeparatorType.SEMICOLON);
        return statement;
    }

    private StatementTree parseSimpleStatement() {
        if (this.tokenSource.peek().isKeyword(KeywordType.INT, KeywordType.BOOL)) return parseDeclaration();
        if (isIdentCall()) return parseIdentCall();
        if (isKeywordCall()) return parseKeywordCall();
        return parseAssignment();
    }

    private StatementTree parseDeclaration() {
        Keyword keyword = this.tokenSource.expectKeyword(KeywordType.INT, KeywordType.BOOL);
        Identifier ident = this.tokenSource.expectIdentifier();
        ExpressionTree expr = null;
        if (this.tokenSource.peek().isOperator(OperatorType.ASSIGN)) {
            this.tokenSource.expectOperator(OperatorType.ASSIGN);
            expr = parseExpression();
        }
        BasicType basicType = basicType(keyword);
        return new DeclarationTree(new TypeTree(basicType, keyword.span()), name(ident), expr);
    }

    private StatementTree parseAssignment() {
        LValueTree lValue = parseLValue();
        Operator assignmentOperator = parseAssignmentOperator();
        ExpressionTree expression = parseExpression();
        return new AssignmentTree(lValue, assignmentOperator, expression);
    }

    private boolean isIdentCall() {
        if (!this.tokenSource.hasMore(1)) return false;
        if (!this.tokenSource.peek(1).isSeparator(SeparatorType.PAREN_OPEN)) return false;
        if (this.tokenSource.peek(0).isIdentifier()) return true;
        return false;
    }

    private boolean isKeywordCall() {
        if (!this.tokenSource.hasMore(1)) return false;
        if (!this.tokenSource.peek(1).isSeparator(SeparatorType.PAREN_OPEN)) return false;
        if (this.tokenSource.peek(0).isIdentifier()) return true;
        
        if (!(this.tokenSource.peek() instanceof Keyword keyword)) return false;
        if (keyword.isKeyword(KeywordType.PRINT, KeywordType.READ, KeywordType.FLUSH)) return true;
        return false;
    }

    private CallTree parseIdentCall() {
        return parseCall(name(this.tokenSource.expectIdentifier()));
    }

    private CallTree parseKeywordCall() {
        return parseCall(name(this.tokenSource.expectKeyword(KeywordType.PRINT, KeywordType.READ, KeywordType.FLUSH)));
    }

    private CallTree parseCall(NameTree name) {
        this.tokenSource.expectSeparator(SeparatorType.PAREN_OPEN);
        List<ExpressionTree> parameters = new ArrayList<>();
        boolean moreParameters = !this.tokenSource.peek().isSeparator(SeparatorType.PAREN_CLOSE);
        while (moreParameters) {
            parameters.add(parseExpression());
            if (this.tokenSource.peek().isSeparator(SeparatorType.COMMA)) this.tokenSource.consume();
            else moreParameters = false;
        }
        Separator closingBracket = this.tokenSource.expectSeparator(SeparatorType.PAREN_CLOSE);
        return new CallTree(name, parameters,closingBracket.span().end());
    }

    private Operator parseAssignmentOperator() {
        if (this.tokenSource.peek() instanceof Operator op) {
            return switch (op.type()) {
                case ASSIGN, ASSIGN_PLUS, ASSIGN_MINUS, ASSIGN_MUL, ASSIGN_DIV, ASSIGN_MOD,
                     ASSIGN_BITWISE_AND, ASSIGN_BITWISE_OR, ASSIGN_BITWISE_XOR,
                     ASSIGN_SHIFT_LEFT, ASSIGN_SHIFT_RIGHT -> {
                    this.tokenSource.consume();
                    yield op;
                }
                default -> throw new ParseException("expected assignment but got " + op.type());
            };
        }
        throw new ParseException("expected assignment but got " + this.tokenSource.peek());
    }

    private LValueTree parseLValue() {
        if (this.tokenSource.peek().isSeparator(SeparatorType.PAREN_OPEN)) {
            this.tokenSource.expectSeparator(SeparatorType.PAREN_OPEN);
            LValueTree inner = parseLValue();
            this.tokenSource.expectSeparator(SeparatorType.PAREN_CLOSE);
            return inner;
        }
        Identifier identifier = this.tokenSource.expectIdentifier();
        return new LValueIdentTree(name(identifier));
    }

    private StatementTree parseIf() {
        Keyword ifKeyword = this.tokenSource.expectKeyword(KeywordType.IF);
        this.tokenSource.expectSeparator(SeparatorType.PAREN_OPEN);
        ExpressionTree condition = parseExpression();
        this.tokenSource.expectSeparator(SeparatorType.PAREN_CLOSE);

        StatementTree conditionTrue = parseStatement();
        StatementTree conditionFalse = null;
        if (this.tokenSource.peek().isKeyword(KeywordType.ELSE)) {
            this.tokenSource.expectKeyword(KeywordType.ELSE);
            conditionFalse = parseStatement();
        }
        return new IfTree(condition, conditionTrue, conditionFalse, ifKeyword.span().start());
    }

    private StatementTree parseWhile() {
        Keyword whileKeyword = this.tokenSource.expectKeyword(KeywordType.WHILE);
        this.tokenSource.expectSeparator(SeparatorType.PAREN_OPEN);
        ExpressionTree condition = parseExpression();
        this.tokenSource.expectSeparator(SeparatorType.PAREN_CLOSE);
        StatementTree loopBody = parseStatement();
        return new WhileTree(condition, loopBody, whileKeyword.span().start());
    }

    private StatementTree parseFor() {
        Keyword forKeyword = this.tokenSource.expectKeyword(KeywordType.FOR);
        this.tokenSource.expectSeparator(SeparatorType.PAREN_OPEN);

        StatementTree initializer = null;
        if (!this.tokenSource.peek().isSeparator(SeparatorType.SEMICOLON)) {
            initializer = parseSimpleStatement();
        }
        this.tokenSource.expectSeparator(SeparatorType.SEMICOLON);

        ExpressionTree condition = parseExpression();
        this.tokenSource.expectSeparator(SeparatorType.SEMICOLON);

        StatementTree step = null;
        if (!this.tokenSource.peek().isSeparator(SeparatorType.PAREN_CLOSE)) {
            step = parseSimpleStatement();
        }
        this.tokenSource.expectSeparator(SeparatorType.PAREN_CLOSE);

        StatementTree body = parseStatement();
        return new ForTree(initializer, condition, step, body, forKeyword.span().start());
    }

    private StatementTree parseBreak() {
        Keyword breakKeyword = this.tokenSource.expectKeyword(KeywordType.BREAK);
        return new BreakTree(breakKeyword.span());
    }

    private StatementTree parseContinue() {
        Keyword continueKeyword = this.tokenSource.expectKeyword(KeywordType.CONTINUE);
        return new ContinueTree(continueKeyword.span());
    }

    private StatementTree parseReturn() {
        Keyword ret = this.tokenSource.expectKeyword(KeywordType.RETURN);
        ExpressionTree expression = parseExpression();
        return new ReturnTree(expression, ret.span().start());
    }

    private ExpressionTree parseExpression() {
        ExpressionTree cond = parseLogicalOr();
        while (this.tokenSource.peek().isOperator(OperatorType.QUESTION)) {
            this.tokenSource.expectOperator(OperatorType.QUESTION);
            ExpressionTree caseTrue = parseExpression();
            this.tokenSource.expectOperator(OperatorType.COLON);
            ExpressionTree caseFalse = parseExpression();
            cond = new TernaryConditionTree(cond, caseTrue, caseFalse);
        }
        return cond;
    }

    private ExpressionTree parseLogicalOr() {
        ExpressionTree lhs = parseLogicalAnd();
        while (this.tokenSource.peek().isOperator(OperatorType.LOGICAL_OR)) {
            this.tokenSource.consume();
            lhs = new BinaryOperationTree(lhs, parseLogicalAnd(), OperatorType.LOGICAL_OR);
        }
        return lhs;
    }

    private ExpressionTree parseLogicalAnd() {
        ExpressionTree lhs = parseBitwiseOr();
        while (this.tokenSource.peek().isOperator(OperatorType.LOGICAL_AND)) {
            this.tokenSource.consume();
            lhs = new BinaryOperationTree(lhs, parseBitwiseOr(), OperatorType.LOGICAL_AND);
        }
        return lhs;
    }

    private ExpressionTree parseBitwiseOr() {
        ExpressionTree lhs = parseBitwiseXor();
        while (this.tokenSource.peek().isOperator(OperatorType.BITWISE_OR)) {
            this.tokenSource.consume();
            lhs = new BinaryOperationTree(lhs, parseBitwiseXor(), OperatorType.BITWISE_OR);
        }
        return lhs;
    }

    private ExpressionTree parseBitwiseXor() {
        ExpressionTree lhs = parseBitwiseAnd();
        while (this.tokenSource.peek().isOperator(OperatorType.BITWISE_XOR)) {
            this.tokenSource.consume();
            lhs = new BinaryOperationTree(lhs, parseBitwiseAnd(), OperatorType.BITWISE_XOR);
        }
        return lhs;
    }

    private ExpressionTree parseBitwiseAnd() {
        ExpressionTree lhs = parseDisEquality();
        while (this.tokenSource.peek().isOperator(OperatorType.BITWISE_AND)) {
            this.tokenSource.consume();
            lhs = new BinaryOperationTree(lhs, parseDisEquality(), OperatorType.BITWISE_AND);
        }
        return lhs;
    }

    private ExpressionTree parseDisEquality() {
        ExpressionTree lhs = parseIntegerComparison();
        while (this.tokenSource.peek().isOperator(OperatorType.EQUALITY, OperatorType.DISEQUALITY)) {
            OperatorType operatorType = ((Operator)this.tokenSource.peek()).type();
            this.tokenSource.consume();
            lhs = new BinaryOperationTree(lhs, parseIntegerComparison(), operatorType);
        }
        return lhs;
    }

    private ExpressionTree parseIntegerComparison() {
        ExpressionTree lhs = parseShift();
        while (this.tokenSource.peek().isOperator(
                OperatorType.SMALLER, OperatorType.SMALLER_EQUAL,
                OperatorType.BIGGER, OperatorType.BIGGER_EQUAL
        )) {
            OperatorType operatorType = ((Operator)this.tokenSource.peek()).type();
            this.tokenSource.consume();
            lhs = new BinaryOperationTree(lhs, parseShift(), operatorType);
        }
        return lhs;
    }

    private ExpressionTree parseShift() {
        ExpressionTree lhs = parsePlusMinus();
        while (this.tokenSource.peek().isOperator(
                OperatorType.SHIFT_LEFT, OperatorType.SHIFT_RIGHT
        )) {
            OperatorType operatorType = ((Operator)this.tokenSource.peek()).type();
            this.tokenSource.consume();
            lhs = new BinaryOperationTree(lhs, parsePlusMinus(), operatorType);
        }
        return lhs;
    }

    private ExpressionTree parsePlusMinus() {
        ExpressionTree lhs = parseMulDivMod();
        while (this.tokenSource.peek().isOperator(OperatorType.PLUS, OperatorType.MINUS)) {
            OperatorType operatorType = ((Operator)this.tokenSource.peek()).type();
            this.tokenSource.consume();
            lhs = new BinaryOperationTree(lhs, parseMulDivMod(), operatorType);
        }
        return lhs;
    }

    private ExpressionTree parseMulDivMod() {
        ExpressionTree lhs = parseFactor();
        while (this.tokenSource.peek().isOperator(
                OperatorType.MUL, OperatorType.DIV, OperatorType.MOD
        )) {
            OperatorType operatorType = ((Operator)this.tokenSource.peek()).type();
            this.tokenSource.consume();
            lhs = new BinaryOperationTree(lhs, parseFactor(), operatorType);
        }
        return lhs;
    }

    private ExpressionTree parseFactor() {
        return switch (this.tokenSource.peek()) {
            case Separator(var type, _) when type == SeparatorType.PAREN_OPEN -> {
                this.tokenSource.consume();
                ExpressionTree expression = parseExpression();
                this.tokenSource.expectSeparator(SeparatorType.PAREN_CLOSE);
                yield expression;
            }
            case Operator(var type, _) when
                    type == OperatorType.MINUS || type == OperatorType.LOGICAL_NOT
                    || type == OperatorType.BITWISE_NOT -> {
                Span span = this.tokenSource.consume().span();
                yield new UnaryOperatorTree(parseFactor(), type, span);
            }
            case Identifier ident -> {
                if (isIdentCall()) yield parseIdentCall();
                this.tokenSource.consume();
                IdentExpressionTree identExpr = new IdentExpressionTree(name(ident));
                yield identExpr;
            }
            case NumberLiteral(String value, int base, Span span) -> {
                this.tokenSource.consume();
                yield new IntLiteralTree(value, base, span);
            }
            case Keyword(KeywordType type, Span span) when
                    type == KeywordType.FALSE || type == KeywordType.TRUE -> {
                this.tokenSource.consume();
                yield new BoolLiteralTree(type == KeywordType.TRUE, span);
            }
            case Keyword(KeywordType type, Span _) when
                    type == KeywordType.PRINT || type == KeywordType.READ || type == KeywordType.FLUSH -> {
                yield parseKeywordCall();
            }
            case Token t -> throw new ParseException("invalid factor " + t);
        };
    }

    private static NameTree name(Identifier ident) {
        return new NameTree(Name.forIdentifier(ident), ident.span());
    }

    private static NameTree name(Keyword keyword) {
        return new NameTree(Name.forKeyword(keyword), keyword.span());
    }

    private BasicType basicType(Keyword keyword) {
        return switch (keyword.type()) {
            case KeywordType.INT -> BasicType.INT;
            case KeywordType.BOOL -> BasicType.BOOL;
            default -> throw new ParseException("Keyword " + keyword.type() + " does not represent a type");
        };
    }
}
