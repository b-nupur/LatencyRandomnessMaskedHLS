import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The class now processes unary operators correctly but does not handle ternary operators.
 */
public class ThreeAddressCodeGenerator {

    private int tempCounter = 0; // Counter for generating temporary variables
    private List<String> instructions = new ArrayList<>(); // Stores TAC instructions

    /**
     * Inner class to represent a token.
     */
    private static class Token {
        enum Type { OPERAND, OPERATOR, PARENTHESIS, UNKNOWN }

        private final String value;
        private final Type type;
//        private final boolean isUnary;

        public Token(String value, Type type) {
            this.value = value;
            this.type = type;
        }

        public String getValue() {
            return value;
        }

        public Type getType() {
            return type;
        }

        public boolean isOperand() {
            return type == Type.OPERAND;
        }

        public boolean isOperator() {
            return type == Type.OPERATOR;
        }

        public boolean isParenthesis() {
            return type == Type.PARENTHESIS;
        }

        public static Token fromString(String str) {
            if (str.matches("[a-zA-Z_][a-zA-Z0-9_]*|\\d+")) {
                return new Token(str, Type.OPERAND);
            } else if (str.matches("[-+*/%&|^<>=]=?|<<=?|>>=?|&&|\\|\\||[-+!]|\\+\\+|--")) {
                return new Token(str, Type.OPERATOR);
            } else if (str.equals("(") || str.equals(")")) {
                return new Token(str, Type.PARENTHESIS);
            } else {
                return new Token(str, Type.UNKNOWN);
            }
        }
    }


    /**
     * Generates a new temporary variable.
     */
    private String newTemp() {
        return "t" + (++tempCounter);
    }

    /**
     * Emits a Three-Address Code instruction.
     */
    private void emit(String operation, String arg1, String arg2, String result) {
        if (arg2 == null) {
            instructions.add(result + " = " + operation + " " + arg1); // Unary operation
        } else {
            instructions.add(result + " = " + arg1 + " " + operation + " " + arg2); // Binary operation
        }
    }

    /**
     * Translates a function body into Three-Address Code (TAC).
     */
    public void translateToTAC(String functionBody) {
        // Remove variable declarations
        functionBody = preprocessDeclarations(functionBody);

        // Split the function body into individual statements
        String[] statements = functionBody.split(";");

        for (String statement : statements) {
            statement = statement.trim();
            if (statement.isEmpty()) continue;

            if (statement.startsWith("return")) {
                // Handle return statements
                String returnValue = statement.replace("return", "").trim();
                String result = parseExpression(returnValue);
                instructions.add("return " + result);
            } else if (statement.matches(".+=.+")) { // Match assignment or compound assignment
                processAssignment(statement);
            } else if(statement.startsWith("++") || statement.startsWith("--")){
                parseExpression(statement);
            }
            else if(statement.endsWith("++") || statement.endsWith("--")){
                parseExpression(statement);
            }
        }
    }

    /**
     * Processes an assignment statement, including compound assignments.
     */
    private void processAssignment(String statement) {
        // Match compound assignment operators
        Pattern compoundPattern = Pattern.compile("(.+)(\\+=|-=|\\*=|/=|%=|&=|\\|=|\\^=|<<=|>>=)(.+)");
        Matcher matcher = compoundPattern.matcher(statement);

        if (matcher.matches()) {
            String lhs = matcher.group(1).trim(); // Left-hand side (variable being assigned)
            String operator = matcher.group(2).trim(); // Compound operator (e.g., +=)
            String rhs = matcher.group(3).trim(); // Right-hand side (expression)

            // Decompose compound assignment (e.g., a += b -> a = a + b)
            String simpleOperator = operator.substring(0, operator.length() - 1); // Extract base operator (e.g., +, -, etc.)
            String result = parseExpression(lhs + " " + simpleOperator + " " + rhs); // Parse the equivalent binary expression
            instructions.add(lhs + " = " + result); // Emit assignment instruction
        } else if(statement.matches(".+=.+")){
            // Handle simple assignments
            String[] parts = statement.split("=");
            String lhs = parts[0].trim(); // Left-hand side (variable being assigned)
            String rhs = parts[1].trim(); // Right-hand side (expression)
            // check for pre-increment/decrement (e.g., ++x, --x)
            if(rhs.matches("(\\+\\+|--)[a-zA-Z_][a-zA-Z0-9_]*")){
                String operator = rhs.substring(0,2); // extract operator (++ or --)
                String variable = rhs.substring(2).trim(); // extract variable name (++var)
                String temp = newTemp(); // Temporary variable for incremented value
                emit("+", variable, "1", temp); // generates the TAC for the operation
                instructions.add(lhs + " = " + temp);
            }else if(rhs.matches("[a-zA-Z_][a-zA-Z0-9_]*(\\+\\+|--)")){
                String variable = rhs.substring(0, rhs.length() - 2).trim(); // extract the variable name
                String operator = rhs.substring(rhs.length()-2); // extract post inc/dec operator (x++ or x--)
                String temp = newTemp(); // Temporary variable for the original value
                instructions.add(lhs + " = " + variable);
                emit("+", variable, "1", variable); // increment the variable
            }else {
                // handle simple assignment expression (x = y + z)
                String result = parseExpression(rhs);
                instructions.add(lhs + " = " + result); // Emit assignment instruction
            }
        }

    }

    /**
     * Parses an expression and generates TAC instructions.
     */
    private String parseExpression(String expression) {
        Stack<String> operands = new Stack<>();
        Stack<Token> operators = new Stack<>();

        // Updated regex token pattern
       String tokenPattern = "\\+\\+|--|==|!=|<=|>=|\\+=|-=|\\*=|/=|%=|&=|\\|=|\\^=|<<=|>>=|&&|\\|\\||<<|>>|[-+*/%&|^<>=()\\[\\]?:]|[a-zA-Z_][a-zA-Z0-9_]*|\\d+";

        Pattern pattern = Pattern.compile(tokenPattern);
        Matcher matcher = pattern.matcher(expression);

        Token prevToken = null;

        while (matcher.find()) {
            String tokenStr = matcher.group().trim();
            if (tokenStr.isEmpty()) continue;

            Token token = Token.fromString(tokenStr);

            if (token.isOperand()) {
                operands.push(token.getValue());
                prevToken = token;
            } else if (token.isOperator()) {
                // Handle Postfix Increment/Decrement (e.g., b++)
                if (prevToken != null && prevToken.isOperand() && (token.getValue().equals("++") || token.getValue().equals("--"))) {
                    String variable = operands.pop();
                    String temp = newTemp();
                    instructions.add(temp + " = " + variable); // Store original value
                    emit(token.getValue().substring(0, 1), variable, "1", variable); // Increment/Decrement the variable
                    operands.push(temp); // Push original value to stack
                    prevToken = token;
                }
                // Handle Prefix Increment/Decrement (e.g., ++x, --x)
                else if ((token.getValue().equals("++") || token.getValue().equals("--"))
                        && (prevToken == null || prevToken.isOperator() || (prevToken.isParenthesis() && prevToken.getValue().equals("(")))) {
                    if (!matcher.find()) {
                        throw new IllegalArgumentException("Invalid operand after prefix operator: " + token.getValue());
                    }
                    String nextOperand = matcher.group().trim(); // Look ahead for operand
                    if (!nextOperand.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                        throw new IllegalArgumentException("Invalid operand after prefix operator: " + token.getValue());
                    }
                    String temp = newTemp();
                    emit(token.getValue().substring(0, 1), nextOperand, "1", nextOperand); // Increment/Decrement the operand
                    operands.push(nextOperand); // Push updated operand to stack
                    prevToken = Token.fromString(nextOperand);
                } else {
                    if (prevToken == null || prevToken.isOperator() || (prevToken.isParenthesis() && prevToken.getValue().equals("("))) {
                        // handle unary operators
                        if (token.getValue().equals("+") || token.getValue().equals("-")) {
                            // unary + or -
                            String nextOperand = matcher.find() ? matcher.group() : null;
                            if (nextOperand == null || !nextOperand.matches("[a-zA-Z][a-zA-Z0-9_]*|\\d+")) {
                                throw new IllegalArgumentException("Invalid unary operator: " + token.getValue());
                            }
                            String temp = newTemp();
                            emit(token.getValue(), nextOperand, null, temp);
                            operands.push(temp);
                            prevToken = Token.fromString(nextOperand);
                        }
                    } else {
                        // Handle binary operators
                        while (!operators.isEmpty() && precedence(operators.peek().getValue()) >= precedence(token.getValue())) {
                            processOperation(operands, operators.pop().getValue());
                        }
                        operators.push(token);
                        prevToken = token;
                    }
                }
            } else if (token.isParenthesis()) {
                if (token.getValue().equals("(")) {
                    operators.push(token);
                } else if (token.getValue().equals(")")) {
                    while (!operators.isEmpty() && !operators.peek().getValue().equals("(")) {
                        processOperation(operands, operators.pop().getValue());
                    }
                    if (operators.isEmpty() || !operators.peek().getValue().equals("(")) {
                        throw new IllegalArgumentException("Mismatched parentheses in expression: " + expression);
                    }
                    operators.pop();
                }
                prevToken = token;
            }
        }

        while (!operators.isEmpty()) {
            Token op = operators.pop();
            if (op.getValue().equals("(")) {
                throw new IllegalArgumentException("Mismatched parentheses in expression: " + expression);
            }
            processOperation(operands, op.getValue());
        }

        if (operands.size() != 1) {
            throw new IllegalArgumentException("Invalid syntax: Expression parsing resulted in incorrect operands.");
        }

        return operands.pop();
    }


    /**
     * Processes a single operation and generates TAC.
     */
    private void processOperation(Stack<String> operands, String operator) {
        if (operands.size() < 2) {
            throw new IllegalArgumentException("Invalid syntax: Not enough operands for binary operator '" + operator + "'.");
        }
        String operand2 = operands.pop();
        String operand1 = operands.pop();
        String temp = newTemp();
        emit(operator, operand1, operand2, temp);
        operands.push(temp);
    }

    /**
     * Determines the precedence of an operator.
     */
    private int precedence(String operator) {
        return switch (operator) {
            case "++", "--" -> 4;
            case "*", "/", "%" -> 3; // Multiplicative
            case "+", "-" -> 2; // Additive
            case "<<", ">>" -> 1; // Shift
            case "<", "<=", ">", ">=", "==", "!=" -> 0; // Relational
            case "&", "^", "|" -> -1; // Bitwise
            case "&&", "||" -> -2; // Logical
            case "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<=", ">>=" -> -3; // Compound assignment
            default -> -4; // Lowest precedence
        };
    }

    /**
     * Preprocesses the function body to remove variable declarations.
     */
    private String preprocessDeclarations(String functionBody) {
        String[] lines = functionBody.split("\\n");
        StringBuilder processedBody = new StringBuilder();

        for (String line : lines) {
            line = line.trim();
            if (!line.matches("(int|float|double|char|short|long|unsigned|signed)[^;]*;")) {
                processedBody.append(line).append("\n");
            }
        }
        return processedBody.toString();
    }

    /**
     * Prints the generated TAC instructions.
     */
    public void printInstructions() {
        for (String instruction : instructions) {
            System.out.println(instruction);
        }
    }

    public static void main(String[] args) {
        ThreeAddressCodeGenerator generator = new ThreeAddressCodeGenerator();

        // Example function body
        String functionBody = """
                int asdd , b, c;
                asdd = 0;
                b = 5;
                a += x + y;
                b *= z - w;
                ++ohh;
                c = b++;
                b = ++c;
                z = ++b + 3 + d;
                p = -q + +r * e;
                return (a + b) / 2;
                """;

        generator.translateToTAC(functionBody);
        generator.printInstructions();
    }
}
