package ast;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Shane on 18/04/16.
 */
public interface Expr extends FunctionElement {

    public static final String add = "add";
    public static final String sub = "sub";
    public static final String div = "div_s";
    public static final String mul = "mul";
    public static final String BITWISE_OR = "or";
    public static final String BITWISE_XOR = "xor";
    public static final String BITWISE_AND = "and";
    public static final String REM = "rem_s";//TODO: Change when code changes.



    public static final String INT = "i32";//TODO: Change when code changes.



    abstract class Nop implements Expr {

        public Nop () {}

    }

    class BNop extends Nop {
        @Override
        public void write(BufferedOutputStream out) throws IOException {
        }

        @Override
        public void write(BufferedOutputStream out, int indent) throws IOException {
        }
    }

    class SNop extends Nop {
        @Override
        public void write(BufferedOutputStream out) throws IOException {
            out.write("( nop )".getBytes());
        }

        @Override
        public void write(BufferedOutputStream out, int indent) throws IOException {
            indent(out, indent);
            write(out);
        }
    }


    abstract class Block implements Expr{

        private final String name;

        private final List<Expr> exprs;

        public Block (String name, List<Expr> exprs) {
            this.name = name;
            this.exprs = new ArrayList<>(exprs);
        }

        public String getName() {
            return name;
        }

        public List<Expr> getExprs() {
            return exprs;
        }
    }

    class SBlock extends Block {

        public SBlock(String name, List<Expr> exprs) {
            super(name, exprs);
        }

        @Override
        public void write(BufferedOutputStream out) throws IOException {

        }

        @Override
        public void write(BufferedOutputStream out, int indent) throws IOException {

        }
    }

    abstract class Loop implements Expr {

        private final String nameOne;
        private final String nameTwo;
        private final List<Expr> expers;

        public Loop (String nameOne, String nameTwo, List<Expr> expers) {
            this.nameOne = nameOne;
            this.nameTwo = nameTwo;
            this.expers = new ArrayList<>(expers);
        }

        public String getNameOne () {
            return nameOne;
        }

        public String getNameTwo () {
            return nameTwo;
        }

        private List<Expr> getExpers () {
            return expers;
        }

    }

    class SLoop extends Loop {

        public SLoop(String nameOne, String nameTwo, List<Expr> expers) {
            super(nameOne, nameTwo, expers);
        }

        @Override
        public void write(BufferedOutputStream out) throws IOException {

        }

        @Override
        public void write(BufferedOutputStream out, int indent) throws IOException {

        }
    }

    abstract class Select implements Expr {

        private final Expr exprOne;
        private final Expr exprTwo;
        private final Expr exprThree;

        public Select (Expr exprOne, Expr exprTwo, Expr exprThree) {
            this.exprOne = exprOne;
            this.exprTwo = exprTwo;
            this.exprThree = exprThree;
        }

        public Expr getExprOne () {
            return exprOne;
        }

        public Expr getExprTwo () {
            return exprTwo;
        }

        public Expr getExprThree () {
            return exprThree;
        }
    }

    class SSelect extends Select {

        public SSelect(Expr exprOne, Expr exprTwo, Expr exprThree) {
            super(exprOne, exprTwo, exprThree);
        }

        @Override
        public void write(BufferedOutputStream out) throws IOException {

        }

        @Override
        public void write(BufferedOutputStream out, int indent) throws IOException {

        }
    }

    abstract class If implements Expr {

        private final Expr condition;
        private final String thenLabel;
        private final List<Expr> thenExprs;
        private final String elseLabel;
        private final List<Expr> elseExprs;

        public If (Expr condition, String thenLabel, List<Expr> thenExprs, String elseLabel, List<Expr> elseExprs) {
            this.condition = condition;
            this.thenLabel = thenLabel;
            this.thenExprs = new ArrayList<>(thenExprs);
            this.elseLabel = elseLabel;
            this.elseExprs = elseExprs;
        }

        public Expr getCondition () {
            return condition;
        }

        public String getThenLabel () {
            return thenLabel;
        }

        public List<Expr> getThenExprs () {
            return thenExprs;
        }

        public String getElseLabel () {
            return elseLabel;
        }

        public List<Expr> getElseExprs () {
            return elseExprs;
        }

    }

    class SIf extends If {

        public SIf(Expr condition, String thenLabel, List<Expr> thenExprs, String elseLabel, List<Expr> elseExprs) {
            super(condition, thenLabel, thenExprs, elseLabel, elseExprs);
        }

        @Override
        public void write(BufferedOutputStream out) throws IOException {

        }

        @Override
        public void write(BufferedOutputStream out, int indent) throws IOException {

        }
    }

    abstract class Br implements Expr {

        private final ExprElement.Var var;
        private final Expr expr;

        public Br (ExprElement.Var var, Expr expr) {
            this.var = var;
            this.expr = expr;
        }

        public ExprElement.Var getVar () {
            return var;
        }

        public Expr getExpr () {
            return expr;
        }

    }

    class SBr extends Br {

        public SBr(ExprElement.Var var, Expr expr) {
            super(var, expr);
        }

        @Override
        public void write(BufferedOutputStream out) throws IOException {

        }

        @Override
        public void write(BufferedOutputStream out, int indent) throws IOException {

        }
    }

    abstract class BrIf implements Expr {

        private final ExprElement.Var var;
        private final Expr expr;
        private final Expr condition;

        public BrIf (ExprElement.Var var, Expr expr, Expr condition) {
            this.var = var;
            this.expr = expr;
            this.condition = condition;
        }

        public ExprElement.Var getVar () {
            return var;
        }

        public Expr getExpr () {
            return expr;
        }

        public Expr getCondition () {
            return condition;
        }

    }

    class SBrIf extends BrIf {

        public SBrIf(ExprElement.Var var, Expr expr, Expr condition) {
            super(var, expr, condition);
        }

        @Override
        public void write(BufferedOutputStream out) throws IOException {

        }

        @Override
        public void write(BufferedOutputStream out, int indent) throws IOException {

        }
    }

    abstract class BrTable implements Expr {

        private final ExprElement.Var varOne;
        private final ExprElement.Var varTwo;
        private final Expr exprOne;
        private final Expr exprTwo;

        public BrTable (ExprElement.Var varOne, ExprElement.Var varTwo, Expr exprOne, Expr exprTwo) {
            this.varOne = varOne;
            this.varTwo = varTwo;
            this.exprOne = exprOne;
            this.exprTwo = exprTwo;
        }

        public ExprElement.Var getVarOne () {
            return varOne;
        }

        public ExprElement.Var getVarTwo () {
            return varTwo;
        }

        public Expr getExprOne () {
            return exprOne;
        }

        public Expr getExprTwo () {
            return exprTwo;
        }

    }

    class SBrTable extends BrTable {

        public SBrTable(ExprElement.Var varOne, ExprElement.Var varTwo, Expr exprOne, Expr exprTwo) {
            super(varOne, varTwo, exprOne, exprTwo);
        }

        @Override
        public void write(BufferedOutputStream out) throws IOException {

        }

        @Override
        public void write(BufferedOutputStream out, int indent) throws IOException {

        }
    }

    abstract class Return implements Expr {

        private final Expr expr;

        public Return (Expr expr) {
            this.expr = expr;
        }

        public Expr getExpr () {
            return expr;
        }

    }

    class SReturn extends Return {

        public SReturn(Expr expr) {
            super(expr);
        }

        @Override
        public void write(BufferedOutputStream out) throws IOException {
            getExpr().write(out);
        }

        @Override
        public void write(BufferedOutputStream out, int indent) throws IOException {
            indent(out, indent);
            write(out);
        }
    }

    abstract class Call implements Expr {

        private final ExprElement.Var var;
        private final List<Expr> expr;

        public Call (ExprElement.Var var, List<Expr> expr) {
            this.var = var;
            this.expr = new ArrayList<>(expr);
        }

        public ExprElement.Var getVar () {
            return var;
        }

        public List<Expr> getExpr () {
            return expr;
        }

    }

    class SCall extends Call {

        public SCall(ExprElement.Var var, List<Expr> expr) {
            super(var, expr);
        }

        @Override
        public void write(BufferedOutputStream out) throws IOException {
            out.write("( call ".getBytes());
            getVar().write(out);
            for (Expr expr: getExpr()) {
                out.write(" ".getBytes());
                expr.write(out);
            }
            out.write(" )".getBytes());
        }

        @Override
        public void write(BufferedOutputStream out, int indent) throws IOException {
            indent(out, indent);
            write(out);
        }
    }

    abstract class CallImport implements Expr{

        private final ExprElement.Var var;
        private final List<Expr> exprs;

        public CallImport (ExprElement.Var var, List<Expr> exprs) {
            this.var = var;
            this.exprs = exprs;
        }

        public ExprElement.Var getVar () {
            return var;
        }

        public List<Expr> getExprs() {
            return exprs;
        }

    }

    class SCallImport extends CallImport {

        public SCallImport(ExprElement.Var var, List<Expr> exprs) {
            super(var, exprs);
        }

        @Override
        public void write(BufferedOutputStream out) throws IOException {

        }

        @Override
        public void write(BufferedOutputStream out, int indent) throws IOException {

        }
    }

    abstract class CallIndirect implements Expr {

        private final ExprElement.Var var;
        private final Expr expr;
        private final List<Expr> exprs;

        public CallIndirect (ExprElement.Var var, Expr expr, List<Expr> exprs) {
            this.var = var;
            this.expr = expr;
            this.exprs = new ArrayList<>(exprs);
        }

    }

    class SCallIndirect extends CallIndirect {

        public SCallIndirect(ExprElement.Var var, Expr expr, List<Expr> exprs) {
            super(var, expr, exprs);
        }

        @Override
        public void write(BufferedOutputStream out) throws IOException {

        }

        @Override
        public void write(BufferedOutputStream out, int indent) throws IOException {

        }
    }

    abstract class GetLocal implements Expr {

        private ExprElement.Var var;

        public GetLocal (ExprElement.Var var) {
            this.var = var;
        }

        public ExprElement.Var getVar () {
            return var;
        }

    }

    class SGetLocal extends GetLocal {

        public SGetLocal(ExprElement.Var var) {
            super(var);
        }

        @Override
        public void write(BufferedOutputStream out) throws IOException {
            out.write("( get_local ".getBytes());
            getVar().write(out);
            out.write(" )".getBytes());
        }

        @Override
        public void write(BufferedOutputStream out, int indent) throws IOException {
            indent(out, indent);
            write(out);
        }
    }

    abstract class SetLocal implements Expr {

        private final ExprElement.Var var;
        private final Expr expr;

        public SetLocal(ExprElement.Var var, Expr expr) {
            this.var = var;
            this.expr = expr;
        }

        public ExprElement.Var getVar() {
            return var;
        }

        public Expr getExpr() {
            return expr;
        }

    }

    class SSetLocal extends SetLocal {

        public SSetLocal(ExprElement.Var var, Expr expr) {
            super(var, expr);
        }

        @Override
        public void write(BufferedOutputStream out) throws IOException {
            out.write("( set_local ".getBytes());
            getVar().write(out);
            out.write(" ".getBytes());
            getExpr().write(out);
            out.write(" )".getBytes());
        }

        @Override
        public void write(BufferedOutputStream out, int indent) throws IOException {
            indent(out, indent);
            write(out);
        }
    }

    abstract class Load implements Expr {

        private final ExprElement.Type type;
        private final String sign;
        private final Integer offset;
        private final Integer align;
        private final Expr expr;

        public Load(ExprElement.Type type, String sign, Integer offset, Integer align, Expr expr) {
            this.type = type;
            this.sign = sign;
            this.offset = offset;
            this.align = align;
            this.expr = expr;
        }

        public ExprElement.Type getType() {
            return type;
        }

        public String getSign() {
            return sign;
        }

        public Integer getOffset() {
            return offset;
        }

        public Integer getAlign() {
            return align;
        }

        public Expr getExpr() {
            return expr;
        }
    }

    class SLoad extends Load {

        public SLoad(ExprElement.Type type, String sign, Integer offset, Integer align, Expr expr) {
            super(type, sign, offset, align, expr);
        }

        @Override
        public void write(BufferedOutputStream out) throws IOException {

        }

        @Override
        public void write(BufferedOutputStream out, int indent) throws IOException {

        }
    }

    abstract class Store implements Expr {

        private final ExprElement.Type type;
        private final Integer offset;
        private final Integer align;
        private final Expr location;
        private final Expr value;

        public Store(ExprElement.Type type, Integer offset, Integer align, Expr location, Expr value) {
            this.type = type;
            this.offset = offset;
            this.align = align;
            this.location = location;
            this.value = value;
        }

        public ExprElement.Type getType() {
            return type;
        }

        public Integer getOffset() {
            return offset;
        }

        public Integer getAlign() {
            return align;
        }

        public Expr getLocation() {
            return location;
        }

        public Expr getValue() {
            return value;
        }
    }

    class SStore extends Store {

        public SStore(ExprElement.Type type, Integer offset, Integer align, Expr location, Expr value) {
            super(type, offset, align, location, value);
        }

        @Override
        public void write(BufferedOutputStream out) throws IOException {

        }

        @Override
        public void write(BufferedOutputStream out, int indent) throws IOException {

        }
    }

    abstract class Const implements Expr {

        private final ExprElement.Type type;
        private final ExprElement.Value value;

        public Const(ExprElement.Type type, ExprElement.Value value) {
            this.type = type;
            this.value = value;
        }

        public ExprElement.Type getType() {
            return type;
        }

        public ExprElement.Value getValue() {
            return value;
        }
    }

    class SConst extends Const {

        public SConst(ExprElement.Type type, ExprElement.Value value) {
            super(type, value);
        }

        @Override
        public void write(BufferedOutputStream out) throws IOException {
            out.write("( ".getBytes());
            getType().write(out);
            out.write(".const ".getBytes());
            getValue().write(out);
            out.write(" )".getBytes());
        }

        @Override
        public void write(BufferedOutputStream out, int indent) throws IOException {
            indent(out, indent);
            write(out);
        }
    }

    abstract class UnOp implements Expr {

        private final ExprElement.Type type;
        private final String op;
        private final Expr expr;

        public UnOp(ExprElement.Type type, String op, Expr expr) {
            this.type = type;
            this.op = op;
            this.expr = expr;
        }

        public ExprElement.Type getType() {
            return type;
        }

        public String getOp() {
            return op;
        }

        public Expr getExpr() {
            return expr;
        }
    }

    class SUnOp extends UnOp {

        public SUnOp(ExprElement.Type type, String op, Expr expr) {
            super(type, op, expr);
        }

        @Override
        public void write(BufferedOutputStream out) throws IOException {

        }

        @Override
        public void write(BufferedOutputStream out, int indent) throws IOException {

        }
    }

    abstract class BinOp implements Expr {

        private final ExprElement.Type type;
        private final String op;
        private final Expr argOne;
        private final Expr argTwo;

        public BinOp(ExprElement.Type type, String op, Expr argOne, Expr argTwo) {
            this.type = type;
            this.op = op;
            this.argOne = argOne;
            this.argTwo = argTwo;
        }

        public ExprElement.Type getType() {
            return type;
        }

        public String getOp() {
            return op;
        }

        public Expr getArgOne() {
            return argOne;
        }

        public Expr getArgTwo() {
            return argTwo;
        }
    }

    class SBinOp extends BinOp {

        public SBinOp(ExprElement.Type type, String op, Expr argOne, Expr argTwo) {
            super(type, op, argOne, argTwo);
        }

        @Override
        public void write(BufferedOutputStream out) throws IOException {
            out.write("( ".getBytes());
            getType().write(out);
            out.write(".".getBytes());
            out.write(getOp().getBytes());
            out.write(" ".getBytes());
            getArgOne().write(out);
            out.write(" ".getBytes());
            getArgTwo().write(out);
            out.write(" )".getBytes());
        }

        @Override
        public void write(BufferedOutputStream out, int indent) throws IOException {
            indent(out, indent);
            write(out);
        }
    }

    abstract class RelOp implements Expr {

    }

    class SRelOp extends RelOp {

        @Override
        public void write(BufferedOutputStream out) throws IOException {

        }

        @Override
        public void write(BufferedOutputStream out, int indent) throws IOException {

        }
    }

    abstract class CvtOp implements Expr {

    }

    class SCvtOp extends CvtOp {

        @Override
        public void write(BufferedOutputStream out) throws IOException {

        }

        @Override
        public void write(BufferedOutputStream out, int indent) throws IOException {

        }
    }

    abstract class Unreachable implements Expr {

    }

    class SUnreachable extends Unreachable {

        @Override
        public void write(BufferedOutputStream out) throws IOException {
            out.write("( unreachable )".getBytes());
        }

        @Override
        public void write(BufferedOutputStream out, int indent) throws IOException {
            indent(out, indent);
            write(out);
        }
    }

    abstract class MemorySize implements Expr {

    }

    class SMemorySize extends MemorySize {

        @Override
        public void write(BufferedOutputStream out) throws IOException {

        }

        @Override
        public void write(BufferedOutputStream out, int indent) throws IOException {

        }
    }

    abstract class GrowMemory implements Expr {

    }

    class SGrowMemory extends GrowMemory {

        @Override
        public void write(BufferedOutputStream out) throws IOException {

        }

        @Override
        public void write(BufferedOutputStream out, int indent) throws IOException {

        }
    }
}
